package com.example.quality.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FragmentMore extends Fragment {
    private static final String TAG = "FragmentMore";
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_ORANGE = 0xFFEF6C00;
    private static final int COLOR_GREEN = 0xFF16A34A;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_MUTED = 0xFF6B7280;
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);

    private CountRepository repository;
    private LocalDate selectedMonth = LocalDate.now().withDayOfMonth(1);
    private TextView monthLabel;
    private TextView incomeValue;
    private TextView expenseValue;
    private TextView balanceValue;
    private LinearLayout transactionList;
    private ActivityResultLauncher<String[]> importFileLauncher;

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
        frame.setBackgroundColor(0xFFFAFAFA);

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
        transactionList.setPadding(dp(14), dp(10), dp(14), dp(12));
        scrollView.addView(transactionList);
        main.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        frame.addView(buildFloatingActionCard(), actionCardParams());
        return frame;
    }

    private View buildHeader() {
        LinearLayout header = vertical();
        header.setBackgroundColor(COLOR_BEE);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout summaryRow = horizontal();
        summaryRow.setGravity(Gravity.BOTTOM);
        summaryRow.setPadding(0, 0, 0, 0);

        monthLabel = text("", 17, COLOR_TEXT, true);
        monthLabel.setGravity(Gravity.CENTER_VERTICAL);
        monthLabel.setOnClickListener(v -> showMonthPicker());
        summaryRow.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(64), 1.1f));

        incomeValue = headerMetric(summaryRow, "收入");
        expenseValue = headerMetric(summaryRow, "支出");
        balanceValue = headerMetric(summaryRow, "结余");
        header.addView(summaryRow);
        return header;
    }

    private TextView headerMetric(LinearLayout row, String label) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        TextView title = text(label, 11, 0xFF5F4B00, false);
        TextView value = text("0.00", 13, COLOR_TEXT, true);
        value.setGravity(Gravity.CENTER);
        box.addView(title);
        box.addView(value);
        row.addView(box, new LinearLayout.LayoutParams(0, dp(52), 1));
        return value;
    }

    private void refreshMonthView() {
        if (transactionList == null) {
            return;
        }
        LocalDate start = selectedMonth.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        monthLabel.setText(String.format(Locale.CHINA, "%d年\n%02d月⌄",
                selectedMonth.getYear(), selectedMonth.getMonthValue()));

        CountStats stats = repository.getStats(start, end);
        incomeValue.setText(repository.formatMoney(stats.income));
        expenseValue.setText(repository.formatMoney(stats.expense));
        balanceValue.setText(repository.formatMoney(stats.balance()));
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
            transactionList.addView(transactionRow(tx));
        }
    }

    private View transactionRow(CountTransaction tx) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setOnLongClickListener(v -> {
            showTransactionActions(tx);
            return true;
        });

        row.addView(iconBubble(tx.categoryIcon), fixed(dp(38), dp(38)));

        LinearLayout textBox = vertical();
        TextView title = text(tx.displayCategoryName(), 15, COLOR_TEXT, false);
        TextView detail = text(tx.date.format(DAY_FORMATTER) + " · " + tx.categoryPath(),
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
        box.setPadding(0, dp(80), 0, dp(40));
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

        content.addView(yearPicker, new LinearLayout.LayoutParams(0, dp(146), 1));
        content.addView(monthPicker, new LinearLayout.LayoutParams(0, dp(146), 1));

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
        sheet.setPadding(dp(18), dp(14), dp(18), dp(18));
        sheet.setBackgroundColor(0xFFFFFFFF);

        CategorySelection categorySelection = new CategorySelection();
        if (editingTx != null) {
            categorySelection.preferredCategoryId = editingTx.categoryId;
        }
        LinearLayout amountRow = horizontal();
        amountRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout categoryRow = buildCategoryAmountRow(categorySelection);
        amountRow.addView(categoryRow, new LinearLayout.LayoutParams(0, dp(38), 1));

        TextView amountDisplay = text("+ 0", 22, COLOR_TEXT, true);
        amountDisplay.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
        );
        amountParams.setMargins(dp(10), 0, 0, 0);
        amountRow.addView(amountDisplay, amountParams);
        sheet.addView(amountRow);

        LinearLayout typeRow = horizontal();
        typeRow.setPadding(0, dp(8), 0, 0);
        TextView expenseButton = typeChip("支出", true);
        TextView incomeButton = typeChip("收入", false);
        typeRow.addView(expenseButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        typeRow.addView(incomeButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        sheet.addView(typeRow);

        EditText noteInput = new EditText(requireContext());
        noteInput.setHint("备注...");
        if (editingTx != null && editingTx.note != null) {
            noteInput.setText(editingTx.note);
        }
        noteInput.setSingleLine(true);
        noteInput.setTextColor(COLOR_TEXT);
        noteInput.setHintTextColor(0xFF9CA3AF);
        noteInput.setBackground(round(0xFFF3F4F6, dp(12)));
        noteInput.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.setMargins(0, dp(10), 0, 0);
        sheet.addView(noteInput, noteParams);

        TextView dateButton = keypadButton(formatSheetDate(draft.date), 12, false);
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

        sheet.addView(buildKeypad(dialog, draft, amountDisplay, dateButton,
                categorySelection, noteInput, expenseButton, incomeButton, refreshCategories));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private View buildKeypad(
            BottomSheetDialog dialog,
            EntryDraft draft,
            TextView amountDisplay,
            TextView dateButton,
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
                    Drawable backIcon = requireContext().getDrawable(R.drawable.ic_back);
                    if (backIcon != null) {
                        backIcon.setBounds(0, 0, dp(24), dp(24));
                        backIcon.setTint(COLOR_TEXT);
                        key.setCompoundDrawables(null, backIcon, null, null);
                    }
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
        dateButton.setOnClickListener(v -> showDateStepDialog(draft, dateButton));
        actions.addView(dateButton, fixed(dp(66), dp(54)));

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
        done.setBackground(round(COLOR_BEE, dp(12)));
        done.setOnClickListener(v -> saveEntry(dialog, draft, categorySelection, noteInput));
        actions.addView(done, fixed(dp(66), dp(54)));

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
        String symbol = TYPE_INCOME.equals(draft.type) ? "+ " : "- ";
        amountDisplay.setText(symbol + (draft.amount.isEmpty() ? "0" : draft.amount));
        amountDisplay.setTextColor(TYPE_INCOME.equals(draft.type) ? COLOR_GREEN : COLOR_TEXT);
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
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, dp(8), 0);
        row.setOnClickListener(v -> showCategoryPicker(selection, () -> renderCategoryAmountRow(row, selection)));
        return row;
    }

    private void renderCategoryAmountRow(LinearLayout row, CategorySelection selection) {
        row.removeAllViews();
        CountCategory category = selection.selectedCategory;
        if (category == null) {
            ImageView emptyIcon = plainIcon(CategoryIconMapper.DEFAULT_ICON, 24);
            emptyIcon.setBackground(round(0xFFFFF7D1, dp(18)));
            emptyIcon.setPadding(dp(7), dp(7), dp(7), dp(7));
            row.addView(emptyIcon, fixed(dp(36), dp(36)));
            TextView empty = text("暂无类别", 14, COLOR_MUTED, false);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            emptyParams.setMargins(dp(8), 0, 0, 0);
            row.addView(empty, emptyParams);
            return;
        }

        ImageView icon = plainIcon(category.icon, 24);
        icon.setBackground(round(0xFFFFF7D1, dp(18)));
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        row.addView(icon, fixed(dp(36), dp(36)));
        TextView name = text(category.name, 15, COLOR_TEXT, true);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(dp(8), 0, 0, 0);
        row.addView(name, nameParams);
    }

    private void showCategoryPicker(CategorySelection selection, Runnable onSelected) {
        LinearLayout list = vertical();
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(list);
        final AlertDialog[] holder = new AlertDialog[1];

        for (CountCategory category : selection.categories) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.addView(plainIcon(category.icon, 24), fixed(dp(34), dp(34)));
            TextView name = text(category.displayName().trim(), 15, COLOR_TEXT, false);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            nameParams.setMargins(dp(10), 0, dp(8), 0);
            row.addView(name, nameParams);
            row.setOnClickListener(v -> {
                selection.selectedCategory = category;
                onSelected.run();
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(50)
            ));
        }

        holder[0] = new AlertDialog.Builder(requireContext())
                .setTitle("选择类别")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .create();
        holder[0].show();
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
                    dateButton.setText(formatSheetDate(draft.date));
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

    private void showImportDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("导入 Excel / CSV")
                .setMessage("支持 .csv 和 .xlsx。推荐表头：日期、类型、金额、分类、备注；类型可填收入或支出。没有表头时按这个顺序读取。")
                .setNegativeButton("取消", null)
                .setPositiveButton("选择文件", (dialog, which) -> openImportPicker())
                .show();
    }

    private void openImportPicker() {
        if (importFileLauncher == null) {
            return;
        }
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
            return;
        }
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
                CountImportResult result = CountImportService.importFromUri(appContext, repository, uri);
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
        new AlertDialog.Builder(requireContext())
                .setTitle("导入完成")
                .setMessage(message.toString())
                .setPositiveButton("知道了", null)
                .show();
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

    private TextView typeChip(String label, boolean selected) {
        TextView chip = text(label, 14, COLOR_TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(selected ? COLOR_BEE : 0xFFF3F4F6, dp(12)));
        return chip;
    }

    private void updateTypeChips(TextView expense, TextView income, boolean isIncome) {
        expense.setBackground(round(isIncome ? 0xFFF3F4F6 : COLOR_BEE, dp(12)));
        income.setBackground(round(isIncome ? COLOR_BEE : 0xFFF3F4F6, dp(12)));
    }

    private TextView keypadButton(String label, int sp, boolean highlighted) {
        TextView view = text(label, sp, COLOR_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(highlighted ? 0xFFF3F4F6 : 0xFFFFFFFF, dp(12)));
        return view;
    }

    private LinearLayout.LayoutParams keypadCell() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout buildFloatingActionCard() {
        LinearLayout card = horizontal();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setBackground(round(0xFFFFFFFF, dp(24)));
        card.setElevation(dp(6));
        card.addView(actionCardButton("图表", false, v -> openStatsFragment()), fixed(dp(48), dp(40)));
        card.addView(actionCardButton("导入", false, v -> showImportDialog()), fixed(dp(48), dp(40)));
        card.addView(actionCardButton("类别", false, v -> openCategoryManageFragment()), fixed(dp(48), dp(40)));
        card.addView(actionCardButton("+", true, v -> showEntrySheet()), fixed(dp(48), dp(40)));
        return card;
    }

    private TextView actionCardButton(String label, boolean primary, View.OnClickListener listener) {
        TextView button = text(label, primary ? 24 : 13, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(primary ? COLOR_BEE : 0x00000000, dp(20)));
        button.setOnClickListener(listener);
        return button;
    }

    private FrameLayout.LayoutParams actionCardParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(200), dp(48), Gravity.BOTTOM | Gravity.END);
        params.setMargins(dp(18), dp(18), dp(18), dp(18));
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

