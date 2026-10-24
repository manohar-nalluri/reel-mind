package com.brainpal.clone;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The "limit reached" experience, in the same visual language as the rest of the
 * app: black canvas, Cabinet Grotesk, cream brain glyph, amber pill.
 *
 * Two windows:
 *   - a centred wall shown the moment the limit is hit (and while the user is
 *     being sent home),
 *   - a small bottom bar that stays for a few seconds afterwards so the kick-out
 *     has an explanation instead of silently dumping the user on the launcher.
 */
public final class BlockOverlay {

    private static final long BAR_MS = 4500L;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View wall;
    private View bar;

    public BlockOverlay(Context ctx) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canDraw() {
        return Settings.canDrawOverlays(ctx);
    }

    // ------------------------------------------------------------------- wall

    public void show(int count, int limit) {
        if (!canDraw() || wm == null) return;
        // A fresh block cancels any pending wall/bar timers from the previous one.
        handler.removeCallbacksAndMessages(null);
        hideWall();
        hideBar();

        // No dim behind the card: the reel stays visible, only the card is drawn.
        // The window is still full-screen and clickable so taps cannot reach the
        // app underneath while the wall is up.
        FrameLayout scrim = new FrameLayout(ctx);
        scrim.setClickable(true);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(26), dp(26), dp(26), dp(22));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141414);
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1), 0x26FFFFFF);
        card.setBackground(bg);

        ImageView brain = new ImageView(ctx);
        brain.setImageResource(R.drawable.br_ic_brain);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(29));
        bp.bottomMargin = dp(14);
        card.addView(brain, bp);

        TextView kicker = Theme.label(ctx, "DAILY LIMIT", 11f, Theme.AMBER);
        kicker.setLetterSpacing(0.16f);
        card.addView(kicker);

        TextView big = Theme.display(ctx, String.valueOf(count), 66f, Theme.TEXT);
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bigp = new LinearLayout.LayoutParams(WRAP, WRAP);
        bigp.topMargin = dp(2);
        card.addView(big, bigp);

        TextView of = Theme.label(ctx, "reels today · limit " + limit, 13f, Theme.MUTED);
        of.setGravity(Gravity.CENTER);
        card.addView(of);

        TextView headline = Theme.display(ctx, "Take a break.", 28f, Theme.TEXT);
        headline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hp.topMargin = dp(20);
        card.addView(headline, hp);

        TextView sub = Theme.label(ctx,
                "You've hit today's limit. Counting is paused until midnight.",
                13f, Theme.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(WRAP, WRAP);
        sp.topMargin = dp(6);
        sp.leftMargin = dp(6);
        sp.rightMargin = dp(6);
        card.addView(sub, sp);

        TextView gotIt = Theme.pillButton(ctx, "Got it");
        gotIt.setOnClickListener(v -> {
            hideWall();
            showBar();
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(MATCH, WRAP);
        gp.topMargin = dp(20);
        card.addView(gotIt, gp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(MATCH, WRAP);
        clp.gravity = Gravity.CENTER;
        clp.leftMargin = dp(28);
        clp.rightMargin = dp(28);
        scrim.addView(card, clp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                MATCH, MATCH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        try {
            wm.addView(scrim, lp);
            wall = scrim;
            android.util.Log.i("BlockOverlay",
                    "wall shown (count=" + count + " limit=" + limit + ")");
            // Stays until the user taps "Got it" — no auto-dismiss.
        } catch (Throwable t) {
            android.util.Log.e("BlockOverlay", "wall addView failed", t);
            wall = null;
        }
    }

    private void hideWall() {
        if (wall != null && wm != null) {
            try {
                wm.removeView(wall);
            } catch (Throwable ignored) {
            }
        }
        wall = null;
    }

    // ------------------------------------------------------------- kick-out bar

    /** Small bottom bar explaining why the app closed itself. */
    private void showBar() {
        if (bar != null || !canDraw() || wm == null) return;

        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), dp(11), dp(18), dp(11));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C1C);
        bg.setCornerRadius(dp(100));
        bg.setStroke(dp(1), 0x33FFFFFF);
        pill.setBackground(bg);

        ImageView brain = new ImageView(ctx);
        brain.setImageResource(R.drawable.br_ic_brain);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(20), dp(15));
        bp.rightMargin = dp(9);
        pill.addView(brain, bp);

        TextView text = Theme.label(ctx,
                "Daily limit reached — sent you home", 13f, Theme.TEXT);
        text.setSingleLine(true);
        pill.addView(text);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WRAP, WRAP,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(84);

        try {
            wm.addView(pill, lp);
            bar = pill;
            handler.postDelayed(this::hideBar, BAR_MS);
        } catch (Throwable t) {
            android.util.Log.e("BlockOverlay", "bar addView failed", t);
            bar = null;
        }
    }

    private void hideBar() {
        if (bar != null && wm != null) {
            try {
                wm.removeView(bar);
            } catch (Throwable ignored) {
            }
        }
        bar = null;
    }

    // ------------------------------------------------------------------ common

    public void hide() {
        handler.removeCallbacksAndMessages(null);
        hideWall();
        hideBar();
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static final int MATCH = WindowManager.LayoutParams.MATCH_PARENT;
    private static final int WRAP = WindowManager.LayoutParams.WRAP_CONTENT;
}
