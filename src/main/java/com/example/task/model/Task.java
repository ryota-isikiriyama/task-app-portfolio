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
    private String dueDate;    // 締切日 (yyyy-MM-dd, 任意)
    private String priority;   // 優先度 (LOW / MEDIUM / HIGH)
    private Long order;        // カラム内の表示順（値が小さいほど上に表示）
    private String description; // 詳細情報（任意、複数行可）

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

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Long getOrder() { return order; }
    public void setOrder(Long order) { this.order = order; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
