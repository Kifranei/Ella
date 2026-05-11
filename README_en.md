# Ella Music

A clean local music player built with Jetpack Compose, Miuix, and Media3, with UI and interaction details tuned for a MIUI/HyperOS-style experience.

Chinese documentation: [README.md](README.md)

## Features

- Local music scanning, search, playback, and folder browsing
- Home, album, folder, and artist search, sorting, fast indexing, and multi-select management
- CJK titles participate in A-Z sorting through Latinized sort keys, with cached keys to reduce home-screen stalls
- Album pages, artist pages, song lists, current queue, mini player, and immersive now-playing screen
- WebDAV library configuration, connection testing, remote directory browsing, and remote audio playback
- WebDAV paths support Chinese characters, spaces, and encoded characters, with last-path restoration and cached folder results
- Multi-source LX Music API import and centralized source management from URLs or local JS files, with single-source search, online playback, and downloads to `Music/Ella/`
- Online artwork display and Kuwo lyric fetching for online music
- Library analytics for format distribution, quality distribution, play-count ranking, and listen-time ranking
- LRC / enhanced LRC / TTML parsing with word-level lyrics, translations, x-bg background vocals, and TTML duet layout
- External LRC and embedded lyric reading, including common Chinese lyric encoding fallbacks
- Custom lyric-page fonts from system-font previews or imported TTF / OTF / TTC font files in Settings
- Desktop lyric floating overlay with double-tap controls, auto-hide, screen-bounded dragging, TTML two-line display, and left / right alignment
- Lyricon provider, SuperLyric, Flyme / AOSP ticker lyric notifications, and Bluetooth lyric support
- WAV, FLAC, M4A, OGG, OPUS, and other audio tag reading
- System, FFmpeg, and Auto decoder modes, with the FFmpeg extension decoder improving ALAC / M4A compatibility
- Now-playing controls for sleep timer, stop-after-current, playback speed, pitch, and online song downloads
- ReplayGain volume normalization
- Miuix settings, theme switching, and default floating bottom navigation

## Build

```bash
git clone https://github.com/Kifranei/Ella.git
cd Ella
./gradlew assembleDebug
```

Release builds first read `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` from the environment. If they are not set, the project-root `release.jks` is used with the default alias `release`.

Prebuilt FFmpeg static libraries are stored in `ffmpeg-decoder/src/main/jni/ffmpeg/android-libs`. To rebuild them on Windows, run:

```powershell
.\build_ffmpeg.ps1
```

The script builds FFmpeg through WSL with the Linux Android NDK.

## Open Source

| Project | Purpose |
|---|---|
| [Miuix](https://github.com/miuix-kmp/miuix) | MIUI/HyperOS-style Compose UI components |
| [AndroidX Media3](https://github.com/androidx/media) | Playback, media session, and ExoPlayer FFmpeg extension |
| [FFmpeg](https://ffmpeg.org) | Software decoding for ALAC and other audio formats |
| [Lyricon](https://github.com/proify/lyricon) | Lyric provider API and status-bar lyrics |
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi) | SuperLyric publishing API |
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger) | Audio tags, embedded lyrics, and embedded artwork |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android/Kotlin TagLib binding |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Liquid glass and backdrop blur effects |
| [Coil](https://github.com/coil-kt/coil) | Image loading for Compose |

## Credits

- **Codex (GPT-5.5)** — Lead development and code collaboration since 1.0.2
- **BetterLyrics** — Visual reference for the blurred cover background and lyric presentation
- **SPlayer** — Visual reference for now-playing motion and lyric experience
- Retro Music Player's jaudiotagger-based tag reading approach
- Miuix, Media3, FFmpeg, Lyricon, SuperLyricApi, Jaudiotagger, Kyant TagLib, Backdrop, Coil, and the other open-source projects used by Ella Music
- **Mimo-V2.5-Pro** — Lead development for early 1.0.0 to 1.0.1 builds

## License

Ella Music is licensed under GPL-3.0-or-later. See [LICENSE](LICENSE).
