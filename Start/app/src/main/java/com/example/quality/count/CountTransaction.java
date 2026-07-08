package com.example.quality.count;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public final List<String> imagePaths;

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
        this(id, type, amount, categoryId, categoryName, parentCategoryName, categoryIcon, date, note, (String) null);
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
        this(id, type, amount, categoryId, categoryName, parentCategoryName, categoryIcon, date, note,
                imagePath == null || imagePath.trim().isEmpty()
                        ? Collections.emptyList()
                        : Collections.singletonList(imagePath));
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
            List<String> imagePaths
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
        List<String> paths = new ArrayList<>();
        if (imagePaths != null) {
            for (String path : imagePaths) {
                if (path != null && !path.trim().isEmpty()) {
                    paths.add(path);
                }
            }
        }
        this.imagePaths = Collections.unmodifiableList(paths);
        this.imagePath = paths.isEmpty() ? null : paths.get(0);
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
