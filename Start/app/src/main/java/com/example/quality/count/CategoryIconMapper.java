package com.example.quality.count;

import android.content.Context;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CategoryIconMapper {
    public static final String DEFAULT_ICON = "list";

    public static final String[] ICON_KEYS = {
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
        if (name == null) {
            return defaultIcon(type);
        }
        switch (name.trim()) {
            case "餐饮":
                return "food";
            case "交通":
                return "car";
            case "购物":
                return "goods";
            case "医疗":
                return "medic";
            case "学习":
                return "learning";
            case "水电费":
                return "bottle";
            case "服饰":
                return "cloth";
            case "数码":
                return "digital";
            case "日用":
                return "tissue";
            case "书籍":
                return "book";
            case "工资":
                return "salary";
            case "兼职":
                return "parttime_job";
            default:
                return defaultIcon(type);
        }
    }

    public static String defaultIcon(String type) {
        return "income".equals(type) ? "salary" : DEFAULT_ICON;
    }
}
