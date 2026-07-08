package com.example.quality.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.count.CategoryIconMapper;
import com.example.quality.count.CountCategory;
import com.example.quality.count.CountCustomIcon;
import com.example.quality.count.CountRepository;
import com.example.quality.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FragmentCategoryManage extends Fragment {
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_BEE_SOFT = 0xFFFFF4BF;
    private static final int COLOR_BACKGROUND = 0xFFFFFBF4;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_SURFACE_SOFT = 0xFFFFF8E8;
    private static final int COLOR_LINE = 0xFFEDE3CF;
    private static final int COLOR_TEXT = 0xFF2F2D2D;
    private static final int COLOR_MUTED = 0xFF6B7280;

    private CountRepository repository;
    private String currentType = TYPE_EXPENSE;
    private TextView expenseTab;
    private TextView incomeTab;
    private LinearLayout categoryList;
    private ActivityResultLauncher<String> customIconPickerLauncher;
    private ActivityResultLauncher<String> customIconPackPickerLauncher;
    private LinearLayout pendingCustomIconGrid;
    private LinearLayout pendingBuiltinIconGrid;
    private String[] pendingCustomIconSelection;
    private String pendingReplacingCustomIconId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CountRepository(requireContext());
        customIconPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleCustomIconPicked
        );
        customIconPackPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleCustomIconPackPicked
        );
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
        page.setBackgroundColor(COLOR_BACKGROUND);

        page.addView(buildHeader());

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(16), dp(18), dp(96));
        scrollView.addView(content);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        TextView tabsTitle = text("类别类型", 14, COLOR_MUTED, false);
        content.addView(tabsTitle);
        LinearLayout tabs = horizontal();
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(0xFFF3F0EA, dp(16)));
        expenseTab = tab("支出", TYPE_EXPENSE);
        incomeTab = tab("收入", TYPE_INCOME);
        tabs.addView(expenseTab, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams incomeParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        tabs.addView(incomeTab, incomeParams);
        LinearLayout.LayoutParams tabsParams = matchWrap();
        tabsParams.setMargins(0, dp(8), 0, dp(18));
        content.addView(tabs, tabsParams);

        TextView listTitle = text("已有类别", 16, COLOR_TEXT, true);
        content.addView(listTitle);
        categoryList = vertical();
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.setMargins(0, dp(10), 0, dp(18));
        content.addView(categoryList, listParams);

        return page;
    }

    private View buildHeader() {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(18), dp(12));
        header.setBackgroundColor(COLOR_BEE);

        TextView title = text("类别管理", 20, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        titleParams.setMargins(dp(4), 0, dp(10), 0);
        header.addView(title, titleParams);

        TextView back = text("‹", 28, COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(0x33FFFFFF, dp(18)));
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        header.addView(back, fixed(dp(38), dp(38)));
        return header;
    }

    private TextView tab(String label, String type) {
        TextView tab = text(label, 15, COLOR_TEXT, true);
        tab.setGravity(Gravity.CENTER);
        tab.setOnClickListener(v -> {
            currentType = type;
            refreshPage();
        });
        return tab;
    }

    private void refreshPage() {
        if (categoryList == null) {
            return;
        }
        styleTab(expenseTab, TYPE_EXPENSE.equals(currentType));
        styleTab(incomeTab, TYPE_INCOME.equals(currentType));
        renderCategories();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setTextColor(COLOR_TEXT);
        tab.setBackground(round(selected ? COLOR_BEE : 0x00000000, dp(12)));
    }

    private void renderCategories() {
        categoryList.removeAllViews();
        List<CountCategory> categories = repository.getRootCategories(currentType);
        if (categories.isEmpty()) {
            TextView empty = text("暂无类别", 14, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            categoryList.addView(empty);
        }
        addCategoryRows(categories);
    }

    private void addCategoryRows(List<CountCategory> categories) {
        int totalItems = categories.size() + 1;
        for (int index = 0; index < totalItems; index += 4) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 4; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < categories.size()) {
                    row.addView(categoryCell(categories.get(itemIndex)), params);
                } else if (itemIndex == categories.size()) {
                    row.addView(addCategoryCell(), params);
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
        cell.setBackground(roundStroke(COLOR_SURFACE, dp(16), COLOR_LINE, 1));
        cell.setElevation(dp(1));
        cell.setOnLongClickListener(v -> {
            showCategoryEditor(category);
            return true;
        });
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

    private View addCategoryCell() {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundStroke(COLOR_SURFACE, dp(16), COLOR_LINE, 1));
        cell.setOnClickListener(v -> showCategoryEditor(null));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_add);
        icon.setColorFilter(COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setBackground(round(COLOR_BEE_SOFT, dp(24)));
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        cell.addView(icon, fixed(dp(42), dp(42)));

        TextView name = text("添加", 12, COLOR_MUTED, false);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(3), dp(6), dp(3), 0);
        cell.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return cell;
    }

    private void showCategoryEditor(@Nullable CountCategory editingCategory) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        boolean editing = editingCategory != null;
        TextView title = text(editing ? "编辑类别" : "新增类别", 18, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)
        ));

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("输入类别名称");
        nameInput.setSingleLine(true);
        nameInput.setTextColor(COLOR_TEXT);
        nameInput.setHintTextColor(0xFF9CA3AF);
        nameInput.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(14), COLOR_LINE, 1));
        nameInput.setPadding(dp(14), 0, dp(14), 0);
        if (editing) {
            nameInput.setText(editingCategory.name);
            nameInput.setSelection(nameInput.getText().length());
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        inputParams.setMargins(0, dp(14), 0, dp(14));
        sheet.addView(nameInput, inputParams);

        TextView iconTitle = text("选择图标", 14, COLOR_MUTED, false);
        sheet.addView(iconTitle);

        LinearLayout iconGrid = vertical();
        String[] selectedIcon = {
                editing ? CategoryIconMapper.normalize(editingCategory.icon) : CategoryIconMapper.defaultIcon(currentType)
        };
        LinearLayout.LayoutParams gridParams = matchWrap();
        gridParams.setMargins(0, dp(10), 0, dp(14));
        sheet.addView(iconGrid, gridParams);
        renderIconGrid(iconGrid, selectedIcon);

        TextView addIconTitle = text("新增图标", 14, COLOR_MUTED, false);
        sheet.addView(addIconTitle);
        LinearLayout customIconGrid = vertical();
        LinearLayout.LayoutParams customGridParams = matchWrap();
        customGridParams.setMargins(0, dp(10), 0, dp(18));
        sheet.addView(customIconGrid, customGridParams);
        renderCustomIconGrid(customIconGrid, iconGrid, selectedIcon);

        TextView save = text(editing ? "保存修改" : "保存类别", 16, COLOR_TEXT, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(COLOR_BEE, dp(16)));
        save.setOnClickListener(v -> saveCategory(dialog, editingCategory, nameInput, selectedIcon[0]));
        sheet.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void renderIconGrid(LinearLayout iconGrid, String[] selectedIcon) {
        iconGrid.removeAllViews();
        List<String> iconRefs = new ArrayList<>();
        for (String iconRef : CategoryIconMapper.ICON_KEYS) {
            iconRefs.add(iconRef);
        }
        int totalItems = iconRefs.size();
        for (int index = 0; index < totalItems; index += 5) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 5; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < iconRefs.size()) {
                    row.addView(iconOption(iconRefs.get(itemIndex), iconGrid, selectedIcon), params);
                } else {
                    row.addView(new SpaceView(requireContext()), params);
                }
            }
            iconGrid.addView(row);
        }
    }

    private void renderCustomIconGrid(LinearLayout customIconGrid, LinearLayout builtinIconGrid, String[] selectedIcon) {
        customIconGrid.removeAllViews();
        List<CountCustomIcon> customIcons = repository.getCustomIcons();
        int totalItems = customIcons.size() + 2;
        for (int index = 0; index < totalItems; index += 5) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 5; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < customIcons.size()) {
                    row.addView(customIconOption(
                            customIcons.get(itemIndex),
                            customIconGrid,
                            builtinIconGrid,
                            selectedIcon
                    ), params);
                } else if (itemIndex == customIcons.size()) {
                    row.addView(iconActionOption(
                            R.drawable.ic_import,
                            v -> launchCustomIconPicker(customIconGrid, builtinIconGrid, selectedIcon, null)
                    ), params);
                } else if (itemIndex == customIcons.size() + 1) {
                    row.addView(iconActionOption(
                            R.drawable.ic_compress,
                            v -> launchCustomIconPackPicker(customIconGrid, builtinIconGrid, selectedIcon)
                    ), params);
                } else {
                    row.addView(new SpaceView(requireContext()), params);
                }
            }
            customIconGrid.addView(row);
        }
    }

    private View iconOption(String iconKey, LinearLayout iconGrid, String[] selectedIcon) {
        FrameLayout box = new FrameLayout(requireContext());
        boolean selected = iconKey.equals(selectedIcon[0]);
        box.setBackground(roundStroke(
                selected ? COLOR_BEE_SOFT : COLOR_SURFACE_SOFT,
                dp(14),
                selected ? COLOR_BEE : COLOR_LINE,
                selected ? 2 : 1
        ));
        ImageView icon = new ImageView(requireContext());
        CategoryIconMapper.loadInto(icon, iconKey, repository, COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        box.addView(icon, iconParams);
        box.setOnClickListener(v -> {
            selectedIcon[0] = iconKey;
            renderIconGrid(iconGrid, selectedIcon);
        });
        return box;
    }

    private View customIconOption(
            CountCustomIcon customIcon,
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon
    ) {
        FrameLayout box = new FrameLayout(requireContext());
        String iconRef = customIcon.iconRef();
        boolean selected = iconRef.equals(selectedIcon[0]);
        box.setBackground(roundStroke(
                selected ? COLOR_BEE_SOFT : COLOR_SURFACE_SOFT,
                dp(14),
                selected ? COLOR_BEE : COLOR_LINE,
                selected ? 2 : 1
        ));
        ImageView icon = new ImageView(requireContext());
        CategoryIconMapper.loadInto(icon, iconRef, repository, COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        box.addView(icon, iconParams);
        box.setOnClickListener(v -> {
            selectedIcon[0] = iconRef;
            renderIconGrid(builtinIconGrid, selectedIcon);
            renderCustomIconGrid(customIconGrid, builtinIconGrid, selectedIcon);
        });
        box.setOnLongClickListener(v -> {
            showCustomIconActions(customIcon, customIconGrid, builtinIconGrid, selectedIcon);
            return true;
        });
        return box;
    }

    private View iconActionOption(int iconResId, View.OnClickListener listener) {
        FrameLayout box = new FrameLayout(requireContext());
        box.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(14), COLOR_LINE, 1));
        box.setOnClickListener(listener);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconResId);
        icon.setColorFilter(COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER);
        box.addView(icon, iconParams);
        return box;
    }

    private void showCustomIconActions(
            CountCustomIcon customIcon,
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);
        TextView title = text("图标操作", 18, COLOR_TEXT, true);
        title.setPadding(dp(2), 0, 0, dp(12));
        sheet.addView(title);
        sheet.addView(actionRow(R.drawable.ic_import, "替换图标", "使用新图片替换当前图标", v -> {
            dialog.dismiss();
            launchCustomIconPicker(customIconGrid, builtinIconGrid, selectedIcon, customIcon.id);
        }));
        sheet.addView(actionRow(R.drawable.ic_category_setting, "删除图标", "使用该图标的类别会切换为默认图标", v -> {
            dialog.dismiss();
            repository.deleteCustomIcon(customIcon.id);
            if (customIcon.iconRef().equals(selectedIcon[0])) {
                selectedIcon[0] = CategoryIconMapper.defaultIcon(currentType);
            }
            renderIconGrid(builtinIconGrid, selectedIcon);
            renderCustomIconGrid(customIconGrid, builtinIconGrid, selectedIcon);
            refreshPage();
            Toast.makeText(requireContext(), "图标已删除", Toast.LENGTH_SHORT).show();
        }));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private View actionRow(int iconResId, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(COLOR_SURFACE_SOFT, dp(16)));
        row.setOnClickListener(listener);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconResId);
        icon.setColorFilter(COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setBackground(round(COLOR_BEE_SOFT, dp(19)));
        row.addView(icon, fixed(dp(38), dp(38)));
        LinearLayout copy = vertical();
        TextView titleView = text(title, 15, COLOR_TEXT, true);
        TextView subtitleView = text(subtitle, 12, COLOR_MUTED, false);
        subtitleView.setPadding(0, dp(2), 0, 0);
        copy.addView(titleView);
        copy.addView(subtitleView);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMargins(dp(12), 0, 0, 0);
        row.addView(copy, copyParams);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);
        return row;
    }

    private void launchCustomIconPicker(
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon,
            @Nullable String replaceId
    ) {
        pendingCustomIconGrid = customIconGrid;
        pendingBuiltinIconGrid = builtinIconGrid;
        pendingCustomIconSelection = selectedIcon;
        pendingReplacingCustomIconId = replaceId;
        customIconPickerLauncher.launch("image/*");
    }

    private void launchCustomIconPackPicker(
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon
    ) {
        pendingCustomIconGrid = customIconGrid;
        pendingBuiltinIconGrid = builtinIconGrid;
        pendingCustomIconSelection = selectedIcon;
        pendingReplacingCustomIconId = null;
        customIconPackPickerLauncher.launch("*/*");
    }

    private ImageView iconView(String iconKey, int sizeDp) {
        ImageView image = new ImageView(requireContext());
        CategoryIconMapper.loadInto(image, iconKey, repository, COLOR_TEXT);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setBackground(round(COLOR_BEE_SOFT, dp(24)));
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        image.setMinimumWidth(dp(sizeDp));
        image.setMinimumHeight(dp(sizeDp));
        return image;
    }

    private void handleCustomIconPicked(Uri uri) {
        if (uri == null || pendingCustomIconGrid == null || pendingCustomIconSelection == null) {
            clearPendingCustomIconPicker();
            return;
        }
        try {
            CountCustomIcon icon = pendingReplacingCustomIconId == null
                    ? repository.importCustomIcon(uri)
                    : repository.replaceCustomIcon(pendingReplacingCustomIconId, uri);
            pendingCustomIconSelection[0] = icon.iconRef();
            if (pendingBuiltinIconGrid != null) {
                renderIconGrid(pendingBuiltinIconGrid, pendingCustomIconSelection);
            }
            renderCustomIconGrid(pendingCustomIconGrid, pendingBuiltinIconGrid, pendingCustomIconSelection);
            refreshPage();
            Toast.makeText(requireContext(), "图标已导入", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "无法导入图标，请选择 PNG/WebP/JPG 图片", Toast.LENGTH_SHORT).show();
        } finally {
            clearPendingCustomIconPicker();
        }
    }

    private void handleCustomIconPackPicked(Uri uri) {
        if (uri == null || pendingCustomIconGrid == null || pendingCustomIconSelection == null) {
            clearPendingCustomIconPicker();
            return;
        }
        try {
            List<CountCustomIcon> imported = repository.importCustomIconPack(uri);
            if (!imported.isEmpty()) {
                pendingCustomIconSelection[0] = imported.get(0).iconRef();
            }
            if (pendingBuiltinIconGrid != null) {
                renderIconGrid(pendingBuiltinIconGrid, pendingCustomIconSelection);
            }
            renderCustomIconGrid(pendingCustomIconGrid, pendingBuiltinIconGrid, pendingCustomIconSelection);
            refreshPage();
            Toast.makeText(requireContext(), "已导入 " + imported.size() + " 个图标", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "无法导入压缩包，请选择包含图标的 ZIP/RAR 文件", Toast.LENGTH_SHORT).show();
        } finally {
            clearPendingCustomIconPicker();
        }
    }

    private void clearPendingCustomIconPicker() {
        pendingCustomIconGrid = null;
        pendingBuiltinIconGrid = null;
        pendingCustomIconSelection = null;
        pendingReplacingCustomIconId = null;
    }

    private void saveCategory(
            BottomSheetDialog dialog,
            @Nullable CountCategory editingCategory,
            EditText nameInput,
            String iconKey
    ) {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "请输入类别名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (editingCategory == null) {
            repository.addCategory(name, currentType, iconKey);
            Toast.makeText(requireContext(), "类别已保存", Toast.LENGTH_SHORT).show();
        } else {
            repository.updateCategory(editingCategory.id, name, iconKey);
            Toast.makeText(requireContext(), "类别已更新", Toast.LENGTH_SHORT).show();
        }
        dialog.dismiss();
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
