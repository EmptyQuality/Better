package com.example.quality.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.R;
import com.example.quality.count.CategoryIconMapper;
import com.example.quality.count.CountCategory;
import com.example.quality.count.CountImportResult;
import com.example.quality.count.CountImportService;
import com.example.quality.count.CountRepository;
import com.example.quality.count.CountStats;
import com.example.quality.count.CountTransaction;
import com.example.quality.util.LogUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FragmentCount extends Fragment {
    private static final String TAG = "FragmentCount";
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final String EXPORT_COUNT = "count";
    private static final String EXPORT_QUALITY = "quality";
    private static final String QUALITY_FILE_NAME = "quality_record.json";
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_BEE_SOFT = 0xFFFFF4BF;
    private static final int COLOR_BACKGROUND = 0xFFFFFBF4;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_SURFACE_SOFT = 0xFFFFF8E8;
    private static final int COLOR_LINE = 0xFFEDE3CF;
    private static final int COLOR_ORANGE = 0xFFEF6C00;
    private static final int COLOR_GREEN = 0xFF16A34A;
    private static final int COLOR_TEXT = 0xFF2F2D2D;
    private static final int COLOR_MUTED = 0xFF6B7280;
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);

    private CountRepository repository;
    private LocalDate selectedMonth = LocalDate.now().withDayOfMonth(1);
    private TextView monthLabel;
    private TextView monthChip;
    private TextView incomeValue;
    private TextView expenseValue;
    private TextView balanceValue;
    private LinearLayout transactionList;
    private ActivityResultLauncher<String[]> importFileLauncher;
    private ActivityResultLauncher<String> exportFileLauncher;
    private String pendingImportSource;
    private String pendingExportType;

    private static class CategorySelection {
        List<CountCategory> categories = new ArrayList<>();
        CountCategory selectedCategory;
        Long preferredCategoryId;
    }

    private static class EntryDraft {
        Long editingId;
        String type = TYPE_EXPENSE;
        String amount = "";
        LocalDate date = LocalDate.now();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        LogUtil.d(TAG, "onAttach");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.d(TAG, "onCreate");
        repository = new CountRepository(requireContext());
        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleImportFile
        );
        exportFileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                this::handleExportFile
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogUtil.d(TAG, "onCreateView");
        return buildContentView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshMonthView();
    }

    private View buildContentView() {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout main = vertical();
        frame.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        main.addView(buildHeader());

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, dp(92));
        transactionList = vertical();
        transactionList.setPadding(dp(18), dp(14), dp(18), dp(12));
        scrollView.addView(transactionList);
        main.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        frame.addView(buildFloatingActionButton(), actionButtonParams());
        return frame;
    }

    private View buildHeader() {
        LinearLayout header = vertical();
        header.setBackgroundColor(COLOR_BEE);
        header.setPadding(dp(18), dp(14), dp(18), dp(18));

        LinearLayout summaryCard = vertical();
        summaryCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        summaryCard.setBackground(round(COLOR_SURFACE_SOFT, dp(22)));
        summaryCard.setElevation(dp(3));

        LinearLayout monthRow = horizontal();
        monthRow.setGravity(Gravity.CENTER_VERTICAL);
        monthRow.setOnClickListener(v -> showMonthPicker());
        monthLabel = text("", 21, COLOR_TEXT, true);
        monthLabel.setGravity(Gravity.CENTER_VERTICAL);
        monthRow.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(36), 1));
        monthChip = text("月份 ⌄", 12, COLOR_MUTED, true);
        monthChip.setGravity(Gravity.CENTER);
        monthChip.setBackground(round(0xFFFFFFFF, dp(15)));
        monthRow.addView(monthChip, fixed(dp(72), dp(30)));
        summaryCard.addView(monthRow);

        LinearLayout metricRow = horizontal();
        metricRow.setGravity(Gravity.CENTER);
        metricRow.setPadding(0, dp(12), 0, 0);
        incomeValue = headerMetric(metricRow, "收入");
        metricRow.addView(metricDivider());
        expenseValue = headerMetric(metricRow, "支出");
        metricRow.addView(metricDivider());
        balanceValue = headerMetric(metricRow, "结余");
        summaryCard.addView(metricRow);
        header.addView(summaryCard);
        return header;
    }

    private TextView headerMetric(LinearLayout row, String label) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        TextView title = text(label, 12, COLOR_MUTED, false);
        title.setGravity(Gravity.CENTER);
        TextView value = text("0.00", 15, COLOR_TEXT, true);
        value.setGravity(Gravity.CENTER);
        box.addView(title);
        box.addView(value);
        row.addView(box, new LinearLayout.LayoutParams(0, dp(48), 1));
        return value;
    }

    private View metricDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x1F8D6E63);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(1), dp(30));
        params.setMargins(dp(4), dp(9), dp(4), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private void refreshMonthView() {
        if (transactionList == null) {
            return;
        }
        LocalDate start = selectedMonth.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        monthLabel.setText(String.format(Locale.CHINA, "%d年 %02d月",
                selectedMonth.getYear(), selectedMonth.getMonthValue()));

        CountStats stats = repository.getStats(start, end);
        incomeValue.setText(repository.formatMoney(stats.income));
        expenseValue.setText(repository.formatMoney(stats.expense));
        balanceValue.setText(repository.formatMoney(stats.balance()));
        balanceValue.setTextColor(stats.balance() < 0 ? COLOR_ORANGE : COLOR_TEXT);
        renderTransactions(start, end);
    }

    private void renderTransactions(LocalDate start, LocalDate end) {
        transactionList.removeAllViews();
        List<CountTransaction> transactions = repository.getTransactions(start, end);
        if (transactions.isEmpty()) {
            transactionList.addView(emptyState());
            return;
        }
        for (CountTransaction tx : transactions) {
            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, 0, 0, dp(10));
            transactionList.addView(transactionRow(tx), params);
        }
    }

    private View transactionRow(CountTransaction tx) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(round(COLOR_SURFACE, dp(18)));
        row.setElevation(dp(2));
        row.setOnLongClickListener(v -> {
            showTransactionActions(tx);
            return true;
        });

        row.addView(iconBubble(tx.categoryIcon), fixed(dp(38), dp(38)));

        LinearLayout textBox = vertical();
        TextView title = text(tx.displayCategoryName(), 15, COLOR_TEXT, true);
        String noteText = tx.note == null || tx.note.trim().isEmpty() ? tx.categoryPath() : tx.note.trim();
        TextView detail = text(tx.date.format(DAY_FORMATTER) + " · " + noteText,
                11, COLOR_MUTED, false);
        textBox.addView(title);
        textBox.addView(detail);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMargins(dp(10), 0, dp(8), 0);
        row.addView(textBox, textParams);

        boolean income = TYPE_INCOME.equals(tx.type);
        TextView amount = text((income ? "+" : "-") + repository.formatMoney(tx.amount),
                14, income ? COLOR_GREEN : COLOR_ORANGE, true);
        amount.setGravity(Gravity.END);
        row.addView(amount);
        return row;
    }

    private View emptyState() {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18), dp(54), dp(18), dp(54));
        box.setBackground(round(COLOR_SURFACE, dp(20)));
        box.setElevation(dp(2));
        TextView title = text("本月还没有记账", 17, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        TextView hint = text("点右下角 + 记第一笔", 14, COLOR_MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(title);
        box.addView(hint);
        return box;
    }

    private void showMonthPicker() {
        LinearLayout content = horizontal();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(8), dp(18), dp(8), dp(18));

        NumberPicker yearPicker = datePicker(selectedMonth.getYear() - 5, selectedMonth.getYear() + 5);
        yearPicker.setValue(selectedMonth.getYear());
        NumberPicker monthPicker = datePicker(1, 12);
        monthPicker.setFormatter(value -> String.format(Locale.CHINA, "%02d", value));
        monthPicker.setValue(selectedMonth.getMonthValue());

        content.addView(compactMonthPickerColumn(yearPicker), new LinearLayout.LayoutParams(0, dp(146), 1));
        content.addView(compactMonthPickerColumn(monthPicker), new LinearLayout.LayoutParams(0, dp(146), 1));

        new AlertDialog.Builder(requireContext())
                .setTitle("选择月份")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    selectedMonth = LocalDate.of(yearPicker.getValue(), monthPicker.getValue(), 1);
                    refreshMonthView();
                })
                .show();
    }

    private View compactMonthPickerColumn(NumberPicker picker) {
        FrameLayout column = new FrameLayout(requireContext());
        column.setPadding(dp(12), 0, dp(12), 0);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(104),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        column.addView(picker, params);
        return column;
    }

    private void showEntrySheet() {
        showEntrySheet(null);
    }

    private void showEntrySheet(@Nullable CountTransaction editingTx) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        EntryDraft draft = new EntryDraft();
        if (editingTx == null) {
            draft.date = LocalDate.now();
        } else {
            draft.editingId = editingTx.id;
            draft.type = editingTx.type;
            draft.amount = amountInputText(editingTx.amount);
            draft.date = editingTx.date;
        }

        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(20));
        sheet.setBackgroundColor(COLOR_SURFACE);

        CategorySelection categorySelection = new CategorySelection();
        if (editingTx != null) {
            categorySelection.preferredCategoryId = editingTx.categoryId;
        }

        TextView sheetTitle = text(editingTx == null ? "添加记录" : "修改记录", 18, COLOR_TEXT, true);
        sheetTitle.setGravity(Gravity.CENTER);
        sheet.addView(sheetTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
        ));

        TextView amountDisplay = text("¥ 0.00", 34, COLOR_TEXT, true);
        amountDisplay.setGravity(Gravity.CENTER);
        sheet.addView(amountDisplay, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
        ));

        LinearLayout typeRow = horizontal();
        typeRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        typeRow.setBackground(round(0xFFF3F0EA, dp(16)));
        TextView expenseButton = typeChip("支出", true);
        TextView incomeButton = typeChip("收入", false);
        typeRow.addView(expenseButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        typeRow.addView(incomeButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams typeParams = matchWrap();
        typeParams.setMargins(0, dp(2), 0, dp(12));
        sheet.addView(typeRow, typeParams);

        LinearLayout categoryRow = buildCategoryAmountRow(categorySelection);
        LinearLayout.LayoutParams categoryParams = matchWrap();
        categoryParams.setMargins(0, 0, 0, dp(10));
        sheet.addView(categoryRow, categoryParams);

        TextView dateButton = infoButton("日期", formatSheetDate(draft.date), "›");
        dateButton.setOnClickListener(v -> showDateStepDialog(draft, dateButton));
        LinearLayout.LayoutParams dateParams = matchWrap();
        dateParams.setMargins(0, 0, 0, dp(10));
        sheet.addView(dateButton, dateParams);

        EditText noteInput = new EditText(requireContext());
        noteInput.setHint("添加备注，可不填");
        if (editingTx != null && editingTx.note != null) {
            noteInput.setText(editingTx.note);
        }
        noteInput.setSingleLine(true);
        noteInput.setTextColor(COLOR_TEXT);
        noteInput.setHintTextColor(0xFF9CA3AF);
        noteInput.setTextSize(15);
        noteInput.setBackgroundColor(0x00000000);
        noteInput.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.setMargins(0, 0, 0, dp(2));
        sheet.addView(noteCard(noteInput), noteParams);

        Runnable refreshCategories = () -> {
            fillCategorySelection(draft.type, categorySelection);
            renderCategoryAmountRow(categoryRow, categorySelection);
        };
        refreshCategories.run();
        updateTypeChips(expenseButton, incomeButton, TYPE_INCOME.equals(draft.type));
        updateAmountDisplay(amountDisplay, draft);

        expenseButton.setOnClickListener(v -> {
            draft.type = TYPE_EXPENSE;
            updateTypeChips(expenseButton, incomeButton, false);
            refreshCategories.run();
            updateAmountDisplay(amountDisplay, draft);
        });
        incomeButton.setOnClickListener(v -> {
            draft.type = TYPE_INCOME;
            updateTypeChips(expenseButton, incomeButton, true);
            refreshCategories.run();
            updateAmountDisplay(amountDisplay, draft);
        });

        sheet.addView(buildKeypad(dialog, draft, amountDisplay,
                categorySelection, noteInput, expenseButton, incomeButton, refreshCategories));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private View buildKeypad(
            BottomSheetDialog dialog,
            EntryDraft draft,
            TextView amountDisplay,
            CategorySelection categorySelection,
            EditText noteInput,
            TextView expenseButton,
            TextView incomeButton,
            Runnable refreshCategories
    ) {
        LinearLayout pad = horizontal();
        pad.setPadding(0, dp(14), 0, 0);

        LinearLayout numbers = vertical();
        String[][] rows = {
                {"7", "8", "9"},
                {"4", "5", "6"},
                {"1", "2", "3"},
                {".", "0", "back"}
        };
        for (String[] rowValues : rows) {
            LinearLayout row = horizontal();
            for (String value : rowValues) {
                TextView key = keypadButton(value, 18, false);
                if ("back".equals(value)) {
                    key.setText("");
                    key.setText("⌫");
                    key.setTextSize(20);
                }
                key.setOnClickListener(v -> handleAmountKey(draft, value, amountDisplay));
                row.addView(key, keypadCell());
            }
            numbers.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(54)
            ));
        }
        pad.addView(numbers, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout actions = vertical();
        TextView plus = keypadButton("+", 20, false);
        plus.setOnClickListener(v -> {
            draft.type = TYPE_INCOME;
            updateTypeChips(expenseButton, incomeButton, true);
            refreshCategories.run();
            updateAmountDisplay(amountDisplay, draft);
        });
        actions.addView(plus, fixed(dp(66), dp(54)));

        TextView minus = keypadButton("-", 20, false);
        minus.setOnClickListener(v -> {
            draft.type = TYPE_EXPENSE;
            updateTypeChips(expenseButton, incomeButton, false);
            refreshCategories.run();
            updateAmountDisplay(amountDisplay, draft);
        });
        actions.addView(minus, fixed(dp(66), dp(54)));

        TextView done = keypadButton("完成", 15, true);
        done.setTextColor(COLOR_TEXT);
        done.setBackground(round(COLOR_BEE, dp(14)));
        done.setOnClickListener(v -> saveEntry(dialog, draft, categorySelection, noteInput));
        actions.addView(done, fixed(dp(66), dp(108)));

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(dp(10), 0, 0, 0);
        pad.addView(actions, actionParams);
        return pad;
    }

    private void handleAmountKey(EntryDraft draft, String key, TextView amountDisplay) {
        if ("back".equals(key)) {
            if (!draft.amount.isEmpty()) {
                draft.amount = draft.amount.substring(0, draft.amount.length() - 1);
            }
        } else if (".".equals(key)) {
            if (!draft.amount.contains(".")) {
                draft.amount = draft.amount.isEmpty() ? "0." : draft.amount + ".";
            }
        } else if ("0".equals(key)) {
            if (!"0".equals(draft.amount)) {
                draft.amount = draft.amount + key;
            }
        } else {
            draft.amount = "0".equals(draft.amount) ? key : draft.amount + key;
        }
        updateAmountDisplay(amountDisplay, draft);
    }

    private void updateAmountDisplay(TextView amountDisplay, EntryDraft draft) {
        amountDisplay.setText("¥ " + (draft.amount.isEmpty() ? "0.00" : draft.amount));
        amountDisplay.setTextColor(TYPE_INCOME.equals(draft.type) ? COLOR_GREEN : COLOR_ORANGE);
    }

    private String amountInputText(double amount) {
        String value = String.format(Locale.US, "%.2f", amount);
        if (value.endsWith(".00")) {
            return value.substring(0, value.length() - 3);
        }
        if (value.endsWith("0")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void saveEntry(
            BottomSheetDialog dialog,
            EntryDraft draft,
            CategorySelection categorySelection,
            EditText noteInput
    ) {
        double amount;
        try {
            amount = Double.parseDouble(draft.amount);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "请输入金额", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(requireContext(), "金额必须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (categorySelection.categories.isEmpty()) {
            Toast.makeText(requireContext(), "没有可用类别", Toast.LENGTH_SHORT).show();
            return;
        }
        if (categorySelection.selectedCategory == null) {
            Toast.makeText(requireContext(), "请选择类别", Toast.LENGTH_SHORT).show();
            return;
        }
        CountCategory category = categorySelection.selectedCategory;
        if (draft.editingId == null) {
            repository.addTransaction(
                    draft.type,
                    amount,
                    category.id,
                    draft.date,
                    noteInput.getText().toString().trim()
            );
        } else {
            repository.updateTransaction(
                    draft.editingId,
                    draft.type,
                    amount,
                    category.id,
                    draft.date,
                    noteInput.getText().toString().trim()
            );
        }
        selectedMonth = draft.date.withDayOfMonth(1);
        refreshMonthView();
        dialog.dismiss();
    }

    private void fillCategorySelection(String type, CategorySelection selection) {
        selection.categories = repository.getCategories(type);
        selection.selectedCategory = null;
        if (selection.preferredCategoryId != null) {
            for (CountCategory category : selection.categories) {
                if (category.id == selection.preferredCategoryId) {
                    selection.selectedCategory = category;
                    break;
                }
            }
            selection.preferredCategoryId = null;
        }
        if (selection.selectedCategory == null && !selection.categories.isEmpty()) {
            selection.selectedCategory = selection.categories.get(0);
        }
    }

    private LinearLayout buildCategoryAmountRow(CategorySelection selection) {
        LinearLayout row = vertical();
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(16), COLOR_LINE, 1));
        row.setOnClickListener(v -> showCategoryPicker(selection, () -> renderCategoryAmountRow(row, selection)));
        return row;
    }

    private void renderCategoryAmountRow(LinearLayout row, CategorySelection selection) {
        row.removeAllViews();
        TextView label = text("类别", 12, COLOR_MUTED, false);
        row.addView(label);

        LinearLayout content = horizontal();
        content.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams contentParams = matchWrap();
        contentParams.setMargins(0, dp(4), 0, 0);
        row.addView(content, contentParams);

        CountCategory category = selection.selectedCategory;
        if (category == null) {
            ImageView emptyIcon = plainIcon(CategoryIconMapper.DEFAULT_ICON, 24);
            emptyIcon.setBackground(round(0xFFFFF7D1, dp(18)));
            emptyIcon.setPadding(dp(7), dp(7), dp(7), dp(7));
            content.addView(emptyIcon, fixed(dp(32), dp(32)));
            TextView empty = text("暂无类别", 14, COLOR_MUTED, false);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                    , 1
            );
            emptyParams.setMargins(dp(8), 0, 0, 0);
            content.addView(empty, emptyParams);
            TextView arrow = text("›", 18, COLOR_MUTED, true);
            arrow.setGravity(Gravity.CENTER);
            content.addView(arrow, fixed(dp(20), dp(24)));
            return;
        }

        ImageView icon = plainIcon(category.icon, 24);
        icon.setBackground(round(0xFFFFF7D1, dp(18)));
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        content.addView(icon, fixed(dp(32), dp(32)));
        TextView name = text(category.name, 15, COLOR_TEXT, true);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        nameParams.setMargins(dp(8), 0, 0, 0);
        content.addView(name, nameParams);
        TextView arrow = text("›", 18, COLOR_MUTED, true);
        arrow.setGravity(Gravity.CENTER);
        content.addView(arrow, fixed(dp(20), dp(24)));
    }

    private void showCategoryPicker(CategorySelection selection, Runnable onSelected) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        TextView title = text("选择类别", 18, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)
        ));

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout grid = vertical();
        grid.setPadding(0, dp(12), 0, 0);
        scrollView.addView(grid);

        for (int index = 0; index < selection.categories.size(); index += 4) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 4; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(86), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < selection.categories.size()) {
                    CountCategory category = selection.categories.get(itemIndex);
                    row.addView(categoryPickerCell(category, selection, onSelected, dialog), params);
                } else {
                    row.addView(new View(requireContext()), params);
                }
            }
            grid.addView(row);
        }

        int rowCount = (selection.categories.size() + 3) / 4;
        sheet.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(380), Math.max(dp(120), dp(94) * rowCount))
        ));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private View categoryPickerCell(
            CountCategory category,
            CategorySelection selection,
            Runnable onSelected,
            BottomSheetDialog dialog
    ) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        boolean selected = selection.selectedCategory != null && selection.selectedCategory.id == category.id;
        cell.setBackground(roundStroke(
                selected ? COLOR_BEE_SOFT : COLOR_SURFACE_SOFT,
                dp(16),
                selected ? COLOR_BEE : COLOR_LINE,
                selected ? 2 : 1
        ));
        ImageView icon = plainIcon(category.icon, 24);
        icon.setBackground(round(0xFFFFF7D1, dp(19)));
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        cell.addView(icon, fixed(dp(38), dp(38)));
        TextView name = text(category.displayName().trim(), 12, COLOR_TEXT, false);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setPadding(dp(3), dp(7), dp(3), 0);
        cell.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        cell.setOnClickListener(v -> {
            selection.selectedCategory = category;
            onSelected.run();
            dialog.dismiss();
        });
        return cell;
    }

    private void showDateStepDialog(EntryDraft draft, TextView dateButton) {
        LocalDate initialDate = draft.date;
        LinearLayout content = horizontal();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(8), dp(18), dp(8), dp(18));

        NumberPicker yearPicker = datePicker(initialDate.getYear() - 5, initialDate.getYear() + 5);
        yearPicker.setValue(initialDate.getYear());

        NumberPicker monthPicker = datePicker(1, 12);
        monthPicker.setFormatter(value -> String.format(Locale.CHINA, "%02d", value));
        monthPicker.setValue(initialDate.getMonthValue());

        NumberPicker dayPicker = datePicker(1, YearMonth.from(initialDate).lengthOfMonth());
        dayPicker.setFormatter(value -> String.format(Locale.CHINA, "%02d", value));
        dayPicker.setValue(initialDate.getDayOfMonth());

        NumberPicker.OnValueChangeListener rangeListener =
                (picker, oldValue, newValue) -> updateDayPickerRange(yearPicker, monthPicker, dayPicker);
        yearPicker.setOnValueChangedListener(rangeListener);
        monthPicker.setOnValueChangedListener(rangeListener);

        content.addView(yearPicker, new LinearLayout.LayoutParams(0, dp(156), 1));
        content.addView(monthPicker, new LinearLayout.LayoutParams(0, dp(156), 1));
        content.addView(dayPicker, new LinearLayout.LayoutParams(0, dp(156), 1));

        new AlertDialog.Builder(requireContext())
                .setTitle("选择日期")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    draft.date = LocalDate.of(
                            yearPicker.getValue(),
                            monthPicker.getValue(),
                            dayPicker.getValue()
                    );
                    dateButton.setText(infoText("日期", formatSheetDate(draft.date), "›"));
                })
                .show();
    }

    private NumberPicker datePicker(int min, int max) {
        NumberPicker picker = new NumberPicker(requireContext());
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return picker;
    }

    private void updateDayPickerRange(NumberPicker yearPicker, NumberPicker monthPicker, NumberPicker dayPicker) {
        int maxDay = YearMonth.of(yearPicker.getValue(), monthPicker.getValue()).lengthOfMonth();
        if (dayPicker.getValue() > maxDay) {
            dayPicker.setValue(maxDay);
        }
        dayPicker.setMaxValue(maxDay);
    }

    private void showTransactionActions(CountTransaction tx) {
        new AlertDialog.Builder(requireContext())
                .setItems(new String[]{"修改", "删除"}, (dialog, which) -> {
                    if (which == 0) {
                        showEntrySheet(tx);
                    } else {
                        confirmDeleteTransaction(tx);
                    }
                })
                .show();
    }

    private void confirmDeleteTransaction(CountTransaction tx) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除记录")
                .setMessage("确定删除这笔记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    repository.deleteTransaction(tx.id);
                    refreshMonthView();
                })
                .show();
    }

    private void openStatsFragment() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, new FragmentCountStats())
                .addToBackStack("count_stats")
                .commit();
    }

    private void openCategoryManageFragment() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, new FragmentCategoryManage())
                .addToBackStack("category_manage")
                .commit();
    }

    private void openQualityFragment() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, new FragmentQuality())
                .addToBackStack("quality_home")
                .commit();
    }

    private void showImportDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        TextView title = text("导入 / 导出", 18, COLOR_TEXT, true);
        title.setPadding(dp(2), 0, 0, dp(12));
        sheet.addView(title);

        sheet.addView(actionSheetItem(R.drawable.ic_import, "导入", "支持 Excel / CSV，兼容微信账单", v -> {
            dialog.dismiss();
            showImportSourceSheet();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_export, "导出账单", "导出全部记账记录为 CSV", v -> {
            dialog.dismiss();
            startExport(EXPORT_COUNT);
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_export, "导出打卡", "导出 Quality 打卡记录为 CSV", v -> {
            dialog.dismiss();
            startExport(EXPORT_QUALITY);
        }));

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showImportSourceSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        TextView title = text("选择导入来源", 18, COLOR_TEXT, true);
        title.setPadding(dp(2), 0, 0, dp(12));
        sheet.addView(title);

        sheet.addView(actionSheetItem(R.drawable.ic_import, "微信支付账单", "导入微信导出的账单明细", v -> {
            dialog.dismiss();
            openImportPicker(CountImportService.SOURCE_WECHAT);
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_import, "鲨鱼记账", "日期 / 收支类型 / 类别 / 账户 / 金额", v -> {
            dialog.dismiss();
            openImportPicker(CountImportService.SOURCE_SHARK);
        }));

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void openImportPicker(String source) {
        if (importFileLauncher == null) {
            return;
        }
        pendingImportSource = source;
        importFileLauncher.launch(new String[]{
                "text/*",
                "text/csv",
                "application/csv",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });
    }

    private void handleImportFile(Uri uri) {
        if (uri == null) {
            pendingImportSource = null;
            return;
        }
        String importSource = pendingImportSource == null ? "" : pendingImportSource;
        pendingImportSource = null;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (RuntimeException ignored) {
            // The current one-shot read permission is enough for immediate import.
        }
        Toast.makeText(requireContext(), "正在导入...", Toast.LENGTH_SHORT).show();
        Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                CountImportResult result = CountImportService.importFromUri(appContext, repository, uri, importSource);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> showImportResult(result));
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> showImportError(e));
                }
            }
        }).start();
    }

    private void startExport(String exportType) {
        if (exportFileLauncher == null) {
            return;
        }
        pendingExportType = exportType;
        String prefix = EXPORT_QUALITY.equals(exportType) ? "better_quality_" : "better_count_";
        exportFileLauncher.launch(prefix + LocalDate.now().format(DAY_FORMATTER) + ".csv");
    }

    private void handleExportFile(Uri uri) {
        if (uri == null || pendingExportType == null) {
            pendingExportType = null;
            return;
        }
        String exportType = pendingExportType;
        pendingExportType = null;
        try {
            String csv = EXPORT_QUALITY.equals(exportType) ? buildQualityCsv() : buildCountCsv();
            try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    throw new IOException("无法打开导出文件");
                }
                output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                output.write(csv.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("导出失败")
                    .setMessage(e.getMessage() == null ? "文件无法写入，请重试" : e.getMessage())
                    .setPositiveButton("知道了", null)
                    .show();
        }
    }

    private String buildCountCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("日期,类型,金额,分类,备注\n");
        for (CountTransaction tx : repository.getAllTransactions()) {
            appendCsvRow(
                    builder,
                    tx.date.format(DAY_FORMATTER),
                    TYPE_INCOME.equals(tx.type) ? "收入" : "支出",
                    repository.formatMoney(tx.amount),
                    tx.categoryPath(),
                    tx.note
            );
        }
        return builder.toString();
    }

    private String buildQualityCsv() throws IOException, JSONException {
        JSONArray records = readQualityRecords();
        StringBuilder builder = new StringBuilder();
        builder.append("日期,运动,阅读,思维,口语,睡眠,随记\n");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) {
                continue;
            }
            JSONObject habits = record.optJSONObject("habits");
            appendCsvRow(
                    builder,
                    record.optString("date", ""),
                    habitCsvValue(habits, "运动"),
                    habitCsvValue(habits, "阅读"),
                    habitCsvValue(habits, "思维"),
                    habitCsvValue(habits, "口语"),
                    habitCsvValue(habits, "睡眠"),
                    record.optString("note", "")
            );
        }
        return builder.toString();
    }

    private JSONArray readQualityRecords() throws IOException, JSONException {
        File file = new File(requireContext().getFilesDir(), QUALITY_FILE_NAME);
        if (!file.exists() || file.length() == 0) {
            return new JSONArray();
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int readLength = 0;
            while (readLength < data.length) {
                int count = input.read(data, readLength, data.length - readLength);
                if (count < 0) {
                    break;
                }
                readLength += count;
            }
            if (readLength <= 0) {
                return new JSONArray();
            }
            return new JSONArray(new String(data, 0, readLength, StandardCharsets.UTF_8));
        }
    }

    private String habitCsvValue(JSONObject habits, String name) {
        return habits != null && habits.optInt(name, 0) == 1 ? "1" : "0";
    }

    private void appendCsvRow(StringBuilder builder, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(csvCell(values[i]));
        }
        builder.append("\n");
    }

    private String csvCell(String value) {
        String text = value == null ? "" : value;
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quote) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private void showImportResult(CountImportResult result) {
        if (!isAdded()) {
            return;
        }
        if (result.latestDate != null) {
            selectedMonth = result.latestDate.withDayOfMonth(1);
        }
        refreshMonthView();

        StringBuilder message = new StringBuilder();
        message.append("已导入 ").append(result.insertedRows).append(" 条");
        if (result.skippedRows > 0) {
            message.append("\n跳过 ").append(result.skippedRows).append(" 行");
        }
        for (String item : result.messages()) {
            message.append("\n").append(item);
        }
        if (!result.createdCategories().isEmpty()) {
            message.append("\n\n已自动添加新类别：");
            for (String category : result.createdCategories()) {
                message.append("\n").append(category);
            }
            message.append("\n可进入类别管理选择图标。");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle("导入完成")
                .setMessage(message.toString())
                .setPositiveButton("知道了", null);
        if (!result.createdCategories().isEmpty()) {
            builder.setNegativeButton("去选择图标", (dialog, which) -> openCategoryManageFragment());
        }
        builder.show();
    }

    private void showImportError(Exception e) {
        if (!isAdded()) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("导入失败")
                .setMessage(e.getMessage() == null ? "文件无法读取，请检查格式" : e.getMessage())
                .setPositiveButton("知道了", null)
                .show();
    }

    private String formatSheetDate(LocalDate date) {
        return String.format(Locale.CHINA, "%d/%d/%d",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private TextView infoButton(String label, String value, String suffix) {
        TextView view = text(infoText(label, value, suffix), 15, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumHeight(dp(64));
        view.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(16), COLOR_LINE, 1));
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        return view;
    }

    private String infoText(String label, String value, String suffix) {
        return label + "\n" + value + "  " + suffix;
    }

    private View noteCard(EditText noteInput) {
        LinearLayout card = vertical();
        card.setMinimumHeight(dp(64));
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(16), COLOR_LINE, 1));
        TextView label = text("备注", 12, COLOR_MUTED, false);
        card.addView(label);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)
        );
        inputParams.setMargins(0, dp(2), 0, 0);
        card.addView(noteInput, inputParams);
        return card;
    }

    private TextView typeChip(String label, boolean selected) {
        TextView chip = text(label, 14, COLOR_TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(selected ? COLOR_BEE : 0x00000000, dp(12)));
        return chip;
    }

    private void updateTypeChips(TextView expense, TextView income, boolean isIncome) {
        expense.setBackground(round(isIncome ? 0x00000000 : COLOR_BEE, dp(12)));
        income.setBackground(round(isIncome ? COLOR_BEE : 0x00000000, dp(12)));
    }

    private TextView keypadButton(String label, int sp, boolean highlighted) {
        TextView view = text(label, sp, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(highlighted ? COLOR_BEE : COLOR_SURFACE_SOFT, dp(12)));
        return view;
    }

    private LinearLayout.LayoutParams keypadCell() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private TextView buildFloatingActionButton() {
        TextView button = text("+", 30, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(COLOR_BEE, dp(28)));
        button.setElevation(dp(8));
        button.setOnClickListener(v -> showActionSheet());
        return button;
    }

    private void showActionSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        TextView title = text("记账操作", 18, COLOR_TEXT, true);
        title.setPadding(dp(2), 0, 0, dp(12));
        sheet.addView(title);

        sheet.addView(actionSheetItem("+", "添加记录", "记录一笔收入或支出", v -> {
            dialog.dismiss();
            showEntrySheet();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_stat, "图表统计", "查看趋势和排行榜", v -> {
            dialog.dismiss();
            openStatsFragment();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_category, "类别管理", "新增或整理收支类别", v -> {
            dialog.dismiss();
            openCategoryManageFragment();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_import, "导入数据", "从 Excel / CSV 批量导入", v -> {
            dialog.dismiss();
            showImportDialog();
        }));
        sheet.addView(actionSheetItem("✓", "Quality 打卡", "切换到每日打卡", v -> {
            dialog.dismiss();
            openQualityFragment();
        }));

        dialog.setContentView(sheet);
        dialog.show();
    }

    private View actionSheetItem(String mark, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(COLOR_SURFACE_SOFT, dp(16)));
        row.setOnClickListener(listener);

        TextView icon = text(mark, 20, COLOR_TEXT, true);
        icon.setGravity(Gravity.CENTER);
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

    private View actionSheetItem(int iconResId, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(COLOR_SURFACE_SOFT, dp(16)));
        row.setOnClickListener(listener);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconResId);
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

    private FrameLayout.LayoutParams actionButtonParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
        params.setMargins(dp(18), dp(18), dp(20), dp(20));
        return params;
    }

    private ImageView iconBubble(String icon) {
        ImageView bubble = plainIcon(icon, 22);
        bubble.setBackground(round(0xFFFFF7D1, dp(20)));
        bubble.setPadding(dp(8), dp(8), dp(8), dp(8));
        return bubble;
    }

    private ImageView plainIcon(String icon, int sizeDp) {
        ImageView image = new ImageView(requireContext());
        image.setImageResource(CategoryIconMapper.drawableResId(requireContext(), icon));
        image.setColorFilter(COLOR_TEXT);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setMinimumWidth(dp(sizeDp));
        image.setMinimumHeight(dp(sizeDp));
        return image;
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

    @Override
    public void onStart() {
        super.onStart();
        LogUtil.d(TAG, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        LogUtil.d(TAG, "onResume");
    }

    @Override
    public void onPause() {
        super.onPause();
        LogUtil.d(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        LogUtil.d(TAG, "onStop");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LogUtil.d(TAG, "onDestroyView");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "onDestroy");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        LogUtil.d(TAG, "onDetach");
    }
}

