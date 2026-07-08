package com.example.quality.count;

import java.time.LocalDate;

public class CountTransaction {
    public final long id;
    public final String type;
    public final double amount;
    public final long categoryId;
    public final String categoryName;
    public final String parentCategoryName;
    public final String categoryIcon;
    public final LocalDate date;
    public final String note;
    public final String imagePath;

    public CountTransaction(
            long id,
            String type,
            double amount,
            long categoryId,
            String categoryName,
            String parentCategoryName,
            String categoryIcon,
            LocalDate date,
            String note
    ) {
        this(id, type, amount, categoryId, categoryName, parentCategoryName, categoryIcon, date, note, null);
    }

    public CountTransaction(
            long id,
            String type,
            double amount,
            long categoryId,
            String categoryName,
            String parentCategoryName,
            String categoryIcon,
            LocalDate date,
            String note,
            String imagePath
    ) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.parentCategoryName = parentCategoryName;
        this.categoryIcon = categoryIcon;
        this.date = date;
        this.note = note;
        this.imagePath = imagePath;
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
