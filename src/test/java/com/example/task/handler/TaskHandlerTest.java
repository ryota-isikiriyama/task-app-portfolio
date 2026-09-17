package com.example.task.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.task.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskHandlerTest {

    @SuppressWarnings("unchecked")
    private final DynamoDbTable<Task> taskTable = mock(DynamoDbTable.class);
    private TaskHandler handler;
    private Context context;

    @BeforeEach
    void setUp() {
        handler = new TaskHandler(taskTable);
        context = mock(Context.class);
        when(context.getLogger()).thenReturn(mock(LambdaLogger.class));
    }

    private APIGatewayProxyRequestEvent requestWithBody(String method, String body) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod(method);
        request.setBody(body);
        return request;
    }

    @Test
    void optionsRequestReturns200WithoutTouchingDynamoDb() {
        APIGatewayProxyRequestEvent request = requestWithBody("OPTIONS", null);
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(200, response.getStatusCode());
    }

    @Test
    void postWithEmptyTitleReturns400() {
        APIGatewayProxyRequestEvent request = requestWithBody("POST", "{\"title\": \"  \"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("title"));
    }

    @Test
    void postWithInvalidJsonReturns400() {
        APIGatewayProxyRequestEvent request = requestWithBody("POST", "{not-json");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void postWithValidTitleFillsDefaultsAndReturns201() throws Exception {
        APIGatewayProxyRequestEvent request = requestWithBody("POST", "{\"title\": \"新しいタスク\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"PENDING\""));
        assertTrue(response.getBody().contains("\"assignee\":\"未設定\""));
        assertTrue(response.getBody().contains("\"priority\":\"MEDIUM\""));
    }

    @Test
    void postWithInvalidStatusReturns400() {
        APIGatewayProxyRequestEvent request = requestWithBody(
                "POST", "{\"title\": \"タスク\", \"status\": \"NOT_A_STATUS\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void postWithTooLongTitleReturns400() {
        String longTitle = "a".repeat(201);
        APIGatewayProxyRequestEvent request = requestWithBody("POST", "{\"title\": \"" + longTitle + "\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void postWithDescriptionStoresIt() {
        APIGatewayProxyRequestEvent request = requestWithBody(
                "POST", "{\"title\": \"タスク\", \"description\": \"詳細な補足情報\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("\"description\":\"詳細な補足情報\""));
    }

    @Test
    void postWithTooLongDescriptionReturns400() {
        String longDescription = "a".repeat(2001);
        APIGatewayProxyRequestEvent request = requestWithBody(
                "POST", "{\"title\": \"タスク\", \"description\": \"" + longDescription + "\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void putForUnknownIdReturns404() {
        when(taskTable.getItem(any(Key.class))).thenReturn(null);
        APIGatewayProxyRequestEvent request = requestWithBody(
                "PUT", "{\"id\": \"missing-id\", \"title\": \"更新\", \"status\": \"PENDING\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(404, response.getStatusCode());
    }

    @Test
    void putWithoutIdReturns400() {
        APIGatewayProxyRequestEvent request = requestWithBody("PUT", "{\"title\": \"更新\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void putKeepsExistingCreatedAtWhenNotProvided() {
        Task existing = new Task("id-1", "既存タイトル", "PENDING", "山田", "2026/01/01 10:00");
        when(taskTable.getItem(any(Key.class))).thenReturn(existing);

        APIGatewayProxyRequestEvent request = requestWithBody(
                "PUT", "{\"id\": \"id-1\", \"title\": \"更新後\", \"status\": \"IN_PROGRESS\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("2026/01/01 10:00"));
    }

    @Test
    void putPreservesDescriptionWhenOmitted() {
        Task existing = new Task("id-1", "既存タイトル", "PENDING", "山田", "2026/01/01 10:00");
        existing.setDescription("元々の詳細情報");
        when(taskTable.getItem(any(Key.class))).thenReturn(existing);

        APIGatewayProxyRequestEvent request = requestWithBody(
                "PUT", "{\"id\": \"id-1\", \"title\": \"更新後\", \"status\": \"IN_PROGRESS\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("元々の詳細情報"));
    }

    @Test
    void deleteWithoutIdReturns400() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("DELETE");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void deleteForUnknownIdReturns404() {
        when(taskTable.getItem(any(Key.class))).thenReturn(null);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("DELETE");
        request.setQueryStringParameters(Map.of("id", "missing-id"));
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(404, response.getStatusCode());
    }

    @Test
    void deleteForKnownIdReturns200() {
        Task existing = new Task("id-1", "タスク", "PENDING", "山田", "2026/01/01 10:00");
        when(taskTable.getItem(any(Key.class))).thenReturn(existing);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("DELETE");
        request.setQueryStringParameters(Map.of("id", "id-1"));
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
        assertEquals(200, response.getStatusCode());
    }

    @Test
    void unexpectedExceptionIsNotLeakedToClient() {
        when(taskTable.getItem(any(Key.class))).thenThrow(new RuntimeException("secret internal detail"));
        APIGatewayProxyRequestEvent request = requestWithBody(
                "PUT", "{\"id\": \"id-1\", \"title\": \"更新\", \"status\": \"PENDING\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal server error"));
        assertTrue(!response.getBody().contains("secret internal detail"));
    }
}
