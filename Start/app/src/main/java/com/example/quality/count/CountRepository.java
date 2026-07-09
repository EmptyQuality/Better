package com.example.quality.count;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CountRepository {
    private static final String CUSTOM_ICON_DIR = "category_icons";
    private static final String CUSTOM_ICON_SOURCE_SINGLE = "single";
    private static final String CUSTOM_ICON_SOURCE_PACK = "pack";
    private static final long MAX_CUSTOM_ICON_BYTES = 2 * 1024 * 1024;
    private static final long MAX_ICON_PACK_BYTES = 30 * 1024 * 1024;

    private final Context appContext;
    private final CountDatabaseHelper helper;
    private final ZoneId zoneId = ZoneId.systemDefault();
    private static final DateTimeFormatter POINT_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    public CountRepository(Context context) {
        appContext = context.getApplicationContext();
        helper = new CountDatabaseHelper(appContext);
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
        return addTransaction(type, amount, categoryId, date, note, (String) null);
    }

    public long addTransaction(String type, double amount, long categoryId, LocalDate date, String note, String imagePath) {
        List<String> imagePaths = new ArrayList<>();
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            imagePaths.add(imagePath);
        }
        return addTransaction(type, amount, categoryId, date, note, imagePaths);
    }

    public long addTransaction(String type, double amount, long categoryId, LocalDate date, String note, List<String> imagePaths) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("amount", amount);
        values.put("category_id", categoryId);
        values.put("happened_at", toMillis(date));
        values.put("note", note);
        values.put("image_path", firstImagePath(imagePaths));
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = db.insert(CountDatabaseHelper.TABLE_TRANSACTIONS, null, values);
        if (id != -1) {
            replaceTransactionImages(db, id, imagePaths);
        }
        return id;
    }

    public void updateTransaction(long id, String type, double amount, long categoryId, LocalDate date, String note) {
        updateTransaction(id, type, amount, categoryId, date, note, getTransactionImagePaths(id));
    }

    public void updateTransaction(long id, String type, double amount, long categoryId, LocalDate date, String note, List<String> imagePaths) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("amount", amount);
        values.put("category_id", categoryId);
        values.put("happened_at", toMillis(date));
        values.put("note", note);
        values.put("image_path", firstImagePath(imagePaths));
        values.put("updated_at", System.currentTimeMillis());
        db.update(
                CountDatabaseHelper.TABLE_TRANSACTIONS,
                values,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
        replaceTransactionImages(db, id, imagePaths);
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

    public int deleteTransactions(LocalDate start, LocalDate end) {
        SQLiteDatabase db = helper.getWritableDatabase();
        String[] args = new String[]{
                String.valueOf(toMillis(start)),
                String.valueOf(toMillis(end))
        };
        db.beginTransaction();
        try {
            db.delete(
                    CountDatabaseHelper.TABLE_TRANSACTION_IMAGES,
                    "transaction_id IN (SELECT id FROM " + CountDatabaseHelper.TABLE_TRANSACTIONS
                            + " WHERE happened_at >= ? AND happened_at < ?)",
                    args
            );
            int deleted = db.delete(
                    CountDatabaseHelper.TABLE_TRANSACTIONS,
                    "happened_at >= ? AND happened_at < ?",
                    args
            );
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
        }
    }

    public List<CountImage> getTransactionImages(long transactionId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CountImage> images = new ArrayList<>();
        String sql = "SELECT id, transaction_id, image_path, sort_order FROM " +
                CountDatabaseHelper.TABLE_TRANSACTION_IMAGES +
                " WHERE transaction_id = ? ORDER BY sort_order, id";
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(transactionId)})) {
            while (cursor.moveToNext()) {
                images.add(new CountImage(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getInt(3)
                ));
            }
        }
        return images;
    }

    public List<String> getTransactionImagePaths(long transactionId) {
        List<String> paths = new ArrayList<>();
        for (CountImage image : getTransactionImages(transactionId)) {
            paths.add(image.imagePath);
        }
        return paths;
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

    public List<CountCustomIcon> getCustomIcons() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<CountCustomIcon> icons = new ArrayList<>();
        String sql = "SELECT id, label, file_name, source, pack_id FROM " +
                CountDatabaseHelper.TABLE_CUSTOM_ICONS +
                " ORDER BY created_at DESC, label";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                icons.add(readCustomIcon(cursor));
            }
        }
        return icons;
    }

    public CountCustomIcon getCustomIcon(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT id, label, file_name, source, pack_id FROM " +
                CountDatabaseHelper.TABLE_CUSTOM_ICONS +
                " WHERE id = ? LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, new String[]{id})) {
            if (cursor.moveToFirst()) {
                return readCustomIcon(cursor);
            }
        }
        return null;
    }

    public CountCustomIcon importCustomIcon(Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("Icon uri is empty");
        }
        byte[] bytes;
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open selected icon");
            }
            bytes = readLimitedBytes(input, MAX_CUSTOM_ICON_BYTES);
        }
        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.length) == null) {
            throw new IOException("Selected file is not a supported image");
        }

        String id = "icon_" + UUID.randomUUID().toString().replace("-", "");
        String displayName = readDisplayName(uri);
        String extension = extensionFromName(displayName);
        if (extension.isEmpty()) {
            extension = ".png";
        }
        String fileName = id + extension;
        File target = customIconFile(fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create icon directory");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes);
        }

        String label = labelFromDisplayName(displayName);
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("label", label.isEmpty() ? "自定义图标" : label);
        values.put("file_name", fileName);
        values.put("source", CUSTOM_ICON_SOURCE_SINGLE);
        values.putNull("pack_id");
        values.put("created_at", now);
        db.insert(CountDatabaseHelper.TABLE_CUSTOM_ICONS, null, values);
        return new CountCustomIcon(id, label, fileName, CUSTOM_ICON_SOURCE_SINGLE, null);
    }

    public File customIconFile(String fileName) {
        return new File(new File(appContext.getFilesDir(), CUSTOM_ICON_DIR), fileName);
    }

    public CountCustomIcon replaceCustomIcon(String id, Uri uri) throws IOException {
        CountCustomIcon existing = getCustomIcon(id);
        if (existing == null) {
            throw new IOException("Custom icon does not exist");
        }
        ImportedIconFile iconFile = copyCustomIconFile(id, uri);
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("label", iconFile.label.isEmpty() ? existing.label : iconFile.label);
        values.put("file_name", iconFile.fileName);
        db.update(
                CountDatabaseHelper.TABLE_CUSTOM_ICONS,
                values,
                "id = ?",
                new String[]{id}
        );
        if (existing.fileName != null && !existing.fileName.equals(iconFile.fileName)) {
            deleteFileQuietly(customIconFile(existing.fileName));
        }
        return getCustomIcon(id);
    }

    public void deleteCustomIcon(String id) {
        CountCustomIcon existing = getCustomIcon(id);
        if (existing == null) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        String iconRef = CategoryIconMapper.CUSTOM_PREFIX + id;
        long now = System.currentTimeMillis();

        ContentValues expenseValues = new ContentValues();
        expenseValues.put("icon", CategoryIconMapper.DEFAULT_EXPENSE_ICON);
        expenseValues.put("updated_at", now);
        db.update(
                CountDatabaseHelper.TABLE_CATEGORIES,
                expenseValues,
                "icon = ? AND type = ?",
                new String[]{iconRef, "expense"}
        );

        ContentValues incomeValues = new ContentValues();
        incomeValues.put("icon", CategoryIconMapper.DEFAULT_INCOME_ICON);
        incomeValues.put("updated_at", now);
        db.update(
                CountDatabaseHelper.TABLE_CATEGORIES,
                incomeValues,
                "icon = ? AND type = ?",
                new String[]{iconRef, "income"}
        );

        db.delete(
                CountDatabaseHelper.TABLE_CUSTOM_ICONS,
                "id = ?",
                new String[]{id}
        );
        deleteFileQuietly(customIconFile(existing.fileName));
    }

    public List<CountCustomIcon> importCustomIconPack(Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("Icon pack uri is empty");
        }
        String displayName = readDisplayName(uri);
        String packId = "pack_" + UUID.randomUUID().toString().replace("-", "");
        String packName = labelFromDisplayName(displayName);
        int packVersion = 1;
        ArchiveIconPackData archiveData = isRarFileName(displayName)
                ? readRarIconPack(uri)
                : readZipIconPack(uri);
        String manifestText = archiveData.manifestText;
        List<PackedIconFile> packedIcons = archiveData.icons;

        Map<String, String> manifestLabels = new HashMap<>();
        if (manifestText != null && !manifestText.trim().isEmpty()) {
            try {
                JSONObject manifest = new JSONObject(manifestText);
                String manifestName = manifest.optString("name", "").trim();
                if (!manifestName.isEmpty()) {
                    packName = manifestName;
                }
                packVersion = Math.max(1, manifest.optInt("version", 1));
                JSONArray icons = manifest.optJSONArray("icons");
                if (icons != null) {
                    for (int i = 0; i < icons.length(); i++) {
                        JSONObject icon = icons.optJSONObject(i);
                        if (icon == null) {
                            continue;
                        }
                        String file = normalizeZipEntryName(icon.optString("file", ""));
                        String label = icon.optString("label", icon.optString("name", "")).trim();
                        if (!file.isEmpty() && !label.isEmpty()) {
                            manifestLabels.put(file, label);
                        }
                    }
                }
            } catch (JSONException e) {
                throw new IOException("Cannot parse icon pack manifest", e);
            }
        }
        if (packedIcons.isEmpty()) {
            throw new IOException("Icon pack does not contain supported images");
        }
        if (packName.isEmpty()) {
            packName = "图标包";
        }

        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues packValues = new ContentValues();
        packValues.put("id", packId);
        packValues.put("name", packName);
        packValues.put("version", packVersion);
        packValues.put("created_at", now);

        List<CountCustomIcon> imported = new ArrayList<>();
        db.beginTransaction();
        try {
            db.insert(CountDatabaseHelper.TABLE_ICON_PACKS, null, packValues);
            for (PackedIconFile packedIcon : packedIcons) {
                if (BitmapFactory.decodeByteArray(packedIcon.bytes, 0, packedIcon.bytes.length) == null) {
                    continue;
                }
                String id = "icon_" + UUID.randomUUID().toString().replace("-", "");
                String fileName = id + extensionFromName(packedIcon.name);
                if (extensionFromName(fileName).isEmpty()) {
                    fileName = id + ".png";
                }
                File target = customIconFile(fileName);
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Cannot create icon directory");
                }
                try (FileOutputStream output = new FileOutputStream(target)) {
                    output.write(packedIcon.bytes);
                }
                String label = manifestLabels.containsKey(packedIcon.name)
                        ? manifestLabels.get(packedIcon.name)
                        : labelFromEntryName(packedIcon.name);
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("label", label.isEmpty() ? "自定义图标" : label);
                values.put("file_name", fileName);
                values.put("source", CUSTOM_ICON_SOURCE_PACK);
                values.put("pack_id", packId);
                values.put("created_at", now);
                db.insert(CountDatabaseHelper.TABLE_CUSTOM_ICONS, null, values);
                imported.add(new CountCustomIcon(id, label, fileName, CUSTOM_ICON_SOURCE_PACK, packId));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (imported.isEmpty()) {
            throw new IOException("Icon pack images cannot be decoded");
        }
        return imported;
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

    private CountCustomIcon readCustomIcon(Cursor cursor) {
        return new CountCustomIcon(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4)
        );
    }

    private CountTransaction readTransaction(Cursor cursor) {
        long id = cursor.getLong(0);
        List<String> imagePaths = getTransactionImagePaths(id);
        String legacyImagePath = cursor.getString(9);
        if (imagePaths.isEmpty() && legacyImagePath != null && !legacyImagePath.trim().isEmpty()) {
            imagePaths.add(legacyImagePath);
        }
        return new CountTransaction(
                id,
                cursor.getString(1),
                cursor.getDouble(2),
                cursor.getLong(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getString(6),
                fromMillis(cursor.getLong(7)),
                cursor.getString(8),
                imagePaths
        );
    }

    private String firstImagePath(List<String> imagePaths) {
        if (imagePaths == null) {
            return null;
        }
        for (String path : imagePaths) {
            if (path != null && !path.trim().isEmpty()) {
                return path;
            }
        }
        return null;
    }

    private void replaceTransactionImages(SQLiteDatabase db, long transactionId, List<String> imagePaths) {
        db.delete(
                CountDatabaseHelper.TABLE_TRANSACTION_IMAGES,
                "transaction_id = ?",
                new String[]{String.valueOf(transactionId)}
        );
        if (imagePaths == null || imagePaths.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int sort = 0;
        for (String path : imagePaths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("transaction_id", transactionId);
            values.put("image_path", path);
            values.put("sort_order", sort++);
            values.put("created_at", now);
            db.insert(CountDatabaseHelper.TABLE_TRANSACTION_IMAGES, null, values);
        }
    }

    private long toMillis(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    private LocalDate fromMillis(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate();
    }

    private ImportedIconFile copyCustomIconFile(String id, Uri uri) throws IOException {
        byte[] bytes;
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open selected icon");
            }
            bytes = readLimitedBytes(input, MAX_CUSTOM_ICON_BYTES);
        }
        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.length) == null) {
            throw new IOException("Selected file is not a supported image");
        }
        String displayName = readDisplayName(uri);
        String extension = extensionFromName(displayName);
        if (extension.isEmpty()) {
            extension = ".png";
        }
        String fileName = id + "_" + System.currentTimeMillis() + extension;
        File target = customIconFile(fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create icon directory");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes);
        }
        return new ImportedIconFile(fileName, labelFromDisplayName(displayName));
    }

    private byte[] readLimitedBytes(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Icon file is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private byte[] readZipEntryBytes(InputStream input, long maxBytes) throws IOException {
        return readLimitedBytes(input, maxBytes);
    }

    private ArchiveIconPackData readZipIconPack(Uri uri) throws IOException {
        String manifestText = null;
        List<PackedIconFile> packedIcons = new ArrayList<>();
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open selected icon pack");
            }
            ZipInputStream zip = new ZipInputStream(input, Charset.forName("GBK"));
            ZipEntry entry;
            long totalBytes = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeZipEntryName(entry.getName());
                if (name.isEmpty()) {
                    continue;
                }
                byte[] bytes = readZipEntryBytes(zip, MAX_CUSTOM_ICON_BYTES);
                totalBytes += bytes.length;
                if (totalBytes > MAX_ICON_PACK_BYTES) {
                    throw new IOException("Icon pack is too large");
                }
                if ("manifest.json".equalsIgnoreCase(name) || name.endsWith("/manifest.json")) {
                    manifestText = new String(bytes, StandardCharsets.UTF_8);
                } else if (isSupportedIconEntry(name)) {
                    packedIcons.add(new PackedIconFile(name, bytes));
                }
            }
        }
        return new ArchiveIconPackData(manifestText, packedIcons);
    }

    private ArchiveIconPackData readRarIconPack(Uri uri) throws IOException {
        String manifestText = null;
        List<PackedIconFile> packedIcons = new ArrayList<>();
        try (InputStream input = appContext.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open selected icon pack");
            }
            try (Archive archive = new Archive(input)) {
                FileHeader header;
                long totalBytes = 0;
                while ((header = archive.nextFileHeader()) != null) {
                    if (header.isDirectory()) {
                        continue;
                    }
                    String name = normalizeZipEntryName(header.getFileName());
                    if (name.isEmpty()) {
                        continue;
                    }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    archive.extractFile(header, output);
                    byte[] bytes = output.toByteArray();
                    if (bytes.length > MAX_CUSTOM_ICON_BYTES) {
                        throw new IOException("Icon file is too large");
                    }
                    totalBytes += bytes.length;
                    if (totalBytes > MAX_ICON_PACK_BYTES) {
                        throw new IOException("Icon pack is too large");
                    }
                    if ("manifest.json".equalsIgnoreCase(name) || name.endsWith("/manifest.json")) {
                        manifestText = new String(bytes, StandardCharsets.UTF_8);
                    } else if (isSupportedIconEntry(name)) {
                        packedIcons.add(new PackedIconFile(name, bytes));
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Cannot read RAR icon pack", e);
        }
        return new ArchiveIconPackData(manifestText, packedIcons);
    }

    private String normalizeZipEntryName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("../") || normalized.equals("..")) {
            return "";
        }
        return normalized;
    }

    private boolean isSupportedIconEntry(String name) {
        String extension = extensionFromName(name);
        return ".png".equals(extension) || ".webp".equals(extension)
                || ".jpg".equals(extension) || ".jpeg".equals(extension);
    }

    private boolean isRarFileName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".rar");
    }

    private String readDisplayName(Uri uri) {
        try (Cursor cursor = appContext.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "" : fallback;
    }

    private String extensionFromName(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "";
        }
        String extension = name.substring(dot).toLowerCase(Locale.ROOT);
        if (".png".equals(extension) || ".webp".equals(extension) || ".jpg".equals(extension)
                || ".jpeg".equals(extension)) {
            return extension;
        }
        return "";
    }

    private String labelFromDisplayName(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String label = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = label.lastIndexOf('.');
        if (dot > 0) {
            label = label.substring(0, dot);
        }
        return label.trim();
    }

    private String labelFromEntryName(String name) {
        if (name == null) {
            return "";
        }
        int slash = name.lastIndexOf('/');
        String label = slash >= 0 ? name.substring(slash + 1) : name;
        return labelFromDisplayName(label);
    }

    private void deleteFileQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static class ImportedIconFile {
        final String fileName;
        final String label;

        ImportedIconFile(String fileName, String label) {
            this.fileName = fileName;
            this.label = label;
        }
    }

    private static class PackedIconFile {
        final String name;
        final byte[] bytes;

        PackedIconFile(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    private static class ArchiveIconPackData {
        final String manifestText;
        final List<PackedIconFile> icons;

        ArchiveIconPackData(String manifestText, List<PackedIconFile> icons) {
            this.manifestText = manifestText;
            this.icons = icons;
        }
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
