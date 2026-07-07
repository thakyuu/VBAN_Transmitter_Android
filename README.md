# VBAN Transmitter for Android

[日本語](README.md) | [English](README_en.md)

このプロジェクトは、Android端末の音声（マイク音声、またはAndroid 10+のシステム内部再生音）を、ローカルネットワーク（Wi-Fi）を通じてWindows PC上の **Voicemeeter** へリアルタイムストリーミングするAndroidアプリケーションです。

## アプリケーションの特長
* **システム音声キャプチャ (Android 10以上)**: バックグラウンドの音楽やゲーム音をそのままVoicemeeterに流し込めます。
* **マイク音声配信**: マイク音声をストリーミングし、PC側の音声入力ソースとして使用可能です。

![アプリのスクリーンショット](docs/screenshot_main.webp)

---

## 必要な環境

### 開発・ビルド環境
* **Android Studio** (最新の安定版推奨)
* **JDK 17** (Android Studioに同梱されています)
* **Android SDK 34** (API 29以上対応)

### 動作環境
* Android 10 (API 29) 以上の実機スマートフォン (システム音声キャプチャに必須)
* Windows PC (Voicemeeter, Voicemeeter Banana, または Voicemeeter Potato がインストール済み)
* 両方の端末が**同じLAN**に接続されていること

---

## インストール

### Obtainium を使用する場合
[Obtainium](https://github.com/ImranR98/Obtainium) を使用すると、GitHub のリリースから直接 APK をインストールし、最新バージョンに自動更新できます。

1. Obtainium を [GitHub Releases](https://github.com/ImranR98/Obtainium/releases) または [F-Droid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid/) からインストールします。
2. Obtainium を開き、**+** ボタンをタップします。
3. リポジトリの URL `https://github.com/thakyuu/VBAN_Transmitter_Android` を入力します。
4. **Add** をタップすると、Obtainium が最新の APK リリースを自動検出してインストールします。
5. Obtainium が定期的にアップデートを確認し、新しいバージョンが利用可能になると通知します。

---

## ビルド・実行手順

### 1. プロジェクトのインポート
1. Android Studio を起動します。
2. **Open** を選択し、プロジェクトのルートフォルダ（本フォルダ）を指定して開きます。
3. Gradleの同期（Sync）が自動的に開始されます。終了するまで数分待ちます。
   * Android Studioが必要なビルドツールやSDKを自動的にダウンロードします。

### 2. アプリの実行
1. Android 端末をデバッグモードでPCに接続するか、エミュレータを設定します。
2. 画面上部の実行ボタン（緑の三角アイコン）を押して、アプリをビルド＆インストールします。

---

## 設定と使用方法

### 1. Windows PC (Voicemeeter) 側の設定
1. Windows上で **Voicemeeter** を起動します。
2. 画面右上の **VBAN** ボタンをクリックして、VBAN設定ウィンドウを開きます。
3. VBANのトグルを **ON** (緑色) にします。
4. **Incoming Streams**（受信ストリーム）の一行目で、以下の設定を行います：
   * **On/Off**: `ON` にチェック
   * **Stream Name**: `Stream1` （アプリの設定と完全に一致させてください。大文字小文字区別あり）
   * **IP Address**: (Androidアプリ画面の下部に表示される「Local Device IP」を入力します。または空欄にして自動検知させます)
   * **Route to**: `Virtual Input (VAIO)` 等、任意の入力端子を選択します。

### 2. Android アプリ側の設定
1. Android端末でアプリを起動します。
2. 接続先の **Voicemeeter PC IP Address** に、Windows PCのローカルIPアドレスを入力します。
   * *(WindowsのIPは、コマンドプロンプトで `ipconfig` を実行して IPv4 アドレスを確認できます)*
3. **UDP Port** に `6980` (デフォルト値)、**Stream Name** に `Stream1` を入力します。
4. 音源（Audio Source）、サンプリングレート、チャンネルを選択します。
   * *推奨設定: 音源 = System Audio、サンプリングレート = 48000 Hz、チャンネル = Stereo (2 Channels)*
5. **START TRANSMISSION** ボタンをタップします。
6. マイク権限および画面録画/キャストのシステム確認ダイアログが表示されるので、**許可 / 今すぐ開始** を選択します。
7. 送信が開始されると、ステータスが **TRANSMITTING...** に変わり、送信パケット数とデータ容量がリアルタイムで増加します。Windows側のVoicemeeterに音が届いていることを確認してください。

---

## ライセンス

このプロジェクトは **MIT ライセンス** の下で公開されています。詳細は [LICENSE](LICENSE) ファイルを参照してください。

本プロジェクトが依存するサードパーティのオープンソースライセンスについては [NOTICE.md](NOTICE.md) を参照してください。

---

## 商標について

VBAN および Voicemeeter は [VB-Audio Software](https://vb-audio.com) の商標です。本アプリケーションは VB-Audio とは提携しておらず、非公式のサードパーティ製ツールです。VBAN プロトコルの仕様は [VB-Audio により公開](https://vb-audio.com/Voicemeeter/vban.htm) されており、本アプリケーションはこれを独自に実装したものです。
