import json, os, urllib.request
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
import uvicorn

PARENT=os.getenv('JARVIS_PARENT_URL','http://127.0.0.1:8765').rstrip('/')
TOKEN=os.getenv('JARVIS_API_TOKEN','')
PORT=int(os.getenv('JARVIS_CLIENT_PORT','8766'))
app=FastAPI(title='JARVIS Child Client',version='0.2.0')
class TextInput(BaseModel): text:str=Field(min_length=1,max_length=4000)
def call(path, method='GET', payload=None):
    data=json.dumps(payload).encode() if payload is not None else None
    req=urllib.request.Request(PARENT+path,data=data,method=method,headers={'Content-Type':'application/json','X-JARVIS-Token':TOKEN})
    try:
        with urllib.request.urlopen(req,timeout=180) as r:return json.loads(r.read())
    except Exception as e: raise HTTPException(502,f'親機へ接続できません: {e}')
@app.get('/')
def home(): return FileResponse(os.path.join(os.path.dirname(__file__),'web','index.html'))
@app.get('/health')
def health(): return call('/health')
@app.post('/chat')
def chat(body:TextInput): return call('/chat','POST',{'text':body.text})
@app.get('/history')
def history(): return call('/history')
if __name__=='__main__': uvicorn.run(app,host='127.0.0.1',port=PORT)
