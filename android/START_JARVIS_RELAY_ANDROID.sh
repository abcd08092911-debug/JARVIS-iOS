#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
CONFIG_DIR="$HOME/.jarvis"
CONFIG="$CONFIG_DIR/relay.env"
mkdir -p "$CONFIG_DIR/logs"
if [ ! -s "$CONFIG" ]; then
  TOKEN="Yomi0929"
  ROOM="Yomi0929"
  cat > "$CONFIG" <<EOF
JARVIS_RELAY_TOKEN='$TOKEN'
JARVIS_RELAY_ROOM='$ROOM'
JARVIS_BIND='127.0.0.1'
JARVIS_PORT='8765'
JARVIS_RELAY_URL='http://127.0.0.1:9000'
EOF
  chmod 600 "$CONFIG"
  echo "初期設定を自動生成しました: $CONFIG"
fi
# shellcheck disable=SC1090
source "$CONFIG"
# 初心者向け固定接続設定。既存の自動生成設定も指定値へ統一する。
if [ "${JARVIS_SIMPLE_CONNECTION:-1}" = "1" ] && { [ "${JARVIS_RELAY_TOKEN:-}" != "Yomi0929" ] || [ "${JARVIS_RELAY_ROOM:-}" != "Yomi0929" ]; }; then
  JARVIS_RELAY_TOKEN='Yomi0929'
  JARVIS_RELAY_ROOM='Yomi0929'
  cat > "$CONFIG" <<EOF
JARVIS_RELAY_TOKEN='Yomi0929'
JARVIS_RELAY_ROOM='Yomi0929'
JARVIS_BIND='127.0.0.1'
JARVIS_PORT='8765'
JARVIS_RELAY_URL='http://127.0.0.1:9000'
EOF
  chmod 600 "$CONFIG"
  echo '初心者向け接続コードとトークンをYomi0929へ統一しました。'
fi
: "${JARVIS_RELAY_TOKEN:?設定ファイルのトークンが空です}"
: "${JARVIS_RELAY_ROOM:?設定ファイルの接続コードが空です}"
command -v uvicorn >/dev/null 2>&1 || { echo '先に bash android/termux_setup.sh を実行してください'; exit 1; }
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
pkill -F "$CONFIG_DIR/relay.pid" 2>/dev/null || true
pkill -F "$CONFIG_DIR/relay-client.pid" 2>/dev/null || true
pkill -F "$CONFIG_DIR/jarvis.pid" 2>/dev/null || true
nohup uvicorn relay_server:app --host 0.0.0.0 --port 9000 > "$CONFIG_DIR/logs/relay.log" 2>&1 & echo $! > "$CONFIG_DIR/relay.pid"
nohup python relay_client.py > "$CONFIG_DIR/logs/relay-client.log" 2>&1 & echo $! > "$CONFIG_DIR/relay-client.pid"
nohup python run_local.py > "$CONFIG_DIR/logs/jarvis.log" 2>&1 & echo $! > "$CONFIG_DIR/jarvis.pid"
echo 'SC-56B親機兼中継サーバーを自動設定・起動しました。'
echo "子機用接続コード: $JARVIS_RELAY_ROOM"
echo "中継トークン保存先: $CONFIG"
echo '子機URL: http://<SC-56Bへ到達できるアドレス>:9000/client'
echo '注意: ルーターのポート開放、公開アドレス、HTTPSは自動設定できません。'
