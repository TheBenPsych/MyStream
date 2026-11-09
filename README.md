<p align="center">
  <img src="docs/readme/hero.jpg" alt="Benflix on a TV and a phone: the Home screen with five coloured doors for Movies, Shows, Live TV, Games and Music" width="100%"/>
</p>

<p align="center">
  <a href="https://github.com/TheBenPsych/MyStream/releases/tag/latest-build"><img alt="Download the latest build" src="https://img.shields.io/badge/download-latest%20build-FFB547?style=for-the-badge&labelColor=0B0C10"/></a>
  <img alt="Downloader code 2488986" src="https://img.shields.io/badge/Downloader%20code-2488986-3DD6C6?style=for-the-badge&labelColor=0B0C10"/>
  <img alt="Android TV, Google TV, Fire TV, phones and web" src="https://img.shields.io/badge/TV%20%C2%B7%20phone%20%C2%B7%20web-7AA7FF?style=for-the-badge&logo=android&logoColor=white&labelColor=0B0C10"/>
</p>

<p align="center">
  <a href="#what-is-benflix">What is it?</a> ·
  <a href="#a-quick-look">A quick look</a> ·
  <a href="#whats-inside">What's inside</a> ·
  <a href="#how-to-install">How to install</a> ·
  <a href="#for-the-server-admin">For the server admin</a> ·
  <a href="#credits-and-license">Credits</a>
</p>

## What is Benflix?

Benflix is a home streaming app. It plays the movies, shows and music you already keep on your own
[Jellyfin](https://jellyfin.org/) server, adds live TV channels, and plays your retro games right on the TV or phone.

Everything sits behind **five coloured doors**: 🟧 **Movies**, 🟦 **Shows**, 🟩 **Live TV**, 🟥 **Games** and 🟪 **Music**.
Each door keeps its colour everywhere inside it, so you always know where you are.

It is a personal project for family and friends. It is not in the Google Play Store or the Amazon Appstore,
so you install it yourself. It takes about five minutes, see [How to install](#how-to-install).

Benflix is built on top of the excellent open-source app [Wholphin](https://github.com/damontecres/Wholphin).
See [Credits and license](#credits-and-license).

<p align="center">
  <img src="docs/readme/how-it-fits.png" alt="Your server at home runs Jellyfin, Seerr, Lidarr, RomM, the Benflix helper, Trailarr, Sonarr and Radarr. Benflix on your TV, your phone and in a web browser connects to it." width="100%"/>
</p>

## A quick look

<p align="center">
  <img src="docs/readme/tv-tour.jpg" alt="Benflix on the TV: a show page, Live TV, Games, the Music page, the Now playing screen with lyrics, and Search" width="100%"/>
</p>

<p align="center">
  <img src="docs/readme/phone-tour.jpg" alt="Real screenshots of Benflix on a phone: Home, Movies, Shows, Live TV, Games, Search, a movie page and Settings" width="100%"/>
</p>

<sub>The phone pictures are real screenshots, taken by the test robot on the public Jellyfin demo server.
The TV pictures come from the finished Benflix design, with made-up titles.</sub>

## What's inside

<table>
<tr>
<td width="50%" valign="top">

### 🟧 Movies and 🟦 Shows
- Each door opens on a big **Picked for you** banner, then **Keep watching**, **My List**, **Top 10**, collections and more
- Clean title pages with **IMDb** and **Rotten Tomatoes** scores, a trailer that fades in, and **More like this**
- **Did you know?** fun facts on movie and show pages
- Shows list every season and episode, with "New episode" news on Home
- Anime gets its own picks, Dubbed and Subbed, plus **AniList** and **MyAnimeList** scores
- An **All movies** and **All shows** grid you can sort by newest, A to Z or year

</td>
<td width="50%" valign="top">

### ▶️ Watching
- A clean player with **Skip intro** and **Skip credits** that find the right spot by themselves
- The next episode plays by itself (you choose when it stops)
- Subtitles move out of the way of on-screen words
- A **parents' guide** on every title
- Two video players (ExoPlayer and MPV) for the best subtitle support

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🟪 Music
- **Daily mixes** made for you, refreshed every morning
- Album, artist and playlist pages that look like movie pages
- A **Now playing** screen with **lyrics** and colourful music bars
- **Save songs to your phone** to listen with no internet
- Bring in your playlists from **Spotify, YouTube Music** or **Deezer**
- Music also plays in Benflix in your web browser

</td>
<td width="50%" valign="top">

### 🟩 Live TV
- About **600 hand-picked channels**, sorted into types like News, Movies, Comedy, Kids and Holiday
- **Benflix channels** with no ads, built from your own movies and shows, and you can **make your own channel**
- Favourites, a TV guide, and number keys to jump to a channel
- **Pause live TV**, and get a reminder before a big event starts

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🟥 Games
- Play your retro games **right on the TV or phone**, no streaming needed
- Over 20 game systems, from NES and Game Boy to PlayStation and arcade
- Play with the **TV remote**, a **Bluetooth controller**, or **your phone** as the game pad
- Save slots with pictures, and your saves follow you
- Party games like **Quiz night**, movie puzzles, leaderboards and the **Ember Road** pixel adventure

</td>
<td width="50%" valign="top">

### 🔎 Search and 🙋 Requests
- **One search for everything**: movies, shows, live channels, games and music, side by side
- Search in plain words, or with your voice, or type on your phone
- Can't find it? **Ask for it** right from Search
- Ask for **movies and shows** (powered by [Seerr](https://github.com/seerr-team/seerr)) or **songs, albums and playlists** (powered by [Lidarr](https://lidarr.audio/))
- See when it's on its way, and get told when it's ready to watch

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 👨‍👩‍👧 Profiles for everyone
- A friendly **Who's watching?** screen and a short startup intro where the doors grow out of the logo
- Fun avatars on a colour you pick, plus an optional PIN for each profile
- A **Kids** profile that only shows kid-friendly titles, and an **Easy mode** with bigger words
- 👍 / 👎 likes, a **My watching** page with your stats, and your settings follow you to every device

</td>
<td width="50%" valign="top">

### 📱 Phone and 🌐 web
- A touch-friendly phone layout, with the same five doors
- Use your phone as a **TV remote**, or send something to **Play on TV**
- **Quick Connect**: sign a new TV in by typing its code on your phone
- **Web Benflix**: open your server's address in any web browser, at home or away, no install needed

</td>
</tr>
</table>

And a few small touches: change any **poster** you don't like, a screensaver with artwork and fun facts,
and Benflix **updates itself** quietly in the background.

## How to install

> **You need:** a login for a Jellyfin server (ask whoever runs it), and a device with Android 6 or newer.

<p align="center">
  <img src="docs/readme/install-steps.png" alt="Step 1: install Downloader. Step 2: type the code 2488986. Step 3: install and sign in." width="100%"/>
</p>

### 📺 On a TV (Google TV, Android TV or Fire TV)

1. **Allow apps from "unknown sources"** on your TV, so it lets you install Benflix. How-to guides:
   [Google TV / Chromecast](https://androidtvnews.com/unknown-sources-chromecast-google-tv/) ·
   [Fire TV](https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/)
2. Install the free **Downloader** app from your TV's app store.
3. Open Downloader, type the code **`2488986`** and press **Go**.
4. When it finishes downloading, choose **Install**.
5. Open Benflix. At home it finds your server by itself; otherwise type the server address, then sign in.
   (Already signed in on your phone? Use **Sign in with my phone** instead of typing your password.)

> [!NOTE]
> The newest Fire TV sticks that run **Vega OS** can't install outside apps, so Benflix won't work on them.
> Older Fire TV sticks and Fire TV Cubes are fine.

### 📱 On an Android phone

1. On your phone, open [aftv.news/2488986](https://aftv.news/2488986) (or tap the **Download** badge at the top of this page and pick **Benflix.apk**).
2. When it finishes downloading, tap it. If your phone asks, allow your browser to install apps.
3. Open Benflix and sign in.

### 🌐 In a web browser

Open your server's address on port `8097` (ask whoever runs the server) in any web browser and sign in.
Nothing to install.

### 🔄 Getting updates

You only install Benflix once. New versions download quietly in the background. When one is ready, a dot
shows on your picture; open your picture's menu and pick **Help and updates** to see what's new and
install it in one click.

### 🍎 iPhone and Apple TV

There is no Apple app. iPhone users can open **web Benflix** in Safari, or use
[Swiftfin](https://github.com/jellyfin/Swiftfin) or [Infuse](https://firecore.com/infuse) to watch from the same Jellyfin server.

## For the server admin

If you run the server, Benflix has a few extra tools that only admins see:

- **Server page** in the app and in web Benflix: who's watching, your libraries, downloads, problems and chores,
  with fix-it buttons, a **Benflix versions** list for every TV and phone, and a way to send a message to one person
- **Requests** to approve or decline, for movies, shows and music
- **Poster fixer** to swap bad posters one at a time

**What the server needs:**

| | Needed for | |
|---|---|---|
| [Jellyfin](https://jellyfin.org/) (version 12 recommended) | Everything | Required |
| Benflix helper (in the [`server`](server) folder) | Games, Live TV, web Benflix, Skip intro, music extras, fun facts | Recommended |
| [Seerr / Jellyseerr](https://github.com/seerr-team/seerr) | Asking for movies and shows | Optional |
| [Lidarr](https://lidarr.audio/) | Asking for music | Optional |
| [RomM](https://github.com/rommapp/romm) | Your game library | Optional |
| [Trailarr](https://github.com/nandyalu/trailarr) | Trailers on title pages | Optional |

To set up (or update) the Benflix helper, run this in the Unraid terminal (**Tools > Terminal**). It's safe to run again any time:

```bash
curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/benflix-games-helper-setup.sh | bash
```

## Credits and license

Benflix is a modified version (a "fork") of **[Wholphin](https://github.com/damontecres/Wholphin)** by
[damontecres](https://github.com/damontecres), an open-source Android TV app for Jellyfin.
A huge thank you to Wholphin and its contributors. Most of the hard work under the hood is theirs.
If you want a polished Jellyfin app from the Play Store, go get Wholphin!

Thanks also to:
- The [Jellyfin](https://jellyfin.org/) team for the media server
- The official [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) app, which some of Wholphin's playback code is adapted from
- The [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin), [RomM](https://github.com/rommapp/romm), [Seerr](https://github.com/seerr-team/seerr), [Lidarr](https://lidarr.audio/), [iptv-org](https://github.com/iptv-org/iptv) and all the other open-source projects this app uses

Benflix is not connected to, or endorsed by, Netflix, Jellyfin or Wholphin.

### License

Like Wholphin, Benflix is released under the **GNU General Public License, version 2** (GPL-2.0).
The full text is in the [LICENSE](LICENSE) file. In short: you are free to use, change and share this
app, but if you share a changed version you must share its source code under the same license
and keep these credits.

## For developers

Benflix is built automatically by GitHub Actions every time a change lands on `main`. The newest APK
is always on the [latest-build release](https://github.com/TheBenPsych/MyStream/releases/tag/latest-build).
Every build is also checked for code that would crash on older TVs and phones, and a test robot clicks
through every screen on a TV and a phone emulator.
For build details, see [DEVELOPMENT.md](DEVELOPMENT.md) (written for Wholphin, mostly still accurate).
The pictures on this page live in [`docs/readme`](docs/readme).
