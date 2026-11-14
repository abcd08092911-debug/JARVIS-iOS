#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_DIR="$ROOT/models"
MODEL="$MODEL_DIR/model.gguf"
URL="https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf?download=true"
mkdir -p "$MODEL_DIR"
if [ -s "$MODEL" ]; then
  echo "モデルは既にあります: $MODEL"
  exit 0
fi
command -v curl >/dev/null 2>&1 || { echo 'curlが必要です'; exit 1; }
echo 'Qwen3-1.7B-Q8_0をダウンロードします。約2GBの空き容量が必要です。'
curl -L --fail --retry 3 --progress-bar "$URL" -o "$MODEL.tmp"
mv "$MODEL.tmp" "$MODEL"
echo "配置完了: $MODEL"
