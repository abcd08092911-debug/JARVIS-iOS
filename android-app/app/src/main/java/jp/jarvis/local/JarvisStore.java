package jp.jarvis.local;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class JarvisStore {
    private final SQLiteDatabase db;
    JarvisStore(SQLiteDatabase database) { db = database; init(); }
    private void init() {
        db.execSQL("CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT,role TEXT NOT NULL,content TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT,content TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS timers(id INTEGER PRIMARY KEY AUTOINCREMENT,label TEXT NOT NULL,due_at INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS memories(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,content TEXT NOT NULL,tags TEXT NOT NULL DEFAULT '',importance INTEGER NOT NULL DEFAULT 3,source TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS conversation_summaries(id INTEGER PRIMARY KEY AUTOINCREMENT,first_message_id INTEGER,last_message_id INTEGER,summary TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories ON memories(active,importance,updated_at)");
    }
    synchronized long message(String role,String content){
        ContentValues v=new ContentValues(); v.put("role",role); v.put("content",content); v.put("created_at",System.currentTimeMillis());
        long id=db.insert("messages",null,v); maybeSummarize(); return id;
    }
    private void maybeSummarize(){
        Cursor c=db.rawQuery("SELECT COUNT(*) FROM messages",null); long count=0; try{if(c.moveToFirst())count=c.getLong(0);}finally{c.close();}
        if(count==0||count%20!=0)return;
        Cursor x=db.rawQuery("SELECT id,role,content FROM messages ORDER BY id DESC LIMIT 20",null); StringBuilder s=new StringBuilder("抽出型要約（LLM未配置・推測なし）: "); long last=0,first=0; int n=0;
        try{while(x.moveToNext()){if(last==0)last=x.getLong(0);first=x.getLong(0);String text=x.getString(1)+": "+x.getString(2).replaceAll("\\s+"," ");if(text.length()>180)text=text.substring(0,180)+"…";if(n++>0)s.append(" / ");s.append(text);}}finally{x.close();}
        ContentValues v=new ContentValues();v.put("first_message_id",first);v.put("last_message_id",last);v.put("summary",s.toString());v.put("created_at",System.currentTimeMillis());db.insert("conversation_summaries",null,v);
    }
    long note(String content){ContentValues v=new ContentValues();v.put("content",content);v.put("created_at",System.currentTimeMillis());return db.insert("notes",null,v);}
    long memory(String content,String kind,int importance,String tags,String source){content=content.trim();if(content.length()==0)return -1;importance=Math.max(1,Math.min(5,importance));Cursor c=db.rawQuery("SELECT id FROM memories WHERE content=? AND active=1",new String[]{content});try{if(c.moveToFirst()){long id=c.getLong(0);ContentValues v=new ContentValues();v.put("importance",importance);v.put("tags",tags);v.put("updated_at",System.currentTimeMillis());db.update("memories",v,"id=?",new String[]{Long.toString(id)});return id;}}finally{c.close();}ContentValues v=new ContentValues();v.put("kind",kind);v.put("content",content);v.put("tags",tags);v.put("importance",importance);v.put("source",source);v.put("active",1);v.put("created_at",System.currentTimeMillis());v.put("updated_at",System.currentTimeMillis());return db.insert("memories",null,v);}
    long timer(String label,long seconds){ContentValues v=new ContentValues();v.put("label",label);v.put("due_at",System.currentTimeMillis()+Math.max(1,Math.min(seconds,31536000))*1000L);v.put("done",0);v.put("created_at",System.currentTimeMillis());return db.insert("timers",null,v);}
    JSONArray rows(String table,String where,String[] args,String order,int limit){JSONArray a=new JSONArray();Cursor c=db.query(table,null,where,args,null,null,order,Integer.toString(Math.max(1,Math.min(limit,100000))));try{while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++){String n=c.getColumnName(i);int t=c.getType(i);try{if(t==Cursor.FIELD_TYPE_INTEGER)o.put(n,c.getLong(i));else o.put(n,c.getString(i));}catch(Exception ignored){}}a.put(o);}}finally{c.close();}return a;}
    JSONArray history(int limit){return rows("messages",null,null,"id DESC",limit);}
    JSONArray summaries(){return rows("conversation_summaries",null,null,"id DESC",50);}
    JSONArray notes(String q){return rows("notes","content LIKE ?",new String[]{"%"+(q==null?"":q)+"%"},"id DESC",50);}
    JSONArray memories(String q){return rows("memories","active=1 AND (content LIKE ? OR tags LIKE ?)",new String[]{"%"+(q==null?"":q)+"%","%"+(q==null?"":q)+"%"},"importance DESC,updated_at DESC",50);}
    JSONArray timers(){return rows("timers","done=0",null,"due_at ASC",100);}
    void completeTimer(long id){ContentValues v=new ContentValues();v.put("done",1);db.update("timers",v,"id=?",new String[]{Long.toString(id)});}
    JSONObject exportAll() throws Exception{JSONObject o=new JSONObject();o.put("_format","jarvis-backup-v2-android");o.put("_created_at",System.currentTimeMillis());o.put("messages",rows("messages",null,null,"id ASC",100000));o.put("notes",rows("notes",null,null,"id ASC",100000));o.put("memories",rows("memories",null,null,"id ASC",100000));o.put("timers",rows("timers",null,null,"id ASC",100000));o.put("conversation_summaries",rows("conversation_summaries",null,null,"id ASC",100000));return o;}
    void importAll(JSONObject o)throws Exception{db.beginTransaction();try{JSONArray a=o.optJSONArray("messages");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);ContentValues v=new ContentValues();v.put("role",x.optString("role"));v.put("content",x.optString("content"));v.put("created_at",x.optLong("created_at",System.currentTimeMillis()));db.insert("messages",null,v);}a=o.optJSONArray("notes");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);ContentValues v=new ContentValues();v.put("content",x.optString("content"));v.put("created_at",x.optLong("created_at",System.currentTimeMillis()));db.insert("notes",null,v);}a=o.optJSONArray("memories");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);ContentValues v=new ContentValues();v.put("kind",x.optString("kind","fact"));v.put("content",x.optString("content"));v.put("tags",x.optString("tags"));v.put("importance",x.optInt("importance",3));v.put("source",x.optString("source","backup"));v.put("active",x.optInt("active",1));v.put("created_at",x.optLong("created_at",System.currentTimeMillis()));v.put("updated_at",x.optLong("updated_at",System.currentTimeMillis()));db.insert("memories",null,v);}a=o.optJSONArray("timers");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);ContentValues v=new ContentValues();v.put("label",x.optString("label"));v.put("due_at",x.optLong("due_at"));v.put("done",x.optInt("done",0));v.put("created_at",x.optLong("created_at",System.currentTimeMillis()));db.insert("timers",null,v);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    static String extractProfile(String text){String[] labels={"名前は","好きなのは","好きなものは","目標は"};for(String l:labels){int i=text.indexOf(l);if(i>=0){String x=text.substring(i+l.length()).replaceFirst("^[：: ]+","").split("[。！？!?\\n]",2)[0].trim();if(!x.isEmpty())return x;}}return null;}
}
