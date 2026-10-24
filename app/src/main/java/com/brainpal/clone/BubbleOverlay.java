package com.brainpal.clone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The floating counter, matching the original:
 *
 *   - cream brain glyph + amber count in a dark rounded pill, top-centre
 *   - only visible while a reels viewer is actually on screen
 *   - draggable
 *   - long-press opens "Hide reel counter for 1 hr / 1 day", like the original
 */
public final class BubbleOverlay {

    private static final int AMBER = 0xFFF5C46B;
    private static final int CREAM = 0xFFFFE9B4;
    private static final int PILL_BG = 0xFF141414;
    private static final long LONG_PRESS_MS = 480L;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout pill;
    private TextView number;
    private ImageView brain;
    private WindowManager.LayoutParams lp;

    private FrameLayout sheet;
    private boolean attached;

    public BubbleOverlay(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canDraw() {
        return Settings.canDrawOverlays(ctx);
    }

    public boolean isShowing() {
        return pill != null;
    }

    /** Show (and update) the pill. Does nothing while the user has hidden it. */
    public void show(int count) {
        if (!canDraw() || wm == null) return;
        if (ReelStore.bubbleHiddenUntil(ctx) > System.currentTimeMillis()) return;

        if (pill == null) {
            if (!build()) return;
        }
        number.setText(String.valueOf(count));

        if (sheet != null && attached) {
            // keep the hide sheet above the pill, nothing else to do
        }
    }

    private boolean build() {
        pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(11), dp(7), dp(14), dp(7));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PILL_BG);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0x26FFFFFF);
        pill.setBackground(bg);
        pill.setElevation(dp(8));

        brain = new ImageView(ctx);
        brain.setImageResource(R.drawable.br_ic_brain);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(21), dp(15));
        ip.rightMargin = dp(7);
        pill.addView(brain, ip);

        number = new TextView(ctx);
        number.setTextColor(AMBER);
        number.setTextSize(16f);
        try {
            number.setTypeface(ctx.getResources().getFont(R.font.cabinet_grotesk_extrabold));
        } catch (Throwable t) {
            number.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        }
        number.setIncludeFontPadding(false);
        pill.addView(number);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(64);

        pill.setOnTouchListener(new DragListener());

        try {
            wm.addView(pill, lp);
            attached = true;
            return true;
        } catch (Throwable t) {
            android.util.Log.e("BubbleOverlay", "addView failed", t);
            pill = null;
            number = null;
            brain = null;
            lp = null;
            attached = false;
            return false;
        }
    }

    public void update(int count) {
        if (number != null) number.setText(String.valueOf(count));
    }

    public void hide() {
        dismissSheet();
        if (pill != null && wm != null) {
            try {
                wm.removeView(pill);
            } catch (Throwable ignored) {
            }
        }
        pill = null;
        number = null;
        brain = null;
        lp = null;
        attached = false;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------- long press

    /** Drag to move; hold still for 480 ms to open the hide sheet. */
    private final class DragListener implements View.OnTouchListener {
        private int startX, startY;
        private float touchX, touchY;
        private boolean moved;

        private final Runnable longPress = () -> {
            if (!moved && pill != null) {
                pill.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                openSheet();
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x;
                    startY = lp.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    moved = false;
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - touchX;
                    float dy = e.getRawY() - touchY;
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                        moved = true;
                        handler.removeCallbacks(longPress);
                    }
                    lp.x = startX + (int) dx;
                    lp.y = startY + (int) dy;
                    try {
                        wm.updateViewLayout(pill, lp);
                    } catch (Throwable ignored) {
                    }
                    return true;

                default:
                    handler.removeCallbacks(longPress);
                    return false;
            }
        }
    }

    // -------------------------------------------------------------- hide sheet

    private void openSheet() {
        if (sheet != null || !canDraw()) return;

        // The finger-up that opened the sheet lands on this scrim, so tapping the
        // scrim must not dismiss: only Cancel does. Otherwise the sheet flashes
        // open and closes the moment the long-press is released.
        FrameLayout scrim = new FrameLayout(ctx);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setClickable(true);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(Theme.card(ctx, 0xFF1C1C1C, 26));
        card.setOnClickListener(v -> { });   // swallow taps

        TextView title = Theme.display(ctx, "Hide reel counter for", 19f, Theme.TEXT);
        card.addView(title);

        TextView sub = Theme.label(ctx, "Counting keeps running. Only the pill is hidden.",
                12f, Theme.MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = dp(4);
        card.addView(sub, sp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rp.topMargin = dp(16);

        TextView hour = Theme.pillButton(ctx, "1 hr");
        hour.setOnClickListener(v -> hideFor(60L * 60L * 1000L, "Counter hidden for 1 hour"));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        hp.rightMargin = dp(8);
        row.addView(hour, hp);

        TextView day = Theme.pillButton(ctx, "1 day");
        day.setOnClickListener(v -> hideFor(24L * 60L * 60L * 1000L, "Counter hidden for 1 day"));
        row.addView(day, new LinearLayout.LayoutParams(0, WRAP, 1f));

        card.addView(row, rp);

        TextView cancel = Theme.ghostButton(ctx, "Cancel", Theme.MUTED);
        cancel.setOnClickListener(v -> dismissSheet());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cp.topMargin = dp(10);
        card.addView(cancel, cp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(MATCH, WRAP);
        clp.gravity = Gravity.BOTTOM;
        clp.leftMargin = dp(16);
        clp.rightMargin = dp(16);
        clp.bottomMargin = dp(28);
        scrim.addView(card, clp);

        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                MATCH, MATCH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.BOTTOM;

        try {
            wm.addView(scrim, wlp);
            sheet = scrim;
            android.util.Log.i("BubbleOverlay", "hide sheet shown");
        } catch (Throwable t) {
            android.util.Log.e("BubbleOverlay", "hide sheet failed", t);
            sheet = null;
        }
    }

    private void hideFor(long millis, String message) {
        ReelStore.setBubbleHiddenUntil(ctx, System.currentTimeMillis() + millis);
        dismissSheet();
        hide();
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
    }

    private void dismissSheet() {
        if (sheet != null && wm != null) {
            try {
                wm.removeView(sheet);
            } catch (Throwable ignored) {
            }
        }
        sheet = null;
    }

    private static final int MATCH = WindowManager.LayoutParams.MATCH_PARENT;
    private static final int WRAP = WindowManager.LayoutParams.WRAP_CONTENT;
}
