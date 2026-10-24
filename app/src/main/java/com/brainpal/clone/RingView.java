package com.brainpal.clone;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Hero progress ring: today's count inside a limit arc. */
public final class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int count;
    private int limit = 100;
    private float shown = 0f;
    private ValueAnimator animator;

    public RingView(Context c) {
        super(c);

        float density = c.getResources().getDisplayMetrics().density;
        float stroke = 14f * density;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF1E1E1E);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(Theme.AMBER);

        numberPaint.setColor(Theme.TEXT);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTypeface(Theme.black(c));

        labelPaint.setColor(Theme.MUTED);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Theme.medium(c));

        hintPaint.setColor(Theme.MUTED_2);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTypeface(Theme.medium(c));
    }

    public void set(int count, int limit) {
        this.count = count;
        this.limit = Math.max(1, limit);

        float target = Math.min(1f, count / (float) this.limit);
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(520);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        numberPaint.setTextSize(getResources().getDisplayMetrics().density * 62f);
        labelPaint.setTextSize(getResources().getDisplayMetrics().density * 15f);
        hintPaint.setTextSize(getResources().getDisplayMetrics().density * 12f);
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (getResources().getDisplayMetrics().density * 236f);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float pad = getResources().getDisplayMetrics().density * 18f;
        oval.set(pad, pad, getWidth() - pad, getHeight() - pad);

        canvas.drawArc(oval, 0, 360, false, track);
        if (shown > 0) {
            canvas.drawArc(oval, -90, 360 * shown, false, arc);
        }

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float numberBaseline = cy - (numberPaint.ascent() + numberPaint.descent()) / 2f - dp(10);
        canvas.drawText(String.valueOf(count), cx, numberBaseline, numberPaint);
        canvas.drawText("reels today", cx, cy + dp(34), labelPaint);

        boolean over = count >= limit;
        canvas.drawText(over ? "limit reached" : "of " + limit + " limit",
                cx, cy + dp(56), hintPaint);
        arc.setColor(over ? 0xFFE86A5C : Theme.AMBER);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
