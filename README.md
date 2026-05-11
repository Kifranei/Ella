# Ella Music

一款简洁的本地音乐播放器，基于 Jetpack Compose、Miuix 和 Media3 构建，面向 MIUI/HyperOS 风格体验做了界面和交互适配。

English documentation: [README_en.md](README_en.md)

## 功能特性

- 本地音乐扫描、搜索、播放和文件夹浏览
- 首页、专辑页、文件夹页和艺人页搜索、排序、快速定位与多选管理
- 中日韩标题按拉丁化读音参与 A-Z 排序，并缓存排序键以减少首页卡顿
- 专辑页、歌手页、歌曲列表、当前播放队列、迷你播放条和沉浸式播放页
- WebDAV 音乐库配置、连接测试、远程目录浏览和远程音频播放
- WebDAV 路径支持中文、空格等字符，远程目录会记住最近打开位置并缓存结果
- LX Music API 多源导入和集中管理，支持 URL 或本地 JS 文件导入、单源搜索、在线播放和下载到 `Music/Ella/`
- 在线音乐封面显示和 Kuwo 歌词获取
- 歌曲库分析，包含格式占比、音质占比、播放次数排行和听歌时长排行
- LRC / 增强 LRC / TTML 歌词解析，支持逐字歌词、翻译、背景词、x-bg 背景人声和 TTML 对唱布局
- 外置 LRC 与内嵌歌词读取，支持常见中文歌词编码回退
- 歌词页字体自定义，可在设置中预览系统字体或导入 TTF / OTF / TTC 字体文件
- 桌面歌词悬浮窗，支持双击显示控制按钮、自动隐藏、屏幕范围拖动、TTML 双行和左右对齐
- Lyricon 词幕、SuperLyric、Flyme / AOSP Ticker 歌词通知和蓝牙歌词适配
- WAV、FLAC、M4A、OGG、OPUS 等格式标签读取
- 系统、FFmpeg、自动三种解码模式，FFmpeg 扩展解码器补充 ALAC / M4A 等格式兼容
- 播放页支持定时暂停、播放完当前歌曲后暂停、倍速播放、变调播放和在线歌曲下载
- ReplayGain 音量均衡
- Miuix 标准设置项、主题切换和默认悬浮底栏

## 构建

```bash
git clone https://github.com/Kifranei/Ella.git
cd Ella
./gradlew assembleDebug
```

Release 包会优先读取环境变量 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。如果未设置环境变量，会使用项目根目录的 `release.jks`，默认别名 `release`。

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
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi) | SuperLyric 歌词发布 API |
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger) | 音频标签、内嵌歌词与封面读取 |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android/Kotlin TagLib 绑定 |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | 液态玻璃和背景模糊效果 |
| [Coil](https://github.com/coil-kt/coil) | Compose 图片加载 |

## 致谢

- **Codex (GPT-5.5)** — 1.0.2 至今主要开发与代码协作
- **BetterLyrics** — 播放页封面模糊背景和歌词视觉效果参考
- **SPlayer** — 播放页动效和歌词体验参考
- Retro Music Player 的 jaudiotagger 标签读取思路
- Miuix、Media3、FFmpeg、Lyricon、SuperLyricApi、Jaudiotagger、Kyant TagLib、Backdrop、Coil 等开源项目
- **Mimo-V2.5-Pro** — 1.0.0 至 1.0.1 早期版本主要开发

## 许可证

Ella Music 使用 GPL-3.0-or-later 开源许可协议发布，详见 [LICENSE](LICENSE)。
