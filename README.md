# Français 1000 — フランス語初心者の単語帳

1000の重要単語・基本フレーズ、IPA発音記号、日本語訳、女性音声を内蔵する**完全オフラインAndroidアプリ**。ログイン・広告・APIキー・ネットワーク権限はありません。学習履歴は端末内のみに保存します。

## 最新版 v1.6.2 — 指一本で進む単語カード

- **カードの同じ場所を1回タップで和訳表示、もう1回タップで次の単語へ**。次カードで音声自動再生
- 通常の100語ランダム学習に加え、未習得単語優先のランダム10枚チャレンジを追加
- 学習中の進捗バー、完了画面、「もう10枚」で気軽に短い学習セッションを繰り返せる
- カード右上の星、横長の音声再生ボタン、任意の習得/復習操作も維持
- **アプリアイコン：利用者が提供した手描きキャラクターの元の線・輪郭を抽出**。キャラクターの形をAIで描き替えず白く塗り、フランス国旗を背景に合成

## 音声エンジン（v1.5.0から継続）

`oui`、`non`、`je suis` 等の短い語に従来の Kokoro フランス語音声で余計な音が混入する報告があったため、単に音声冒頭をカットせず**音声生成モデルそのものを交換**しました。

- 音声モデル：**Supertone Supertonic 3** / `fr` / **女性声 F2** / 44.1kHz / 品質ステップ 10
- スマートフォンには生成済み Ogg 音声 **1000個すべて**を同梱。アプリでの再生にネット接続もモデルも不要
- GitHub Actions で `python scripts/generate_audio.py` → 検証 → APK ビルド・Release を自動実行。アカウントやAPIキーの設定は不要
- `oui`、`non`、`je suis`、`bonjour`、`merci`、`j'ai`、`c'est` の試聴サンプルを Actions の `French-Female-Audio-QA` に保存
- 自動検証で音声ファイル数、内容の欠落、極端な長さ・音量異常を検出。**音声内容が完全に正しいことを自動検証できるわけではありません。** ネイティブ話者の全件監修は未実施です。

## 学習機能

- 基礎から100語ずつ全10章。カードは章を開くたびランダム表示、表示時に発音を自動再生
- カード右上の星からお気に入り登録。大型の「発音を聞く」ボタンでいつでも再生
- すべての項目にIPA発音記号を表示（辞書推定＋一部手動修正。全件の専門家監修は未実施）
- フランス語から日本語訳を選ぶ4択テスト。レッスン範囲指定で20〜100問、または全問
- 1問ずつ正誤を表示せず連続回答、最後に一括採点。間違った語だけ再テスト
- 学習進捗・苦手単語・テスト成績・お気に入り・検索、端末内保存
- 白と黒を基調とするUI。不要なキャッチコピー、章の初めに戻る「続きから学ぶ」はありません

## インストール

Releases: https://github.com/eponine0805/francais-1000/releases/latest

`v1.6.2` 公開後、`Francais1000.apk` を取得して端末でインストールしてください。現状のAPKはデバッグ署名です。署名が異なるAPKは上書きできず、アンインストールすると端末内学習記録が消えることがあります。

## 開発と音声ライセンス

Android: Java + Android SDK 35 / Gradle 8.9 / OpenJDK 17。
音声のビルド：Python 3.12、`supertonic==1.3.1`、FFmpeg。

- モデル：**Supertone/supertonic-3**、BigScience OpenRAIL-M（利用制限つき）
  - https://huggingface.co/Supertone/supertonic-3
  - https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE
- 推論SDK：Supertone `supertonic`、MIT License
  - https://pypi.org/project/supertonic/

本アプリの音声はAI合成です。モデルは**APKに含まず**生成済み音声のみを配布します。OpenRAIL-Mでは生成物の利用に対しても制約があります。販売前には音声データと教材に関する権利・品質の最終確認を行ってください。*「AIが生成した音声」と明示してください。*

独自の初級語彙選定はCEFR A1と初級学習教材を参考にしていますが、公式CEFR語彙リストではありません。
参考：
- https://www.coe.int/fr/web/common-european-framework-reference-languages/cefr-descriptors-search
- https://www.france-education-international.fr/document/cecrl
- https://apprendre.tv5monde.com/fr/aides/vocabulaire


## v1.6.2 — 1000語・IPA・意味の監査

- [1000語 Excel／監査とテスト記録](data/Francais1000_1000words_reviewed.xlsx)
- [検査結果・未完了項目](docs/2026-10-18-audit.md)
- 特定の /ɥ/ 音を /y/ とした旧IPAの誤り20件と、日本語表記7件を修正。
- 元の単語ID・レッスン・既存音声ファイルを維持。学習記録の紐付けは変えません。
- **波形検査1000件と発音の人間による聴感監修1000件は別物です。後者は未実施。**
