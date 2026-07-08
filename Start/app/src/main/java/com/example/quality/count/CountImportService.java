package com.example.quality.count;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CountImportService {
    public static final String SOURCE_WECHAT = "wechat";
    public static final String SOURCE_SHARK = "shark";
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-M-d", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyy.M.d", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.CHINA),
            DateTimeFormatter.ofPattern("M-d-yyyy", Locale.CHINA)
    };

    private CountImportService() {
    }

    public static CountImportResult importFromUri(
            Context context,
            CountRepository repository,
            Uri uri
    ) throws IOException {
        return importFromUri(context, repository, uri, "");
    }

    public static CountImportResult importFromUri(
            Context context,
            CountRepository repository,
            Uri uri,
            String source
    ) throws IOException {
        CountImportResult result = new CountImportResult();
        String fileName = readDisplayName(context, uri);
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("无法打开文件");
            }
            bytes = readAllBytes(input);
        }

        List<List<String>> rows;
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".xls") && !lowerName.endsWith(".xlsx")) {
            result.addSkipped("暂不支持旧版 .xls，请另存为 .xlsx 或 .csv 后再导入");
            return result;
        } else if (lowerName.endsWith(".xlsx") || isZip(bytes)) {
            rows = readXlsxRows(bytes);
        } else {
            rows = readCsvRows(bytes);
        }

        List<CountImportRecord> records = parseRows(rows, result, source);
        if (!records.isEmpty()) {
            result.insertedRows = repository.importTransactions(records, result);
            for (CountImportRecord record : records) {
                if (result.latestDate == null || record.date.isAfter(result.latestDate)) {
                    result.latestDate = record.date;
                }
            }
        }
        if (result.insertedRows == 0 && result.skippedRows == 0) {
            result.addSkipped("没有找到可导入的记账行");
        }
        return result;
    }

    private static String readDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
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
        return uri.getLastPathSegment();
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 3
                && bytes[3] == 4;
    }

    private static List<List<String>> readCsvRows(byte[] bytes) {
        return parseCsv(decodeText(bytes));
    }

    private static String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
                    .replace("\uFEFF", "");
        } catch (CharacterCodingException ignored) {
            return Charset.forName("GBK").decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
                    .replace("\uFEFF", "");
        }
    }

    private static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean hasValue = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                    hasValue = true;
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                addRowIfNeeded(rows, row, hasValue);
                row = new ArrayList<>();
                cell.setLength(0);
                hasValue = false;
            } else {
                cell.append(ch);
                if (!Character.isWhitespace(ch)) {
                    hasValue = true;
                }
            }
        }
        row.add(cell.toString().trim());
        addRowIfNeeded(rows, row, hasValue || cell.length() > 0);
        return rows;
    }

    private static void addRowIfNeeded(List<List<String>> rows, List<String> row, boolean hasValue) {
        if (!hasValue) {
            for (String cell : row) {
                if (cell != null && !cell.trim().isEmpty()) {
                    hasValue = true;
                    break;
                }
            }
        }
        if (hasValue) {
            rows.add(trimTrailing(row));
        }
    }

    private static List<List<String>> readXlsxRows(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> worksheetNames = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                boolean sharedStrings = "xl/sharedStrings.xml".equals(name);
                boolean worksheet = name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml");
                if (sharedStrings || worksheet) {
                    entries.put(name, readAllBytes(zip));
                    if (worksheet) {
                        worksheetNames.add(name);
                    }
                }
            }
        }
        if (worksheetNames.isEmpty()) {
            throw new IOException("Excel 文件里没有可读取的工作表");
        }
        Collections.sort(worksheetNames);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        return parseWorksheet(entries.get(worksheetNames.get(0)), sharedStrings);
    }

    private static List<String> parseSharedStrings(byte[] xml) throws IOException {
        List<String> values = new ArrayList<>();
        if (xml == null) {
            return values;
        }
        try {
            XmlPullParser parser = newParser(xml);
            StringBuilder current = null;
            boolean inText = false;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("si".equals(name)) {
                        current = new StringBuilder();
                    } else if ("t".equals(name) && current != null) {
                        inText = true;
                    }
                } else if (event == XmlPullParser.TEXT && inText && current != null) {
                    current.append(parser.getText());
                } else if (event == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("t".equals(name)) {
                        inText = false;
                    } else if ("si".equals(name) && current != null) {
                        values.add(current.toString());
                        current = null;
                    }
                }
                event = parser.next();
            }
            return values;
        } catch (XmlPullParserException e) {
            throw new IOException("无法解析 Excel 共享文本", e);
        }
    }

    private static List<List<String>> parseWorksheet(byte[] xml, List<String> sharedStrings) throws IOException {
        if (xml == null) {
            throw new IOException("Excel 工作表内容为空");
        }
        List<List<String>> rows = new ArrayList<>();
        try {
            XmlPullParser parser = newParser(xml);
            List<String> row = null;
            StringBuilder cellText = null;
            String cellType = "";
            int cellIndex = 0;
            boolean inCell = false;
            boolean inValue = false;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("row".equals(name)) {
                        row = new ArrayList<>();
                    } else if ("c".equals(name) && row != null) {
                        inCell = true;
                        inValue = false;
                        cellText = new StringBuilder();
                        cellType = valueOrEmpty(parser.getAttributeValue(null, "t"));
                        String ref = valueOrEmpty(parser.getAttributeValue(null, "r"));
                        cellIndex = ref.isEmpty() ? row.size() : columnIndex(ref);
                    } else if (inCell && ("v".equals(name) || "t".equals(name))) {
                        inValue = true;
                    }
                } else if (event == XmlPullParser.TEXT && inValue && cellText != null) {
                    cellText.append(parser.getText());
                } else if (event == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("v".equals(name) || "t".equals(name)) {
                        inValue = false;
                    } else if ("c".equals(name) && row != null && cellText != null) {
                        putCell(row, cellIndex, decodeXlsxCell(cellText.toString(), cellType, sharedStrings));
                        inCell = false;
                        cellText = null;
                    } else if ("row".equals(name) && row != null) {
                        addRowIfNeeded(rows, row, false);
                        row = null;
                    }
                }
                event = parser.next();
            }
            return rows;
        } catch (XmlPullParserException e) {
            throw new IOException("无法解析 Excel 工作表", e);
        }
    }

    private static XmlPullParser newParser(byte[] xml) throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new InputStreamReader(new ByteArrayInputStream(xml), StandardCharsets.UTF_8));
        return parser;
    }

    private static String decodeXlsxCell(String raw, String cellType, List<String> sharedStrings) {
        String value = valueOrEmpty(raw).trim();
        if ("s".equals(cellType)) {
            try {
                int index = Integer.parseInt(value);
                if (index >= 0 && index < sharedStrings.size()) {
                    return sharedStrings.get(index).trim();
                }
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private static int columnIndex(String cellRef) {
        int index = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = Character.toUpperCase(cellRef.charAt(i));
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            index = index * 26 + (ch - 'A' + 1);
        }
        return Math.max(0, index - 1);
    }

    private static void putCell(List<String> row, int index, String value) {
        while (row.size() <= index) {
            row.add("");
        }
        row.set(index, value);
    }

    private static List<CountImportRecord> parseRows(
            List<List<String>> rows,
            CountImportResult result,
            String source
    ) {
        List<CountImportRecord> records = new ArrayList<>();
        int firstDataRow = firstNonEmptyRow(rows);
        if (firstDataRow < 0) {
            return records;
        }

        Columns columns;
        int headerRow = findHeaderRow(rows, firstDataRow, source);
        if (headerRow >= 0) {
            columns = columnsFromHeader(rows.get(headerRow));
            firstDataRow = headerRow + 1;
        } else {
            columns = Columns.defaultOrder();
        }

        if (columns.date < 0) {
            result.addSkipped("缺少“日期”列");
            return records;
        }
        if (columns.amount < 0 && columns.expenseAmount < 0 && columns.incomeAmount < 0) {
            result.addSkipped("缺少“金额”列，或“收入/支出”金额列");
            return records;
        }

        for (int i = firstDataRow; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isRowEmpty(row)) {
                continue;
            }
            result.totalRows++;
            CountImportRecord record = parseRecord(row, columns, i + 1, result, source);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    private static CountImportRecord parseRecord(
            List<String> row,
            Columns columns,
            int rowNumber,
            CountImportResult result,
            String source
    ) {
        LocalDate date = parseDate(cell(row, columns.date));
        if (date == null) {
            result.addSkipped("第 " + rowNumber + " 行：日期无法识别");
            return null;
        }

        String type;
        double amount;
        if (columns.amount >= 0) {
            Double rawAmount = parseAmount(cell(row, columns.amount));
            if (rawAmount == null || rawAmount == 0) {
                result.addSkipped("第 " + rowNumber + " 行：金额为空或为 0");
                return null;
            }
            type = parseType(cell(row, columns.type), rawAmount);
            if (type == null) {
                result.addSkipped("第 " + rowNumber + " 行：收/支无法识别");
                return null;
            }
            amount = Math.abs(rawAmount);
        } else {
            Double expense = parseAmount(cell(row, columns.expenseAmount));
            Double income = parseAmount(cell(row, columns.incomeAmount));
            if (expense != null && expense > 0) {
                type = TYPE_EXPENSE;
                amount = expense;
            } else if (income != null && income > 0) {
                type = TYPE_INCOME;
                amount = income;
            } else {
                result.addSkipped("第 " + rowNumber + " 行：收入/支出金额为空");
                return null;
            }
        }

        String category = cell(row, columns.category).trim();
        String note = buildImportNote(row, columns, category, source);
        return new CountImportRecord(type, amount, category, date, note);
    }

    private static int findHeaderRow(List<List<String>> rows, int startRow, String source) {
        for (int i = startRow; i < rows.size(); i++) {
            if (looksLikeHeader(rows.get(i), source)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstNonEmptyRow(List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (!isRowEmpty(rows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean looksLikeHeader(List<String> row, String source) {
        if (SOURCE_WECHAT.equals(source)) {
            return containsHeaders(row, "交易时间", "收支", "金额元");
        }
        if (SOURCE_SHARK.equals(source)) {
            return containsHeaders(row, "日期", "收支类型", "金额");
        }
        for (String cell : row) {
            String header = normalizeHeader(cell);
            if (isDateHeader(header) || isTypeHeader(header) || isAmountHeader(header)
                    || isCategoryHeader(header) || isNoteHeader(header)
                    || isIncomeAmountHeader(header) || isExpenseAmountHeader(header)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsHeaders(List<String> row, String first, String second, String third) {
        boolean hasFirst = false;
        boolean hasSecond = false;
        boolean hasThird = false;
        for (String cell : row) {
            String header = normalizeHeader(cell);
            hasFirst = hasFirst || first.equals(header);
            hasSecond = hasSecond || second.equals(header);
            hasThird = hasThird || third.equals(header);
        }
        return hasFirst && hasSecond && hasThird;
    }

    private static Columns columnsFromHeader(List<String> row) {
        Columns columns = new Columns();
        for (int i = 0; i < row.size(); i++) {
            String header = normalizeHeader(row.get(i));
            if (columns.date < 0 && isDateHeader(header)) {
                columns.date = i;
            } else if (columns.type < 0 && isTypeHeader(header)) {
                columns.type = i;
            } else if (columns.amount < 0 && isAmountHeader(header)) {
                columns.amount = i;
            } else if (columns.category < 0 && isCategoryHeader(header)) {
                columns.category = i;
            } else if (columns.note < 0 && isNoteHeader(header)) {
                columns.note = i;
            } else if (columns.account < 0 && isAccountHeader(header)) {
                columns.account = i;
            } else if (columns.counterparty < 0 && isCounterpartyHeader(header)) {
                columns.counterparty = i;
            } else if (columns.product < 0 && isProductHeader(header)) {
                columns.product = i;
            } else if (columns.incomeAmount < 0 && isIncomeAmountHeader(header)) {
                columns.incomeAmount = i;
            } else if (columns.expenseAmount < 0 && isExpenseAmountHeader(header)) {
                columns.expenseAmount = i;
            }
        }
        return columns;
    }

    private static String buildImportNote(List<String> row, Columns columns, String category, String source) {
        if (SOURCE_SHARK.equals(source)) {
            String note = cell(row, columns.note).trim();
            return note.isEmpty() ? valueOrEmpty(category).trim() : note;
        }
        StringBuilder builder = new StringBuilder();
        appendNotePart(builder, "交易对方", cell(row, columns.counterparty));
        appendNotePart(builder, "商品", cell(row, columns.product));
        appendNotePart(builder, "备注", cell(row, columns.note));
        return builder.toString();
    }

    private static void appendNotePart(StringBuilder builder, String label, String value) {
        String text = valueOrEmpty(value).trim();
        if (text.isEmpty() || "/".equals(text)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("；");
        }
        builder.append(label).append("：").append(text);
    }

    private static LocalDate parseDate(String value) {
        String text = valueOrEmpty(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (isNumeric(text)) {
            try {
                double serial = Double.parseDouble(text);
                if (serial > 20000 && serial < 80000) {
                    return EXCEL_EPOCH.plusDays((long) Math.floor(serial));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (text.matches("\\d{8}")) {
            try {
                return LocalDate.of(
                        Integer.parseInt(text.substring(0, 4)),
                        Integer.parseInt(text.substring(4, 6)),
                        Integer.parseInt(text.substring(6, 8))
                );
            } catch (RuntimeException ignored) {
            }
        }
        int space = text.indexOf(' ');
        int time = text.indexOf('T');
        int cut = minPositive(space, time);
        if (cut > 0) {
            text = text.substring(0, cut);
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static Double parseAmount(String value) {
        String text = valueOrEmpty(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        boolean wrappedNegative = text.startsWith("(") && text.endsWith(")");
        text = text.replace(",", "")
                .replace("￥", "")
                .replace("¥", "")
                .replace("元", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "");
        if (text.isEmpty() || "-".equals(text) || "+".equals(text)) {
            return null;
        }
        try {
            double amount = Double.parseDouble(text);
            return wrappedNegative ? -amount : amount;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String parseType(String value, double rawAmount) {
        String text = normalizeHeader(value);
        if (text.contains("中性")) {
            return null;
        }
        if (text.contains("收入") || text.equals("收") || text.equals("income")
                || text.equals("in") || text.equals("+") || text.equals("入账")) {
            return TYPE_INCOME;
        }
        if (text.contains("支出") || text.equals("支") || text.equals("expense")
                || text.equals("out") || text.equals("-") || text.equals("出账")) {
            return TYPE_EXPENSE;
        }
        if (text.isEmpty()) {
            return rawAmount < 0 ? TYPE_EXPENSE : null;
        }
        return rawAmount < 0 ? TYPE_EXPENSE : null;
    }

    private static boolean isDateHeader(String header) {
        return header.equals("日期") || header.equals("时间") || header.equals("记账日期")
                || header.equals("账单日期") || header.equals("交易时间")
                || header.equals("date") || header.equals("day")
                || header.equals("happenedat");
    }

    private static boolean isTypeHeader(String header) {
        return header.equals("类型") || header.equals("收支") || header.equals("收支类型") || header.equals("方向")
                || header.equals("type");
    }

    private static boolean isAmountHeader(String header) {
        return header.equals("金额") || header.equals("金额元") || header.equals("money")
                || header.equals("amount") || header.equals("数额");
    }

    private static boolean isIncomeAmountHeader(String header) {
        return header.equals("收入") || header.equals("收入金额") || header.equals("inflow")
                || header.equals("income");
    }

    private static boolean isExpenseAmountHeader(String header) {
        return header.equals("支出") || header.equals("支出金额") || header.equals("outflow")
                || header.equals("expense");
    }

    private static boolean isCategoryHeader(String header) {
        return header.equals("分类") || header.equals("类别") || header.equals("category")
                || header.equals("账目分类") || header.equals("交易类型");
    }

    private static boolean isNoteHeader(String header) {
        return header.equals("备注") || header.equals("名称") || header.equals("说明")
                || header.equals("描述") || header.equals("note") || header.equals("memo")
                || header.equals("name") || header.equals("description");
    }

    private static boolean isAccountHeader(String header) {
        return header.equals("账户") || header.equals("账号") || header.equals("account");
    }

    private static boolean isCounterpartyHeader(String header) {
        return header.equals("交易对方") || header.equals("对方") || header.equals("商户")
                || header.equals("merchant") || header.equals("counterparty");
    }

    private static boolean isProductHeader(String header) {
        return header.equals("商品") || header.equals("商品名称") || header.equals("项目")
                || header.equals("product") || header.equals("item");
    }

    private static String normalizeHeader(String value) {
        return valueOrEmpty(value).toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("/", "")
                .replace("：", "")
                .replace(":", "")
                .replace("（", "")
                .replace("）", "")
                .replace("(", "")
                .replace(")", "")
                .trim();
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return valueOrEmpty(row.get(index));
    }

    private static boolean isRowEmpty(List<String> row) {
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> trimTrailing(List<String> row) {
        int last = row.size() - 1;
        while (last >= 0 && valueOrEmpty(row.get(last)).isEmpty()) {
            last--;
        }
        List<String> trimmed = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            trimmed.add(valueOrEmpty(row.get(i)).trim());
        }
        return trimmed;
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int minPositive(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static class Columns {
        int date = -1;
        int type = -1;
        int amount = -1;
        int incomeAmount = -1;
        int expenseAmount = -1;
        int category = -1;
        int note = -1;
        int account = -1;
        int counterparty = -1;
        int product = -1;

        static Columns defaultOrder() {
            Columns columns = new Columns();
            columns.date = 0;
            columns.type = 1;
            columns.amount = 2;
            columns.category = 3;
            columns.note = 4;
            return columns;
        }
    }
}
