# 更新日志

## 1.0.5 - 2026-05-09

### 亮点

- 新增 WebDAV 音乐库配置、连接测试、远程目录浏览和远程音频播放。
- 新增落雪音乐源入口，支持通过 URL 或本地 JS 文件导入源脚本，并搜索、播放在线歌曲。
- 新增落雪在线歌曲下载功能，解析后保存到系统 `Music/Ella/` 目录。
- 新增歌词字体自定义，可在设置中预览系统字体或导入字体文件并应用到歌词页。
- 新增歌手页、当前播放队列、歌曲库分析和播放页音频信息展示。
- 改进歌曲列表排序，中日韩标题会按拉丁化读音参与 A-Z 排序和快速索引。

### WebDAV

- WebDAV 配置支持地址、用户名、密码保存，并可一键移除。
- WebDAV 目录支持中文、空格等路径的正确显示和访问。
- WebDAV 文件夹页会记住最近打开路径并缓存目录结果，避免切换页面后回到根目录。
- WebDAV 远程音频支持直接播放、加入队列，并缓存内嵌封面和歌词元数据。
- 设置页新增清除远程封面和歌词缓存入口。

### 在线音乐

- 新增落雪源在线音乐页面。
- 支持网络 URL 导入和本地 JS 文件导入落雪 Music API 脚本。
- 支持使用导入源解析播放地址，兼容 `render_api.js` 这类通过远程 API 返回播放地址的源。
- 搜索结果支持封面显示，并使用更清晰的在线封面图。
- 在线 Kuwo 歌曲支持歌词获取，并修复翻译歌词错位显示为上一句的问题。
- 在线歌曲可通过下载按钮保存到 `Music/Ella/`。

### 曲库与播放

- 新增歌手详情页，可查看歌手歌曲、相关专辑并播放全部。
- 新增当前播放列表弹窗，可直接切换队列歌曲。
- 歌曲列表、专辑详情和文件夹详情支持将歌曲加入当前播放队列。
- 歌曲列表和文件夹详情页新增 `Dolby`、`Master`、`HR`、`SQ`、`HQ`、`LQ` 等音质标签。
- 播放页新增音频格式、位深、采样率和声道信息显示。
- 随机播放、列表循环和单曲循环整合为一个播放模式按钮。

### 歌词

- 改进 TTML 背景歌词、翻译歌词、逐字歌词和词幕/Ticker 同步处理。
- 歌词页支持自定义字体，设置页可选择系统字体或导入 TTF/OTF/TTC 字体文件。
- 修复 TTML `x-bg` 背景歌词残留括号的问题。
- 播放状态恢复后同步恢复词幕和 Ticker 歌词推送。

### 界面与分析

- 歌曲库分析新增格式占比、音质占比、播放次数排行和听歌时长排行。
- 正在播放页继续优化封面取色、模糊背景、迷你歌词和辉光进度条。
- 迷你播放器和悬浮导航栏改进模糊/浮动表现。
- 设置页、关于页和主题选择控件继续向 MIUIX 风格靠拢。

### 构建

- 版本号更新至 1.0.5。

# Changelog

## 1.0.5 - 2026-05-09

### Highlights

- Added WebDAV library configuration, connection testing, remote directory browsing, and remote audio playback.
- Added an LX Music source entry with URL and local JS import for online song search and playback.
- Added LX online song downloads, saving resolved tracks to the system `Music/Ella/` directory.
- Added configurable lyric fonts with system-font previews and imported font files from Settings.
- Added artist pages, current queue support, library analytics, and audio information on the now-playing page.
- Improved library sorting so CJK titles participate in A-Z ordering and fast indexing through Latinized sort keys.

### WebDAV

- WebDAV configuration now saves URL, username, and password, with a removal action.
- WebDAV paths with Chinese characters, spaces, and encoded characters now display and open correctly.
- The WebDAV folder page remembers the last opened path and caches directory results instead of returning to root on page switches.
- Remote WebDAV audio supports direct playback, queue insertion, and cached embedded artwork/lyrics metadata.
- Settings now include an action to clear remote artwork and lyric metadata cache.

### Online Music

- Added the LX source online music page.
- Supports importing LX Music API scripts from network URLs and local JS files.
- Uses imported sources to resolve playable URLs, including `render_api.js` style sources backed by remote APIs.
- Search results show online artwork, now using sharper cover images.
- Online Kuwo songs can fetch lyrics, with translated lyric lines merged correctly instead of appearing as the previous line.
- Online songs can be downloaded to `Music/Ella/`.

### Library And Playback

- Added artist detail pages with play-all, songs, and related albums.
- Added a current-queue popup on the now-playing page.
- Song lists, album details, and folder details can add tracks to the current queue.
- Song lists and folder details now show quality badges such as `Dolby`, `Master`, `HR`, `SQ`, `HQ`, and `LQ`.
- The now-playing page now shows audio format, bit depth, sample rate, and channel information.
- Shuffle, repeat-all, and repeat-one are combined into a single playback mode button.

### Lyrics

- Improved TTML background vocals, translations, word-level lyrics, and Lyricon/Ticker synchronization.
- The lyric page now supports custom fonts selected from system fonts or imported TTF/OTF/TTC files.
- Fixed leftover parentheses in TTML `x-bg` background-vocal lyrics.
- Restored Lyricon and Ticker lyric pushes after playback-state restoration.

### UI And Analytics

- Added library analytics for format distribution, quality distribution, play-count ranking, and listen-time ranking.
- Continued polishing cover-derived colors, blurred backgrounds, mini lyrics, and the glow seek bar on the now-playing page.
- Improved blur/floating behavior for the mini player and floating navigation bar.
- Continued aligning Settings, About, and theme selection controls with MIUIX styling.

### Build

- Bumped version to 1.0.5.
