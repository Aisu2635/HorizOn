# Contributing

Thanks for helping out!

## Setup

1. Install Android Studio (recent stable).
2. Clone the repo and open the folder in Android Studio.
3. Run `./gradlew testDebugUnitTest lintDebug` before opening a PR.

## Code style

- Kotlin official code style (`kotlin.code.style=official`).
- Jetpack Compose for all UI; single activity, MVVM with `StateFlow`.
- Keep dependencies minimal and free of proprietary SDKs (F-Droid friendly).

## Branches and PRs

- Branch from `main`: `feature/<short-name>` or `fix/<short-name>`.
- Keep PRs focused; include screenshots for UI changes.
- CI (lint + unit tests) must pass.

## Bug reports

Include your device, Android version and the music app you were using.
