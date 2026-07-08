package com.example.quality.fragment;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class FragmentQuality extends Fragment {
    private static final String TAG = "FragmentQuality";
    private static final String FILE_NAME = "quality_record.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);
    private static final DateTimeFormatter STRIP_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_MUTED = 0xFF6B7280;
    private static final int COLOR_LINE = 0xFFEDE3CF;
    private static final int COLOR_DISABLED = 0xFFB8B0A6;
    private static final int COLOR_SOFT = 0xFFF7F2EA;
    private static final String[] CALENDAR_WEEK_NAMES = {"日", "一", "二", "三", "四", "五", "六"};
    private static final String[] HABIT_NAMES = {"运动", "阅读", "思维", "口语", "睡眠"};
    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private TextView buttonBackToCount;
    private HorizontalScrollView dateStripScroll;
    private LinearLayout dateStrip;
    private TextView buttonCalendar;
    private Button buttonStatistic;
    private Button buttonSaveRecord;
    private EditText editNote;
    private CheckBox checkExercise;
    private CheckBox checkReading;
    private CheckBox checkThinking;
    private CheckBox checkSpeaking;
    private CheckBox checkSleep;
    private LocalDate selectedDate;

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
        editNote = view.findViewById(R.id.editNote);
        checkExercise = view.findViewById(R.id.checkExercise);
        checkReading = view.findViewById(R.id.checkReading);
        checkThinking = view.findViewById(R.id.checkThinking);
        checkSpeaking = view.findViewById(R.id.checkSpeaking);
        checkSleep = view.findViewById(R.id.checkSleep);
    }

    private void setupListeners() {
        buttonBackToCount.setOnClickListener(v -> navigateBackToCount());
        buttonCalendar.setOnClickListener(v -> showCalendarSheet());
        buttonSaveRecord.setOnClickListener(v -> saveCurrentRecord());
        buttonStatistic.setOnClickListener(v -> showStatisticStartPicker());
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

    private void showSingleDatePicker() {
        DatePickerDialog dialog = createDatePickerDialog("选择打卡日期", selectedDate, date -> loadDate(date));
        dialog.show();
    }

    private void showCalendarSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(14));
        sheet.setBackground(roundStroke(COLOR_SURFACE, dp(20), COLOR_SURFACE, 0));

        TextView title = new TextView(requireContext());
        title.setText("选择日期");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        sheet.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
        ));

        LinearLayout monthBar = new LinearLayout(requireContext());
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setGravity(Gravity.CENTER);
        monthBar.setPadding(0, dp(16), 0, dp(12));

        TextView previousMonth = createMonthNavButton("‹");
        TextView monthTitle = new TextView(requireContext());
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setTextColor(COLOR_TEXT);
        monthTitle.setTextSize(17);
        monthTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monthTitle.setIncludeFontPadding(false);
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
            TextView label = new TextView(requireContext());
            label.setText(weekName);
            label.setTextColor(COLOR_MUTED);
            label.setTextSize(13);
            label.setGravity(Gravity.CENTER);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setIncludeFontPadding(false);
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

        final LocalDate[] shownMonth = {selectedDate.withDayOfMonth(1)};
        previousMonth.setOnClickListener(v -> {
            shownMonth[0] = shownMonth[0].minusMonths(1);
            renderCalendarMonth(monthContainer, monthTitle, shownMonth[0], dialog);
        });
        nextMonth.setOnClickListener(v -> {
            shownMonth[0] = shownMonth[0].plusMonths(1);
            renderCalendarMonth(monthContainer, monthTitle, shownMonth[0], dialog);
        });
        todayButton.setOnClickListener(v -> {
            loadDate(LocalDate.now());
            dialog.dismiss();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        renderCalendarMonth(monthContainer, monthTitle, shownMonth[0], dialog);
        dialog.setContentView(sheet);
        dialog.show();
    }

    private TextView createMonthNavButton(String text) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(28);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setIncludeFontPadding(false);
        button.setBackground(roundStroke(COLOR_SOFT, dp(19), COLOR_LINE, 1));
        return button;
    }

    private TextView createCalendarAction(String text, boolean primary) {
        TextView button = new TextView(requireContext());
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? COLOR_TEXT : COLOR_MUTED);
        button.setIncludeFontPadding(false);
        button.setBackground(roundStroke(primary ? COLOR_BEE : COLOR_SURFACE, dp(20), primary ? COLOR_BEE : COLOR_LINE, 1));
        return button;
    }

    private void renderCalendarMonth(
            LinearLayout monthContainer,
            TextView monthTitle,
            LocalDate month,
            BottomSheetDialog dialog
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
                    cell = createCalendarDayCell(date, selected, isToday, future, dialog);
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
                params.setMargins(dp(2), dp(3), dp(2), dp(3));
                row.addView(cell, params);
            }
            monthContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
            ));
        }
    }

    private TextView createCalendarDayCell(
            LocalDate date,
            boolean selected,
            boolean today,
            boolean future,
            BottomSheetDialog dialog
    ) {
        TextView cell = new TextView(requireContext());
        cell.setText(String.format(Locale.CHINA, "%02d", date.getDayOfMonth()));
        cell.setGravity(Gravity.CENTER);
        cell.setTextSize(15);
        cell.setIncludeFontPadding(false);
        cell.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);

        int textColor = COLOR_TEXT;
        int fillColor = 0x00FFFFFF;
        int strokeColor = 0x00FFFFFF;
        int strokeWidth = 0;
        if (selected) {
            fillColor = COLOR_BEE;
            strokeColor = COLOR_BEE;
            strokeWidth = 1;
        } else if (today) {
            fillColor = COLOR_SURFACE;
            strokeColor = COLOR_BEE;
            strokeWidth = 2;
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
            loadDate(date);
            dialog.dismiss();
        });
        return cell;
    }

    private void showStatisticStartPicker() {
        DatePickerDialog dialog = createDatePickerDialog("选择开始日期", selectedDate, startDate -> showStatisticEndPicker(startDate));
        dialog.show();
    }

    private void showStatisticEndPicker(LocalDate startDate) {
        DatePickerDialog dialog = createDatePickerDialog("选择结束日期", startDate, endDate -> showStatisticResult(startDate, endDate));
        dialog.show();
    }

    private DatePickerDialog createDatePickerDialog(String title, LocalDate initialDate, DateSelectedCallback callback) {
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                R.style.Theme_Quality_DatePicker,
                (view, year, month, dayOfMonth) -> callback.onSelected(LocalDate.of(year, month + 1, dayOfMonth)),
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        );
        dialog.setTitle(title);
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        return dialog;
    }

    private void loadDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            Toast.makeText(requireContext(), "不能选择未来日期", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedDate = date;
        renderDateStrip();
        clearInputs();

        JSONObject record = findRecord(readRecords(), date.format(DATE_FORMATTER));
        if (record == null) {
            return;
        }

        JSONObject habits = record.optJSONObject("habits");
        if (habits != null) {
            checkExercise.setChecked(readHabitValue(habits, "运动") == 1);
            checkReading.setChecked(readHabitValue(habits, "阅读") == 1);
            checkThinking.setChecked(readHabitValue(habits, "思维") == 1);
            checkSpeaking.setChecked(readHabitValue(habits, "口语") == 1);
            checkSleep.setChecked(readHabitValue(habits, "睡眠") == 1);
        }
        editNote.setText(record.optString("note", ""));
    }

    private String formatDisplayDate(LocalDate date) {
        return date.format(DATE_FORMATTER) + " " + WEEK_NAMES[date.getDayOfWeek().getValue() - 1];
    }

    private void renderDateStrip() {
        if (dateStrip == null || selectedDate == null) {
            return;
        }
        dateStrip.removeAllViews();
        LocalDate today = LocalDate.now();
        LocalDate startDate = selectedDate.minusDays(7);
        int selectedIndex = 7;
        for (int i = 0; i < 15; i++) {
            LocalDate date = startDate.plusDays(i);
            TextView chip = createDateChip(date, date.equals(selectedDate), date.isAfter(today));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(74), dp(44));
            params.setMargins(0, 0, dp(8), 0);
            dateStrip.addView(chip, params);
        }
        dateStripScroll.post(() -> {
            int scrollX = Math.max(0, selectedIndex * dp(82) - dateStripScroll.getWidth() / 2 + dp(37));
            dateStripScroll.smoothScrollTo(scrollX, 0);
        });
    }

    private TextView createDateChip(LocalDate date, boolean selected, boolean future) {
        TextView chip = new TextView(requireContext());
        chip.setText(date.format(STRIP_DATE_FORMATTER) + "\n" + WEEK_NAMES[date.getDayOfWeek().getValue() - 1]);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setTextSize(13);
        chip.setIncludeFontPadding(false);
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setTextColor(future ? 0xFFB8B0A6 : COLOR_TEXT);
        int fillColor = selected ? COLOR_BEE : COLOR_SURFACE;
        int strokeColor = selected ? COLOR_BEE : COLOR_LINE;
        if (future) {
            fillColor = 0xFFF7F2EA;
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

    private void clearInputs() {
        checkExercise.setChecked(false);
        checkReading.setChecked(false);
        checkThinking.setChecked(false);
        checkSpeaking.setChecked(false);
        checkSleep.setChecked(false);
        editNote.setText("");
    }

    private void saveCurrentRecord() {
        if (selectedDate.isAfter(LocalDate.now())) {
            Toast.makeText(requireContext(), "未来日期不能保存", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Boolean> habits = getCurrentHabits();
        String note = editNote.getText().toString().trim();
        boolean hasCheckedHabit = false;
        for (Boolean completed : habits.values()) {
            if (completed) {
                hasCheckedHabit = true;
                break;
            }
        }
        if (!hasCheckedHabit && note.isEmpty()) {
            Toast.makeText(requireContext(), "空记录不能保存", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray records = readRecords();
        String dateString = selectedDate.format(DATE_FORMATTER);
        JSONObject record = findRecord(records, dateString);
        if (record == null) {
            record = new JSONObject();
            records.put(record);
        }

        try {
            record.put("date", dateString);
            JSONObject habitsJson = new JSONObject();
            for (Map.Entry<String, Boolean> entry : habits.entrySet()) {
                habitsJson.put(entry.getKey(), entry.getValue() ? 1 : 0);
            }
            record.put("habits", habitsJson);
            record.put("note", note);
            if (writeRecords(records)) {
                Toast.makeText(requireContext(), "当日记录已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            LogUtil.e(TAG, "save json error: " + e.getMessage());
            Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Map<String, Boolean> getCurrentHabits() {
        Map<String, Boolean> habits = new LinkedHashMap<>();
        habits.put("运动", checkExercise.isChecked());
        habits.put("阅读", checkReading.isChecked());
        habits.put("思维", checkThinking.isChecked());
        habits.put("口语", checkSpeaking.isChecked());
        habits.put("睡眠", checkSleep.isChecked());
        return habits;
    }

    private void showStatisticResult(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            Toast.makeText(requireContext(), "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show();
            return;
        }

        LocalDate today = LocalDate.now();
        if (startDate.isAfter(today) || endDate.isAfter(today)) {
            Toast.makeText(requireContext(), "统计范围不能包含未来日期", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray records = readRecords();
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Map<String, Integer> completedDays = new LinkedHashMap<>();
        for (String habitName : HABIT_NAMES) {
            completedDays.put(habitName, 0);
        }

        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) {
                continue;
            }
            LocalDate recordDate;
            try {
                recordDate = LocalDate.parse(record.optString("date"), DATE_FORMATTER);
            } catch (Exception e) {
                continue;
            }
            if (recordDate.isBefore(startDate) || recordDate.isAfter(endDate) || recordDate.isAfter(today)) {
                continue;
            }

            JSONObject habits = record.optJSONObject("habits");
            if (habits == null) {
                continue;
            }
            for (String habitName : HABIT_NAMES) {
                completedDays.put(habitName, completedDays.get(habitName) + readHabitValue(habits, habitName));
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append(startDate.format(DATE_FORMATTER))
                .append(" ~ ")
                .append(endDate.format(DATE_FORMATTER))
                .append(" 统计：\n\n");
        for (String habitName : HABIT_NAMES) {
            int completed = completedDays.get(habitName);
            double rate = completed * 100.0 / totalDays;
            builder.append(habitName)
                    .append("：完成")
                    .append(completed)
                    .append("/")
                    .append(totalDays)
                    .append("天 ")
                    .append(String.format(Locale.CHINA, "%.1f%%", rate))
                    .append("\n");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("打卡统计")
                .setMessage(builder.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    private JSONArray readRecords() {
        File file = new File(requireContext().getFilesDir(), FILE_NAME);
        if (!file.exists() || file.length() == 0) {
            return new JSONArray();
        }

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
            if (readLength <= 0) {
                return new JSONArray();
            }
            return new JSONArray(new String(data, 0, readLength, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            LogUtil.e(TAG, "read json error: " + e.getMessage());
            return new JSONArray();
        }
    }

    private boolean writeRecords(JSONArray records) {
        File file = new File(requireContext().getFilesDir(), FILE_NAME);
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(records.toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | JSONException e) {
            LogUtil.e(TAG, "write json error: " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private JSONObject findRecord(JSONArray records, String dateString) {
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && dateString.equals(record.optString("date"))) {
                return record;
            }
        }
        return null;
    }

    private int readHabitValue(JSONObject habits, String habitName) {
        return habits.optInt(habitName, 0) == 1 ? 1 : 0;
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

    private interface DateSelectedCallback {
        void onSelected(LocalDate date);
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
