#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
if ! command -v python >/dev/null 2>&1; then pkg update -y; pkg install -y python; fi
python START_JARVIS.py
