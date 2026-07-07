package com.example.quality.count;

public class CountStats {
    public final double income;
    public final double expense;

    public CountStats(double income, double expense) {
        this.income = income;
        this.expense = expense;
    }

    public double balance() {
        return income - expense;
    }
}
