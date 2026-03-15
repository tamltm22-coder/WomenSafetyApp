package com.example.womensafetyapp.firebase;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final FirebaseDatabase firebaseDatabase;
    private final DatabaseReference rootRef;

    // URL của project Firebase thật
    private static final String DATABASE_URL = "https://womensafetyapp-803f0-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private FirebaseManager() {
        firebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL);
        rootRef = firebaseDatabase.getReference();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public DatabaseReference getRootRef() {
        return rootRef;
    }

    public DatabaseReference getRef(String childPath) {
        return rootRef.child(childPath);
    }
}
