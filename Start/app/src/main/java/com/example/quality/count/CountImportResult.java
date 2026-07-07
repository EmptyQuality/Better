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
}
