# Français 1000 — 初めてのフランス語単語帳

日本語を母語とする初心者向け、**1000語・表現を最初から収録した完全オフラインAndroidアプリ**。ログイン・広告・課金・インターネット権限なし。

## v1.4.0の改善（オフライン UI アップデート）

- 100語のカード表示順を毎回ランダムにシャッフル。出題番号は画面の単語の前に表示しません。
- 単語カードの再生ボタンを横長・大きめに変更し、お気に入り登録はカード右上の星（☆ / ★）に統合。
- 前後移動・習得・未習得ボタンと画面下部のタブを拡大。
- ホームから「続きから学ぶ」、余分なキャッチコピーを削除。学習したい章を直接選びます。
- **音声の根本的な品質改善は別工程です。** この版の自動ビルドでは従来のKokoro音声が使われます。Google Cloud Chirp 3: HD は新しい専用ワークフローで生成します。

## プレミアム女性音声（Chirp 3: HD）を収録するまで

フランス語初級の短い語で Kokoro の合成音声に余分な子音・母音が混入する事例が確認されたため、クラウドのフランス語専用高品質女性音声を使う選択肢を追加しました。クラウド利用は **音声ファイル生成時のみ**。Androidアプリは生成済み Ogg 音声を内包し、オフラインのまま使えます。

このワークフローは、**有効なGoogle Cloud プロジェクト・Cloud Text-to-Speech API・請求設定、およびAPIの認証情報**が必要です。料金が発生する場合があるため、こちらから自動的に有効化しません。GitHub リポジトリの [Actions secrets](https://github.com/eponine0805/francais-1000/settings/secrets/actions) に、Cloud Text-to-Speech API に限定したAPIキーを `GOOGLE_TTS_API_KEY` の名前で登録します。サービスアカウントを使う場合は、代わりに `GOOGLE_TTS_SERVICE_ACCOUNT_JSON` を登録できます。**JSONキーやAPIキーをチャット・ソースコード・READMEに貼らないでください。**

1. [Actions → Premium French audio - audition and build](https://github.com/eponine0805/francais-1000/actions/workflows/premium-french-audio.yml) から `mode = samples` を実行。
2. `oui`, `non`, `je suis`, `bonjour`, `merci` の試聴用 Ogg ファイルを Artifacts で取得して、余計な音が入らないか確認。
3. 問題がなければ `mode = full` を実行。`fr-FR-Chirp3-HD-Aoede`（女性）を全1000項目に使い、APK v1.4.1を公開します。

Google Cloudの1か月の無料使用枠・価格は変更されるため、使用前に必ず https://cloud.google.com/text-to-speech/pricing を確認してください。クラウド音声の完璧な発音は保証できません。販売前には合成音声の品質・ライセンス条件を確認してください。

## v1.3.0の改善

- **すべての1000項目にIPA発音記号**を表示（フラッシュカード・テスト・結果・単語帳）。辞書と音声を照合するための補助情報です。IPAは自動辞書推定に一部手動修正を加えており、独立した音声学者による全件監修は未実施です。
- 白・黒・薄いグレーのUIをさらに整理。学習トップに進捗バー・学習継続日数・続きから再開を追加。
- **単語帳の日本語・フランス語検索、保存済み単語、お気に入り機能**を追加。
- 採点結果でIPAを表示し、不正解を先に一覧化。1000問テストでも30件ずつ段階表示。
- **je suis の先頭に混入していた可能性が高い短い合成音**を、検証済みサンプルの長さに基づいて抑制（初級教材用の発音 /ʒə sɥi/ を意図）。

## v1.2.0の改善

- 音声：eSpeakの機械音を廃止。Kokoro-82Mの**フランス語女性ニューラル音声 `ff_siwis`**（少しゆっくり）1000件をビルド時に作成し、APK内に同梱。端末で音声の通信・生成はしません。
- 教材：CEFR A1能力記述、France Éducation internationalのCEFR語彙テーマ、TV5MONDE「Première classe」とA1教材の展開を参照し、よく使う挨拶・自己紹介・質問・動詞を前半に移し、色・方向・基本表現を追加。重複1000語を排除。名詞の性を意識して冠詞とともに表示。
- デザイン：白を基調に黒・グレーを使用。4択の選択肢の数字を廃止、文字の縦位置と余白を統一。
- テスト：指定範囲からランダムに20〜100問または全問。各回答の直後に正誤を表示せず、**全問終了後にまとめて採点・全問答え合わせ**。その回の間違いだけ再テスト可。過去の未復習問題だけのテスト可（100件を超えた場合は100件をランダム抽出）。
- 学習記録は端末内に保存。旧v1.1.0の単語IDから新しい並びへの学習記録移行を実装。

## Androidアプリをインストール

GitHub **Releases** の `v1.4.0` にある `Francais1000.apk` をダウンロードしてインストールしてください。APKの生成はGitHub Actionsで検証しています。Android APKへの署名は現在デバッグ署名です。旧バージョンから上書きするには署名一致が必要です。

## ビルド

GitHub Actionsの `Build Android APK`（Kokoro版）は、Python 3.12で公開のKokoro音声モデル・女性音声データを取得し、1000個の高品質Ogg音声を生成、Java/Android SDK35でAPKをビルド、GitHub Releasesに保存します。**端末のアプリでは全く通信しません**。音声モデルの取得や合成はビルド時のみ行います。生成処理は数十分かかる場合があります。

開発依存：Gradle 8.9、Android SDK 35、OpenJDK 17、Python 3.12、kokoro-onnx、misaki-fork、ffmpeg、espeak-ng。

教材設計の参考資料（**原文のリストを転載していません**）：
- Council of Europe CEFR A1: https://www.coe.int/fr/web/common-european-framework-reference-languages/cefr-descriptors-search
- France Éducation international `Inventaire linguistique des contenus clés des niveaux du CECRL`: https://www.france-education-international.fr/document/cecrl
- TV5MONDE 初学者プログラム: https://enseigner.tv5monde.com/articles-dossiers/articles/premiere-classe-avec-tv5monde-des-exercices-concus-pour-debuter-en
- TV5MONDE 初級用語彙: https://apprendre.tv5monde.com/fr/aides/vocabulaire
- Hachette FLE Cosmopolite 1 A1: https://cosmopolite.hachettefle.fr/cosmopolite-1_livre-de-l-eleve_fr.html

音声クレジット：Kokoro-82M https://huggingface.co/hexgrad/Kokoro-82M (Apache 2.0)、kokoro-onnx https://github.com/thewh1teagle/kokoro-onnx (MIT)。`ff_siwis`はSIWIS French Speech Synthesis Database (University of Edinburgh, CC BY 4.0) に由来します。生成音声は本アプリの付属物です。

**注：** 1000語は学習目標と初級教材を参考に独自編成したもので、CEFRや出版社公認の公式語彙リストではありません。A1〜A2への橋渡しとなる後半の語も含みます。
