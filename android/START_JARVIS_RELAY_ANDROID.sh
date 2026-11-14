#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
: "${JARVIS_RELAY_TOKEN:?JARVIS_RELAY_TOKENを設定してください}"
export JARVIS_BIND="${JARVIS_BIND:-127.0.0.1}"
export JARVIS_PORT="${JARVIS_PORT:-8765}"
export JARVIS_RELAY_URL="${JARVIS_RELAY_URL:-http://127.0.0.1:9000}"
export JARVIS_RELAY_ROOM="${JARVIS_RELAY_ROOM:?JARVIS_RELAY_ROOMを設定してください}"
mkdir -p "$HOME/.jarvis/logs"
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup uvicorn relay_server:app --host 0.0.0.0 --port 9000 > "$HOME/.jarvis/logs/relay.log" 2>&1 & echo $! > "$HOME/.jarvis/relay.pid"
nohup python relay_client.py > "$HOME/.jarvis/logs/relay-client.log" 2>&1 & echo $! > "$HOME/.jarvis/relay-client.pid"
nohup python run_local.py > "$HOME/.jarvis/logs/jarvis.log" 2>&1 & echo $! > "$HOME/.jarvis/jarvis.pid"
echo 'SC-56B親機兼中継サーバーを起動しました。'
echo '中継画面: http://<SC-56Bの公開アドレス>:9000/client'
