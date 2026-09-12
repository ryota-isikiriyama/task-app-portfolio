# 🚀 Task管理カンバンアプリ（Serverless Web Application）

個人・チーム開発を想定して作成した、レスポンシブ対応の本格的なカンバン方式タスク管理Webアプリケーションです。
フロントエンドからバックエンド、インフラ（AWS）までを一人で構築・デプロイしています。

## 🌟 デモ & URL
* **デモアプリ（GitHub Pages）**: [https://ryota-isikiriyama.github.io/task-app-portfolio/](https://ryota-isikiriyama.github.io/task-app-portfolio/)
* **ソースコード（GitHub）**: [https://github.com/ryota-isikiriyama/task-app-portfolio](https://github.com/ryota-isikiriyama/task-app-portfolio)

---

## 🛠️ 使用技術（テックスタック）

### フロントエンド
* **HTML5 / CSS3 / JavaScript (Vanilla JS)**
  * PCでのドラッグ＆ドロップ操作だけでなく、スマートフォンなどのタッチデバイス（`touchstart` / `touchmove`）でも直感的に操作できるマルチデバイス対応を実現。
  * タスクの動的なステータス変更、モーダルによるインライン編集、担当者ごとのリアルタイム・フィルタリング機能を実装。

### バックエンド
* **Java (Spring Boot)**
  * AWS Lambda上で動作するAPIをJavaで実装。オブジェクト指向設計やルーティング処理を担当。

### インフラ・データベース (AWS Serverless)
* **Amazon API Gateway**: REST APIのエンドポイント提供
* **AWS Lambda**: サーバーレス環境でのバックエンド処理実行
* **Amazon DynamoDB**: タスクデータ（ID、タイトル、担当者、作成日時、ステータス）の高速なNoSQL永続化

---

## ✨ 主な機能
1. **タスクのCRUD処理**: タスクの新規追加、詳細の編集、ステータスの変更、削除。
2. **カンバンボード形式**: 「未着手」「進行中」「完了」の3カラムで視覚的に管理。
3. **マルチデバイス対応**: PCではドラッグ＆ドロップ、スマホではタッチ操作・専用ボタンタップでスムーズにステータス移動が可能。
4. **担当者フィルター**: 登録された担当者名でタスクを即時絞り込み表示。