package com.example.womensafetyapp.models;

public class User {
    private String uid;      // UID Firebase
    private String fullname;
    private String phone;
    private String email;
    private String password;

    public User() {}

    public User(String uid, String fullname, String phone, String email, String password) {
        this.uid = uid;
        this.fullname = fullname;
        this.phone = phone;
        this.email = email;
        this.password = password;
    }

    // Getters & Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
