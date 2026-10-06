# HorizOn: Navigation plan

> Status: N0 done, go (2026-10-06); N1, N2 and N3 done. See [docs/nav/N0-report.md](docs/nav/N0-report.md). Adds turn-by-turn directions to the standby screen, next to the music card, while Google Maps is navigating. Inspired by an AI-rendered mockup (directions on the left, player on the right).

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
While navigating, Google Maps (`com.google.android.apps.maps`) keeps an ongoing notification with the next maneuver. Confirmed in N0 (Google Maps 26.39, Android 16, OnePlus; [report](docs/nav/N0-report.md), fixtures in `app/src/test/resources/nav/`):

| Notification field | Real content | Used for |
|---|---|---|
| `android.title` | `100 m · Turn left toward Street A`, or a bare `Head north`; `Starting navigation…` at first | Distance, instruction, road (split on ` · `, then on ` toward ` / ` onto ` / ` on `) |
| `android.shortCriticalText` | `100 m`, `350 ft`; empty when there is no distance | Distance to the next turn |
| `android.subText` | `Arrive 7:53 pm` / `Arrive 19:59` (follows the phone's clock format) | ETA |
| `android.progress` / `android.progressMax` | metres travelled / route length in metres (also in miles mode) | Remaining distance (we format it in Maps' units) and a trip progress bar |
| `android.progressSegments` | traffic-coloured route segments | Later: a traffic-coloured progress bar |
| Large icon (`getLargeIcon()`, an `Icon`) | White arrow on transparent, 168×168 | Turn arrow, tinted by us |
| `contentIntent` | Opens Maps' navigation screen | Tap to open Maps |
| `category`, flags | `navigation`, ongoing, foreground service | Telling it apart from other Maps notifications |

Not present: remaining time, and the destination. `android.text` is null and there are no custom views. On Android 16 it is a promoted `ProgressStyle` Live Update; older Android versions are not sampled yet.

Behaviour that shapes the design:
- **Maps removes the notification while Maps is on screen** and posts it again when you leave. Removal is therefore cleared after a 3 s grace period, so coming back from Maps doesn't flash the clock.
- **Updates are irregular:** about once a second while moving, but none for 45 to 90 s while standing still. A quiet notification is not stale; there is no time-out while it is posted.
- **The channel changes between posts** (`1_foreground_1` / `1_2`), so it is not used for filtering.
- Maps also posts a traffic alert (id 1532, no category) and a group summary (id 0). Both are ignored.

### 3.2 Reading it without breaking our privacy promise
We already have notification access (needed for media sessions). Today `MediaListenerService` deliberately overrides nothing. It will now override `onNotificationPosted`, `onNotificationRemoved`, `onListenerConnected` and `onListenerDisconnected`, with strict rules:

1. **Allowlist by package first.** The very first check is `sbn.packageName in NAV_PACKAGES`. Anything else returns immediately; its extras are never touched.
2. **Ongoing navigation only.** Require `FLAG_ONGOING_EVENT` and `category == navigation`, checked before any text is read.
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

The service is bound by the system, not by our UI, so the repository is a process-wide singleton (an `object` or a holder on the `Application`) that both sides share. `onListenerConnected` scans `activeNotifications` so navigation that started before HorizOn opened shows up immediately; `onListenerDisconnected` clears the state; `onNotificationRemoved` (for the tracked key) clears it after the 3 s grace period.

### 3.4 Model

Implemented in N1 (`app/src/main/java/dev/horizon/nav/`):

- `NavFields`: the few notification values we read, as plain strings and ints.
- `NavParser.parse(NavFields): NavInfo?`: pure Kotlin, tested on the JVM against the N0 fixtures. `NavInfo` holds `distanceToTurn`, `instruction`, `road`, `arrival`, `etaTime`, `remainingMeters`, `tripProgress`, `imperial` and `starting`.
- `NavState`: `NavInfo` plus the maneuver bitmap, the `contentIntent` and the notification key.
- `NavRepository`: a process-wide `StateFlow<NavState?>`, written by `MediaListenerService`, read by the UI.

Parsing rules:
- Keep Maps' own strings for the turn distance and arrival time; don't reformat them. Only the remaining distance is ours to format, in the units Maps uses (`ft`/`mi` in the turn distance means imperial).
- The road split and the `Arrive ` prefix are English-only. In other languages the whole instruction and arrival line are shown as they are, which is still correct, just less styled.
- If the title can't be split, show it whole. Never show nothing while a navigation notification exists.

### 3.5 UI

New files under `ui/nav/`:
- `NavPanel.kt`: the left half. Header line with date and time (the clock shrinks into this line while navigating, as in the mockup), large maneuver arrow, distance in the big clock type style, instruction + street, then an ETA / remaining row, and a small "via Google Maps" label (app label text only; no Google logo, consistent with the existing rule against service logos).
- `RoadBackdrop.kt` (deferred: the panel reads well without it on a real phone; revisit if the mockup's look is wanted): an optional, subtle decorative perspective-road drawing on `Canvas` behind the panel. It is clearly decorative, not a map, and can be turned off. It is drawn once per maneuver change, not animated per frame (battery and heat).
- Maneuver icon: use Maps' large-icon bitmap, tinted to `Paper` with a `ColorFilter`. If missing, map keywords in the instruction ("left", "right", "U-turn", "roundabout", "merge", "arrive") to our own vector icons in `res/drawable/`.

Layout rules in `DeskScreen`:

| Navigating | Music available | Shows |
|---|---|---|
| No | yes / no | Today's behavior (split or full clock) |
| Yes | yes | **NavPanel + PlayerCard** (the mockup) |
| Yes | no | NavPanel on the left, the chosen clock face on the right (full-width directions left half the screen empty in testing) |

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
- One-time prompt (done in N3): while directions are off, the listener still notes whether Maps has an ongoing `navigation`-category notification (package, category and flags only; no text). If so, and the user has never chosen, a pill at the top asks "Google Maps is navigating. Show directions here?" with Show / Not now. Any choice, including the switch in the controls, stops it for good.

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

1. **N0: Spike. Done:** go, see [N0 report](docs/nav/N0-report.md). Debug sample logger; capture real Maps notifications (2 locales, km/mi, Android 14 and 16). Confirm the fields in 3.1. Exit: fixtures committed, go/no-go on approach A.
2. **N1: Data. Done.** `NavState`, `NavParser` + tests, `NavRepository`, listener overrides with the package allowlist, `showNavigation` setting.
3. **N2: UI. Done** (tested on device 2026-10-06, T1–T11 pass; turn changes while moving not yet seen). The on/off switch lives in the tap controls ("Directions on/off") until there is a settings screen. `NavPanel`, maneuver icon tinting + fallbacks, layout rules in `DeskScreen`, directions pill, tap-to-open Maps.
4. **N3: Polish. Done, not yet tried on a phone.** Debug `DemoNavActivity` (a made-up route: `adb shell am start -n dev.horizon/.debug.DemoNavActivity`), the one-time prompt, a rise-in transition for new maneuvers and a gliding trip progress bar. Burn-in shift and the live-region announcement already came with N2. `RoadBackdrop` deferred. After the on-device demo test: turns no longer overlap mid-change, our fallback arrows are drawn shapes (U-turns swing right in left-hand-traffic countries such as India), short street names wrap as a whole, and the prompt became a card under the clock instead of an overlay.
5. **N4: Docs and release.** README privacy text, Plan.md updates, new screenshot `docs/screens/nav.svg`, ship in the next minor version.

## 7. Risks

- **Undocumented format.** Google can change the notification at any time. Mitigation: tolerant parser, raw-text fallback, fixtures that make breakage obvious, quick patch releases.
- **Privacy perception.** Reading any notification content is a change from today's promise. Mitigation: opt-in, package allowlist checked before touching content, memory only, clear README wording.
- **Listener killed by OEM battery savers.** Same risk as music today; `requestRebind` on resume and show "directions unavailable" rather than stale data. State is cleared when the listener disconnects.
- **Heads-up popups from Maps.** Some phones (OnePlus "Live Alerts") pop Maps' notification over HorizOn on every maneuver change. HorizOn can't suppress another app's alerts; users can turn them off for Maps in the phone's notification settings.
- **Driver distraction.** Keep the screen calm: no flashing, no extra taps needed, large type. HorizOn mirrors Maps; voice guidance stays in Maps.
- **Untested setups.** Only Android 16 on one OnePlus phone, in English, has been sampled. Android 8 to 15 (no `ProgressStyle`, so maybe no `progressMax` and no remaining distance), other languages and Maps Go still need samples. Re-run the N0 guide on another device when possible.
- **No real map.** Users may expect the mockup's map. The decorative backdrop and tap-to-open-Maps cover this; a real map needs option B.

## 8. Later

- Waze (`com.waze`) and other apps that post navigation-category notifications (OsmAnd, Organic Maps, HERE WeGo), each behind fixtures.
- Lane guidance and speed limit if present in the notification.
- Trip progress bar from `ProgressStyle`.
- Saved destinations launched via Maps intents (section 3.6).
- Car profile: auto-open the nav layout when a specific Bluetooth car device connects (ties into the existing `Headset` code and the "per-charger profiles" idea in Plan.md).
