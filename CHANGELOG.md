# Ella Music v1.0.7

## 更新日志

### 亮点

- 新增桌面歌词悬浮窗，支持逐字歌词、翻译、TTML 对唱和 x-bg 背景人声显示。
- 新增 SuperLyric 支持，可向 SuperLyric 模块发布逐字歌词、翻译和背景人声。
- LX 在线音乐支持导入多个 LX 源并集中管理，搜索时可选择单个源使用。
- 应用名称调整为 Ella Music。
- 默认解码模式改为 FFmpeg，Lyricon 词幕、Flyme 状态栏歌词和 SuperLyric 均默认关闭，需手动开启。
- 播放页、艺人页、专辑页、文件夹页、WebDAV 和本地扫描体验继续优化。

### 歌词

- 桌面歌词新增播放 / 暂停、上一首、下一首、字号调节、锁定和关闭控制。
- 桌面歌词控制按钮默认隐藏，双击歌词显示，数秒无操作后自动隐藏。
- 桌面歌词限制在屏幕范围内拖动，避免拖出屏幕外。
- 修复桌面歌词关闭按钮无效的问题，手动关闭后不会被后续歌词更新立即拉起。
- 修复桌面歌词 TTML x-bg 与主歌词重叠的问题，TTML 主行和背景人声分上下两行显示，并按 v1 / v2 分左右对齐。
- 支持 SuperLyric 发布逐字歌词、翻译、背景人声和背景人声逐字时间轴。
- Lyricon 词幕、Flyme 状态栏歌词和 SuperLyric 默认关闭，降低首次安装后的系统干扰。
- 修复酷我在线歌词“上一句翻译 / 当前原文”错位问题。

### LX 在线音乐

- 支持导入多个 LX 源并集中管理。
- 每次搜索只使用当前选择的一个 LX 源，避免多源混搜造成结果混乱。
- 修复在线 URL 导入 LX 源失败的问题。
- 优化 LX 搜索结果返回播放页后丢失的问题。
- LX 播放列表可一次加入当前页面已加载的歌曲，不再只能逐首加入。
- 播放页右上角菜单新增 LX 在线歌曲下载入口。
- 统一 “LX” 相关命名，替换旧的“落雪”文案。

### 曲库、文件夹与 WebDAV

- 文件夹页右上角新增本地文件夹添加入口，使用系统 Document API 选择目录。
- 本地文件夹支持长按屏蔽，并在文件夹页底部显示已屏蔽文件夹管理入口。
- 修复定位当前歌曲按钮点击一次后，返回页面会反复自动跳到当前歌曲的问题。
- WebDAV 独立为单独页面，并修复选择目录后无法回到上一级目录的问题。
- 首页下拉刷新时不再硬控列表滚动，刷新期间仍可滑动列表。

### 播放与界面

- 播放页向全屏封面背景和底部圆形控制按钮风格调整，同时保留原有 TTML 歌词能力。
- 播放页动态背景封面请求恢复为 512，降低打开播放页时的卡顿。
- 播放页音频信息增加 Dolby / M4A 等格式提示。
- 新增播放完当前歌曲后暂停、定时暂停、倍速播放和变调播放入口。
- 艺人页改为歌曲在上、专辑在下，并优化顶部文字和图标可读性。
- 艺人页专辑列表显示专辑封面。
- 设置页继续按 Miuix 卡片布局调整。

### 解码

- 解码选项保留系统、FFmpeg、自动三种模式。
- 默认解码模式改为 FFmpeg。
- 只有自动模式才允许系统解码失败后回落到 FFmpeg，避免手动选择系统解码时被静默回落。

### 构建

- 版本号更新至 1.0.7。
- versionCode 更新至 8。

### 说明

- 桌面歌词需要系统悬浮窗权限。
- SuperLyric 需要用户设备上存在兼容的 SuperLyric 模块或实现。
- Lyricon、Flyme 状态栏歌词和蓝牙歌词仍依赖目标系统或设备能力，不同 ROM、车机和蓝牙设备表现可能不同。


# Ella Music v1.0.7

## Release Notes

### Highlights

- Added a desktop lyric floating overlay with word-level lyrics, translations, TTML duet lines, and x-bg background vocals.
- Added SuperLyric support for publishing word-level lyrics, translations, and background vocals.
- LX online music now supports importing and managing multiple LX sources, while each search uses one selected source.
- Renamed the app to Ella Music.
- Default decoder mode is now FFmpeg. Lyricon, Flyme status-bar lyrics, and SuperLyric are disabled by default and must be enabled manually.
- Continued polishing the now-playing page, artist pages, album pages, folder browsing, WebDAV, and local scanning.

### Lyrics

- Added desktop lyric controls for play / pause, previous, next, font size, lock, and close.
- Desktop lyric controls are hidden by default, shown by double-tapping the lyric overlay, and hidden again after a short idle timeout.
- Desktop lyric dragging is now clamped to the visible screen area.
- Fixed the desktop lyric close button so manual close is respected and lyric updates do not immediately reopen the overlay.
- Fixed TTML x-bg overlap in desktop lyrics. Primary and background vocal lines are now displayed on separate rows and aligned left / right according to v1 / v2.
- Added SuperLyric publishing for word-level lyrics, translations, background vocals, and background word timings.
- Lyricon, Flyme status-bar lyrics, and SuperLyric now default to off to reduce first-run system interference.
- Fixed Kuwo online lyric pairing where the previous translation could be paired with the current original line.

### LX Online Music

- Added multi-source LX import and centralized source management.
- Each search uses only the currently selected LX source to avoid mixed results from multiple sources.
- Fixed importing LX sources from online URLs.
- Improved preservation of LX search results after returning from the now-playing page.
- LX playlists can now add all currently loaded page results at once instead of one track at a time.
- Added an LX online song download action to the now-playing overflow menu.
- Standardized LX wording and replaced older “Luoxue” labels.

### Library, Folders, And WebDAV

- Added a local-folder add button on the folder page, using the system Document API folder picker.
- Local folders can be blocked by long-pressing them, with blocked-folder management shown from the folder page.
- Fixed the locate-current-song button repeatedly auto-scrolling after returning to a page.
- Moved WebDAV into its own page and fixed parent-directory navigation after selecting a directory.
- Home pull-to-refresh no longer locks list scrolling while scanning.

### Playback And UI

- Adjusted the now-playing page toward a full-cover background and rounded bottom controls while preserving TTML lyric support.
- Restored the now-playing dynamic background artwork request size to 512 to reduce page-opening jank.
- Added Dolby / M4A and related audio info labels on the now-playing page.
- Added stop-after-current, sleep timer, playback speed, and pitch controls.
- Artist pages now show songs before albums and improve header text / icon readability.
- Artist album rows now show album artwork.
- Continued migrating Settings toward Miuix card layouts.

### Decoder

- Decoder options remain System, FFmpeg, and Auto.
- Default decoder mode is now FFmpeg.
- FFmpeg fallback after system-decoder failure is now limited to Auto mode, so manually selecting System mode is respected.

### Build

- Bumped version to 1.0.7.
- Bumped versionCode to 8.

### Notes

- Desktop lyrics require the system overlay permission.
- SuperLyric requires a compatible SuperLyric module or implementation on the user device.
- Lyricon, Flyme status-bar lyrics, and Bluetooth lyrics still depend on target system or device support, so behavior may vary across ROMs, car displays, and Bluetooth devices.
