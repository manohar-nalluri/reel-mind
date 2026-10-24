package com.brainpal.clone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Design tokens lifted from the original's launch screen: pure black canvas,
 * Cabinet Grotesk display type, peach/amber fully-rounded pills with dark brown
 * label text, and a slightly lifted dark card for secondary content.
 */
public final class Theme {

    public static final int BG = 0xFF000000;
    public static final int CARD = 0xFF161616;
    public static final int CARD_2 = 0xFF1E1E1E;
    public static final int BORDER = 0xFF2A2A2A;

    public static final int AMBER = 0xFFF5C46B;
    public static final int AMBER_LIGHT = 0xFFFFD9A0;
    public static final int AMBER_DEEP = 0xFFF0B45C;
    public static final int ON_AMBER = 0xFF3D2A0B;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF8A8A8A;
    public static final int MUTED_2 = 0xFF5C5C5C;

    private Theme() {}

    // ------------------------------------------------------------------ metrics

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics()));
    }

    // ------------------------------------------------------------------- type

    public static Typeface display(Context c) { return font(c, R.font.cabinet_grotesk_extrabold); }
    public static Typeface black(Context c) { return font(c, R.font.cabinet_grotesk_black); }
    public static Typeface bold(Context c) { return font(c, R.font.cabinet_grotesk_bold); }
    public static Typeface medium(Context c) { return font(c, R.font.cabinet_grotesk_medium); }
    public static Typeface body(Context c) { return font(c, R.font.instrument_sans_regular); }

    private static Typeface font(Context c, int res) {
        try {
            return c.getResources().getFont(res);
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }

    // ---------------------------------------------------------------- surfaces

    /** Fully-rounded amber pill, with a subtle vertical gradient like the original. */
    public static GradientDrawable amberPill(Context c) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{AMBER_LIGHT, AMBER_DEEP});
        g.setCornerRadius(dp(c, 100));
        return g;
    }

    public static GradientDrawable amberPillPressed(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFD9A24E);
        g.setCornerRadius(dp(c, 100));
        return g;
    }

    public static GradientDrawable darkPill(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 100));
        return g;
    }

    public static GradientDrawable card(Context c) {
        return card(c, CARD, 24);
    }

    public static GradientDrawable card(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable outlined(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(c, 1), BORDER);
        g.setCornerRadius(dp(c, 100));
        return g;
    }

    // ------------------------------------------------------------------ widgets

    public static TextView label(Context c, String text, float sizeSp, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        v.setTypeface(medium(c));
        v.setIncludeFontPadding(false);
        return v;
    }

    public static TextView display(Context c, String text, float sizeSp, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        v.setTypeface(display(c));
        v.setIncludeFontPadding(false);
        v.setLetterSpacing(-0.02f);
        return v;
    }

    /** Amber CTA pill, text colour matching the original (dark brown on peach). */
    public static TextView pillButton(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(16f);
        v.setTypeface(bold(c));
        v.setTextColor(ON_AMBER);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12));

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, amberPillPressed(c));
        bg.addState(new int[]{}, amberPill(c));
        v.setBackground(bg);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    /** Neutral ghost pill used for secondary actions. */
    public static TextView ghostButton(Context c, String text, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(15f);
        v.setTypeface(medium(c));
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));

        StateListDrawable bg = new StateListDrawable();
        GradientDrawable pressed = darkPill(c, 0xFF262626);
        bg.addState(new int[]{android.R.attr.state_pressed}, pressed);
        bg.addState(new int[]{}, outlined(c));
        v.setBackground(bg);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }
}
