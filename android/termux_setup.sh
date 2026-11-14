#!/data/data/com.termux/files/usr/bin/bash
set -e
pkg update -y
pkg install -y python git
python -m pip install --upgrade pip
cd "$(dirname "$0")/.."
pip install -r requirements.txt
mkdir -p "$HOME/.jarvis"
command -v curl >/dev/null 2>&1 || pkg install -y curl
if [ ! -s "models/model.gguf" ]; then bash scripts/download_qwen3_1_7b.sh; fi
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
echo 'セットアップ完了。Qwen3-1.7Bオフラインモデルを確認しました。python run_local.py で起動してください。'
echo '初回は localhost のまま動作確認し、LAN接続時はJARVIS_API_TOKENを設定してください。'
