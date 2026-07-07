package com.example.quality.count;

import java.time.LocalDate;

public class CountTransaction {
    public final long id;
    public final String type;
    public final double amount;
    public final String categoryName;
    public final String parentCategoryName;
    public final String categoryIcon;
    public final LocalDate date;
    public final String note;

    public CountTransaction(
            long id,
            String type,
            double amount,
            String categoryName,
            String parentCategoryName,
            String categoryIcon,
            LocalDate date,
            String note
    ) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.categoryName = categoryName;
        this.parentCategoryName = parentCategoryName;
        this.categoryIcon = categoryIcon;
        this.date = date;
        this.note = note;
    }

    public String displayCategoryName() {
        if (note != null && !note.trim().isEmpty()) {
            return note.trim();
        }
        if (parentCategoryName == null || parentCategoryName.isEmpty()) {
            return categoryName;
        }
        return parentCategoryName + " / " + categoryName;
    }

    public String categoryPath() {
        if (parentCategoryName == null || parentCategoryName.isEmpty()) {
            return categoryName;
        }
        return parentCategoryName + " / " + categoryName;
    }
}
