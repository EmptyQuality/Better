package com.example.quality.count;

public class CountCategory {
    public final long id;
    public final String name;
    public final String type;
    public final String icon;
    public final Long parentId;
    public final int level;

    public CountCategory(long id, String name, String type, String icon, Long parentId, int level) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.parentId = parentId;
        this.level = level;
    }

    public String displayName() {
        return level == 2 ? "  - " + name : name;
    }
}
