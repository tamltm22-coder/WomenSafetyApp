package com.example.womensafetyapp.models;

public class Log {
    private int logId;
    private int userId;
    private int emergencyId;
    private String actionType;
    private String message;
    private String createdAt;
    private String status;

    public Log(int logId, int userId, int emergencyId, String actionType, String message, String createdAt, String status) {
        this.logId = logId;
        this.userId = userId;
        this.emergencyId = emergencyId;
        this.actionType = actionType;
        this.message = message;
        this.createdAt = createdAt;
        this.status = status;
    }

    // Getters
    public int getLogId() { return logId; }
    public int getUserId() { return userId; }
    public int getEmergencyId() { return emergencyId; }
    public String getActionType() { return actionType; }
    public String getMessage() { return message; }
    public String getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
}
