package com.example.quality.count;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CountDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "better_count.db";
    private static final int DATABASE_VERSION = 2;

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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
        onCreate(db);
    }

    private void seedDefaultCategories(SQLiteDatabase db) {
        insertCategory(db, "餐饮", "expense", "food", null, 1, 10);
        insertCategory(db, "交通", "expense", "car", null, 1, 20);
        insertCategory(db, "购物", "expense", "goods", null, 1, 30);
        insertCategory(db, "医疗", "expense", "medic", null, 1, 40);
        insertCategory(db, "学习", "expense", "learning", null, 1, 50);
        insertCategory(db, "水电费", "expense", "bottle", null, 1, 60);
        insertCategory(db, "服饰", "expense", "cloth", null, 1, 70);
        insertCategory(db, "数码", "expense", "digital", null, 1, 80);
        insertCategory(db, "日用", "expense", "tissue", null, 1, 90);
        insertCategory(db, "书籍", "expense", "book", null, 1, 100);

        insertCategory(db, "工资", "income", "salary", null, 1, 10);
        insertCategory(db, "兼职", "income", "parttime_job", null, 1, 20);
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
