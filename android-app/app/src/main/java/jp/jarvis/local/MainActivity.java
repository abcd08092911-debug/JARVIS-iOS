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
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String CODE = "Yomi0929";
    private EditText input; private TextView log, status; private TextToSpeech tts; private SpeechRecognizer recognizer;
    private SQLiteDatabase db; private HostServer host; private RelayServer relay; private volatile boolean running;
    private final ConcurrentLinkedQueue<RelayMessage> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String,String> replies = new ConcurrentHashMap<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db=openOrCreateDatabase("jarvis.sqlite3",MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, content TEXT, created_at INTEGER)");
        buildUi();
        tts=new TextToSpeech(this, s -> { });
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);
    }
    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); root.setBackgroundColor(Color.rgb(16,21,28));
        TextView title=new TextView(this); title.setText("JARVIS 親機兼中継機"); title.setTextColor(Color.WHITE); title.setTextSize(24); root.addView(title);
        status=new TextView(this); status.setText("停止中"); status.setTextColor(Color.LTGRAY); root.addView(status);
        log=new TextView(this); log.setTextColor(Color.WHITE); log.setTextIsSelectable(true); ScrollView scroll=new ScrollView(this); scroll.addView(log); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        input=new EditText(this); input.setHint("JARVISへの指示"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); root.addView(input);
        LinearLayout row=new LinearLayout(this); Button send=new Button(this); send.setText("送信"); Button listen=new Button(this); listen.setText("音声入力"); Button hostButton=new Button(this); hostButton.setText("親機・中継機起動"); row.addView(send,new LinearLayout.LayoutParams(0,-2,1)); row.addView(listen,new LinearLayout.LayoutParams(0,-2,1)); row.addView(hostButton,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row); setContentView(root);
        send.setOnClickListener(v -> chat(input.getText().toString(), true)); hostButton.setOnClickListener(v -> startHost()); listen.setOnClickListener(v -> listen());
    }
    private void startHost(){
        if(running)return; running=true; host=new HostServer(8765); relay=new RelayServer(9000); host.start(); relay.start();
        new Thread(() -> { while(running){ RelayMessage m=queue.poll(); if(m!=null){ String answer=chatInternal(m.text); replies.put(m.id,answer); } else try{Thread.sleep(80);}catch(Exception ignored){} } }).start();
        status.setText("親機:8765 / 中継機:9000 起動中\n接続コード・トークン: Yomi0929");
        add("JARVIS: 親機と中継機を起動しました。\n子機URL: http://SC-56BのIPアドレス:9000/client");
    }
    private void chat(String text, boolean speak){ if(text==null||text.trim().isEmpty())return; input.setText(""); String answer=chatInternal(text); add("あなた: "+text+"\nJARVIS: "+answer); if(speak&&tts!=null)tts.speak(answer,TextToSpeech.QUEUE_FLUSH,null,"jarvis"); }
    private String chatInternal(String text){
        db.execSQL("INSERT INTO messages(role,content,created_at) VALUES(?,?,?)",new Object[]{"user",text,System.currentTimeMillis()});
        String answer=answer(text);
        db.execSQL("INSERT INTO messages(role,content,created_at) VALUES(?,?,?)",new Object[]{"assistant",answer,System.currentTimeMillis()});
        return answer;
    }
    private String answer(String text){
        if(text.contains("こんにちは")||text.contains("こんばんは")) return "こんにちは。JARVIS親機です。";
        if(text.contains("覚えて")||text.contains("記憶")) return "承知しました。SC-56B親機の端末内記憶へ保存しました。";
        return "承知しました。SC-56B親機のローカルAIコアで処理しました。";
    }
    private void add(String x){ runOnUiThread(() -> log.append(x+"\n\n")); }
    private void listen(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){add("音声認識を利用できません。");return;}
        if(recognizer!=null)recognizer.destroy(); recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));chat(a.get(0),true);}} public void onError(int e){} public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}});
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ja-JP");recognizer.startListening(i);
    }
    private abstract class HttpServer extends Thread {
        final int port; volatile boolean alive=true; HttpServer(int p){port=p;}
        public void run(){try(ServerSocket ss=new ServerSocket(port,30,InetAddress.getByName("0.0.0.0"))){while(alive){final Socket s=ss.accept();new Thread(()->handle(s)).start();}}catch(Exception e){add("サーバー"+port+"番の起動失敗: "+e.getMessage());}}
        private void handle(Socket s){try{HttpRequest r=read(s);HttpResponse response=route(r);write(s,response);}catch(Exception e){try{write(s,new HttpResponse(400,"{\"error\":\"bad request\"}"));}catch(Exception ignored){}}finally{try{s.close();}catch(Exception ignored){}}}
        abstract HttpResponse route(HttpRequest r);
    }
    private class HostServer extends HttpServer { HostServer(int p){super(p);} HttpResponse route(HttpRequest r){
        if(r.path.equals("/health"))return new HttpResponse(200,"{\"status\":\"ok\",\"mode\":\"android-apk-host-relay\",\"relay_port\":9000}");
        if(r.path.equals("/chat")&&r.method.equals("POST")){String text=jsonText(r.body);String ans=chatInternal(text);return new HttpResponse(200,"{\"status\":\"ok\",\"reply\":\""+json(ans)+"\"}");}
        return new HttpResponse(200,"{\"status\":\"ok\",\"message\":\"JARVIS Android親機\"}");
    }}
    private class RelayServer extends HttpServer { RelayServer(int p){super(p);} HttpResponse route(HttpRequest r){
        if(r.path.equals("/health"))return new HttpResponse(200,"{\"status\":\"ok\",\"service\":\"jarvis-relay\"}");
        if(r.path.equals("/client")||r.path.equals("/client/"))return new HttpResponse(200,clientHtml(),"text/html; charset=utf-8");
        if(r.path.equals("/client/message")&&r.method.equals("POST")){String token=jsonValue(r.body,"token"),room=jsonValue(r.body,"room"),text=jsonValue(r.body,"text");if(!CODE.equals(token)||!CODE.equals(room)||text.isEmpty())return new HttpResponse(401,"{\"error\":\"接続コードまたはトークンが違います\"}");String id=UUID.randomUUID().toString();queue.add(new RelayMessage(id,text));return new HttpResponse(200,"{\"ok\":true,\"message_id\":\""+id+"\"}");}
        if(r.path.startsWith("/client/reply")){Map<String,String> q=query(r.path);if(!CODE.equals(q.get("token"))||!CODE.equals(q.get("room")))return new HttpResponse(401,"{\"error\":\"認証失敗\"}");String ans=replies.remove(q.get("message_id"));return new HttpResponse(200,"{\"reply\":"+(ans==null?"null":"\""+json(ans)+"\"")+"}");}
        return new HttpResponse(404,"{\"error\":\"not found\"}");
    }}
    private String clientHtml(){return "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>JARVIS子機</title><style>body{font-family:sans-serif;max-width:700px;margin:2rem auto;padding:1rem;background:#10151c;color:white}input,textarea,button{font:inherit;padding:.7rem;margin:.3rem 0;box-sizing:border-box;width:100%}textarea{height:100px}#log{white-space:pre-wrap;background:#18222d;padding:1rem;min-height:180px}</style><h1>JARVIS子機</h1><input id='room' placeholder='接続コード'><input id='token' type='password' placeholder='中継トークン'><div id='log'></div><textarea id='q' placeholder='JARVISへの指示'></textarea><button onclick='send()'>送信</button><script>const $=x=>document.querySelector(x),log=$('#log');function add(x){log.textContent+=x+'\\n\\n'}async function send(){let room=$('#room').value,token=$('#token').value,text=$('#q').value.trim();if(!room||!token||!text)return;$('#q').value='';add('あなた: '+text);let r=await fetch('/client/message',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({room,token,text})}),j=await r.json();if(!j.message_id){add('接続失敗: '+(j.error||'不明'));return}let n=0;async function poll(){let x=await(await fetch('/client/reply?room='+encodeURIComponent(room)+'&token='+encodeURIComponent(token)+'&message_id='+encodeURIComponent(j.message_id))).json();if(x.reply!==null){add('JARVIS: '+x.reply)}else if(n++<180)setTimeout(poll,500)}poll()}</script>";}
    private static class RelayMessage{String id,text;RelayMessage(String i,String t){id=i;text=t;}}
    private static class HttpRequest{String method,path,body;HttpRequest(String m,String p,String b){method=m;path=p;body=b;}}
    private static class HttpResponse{int code;String body,type;HttpResponse(int c,String b){this(c,b,"application/json; charset=utf-8");}HttpResponse(int c,String b,String t){code=c;body=b;type=t;}}
    private HttpRequest read(Socket s)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String first=r.readLine();if(first==null)throw new IOException();String[] p=first.split(" ");int len=0;String line;while((line=r.readLine())!=null&&!line.isEmpty())if(line.toLowerCase().startsWith("content-length:"))len=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());char[] c=new char[len];if(len>0)r.read(c);return new HttpRequest(p[0],p[1],new String(c));}
    private void write(Socket s,HttpResponse x)throws Exception{byte[] b=x.body.getBytes(StandardCharsets.UTF_8);OutputStream o=s.getOutputStream();o.write(("HTTP/1.1 "+x.code+" OK\r\nContent-Type: "+x.type+"\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));o.write(b);o.flush();}
    private String jsonText(String b){return jsonValue(b,"text");}
    private String jsonValue(String b,String key){String marker="\""+key+"\"";int i=b.indexOf(marker);if(i<0)return "";i=b.indexOf(':',i+marker.length());if(i<0)return "";i=b.indexOf('"',i);if(i<0)return "";StringBuilder out=new StringBuilder();boolean esc=false;for(int j=i+1;j<b.length();j++){char c=b.charAt(j);if(esc){out.append(c);esc=false;}else if(c=='\\')esc=true;else if(c=='"')break;else out.append(c);}return out.toString();}
    private String json(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private Map<String,String> query(String path){Map<String,String> m=new HashMap<>();int q=path.indexOf('?');if(q<0)return m;for(String x:path.substring(q+1).split("&")){String[] p=x.split("=",2);if(p.length==2)try{m.put(URLDecoder.decode(p[0],"UTF-8"),URLDecoder.decode(p[1],"UTF-8"));}catch(Exception ignored){}}return m;}
    @Override protected void onDestroy(){running=false;if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
