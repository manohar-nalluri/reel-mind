package com.brainpal.clone;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local-only storage.
 *
 * Two layers:
 *   - a prefs counter (fast path for "today", and the value the bubble shows)
 *   - an append-only event log (package, timestamp, view duration) that makes
 *     the analytics screens possible
 *
 * No network, no accounts, no sync. The event log is a TSV in filesDir; a few
 * hundred thousand lines would still be only a few MB, and we prune at 90 days.
 */
public final class ReelStore {

    private static final String PREFS = "reel_counter";
    private static final String K_COUNT = "count";
    private static final String K_DATE = "date";
    private static final String K_TOTAL = "total";
    private static final String K_LIMIT = "limit";
    private static final String K_BLOCK = "block_enabled";
    private static final String K_MIGRATED = "events_migrated_v1";
    private static final String K_BUBBLE = "bubble_enabled";
    private static final String K_BUBBLE_HIDDEN_UNTIL = "bubble_hidden_until";

    private static final String EVENTS = "reel_events.tsv";
    private static final int RETENTION_DAYS = 90;

    /**
     * The screens read the log several times per render. Keep it in memory and
     * invalidate on every write instead of re-parsing the file for each widget.
     */
    private static List<Event> cache;

    /** One counted reel. */
    public static final class Event {
        public final long time;
        public final String pkg;
        public final long durationMs;

        Event(long time, String pkg, long durationMs) {
            this.time = time;
            this.pkg = pkg;
            this.durationMs = durationMs;
        }
    }

    private ReelStore() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), EVENTS);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long dayKey(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ---------------------------------------------------------------- counters

    public static void rollover(Context c) {
        SharedPreferences sp = p(c);
        if (!today().equals(sp.getString(K_DATE, null))) {
            sp.edit().putString(K_DATE, today()).putInt(K_COUNT, 0).apply();
        }
    }

    public static int todayCount(Context c) {
        rollover(c);
        return p(c).getInt(K_COUNT, 0);
    }

    public static int totalCount(Context c) {
        return Math.max(p(c).getInt(K_TOTAL, 0), readEvents(c).size());
    }

    public static void resetToday(Context c) {
        p(c).edit().putString(K_DATE, today()).putInt(K_COUNT, 0).apply();
        // Drop today's rows so the analytics agree with the hero number.
        long start = startOfToday();
        List<Event> all = readEvents(c);
        List<Event> keep = new ArrayList<>();
        for (Event e : all) if (e.time < start) keep.add(e);
        writeEvents(c, keep);
    }

    /** Remove all history. */
    public static void resetAll(Context c) {
        p(c).edit().putString(K_DATE, today()).putInt(K_COUNT, 0).putInt(K_TOTAL, 0).apply();
        writeEvents(c, Collections.<Event>emptyList());
    }

    public static int getLimit(Context c) { return p(c).getInt(K_LIMIT, 100); }

    public static void setLimit(Context c, int v) {
        p(c).edit().putInt(K_LIMIT, Math.max(1, Math.min(999, v))).apply();
    }

    public static boolean blockEnabled(Context c) { return p(c).getBoolean(K_BLOCK, true); }

    public static void setBlockEnabled(Context c, boolean b) {
        p(c).edit().putBoolean(K_BLOCK, b).apply();
    }

    public static boolean bubbleEnabled(Context c) { return p(c).getBoolean(K_BUBBLE, true); }

    public static void setBubbleEnabled(Context c, boolean b) {
        p(c).edit().putBoolean(K_BUBBLE, b).apply();
    }

    /** Long-press on the pill hides it for an hour or a day, like the original. */
    public static long bubbleHiddenUntil(Context c) {
        return p(c).getLong(K_BUBBLE_HIDDEN_UNTIL, 0L);
    }

    public static void setBubbleHiddenUntil(Context c, long epochMillis) {
        p(c).edit().putLong(K_BUBBLE_HIDDEN_UNTIL, epochMillis).apply();
    }

    // ------------------------------------------------------------------ record

    /** Record one counted reel and return today's new total. */
    public static synchronized int record(Context c, String pkg, long durationMs) {
        rollover(c);
        migrate(c);

        SharedPreferences sp = p(c);
        int n = sp.getInt(K_COUNT, 0) + 1;
        int t = sp.getInt(K_TOTAL, 0) + 1;
        sp.edit().putInt(K_COUNT, n).putInt(K_TOTAL, t).apply();

        append(c, new Event(System.currentTimeMillis(), pkg, Math.max(0, durationMs)));
        return n;
    }

    /** Kept for call sites that don't know the app / duration. */
    public static int increment(Context c) {
        return record(c, "unknown", 0);
    }

    private static void append(Context c, Event e) {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file(c), true))) {
            w.write(e.time + "\t" + e.pkg + "\t" + e.durationMs);
            w.newLine();
        } catch (Throwable ignored) {
        }
        if (cache != null) cache.add(e);
    }

    /**
     * Older installs only have the prefs counter. Convert that number into
     * synthetic events so the new screens aren't empty on first launch.
     */
    private static void migrate(Context c) {
        try {
            SharedPreferences sp = p(c);
            if (sp.getBoolean(K_MIGRATED, false)) return;

            File f = file(c);
            if (!f.exists() || f.length() == 0) {
                int legacy = sp.getInt(K_COUNT, 0);
                long now = System.currentTimeMillis();
                List<Event> seed = new ArrayList<>();
                for (int i = 0; i < legacy; i++) {
                    seed.add(new Event(now - (legacy - i) * 15000L, "unknown", 0));
                }
                if (!seed.isEmpty()) writeEvents(c, seed);
            }
            sp.edit().putBoolean(K_MIGRATED, true).apply();
        } catch (Throwable ignored) {
        }
    }

    public static synchronized List<Event> readEvents(Context c) {
        migrate(c);
        if (cache != null) return cache;
        List<Event> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;

        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                try {
                    long time = Long.parseLong(parts[0]);
                    String pkg = parts[1];
                    long dur = parts.length > 2 ? Long.parseLong(parts[2]) : 0L;
                    out.add(new Event(time, pkg, dur));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        cache = out;
        return out;
    }

    private static void writeEvents(Context c, List<Event> events) {
        cache = new ArrayList<>(events);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file(c), false))) {
            for (Event e : events) {
                w.write(e.time + "\t" + e.pkg + "\t" + e.durationMs);
                w.newLine();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Called opportunistically; keeps the log bounded. */
    public static void prune(Context c) {
        List<Event> all = readEvents(c);
        long cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60L * 60L * 1000L;
        List<Event> keep = new ArrayList<>();
        for (Event e : all) if (e.time >= cutoff) keep.add(e);
        if (keep.size() != all.size()) writeEvents(c, keep);
    }

    // ------------------------------------------------------------------- stats

    /** Reels for each of the last 7 days, oldest first. Index 6 = today. */
    public static int[] last7Days(Context c) {
        int[] out = new int[7];
        long start = dayKey(startOfToday());
        for (Event e : readEvents(c)) {
            long d = dayKey(e.time);
            long diff = (start - d) / (24L * 60L * 60L * 1000L);
            if (diff >= 0 && diff < 7) out[6 - (int) diff]++;
        }
        return out;
    }

    /** Reels for each of the last N days, oldest first. */
    public static int[] lastDays(Context c, int n) {
        int[] out = new int[n];
        long start = dayKey(startOfToday());
        for (Event e : readEvents(c)) {
            long d = dayKey(e.time);
            long diff = (start - d) / (24L * 60L * 60L * 1000L);
            if (diff >= 0 && diff < n) out[n - 1 - (int) diff]++;
        }
        return out;
    }

    /** Reels per hour for today, 0..23. */
    public static int[] hourlyToday(Context c) {
        int[] out = new int[24];
        long start = startOfToday();
        Calendar cal = Calendar.getInstance();
        for (Event e : readEvents(c)) {
            if (e.time < start) continue;
            cal.setTimeInMillis(e.time);
            out[cal.get(Calendar.HOUR_OF_DAY)]++;
        }
        return out;
    }

    /** package -> reels, today only. Sorted by count desc. */
    public static Map<String, Integer> perAppToday(Context c) {
        Map<String, Integer> counts = new HashMap<>();
        long start = startOfToday();
        for (Event e : readEvents(c)) {
            if (e.time < start) continue;
            Integer v = counts.get(e.pkg);
            counts.put(e.pkg, v == null ? 1 : v + 1);
        }
        return sortDesc(counts);
    }

    /** package -> reels over the last N days. */
    public static Map<String, Integer> perApp(Context c, int days) {
        Map<String, Integer> counts = new HashMap<>();
        long start = dayKey(startOfToday()) - (days - 1) * 24L * 60L * 60L * 1000L;
        for (Event e : readEvents(c)) {
            if (e.time < start) continue;
            Integer v = counts.get(e.pkg);
            counts.put(e.pkg, v == null ? 1 : v + 1);
        }
        return sortDesc(counts);
    }

    private static Map<String, Integer> sortDesc(Map<String, Integer> in) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(in.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : list) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** Total watched time in ms over the last N days. 0 durations are ignored. */
    public static long watchedMillis(Context c, int days) {
        long start = dayKey(startOfToday()) - (days - 1) * 24L * 60L * 60L * 1000L;
        long total = 0;
        for (Event e : readEvents(c)) if (e.time >= start) total += Math.max(0, e.durationMs);
        return total;
    }

    /** Number of consecutive days ending today with at least one reel. */
    public static int streak(Context c) {
        int[] days = lastDays(c, 60);
        int s = 0;
        for (int i = days.length - 1; i >= 0; i--) {
            if (days[i] > 0) s++;
            else break;
        }
        return s;
    }

    /** Best day (index into last N, oldest first) and its count. */
    public static int bestDayIndex(Context c, int n) {
        int[] days = lastDays(c, n);
        int best = 0;
        for (int i = 1; i < n; i++) if (days[i] > days[best]) best = i;
        return best;
    }

    public static int worstDayIndex(Context c, int n) {
        int[] days = lastDays(c, n);
        int worst = 0;
        for (int i = 1; i < n; i++) if (days[i] < days[worst]) worst = i;
        return worst;
    }

    /** Label for an index in a last-N-days array. */
    public static String dayLabel(int daysAgo) {
        if (daysAgo == 0) return "Today";
        if (daysAgo == 1) return "Yesterday";
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("EEE d MMM", Locale.US).format(cal.getTime());
    }

    /** Human-friendly app name from a package. */
    public static String appName(String pkg) {
        switch (pkg) {
            case "com.instagram.android": return "Instagram";
            case "com.google.android.youtube": return "YouTube";
            case "com.snapchat.android": return "Snapchat";
            case "com.zhiliaoapp.musically": return "TikTok";
            case "com.ss.android.ugc.trill": return "TikTok";
            case "com.facebook.katana": return "Facebook";
            case "unknown": return "Earlier today";
            default:
                int dot = pkg.lastIndexOf('.');
                String s = dot >= 0 && dot < pkg.length() - 1 ? pkg.substring(dot + 1) : pkg;
                return s.isEmpty() ? "Unknown" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    public static int appColor(String pkg) {
        switch (pkg) {
            case "com.instagram.android": return 0xFFF5C46B;  // amber
            case "com.google.android.youtube": return 0xFFE86A5C; // red
            case "com.snapchat.android": return 0xFFE9E45B;    // yellow
            case "com.zhiliaoapp.musically":
            case "com.ss.android.ugc.trill": return 0xFF6FD3D6; // teal
            case "com.facebook.katana": return 0xFF7C9BF5;     // blue
            case "unknown": return 0xFF8A8A8A;
            default: return 0xFFB0B0B0;
        }
    }
}
