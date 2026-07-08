package com.example.quality.count;

public class CountImage {
    public final long id;
    public final long transactionId;
    public final String imagePath;
    public final int sortOrder;

    public CountImage(long id, long transactionId, String imagePath, int sortOrder) {
        this.id = id;
        this.transactionId = transactionId;
        this.imagePath = imagePath;
        this.sortOrder = sortOrder;
    }
}
