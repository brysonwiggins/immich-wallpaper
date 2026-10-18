<p align="center">
  <img src="art/icon/icon-rounded-1024.png" width="128" alt="Wallpapers with Immich icon">
</p>

<h1 align="center">Wallpapers with Immich</h1>

<p align="center">
  A new photo from your own <a href="https://immich.app">Immich</a> server on your Android lock and home screen,
  every time the screen turns off.
</p>

<p align="center">
  <a href="https://github.com/brysonwiggins/immich-wallpaper/actions/workflows/ci.yml"><img src="https://github.com/brysonwiggins/immich-wallpaper/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="https://github.com/brysonwiggins/immich-wallpaper/releases/latest"><img src="https://img.shields.io/github/v/release/brysonwiggins/immich-wallpaper" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License: GPL-3.0"></a>
</p>

> An independent app. Not affiliated with or endorsed by Immich or FUTO.

## Features
- **A new photo at every screen-off.** Photos are prepared in the background, so the change is instant.
- **Your sources.** Choose albums (including ones shared with you), people, favorites, or photos that aren't in
  any album. Archived photos are left out unless they're in an album you chose; the Locked Folder never shows.
- **Never show.** Hide albums, people or single photos, and browse exactly which photos are included.
- **Works offline.** Up to 10 photos kept on the phone for when your server can't be reached.
- **Made for foldables.** On the Galaxy Z Fold, the cover and inner screens get their own crops, and two portraits
  can sit side by side when unfolded.
- **Keeps faces in frame** and the clock area clear.
- **Private.** It talks only to your server. No ads, analytics or tracking.

## Requirements
- Android 10 or newer
- An Immich server, version 3.0 or newer
- Away from home, a way for the phone to reach your server: a VPN (WireGuard, Tailscale) or a reverse proxy with HTTPS

## Download
- **Google Play:** coming soon
- **GitHub:** the APK on the [latest release](https://github.com/brysonwiggins/immich-wallpaper/releases/latest).
  This build has no Google libraries.

## Setup
1. **Create an API key in Immich's web app.** The Immich phone app can't make keys. Open **Account Settings →
   API Keys → New API Key** and tick only these permissions:

   | Permission | Used for |
   |---|---|
   | `asset.read` | Finding photos in your sources |
   | `asset.view` | Downloading the photos |
   | `album.read` | Listing your albums |
   | `person.read` | Listing people |
   | `face.read` | Keeping faces in frame |

   The app only reads. It can't change, delete or upload anything.
2. **Connect.** Open the app and enter your server address (the one you use in a browser) and the key.
3. **Choose your photos.** Pick sources, optionally choose offline photos, and turn on **Change at screen-off**.
4. **Allow background use** when asked. Some phones otherwise stop the app overnight.

To stop without uninstalling, use **Turn off** on the Home or Settings screen. You can put the phone's default
wallpaper back, choose another one, or keep the current photo.

## Privacy
The app stores its settings, your API key (encrypted with Android's Keystore) and prepared photos on your phone,
and sends requests only to your own server. See the [privacy policy](PRIVACY.md).

## Support development
The app is free. If you enjoy it, you can support it from the About screen: a tip jar in the Google Play version,
and donation links in the GitHub version.

## Building
Requires JDK 21 and the Android SDK.

```sh
./gradlew check                    # lint and all tests
./gradlew :app:assembleFossDebug   # debug APK without Google libraries
./gradlew :app:assemblePlayDebug   # debug APK with the Play Billing tip jar
```

The JVM modules also build without the Android SDK: `./gradlew -Pimmichwall.jvmOnly=true check`.

| Module | Contents |
|---|---|
| `core` | Pure Kotlin rules: layout, cropping, pairing, the queue and when to swap |
| `data` | Immich client, file storage, the prepared-photo queue and the offline set |
| `render`, `render-android` | Drawing the prepared wallpapers |
| `presentation` | Screen logic, tested on the JVM |
| `app` | The Android app: background service, workers and the Compose UI |

## Contributing
Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License
[GNU General Public License v3.0](LICENSE).
