from pathlib import Path
import sqlite3
import re
from collections import Counter
from .config import APP_DIR, DB_PATH

def connect():
    APP_DIR.mkdir(parents=True, exist_ok=True)
    conn=sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory=sqlite3.Row
    return conn

def initialize():
    with connect() as conn:
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS timers (id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT NOT NULL, due_at TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS memories (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL DEFAULT 'fact', content TEXT NOT NULL, tags TEXT NOT NULL DEFAULT '', importance INTEGER NOT NULL DEFAULT 3, source TEXT NOT NULL DEFAULT 'user', active INTEGER NOT NULL DEFAULT 1, supersedes_id INTEGER, last_accessed TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS conversation_summaries (id INTEGER PRIMARY KEY AUTOINCREMENT, first_message_id INTEGER, last_message_id INTEGER, summary TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE INDEX IF NOT EXISTS idx_memories_importance ON memories(importance DESC, updated_at DESC);
        ''')
        cols={r['name'] for r in conn.execute('PRAGMA table_info(memories)').fetchall()}
        if 'active' not in cols: conn.execute('ALTER TABLE memories ADD COLUMN active INTEGER NOT NULL DEFAULT 1')
        if 'supersedes_id' not in cols: conn.execute('ALTER TABLE memories ADD COLUMN supersedes_id INTEGER')
        try:
            conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts5(content, tags, content='memories', content_rowid='id')")
            conn.execute("INSERT INTO memories_fts(rowid,content,tags) SELECT id,content,tags FROM memories WHERE id NOT IN (SELECT rowid FROM memories_fts)")
        except sqlite3.OperationalError:
            pass

def add_message(role, content):
    with connect() as conn:
        cur=conn.execute("INSERT INTO messages(role,content) VALUES (?,?)",(role,content)); return cur.lastrowid

def recent_messages(limit=30):
    with connect() as conn: rows=conn.execute("SELECT id,role,content,created_at FROM messages ORDER BY id DESC LIMIT ?",(max(1,min(limit,200)),)).fetchall()
    return [dict(r) for r in reversed(rows)]

def message_count():
    with connect() as conn: return int(conn.execute('SELECT count(*) FROM messages').fetchone()[0])

def add_note(content):
    with connect() as conn:
        cur=conn.execute("INSERT INTO notes(content) VALUES (?)",(content,)); return cur.lastrowid

def search_notes(keyword, limit=20):
    with connect() as conn: rows=conn.execute("SELECT id,content,created_at FROM notes WHERE content LIKE ? ORDER BY id DESC LIMIT ?",(f"%{keyword}%",limit)).fetchall()
    return [dict(r) for r in rows]

def _terms(text): return [x.lower() for x in re.findall(r'[ぁ-んァ-ヶ一-龯A-Za-z0-9]{2,}', text or '')]
def _similarity(a,b):
    aa=Counter(_terms(a)); bb=Counter(_terms(b)); common=set(aa)&set(bb)
    return sum(min(aa[x],bb[x]) for x in common)/max(1,sum(aa.values())) if common else 0.0

def add_memory(content, kind='fact', importance=3, tags='', source='user'):
    content=content.strip(); importance=max(1,min(int(importance),5))
    if not content: raise ValueError('記憶内容が空です')
    with connect() as conn:
        old=conn.execute("SELECT id FROM memories WHERE content=? AND active=1",(content,)).fetchone()
        if old:
            conn.execute("UPDATE memories SET importance=max(importance,?),tags=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",(importance,tags,old['id'])); return old['id']
        previous=conn.execute("SELECT id,content FROM memories WHERE active=1 AND kind=? ORDER BY importance DESC,updated_at DESC LIMIT 100",(kind,)).fetchall()
        supersedes=None
        for p in previous:
            if _similarity(content,p['content']) >= 0.55 and content != p['content']:
                supersedes=p['id']; conn.execute("UPDATE memories SET active=0,updated_at=CURRENT_TIMESTAMP WHERE id=?",(p['id'],)); break
        cur=conn.execute("INSERT INTO memories(kind,content,tags,importance,source,supersedes_id) VALUES (?,?,?,?,?,?)",(kind,content,tags,importance,source,supersedes)); mid=cur.lastrowid
        try: conn.execute("INSERT INTO memories_fts(rowid,content,tags) VALUES (?,?,?)",(mid,content,tags))
        except sqlite3.OperationalError: pass
        return mid

def search_memories(query, limit=12):
    query=(query or '').strip(); limit=max(1,min(limit,50))
    with connect() as conn:
        rows=[]
        if query:
            try:
                terms=' OR '.join('"'+x.replace('"','')+'"' for x in query.split() if x)
                rows=conn.execute("SELECT m.id,m.kind,m.content,m.tags,m.importance,m.created_at,m.updated_at FROM memories_fts f JOIN memories m ON m.id=f.rowid WHERE memories_fts MATCH ? AND m.active=1 ORDER BY m.importance DESC,m.updated_at DESC LIMIT ?",(terms,limit)).fetchall()
            except sqlite3.OperationalError: pass
            if not rows: rows=conn.execute("SELECT id,kind,content,tags,importance,created_at,updated_at FROM memories WHERE active=1 AND (content LIKE ? OR tags LIKE ?) ORDER BY importance DESC,updated_at DESC LIMIT ?",(f"%{query}%",f"%{query}%",limit)).fetchall()
            if not rows:
                candidates=conn.execute("SELECT id,kind,content,tags,importance,created_at,updated_at FROM memories WHERE active=1").fetchall()
                ranked=sorted(candidates,key=lambda r:(_similarity(query,r['content']+' '+r['tags']),r['importance']),reverse=True)
                rows=[r for r in ranked if _similarity(query,r['content']+' '+r['tags'])>0][:limit]
        else:
            rows=conn.execute("SELECT id,kind,content,tags,importance,created_at,updated_at FROM memories WHERE active=1 ORDER BY importance DESC,updated_at DESC LIMIT ?",(limit,)).fetchall()
        ids=[r['id'] for r in rows]
        if ids: conn.executemany("UPDATE memories SET last_accessed=CURRENT_TIMESTAMP WHERE id=?",[(i,) for i in ids])
    return [dict(r) for r in rows]

def list_memories(limit=100): return search_memories('',limit)
def profile_facts(text):
    facts=[]
    patterns=[('profile_name',r'(?:名前は|名乗りは)\s*([^。\n]{1,40})'),('preference',r'(?:好きなのは|好きなものは|好みは)\s*([^。\n]{1,100})'),('preference',r'(?:嫌いなのは|嫌いなものは)\s*([^。\n]{1,100})'),('goal',r'(?:目標は|目的は)\s*([^。\n]{1,120})')]
    for kind,pat in patterns:
        for m in re.finditer(pat,text): facts.append((kind,m.group(1).strip()))
    return facts

def summary_exists(last_message_id):
    with connect() as conn: return conn.execute('SELECT 1 FROM conversation_summaries WHERE last_message_id=?',(last_message_id,)).fetchone() is not None

def add_summary(first_message_id,last_message_id,summary):
    with connect() as conn: conn.execute('INSERT INTO conversation_summaries(first_message_id,last_message_id,summary) VALUES (?,?,?)',(first_message_id,last_message_id,summary))

def add_timer(label,due_at):
    with connect() as conn:
        cur=conn.execute("INSERT INTO timers(label,due_at) VALUES (?,?)",(label,due_at)); return cur.lastrowid

def list_timers(include_done=False):
    sql="SELECT id,label,due_at,done,created_at FROM timers"+("" if include_done else " WHERE done=0")+" ORDER BY due_at"
    with connect() as conn: rows=conn.execute(sql).fetchall()
    return [dict(r) for r in rows]

def complete_timer(timer_id):
    with connect() as conn:
        cur=conn.execute("UPDATE timers SET done=1 WHERE id=?",(timer_id,)); return cur.rowcount==1

def export_data():
    with connect() as conn:
        return {'messages':[dict(r) for r in conn.execute('SELECT role,content,created_at FROM messages ORDER BY id')],'notes':[dict(r) for r in conn.execute('SELECT content,created_at FROM notes ORDER BY id')],'memories':[dict(r) for r in conn.execute('SELECT kind,content,tags,importance,source,active,supersedes_id,created_at,updated_at FROM memories ORDER BY id')],'timers':[dict(r) for r in conn.execute('SELECT label,due_at,done,created_at FROM timers ORDER BY id')]}

initialize()
