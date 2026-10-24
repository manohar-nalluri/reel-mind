# ReelMind — clean-room rebuild of BrainPal's core feature

A standalone Android app that does **only** what BrainPal's core does: count the
reels you scroll in Instagram / YouTube / Snapchat / TikTok and block you after a
daily limit.

No login, no friends, no duels, no subscription, no analytics, **no network
permission at all**.

## What was copied

The *behaviour*, reverse-engineered from `com.brainrot.android` 19.1.619:

| Original | Here |
|---|---|
| AccessibilityService watching reels viewers | `ReelWatcher` |
| View-id markers per host app | `MARKERS` in `ReelWatcher` |
| Modal suppression (comments/share sheet) | `SUPPRESSORS` in `ReelWatcher` |
| Count **distinct** reels, not scroll events | per-app `identity()` + `lastIdentity` |
| Daily count with midnight rollover | `ReelStore.rollover()` |
| Overlay block wall + kick user out | `BlockOverlay` + `performGlobalAction` |
| Floating brain pill, only on a reels page | `BubbleOverlay` + marker gate |
| Long-press → "Hide reel counter for 1 hr / 1 day" | `BubbleOverlay.openSheet()` |

### The detection rule

```
event from a supported package
  └─ is that app's reels marker visible?                       no → hide pill, ignore
       └─ is a modal open (comments / share sheet)?            yes → ignore
       └─ compute the reel's identity (see below)
            └─ same as last identity?                          yes → ignore
            └─ new identity seen twice in a row?               no  → wait for one more event
            └─ else → count++, update pill
```

Identity is derived from the reel, never from the scroll event, so idle time and
UI re-renders cannot inflate the count:

| App | Identity |
|---|---|
| Instagram | `hash(clips_video_container.contentDescription)` + caption, mirroring the original |
| YouTube | longest stable text inside `reel_player_page_content` (first 40 chars) |
| TikTok | `:id/title` + `:id/desc` |
| Snapchat | best marker contentDescription / text |

Ad CTAs ("Shop now", "Install", …) are excluded from identity. The two-event
confirmation is skipped for the first reel of a session, so it still counts
immediately.

Per-app markers:

| App | Marker view id |
|---|---|
| Instagram | `clips_viewer_view_pager` |
| YouTube | `reel_recycler` |
| Snapchat | `spotlight_container` |
| TikTok | `view_rootview` (+ title/desc) |

Suppression ids: Instagram `layout_comment_thread_edittext`, `igds_snackbar`;
YouTube `app_engagement_panel`, `design_bottom_sheet`.

## Screens (all local, no loading states)

| Screen | Contents |
|---|---|
| Onboarding | black canvas, giant Cabinet Grotesk headline, permission rows with peach "Allow" pills, tappable "Why should I give this permission?" card |
| Home | greeting + name ("Manu") with time-of-day kicker, progress ring around today's count, all-time / 7-day avg / week cards, 7-day bar chart, per-app split with share bars, limit stepper, blocking + bubble toggles, **Counter hidden / Back at h:mm + Show now** when the pill was hidden |
| Analytics | Today / 7 / 30-day ranges, total + avg + watched time + best day, hourly chart (today) or daily chart, per-app share, best/quietest day, streak, average session, on-disk size |
| Floating bubble | dark pill with cream brain glyph + amber count, top-centre, draggable, updates on every counted reel |
| Hide sheet | long-press the pill → "Hide reel counter for" + **1 hr** / **1 day** / Cancel |
| Block wall | same visual language as the app: black scrim, centred card, brain glyph, "DAILY LIMIT", count, "Take a break.", amber **Got it** |
| Kick-out bar | small bottom bar after the wall: brain glyph + "Daily limit reached — sent you home" |

### Bubble visibility

The pill is shown only while a reels viewer is actually on screen. The watcher
re-checks the host app's marker on every accessibility event; leaving the reels
page (Instagram home, YouTube home, another app) hides it after a 1.2 s delay so
a transient re-render doesn't make it flicker. It is never shown over the
launcher, notifications, or our own screens.

Long-press hides the pill for the chosen duration and stores
`bubble_hidden_until` (epoch ms). Counting keeps running while it is hidden.
The Home screen shows the remaining time and a **Show now** button that clears
the timestamp and re-shows the pill on the next reels event.

### Data model

`reel_events.tsv` in `filesDir`, one row per counted reel:

```
<epochMillis>	<package>	<viewDurationMillis>
```

90-day retention, pruned when the accessibility service connects. The prefs
counter is kept alongside as the fast path for today's number / the bubble.
Older installs migrate their single counter into synthetic events on first run
so the charts aren't blank.

## What was deliberately dropped

Everything that made it a business rather than a utility:

- Google sign-in, `access_token` / `refresh_token`, `/auth/*`
- Friends, invites, votes, duels, leaderboards (`/invite/*`)
- RevenueCat + Razorpay subscriptions (`/payments/*`)
- Firebase, Crashlytics, analytics, FCM
- Couchbase Lite + sync gateway
- The 40 MB of obfuscated libraries
- PAIRIP anti-tamper (not needed — this is your own code)
- `PACKAGE_USAGE_STATS`, `READ_PHONE_STATE`, ad-ID permissions

## Verified on device

Tested on a vivo V2045 (Android 14, arm64-v8a) against the real Instagram app.

| Check | Result |
|---|---|
| Builds | ✅ ~452 KB debug APK, no third-party deps |
| Installs | ✅ `Success` |
| Accessibility bound | ✅ `capabilities=33`, 4 event types |
| Counts reels | ✅ reel #1 → #70+ across real swipes |
| No drift when idle | ✅ 30 s untouched → **0** new counts |
| One count per swipe | ✅ 3 swipes → exactly +3 |
| Per-day persistence | ✅ `reel_counter.xml`, `date` + `count` |
| Block wall draws | ✅ full-screen wall + back/home kick-out |
| Pill gated to reels | ✅ hidden on launcher and Instagram home; visible on Reels |
| Pill shows brain | ✅ cream brain glyph + amber count |
| Long-press sheet | ✅ 1 hr / 1 day / Cancel |
| Hide for 1 hr | ✅ pill disappears, `bubble_hidden_until` set |
| Re-enable | ✅ "Show now" clears it and the pill returns |
| Analytics layout | ✅ three segments fit on one line, no clipped nav label |
| Scroll stays put | ✅ +/- and toggles keep the scroll position, no top flash |
| Block wall design | ✅ matches the app (brain, Cabinet Grotesk, amber CTA) |
| Kick-out bar | ✅ bottom bar explains the kick, pill is gone from the launcher |
| Launcher icon | ✅ adaptive amber brain on black, label **ReelMind** |

Only Instagram was exercised. YouTube/Snapchat/TikTok paths share the same code
but use different view ids, so treat them as implemented-but-unverified.

## Detection caveats (know these before trusting the number)

1. **View ids are the host apps' internals.** They change between releases. When
   Instagram renames `clips_viewer_view_pager` or `clips_video_container`,
   counting silently stops. This is inherent to the technique — the original has
   the same fragility.
2. **TikTok is the weakest.** It falls back to `view_rootview` + text identity,
   which can double-count if the caption re-renders.
3. **Identity is a heuristic.** Two reels with identical video description and
   caption hash to the same identity and count once.
4. **Blocking is one-way.** Once over the limit every new reel re-triggers the
   wall. There is no "5 more minutes" escape hatch yet.

## Build / install

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
~/.gradle/wrapper/dists/gradle-8.10.2-all/*/gradle-8.10.2/bin/gradle assembleDebug

# -i com.android.vending lets Android 13+ keep accessibility enabled
adb install -r -i com.android.vending app/build/outputs/apk/debug/app-debug.apk

# re-enable the service after every reinstall
adb shell settings put secure enabled_accessibility_services \
  com.brainpal.clone/com.brainpal.clone.ReelWatcher
adb shell settings put secure accessibility_enabled 1
```

Then on the phone:

1. Open **Reel Counter**
2. **Grant overlay permission** → allow
3. **Enable accessibility service** → find "Reel Counter" → toggle on
4. Set your limit, then scroll reels

### If accessibility refuses to turn on

Android 13+ blocks accessibility for sideloaded apps ("restricted settings").
Two fixes:

- **Install as if from Play** (what was used here):
  `adb install -r -i com.android.vending app/build/outputs/apk/debug/app-debug.apk`
- Or on the phone: **App info → ⋮ → Allow restricted settings**, then toggle
  accessibility again.

Verify it bound:

```sh
adb shell dumpsys accessibility | grep 'Bound services'
adb logcat -s ReelWatcher      # should print "reel #N (<package>)"
```

## Permissions it asks for

| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | to see the reel viewer on screen |
| `SYSTEM_ALERT_WINDOW` | to draw the block wall |

That's all. No `INTERNET`.

## Release build

The release keystore is **not** in git (`keystore/` and `keystore.properties` are
ignored). Back both up somewhere safe — losing them means you can never update
an installed copy.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
GRADLE=~/.gradle/wrapper/dists/gradle-8.10.2-all/*/gradle-8.10.2/bin/gradle

"$GRADLE" assembleRelease   # app/build/outputs/apk/release/app-release.apk
"$GRADLE" bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

Verify the signature and that it is not debuggable:

```sh
APKSIGNER=~/Library/Android/sdk/build-tools/35.0.1/apksigner
"$APKSIGNER" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The release APK is signed with `CN=ReelMind` (4096-bit RSA, v2 + v3 schemes)
and has no `android:debuggable` flag. It installs over the debug build only
after uninstalling it, because the signing keys differ — uninstalling wipes the
local count history.

For Play Store, upload `app-release.aab` and enrol in Play App Signing. Change
`applicationId` from `com.brainpal.clone` to your own ID first, and make sure
you have a licence for Cabinet Grotesk (or swap it for an open font) before
publishing.
