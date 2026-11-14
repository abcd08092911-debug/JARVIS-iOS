package jp.jarvis.local;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private EditText input; private TextView log, status; private TextToSpeech tts; private SpeechRecognizer recognizer;
    private SQLiteDatabase db; private JarvisHttpServer server;
    private final String password = "Yomi0929";
    @Override public void onCreate(Bundle b) { super.onCreate(b); db=openOrCreateDatabase("jarvis.sqlite3",MODE_PRIVATE,null); db.execSQL("CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, content TEXT, created_at INTEGER)");
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); root.setBackgroundColor(Color.rgb(16,21,28));
        TextView title=new TextView(this); title.setText("JARVIS  Termux不要版"); title.setTextColor(Color.WHITE); title.setTextSize(24); root.addView(title);
        status=new TextView(this); status.setText("停止中"); status.setTextColor(Color.LTGRAY); root.addView(status);
        log=new TextView(this); log.setTextColor(Color.WHITE); log.setTextIsSelectable(true); ScrollView scroll=new ScrollView(this); scroll.addView(log); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        input=new EditText(this); input.setHint("JARVISへの指示"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); root.addView(input);
        LinearLayout row=new LinearLayout(this); Button send=new Button(this); send.setText("送信"); Button listen=new Button(this); listen.setText("音声入力"); Button host=new Button(this); host.setText("親機起動"); row.addView(send,new LinearLayout.LayoutParams(0,-2,1)); row.addView(listen,new LinearLayout.LayoutParams(0,-2,1)); row.addView(host,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row); setContentView(root);
        tts=new TextToSpeech(this, s -> { }); send.setOnClickListener(v -> chat(input.getText().toString())); host.setOnClickListener(v -> startHost()); listen.setOnClickListener(v -> listen());
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);
    }
    private void startHost(){ if(server!=null)return; server=new JarvisHttpServer(); server.start(); status.setText("親機起動中: http://0.0.0.0:8765/\n中継機能は次の試作段階で追加"); add("JARVIS: 親機を起動しました。記憶は端末内SQLiteへ保存します。"); }
    private void chat(String text){ if(text==null||text.trim().isEmpty())return; runOnUiThread(() -> input.setText("")); db.execSQL("INSERT INTO messages(role,content,created_at) VALUES(?,?,?)",new Object[]{"user",text,System.currentTimeMillis()}); String answer=answer(text); db.execSQL("INSERT INTO messages(role,content,created_at) VALUES(?,?,?)",new Object[]{"assistant",answer,System.currentTimeMillis()}); add("あなた: "+text+"\nJARVIS: "+answer); }
    private String answer(String text){ if(text.contains("こんにちは")) return "こんにちは。JARVISです。"; if(text.contains("覚えて")||text.contains("記憶")) return "承知しました。端末内の記憶へ保存しました。"; return "承知しました。現在はTermux不要版の試作AIコアです。Qwen3ローカル推論は統合準備中です。"; }
    private void add(String x){ runOnUiThread(() -> log.append(x+"\n\n")); }
    private void listen(){ if(!SpeechRecognizer.isRecognitionAvailable(this)){add("音声認識を利用できません。");return;} if(recognizer!=null)recognizer.destroy(); recognizer=SpeechRecognizer.createSpeechRecognizer(this); recognizer.setRecognitionListener(new RecognitionListener(){public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));chat(a.get(0));}} public void onError(int e){} public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}}); Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ja-JP");recognizer.startListening(i); }
    private class JarvisHttpServer extends Thread { private volatile boolean running=true; public void run(){try{ServerSocket ss=new ServerSocket(8765,20,InetAddress.getByName("0.0.0.0"));while(running){Socket s=ss.accept();new Thread(()->handle(s)).start();}}catch(Exception e){}} private void handle(Socket s){try{BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String line=r.readLine();if(line==null){s.close();return;}String[] p=line.split(" ");int len=0;while(!(line=r.readLine()).isEmpty()){if(line.toLowerCase().startsWith("content-length:"))len=Integer.parseInt(line.split(":")[1].trim());}char[] c=new char[len];if(len>0)r.read(c);String body=new String(c);String out;if(p[1].startsWith("/health"))out="{\"status\":\"ok\",\"mode\":\"android-apk-host\"}";else if(p[1].startsWith("/chat")){String text=body.replaceAll(".*\\\"text\\\"\\s*:\\s*\\\"(.*?)\\\".*","$1");String ans=answer(text);chat(text);out="{\"status\":\"ok\",\"reply\":\""+json(ans)+"\"}";}else out="{\"status\":\"ok\",\"message\":\"JARVIS Android APK\"}";byte[] bytes=out.getBytes(StandardCharsets.UTF_8);OutputStream o=s.getOutputStream();o.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));o.write(bytes);o.flush();s.close();}catch(Exception e){try{s.close();}catch(Exception ignored){}}}private String json(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
