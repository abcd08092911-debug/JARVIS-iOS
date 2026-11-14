import subprocess
from .config import LLAMA_BIN, MODEL_PATH

class LocalLLM:
    def __init__(self): self.last_error = None
    @property
    def available(self): return MODEL_PATH.exists()
    def generate(self, prompt, max_tokens=512):
        if not self.available:
            self.last_error = f"モデルがありません: {MODEL_PATH}"
            return None
        try:
            p=subprocess.run([LLAMA_BIN,"-m",str(MODEL_PATH),"-p",prompt,"-n",str(max_tokens),"--no-display-prompt","-e"], text=True, capture_output=True, timeout=180)
            if p.returncode != 0: self.last_error=(p.stderr or "llama.cpp error").strip(); return None
            return p.stdout.strip()
        except FileNotFoundError:
            self.last_error=f"llama.cpp実行ファイルがありません: {LLAMA_BIN}"; return None
        except subprocess.TimeoutExpired:
            self.last_error="推論がタイムアウトしました"; return None
