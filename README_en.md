<!--suppress ALL -->

<h1 align="center">Ella Music</h1>

<p align="center">
  <b>An Android Music Player Inspired by MIUI / HyperOS</b>
</p>

<p align="center">
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/v/release/Kifranei/Ella?style=flat&color=6750A4" alt="Version"></a>
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/downloads/Kifranei/Ella/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/Kifranei/Ella/commits"><img src="https://img.shields.io/github/last-commit/Kifranei/Ella?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/Kifranei/Ella/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Kifranei/Ella?style=flat" alt="License"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/Document-Chinese-red.svg" alt="CN"></a>
</p>

<p align="center">
  <b>Local Music · Local Playlists · WebDAV · Dynamic Player UI · Word-by-Word Lyrics · Floating Lyrics · Status Bar Lyrics</b>
</p>

---

## Native Build Notes

Ella packages prebuilt arm64-v8a native libraries by default. Normal `assembleDebug` / `assembleFastRelease` builds do not rebuild FFmpeg or lyrico-audiotag.

To refresh native outputs, run these scripts manually:

- `.\build_ffmpeg.ps1`
- `.\build_lyrico_taglib.ps1`

The scripts enable `-PellaBuildNative=true`, rebuild the `.so` files, and copy them into each module's `src/main/jniLibs/arm64-v8a/` directory.

---

## ✨ Overview

**Ella Music** is an Android music player built with **Jetpack Compose, Miuix, and AndroidX Media3**.

It focuses on local music playback while integrating local playlists, WebDAV remote libraries, LX Music API online sources, LRC / Enhanced LRC / TTML / Lyricify lyric parsing, dynamic playback pages, immersive player UI, floating desktop lyrics, Lyricon integration, SuperLyricApi lyric publishing, Lyric Getter API raw-lyric transmission, Flyme / AOSP ticker lyrics, Bluetooth lyrics, lyric card sharing, AI song interpretation, FFmpeg extended decoding, application logs, backup and restore, and music library analytics.

The overall UI and interaction design are inspired by **MIUI / HyperOS**, aiming to provide a lightweight, modern, lyric-focused music playback experience on Android.

---

## 🚀 Features

### 🎵 Local Music Playback

- Supports local music scanning, searching, playback, and folder browsing, with switching between Android media library scanning and custom folder scanning.
- The home dashboard, albums, folders, and artists pages support searching, sorting, fast indexing, and multi-selection management.
- Supports album pages, artist pages, song lists, the current playback queue, mini player, and immersive playback page.
- Supports local playlist import / export and is compatible with Salt Player playlists, M3U, and M3U8.
- Supports reading audio file rating tags and automatically generating a Five-Star Songs playlist.
- Album recognition considers both album name and album artist; when album artist is empty, tracks are grouped by album name. Album details support disc grouping, track numbers, copyright display, and sorting by disc number / track number.
- Artist pages support songs, participated albums, and released albums tabs, and include album artists in the artist system.
- Supports browsing the music library by genre, year, composer, and lyricist, with configurable artist / genre separators and a do-not-split list.
- Chinese, Japanese, Korean, and other CJK titles can participate in A-Z sorting through romanized sort keys, with cached sort keys to reduce home page stutter.
- Supports music library analytics, including play count ranking, listening duration ranking, format distribution, and quality distribution.

### 🖼 Playback Page & Dynamic Covers

- The top area of the playback page uses a 1:1 cover / video cover layout.
- Supports a dark dynamic flowing-light background, and long titles automatically scale down to preserve complete information.
- Dynamic video covers can be matched from the current album folder, public Movies directory, and app-specific directory.
- Supports album-level video cover reuse, allowing multiple songs in the same album to share one video.
- Supports pull-down-to-dismiss on the playback page; both pull-down and direct back navigation include translation, scale, background blur, and top corner radius animations.
- Provides a cover page, lyrics page, and landscape lyrics page.
- Mini lyrics show the previous, current, and next lines to reduce cases where lyrics are incorrectly treated as having no following line.
- Supports bottom action menus, playback queue panels, playback mode switching, and progress control.

### 🎤 Lyric Experience

- Supports LRC, Enhanced LRC, TTML, and Lyricify lyric parsing.
- Supports word-by-word lyrics, translations, romanization / pronunciation, background words, `x-bg` background vocals, and TTML duet layouts.
- Supports reading external LRC files and embedded lyrics.
- Provides fallback compatibility for common Chinese lyric encodings.
- Supports sustained glow, continuous word-by-word sweep, line-break lyric progress, tapping lyrics to seek, and double-tapping lyrics to play / pause.
- Word-by-word lyrics keep the current character clearly highlighted; short characters avoid wide feathered sweep effects that can cause blurring.
- Improves TTML / Lyricify recognition for word-by-word endings, spaces, translations, original text, and annotations, reducing missing words and line-position mismatches.
- Supports long-press lyric selection for generating lyric cards, with customizable Via information in app preferences.
- The lyrics page font can be selected from system font previews or imported from TTF / OTF / TTC font files.

### 🪟 Floating Lyrics & System Lyrics

- Supports a floating desktop lyrics window.
- Text shadows improve readability on bright wallpapers.
- Supports double-tap controls, auto-hide, and dragging constrained within the screen bounds.
- Floating lyrics controls include play / pause, previous track, next track, font size, lock, and close.
- Supports lyric barrage.
- Supports publishing lyric data to the SuperLyric ecosystem through SuperLyricApi.
- Supports passing raw lyric text through the Lyric Getter API.
- Supports Flyme / AOSP ticker lyrics notifications and Bluetooth lyrics.
- Supports passing translations to Samsung floating lyrics while preserving word-by-word spacing, trailing words, and dual-line display structure as much as possible.

### 🌐 WebDAV & LX Online Music

- Supports WebDAV configuration, Digest authentication, connection testing, remote directory browsing, and remote audio playback.
- The WebDAV entry is located in the home page online music area for convenient access alongside LX Music.
- Supports importing and centrally managing multiple LX Music API sources.
- Supports importing sources from URLs or local JS files.
- Supports online search, online playback, cover display, lyric retrieval, and downloads to `Music/Ella/`.
- Online queues skip unplayable items to reduce playback interruptions.

### 🎚 Playback, Decoding & Audio Quality

- Supports reading audio tags from WAV, FLAC, M4A, OGG, OPUS, and other formats, with fallback fixes for garbled tags.
- Provides system decoding, FFmpeg decoding, and automatic decoding modes. Automatic decoding is used by default; switch to the FFmpeg decoder if a file cannot be decoded.
- The FFmpeg extended decoder improves compatibility with ALAC / AAC and other M4A formats.
- Supports ReplayGain volume normalization.
- Supports sleep timer, stop after current track, playback speed, pitch control, queue clearing, disabling audio focus, and shuffle settings.
- Supports audio output switching, an option for the previous button to replay the current song, and listening history grouped by date.
- Supports displaying Dolby Atmos, Master, Apple Lossless, Hi-Res, Lossless, HQ, LQ, and other quality labels.
- *Specifications such as 24-bit / 96 kHz in music library lists are unified into the Hi-Res (MQ) system.*
- Improves metadata fallback and quality recognition for WAV, ALAC / M4A, 24-bit / 96 kHz, and related formats.

### 🎨 UI & Settings

- MIUI / HyperOS-style settings pages and UI components based on Miuix.
- Uses a floating bottom navigation bar by default, allowing direct switching between the home, music library, and settings pages.
- Music library search, sorting, and multi-selection states prioritize closing the current state when the Back button is pressed.
- All major song lists use a unified more menu, song information, add to playlist, play next, share, edit tags, and delete actions.
- MiniPlayer covers automatically rotate during playback and support circular progress plus song / lyric switching animations.
- Album and artist detail pages provide large titles, gradient backgrounds, and unified information layouts.
- Supports theme switching and common playback, lyrics, scanning, decoder, external music tag scraping software, and lyric timing software settings.
- Supports a GitHub software update page, app log viewer, Logcat / network log collection, copying / sending detailed logs, automatic log retention, and app data backup.
- Supports Shortcuts, with default entries for the music library, playlists, and folders.
- Song information pages support viewing audio tags, modified time, added time, 163 key decryption information, alias / comment, and jumping to NetEase Cloud Music song, album, or artist pages.
- Supports AI interpretation generated from song information and lyrics through OpenAI-compatible APIs.

---

## 📱 Requirements

| Item | Requirement                                                             |
|:--|:------------------------------------------------------------------------|
| Android Version | Android 12 / API 31 or higher                                            |
| Target SDK | Android 17 / API 37                                                     |
| Default ABI | `arm64-v8a`                                                             |
| Network | Required for WebDAV, LX online sources, and online lyrics               |
| Video Permission | Android 13+ may require video media permission for dynamic video covers |
| Overlay Permission | Required when using floating lyrics                                     |
| Notification Permission | Required on Android 13 and above                                        |

---

## 📦 Download

Download the latest version from [Releases](https://github.com/Kifranei/Ella/releases).

Recommended first-time setup:

1. Install Ella Music.
2. Grant music file access permission and choose a scanning mode (media library scanning or custom folder scanning).
3. After scanning completes, the app is ready to use. To display lyrics on other pages, enable the option in the settings page.
4. Configure WebDAV manually if using a remote library.
---

## 🖼 Dynamic Video Covers

Dynamic video covers are used in the top area of the immersive playback page. Album-level configuration is recommended:

```text
Music/Album Name/
├── cover.mp4
├── Song A.flac
├── Song B.flac
└── Song C.flac
```

All songs in the same album can share one video, avoiding duplicate video files for each song.

Centralized management is supported:

```text
Movies/Ella/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

Single-file configuration is also supported:

```text
Music/Song File Name.m4a
Music/Song File Name.mp4
```

The actual matching order depends on the implementation. It typically checks the song's local folder first, then song / album videos under DynamicCovers, and finally uses the global fallback video.

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

If these variables are not set, the build uses `release.jks` in the project root. If no usable release keystore is available, the release build fails directly to avoid accidentally producing a release package signed with a debug key.

---

## 🎧 FFmpeg

Prebuilt FFmpeg static libraries are located at:

```text
ffmpeg-decoder/src/main/jni/ffmpeg/android-libs
```

To rebuild on Windows, run:

```powershell
.\build_ffmpeg.ps1
```

The script builds FFmpeg through WSL using the Linux Android NDK.

---

## 🧩 Ecosystem

| Category | Capability                                                                                                                                     |
|:--|:-----------------------------------------------------------------------------------------------------------------------------------------------|
| Local Music | Scanning, searching, playback, custom folders, folder browsing, local playlists, five-star songs, album / artist management                    |
| Remote Music | WebDAV Digest authentication, directory browsing, and playback                                                                                 |
| Online Music | LX Music API source import, search, streaming playback, downloads                                                                              |
| Dynamic Covers | Album folder videos, album videos, song videos, fallback videos                                                                                |
| Lyrics | LRC, Enhanced LRC, TTML, Lyricify, word-by-word lyrics, translation, romanization, background vocals                                           |
| System Lyrics | Floating lyrics, lyric barrage, SuperLyricApi, Lyric Getter API, Flyme status bar lyrics (Ticker notification), Bluetooth lyrics               |
| Decoding | Media3, system decoder, FFmpeg extended decoder                                                                                                |
| Audio Metadata | TagLib, Lyrico-Audiotag, embedded and external lyrics, 163 key decryption, alias / comment, quality label display                              |
| Analytics | Format distribution, quality distribution, play count ranking, listening duration ranking, listening history                                   |
| UI | Jetpack Compose, Miuix, floating bottom navigation, home dashboard, update page, immersive playback page, landscape lyrics, lyric card sharing |

---

## 🧱 Open Source Projects

| Project | Purpose                                                                         |
|:--|:--------------------------------------------------------------------------------|
| [Miuix](https://github.com/compose-miuix-ui/miuix) | MIUI / HyperOS-style Compose UI components                                      |
| [AndroidX Media3](https://github.com/androidx/media) | Playback, media sessions, and ExoPlayer FFmpeg extension                        |
| [FFmpeg](https://ffmpeg.org) | Software decoding for ALAC and other audio formats (LGPL-2.1)                   |
| [Lyricon](https://github.com/proify/lyricon) | Lyric Provider API and status bar lyrics                                        |
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi) | Publishes lyric data to the SuperLyric ecosystem (LGPL-2.1)                     |
| [LyricGetter-API](https://github.com/xiaowine/Lyric-Getter-Api) | Passes raw lyric text to the Lyric Getter ecosystem / API adaptation (LGPL-2.1) |
| [Lyrico](https://github.com/Replica0110/Lyrico) | Tag read and log page interaction reference                                     |
| [163KeyDecrypter](https://github.com/lycode404/163KeyDecrypter) | NetEase Cloud Music 163 key decryption flow reference                           |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android / Kotlin TagLib bindings                                                |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Liquid glass and background blur effects                                        |
| [Coil](https://github.com/coil-kt/coil) | Compose image loading                                                           |
| [QuickJS wrapper Android](https://github.com/HarlonWang/quickjs-wrapper) | Runtime for LX Music API JavaScript sources                                     |
| [LX Music Mobile](https://github.com/lyswhut/lx-music-mobile) | LX Music API compatibility implementation and reference                         |
| [accompanist-lyrics-core](https://github.com/6xingyv/accompanist-lyrics-core) | Lyric parsing and TTML / LRC structure reference (Apache-2.0)                   |

---

## 📄 License

The Ella Music main project is open-sourced under **Apache-2.0**. Third-party components retain their own licenses; see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

---

## 👥 Credits

- **Codex (GPT-5.5)** — Primary development and code collaboration since version 1.0.2.
- **Mimo-V2.5-Pro** — Main development contributor for early versions 1.0.0 to 1.0.1.
- **BetterLyrics** — Visual reference for blurred cover backgrounds and lyric display.
- **SPlayer** — Visual reference for playback page animations and lyric experience.
- **Lyrico** — Reference for external tag editor adaptation and log page interaction.
- **LX Music Mobile** — Provides LX Music API compatibility implementation and testing reference.
- **Light Cone Music** — Interface design and feature implementation reference.
- Thanks to Miuix, Media3, FFmpeg, Lyricon, SuperLyricApi, LyricGetter-API, Lyrico, 163KeyDecrypter, Jaudiotagger, Kyant TagLib, Backdrop, Coil, accompanist-lyrics-core, and other open source projects used by Ella Music.

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

The Ella Music main project is licensed under **Apache-2.0**. Third-party components retain their own licenses; see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
