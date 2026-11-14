#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <string>
#include <vector>
#include <unistd.h>
#include "llama.h"

extern "C" JNIEXPORT jstring JNICALL
Java_jp_jarvis_local_ModelBridge_nativeGenerate(JNIEnv *env, jclass, jstring jm, jstring jp) {
    auto text = [&](jstring s) { const char *p=env->GetStringUTFChars(s,nullptr); std::string r=p?p:""; if(p) env->ReleaseStringUTFChars(s,p); return r; };
    const std::string model_path=text(jm), prompt=text(jp);
    if (model_path.empty()) return env->NewStringUTF("[モデル未配置] GGUFモデルを選択してください。");
    if (prompt.empty()) return env->NewStringUTF("[推論エラー] プロンプトが空です。");
    llama_backend_init();
    auto mp=llama_model_default_params(); mp.n_gpu_layers=0;
    llama_model *model=llama_model_load_from_file(model_path.c_str(),mp);
    if(!model){llama_backend_free();return env->NewStringUTF("[推論エラー] GGUFモデルを読み込めませんでした。");}
    const llama_vocab *v=llama_model_get_vocab(model);
    int np=-llama_tokenize(v,prompt.c_str(),prompt.size(),nullptr,0,true,true);
    if(np<=0||np>1536){llama_model_free(model);llama_backend_free();return env->NewStringUTF("[推論エラー] プロンプトが長すぎるか、トークン化に失敗しました。");}
    std::vector<llama_token> tokens(np);
    if(llama_tokenize(v,prompt.c_str(),prompt.size(),tokens.data(),tokens.size(),true,true)<0){llama_model_free(model);llama_backend_free();return env->NewStringUTF("[推論エラー] プロンプトのトークン化に失敗しました。");}
    auto cp=llama_context_default_params(); cp.n_ctx=std::min<uint32_t>(2048,np+200); cp.n_batch=std::min<uint32_t>(256,np); cp.n_threads=std::min<uint32_t>(4,std::max<uint32_t>(2,sysconf(_SC_NPROCESSORS_ONLN)-1)); cp.n_threads_batch=cp.n_threads;
    llama_context *ctx=llama_init_from_model(model,cp);
    if(!ctx){llama_model_free(model);llama_backend_free();return env->NewStringUTF("[推論エラー] 推論コンテキストを確保できませんでした。");}
    auto *s=llama_sampler_chain_init(llama_sampler_chain_default_params()); llama_sampler_chain_add(s,llama_sampler_init_top_k(40)); llama_sampler_chain_add(s,llama_sampler_init_top_p(.92f,1)); llama_sampler_chain_add(s,llama_sampler_init_temp(.7f)); llama_sampler_chain_add(s,llama_sampler_init_dist(1234));
    auto *b=llama_batch_ext_init(ctx); if(!b){llama_sampler_free(s);llama_free(ctx);llama_model_free(model);llama_backend_free();return env->NewStringUTF("[推論エラー] 推論バッチを確保できませんでした。");}
    for(auto t:tokens) if(llama_batch_ext_add_token(b,0,t)<0){llama_batch_ext_free(b);llama_sampler_free(s);llama_free(ctx);llama_model_free(model);llama_backend_free();return env->NewStringUTF("[推論エラー] 推論バッチの作成に失敗しました。");}
    if(llama_process(ctx,LLAMA_PROCESS_TYPE_DECODE,b)!=0){llama_batch_ext_free(b);llama_sampler_free(s);llama_free(ctx);llama_model_free(model);llama_backend_free();return env->NewStringUTF("[推論エラー] プロンプトの評価に失敗しました。");}
    std::string out; for(int i=0;i<192;i++){auto t=llama_sampler_sample(s,ctx,-1);if(llama_vocab_is_eog(v,t))break;char piece[256];int n=llama_token_to_piece(v,t,piece,sizeof(piece),0,true);if(n>0)out.append(piece,n);llama_sampler_accept(s,t);llama_batch_ext_clear(b);if(llama_batch_ext_add_token(b,0,t)<0||llama_process(ctx,LLAMA_PROCESS_TYPE_DECODE,b)!=0)break;}
    llama_batch_ext_free(b);llama_sampler_free(s);llama_free(ctx);llama_model_free(model);llama_backend_free();
    return env->NewStringUTF(out.empty()?"[推論エラー] モデルから生成結果を取得できませんでした。":out.c_str());
}
