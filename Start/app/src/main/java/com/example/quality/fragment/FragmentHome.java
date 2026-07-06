package com.example.quality.fragment;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.R;
import com.example.quality.util.LogUtil;

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

public class FragmentHome extends Fragment {
    private static final String TAG = "FragmentHome";
    private static final String FILE_NAME = "quality_record.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);
    private static final String[] HABIT_NAMES = {"运动", "阅读", "思维", "口语", "睡眠"};
    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private TextView textSelectedDate;
    private TextView buttonPreviousDay;
    private TextView buttonNextDay;
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
        return inflater.inflate(R.layout.fragment_home, container, false);
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
        textSelectedDate = view.findViewById(R.id.textSelectedDate);
        buttonPreviousDay = view.findViewById(R.id.buttonPreviousDay);
        buttonNextDay = view.findViewById(R.id.buttonNextDay);
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
        buttonPreviousDay.setOnClickListener(v -> loadDate(selectedDate.minusDays(1)));
        buttonNextDay.setOnClickListener(v -> {
            LocalDate nextDate = selectedDate.plusDays(1);
            if (nextDate.isAfter(LocalDate.now())) {
                Toast.makeText(requireContext(), "不能选择未来日期", Toast.LENGTH_SHORT).show();
                return;
            }
            loadDate(nextDate);
        });
        textSelectedDate.setOnClickListener(v -> showSingleDatePicker());
        buttonSaveRecord.setOnClickListener(v -> saveCurrentRecord());
        buttonStatistic.setOnClickListener(v -> showStatisticStartPicker());
    }

    private void showSingleDatePicker() {
        DatePickerDialog dialog = createDatePickerDialog("选择打卡日期", selectedDate, date -> loadDate(date));
        dialog.show();
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
        textSelectedDate.setText(formatDisplayDate(date));
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
        Object value = habits.opt(habitName);
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1 ? 1 : 0;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        if ("1".equals(String.valueOf(value))) {
            return 1;
        }
        return 0;
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
