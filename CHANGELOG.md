# 更新日志

## 1.0.5 - 2026-05-09

### 亮点

- 新增 WebDAV 音乐库配置、连接测试、远程目录浏览和远程音频播放。
- 新增歌手页，支持从播放页跳转查看歌手歌曲与相关专辑。
- 新增当前播放列表能力，歌曲列表、专辑详情和文件夹详情支持将歌曲加入当前播放列表。
- 新增歌曲库分析页，显示格式占比、音质占比、播放次数排行和听歌时长排行。
- 播放页新增音频格式、位深、采样率和声道信息显示。

### WebDAV 与存储源

- WebDAV 配置支持地址、用户名、密码保存。
- WebDAV 支持 `PROPFIND` 读取远程目录，显示目录与可播放音频文件。
- WebDAV 远程音频支持直接播放和加入当前播放列表。
- 保留系统目录入口，可配合 rclone/RCX/rcloud sync 这类 DocumentProvider 间接接入网络目录。

### 歌手与曲库

- 新增歌手详情页，可查看歌手歌曲、相关专辑并播放全部。
- 多歌手拆分兼容 `/`、`&`、`、`、`;`、`；`、`,`、`，`、`+`、`×`、`feat.`、`ft.`、`with` 等常见分隔写法。
- 歌曲列表和文件夹详情页新增 `Dolby`、`Master`、`HR`、`SQ`、`HQ`、`LQ` 等音质标签。
- 歌曲库分析新增格式饼图、音质饼图、格式体积统计、播放次数排行和听歌时长排行。

### 播放与歌词

- 播放页新增当前播放列表弹窗，可直接切换队列歌曲。
- 随机播放、列表循环和单曲循环整合为一个播放模式按钮，并恢复为直观图标显示。
- 修复 TTML `x-bg` 背景歌词残留括号的问题。
- 改进 TTML 背景歌词、翻译歌词、逐字歌词和词幕/Ticker 同步处理。
- 播放状态恢复后同步恢复词幕和 Ticker 歌词推送。

### 界面

- 正在播放页继续优化封面取色、模糊背景、迷你歌词和辉光进度条。
- 迷你播放器和悬浮导航栏改进模糊/浮动表现。
- 设置页、关于页和主题选择控件继续向 MIUIX 风格靠拢。

### 构建

- 版本号更新至 1.0.5。

## 1.0.4 - 2026-05-08

### 亮点

- 新增文件夹详情页排序、搜索与快速索引导航。
- 重构正在播放页，新增基于封面的模糊背景、迷你歌词和发光进度条。
- 新增歌词行点击跳转，以及封面页迷你歌词。
- 新增曲库首次扫描进度显示。
- 新增迷你播放器左右滑动切换上一首/下一首。

### 播放与曲库

- 新增文件夹详情页搜索、排序与快速索引导航，适配大文件夹场景。
- 新增首次扫描进度显示，避免曲库扫描时看起来像卡住。

### 歌词

- 修复 TTML 英文音节片段如 `ne` / `ver` 在应用内歌词页被分开动画显示的问题。
- 新增歌词页点击歌词行跳转播放进度。
- 新增封面页迷你歌词，支持翻译和 TTML 背景和声文本。
- 为逐词歌词新增长音发光效果和 Apple Music 风格间奏点。

### 界面

- 底部导航改为默认悬浮高斯模糊样式。
- 新增迷你播放器左右滑动切换上一首/下一首。
- 更新正在播放页的封面/歌词切换与翻译控制。
- 正在播放页新增基于封面的模糊动态背景，以及缓慢旋转的封面背景。
- 修复高屏幕设备上旋转封面背景边缘露出的问题。
- 更新关于页贡献者致谢：BetterLyrics、SPlayer 和 Mimo-V2.5-Pro。

### 构建

- 版本号更新至 1.0.4。


# Changelog

## 1.0.5 - 2026-05-09

### Highlights

- Added WebDAV library configuration, connection testing, remote directory browsing, and remote audio playback.
- Added artist detail pages with songs and related albums.
- Added current-queue support, with add-to-queue actions from song lists, album details, and folder details.
- Added library analytics for format distribution, quality distribution, play-count ranking, and listen-time ranking.
- Added audio format, bit depth, sample rate, and channel information on the now-playing page.

### WebDAV And Storage Sources

- WebDAV configuration now saves URL, username, and password.
- WebDAV uses `PROPFIND` to browse remote directories and show playable audio files.
- Remote WebDAV audio can be played directly or added to the current queue.
- Kept the system directory entry for DocumentProvider-based integrations such as rclone, RCX, or rcloud sync.

### Artists And Library

- Added artist detail pages with play-all, songs, and related albums.
- Artist splitting now supports common separators such as `/`, `&`, `、`, `;`, `；`, `,`, `，`, `+`, `×`, `feat.`, `ft.`, and `with`.
- Song lists and folder details now show quality badges such as `Dolby`, `Master`, `HR`, `SQ`, `HQ`, and `LQ`.
- Library analytics now include format pie charts, quality pie charts, format size summaries, play-count rankings, and listen-time rankings.

### Playback And Lyrics

- Added a current-queue popup on the now-playing page.
- Combined shuffle, repeat-all, and repeat-one into one playback-mode button while keeping recognizable icons.
- Fixed leftover parentheses in TTML `x-bg` background-vocal lyrics.
- Improved TTML background vocals, translations, word-level lyrics, and Lyricon/Ticker synchronization.
- Restored Lyricon and Ticker lyric pushes after playback-state restoration.

### UI

- Continued polishing cover-derived colors, blurred backgrounds, mini lyrics, and the glow seek bar on the now-playing page.
- Improved blur/floating behavior for the mini player and floating navigation bar.
- Continued aligning Settings, About, and theme selection controls with MIUIX styling.

### Build

- Bumped version to 1.0.5.

## 1.0.4 - 2026-05-08

### Highlights

- Added folder-detail sorting, search, and fast index navigation.
- Rebuilt the now-playing screen with cover-derived blurred background, mini lyrics, and a glow seek bar.
- Added lyric-line click seeking and mini lyrics on the cover page.
- Added first-scan progress reporting for the music library.
- Added swipe gestures to the mini player for previous/next track switching.

### Playback And Library

- Added folder-detail search, sorting, and fast index navigation for large folders.
- Added first-scan progress reporting so the library no longer appears frozen while scanning.

### Lyrics

- Fixed TTML English syllable fragments such as `ne` / `ver` being animated separately in the in-app lyric page.
- Added lyric-line click seeking on the lyric page.
- Added mini lyrics on the cover page, including translation and TTML background-vocal text.
- Added long-sustain glow and Apple Music-style interlude dots for word-level lyrics.

### UI

- Changed bottom navigation to a default floating Gaussian-blur style.
- Added swipe gestures to the mini player for previous/next track switching.
- Updated the now-playing cover/lyric toggle and translation controls.
- Added cover-derived blurred dynamic backgrounds and slow cover-background rotation on the now-playing screen.
- Fixed rotated cover-background edge exposure on tall screens.
- Updated About page contributor credits for BetterLyrics, SPlayer, and Mimo-V2.5-Pro.

### Build

- Bumped version to 1.0.4.
