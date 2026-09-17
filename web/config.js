// API接続設定。
// API_KEYはブラウザに配信されるJSに含まれる=誰でも閲覧可能な値。
// これは「秘密」ではなく、API Gateway側のUsage Plan（レート制限/クォータ）と組み合わせて
// 過度なアクセスを抑止するためのものと理解した上で運用すること。
const API_CONFIG = {
    BASE_URL: "https://ra1yyej058.execute-api.ap-northeast-1.amazonaws.com/Prod/tasks",
    API_KEY: "REPLACE_WITH_API_KEY_VALUE"
};
