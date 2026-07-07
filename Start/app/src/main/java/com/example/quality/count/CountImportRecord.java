package com.example.quality.count;

import java.time.LocalDate;

public class CountImportRecord {
    public final String type;
    public final double amount;
    public final String categoryName;
    public final LocalDate date;
    public final String note;

    public CountImportRecord(
            String type,
            double amount,
            String categoryName,
            LocalDate date,
            String note
    ) {
        this.type = type;
        this.amount = amount;
        this.categoryName = categoryName;
        this.date = date;
        this.note = note;
    }
}
