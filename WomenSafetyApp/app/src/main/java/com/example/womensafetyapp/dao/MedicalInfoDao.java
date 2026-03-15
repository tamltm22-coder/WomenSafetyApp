package com.example.womensafetyapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.womensafetyapp.database.DatabaseHelper;
import com.example.womensafetyapp.models.MedicalInfo;

public class MedicalInfoDao {
    private final DatabaseHelper dbHelper;

    public MedicalInfoDao(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void insertOrUpdate(MedicalInfo info) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uid", info.getUid());
        values.put("blood_type", info.getBlood_type());
        values.put("allergies", info.getAllergies());
        values.put("conditions", info.getConditions());
        values.put("medications", info.getMedications());

        int rows = db.update("Medical_Info", values, "uid = ?", new String[]{info.getUid()});
        if (rows == 0) db.insert("Medical_Info", null, values);
        db.close();
    }

    public MedicalInfo getByUserUid(String uid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM Medical_Info WHERE uid = ?", new String[]{uid});
        MedicalInfo info = null;

        if (cursor.moveToFirst()) {
            info = new MedicalInfo(
                    cursor.getString(cursor.getColumnIndexOrThrow("uid")),
                    cursor.getString(cursor.getColumnIndexOrThrow("blood_type")),
                    cursor.getString(cursor.getColumnIndexOrThrow("allergies")),
                    cursor.getString(cursor.getColumnIndexOrThrow("conditions")),
                    cursor.getString(cursor.getColumnIndexOrThrow("medications"))
            );
        }
        cursor.close();
        db.close();
        return info;
    }
}
