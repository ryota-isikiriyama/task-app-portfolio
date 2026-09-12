package com.example.task.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.task.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
            DynamoDbClient ddb = DynamoDbClient.builder()
                    .region(Region.AP_NORTHEAST_1)
                    .build();
            DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(ddb)
                    .build();
            DynamoDbTable<Task> taskTable = enhancedClient.table("Tasks", TableSchema.fromBean(Task.class));

            if ("POST".equalsIgnoreCase(httpMethod)) {
                Task task = objectMapper.readValue(request.getBody(), Task.class);
                if (task.getId() == null || task.getId().isEmpty()) {
                    task.setId(UUID.randomUUID().toString());
                }
                if (task.getStatus() == null) {
                    task.setStatus("PENDING");
                }
                if (task.getAssignee() == null || task.getAssignee().trim().isEmpty()) {
                    task.setAssignee("未設定");
                }
                if (task.getCreatedAt() == null || task.getCreatedAt().isEmpty()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
                    task.setCreatedAt(LocalDateTime.now().format(formatter));
                }
                taskTable.putItem(task);
                response.setStatusCode(201);
                response.setBody(objectMapper.writeValueAsString(task));
            } else if ("GET".equalsIgnoreCase(httpMethod)) {
                List<Task> taskList = new ArrayList<>();
                taskTable.scan().items().forEach(taskList::add);
                response.setStatusCode(200);
                response.setBody(objectMapper.writeValueAsString(taskList));
            } else if ("PUT".equalsIgnoreCase(httpMethod)) {
                Task task = objectMapper.readValue(request.getBody(), Task.class);
                taskTable.updateItem(task);
                response.setStatusCode(200);
                response.setBody(objectMapper.writeValueAsString(task));
            } else if ("DELETE".equalsIgnoreCase(httpMethod)) {
                String taskId = request.getQueryStringParameters() != null ? request.getQueryStringParameters().get("id") : null;
                if (taskId != null) {
                    Key key = Key.builder().partitionValue(taskId).build();
                    taskTable.deleteItem(key);
                    response.setStatusCode(200);
                    response.setBody("{\"message\": \"Deleted successfully\"}");
                } else {
                    response.setStatusCode(400);
                    response.setBody("{\"error\": \"Missing id parameter\"}");
                }
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"" + e.toString() + "\"}");
        }

        return response;
    }
}