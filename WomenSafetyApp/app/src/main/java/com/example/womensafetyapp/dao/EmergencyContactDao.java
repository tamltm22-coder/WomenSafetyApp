package com.example.womensafetyapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.womensafetyapp.database.DatabaseHelper;

public class EmergencyContactDao {
    private final SQLiteDatabase db;

    public EmergencyContactDao(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    public long addContact(int userId, String name, String phone, String relation) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("name", name);
        v.put("phone", phone);
        v.put("relation", relation);
        return db.insert("Emergency_Contacts", null, v);
    }

    public Cursor getContacts(int userId) {
        return db.rawQuery("SELECT * FROM Emergency_Contacts WHERE user_id=?", new String[]{String.valueOf(userId)});
    }

    public int deleteContact(int contactId) {
        return db.delete("Emergency_Contacts", "contact_id=?", new String[]{String.valueOf(contactId)});
    }

}
