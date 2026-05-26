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
  <b>本地音乐 · 本地歌单 · WebDAV · 动态播放页 · 逐词歌词 · 桌面歌词 · 状态栏歌词</b>
</p>

---

## Native 构建说明

Ella 默认打包仓库内预编译的 arm64-v8a native 库，普通 `assembleDebug` / `assembleFastRelease` 不会重新编译 FFmpeg 或 lyrico-audiotag。

如果需要更新 native 产物，请手动运行：

- `.\build_ffmpeg.ps1`
- `.\build_lyrico_taglib.ps1`

脚本会启用 `-PellaBuildNative=true` 重新生成 `.so`，并复制到对应模块的 `src/main/jniLibs/arm64-v8a/`。

---

## ✨ 项目简介

**Ella Music** 是一款基于 **Jetpack Compose、Miuix 和 AndroidX Media3** 构建的 Android 音乐播放器。

它以本地音乐播放为核心，同时集成了本地歌单、WebDAV 远程曲库、LX Music API 在线音源、LRC / 增强 LRC / TTML / Lyricify 歌词解析、动态播放页、沉浸式播放页、桌面歌词悬浮窗、Lyricon 集成、SuperLyricApi 歌词发布、Lyric Getter API 原文歌词传递、Flyme / AOSP 跑马灯歌词、蓝牙歌词、歌词卡片分享、AI 歌曲解读、FFmpeg 扩展解码、应用日志、备份恢复以及音乐库统计分析等能力。

应用整体采用接近 **MIUI / HyperOS** 的视觉与交互风格，目标是在 Android 上提供轻量、现代、以歌词体验为重点的音乐播放体验。

---

## 🚀 功能特性

### 🎵 本地音乐播放

- 支持本地音乐扫描、搜索、播放和文件夹浏览，可在 Android 媒体库与自定义文件夹扫描模式之间切换。
- 首页仪表盘、专辑、文件夹、艺术家页面支持搜索、排序、快速索引和多选管理。
- 支持专辑页、艺术家页、歌曲列表、当前播放队列、迷你播放器和沉浸式播放页。
- 支持本地歌单导入 / 导出，兼容 Salt Player 歌单、M3U 和 M3U8。
- 支持读取音频文件评分标签，并自动生成“五星歌曲”歌单。
- 专辑识别会同时考虑专辑名与专辑艺术家，专辑艺术家为空时按专辑名归组；专辑详情支持 Disc 分组、音轨编号、版权显示和按碟号 / 音轨排序。
- 艺术家页支持歌曲、参与专辑和发行专辑分页，并将专辑艺术家纳入艺术家系统。
- 支持按流派、年份、作曲家和作词家浏览音乐库，并可配置艺术家 / 流派分隔符与“不拆分名单”。
- 中文、日文、韩文等 CJK 标题可通过拉丁化排序键参与 A-Z 排序，并缓存排序键以减少首页卡顿。
- 支持音乐库统计分析，包括播放次数排行、听歌时长排行、格式分布和音质分布。

### 🖼 播放页与动态封面

- 播放页顶部采用 1:1 封面 / 视频封面布局。
- 支持深色动态流光背景，长标题会自适应缩小以保留完整信息。
- 动态视频封面可从当前专辑文件夹、公共 Movies 目录和应用专属目录中匹配。
- 支持专辑级视频封面复用，同一专辑内多首歌曲可共用同一个视频。
- 支持跟手下拉关闭播放页，下拉和直接返回都带有位移、缩放、背景模糊和顶部圆角动画。
- 提供封面页、歌词页和横屏歌词页。
- 迷你歌词显示上一行、当前行和下一行，降低误判为无后续歌词的情况。
- 支持底部操作菜单、播放队列面板、播放模式切换和进度控制。

### 🎤 歌词体验

- 支持 LRC、增强 LRC、TTML 和 Lyricify 歌词解析。
- 支持逐词歌词、翻译、罗马音 / 发音、背景词、`x-bg` 背景人声和 TTML 对唱布局。
- 支持读取外部 LRC 文件和内嵌歌词。
- 对常见中文歌词编码提供回退兼容。
- 支持延音辉光、连续逐词扫光、换行歌词进度、点击歌词跳转播放进度和双击歌词播放 / 暂停。
- 逐字歌词会保持当前字清晰高亮，短字不使用会导致发虚的宽羽化扫光。
- 改进 TTML / Lyricify 逐词尾部、空格、翻译、原文和注音识别，减少缺字与行位错乱。
- 支持长按歌词选择多句生成歌词卡片，可在应用偏好里自定义 Via 信息。
- 歌词页字体支持从系统字体预览选择，也支持导入 TTF / OTF / TTC 字体文件。

### 🪟 桌面歌词与系统歌词

- 支持桌面歌词悬浮窗。
- 通过文字阴影提高亮色壁纸上的可读性。
- 支持双击控制、自动隐藏和限制在屏幕范围内拖动。
- 桌面歌词控制项包括播放 / 暂停、上一首、下一首、字体大小、锁定和关闭。
- 支持 词幕。
- 支持通过 SuperLyricApi 向 SuperLyric 生态发布歌词数据。
- 支持通过 Lyric Getter API 传递歌词原文。
- 支持 Flyme / AOSP 跑马灯歌词通知和蓝牙歌词。
- 支持三星悬浮歌词翻译传递，并尽量保留逐词空格、尾部词和双行显示结构。

### 🌐 WebDAV 与 LX 在线音乐

- 支持 WebDAV 配置、Digest 认证、连接测试、远程目录浏览和远程音频播放。
- WebDAV 入口位于首页在线音乐区域，便于和 LX Music 一起访问。
- 支持多源 LX Music API 导入和集中管理。
- 支持从 URL 或本地 JS 文件导入音源。
- 支持在线搜索、在线播放、封面显示、歌词获取，以及下载到 `Music/Ella/`。
- 在线队列会跳过不可播放项目，减少播放中断。

### 🎚 播放、解码与音质

- 支持 WAV、FLAC、M4A、OGG、OPUS 等音频标签读取，并对乱码标签提供回退修正。
- 提供系统解码、FFmpeg 解码和自动解码模式，默认使用自动解码模式，如无法解码请切换 FFmpeg 解码器。
- FFmpeg 扩展解码器可提高 ALAC / AAC 等 M4A 格式兼容性。
- 支持 ReplayGain 音量标准化。
- 支持睡眠定时、播完当前停止、播放速度、音调控制、队列清空、禁用音频焦点和随机播放设置。
- 支持音频输出切换器、上一曲按钮重放当前歌曲选项和按日期查看听歌历史。
- 支持 Dolby Atmos、Master、Apple Lossless、Hi-Res、Lossless、HQ、LQ 等音质标签展示。
- *音乐库列表中 24-bit / 96 kHz 等规格统一归入 Hi-Res（MQ）体系。*
- 改进 WAV、ALAC / M4A、24-bit / 96 kHz 等元数据回退与音质识别。

### 🎨 界面与设置

- 基于 Miuix 的 MIUI / HyperOS 风格设置页和 UI 组件。
- 默认使用悬浮底部导航栏，首页、音乐库和设置页可直接切换。
- 音乐库搜索、排序和多选状态支持返回键优先关闭当前状态。
- 所有主要歌曲列表使用统一的更多菜单、歌曲信息、添加到歌单、下一首播放、分享、编辑标签和删除操作。
- MiniPlayer 封面会随播放而自动旋转，支持环形进度条和歌曲 / 歌词切换动画。
- 专辑与艺术家详情页提供大标题、渐变背景和统一信息布局。
- 支持主题切换，以及常见播放、歌词、扫描、解码器、外部音乐标签刮削软件和歌词打轴软件设置。
- 支持 GitHub 软件更新页、应用日志查看器、Logcat / 网络日志采集、复制 / 发送详细日志、自动日志保留、应用数据备份。
- 支持Shortcuts，默认提供音乐库、歌单和文件夹入口。
- 歌曲信息页支持查看音频标签、修改时间、添加时间、163 key 解密信息、alias / comment，并可跳转到网易云歌曲、专辑或歌手页面。
- 支持使用 OpenAI 兼容接口根据歌曲信息和歌词生成 AI 解读。

---

## 📱 运行要求

| 项目 | 要求                              |
|:--|:--------------------------------|
| Android 版本 | Android 12 / API 31 或更高版本       |
| Target SDK | Android 17 / API 37               |
| 默认 ABI | `arm64-v8a`                     |
| 网络 | WebDAV、LX 在线音源和在线歌词需要网络         |
| 视频权限 | Android 13+ 使用动态视频封面时可能需要视频媒体权限 |
| 悬浮窗权限 | 使用桌面歌词时需要                       |
| 通知权限 | Android 13 及以上需要                |

---

## 📦 下载

请从 [Releases](https://github.com/Kifranei/Ella/releases) 下载最新版本。

首次使用建议流程：

1. 安装 Ella Music。
2. 授予音乐文件访问权限，并选择扫描模式（媒体库扫描或自定义文件夹扫描）。
3. 扫描完成即可使用。 如需在其他页面上显示歌词，请前往设置页面开启。
4. 如使用远程曲库，请自行配置 WebDAV。
---

## 🖼 动态视频封面

动态视频封面用于沉浸式播放页顶部区域。推荐使用专辑级配置：

```text
Music/专辑名称/
├── cover.mp4
├── 歌曲A.flac
├── 歌曲B.flac
└── 歌曲C.flac
```

同一专辑中的所有歌曲可以共用同一个视频，避免为每首歌曲重复存放视频文件。

支持集中管理：

```text
Movies/Ella/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

也支持单文件配置：
```text
Music/歌曲文件名.m4a
Music/歌曲文件名.mp4
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

如果未设置这些变量，会使用项目根目录下的 `release.jks`；如果没有可用的 release keystore，则 release 构建会直接失败，避免误产出 debug 签名的 release 包。

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

| 分类 | 能力                                                           |
|:--|:-------------------------------------------------------------|
| 本地音乐 | 扫描、搜索、播放、自定义文件夹、文件夹浏览、本地歌单、五星歌曲、专辑 / 艺术家管理               |
| 远程音乐 | WebDAV Digest 认证、目录浏览及播放                                      |
| 在线音乐 | LX Music API 音源导入、搜索、串流播放、下载                                |
| 动态封面 | 专辑文件夹视频、专辑视频、歌曲视频、fallback 视频                                |
| 歌词 | LRC、增强 LRC、TTML、Lyricify、逐词歌词、翻译、罗马音、背景人声                    |
| 系统歌词 | 桌面歌词、词幕、SuperLyricApi、Lyric Getter API、FLYme 状态栏歌词（Ticker 通知）、蓝牙歌词 |
| 解码 | Media3、系统解码器、FFmpeg 扩展解码器                                    |
| 音频元数据 | TagLib、Jaudiotagger、读取内嵌和外置歌词、163 key 解密、alias / comment、显示音质标签 |
| 统计分析 | 格式分布、音质分布、播放次数排行、听歌时长排行、听歌历史                                |
| UI | Jetpack Compose、Miuix、悬浮底部导航、首页仪表盘、更新页、沉浸式播放页、横屏歌词、歌词卡片分享  |

---

## 🧱 开源项目

| 项目 | 用途                                           |
|:--|:---------------------------------------------|
| [Miuix](https://github.com/compose-miuix-ui/miuix) | MIUI / HyperOS 风格 Compose UI 组件              |
| [AndroidX Media3](https://github.com/androidx/media) | 播放、媒体会话和 ExoPlayer FFmpeg 扩展                 |
| [FFmpeg](https://ffmpeg.org) | 用于 ALAC 等音频格式的软件解码（LGPL-2.1）                 |
| [Lyricon](https://github.com/proify/lyricon) | Lyric Provider API 和状态栏歌词                    |
| [SuperLyricApi](https://github.com/HChenX/SuperLyricApi) | 用于向 SuperLyric 生态发布歌词数据（LGPL-2.1）            |
| [LyricGetter-API](https://github.com/xiaowine/Lyric-Getter-Api) | 用于向 Lyric Getter 生态传递歌词原文 / API 适配（LGPL-2.1） |
| [Lyrico](https://github.com/Replica0110/Lyrico) | 标签读取与日志页面交互参考                                |
| [163KeyDecrypter](https://github.com/lycode404/163KeyDecrypter) | 网易云音乐 163 key 解密流程参考                         |
| [Kyant TagLib](https://github.com/Kyant0/TagLib) | Android / Kotlin TagLib 绑定                   |
| [Kyant Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | 液态玻璃与背景模糊效果                                  |
| [Coil](https://github.com/coil-kt/coil) | Compose 图片加载                                 |
| [QuickJS wrapper Android](https://github.com/HarlonWang/quickjs-wrapper) | 运行 LX Music API JavaScript 音源                |
| [LX Music Mobile](https://github.com/lyswhut/lx-music-mobile) | LX Music API 兼容实现与参考                         |
| [accompanist-lyrics-core](https://github.com/6xingyv/accompanist-lyrics-core) | 歌词解析与 TTML / LRC 结构参考（Apache-2.0）            |

---

## 📄 许可证

Ella Music 主项目以 **Apache-2.0** 协议开源。第三方组件保留其各自许可证，详见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

---

## 👥 致谢

- **Codex (GPT-5.5)** — 自 1.0.2 起至今的主要开发与代码协作。
- **Mimo-V2.5-Pro** — 负责早期 1.0.0 到 1.0.1 版本的主要开发。
- **BetterLyrics** — 为模糊封面背景和歌词展示提供视觉参考。
- **SPlayer** — 为播放页动效和歌词体验提供视觉参考。
- **Lyrico** — 为外部标签编辑器适配和日志页面交互提供参考。
- **LX Music Mobile** — 提供 LX Music API 兼容实现与测试参考。
- **光锥音乐** — 界面设计与功能实现参考。
- 感谢 Ella Music 所使用的 Miuix、Media3、FFmpeg、Lyricon、SuperLyricApi、LyricGetter-API、Lyrico、163KeyDecrypter、Kyant TagLib、Backdrop、Coil、accompanist-lyrics-core 以及其它开源项目。

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
  <img src="https://count.getloli.com/get/@kifranei_ella?theme=gelbooru-h" alt="Visitor Count" />
</p>

---

## 📄 许可证

Ella Music 主项目以 **Apache-2.0** 协议开源。第三方组件保留其各自许可证，详见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。
