# Ella Music

A clean local music player built with Jetpack Compose, Miuix, and Media3, with UI and interaction details tuned for a MIUI/HyperOS-style experience.

Chinese documentation: [README.md](README.md)

## Features

- Local music scanning, search, playback, and folder browsing
- Album pages, song lists, mini player, and immersive now-playing screen
- LRC / enhanced LRC parsing with word-level lyric display
- External LRC and embedded lyric reading, including common Chinese lyric encoding fallbacks
- Lyricon provider support and AOSP ticker lyric notifications
- WAV, FLAC, M4A, OGG, OPUS, and other audio tag reading
- FFmpeg extension decoder for ALAC / M4A compatibility
- ReplayGain volume normalization
- Miuix settings, theme switching, and optional liquid glass bottom navigation

## Build

```bash
git clone https://github.com/Kifranei/Ella.git
cd Ella
./gradlew assembleDebug
```

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
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger) | Audio tags, embedded lyrics, and embedded artwork |
| [TagLib](https://github.com/taglib/taglib) | Fallback audio tag reading |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android/Kotlin TagLib binding |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Liquid glass and backdrop blur effects |
| [Coil](https://github.com/coil-kt/coil) | Image loading for Compose |
| [AndroidX DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) | Settings persistence |
| [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Async tasks and Flow state streams |

## Credits

- **Mimo-V2.5-Pro** — Lead development
- **GPT-5.5** — Code collaboration and bug fixing
- Retro Music Player's jaudiotagger-based tag reading approach
- Miuix, Media3, FFmpeg, Lyricon, Jaudiotagger, TagLib, Backdrop, Coil, and the other open-source projects used by Ella Music

## License

```
Copyright (c) 2026 Ella Music. All rights reserved.
```
