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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.count.CategoryIconMapper;
import com.example.quality.count.CountRepository;
import com.example.quality.count.CountTransaction;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class FragmentCountTransactionDetail extends Fragment {
    private static final String ARG_TRANSACTION_ID = "transaction_id";
    private static final String TYPE_INCOME = "income";
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
    private long transactionId;

    public static FragmentCountTransactionDetail newInstance(long transactionId) {
        FragmentCountTransactionDetail fragment = new FragmentCountTransactionDetail();
        Bundle args = new Bundle();
        args.putLong(ARG_TRANSACTION_ID, transactionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CountRepository(requireContext());
        transactionId = getArguments() == null ? -1 : getArguments().getLong(ARG_TRANSACTION_ID, -1);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        CountTransaction tx = repository.getTransaction(transactionId);
        return buildContentView(tx);
    }

    private View buildContentView(@Nullable CountTransaction tx) {
        LinearLayout page = vertical();
        page.setBackgroundColor(COLOR_BACKGROUND);
        page.addView(buildHeader());

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(16), dp(18), dp(28));
        scrollView.addView(content);
        page.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        if (tx == null) {
            TextView empty = text("记录不存在", 16, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, dp(60));
            content.addView(empty, matchWrap());
            return page;
        }

        LinearLayout iconCard = vertical();
        iconCard.setGravity(Gravity.CENTER);
        iconCard.setPadding(dp(18), dp(18), dp(18), dp(16));
        iconCard.setBackground(round(COLOR_SURFACE, dp(20)));
        iconCard.setElevation(dp(2));
        ImageView icon = iconBubble(tx.categoryIcon, 34);
        iconCard.addView(icon, fixed(dp(64), dp(64)));
        TextView category = text(tx.categoryPath(), 17, COLOR_TEXT, true);
        category.setGravity(Gravity.CENTER);
        category.setPadding(0, dp(10), 0, 0);
        iconCard.addView(category, matchWrap());
        LinearLayout.LayoutParams iconParams = matchWrap();
        iconParams.setMargins(0, 0, 0, dp(12));
        content.addView(iconCard, iconParams);

        LinearLayout infoCard = vertical();
        infoCard.setPadding(dp(16), dp(6), dp(16), dp(6));
        infoCard.setBackground(round(COLOR_SURFACE, dp(20)));
        infoCard.setElevation(dp(2));
        infoCard.addView(detailRow("日期", tx.date.format(DAY_FORMATTER), COLOR_TEXT));
        infoCard.addView(divider());
        boolean income = TYPE_INCOME.equals(tx.type);
        infoCard.addView(detailRow("金额", (income ? "+" : "-") + repository.formatMoney(tx.amount),
                income ? COLOR_GREEN : COLOR_ORANGE));
        infoCard.addView(divider());
        infoCard.addView(detailRow("类别", tx.categoryPath(), COLOR_TEXT));
        infoCard.addView(divider());
        String note = tx.note == null || tx.note.trim().isEmpty() ? "无" : tx.note.trim();
        infoCard.addView(detailRow("备注", note, COLOR_TEXT));
        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.setMargins(0, 0, 0, dp(12));
        content.addView(infoCard, infoParams);

        LinearLayout imageCard = vertical();
        imageCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        imageCard.setBackground(round(COLOR_SURFACE, dp(20)));
        imageCard.setElevation(dp(2));
        TextView imageTitle = text("图片", 16, COLOR_TEXT, true);
        imageCard.addView(imageTitle);
        View imageContent = buildImageContent(tx.imagePaths);
        LinearLayout.LayoutParams imageContentParams = matchWrap();
        imageContentParams.setMargins(0, dp(12), 0, 0);
        imageCard.addView(imageContent, imageContentParams);
        content.addView(imageCard, matchWrap());
        return page;
    }

    private View buildHeader() {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(18), dp(12));
        header.setBackgroundColor(COLOR_BEE);

        TextView title = text("记录详情", 20, COLOR_TEXT, true);
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

    private View detailRow(String label, String value, int valueColor) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        TextView labelView = text(label + "：", 14, COLOR_MUTED, false);
        row.addView(labelView, fixed(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = text(value, 15, valueColor, true);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View buildImageContent(List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            TextView empty = text("无图片", 14, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(round(COLOR_SURFACE_SOFT, dp(16)));
            empty.setPadding(0, dp(26), 0, dp(26));
            return empty;
        }
        LinearLayout images = vertical();
        boolean hasImage = false;
        for (String imagePath : imagePaths) {
            if (imagePath == null || imagePath.trim().isEmpty()) {
                continue;
            }
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                continue;
            }
            hasImage = true;
            ImageView image = new ImageView(requireContext());
            image.setImageURI(Uri.fromFile(imageFile));
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(round(COLOR_SURFACE_SOFT, dp(16)));
            image.setMinimumHeight(dp(180));
            image.setOnClickListener(v -> showImageViewer(imagePath));
            LinearLayout.LayoutParams params = matchWrap();
            params.setMargins(0, 0, 0, dp(12));
            images.addView(image, params);
        }
        if (!hasImage) {
            TextView missing = text("图片文件不存在", 14, COLOR_MUTED, false);
            missing.setGravity(Gravity.CENTER);
            missing.setBackground(round(COLOR_SURFACE_SOFT, dp(16)));
            missing.setPadding(0, dp(26), 0, dp(26));
            return missing;
        }
        return images;
    }

    private void showImageViewer(String imagePath) {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
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

    private ImageView iconBubble(String iconKey, int iconSizeDp) {
        ImageView image = new ImageView(requireContext());
        CategoryIconMapper.loadInto(image, iconKey, repository, COLOR_TEXT);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(13), dp(13), dp(13), dp(13));
        image.setBackground(round(COLOR_BEE_SOFT, dp(32)));
        image.setMinimumWidth(dp(iconSizeDp));
        image.setMinimumHeight(dp(iconSizeDp));
        return image;
    }

    private View divider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x1F8D6E63);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        return divider;
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
}
