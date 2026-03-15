package com.example.womensafetyapp.models;

public class Notification {
    private int notificationId;
    private int emergencyId;
    private int contactId;
    private String message;
    private String sentAt;
    private String status;

    public Notification(int notificationId, int emergencyId, int contactId, String message, String sentAt, String status) {
        this.notificationId = notificationId;
        this.emergencyId = emergencyId;
        this.contactId = contactId;
        this.message = message;
        this.sentAt = sentAt;
        this.status = status;
    }

    // Getters
    public int getNotificationId() { return notificationId; }
    public int getEmergencyId() { return emergencyId; }
    public int getContactId() { return contactId; }
    public String getMessage() { return message; }
    public String getSentAt() { return sentAt; }
    public String getStatus() { return status; }
}
