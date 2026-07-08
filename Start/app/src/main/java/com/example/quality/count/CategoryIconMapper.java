package com.example.quality.count;

import android.content.Context;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CategoryIconMapper {
    public static final String DEFAULT_ICON = "list";
    public static final String DEFAULT_EXPENSE_ICON = "expend";
    public static final String DEFAULT_INCOME_ICON = "income";

    public static final String[] ICON_KEYS = {
            "expend",
            "income",
            "food",
            "car",
            "goods",
            "medic",
            "learning",
            "bottle",
            "cloth",
            "digital",
            "tissue",
            "book",
            "salary",
            "parttime_job",
            "city_break",
            "communicartion",
            "delivery",
            "entertainment",
            "financing",
            "game",
            "gift_money",
            "list",
            "rice",
            "school",
            "setting",
            "snack",
            "team",
            "trip",
            "wash"
    };

    private static final Set<String> ICON_KEY_SET = new HashSet<>(Arrays.asList(ICON_KEYS));

    static {
        ICON_KEY_SET.add(DEFAULT_EXPENSE_ICON);
        ICON_KEY_SET.add(DEFAULT_INCOME_ICON);
    }

    private CategoryIconMapper() {
    }

    public static String normalize(String rawIcon) {
        if (rawIcon == null) {
            return DEFAULT_ICON;
        }
        String icon = rawIcon.trim();
        if (icon.startsWith("ic_category_")) {
            icon = icon.substring("ic_category_".length());
        }
        return ICON_KEY_SET.contains(icon) ? icon : DEFAULT_ICON;
    }

    public static int drawableResId(Context context, String iconKey) {
        String normalized = normalize(iconKey);
        if (DEFAULT_EXPENSE_ICON.equals(normalized) || DEFAULT_INCOME_ICON.equals(normalized)) {
            int resId = context.getResources().getIdentifier(
                    "ic_" + normalized,
                    "drawable",
                    context.getPackageName()
            );
            if (resId != 0) {
                return resId;
            }
        }
        int resId = context.getResources().getIdentifier(
                "ic_category_" + normalized,
                "drawable",
                context.getPackageName()
        );
        if (resId != 0) {
            return resId;
        }
        return context.getResources().getIdentifier(
                "ic_category_" + DEFAULT_ICON,
                "drawable",
                context.getPackageName()
        );
    }

    public static String suggestedIcon(String name, String type) {
        return defaultIcon(type);
    }

    public static String defaultIcon(String type) {
        return "income".equals(type) ? DEFAULT_INCOME_ICON : DEFAULT_EXPENSE_ICON;
    }
}
