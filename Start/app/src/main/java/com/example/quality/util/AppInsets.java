package com.example.quality.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class AppInsets {
    private AppInsets() {
    }

    public static void applySystemBarPadding(View view, boolean top, boolean bottom) {
        int leftPadding = view.getPaddingLeft();
        int topPadding = view.getPaddingTop();
        int rightPadding = view.getPaddingRight();
        int bottomPadding = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = safeInsets(windowInsets);
            v.setPadding(
                    leftPadding + insets.left,
                    topPadding + (top ? insets.top : 0),
                    rightPadding + insets.right,
                    bottomPadding + (bottom ? insets.bottom : 0)
            );
            return windowInsets;
        });
        requestInsetsWhenAttached(view);
    }

    public static void applySystemBarMargins(
            View view,
            boolean left,
            boolean top,
            boolean right,
            boolean bottom
    ) {
        ViewGroup.LayoutParams rawParams = view.getLayoutParams();
        if (!(rawParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rawParams;
        int leftMargin = params.leftMargin;
        int topMargin = params.topMargin;
        int rightMargin = params.rightMargin;
        int bottomMargin = params.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = safeInsets(windowInsets);
            ViewGroup.LayoutParams updatedRawParams = v.getLayoutParams();
            if (updatedRawParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams updatedParams = (ViewGroup.MarginLayoutParams) updatedRawParams;
                updatedParams.setMargins(
                        leftMargin + (left ? insets.left : 0),
                        topMargin + (top ? insets.top : 0),
                        rightMargin + (right ? insets.right : 0),
                        bottomMargin + (bottom ? insets.bottom : 0)
                );
                v.setLayoutParams(updatedParams);
            }
            return windowInsets;
        });
        requestInsetsWhenAttached(view);
    }

    public static void showScrollableBottomSheet(BottomSheetDialog dialog, View content) {
        setScrollableBottomSheetContent(dialog, content);
        dialog.show();
    }

    public static void showFittedBottomSheet(BottomSheetDialog dialog, View content) {
        dialog.setContentView(content);
        dialog.setOnShowListener(ignored -> configureBottomSheet(dialog, content, true));
        dialog.show();
    }

    public static void setScrollableBottomSheetContent(BottomSheetDialog dialog, View content) {
        Context context = content.getContext();
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialog.setContentView(scrollView);
        dialog.setOnShowListener(ignored -> configureBottomSheet(dialog, scrollView, false));
    }

    private static void configureBottomSheet(BottomSheetDialog dialog, View content, boolean fillAvailableHeight) {
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            applySystemBarPadding(content, false, true);
            return;
        }
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(!fillAvailableHeight);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        int initialBottomPadding = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets insets = safeInsets(windowInsets);
            int rootHeight = v.getRootView().getHeight();
            if (rootHeight <= 0) {
                rootHeight = v.getResources().getDisplayMetrics().heightPixels;
            }
            int availableHeight = rootHeight - insets.top - insets.bottom - dp(v, 8);
            if (availableHeight > 0) {
                behavior.setMaxHeight(availableHeight);
                if (fillAvailableHeight) {
                    ViewGroup.LayoutParams sheetParams = bottomSheet.getLayoutParams();
                    sheetParams.height = availableHeight;
                    bottomSheet.setLayoutParams(sheetParams);

                    ViewGroup.LayoutParams contentParams = v.getLayoutParams();
                    if (contentParams != null) {
                        contentParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        v.setLayoutParams(contentParams);
                    }
                }
            }
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    initialBottomPadding + insets.bottom
            );
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            return windowInsets;
        });
        requestInsetsWhenAttached(content);
    }

    private static Insets safeInsets(WindowInsetsCompat windowInsets) {
        Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
        return Insets.of(
                Math.max(systemBars.left, cutout.left),
                Math.max(systemBars.top, cutout.top),
                Math.max(systemBars.right, cutout.right),
                Math.max(systemBars.bottom, cutout.bottom)
        );
    }

    private static void requestInsetsWhenAttached(View view) {
        if (ViewCompat.isAttachedToWindow(view)) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View attachedView) {
                    attachedView.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(attachedView);
                }

                @Override
                public void onViewDetachedFromWindow(View detachedView) {
                }
            });
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
