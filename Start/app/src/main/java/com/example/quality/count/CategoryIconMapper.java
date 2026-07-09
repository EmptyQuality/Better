package com.example.quality.count;

import android.content.Context;
import android.net.Uri;
import android.widget.ImageView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CategoryIconMapper {
    public static final String BUILTIN_PREFIX = "builtin:";
    public static final String CUSTOM_PREFIX = "custom:";
    public static final String DEFAULT_ICON = BUILTIN_PREFIX + "list";
    public static final String DEFAULT_EXPENSE_ICON = BUILTIN_PREFIX + "expend";
    public static final String DEFAULT_INCOME_ICON = BUILTIN_PREFIX + "income";

    public static final String[] ICON_KEYS = {
            BUILTIN_PREFIX + "expend",
            BUILTIN_PREFIX + "income",
            BUILTIN_PREFIX + "food",
            BUILTIN_PREFIX + "car",
            BUILTIN_PREFIX + "goods",
            BUILTIN_PREFIX + "medic",
            BUILTIN_PREFIX + "learning",
            BUILTIN_PREFIX + "bottle",
            BUILTIN_PREFIX + "cloth",
            BUILTIN_PREFIX + "digital",
            BUILTIN_PREFIX + "tissue",
            BUILTIN_PREFIX + "book",
            BUILTIN_PREFIX + "salary",
            BUILTIN_PREFIX + "parttime_job",
            BUILTIN_PREFIX + "city_break",
            BUILTIN_PREFIX + "communicartion",
            BUILTIN_PREFIX + "delivery",
            BUILTIN_PREFIX + "entertainment",
            BUILTIN_PREFIX + "financing",
            BUILTIN_PREFIX + "game",
            BUILTIN_PREFIX + "gift_money",
            BUILTIN_PREFIX + "list",
            BUILTIN_PREFIX + "rice",
            BUILTIN_PREFIX + "school",
            BUILTIN_PREFIX + "setting",
            BUILTIN_PREFIX + "snack",
            BUILTIN_PREFIX + "team",
            BUILTIN_PREFIX + "trip",
            BUILTIN_PREFIX + "other_expend",
            BUILTIN_PREFIX + "other_income",
            BUILTIN_PREFIX + "school_allowance",
            BUILTIN_PREFIX + "we_allowance",
            BUILTIN_PREFIX + "home_allowance",
            BUILTIN_PREFIX + "wash"
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
        if (isCustom(icon)) {
            return icon;
        }
        return ICON_KEY_SET.contains(icon) ? icon : DEFAULT_ICON;
    }

    public static boolean isCustom(String iconRef) {
        return iconRef != null && iconRef.startsWith(CUSTOM_PREFIX) && iconRef.length() > CUSTOM_PREFIX.length();
    }

    public static int drawableResId(Context context, String iconKey) {
        String normalized = normalize(iconKey);
        String key = normalized.startsWith(BUILTIN_PREFIX)
                ? normalized.substring(BUILTIN_PREFIX.length())
                : normalized;
        if ("expend".equals(key) || "income".equals(key)) {
            int resId = context.getResources().getIdentifier(
                    "ic_" + key,
                    "drawable",
                    context.getPackageName()
            );
            if (resId != 0) {
                return resId;
            }
        }
        int resId = context.getResources().getIdentifier(
                "ic_category_" + key,
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

    public static void loadInto(ImageView imageView, String iconRef, CountRepository repository, int tintColor) {
        String normalized = normalize(iconRef);
        imageView.clearColorFilter();
        if (isCustom(normalized)) {
            CountCustomIcon icon = repository.getCustomIcon(normalized.substring(CUSTOM_PREFIX.length()));
            if (icon != null && icon.fileName != null && !icon.fileName.trim().isEmpty()) {
                imageView.setImageURI(Uri.fromFile(repository.customIconFile(icon.fileName)));
                imageView.setColorFilter(tintColor);
                return;
            }
        }
        imageView.setImageResource(drawableResId(imageView.getContext(), normalized));
        imageView.setColorFilter(tintColor);
    }

    public static String suggestedIcon(String name, String type) {
        return defaultIcon(type);
    }

    public static String defaultIcon(String type) {
        return "income".equals(type) ? DEFAULT_INCOME_ICON : DEFAULT_EXPENSE_ICON;
    }
}
