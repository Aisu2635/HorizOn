# N0 report

## Device
- Model / Android version / SDK: OnePlus CPH2723 (OP612BL1) / Android 16 / SDK 36
- Google Maps version: 26.39.05.984891338
- Phone clock: 12h; Maps units: km; language: English (en-IN)

## Build
- `lintDebug testDebugUnitTest assembleDebug`: pass (first try)
- Fixes made to compile or pass lint (file, what, why): none needed
- Install: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (release build with another signature was installed); uninstalled `dev.horizon`, then `installDebug` succeeded. Notification access granted with `cmd notification allow_listener`.

## Listener
- "Listener connected" seen: yes (`Listener connected, 3 navigation notification(s) active` at 19:27:15)
- Any crashes (from crash_log.txt / AndroidRuntime): none from `dev.horizon`. The crash buffer only has `com.hdfcbank.payzapp` isolated-process crashes (unrelated).

## Scenarios
Times are phone local time (IST, UTC+5:30) on 2026-10-06; sample `time` fields are UTC.

| # | Done? | Events seen (posted/active/removed, count) | Notes |
|---|---|---|---|
| S1 | yes | posted 32 (`CPH2723-S1.jsonl`) | 19:29:00. ~5.2 km route (`progressMax` 5212). First post title `Starting navigation…` with `progressMax` 0, then `Head north` / `100 m · Turn left toward Street A`. Posts ~1/s, but **no posts at all 19:29:26–19:31:00** while the phone sat still with the shade open. Traffic alert (id 1532) + group summary (id 0) also posted. |
| S2 | skipped | – | Not possible to move. |
| S3 | yes | posted 15, removed 3 (`CPH2723-S3.jsonl`) | Opening Maps at 19:31:14 **removed** all Maps notifications (id 1, 1532, 0). Going Home at 19:31:30 posted them again. Maps does not keep its notification while visible. |
| S4 | yes | removed 1 (`CPH2723-S4.jsonl`); also removed 3 at the end of S8, S5, S6 | Exit navigation (in Maps and from the notification action) gives `removed` for id 1, plus 1532 and 0 when present. |
| S5 | yes | posted 33, removed 3 (`CPH2723-S5-miles.jsonl`) | Miles: `350 ft · Turn left toward Street A`, `shortCriticalText` `350 ft`. `progressMax` stays metric-looking (4718). Units switched back to km. |
| S6 | yes | posted 24, removed 3 (`CPH2723-S6-24h.jsonl`) | 24h: `Arrive 19:59` (12h: `Arrive 7:53 pm` with U+202F before `pm`). Also a street-less title: `100 m · Turn left`. `android.progress` advanced 0→2→3. Clock switched back to 12h. |
| S7 | skipped | – | |
| S8 | yes | posted 12, active 3, removed 3 (`CPH2723-S8.jsonl`); active 2 (`CPH2723-S8b-reconnect.jsonl`) | Maps was already navigating (~82 km trip) when the listener was toggled at 19:27:15: `active` for id 1 (nav), 1532 (traffic alert), 0 (group summary). After the reconnect, **no `posted` updates for 46 s** until exit. 8b: force-stop + toggle at 19:32:35 gave 2 `active` events and two `Listener connected` lines (19:32:37, 19:32:44). |
| S9 | skipped | – | |

## What the notification contains
Representative S1 sample (`CPH2723-S1.jsonl`, 13:59:01Z):
- Distance to next turn (e.g. "300 m"): `android.shortCriticalText` = `100 m` (also prefix of `android.title`, before ` · `). Empty string when the title is `Head north`.
- Instruction (e.g. "Turn right"): `android.title` = `100 m · Turn left toward Street A` (or `Head north` with no distance)
- Street: inside `android.title`, after `toward ` (absent in some titles, e.g. `100 m · Turn left`)
- ETA: `android.subText` = `Arrive 7:53 pm` / `Arrive 19:59` (follows the phone clock format)
- Remaining distance: no text. Only `android.progressMax` (route length, looks like metres) minus `android.progress`
- Remaining time: none
- Turn arrow (large icon present? size?): yes, `android.largeIcon`, 168x168 PNG. `ad723be1` = straight/north, `f28d0003` = turn left. The traffic alert uses `4d4dce5d` (168x168). Also `android.progressTrackerIcon` (not saved).
- Category / channel / ongoing flag: `category` = `navigation`, `ongoing` = true, `foregroundService` = true, id 1, action `Exit navigation`. `channel` alternates between `1_foreground_1` and `1_2` on consecutive posts. `onlyAlertOnce` is mostly true, false on some posts.
- Is the data in `extras` or only in `customViews`?: all in `extras`. `customViews` is always empty. `android.text` is null.
- Any Android 16 progress fields (`android.progress*`): yes. Template `Notification$ProgressStyle`, `android.requestPromotedOngoing` = true (shown as a Live Update chip in the status bar), `android.progress`, `android.progressMax`, `android.progressSegments` (traffic-coloured segments: blue -15772673, orange -24576, red -769226, grey -3552808), `android.progressPoints` (empty), `android.progressTrackerIcon`, `android.styledByProgress` = false.

## Surprises / questions
- Maps removes its notification whenever Maps is in the foreground (S3). The mirror will go blank while Maps is open unless it keeps the last state.
- Updates are not steady: ~1/s at times, but long gaps (46 s after reconnect in S8, ~90 s in S1) while stationary. Unknown whether that is Maps throttling when not moving or OEM (ColorOS) behaviour.
- `channel` flips between `1_foreground_1` and `1_2` for the same notification, so don't filter on channel. Use package + id 1 + `category` = `navigation`.
- The title has two shapes: bare instruction (`Head north`) and `<distance> · <instruction>`. The separator is ` · ` with a no-break space inside the distance (`100 m`).
- Maps also posts a non-navigation traffic alert (id 1532, `BigTextStyle`, `category` null) with `android.car.EXTENSIONS`, and a group summary (id 0) with no text. The parser must ignore these.
- The destination never appears in any field.
- Logger quirk: the "icon already saved" memory survives deleting `files/nav_samples`, so following step 7 (clear after granting access) lost two icons until HorizOn was force-stopped. Clearing before granting access, or resetting the memory, avoids it.
- After a force-stop + toggle, `Listener connected` was logged twice (7 s apart).

## Files pushed
- `app/src/test/resources/nav/samples/CPH2723-S1.jsonl`
- `app/src/test/resources/nav/samples/CPH2723-S3.jsonl`
- `app/src/test/resources/nav/samples/CPH2723-S4.jsonl`
- `app/src/test/resources/nav/samples/CPH2723-S5-miles.jsonl`
- `app/src/test/resources/nav/samples/CPH2723-S6-24h.jsonl`
- `app/src/test/resources/nav/samples/CPH2723-S8.jsonl`
- `app/src/test/resources/nav/samples/CPH2723-S8b-reconnect.jsonl`
- `app/src/test/resources/nav/icons/ad723be1.png`, `f28d0003.png`, `4d4dce5d.png`
- `docs/nav/screens/shade_S1.png` (expanded shade), `docs/nav/screens/shade_S1_chip.png` (status-bar Live Update chip)
- `docs/nav/N0-report.md`

Redaction: the one street name in the data → `Street A` (15 occurrences). No other location text was present.
