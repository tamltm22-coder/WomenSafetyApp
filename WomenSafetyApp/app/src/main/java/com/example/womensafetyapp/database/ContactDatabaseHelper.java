package com.example.womensafetyapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.example.womensafetyapp.models.EmergencyContact;
import com.example.womensafetyapp.models.UserProfile;

import java.util.ArrayList;

public class ContactDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "contacts_db";
    private static final int DB_VERSION = 4; // ⬅️ bump lên 3 để tạo bảng profile

    // ===== CONTACTS TABLE =====
    private static final String TABLE_NAME = "contacts";
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_PHONE = "phone";
    private static final String COL_RELATION = "relation";
    private static final String COL_FB_KEY = "firebase_key";

    // ===== PROFILE TABLE =====
    private static final String TABLE_PROFILE = "profile";
    private static final String P_ID = "id"; // luôn = 1 record
    private static final String P_FULLNAME = "full_name";
    private static final String P_PHONE = "phone";
    private static final String P_EMAIL = "email";
    private static final String P_BLOOD = "blood";
    private static final String P_ALLERGIES = "allergies";
    private static final String P_CONDITIONS = "conditions";
    private static final String P_MEDICATIONS = "medications";
    // ===== LOCAL NUMBERS TABLE (mới) =====
    private static final String TABLE_LOCAL = "local_numbers";
    private static final String L_ID = "id";
    private static final String L_NAME = "name";
    private static final String L_PHONE = "phone";
    public ContactDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Contacts
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT, " +
                COL_PHONE + " TEXT, " +
                COL_RELATION + " TEXT, " +
                COL_FB_KEY + " TEXT)");

        // Profile (1 record)
        db.execSQL("CREATE TABLE " + TABLE_PROFILE + " (" +
                P_ID + " INTEGER PRIMARY KEY, " +      // luôn 1
                P_FULLNAME + " TEXT, " +
                P_PHONE + " TEXT, " +
                P_EMAIL + " TEXT, " +
                P_BLOOD + " TEXT, " +
                P_ALLERGIES + " TEXT, " +
                P_CONDITIONS + " TEXT, " +
                P_MEDICATIONS + " TEXT" +
                ")");
        // Local numbers (mới)
        db.execSQL("CREATE TABLE " + TABLE_LOCAL + " (" +
                L_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                L_NAME + " TEXT, " +
                L_PHONE + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // V2: đã thêm cột firebase_key cho contacts
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_FB_KEY + " TEXT");
        }
        // V3: thêm bảng profile
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PROFILE + " (" +
                    P_ID + " INTEGER PRIMARY KEY, " +
                    P_FULLNAME + " TEXT, " +
                    P_PHONE + " TEXT, " +
                    P_EMAIL + " TEXT, " +
                    P_BLOOD + " TEXT, " +
                    P_ALLERGIES + " TEXT, " +
                    P_CONDITIONS + " TEXT, " +
                    P_MEDICATIONS + " TEXT" +
                    ")");
        }
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_LOCAL + " (" +
                    L_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    L_NAME + " TEXT, " +
                    L_PHONE + " TEXT)");
        }
    }


    // ===== CONTACTS CRUD =====
    // ====== CRUD cho LOCAL NUMBERS ======
    public long addLocalNumber(String name, String phone) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(L_NAME, name);
        cv.put(L_PHONE, phone);
        long row = db.insert(TABLE_LOCAL, null, cv);
        db.close();
        return row;
    }
    public ArrayList<LocalNumber> getAllLocalNumbers() {
        ArrayList<LocalNumber> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + L_ID + "," + L_NAME + "," + L_PHONE +
                " FROM " + TABLE_LOCAL + " ORDER BY " + L_ID + " DESC", null);
        if (c.moveToFirst()) {
            do {
                LocalNumber ln = new LocalNumber(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2)
                );
                list.add(ln);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return list;
    }

    public void deleteLocalNumber(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOCAL, L_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    // Model nhỏ cho local number (nếu bạn chưa có file riêng)
    public static class LocalNumber {
        public int id;
        public String name;
        public String phone;

        public LocalNumber(int id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }
    }

    // Insert contact
    public long addContact(EmergencyContact contact) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, contact.getName());
        cv.put(COL_PHONE, contact.getPhone());
        cv.put(COL_RELATION, contact.getRelation());
        cv.put(COL_FB_KEY, contact.getFirebaseKey()); // có thể null
        long rowId = db.insert(TABLE_NAME, null, cv);
        db.close();
        return rowId;
    }

    // Cập nhật firebaseKey cho bản ghi local theo id
    public void updateFirebaseKey(int id, String firebaseKey) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_FB_KEY, firebaseKey);
        db.update(TABLE_NAME, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    // Get all contacts
    public ArrayList<EmergencyContact> getAllContacts() {
        ArrayList<EmergencyContact> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_ID + "," + COL_NAME + "," + COL_PHONE + "," + COL_RELATION + "," + COL_FB_KEY +
                " FROM " + TABLE_NAME, null);
        if (c.moveToFirst()) {
            do {
                EmergencyContact ec = new EmergencyContact(
                        c.getInt(0), 0, c.getString(1), c.getString(2), c.getString(3));
                ec.setFirebaseKey(c.getString(4));
                list.add(ec);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return list;
    }

    // kiểm tra tồn tại theo firebaseKey
    public boolean existsByFirebaseKey(@Nullable String fbKey) {
        if (fbKey == null) return false;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM " + TABLE_NAME + " WHERE " + COL_FB_KEY + "=? LIMIT 1",
                new String[]{fbKey});
        boolean has = c.moveToFirst();
        c.close();
        db.close();
        return has;
    }

    // Delete contact theo id
    public void deleteContact(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    // ===== PROFILE UPSERT / READ =====

    /** Lưu hoặc cập nhật 1 record profile (id luôn = 1). */
    public void upsertProfile(UserProfile p) {
        if (p == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(P_ID, 1);
        cv.put(P_FULLNAME, p.getFullName());
        cv.put(P_PHONE, p.getPhone());
        cv.put(P_EMAIL, p.getEmail());
        cv.put(P_BLOOD, p.getBloodType());
        cv.put(P_ALLERGIES, p.getAllergies());
        cv.put(P_CONDITIONS, p.getConditions());
        cv.put(P_MEDICATIONS, p.getMedications());

        int rows = db.update(TABLE_PROFILE, cv, P_ID + "=?", new String[]{"1"});
        if (rows == 0) {
            db.insert(TABLE_PROFILE, null, cv);
        }
        db.close();
    }

    /** Lấy profile (trả về null nếu chưa tạo). */
    @Nullable
    public UserProfile getProfile() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_PROFILE, null, P_ID + "=?", new String[]{"1"}, null, null, null);
        UserProfile p = null;
        if (c != null && c.moveToFirst()) {
            p = new UserProfile();
            p.setFullName(c.getString(c.getColumnIndexOrThrow(P_FULLNAME)));
            p.setPhone(c.getString(c.getColumnIndexOrThrow(P_PHONE)));
            p.setEmail(c.getString(c.getColumnIndexOrThrow(P_EMAIL)));
            p.setBloodType(c.getString(c.getColumnIndexOrThrow(P_BLOOD)));
            p.setAllergies(c.getString(c.getColumnIndexOrThrow(P_ALLERGIES)));
            p.setConditions(c.getString(c.getColumnIndexOrThrow(P_CONDITIONS)));
            p.setMedications(c.getString(c.getColumnIndexOrThrow(P_MEDICATIONS)));
        }
        if (c != null) c.close();
        db.close();
        return p;
    }
//    public void wipeContacts() {
//        SQLiteDatabase db = getWritableDatabase();
//        db.delete("contacts", null, null);
//        db.close();
//    }
//    // ContactDatabaseHelper.java
//    public void wipeProfile() {
//        SQLiteDatabase db = getWritableDatabase();
//        db.delete("profile", null, null);
//        db.close();
//    }
// ==== WIPERS ====
public void wipeContacts() {
    SQLiteDatabase db = getWritableDatabase();
    db.delete("contacts", null, null);
    db.close();
}

    public void wipeProfile() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("profile", null, null);
        db.close();
    }

    public void wipeLocalNumbers() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("local_numbers", null, null);
        db.close();
    }

    /** Xoá toàn bộ dữ liệu local của app (contacts/profile/local_numbers). */
//    public void wipeAllLocal() {
//        SQLiteDatabase db = getWritableDatabase();
//        db.delete("contacts", null, null);
//        db.delete("profile", null, null);
//        db.delete("local_numbers", null, null);
//        db.close();
//    }


}
