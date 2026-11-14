from pathlib import Path
from datetime import datetime, timedelta
from . import db

def create_timer(label, seconds):
    due=(datetime.now()+timedelta(seconds=max(1,min(seconds,31536000)))).isoformat(timespec="seconds")
    return {"id":db.add_timer(label,due),"label":label,"due_at":due}

def search_files(root, query, limit=50):
    base=Path(root).expanduser().resolve()
    if not base.exists() or not base.is_dir(): raise ValueError("検索対象フォルダがありません")
    out=[]
    for p in base.rglob("*"):
        if len(out)>=limit: break
        if query.lower() in p.name.lower(): out.append(str(p))
    return out
