package com.example.quality.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.count.CategoryIconMapper;
import com.example.quality.count.CountCategory;
import com.example.quality.count.CountRepository;

import java.util.List;

public class FragmentCategoryManage extends Fragment {
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_MUTED = 0xFF6B7280;

    private CountRepository repository;
    private String currentType = TYPE_EXPENSE;
    private String selectedIcon = "food";
    private TextView expenseTab;
    private TextView incomeTab;
    private LinearLayout categoryList;
    private LinearLayout iconGrid;
    private EditText nameInput;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CountRepository(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return buildContentView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshPage();
    }

    private View buildContentView() {
        LinearLayout page = vertical();
        page.setBackgroundColor(0xFFFAFAFA);

        page.addView(buildHeader());

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(14), dp(16), dp(96));
        scrollView.addView(content);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        TextView tabsTitle = text("类别类型", 14, COLOR_MUTED, false);
        content.addView(tabsTitle);
        LinearLayout tabs = horizontal();
        tabs.setPadding(0, dp(8), 0, dp(16));
        expenseTab = tab("支出", TYPE_EXPENSE);
        incomeTab = tab("收入", TYPE_INCOME);
        tabs.addView(expenseTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams incomeParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        incomeParams.setMargins(dp(10), 0, 0, 0);
        tabs.addView(incomeTab, incomeParams);
        content.addView(tabs);

        TextView listTitle = text("已有类别", 16, COLOR_TEXT, true);
        content.addView(listTitle);
        categoryList = vertical();
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.setMargins(0, dp(10), 0, dp(18));
        content.addView(categoryList, listParams);

        TextView addTitle = text("新增类别", 16, COLOR_TEXT, true);
        content.addView(addTitle);

        nameInput = new EditText(requireContext());
        nameInput.setHint("输入类别名称");
        nameInput.setSingleLine(true);
        nameInput.setTextColor(COLOR_TEXT);
        nameInput.setHintTextColor(0xFF9CA3AF);
        nameInput.setBackground(roundStroke(0xFFFFFFFF, dp(10), 0xFFE5E7EB, 1));
        nameInput.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        inputParams.setMargins(0, dp(10), 0, dp(16));
        content.addView(nameInput, inputParams);

        TextView iconTitle = text("选择图标", 14, COLOR_MUTED, false);
        content.addView(iconTitle);
        iconGrid = vertical();
        LinearLayout.LayoutParams gridParams = matchWrap();
        gridParams.setMargins(0, dp(10), 0, dp(18));
        content.addView(iconGrid, gridParams);

        TextView save = text("保存类别", 16, COLOR_TEXT, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(COLOR_BEE, dp(12)));
        save.setOnClickListener(v -> saveCategory());
        content.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        return page;
    }

    private View buildHeader() {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(16), dp(8));
        header.setBackgroundColor(COLOR_BEE);

        TextView back = text("<", 24, COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        header.addView(back, fixed(dp(44), dp(44)));

        TextView title = text("类别管理", 20, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        titleParams.setMargins(dp(4), 0, 0, 0);
        header.addView(title, titleParams);
        return header;
    }

    private TextView tab(String label, String type) {
        TextView tab = text(label, 15, COLOR_TEXT, true);
        tab.setGravity(Gravity.CENTER);
        tab.setOnClickListener(v -> {
            currentType = type;
            selectedIcon = CategoryIconMapper.defaultIcon(currentType);
            refreshPage();
        });
        return tab;
    }

    private void refreshPage() {
        if (categoryList == null || iconGrid == null) {
            return;
        }
        styleTab(expenseTab, TYPE_EXPENSE.equals(currentType));
        styleTab(incomeTab, TYPE_INCOME.equals(currentType));
        renderCategories();
        renderIconGrid();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setTextColor(COLOR_TEXT);
        tab.setBackground(round(selected ? COLOR_BEE : 0xFFFFFFFF, dp(12)));
    }

    private void renderCategories() {
        categoryList.removeAllViews();
        List<CountCategory> categories = repository.getRootCategories(currentType);
        if (categories.isEmpty()) {
            TextView empty = text("暂无类别", 14, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            categoryList.addView(empty);
            return;
        }
        addCategoryRows(categories);
    }

    private void addCategoryRows(List<CountCategory> categories) {
        for (int index = 0; index < categories.size(); index += 4) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 4; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < categories.size()) {
                    row.addView(categoryCell(categories.get(itemIndex)), params);
                } else {
                    SpaceView space = new SpaceView(requireContext());
                    row.addView(space, params);
                }
            }
            categoryList.addView(row);
        }
    }

    private View categoryCell(CountCategory category) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundStroke(0xFFFFFFFF, dp(12), 0xFFE5E7EB, 1));
        cell.addView(iconView(category.icon, 24), fixed(dp(42), dp(42)));
        TextView name = text(category.name, 12, COLOR_TEXT, false);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setPadding(dp(3), dp(6), dp(3), 0);
        cell.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return cell;
    }

    private void renderIconGrid() {
        iconGrid.removeAllViews();
        String[] keys = CategoryIconMapper.ICON_KEYS;
        for (int index = 0; index < keys.length; index += 5) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 5; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < keys.length) {
                    row.addView(iconOption(keys[itemIndex]), params);
                } else {
                    row.addView(new SpaceView(requireContext()), params);
                }
            }
            iconGrid.addView(row);
        }
    }

    private View iconOption(String iconKey) {
        FrameLayout box = new FrameLayout(requireContext());
        boolean selected = iconKey.equals(selectedIcon);
        box.setBackground(roundStroke(
                selected ? 0xFFFFF7D1 : 0xFFFFFFFF,
                dp(12),
                selected ? COLOR_BEE : 0xFFE5E7EB,
                selected ? 2 : 1
        ));
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(CategoryIconMapper.drawableResId(requireContext(), iconKey));
        icon.setColorFilter(COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        box.addView(icon, iconParams);
        box.setOnClickListener(v -> {
            selectedIcon = iconKey;
            renderIconGrid();
        });
        return box;
    }

    private ImageView iconView(String iconKey, int sizeDp) {
        ImageView image = new ImageView(requireContext());
        image.setImageResource(CategoryIconMapper.drawableResId(requireContext(), iconKey));
        image.setColorFilter(COLOR_TEXT);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setBackground(round(0xFFFFF7D1, dp(24)));
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        image.setMinimumWidth(dp(sizeDp));
        image.setMinimumHeight(dp(sizeDp));
        return image;
    }

    private void saveCategory() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "请输入类别名称", Toast.LENGTH_SHORT).show();
            return;
        }
        repository.addCategory(name, currentType, selectedIcon);
        nameInput.setText("");
        Toast.makeText(requireContext(), "类别已保存", Toast.LENGTH_SHORT).show();
        refreshPage();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int radius, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class SpaceView extends View {
        SpaceView(Context context) {
            super(context);
        }
    }
}
