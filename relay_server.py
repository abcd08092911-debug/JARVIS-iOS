"""JARVIS独自の軽量中継サーバー。外部VPNアプリや特定クラウドに依存しない。"""
from collections import defaultdict
from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field
from secrets import compare_digest
import os, time, uuid

app=FastAPI(title='JARVIS Relay',version='0.1.0')
ROOM_TOKEN=os.getenv('JARVIS_RELAY_TOKEN','')
rooms=defaultdict(lambda:{'host':False,'host_queue':[],'replies':{}})

def check(room:str, token:str):
    if not ROOM_TOKEN or not compare_digest(token,ROOM_TOKEN): raise HTTPException(401,'中継トークンが正しくありません')
    if not room or len(room)>100: raise HTTPException(400,'roomが不正です')
    return rooms[room]
class Message(BaseModel): room:str; token:str; text:str=Field(min_length=1,max_length=4000)
class Reply(BaseModel): room:str; token:str; message_id:str; reply:str=Field(max_length=200000)
class Register(BaseModel): room:str; token:str
@app.get('/health')
def health(): return {'status':'ok','service':'jarvis-relay'}
@app.get('/',response_class=HTMLResponse)
def home(): return '<h1>JARVIS Relay</h1><p>JARVIS専用中継サーバー稼働中</p>'
@app.get('/client',response_class=HTMLResponse)
def client_page():
    return '''<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JARVIS子機</title><style>body{font-family:system-ui;max-width:700px;margin:2rem auto;padding:0 1rem}textarea,input,button{font:inherit;padding:.6rem;margin:.2rem 0}textarea{width:100%;height:100px;box-sizing:border-box}#log{white-space:pre-wrap;background:#eef;padding:1rem;min-height:160px}</style><h1>JARVIS子機</h1><input id="room" placeholder="接続コード"><input id="token" type="password" placeholder="中継トークン"><div id="log"></div><textarea id="q" placeholder="JARVISへの指示"></textarea><button onclick="send()">送信</button><script>const log=document.querySelector('#log');function add(x){log.textContent+=x+'\\n\\n'}async function send(){const room=document.querySelector('#room').value,token=document.querySelector('#token').value,text=document.querySelector('#q').value.trim();if(!room||!token||!text)return;document.querySelector('#q').value='';add('あなた: '+text);const r=await fetch('/client/message',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({room,token,text})});const j=await r.json();let n=0;const poll=async()=>{const x=await (await fetch('/client/reply?room='+encodeURIComponent(room)+'&token='+encodeURIComponent(token)+'&message_id='+j.message_id)).json();if(x.reply!==null){add('JARVIS: '+x.reply)}else if(n++<180)setTimeout(poll,1000)};poll()}</script>'''
@app.post('/register')
def register(x:Register): check(x.room,x.token)['host']=True; return {'ok':True,'room':x.room}
@app.post('/client/message')
def client_message(x:Message):
    r=check(x.room,x.token); mid=uuid.uuid4().hex; r['host_queue'].append({'id':mid,'text':x.text,'created_at':time.time()}); return {'ok':True,'message_id':mid}
@app.get('/host/poll')
def host_poll(room:str,token:str):
    r=check(room,token); items=r['host_queue']; r['host_queue']=[]; return {'messages':items}
@app.post('/host/reply')
def host_reply(x:Reply): check(x.room,x.token)['replies'][x.message_id]=x.reply; return {'ok':True}
@app.get('/client/reply')
def client_reply(room:str,token:str,message_id:str):
    r=check(room,token); return {'reply':r['replies'].pop(message_id,None)}
