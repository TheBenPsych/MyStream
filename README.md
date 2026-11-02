<p align="center">
  <img src="app/src/main/res/drawable-nodpi/benflix_logo.png" alt="Benflix" width="320"/>
</p>

<h1 align="center">Benflix</h1>

<p align="center">
  A Netflix-style app for your own movies and shows, for Android TV, Google TV, Fire TV and Android phones.
</p>

<p align="center">
  <a href="https://github.com/TheBenPsych/MyStream/releases/tag/latest-build">
    <img alt="Latest build" src="https://img.shields.io/badge/download-latest%20build-E50914"/>
  </a>
</p>

## What is Benflix?

Benflix is a home streaming app. It plays the movies and shows that you already keep on your own
[Jellyfin](https://jellyfin.org/) media server, and makes them look and feel like a big streaming service.

It is a personal project for family and friends. It is not on the Google Play Store or Amazon Appstore,
so you install it yourself (it only takes a few minutes, see [How to install](#how-to-install)).

Benflix is built on top of the excellent open-source app [Wholphin](https://github.com/damontecres/Wholphin).
See [Credits and license](#credits-and-license) below.

<!-- Screenshots coming soon -->

## Features

### A home screen that feels like Netflix
- Big rotating banner at the top with a muted trailer playing behind it
- Rows for Continue watching, Top 10, seasonal picks, Because you watched, Pick a mood, New on Benflix, My List, collections, genres and studios
- Separate anime rows and a Top 10 for anime
- Rotten Tomatoes scores, "New episode" badges and a "Surprise me" button
- A top menu with Home, Browse, Channels, Games, My Benflix and More

### Profiles for everyone
- "Who's watching?" screen with fun avatars, and an optional PIN per profile
- A Kids profile on every account that only shows kid-friendly titles
- Thumbs up / thumbs down likes for each profile

### Watching
- Netflix-style player with skip intro and skip credits
- Next episode plays automatically
- Ambient mode when you pause
- Trailers play right inside the app
- Two video players to choose from (ExoPlayer and MPV), for the best subtitle support

### Extras
- Ask for new movies and shows from the Requests tab (works with [Seerr / Jellyseerr](https://github.com/seerr-team/seerr))
- Small holiday and birthday touches on the logo
- Screensaver with movie artwork and fun facts
- Games section for playing retro games (in progress)
- Updates itself: when a new version is ready, a pop-up shows what is new and installs it for you

## How to install

You need a Jellyfin server and a login from whoever runs it.

### On a TV (Google TV, Android TV or Fire TV)

1. Turn on installing apps from "unknown sources" on your TV. Guides:
   [Google TV / Chromecast](https://androidtvnews.com/unknown-sources-chromecast-google-tv/) ·
   [Fire TV](https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/)
2. Install the free **Downloader** app from your TV's app store.
3. Open Downloader and type the code **`2488986`**, then press Go.
4. When it finishes downloading, choose **Install**.
5. Open Benflix, enter your Jellyfin server address, and sign in.

### On an Android phone

1. On your phone, open this link:
   [Benflix.apk](https://github.com/TheBenPsych/MyStream/releases/download/latest-build/Benflix.apk)
2. When it finishes downloading, tap it and allow your browser to install apps if it asks.
3. Open Benflix and sign in.

### Getting updates

You only install Benflix once. After that, the app checks for new versions by itself and shows a pop-up
when one is ready. You can also go to **Settings > Install update**.

### What you need

- Android 6 or newer (or Fire TV OS 6 or newer)
- A Jellyfin server, version 10.10 or 10.11

There is no iPhone or Apple TV version. Apple users can use [Swiftfin](https://github.com/jellyfin/Swiftfin) or [Infuse](https://firecore.com/infuse) instead.

## Credits and license

Benflix is a modified version (a "fork") of **[Wholphin](https://github.com/damontecres/Wholphin)** by
[damontecres](https://github.com/damontecres), an open-source Android TV app for Jellyfin.
A huge thank you to Wholphin and its contributors. Most of the hard work under the hood is theirs.
If you want a polished Jellyfin app from the Play Store, go get Wholphin!

Thanks also to:
- The [Jellyfin](https://jellyfin.org/) team for the media server
- The official [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) app, which some of Wholphin's playback code is adapted from
- The [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin) and all the other open-source libraries this app uses

Benflix is not connected to, or endorsed by, Netflix, Jellyfin or Wholphin.

### License

Like Wholphin, Benflix is released under the **GNU General Public License, version 2** (GPL-2.0).
The full text is in the [LICENSE](LICENSE) file. In short: you are free to use, change and share this
app, but if you share a changed version you must share its source code under the same license
and keep these credits.

## For developers

Benflix is built automatically by GitHub Actions every time a change lands on `main`. The newest APK
is always on the [latest-build release](https://github.com/TheBenPsych/MyStream/releases/tag/latest-build).
For build details, see [DEVELOPMENT.md](DEVELOPMENT.md) (written for Wholphin, mostly still accurate).
