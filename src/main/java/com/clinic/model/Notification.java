package com.clinic.model;

import java.time.LocalDateTime;

/** One message in a user's notification inbox. */
public class Notification {

    private int id;
    private int userId;
    private String message;
    private String category;
    private LocalDateTime createdAt;
    private boolean read;

    public Notification() {
        // for the JSON mapper
    }

    public Notification(int id, int userId, String message, String category, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.message = message;
        this.category = category;
        this.createdAt = createdAt;
        this.read = false;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
