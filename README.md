# Français 1000 / フランス語1000

**はじめて学ぶ人向け・完全オフラインのAndroid単語帳。** 初期状態でフランス語1000語・日本語訳・フランス語音声1000件を収録しています。

## できること

- 100語×10レッスン（挨拶・最重要動詞から、生活・旅行・形容詞へ）
- カードを開いた瞬間に発音を自動再生、何度でも再生可能。タップで和訳を表示
- Lesson 1〜10から開始・終了範囲を選び、**20〜100問の任意の数**または**その範囲の全問**をランダムに**4択（フランス語 → 日本語）**出題
- 間違いの自動記録、復習待ち一覧、復習完了処理、間違い回数の累積履歴
- 覚えた単語・学習済み単語・テスト日時・正解数を端末のローカル領域に保存
- ログイン・APIキー・広告・ネット接続・Android権限いずれも不要

## APKをGitHubから手に入れる

**個人リポジトリにアップロードして `main` へpushすると、GitHub Actionsが1000件の音声を生成してAndroid APKをビルドします。**

1. リポジトリの **Actions** → **Build Android APK** → 最新の成功した実行を開きます。
2. 初回成功時に **Releases → Français 1000 v1.1.0** に `Francais1000.apk` が自動公開されます。
3. Androidで `Francais1000.apk` をダウンロードして開き、「不明なアプリのインストール」をこの操作だけ許可してインストールします。

Releases が作成できない設定の場合も、Actions の Artifacts に `Francais1000-Android-APK` が残ります（ZIP内の `app-debug.apk`）。

**注意:** GitHub Actionsのデバッグ署名は実行ごとに変わり得るため、将来作り直したAPKを上書き更新できない場合があります。データ保護のため、継続的に配布・更新する場合は同一の署名鍵で署名する運用に切り替えてください。アンインストールすると学習記録が失われます。

## ローカルでビルド

Android Studio (JDK 17 / Android SDK 35) でプロジェクトを開いて `app` をビルドするか、Gradle 8.9 で以下を実行します。

```bash
python3 scripts/validate.py
# 上記が不足ファイルで失敗する場合、espeak と ffmpeg を用意して
# python3 scripts/generate_audio.py を先に実行
# Gradle 8.9 と Android SDK が利用できる環境で
# gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 技術情報

- Android Java / Android SDK / MediaPlayer / SharedPreferences、依存ライブラリ0
- `app/src/main/assets/words.json`：固定の1000語
- `app/src/main/assets/audio/0001.ogg`〜`1000.ogg`：オフライン音声（配布ZIPに同梱、GitHub Actionsではビルド時生成）
- 収録音声は eSpeak フランス語音声を使って合成。**合成音なのでネイティブ録音ではありません。**
- `scripts/prepare_vocabulary.py` は独自編集した `vocab_source.txt` からJSON生成
- `scripts/generate_audio.py` は eSpeak / ffmpeg で音声を再生成

※ データはアプリ専用の端末領域に保存されます。アプリのデータを削除したりアンインストールすると記録が消える場合があります。クラウド同期はしません。

## 単語の品質管理

- `scripts/vocab_source.txt` を正本として、各レッスン100項目の計1000項目を固定収録。
- 1000項目のフランス語表記・日本語訳・レッスン番号の一貫性を検証。
- 機械的な頻度ランキングを盲信せず、CEFR A1でよく使う挨拶・代名詞・最重要動詞を先頭に配置。
- `meilleur` の綴り、`les toilettes`、食材に使う部分冠詞などを修正。
- 語義の重なりがある場合、4択には同一の日本語訳を2個以上出さない。誤答候補は同じレッスンから選ぶ。
- 発音はeSpeak合成音声で、実際の人間の発音の完全な代替ではありません。