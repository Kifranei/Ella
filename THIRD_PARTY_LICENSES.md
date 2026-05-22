# Third-Party Licenses

Ella Music main project is licensed under Apache-2.0. Third-party components keep their own licenses.

| Project | Purpose | License | Link | Notes |
|:--|:--|:--|:--|:--|
| Miuix | MIUI / HyperOS style Compose UI components | Apache-2.0 | https://github.com/compose-miuix-ui/miuix | Gradle artifacts: `top.yukonga.miuix.kmp:*` |
| AndroidX Media3 | Playback, media session, ExoPlayer, FFmpeg extension integration | Apache-2.0 | https://github.com/androidx/media | Gradle artifacts: `androidx.media3:*` |
| Lyricon | Lyric Provider API and status-bar lyric integration | Apache-2.0 | https://github.com/proify/lyricon | Gradle artifact: `io.github.proify.lyricon:provider` |
| LyriCo | External tag-editor and log-page interaction reference | Apache-2.0 | https://github.com/Replica0110/Lyrico | Referenced integration target / UI reference |
| LX Music Mobile | LX Music API compatibility reference | Apache-2.0 | https://github.com/lyswhut/lx-music-mobile | Reference for LX online source compatibility |
| Kyant Backdrop | Liquid glass and background blur effects | Apache-2.0 | https://github.com/Kyant0/AndroidLiquidGlass | Gradle artifact: `io.github.kyant0:backdrop` |
| Kyant TagLib | Android / Kotlin TagLib binding | Apache-2.0 | https://github.com/Kyant0/TagLib | Gradle artifact: `io.github.kyant0:taglib` |
| Coil | Compose image loading | Apache-2.0 | https://github.com/coil-kt/coil | Gradle artifacts: `io.coil-kt.coil3:*` |
| quickjs-wrapper Android | JavaScript runtime wrapper used for LX Music API sources | Apache-2.0 | https://github.com/HarlonWang/quickjs-wrapper | Gradle artifact: `wang.harlon.quickjs:wrapper-android`; this is the traceable upstream repository for the Android wrapper artifact |
| 163KeyDecrypter | NetEase Music 163 key decoding reference | MIT | https://github.com/lycode404/163KeyDecrypter | Used as decoding reference |
| FFmpeg | Software decoding for ALAC and other audio formats | LGPL-2.1 | https://ffmpeg.org | Local FFmpeg build uses an LGPL-2.1 configuration; nonfree and version3 options are disabled |
| LyricGetter-API | API for passing raw lyric text to the Lyric Getter ecosystem | LGPL-2.1 | https://github.com/xiaowine/Lyric-Getter-Api | Gradle artifact: `com.github.HChenX:Lyric-Getter-Api` |
| SuperLyricApi | API for publishing lyric data to the SuperLyric ecosystem | LGPL-2.1 | https://github.com/HChenX/SuperLyricApi | Gradle artifact: `com.github.HChenX:SuperLyricApi` |
| Jaudiotagger | Audio tags, embedded lyrics, and embedded cover reading | LGPL-2.1 | https://github.com/Adonai/jaudiotagger | Gradle artifact: `com.github.Adonai:jaudiotagger` |

LGPL-2.1 components are listed separately here so the Apache-2.0 license of the Ella Music main project is not confused with the licenses of bundled or linked third-party components.
