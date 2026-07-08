package com.example.quality.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.R;
import com.example.quality.util.LogUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FragmentQuality extends Fragment {
    private static final String TAG = "FragmentQuality";
    private static final String ITEMS_FILE_NAME = "quality_items.json";
    private static final String RECORD_FILE_NAME = "quality_record.json";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);
    private static final DateTimeFormatter STRIP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);
    private static final String[] CALENDAR_WEEK_NAMES = {"日", "一", "二", "三", "四", "五", "六"};
    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_BEE_SOFT = 0xFFFFF4BF;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_MUTED = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFEDE3CF;
    private static final int COLOR_DISABLED = 0xFFB8B0A6;
    private static final int COLOR_SOFT = 0xFFF7F2EA;
    private static final int COLOR_GREEN = 0xFF16A34A;

    private TextView buttonBackToCount;
    private HorizontalScrollView dateStripScroll;
    private LinearLayout dateStrip;
    private TextView buttonCalendar;
    private Button buttonStatistic;
    private Button buttonSaveRecord;
    private TextView buttonAddHabit;
    private TextView buttonManageHabits;
    private TextView textHabitEmpty;
    private LinearLayout layoutHabitItems;
    private EditText editNote;
    private LocalDate selectedDate;
    private List<QualityItem> allItems = new ArrayList<>();
    private final Map<String, CheckBox> itemCheckBoxes = new HashMap<>();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        LogUtil.d(TAG, "onAttach");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.d(TAG, "onCreate");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogUtil.d(TAG, "onCreateView");
        return inflater.inflate(R.layout.fragment_quality, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LogUtil.d(TAG, "onViewCreated");
        bindViews(view);
        selectedDate = savedInstanceState == null
                ? LocalDate.now()
                : LocalDate.parse(savedInstanceState.getString("selectedDate", LocalDate.now().format(DATE_FORMATTER)));
        allItems = readItems();
        setupListeners();
        loadDate(selectedDate);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedDate != null) {
            outState.putString("selectedDate", selectedDate.format(DATE_FORMATTER));
        }
    }

    private void bindViews(@NonNull View view) {
        buttonBackToCount = view.findViewById(R.id.buttonBackToCount);
        dateStripScroll = view.findViewById(R.id.dateStripScroll);
        dateStrip = view.findViewById(R.id.dateStrip);
        buttonCalendar = view.findViewById(R.id.buttonCalendar);
        buttonStatistic = view.findViewById(R.id.buttonStatistic);
        buttonSaveRecord = view.findViewById(R.id.buttonSaveRecord);
        buttonAddHabit = view.findViewById(R.id.buttonAddHabit);
        buttonManageHabits = view.findViewById(R.id.buttonManageHabits);
        textHabitEmpty = view.findViewById(R.id.textHabitEmpty);
        layoutHabitItems = view.findViewById(R.id.layoutHabitItems);
        editNote = view.findViewById(R.id.editNote);
    }

    private void setupListeners() {
        buttonBackToCount.setOnClickListener(v -> navigateBackToCount());
        buttonCalendar.setOnClickListener(v -> showDatePickerSheet());
        buttonStatistic.setOnClickListener(v -> showCheckInCalendarSheet());
        buttonSaveRecord.setOnClickListener(v -> saveCurrentRecord());
        buttonAddHabit.setOnClickListener(v -> showItemEditor(null));
        buttonManageHabits.setOnClickListener(v -> showItemManagerSheet());
    }

    private void navigateBackToCount() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
            return;
        }
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.container, new FragmentCount())
                .commit();
    }

    private void loadDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            Toast.makeText(requireContext(), "不能选择未来日期", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedDate = date;
        renderDateStrip();
        renderHabitList(new HashMap<>());
        editNote.setText("");

        JSONObject record = findRecord(readRecordData().optJSONArray("records"), date.format(DATE_FORMATTER));
        if (record == null) {
            return;
        }
        renderHabitList(readRecordValues(record));
        editNote.setText(record.optString("note", ""));
    }

    private void renderHabitList(Map<String, Integer> values) {
        layoutHabitItems.removeAllViews();
        itemCheckBoxes.clear();

        List<QualityItem> activeItems = activeItems();
        textHabitEmpty.setVisibility(activeItems.isEmpty() ? View.VISIBLE : View.GONE);
        for (QualityItem item : activeItems) {
            layoutHabitItems.addView(createHabitRow(item, values.containsKey(item.id) && values.get(item.id) == 1));
        }
    }

    private View createHabitRow(QualityItem item, boolean checked) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(8), 0);
        row.setBackground(roundStroke(COLOR_SOFT, dp(12), COLOR_LINE, 1));

        TextView name = new TextView(requireContext());
        name.setText(item.name);
        name.setTextColor(COLOR_TEXT);
        name.setTextSize(16);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(false);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(50), 1));

        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setButtonDrawable(null);
        checkBox.setText("✓");
        checkBox.setTextSize(22);
        checkBox.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        checkBox.setGravity(Gravity.CENTER);
        checkBox.setIncludeFontPadding(false);
        checkBox.setBackgroundResource(R.drawable.bg_check_mark);
        checkBox.setTextColor(getResources().getColor(R.color.quality_check_text, requireContext().getTheme()));
        checkBox.setChecked(checked);
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(32), dp(32)));
        itemCheckBoxes.put(item.id, checkBox);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        row.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
        return row;
    }

    private void saveCurrentRecord() {
        if (selectedDate.isAfter(LocalDate.now())) {
            Toast.makeText(requireContext(), "未来日期不能保存", Toast.LENGTH_SHORT).show();
            return;
        }

        List<QualityItem> activeItems = activeItems();
        if (activeItems.isEmpty()) {
            Toast.makeText(requireContext(), "先新增一个打卡条目", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject data = readRecordData();
        JSONArray records = data.optJSONArray("records");
        if (records == null) {
            records = new JSONArray();
        }
        String dateString = selectedDate.format(DATE_FORMATTER);
        JSONObject record = findRecord(records, dateString);
        if (record == null) {
            record = new JSONObject();
            records.put(record);
        }

        try {
            record.put("date", dateString);
            JSONArray itemValues = new JSONArray();
            for (QualityItem item : activeItems) {
                JSONObject value = new JSONObject();
                value.put("id", item.id);
                CheckBox checkBox = itemCheckBoxes.get(item.id);
                value.put("value", checkBox != null && checkBox.isChecked() ? 1 : 0);
                itemValues.put(value);
            }
            record.put("items", itemValues);
            record.put("note", editNote.getText().toString().trim());
            data.put("version", 2);
            data.put("records", records);
            if (writeRecordData(data)) {
                Toast.makeText(requireContext(), "当日记录已保存", Toast.LENGTH_SHORT).show();
                renderDateStrip();
            } else {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            LogUtil.e(TAG, "save json error: " + e.getMessage());
            Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showItemEditor(@Nullable QualityItem editingItem) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint("例如：运动、阅读、早睡");
        input.setText(editingItem == null ? "" : editingItem.name);
        input.setSelectAllOnFocus(true);
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));

        new AlertDialog.Builder(requireContext())
                .setTitle(editingItem == null ? "新增打卡条目" : "编辑打卡条目")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "条目名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (editingItem == null) {
                        addItem(name);
                    } else {
                        renameItem(editingItem.id, name);
                    }
                    loadDate(selectedDate);
                })
                .show();
    }

    private void addItem(String name) {
        QualityItem item = new QualityItem(
                "habit_" + System.currentTimeMillis(),
                name,
                true,
                nextSort(),
                LocalDate.now().format(DATE_FORMATTER)
        );
        allItems.add(item);
        writeItems(allItems);
    }

    private void renameItem(String id, String name) {
        for (QualityItem item : allItems) {
            if (item.id.equals(id)) {
                item.name = name;
                break;
            }
        }
        writeItems(allItems);
    }

    private void setItemEnabled(String id, boolean enabled) {
        for (QualityItem item : allItems) {
            if (item.id.equals(id)) {
                item.enabled = enabled;
                break;
            }
        }
        writeItems(allItems);
        loadDate(selectedDate);
    }

    private void showItemManagerSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(18));
        sheet.setBackground(roundStroke(COLOR_SURFACE, dp(20), COLOR_SURFACE, 0));

        TextView title = text("管理打卡条目", 18, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(30)
        ));

        TextView add = createActionButton("新增条目", true);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        addParams.setMargins(0, dp(14), 0, dp(12));
        sheet.addView(add, addParams);
        add.setOnClickListener(v -> {
            dialog.dismiss();
            showItemEditor(null);
        });

        if (allItems.isEmpty()) {
            TextView empty = text("还没有条目，先新增一个开始记录。", 14, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            sheet.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(64)
            ));
        } else {
            for (QualityItem item : sortedItems()) {
                sheet.addView(createManagerRow(item, dialog));
            }
        }

        TextView close = createActionButton("完成", false);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        closeParams.setMargins(0, dp(10), 0, 0);
        sheet.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private View createManagerRow(QualityItem item, BottomSheetDialog dialog) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(8), 0);
        row.setBackground(roundStroke(COLOR_SOFT, dp(12), COLOR_LINE, 1));

        LinearLayout nameBox = new LinearLayout(requireContext());
        nameBox.setOrientation(LinearLayout.VERTICAL);
        nameBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(item.name, 16, COLOR_TEXT, true);
        TextView status = text(item.enabled ? "启用中" : "已停用", 12, item.enabled ? COLOR_GREEN : COLOR_MUTED, false);
        nameBox.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
        ));
        nameBox.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20)
        ));
        row.addView(nameBox, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView edit = smallButton("编辑");
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            showItemEditor(item);
        });
        row.addView(edit, new LinearLayout.LayoutParams(dp(58), dp(34)));

        TextView toggle = smallButton(item.enabled ? "停用" : "启用");
        toggle.setOnClickListener(v -> {
            dialog.dismiss();
            setItemEnabled(item.id, !item.enabled);
            showItemManagerSheet();
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(58), dp(34));
        toggleParams.setMargins(dp(8), 0, 0, 0);
        row.addView(toggle, toggleParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private void showDatePickerSheet() {
        BottomSheetDialog dialog = createCalendarSheet("选择日期", selectedDate.withDayOfMonth(1), false);
        dialog.show();
    }

    private void showCheckInCalendarSheet() {
        BottomSheetDialog dialog = createCalendarSheet("打卡日历", selectedDate.withDayOfMonth(1), true);
        dialog.show();
    }

    private BottomSheetDialog createCalendarSheet(String titleText, LocalDate initialMonth, boolean showStats) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(14));
        sheet.setBackground(roundStroke(COLOR_SURFACE, dp(20), COLOR_SURFACE, 0));

        TextView title = text(titleText, 18, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
        ));

        LinearLayout statRow = new LinearLayout(requireContext());
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.setVisibility(showStats ? View.VISIBLE : View.GONE);
        TextView monthStat = statCell("本月", "0天");
        TextView currentStreak = statCell("当前连续", "0天");
        TextView bestStreak = statCell("最长连续", "0天");
        statRow.addView(monthStat, new LinearLayout.LayoutParams(0, dp(58), 1));
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        middleParams.setMargins(dp(8), 0, dp(8), 0);
        statRow.addView(currentStreak, middleParams);
        statRow.addView(bestStreak, new LinearLayout.LayoutParams(0, dp(58), 1));
        LinearLayout.LayoutParams statParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        statParams.setMargins(0, dp(14), 0, 0);
        sheet.addView(statRow, statParams);

        LinearLayout monthBar = new LinearLayout(requireContext());
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setGravity(Gravity.CENTER);
        monthBar.setPadding(0, dp(16), 0, dp(12));

        TextView previousMonth = createMonthNavButton("‹");
        TextView monthTitle = text("", 17, COLOR_TEXT, true);
        monthTitle.setGravity(Gravity.CENTER);
        TextView nextMonth = createMonthNavButton("›");

        monthBar.addView(previousMonth, new LinearLayout.LayoutParams(dp(44), dp(38)));
        monthBar.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(38), 1));
        monthBar.addView(nextMonth, new LinearLayout.LayoutParams(dp(44), dp(38)));
        sheet.addView(monthBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout weekRow = new LinearLayout(requireContext());
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String weekName : CALENDAR_WEEK_NAMES) {
            TextView label = text(weekName, 13, COLOR_MUTED, true);
            label.setGravity(Gravity.CENTER);
            weekRow.addView(label, new LinearLayout.LayoutParams(0, dp(28), 1));
        }
        sheet.addView(weekRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
        ));

        LinearLayout monthContainer = new LinearLayout(requireContext());
        monthContainer.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(monthContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout footer = new LinearLayout(requireContext());
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(10), 0, 0);
        TextView todayButton = createCalendarAction("今天", true);
        TextView cancelButton = createCalendarAction("取消", false);
        footer.addView(todayButton, new LinearLayout.LayoutParams(dp(86), dp(40)));
        View spacer = new View(requireContext());
        footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        footer.addView(cancelButton, new LinearLayout.LayoutParams(dp(86), dp(40)));
        sheet.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        JSONObject recordData = readRecordData();
        Set<String> checkedDays = checkedDayStrings(recordData);
        final LocalDate[] shownMonth = {initialMonth};
        Runnable render = () -> {
            renderCalendarMonth(monthContainer, monthTitle, shownMonth[0], dialog, showStats, checkedDays);
            if (showStats) {
                updateStatCells(monthStat, currentStreak, bestStreak, shownMonth[0], checkedDays);
            }
        };

        previousMonth.setOnClickListener(v -> {
            shownMonth[0] = shownMonth[0].minusMonths(1);
            render.run();
        });
        nextMonth.setOnClickListener(v -> {
            shownMonth[0] = shownMonth[0].plusMonths(1);
            render.run();
        });
        todayButton.setOnClickListener(v -> {
            loadDate(LocalDate.now());
            dialog.dismiss();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        render.run();
        dialog.setContentView(sheet);
        return dialog;
    }

    private void renderCalendarMonth(
            LinearLayout monthContainer,
            TextView monthTitle,
            LocalDate month,
            BottomSheetDialog dialog,
            boolean detailMode,
            Set<String> checkedDays
    ) {
        monthContainer.removeAllViews();
        monthTitle.setText(month.format(DateTimeFormatter.ofPattern("yyyy年MM月", Locale.CHINA)));

        LocalDate today = LocalDate.now();
        LocalDate firstDay = month.withDayOfMonth(1);
        int firstColumn = firstDay.getDayOfWeek().getValue() % 7;
        int daysInMonth = month.lengthOfMonth();

        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int columnIndex = 0; columnIndex < 7; columnIndex++) {
                int cellIndex = rowIndex * 7 + columnIndex;
                int dayNumber = cellIndex - firstColumn + 1;
                View cell;
                if (dayNumber < 1 || dayNumber > daysInMonth) {
                    cell = new View(requireContext());
                } else {
                    LocalDate date = month.withDayOfMonth(dayNumber);
                    boolean selected = date.equals(selectedDate);
                    boolean isToday = date.equals(today);
                    boolean future = date.isAfter(today);
                    boolean checked = checkedDays.contains(date.format(DATE_FORMATTER));
                    cell = createCalendarDayCell(date, selected, isToday, future, checked, detailMode, dialog);
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
                params.setMargins(dp(2), dp(3), dp(2), dp(3));
                row.addView(cell, params);
            }
            monthContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            ));
        }
    }

    private TextView createCalendarDayCell(
            LocalDate date,
            boolean selected,
            boolean today,
            boolean future,
            boolean checked,
            boolean detailMode,
            BottomSheetDialog dialog
    ) {
        TextView cell = new TextView(requireContext());
        cell.setText(checked ? String.format(Locale.CHINA, "%02d\n●", date.getDayOfMonth())
                : String.format(Locale.CHINA, "%02d", date.getDayOfMonth()));
        cell.setGravity(Gravity.CENTER);
        cell.setTextSize(checked ? 13 : 15);
        cell.setIncludeFontPadding(false);
        cell.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);

        int textColor = checked ? COLOR_GREEN : COLOR_TEXT;
        int fillColor = 0x00FFFFFF;
        int strokeColor = 0x00FFFFFF;
        int strokeWidth = 0;
        if (selected) {
            fillColor = COLOR_BEE;
            strokeColor = COLOR_BEE;
            strokeWidth = 1;
            textColor = COLOR_TEXT;
        } else if (today) {
            fillColor = COLOR_SURFACE;
            strokeColor = COLOR_BEE;
            strokeWidth = 2;
        } else if (checked) {
            fillColor = 0xFFEAF7ED;
            strokeColor = 0xFFCDEBD4;
            strokeWidth = 1;
        } else if (future) {
            textColor = COLOR_DISABLED;
            fillColor = COLOR_SOFT;
            strokeColor = 0xFFECE1D2;
            strokeWidth = 1;
        }
        cell.setTextColor(textColor);
        cell.setBackground(roundStroke(fillColor, dp(14), strokeColor, strokeWidth));
        cell.setOnClickListener(v -> {
            if (future) {
                Toast.makeText(requireContext(), "不能选择未来日期", Toast.LENGTH_SHORT).show();
                return;
            }
            if (detailMode) {
                showDayDetail(date);
                return;
            }
            loadDate(date);
            dialog.dismiss();
        });
        return cell;
    }

    private void showDayDetail(LocalDate date) {
        JSONObject record = findRecord(readRecordData().optJSONArray("records"), date.format(DATE_FORMATTER));
        StringBuilder message = new StringBuilder();
        message.append(date.format(DATE_FORMATTER))
                .append(" ")
                .append(WEEK_NAMES[date.getDayOfWeek().getValue() - 1])
                .append("\n\n");
        if (record == null) {
            message.append("这一天没有保存打卡记录。");
        } else {
            JSONArray items = record.optJSONArray("items");
            if (items == null || items.length() == 0) {
                message.append("这一天没有参与的打卡条目。");
            } else {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject value = items.optJSONObject(i);
                    if (value == null) {
                        continue;
                    }
                    String id = value.optString("id", "");
                    message.append(value.optInt("value", 0) == 1 ? "✓ " : "□ ")
                            .append(itemName(id))
                            .append("\n");
                }
            }
            String note = record.optString("note", "").trim();
            if (!note.isEmpty()) {
                message.append("\n随记：\n").append(note);
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("打卡详情")
                .setMessage(message.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    private void updateStatCells(
            TextView monthStat,
            TextView currentStreak,
            TextView bestStreak,
            LocalDate month,
            Set<String> checkedDays
    ) {
        YearMonth yearMonth = YearMonth.from(month);
        int monthDays = 0;
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            if (!date.isAfter(LocalDate.now()) && checkedDays.contains(date.format(DATE_FORMATTER))) {
                monthDays++;
            }
        }
        monthStat.setText("本月\n" + monthDays + "天");
        currentStreak.setText("当前连续\n" + currentStreak(checkedDays) + "天");
        bestStreak.setText("最长连续\n" + bestStreak(checkedDays) + "天");
    }

    private int currentStreak(Set<String> checkedDays) {
        LocalDate day = LocalDate.now();
        if (!checkedDays.contains(day.format(DATE_FORMATTER))) {
            day = latestCheckedDay(checkedDays);
            if (day == null) {
                return 0;
            }
        }
        int count = 0;
        while (checkedDays.contains(day.format(DATE_FORMATTER))) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    @Nullable
    private LocalDate latestCheckedDay(Set<String> checkedDays) {
        LocalDate latest = null;
        for (String dateString : checkedDays) {
            try {
                LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
                if (latest == null || date.isAfter(latest)) {
                    latest = date;
                }
            } catch (Exception ignored) {
            }
        }
        return latest;
    }

    private int bestStreak(Set<String> checkedDays) {
        List<LocalDate> dates = new ArrayList<>();
        for (String dateString : checkedDays) {
            try {
                dates.add(LocalDate.parse(dateString, DATE_FORMATTER));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(dates);
        int best = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            if (previous == null || date.equals(previous.plusDays(1))) {
                current++;
            } else if (!date.equals(previous)) {
                current = 1;
            }
            best = Math.max(best, current);
            previous = date;
        }
        return best;
    }

    private Set<String> checkedDayStrings(JSONObject data) {
        Set<String> days = new HashSet<>();
        JSONArray records = data.optJSONArray("records");
        if (records == null) {
            return days;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && hasCheckedItem(record)) {
                days.add(record.optString("date", ""));
            }
        }
        return days;
    }

    private boolean hasCheckedItem(JSONObject record) {
        JSONArray items = record.optJSONArray("items");
        if (items == null) {
            return false;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && item.optInt("value", 0) == 1) {
                return true;
            }
        }
        return false;
    }

    private void renderDateStrip() {
        if (dateStrip == null || selectedDate == null) {
            return;
        }
        Set<String> checkedDays = checkedDayStrings(readRecordData());
        dateStrip.removeAllViews();
        LocalDate today = LocalDate.now();
        LocalDate startDate = selectedDate.minusDays(7);
        int selectedIndex = 7;
        for (int i = 0; i < 15; i++) {
            LocalDate date = startDate.plusDays(i);
            TextView chip = createDateChip(
                    date,
                    date.equals(selectedDate),
                    date.isAfter(today),
                    checkedDays.contains(date.format(DATE_FORMATTER))
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(74), dp(46));
            params.setMargins(0, 0, dp(8), 0);
            dateStrip.addView(chip, params);
        }
        dateStripScroll.post(() -> {
            int scrollX = Math.max(0, selectedIndex * dp(82) - dateStripScroll.getWidth() / 2 + dp(37));
            dateStripScroll.smoothScrollTo(scrollX, 0);
        });
    }

    private TextView createDateChip(LocalDate date, boolean selected, boolean future, boolean checked) {
        TextView chip = new TextView(requireContext());
        chip.setText(date.format(STRIP_DATE_FORMATTER) + "\n" + WEEK_NAMES[date.getDayOfWeek().getValue() - 1]);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(13);
        chip.setIncludeFontPadding(false);
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setTextColor(future ? COLOR_DISABLED : (checked ? COLOR_GREEN : COLOR_TEXT));
        int fillColor = selected ? COLOR_BEE : (checked ? 0xFFEAF7ED : COLOR_SURFACE);
        int strokeColor = selected ? COLOR_BEE : (checked ? 0xFFCDEBD4 : COLOR_LINE);
        if (future) {
            fillColor = COLOR_SOFT;
            strokeColor = 0xFFECE1D2;
        }
        chip.setBackground(roundStroke(fillColor, dp(14), strokeColor, selected ? 2 : 1));
        chip.setOnClickListener(v -> {
            if (future) {
                Toast.makeText(requireContext(), "不能选择未来日期", Toast.LENGTH_SHORT).show();
                return;
            }
            loadDate(date);
        });
        return chip;
    }

    private JSONObject readRecordData() {
        File file = new File(requireContext().getFilesDir(), RECORD_FILE_NAME);
        if (!file.exists() || file.length() == 0) {
            return emptyRecordData();
        }
        try {
            String content = readFile(file);
            JSONObject data = new JSONObject(content);
            if (data.optJSONArray("records") == null) {
                data.put("records", new JSONArray());
            }
            data.put("version", data.optInt("version", 2));
            return data;
        } catch (IOException | JSONException e) {
            LogUtil.e(TAG, "read record json error: " + e.getMessage());
            return emptyRecordData();
        }
    }

    private JSONObject emptyRecordData() {
        JSONObject data = new JSONObject();
        try {
            data.put("version", 2);
            data.put("records", new JSONArray());
        } catch (JSONException ignored) {
        }
        return data;
    }

    private boolean writeRecordData(JSONObject data) {
        File file = new File(requireContext().getFilesDir(), RECORD_FILE_NAME);
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | JSONException e) {
            LogUtil.e(TAG, "write record json error: " + e.getMessage());
            return false;
        }
    }

    private List<QualityItem> readItems() {
        File file = new File(requireContext().getFilesDir(), ITEMS_FILE_NAME);
        if (!file.exists() || file.length() == 0) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = new JSONArray(readFile(file));
            List<QualityItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "");
                String name = item.optString("name", "");
                if (id.isEmpty() || name.isEmpty()) {
                    continue;
                }
                items.add(new QualityItem(
                        id,
                        name,
                        item.optBoolean("enabled", true),
                        item.optInt("sort", i + 1),
                        item.optString("createdAt", "")
                ));
            }
            Collections.sort(items);
            return items;
        } catch (IOException | JSONException e) {
            LogUtil.e(TAG, "read item json error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean writeItems(List<QualityItem> items) {
        File file = new File(requireContext().getFilesDir(), ITEMS_FILE_NAME);
        JSONArray array = new JSONArray();
        try {
            for (QualityItem item : sortedItems(items)) {
                JSONObject json = new JSONObject();
                json.put("id", item.id);
                json.put("name", item.name);
                json.put("enabled", item.enabled);
                json.put("sort", item.sort);
                json.put("createdAt", item.createdAt);
                array.put(json);
            }
            try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
                outputStream.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            allItems = readItems();
            return true;
        } catch (IOException | JSONException e) {
            LogUtil.e(TAG, "write item json error: " + e.getMessage());
            return false;
        }
    }

    private String readFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int readLength = 0;
            while (readLength < data.length) {
                int count = inputStream.read(data, readLength, data.length - readLength);
                if (count < 0) {
                    break;
                }
                readLength += count;
            }
            return new String(data, 0, readLength, StandardCharsets.UTF_8);
        }
    }

    @Nullable
    private JSONObject findRecord(@Nullable JSONArray records, String dateString) {
        if (records == null) {
            return null;
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && dateString.equals(record.optString("date"))) {
                return record;
            }
        }
        return null;
    }

    private Map<String, Integer> readRecordValues(JSONObject record) {
        Map<String, Integer> values = new HashMap<>();
        JSONArray items = record.optJSONArray("items");
        if (items == null) {
            return values;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                values.put(item.optString("id", ""), item.optInt("value", 0));
            }
        }
        return values;
    }

    private List<QualityItem> activeItems() {
        List<QualityItem> items = new ArrayList<>();
        for (QualityItem item : allItems) {
            if (item.enabled) {
                items.add(item);
            }
        }
        Collections.sort(items);
        return items;
    }

    private List<QualityItem> sortedItems() {
        return sortedItems(allItems);
    }

    private List<QualityItem> sortedItems(List<QualityItem> source) {
        List<QualityItem> items = new ArrayList<>(source);
        Collections.sort(items);
        return items;
    }

    private int nextSort() {
        int max = 0;
        for (QualityItem item : allItems) {
            max = Math.max(max, item.sort);
        }
        return max + 1;
    }

    private String itemName(String id) {
        for (QualityItem item : allItems) {
            if (item.id.equals(id)) {
                return item.name;
            }
        }
        return id.isEmpty() ? "未知条目" : id;
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView textView = new TextView(requireContext());
        textView.setText(content);
        textView.setTextSize(size);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        textView.setIncludeFontPadding(false);
        return textView;
    }

    private TextView statCell(String label, String value) {
        TextView cell = text(label + "\n" + value, 13, COLOR_TEXT, true);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundStroke(COLOR_SOFT, dp(12), COLOR_LINE, 1));
        return cell;
    }

    private TextView smallButton(String content) {
        TextView button = text(content, 13, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundStroke(COLOR_SURFACE, dp(17), COLOR_LINE, 1));
        return button;
    }

    private TextView createMonthNavButton(String content) {
        TextView button = text(content, 28, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundStroke(COLOR_SOFT, dp(19), COLOR_LINE, 1));
        return button;
    }

    private TextView createCalendarAction(String content, boolean primary) {
        TextView button = text(content, 15, primary ? COLOR_TEXT : COLOR_MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundStroke(primary ? COLOR_BEE : COLOR_SURFACE, dp(20),
                primary ? COLOR_BEE : COLOR_LINE, 1));
        return button;
    }

    private TextView createActionButton(String content, boolean primary) {
        TextView button = text(content, 15, primary ? COLOR_TEXT : COLOR_MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundStroke(primary ? COLOR_BEE : COLOR_SURFACE, dp(20),
                primary ? COLOR_BEE : COLOR_LINE, 1));
        return button;
    }

    private GradientDrawable roundStroke(int color, int radius, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class QualityItem implements Comparable<QualityItem> {
        final String id;
        String name;
        boolean enabled;
        final int sort;
        final String createdAt;

        QualityItem(String id, String name, boolean enabled, int sort, String createdAt) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.sort = sort;
            this.createdAt = createdAt;
        }

        @Override
        public int compareTo(QualityItem other) {
            if (sort != other.sort) {
                return sort - other.sort;
            }
            return name.compareTo(other.name);
        }
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
