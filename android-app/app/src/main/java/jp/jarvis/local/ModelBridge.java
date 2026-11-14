package jp.jarvis.local;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ModelBridge {
    private static boolean loaded;
    private static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor();
    static { try { System.loadLibrary("jarvis_llama_bridge"); loaded=true; } catch (Throwable ignored) {} }
    static boolean isNativeAvailable(){ return loaded; }
    static File findModel(File externalDir){
        if(externalDir==null)return null;
        File[] fs=externalDir.listFiles((d,n)->n.toLowerCase().endsWith(".gguf"));
        return fs!=null&&fs.length>0?fs[0]:null;
    }
    static String modelStatus(File externalDir){
        if(!loaded)return "JNI推論ライブラリ未搭載";
        return findModel(externalDir)!=null?"GGUFモデル配置済み（CPU推論接続済み）":"モデル未配置（GGUFモデルを選択）";
    }
    static String generate(String modelPath,String prompt){
        if(!loaded)return "[推論エラー] JNI推論ライブラリを読み込めません。";
        if(modelPath==null||modelPath.isEmpty())return "[モデル未配置] GGUFモデルを選択してください。";
        try { return nativeGenerate(modelPath,prompt); } catch(Throwable e){ return "[推論エラー] "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()); }
    }
    static void generateAsync(String modelPath, String prompt, java.util.function.Consumer<String> callback){
        INFERENCE_EXECUTOR.execute(() -> { String result=generate(modelPath,prompt); if(callback!=null)callback.accept(result); });
    }
    private static native String nativeGenerate(String modelPath,String prompt);
}
