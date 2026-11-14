#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
CONFIG="$HOME/.jarvis/relay.env"
mkdir -p "$HOME/.jarvis/logs"
if [ ! -s "$CONFIG" ]; then
  TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
  ROOM="JARVIS-$(python3 -c 'import secrets; print(secrets.token_hex(4).upper())')"
  cat > "$CONFIG" <<EOF
JARVIS_RELAY_TOKEN='$TOKEN'
JARVIS_RELAY_ROOM='$ROOM'
JARVIS_RELAY_URL='http://127.0.0.1:9000'
JARVIS_LOCAL_URL='http://127.0.0.1:8765'
EOF
  chmod 600 "$CONFIG"
fi
source "$CONFIG"
pkill -F "$HOME/.jarvis/relay.pid" 2>/dev/null || true
pkill -F "$HOME/.jarvis/relay-client.pid" 2>/dev/null || true
pkill -F "$HOME/.jarvis/jarvis.pid" 2>/dev/null || true
nohup uvicorn relay_server:app --host 0.0.0.0 --port 9000 > "$HOME/.jarvis/logs/relay.log" 2>&1 & echo $! > "$HOME/.jarvis/relay.pid"
nohup python3 relay_client.py > "$HOME/.jarvis/logs/relay-client.log" 2>&1 & echo $! > "$HOME/.jarvis/relay-client.pid"
nohup python3 run_local.py > "$HOME/.jarvis/logs/jarvis.log" 2>&1 & echo $! > "$HOME/.jarvis/jarvis.pid"
echo "JARVIS親機兼中継機を起動しました。子機: http://<この端末のIP>:9000/client"
