# Ella Music

<p align="center">
  <b>An Android Music Player Inspired by MIUI / HyperOS</b>
</p>

<p align="center">
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/v/release/Kifranei/Ella?style=flat&color=6750A4" alt="Version"></a>
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/downloads/Kifranei/Ella/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/Kifranei/Ella/commits"><img src="https://img.shields.io/github/last-commit/Kifranei/Ella?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/Kifranei/Ella/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Kifranei/Ella?style=flat" alt="License"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/文档-中文-red.svg" alt="CN"></a>
</p>

<p align="center">
  <b>Local Music · WebDAV · LX / MusicFree Online Sources · Dynamic Player UI · Word-by-Word Lyrics · Floating Lyrics · Status Bar Lyrics</b>
</p>

---

## ✨ Overview

**Ella Music** is an Android music player built with **Jetpack Compose, Miuix, and AndroidX Media3**.

It focuses on local music playback while integrating WebDAV remote libraries, LX Music API and MusicFree online sources, LRC / Enhanced LRC / TTML / Lyricify lyric parsing, dynamic playback pages, immersive player UI, floating desktop lyrics, Lyricon integration, SuperLyric integration, Flyme / AOSP ticker lyrics, Bluetooth lyrics, FFmpeg extended decoding, application logs, backup & restore, and music library analytics.

The overall UI and interaction design are heavily inspired by **MIUI / HyperOS**, aiming to provide a lightweight, modern, lyric-focused music experience on Android.

---

## 🚀 Features

### 🎵 Local Music Playback

* Supports local music scanning, searching, playback, and folder browsing, with a switch between Android MediaStore and custom-folder scanning.
* Dashboard, albums, folders, and artists pages support searching, sorting, fast indexing, and multi-selection management.
* Includes album pages, artist pages, song lists, playback queue, mini player, and immersive playback page.
* Album recognition considers album name and album artist, falling back to album name only when album artist is missing; album pages sort by disc number and track number by default.
* Artist pages provide songs, participated albums, and release albums tabs, with album artists included in the artist system.
* Supports library browsing by genre, year, composer, and lyricist, with configurable artist/genre separators and protected names.
* CJK titles (Chinese / Japanese / Korean) can participate in A-Z sorting through Latinized sort keys with caching to reduce homepage lag.
* Includes music library analytics such as play count ranking, listening duration ranking, format distribution, and quality distribution.

### 🖼 Playback UI & Dynamic Covers

* Playback page uses a 1:1 cover / video cover layout.
* Supports dynamic flowing backgrounds in dark mode.
* Long titles automatically scale down to preserve full information.
* Dynamic video covers can be matched from album folders, public Movies directories, and app-specific directories.
* Supports album-level video reuse across multiple tracks.
* Pull-down dismissal follows the finger and includes offset, scaling, background blur, and rounded corner animations.
* Provides cover page, lyric page, and landscape lyric page.
* Mini lyrics display previous, current, and next lines to reduce false "end-of-lyrics" situations.
* Includes bottom action menu, playback queue panel, playback mode switching, and progress controls.

### 🎤 Lyric Experience

* Supports LRC, Enhanced LRC, TTML, and Lyricify parsing.
* Supports word-by-word lyrics, translations, romanization / pronunciation, background vocals, `x-bg` background vocals, and TTML duet layouts.
* Supports external LRC files and embedded lyrics.
* Provides fallback compatibility for common Chinese lyric encodings.
* Supports sustained glow effects, continuous word-by-word sweep animations, line progress animations, lyric-click seeking, and double-tap play/pause on lyric lines.
* Word-by-word lyrics keep the current character crisp; short words avoid wide feathered sweep effects that can look blurry.
* Improves TTML / Lyricify handling for trailing words, spaces, translations, original text, and pronunciation alignment.
* Lyric fonts can be selected from system font previews or imported via TTF / OTF / TTC files.

### 🪟 Floating & System Lyrics

* Supports floating desktop lyrics.
* Text shadows improve readability on bright wallpapers.
* Supports double-tap controls, auto-hide, and screen-bound dragging.
* Floating lyric controls include play/pause, previous/next track, font size, lock, and close.
* Supports Lyric Barrage.
* Supports SuperLyric, Flyme / AOSP ticker lyrics, and Bluetooth lyrics.
* Supports Lyric Getter raw lyric transmission.
* Supports Samsung floating lyric translation transmission while preserving spacing, trailing words, and dual-line layouts whenever possible.

### 🌐 WebDAV, LX & MusicFree Online Music

* Supports WebDAV configuration, Digest authentication, connection testing, remote directory browsing, and remote audio playback.
* The WebDAV entry is integrated into the homepage online music section alongside LX Music and MusicFree.
* Supports importing and managing multiple LX Music API sources.
* Supports importing sources from URLs or local JS files.
* Supports online search, streaming playback, cover display, lyric retrieval, and downloads to `Music/Ella/`.
* Supports MusicFree plugin management, plugin marketplace import, online search, lazy-loaded queues, and downloads.
* Online queues automatically skip unavailable tracks to reduce playback interruption.

### 🎚 Playback, Decoding & Audio Quality

* Supports WAV, FLAC, M4A, OGG, OPUS, and other metadata parsing, with fallbacks for garbled tags.
* Provides system decoder, FFmpeg decoder, and automatic decoding modes.
* FFmpeg extended decoder improves compatibility for ALAC / AAC M4A formats.
* Supports ReplayGain volume normalization.
* Supports sleep timer, stop-after-current-track, playback speed, pitch control, queue clearing, audio focus, and shuffle settings.
* Supports audio output switching, previous-button replay-current behavior, and date-based listening history.
* Displays Dolby Atmos, Master, Apple Lossless, Hi-Res, Lossless, HQ, and LQ quality badges.
* *24-bit / 96 kHz specifications are unified under the Hi-Res (MQ) category.*
* Improves metadata fallback and quality recognition for WAV, ALAC / M4A, 24-bit / 96 kHz, and related formats.

### 🎨 UI & Settings

* Built with Miuix components inspired by MIUI / HyperOS.
* Uses a floating bottom navigation bar by default.
* Search, sort, and multi-selection states in the music library are dismissed by Back before leaving the page.
* MiniPlayer cover rotates automatically during playback and supports circular progress and song/lyric transition animations.
* Album and artist pages feature large headers, gradient backgrounds, and unified layouts.
* Supports theme switching and playback, lyric, scanning, and decoder settings.
* Includes a GitHub-based software update page, application log viewer, Logcat / network log collection, detailed log sharing, automatic log retention, backup & restore, and richer diagnostics.
* Song info pages can display audio tags and decoded 163 key information, with shortcuts to NetEase Music songs, albums, and artists.

---

## 📱 Requirements

| Item                    | Requirement                                                       |
| :---------------------- | :---------------------------------------------------------------- |
| Android Version         | Android 10.0 / API 29 or higher                                   |
| Target SDK              | Android API 37                                                    |
| Default ABI             | `arm64-v8a`                                                       |
| Network                 | Required for WebDAV, LX / MusicFree sources, and online lyrics    |
| Video Permission        | Android 13+ may require video media permission for dynamic covers |
| Overlay Permission      | Required for floating lyrics                                      |
| Notification Permission | Required on Android 13+                                           |

---

## 📦 Download

Download the latest version from [Releases](https://github.com/Kifranei/Ella/releases).

Recommended first-time setup:

1. Install Ella Music.
2. Grant music file access permission, or enable auto music scanning on startup in settings and reopen the app.
3. After scanning completes, the app is ready to use.
   To display lyrics on other pages, enable the corresponding option in settings.
4. Configure WebDAV manually if using remote libraries.
5. Import LX Music API sources or MusicFree plugins manually if online music is needed.
   No API sources or plugins are bundled with the app.

---

## 🖼 Dynamic Video Covers

Dynamic video covers are used in the immersive playback page header area.

Recommended album-level structure:

```text
Music/Album Name/
├── cover.mp4
├── Song A.flac
├── Song B.flac
└── Song C.flac
```

All songs in the same album can share one video.

Centralized management is also supported:

```text
Movies/Ella/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

Actual matching order depends on implementation details.
Typically, the app first checks the local song folder, then DynamicCovers song/album videos, and finally falls back to a global default video.

---

## 🛠 Build

```bash
git clone https://github.com/Kifranei/Ella.git
cd Ella
./gradlew :app:assembleDebug -PellaAbi=arm64-v8a
```

Windows PowerShell:

```powershell
git clone https://github.com/Kifranei/Ella.git
cd Ella
.\gradlew.bat :app:assembleDebug -PellaAbi=arm64-v8a
```

Release builds prioritize the following environment variables:

```bash
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

If these variables are not provided, the project will attempt to use `release.jks` in the repository root.
If no release keystore is available, release builds fail instead of producing a debug-signed release APK.

---

## 🎧 FFmpeg

Prebuilt FFmpeg static libraries are located at:

```text
ffmpeg-decoder/src/main/jni/ffmpeg/android-libs
```

To rebuild FFmpeg on Windows:

```powershell
.\build_ffmpeg.ps1
```

The script uses WSL and the Android NDK to build FFmpeg.

---

## 🧩 Ecosystem

| Category       | Capability                                                                                               |
| :------------- | :------------------------------------------------------------------------------------------------------- |
| Local Music    | Scanning, searching, playback, custom folders, folder browsing, album / artist management                |
| Remote Music   | WebDAV Digest authentication, browsing, and playback                                                      |
| Online Music   | LX Music API / MusicFree import, search, streaming, downloads                                            |
| Dynamic Covers | Album folder videos, album videos, song videos, fallback videos                                          |
| Lyrics         | LRC, Enhanced LRC, TTML, Lyricify, word-by-word lyrics, translation, romanization, background vocals     |
| System Lyrics  | Floating lyrics, lyric barrage, SuperLyric, Lyric Getter, Flyme ticker lyrics, Bluetooth lyrics          |
| Decoding       | Media3, system decoder, FFmpeg extended decoder                                                          |
| Metadata       | TagLib, Jaudiotagger, embedded/external lyrics, 163 key decoding, quality badges                         |
| Analytics      | Format distribution, quality distribution, play count ranking, listening duration ranking, history       |
| UI             | Jetpack Compose, Miuix, floating bottom navigation, dashboard, update page, immersive playback page, landscape lyrics |

---

## 🧱 Open Source Projects

| Project                                                                  | Purpose                                                 |
| :----------------------------------------------------------------------- | :------------------------------------------------------ |
| [Miuix](https://github.com/compose-miuix-ui/miuix)                       | MIUI / HyperOS style Compose UI components              |
| [AndroidX Media3](https://github.com/androidx/media)                     | Playback, media sessions, ExoPlayer FFmpeg extension    |
| [FFmpeg](https://ffmpeg.org)                                             | Software decoding for ALAC and other formats            |
| [Lyricon](https://github.com/proify/lyricon)                             | Lyric Provider API and status bar lyrics                |
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi)                 | SuperLyric publishing API                               |
| [SuperLyric](https://github.com/HChenX/SuperLyric)                       | System lyric module and status-bar lyric ecosystem reference |
| [Lyric Getter](https://github.com/xiaowine/Lyric-Getter)                 | Lyric Getter raw lyric display and API integration      |
| [Lyrico](https://github.com/Replica0110/Lyrico)                          | Tag editor integration and log page interaction reference |
| [163KeyDecrypter](https://github.com/lycode404/163KeyDecrypter)          | Reference for NetEase Music 163 key decryption          |
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger)                   | Audio tags, embedded lyrics, embedded covers            |
| [Kyant TagLib](https://github.com/Kyant0/TagLib)                         | Android / Kotlin TagLib bindings                        |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)           | Liquid glass and blur effects                           |
| [Coil](https://github.com/coil-kt/coil)                                  | Compose image loading                                   |
| [QuickJS Android](https://github.com/HarlonWang/quickjs-wrapper-android) | Runtime for LX Music API / MusicFree JavaScript sources |
| [LX Music Mobile](https://github.com/lyswhut/lx-music-mobile)            | LX Music API compatibility reference                    |
| [MusicFree](https://github.com/maotoumao/MusicFree)                      | MusicFree plugin protocol and compatibility reference   |

---

## 📄 License

Ella Music is licensed under **AGPL-3.0-or-later**.

Because the project contains compatibility implementations related to the MusicFree plugin protocol and runtime adaptation, modified distributions must comply with AGPL source disclosure requirements.

---

## 👥 Credits

* **Codex (GPT-5.5)** — Primary development and code collaboration since version 1.0.2.
* **Mimo-V2.5-Pro** — Main development contributor for versions 1.0.0 to 1.0.1.
* **BetterLyrics** — Visual inspiration for blurred cover backgrounds and lyric presentation.
* **SPlayer** — Visual inspiration for playback animations and lyric experience.
* **Lyrico** — Reference for external tag editor integration and log page interaction.
* **Retro Music Player** — Reference for jaudiotagger-based metadata reading.
* Special thanks to Miuix, Media3, FFmpeg, Lyricon, SuperLyric, SuperLyricApi, Lyric Getter, Lyrico, 163KeyDecrypter, Jaudiotagger, Kyant TagLib, Backdrop, Coil, and all other open source projects used by Ella Music.

---

## ⭐ Star History

<p align="center">
  <a href="https://www.star-history.com/#Kifranei/Ella&Date">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=Kifranei/Ella&type=Date&theme=dark" />
      <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=Kifranei/Ella&type=Date" />
      <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=Kifranei/Ella&type=Date" width="600" />
    </picture>
  </a>
</p>

---

## 👀 Visitor Count

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_ella?theme=gelbooru-h" alt="Visitor Count" />
</p>

---

## 📄 License

Ella Music includes compatibility code related to MusicFree, therefore the license has been adjusted to **AGPL-3.0-or-later**. See [LICENSE](LICENSE) for details.
