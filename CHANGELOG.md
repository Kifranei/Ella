# 更新日志

## v1.1.0 - 2026-05-15

### 新增

- 新增 MusicFree 在线音乐支持：插件管理、插件广场导入、在线搜索、在线播放、懒加载队列和下载。
- 新增 LX 在线音乐入口与多 API 源管理，支持从首页进入 LX / MusicFree 在线音乐。
- 新增首页仪表盘、最近播放、听歌统计入口和在线音乐入口。
- 新增应用日志页，支持详细运行日志查看、复制、导出和系统分享。
- 新增日志自动清理策略，可保留最近 1 / 3 / 7 / 14 / 30 天日志。
- 新增应用备份与恢复能力，支持导出和恢复设置、听歌历史与统计数据。
- 新增播放页外部标签编辑入口，支持在 Ella 内选择 Lyrico 或 LunaBeat 打开。
- 新增歌词来源、歌词字体、桌面歌词、车载歌词、状态栏歌词、SuperLyric 和 Lyricon 相关设置。
- 新增音频输出切换器、队列清空、音频焦点开关、随机播放模式和启动后自动播放设置。
- 新增 WebDAV 首页入口，并将在线音乐入口集中为 LX Music、MusicFree 和 WebDAV。
- 新增应用彩色音符图标与 Flyme Ticker 24x24 单色图标适配。

### 改进

- 设置页重新整理：底部导航保留设置入口，首页移除右上角设置按钮，LX / MusicFree 移至首页，外观 / 扫描 / 歌词 / 音频 / 备份拆分为二级页面。
- 日志页右上角加入自动清理下拉菜单，减少长期运行后的日志堆积。
- 日志页导出分享改为发送入口，自动清理菜单改为 Miuix 窗口级下拉，不再挤压页面布局。
- 首页最近听过按歌曲去重，重复播放同一首歌时只保留最新记录展示。
- 播放页采用深色动态流光背景，长标题自适应缩小显示，迷你歌词支持上一行 / 当前行 / 下一行展示。
- 改进迷你控制条玻璃模糊效果，在大曲库页面也保持高斯模糊质感。
- 改进横屏歌词页布局、进度条和播放控制，降低当前歌词字号以适配带翻译歌曲。
- 改进歌词页、迷你歌词、播放控制区、可视化效果和滚动歌词交互。
- 改进 TTML / Lyricify / 增强 LRC 解析，保留逐词空格、尾部词、翻译、原文和背景人声结构。
- 改进 Lyricon、SuperLyric、Flyme / AOSP 跑马灯、蓝牙歌词和三星悬浮歌词的双行歌词传递。
- 恢复并增强歌曲库音质标签，补充 Dolby 双 D 标识，并改进 24/96 ALAC / M4A、WAV 等格式识别。
- 改进首页、艺术家页、播放页、设置页、日志页、关于页和在线音乐页面布局。
- 改进专辑详情、艺术家详情和文件夹详情页返回按钮、背景渐变、搜索框暗色文字和页面背景一致性。
- 改进文件夹页本地扫描目录操作，使用图标按钮替代“全量”和“移除”文字按钮。
- 关于页更新为 AGPL-3.0-or-later，并补充 MusicFree、LX Music Mobile 等开源项目说明。
- 使用 Maven 依赖管理 Miuix 和 Media3，减少本地构建准备成本。
- Release 默认仅打包 `arm64-v8a`，减小 APK 体积。

### 修复

- 修复 MusicFree `qishui.js`、`kg.js` 等插件无法导入或搜索失败的问题。
- 修复酷狗 MusicFree 插件搜索时因接口结构变化读取 `lists` 失败的问题，增加酷狗搜索兜底适配。
- 修复 QQ MusicFree 插件搜索结果缺少歌曲时长的问题。
- 修复 MusicFree 插件页每次进入自动展开全部音源的问题。
- 修复 LX 页面每次进入自动展开全部源的问题。
- 修复 MusicFree / LX 删除当前源、移除按钮和缓存清理体验问题。
- 修复清除封面歌词缓存未覆盖 LX / MusicFree 在线封面、歌词和远程元数据缓存的问题。
- 修复只有原文和翻译时，原文被误识别为注音、翻译被写入原文行的问题。
- 修复部分 TTML 歌词播放到当前行时缺少尾部单词的问题。
- 修复媒体通知跳回应用后，播放页音质识别变成 LQ、迷你歌词丢失和播放状态恢复不完整的问题。
- 修复底部导航在首页、音乐库、设置页之间可能无法进入目标页面的问题。
- 修复首页排序、封面加载和播放列表恢复导致的卡顿、ANR 与 OOM 问题。
- 修复在线队列中不可播放 LX 项目导致播放中断的问题。
- 修复歌词入口、可视化会话、主题外文字颜色和旧版启动器 / 播放兼容性问题。
- 修复歌词字体页暗色背景与设置页不一致的问题。
- 修复文件夹页右上角加号和 WebDAV 图标在暗色模式下颜色过深的问题。
- 移除文件夹页无意义的增量刷新入口和相关扫描代码。
- 修复外部标签编辑器已安装但可能无法打开的问题，补充包可见性、FileProvider Uri、路径参数和逐个启动兜底。
- 修复外部标签编辑器可能被 MiXplorer 等文件管理器截获的问题，改为 Ella 内部选择 Lyrico / LunaBeat，并移除兼容性不稳定的音乐标签入口。
- 修复进入横屏播放页后系统导航栏变白，返回播放页仍保持白色的问题。
- 修复关于页暗色卡片被流体背景染色过重的问题，卡片恢复暗灰底色。
- 修复清数据后进入设置 / 歌词二级页可能 ANR 的问题，移除 Media3 Controller Future 主线程等待并禁用通知封面同步加载路径。
- 修复外部歌词渠道切歌后偶发不更新、无歌词歌曲沿用上一首歌词的问题。
- 修复桌面歌词关闭后设置状态不同步、暂停按钮图标不符合预期、超长歌词溢出屏幕的问题。
- 修复逐字 LRC 中罗马音 / 翻译识别错位、日语逐字桌面歌词逐字分割过碎的问题。
- 修复 WAV 歌曲在音乐库中可能无法读取封面、歌手和标题的问题。
- 修复不同歌手的同名专辑被合并的问题。
- 修复专辑详情页排序不按音轨号的问题，无音轨号时回落到名称排序。
- 修复自动扫描默认开启导致清数据启动后立即扫描的问题，现在默认关闭。
- 修复日志页空态才显示“导出详细日志”的重复入口问题。
- 修复歌曲库分析页背景色与其它页面不一致的问题。
- 修复 launcher 图标在新旧设备上被裁切的问题，补充旧版图标 fallback。

# Changelog

## v1.1.0 - 2026-05-15

### Added

- Added MusicFree online music support: plugin management, plugin-hub imports, online search, streaming, lazy online queues, and downloads.
- Added LX online music entry and multi-source API management, with LX / MusicFree entries moved to Home.
- Added a dashboard-style Home page with recent plays, listening statistics, and online music shortcuts.
- Added an in-app log page with detailed runtime logs, copy, export, and system sharing.
- Added automatic log retention options for 1 / 3 / 7 / 14 / 30 days.
- Added app backup and restore for settings, listening history, and statistics.
- Added external tag editor integration with an in-app chooser for Lyrico and LunaBeat.
- Added lyric-source, lyric-font, desktop lyric, car lyric, ticker lyric, SuperLyric, and Lyricon settings.
- Added audio output switching, queue clearing, audio-focus toggle, shuffle mode, and startup auto-play settings.
- Added a Home WebDAV entry and grouped online music access as LX Music, MusicFree, and WebDAV.
- Added a refreshed colorful music-note launcher icon and Flyme Ticker 24x24 monochrome icon support.

### Improved

- Reworked Settings navigation: Settings stays in bottom navigation, the Home top-right Settings button was removed, LX / MusicFree moved to Home, and Appearance / Scan / Lyrics / Audio / Backup were split into secondary pages.
- Added log retention control to the top-right of the log page.
- Renamed log export sharing to Send and replaced the retention control with a Miuix window-level dropdown so it no longer pushes page content down.
- De-duplicated Home recent plays by song, keeping the newest playback entry for repeated tracks.
- Refined the now-playing page with a dark dynamic flow background, adaptive long-title sizing, and mini lyrics showing previous / current / next lines.
- Improved the mini player glass blur so it stays visibly blurred even on large library pages.
- Improved the landscape lyric page layout, progress row, playback controls, and current-line sizing for translated lyrics.
- Improved the lyric page, mini lyrics, player controls, visualizer, and scrollable lyric interactions.
- Improved TTML / Lyricify / enhanced LRC parsing, preserving word spacing, tail words, translations, original lines, and background vocals.
- Improved dual-line lyric delivery for Lyricon, SuperLyric, Flyme / AOSP ticker lyrics, Bluetooth lyrics, and Samsung floating lyrics.
- Restored and expanded library quality labels, added a Dolby double-D label, and improved 24/96 ALAC / M4A and WAV recognition.
- Refined Home, Artist, Now Playing, Settings, Logs, About, and online music layouts.
- Refined Album detail, Artist detail, and Folder detail back buttons, background gradients, dark search-field text, and page background consistency.
- Improved local scan-folder actions on the Folder page by replacing the “Full” and “Remove” text buttons with icon buttons.
- Updated About and licensing information to AGPL-3.0-or-later and credited MusicFree, LX Music Mobile, and related open-source projects.
- Switched Miuix and Media3 usage to Maven dependencies to reduce local build setup work.
- Release builds now package `arm64-v8a` by default to reduce APK size.

### Fixed

- Fixed MusicFree plugins such as `qishui.js` and `kg.js` failing to import or search.
- Fixed Kugou MusicFree plugins failing with `cannot read property 'lists' of undefined` after upstream API response changes by adding a Kugou search fallback.
- Fixed QQ MusicFree search results missing song durations.
- Fixed MusicFree source groups expanding automatically every time the page opened.
- Fixed LX source groups expanding automatically every time the page opened.
- Fixed MusicFree / LX current-source deletion, remove-button behavior, and cache cleanup UX.
- Fixed online artwork / lyric cache cleanup not covering LX / MusicFree and remote metadata caches.
- Fixed original / translation-only lyrics being misclassified as pronunciation, causing original and translated lines to swap.
- Fixed some TTML lines losing tail words while the current line was playing.
- Fixed media-notification return restoring incomplete playback state, which could make quality labels fall back to LQ and mini lyrics disappear.
- Fixed bottom navigation sometimes failing to enter Home, Library, or Settings.
- Fixed home sorting, artwork loading, and queue restoration stalls, including ANR and OOM cases.
- Fixed unplayable LX queue items interrupting playback.
- Fixed lyric entry, visualizer sessions, themed text colors, and legacy launcher / playback compatibility issues.
- Fixed the lyric font page background not matching Settings in dark mode.
- Fixed dark-mode icon tint on the Folder page add button and WebDAV actions.
- Removed the unnecessary incremental scan entry and related scan code.
- Fixed external tag editors being installed but failing to open by adding package visibility, FileProvider Uri support, path extras, and per-editor fallback launching.
- Fixed external tag editing being intercepted by file managers such as MiXplorer by switching to Ella's own Lyrico / LunaBeat chooser and removing the unstable Music Tag Editor entry.
- Fixed the system navigation bar turning white after entering and leaving the landscape player.
- Fixed About page dark cards being overly tinted by the fluid background; cards now use a dark gray surface.
- Fixed ANRs after clearing data and entering Settings / Lyrics secondary pages by removing main-thread Media3 Controller Future waits and disabling synchronous notification artwork loading paths.
- Fixed external lyric channels sometimes not updating after track changes and no-lyric tracks reusing the previous song's lyrics.
- Fixed desktop lyric close-state sync, pause icon style, and overly long lyric lines overflowing off screen.
- Fixed word-level LRC romanization / translation misclassification and overly fragmented Japanese desktop lyric segmentation.
- Fixed WAV songs sometimes losing artwork, artist, and title metadata in the library.
- Fixed same-name albums by different artists being merged together.
- Fixed album detail ordering to use track numbers first and fall back to title sorting when track numbers are missing.
- Fixed automatic scanning being enabled by default after clearing data; it is now off by default.
- Fixed the duplicate “export detailed logs” action appearing only on the empty log state.
- Fixed the library analytics page background not matching other pages.
- Fixed launcher icon clipping on old and new devices by keeping the foreground inside the adaptive icon safe area and adding a legacy fallback.
