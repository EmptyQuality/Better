package com.example.quality.fragment;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.quality.R;
import com.example.quality.count.CategoryIconMapper;
import com.example.quality.count.CountCategory;
import com.example.quality.count.CountCustomIcon;
import com.example.quality.count.CountImportResult;
import com.example.quality.count.CountImportService;
import com.example.quality.count.CountRepository;
import com.example.quality.count.CountStats;
import com.example.quality.count.CountTransaction;
import com.example.quality.util.AppInsets;
import com.example.quality.util.LogUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FragmentCount extends Fragment {
    private static final String TAG = "FragmentCount";
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final String EXPORT_COUNT = "count";
    private static final String EXPORT_QUALITY = "quality";
    private static final String QUALITY_FILE_NAME = "quality_record.json";
    private static final String QUALITY_ITEMS_FILE_NAME = "quality_items.json";
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
    private static final DateTimeFormatter GROUP_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("MM月dd日 EEEE", Locale.ENGLISH);

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
    private ActivityResultLauncher<String> exportZipFileLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> customIconPickerLauncher;
    private ActivityResultLauncher<String> customIconPackPickerLauncher;
    private String pendingImportSource;
    private String pendingExportType;
    private File pendingPhotoFile;
    private EntryDraft pendingImageDraft;
    private Runnable pendingImageRefresh;
    private boolean pendingPhotoOpensNewRecord;
    private LinearLayout pendingCustomIconGrid;
    private LinearLayout pendingBuiltinIconGrid;
    private String[] pendingCustomIconSelection;
    private String pendingReplacingCustomIconId;
    private String pendingCustomIconType;

    private static class CategorySelection {
        List<CountCategory> categories = new ArrayList<>();
        CountCategory selectedCategory;
        Long preferredCategoryId;
        String type = TYPE_EXPENSE;
    }

    private static class EntryDraft {
        Long editingId;
        String type = TYPE_EXPENSE;
        String amount = "";
        Double pendingAmount;
        String pendingOperator;
        LocalDate date = LocalDate.now();
        List<String> imagePaths = new ArrayList<>();
        List<String> originalImagePaths = new ArrayList<>();
        Set<String> addedImagePaths = new HashSet<>();
        Set<String> removedImagePaths = new HashSet<>();
        boolean entrySaved;
    }

    private static class ImageExportItem {
        final File file;
        final String relativePath;

        ImageExportItem(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
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
        exportZipFileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                this::handleCountZipExportFile
        );
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::handlePhotoTaken
        );
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleImagePicked
        );
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

        View header = buildHeader();
        AppInsets.applySystemBarPadding(header, true, false);
        main.addView(header);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, dp(92));
        AppInsets.applySystemBarPadding(scrollView, false, true);
        transactionList = vertical();
        transactionList.setPadding(dp(18), dp(14), dp(18), dp(12));
        scrollView.addView(transactionList);
        main.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        View actionButton = buildFloatingActionButton();
        frame.addView(actionButton, actionButtonParams());
        AppInsets.applySystemBarMargins(actionButton, false, false, true, true);
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
        int index = 0;
        while (index < transactions.size()) {
            LocalDate date = transactions.get(index).date;
            List<CountTransaction> dayTransactions = new ArrayList<>();
            while (index < transactions.size() && transactions.get(index).date.equals(date)) {
                dayTransactions.add(transactions.get(index));
                index++;
            }
            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, 0, 0, dp(12));
            transactionList.addView(transactionGroupCard(date, dayTransactions), params);
        }
    }

    private View transactionGroupCard(LocalDate date, List<CountTransaction> transactions) {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(12), dp(14), dp(4));
        card.setBackground(round(COLOR_SURFACE, dp(18)));
        card.setElevation(dp(2));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView day = text(date.format(GROUP_DAY_FORMATTER), 15, COLOR_MUTED, false);
        header.addView(day, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView total = text(daySummary(transactions), 14, COLOR_MUTED, false);
        total.setGravity(Gravity.END);
        header.addView(total);
        card.addView(header);

        for (int i = 0; i < transactions.size(); i++) {
            card.addView(transactionGroupRow(transactions.get(i)));
            if (i < transactions.size() - 1) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(0x1AE0D6C7);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                );
                dividerParams.setMargins(dp(50), 0, 0, 0);
                card.addView(divider, dividerParams);
            }
        }
        return card;
    }

    private String daySummary(List<CountTransaction> transactions) {
        double income = 0;
        double expense = 0;
        for (CountTransaction tx : transactions) {
            if (TYPE_INCOME.equals(tx.type)) {
                income += tx.amount;
            } else {
                expense += tx.amount;
            }
        }
        if (income > 0 && expense > 0) {
            return "收入: " + repository.formatMoney(income)
                    + "  支出: " + repository.formatMoney(expense);
        }
        if (income > 0) {
            return "收入: " + repository.formatMoney(income);
        }
        return "支出: " + repository.formatMoney(expense);
    }

    private View transactionGroupRow(CountTransaction tx) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setOnClickListener(v -> showEntrySheet(tx));
        row.setOnLongClickListener(v -> {
            confirmDeleteTransaction(tx);
            return true;
        });

        row.addView(iconBubble(tx.categoryIcon), fixed(dp(38), dp(38)));

        LinearLayout textBox = vertical();
        TextView title = text(tx.displayCategoryName(), 15, COLOR_TEXT, true);
        TextView detail = text(tx.categoryPath(), 11, COLOR_MUTED, false);
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
                15, income ? COLOR_GREEN : COLOR_ORANGE, true);
        amount.setGravity(Gravity.END);
        row.addView(amount);

        ImageView detailIcon = new ImageView(requireContext());
        detailIcon.setImageResource(R.drawable.ic_detail);
        detailIcon.setColorFilter(COLOR_MUTED);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        detailIcon.setPadding(dp(5), dp(5), dp(5), dp(5));
        detailIcon.setBackground(round(COLOR_BEE_SOFT, dp(12)));
        detailIcon.setOnClickListener(v -> openTransactionDetail(tx.id));
        LinearLayout.LayoutParams detailParams = fixed(dp(24), dp(24));
        detailParams.setMargins(dp(8), 0, 0, 0);
        row.addView(detailIcon, detailParams);
        return row;
    }

    private View transactionRow(CountTransaction tx) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(round(COLOR_SURFACE, dp(18)));
        row.setElevation(dp(2));
        row.setOnClickListener(v -> showEntrySheet(tx));
        row.setOnLongClickListener(v -> {
            confirmDeleteTransaction(tx);
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

        ImageView detailIcon = new ImageView(requireContext());
        detailIcon.setImageResource(R.drawable.ic_detail);
        detailIcon.setColorFilter(COLOR_MUTED);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        detailIcon.setPadding(dp(5), dp(5), dp(5), dp(5));
        detailIcon.setBackground(round(COLOR_BEE_SOFT, dp(12)));
        detailIcon.setOnClickListener(v -> openTransactionDetail(tx.id));
        LinearLayout.LayoutParams detailParams = fixed(dp(24), dp(24));
        detailParams.setMargins(dp(8), 0, 0, 0);
        row.addView(detailIcon, detailParams);
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
        return compactPickerColumn(picker, 104);
    }

    private View compactDatePickerColumn(NumberPicker picker) {
        return compactPickerColumn(picker, 82);
    }

    private View compactPickerColumn(NumberPicker picker, int widthDp) {
        FrameLayout column = new FrameLayout(requireContext());
        column.setPadding(dp(12), 0, dp(12), 0);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(widthDp),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        column.addView(picker, params);
        return column;
    }

    private void showEntrySheet() {
        showEntrySheet(null, new ArrayList<>());
    }

    private void showEntrySheet(@Nullable CountTransaction editingTx) {
        showEntrySheet(editingTx, editingTx == null ? new ArrayList<>() : editingTx.imagePaths);
    }

    private void showEntrySheet(@Nullable CountTransaction editingTx, @Nullable String imagePath) {
        List<String> imagePaths = new ArrayList<>();
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            imagePaths.add(imagePath);
        }
        showEntrySheet(editingTx, imagePaths);
    }

    private void showEntrySheet(@Nullable CountTransaction editingTx, @Nullable List<String> imagePaths) {
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
        if (imagePaths != null) {
            draft.imagePaths.addAll(imagePaths);
            if (editingTx == null) {
                draft.addedImagePaths.addAll(imagePaths);
            } else {
                draft.originalImagePaths.addAll(imagePaths);
            }
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

        ScrollView formScroll = new ScrollView(requireContext());
        formScroll.setFillViewport(false);
        formScroll.setClipToPadding(false);
        LinearLayout form = vertical();
        formScroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sheet.addView(formScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
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
        form.addView(typeRow, typeParams);

        LinearLayout categoryRow = buildCategoryAmountRow(categorySelection);
        LinearLayout.LayoutParams categoryParams = matchWrap();
        categoryParams.setMargins(0, 0, 0, dp(10));
        form.addView(categoryRow, categoryParams);

        TextView dateButton = infoButton("日期", formatSheetDate(draft.date), "›");
        dateButton.setOnClickListener(v -> showDateStepDialog(draft, dateButton));
        LinearLayout.LayoutParams dateParams = matchWrap();
        dateParams.setMargins(0, 0, 0, dp(10));
        form.addView(dateButton, dateParams);

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
        form.addView(noteCard(noteInput), noteParams);

        LinearLayout photoSection = vertical();
        LinearLayout.LayoutParams photoParams = matchWrap();
        photoParams.setMargins(0, dp(10), 0, 0);
        form.addView(photoSection, photoParams);
        final Runnable[] refreshImages = new Runnable[1];
        refreshImages[0] = () -> renderPhotoAttachmentCard(photoSection, draft, refreshImages[0]);
        refreshImages[0].run();

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

        sheet.addView(buildKeypad(dialog, draft, amountDisplay, categorySelection, noteInput));
        dialog.setOnDismissListener(ignored -> {
            if (!draft.entrySaved) {
                deleteAddedImages(draft);
            }
            pendingImageDraft = null;
            pendingImageRefresh = null;
        });
        AppInsets.showFittedBottomSheet(dialog, sheet);
    }

    private View buildKeypad(
            BottomSheetDialog dialog,
            EntryDraft draft,
            TextView amountDisplay,
            CategorySelection categorySelection,
            EditText noteInput
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
        plus.setOnClickListener(v -> handleOperatorKey(draft, "+", amountDisplay));
        actions.addView(plus, fixed(dp(66), dp(54)));

        TextView minus = keypadButton("-", 20, false);
        minus.setOnClickListener(v -> handleOperatorKey(draft, "-", amountDisplay));
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

    private void handleOperatorKey(EntryDraft draft, String operator, TextView amountDisplay) {
        double current = parseDraftAmount(draft.amount);
        if (draft.pendingOperator != null && draft.pendingAmount != null && !draft.amount.isEmpty()) {
            current = calculateAmount(draft.pendingAmount, current, draft.pendingOperator);
        }
        draft.pendingAmount = current;
        draft.pendingOperator = operator;
        draft.amount = "";
        updateAmountDisplay(amountDisplay, draft);
    }

    private void updateAmountDisplay(TextView amountDisplay, EntryDraft draft) {
        String text = draft.amount.isEmpty() ? "0.00" : draft.amount;
        if (draft.pendingOperator != null && draft.pendingAmount != null) {
            text = amountInputText(draft.pendingAmount) + " " + draft.pendingOperator + " " + text;
        }
        amountDisplay.setText("¥ " + text);
        amountDisplay.setTextColor(TYPE_INCOME.equals(draft.type) ? COLOR_GREEN : COLOR_ORANGE);
    }

    private double finalDraftAmount(EntryDraft draft) {
        double current = parseDraftAmount(draft.amount);
        if (draft.pendingOperator == null || draft.pendingAmount == null) {
            return current;
        }
        return calculateAmount(draft.pendingAmount, current, draft.pendingOperator);
    }

    private double calculateAmount(double left, double right, String operator) {
        return "-".equals(operator) ? left - right : left + right;
    }

    private double parseDraftAmount(String value) {
        if (value == null || value.isEmpty() || ".".equals(value)) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
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
            amount = finalDraftAmount(draft);
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
                    noteInput.getText().toString().trim(),
                    draft.imagePaths
            );
        } else {
            repository.updateTransaction(
                    draft.editingId,
                    draft.type,
                    amount,
                    category.id,
                    draft.date,
                    noteInput.getText().toString().trim(),
                    draft.imagePaths
            );
        }
        deleteRemovedImages(draft);
        selectedMonth = draft.date.withDayOfMonth(1);
        refreshMonthView();
        draft.entrySaved = true;
        dialog.dismiss();
    }

    private void fillCategorySelection(String type, CategorySelection selection) {
        selection.type = type;
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

        int totalItems = selection.categories.size() + 1;
        for (int index = 0; index < totalItems; index += 4) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 4; column++) {
                int itemIndex = index + column;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(86), 1);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                if (itemIndex < selection.categories.size()) {
                    CountCategory category = selection.categories.get(itemIndex);
                    row.addView(categoryPickerCell(category, selection, onSelected, dialog), params);
                } else if (itemIndex == selection.categories.size()) {
                    row.addView(categoryPickerAddCell(selection, onSelected, dialog), params);
                } else {
                    row.addView(new View(requireContext()), params);
                }
            }
            grid.addView(row);
        }

        int rowCount = (totalItems + 3) / 4;
        sheet.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(dp(380), Math.max(dp(120), dp(94) * rowCount))
        ));
        AppInsets.showScrollableBottomSheet(dialog, sheet);
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

    private View categoryPickerAddCell(
            CategorySelection selection,
            Runnable onSelected,
            BottomSheetDialog pickerDialog
    ) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(16), COLOR_LINE, 1));

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_add);
        icon.setColorFilter(COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setBackground(round(0xFFFFF7D1, dp(19)));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        cell.addView(icon, fixed(dp(38), dp(38)));

        TextView name = text("新增", 12, COLOR_TEXT, false);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(3), dp(7), dp(3), 0);
        cell.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        cell.setOnClickListener(v -> {
            pickerDialog.dismiss();
            showInlineCategoryEditor(selection, onSelected);
        });
        return cell;
    }

    private void showInlineCategoryEditor(CategorySelection selection, Runnable onSelected) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        TextView title = text("新增类别", 18, COLOR_TEXT, true);
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
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        inputParams.setMargins(0, dp(14), 0, dp(14));
        sheet.addView(nameInput, inputParams);

        LinearLayout iconArea = vertical();
        sheet.addView(iconArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        LinearLayout iconGrid = vertical();
        String[] selectedIcon = {CategoryIconMapper.defaultIcon(selection.type)};
        iconArea.addView(inlineIconScrollSection("选择图标", iconGrid), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                2
        ));
        renderInlineCategoryIconGrid(iconGrid, selectedIcon);

        LinearLayout customIconGrid = vertical();
        LinearLayout.LayoutParams customSectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        customSectionParams.setMargins(0, dp(12), 0, dp(14));
        iconArea.addView(inlineIconScrollSection("新增图标", customIconGrid), customSectionParams);
        renderInlineCustomIconGrid(customIconGrid, iconGrid, selectedIcon, selection.type);

        TextView save = text("保存类别", 16, COLOR_TEXT, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(COLOR_BEE, dp(16)));
        save.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "请输入类别名称", Toast.LENGTH_SHORT).show();
                return;
            }
            long categoryId = repository.addCategory(name, selection.type, selectedIcon[0]);
            selection.preferredCategoryId = categoryId;
            fillCategorySelection(selection.type, selection);
            onSelected.run();
            Toast.makeText(requireContext(), "类别已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        sheet.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        AppInsets.showFittedBottomSheet(dialog, sheet);
    }

    private View inlineIconScrollSection(String titleText, LinearLayout grid) {
        LinearLayout section = vertical();
        TextView title = text(titleText, 14, COLOR_MUTED, false);
        section.addView(title);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setClipToPadding(false);
        scrollView.setFillViewport(false);
        grid.setPadding(0, dp(6), 0, dp(4));
        scrollView.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        section.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        return section;
    }

    private void renderInlineCategoryIconGrid(LinearLayout iconGrid, String[] selectedIcon) {
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
                    row.addView(inlineCategoryIconOption(iconRefs.get(itemIndex), iconGrid, selectedIcon), params);
                } else {
                    row.addView(new View(requireContext()), params);
                }
            }
            iconGrid.addView(row);
        }
    }

    private View inlineCategoryIconOption(String iconKey, LinearLayout iconGrid, String[] selectedIcon) {
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
            renderInlineCategoryIconGrid(iconGrid, selectedIcon);
        });
        return box;
    }

    private void renderInlineCustomIconGrid(
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon,
            String type
    ) {
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
                    row.addView(inlineCustomIconOption(
                            customIcons.get(itemIndex),
                            customIconGrid,
                            builtinIconGrid,
                            selectedIcon,
                            type
                    ), params);
                } else if (itemIndex == customIcons.size()) {
                    row.addView(inlineIconActionOption(
                            R.drawable.ic_import,
                            v -> launchCustomIconPicker(customIconGrid, builtinIconGrid, selectedIcon, null, type)
                    ), params);
                } else if (itemIndex == customIcons.size() + 1) {
                    row.addView(inlineIconActionOption(
                            R.drawable.ic_compress,
                            v -> launchCustomIconPackPicker(customIconGrid, builtinIconGrid, selectedIcon, type)
                    ), params);
                } else {
                    row.addView(new View(requireContext()), params);
                }
            }
            customIconGrid.addView(row);
        }
    }

    private View inlineCustomIconOption(
            CountCustomIcon customIcon,
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon,
            String type
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
            renderInlineCategoryIconGrid(builtinIconGrid, selectedIcon);
            renderInlineCustomIconGrid(customIconGrid, builtinIconGrid, selectedIcon, type);
        });
        box.setOnLongClickListener(v -> {
            showInlineCustomIconActions(customIcon, customIconGrid, builtinIconGrid, selectedIcon, type);
            return true;
        });
        return box;
    }

    private View inlineIconActionOption(int iconResId, View.OnClickListener listener) {
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

    private void showInlineCustomIconActions(
            CountCustomIcon customIcon,
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon,
            String type
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);
        TextView title = text("图标操作", 18, COLOR_TEXT, true);
        title.setPadding(dp(2), 0, 0, dp(12));
        sheet.addView(title);
        sheet.addView(actionSheetItem(R.drawable.ic_import, "替换图标", "使用新图片替换当前图标", v -> {
            dialog.dismiss();
            launchCustomIconPicker(customIconGrid, builtinIconGrid, selectedIcon, customIcon.id, type);
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_category_setting, "删除图标", "使用该图标的类别会切换为默认图标", v -> {
            dialog.dismiss();
            repository.deleteCustomIcon(customIcon.id);
            if (customIcon.iconRef().equals(selectedIcon[0])) {
                selectedIcon[0] = CategoryIconMapper.defaultIcon(type);
            }
            renderInlineCategoryIconGrid(builtinIconGrid, selectedIcon);
            renderInlineCustomIconGrid(customIconGrid, builtinIconGrid, selectedIcon, type);
            refreshMonthView();
            Toast.makeText(requireContext(), "图标已删除", Toast.LENGTH_SHORT).show();
        }));
        AppInsets.showScrollableBottomSheet(dialog, sheet);
    }

    private void launchCustomIconPicker(
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon,
            @Nullable String replaceId,
            String type
    ) {
        pendingCustomIconGrid = customIconGrid;
        pendingBuiltinIconGrid = builtinIconGrid;
        pendingCustomIconSelection = selectedIcon;
        pendingReplacingCustomIconId = replaceId;
        pendingCustomIconType = type;
        customIconPickerLauncher.launch("image/*");
    }

    private void launchCustomIconPackPicker(
            LinearLayout customIconGrid,
            LinearLayout builtinIconGrid,
            String[] selectedIcon,
            String type
    ) {
        pendingCustomIconGrid = customIconGrid;
        pendingBuiltinIconGrid = builtinIconGrid;
        pendingCustomIconSelection = selectedIcon;
        pendingReplacingCustomIconId = null;
        pendingCustomIconType = type;
        customIconPackPickerLauncher.launch("*/*");
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

        content.addView(compactDatePickerColumn(yearPicker), new LinearLayout.LayoutParams(0, dp(156), 1));
        content.addView(compactDatePickerColumn(monthPicker), new LinearLayout.LayoutParams(0, dp(156), 1));
        content.addView(compactDatePickerColumn(dayPicker), new LinearLayout.LayoutParams(0, dp(156), 1));

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

    private void confirmDeleteTransaction(CountTransaction tx) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除记录")
                .setMessage("确定删除这笔记录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    repository.deleteTransaction(tx.id);
                    for (String imagePath : tx.imagePaths) {
                        deleteImageFile(imagePath);
                    }
                    refreshMonthView();
                })
                .show();
    }

    private void openTransactionDetail(long transactionId) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, FragmentCountTransactionDetail.newInstance(transactionId))
                .addToBackStack("transaction_detail")
                .commit();
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
        sheet.addView(actionSheetItem(R.drawable.ic_export, "导出账单", "导出 CSV 与图片 ZIP 备份", v -> {
            dialog.dismiss();
            startExport(EXPORT_COUNT);
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_export, "导出打卡", "导出 Quality 打卡记录为 CSV", v -> {
            dialog.dismiss();
            startExport(EXPORT_QUALITY);
        }));

        AppInsets.showScrollableBottomSheet(dialog, sheet);
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

        AppInsets.showScrollableBottomSheet(dialog, sheet);
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
        pendingExportType = exportType;
        String date = LocalDate.now().format(DAY_FORMATTER);
        if (EXPORT_COUNT.equals(exportType)) {
            if (exportZipFileLauncher == null) {
                pendingExportType = null;
                return;
            }
            exportZipFileLauncher.launch("better_count_" + date + ".zip");
            return;
        }
        if (exportFileLauncher == null) {
            pendingExportType = null;
            return;
        }
        exportFileLauncher.launch("better_quality_" + date + ".csv");
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

    private void handleCountZipExportFile(Uri uri) {
        if (uri == null || pendingExportType == null) {
            pendingExportType = null;
            return;
        }
        pendingExportType = null;
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("无法打开导出文件");
            }
            writeCountZip(output);
            Toast.makeText(requireContext(), "导出成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("导出失败")
                    .setMessage(e.getMessage() == null ? "文件无法写入，请重试" : e.getMessage())
                    .setPositiveButton("知道了", null)
                    .show();
        }
    }

    private void writeCountZip(OutputStream output) throws IOException {
        List<ImageExportItem> images = new ArrayList<>();
        String csv = buildCountCsv(images);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("transactions.csv"));
            zip.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            zip.write(csv.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            byte[] buffer = new byte[8192];
            for (ImageExportItem image : images) {
                zip.putNextEntry(new ZipEntry(image.relativePath));
                try (InputStream input = new FileInputStream(image.file)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private String buildCountCsv() {
        return buildCountCsv(new ArrayList<>());
    }

    private String buildCountCsv(List<ImageExportItem> images) {
        StringBuilder builder = new StringBuilder();
        builder.append("日期,类型,金额,分类,备注,图片\n");
        Set<String> usedImageNames = new HashSet<>();
        for (CountTransaction tx : repository.getAllTransactions()) {
            String imageRelativePath = exportImagePaths(tx, images, usedImageNames);
            appendCsvRow(
                    builder,
                    tx.date.format(DAY_FORMATTER),
                    TYPE_INCOME.equals(tx.type) ? "收入" : "支出",
                    repository.formatMoney(tx.amount),
                    tx.categoryPath(),
                    tx.note,
                    imageRelativePath
            );
        }
        return builder.toString();
    }

    private String exportImagePaths(CountTransaction tx, List<ImageExportItem> images, Set<String> usedImageNames) {
        if (tx.imagePaths == null || tx.imagePaths.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String imagePath : tx.imagePaths) {
            if (imagePath == null || imagePath.trim().isEmpty()) {
                continue;
            }
            File imageFile = new File(imagePath);
            if (!imageFile.exists() || !imageFile.isFile()) {
                continue;
            }
            String fileName = uniqueExportFileName(imageFile.getName(), usedImageNames);
            String relativePath = "images/" + fileName;
            images.add(new ImageExportItem(imageFile, relativePath));
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append(relativePath);
        }
        return builder.toString();
    }

    private String uniqueExportFileName(String rawName, Set<String> usedNames) {
        String name = rawName == null || rawName.trim().isEmpty()
                ? "image.jpg"
                : rawName.trim();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : ".jpg";
        String candidate = base + extension;
        int index = 2;
        while (usedNames.contains(candidate)) {
            candidate = base + "_" + index + extension;
            index++;
        }
        usedNames.add(candidate);
        return candidate;
    }

    private String buildQualityCsv() throws IOException, JSONException {
        JSONObject data = readQualityRecordData();
        JSONArray records = data.optJSONArray("records");
        if (records == null) {
            records = new JSONArray();
        }
        Map<String, String> itemNames = readQualityItemNames();
        StringBuilder builder = new StringBuilder();
        builder.append("日期,是否打卡,已打卡条目,未打卡条目,随记\n");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) {
                continue;
            }
            JSONArray items = record.optJSONArray("items");
            StringBuilder checked = new StringBuilder();
            StringBuilder unchecked = new StringBuilder();
            boolean hasChecked = false;
            if (items != null) {
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.optJSONObject(j);
                    if (item == null) {
                        continue;
                    }
                    String id = item.optString("id", "");
                    String name = itemNames.containsKey(id) ? itemNames.get(id) : id;
                    boolean value = item.optInt("value", 0) == 1;
                    if (value) {
                        hasChecked = true;
                        appendJoinedName(checked, name);
                    } else {
                        appendJoinedName(unchecked, name);
                    }
                }
            }
            appendCsvRow(
                    builder,
                    record.optString("date", ""),
                    hasChecked ? "1" : "0",
                    checked.toString(),
                    unchecked.toString(),
                    record.optString("note", "")
            );
        }
        return builder.toString();
    }

    private JSONObject readQualityRecordData() throws IOException, JSONException {
        File file = new File(requireContext().getFilesDir(), QUALITY_FILE_NAME);
        if (!file.exists() || file.length() == 0) {
            JSONObject data = new JSONObject();
            data.put("version", 2);
            data.put("records", new JSONArray());
            return data;
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
                JSONObject empty = new JSONObject();
                empty.put("version", 2);
                empty.put("records", new JSONArray());
                return empty;
            }
            JSONObject recordData = new JSONObject(new String(data, 0, readLength, StandardCharsets.UTF_8));
            if (recordData.optJSONArray("records") == null) {
                recordData.put("records", new JSONArray());
            }
            return recordData;
        }
    }

    private Map<String, String> readQualityItemNames() throws IOException, JSONException {
        Map<String, String> names = new HashMap<>();
        File file = new File(requireContext().getFilesDir(), QUALITY_ITEMS_FILE_NAME);
        if (!file.exists() || file.length() == 0) {
            return names;
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
            JSONArray items = new JSONArray(new String(data, 0, readLength, StandardCharsets.UTF_8));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    names.put(item.optString("id", ""), item.optString("name", ""));
                }
            }
        }
        return names;
    }

    private void appendJoinedName(StringBuilder builder, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("、");
        }
        builder.append(name);
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

    private void renderPhotoAttachmentCard(LinearLayout host, EntryDraft draft, Runnable refreshImages) {
        host.removeAllViews();
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(8), dp(14), dp(12));
        card.setBackground(roundStroke(COLOR_SURFACE_SOFT, dp(16), COLOR_LINE, 1));
        TextView label = text("图片", 12, COLOR_MUTED, false);
        card.addView(label);

        HorizontalScrollView scrollView = new HorizontalScrollView(requireContext());
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = horizontal();
        strip.setPadding(0, dp(8), 0, 0);
        scrollView.addView(strip);

        for (String imagePath : draft.imagePaths) {
            LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(78), dp(78));
            thumbParams.setMargins(0, 0, dp(10), 0);
            strip.addView(imageThumbnailCell(imagePath, draft, refreshImages), thumbParams);
        }
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        strip.addView(addImageCell(draft, refreshImages), addParams);

        card.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(90)
        ));
        host.addView(card, matchWrap());
    }

    private View imageThumbnailCell(String imagePath, EntryDraft draft, Runnable refreshImages) {
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setBackground(round(0xFFFFFFFF, dp(12)));

        ImageView preview = new ImageView(requireContext());
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setImageURI(Uri.fromFile(new File(imagePath)));
        preview.setBackground(round(0xFFFFFFFF, dp(12)));
        frame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        frame.setOnClickListener(v -> showImageViewer(imagePath));

        TextView delete = text("×", 16, COLOR_TEXT, true);
        delete.setGravity(Gravity.CENTER);
        delete.setBackground(round(COLOR_BEE, dp(11)));
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(
                dp(22),
                dp(22),
                Gravity.TOP | Gravity.END
        );
        deleteParams.setMargins(0, dp(3), dp(3), 0);
        frame.addView(delete, deleteParams);
        delete.setOnClickListener(v -> {
            draft.imagePaths.remove(imagePath);
            if (draft.addedImagePaths.remove(imagePath)) {
                deleteImageFile(imagePath);
            } else if (draft.originalImagePaths.contains(imagePath)) {
                draft.removedImagePaths.add(imagePath);
            }
            refreshImages.run();
        });
        return frame;
    }

    private View addImageCell(EntryDraft draft, Runnable refreshImages) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundStroke(0xFFFFFFFF, dp(12), COLOR_LINE, 1));
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_add);
        icon.setColorFilter(COLOR_TEXT);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cell.addView(icon, fixed(dp(30), dp(30)));
        TextView label = text("添加", 12, COLOR_MUTED, false);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(5), 0, 0);
        cell.addView(label);
        cell.setOnClickListener(v -> showAddImageOptions(draft, refreshImages));
        return cell;
    }

    private void showAddImageOptions(EntryDraft draft, Runnable refreshImages) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = vertical();
        sheet.setPadding(dp(18), dp(16), dp(18), dp(22));
        sheet.setBackgroundColor(COLOR_SURFACE);

        TextView title = text("添加图片", 18, COLOR_TEXT, true);
        title.setPadding(dp(2), 0, 0, dp(12));
        sheet.addView(title);
        sheet.addView(actionSheetItem(R.drawable.ic_camera, "拍照", "拍摄一张新的附件图片", v -> {
            dialog.dismiss();
            startPhotoRecord(draft, refreshImages);
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_add, "从相册选择", "选择一张已有图片", v -> {
            dialog.dismiss();
            pickImage(draft, refreshImages);
        }));
        AppInsets.showScrollableBottomSheet(dialog, sheet);
    }

    private void showImageViewer(String imagePath) {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Toast.makeText(requireContext(), "图片文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).create();
        ScrollView scrollView = new ScrollView(requireContext());
        ImageView image = new ImageView(requireContext());
        image.setImageURI(Uri.fromFile(imageFile));
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(10);
        scrollView.setPadding(padding, padding, padding, padding);
        scrollView.addView(image, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialog.setView(scrollView);
        dialog.show();
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
        sheet.addView(actionSheetItem(R.drawable.ic_camera, "拍照记录", "拍照后补充金额和类别", v -> {
            dialog.dismiss();
            startPhotoRecord();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_stat, "图表统计", "查看趋势和排行榜", v -> {
            dialog.dismiss();
            openStatsFragment();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_category, "类别管理", "新增或整理收支类别", v -> {
            dialog.dismiss();
            openCategoryManageFragment();
        }));
        sheet.addView(actionSheetItem(R.drawable.ic_import, "导入 / 导出", "导入账单或导出备份数据", v -> {
            dialog.dismiss();
            showImportDialog();
        }));
        sheet.addView(actionSheetItem("✓", "Quality 打卡", "切换到每日打卡", v -> {
            dialog.dismiss();
            openQualityFragment();
        }));

        AppInsets.showScrollableBottomSheet(dialog, sheet);
    }

    private void startPhotoRecord() {
        pendingPhotoOpensNewRecord = true;
        startPhotoRecord(null, null);
    }

    private void startPhotoRecord(@Nullable EntryDraft draft, @Nullable Runnable refreshImages) {
        if (cameraLauncher == null) {
            return;
        }
        try {
            pendingImageDraft = draft;
            pendingImageRefresh = refreshImages;
            pendingPhotoFile = createPhotoFile();
            Uri photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    pendingPhotoFile
            );
            cameraLauncher.launch(photoUri);
        } catch (ActivityNotFoundException e) {
            clearPendingPhoto();
            Toast.makeText(requireContext(), "未找到可用相机", Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException e) {
            clearPendingPhoto();
            Toast.makeText(requireContext(), "无法创建照片文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImage(EntryDraft draft, Runnable refreshImages) {
        if (imagePickerLauncher == null) {
            return;
        }
        pendingImageDraft = draft;
        pendingImageRefresh = refreshImages;
        imagePickerLauncher.launch("image/*");
    }

    private File createPhotoFile() throws IOException {
        File picturesDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            picturesDir = new File(requireContext().getFilesDir(), "Pictures");
        }
        if (!picturesDir.exists() && !picturesDir.mkdirs()) {
            throw new IOException("Cannot create pictures directory");
        }
        return new File(picturesDir, "count_photo_" + System.currentTimeMillis() + ".jpg");
    }

    private void handlePhotoTaken(Boolean saved) {
        File photoFile = pendingPhotoFile;
        pendingPhotoFile = null;
        if (!Boolean.TRUE.equals(saved) || photoFile == null || !photoFile.exists()) {
            if (photoFile != null && photoFile.exists()) {
                photoFile.delete();
            }
            clearPendingImageTarget();
            return;
        }
        String imagePath = photoFile.getAbsolutePath();
        if (pendingPhotoOpensNewRecord || pendingImageDraft == null) {
            pendingPhotoOpensNewRecord = false;
            clearPendingImageTarget();
            showEntrySheet(null, imagePath);
            return;
        }
        pendingImageDraft.imagePaths.add(imagePath);
        pendingImageDraft.addedImagePaths.add(imagePath);
        if (pendingImageRefresh != null) {
            pendingImageRefresh.run();
        }
        clearPendingImageTarget();
    }

    private void handleImagePicked(Uri uri) {
        if (uri == null || pendingImageDraft == null) {
            clearPendingImageTarget();
            return;
        }
        try {
            File imageFile = copyPickedImage(uri);
            String imagePath = imageFile.getAbsolutePath();
            pendingImageDraft.imagePaths.add(imagePath);
            pendingImageDraft.addedImagePaths.add(imagePath);
            if (pendingImageRefresh != null) {
                pendingImageRefresh.run();
            }
        } catch (IOException e) {
            Toast.makeText(requireContext(), "无法读取图片", Toast.LENGTH_SHORT).show();
        } finally {
            clearPendingImageTarget();
        }
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
                renderInlineCategoryIconGrid(pendingBuiltinIconGrid, pendingCustomIconSelection);
            }
            renderInlineCustomIconGrid(
                    pendingCustomIconGrid,
                    pendingBuiltinIconGrid,
                    pendingCustomIconSelection,
                    pendingCustomIconType == null ? TYPE_EXPENSE : pendingCustomIconType
            );
            refreshMonthView();
            Toast.makeText(requireContext(), pendingReplacingCustomIconId == null ? "图标已导入" : "图标已替换", Toast.LENGTH_SHORT).show();
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
                renderInlineCategoryIconGrid(pendingBuiltinIconGrid, pendingCustomIconSelection);
            }
            renderInlineCustomIconGrid(
                    pendingCustomIconGrid,
                    pendingBuiltinIconGrid,
                    pendingCustomIconSelection,
                    pendingCustomIconType == null ? TYPE_EXPENSE : pendingCustomIconType
            );
            refreshMonthView();
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
        pendingCustomIconType = null;
    }

    private File copyPickedImage(Uri uri) throws IOException {
        File target = createPhotoFile();
        try (
                InputStream input = requireContext().getContentResolver().openInputStream(uri);
                OutputStream output = new FileOutputStream(target)
        ) {
            if (input == null) {
                throw new IOException("Cannot open selected image");
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return target;
    }

    private void clearPendingPhoto() {
        if (pendingPhotoFile != null && pendingPhotoFile.exists()) {
            pendingPhotoFile.delete();
        }
        pendingPhotoFile = null;
        clearPendingImageTarget();
    }

    private void clearPendingImageTarget() {
        pendingImageDraft = null;
        pendingImageRefresh = null;
        pendingPhotoOpensNewRecord = false;
    }

    private void deleteImageFile(@Nullable String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return;
        }
        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            imageFile.delete();
        }
    }

    private void deleteAddedImages(EntryDraft draft) {
        for (String imagePath : new ArrayList<>(draft.addedImagePaths)) {
            deleteImageFile(imagePath);
        }
        draft.addedImagePaths.clear();
    }

    private void deleteRemovedImages(EntryDraft draft) {
        for (String imagePath : new ArrayList<>(draft.removedImagePaths)) {
            deleteImageFile(imagePath);
        }
        draft.removedImagePaths.clear();
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
        CategoryIconMapper.loadInto(image, icon, repository, COLOR_TEXT);
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

