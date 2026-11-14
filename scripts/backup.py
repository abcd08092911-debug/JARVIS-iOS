#!/usr/bin/env python3
import argparse, base64, hashlib, json, sys
from pathlib import Path
from datetime import datetime, timezone
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cryptography.fernet import Fernet
from src.jarvis import db

def key_from_password(password):
    return base64.urlsafe_b64encode(hashlib.sha256(password.encode('utf-8')).digest())

def backup(output, password):
    payload=db.export_data()
    payload['_format']='jarvis-backup-v2'
    payload['_created_at']=datetime.now(timezone.utc).isoformat()
    raw=json.dumps(payload,ensure_ascii=False).encode('utf-8')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(Fernet(key_from_password(password)).encrypt(raw))
    print(f'暗号化バックアップを作成: {output}')
    print(f"会話 {len(payload.get('messages',[]))}件 / 長期記憶 {len(payload.get('memories',[]))}件 / メモ {len(payload.get('notes',[]))}件 / タイマー {len(payload.get('timers',[]))}件")

def restore(input_path, password):
    obj=json.loads(Fernet(key_from_password(password)).decrypt(input_path.read_bytes()))
    db.initialize()
    with db.connect() as conn:
        for x in obj.get('messages',[]):
            conn.execute('INSERT INTO messages(role,content,created_at) VALUES (?,?,?)',(x['role'],x['content'],x.get('created_at')))
        for x in obj.get('notes',[]):
            conn.execute('INSERT INTO notes(content,created_at) VALUES (?,?)',(x['content'],x.get('created_at')))
        for x in obj.get('memories',[]):
            cur=conn.execute('INSERT INTO memories(kind,content,tags,importance,source,active,supersedes_id,last_accessed,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)',(x.get('kind','fact'),x['content'],x.get('tags',''),x.get('importance',3),x.get('source','backup'),x.get('active',1),x.get('supersedes_id'),x.get('last_accessed'),x.get('created_at'),x.get('updated_at')))
            try: conn.execute('INSERT INTO memories_fts(rowid,content,tags) VALUES (?,?,?)',(cur.lastrowid,x['content'],x.get('tags','')))
            except Exception: pass
        for x in obj.get('conversation_summaries',[]):
            conn.execute('INSERT INTO conversation_summaries(first_message_id,last_message_id,summary,created_at) VALUES (?,?,?,?)',(x.get('first_message_id'),x.get('last_message_id'),x['summary'],x.get('created_at')))
        for x in obj.get('timers',[]):
            conn.execute('INSERT INTO timers(label,due_at,done,created_at) VALUES (?,?,?,?)',(x['label'],x['due_at'],x.get('done',0),x.get('created_at')))
    print(f'復元完了: {input_path}')
    print(f"会話 {len(obj.get('messages',[]))}件 / 長期記憶 {len(obj.get('memories',[]))}件 / メモ {len(obj.get('notes',[]))}件 / タイマー {len(obj.get('timers',[]))}件")

def main():
    ap=argparse.ArgumentParser(description='JARVISの会話・記憶を暗号化バックアップ/復元します')
    sub=ap.add_subparsers(dest='cmd',required=True)
    b=sub.add_parser('backup'); b.add_argument('output'); b.add_argument('--password',required=True)
    r=sub.add_parser('restore'); r.add_argument('input'); r.add_argument('--password',required=True)
    a=ap.parse_args()
    if a.cmd=='backup': backup(Path(a.output),a.password)
    else: restore(Path(a.input),a.password)
if __name__=='__main__': main()
