# N0: Capturing Google Maps navigation notifications

Goal (see [Nav_plan.md](../../Nav_plan.md), milestone N0): learn exactly what Google Maps puts in its turn-by-turn notification, so the parser can be built from real data.

Debug builds of HorizOn now record every notification from Google Maps (and only Google Maps) to:
- Logcat, tag `HorizOnNav`
- `files/nav_samples/samples.jsonl` in the app's private storage (one JSON object per event)
- `files/nav_samples/icons/*.png`: each distinct large icon (the turn arrows), saved once

Code: `app/src/debug/java/dev/horizon/nav/NavSampleLogger.kt` (release builds use a no-op in `app/src/release/`).

This guide is written to be followed by a Claude Code session running on the laptop with the phone connected over USB. The person holding the phone does the steps marked **[phone]**.

---

## 1. Setup

1. Check the phone is connected: `adb devices` must list it as `device` (not `unauthorized`; if so, accept the prompt on the phone).
2. Get the branch:
   ```bash
   git fetch origin feature/nav-mirror
   git checkout feature/nav-mirror
   git pull origin feature/nav-mirror
   ```
3. Build and check. This code has **not been compiled yet** (it was written without an Android SDK), so errors here are expected and useful:
   ```bash
   ./gradlew lintDebug testDebugUnitTest assembleDebug
   ```
   If it fails, fix only what is needed to compile or pass lint, keeping the behavior described above. Note every fix for the report.
4. Install:
   ```bash
   ./gradlew installDebug
   ```
   If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a release build is installed with a different signature. Uninstall it (`adb uninstall dev.horizon`) and install again; notification access will then need granting again.
5. Grant notification access (either way):
   - **[phone]** Open HorizOn, tap **Allow access** on the music card, enable HorizOn.
   - Or: `adb shell cmd notification allow_listener dev.horizon/dev.horizon.media.MediaListenerService`
6. Record device info for the report:
   ```bash
   adb shell getprop ro.product.model
   adb shell getprop ro.build.version.release
   adb shell getprop ro.build.version.sdk
   adb shell dumpsys package com.google.android.apps.maps | grep -m1 versionName
   adb shell settings get system time_12_24
   ```
7. Clear old samples and start a Logcat capture in the background:
   ```bash
   adb shell run-as dev.horizon rm -rf files/nav_samples
   adb logcat -c
   adb logcat -s HorizOnNav:V AndroidRuntime:E > nav_logcat.txt
   ```
   Sanity check: toggle HorizOn's notification access off and on (or reinstall). Logcat should show `Listener connected, N navigation notification(s) active`. If nothing appears at all, the listener is not running; note it and stop.

## 2. Scenarios

Run as many as practical. For each, note the time, what was done, and what the Maps notification looked like in the notification shade. For screenshots of the shade, **[phone]** pull it down, then run `adb exec-out screencap -p > shade_<scenario>.png`.

| # | Scenario | Steps |
|---|---|---|
| S1 | Start driving navigation | **[phone]** In Google Maps, pick a destination a few km away, choose driving, tap **Start**. Leave Maps (press Home) so it runs in the background. Wait 30 s. |
| S2 | Maneuver changes | Actually move (a short drive or walk with walking navigation) until at least two turn instructions have changed. If moving isn't possible, write "skipped". |
| S3 | Maps in the foreground | **[phone]** Open Maps again while navigating for 15 s, then go Home. (Does Maps keep posting while visible?) |
| S4 | End navigation | **[phone]** Exit navigation in Maps. Expect a `removed` event. |
| S5 | Other units | **[phone]** Maps → Settings → Navigation → Distance units: switch km ↔ miles. Repeat S1 briefly, then S4. Switch back afterwards. |
| S6 | 24h / 12h clock | **[phone]** Switch the phone between 12h and 24h time. Repeat S1 briefly, then S4. Switch back afterwards. |
| S7 | Other language (optional) | **[phone]** Change the phone language (e.g. Hindi or Spanish). Repeat S1 briefly, then S4. Switch back. |
| S8 | Already navigating when listener connects | Start navigation, then toggle HorizOn's notification access off and on. Expect `active` events. |
| S9 | Walking or two-wheeler mode (optional) | Repeat S1 with walking or two-wheeler navigation. |

## 3. Collect the results

Stop the Logcat capture (Ctrl+C), then:

```bash
adb exec-out run-as dev.horizon cat files/nav_samples/samples.jsonl > samples.jsonl
adb exec-out run-as dev.horizon tar -cf - -C files nav_samples > nav_samples.tar
mkdir -p nav_samples_out && tar -xf nav_samples.tar -C nav_samples_out
adb logcat -b crash -d > crash_log.txt
```

### Redact before committing
The samples contain real street names, the destination and possibly the home area. Before committing:
- Replace the destination and any home or work address with consistent placeholders (`<DEST>`, `<HOME>`); replace other street names with `Street A`, `Street B`, ... **consistently** (the same street gets the same placeholder everywhere).
- Keep everything else exactly as is: distances, units, times, separators such as `·`, punctuation, key names, flags, structure.
- Show the person the list of replacements and get their OK before committing.

### Commit the data to the branch
Put the redacted files here and push, so the next session can read them directly:

```
app/src/test/resources/nav/samples/<device>-<scenario>.jsonl   # split samples.jsonl by scenario using the times noted
app/src/test/resources/nav/icons/*.png                          # from nav_samples_out/nav_samples/icons/
docs/nav/screens/shade_<scenario>.png                           # shade screenshots, if any (redact or crop addresses)
docs/nav/N0-report.md                                           # the report below
```

```bash
git add app/src/test/resources/nav docs/nav
git commit -m "Add N0 Google Maps navigation notification samples"
git push origin feature/nav-mirror
```

Do not commit `nav_logcat.txt`, `samples.jsonl` or `nav_samples.tar` (unredacted).

## 4. Report

Write `docs/nav/N0-report.md` with this template, commit it with the samples, and also print it in the chat so it can be pasted back:

```markdown
# N0 report

## Device
- Model / Android version / SDK:
- Google Maps version:
- Phone clock: 12h or 24h; Maps units: km or mi; language:

## Build
- `lintDebug testDebugUnitTest assembleDebug`: pass / fail
- Fixes made to compile or pass lint (file, what, why):

## Listener
- "Listener connected" seen: yes / no
- Any crashes (from crash_log.txt / AndroidRuntime):

## Scenarios
| # | Done? | Events seen (posted/active/removed, count) | Notes |
|---|---|---|---|
| S1 | | | |
| S2 | | | |
| S3 | | | |
| S4 | | | |
| S5 | | | |
| S6 | | | |
| S7 | | | |
| S8 | | | |
| S9 | | | |

## What the notification contains
For one representative S1 sample, list which field holds each item ("none" if missing):
- Distance to next turn (e.g. "300 m"):
- Instruction (e.g. "Turn right"):
- Street:
- ETA:
- Remaining distance:
- Remaining time:
- Turn arrow (large icon present? size?):
- Category / channel / ongoing flag:
- Is the data in `extras` or only in `customViews`?
- Any Android 16 progress fields (`android.progress*`):

## Surprises / questions
-

## Files pushed
-
```

---

## Prompt for the local Claude Code session

Paste this into Claude Code running on the laptop, in the HorizOn folder:

> Read `docs/nav/N0-testing.md` on branch `feature/nav-mirror` and follow it step by step. My phone is connected over USB with USB debugging on. Run every adb and gradle command yourself; when a step needs me to do something on the phone, tell me exactly what to tap and wait for me to say "done". Fix only compile or lint errors, and list every fix. Before committing samples, show me the redactions. At the end, push to `feature/nav-mirror` and print the full N0 report.
