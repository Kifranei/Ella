<!--suppress ALL -->

<h1 align="center">Ella Music</h1>

<p align="center">
  <b>一款贴近 MIUI / HyperOS 体验的 Android 音乐播放器</b>
</p>

<p align="center">
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/v/release/Kifranei/Ella?style=flat&color=6750A4" alt="Version"></a>
  <a href="https://github.com/Kifranei/Ella/releases"><img src="https://img.shields.io/github/downloads/Kifranei/Ella/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/Kifranei/Ella/commits"><img src="https://img.shields.io/github/last-commit/Kifranei/Ella?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/Kifranei/Ella/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Kifranei/Ella?style=flat" alt="License"></a>
  <a href="README_en.md"><img src="https://img.shields.io/badge/Document-English-blue.svg" alt="EN"></a>
</p>

<p align="center">
  <b>本地音乐 · WebDAV · LX / MusicFree 在线音源 · 动态播放页 · 逐词歌词 · 桌面歌词 · 状态栏歌词</b>
</p>

---

## ✨ 项目简介

**Ella Music** 是一款基于 **Jetpack Compose、Miuix 和 AndroidX Media3** 构建的 Android 音乐播放器。

它以本地音乐播放为核心，同时集成了 WebDAV 远程曲库、LX Music API 与 MusicFree 在线音源、LRC / 增强 LRC / TTML / Lyricify 歌词解析、动态播放页、沉浸式播放页、桌面歌词悬浮窗、Lyricon 集成、SuperLyric 集成、Flyme / AOSP 跑马灯歌词、蓝牙歌词、FFmpeg 扩展解码、应用日志、备份恢复以及音乐库统计分析等能力。

应用整体采用接近 **MIUI / HyperOS** 的视觉与交互风格，目标是在 Android 上提供轻量、现代、以歌词体验为重点的音乐播放体验。

---

## 🔥 v1.1.0 亮点

- 在线音乐入口重整到首页，LX Music、MusicFree 和 WebDAV 入口更集中。
- MusicFree 插件导入与搜索兼容性增强，适配 `qishui.js`、酷狗、QQ 等常见音源问题。
- 播放页、歌词页、队列面板、专辑详情和艺术家详情继续统一为深色动态视觉体系。
- 歌词解析与外部歌词推送进一步修复，改善逐词 LRC、TTML、罗马音 / 翻译识别、桌面歌词和 SuperLyric / Lyricon 传递。
- 修复 Media3 Controller Future 导致的启动 / 进入设置 ANR，并改进通知栏元数据刷新路径。
- 改进 WAV / ALAC / M4A 元数据读取、专辑归类、音轨排序和音质标签识别。
- 日志页支持详细运行日志、复制、发送分享和自动保留最近 1 / 3 / 7 / 14 / 30 天。
- 默认仅构建 `arm64-v8a`，缩小 release 包体积。

---

## 🚀 功能特性

### 🎵 本地音乐播放

- 支持本地音乐扫描、搜索、播放和文件夹浏览。
- 首页仪表盘、专辑、文件夹、艺术家页面支持搜索、排序、快速索引和多选管理。
- 支持专辑页、艺术家页、歌曲列表、当前播放队列、迷你播放器和沉浸式播放页。
- 专辑识别会同时考虑专辑名与艺术家，避免不同歌手的同名专辑混在一起；专辑详情默认按音轨排序。
- 中文、日文、韩文等 CJK 标题可通过拉丁化排序键参与 A-Z 排序，并缓存排序键以减少首页卡顿。
- 支持音乐库统计分析，包括播放次数排行、听歌时长排行、格式分布和音质分布。

### 🖼 播放页与动态封面

- 播放页顶部采用 1:1 封面 / 视频封面布局。
- 支持深色动态流光背景，长标题会自适应缩小以保留完整信息。
- 动态视频封面可从当前专辑文件夹、公共 Movies 目录和应用专属目录中匹配。
- 支持专辑级视频封面复用，同一专辑内多首歌曲可共用同一个视频。
- 支持下拉关闭播放页，并带有顶部圆角动画。
- 提供封面页、歌词页和横屏歌词页。
- 迷你歌词显示上一行、当前行和下一行，降低误判为无后续歌词的情况。
- 支持底部操作菜单、播放队列面板、播放模式切换和进度控制。

### 🎤 歌词体验

- 支持 LRC、增强 LRC、TTML 和 Lyricify 歌词解析。
- 支持逐词歌词、翻译、罗马音 / 发音、背景词、`x-bg` 背景人声和 TTML 对唱布局。
- 支持读取外部 LRC 文件和内嵌歌词。
- 对常见中文歌词编码提供回退兼容。
- 支持延音辉光、连续逐词扫光、换行歌词进度和点击歌词跳转播放进度。
- 改进 TTML / Lyricify 逐词尾部、空格、翻译、原文和注音识别，减少缺字与行位错乱。
- 歌词页字体支持从系统字体预览选择，也支持导入 TTF / OTF / TTC 字体文件。

### 🪟 桌面歌词与系统歌词

- 支持桌面歌词悬浮窗。
- 默认采用纯文字桌面歌词样式，不显示大面积黑色卡片背景。
- 通过文字阴影提高亮色壁纸上的可读性。
- 支持双击控制、自动隐藏和限制在屏幕范围内拖动。
- 桌面歌词控制项包括播放 / 暂停、上一首、下一首、字体大小、锁定和关闭。
- 支持 Lyricon Provider 集成。
- 支持 SuperLyric、Flyme / AOSP 跑马灯歌词通知和蓝牙歌词。
- 支持三星悬浮歌词翻译传递，并尽量保留逐词空格、尾部词和双行显示结构。

### 🌐 WebDAV、LX 与 MusicFree 在线音乐

- 支持 WebDAV 配置、连接测试、远程目录浏览和远程音频播放。
- WebDAV 路径支持中文、空格和其它特殊字符，并对文件夹结果进行缓存。
- WebDAV 入口位于首页在线音乐区域，便于和 LX Music / MusicFree 一起访问。
- 支持多源 LX Music API 导入和集中管理。
- 支持从 URL 或本地 JS 文件导入音源。
- 支持在线搜索、在线播放、封面显示、酷我歌词获取，以及下载到 `Music/Ella/`。
- 支持 MusicFree 插件管理、插件广场导入、在线搜索、懒加载播放队列和下载。
- 在线队列会跳过不可播放项目，减少播放中断。

### 🎚 播放、解码与音质

- 支持 WAV、FLAC、M4A、OGG、OPUS 等音频标签读取。
- 提供系统解码、FFmpeg 解码和自动解码模式，默认使用 FFmpeg 解码模式。
- FFmpeg 扩展解码器可提高 ALAC / M4A 等格式兼容性。
- 支持 ReplayGain 音量标准化。
- 支持睡眠定时、播完当前停止、播放速度、音调控制、队列清空、音频焦点和随机播放设置。
- 支持音频输出切换器和听歌历史。
- 支持 Dolby Atmos、Dolby 双 D、Master、Apple Lossless、Hi-Res、Lossless、HQ、LQ 等音质标签。
- 音乐库列表中 24-bit / 96 kHz 等规格统一归入 Hi-Res / MQ 体系，播放页保留 Master 展示语义。
- 改进 WAV、ALAC / M4A、24-bit / 96 kHz 等元数据回退与音质识别。

### 🎨 界面与设置

- 基于 Miuix 的 MIUI / HyperOS 风格设置页和 UI 组件。
- 默认使用悬浮底部导航栏，首页、音乐库和设置页可直接切换。
- MiniPlayer 支持环形进度条和歌曲 / 歌词切换动画。
- 专辑与艺术家详情页提供大标题、渐变背景和统一信息布局。
- 支持主题切换，以及常见播放、歌词、扫描和解码器设置。
- 支持应用日志查看器、复制 / 发送详细日志、自动日志保留、应用备份和更丰富的诊断信息。

---

## 📱 运行要求

| 项目 | 要求 |
|:--|:--|
| Android 版本 | Android 10.0 / API 29 或更高版本 |
| Target SDK | Android API 37 |
| 默认 ABI | `arm64-v8a` |
| 网络 | WebDAV、LX / MusicFree 在线音源和在线歌词需要网络 |
| 视频权限 | Android 13+ 使用动态视频封面时可能需要视频媒体权限 |
| 悬浮窗权限 | 使用桌面歌词时需要 |
| 通知权限 | Android 13 及以上需要 |

---

## 📦 下载

请从 [Releases](https://github.com/Kifranei/Ella/releases) 下载最新版本。

首次使用建议流程：

1. 安装 Ella Music。
2. 授予音乐文件访问权限。
3. 等待本地音乐扫描完成。
4. 如需使用桌面歌词，授予悬浮窗权限。
5. 如使用远程曲库，配置 WebDAV。
6. 如需要在线音乐，导入 LX Music API 音源或 MusicFree 插件。
7. 根据需要启用 Lyricon / SuperLyric / 跑马灯 / 蓝牙歌词选项。

---

## 🖼 动态视频封面

动态视频封面用于沉浸式播放页顶部区域。推荐使用专辑级配置：

```text
Music/Album Name/
├── cover.mp4
├── Song A.flac
├── Song B.flac
└── Song C.flac
```

同一专辑中的所有歌曲可以共用同一个视频，避免为每首歌曲重复存放视频文件。

也支持集中管理：

```text
Movies/Ella/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

实际匹配顺序以实现为准：通常会先检查歌曲所在本地文件夹，再检查 DynamicCovers 下的歌曲 / 专辑视频，最后使用全局 fallback 视频。

---

## 🛠 构建

```bash
git clone https://github.com/Kifranei/Ella.git
cd Ella
./gradlew :app:assembleDebug -PellaAbi=arm64-v8a
```

Windows PowerShell：

```powershell
git clone https://github.com/Kifranei/Ella.git
cd Ella
.\gradlew.bat :app:assembleDebug -PellaAbi=arm64-v8a
```

Release 构建会优先读取以下环境变量：

```bash
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

如果未设置这些变量，会使用项目根目录下的 `release.jks`；如果没有可用的 release keystore，则 release 构建会回退到 debug 签名配置。

---

## 🎧 FFmpeg

预构建的 FFmpeg 静态库位于：

```text
ffmpeg-decoder/src/main/jni/ffmpeg/android-libs
```

如需在 Windows 上重新构建，请运行：

```powershell
.\build_ffmpeg.ps1
```

该脚本会通过 WSL 使用 Linux Android NDK 构建 FFmpeg。

---

## 🧩 生态能力

| 分类 | 能力 |
|:--|:--|
| 本地音乐 | 扫描、搜索、播放、文件夹浏览、专辑 / 艺术家管理 |
| 远程音乐 | WebDAV 连接测试、目录浏览、远程播放 |
| 在线音乐 | LX Music API / MusicFree 音源导入、搜索、串流播放、下载 |
| 动态封面 | 专辑文件夹视频、专辑视频、歌曲视频、fallback 视频 |
| 歌词 | LRC、增强 LRC、TTML、Lyricify、逐词歌词、翻译、罗马音、背景人声 |
| 系统歌词 | 桌面歌词、Lyricon、SuperLyric、跑马灯通知、蓝牙歌词 |
| 解码 | Media3、系统解码器、FFmpeg 扩展解码器 |
| 音频元数据 | TagLib、Jaudiotagger、封面、标签、内嵌歌词、音质标签 |
| 统计分析 | 格式分布、音质分布、播放次数排行、听歌时长排行 |
| UI | Jetpack Compose、Miuix、悬浮底部导航、首页仪表盘、沉浸式播放页、横屏歌词 |

---

## 🧱 开源项目

| 项目 | 用途 |
|:--|:--|
| [Miuix](https://github.com/compose-miuix-ui/miuix) | MIUI / HyperOS 风格 Compose UI 组件 |
| [AndroidX Media3](https://github.com/androidx/media) | 播放、媒体会话和 ExoPlayer FFmpeg 扩展 |
| [FFmpeg](https://ffmpeg.org) | 用于 ALAC 等音频格式的软件解码 |
| [Lyricon](https://github.com/proify/lyricon) | Lyric Provider API 和状态栏歌词 |
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi) | SuperLyric 发布 API |
| [Jaudiotagger](https://github.com/Adonai/jaudiotagger) | 音频标签、内嵌歌词和内嵌封面 |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android / Kotlin TagLib 绑定 |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | 液态玻璃与背景模糊效果 |
| [Coil](https://github.com/coil-kt/coil) | Compose 图片加载 |
| [QuickJS Android](https://github.com/HarlonWang/quickjs-wrapper-android) | 运行 LX Music API / MusicFree JavaScript 音源 |
| [MusicFree](https://github.com/maotoumao/MusicFree) | MusicFree 插件协议、导入兼容与运行时适配参考 |

---

## 📄 许可证

Ella Music 以 **AGPL-3.0-or-later** 协议开源。由于项目包含对 MusicFree 插件协议与运行时适配的兼容实现，分发修改版本时请遵循 AGPL 相关源码公开要求。

---

## 👥 致谢

- **Codex (GPT-5.5)** — 自 1.0.2 起负责主要开发与代码协作。
- **Mimo-V2.5-Pro** — 负责早期 1.0.0 到 1.0.1 版本的主要开发。
- **BetterLyrics** — 为模糊封面背景和歌词展示提供视觉参考。
- **SPlayer** — 为播放页动效和歌词体验提供视觉参考。
- **Retro Music Player** — 为基于 jaudiotagger 的标签读取方案提供参考。
- 感谢 Ella Music 所使用的 Miuix、Media3、FFmpeg、Lyricon、SuperLyricApi、Jaudiotagger、Kyant TagLib、Backdrop、Coil 以及其它开源项目。

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

## 👀 访问统计

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_ella?theme=moebooru-h" alt="Visitor Count" />
</p>

---

## 📄 许可证

Ella Music 使用了 MusicFree 相关兼容代码，因此许可证已调整为 **AGPL-3.0-or-later**。详见 [LICENSE](LICENSE)。
