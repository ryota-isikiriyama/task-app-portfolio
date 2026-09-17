package com.example.task.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.task.model.Task;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "IN_PROGRESS", "COMPLETED");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_ASSIGNEE_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final String ASSIGNEE_INDEX_NAME = "AssigneeIndex";

    // Lambdaの実行環境（コンテナ）は複数リクエストで再利用されるため、
    // AWS SDKクライアントはハンドラー呼び出しごとではなく、クラス初期化時に一度だけ生成する。
    private static final DynamoDbEnhancedClient ENHANCED_CLIENT = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(DynamoDbClient.builder().region(Region.AP_NORTHEAST_1).build())
            .build();

    private final DynamoDbTable<Task> taskTable;

    public TaskHandler() {
        this(ENHANCED_CLIENT.table("Tasks", TableSchema.fromBean(Task.class)));
    }

    // テストからモックのDynamoDbTableを注入できるようにするためのコンストラクタ
    TaskHandler(DynamoDbTable<Task> taskTable) {
        this.taskTable = taskTable;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
        headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        response.setHeaders(headers);

        String httpMethod = request.getHttpMethod();

        if ("OPTIONS".equalsIgnoreCase(httpMethod)) {
            response.setStatusCode(200);
            return response;
        }

        try {
            if ("POST".equalsIgnoreCase(httpMethod)) {
                handleCreate(request, response);
            } else if ("GET".equalsIgnoreCase(httpMethod)) {
                handleList(request, response);
            } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                handleUpdate(request, response);
            } else if ("DELETE".equalsIgnoreCase(httpMethod)) {
                handleDelete(request, response);
            } else {
                writeError(response, 405, "Method not allowed");
            }
        } catch (JsonProcessingException e) {
            context.getLogger().log("Invalid request body: " + e.getMessage());
            writeError(response, 400, "Request body is not valid JSON");
        } catch (ValidationException e) {
            writeError(response, 400, e.getMessage());
        } catch (Exception e) {
            // クライアントには内部実装の詳細（スタックトレース等）を返さず、
            // 詳細はCloudWatch Logsにのみ出力する。
            context.getLogger().log("Unexpected error handling " + httpMethod + " request: " + e);
            writeError(response, 500, "Internal server error");
        }

        return response;
    }

    private void handleCreate(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws JsonProcessingException {
        Task task = parseBody(request.getBody());

        if (task.getId() == null || task.getId().isEmpty()) {
            task.setId(UUID.randomUUID().toString());
        }
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("PENDING");
        }
        if (task.getAssignee() == null || task.getAssignee().trim().isEmpty()) {
            task.setAssignee("未設定");
        }
        if (task.getCreatedAt() == null || task.getCreatedAt().isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            task.setCreatedAt(LocalDateTime.now().format(formatter));
        }
        if (task.getPriority() == null || task.getPriority().isEmpty()) {
            task.setPriority("MEDIUM");
        }
        if (task.getOrder() == null) {
            task.setOrder(System.currentTimeMillis());
        }

        validateTask(task);

        taskTable.putItem(task);
        response.setStatusCode(201);
        response.setBody(OBJECT_MAPPER.writeValueAsString(task));
    }

    private void handleList(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws JsonProcessingException {
        String assignee = request.getQueryStringParameters() != null
                ? request.getQueryStringParameters().get("assignee")
                : null;

        List<Task> taskList = new ArrayList<>();
        if (assignee != null && !assignee.isEmpty() && !"ALL".equalsIgnoreCase(assignee)) {
            // 担当者で絞り込む場合はGSIへのQueryを使い、テーブル全体のScanを避ける。
            DynamoDbIndex<Task> index = taskTable.index(ASSIGNEE_INDEX_NAME);
            index.query(QueryConditional.keyEqualTo(k -> k.partitionValue(assignee)))
                    .forEach(page -> taskList.addAll(page.items()));
        } else {
            taskTable.scan().items().forEach(taskList::add);
        }

        response.setStatusCode(200);
        response.setBody(OBJECT_MAPPER.writeValueAsString(taskList));
    }

    private void handleUpdate(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response)
            throws JsonProcessingException {
        Task task = parseBody(request.getBody());

        if (task.getId() == null || task.getId().isEmpty()) {
            throw new ValidationException("id is required");
        }

        Task existing = taskTable.getItem(Key.builder().partitionValue(task.getId()).build());
        if (existing == null) {
            writeError(response, 404, "Task not found: " + task.getId());
            return;
        }

        // 更新リクエストに含まれない項目は既存の値を維持する。
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus(existing.getStatus());
        }
        if (task.getAssignee() == null || task.getAssignee().trim().isEmpty()) {
            task.setAssignee(existing.getAssignee());
        }
        if (task.getCreatedAt() == null || task.getCreatedAt().isEmpty()) {
            task.setCreatedAt(existing.getCreatedAt());
        }
        if (task.getPriority() == null || task.getPriority().isEmpty()) {
            task.setPriority(existing.getPriority() != null ? existing.getPriority() : "MEDIUM");
        }
        if (task.getOrder() == null) {
            task.setOrder(existing.getOrder() != null ? existing.getOrder() : System.currentTimeMillis());
        }
        if (task.getDueDate() == null) {
            task.setDueDate(existing.getDueDate());
        }
        if (task.getDescription() == null) {
            task.setDescription(existing.getDescription());
        }

        validateTask(task);

        taskTable.updateItem(task);
        response.setStatusCode(200);
        response.setBody(OBJECT_MAPPER.writeValueAsString(task));
    }

    private void handleDelete(APIGatewayProxyRequestEvent request, APIGatewayProxyResponseEvent response) {
        String taskId = request.getQueryStringParameters() != null
                ? request.getQueryStringParameters().get("id")
                : null;

        if (taskId == null || taskId.isEmpty()) {
            writeError(response, 400, "Missing id parameter");
            return;
        }

        Key key = Key.builder().partitionValue(taskId).build();
        Task existing = taskTable.getItem(key);
        if (existing == null) {
            writeError(response, 404, "Task not found: " + taskId);
            return;
        }

        taskTable.deleteItem(key);
        response.setStatusCode(200);
        response.setBody("{\"message\": \"Deleted successfully\"}");
    }

    private Task parseBody(String body) throws JsonProcessingException {
        if (body == null || body.isEmpty()) {
            throw new ValidationException("Request body is required");
        }
        try {
            return OBJECT_MAPPER.readValue(body, Task.class);
        } catch (JsonMappingException e) {
            throw new ValidationException("Request body does not match the expected task format");
        }
    }

    private void validateTask(Task task) {
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new ValidationException("title is required");
        }
        if (task.getTitle().length() > MAX_TITLE_LENGTH) {
            throw new ValidationException("title must be " + MAX_TITLE_LENGTH + " characters or fewer");
        }
        if (task.getAssignee() != null && task.getAssignee().length() > MAX_ASSIGNEE_LENGTH) {
            throw new ValidationException("assignee must be " + MAX_ASSIGNEE_LENGTH + " characters or fewer");
        }
        if (task.getStatus() != null && !ALLOWED_STATUSES.contains(task.getStatus())) {
            throw new ValidationException("status must be one of " + ALLOWED_STATUSES);
        }
        if (task.getPriority() != null && !ALLOWED_PRIORITIES.contains(task.getPriority())) {
            throw new ValidationException("priority must be one of " + ALLOWED_PRIORITIES);
        }
        if (task.getDescription() != null && task.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("description must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer");
        }
    }

    private void writeError(APIGatewayProxyResponseEvent response, int statusCode, String message) {
        response.setStatusCode(statusCode);
        try {
            response.setBody(OBJECT_MAPPER.writeValueAsString(Map.of("error", message)));
        } catch (JsonProcessingException e) {
            response.setBody("{\"error\": \"" + message.replace("\"", "'") + "\"}");
        }
    }

    private static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }
}
