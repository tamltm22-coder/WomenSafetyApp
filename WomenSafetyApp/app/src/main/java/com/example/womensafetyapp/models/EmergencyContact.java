package com.example.womensafetyapp.models;

public class EmergencyContact {
    private int contact_id;
    private int user_id;
    private String name;
    private String phone;
    private String relation;

    // NEW: key để xóa/sync trên Firebase
    private String firebaseKey;

    public EmergencyContact() {} // bắt buộc cho Firebase

    public EmergencyContact(int contact_id, int user_id, String name, String phone, String relation) {
        this.contact_id = contact_id;
        this.user_id = user_id;
        this.name = name;
        this.phone = phone;
        this.relation = relation;
    }

    // getters
    public int getContact_id() { return contact_id; }
    public int getUser_id() { return user_id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getRelation() { return relation; }
    public String getFirebaseKey() { return firebaseKey; }

    // setters
    public void setContact_id(int contact_id) { this.contact_id = contact_id; }
    public void setUser_id(int user_id) { this.user_id = user_id; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setRelation(String relation) { this.relation = relation; }
    public void setFirebaseKey(String firebaseKey) { this.firebaseKey = firebaseKey; }
}
