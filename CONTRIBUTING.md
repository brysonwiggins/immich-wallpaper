# Contributing

Thanks for helping improve Wallpapers with Immich.

## Reporting a bug
Open an issue with:
- your phone model and Android version;
- your Immich server version;
- the app version, from **Settings → About**;
- what you did, what you expected, and what happened.

The **Diagnostics log** (**Settings → Diagnostics log → Share**) helps a lot. It contains no API keys, server
addresses or photo links.

## Suggesting a feature
Open an issue describing what you'd like to do and why. For larger changes, please discuss them in an issue
before opening a pull request.

## Pull requests
1. Fork the repository and create a branch from `main`.
2. Keep each pull request to one change, with tests where the logic can be tested on the JVM (`core`, `data`,
   `presentation`).
3. Run `./gradlew check` before pushing.
4. If your change alters a screen, add `[record screenshots]` to the commit message. CI then re-records the
   screenshot tests and commits them to your branch. Check the new images.

By contributing, you agree that your contributions are licensed under the [GPL-3.0](LICENSE).
