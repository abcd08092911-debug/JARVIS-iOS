JARVIS SC-56B Starter v0.2
===========================
ローカル優先のAI執事コアです。FastAPI、SQLite記憶、ローカルllama.cpp接続、メモ、タイマー、ファイル検索、ブラウザ画面、APIトークン保護、暗号化バックアップを含みます。

起動（PC/Termux）
------------------
1. Python 3.11以降
2. python -m venv .venv && source .venv/bin/activate
3. pip install -r requirements.txt
4. python run_local.py
5. http://127.0.0.1:8765/ または /docs

ローカルLLM
-----------
llama.cppのllama-cliを端末へ導入し、GGUFモデルを models/model.gguf に配置します。別名の場合は JARVIS_MODEL_PATH と JARVIS_LLAMA_BIN を設定してください。モデルは必ずライセンスを確認してください。

LANで使う場合
-------------
同一Wi-Fi内だけで使い、JARVIS_BIND=0.0.0.0 と JARVIS_API_TOKEN=長いランダム文字列を設定します。例: JARVIS_BIND=0.0.0.0 JARVIS_API_TOKEN=... python run_local.py。ルーターのポート開放はしないでください。外出先はVPNを使います。

バックアップ
------------
python scripts/backup.py backup backups/jarvis.enc --password 'USBとは別に保管する強いパスワード'
復元: python scripts/backup.py restore backups/jarvis.enc --password '同じパスワード'
USBは唯一のバックアップにせず、USBへの自動実行は行いません。

未検証
------
SC-56B実機での速度・発熱・常駐・音声認識・音声合成・モデルサイズは未検証です。最初はlocalhostで確認し、LAN公開前にトークンを設定してください。
