# Français 1000 — フランス語初心者単語帳

1000項目の単語・表現、発音記号（IPA）、日本語訳、女性音声を内蔵する完全オフラインAndroidアプリ。通信・ログイン・APIキー・広告は不要です。

## v1.5.0 — 女性音声を根本的に変更

従来のKokoroでは oui、non、je suis などで余計な音が混入する問題が報告されました。最初の音を機械的に削るのではなく、**Supertone Supertonic 3** のフランス語専用生成モードへ切り替えています。

- 女性 F2、言語 fr、44.1kHz、品質ステップ10
- 1000件の生成済みOggをAPKに同梱。モデルと認証情報は端末に不要
- ビルドはGitHub Actionsで実行。利用者のクラウド設定は不要
- oui、non、je suis、bonjour、merci、j'ai、c'est の実際の音声を Actions の French-Female-Audio-QA に保存
- 音声の欠落・極端な長さ・音量異常を検証。**余分な音や誤発音を完全に検出できるわけではありません。** 販売前には試聴確認が必要です。

## 学習機能

100語ずつの10章。開くたびにカードはランダムな順番、表示時に音声を自動再生。右上の星がお気に入り、再生は横長ボタンでいつでもできます。フランス語→日本語の4択テストは範囲と問題数（20〜100または全問）を指定。各問直後の正誤表示はなく、最後に一括採点し、間違いだけ再テストできます。学習記録・検索・苦手単語は端末内保存。白黒デザインです。

## Androidダウンロード

https://github.com/eponine0805/francais-1000/releases/latest

v1.5.0 にある Francais1000.apk をインストールしてください。現在のAPKはデバッグ署名です。旧版と署名が異なって上書きできない場合、アンインストールで学習記録が消える点に注意してください。

## ライセンス

生成モデル：Supertone/supertonic-3、**BigScience OpenRAIL-M**（利用制限があります）。
https://huggingface.co/Supertone/supertonic-3
https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE

生成SDK：supertonic 1.3.1、MITライセンス。
https://pypi.org/project/supertonic/

**音声はAI合成**です。モデルはAPKに同梱しません。100円での販売前には利用条件・教材品質・発音の最終確認が必要です。IPA表記もすべて専門家監修済みではありません。

初級語彙の編成はCEFR A1、France Éducation international、TV5MONDE等を参照した独自リストで、公式の認定教材ではありません。
