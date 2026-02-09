package com.example.todolist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

public class WeeklyBarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] values = new int[] { 0, 0, 0, 0, 0, 0, 0 };

    public WeeklyBarChartView(Context context) {
        super(context);
        init();
    }

    public WeeklyBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeeklyBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private int chartType = 0; // 0 = Bar, 1 = Line, 2 = Pie/Donut
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint piePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void init() {
        barPaint.setColor(0xFF2196F3); // Material Blue
        barPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(0xFF2196F3);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        pointPaint.setColor(0xFF2196F3);
        pointPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0x33B0BEC5); // Blue Grey 200 light
        gridPaint.setStrokeWidth(dp(1));

        textPaint.setColor(0xFF757575);
        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);

        piePaint.setStyle(Paint.Style.STROKE);
        piePaint.setStrokeWidth(dp(16));
        piePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setChartType(int type) {
        this.chartType = type;
        invalidate();
    }

    public void setValues(int[] values) {
        if (values == null || values.length != 7)
            return;
        this.values = values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        // If Pie Chart (Type 2)
        if (chartType == 2) {
            drawPieChart(canvas, w, h);
            return;
        }

        int max = 0;
        for (int v : values)
            max = Math.max(max, v);
        if (max <= 0)
            max = 1;

        float paddingLeft = dp(16);
        float paddingRight = dp(16);
        float paddingTop = dp(24); // More space for text
        float paddingBottom = dp(8);

        float chartW = w - paddingLeft - paddingRight;
        float chartH = h - paddingTop - paddingBottom;

        // Baseline
        canvas.drawLine(paddingLeft, h - paddingBottom, w - paddingRight, h - paddingBottom, gridPaint);

        float slotW = chartW / 7f;

        if (chartType == 0) {
            // BAR CHART
            float barW = slotW * 0.4f;
            for (int i = 0; i < 7; i++) {
                float xCenter = paddingLeft + slotW * i + slotW / 2f;
                // Draw text always
                if (values[i] > 0) {
                    float ratio = values[i] / (float) max;
                    if (ratio > 1f)
                        ratio = 1f;

                    float barH = chartH * ratio * 0.9f;
                    float top = h - paddingBottom - barH;
                    float bottom = h - paddingBottom;
                    float barLeft = xCenter - barW / 2f;
                    float barRight = xCenter + barW / 2f;

                    canvas.drawRoundRect(barLeft, top, barRight, bottom, dp(4), dp(4), barPaint);
                    canvas.drawText(String.valueOf(values[i]), xCenter, top - dp(4), textPaint);
                }
            }
        } else {
            // SMOOTH LINE CHART
            float[] cy = new float[7];
            float[] cx = new float[7];

            for (int i = 0; i < 7; i++) {
                float xCenter = paddingLeft + slotW * i + slotW / 2f;
                cx[i] = xCenter;
                float ratio = values[i] / (float) max;
                if (ratio > 1f)
                    ratio = 1f;
                float valH = chartH * ratio * 0.90f;
                cy[i] = h - paddingBottom - valH;
            }

            // Draw Cubic Bezier Path
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(cx[0], cy[0]);
            for (int i = 0; i < 6; i++) {
                float midX = (cx[i] + cx[i + 1]) / 2.0f;
                path.cubicTo(midX, cy[i], midX, cy[i + 1], cx[i + 1], cy[i + 1]);
            }
            canvas.drawPath(path, linePaint);

            // Draw points and text
            for (int i = 0; i < 7; i++) {
                if (values[i] > 0) {
                    canvas.drawCircle(cx[i], cy[i], dp(4), pointPaint);
                    Paint whiteP = new Paint(Paint.ANTI_ALIAS_FLAG);
                    whiteP.setColor(0xFFFFFFFF);
                    canvas.drawCircle(cx[i], cy[i], dp(2), whiteP);
                    canvas.drawText(String.valueOf(values[i]), cx[i], cy[i] - dp(8), textPaint);
                } else {
                    // Small dot for 0
                    canvas.drawCircle(cx[i], cy[i], dp(2), pointPaint);
                }
            }
        }
    }

    private void drawPieChart(Canvas canvas, float w, float h) {
        int total = 0;
        for (int v : values)
            total += v;

        if (total == 0) {
            Paint emptyP = new Paint(Paint.ANTI_ALIAS_FLAG);
            emptyP.setColor(0xFFE0E0E0);
            emptyP.setStyle(Paint.Style.STROKE);
            emptyP.setStrokeWidth(dp(16));
            canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 3f, emptyP);
            return;
        }

        float radius = Math.min(w, h) / 3f; // Smaller radius to fit in view
        float cx = w / 2;
        float cy = h / 2;
        // Rect for arc
        android.graphics.RectF rect = new android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        float startAngle = -90;
        int[] colors = {
                0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
                0xFF5C6BC0, 0xFF42A5F5, 0xFF26C6DA
        }; // 7 colors for 7 days

        for (int i = 0; i < 7; i++) {
            if (values[i] == 0)
                continue;
            float sweep = 360f * (values[i] / (float) total);

            piePaint.setColor(colors[i % colors.length]);
            // Draw slightly separated arcs
            canvas.drawArc(rect, startAngle + 2, sweep - 4, false, piePaint);

            // Draw text if enough sweep
            if (sweep > 15) {
                double rad = Math.toRadians(startAngle + sweep / 2);
                float tx = (float) (cx + (radius + dp(20)) * Math.cos(rad));
                float ty = (float) (cy + (radius + dp(20)) * Math.sin(rad));
                canvas.drawText(String.valueOf(values[i]), tx, ty + dp(4), textPaint);
            }

            startAngle += sweep;
        }

        // Draw total in center
        Paint centerText = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerText.setColor(0xFF212121);
        centerText.setTextSize(dp(24));
        centerText.setTextAlign(Paint.Align.CENTER);
        centerText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String s = total + " Done";
        canvas.drawText(String.valueOf(total), cx, cy, centerText);

        centerText.setTextSize(dp(12));
        centerText.setColor(0xFF757575);
        canvas.drawText("Done", cx, cy + dp(16), centerText);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
