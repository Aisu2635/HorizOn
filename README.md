# HorizOn

An open-source Android app that turns a charging phone on its side into a calm desk clock and music display.

> Early development (v0.1 in progress). See [Plan.md](Plan.md) for the roadmap.

| Clock + music | Directions + music | Full clock | Night mode |
|---|---|---|---|
| ![Split view](docs/screens/split.svg) | ![Directions](docs/screens/nav.svg) | ![Full clock](docs/screens/clock.svg) | ![Night mode](docs/screens/night.svg) |

## Features (planned for v0.1)

- Big landscape clock with date and battery/charging status
- Now playing from any music app that publishes a media session, with play/pause, previous and next
- Player card tinted from the album art, with a thin glass edge; colours stay readable even for dark or black-and-white covers
- Swipe left from the music for **lyrics**, drawn as a growing web of words (HorizOn+ only), or a visualizer in the album's colours
- Starts automatically while charging (Android screen saver), or launch it from the app icon
- Burn-in protection, and a pure black background so OLED screens keep unused pixels off
- Optional turn-by-turn directions from Google Maps next to the music (tap the screen, then **Directions**)
- Swipe left while music plays: time-synced lyrics drawn as a web of words (HorizOn+ only), or a visualizer in the album's colours

Night mode, more clock faces and alarm display are planned for v0.2.

## Two builds

- **HorizOn** (`HorizOn-vX.Y.Z.apk`) has **no internet permission**. The lyrics screen shows a visualizer.
- **HorizOn+** (`HorizOnPlus-vX.Y.Z.apk`) is the same app plus internet, used for one thing: looking up time-synced lyrics on [LRCLIB](https://lrclib.net), a free, open lyrics database. Lyrics stay off until you agree on the lyrics screen or turn on **Lyrics** in the controls.

Both use the same signing key, so you can switch by installing one over the other; your settings stay.

## Privacy

HorizOn has **no internet permission**. It uses notification access to read which song is playing. If you turn on **Directions**, it also reads Google Maps' turn-by-turn notification while you navigate, to show the next turn. It reads no other notifications, keeps directions in memory only, and never stores or sends anything.

HorizOn+ additionally sends the playing song's title, artist, album and length to lrclib.net, and only while **Lyrics** is on. There's no account, and lyrics are kept in memory only, so no listening history is stored. Lyrics in LRCLIB are added by its community.

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
./gradlew assembleOfflineDebug assembleOnlineDebug          # build both debug APKs
./gradlew testOfflineDebugUnitTest testOnlineDebugUnitTest  # unit tests
./gradlew lintOfflineDebug lintOnlineDebug                  # lint
```

Or open the folder in Android Studio, pick a build variant (`offlineDebug` or `onlineDebug`) and press Run. In debug builds, `adb shell am start -n dev.horizon/.debug.DemoMediaActivity` plays demo songs with made-up lyrics.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE)
