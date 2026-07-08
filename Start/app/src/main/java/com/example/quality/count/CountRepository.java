package com.example.quality.count;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CountRepository {
    private final CountDatabaseHelper helper;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private static final DateTimeFormatter POINT_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    public CountRepository(Context context) {
        helper = new CountDatabaseHelper(context.getApplicationContext());
    }

    public List<CountCategory> getCategories(String type) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CountCategory> categories = new ArrayList<>();
        String sql = "SELECT child.id, child.name, child.type, child.icon, child.parent_id, child.level " +
                "FROM " + CountDatabaseHelper.TABLE_CATEGORIES + " child " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON child.parent_id = parent.id " +
                "WHERE child.type = ? " +
                "ORDER BY COALESCE(parent.sort_order, child.sort_order), child.level, child.sort_order, child.id";
        try (Cursor cursor = db.rawQuery(sql, new String[]{type})) {
            while (cursor.moveToNext()) {
                categories.add(readCategory(cursor));
            }
        }
        return categories;
    }

    public List<CountCategory> getRootCategories(String type) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CountCategory> categories = new ArrayList<>();
        String sql = "SELECT id, name, type, icon, parent_id, level FROM " +
                CountDatabaseHelper.TABLE_CATEGORIES +
                " WHERE type = ? AND parent_id IS NULL ORDER BY sort_order, id";
        try (Cursor cursor = db.rawQuery(sql, new String[]{type})) {
            while (cursor.moveToNext()) {
                categories.add(readCategory(cursor));
            }
        }
        return categories;
    }

    public CountCategory getCategory(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT id, name, type, icon, parent_id, level FROM " +
                CountDatabaseHelper.TABLE_CATEGORIES + " WHERE id = ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return readCategory(cursor);
            }
        }
        return null;
    }

    public long addCategory(String name, String type, String iconKey) {
        return addCategory(name, type, iconKey, null);
    }

    public long addCategory(String name, String type, String iconKey, Long parentId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        CountCategory parent = parentId == null ? null : getCategory(parentId);
        String finalType = parent == null ? type : parent.type;
        Long existingId = findCategoryId(db, name, finalType, parentId);
        if (existingId != null) {
            return existingId;
        }
        String icon = parent == null
                ? CategoryIconMapper.normalize(iconKey)
                : CategoryIconMapper.normalize(parent.icon);
        int level = parent == null ? 1 : 2;
        int sortOrder = nextSortOrder(db, finalType, parentId);
        long now = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", finalType);
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
        return db.insert(CountDatabaseHelper.TABLE_CATEGORIES, null, values);
    }

    public void renameCategory(long id, String newName) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", newName);
        values.put("updated_at", System.currentTimeMillis());
        db.update(
                CountDatabaseHelper.TABLE_CATEGORIES,
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public void updateCategory(long id, String newName, String iconKey) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", newName);
        values.put("icon", CategoryIconMapper.normalize(iconKey));
        values.put("updated_at", System.currentTimeMillis());
        db.update(
                CountDatabaseHelper.TABLE_CATEGORIES,
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public long addTransaction(String type, double amount, long categoryId, LocalDate date, String note) {
        return addTransaction(type, amount, categoryId, date, note, null);
    }

    public long addTransaction(String type, double amount, long categoryId, LocalDate date, String note, String imagePath) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("amount", amount);
        values.put("category_id", categoryId);
        values.put("happened_at", toMillis(date));
        values.put("note", note);
        values.put("image_path", imagePath);
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insert(CountDatabaseHelper.TABLE_TRANSACTIONS, null, values);
    }

    public void updateTransaction(long id, String type, double amount, long categoryId, LocalDate date, String note) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("amount", amount);
        values.put("category_id", categoryId);
        values.put("happened_at", toMillis(date));
        values.put("note", note);
        values.put("updated_at", System.currentTimeMillis());
        db.update(
                CountDatabaseHelper.TABLE_TRANSACTIONS,
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public int importTransactions(List<CountImportRecord> records) {
        return importTransactions(records, null);
    }

    public int importTransactions(List<CountImportRecord> records, CountImportResult result) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        int inserted = 0;
        db.beginTransaction();
        try {
            for (CountImportRecord record : records) {
                if (record == null || record.date == null || record.amount <= 0) {
                    continue;
                }
                long categoryId = resolveImportCategoryId(db, record.categoryName, record.type, result);
                long now = System.currentTimeMillis();
                ContentValues values = new ContentValues();
                values.put("type", record.type);
                values.put("amount", record.amount);
                values.put("category_id", categoryId);
                values.put("happened_at", toMillis(record.date));
                values.put("note", record.note);
                values.put("created_at", now);
                values.put("updated_at", now);
                long id = db.insert(CountDatabaseHelper.TABLE_TRANSACTIONS, null, values);
                if (id != -1) {
                    inserted++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return inserted;
    }

    public void deleteTransaction(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(
                CountDatabaseHelper.TABLE_TRANSACTIONS,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    public CountStats getStats(LocalDate start, LocalDate end) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income, " +
                "COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense " +
                "FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS +
                " WHERE happened_at >= ? AND happened_at < ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(toMillis(start)),
                String.valueOf(toMillis(end))
        })) {
            if (cursor.moveToFirst()) {
                return new CountStats(cursor.getDouble(0), cursor.getDouble(1));
            }
        }
        return new CountStats(0, 0);
    }

    public CountTransaction getLargestTransaction(String type, LocalDate start, LocalDate end) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT tx.id, tx.type, tx.amount, tx.category_id, category.name, parent.name, category.icon, tx.happened_at, tx.note, tx.image_path " +
                "FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS + " tx " +
                "JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " category ON tx.category_id = category.id " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON category.parent_id = parent.id " +
                "WHERE tx.type = ? AND tx.happened_at >= ? AND tx.happened_at < ? " +
                "ORDER BY tx.amount DESC, tx.created_at DESC, tx.id DESC LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                type,
                String.valueOf(toMillis(start)),
                String.valueOf(toMillis(end))
        })) {
            if (cursor.moveToFirst()) {
                return readTransaction(cursor);
            }
        }
        return null;
    }

    public CountTransaction getTransaction(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT tx.id, tx.type, tx.amount, tx.category_id, category.name, parent.name, category.icon, tx.happened_at, tx.note, tx.image_path " +
                "FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS + " tx " +
                "JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " category ON tx.category_id = category.id " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON category.parent_id = parent.id " +
                "WHERE tx.id = ? LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return readTransaction(cursor);
            }
        }
        return null;
    }

    public List<CategoryTotal> getCategoryTotals(String type, LocalDate start, LocalDate end) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CategoryTotal> totals = new ArrayList<>();
        String sql = "SELECT " +
                "CASE WHEN parent.name IS NULL THEN category.name ELSE parent.name || ' / ' || category.name END AS display_name, " +
                "category.icon, SUM(tx.amount) AS total " +
                "FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS + " tx " +
                "JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " category ON tx.category_id = category.id " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON category.parent_id = parent.id " +
                "WHERE tx.type = ? AND tx.happened_at >= ? AND tx.happened_at < ? " +
                "GROUP BY category.id, display_name, category.icon " +
                "ORDER BY total DESC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                type,
                String.valueOf(toMillis(start)),
                String.valueOf(toMillis(end))
        })) {
            while (cursor.moveToNext()) {
                totals.add(new CategoryTotal(cursor.getString(0), cursor.getString(1), cursor.getDouble(2)));
            }
        }
        return totals;
    }

    public List<CountTransaction> getTransactions(LocalDate start, LocalDate end) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CountTransaction> transactions = new ArrayList<>();
        String sql = "SELECT tx.id, tx.type, tx.amount, tx.category_id, category.name, parent.name, category.icon, tx.happened_at, tx.note, tx.image_path " +
                "FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS + " tx " +
                "JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " category ON tx.category_id = category.id " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON category.parent_id = parent.id " +
                "WHERE tx.happened_at >= ? AND tx.happened_at < ? " +
                "ORDER BY tx.happened_at DESC, tx.created_at DESC, tx.id DESC";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(toMillis(start)),
                String.valueOf(toMillis(end))
        })) {
            while (cursor.moveToNext()) {
                transactions.add(readTransaction(cursor));
            }
        }
        return transactions;
    }

    public List<CountTransaction> getAllTransactions() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CountTransaction> transactions = new ArrayList<>();
        String sql = "SELECT tx.id, tx.type, tx.amount, tx.category_id, category.name, parent.name, category.icon, tx.happened_at, tx.note, tx.image_path " +
                "FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS + " tx " +
                "JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " category ON tx.category_id = category.id " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON category.parent_id = parent.id " +
                "ORDER BY tx.happened_at DESC, tx.created_at DESC, tx.id DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                transactions.add(readTransaction(cursor));
            }
        }
        return transactions;
    }

    public CountCategory getDefaultCategory(String type) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT id, name, type, icon, parent_id, level FROM " +
                CountDatabaseHelper.TABLE_CATEGORIES +
                " WHERE type = ? AND parent_id IS NULL ORDER BY sort_order, id LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{type})) {
            if (cursor.moveToFirst()) {
                return readCategory(cursor);
            }
        }
        return null;
    }

    public List<CountSeriesPoint> getExpenseTrendByDay(LocalDate start, LocalDate end) {
        List<CountSeriesPoint> points = new ArrayList<>();
        LocalDate day = start;
        while (day.isBefore(end)) {
            double expense = getStats(day, day.plusDays(1)).expense;
            points.add(new CountSeriesPoint(day.format(POINT_DAY_FORMATTER), expense));
            day = day.plusDays(1);
        }
        return points;
    }

    public List<CountSeriesPoint> getExpenseTrendByMonth(int year) {
        List<CountSeriesPoint> points = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate start = LocalDate.of(year, month, 1);
            double expense = getStats(start, start.plusMonths(1)).expense;
            points.add(new CountSeriesPoint(String.format(Locale.CHINA, "%02d月", month), expense));
        }
        return points;
    }

    public String formatMoney(double value) {
        return String.format(Locale.CHINA, "%.2f", value);
    }

    private CountCategory readCategory(Cursor cursor) {
        Long parentId = cursor.isNull(4) ? null : cursor.getLong(4);
        return new CountCategory(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                parentId,
                cursor.getInt(5)
        );
    }

    private CountTransaction readTransaction(Cursor cursor) {
        return new CountTransaction(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getDouble(2),
                cursor.getLong(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getString(6),
                fromMillis(cursor.getLong(7)),
                cursor.getString(8),
                cursor.getString(9)
        );
    }

    private long toMillis(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    private LocalDate fromMillis(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate();
    }

    private int nextSortOrder(SQLiteDatabase db, String type, Long parentId) {
        String sql;
        String[] args;
        if (parentId == null) {
            sql = "SELECT COALESCE(MAX(sort_order), 0) + 10 FROM " +
                    CountDatabaseHelper.TABLE_CATEGORIES +
                    " WHERE type = ? AND parent_id IS NULL";
            args = new String[]{type};
        } else {
            sql = "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM " +
                    CountDatabaseHelper.TABLE_CATEGORIES +
                    " WHERE type = ? AND parent_id = ?";
            args = new String[]{type, String.valueOf(parentId)};
        }
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return parentId == null ? 10 : 1;
    }

    private Long findCategoryId(SQLiteDatabase db, String name, String type, Long parentId) {
        String sql;
        String[] args;
        if (parentId == null) {
            sql = "SELECT id FROM " + CountDatabaseHelper.TABLE_CATEGORIES +
                    " WHERE name = ? AND type = ? AND parent_id IS NULL LIMIT 1";
            args = new String[]{name, type};
        } else {
            sql = "SELECT id FROM " + CountDatabaseHelper.TABLE_CATEGORIES +
                    " WHERE name = ? AND type = ? AND parent_id = ? LIMIT 1";
            args = new String[]{name, type, String.valueOf(parentId)};
        }
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return null;
    }

    private long resolveImportCategoryId(SQLiteDatabase db, String rawName, String type, CountImportResult result) {
        String name = normalizeImportedCategoryName(rawName);
        if (name.isEmpty()) {
            Long defaultId = findDefaultCategoryId(db, type);
            if (defaultId != null) {
                return defaultId;
            }
            name = "income".equals(type) ? "其他收入" : "其他支出";
        }

        Long existingId = findCategoryIdByDisplayName(db, name, type);
        if (existingId != null) {
            return existingId;
        }
        String leafName = leafImportedCategoryName(name);
        if (!leafName.equals(name)) {
            existingId = findCategoryIdByDisplayName(db, leafName, type);
            if (existingId != null) {
                return existingId;
            }
            name = leafName;
        }

        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("icon", CategoryIconMapper.defaultIcon(type));
        values.putNull("parent_id");
        values.put("level", 1);
        values.put("sort_order", nextSortOrder(db, type, null));
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = db.insert(CountDatabaseHelper.TABLE_CATEGORIES, null, values);
        if (id != -1 && result != null) {
            result.addCreatedCategory(type, name);
        }
        return id;
    }

    private Long findDefaultCategoryId(SQLiteDatabase db, String type) {
        String sql = "SELECT id FROM " + CountDatabaseHelper.TABLE_CATEGORIES +
                " WHERE type = ? AND parent_id IS NULL ORDER BY sort_order, id LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{type})) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return null;
    }

    private Long findCategoryIdByDisplayName(SQLiteDatabase db, String name, String type) {
        String sql = "SELECT child.id, child.name, parent.name " +
                "FROM " + CountDatabaseHelper.TABLE_CATEGORIES + " child " +
                "LEFT JOIN " + CountDatabaseHelper.TABLE_CATEGORIES + " parent ON child.parent_id = parent.id " +
                "WHERE child.type = ? ORDER BY child.level DESC, child.sort_order, child.id";
        try (Cursor cursor = db.rawQuery(sql, new String[]{type})) {
            while (cursor.moveToNext()) {
                String childName = cursor.getString(1);
                String parentName = cursor.getString(2);
                if (name.equals(childName)) {
                    return cursor.getLong(0);
                }
                if (parentName != null) {
                    String compactPath = parentName + "/" + childName;
                    String spacedPath = parentName + " / " + childName;
                    if (name.equals(compactPath) || name.equals(spacedPath)) {
                        return cursor.getLong(0);
                    }
                }
            }
        }
        return null;
    }

    private String normalizeImportedCategoryName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.contains(">")) {
            name = name.substring(name.lastIndexOf('>') + 1).trim();
        }
        return name;
    }

    private String leafImportedCategoryName(String name) {
        String leaf = name;
        if (leaf.contains("/")) {
            leaf = leaf.substring(leaf.lastIndexOf('/') + 1).trim();
        }
        if (leaf.contains("\\")) {
            leaf = leaf.substring(leaf.lastIndexOf('\\') + 1).trim();
        }
        return leaf.isEmpty() ? name : leaf;
    }

}
