# 1.2.0

From tag `1.1.97` to current `HEAD`.

中文更新日志
- 新增自定义艺术家封面文件夹，支持按艺术家名称匹配封面资源。
- 新增音乐库来源切换器，支持本地 / Navidrome / Emby，并扩展为多地址远程音乐源管理。
- 新增 WebDAV 接入音乐库来源，支持递归索引 WebDAV 音频并纳入歌曲、专辑、艺术家等库视图。
- 修复 Navidrome / Emby 大曲库只能加载部分歌曲的问题，远程曲库改为分页与完整加载策略。
- 支持远程 HTTP 音频读取内嵌歌词 / 标签头部缓存，改善 Navidrome / Emby 等远程歌曲内嵌歌词识别。
- 新增 Apple Music 风格动态流光背景，并加入低功耗可见性门控。
- 歌词更多菜单增加罗马音 / 注音显示位置设置，并优化菜单结构。
- 修复媒体通知歌词元数据补丁导致的歌词重载闪烁，并进一步平滑歌词换行与重排动效。
- 优化歌词插件搜索，并行化检索流程并增加超时控制。
- 新增软件均衡器能力，扩展参数 Q、音色、压缩器、立体声宽度、混响等 DSP 效果。
- 优化文件夹歌单分类页和详情页，支持多选、排序记忆、菜单跳转与封面。
- 新增全标签搜索开关，优化专辑艺术家 / 艺术家显示与搜索去重。
- 修复扫描 toast 重复弹出、隐藏播放页拦截返回键、163 key 解密结果显示等问题。
- 优化远程歌曲列表分页加载、歌词对唱显示、播放页和横屏页面细节。
- 打包字体去重，减小 APK 体积，并在 release APK 文件名中嵌入 git 短哈希便于溯源。
- 补全 RaWs Music 开源引用与第三方许可信息。

English
- Added custom artist-cover folders with artist-name based cover matching.
- Added a library-source switcher for Local / Navidrome / Emby, then expanded it into multi-server remote source management.
- Added WebDAV as a music library source, including recursive WebDAV audio indexing for songs, albums, artists, and related library views.
- Fixed Navidrome / Emby large libraries only loading a partial song set by improving remote pagination and full-library loading.
- Added embedded lyric / tag-header caching for remote HTTP audio, improving embedded lyric detection for Navidrome / Emby and other remote songs.
- Added an Apple Music style flowing dynamic background with low-power visibility gating.
- Added romanization / pronunciation placement controls to the lyric menu and cleaned up the menu structure.
- Fixed lyric reload flicker caused by media-notification metadata patches and further smoothed lyric line wrapping / relayout animations.
- Optimized lyric plugin search with parallel lookup and timeout control.
- Expanded software equalizer support with parameter Q, tone, compressor, stereo width, reverb, and related DSP effects.
- Improved folder playlist category/detail pages with multi-select, sort persistence, menu navigation, and covers.
- Added a full-tag search toggle and improved album-artist / artist display and search deduplication.
- Fixed repeated scan toasts, hidden player pages intercepting back navigation, and missing 163 key decrypt result display.
- Improved remote song-list pagination, duet lyric display, player page details, and landscape playback details.
- Reduced APK size by deduplicating bundled fonts and embedded the git short hash in release APK filenames for traceability.
- Added RaWs Music credits and third-party license references.
