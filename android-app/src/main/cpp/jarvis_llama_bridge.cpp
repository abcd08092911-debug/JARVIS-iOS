#include <jni.h>
#include <string>
extern "C" JNIEXPORT jstring JNICALL Java_jp_jarvis_local_ModelBridge_nativeGenerate(JNIEnv* env,jclass,jstring,jstring){
    // llama.cpp本体とGGUFモデルは同梱せず、固定のAI回答も返さない安全な未実装ブリッジ。
    return nullptr;
}
