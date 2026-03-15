package com.example.womensafetyapp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "womensafetyapp.db";
    private static final int DATABASE_VERSION = 2;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS Users (" +
                "uid TEXT PRIMARY KEY, " +
                "fullname TEXT, " +
                "phone TEXT, " +
                "email TEXT, " +
                "password TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Medical_Info (" +
                "uid TEXT PRIMARY KEY, " +
                "blood_type TEXT, " +
                "allergies TEXT, " +
                "conditions TEXT, " +
                "medications TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS Users");
        db.execSQL("DROP TABLE IF EXISTS Medical_Info");
        onCreate(db);
    }
}
