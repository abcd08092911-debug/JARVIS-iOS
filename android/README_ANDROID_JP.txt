Android / Termux 手順（JARVIS v0.2）

1. Termuxを信頼できる配布元から導入し、配布元を混在させない。
2. JARVISフォルダを端末へコピーし、Termuxで移動する。
3. bash android/termux_setup.sh を実行する。
4. GGUFモデルを models/model.gguf に配置する。
5. python run_local.py で起動する。
6. http://127.0.0.1:8765/ または /docs を開く。

LAN接続時は JARVIS_BIND=0.0.0.0 と長い JARVIS_API_TOKEN を設定し、同一Wi-Fi内だけで使用する。ルーターのポート開放はしない。外出先からはVPNを使用する。Androidの省電力設定により常駐が停止する場合があるため、電池消費を確認しながらTermuxの最適化設定を調整する。
