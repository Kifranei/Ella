# 更新日志

## v1.1.0 - 2026-05-15

### 新增

- 新增 WebDAV 首页入口，并将在线音乐入口集中为 LX Music、MusicFree 和 WebDAV。
- 新增应用彩色音符图标与 Flyme Ticker 24x24 单色图标适配。

### 改进

- 设置页继续整理：移除首页右上角设置入口，外观 / 扫描 / 歌词 / 音频 / 备份拆分为二级页面。
- 日志页导出分享改为发送入口，自动清理菜单改为 Miuix 窗口级下拉，不再挤压页面布局。
- 首页最近听过按歌曲去重，重复播放同一首歌时只保留最新记录展示。
- 改进播放页、歌词页、队列面板和标签编辑器选择弹窗的半屏视觉体系。
- 改进迷你控制条玻璃模糊效果，在大曲库页面也保持高斯模糊质感。
- 改进歌词页、横屏歌词页、桌面歌词和逐字歌词显示，减少遮挡、裁切和长句溢出。
- 改进 TTML / Lyricify / 增强 LRC 解析，优化逐词空格、翻译、罗马音、原文和背景人声识别。
- 改进 Lyricon、SuperLyric、Flyme / AOSP 跑马灯、蓝牙歌词和桌面歌词的切歌后重发逻辑。
- 改进专辑详情、艺术家详情和文件夹详情页返回按钮、背景渐变、搜索框暗色文字和页面背景一致性。
- 改进文件夹页本地扫描目录操作，使用图标按钮替代“全量”和“移除”文字按钮。
- 关于页更新为 AGPL-3.0-or-later，并补充 MusicFree、LX Music Mobile 等开源项目说明。
- Release 默认仅打包 `arm64-v8a`，减小 APK 体积。

### 修复

- 修复 MusicFree `qishui.js`、`kg.js` 等插件无法导入或搜索失败的问题。
- 修复酷狗 MusicFree 插件搜索时因接口结构变化读取 `lists` 失败的问题，增加酷狗搜索兜底适配。
- 修复 QQ MusicFree 插件搜索结果缺少歌曲时长的问题。
- 修复 MusicFree / LX 页面每次进入自动展开全部音源的问题。
- 修复 MusicFree / LX 当前源删除、移除按钮和缓存清理体验问题。
- 修复清除封面歌词缓存未覆盖 LX / MusicFree 在线封面、歌词和远程元数据缓存的问题。
- 修复外部标签编辑器已安装但可能无法打开、可能被 MiXplorer 等文件管理器截获的问题，改为 Ella 内部选择 Lyrico / LunaBeat，并移除兼容性不稳定的音乐标签入口。
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

- Added a Home WebDAV entry and grouped online music access as LX Music, MusicFree, and WebDAV.
- Added a refreshed colorful music-note launcher icon and Flyme Ticker 24x24 monochrome icon support.

### Improved

- Continued reorganizing Settings by removing the Home top-right Settings entry and splitting Appearance / Scan / Lyrics / Audio / Backup into secondary pages.
- Renamed log export sharing to Send and replaced the retention control with a Miuix window-level dropdown so it no longer pushes page content down.
- De-duplicated Home recent plays by song, keeping the newest playback entry for repeated tracks.
- Refined the half-sheet visual system for Now Playing, Lyrics, Queue, and tag-editor selection.
- Improved the mini player glass blur so it stays visibly blurred even on large library pages.
- Improved lyric page, landscape lyrics, desktop lyrics, and word-level lyric rendering to reduce clipping, overlap, and long-line overflow.
- Improved TTML / Lyricify / enhanced LRC parsing for word spacing, translations, romanization, original lines, and background vocals.
- Improved lyric resend behavior after track changes for Lyricon, SuperLyric, Flyme / AOSP ticker lyrics, Bluetooth lyrics, and desktop lyrics.
- Refined Album detail, Artist detail, and Folder detail back buttons, background gradients, dark search-field text, and page background consistency.
- Improved local scan-folder actions on the Folder page by replacing the “Full” and “Remove” text buttons with icon buttons.
- Updated About and licensing information to AGPL-3.0-or-later and credited MusicFree, LX Music Mobile, and related open-source projects.
- Release builds now package `arm64-v8a` by default to reduce APK size.

### Fixed

- Fixed MusicFree plugins such as `qishui.js` and `kg.js` failing to import or search.
- Fixed Kugou MusicFree plugins failing with `cannot read property 'lists' of undefined` after upstream API response changes by adding a Kugou search fallback.
- Fixed QQ MusicFree search results missing song durations.
- Fixed MusicFree / LX source groups expanding automatically every time the pages opened.
- Fixed MusicFree / LX current-source deletion, remove-button behavior, and cache cleanup UX.
- Fixed online artwork / lyric cache cleanup not covering LX / MusicFree and remote metadata caches.
- Fixed external tag editors being installed but failing to open or being intercepted by file managers such as MiXplorer by switching to Ella's own Lyrico / LunaBeat chooser and removing the unstable Music Tag Editor entry.
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
