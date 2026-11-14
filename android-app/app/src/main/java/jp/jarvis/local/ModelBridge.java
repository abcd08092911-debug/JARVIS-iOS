package jp.jarvis.local;

import java.io.File;

final class ModelBridge {
    private static boolean loaded;
    static { try { System.loadLibrary("jarvis_llama_bridge"); loaded=true; } catch (Throwable ignored) {} }
    static boolean isNativeAvailable(){return loaded;}
    static String modelStatus(File externalDir){if(externalDir==null)return "モデル未配置";File[] fs=externalDir.listFiles((d,n)->n.toLowerCase().endsWith(".gguf"));return fs!=null&&fs.length>0?"GGUFモデル配置済み（JNI推論接続待ち）":"モデル未配置";}
    static String generate(String modelPath,String prompt){if(!loaded||modelPath==null||modelPath.isEmpty())return null;return nativeGenerate(modelPath,prompt);}
    private static native String nativeGenerate(String modelPath,String prompt);
}
