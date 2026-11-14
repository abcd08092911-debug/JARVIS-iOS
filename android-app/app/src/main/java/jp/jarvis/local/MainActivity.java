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
import org.json.*;

public class MainActivity extends Activity {
    private static final String CODE = "Yomi0929";
    private EditText input; private TextView log, status; private TextToSpeech tts; private SpeechRecognizer recognizer;
    private SQLiteDatabase db; private JarvisStore store; private Workspace workspace; private HostServer host; private RelayServer relay; private volatile boolean running;
    private boolean shiritori = false; private String lastWord = "";
    private final ConcurrentLinkedQueue<RelayMessage> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String,String> replies = new ConcurrentHashMap<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db=openOrCreateDatabase("jarvis.sqlite3",MODE_PRIVATE,null); store=new JarvisStore(db);
        workspace=new Workspace(new File(getFilesDir(),"JARVIS_WORKSPACE"));
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
        store.message("user",text);
        String answer=answer(text);
        if(answer!=null) store.message("assistant",answer);
        return answer;
    }
    private String answer(String text){
        String t=text.trim();
        try {
            if(t.matches("^(覚えて|記憶して|記憶に保存)[:： ]?.+")){String x=t.replaceFirst("^(覚えて|記憶して|記憶に保存)[:： ]?","");store.memory(x,"conversation_fact",4,"","chat");return "長期記憶に保存しました。";}
            String profile=JarvisStore.extractProfile(t); if(profile!=null){store.memory(profile,"profile",5,"","profile_extractor");return "プロフィール情報を長期記憶に保存しました。";}
            if(t.startsWith("メモ")||t.startsWith("メモして")){String x=t.replaceFirst("^メモ(して)?[:： ]?","");if(x.isEmpty())return "メモ内容が空です。";store.note(x);return "メモを保存しました。";}
            if(t.matches("^タイマー.*[0-9０-９]+.*")){java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9]+)\\s*(秒|分|時間)").matcher(t);if(m.find()){long n=Long.parseLong(m.group(1));if(m.group(2).equals("分"))n*=60;if(m.group(2).equals("時間"))n*=3600;String label=t.replaceFirst("^タイマー[:： ]?","");store.timer(label,n);return "タイマーを設定しました。";}}
            if(t.startsWith("記憶検索")||t.startsWith("検索")){String q=t.replaceFirst("^(記憶検索|検索)[:： ]?","");return "検索結果: "+store.memories(q).toString();}
            if(t.contains("こんにちは")||t.contains("こんばんは")) return "こんにちは。JARVIS親機です。";
        } catch(Exception e){return "機能処理に失敗しました: "+e.getMessage();}
        if(text.contains("しりとり")){ shiritori=true; lastWord=""; return "承知しました。しりとりを始めましょう。先に言ってください。"; }
        if(shiritori){
            if(text.contains("返事して")) return "はい。しりとり中です。次の言葉をどうぞ。";
            String word=text.replaceFirst("^.*?初めは","").trim().replaceAll("[。！？!?、,].*$","");
            if(word.isEmpty()) word=text.trim().replaceAll("[。！？!?、,].*$","");
            if(word.isEmpty()) return "言葉をもう一度お願いします。";
            if(word.endsWith("ん")){ shiritori=false; return "「"+word+"」は「ん」で終わるため、私の勝ちです。もう一度できます。"; }
            String last=word.substring(word.length()-1); String next=nextWord(last); lastWord=next;
            return "「"+word+"」ですね。では、私の番は「"+next+"」です。次は「"+next.substring(next.length()-1)+"」からどうぞ。";
        }
        return "ローカルAIモデルが搭載されていないため、生成回答は利用できません。記憶・メモ・タイマーなどの機能指示は利用できます。";
    }
    private String nextWord(String last){
        String[][] words={{"あ","あひる"},{"い","いぬ"},{"う","うさぎ"},{"え","えんぴつ"},{"お","おにぎり"},{"か","かめ"},{"き","きつね"},{"く","くるま"},{"け","けいき"},{"こ","こあら"},{"さ","さくら"},{"し","しか"},{"す","すいか"},{"せ","せみ"},{"そ","そら"},{"た","たぬき"},{"ち","ちず"},{"つ","つき"},{"て","てがみ"},{"と","とまと"},{"な","なす"},{"に","にわとり"},{"ぬ","ぬま"},{"ね","ねこ"},{"の","のみもの"},{"は","はな"},{"ひ","ひこうき"},{"ふ","ふね"},{"へ","へび"},{"ほ","ほし"},{"ま","まくら"},{"み","みかん"},{"む","むし"},{"め","めだか"},{"も","もも"},{"や","やま"},{"ゆ","ゆき"},{"よ","よる"},{"ら","らっぱ"},{"り","りす"},{"る","るすばん"},{"れ","れもん"},{"ろ","ろうそく"},{"わ","わに"}};
        for(String[] x:words) if(x[0].equals(last)) return x[1];
        return "りんご";
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
        if(r.path.startsWith("/client/api/") && !authorized(r)) return new HttpResponse(401,"{\"error\":\"認証失敗\"}");
        try {
            if(r.path.equals("/client/api/memory")&&r.method.equals("POST")){long id=store.memory(jsonValue(r.body,"content"),jsonValue(r.body,"kind"),3,jsonValue(r.body,"tags"),"client");return new HttpResponse(200,"{\"saved\":true,\"id\":"+id+"}");}
            if(r.path.equals("/client/api/notes")&&r.method.equals("POST")){long id=store.note(jsonValue(r.body,"content"));return new HttpResponse(200,"{\"saved\":true,\"id\":"+id+"}");}
            if(r.path.equals("/client/api/timers")&&r.method.equals("POST")){long id=store.timer(jsonValue(r.body,"label"),Long.parseLong(jsonValue(r.body,"seconds")));return new HttpResponse(200,"{\"saved\":true,\"id\":"+id+"}");}
            if(r.path.equals("/client/api/history")) return new HttpResponse(200,"{\"messages\":"+store.history(50)+"}");
            if(r.path.equals("/client/api/memory")) {String q=jsonValue(r.body,"q");return new HttpResponse(200,"{\"memories\":"+store.memories(q)+"}");}
            if(r.path.equals("/client/api/notes/search")) return new HttpResponse(200,"{\"results\":"+store.notes(jsonValue(r.body,"q"))+"}");
            if(r.path.equals("/client/api/timers")) return new HttpResponse(200,"{\"timers\":"+store.timers()+"}");
            if(r.path.equals("/client/api/files/search")){return new HttpResponse(200,"{\"results\":"+workspace.search(jsonValue(r.body,"q"))+"}");}
            if(r.path.equals("/client/api/files/create")||r.path.equals("/client/api/code/create")){String kind=r.path.contains("code")?"code":jsonValue(r.body,"kind");String result=workspace.write(jsonValue(r.body,"path"),jsonValue(r.body,"content"),kind);return new HttpResponse(200,"{\"saved\":true,\"requires_run_approval\":"+(kind.equals("code")?"true":"false")+",\"file\":"+result+"}");}
            if(r.path.equals("/client/api/backup")){File f=new File(getExternalFilesDir(null),"jarvis-backup-"+System.currentTimeMillis()+".jvb");EncryptedBackup.save(f,store.exportAll(),CODE);return new HttpResponse(200,"{\"saved\":true,\"path\":\""+json(f.getAbsolutePath())+"\"}");}
        } catch(Exception e){return new HttpResponse(400,"{\"error\":\""+json(e.getMessage()==null?"処理に失敗しました":e.getMessage())+"\"}");}
        if(r.path.equals("/client/message")&&r.method.equals("POST")){String token=jsonValue(r.body,"token"),room=jsonValue(r.body,"room"),text=jsonValue(r.body,"text");if(!CODE.equals(token)||!CODE.equals(room)||text.isEmpty())return new HttpResponse(401,"{\"error\":\"接続コードまたはトークンが違います\"}");String id=UUID.randomUUID().toString();queue.add(new RelayMessage(id,text));return new HttpResponse(200,"{\"ok\":true,\"message_id\":\""+id+"\"}");}
        if(r.path.startsWith("/client/reply")){Map<String,String> q=query(r.path);if(!CODE.equals(q.get("token"))||!CODE.equals(q.get("room")))return new HttpResponse(401,"{\"error\":\"認証失敗\"}");String ans=replies.remove(q.get("message_id"));return new HttpResponse(200,"{\"reply\":"+(ans==null?"null":"\""+json(ans)+"\"")+"}");}
        return new HttpResponse(404,"{\"error\":\"not found\"}");
    }}
    private boolean authorized(HttpRequest r){String token=jsonValue(r.body,"token");if(CODE.equals(token))return true;Map<String,String> q=query(r.path);return CODE.equals(q.get("token"));}
    private String clientHtml(){return "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>JARVIS子機</title><style>body{font-family:sans-serif;max-width:700px;margin:2rem auto;padding:1rem;background:#10151c;color:white}input,textarea,button{font:inherit;padding:.7rem;margin:.3rem 0;box-sizing:border-box;width:100%}textarea{height:100px}#log{white-space:pre-wrap;background:#18222d;padding:1rem;min-height:180px}</style><h1>JARVIS子機</h1><input id='room' placeholder='接続コード'><input id='token' type='password' placeholder='中継トークン'><div id='log'></div><textarea id='q' placeholder='JARVISへの指示'></textarea><button onclick='send()'>送信</button><script>const $=x=>document.querySelector(x),log=$('#log');function add(x){log.textContent+=x+'\\n\\n'}async function send(){let room=$('#room').value,token=$('#token').value,text=$('#q').value.trim();if(!room||!token||!text)return;$('#q').value='';add('あなた: '+text);let r=await fetch('/client/message',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({room,token,text})}),j=await r.json();if(!j.message_id){add('接続失敗: '+(j.error||'不明'));return}let n=0;async function poll(){let x=await(await fetch('/client/reply?room='+encodeURIComponent(room)+'&token='+encodeURIComponent(token)+'&message_id='+encodeURIComponent(j.message_id))).json();if(x.reply!==null){add('JARVIS: '+x.reply)}else if(n++<180)setTimeout(poll,500)}poll()}</script>";}
    private static class RelayMessage{String id,text;RelayMessage(String i,String t){id=i;text=t;}}
    private static class HttpRequest{String method,path,body;HttpRequest(String m,String p,String b){method=m;path=p;body=b;}}
    private static class HttpResponse{int code;String body,type;HttpResponse(int c,String b){this(c,b,"application/json; charset=utf-8");}HttpResponse(int c,String b,String t){code=c;body=b;type=t;}}
    private HttpRequest read(Socket s)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String first=r.readLine();if(first==null)throw new IOException();String[] p=first.split(" ");int len=0;String line;while((line=r.readLine())!=null&&!line.isEmpty())if(line.toLowerCase().startsWith("content-length:"))len=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());char[] c=new char[len];if(len>0)r.read(c);return new HttpRequest(p[0],p[1],new String(c));}
    private void write(Socket s,HttpResponse x)throws Exception{byte[] b=x.body.getBytes(StandardCharsets.UTF_8);OutputStream o=s.getOutputStream();o.write(("HTTP/1.1 "+x.code+" OK\r\nContent-Type: "+x.type+"\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));o.write(b);o.flush();}
    private String jsonText(String b){return jsonValue(b,"text");}
    private String jsonValue(String b,String key){String marker="\""+key+"\"";int i=b.indexOf(marker);if(i<0)return "";i=b.indexOf(':',i+marker.length());if(i<0)return "";i++;while(i<b.length()&&Character.isWhitespace(b.charAt(i)))i++;if(i<b.length()&&b.charAt(i)=='"'){StringBuilder out=new StringBuilder();boolean esc=false;for(int j=i+1;j<b.length();j++){char c=b.charAt(j);if(esc){out.append(c);esc=false;}else if(c=='\\')esc=true;else if(c=='"')break;else out.append(c);}return out.toString();}int j=i;while(j<b.length()&&",}".indexOf(b.charAt(j))<0)j++;return b.substring(i,j).trim();}
    private String json(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private Map<String,String> query(String path){Map<String,String> m=new HashMap<>();int q=path.indexOf('?');if(q<0)return m;for(String x:path.substring(q+1).split("&")){String[] p=x.split("=",2);if(p.length==2)try{m.put(URLDecoder.decode(p[0],"UTF-8"),URLDecoder.decode(p[1],"UTF-8"));}catch(Exception ignored){}}return m;}
    @Override protected void onDestroy(){running=false;if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
