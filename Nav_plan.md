# HorizOn: Navigation plan

> Status: proposal (2026-10-06). Adds turn-by-turn directions to the standby screen, next to the music card, while Google Maps is navigating. Inspired by an AI-rendered mockup (directions on the left, player on the right).

## 1. Goal

When the phone is docked in landscape (car mount, bike mount, desk) and Google Maps is navigating, HorizOn shows the next turn on the left half of the screen and keeps the music card on the right:

```
┌──────────────────────────────────────┬──────────────────────────────┐
│ TUESDAY, OCT 6 • 6:56 PM             │ ┌──────┐  YOUTUBE MUSIC      │
│                                      │ │ art  │  Sugar              │
│  ↱   In 300 m                        │ └──────┘  Maroon 5           │
│      Turn right on Broadway St       │ ━━━○──────────────────────── │
│                                      │ 0:19                   3:55  │
│  ETA 7:30 PM        4.2 km left      │      ⏮      ⏸      ⏭        │
│        (decorative road graphic)     │                              │
│ ▭ 24%                via Google Maps │                              │
└──────────────────────────────────────┴──────────────────────────────┘
```

The mockup's moving street map is not a goal for v1 (see section 2). The turn, distance, street, ETA and remaining distance are.

## 2. Options considered

| | A. Mirror the Maps navigation notification (recommended) | B. Google Maps SDK / Navigation SDK inside HorizOn | C. Intents only |
|---|---|---|---|
| What it is | Read the ongoing turn-by-turn notification Google Maps already posts, and redraw it in our style | Embed a real map and run routing ourselves | A "Navigate" button that opens Google Maps |
| Real map tiles | No | Yes | No (Maps itself is shown) |
| Internet permission | **Not needed** | Required | Not needed |
| API key / billing | None | Google Maps Platform project with billing; Navigation SDK is priced per trip and has its own terms | None |
| Proprietary deps | None | Google Play services (breaks the F-Droid goal in CONTRIBUTING.md) | None |
| New permissions | None (reuses notification access we already have) | Location, internet | None |
| Work | Medium | Large (routing UI, search, rerouting, voice) | Tiny |
| Fits the app | Yes: glanceable mirror, like the music card | No: turns HorizOn into a navigation app | Partly |

**Decision: build A, plus C as a small helper.** It matches how the music card already works (we mirror another app's state; we don't reimplement the app), keeps "no internet permission" true, and needs no API keys in an open-source repo. Option B can be revisited if HorizOn ever drops the no-internet rule; it is out of scope here.

## 3. How it works

### 3.1 Source: the Google Maps navigation notification
While navigating, Google Maps (`com.google.android.apps.maps`) keeps an ongoing notification with the next maneuver. Typical content (exact strings vary by Maps version, locale and units, so we must capture real samples first, see N0):

| Notification field | Typical content | Used for |
|---|---|---|
| `EXTRA_TITLE` | `300 m` or `Turn right` | Distance to the next maneuver / instruction |
| `EXTRA_TEXT` / `EXTRA_BIG_TEXT` | `onto Broadway St` / `toward Main St` | Street |
| `EXTRA_SUB_TEXT` | `12 min · 4.2 km · 7:30 PM ETA` | Time left, distance left, ETA |
| Large icon (`EXTRA_LARGE_ICON` / `getLargeIcon()`) | White maneuver arrow bitmap | Turn arrow |
| `contentIntent` | Opens Maps' navigation screen | Tap to open Maps |
| `flags`, `category` | `FLAG_ONGOING_EVENT`, `navigation` category | Telling it apart from other Maps notifications |

On Android 16, Maps may post this as a promoted "Live Update" (`Notification.ProgressStyle`). The parser must read the same standard extras in both cases and use the progress value, when present, as an optional trip-progress bar.

### 3.2 Reading it without breaking our privacy promise
We already have notification access (needed for media sessions). Today `MediaListenerService` deliberately overrides nothing. It will now override `onNotificationPosted`, `onNotificationRemoved`, `onListenerConnected` and `onListenerDisconnected`, with strict rules:

1. **Allowlist by package first.** The very first check is `sbn.packageName in NAV_PACKAGES`. Anything else returns immediately; its extras are never touched.
2. **Ongoing navigation only.** Require `FLAG_ONGOING_EVENT`, and prefer `category == navigation` when set.
3. **Memory only.** The parsed state lives in a `StateFlow`; nothing is written to disk, DataStore or logs (release builds log nothing).
4. **Opt-in.** A setting "Show directions from Google Maps" (default off). When off, the service ignores even allowlisted packages.
5. **Do not rename `MediaListenerService`.** Notification access is granted per component name; renaming the class would silently revoke every existing user's grant. Update its KDoc instead.

`NAV_PACKAGES` for v1: Google Maps (`com.google.android.apps.maps`) and Google Maps Go (`com.google.android.apps.navlite`) if samples show the same format. Waze and others later (section 8).

### 3.3 Data flow

```
Google Maps ──notification──▶ MediaListenerService (filters by package)
                                   │ NavParser.parse(extras, icon)
                                   ▼
                            NavRepository.state : StateFlow<NavState?>
                                   │
                                   ▼
                     DeskScreen ──▶ NavPanel (left)  +  PlayerCard (right)
```

The service is bound by the system, not by our UI, so the repository is a process-wide singleton (an `object` or a holder on the `Application`) that both sides share. `onListenerConnected` scans `activeNotifications` so navigation that started before HorizOn opened shows up immediately; `onListenerDisconnected` and `onNotificationRemoved` (for the tracked key) clear the state.

### 3.4 Model

```kotlin
/** The next maneuver mirrored from a navigation app's notification. */
data class NavState(
    val packageName: String,
    val appLabel: String?,         // "Google Maps", shown as "via Google Maps"
    val distanceToTurn: String?,   // "300 m", kept as Maps formatted it (units, locale)
    val instruction: String?,      // "Turn right"
    val street: String?,           // "Broadway St"
    val eta: String?,              // "7:30 PM"
    val remainingDistance: String?,// "4.2 km"
    val remainingTime: String?,    // "12 min"
    val maneuverIcon: Bitmap?,     // Maps' arrow, tinted by us
    val tripProgress: Float?,      // 0..1 from ProgressStyle when available
    val openIntent: PendingIntent?,
    val updatedAt: Long,           // elapsedRealtime, for staleness
)
```

`NavParser` is a pure function (`Bundle`-free inputs: plain strings + bitmap) so it can be unit-tested on the JVM. Rules:
- Keep Maps' own strings for distances and times; don't convert units or reformat. This avoids locale bugs.
- Split `EXTRA_SUB_TEXT` on `·` / `•` and classify each part (contains a clock time → ETA; distance unit → remaining distance; duration → remaining time). Unrecognized parts are dropped, not guessed.
- If parsing fails, fall back to showing the raw title and text. Never show nothing while a nav notification exists.

### 3.5 UI

New files under `ui/nav/`:
- `NavPanel.kt`: the left half. Header line with date and time (the clock shrinks into this line while navigating, as in the mockup), large maneuver arrow, distance in the big clock type style, instruction + street, then an ETA / remaining row, and a small "via Google Maps" label (app label text only; no Google logo, consistent with the existing rule against service logos).
- `RoadBackdrop.kt`: an optional, subtle decorative perspective-road drawing on `Canvas` behind the panel. It is clearly decorative, not a map, and can be turned off. It is drawn once per maneuver change, not animated per frame (battery and heat).
- Maneuver icon: use Maps' large-icon bitmap, tinted to `Paper` with a `ColorFilter`. If missing, map keywords in the instruction ("left", "right", "U-turn", "roundabout", "merge", "arrive") to our own vector icons in `res/drawable/`.

Layout rules in `DeskScreen`:

| Navigating | Music available | Shows |
|---|---|---|
| No | yes / no | Today's behavior (split or full clock) |
| Yes | yes | **NavPanel + PlayerCard** (the mockup) |
| Yes | no | NavPanel full width, with large clock in the header |

- Navigation takes priority over the user's last swipe while it is active; a swipe still switches to the full clock, and the "now playing" pill gains a "directions" pill that brings the nav view back.
- Tap the panel → send Maps' `contentIntent` (reuse `pendingIntentStartOptions()` from `MediaRepository`; move it to a shared helper).
- Animate maneuver changes with the same crossfade used for track changes; flash nothing (driver distraction).
- Burn-in: apply the existing `burnInShift` to the nav panel too. Night mode (v0.2) will recolor it red like the clock.
- Accessibility: announce the instruction via `semantics { liveRegion = Polite }` only when the maneuver changes, not on every distance tick.

### 3.6 Starting navigation (option C helper)
On the nav pill / empty state, a "Open Google Maps" action:
- Launch `com.google.android.apps.maps` via `getLaunchIntentForPackage`.
- Later: recent / favorite destinations stored locally, launched with the documented Maps intent `google.navigation:q=<address or lat,lng>&mode=d`. No geocoding by us, so still no internet.

### 3.7 Settings and onboarding
- `SettingsRepository`: `showNavigation: Flow<Boolean>` (default false), `roadBackdrop: Flow<Boolean>` (default true).
- If notification access is already granted, the setting is a single toggle in the tap controls.
- One-time hint: when HorizOn sees an ongoing notification from an allowlisted package (package name only, nothing read) and the toggle is off, show a small card "Show directions from Google Maps here?" with Turn on / Not now, like the music access card.

## 4. Privacy and docs changes

- README "Privacy": replace "it never reads or stores notification content" with: "It reads only the ongoing navigation notification from Google Maps, and only if you turn on directions. It is kept in memory and never stored or sent anywhere. No other notification is read."
- Plan.md section 6 (Permissions): note the second use of notification access. No new manifest permissions.
- Update the `MediaListenerService` KDoc to describe exactly what it reads.

## 5. Testing

- **Unit (JVM):** `NavParserTest` with fixtures captured in N0: km and miles, 12h and 24h, at least English plus one other locale, "Arrive" and "Continue" maneuvers, roundabouts, missing subtext, rerouting text. `NavStateTest` for sub-text classification.
- **Debug harness:** `debug/DemoNavActivity` (debug build only, like `DemoMediaActivity`) posts a fake ongoing notification with the navigation category and cycles maneuvers. Debug builds add our own package to `NAV_PACKAGES` so it is picked up.
- **Sample capture:** a debug-only toggle that logs the extras keys/values of allowlisted notifications to Logcat, used to build fixtures. Never compiled into release.
- **Manual:** real drive or Maps' route preview simulation on Pixel and Samsung; Android 8 (min SDK), 14, and 16 (Live Updates); start nav before vs. after opening HorizOn; Maps killed mid-route; access revoked while showing.
- CI: no change beyond new unit tests (`./gradlew testDebugUnitTest lintDebug`).

## 6. Milestones

1. **N0: Spike.** Debug sample logger; capture real Maps notifications (2 locales, km/mi, Android 14 and 16). Confirm the fields in 3.1. Exit: fixtures committed, go/no-go on approach A.
2. **N1: Data.** `NavState`, `NavParser` + tests, `NavRepository`, listener overrides with the package allowlist, `showNavigation` setting.
3. **N2: UI.** `NavPanel`, maneuver icon tinting + fallbacks, layout rules in `DeskScreen`, directions pill, tap-to-open Maps.
4. **N3: Polish.** `RoadBackdrop`, transitions, burn-in, accessibility, one-time hint card, debug `DemoNavActivity`.
5. **N4: Docs and release.** README privacy text, Plan.md updates, new screenshot `docs/screens/nav.svg`, ship in the next minor version.

## 7. Risks

- **Undocumented format.** Google can change the notification at any time. Mitigation: tolerant parser, raw-text fallback, fixtures that make breakage obvious, quick patch releases.
- **Privacy perception.** Reading any notification content is a change from today's promise. Mitigation: opt-in, package allowlist checked before touching content, memory only, clear README wording.
- **Listener killed by OEM battery savers.** Same risk as music today; `requestRebind` on resume and show "directions unavailable" rather than stale data. Clear state if no update arrives for a few minutes while the listener is disconnected.
- **Driver distraction.** Keep the screen calm: no flashing, no extra taps needed, large type. HorizOn mirrors Maps; voice guidance stays in Maps.
- **No real map.** Users may expect the mockup's map. The decorative backdrop and tap-to-open-Maps cover this; a real map needs option B.

## 8. Later

- Waze (`com.waze`) and other apps that post navigation-category notifications (OsmAnd, Organic Maps, HERE WeGo), each behind fixtures.
- Lane guidance and speed limit if present in the notification.
- Trip progress bar from `ProgressStyle`.
- Saved destinations launched via Maps intents (section 3.6).
- Car profile: auto-open the nav layout when a specific Bluetooth car device connects (ties into the existing `Headset` code and the "per-charger profiles" idea in Plan.md).
