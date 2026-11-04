# HorizOn

An open-source Android app that turns a charging phone on its side into a calm desk clock and music display.

> Early development (v0.1 in progress). See [Plan.md](Plan.md) for the roadmap.

| Clock + music | Directions + music | Full clock | Night mode |
|---|---|---|---|
| ![Split view](docs/screens/split.svg) | ![Directions](docs/screens/nav.svg) | ![Full clock](docs/screens/clock.svg) | ![Night mode](docs/screens/night.svg) |

## Features (planned for v0.1)

- Big landscape clock with date and battery/charging status
- Now playing from any music app that publishes a media session, with play/pause, previous and next
- Player background and accent colours taken from the album art (dark and black-and-white covers included)
- Starts automatically while charging (Android screen saver), or launch it from the app icon
- Burn-in protection, and a pure black background so OLED screens keep unused pixels off
- Optional turn-by-turn directions from Google Maps next to the music (tap the screen, then **Directions**)
- Swipe left while music plays: time-synced lyrics drawn as a web of words (HorizOn+ only), or a visualizer in the album's colours. The visualizer also fills a song's intro (with a countdown), long instrumental breaks and the outro; choruses and held line endings get a glow and a shockwave

Night mode, more clock faces and alarm display are planned for v0.2.

## Two builds

| | HorizOn | HorizOn+ |
|---|---|---|
| APK | `HorizOn-vX.Y.Z.apk` | `HorizOnPlus-vX.Y.Z.apk` |
| Internet permission | No | Yes, used only for lyrics |
| Lyrics screen | Visualizer | Time-synced lyrics from [LRCLIB](https://lrclib.net), visualizer when there are none |

Both are signed with the same key: install one over the other to switch, and your settings stay. They can't be installed side by side.

## Privacy

HorizOn has **no internet permission**. It uses notification access to read which song is playing. If you turn on **Directions**, it also reads Google Maps' turn-by-turn notification while you navigate, to show the next turn. It reads no other notifications, keeps directions in memory only, and never stores or sends anything.

HorizOn+ is the same, plus one thing: once you agree to show lyrics, it sends the playing song's title, artist, album and length to lrclib.net to find its lyrics. Nothing else is sent, there's no account, and lyrics are kept in memory only (no listening history is saved). Lyrics in LRCLIB are added by its users.

## Install

1. Download the latest `HorizOn-vX.Y.Z.apk` (or `HorizOnPlus-vX.Y.Z.apk` for lyrics) from [Releases](https://github.com/Aisu2635/HorizOn/releases) on your phone and open it. Android will ask you to allow installs from your browser or file manager.
2. Turn the phone sideways and open **HorizOn**.
3. Tap **Allow access** on the music card. Because the app isn't from the Play Store, Android 13+ may say the setting is **restricted**: long-press the HorizOn icon → **App info** → **⋮** → **Allow restricted settings**, then try again.
4. Optional: when headphones are connected, tap **Show headphone battery** and allow **Nearby devices**.

Requires Android 8.0 or newer.

## Release (maintainers)

Releases are built and signed by GitHub Actions when a version tag is pushed. Each release attaches both APKs:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and commit.
2. `git tag v0.1.0 && git push origin v0.1.0` (the tag must match `versionName`).

The workflow needs these repository secrets: `HORIZON_KEYSTORE_BASE64`, `HORIZON_KEYSTORE_PASSWORD`, `HORIZON_KEY_ALIAS`, `HORIZON_KEY_PASSWORD`. To sign locally instead, create a `keystore.properties` in the project root (it is git-ignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## Build

Requirements: Android Studio (recent stable), which includes the JDK and Android SDK.

```bash
./gradlew assembleOfflineDebug assembleOnlineDebug              # debug APKs: HorizOn and HorizOn+
./gradlew testOfflineDebugUnitTest testOnlineDebugUnitTest      # unit tests
./gradlew lintOfflineDebug lintOnlineDebug                      # lint
```

Debug builds include demo players: `adb shell am start -n dev.horizon/.debug.DemoMediaActivity` plays fake songs with made-up lyrics, and `.debug.DemoNavActivity` plays a fake route.

Or open the folder in Android Studio and press Run.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE)
