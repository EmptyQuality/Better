package com.example.quality.count;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CountLineChartView extends View {
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<CountSeriesPoint> points = new ArrayList<>();

    public CountLineChartView(Context context) {
        super(context);
        axisPaint.setColor(0xFFE5E7EB);
        axisPaint.setStrokeWidth(2f);
        linePaint.setColor(0xFF6B7280);
        linePaint.setStrokeWidth(2.2f);
        linePaint.setStyle(Paint.Style.STROKE);
        pointPaint.setColor(0xFFF8C91C);
        pointPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFF6B7280);
        textPaint.setTextSize(22f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPoints(List<CountSeriesPoint> points) {
        this.points = points == null ? new ArrayList<>() : points;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = 32;
        int right = width - 32;
        int top = 16;
        int bottom = height - 34;
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
        for (int i = 0; i < count; i++) {
            CountSeriesPoint point = points.get(i);
            float x = count == 1 ? width / 2f : left + (right - left) * i / (float) (count - 1);
            float y = bottom - (float) (point.value / max) * (bottom - top);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
            canvas.drawCircle(x, y, 6.5f, pointPaint);
            if (count <= 12 || i % Math.max(1, count / 6) == 0 || i == count - 1) {
                canvas.drawText(point.label, x, height - 8, textPaint);
            }
            if (point.value == max && point.value > 0) {
                canvas.drawText(String.format(Locale.CHINA, "%.2f", point.value), x, Math.max(top + 20, y - 14), textPaint);
            }
        }
        canvas.drawPath(path, linePaint);
    }
}
