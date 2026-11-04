# HorizOn: Lyrics plan

> Status: L0–L6 built (2026-11-04), waiting for CI and on-device testing. Adds a third screen, reached by swiping left from clock + music: time-synced lyrics drawn as a growing web of words, or an audio visualizer when a song has no lyrics. Also gives the music card a glass edge and makes every music colour come from the album cover.
>
> Mockup (animated): https://claude.ai/artifact/953JBAWSCjxyXac6QzowEA

## 1. Decisions so far

| Question | Decision |
|---|---|
| Where lyrics come from | [LRCLIB](https://lrclib.net), a free, open database of time-synced lyrics. No account, no API key. |
| Internet | **Two builds.** `offline` stays exactly as today (no internet permission). `online` adds internet, used only for the lyrics lookup. |
| Lyrics screen | Swipe **left** from clock + music. Swipe right still goes to the full clock. |
| Look | Lyrics web: words appear one by one near the previous word, a line draws to each new word, the current word glows, earlier lines fade into a trail and disappear. |
| No lyrics | A visualizer in the album's colours. It pulses to the playback, not to the real sound (no microphone permission). |
| Song position | Hidden on the lyrics screen. A tap shows it for about 3.5 s. |
| Colours | Taken from the album cover, including on dark and black-and-white covers (section 6). |
| Music card | Option A, "Edge light": a thin glass rim, bright at the top-left, fading along the sides, with a touch of the album colour at the bottom-right. |

## 2. Two builds (product flavors)

Gradle product flavors on one dimension, `network`:

| Flavor | Name shown | Internet | Lyrics | Where it ships |
|---|---|---|---|---|
| `offline` (default) | HorizOn | No | No; the third screen shows the visualizer | GitHub release, F-Droid later |
| `online` | HorizOn+ | Yes | Yes, once the user agrees (section 5.4) | GitHub release |

- **Same `applicationId` and signing key** for both, so a user can switch builds by installing the other APK over the top. Settings and notification access survive, and Android shows the new internet permission when updating. Consequence: the two can't be installed side by side. That is fine for a desk clock, and avoids two apps both asking for notification access.
- `versionName` gets a suffix in the online build (`0.3.0-online`) so bug reports say which build they came from.
- `app/src/online/AndroidManifest.xml` adds `<uses-permission android:name="android.permission.INTERNET"/>`. Nothing else in the manifest differs.
- Code that differs lives in flavor source sets, the same pattern as the debug/release `NavSampleLogger`:
  - `main`: the `LyricsSource` interface, the LRC parser, sync, UI and visualizer.
  - `offline`: `LyricsSources.create()` returns a source that always answers "no lyrics".
  - `online`: `LyricsSources.create()` returns the LRCLIB source.
- **CI and release change.** Gradle task names gain the flavor:
  - CI: `./gradlew lintOfflineDebug lintOnlineDebug testOfflineDebugUnitTest testOnlineDebugUnitTest assembleOfflineDebug assembleOnlineDebug`, and upload both debug APKs.
  - Release: build `assembleOfflineRelease assembleOnlineRelease`, attach `HorizOn-vX.Y.Z.apk` and `HorizOn+-vX.Y.Z.apk`, and say in the release notes which is which.
  - README build commands and install section updated to match.

## 3. Screens and swiping

Today a sideways swipe toggles between clock + music and the full clock. It becomes three pages in a row:

```
  Full clock   ⟵ swipe right ─  Clock + music  ─ swipe left ⟶   Lyrics / visualizer
```

- The lyrics page exists only while something is playing or paused (`nowPlaying != null`). With no music, swiping left does nothing.
- Directions take priority as today. While Maps is navigating, the lyrics page shows a small directions pill at the top (reusing `DirectionsPill`), so the next turn is never more than a glance away.
- In `DeskScreen`, `preferFullClock: Boolean` becomes a `page` state (`Clock`, `Main`, `Lyrics`). Swipe direction now matters. Music or directions appearing still brings the user back to `Main`, except when they're already on `Lyrics`.
- Gestures on the lyrics page:
  - A single tap shows the song position for 3.5 s (thin bar, elapsed and total time).
  - A double tap opens the music app, like the player card.
  - The controls bar (clock style, Directions, Close) appears as on other pages. It gains a **Lyrics** switch in the online build.

## 4. Lyrics web (rendering)

A Compose `Canvas` in `ui/lyrics/LyricsWeb.kt`, driven by `withFrameNanos` and only while the page is visible.

- **Timing.** LRC files time each line. Words get evenly spaced times across their line, ending a little before the next line starts. "Enhanced" LRC files, which time each word, are used directly when present.
- **Placing a word.**
  - Try about 18 spots at 95–180 dp from the previous word, in random directions, squeezed a little vertically.
  - Keep them inside the safe area, below the header and above the progress area.
  - Pick the spot farthest from the words still visible, so text doesn't pile up.
  - The first word of the song starts near the centre.
- **Lines between words.**
  - Each word connects to the previous one, drawing out over about 380 ms as the word appears.
  - Some words (about 45%) also connect to a nearby older word that's still visible, which turns the chain into a web.
  - Lines inside the current lyric line use the accent colour; older ones are faint white.
- **Fading.**
  - Words of the current line stay fully bright, and the newest word is larger, with a soft accent glow and dot.
  - Earlier lines drop to about 50% at once, then fade to nothing over about 9 s, and are removed.
- **Drift.** Each word floats a few dp on a slow cycle. This keeps the screen alive and doubles as burn-in protection. It's off when the system's animations are turned off.
- **Seeking and skipping.** If the position jumps backwards or by more than a few seconds, the web is cleared and rebuilt from the current line. A new song clears it too.
- **Pause.** Time stops, words stop appearing, and the drift continues gently.
- **Battery.** It's mostly pure black with thin lines. Frames run only while the page is on screen. If testing shows it costs too much, cap it at 30 fps.
- **Text.** Shown as LRCLIB has it (any language or script). Line text wider than the screen is never split into nodes larger than one word, so long words are fine.

## 5. Lyrics data (online build)

### 5.1 Lookup
`LrcLibSource` uses `HttpURLConnection` and Android's built-in `org.json`, so no new libraries and the build stays F-Droid friendly.

1. `GET https://lrclib.net/api/get?track_name=…&artist_name=…&album_name=…&duration=…` (duration in seconds, from `NowPlaying.durationMs`). LRCLIB requires an exact match within about 2 s.
2. If that finds nothing: `GET /api/search?track_name=…&artist_name=…`. Pick the result whose duration is within 3 s and that has `syncedLyrics`.
3. The response gives `syncedLyrics` (LRC text), `plainLyrics` and `instrumental`.
   - Synced lyrics show the lyrics web.
   - Plain lyrics only, or instrumental, show the visualizer. Plain text has no timing to animate with.
- `User-Agent: HorizOn/<version> (https://github.com/Aisu2635/HorizOn)`, as LRCLIB asks of apps.
- Timeouts of 5 s. One attempt per song. A failure means no lyrics, shown as the visualizer with a small "No lyrics found" label.
- Requests run only while the lyrics page is open, or for the current song when the Lyrics switch is on and the screen is on. Never in the background.

### 5.2 Cleaning up titles
YouTube Music often reports video titles. Before searching, `TitleCleaner` (pure Kotlin, unit-tested) does two things:
- It removes `(Official Video)`, `(Official Audio)`, `(Lyric Video)`, `[From "…"]`, `(feat. …)`, `- Remastered 2011` and similar.
- It moves "Artist - Title" into the right fields when the artist field holds the channel name (e.g. "… - Topic").

### 5.3 Cache
An in-memory cache of the last 50 songs, keyed by cleaned title, artist and duration, so going back to a song costs nothing. **Nothing is written to disk**: a lyrics cache would amount to a listening history on the phone.

### 5.4 Consent
The Lyrics switch is off by default, even in the online build. The first time the user opens the lyrics page, a card like the directions prompt asks:

> **Show lyrics?** HorizOn+ will look up each song's title and artist on lrclib.net. Nothing else is sent.

The card has **Show lyrics** and **Not now** buttons. "Not now" leaves the visualizer, and the switch stays available in the controls.

## 6. Colours from the album cover

`AlbumColors` (pure Kotlin HSL maths, JVM-tested) picks the colours from the swatches `Palette` already finds in `PlayerCard`:

1. **Pick** the swatch with the most *visible* colour: saturation × min(1, 3 × lightness). A near-black swatch's "colour" counts for little.
2. **No colour** (best saturation < 0.15, e.g. black-and-white or greyscale covers): use a warm white `#ECE4D8` as the accent. Today's fallback is the app's amber, which was never on the cover.
3. **Otherwise lift** it to read on pure black: saturation at least 0.55, lightness clamped to 0.62–0.74.
4. **Card tint:** the same hue, saturation at most 0.6, lightness 0.12. For a no-colour cover, a barely warm grey at 0.09.
5. **Dark covers:** cover thumbnails (card, lyrics header, pills, visualizer centre) get a hairline white outline at about 15%, so a near-black cover doesn't vanish into the black screen.

The accent is used for:
- the lines, glow and dot in the lyrics web
- the visualizer bars and glow
- the player's progress bar and app name
- the glass rim's corner

Colour changes animate over 600 ms, as the card tint already does.

## 7. Visualizer (no lyrics, and the offline build)

`ui/lyrics/Visualizer.kt`:
- A ring of about 72 bars around the round album cover, drawn in the accent colour.
- **Movement:** bar lengths come from two slow sine waves around the ring, multiplied by a pulse that repeats at an assumed 118 BPM. The pulse runs only while playing; paused, the bars rest short.
- A soft accent glow behind the cover swells with the pulse.
- No microphone permission and no audio capture. A later, optional "React to the music" switch could use Android's `Visualizer` API, which needs the microphone permission and returns silence on some phones (several Xiaomi models). Not in this plan.

## 8. Glass edge on the music card (Edge light)

In `PlayerCard`, after `cardBackground`:
- A 1.2 dp border with `Brush.linearGradient` from top-left to bottom-right: white at 55% → 10% → 6% → accent at 45%, with the card's 28 dp corners.
- Same treatment, scaled down, on the now-playing pill.
- Stays thin, so the OLED black and its power saving are unchanged.

## 9. Privacy and docs

- **Offline build:** nothing changes. It still has no internet permission.
- **README:**
  - A "Two builds" section explains HorizOn vs HorizOn+.
  - The privacy text for HorizOn+ says: with Lyrics on, the song's title, artist, album and length are sent to lrclib.net. Nothing else is sent, there's no account, and lyrics are kept in memory only.
  - LRCLIB credited as the lyrics source. Lyrics in LRCLIB are added by its users.
- Plan.md and the music access card text updated to mention lyrics.

## 10. Testing

- **Unit (JVM):**
  - `LrcParserTest`: timestamps `[mm:ss.xx]` and `[mm:ss]`, several timestamps on one line, `[offset:]`, enhanced word times, blank and instrumental lines, metadata tags ignored.
  - `LyricsSyncTest`: current line and word at a position, with seek, pause, speed and loop.
  - `TitleCleanerTest`.
  - `AlbumColorsTest`: bright, mid, near-black and greyscale covers. Expected values come from the mockup's four sample covers.
  - `LrcLibSourceTest`: JSON parsing from saved responses (no network in tests).
- **Debug demo:** `DemoMediaActivity`'s demo tracks get bundled sample LRC files (made-up lyrics). A debug-only `LyricsSource` serves them, so the web can be tried offline and without a real music app. Its demo tracks also get a dark and a greyscale cover.
- **On device:**
  - Both builds.
  - YouTube Music and Spotify, with songs in English, Hindi and one other language.
  - Seek, skip, pause, ads (YouTube Music's ad sessions).
  - No network, and a song that isn't on LRCLIB.
  - Switching builds over the top of each other.
  - Battery drain over 30 minutes on the lyrics page.

## 11. Milestones

1. **L0: Colours and glass (both builds).** `AlbumColors` and tests, Edge light rim, thumbnail outlines. Small and visible straight away.
2. **L1: Flavors.** `offline`/`online` flavors, the online manifest, `LyricsSource` stubs, CI and release workflow, README build section. Exit: both APKs build in CI.
3. **L2: Third page and visualizer.** Three-page swiping, tap to show position, the visualizer. Exit: works in the offline build.
4. **L3: LRC and sync.** `LrcParser`, `LyricsSync`, title cleaner, debug demo lyrics, all tested.
5. **L4: Lyrics web.** The canvas web, fed by the demo lyrics. Exit: matches the mockup on a phone.
6. **L5: LRCLIB (online build).** Lookup, cache, consent card, "No lyrics found". Exit: real songs show synced lyrics.
7. **L6: Docs and release.** README, privacy text, release notes, both APKs attached, version 0.3.0.

## 12. Risks

- **Coverage:** LRCLIB is strongest for English pop and rock. Indian and regional songs are hit and miss, and YouTube Music's video titles make matching harder. Mitigations: the title cleaner, the search fallback, and the visualizer as a graceful "no lyrics" state.
- **Timing:** sync follows the position the music app reports, so some apps may run slightly early or late. A per-app offset could come later.
- **Availability:** LRCLIB is a free community service. If it is down, lyrics are simply missing; nothing breaks.
- **Content:** lyrics are third-party text shown as-is; HorizOn doesn't store or redistribute them.
- **Two builds:** every release ships two APKs, and bug reports need to say which. The `-online` version suffix helps.
- **Battery and heat:** a constantly animating screen on a charging phone. Measure in L4; cap the frame rate if needed.

## 13. Open questions

- The online build's name: "HorizOn+" is a placeholder.
- Whether the lyrics page should also be offered when the screen saver (`DreamService`, not built yet) starts.
