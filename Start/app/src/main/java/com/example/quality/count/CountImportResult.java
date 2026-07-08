package com.example.quality.count;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CountImportResult {
    public int totalRows;
    public int insertedRows;
    public int skippedRows;
    public LocalDate latestDate;
    private final List<String> messages = new ArrayList<>();
    private final List<String> createdCategories = new ArrayList<>();

    public void addSkipped(String message) {
        skippedRows++;
        if (messages.size() < 8) {
            messages.add(message);
        }
    }

    public void markImported(LocalDate date) {
        insertedRows++;
        if (date != null && (latestDate == null || date.isAfter(latestDate))) {
            latestDate = date;
        }
    }

    public List<String> messages() {
        return Collections.unmodifiableList(messages);
    }

    public void addCreatedCategory(String type, String name) {
        String label = ("income".equals(type) ? "收入：" : "支出：") + name;
        if (!createdCategories.contains(label)) {
            createdCategories.add(label);
        }
    }

    public List<String> createdCategories() {
        return Collections.unmodifiableList(createdCategories);
    }
}
