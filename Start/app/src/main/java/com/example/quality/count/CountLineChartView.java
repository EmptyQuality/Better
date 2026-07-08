package com.example.quality.count;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CountLineChartView extends View {
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<CountSeriesPoint> points = new ArrayList<>();
    private int selectedIndex = -1;
    private float[] pointXs = new float[0];
    private float[] pointYs = new float[0];
    private final float density;

    public CountLineChartView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        axisPaint.setColor(0xFFE5E7EB);
        axisPaint.setStrokeWidth(dp(1));
        linePaint.setColor(0xFF6B7280);
        linePaint.setStrokeWidth(dp(1));
        linePaint.setStyle(Paint.Style.STROKE);
        pointPaint.setColor(0xFFF8C91C);
        pointPaint.setStyle(Paint.Style.FILL);
        emptyPointFillPaint.setColor(0xFFFFFFFF);
        emptyPointFillPaint.setStyle(Paint.Style.FILL);
        emptyPointStrokePaint.setColor(0xFF9CA3AF);
        emptyPointStrokePaint.setStyle(Paint.Style.STROKE);
        emptyPointStrokePaint.setStrokeWidth(dp(1));
        textPaint.setColor(0xFF6B7280);
        textPaint.setTextSize(sp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);
        selectedPointPaint.setColor(0xFF2F2D2D);
        selectedPointPaint.setStyle(Paint.Style.STROKE);
        selectedPointPaint.setStrokeWidth(dp(1.5f));
        tooltipPaint.setColor(0xEE2F2D2D);
        tooltipPaint.setStyle(Paint.Style.FILL);
        tooltipTextPaint.setColor(0xFFFFFFFF);
        tooltipTextPaint.setTextSize(sp(12));
        tooltipTitlePaint.setColor(0xFFFFFFFF);
        tooltipTitlePaint.setTextSize(sp(13));
        tooltipTitlePaint.setFakeBoldText(true);
        setClickable(true);
    }

    public void setPoints(List<CountSeriesPoint> points) {
        this.points = points == null ? new ArrayList<>() : points;
        selectedIndex = -1;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
            int hitIndex = findHitPoint(event.getX(), event.getY());
            if (hitIndex >= 0) {
                selectedIndex = hitIndex;
                invalidate();
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                return true;
            }
            if (selectedIndex != -1) {
                selectedIndex = -1;
                invalidate();
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = (int) dp(18);
        int right = width - (int) dp(10);
        int top = (int) dp(12);
        int bottom = height - (int) dp(28);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
        canvas.drawLine(left, top + (bottom - top) / 2f, right, top + (bottom - top) / 2f, axisPaint);

        if (points == null || points.isEmpty()) {
            canvas.drawText("暂无数据", width / 2f, height / 2f, textPaint);
            return;
        }

        double max = 0;
        for (CountSeriesPoint point : points) {
            max = Math.max(max, point.value);
        }
        if (max <= 0) {
            max = 1;
        }

        Path path = new Path();
        int count = points.size();
        ensurePointBuffers(count);
        for (int i = 0; i < count; i++) {
            CountSeriesPoint point = points.get(i);
            float x = count == 1 ? width / 2f : left + (right - left) * i / (float) (count - 1);
            float y = bottom - (float) (point.value / max) * (bottom - top);
            pointXs[i] = x;
            pointYs[i] = y;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
            if (count <= 12 || i % Math.max(1, count / 6) == 0 || i == count - 1) {
                canvas.drawText(point.label, x, height - dp(3), textPaint);
            }
        }
        canvas.drawPath(path, linePaint);
        for (int i = 0; i < count; i++) {
            CountSeriesPoint point = points.get(i);
            if (point.hasRecord) {
                canvas.drawCircle(pointXs[i], pointYs[i], dp(3.2f), pointPaint);
            } else {
                canvas.drawCircle(pointXs[i], pointYs[i], dp(3.8f), emptyPointFillPaint);
                canvas.drawCircle(pointXs[i], pointYs[i], dp(3.8f), emptyPointStrokePaint);
            }
            if (i == selectedIndex) {
                canvas.drawCircle(pointXs[i], pointYs[i], dp(4.8f), selectedPointPaint);
            }
        }
        drawSelectedTooltip(canvas, top, bottom);
    }

    private void drawSelectedTooltip(Canvas canvas, int top, int bottom) {
        if (selectedIndex < 0 || selectedIndex >= points.size() || selectedIndex >= pointXs.length) {
            return;
        }
        CountSeriesPoint point = points.get(selectedIndex);
        float anchorX = pointXs[selectedIndex];
        float anchorY = pointYs[selectedIndex];
        String title = point.label;
        String total = nonEmpty(point.totalLabel, "总额") + ": " + money(point.value);
        String maxItem = point.maxItemName == null || point.maxItemName.trim().isEmpty()
                ? nonEmpty(point.maxItemLabel, "最大一笔") + ": 暂无"
                : nonEmpty(point.maxItemLabel, "最大一笔") + ": " + point.maxItemName + "  " + money(point.maxItemAmount);

        float paddingH = dp(12);
        float paddingV = dp(9);
        float lineGap = dp(18);
        float maxTextWidth = Math.max(
                tooltipTitlePaint.measureText(title),
                Math.max(tooltipTextPaint.measureText(total), tooltipTextPaint.measureText(maxItem))
        );
        float bubbleWidth = Math.min(getWidth() - dp(20), maxTextWidth + paddingH * 2);
        float bubbleHeight = dp(72);
        float bubbleLeft = clamp(anchorX - bubbleWidth / 2f, dp(8), getWidth() - bubbleWidth - dp(8));
        float bubbleTop = anchorY - bubbleHeight - dp(16);
        if (bubbleTop < top) {
            bubbleTop = Math.min(bottom - bubbleHeight - dp(8), anchorY + dp(16));
        }
        RectF rect = new RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleWidth, bubbleTop + bubbleHeight);
        canvas.drawRoundRect(rect, dp(6), dp(6), tooltipPaint);

        Path arrow = new Path();
        boolean arrowBelow = rect.bottom <= anchorY;
        float arrowY = arrowBelow ? rect.bottom : rect.top;
        arrow.moveTo(anchorX, arrowBelow ? arrowY + dp(8) : arrowY - dp(8));
        arrow.lineTo(clamp(anchorX - dp(7), rect.left + dp(10), rect.right - dp(10)), arrowY);
        arrow.lineTo(clamp(anchorX + dp(7), rect.left + dp(10), rect.right - dp(10)), arrowY);
        arrow.close();
        canvas.drawPath(arrow, tooltipPaint);

        float textX = rect.left + paddingH;
        float textY = rect.top + paddingV + dp(12);
        canvas.drawText(title, textX, textY, tooltipTitlePaint);
        canvas.drawText(total, textX, textY + lineGap, tooltipTextPaint);
        canvas.drawText(fitText(maxItem, bubbleWidth - paddingH * 2), textX, textY + lineGap * 2, tooltipTextPaint);
    }

    private int findHitPoint(float x, float y) {
        if (pointXs.length == 0 || pointYs.length == 0) {
            return -1;
        }
        float bestDistance = dp(18);
        int hitIndex = -1;
        for (int i = 0; i < points.size() && i < pointXs.length; i++) {
            float dx = pointXs[i] - x;
            float dy = pointYs[i] - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= bestDistance) {
                bestDistance = distance;
                hitIndex = i;
            }
        }
        return hitIndex;
    }

    private void ensurePointBuffers(int count) {
        if (pointXs.length != count) {
            pointXs = new float[count];
            pointYs = new float[count];
        }
    }

    private String fitText(String value, float maxWidth) {
        if (tooltipTextPaint.measureText(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && tooltipTextPaint.measureText(value.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : value.substring(0, end) + ellipsis;
    }

    private String money(double value) {
        String formatted = String.format(Locale.CHINA, "%.2f", value);
        if (formatted.endsWith(".00")) {
            return formatted.substring(0, formatted.length() - 3);
        }
        if (formatted.endsWith("0")) {
            return formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
