package com.example.womensafetyapp.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class UserProfile {
    private int id = 1; // 1 record local

    private String fullName;
    private String phone;
    private String email;
    private String bloodType;
    private String allergies;
    private String conditions;
    private String medications;

    public UserProfile() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // full_name <-> fullName
    @PropertyName("full_name")
    public String getFullName() { return fullName; }
    @PropertyName("full_name")
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // blood_type <-> bloodType
    @PropertyName("blood_type")
    public String getBloodType() { return bloodType; }
    @PropertyName("blood_type")
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getMedications() { return medications; }
    public void setMedications(String medications) { this.medications = medications; }
}
