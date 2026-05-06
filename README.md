# Ella Music

一款简洁的本地音乐播放器，基于 Jetpack Compose、Miuix 和 Media3 构建，面向 MIUI/HyperOS 风格体验做了界面和交互适配。

English documentation: [README_en.md](README_en.md)

## 功能特性

- 本地音乐扫描、搜索、播放和文件夹浏览
- 专辑页、歌曲列表、迷你播放条和沉浸式播放页
- LRC / 增强 LRC 歌词解析，支持逐字歌词显示
- 外置 LRC 与内嵌歌词读取，支持常见中文歌词编码回退
- Lyricon 词幕适配和 AOSP Ticker 歌词通知
- WAV、FLAC、M4A、OGG、OPUS 等格式标签读取
- FFmpeg 扩展解码器，补充 ALAC / M4A 等格式兼容
- ReplayGain 音量均衡
- Miuix 标准设置项、主题切换和可选液态玻璃底栏

## 构建

```bash
git clone https://github.com/Kifranei/Ella.git
cd Ella
./gradlew assembleDebug
```

FFmpeg 静态库已经放在 `ffmpeg-decoder/src/main/jni/ffmpeg/android-libs`。如需重新编译，可在 Windows 上运行：

```powershell
.\build_ffmpeg.ps1
```

脚本会通过 WSL 使用 Linux 版 Android NDK 编译 FFmpeg。

## 开源项目

| 项目 | 用途 |
|---|---|
| [Miuix](https://github.com/miuix-kmp/miuix) | MIUI/HyperOS 风格 Compose UI 组件 |
| [AndroidX Media3](https://github.com/androidx/media) | 播放、媒体会话和 ExoPlayer FFmpeg 扩展 |
| [FFmpeg](https://ffmpeg.org) | ALAC 等音频格式的软件解码 |
| [Lyricon](https://github.com/proify/lyricon) | 词幕 Provider API 与状态栏歌词 |
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger) | 音频标签、内嵌歌词与封面读取 |
| [TagLib](https://github.com/taglib/taglib) | 音频标签兜底读取 |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android/Kotlin TagLib 绑定 |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | 液态玻璃和背景模糊效果 |
| [Coil](https://github.com/coil-kt/coil) | Compose 图片加载 |
| [AndroidX DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) | 设置持久化 |
| [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 异步任务和 Flow 状态流 |

## 致谢

- **Mimo-V2.5-Pro** — 主要开发
- **GPT-5.5** — 代码协作与问题修复
- Retro Music Player 的 jaudiotagger 标签读取思路
- Miuix、Media3、FFmpeg、Lyricon、Jaudiotagger、TagLib、Backdrop、Coil 等开源项目

## 许可证

```
Copyright (c) 2026 Ella Music. All rights reserved.
```
