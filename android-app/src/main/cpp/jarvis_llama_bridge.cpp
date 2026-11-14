#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <mutex>
#include <new>
#include <string>
#include <vector>
#include <unistd.h>
#include "llama.h"

namespace {
struct ModelCache {
    llama_model * model = nullptr;
    std::string path;
    bool backend_ready = false;
    std::mutex mutex;

    ~ModelCache() {
        if (model) {
            llama_model_free(model);
            model = nullptr;
        }
        if (backend_ready) {
            llama_backend_free();
        }
    }
};

ModelCache & cache() {
    static ModelCache value;
    return value;
}

jstring error(JNIEnv * env, const char * message) {
    return env->NewStringUTF(message);
}

std::string jstring_text(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

void release_model_locked(ModelCache & state) {
    if (state.model) {
        llama_model_free(state.model);
        state.model = nullptr;
    }
    state.path.clear();
}

bool ensure_model_locked(ModelCache & state, const std::string & path) {
    if (state.model && state.path == path) return true;
    release_model_locked(state);
    if (!state.backend_ready) {
        llama_backend_init();
        state.backend_ready = true;
    }
    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;
    state.model = llama_model_load_from_file(path.c_str(), params);
    if (!state.model) return false;
    state.path = path;
    return true;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_jp_jarvis_local_ModelBridge_nativeGenerate(JNIEnv * env, jclass, jstring jm, jstring jp) {
    const std::string model_path = jstring_text(env, jm);
    const std::string prompt = jstring_text(env, jp);
    if (model_path.empty()) return error(env, "[モデル未配置] GGUFモデルを選択してください。");
    if (prompt.empty()) return error(env, "[推論エラー] プロンプトが空です。");

    ModelCache & state = cache();
    // llama_model and llama_backend are process-global; serialize all model use.
    std::lock_guard<std::mutex> guard(state.mutex);
    try {
        if (!ensure_model_locked(state, model_path)) {
            return error(env, "[推論エラー] GGUFモデルを読み込めませんでした（メモリ不足またはファイル不正）。");
        }

        const llama_vocab * vocab = llama_model_get_vocab(state.model);
        int n_prompt = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
        if (n_prompt <= 0 || n_prompt > 1536) {
            return error(env, "[推論エラー] プロンプトが長すぎるか、トークン化に失敗しました。");
        }
        std::vector<llama_token> tokens((size_t) n_prompt);
        if (llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(), n_prompt, true, true) < 0) {
            return error(env, "[推論エラー] プロンプトのトークン化に失敗しました。");
        }

        auto context_params = llama_context_default_params();
        context_params.n_ctx = 2048;
        context_params.n_batch = std::min<uint32_t>(256, (uint32_t) std::max(1, n_prompt));
        const long cores = sysconf(_SC_NPROCESSORS_ONLN);
        context_params.n_threads = std::min<uint32_t>(4, std::max<uint32_t>(2, cores > 1 ? (uint32_t) cores - 1 : 2));
        context_params.n_threads_batch = context_params.n_threads;
        llama_context * context = llama_init_from_model(state.model, context_params);
        if (!context) return error(env, "[推論エラー] 推論コンテキストを確保できませんでした（メモリ不足）。");

        llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_batch_ext * batch = llama_batch_ext_init(context);
        if (!sampler || !batch) {
            if (batch) llama_batch_ext_free(batch);
            if (sampler) llama_sampler_free(sampler);
            llama_free(context);
            return error(env, "[推論エラー] 推論用メモリを確保できませんでした（メモリ不足）。");
        }
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(.92f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(.7f));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234));

        bool ok = true;
        for (llama_token token : tokens) {
            if (llama_batch_ext_add_token(batch, 0, token) < 0) { ok = false; break; }
        }
        if (ok && llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) ok = false;
        std::string output;
        if (ok) {
            for (int i = 0; i < 160; ++i) {
                const llama_token token = llama_sampler_sample(sampler, context, -1);
                if (llama_vocab_is_eog(vocab, token)) break;
                char piece[256];
                const int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
                if (n > 0) output.append(piece, n);
                llama_sampler_accept(sampler, token);
                llama_batch_ext_clear(batch);
                if (llama_batch_ext_add_token(batch, 0, token) < 0 || llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) break;
            }
        }
        llama_batch_ext_free(batch);
        llama_sampler_free(sampler);
        llama_free(context);
        if (!ok) return error(env, "[推論エラー] プロンプトの評価に失敗しました。");
        return env->NewStringUTF(output.empty() ? "[推論エラー] モデルから生成結果を取得できませんでした。" : output.c_str());
    } catch (const std::bad_alloc &) {
        return error(env, "[推論エラー] メモリ不足です。生成長を短くして再試行してください。");
    } catch (...) {
        return error(env, "[推論エラー] 推論中に予期しないエラーが発生しました。");
    }
}
