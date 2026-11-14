#!/usr/bin/env python3
import argparse, base64, hashlib, json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cryptography.fernet import Fernet
from src.jarvis import db
from src.jarvis.config import APP_DIR

def key_from_password(password):
    digest=hashlib.sha256(password.encode()).digest()
    return base64.urlsafe_b64encode(digest)

def main():
    ap=argparse.ArgumentParser(); sub=ap.add_subparsers(dest='cmd',required=True)
    b=sub.add_parser('backup'); b.add_argument('output'); b.add_argument('--password',required=True)
    r=sub.add_parser('restore'); r.add_argument('input'); r.add_argument('--password',required=True)
    a=ap.parse_args(); path=Path(a.output if a.cmd=='backup' else a.input)
    if a.cmd=='backup':
        payload=json.dumps(db.export_data(),ensure_ascii=False).encode(); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(Fernet(key_from_password(a.password)).encrypt(payload)); print(f'暗号化バックアップを作成: {path}')
    else:
        data=Fernet(key_from_password(a.password)).decrypt(path.read_bytes()); obj=json.loads(data); db.initialize()
        for x in obj.get('notes',[]): db.add_note(x['content'])
        for x in obj.get('messages',[]): db.add_message(x['role'],x['content'])
        for x in obj.get('timers',[]):
            with db.connect() as conn: conn.execute('INSERT INTO timers(label,due_at,done,created_at) VALUES (?,?,?,?)',(x['label'],x['due_at'],x.get('done',0),x.get('created_at')))
        print(f'復元完了: {path}')
if __name__=='__main__': main()
