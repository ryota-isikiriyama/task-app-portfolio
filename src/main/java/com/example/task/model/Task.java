package com.example.task.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class Task {
    private String id;
    private String title;
    private String status;
    private String assignee;   // 入力者・担当者名
    private String createdAt;  // 作成日時

    public Task() {}

    public Task(String id, String title, String status, String assignee, String createdAt) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.assignee = assignee;
        this.createdAt = createdAt;
    }

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}