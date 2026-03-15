package com.example.womensafetyapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.womensafetyapp.database.DatabaseHelper;
import com.example.womensafetyapp.models.User;

public class UserDao {
    private final DatabaseHelper dbHelper;

    public UserDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void insertOrUpdate(User user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uid", user.getUid());
        values.put("fullname", user.getFullname());
        values.put("phone", user.getPhone());
        values.put("email", user.getEmail());
        values.put("password", user.getPassword());

        int rows = db.update("Users", values, "uid = ?", new String[]{user.getUid()});
        if (rows == 0) db.insert("Users", null, values);
        db.close();
    }

    public User getUserByUid(String uid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM Users WHERE uid = ?", new String[]{uid});
        User user = null;

        if (cursor.moveToFirst()) {
            user = new User(
                    cursor.getString(cursor.getColumnIndexOrThrow("uid")),
                    cursor.getString(cursor.getColumnIndexOrThrow("fullname")),
                    cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                    cursor.getString(cursor.getColumnIndexOrThrow("email")),
                    cursor.getString(cursor.getColumnIndexOrThrow("password"))
            );
        }
        cursor.close();
        db.close();
        return user;
    }
}
