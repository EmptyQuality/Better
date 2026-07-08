package com.example.quality.count;

public class CountSeriesPoint {
    public final String label;
    public final double value;
    public final String totalLabel;
    public final String maxItemLabel;
    public final String maxItemName;
    public final double maxItemAmount;
    public final boolean hasRecord;

    public CountSeriesPoint(String label, double value) {
        this(label, value, "", "", "", 0, value > 0);
    }

    public CountSeriesPoint(
            String label,
            double value,
            String totalLabel,
            String maxItemLabel,
            String maxItemName,
            double maxItemAmount,
            boolean hasRecord
    ) {
        this.label = label;
        this.value = value;
        this.totalLabel = totalLabel;
        this.maxItemLabel = maxItemLabel;
        this.maxItemName = maxItemName;
        this.maxItemAmount = maxItemAmount;
        this.hasRecord = hasRecord;
    }
}
