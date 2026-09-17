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
5. **カラム内の並び替え**: ドラッグ&ドロップ/タッチ操作でのカード並び順をサーバー側に保存（リロードしても順序が維持される）。
6. **優先度・締切日**: タスクごとに優先度（低・中・高）と締切日を設定でき、期限超過は強調表示。
7. **ダークモード**: OS/ブラウザの配色設定（`prefers-color-scheme`）に自動追従。
8. **トースト通知**: API呼び出しの成功/失敗を画面上に通知（バリデーションエラーなどもその場で表示）。

---

## 🔒 セキュリティ・信頼性まわりの設計

* **APIキー + Usage Plan**: API Gatewayに `ApiKeyRequired: true` を設定し、スロットリング（`RateLimit`/`BurstLimit`）とクォータ（`Quota`）を持つUsage Planを紐付けている。
  ただしAPIキー自体は静的サイト（`web/config.js`）に埋め込まれるため、ブラウザの開発者ツールから誰でも閲覧できる＝秘密情報ではない。
  ここでの本質的な防御は「キーの秘匿」ではなく「Usage Planによる利用量の制限」である点を意識した設計にしている。
* **サーバー側バリデーション**: タイトル必須・文字数上限、`status`/`priority`の値チェックなど、フロント側の入力チェックをバイパスして直接APIを叩かれた場合にも不正なデータが保存されないようにしている。
* **エラーハンドリング**: 想定外の例外はCloudWatch Logsにのみ詳細を出力し、クライアントには内部実装が漏れない汎用メッセージのみを返す。
* **Lambdaのコールドスタート最適化**: DynamoDBクライアントはリクエストごとではなく、Lambda実行環境の初期化時に1度だけ生成して使い回す。
* **DynamoDB GSI**: `assignee` に対するグローバルセカンダリインデックスを用意し、担当者絞り込みをフルスキャンではなくQueryで行えるようにAPI側（`GET /tasks?assignee=...`）を対応済み（現状のフロントは規模的にシンプルなクライアント側フィルタを採用しているが、必要に応じて切り替え可能）。

---

## ✅ テスト・CI

* `src/test/java` にJUnit5 + Mockitoによるユニットテストを実装（バリデーション、404/400/500のハンドリング、例外時に内部情報が漏れないことなどを検証）。
* GitHub Actions（`.github/workflows/ci.yml`）で `main` ブランチへのpush/PR時に自動でテスト＆ビルドを実行。

```bash
mvn test
```

---

## ⚙️ セットアップ（デプロイ後の設定）

`web/config.js` にAPIのエンドポイントURLとAPIキーを設定する。

```bash
# デプロイ後、APIキーの実際の値を取得
aws apigateway get-api-key --api-key <template.yamlのOutputs.ApiKeyIdの値> --include-value --query value --output text
```

```js
// web/config.js
const API_CONFIG = {
    BASE_URL: "https://xxxxxxxxxx.execute-api.ap-northeast-1.amazonaws.com/Prod/tasks",
    API_KEY: "取得した値をここに設定"
};
```