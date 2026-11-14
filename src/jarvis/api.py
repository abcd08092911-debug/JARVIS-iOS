from fastapi import FastAPI, HTTPException, Header
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from pathlib import Path
from . import db
from .persona import SYSTEM_PROMPT_JA
from .config import API_TOKEN, BIND_HOST, MODEL_PATH, MAX_CONTEXT_MESSAGES
from .llm import LocalLLM
from .tools import create_timer, search_files
from .capabilities import capabilities, write_file, list_connectors, save_connector
app=FastAPI(title='JARVIS Local Core',version='0.2.0'); db.initialize(); llm=LocalLLM()
class TextInput(BaseModel): text:str=Field(min_length=1,max_length=4000)
class TimerInput(BaseModel): label:str=Field(min_length=1,max_length=200); seconds:int=Field(gt=0,le=31536000)
class FileSearchInput(BaseModel): root:str; query:str=Field(min_length=1,max_length=200)
class FileWriteInput(BaseModel): path:str=Field(min_length=1,max_length=300); content:str=Field(max_length=2000000); kind:str='text'
class ConnectorInput(BaseModel): name:str=Field(min_length=1,max_length=50); kind:str=Field(min_length=1,max_length=50); base_url:str=''; enabled:bool=False
def auth(token):
    if BIND_HOST not in ('127.0.0.1','localhost','::1'):
        if not API_TOKEN or token!=API_TOKEN: raise HTTPException(401,'有効なX-JARVIS-Tokenが必要です')
@app.get('/')
def home(): return FileResponse(Path(__file__).resolve().parents[2]/'web'/'index.html')
@app.get('/manifest.json')
def manifest(): return FileResponse(Path(__file__).resolve().parents[2]/'web'/'manifest.json', media_type='application/manifest+json')
@app.get('/sw.js')
def service_worker(): return FileResponse(Path(__file__).resolve().parents[2]/'web'/'sw.js', media_type='application/javascript')
@app.get('/health')
def health(): return {'status':'ok','mode':'local-first','llm_connected':llm.available,'model':str(MODEL_PATH),'bind':BIND_HOST}
@app.get('/persona')
def persona(): return {'name':'JARVIS','style':'丁寧で落ち着いた執事','system_prompt':SYSTEM_PROMPT_JA}
@app.get('/capabilities')
def get_capabilities(x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return {'capabilities':capabilities()}
@app.get('/connectors')
def connectors(x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return {'connectors':list_connectors()}
@app.post('/connectors')
def connector(body:ConnectorInput,x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return save_connector(body.name,body.kind,body.base_url,body.enabled)
@app.post('/notes')
def create_note(body:TextInput, x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return {'id':db.add_note(body.text),'saved':True}
@app.get('/notes/search')
def find_notes(q:str,x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return {'results':db.search_notes(q)}
@app.get('/history')
def history(limit:int=30,x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return {'messages':db.recent_messages(limit)}
@app.post('/timers')
def timer(body:TimerInput,x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return create_timer(body.label,body.seconds)
@app.get('/timers')
def timers(x_jarvis_token:str|None=Header(default=None)): auth(x_jarvis_token); return {'timers':db.list_timers()}
@app.post('/files/search')
def files(body:FileSearchInput,x_jarvis_token:str|None=Header(default=None)):
    auth(x_jarvis_token)
    try:return {'results':search_files(body.root,body.query)}
    except ValueError as e: raise HTTPException(400,str(e))
@app.post('/files/create')
def create_file(body:FileWriteInput,x_jarvis_token:str|None=Header(default=None)):
    auth(x_jarvis_token)
    try: return {'saved':True, **write_file(body.path,body.content,body.kind)}
    except ValueError as e: raise HTTPException(400,str(e))
@app.post('/code/create')
def create_code(body:FileWriteInput,x_jarvis_token:str|None=Header(default=None)):
    auth(x_jarvis_token)
    if body.kind == 'text': body.kind='code'
    try: return {'saved':True, 'requires_run_approval':True, **write_file(body.path,body.content,body.kind)}
    except ValueError as e: raise HTTPException(400,str(e))
@app.post('/chat')
def chat(body:TextInput,x_jarvis_token:str|None=Header(default=None)):
    auth(x_jarvis_token); db.add_message('user',body.text); notes=db.search_notes(body.text[:80]); context='\n'.join(f"- {n['content']}" for n in notes)
    prompt=SYSTEM_PROMPT_JA+'\n\n関連する保存メモ:\n'+context+'\n\n会話:\n'+ '\n'.join(f"{m['role']}: {m['content']}" for m in db.recent_messages(MAX_CONTEXT_MESSAGES)) + '\nassistant:'
    answer=llm.generate(prompt)
    if answer: db.add_message('assistant',answer); return {'status':'ok','mode':'offline-local','reply':answer}
    return {'status':'llm_unavailable','mode':'core-only','message':'ローカルAIモデルを利用できません。モデル配置とllama.cpp設定をご確認ください。','detail':llm.last_error}
