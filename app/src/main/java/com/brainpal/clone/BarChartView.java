package com.brainpal.clone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Simple bar chart used for the 7-day history and the 24-hour split. */
public final class BarChartView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barDim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] values = new int[0];
    private String[] labels = new String[0];
    private int highlight = -1;
    private boolean showValues = true;

    public BarChartView(Context c) {
        super(c);
        bar.setColor(Theme.AMBER);
        barDim.setColor(0xFF3A3A3A);

        label.setColor(Theme.MUTED_2);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Theme.medium(c));
        label.setTextSize(c.getResources().getDisplayMetrics().density * 10f);

        valuePaint.setColor(Theme.MUTED);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Theme.bold(c));
        valuePaint.setTextSize(c.getResources().getDisplayMetrics().density * 10f);
    }

    public void set(int[] values, String[] labels, int highlight, boolean showValues) {
        this.values = values;
        this.labels = labels;
        this.highlight = highlight;
        this.showValues = showValues;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) (getResources().getDisplayMetrics().density * 132f);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values == null || values.length == 0) return;

        float d = getResources().getDisplayMetrics().density;
        float labelSpace = d * 20f;
        float valueSpace = d * 14f;
        float bottom = getHeight() - labelSpace;
        float top = valueSpace;

        int max = 1;
        for (int v : values) max = Math.max(max, v);

        float slot = getWidth() / (float) values.length;
        float gap = Math.max(d * 2f, slot * 0.26f);
        float barW = Math.max(d * 2f, slot - gap);
        float radius = Math.min(d * 6f, barW / 2f);

        for (int i = 0; i < values.length; i++) {
            float cx = slot * i + slot / 2f;
            float h = values[i] <= 0 ? d * 3f : Math.max(d * 6f, (bottom - top) * (values[i] / (float) max));
            float left = cx - barW / 2f;
            rect.set(left, bottom - h, left + barW, bottom);
            boolean on = i == highlight;
            canvas.drawRoundRect(rect, radius, radius, on ? bar : barDim);

            if (showValues && values[i] > 0 && values.length <= 10) {
                canvas.drawText(String.valueOf(values[i]), cx, bottom - h - d * 4f, valuePaint);
            }
            if (labels != null && i < labels.length && labels[i] != null && !labels[i].isEmpty()) {
                canvas.drawText(labels[i], cx, getHeight() - d * 4f, label);
            }
        }
    }
}
