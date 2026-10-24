package com.brainpal.clone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Single-activity shell: onboarding -> home -> analytics.
 *
 * Everything is rendered synchronously from local storage, so there are no
 * loading states anywhere: opening a screen computes its numbers on the spot.
 */
public class MainActivity extends Activity {

    private FrameLayout root;
    private ScrollView activeScroll;
    private boolean scrollToTop = false;
    private boolean showingOnboarding = false;
    private int tab = 0;            // 0 = home, 1 = analytics
    private int statsRange = 7;     // days
    private boolean hintOpen = false;
    private boolean skipSetup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Theme.BG);
        w.setNavigationBarColor(Theme.BG);

        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        // Rebuilding the view tree used to reset every ScrollView to the top, so
        // tapping -/+ or a toggle threw you back to the header. Remember where the
        // user was and restore it after the new tree is laid out.
        boolean wasOnboarding = showingOnboarding;
        int restoreY = (!scrollToTop && !wasOnboarding && activeScroll != null)
                ? activeScroll.getScrollY() : 0;
        scrollToTop = false;

        root.removeAllViews();
        activeScroll = null;

        boolean ready = accessibilityOn() && Settings.canDrawOverlays(this);
        showingOnboarding = !(ready || skipSetup);
        if (!showingOnboarding) {
            root.addView(shell());
        } else {
            root.addView(onboarding());
        }

        if (restoreY > 0 && activeScroll != null) {
            // Restore in onPreDraw, i.e. after layout but before the frame is
            // drawn. A post() ran one frame later, which showed the top of the
            // screen for a split second.
            final ScrollView target = activeScroll;
            final int y = restoreY;
            target.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            ViewTreeObserver vto = target.getViewTreeObserver();
                            if (vto.isAlive()) vto.removeOnPreDrawListener(this);
                            target.scrollTo(0, y);
                            return true;
                        }
                    });
        }
    }

    private boolean accessibilityOn() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================== onboarding

    private View onboarding() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.BG);

        activeScroll = scroll;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(56), dp(24), dp(40));
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));

        // Spacer pushes the heading to roughly the same position as the original.
        col.addView(space(70));

        TextView kicker = Theme.label(this, "REELMIND", 12f, Theme.AMBER);
        kicker.setLetterSpacing(0.18f);
        kicker.setGravity(Gravity.CENTER);
        col.addView(kicker);

        TextView title = Theme.display(this, "Enable permissions to start counting reels", 38f, Theme.TEXT);
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tp.topMargin = dp(18);
        col.addView(title, tp);

        TextView sub = Theme.label(this, "Two permissions, no account, nothing leaves your phone.", 14f, Theme.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = dp(14);
        sp.leftMargin = dp(8);
        sp.rightMargin = dp(8);
        col.addView(sub, sp);

        col.addView(space(44));

        col.addView(permissionRow(
                "Display over other apps",
                "To show the floating reels count",
                Settings.canDrawOverlays(this),
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())))));

        col.addView(space(26));

        col.addView(permissionRow(
                "Accessibility access",
                "To count reels in Instagram, YouTube, Snapchat and TikTok",
                accessibilityOn(),
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        col.addView(space(36));

        // Bottom hint pill, matching the original's "Why should I give this permission?" card.
        LinearLayout hint = new LinearLayout(this);
        hint.setOrientation(LinearLayout.VERTICAL);
        hint.setPadding(dp(18), dp(16), dp(16), dp(16));
        hint.setBackground(Theme.card(this, Theme.CARD_2, 28));

        LinearLayout hintRow = new LinearLayout(this);
        hintRow.setOrientation(LinearLayout.HORIZONTAL);
        hintRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText("✓");
        badge.setTextSize(14f);
        badge.setTypeface(Theme.bold(this));
        badge.setTextColor(Theme.ON_AMBER);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Theme.darkPill(this, Theme.AMBER_LIGHT));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(32), dp(32));
        bp.rightMargin = dp(12);
        hintRow.addView(badge, bp);

        TextView hintTitle = Theme.label(this, "Why should I give this permission?", 15f, Theme.TEXT);
        hintTitle.setTypeface(Theme.bold(this));
        hintRow.addView(hintTitle, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView chevron = Theme.label(this, hintOpen ? "⌄" : "›", 18f, Theme.MUTED);
        hintRow.addView(chevron);

        hint.addView(hintRow);

        TextView why = Theme.label(this, "", 13f, Theme.MUTED);
        why.setLineSpacing(dp(3), 1f);
        why.setPadding(0, dp(10), 0, 0);
        why.setText("The overlay draws the small counter at the top of the screen. "
                + "Accessibility only looks for the reels viewer in those four apps — "
                + "it never reads messages, passwords or anything else. "
                + "There is no internet permission, so nothing can be uploaded.");
        why.setVisibility(hintOpen ? View.VISIBLE : View.GONE);
        hint.addView(why);

        hint.setClickable(true);
        hint.setOnClickListener(v -> {
            hintOpen = !hintOpen;
            why.setVisibility(hintOpen ? View.VISIBLE : View.GONE);
            chevron.setText(hintOpen ? "⌄" : "›");
        });

        col.addView(hint);

        col.addView(space(24));

        TextView cont = Theme.pillButton(this, accessibilityOn() && Settings.canDrawOverlays(this)
                ? "Start counting" : "Maybe later");
        cont.setPadding(dp(22), dp(15), dp(22), dp(15));
        cont.setOnClickListener(v -> {
            skipSetup = true;
            tab = 0;
            render();
        });
        col.addView(cont, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView fine = Theme.label(this,
                "No sign-in  ·  No network  ·  Data stays on this device", 12f, Theme.MUTED_2);
        fine.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(MATCH, WRAP);
        fp.topMargin = dp(18);
        col.addView(fine, fp);

        return scroll;
    }

    private View permissionRow(String title, String subtitle, boolean granted, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView t = Theme.label(this, title, 20f, Theme.TEXT);
        t.setTypeface(Theme.bold(this));
        TextView s = Theme.label(this, subtitle, 14f, Theme.MUTED);
        s.setLineSpacing(dp(2), 1f);

        text.addView(t);
        LinearLayout.LayoutParams tsp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tsp.topMargin = dp(3);
        text.addView(s, tsp);

        row.addView(text, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView btn = Theme.pillButton(this, granted ? "On ✓" : "Allow");
        if (granted) {
            btn.setTextColor(Theme.MUTED);
            btn.setBackground(Theme.outlined(this));
            btn.setPadding(dp(18), dp(12), dp(18), dp(12));
        } else {
            btn.setOnClickListener(v -> action.run());
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(WRAP, WRAP);
        bp.leftMargin = dp(14);
        row.addView(btn, bp);

        return row;
    }

    // ================================================================== shell

    private View shell() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Theme.BG);

        View content = tab == 0 ? home() : analytics();
        frame.addView(content);

        // Floating bottom nav.
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackground(Theme.card(this, Theme.CARD_2, 100));
        nav.setElevation(dp(12));

        nav.addView(navItem("Home", 0));
        nav.addView(navItem("Analytics", 1));

        // Wrap the nav around its labels instead of a fixed 196dp: "Analytics" needs
        // more room than "Home", and a fixed width made the selected label wrap and
        // drop a line below the pill.
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(WRAP, WRAP);
        np.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        np.bottomMargin = dp(22);
        frame.addView(nav, np);

        return frame;
    }

    private View navItem(String text, int index) {
        TextView v = Theme.label(this, text, 14f, index == tab ? Theme.ON_AMBER : Theme.MUTED);
        v.setTypeface(index == tab ? Theme.bold(this) : Theme.medium(this));
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setMaxLines(1);
        v.setPadding(dp(22), dp(11), dp(22), dp(11));
        if (index == tab) v.setBackground(Theme.amberPill(this));
        v.setClickable(true);
        v.setOnClickListener(x -> {
            tab = index;
            scrollToTop = true;
            render();
        });
        v.setLayoutParams(new LinearLayout.LayoutParams(WRAP, WRAP));
        return v;
    }

    // =================================================================== home

    private View home() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        scroll.setFillViewport(true);
        activeScroll = scroll;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(44), dp(22), dp(130));
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));

        // Header: greeting + streak chip.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout greet = new LinearLayout(this);
        greet.setOrientation(LinearLayout.VERTICAL);
        TextView kicker = Theme.label(this, timeGreeting().toUpperCase(Locale.US), 11f, Theme.MUTED);
        kicker.setLetterSpacing(0.16f);
        TextView hello = Theme.display(this, "Manu", 30f, Theme.TEXT);
        greet.addView(kicker);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hp.topMargin = dp(4);
        greet.addView(hello, hp);
        header.addView(greet, new LinearLayout.LayoutParams(0, WRAP, 1f));

        int streak = ReelStore.streak(this);
        if (streak > 0) {
            TextView chip = Theme.label(this, streak + " day streak", 12f, Theme.AMBER);
            chip.setTypeface(Theme.bold(this));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(Theme.darkPill(this, 0xFF231A0C));
            header.addView(chip);
        }
        col.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        col.addView(space(18));

        RingView ring = new RingView(this);
        ring.set(ReelStore.todayCount(this), ReelStore.getLimit(this));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(236), dp(236));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(ring, rp);

        col.addView(space(20));

        // Three stat cards.
        int[] week = ReelStore.last7Days(this);
        int weekTotal = sum(week);
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);

        stats.addView(statCard("All time", String.valueOf(ReelStore.totalCount(this)), Theme.TEXT));
        stats.addView(hspace(10));
        stats.addView(statCard("7-day avg", String.valueOf(Math.round(weekTotal / 7f)), Theme.TEXT));
        stats.addView(hspace(10));
        stats.addView(statCard("This week", String.valueOf(weekTotal), Theme.AMBER));

        col.addView(stats, new LinearLayout.LayoutParams(MATCH, WRAP));

        col.addView(space(26));

        // Last 7 days chart.
        col.addView(sectionHeader("Last 7 days", "avg " + Math.round(weekTotal / 7f) + "/day"));
        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setPadding(dp(16), dp(16), dp(16), dp(12));
        chartCard.setBackground(Theme.card(this));

        BarChartView chart = new BarChartView(this);
        chart.set(week, weekLabels(), 6, true);
        chartCard.addView(chart, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(chartCard);

        col.addView(space(26));

        // Per-app split.
        col.addView(sectionHeader("Where they came from", "today"));
        col.addView(appBreakdown(ReelStore.perAppToday(this), ReelStore.todayCount(this)));

        col.addView(space(26));

        // Controls.
        col.addView(sectionHeader("Controls", "local only"));
        col.addView(controlsCard());

        col.addView(space(20));

        TextView footnote = Theme.label(this,
                "Counted by watching the reels viewer. No account, no uploads.",
                12f, Theme.MUTED_2);
        footnote.setGravity(Gravity.CENTER);
        footnote.setPadding(0, dp(4), 0, 0);
        col.addView(footnote);

        return scroll;
    }

    private View statCard(String label, String value, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Theme.card(this, Theme.CARD, 20));

        TextView v = Theme.display(this, value, 26f, color);
        TextView l = Theme.label(this, label, 11f, Theme.MUTED);

        card.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(4);
        card.addView(l, lp);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        card.setLayoutParams(p);
        return card;
    }

    private View sectionHeader(String title, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView t = Theme.display(this, title, 19f, Theme.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView r = Theme.label(this, right, 11f, Theme.MUTED_2);
        row.addView(r);
        return row;
    }

    private View appBreakdown(Map<String, Integer> counts, int total) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(6), dp(16), dp(10));
        card.setBackground(Theme.card(this));

        if (counts.isEmpty()) {
            TextView empty = Theme.label(this, "Nothing counted yet. Open Reels and scroll — "
                    + "the number appears here straight away.", 13f, Theme.MUTED);
            empty.setLineSpacing(dp(3), 1f);
            empty.setPadding(0, dp(14), 0, dp(14));
            card.addView(empty);
            return card;
        }

        int i = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (i++ >= 5) break;
            card.addView(appRow(e.getKey(), e.getValue(), total));
        }
        return card;
    }

    private View appRow(String pkg, int count, int total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(Theme.darkPill(this, ReelStore.appColor(pkg)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotParams.rightMargin = dp(10);
        top.addView(dot, dotParams);

        TextView name = Theme.label(this, ReelStore.appName(pkg), 14f, Theme.TEXT);
        name.setTypeface(Theme.medium(this));
        top.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));

        int pct = total > 0 ? Math.round(count * 100f / total) : 0;
        TextView val = Theme.label(this, count + "  ·  " + pct + "%", 12f, Theme.MUTED);
        top.addView(val);

        row.addView(top);

        // Thin progress bar.
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Theme.darkPill(this, 0xFF242424));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(MATCH, dp(4));
        tp.topMargin = dp(8);

        View filled = new View(this);
        filled.setBackground(Theme.darkPill(this, ReelStore.appColor(pkg)));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, MATCH, Math.max(0.01f,
                total > 0 ? count / (float) Math.max(total, 1) : 0f));
        track.addView(filled, fp);
        row.addView(track, tp);

        return row;
    }

    private View controlsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(8));
        card.setBackground(Theme.card(this));

        // Daily limit stepper.
        LinearLayout limitRow = new LinearLayout(this);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        limitRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout lt = new LinearLayout(this);
        lt.setOrientation(LinearLayout.VERTICAL);
        TextView l1 = Theme.label(this, "Daily limit", 15f, Theme.TEXT);
        l1.setTypeface(Theme.bold(this));
        TextView l2 = Theme.label(this, "Block after this many reels", 12f, Theme.MUTED);
        lt.addView(l1);
        LinearLayout.LayoutParams l2p = new LinearLayout.LayoutParams(WRAP, WRAP);
        l2p.topMargin = dp(2);
        lt.addView(l2, l2p);
        limitRow.addView(lt, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView minus = stepButton("−");
        minus.setOnClickListener(v -> {
            ReelStore.setLimit(this, ReelStore.getLimit(this) - 5);
            render();
        });
        limitRow.addView(minus);

        TextView limitValue = Theme.display(this, String.valueOf(ReelStore.getLimit(this)), 20f, Theme.TEXT);
        limitValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(dp(52), WRAP);
        limitRow.addView(limitValue, lvp);

        TextView plus = stepButton("+");
        plus.setOnClickListener(v -> {
            ReelStore.setLimit(this, ReelStore.getLimit(this) + 5);
            render();
        });
        limitRow.addView(plus);

        card.addView(limitRow, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Toggles.
        card.addView(divider());
        card.addView(toggleRow("Block at the limit", "Show the wall and leave the app",
                ReelStore.blockEnabled(this), on -> {
                    ReelStore.setBlockEnabled(this, on);
                    return true;
                }));

        card.addView(divider());
        card.addView(toggleRow("Floating counter", "Show the count over other apps",
                ReelStore.bubbleEnabled(this), on -> {
                    ReelStore.setBubbleEnabled(this, on);
                    ReelWatcher.refreshBubble(this);
                    return true;
                }));

        long hiddenUntil = ReelStore.bubbleHiddenUntil(this);
        if (hiddenUntil > System.currentTimeMillis()) {
            card.addView(divider());
            card.addView(hiddenRow(hiddenUntil));
        }

        card.addView(divider());

        // Permission shortcuts.
        LinearLayout perms = new LinearLayout(this);
        perms.setOrientation(LinearLayout.HORIZONTAL);
        perms.setPadding(0, dp(12), 0, dp(10));

        TextView permBtn = Theme.ghostButton(this, "Permissions", Theme.TEXT);
        permBtn.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        perms.addView(permBtn);

        perms.addView(hspace(10));

        TextView reset = Theme.ghostButton(this, "Reset today", Theme.MUTED);
        reset.setOnClickListener(v -> {
            ReelStore.resetToday(this);
            ReelWatcher.refreshBubble(this);
            render();
        });
        perms.addView(reset);

        card.addView(perms, new LinearLayout.LayoutParams(MATCH, WRAP));

        return card;
    }

    /** Shown after a long-press "hide for 1 hr / 1 day" so there is a way back. */
    private View hiddenRow(long hiddenUntil) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView t = Theme.label(this, "Counter hidden", 15f, Theme.TEXT);
        t.setTypeface(Theme.bold(this));

        String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date(hiddenUntil));
        TextView sub = Theme.label(this, "Back at " + time, 12f, Theme.MUTED);

        text.addView(t);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(WRAP, WRAP);
        sp.topMargin = dp(2);
        text.addView(sub, sp);

        row.addView(text, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView show = Theme.pillButton(this, "Show now");
        show.setPadding(dp(16), dp(9), dp(16), dp(9));
        show.setOnClickListener(v -> {
            ReelStore.setBubbleHiddenUntil(this, 0L);
            ReelWatcher.refreshBubble(this);
            render();
        });
        row.addView(show);

        return row;
    }

    private TextView stepButton(String text) {
        TextView v = Theme.label(this, text, 18f, Theme.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.darkPill(this, 0xFF262626));
        v.setClickable(true);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return v;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFF232323);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, dp(1));
        p.topMargin = dp(12);
        v.setLayoutParams(p);
        return v;
    }

    private interface ToggleAction { boolean run(boolean on); }

    private View toggleRow(String title, String subtitle, boolean on, ToggleAction action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Theme.label(this, title, 15f, Theme.TEXT);
        t.setTypeface(Theme.medium(this));
        TextView s = Theme.label(this, subtitle, 12f, Theme.MUTED);
        text.addView(t);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(WRAP, WRAP);
        sp.topMargin = dp(2);
        text.addView(s, sp);

        row.addView(text, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView pill = new TextView(this);
        pill.setText(on ? "ON" : "OFF");
        pill.setTextSize(11f);
        pill.setTypeface(Theme.bold(this));
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), dp(8), dp(16), dp(8));
        pill.setTextColor(on ? Theme.ON_AMBER : Theme.MUTED);
        pill.setBackground(on ? Theme.amberPill(this) : Theme.darkPill(this, 0xFF262626));

        row.addView(pill);

        row.setClickable(true);
        row.setOnClickListener(v -> {
            boolean next = !on;
            if (action.run(next)) render();
        });

        return row;
    }

    // ============================================================== analytics

    private View analytics() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        scroll.setFillViewport(true);
        activeScroll = scroll;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(44), dp(22), dp(130));
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));

        TextView kicker = Theme.label(this, "LOCAL DATA ONLY", 11f, Theme.MUTED);
        kicker.setLetterSpacing(0.16f);
        col.addView(kicker);

        TextView title = Theme.display(this, "Analytics", 32f, Theme.TEXT);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tp.topMargin = dp(4);
        col.addView(title, tp);

        col.addView(space(18));

        // Range segmented control.
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(dp(5), dp(5), dp(5), dp(5));
        seg.setBackground(Theme.card(this, Theme.CARD, 100));
        seg.addView(segItem("Today", 1));
        seg.addView(segItem("7 days", 7));
        seg.addView(segItem("30 days", 30));
        col.addView(seg, new LinearLayout.LayoutParams(MATCH, WRAP));

        col.addView(space(22));

        int[] days = ReelStore.lastDays(this, statsRange);
        int total = sum(days);
        int avg = Math.round(total / (float) statsRange);
        long watched = ReelStore.watchedMillis(this, statsRange);
        int best = ReelStore.bestDayIndex(this, statsRange);
        int bestCount = days.length > 0 ? days[best] : 0;

        // Hero total.
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(Theme.card(this));

        TextView totalNumber = Theme.display(this, String.valueOf(total), 58f, Theme.TEXT);
        hero.addView(totalNumber);

        TextView totalLabel = Theme.label(this,
                total == 1 ? "reel in the last " + rangeLabel() : "reels in the last " + rangeLabel(),
                13f, Theme.MUTED);
        hero.addView(totalLabel);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(16), 0, 0);
        chips.addView(metricChip("Avg / day", String.valueOf(avg)));
        chips.addView(hspace(8));
        chips.addView(metricChip("Watched", formatDuration(watched)));
        chips.addView(hspace(8));
        chips.addView(metricChip("Best day", bestCount + ""));
        hero.addView(chips, new LinearLayout.LayoutParams(MATCH, WRAP));

        col.addView(hero);

        col.addView(space(24));

        // Chart: hourly when looking at today, daily otherwise.
        if (statsRange == 1) {
            int[] hours = ReelStore.hourlyToday(this);
            col.addView(sectionHeader("By hour", "today"));
            col.addView(chartCard(hours, hourLabels(), peakIndex(hours), false));
        } else {
            col.addView(sectionHeader("By day", "last " + statsRange + " days"));
            int[] trimmed = days;
            String[] labels = new String[days.length];
            for (int i = 0; i < days.length; i++) {
                int ago = days.length - 1 - i;
                if (statsRange <= 7) {
                    labels[i] = ago == 0 ? "Today" : weekdayShort(ago);
                } else {
                    labels[i] = (ago % 5 == 0 || ago == 0) ? String.valueOf(ago == 0 ? "now" : ago + "d") : "";
                }
            }
            col.addView(chartCard(trimmed, labels, days.length - 1, statsRange <= 7));
        }

        col.addView(space(24));

        // Per-app split for the range.
        Map<String, Integer> apps = ReelStore.perApp(this, statsRange);
        col.addView(sectionHeader("By app", "share of reels"));
        col.addView(appBreakdown(apps, total));

        col.addView(space(24));

        // Highlights.
        col.addView(sectionHeader("Highlights", "all local"));
        col.addView(highlightsCard(days, best, bestCount, avg, watched));

        return scroll;
    }

    private View segItem(String text, int days) {
        boolean on = statsRange == days;
        TextView v = Theme.label(this, text, 12.5f, on ? Theme.ON_AMBER : Theme.MUTED);
        v.setTypeface(on ? Theme.bold(this) : Theme.medium(this));
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setMaxLines(1);
        v.setPadding(dp(6), dp(10), dp(6), dp(10));
        if (on) v.setBackground(Theme.amberPill(this));
        v.setClickable(true);
        v.setOnClickListener(x -> {
            statsRange = days;
            render();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        v.setLayoutParams(p);
        return v;
    }

    private View metricChip(String label, String value) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setPadding(dp(12), dp(10), dp(12), dp(10));
        chip.setBackground(Theme.card(this, Theme.CARD_2, 16));

        TextView v = Theme.display(this, value, 18f, Theme.TEXT);
        TextView l = Theme.label(this, label, 10f, Theme.MUTED);
        chip.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(2);
        chip.addView(l, lp);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        chip.setLayoutParams(p);
        return chip;
    }

    private View chartCard(int[] values, String[] labels, int highlight, boolean showValues) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(16), dp(14), dp(10));
        card.setBackground(Theme.card(this));

        BarChartView chart = new BarChartView(this);
        chart.set(values, labels, highlight, showValues);
        card.addView(chart, new LinearLayout.LayoutParams(MATCH, WRAP));
        return card;
    }

    private View highlightsCard(int[] days, int best, int bestCount, int avg, long watched) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(12));
        card.setBackground(Theme.card(this));

        int bestAgo = days.length - 1 - best;
        card.addView(highlightRow("Best day",
                ReelStore.dayLabel(bestAgo) + "  ·  " + bestCount + " reels"));

        int[] week = ReelStore.last7Days(this);
        int worst = ReelStore.worstDayIndex(this, 7);
        card.addView(divider());
        card.addView(highlightRow("Quietest day",
                ReelStore.dayLabel(6 - worst) + "  ·  " + week[worst] + " reels"));

        card.addView(divider());
        card.addView(highlightRow("Streak", ReelStore.streak(this) + " days in a row"));

        card.addView(divider());
        card.addView(highlightRow("Average session",
                avg > 0 ? formatDuration(watched / Math.max(1, avg)) : "—"));

        card.addView(divider());
        card.addView(highlightRow("Storage",
                "Local only  ·  " + formatBytes(this)));

        return card;
    }

    private View highlightRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView l = Theme.label(this, label, 13f, Theme.MUTED);
        row.addView(l, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView v = Theme.label(this, value, 13f, Theme.TEXT);
        v.setTypeface(Theme.medium(this));
        row.addView(v);
        return row;
    }

    private String rangeLabel() {
        switch (statsRange) {
            case 1: return "day";
            case 7: return "7 days";
            default: return statsRange + " days";
        }
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "—";
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return totalSec + "s";
    }

    private String formatBytes(android.content.Context c) {
        java.io.File[] files = c.getFilesDir().listFiles();
        long bytes = 0;
        if (files != null) for (java.io.File f : files) bytes += f.length();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    private String[] hourLabels() {
        String[] out = new String[24];
        for (int i = 0; i < 24; i++) {
            if (i % 6 == 0) {
                out[i] = i == 0 ? "12a" : i == 12 ? "12p" : i + (i < 12 ? "a" : "p");
            } else {
                out[i] = "";
            }
        }
        return out;
    }

    private String[] weekLabels() {
        String[] out = new String[7];
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.US);
        for (int i = 0; i < 7; i++) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -(6 - i));
            out[i] = i == 6 ? "Today" : f.format(cal.getTime());
        }
        return out;
    }

    private String weekdayShort(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("EEE", Locale.US).format(cal.getTime());
    }

    private int peakIndex(int[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) if (values[i] > values[best]) best = i;
        return best;
    }

    private int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    private String timeGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "Late night";
        if (h < 12) return "Good morning";
        if (h < 17) return "Good afternoon";
        if (h < 22) return "Good evening";
        return "Late night";
    }

    // ================================================================== helpers

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private int dp(int v) { return Theme.dp(this, v); }

    /** Vertical gap. */
    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    /** Horizontal gap inside a row. */
    private View hspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), 1));
        return v;
    }
}
