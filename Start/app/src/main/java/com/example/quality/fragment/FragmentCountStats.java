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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.count.CategoryIconMapper;
import com.example.quality.count.CategoryTotal;
import com.example.quality.count.CountLineChartView;
import com.example.quality.count.CountRepository;
import com.example.quality.count.CountSeriesPoint;
import com.example.quality.count.CountStats;
import com.example.quality.util.LogUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class FragmentCountStats extends Fragment {
    private static final String TAG = "FragmentCountStats";
    private static final String TYPE_EXPENSE = "expense";
    private static final String TYPE_INCOME = "income";
    private static final int COLOR_BEE = 0xFFF8C91C;
    private static final int COLOR_TEXT = 0xFF111827;
    private static final int COLOR_MUTED = 0xFF6B7280;

    private CountRepository repository;
    private String mode = "week";
    private String currentType = TYPE_EXPENSE;
    private LocalDate anchor = LocalDate.now();
    private TextView typeTitle;
    private TextView weekTab;
    private TextView monthTab;
    private TextView yearTab;
    private TextView periodLabel;
    private TextView totalValue;
    private TextView averageValue;
    private TextView rankTitle;
    private CountLineChartView chartView;
    private LinearLayout rankingList;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        LogUtil.d(TAG, "onAttach");
    }

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
        refreshStats();
    }

    private View buildContentView() {
        LinearLayout page = vertical();
        page.setBackgroundColor(0xFFFAFAFA);

        LinearLayout header = vertical();
        header.setBackgroundColor(COLOR_BEE);
        header.setPadding(dp(12), dp(8), dp(12), dp(10));

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("<", 24, COLOR_TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        titleRow.addView(back, fixed(dp(42), dp(42)));

        typeTitle = text("", 20, COLOR_TEXT, true);
        typeTitle.setGravity(Gravity.CENTER);
        typeTitle.setOnClickListener(v -> showTypePicker());
        titleRow.addView(typeTitle, new LinearLayout.LayoutParams(0, dp(42), 1));

        View spacer = new View(requireContext());
        titleRow.addView(spacer, fixed(dp(42), dp(42)));
        header.addView(titleRow);

        LinearLayout tabs = horizontal();
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, dp(8), 0, 0);
        weekTab = tab("周", "week");
        monthTab = tab("月", "month");
        yearTab = tab("年", "year");
        tabs.addView(weekTab, tabParams());
        tabs.addView(monthTab, tabParams());
        tabs.addView(yearTab, tabParams());
        header.addView(tabs);
        page.addView(header);

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(12), dp(18), dp(92));
        scrollView.addView(content);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout periodRow = horizontal();
        periodRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = smallButton("<");
        prev.setOnClickListener(v -> {
            movePeriod(false);
            refreshStats();
        });
        TextView next = smallButton(">");
        next.setOnClickListener(v -> {
            movePeriod(true);
            refreshStats();
        });
        periodLabel = text("", 16, COLOR_TEXT, true);
        periodLabel.setGravity(Gravity.CENTER);
        periodRow.addView(prev, fixed(dp(40), dp(36)));
        periodRow.addView(periodLabel, new LinearLayout.LayoutParams(0, dp(36), 1));
        periodRow.addView(next, fixed(dp(40), dp(36)));
        content.addView(periodRow);

        totalValue = text("总支出：0.00", 17, COLOR_MUTED, false);
        totalValue.setPadding(0, dp(12), 0, dp(4));
        averageValue = text("平均值：0.00", 16, COLOR_MUTED, false);
        content.addView(totalValue);
        content.addView(averageValue);

        chartView = new CountLineChartView(requireContext());
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(136)
        );
        chartParams.setMargins(0, dp(12), 0, dp(16));
        content.addView(chartView, chartParams);

        rankTitle = text("", 18, COLOR_TEXT, true);
        rankTitle.setPadding(0, dp(2), 0, dp(12));
        content.addView(rankTitle);
        rankingList = vertical();
        content.addView(rankingList);
        return page;
    }

    private void refreshStats() {
        if (rankingList == null) {
            return;
        }
        updateTabs();
        updateTypeTitle();
        LocalDate start = rangeStart();
        LocalDate end = rangeEnd(start);
        periodLabel.setText(periodText(start, end));
        CountStats stats = repository.getStats(start, end);
        boolean income = TYPE_INCOME.equals(currentType);
        double total = income ? stats.income : stats.expense;
        int averageBy = averageDivisor(start, end);
        totalValue.setText((income ? "总收入：" : "总支出：") + repository.formatMoney(total));
        averageValue.setText("平均值：" + repository.formatMoney(averageBy == 0 ? 0 : total / averageBy));
        rankTitle.setText(income ? "收入排行榜" : "支出排行榜");

        List<CountSeriesPoint> points = "year".equals(mode)
                ? getTrendByMonth(currentType, start.getYear())
                : getTrendByDay(currentType, start, end);
        chartView.setPoints(points);
        renderRanking(start, end, total);
    }

    private void renderRanking(LocalDate start, LocalDate end, double total) {
        rankingList.removeAllViews();
        List<CategoryTotal> totals = repository.getCategoryTotals(currentType, start, end);
        if (totals.isEmpty()) {
            TextView empty = text(TYPE_INCOME.equals(currentType) ? "暂无收入" : "暂无支出", 15, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            rankingList.addView(empty);
            return;
        }
        for (CategoryTotal item : totals) {
            LinearLayout row = vertical();
            row.setPadding(0, dp(6), 0, dp(10));
            LinearLayout top = horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(iconBubble(item.icon), fixed(dp(40), dp(40)));
            TextView name = text(item.name, 15, COLOR_TEXT, true);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            nameParams.setMargins(dp(10), 0, dp(8), 0);
            top.addView(name, nameParams);
            double percent = total <= 0 ? 0 : item.total * 100 / total;
            TextView amount = text(String.format(Locale.CHINA, "%.1f%%   %s",
                    percent, repository.formatMoney(item.total)), 13, COLOR_TEXT, false);
            top.addView(amount);
            row.addView(top);
            View bar = new View(requireContext());
            bar.setBackground(round(COLOR_BEE, dp(4)));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    Math.max(dp(10), (int) (getResources().getDisplayMetrics().widthPixels * 0.72f * percent / 100f)),
                    dp(5)
            );
            barParams.setMargins(dp(50), dp(6), dp(10), 0);
            row.addView(bar, barParams);
            rankingList.addView(row);
        }
    }

    private TextView tab(String label, String targetMode) {
        TextView tab = text(label, 14, COLOR_TEXT, true);
        tab.setGravity(Gravity.CENTER);
        tab.setOnClickListener(v -> {
            mode = targetMode;
            refreshStats();
        });
        return tab;
    }

    private void showTypePicker() {
        String[] items = {"支出", "收入"};
        new AlertDialog.Builder(requireContext())
                .setItems(items, (dialog, which) -> {
                    currentType = which == 0 ? TYPE_EXPENSE : TYPE_INCOME;
                    refreshStats();
                })
                .show();
    }

    private void updateTypeTitle() {
        typeTitle.setText((TYPE_INCOME.equals(currentType) ? "收入" : "支出") + " ▼");
    }

    private void updateTabs() {
        styleTab(weekTab, "week".equals(mode));
        styleTab(monthTab, "month".equals(mode));
        styleTab(yearTab, "year".equals(mode));
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setTextColor(selected ? COLOR_BEE : COLOR_TEXT);
        tab.setBackground(round(selected ? 0xFF2F2D2D : COLOR_BEE, dp(8)));
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(32));
        params.setMargins(dp(6), 0, dp(6), 0);
        return params;
    }

    private void movePeriod(boolean next) {
        if ("year".equals(mode)) {
            anchor = next ? anchor.plusYears(1) : anchor.minusYears(1);
        } else if ("month".equals(mode)) {
            anchor = next ? anchor.plusMonths(1) : anchor.minusMonths(1);
        } else {
            anchor = next ? anchor.plusWeeks(1) : anchor.minusWeeks(1);
        }
    }

    private List<CountSeriesPoint> getTrendByDay(String type, LocalDate start, LocalDate end) {
        java.util.ArrayList<CountSeriesPoint> points = new java.util.ArrayList<>();
        LocalDate day = start;
        while (day.isBefore(end)) {
            CountStats stats = repository.getStats(day, day.plusDays(1));
            double value = TYPE_INCOME.equals(type) ? stats.income : stats.expense;
            points.add(new CountSeriesPoint(String.format(Locale.CHINA, "%02d-%02d",
                    day.getMonthValue(), day.getDayOfMonth()), value));
            day = day.plusDays(1);
        }
        return points;
    }

    private List<CountSeriesPoint> getTrendByMonth(String type, int year) {
        java.util.ArrayList<CountSeriesPoint> points = new java.util.ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate start = LocalDate.of(year, month, 1);
            CountStats stats = repository.getStats(start, start.plusMonths(1));
            double value = TYPE_INCOME.equals(type) ? stats.income : stats.expense;
            points.add(new CountSeriesPoint(String.format(Locale.CHINA, "%02d月", month), value));
        }
        return points;
    }

    private LocalDate rangeStart() {
        if ("year".equals(mode)) {
            return LocalDate.of(anchor.getYear(), 1, 1);
        }
        if ("month".equals(mode)) {
            return LocalDate.of(anchor.getYear(), anchor.getMonthValue(), 1);
        }
        return anchor.with(DayOfWeek.MONDAY);
    }

    private LocalDate rangeEnd(LocalDate start) {
        if ("year".equals(mode)) {
            return start.plusYears(1);
        }
        if ("month".equals(mode)) {
            return start.plusMonths(1);
        }
        return start.plusDays(7);
    }

    private int averageDivisor(LocalDate start, LocalDate end) {
        if ("year".equals(mode)) {
            return 12;
        }
        return (int) (end.toEpochDay() - start.toEpochDay());
    }

    private String periodText(LocalDate start, LocalDate end) {
        if ("year".equals(mode)) {
            return start.getYear() + "年";
        }
        if ("month".equals(mode)) {
            return String.format(Locale.CHINA, "%d年%02d月", start.getYear(), start.getMonthValue());
        }
        LocalDate last = end.minusDays(1);
        return String.format(Locale.CHINA, "%02d-%02d ~ %02d-%02d",
                start.getMonthValue(), start.getDayOfMonth(),
                last.getMonthValue(), last.getDayOfMonth());
    }

    private TextView smallButton(String label) {
        TextView button = text(label, 18, COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(0xFFFFFFFF, dp(12)));
        return button;
    }

    private ImageView iconBubble(String icon) {
        ImageView bubble = new ImageView(requireContext());
        bubble.setImageResource(CategoryIconMapper.drawableResId(requireContext(), icon));
        bubble.setColorFilter(COLOR_TEXT);
        bubble.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bubble.setPadding(dp(9), dp(9), dp(9), dp(9));
        bubble.setBackground(round(0xFFF3F4F6, dp(20)));
        return bubble;
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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
