package com.brainpal.clone;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The whole feature.
 *
 * Detection is copied from the original's behaviour: a supported app, a view-id
 * that only exists while the reels viewer is open, no modal covering the reel,
 * and a *changed* reel identity. The identity is the important part — the
 * original does not count scroll events and neither do we.
 *
 *   Instagram  hash(contentDescription of clips_video_container) + caption
 *   YouTube    longest stable text inside reel_player_page_content
 *   TikTok     :id/title + :id/desc
 *   Snapchat   best text inside spotlight_container
 *
 * A new identity must also be seen twice before it counts, so a one-off UI
 * re-render cannot inflate the number.
 */
public class ReelWatcher extends AccessibilityService {

    private static final String TAG = "ReelWatcher";
    private static final long DEBOUNCE_MS = 450;
    private static final long HIDE_DELAY_MS = 1200;

    private static final String IG = "com.instagram.android";
    private static final String YT = "com.google.android.youtube";
    private static final String SNAP = "com.snapchat.android";
    private static final String TT = "com.zhiliaoapp.musically";
    private static final String TT_INTL = "com.ss.android.ugc.trill";

    private static final Map<String, String> MARKERS = new HashMap<>();
    private static final Map<String, String[]> SUPPRESSORS = new HashMap<>();

    /** Ad CTA labels that change while watching; never use them as identity. */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "shop now", "visit site", "install", "order now", "learn more",
            "sign up", "get offer", "download now", "ad", "sponsored"));

    static {
        MARKERS.put(IG, "com.instagram.android:id/clips_viewer_view_pager");
        MARKERS.put(YT, "com.google.android.youtube:id/reel_recycler");
        MARKERS.put(SNAP, "com.snapchat.android:id/spotlight_container");
        MARKERS.put(TT, "com.zhiliaoapp.musically:id/view_rootview");
        MARKERS.put(TT_INTL, "com.ss.android.ugc.trill:id/view_rootview");

        SUPPRESSORS.put(IG, new String[]{
                "com.instagram.android:id/layout_comment_thread_edittext",
                "com.instagram.android:id/igds_snackbar"});
        SUPPRESSORS.put(YT, new String[]{
                "com.google.android.youtube:id/app_engagement_panel",
                "com.google.android.youtube:id/design_bottom_sheet"});
    }

    private static ReelWatcher instance;

    private final Map<String, String> lastIdentity = new HashMap<>();
    private final Map<String, String> pendingIdentity = new HashMap<>();
    private final Map<String, Long> lastSeenAt = new HashMap<>();

    private long lastCountAt = 0L;
    private boolean reelsOnScreen = false;

    private BlockOverlay overlay;
    private BubbleOverlay bubble;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideBubble = () -> {
        reelsOnScreen = false;
        if (bubble != null) bubble.hide();
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        overlay = new BlockOverlay(this);
        bubble = new BubbleOverlay(this);
        ReelStore.prune(this);
        Log.i(TAG, "accessibility service connected");
    }

    /** Called by the home screen after toggling the pill or resetting today. */
    public static void refreshBubble(android.content.Context c) {
        ReelWatcher svc = instance;
        if (svc == null || svc.bubble == null) return;
        if (ReelStore.bubbleEnabled(svc) && svc.reelsOnScreen) {
            svc.bubble.show(ReelStore.todayCount(svc));
        } else {
            svc.bubble.hide();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();

        // Our own overlays (block wall, hide sheet) are not a reason to hide the pill.
        if (pkg.equals(getPackageName())) return;

        String marker = MARKERS.get(pkg);
        int type = event.getEventType();

        // Any other app taking focus means we are no longer looking at reels.
        // Hide immediately here (not via the 1.2 s flicker guard): when we kick
        // the user home after the limit, the pill must not sit on the launcher.
        if (marker == null) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handler.removeCallbacks(hideBubble);
                reelsOnScreen = false;
                if (bubble != null) bubble.hide();
            }
            return;
        }

        if (type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            AccessibilityNodeInfo viewer = firstVisible(root, marker);
            if (viewer == null) {
                // Inside Instagram/YouTube but not on the reels page: hide the pill.
                scheduleHide();
                return;
            }

            // We are on a reels page: keep the pill up.
            handler.removeCallbacks(hideBubble);
            reelsOnScreen = true;
            if (ReelStore.bubbleEnabled(this) && bubble != null) {
                bubble.show(ReelStore.todayCount(this));
            }

            String[] suppress = SUPPRESSORS.get(pkg);
            if (suppress != null) {
                for (String id : suppress) {
                    if (firstVisible(root, id) != null) return; // comments/share open
                }
            }

            String identity = identity(pkg, root, viewer);
            if (identity == null) return;
            countIfNew(pkg, identity);

        } catch (Throwable t) {
            Log.w(TAG, "event handling failed", t);
        } finally {
            root.recycle();
        }
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideBubble);
        handler.postDelayed(hideBubble, HIDE_DELAY_MS);
    }

    /**
     * Counts only when the identity changed AND the new identity is stable
     * across two consecutive events. The first reels session counts at once.
     */
    private void countIfNew(String pkg, String identity) {
        String last = lastIdentity.get(pkg);

        if (last == null) {
            confirm(pkg, identity);
            return;
        }
        if (identity.equals(last)) {
            pendingIdentity.remove(pkg);
            return;
        }
        String pending = pendingIdentity.get(pkg);
        if (identity.equals(pending)) {
            confirm(pkg, identity);
            return;
        }
        pendingIdentity.put(pkg, identity);
    }

    private void confirm(String pkg, String identity) {
        lastIdentity.put(pkg, identity);
        pendingIdentity.remove(pkg);

        long now = SystemClock.elapsedRealtime();
        if (now - lastCountAt < DEBOUNCE_MS) return;
        lastCountAt = now;

        Long seen = lastSeenAt.get(pkg);
        long duration = seen == null ? 0L : Math.min(120_000L, now - seen);
        lastSeenAt.put(pkg, now);

        int count = ReelStore.record(this, pkg, duration);
        Log.i(TAG, "reel #" + count + " (" + pkg + ", " + duration + "ms)");
        if (bubble != null && ReelStore.bubbleEnabled(this)) bubble.update(count);
        maybeBlock(count);
    }

    private void maybeBlock(int count) {
        if (!ReelStore.blockEnabled(this)) return;
        int limit = ReelStore.getLimit(this);
        if (count < limit) return;

        // The wall is transparent, so hide the pill instead of letting it show
        // through while the wall is up.
        if (bubble != null) bubble.hide();
        if (overlay != null) overlay.show(count, limit);

        performGlobalAction(GLOBAL_ACTION_BACK);
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    // ------------------------------------------------------------- identities

    private String identity(String pkg, AccessibilityNodeInfo root, AccessibilityNodeInfo viewer) {
        switch (pkg) {
            case IG: return instagramIdentity(root);
            case YT: return youtubeIdentity(root);
            case SNAP: return snapchatIdentity(viewer);
            case TT:
            case TT_INTL: return tiktokIdentity(pkg, root, viewer);
            default: return generic(viewer);
        }
    }

    /**
     * Mirrors the original: the reel's identity is the hash of the visible video
     * container's contentDescription plus the caption text. Counting is skipped
     * only when both are unchanged.
     */
    private String instagramIdentity(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo container = firstVisible(root, IG + ":id/clips_video_container");
        if (container == null) container = firstVisible(root, IG + ":id/clips_media_component");
        if (container == null) return null;

        CharSequence cd = container.getContentDescription();
        if (cd == null || cd.length() == 0) return null;

        String hash = Integer.toHexString(cd.toString().hashCode());

        String caption = "";
        AccessibilityNodeInfo captionNode = firstVisible(root, IG + ":id/clips_caption_component");
        if (captionNode != null) caption = longestDescription(captionNode);

        return "ig|" + hash + "|" + truncate(caption, 20);
    }

    /**
     * Mirrors the original: walk the player page and take the longest stable
     * piece of text, ignoring ad CTAs that change on their own.
     */
    private String youtubeIdentity(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo page = firstVisible(root, YT + ":id/reel_player_page_content");
        if (page == null) page = firstVisible(root, YT + ":id/subtitle_window_identifier");

        String best = page != null ? bestText(page, 40) : "";
        if (best.isEmpty()) best = bestText(root, 40);
        if (best.isEmpty()) return null;
        return "yt|" + truncate(best, 40);
    }

    private String snapchatIdentity(AccessibilityNodeInfo viewer) {
        CharSequence cd = viewer.getContentDescription();
        if (cd != null && cd.length() > 3) return "snap|" + truncate(cd.toString(), 40);
        String best = bestText(viewer, 40);
        return best.isEmpty() ? null : "snap|" + truncate(best, 40);
    }

    private String tiktokIdentity(String pkg, AccessibilityNodeInfo root, AccessibilityNodeInfo viewer) {
        String title = textOf(firstVisible(root, pkg + ":id/title"));
        String desc = textOf(firstVisible(root, pkg + ":id/desc"));
        if (title.isEmpty() && desc.isEmpty()) {
            String best = bestText(viewer, 60);
            return best.isEmpty() ? null : "tt|" + truncate(best, 60);
        }
        return "tt|" + truncate(title + "|" + desc, 60);
    }

    private String generic(AccessibilityNodeInfo viewer) {
        String best = bestText(viewer, 40);
        return best.isEmpty() ? null : "x|" + truncate(best, 40);
    }

    // ---------------------------------------------------------------- helpers

    private static AccessibilityNodeInfo firstVisible(AccessibilityNodeInfo root, String viewId) {
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes == null || nodes.isEmpty()) return null;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && n.isVisibleToUser()) return n;
            }
        } catch (Throwable ignored) {
            // some OEM builds throw on stale node handles
        }
        return null;
    }

    /** Longest non-trivial contentDescription inside a subtree, like the original. */
    private static String longestDescription(AccessibilityNodeInfo start) {
        String best = "";
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(start);
        int visited = 0;

        while (!queue.isEmpty() && visited++ < 300) {
            AccessibilityNodeInfo n = queue.poll();
            if (n == null) continue;

            CharSequence cd = n.getContentDescription();
            if (cd != null) {
                String s = cd.toString().trim();
                if (s.length() > 5 && s.length() > best.length()) best = s;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return best;
    }

    /** Longest useful text/description in a subtree, skipping ad CTAs. */
    private static String bestText(AccessibilityNodeInfo start, int maxLen) {
        String best = "";
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(start);
        int visited = 0;

        while (!queue.isEmpty() && visited++ < 400) {
            AccessibilityNodeInfo n = queue.poll();
            if (n == null) continue;

            String cd = trim(n.getContentDescription());
            if (useful(cd) && cd.length() > best.length()) best = cd;

            String text = trim(n.getText());
            if (useful(text) && text.length() > best.length()) best = text;

            if (best.length() >= maxLen) break;

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return best;
    }

    private static boolean useful(String s) {
        if (s.length() < 4) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        if (STOPWORDS.contains(lower)) return false;
        if (lower.matches("^[0-9.,:\\s]+$")) return false;
        return true;
    }

    private static String trim(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private static String textOf(AccessibilityNodeInfo n) {
        if (n == null) return "";
        String t = trim(n.getText());
        if (!t.isEmpty()) return t;
        return trim(n.getContentDescription());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "interrupted");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        handler.removeCallbacks(hideBubble);
        reelsOnScreen = false;
        lastIdentity.clear();
        pendingIdentity.clear();
        lastSeenAt.clear();
        if (bubble != null) bubble.hide();
        if (overlay != null) overlay.hide();
        return super.onUnbind(intent);
    }
}
