from pathlib import Path
import os

APP_DIR = Path(os.getenv("JARVIS_DATA_DIR", str(Path.home() / ".jarvis"))).expanduser()
DB_PATH = APP_DIR / "jarvis.sqlite3"
MODEL_PATH = Path(os.getenv("JARVIS_MODEL_PATH", str(Path(__file__).resolve().parents[2] / "models" / "model.gguf"))).expanduser()
LLAMA_BIN = os.getenv("JARVIS_LLAMA_BIN", "llama-cli")
API_TOKEN = os.getenv("JARVIS_API_TOKEN", "")
BIND_HOST = os.getenv("JARVIS_BIND", "127.0.0.1")
PORT = int(os.getenv("JARVIS_PORT", "8765"))
MAX_CONTEXT_MESSAGES = int(os.getenv("JARVIS_MAX_CONTEXT", "12"))
