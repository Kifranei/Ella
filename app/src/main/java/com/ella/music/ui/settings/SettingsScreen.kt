package com.ella.music.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.player.DesktopLyricService
import com.ella.music.ui.components.TagEditorOptionIds
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToSettingsDetail: () -> Unit,
    onNavigateToLyricSettings: () -> Unit,
    onNavigateToAudioSettings: () -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    mainViewModel: MainViewModel? = null,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings),
            color = pageBackground,
            centeredTitle = true
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SmallTitle(text = stringResource(R.string.settings_app))

            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.settings_preferences),
                        summary = stringResource(R.string.settings_preferences_summary),
                        onClick = onNavigateToSettingsDetail
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_lyrics),
                        summary = stringResource(R.string.settings_lyrics_summary),
                        onClick = onNavigateToLyricSettings
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_audio),
                        summary = stringResource(R.string.settings_audio_summary),
                        onClick = onNavigateToAudioSettings
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup),
                        summary = stringResource(R.string.settings_backup_summary),
                        onClick = onNavigateToBackupSettings
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_other))

            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_online_cache),
                        summary = stringResource(R.string.settings_clear_online_cache_summary),
                        onClick = {
                            scope.launch {
                                mainViewModel?.clearOnlineMetadataCache()
                                playerViewModel?.clearOnlineMetadataCache()
                                Toast.makeText(context, context.getString(R.string.settings_clear_online_cache_done), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_logs),
                        summary = stringResource(R.string.settings_logs_summary),
                        onClick = onNavigateToLogs
                    )
                    ArrowPreference(
                        title = stringResource(R.string.about),
                        summary = "Ella Music v${BuildConfig.VERSION_NAME}",
                        onClick = onNavigateToAbout
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    val gaplessPlayback by settingsManager.gaplessPlayback.collectAsState(initial = true)
    val replayGainEnabled by settingsManager.replayGainEnabled.collectAsState(initial = false)
    val audioFocusDisabled by settingsManager.audioFocusDisabled.collectAsState(initial = false)
    val shuffleMode by settingsManager.shuffleMode.collectAsState(initial = SettingsManager.SHUFFLE_MODE_PSEUDO)
    val previousButtonAction by settingsManager.previousButtonAction.collectAsState(initial = SettingsManager.PREVIOUS_BUTTON_PREVIOUS)
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = 2)
    val startupPlayMode by settingsManager.startupPlayMode.collectAsState(initial = SettingsManager.STARTUP_PLAY_OFF)
    val decoderLabels = listOf("系统解码", "FFmpeg 解码", "自动")
    val selectedDecoderMode = decoderMode.coerceIn(decoderLabels.indices)
    val shuffleModeLabels = listOf("伪随机", "真随机")
    val selectedShuffleMode = shuffleMode.coerceIn(shuffleModeLabels.indices)
    val previousButtonLabels = listOf("上一曲", "重放当前歌曲")
    val selectedPreviousButtonAction = previousButtonAction.coerceIn(previousButtonLabels.indices)
    val startupPlayLabels = listOf("关闭", "随机播放", "继续上一次")
    val selectedStartupPlayMode = startupPlayMode.coerceIn(startupPlayLabels.indices)
    val startupPlayEntries = remember {
        startupPlayLabels.mapIndexed { index, label ->
            DropdownItem(
                title = label,
                summary = when (index) {
                    SettingsManager.STARTUP_PLAY_RANDOM -> "启动并加载音乐库后随机播放一首，从开头开始"
                    SettingsManager.STARTUP_PLAY_RESUME -> "启动后恢复上次队列，并从上次进度继续播放"
                    else -> "启动后不自动播放"
                }
            )
        }
    }
    val decoderEntries = remember {
        decoderLabels.mapIndexed { index, label ->
            DropdownItem(
                title = label,
                summary = when (index) {
                    0 -> "只使用 Android 系统解码；不支持的格式会播放失败"
                    1 -> "优先使用 FFmpeg 扩展解码"
                    else -> "系统不支持时才回落到 FFmpeg"
                }
            )
        }
    }
    val shuffleModeEntries = remember {
        shuffleModeLabels.mapIndexed { index, label ->
            DropdownItem(
                title = label,
                summary = when (index) {
                    SettingsManager.SHUFFLE_MODE_TRUE_RANDOM -> "每次随机抽取，可能连续随机到同一首"
                    else -> "洗牌队列播放，一轮内尽量不重复"
                }
            )
        }
    }
    val previousButtonEntries = remember {
        previousButtonLabels.mapIndexed { index, label ->
            DropdownItem(
                title = label,
                summary = when (index) {
                    SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT ->
                        "当前歌曲播放超过 20 秒时重放；20 秒内仍切到上一曲"
                    else -> "始终切换到播放队列里的上一曲"
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = "音频",
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTitle(text = "播放")

            SettingsCardGroup {
                Column {
                    SwitchPreference(
                        title = "无缝播放",
                        summary = "歌曲之间无间隙切换",
                        checked = gaplessPlayback,
                        onCheckedChange = {
                            scope.launch { settingsManager.setGaplessPlayback(it) }
                        }
                    )
                    SwitchPreference(
                        title = "ReplayGain 音量均衡",
                        summary = "根据音频标签自动调整播放音量",
                        checked = replayGainEnabled,
                        onCheckedChange = {
                            scope.launch { settingsManager.setReplayGainEnabled(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = "启动后自动播放",
                        summary = "当前：${startupPlayLabels[selectedStartupPlayMode]}",
                        items = startupPlayEntries,
                        selectedIndex = selectedStartupPlayMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setStartupPlayMode(index) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = "随机播放模式",
                        summary = "当前：${shuffleModeLabels[selectedShuffleMode]}",
                        items = shuffleModeEntries,
                        selectedIndex = selectedShuffleMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setShuffleMode(index) }
                            playerViewModel?.setShuffleMode(index)
                        }
                    )
                    WindowSpinnerPreference(
                        title = "上一曲按钮",
                        summary = "当前：${previousButtonLabels[selectedPreviousButtonAction]}",
                        items = previousButtonEntries,
                        selectedIndex = selectedPreviousButtonAction,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setPreviousButtonAction(index) }
                            playerViewModel?.setPreviousButtonAction(index)
                        }
                    )
                }
            }

            SmallTitle(text = "系统")

            SettingsCardGroup {
                Column {
                    SwitchPreference(
                        title = "关闭音频焦点",
                        summary = "开启后播放时不再抢占其他应用音频焦点",
                        checked = audioFocusDisabled,
                        onCheckedChange = {
                            scope.launch { settingsManager.setAudioFocusDisabled(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = "解码器",
                        summary = "当前：${decoderLabels[selectedDecoderMode]}",
                        items = decoderEntries,
                        selectedIndex = selectedDecoderMode,
                        onSelectedIndexChange = { index ->
                            playerViewModel?.setDecoderMode(index)
                                ?: scope.launch { settingsManager.setDecoderMode(index) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val playbackStatsStore = remember { PlaybackStatsStore.getInstance(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val backup = JSONObject()
                    .put("version", 1)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("settings", settingsManager.exportSettingsJson())
                    .put("playback", playbackStatsStore.exportJson())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(backup.toString(2).toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开备份文件")
                }
            }.onSuccess {
                Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "备份导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("无法读取备份文件")
                }
                val root = JSONObject(text)
                settingsManager.restoreSettingsJson(root.optJSONObject("settings") ?: root)
                root.optJSONObject("playback")?.let { playbackStatsStore.restoreJson(it) }
            }.onSuccess {
                Toast.makeText(context, "备份已恢复", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "备份恢复失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = "备份",
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTitle(text = "备份")

            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = "导出设置和听歌统计",
                        summary = "导出设置、听歌历史和排行热力图数据",
                        onClick = {
                            exportLauncher.launch("ella_backup_${System.currentTimeMillis()}.json")
                        }
                    )
                    ArrowPreference(
                        title = "恢复设置和听歌统计",
                        summary = "从备份 JSON 恢复设置、历史和统计",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/json", "text/*"))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
fun SettingsDetailScreen(
    onBack: () -> Unit,
    onNavigateToLyricFont: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    showOnlyLyrics: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    val autoScan by settingsManager.autoScan.collectAsState(initial = true)
    val lyriconEnabled by settingsManager.lyriconEnabled.collectAsState(initial = false)
    val lyriconTranslation by settingsManager.lyriconTranslation.collectAsState(initial = true)
    val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
    val bottomBarGlassEffect by settingsManager.bottomBarGlassEffect.collectAsState(initial = BottomBarGlassEffect.Blur)
    val tickerEnabled by settingsManager.tickerEnabled.collectAsState(initial = false)
    val tickerHideNotification by settingsManager.tickerHideNotification.collectAsState(initial = false)
    val tickerHideWhenPaused by settingsManager.tickerHideWhenPaused.collectAsState(initial = false)
    val samsungFloatingLyricTranslation by settingsManager.samsungFloatingLyricTranslation.collectAsState(initial = false)
    val desktopLyricEnabled by settingsManager.desktopLyricEnabled.collectAsState(initial = false)
    val desktopLyricHideWhenPaused by settingsManager.desktopLyricHideWhenPaused.collectAsState(initial = false)
    val desktopLyricStatusBarMode by settingsManager.desktopLyricStatusBarMode.collectAsState(initial = false)
    val desktopLyricStatusBarTopOffset by settingsManager.desktopLyricStatusBarTopOffset.collectAsState(initial = 16)
    val desktopLyricLocked by settingsManager.desktopLyricLocked.collectAsState(initial = false)
    val desktopLyricFontScale by settingsManager.desktopLyricFontScale.collectAsState(initial = 100)
    val desktopLyricTranslationScale by settingsManager.desktopLyricTranslationScale.collectAsState(initial = 110)
    val desktopLyricOpacity by settingsManager.desktopLyricOpacity.collectAsState(initial = 100)
    val desktopLyricTextColor by settingsManager.desktopLyricTextColor.collectAsState(initial = -1)
    val desktopLyricShadowStrength by settingsManager.desktopLyricShadowStrength.collectAsState(initial = 100)
    val superLyricEnabled by settingsManager.superLyricEnabled.collectAsState(initial = false)
    val superLyricTranslation by settingsManager.superLyricTranslation.collectAsState(initial = true)
    val lyricGetterEnabled by settingsManager.lyricGetterEnabled.collectAsState(initial = false)
    val bluetoothLyricEnabled by settingsManager.bluetoothLyricEnabled.collectAsState(initial = false)
    val bluetoothLyricTranslation by settingsManager.bluetoothLyricTranslation.collectAsState(initial = false)
    val miniPlayerLyricTranslation by settingsManager.miniPlayerLyricTranslation.collectAsState(initial = true)
    val miniPlayerCoverRotation by settingsManager.miniPlayerCoverRotation.collectAsState(initial = true)
    val miniPlayerLyricsEnabled by settingsManager.miniPlayerLyricsEnabled.collectAsState(initial = true)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val lyricFontName by settingsManager.lyricFontName.collectAsState(initial = "")
    val lyricPerspectiveEffect by settingsManager.lyricPerspectiveEffect.collectAsState(initial = false)
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val dynamicCoverEnabled by settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val playerImmersiveCover by settingsManager.playerImmersiveCover.collectAsState(initial = true)
    val playerDynamicFlowEnabled by settingsManager.playerDynamicFlowEnabled.collectAsState(initial = false)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = true)
    val lyricShareCustomInfo by settingsManager.lyricShareCustomInfo.collectAsState(initial = "")
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = false)
    val metadataEditorId by settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val shortcutLibraryLabel by settingsManager.shortcutLibraryLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL)
    val shortcutPlaylistsLabel by settingsManager.shortcutPlaylistsLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    val shortcutFolderLabel by settingsManager.shortcutFolderLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL)
    val openAiApiKey by settingsManager.openAiApiKey.collectAsState(initial = "")
    val openAiBaseUrl by settingsManager.openAiBaseUrl.collectAsState(initial = SettingsManager.DEFAULT_OPENAI_BASE_URL)
    val openAiModel by settingsManager.openAiModel.collectAsState(initial = SettingsManager.DEFAULT_OPENAI_MODEL)
    val artistSeparators by settingsManager.artistSeparators.collectAsState(initial = "")
    val artistProtectedNames by settingsManager.artistProtectedNames.collectAsState(initial = "")
    val genreSeparators by settingsManager.genreSeparators.collectAsState(initial = "")
    val genreProtectedNames by settingsManager.genreProtectedNames.collectAsState(initial = "")
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = false)
    val categoryGridColumns by settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val homeDailyMixVisible by settingsManager.homeDailyMixVisible.collectAsState(initial = true)
    val homeSectionOrder by settingsManager.homeSectionOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_SECTION_ORDER)
    val homeHiddenSections by settingsManager.homeHiddenSections.collectAsState(initial = "")
    val homeLibraryTileOrder by settingsManager.homeLibraryTileOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
    val homeHiddenLibraryTiles by settingsManager.homeHiddenLibraryTiles.collectAsState(initial = "")
    val themeLabels = listOf("跟随系统", "浅色", "深色")
    val selectedThemeMode = themeMode.coerceIn(themeLabels.indices)
    val themeEntries = remember { themeLabels.map { DropdownItem(title = it) } }
    val bottomBarGlassEffects = remember {
        listOf(BottomBarGlassEffect.Blur, BottomBarGlassEffect.LiquidGlass)
    }
    val bottomBarGlassBlurLabel = stringResource(R.string.bottom_bar_glass_effect_blur)
    val bottomBarGlassLiquidLabel = stringResource(R.string.bottom_bar_glass_effect_liquid)
    val bottomBarGlassEntries = remember(bottomBarGlassBlurLabel, bottomBarGlassLiquidLabel) {
        listOf(
            DropdownItem(title = bottomBarGlassBlurLabel),
            DropdownItem(title = bottomBarGlassLiquidLabel)
        )
    }
    val selectedBottomBarGlassEffectIndex =
        bottomBarGlassEffects.indexOf(bottomBarGlassEffect).takeIf { it >= 0 } ?: 0
    val bottomBarGlassSummary = when (bottomBarGlassEffect) {
        BottomBarGlassEffect.Blur -> stringResource(R.string.settings_bottom_bar_glass_effect_summary_blur)
        BottomBarGlassEffect.LiquidGlass -> stringResource(R.string.settings_bottom_bar_glass_effect_summary_liquid)
    }
    val categoryGridEntries = remember {
        (1..4).map { columns ->
            DropdownItem(
                title = "${columns}列",
                summary = when (columns) {
                    1 -> "列表式大卡片"
                    4 -> "最紧凑的网格"
                    else -> "适合多数屏幕"
                }
            )
        }
    }
    val metadataEditorOptions = remember {
        listOf(
            TagEditorOptionIds.ASK_EACH_TIME to "每次选择",
            TagEditorOptionIds.LYRICO to "Lyrico",
            TagEditorOptionIds.LUNABEAT_METADATA to "LunaBeat（编辑元数据）",
            TagEditorOptionIds.MUSIC_TAG to "音乐标签"
        )
    }
    val lyricTimingEditorOptions = remember {
        listOf(
            TagEditorOptionIds.ASK_EACH_TIME to "每次选择",
            TagEditorOptionIds.LUNABEAT_LYRIC_TIMING to "LunaBeat（歌词打轴）"
        )
    }
    val metadataEditorIndex = metadataEditorOptions
        .indexOfFirst { it.first == metadataEditorId }
        .takeIf { it >= 0 }
        ?: 0
    val lyricTimingEditorIndex = lyricTimingEditorOptions
        .indexOfFirst { it.first == lyricTimingEditorId }
        .takeIf { it >= 0 }
        ?: 0
    val metadataEditorEntries = remember(metadataEditorOptions) {
        metadataEditorOptions.map { DropdownItem(title = it.second) }
    }
    val lyricTimingEditorEntries = remember(lyricTimingEditorOptions) {
        lyricTimingEditorOptions.map { DropdownItem(title = it.second) }
    }
    val desktopLyricColorPresets = remember {
        listOf(
            "白色" to android.graphics.Color.WHITE,
            "银灰" to android.graphics.Color.rgb(191, 191, 191),
            "淡蓝" to android.graphics.Color.rgb(145, 205, 255),
            "天蓝" to android.graphics.Color.rgb(3, 169, 244),
            "浅粉" to android.graphics.Color.rgb(255, 188, 214),
            "薄荷绿" to android.graphics.Color.rgb(166, 235, 203),
            "荧光绿" to android.graphics.Color.rgb(26, 201, 125),
            "淡紫" to android.graphics.Color.rgb(179, 136, 255),
            "柔红" to android.graphics.Color.rgb(255, 112, 112),
            "暖黄色" to android.graphics.Color.rgb(255, 224, 150),
            "橙色" to android.graphics.Color.rgb(255, 87, 34)
        )
    }
    val desktopLyricColorEntries = remember(desktopLyricColorPresets) {
        desktopLyricColorPresets.map { DropdownItem(title = it.first) }
    }
    val selectedDesktopLyricColorIndex =
        desktopLyricColorPresets.indexOfFirst { it.second == desktopLyricTextColor }.takeIf { it >= 0 } ?: 0
    val homeSectionItems = remember {
        listOf(
            HomePreferenceItem("library", "音乐库", "首页音乐库入口区块"),
            HomePreferenceItem("online", "在线音乐", "LX 在线音乐入口"),
            HomePreferenceItem("recent", "最近听过", "最近播放歌曲")
        )
    }
    val homeLibraryTileItems = remember {
        listOf(
            HomePreferenceItem("artist", "艺术家", "按艺术家浏览"),
            HomePreferenceItem("album", "专辑", "按专辑浏览"),
            HomePreferenceItem("folder", "文件夹", "按文件夹分类浏览"),
            HomePreferenceItem("folder_tree", "文件夹层次结构", "按嵌套目录浏览"),
            HomePreferenceItem("playlist", "歌单", "收藏与自建歌单"),
            HomePreferenceItem("analytics", "听歌统计", "历史、热力图和排行"),
            HomePreferenceItem("genre", "流派", "按流派浏览"),
            HomePreferenceItem("year", "年份", "按年份归档"),
            HomePreferenceItem("composer", "作曲家", "按作曲家浏览"),
            HomePreferenceItem("lyricist", "作词家", "按作词家浏览")
        )
    }
    var showHomeDisplayPage by remember { mutableStateOf(false) }
    val dynamicCoverPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setDynamicCoverEnabled(granted) }
        if (granted) {
            Toast.makeText(context, "已开启动态封面", Toast.LENGTH_SHORT).show()
        } else {
            val activity = context as? android.app.Activity
            val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                true
            }
            if (!shouldShowRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, "请在系统设置中授予视频权限后再开启动态封面", Toast.LENGTH_LONG).show()
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            } else {
                Toast.makeText(context, "未授予视频权限，动态封面已关闭", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setDynamicCoverEnabled(enabled: Boolean) {
        if (!enabled) {
            scope.launch { settingsManager.setDynamicCoverEnabled(false) }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                scope.launch { settingsManager.setDynamicCoverEnabled(true) }
            } else {
                scope.launch { settingsManager.setDynamicCoverEnabled(false) }
                dynamicCoverPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            scope.launch { settingsManager.setDynamicCoverEnabled(true) }
        }
    }

    fun applyDesktopLyricSettings() {
        playerViewModel?.applyDesktopLyricSettings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = when {
                showHomeDisplayPage -> "首页显示"
                showOnlyLyrics -> "歌词"
                else -> "应用偏好"
            },
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = { if (showHomeDisplayPage) showHomeDisplayPage = false else onBack() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (showHomeDisplayPage) {
                HomeDisplaySettingsPage(
                    sectionItems = homeSectionItems,
                    sectionOrder = homeSectionOrder,
                    hiddenSections = homeHiddenSections,
                    tileItems = homeLibraryTileItems,
                    tileOrder = homeLibraryTileOrder,
                    hiddenTiles = homeHiddenLibraryTiles,
                    onHiddenSectionsChange = { value ->
                        scope.launch { settingsManager.setHomeHiddenSections(value) }
                    },
                    onHiddenTilesChange = { value ->
                        scope.launch { settingsManager.setHomeHiddenLibraryTiles(value) }
                    },
                    onSectionOrderChange = { value ->
                        scope.launch { settingsManager.setHomeSectionOrder(value) }
                    },
                    onTileOrderChange = { value ->
                        scope.launch { settingsManager.setHomeLibraryTileOrder(value) }
                    }
                )
                Spacer(modifier = Modifier.height(160.dp))
                return@Column
            }

            if (!showOnlyLyrics) {
                SmallTitle(text = "外观")

                SettingsCardGroup {
                    Column {
                        WindowSpinnerPreference(
                            title = "主题模式",
                            summary = "选择应用明暗外观",
                            items = themeEntries,
                            selectedIndex = selectedThemeMode,
                            onSelectedIndexChange = { index ->
                                scope.launch { settingsManager.setThemeMode(index) }
                            }
                        )
                        WindowSpinnerPreference(
                            title = stringResource(R.string.settings_bottom_bar_glass_effect),
                            summary = bottomBarGlassSummary,
                            items = bottomBarGlassEntries,
                            selectedIndex = selectedBottomBarGlassEffectIndex,
                            onSelectedIndexChange = { index ->
                                bottomBarGlassEffects.getOrNull(index)?.let { effect ->
                                    scope.launch { settingsManager.setBottomBarGlassEffect(effect) }
                                }
                            }
                        )
                        WindowSpinnerPreference(
                            title = "分类网格列数",
                            summary = "专辑、流派、年份等页面显示为 ${categoryGridColumns.coerceIn(1, 4)} 列",
                            items = categoryGridEntries,
                            selectedIndex = (categoryGridColumns - 1).coerceIn(categoryGridEntries.indices),
                            onSelectedIndexChange = { index ->
                                scope.launch { settingsManager.setCategoryGridColumns(index + 1) }
                            }
                        )
                        SwitchPreference(
                            title = "显示专辑艺术家",
                            summary = "开启后将专辑艺术家并入艺术家列表，并在艺术家页显示发行专辑Tab",
                            checked = showAlbumArtists,
                            onCheckedChange = {
                                scope.launch { settingsManager.setShowAlbumArtists(it) }
                            }
                        )
                        SwitchPreference(
                            title = "播放后进入播放页",
                            summary = "本地、WebDAV 和在线歌曲点播放后自动打开播放界面",
                            checked = openPlayerOnPlay,
                            onCheckedChange = {
                                scope.launch { settingsManager.setOpenPlayerOnPlay(it) }
                            }
                        )
                        SwitchPreference(
                            title = "列表中显示下一首播放",
                            summary = "在歌曲列表的时长左侧显示快捷按钮；关闭后仍可从歌曲菜单操作",
                            checked = showPlayNextInLists,
                            onCheckedChange = {
                                scope.launch { settingsManager.setShowPlayNextInLists(it) }
                            }
                        )
                        SwitchPreference(
                            title = "动态封面",
                            summary = "开启后读取本地视频封面文件；需要时才申请视频/相册权限",
                            checked = dynamicCoverEnabled,
                            onCheckedChange = ::setDynamicCoverEnabled
                        )
                        SwitchPreference(
                            title = "沉浸专辑封面",
                            summary = "开启后播放页封面延伸到顶部；关闭后使用收敛的圆角封面布局",
                            checked = playerImmersiveCover,
                            onCheckedChange = {
                                scope.launch { settingsManager.setPlayerImmersiveCover(it) }
                            }
                        )
                        SwitchPreference(
                            title = "动态流光",
                            summary = "播放页背景流光动画；关闭后保留静态取色背景以降低播放中掉帧",
                            checked = playerDynamicFlowEnabled,
                            onCheckedChange = {
                                scope.launch { settingsManager.setPlayerDynamicFlowEnabled(it) }
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_lyric_perspective),
                            summary = stringResource(R.string.settings_lyric_perspective_summary),
                            checked = lyricPerspectiveEffect,
                            onCheckedChange = {
                                scope.launch { settingsManager.setLyricPerspectiveEffect(it) }
                            }
                        )
                        ArrowPreference(
                            title = "歌词字体",
                            summary = lyricFontName.ifBlank { "系统默认" },
                            onClick = onNavigateToLyricFont
                        )
                    }
                }

                SmallTitle(text = "首页自定义")

                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = "每日精选",
                            summary = "控制首页顶部每日精选大卡片显示",
                            checked = homeDailyMixVisible,
                            onCheckedChange = {
                                scope.launch { settingsManager.setHomeDailyMixVisible(it) }
                            }
                        )
                        ArrowPreference(
                            title = "首页显示项目",
                            summary = "显示项目、首页区块顺序和音乐库宫格顺序",
                            onClick = { showHomeDisplayPage = true }
                        )
                    }
                }

                SmallTitle(text = "AI 解读")

                SettingsCardGroup {
                    Column {
                        SplitSettingTextField(
                            label = "OpenAI API Key",
                            value = openAiApiKey,
                            summary = "用于歌曲 AI 解读；只保存在本机",
                            onValueChange = { value -> scope.launch { settingsManager.setOpenAiApiKey(value) } }
                        )
                        SplitSettingTextField(
                            label = "OpenAI Base URL",
                            value = openAiBaseUrl,
                            summary = "OpenAI 兼容 Chat Completions 地址；会自动补全 /chat/completions",
                            onValueChange = { value -> scope.launch { settingsManager.setOpenAiBaseUrl(value) } }
                        )
                        SplitSettingTextField(
                            label = "OpenAI 模型",
                            value = openAiModel,
                            summary = "例如 ${SettingsManager.DEFAULT_OPENAI_MODEL}；可按账号可用模型自行修改",
                            onValueChange = { value -> scope.launch { settingsManager.setOpenAiModel(value) } }
                        )
                    }
                }

                SmallTitle(text = "歌词卡片分享")

                SettingsCardGroup {
                    Column {
                        SplitSettingTextField(
                            label = "自定义信息",
                            value = lyricShareCustomInfo,
                            summary = "留空显示 Via Ella；填写名称后显示为 Via @名称",
                            onValueChange = { value -> scope.launch { settingsManager.setLyricShareCustomInfo(value) } }
                        )
                    }
                }

                SmallTitle(text = "音乐标签刮削")

                SettingsCardGroup {
                    Column {
                        WindowSpinnerPreference(
                            title = "元数据编辑软件",
                            summary = "",
                            items = metadataEditorEntries,
                            selectedIndex = metadataEditorIndex,
                            onSelectedIndexChange = { index ->
                                scope.launch {
                                    settingsManager.setMetadataEditorId(
                                        metadataEditorOptions.getOrNull(index)?.first.orEmpty()
                                    )
                                }
                            }
                        )
                        WindowSpinnerPreference(
                            title = "歌词打轴软件",
                            summary = "",
                            items = lyricTimingEditorEntries,
                            selectedIndex = lyricTimingEditorIndex,
                            onSelectedIndexChange = { index ->
                                scope.launch {
                                    settingsManager.setLyricTimingEditorId(
                                        lyricTimingEditorOptions.getOrNull(index)?.first.orEmpty()
                                    )
                                }
                            }
                        )
                    }
                }

                SmallTitle(text = "桌面快捷方式")

                SettingsCardGroup {
                    Column {
                        SplitSettingTextField(
                            label = "音乐库",
                            value = shortcutLibraryLabel,
                            summary = "长按桌面图标时显示的快捷方式名称",
                            singleLine = true,
                            onValueChange = { value -> scope.launch { settingsManager.setShortcutLibraryLabel(value) } }
                        )
                        SplitSettingTextField(
                            label = "歌单",
                            value = shortcutPlaylistsLabel,
                            summary = "长按桌面图标时显示的快捷方式名称",
                            singleLine = true,
                            onValueChange = { value -> scope.launch { settingsManager.setShortcutPlaylistsLabel(value) } }
                        )
                        SplitSettingTextField(
                            label = "文件夹",
                            value = shortcutFolderLabel,
                            summary = "长按桌面图标时显示的快捷方式名称",
                            singleLine = true,
                            onValueChange = { value -> scope.launch { settingsManager.setShortcutFolderLabel(value) } }
                        )
                    }
                }

                SmallTitle(text = "扫描")

                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = "自动扫描",
                            summary = "启动时自动扫描音乐文件",
                            checked = autoScan,
                            onCheckedChange = {
                                scope.launch { settingsManager.setAutoScan(it) }
                            }
                        )

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = "最短时长过滤",
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "过滤短于 ${minDurationSec} 秒的音频文件",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = minDurationSec.toFloat() / 60f,
                                onValueChange = { fraction ->
                                    val sec = (fraction * 60f).toInt().coerceIn(0, 60)
                                    scope.launch { settingsManager.setMinDurationSec(sec) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(text = "0秒", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(text = "60秒", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }

                        SplitSettingTextField(
                            label = "自定义艺术家分隔符",
                            value = artistSeparators,
                            summary = "一行一个；例如 /、feat.、&",
                            onValueChange = { value -> scope.launch { settingsManager.setArtistSeparators(value) } }
                        )
                        SplitSettingTextField(
                            label = "不拆分的艺术家",
                            value = artistProtectedNames,
                            summary = "一行一个；会先保护再按分隔符拆分",
                            onValueChange = { value -> scope.launch { settingsManager.setArtistProtectedNames(value) } }
                        )
                        SplitSettingTextField(
                            label = "自定义流派分隔符",
                            value = genreSeparators,
                            summary = "一行一个；用于流派分类",
                            onValueChange = { value -> scope.launch { settingsManager.setGenreSeparators(value) } }
                        )
                        SplitSettingTextField(
                            label = "不拆分的流派",
                            value = genreProtectedNames,
                            summary = "一行一个；例如带 / 的复合流派名",
                            onValueChange = { value -> scope.launch { settingsManager.setGenreProtectedNames(value) } }
                        )
                        SwitchPreference(
                            title = "标签忽略大小写",
                            summary = "开启后 LiSA 与 LISA 等大小写不同的标签会合并显示",
                            checked = tagIgnoreCase,
                            onCheckedChange = {
                                scope.launch { settingsManager.setTagIgnoreCase(it) }
                            }
                        )
                    }
                }
            }

            if (showOnlyLyrics) {
                SmallTitle(text = stringResource(R.string.settings_lyrics))

                SettingsCardGroup {
                    Column {
                    SwitchPreference(
                        title = "迷你播放条显示歌词",
                        summary = "播放时在迷你播放条显示当前歌词；关闭后显示歌曲标题和歌手",
                        checked = miniPlayerLyricsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setMiniPlayerLyricsEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "迷你播放条显示翻译",
                        summary = "播放栏显示歌词时同时显示翻译",
                        enabled = miniPlayerLyricsEnabled,
                        checked = miniPlayerLyricTranslation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setMiniPlayerLyricTranslation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "迷你播放条封面旋转",
                        summary = "播放时旋转迷你播放条封面；关闭后保持静态封面",
                        checked = miniPlayerCoverRotation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setMiniPlayerCoverRotation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "启用词幕",
                        summary = "将歌词推送到词幕（Lyricon）",
                        checked = lyriconEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyriconEnabled(enabled)
                                ?: scope.launch { settingsManager.setLyriconEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "传递翻译",
                        summary = "在词幕中显示歌词翻译",
                        enabled = lyriconEnabled,
                        checked = lyriconTranslation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyriconTranslation(enabled)
                                ?: scope.launch { settingsManager.setLyriconTranslation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "启用桌面歌词",
                        summary = "通过悬浮窗在其他应用上方显示当前歌词",
                        checked = desktopLyricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, context.getString(R.string.desktop_lyric_overlay_permission_required), Toast.LENGTH_SHORT).show()
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else {
                                playerViewModel?.setDesktopLyricEnabled(enabled)
                                    ?: scope.launch { settingsManager.setDesktopLyricEnabled(enabled) }
                            }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.desktop_lyric_status_bar_mode),
                        summary = stringResource(R.string.desktop_lyric_status_bar_mode_summary),
                        enabled = desktopLyricEnabled,
                        checked = desktopLyricStatusBarMode,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsManager.setDesktopLyricStatusBarMode(enabled)
                                if (enabled) settingsManager.resetDesktopLyricPosition()
                                applyDesktopLyricSettings()
                            }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_lyric_hide_when_paused),
                        summary = stringResource(R.string.settings_floating_lyric_hide_when_paused_summary),
                        enabled = desktopLyricEnabled,
                        checked = desktopLyricHideWhenPaused,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setDesktopLyricHideWhenPaused(enabled)
                                ?: scope.launch { settingsManager.setDesktopLyricHideWhenPaused(enabled) }
                        }
                    )

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.settings_status_lyric_top_offset_value, desktopLyricStatusBarTopOffset),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_status_lyric_top_offset_summary),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = (desktopLyricStatusBarTopOffset.coerceIn(0, 120).toFloat() / 120f).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val offset = (fraction * 120f).toInt().coerceIn(0, 120)
                                scope.launch {
                                    settingsManager.setDesktopLyricStatusBarTopOffset(offset)
                                    applyDesktopLyricSettings()
                                }
                            },
                            enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "0dp", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "120dp", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }

                    SwitchPreference(
                        title = "锁定桌面歌词",
                        summary = "锁定后悬浮歌词不可拖动；可在这里或锁定通知中解除",
                        enabled = desktopLyricEnabled,
                        checked = desktopLyricLocked,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsManager.setDesktopLyricLocked(enabled)
                                applyDesktopLyricSettings()
                            }
                        }
                    )

                    ArrowPreference(
                        title = stringResource(R.string.desktop_lyric_reset_position),
                        summary = stringResource(R.string.desktop_lyric_reset_position_summary),
                        enabled = desktopLyricEnabled,
                        onClick = {
                            scope.launch {
                                settingsManager.resetDesktopLyricPosition()
                                withContext(Dispatchers.Main) {
                                    context.startService(
                                        Intent(context, DesktopLyricService::class.java)
                                            .setAction(DesktopLyricService.ACTION_RESET_POSITION)
                                    )
                                    Toast.makeText(context, context.getString(R.string.desktop_lyric_reset_position_done), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "桌面歌词大小 ${desktopLyricFontScale}%",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "调大主歌词和注音的显示尺寸",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = ((desktopLyricFontScale.coerceIn(80, 220) - 80).toFloat() / 140f).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val scale = (80 + fraction * 140f).toInt().coerceIn(80, 220)
                                scope.launch {
                                    settingsManager.setDesktopLyricFontScale(scale)
                                    applyDesktopLyricSettings()
                                }
                            },
                            enabled = desktopLyricEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "80%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "220%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "翻译大小 ${desktopLyricTranslationScale}%",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "单独放大桌面歌词的翻译行",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = ((desktopLyricTranslationScale.coerceIn(80, 220) - 80).toFloat() / 140f).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val scale = (80 + fraction * 140f).toInt().coerceIn(80, 220)
                                scope.launch {
                                    settingsManager.setDesktopLyricTranslationScale(scale)
                                    applyDesktopLyricSettings()
                                }
                            },
                            enabled = desktopLyricEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "80%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "220%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "桌面歌词透明度 ${desktopLyricOpacity}%",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "调低后歌词会更淡，锁定状态也会保留",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = ((desktopLyricOpacity.coerceIn(35, 100) - 35).toFloat() / 65f).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val opacity = (35 + fraction * 65f).toInt().coerceIn(35, 100)
                                scope.launch {
                                    settingsManager.setDesktopLyricOpacity(opacity)
                                    applyDesktopLyricSettings()
                                }
                            },
                            enabled = desktopLyricEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "35%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "100%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "桌面歌词阴影 ${desktopLyricShadowStrength}%",
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "增强浅色壁纸上的可读性；设为 0 可关闭阴影",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = (desktopLyricShadowStrength.coerceIn(0, 160).toFloat() / 160f).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val strength = (fraction * 160f).toInt().coerceIn(0, 160)
                                scope.launch {
                                    settingsManager.setDesktopLyricShadowStrength(strength)
                                    applyDesktopLyricSettings()
                                }
                            },
                            enabled = desktopLyricEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "0%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = "160%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }

                    WindowSpinnerPreference(
                        title = "桌面歌词颜色",
                        summary = "当前：${desktopLyricColorPresets[selectedDesktopLyricColorIndex].first}",
                        enabled = desktopLyricEnabled,
                        items = desktopLyricColorEntries,
                        selectedIndex = selectedDesktopLyricColorIndex,
                        onSelectedIndexChange = { index ->
                            val color = desktopLyricColorPresets.getOrNull(index)?.second ?: android.graphics.Color.WHITE
                            scope.launch {
                                settingsManager.setDesktopLyricTextColor(color)
                                applyDesktopLyricSettings()
                            }
                        }
                    )

                    SwitchPreference(
                        title = "启用 SuperLyric",
                        summary = "通过 SuperLyricApi 向 SuperLyric 生态发布逐字歌词",
                        checked = superLyricEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSuperLyricEnabled(enabled)
                                ?: scope.launch { settingsManager.setSuperLyricEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "SuperLyric 传递翻译",
                        summary = "关闭后只传原文，不再通过 SuperLyricApi 传递翻译行",
                        enabled = superLyricEnabled,
                        checked = superLyricTranslation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSuperLyricTranslation(enabled)
                                ?: scope.launch { settingsManager.setSuperLyricTranslation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "启用 Lyric Getter",
                        summary = "通过 Lyric Getter API 传递当前原文歌词",
                        checked = lyricGetterEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyricGetterEnabled(enabled)
                                ?: scope.launch { settingsManager.setLyricGetterEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "启用 FLYme 状态栏歌词",
                        summary = "在魅族设备上通过 Ticker 通知显示歌词",
                        checked = tickerEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setTickerEnabled(enabled)
                                ?: scope.launch { settingsManager.setTickerEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "隐藏 FLYme 歌词通知",
                        summary = "复用播放通知只更新 Ticker，避免通知栏额外出现歌词通知。",
                        enabled = tickerEnabled,
                        checked = tickerHideNotification,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setTickerHideNotification(enabled)
                                ?: scope.launch {
                                    settingsManager.setTickerHideNotification(enabled)
                                    if (enabled) settingsManager.setSamsungFloatingLyricTranslation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_status_lyric_hide_when_paused),
                        summary = stringResource(R.string.settings_status_lyric_hide_when_paused_summary),
                        enabled = tickerEnabled,
                        checked = tickerHideWhenPaused,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setTickerHideWhenPaused(enabled)
                                ?: scope.launch { settingsManager.setTickerHideWhenPaused(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "Samsung 浮动歌词翻译",
                        summary = if (tickerHideNotification) {
                            "隐藏 FLYme 歌词通知时复用播放通知，无法同时写入三星浮动通知正文。"
                        } else {
                            "开启后把翻译写入通知正文，三星浮动通知可尝试双行显示。"
                        },
                        enabled = tickerEnabled && !tickerHideNotification,
                        checked = samsungFloatingLyricTranslation && !tickerHideNotification,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSamsungFloatingLyricTranslation(enabled)
                                ?: scope.launch { settingsManager.setSamsungFloatingLyricTranslation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "启用蓝牙车载歌词",
                        summary = "将当前歌词写入媒体标题，供蓝牙设备或车机显示。",
                        checked = bluetoothLyricEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setBluetoothLyricEnabled(enabled)
                                ?: scope.launch { settingsManager.setBluetoothLyricEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = "车载歌词传递翻译",
                        summary = "开启后用当前歌词翻译替换媒体歌手行。",
                        enabled = bluetoothLyricEnabled,
                        checked = bluetoothLyricTranslation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setBluetoothLyricTranslation(enabled)
                                ?: scope.launch { settingsManager.setBluetoothLyricTranslation(enabled) }
                        }
                    )
                    }
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
private fun SplitSettingTextField(
    label: String,
    value: String,
    summary: String,
    singleLine: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var localValue by remember(label) { mutableStateOf(value) }
    var hasPendingEdit by remember(label) { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!hasPendingEdit && value != localValue) {
            localValue = value
        }
    }

    LaunchedEffect(localValue) {
        if (!hasPendingEdit) return@LaunchedEffect
        delay(360)
        if (localValue != value) {
            onValueChange(localValue)
        }
        hasPendingEdit = false
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = summary,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        TextField(
            value = localValue,
            onValueChange = {
                localValue = it
                hasPendingEdit = true
            },
            label = label,
            useLabelAsPlaceholder = true,
            singleLine = singleLine,
            insideMargin = DpSize(12.dp, 10.dp),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            cornerRadius = 12.dp,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class HomePreferenceItem(
    val id: String,
    val title: String,
    val summary: String
)

@Composable
private fun HomeDisplaySettingsPage(
    sectionItems: List<HomePreferenceItem>,
    sectionOrder: String,
    hiddenSections: String,
    tileItems: List<HomePreferenceItem>,
    tileOrder: String,
    hiddenTiles: String,
    onHiddenSectionsChange: (String) -> Unit,
    onHiddenTilesChange: (String) -> Unit,
    onSectionOrderChange: (String) -> Unit,
    onTileOrderChange: (String) -> Unit
) {
    val orderedSections = remember(sectionItems, sectionOrder) {
        sectionItems.orderedByCsv(sectionOrder, SettingsManager.DEFAULT_HOME_SECTION_ORDER)
    }
    val orderedTiles = remember(tileItems, tileOrder) {
        tileItems.orderedByCsv(tileOrder, SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
    }
    val hiddenSectionIds = remember(hiddenSections) { hiddenSections.csvIdSet() }
    val hiddenTileIds = remember(hiddenTiles) { hiddenTiles.csvIdSet() }

    HomeDisplayGroup(
        title = "首页区块",
        items = orderedSections,
        hiddenIds = hiddenSectionIds,
        onHiddenIdsChange = onHiddenSectionsChange
    )
    SmallTitle(text = "音乐库宫格")
    HomeDisplayGroup(
        title = null,
        items = orderedTiles,
        hiddenIds = hiddenTileIds,
        onHiddenIdsChange = onHiddenTilesChange
    )
    SmallTitle(text = "显示顺序")
    SettingsCardGroup {
        Column {
            SplitSettingTextField(
                label = "首页区块顺序",
                value = sectionOrder,
                summary = "用英文逗号分隔：library,online,recent",
                singleLine = true,
                onValueChange = onSectionOrderChange
            )
            SplitSettingTextField(
                label = "音乐库宫格顺序",
                value = tileOrder,
                summary = "artist,album,folder,folder_tree,playlist,analytics,genre,year,composer,lyricist",
                onValueChange = onTileOrderChange
            )
        }
    }
}

@Composable
private fun HomeDisplayGroup(
    title: String?,
    items: List<HomePreferenceItem>,
    hiddenIds: Set<String>,
    onHiddenIdsChange: (String) -> Unit
) {
    if (title != null) {
        SmallTitle(text = title)
    }
    SettingsCardGroup {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeDisplayCommand(
                    text = "全选",
                    modifier = Modifier.weight(1f),
                    onClick = { onHiddenIdsChange("") }
                )
                HomeDisplayCommand(
                    text = "反选",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val allIds = items.map { it.id }.toSet()
                        val nextHidden = allIds - hiddenIds
                        onHiddenIdsChange(nextHidden.toCsv())
                    }
                )
            }
            items.forEach { item ->
                val checked = item.id !in hiddenIds
                HomeDisplayCheckRow(
                    item = item,
                    checked = checked,
                    onClick = {
                        val nextHidden = if (checked) {
                            hiddenIds + item.id
                        } else {
                            hiddenIds - item.id
                        }
                        onHiddenIdsChange(nextHidden.toCsv())
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeDisplayCommand(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        onClick = onClick
    ) {
        BasicComponent(
            title = text
        )
    }
}

@Composable
private fun HomeDisplayCheckRow(
    item: HomePreferenceItem,
    checked: Boolean,
    onClick: () -> Unit
) {
    BasicComponent(
        title = item.title,
        summary = item.summary,
        modifier = Modifier.clickable(onClick = onClick),
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.id,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (checked) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    )
}

private fun List<HomePreferenceItem>.orderedByCsv(order: String, defaultOrder: String): List<HomePreferenceItem> {
    val byId = associateBy { it.id }
    val orderIds = order.csvIds(defaultOrder)
    return (orderIds.mapNotNull { byId[it] } + filterNot { it.id in orderIds }).distinctBy { it.id }
}

private fun String.csvIdSet(): Set<String> =
    split(',', '，', ';', '；')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.csvIds(defaultValue: String): List<String> {
    val ids = csvIdSet().toList()
    val defaults = defaultValue.csvIdSet().toList()
    return (ids + defaults).distinct()
}

private fun Set<String>.toCsv(): String = sorted().joinToString(",")

@Composable
private fun SettingsCardGroup(content: @Composable () -> Unit) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = if (isDark) Color(0xFF1D1D21) else Color(0xFFFFFFFF)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = cardColor
        )
    ) {
        content()
    }
}
