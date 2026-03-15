package com.example.womensafetyapp.models;

public class MedicalInfo {
    private String uid; // Liên kết theo UID Firebase
    private String blood_type;
    private String allergies;
    private String conditions;
    private String medications;

    public MedicalInfo() {}

    public MedicalInfo(String uid, String blood_type, String allergies, String conditions, String medications) {
        this.uid = uid;
        this.blood_type = blood_type;
        this.allergies = allergies;
        this.conditions = conditions;
        this.medications = medications;
    }

    // Getters & Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getBlood_type() { return blood_type; }
    public void setBlood_type(String blood_type) { this.blood_type = blood_type; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getMedications() { return medications; }
    public void setMedications(String medications) { this.medications = medications; }
}
