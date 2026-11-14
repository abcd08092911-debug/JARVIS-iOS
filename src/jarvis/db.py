from pathlib import Path
import sqlite3
from .config import APP_DIR, DB_PATH

def connect():
    APP_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    return conn

def initialize():
    with connect() as conn:
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS timers (id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT NOT NULL, due_at TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        ''')

def add_message(role, content):
    with connect() as conn:
        cur = conn.execute("INSERT INTO messages(role,content) VALUES (?,?)", (role, content)); return cur.lastrowid

def recent_messages(limit=30):
    with connect() as conn:
        rows=conn.execute("SELECT id,role,content,created_at FROM messages ORDER BY id DESC LIMIT ?", (max(1,min(limit,100)),)).fetchall()
    return [dict(r) for r in reversed(rows)]

def add_note(content):
    with connect() as conn:
        cur=conn.execute("INSERT INTO notes(content) VALUES (?)",(content,)); return cur.lastrowid

def search_notes(keyword, limit=20):
    with connect() as conn:
        rows=conn.execute("SELECT id,content,created_at FROM notes WHERE content LIKE ? ORDER BY id DESC LIMIT ?",(f"%{keyword}%",limit)).fetchall()
    return [dict(r) for r in rows]

def add_timer(label, due_at):
    with connect() as conn:
        cur=conn.execute("INSERT INTO timers(label,due_at) VALUES (?,?)",(label,due_at)); return cur.lastrowid

def list_timers(include_done=False):
    sql="SELECT id,label,due_at,done,created_at FROM timers" + ("" if include_done else " WHERE done=0") + " ORDER BY due_at"
    with connect() as conn: rows=conn.execute(sql).fetchall()
    return [dict(r) for r in rows]

def complete_timer(timer_id):
    with connect() as conn:
        cur=conn.execute("UPDATE timers SET done=1 WHERE id=?",(timer_id,)); return cur.rowcount == 1

def export_data():
    with connect() as conn:
        return {"messages":[dict(r) for r in conn.execute("SELECT role,content,created_at FROM messages ORDER BY id")],"notes":[dict(r) for r in conn.execute("SELECT content,created_at FROM notes ORDER BY id")],"timers":[dict(r) for r in conn.execute("SELECT label,due_at,done,created_at FROM timers ORDER BY id")]}
