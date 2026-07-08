package com.example.quality.count;

public class CountCustomIcon {
    public final String id;
    public final String label;
    public final String fileName;
    public final String source;
    public final String packId;

    public CountCustomIcon(String id, String label, String fileName, String source, String packId) {
        this.id = id;
        this.label = label;
        this.fileName = fileName;
        this.source = source;
        this.packId = packId;
    }

    public String iconRef() {
        return CategoryIconMapper.CUSTOM_PREFIX + id;
    }
}
