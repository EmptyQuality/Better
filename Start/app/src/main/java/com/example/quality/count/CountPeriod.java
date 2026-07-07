package com.example.quality.count;

import java.time.LocalDate;

public enum CountPeriod {
    DAY,
    MONTH,
    YEAR;

    public LocalDate startFor(LocalDate anchor) {
        if (this == YEAR) {
            return LocalDate.of(anchor.getYear(), 1, 1);
        }
        if (this == MONTH) {
            return LocalDate.of(anchor.getYear(), anchor.getMonthValue(), 1);
        }
        return anchor;
    }

    public LocalDate endFor(LocalDate anchor) {
        LocalDate start = startFor(anchor);
        if (this == YEAR) {
            return start.plusYears(1);
        }
        if (this == MONTH) {
            return start.plusMonths(1);
        }
        return start.plusDays(1);
    }

    public LocalDate previous(LocalDate anchor) {
        if (this == YEAR) {
            return anchor.minusYears(1);
        }
        if (this == MONTH) {
            return anchor.minusMonths(1);
        }
        return anchor.minusDays(1);
    }

    public LocalDate next(LocalDate anchor) {
        if (this == YEAR) {
            return anchor.plusYears(1);
        }
        if (this == MONTH) {
            return anchor.plusMonths(1);
        }
        return anchor.plusDays(1);
    }
}
