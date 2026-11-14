JARVIS iPhone親機版（IPA作成）
==============================

この版は「iPhoneを親機」にするための初期実装です。iPhone内に会話記憶を保存し、iPhone上で親機HTTPサーバーを起動して、同じWi-Fiの他端末から /health と /chat を受け付けます。

Macがなくても、GitHubのSafari画面からActionsの「Build JARVIS iOS IPA」を実行できます。生成されたJARVIS.ipaをWindows PCへダウンロードし、SideloadlyでApple IDを使ってiPhoneへ入れます。

注意: この初期版の回答エンジンは親機動作確認用の最小ローカルコアです。高性能な量子化LLMをiPhoneへ組み込むには、iOS対応の推論ライブラリとモデルを別途追加する必要があります。親機サーバーはiOSのフォアグラウンド動作中に利用してください。
