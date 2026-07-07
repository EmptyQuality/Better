package com.example.quality.count;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CountDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "better_count.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CATEGORIES = "count_categories";
    public static final String TABLE_TRANSACTIONS = "count_transactions";

    public CountDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CATEGORIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "icon TEXT NOT NULL, " +
                "parent_id INTEGER, " +
                "level INTEGER NOT NULL DEFAULT 1, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "FOREIGN KEY(parent_id) REFERENCES " + TABLE_CATEGORIES + "(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX idx_count_categories_type_parent ON " +
                TABLE_CATEGORIES + "(type, parent_id, sort_order)");

        db.execSQL("CREATE TABLE " + TABLE_TRANSACTIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "category_id INTEGER NOT NULL, " +
                "happened_at INTEGER NOT NULL, " +
                "note TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "FOREIGN KEY(category_id) REFERENCES " + TABLE_CATEGORIES + "(id)" +
                ")");
        db.execSQL("CREATE INDEX idx_count_transactions_period ON " +
                TABLE_TRANSACTIONS + "(happened_at, type)");
        db.execSQL("CREATE INDEX idx_count_transactions_category ON " +
                TABLE_TRANSACTIONS + "(category_id)");

        seedDefaultCategories(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first Count schema. Future schema changes should migrate here.
    }

    private void seedDefaultCategories(SQLiteDatabase db) {
        long food = insertCategory(db, "餐饮", "expense", "餐", null, 1, 10);
        insertCategory(db, "吃饭", "expense", "餐", food, 2, 11);
        insertCategory(db, "饮品", "expense", "餐", food, 2, 12);
        insertCategory(db, "交通", "expense", "行", null, 1, 20);
        insertCategory(db, "购物", "expense", "购", null, 1, 30);
        insertCategory(db, "居家", "expense", "家", null, 1, 40);
        insertCategory(db, "娱乐", "expense", "乐", null, 1, 50);
        insertCategory(db, "医疗", "expense", "医", null, 1, 60);
        insertCategory(db, "学习", "expense", "学", null, 1, 70);
        insertCategory(db, "其他支出", "expense", "其", null, 1, 90);

        insertCategory(db, "工资", "income", "薪", null, 1, 10);
        insertCategory(db, "奖金", "income", "奖", null, 1, 20);
        insertCategory(db, "兼职", "income", "工", null, 1, 30);
        insertCategory(db, "投资", "income", "投", null, 1, 40);
        insertCategory(db, "其他收入", "income", "其", null, 1, 90);
    }

    private long insertCategory(
            SQLiteDatabase db,
            String name,
            String type,
            String icon,
            Long parentId,
            int level,
            int sortOrder
    ) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("icon", icon);
        if (parentId == null) {
            values.putNull("parent_id");
        } else {
            values.put("parent_id", parentId);
        }
        values.put("level", level);
        values.put("sort_order", sortOrder);
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insert(TABLE_CATEGORIES, null, values);
    }
}
