# JARVIS Android APK 試作版

TermuxなしでSC-56BへインストールできるAndroidアプリの試作です。

## ビルド

GitHub Actionsの `Build Android APK` を実行し、完了後にArtifactsから `JARVIS-Android-debug` をダウンロードします。APKをSC-56Bへコピーしてインストールします。

## 試作版で確認できる機能

- JARVISアプリの単体起動
- 端末内SQLiteへの会話保存
- 簡易オフライン応答
- 日本語音声入力
- 端末の読み上げエンジンを利用できる基盤
- 親機モードの簡易HTTPサーバー（8765番）
- `/health` と `/chat` の基本API

## まだTermux版と同じではない機能

この試作APKには、まだ次の機能が完全移植されていません。

- Qwen3-1.7B GGUFとllama.cppによる本格的なローカル推論
- 長期記憶の検索・重要度・要約の完全版
- Relay中継サーバーと外部子機接続の完全版
- 暗号化自動バックアップとUSB検出
- ファイル作成・コード作成・タイマー・コネクター

したがって、今回のAPKは「機能完全一致版」ではなく、Termuxなし版の移植土台です。完全一致にするには、llama.cppのAndroid NDK統合、Relay、バックアップ、各APIを順番に移植して実機テストする必要があります。
