package jp.jarvis.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class Workspace {
    private final File root;
    Workspace(File base){root=base;root.mkdirs();}
    private File safe(String rel)throws IOException{File p=new File(root,rel).getCanonicalFile();File r=root.getCanonicalFile();if(!p.equals(r)&&!p.getPath().startsWith(r.getPath()+File.separator))throw new IOException("ワークスペース外への操作は禁止されています");return p;}
    String write(String rel,String content,String kind)throws Exception{if(content.length()>2000000)throw new IOException("1ファイル2MBを超える内容は分割してください");File p=safe(rel);if(!p.getParentFile().exists()&&!p.getParentFile().mkdirs())throw new IOException("親フォルダを作成できません");Files.write(p.toPath(),content.getBytes(StandardCharsets.UTF_8));return new org.json.JSONObject().put("path",p.getAbsolutePath()).put("kind",kind).put("bytes",p.length()).toString();}
    org.json.JSONArray search(String query)throws IOException{org.json.JSONArray a=new org.json.JSONArray();final String q=query.toLowerCase(Locale.ROOT);Files.walk(root.toPath()).forEach(x->{if(a.length()<50&&x.getFileName()!=null&&x.getFileName().toString().toLowerCase(Locale.ROOT).contains(q))a.put(x.toFile().getAbsolutePath());});return a;}
    String path(){return root.getAbsolutePath();}
}
