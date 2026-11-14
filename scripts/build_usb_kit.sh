#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/../JARVIS_USB_v0.2}"
rm -rf "$OUT"; mkdir -p "$OUT/INSTALL/Android" "$OUT/INSTALL/Desktop" "$OUT/IOS/IPA" "$OUT/IOS/BACKUP_IMPORT" "$OUT/RELEASES" "$OUT/BACKUP_ENCRYPTED" "$OUT/CHECKSUMS" "$OUT/MANUAL"
cp -a "$ROOT" "$OUT/RELEASES/JARVIS_SC56B_Starter"
rm -rf "$OUT/RELEASES/JARVIS_SC56B_Starter/.venv" "$OUT/RELEASES/JARVIS_SC56B_Starter/__pycache__" "$OUT/RELEASES/JARVIS_SC56B_Starter/src/__pycache__" "$OUT/RELEASES/JARVIS_SC56B_Starter/src/jarvis/__pycache__"
cp "$ROOT/android/README_ANDROID_JP.txt" "$OUT/INSTALL/Android/"
cp "$ROOT/android/termux_setup.sh" "$OUT/INSTALL/Android/"
cp "$ROOT/README_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/USB_RECOVERY_PLAN_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/IPHONE_START_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/HOST_CLIENT_GUIDE_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/REMOTE_ACCESS_GUIDE_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/SELF_HOSTED_RELAY_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/USB_STRUCTURE_JP.txt" "$OUT/MANUAL/"
cp "$ROOT/INSTALL_IPHONE_SIDELOADLY_JP.txt" "$OUT/IOS/"
cp "$ROOT/IOS_IPA_REQUIRED.txt" "$OUT/IOS/IPA/README_IPA_REQUIRED.txt"
cp -a "$ROOT/ios" "$OUT/IOS/SOURCE"
cp "$ROOT/.github/workflows/build-ios.yml" "$OUT/IOS/"
cp "$ROOT/ios/README_IPHONE_BUILD_JP.txt" "$OUT/IOS/"
cat > "$OUT/README_START_JP.txt" <<'EOF'
JARVIS USB 起動案内
====================

Android / Termux:
1. USB内の RELEASES/JARVIS_SC56B_Starter フォルダをAndroid本体へコピーする。
2. Termuxでそのフォルダへ移動する。
3. START_JARVIS_ANDROID.sh を実行する。

USB直下の START_JARVIS_ANDROID.sh は、USB内の本体フォルダをコピーした後に実行する簡易起動ファイルです。

Windows:
RELEASES/JARVIS_SC56B_Starter/START_JARVIS.bat をダブルクリックする。
macOS/Linux:
RELEASES/JARVIS_SC56B_Starter/START_JARVIS.sh を実行する。

USBを挿しただけでは自動実行しません。最初に内容を確認してから、手動でコピー・実行してください。
EOF
cat > "$OUT/START_JARVIS_ANDROID.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
set -e
USB_ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$USB_ROOT/RELEASES/JARVIS_SC56B_Starter"
DEST="$HOME/JARVIS_SC56B_Starter"
if [ ! -d "$SRC" ]; then echo "USB内のJARVIS本体が見つかりません"; exit 1; fi
if [ ! -d "$DEST" ]; then cp -a "$SRC" "$DEST"; fi
cd "$DEST"
bash START_JARVIS_ANDROID.sh
EOF
cat > "$OUT/START_JARVIS_WINDOWS.bat" <<'EOF'
@echo off
chcp 65001 >nul
cd /d "%~dp0RELEASES\JARVIS_SC56B_Starter"
call START_JARVIS.bat
EOF
cat > "$OUT/START_JARVIS_MAC_LINUX.sh" <<'EOF'
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/RELEASES/JARVIS_SC56B_Starter"
./START_JARVIS.sh
EOF
chmod +x "$OUT/START_JARVIS_ANDROID.sh" "$OUT/START_JARVIS_MAC_LINUX.sh"
( cd "$OUT" && find . -type f -not -path './CHECKSUMS/*' -print0 | sort -z | xargs -0 sha256sum > CHECKSUMS/SHA256SUMS.txt )
echo "USB kit created: $OUT"
