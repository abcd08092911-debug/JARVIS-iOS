"""JARVISの端末自動バックアップとUSB移動管理。"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

from cryptography.fernet import Fernet

from . import db
from .config import APP_DIR

DEFAULT_PASSWORD = "Yomi0929"
BACKUP_DIR = APP_DIR / "backups"
LOCAL_BACKUP = BACKUP_DIR / "jarvis-auto-latest.enc"
INTERVAL_SECONDS = max(300, int(os.getenv("JARVIS_AUTO_BACKUP_SECONDS", str(6 * 60 * 60))))

_lock = threading.Lock()
_last_usb_paths: set[str] = set()
_pending_usb: Path | None = None
_last_backup: str | None = None
_last_error: str | None = None


def _key(password: str = DEFAULT_PASSWORD) -> bytes:
    return __import__("base64").urlsafe_b64encode(hashlib.sha256(password.encode("utf-8")).digest())


def create_local_backup() -> Path:
    """現在のデータを暗号化して端末内へ保存する。"""
    global _last_backup, _last_error
    payload = db.export_data()
    payload["_format"] = "jarvis-backup-v2"
    payload["_created_at"] = datetime.now(timezone.utc).isoformat()
    raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    temporary = BACKUP_DIR / "jarvis-auto-latest.enc.tmp"
    temporary.write_bytes(Fernet(_key()).encrypt(raw))
    temporary.replace(LOCAL_BACKUP)
    _last_backup = datetime.now(timezone.utc).isoformat()
    _last_error = None
    return LOCAL_BACKUP


def _mount_candidates() -> list[Path]:
    """一般的なLinux/Androidのリムーバブル媒体候補を返す。"""
    roots: list[Path] = []
    user = os.getenv("USER", "")
    for raw in (
        f"/media/{user}", f"/run/media/{user}", "/mnt", "/storage", "/sdcard",
        os.getenv("JARVIS_USB_ROOT", ""),
    ):
        if not raw:
            continue
        root = Path(raw).expanduser()
        if not root.exists() or not root.is_dir():
            continue
        try:
            entries = list(root.iterdir())
        except OSError:
            continue
        for entry in entries:
            if entry.is_dir() and entry != APP_DIR and entry.name not in ("emulated", "self", "enc_emulated"):
                roots.append(entry)
    # Windows/macOSの手動指定にも対応する。
    for raw in os.getenv("JARVIS_USB_PATHS", "").split(os.pathsep):
        if raw and Path(raw).is_dir():
            roots.append(Path(raw))
    unique: list[Path] = []
    seen: set[str] = set()
    for path in roots:
        try:
            key = str(path.resolve())
        except OSError:
            continue
        if key not in seen:
            seen.add(key)
            unique.append(path)
    return unique


def _detect_usb() -> Path | None:
    global _last_usb_paths, _pending_usb
    candidates = _mount_candidates()
    current = {str(p.resolve()) for p in candidates}
    new_paths = current - _last_usb_paths
    _last_usb_paths = current
    if new_paths:
        # 最初に検出した媒体を確認対象にする。
        _pending_usb = Path(sorted(new_paths)[0])
    return _pending_usb


def status() -> dict:
    usb = _detect_usb()
    return {
        "local_backup_exists": LOCAL_BACKUP.exists(),
        "local_backup": str(LOCAL_BACKUP) if LOCAL_BACKUP.exists() else None,
        "last_backup": _last_backup,
        "last_error": _last_error,
        "usb_detected": usb is not None,
        "usb_path": str(usb) if usb else None,
        "interval_seconds": INTERVAL_SECONDS,
    }


def save_to_usb_and_remove_local() -> dict:
    """USBへコピーし、暗号化ファイルを検証してから端末側を削除する。"""
    global _pending_usb, _last_error
    with _lock:
        usb = _pending_usb or _detect_usb()
        if not usb:
            raise RuntimeError("USBが検出されていません")
        if not LOCAL_BACKUP.exists():
            raise RuntimeError("端末内の自動バックアップがありません")
        target_dir = usb / "JARVIS_BACKUPS"
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / f"jarvis-auto-{datetime.now().strftime('%Y%m%d-%H%M%S')}.enc"
        shutil.copy2(LOCAL_BACKUP, target)
        # コピー先を復号できることを確認してから、端末側を削除する。
        Fernet(_key()).decrypt(target.read_bytes())
        source_hash = hashlib.sha256(LOCAL_BACKUP.read_bytes()).hexdigest()
        target_hash = hashlib.sha256(target.read_bytes()).hexdigest()
        if source_hash != target_hash:
            target.unlink(missing_ok=True)
            raise RuntimeError("USBへのコピー検証に失敗しました")
        LOCAL_BACKUP.unlink()
        _pending_usb = None
        _last_error = None
        return {"saved_to": str(target), "local_deleted": True}


def dismiss_usb_prompt() -> dict:
    global _pending_usb
    _pending_usb = None
    return {"dismissed": True}


def _worker() -> None:
    # 起動直後に既存データを一度保存し、その後は定期保存する。
    time.sleep(5)
    while True:
        try:
            create_local_backup()
            status()
        except Exception as exc:  # 自動処理の失敗でJARVIS本体を停止させない
            global _last_error
            _last_error = str(exc)
        time.sleep(INTERVAL_SECONDS)


def start() -> None:
    if os.getenv("JARVIS_DISABLE_AUTO_BACKUP", "0") == "1":
        return
    thread = threading.Thread(target=_worker, name="jarvis-auto-backup", daemon=True)
    thread.start()
