# Zendesk

An open-source Android app that turns a charging phone on its side into a calm desk clock and music display.

> Early development (v0.1 in progress). See [Plan.md](Plan.md) for the roadmap.

| Clock + music | Full clock | Night mode |
|---|---|---|
| ![Split view](docs/screens/split.svg) | ![Full clock](docs/screens/clock.svg) | ![Night mode](docs/screens/night.svg) |

## Features (planned for v0.1)

- Big landscape clock with date and battery/charging status
- Now playing from any music app that publishes a media session, with play/pause, previous and next
- Player background tinted from the album art
- Starts automatically while charging (Android screen saver), or launch it from the app icon
- Burn-in protection

Night mode, more clock faces and alarm display are planned for v0.2.

## Privacy

Zendesk has **no internet permission**. It uses notification access only to read which song is playing; it never reads or stores notification content.

## Install

Download the APK from [GitHub Releases](../../releases) once v0.1 ships.

## Build

Requirements: Android Studio (recent stable), which includes the JDK and Android SDK.

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

Or open the folder in Android Studio and press Run.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE)
