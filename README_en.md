<!--suppress ALL -->

<h1 align="center">Ella Music</h1>

<p align="center">
  <b>An Android music player tailored for a MIUI / HyperOS-style experience</b>
</p>

<p align="center">
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/v/release/Kifranei/Ella?style=flat&color=6750A4" alt="Version"></a>
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/downloads/Kifranei/Ella/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/Kifranei/Ella/commits"><img src="https://img.shields.io/github/last-commit/Kifranei/Ella?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/Kifranei/Ella/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Kifranei/Ella?style=flat" alt="License"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/Document-Chinese-red.svg" alt="CN"></a>
</p>

<p align="center">
  <b>Local Music · WebDAV · LX / MusicFree Online Sources · Dynamic Player · Word-level Lyrics · Desktop Lyrics · Status-bar Lyrics</b>
</p>

---

## ✨ Overview

**Ella Music** is an Android music player built with **Jetpack Compose, Miuix, and AndroidX Media3**.

It is centered around local music playback, while also integrating WebDAV remote libraries, LX Music API and MusicFree online sources, LRC / enhanced LRC / TTML / Lyricify lyric parsing, a dynamic now-playing screen, desktop lyric overlay, Lyricon integration, SuperLyric integration, Flyme / AOSP ticker lyrics, Bluetooth lyrics, FFmpeg extension decoding, app logs, backup / restore, and music library analytics.

The app follows a **MIUI / HyperOS-inspired** visual and interaction style, aiming to provide a lightweight, modern, and lyric-focused music experience on Android.

---

## 🔥 v1.1.0 Highlights

- Moved online music access to Home, grouping LX Music, MusicFree, and WebDAV in one place.
- Improved MusicFree plugin import and search compatibility for common sources such as `qishui.js`, Kugou, and QQ.
- Continued unifying Now Playing, Lyrics, Queue, Album detail, and Artist detail pages under the dark dynamic visual system.
- Improved lyric parsing and external lyric delivery for word-level LRC, TTML, romanization / translation recognition, desktop lyrics, SuperLyric, and Lyricon.
- Fixed startup / Settings ANRs caused by Media3 controller Future waiting and improved notification metadata refresh behavior.
- Improved WAV / ALAC / M4A metadata reading, album grouping, track sorting, and quality-label detection.
- Added detailed runtime logs with copy, Send/share, and automatic retention for 1 / 3 / 7 / 14 / 30 days.
- Release builds now default to `arm64-v8a` only to reduce APK size.

---

## 🚀 Features

### 🎵 Local music playback

- Local music scanning, search, playback, and folder browsing.
- Search, sorting, fast indexing, and multi-select management for the dashboard home, album, folder, and artist pages.
- Album pages, artist pages, song lists, current queue, mini player, and immersive now-playing screen.
- Album grouping now considers both album title and artist to avoid merging same-name albums by different artists; album detail pages sort by track number by default.
- CJK titles can participate in A-Z sorting through Latinized sort keys, with cached keys to reduce home-screen stalls.
- Library analytics for play-count ranking, listen-time ranking, format distribution, and quality distribution.

### 🖼 Now-playing and dynamic covers

- 1:1 top cover / video cover layout for the now-playing page.
- Dark dynamic flow background, with adaptive long-title sizing to preserve more full titles.
- Dynamic video covers can be searched from the current album folder, public Movies directory, and app-specific directories.
- Album-level video cover reuse is supported, so one video can be shared by multiple songs in the same album.
- Drag-down dismiss with animated top corner radius.
- Cover page, lyric page, and landscape lyric page.
- Mini lyrics show the previous, current, and next lines to make upcoming lyrics clearer.
- Bottom action menu, queue panel, playback mode switching, and progress control.

### 🎤 Lyric experience

- LRC, enhanced LRC, TTML, and Lyricify lyric parsing.
- Word-level lyrics, translations, romanization / pronunciation, background words, `x-bg` background vocals, and TTML duet layout.
- External LRC and embedded lyric reading.
- Fallback support for common Chinese lyric encodings.
- Sustain glow, continuous word-level sweep, wrapped-line progress, and click-to-seek.
- Improved TTML / Lyricify word tails, spaces, translations, original lines, and pronunciation detection to reduce missing words and line swaps.
- Custom lyric-page fonts from system-font previews or imported TTF / OTF / TTC files.

### 🪟 Desktop and system lyrics

- Desktop lyric floating overlay.
- Text-only desktop lyric style by default, without a large black card background.
- Text shadows improve readability on bright wallpapers.
- Double-tap controls, auto-hide, and screen-bounded dragging.
- Desktop lyric controls for play / pause, previous, next, font size, lock, and close.
- Lyricon Provider integration.
- SuperLyric, Flyme / AOSP ticker lyric notifications, and Bluetooth lyric support.
- Samsung floating lyric translation delivery, with better preservation of word spacing, tail words, and dual-line structure.

### 🌐 WebDAV, LX, and MusicFree online music

- WebDAV configuration, connection testing, remote directory browsing, and remote audio playback.
- WebDAV paths support Chinese characters, spaces, and other special characters, with folder-result caching.
- WebDAV is available from the Home online-music area alongside LX Music and MusicFree.
- Multi-source LX Music API import and centralized source management.
- Import sources from URLs or local JS files.
- Online search, playback, artwork display, Kuwo lyric fetching, and downloads to `Music/Ella/`.
- MusicFree plugin management, plugin-hub imports, online search, lazy playback queues, and downloads.
- Online queues skip unplayable items to reduce playback interruptions.

### 🎚 Playback, decoding, and quality

- WAV, FLAC, M4A, OGG, OPUS, and other audio tag reading.
- System, FFmpeg, and Auto decoder modes. FFmpeg is the default decoder mode.
- FFmpeg extension decoder improves compatibility for ALAC / M4A and other formats.
- ReplayGain volume normalization.
- Sleep timer, stop-after-current, playback speed, pitch control, queue clearing, audio-focus control, and shuffle settings.
- Audio output switcher and listening history.
- Audio quality labels: Dolby Atmos, Dolby double-D, Master, Apple Lossless, Hi-Res, Lossless, HQ, and LQ.
- Library lists classify 24-bit / 96 kHz-style material under the Hi-Res / MQ quality system, while the Now Playing page keeps the Master wording where appropriate.
- Improved metadata fallback and quality recognition for WAV, ALAC / M4A, 24-bit / 96 kHz, and related files.

### 🎨 UI and settings

- MIUI / HyperOS-style settings and UI components based on Miuix.
- Default floating bottom navigation with direct access to Home, Library, and Settings.
- MiniPlayer circular progress ring and song / lyric transition animation.
- Album and artist detail pages with large headers, gradients, and unified information layout.
- Theme switching and common playback, lyric, scanning, and decoder settings.
- In-app log viewer with detailed log copy / Send sharing, automatic retention, app backup support, and richer diagnostics.

---

## 📱 Requirements

| Item | Requirement |
|:--|:--|
| Android version | Android 10.0 / API 29 or later |
| Target SDK | Android API 37 |
| Default ABI | `arm64-v8a` |
| Network | Required for WebDAV, LX / MusicFree online sources, and online lyrics |
| Video permission | Dynamic video covers may require video media permission on Android 13+ |
| Overlay permission | Required for desktop lyrics |
| Notification permission | Required on Android 13 and later |

---

## 📦 Download

Download the latest build from [Releases](https://github.com/Kifranei/Ella/releases).

Recommended first-time setup:

1. Install Ella Music.
2. Grant music file access permission.
3. Wait for the local music scan to complete.
4. Grant overlay permission if you want to use desktop lyrics.
5. Configure WebDAV if you use a remote music library.
6. Import LX Music API sources or MusicFree plugins if you need online music.
7. Enable Lyricon / SuperLyric / Ticker / Bluetooth lyric options as needed.

---

## 🖼 Dynamic video covers

Dynamic video covers are used by the top area of the immersive now-playing page. Album-level configuration is recommended:

```text
Music/Album Name/
├── cover.mp4
├── Song A.flac
├── Song B.flac
└── Song C.flac
```

All songs in the same album can share the same video, avoiding duplicate video files for each track.

Centralized management is also supported:

```text
Movies/Ella/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

The actual matching order follows the implementation: generally, Ella first checks the song's local folder, then song / album videos under DynamicCovers, and finally a global fallback video.

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

Release builds first read the following environment variables:

```bash
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

If these variables are not set, the project-root `release.jks` will be used; when no release keystore is available, the release build falls back to the debug signing configuration.

---

## 🎧 FFmpeg

Prebuilt FFmpeg static libraries are included here:

```text
ffmpeg-decoder/src/main/jni/ffmpeg/android-libs
```

To rebuild them on Windows, run:

```powershell
.\build_ffmpeg.ps1
```

The script builds FFmpeg through WSL with the Linux Android NDK.

---

## 🧩 Ecosystem

| Category | Capability |
|:--|:--|
| Local music | Scanning, search, playback, folder browsing, album / artist management |
| Remote music | WebDAV connection testing, directory browsing, remote playback |
| Online music | LX Music API / MusicFree source import, search, streaming, downloads |
| Dynamic covers | Album-folder video, album video, song video, fallback video |
| Lyrics | LRC, enhanced LRC, TTML, Lyricify, word-level lyrics, translation, romanization, background vocals |
| System lyrics | Desktop lyrics, Lyricon, SuperLyric, ticker notifications, Bluetooth lyrics |
| Decoding | Media3, system decoder, FFmpeg extension decoder |
| Audio metadata | TagLib, Jaudiotagger, artwork, tags, embedded lyrics, quality labels |
| Analytics | Format distribution, quality distribution, play-count ranking, listen-time ranking |
| UI | Jetpack Compose, Miuix, floating bottom navigation, dashboard home, immersive player, landscape lyrics |

---

## 🧱 Open-source projects

| Project | Purpose |
|:--|:--|
| [Miuix](https://github.com/compose-miuix-ui/miuix) | MIUI / HyperOS-style Compose UI components |
| [AndroidX Media3](https://github.com/androidx/media) | Playback, media session, and ExoPlayer FFmpeg extension |
| [FFmpeg](https://ffmpeg.org) | Software decoding for ALAC and other audio formats |
| [Lyricon](https://github.com/proify/lyricon) | Lyric Provider API and status-bar lyrics |
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi) | SuperLyric publishing API |
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger) | Audio tags, embedded lyrics, and embedded artwork |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android / Kotlin TagLib binding |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Liquid glass and backdrop blur effects |
| [Coil](https://github.com/coil-kt/coil) | Image loading for Compose |
| [QuickJS Android](https://github.com/HarlonWang/quickjs-wrapper-android) | Running LX Music API / MusicFree JavaScript sources |
| [MusicFree](https://github.com/maotoumao/MusicFree) | MusicFree plugin protocol, import compatibility, and runtime adaptation reference |

---

## 📄 License

Ella Music is released under **AGPL-3.0-or-later**. Because the project includes compatibility work for MusicFree plugin protocols and runtime behavior, redistributed modified versions should follow the AGPL source-availability requirements.

---

## 👥 Credits

- **Codex (GPT-5.5)** — Lead development and code collaboration since 1.0.2.
- **Mimo-V2.5-Pro** — Lead development for early 1.0.0 to 1.0.1 builds.
- **BetterLyrics** — Visual reference for the blurred cover background and lyric presentation.
- **SPlayer** — Visual reference for now-playing motion and lyric experience.
- **Retro Music Player** — Reference for the jaudiotagger-based tag reading approach.
- Thanks to Miuix, Media3, FFmpeg, Lyricon, SuperLyricApi, Jaudiotagger, Kyant TagLib, Backdrop, Coil, and the other open-source projects used by Ella Music.

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

## 👀 Visit Statistics

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_ella?theme=moebooru-h" alt="Visitor Count" />
</p>

---

## 📄 License

Ella Music includes MusicFree-related compatibility code, so the project license has been changed to **AGPL-3.0-or-later**. See [LICENSE](LICENSE).
