package com.ella.music.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
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
        SmallTopAppBar(
            title = "设置",
            color = pageBackground
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SmallTitle(text = "应用")

            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = "应用偏好",
                        summary = "外观和扫描相关设置",
                        onClick = onNavigateToSettingsDetail
                    )
                    ArrowPreference(
                        title = "歌词",
                        summary = "词幕、桌面歌词、状态栏歌词和车载歌词",
                        onClick = onNavigateToLyricSettings
                    )
                    ArrowPreference(
                        title = "音频",
                        summary = "播放、解码、随机和音频焦点",
                        onClick = onNavigateToAudioSettings
                    )
                    ArrowPreference(
                        title = "备份",
                        summary = "导出和恢复设置、听歌历史与统计数据",
                        onClick = onNavigateToBackupSettings
                    )
                }
            }

            SmallTitle(text = "其他")

            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = "清除封面歌词缓存",
                        summary = "清除 WebDAV、LX 和 MusicFree 的封面、歌词与远程元数据缓存",
                        onClick = {
                            scope.launch {
                                mainViewModel?.clearOnlineMetadataCache()
                                playerViewModel?.clearOnlineMetadataCache()
                                Toast.makeText(context, "在线封面歌词缓存已清除", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ArrowPreference(
                        title = "日志",
                        summary = "查看详细日志、警告和闪退记录",
                        onClick = onNavigateToLogs
                    )
                    ArrowPreference(
                        title = "关于",
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
    val decoderMode by settingsManager.decoderMode.collectAsState(initial = 2)
    val startupPlayMode by settingsManager.startupPlayMode.collectAsState(initial = SettingsManager.STARTUP_PLAY_OFF)
    val decoderLabels = listOf("系统解码", "FFmpeg 解码", "自动")
    val selectedDecoderMode = decoderMode.coerceIn(decoderLabels.indices)
    val shuffleModeLabels = listOf("伪随机", "真随机")
    val selectedShuffleMode = shuffleMode.coerceIn(shuffleModeLabels.indices)
    val startupPlayLabels = listOf("关闭", "随机播放", "继续上一次")
    val selectedStartupPlayMode = startupPlayMode.coerceIn(startupPlayLabels.indices)
    val startupPlayEntries = remember {
        startupPlayLabels.mapIndexed { index, label ->
            SpinnerEntry(
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
            SpinnerEntry(
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
            SpinnerEntry(
                title = label,
                summary = when (index) {
                    SettingsManager.SHUFFLE_MODE_TRUE_RANDOM -> "每次随机抽取，可能连续随机到同一首"
                    else -> "洗牌队列播放，一轮内尽量不重复"
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
        SmallTopAppBar(
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
                            scope.launch { settingsManager.setDecoderMode(index) }
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
        SmallTopAppBar(
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

    val autoScan by settingsManager.autoScan.collectAsState(initial = false)
    val lyriconEnabled by settingsManager.lyriconEnabled.collectAsState(initial = false)
    val lyriconTranslation by settingsManager.lyriconTranslation.collectAsState(initial = true)
    val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
    val tickerEnabled by settingsManager.tickerEnabled.collectAsState(initial = false)
    val samsungFloatingLyricTranslation by settingsManager.samsungFloatingLyricTranslation.collectAsState(initial = false)
    val desktopLyricEnabled by settingsManager.desktopLyricEnabled.collectAsState(initial = false)
    val superLyricEnabled by settingsManager.superLyricEnabled.collectAsState(initial = false)
    val superLyricTranslation by settingsManager.superLyricTranslation.collectAsState(initial = true)
    val bluetoothLyricEnabled by settingsManager.bluetoothLyricEnabled.collectAsState(initial = false)
    val bluetoothLyricTranslation by settingsManager.bluetoothLyricTranslation.collectAsState(initial = false)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val lyricFontName by settingsManager.lyricFontName.collectAsState(initial = "")
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val themeLabels = listOf("跟随系统", "浅色", "深色")
    val selectedThemeMode = themeMode.coerceIn(themeLabels.indices)
    val themeEntries = remember { themeLabels.map { SpinnerEntry(title = it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = if (showOnlyLyrics) "歌词" else "应用偏好",
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
                        SwitchPreference(
                            title = "播放后进入播放页",
                            summary = "本地、WebDAV 和在线歌曲点播放后自动打开播放界面",
                            checked = openPlayerOnPlay,
                            onCheckedChange = {
                                scope.launch { settingsManager.setOpenPlayerOnPlay(it) }
                            }
                        )
                        ArrowPreference(
                            title = "歌词字体",
                            summary = lyricFontName.ifBlank { "系统默认" },
                            onClick = onNavigateToLyricFont
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
                    }
                }
            }

            if (showOnlyLyrics) {
                SmallTitle(text = "歌词")

                SettingsCardGroup {
                    Column {
                    SwitchPreference(
                        title = "启用词幕",
                        summary = "将歌词推送到词幕（Lyricon）",
                        checked = lyriconEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setLyriconEnabled(enabled) }
                            playerViewModel?.setLyriconEnabled(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "传递翻译",
                        summary = "在词幕中显示歌词翻译",
                        enabled = lyriconEnabled,
                        checked = lyriconTranslation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setLyriconTranslation(enabled) }
                            playerViewModel?.setLyriconTranslation(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "启用桌面歌词",
                        summary = "通过悬浮窗在其他应用上方显示当前歌词",
                        checked = desktopLyricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else {
                                scope.launch { settingsManager.setDesktopLyricEnabled(enabled) }
                                playerViewModel?.setDesktopLyricEnabled(enabled)
                            }
                        }
                    )

                    SwitchPreference(
                        title = "启用 SuperLyric",
                        summary = "向 SuperLyric 模块发布逐字歌词",
                        checked = superLyricEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setSuperLyricEnabled(enabled) }
                            playerViewModel?.setSuperLyricEnabled(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "SuperLyric 传递翻译",
                        summary = "关闭后只传原文，不再把翻译行交给 SuperLyric",
                        enabled = superLyricEnabled,
                        checked = superLyricTranslation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setSuperLyricTranslation(enabled) }
                            playerViewModel?.setSuperLyricTranslation(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "启用 FLYme 状态栏歌词",
                        summary = "在魅族设备上通过 Ticker 通知显示歌词",
                        checked = tickerEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setTickerEnabled(enabled) }
                            playerViewModel?.setTickerEnabled(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "Samsung 浮动歌词翻译",
                        summary = "开启后把翻译写入通知正文，三星浮动通知可尝试双行显示。",
                        enabled = tickerEnabled,
                        checked = samsungFloatingLyricTranslation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setSamsungFloatingLyricTranslation(enabled) }
                            playerViewModel?.setSamsungFloatingLyricTranslation(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "启用蓝牙车载歌词",
                        summary = "将当前歌词写入媒体标题，供蓝牙设备或车机显示。",
                        checked = bluetoothLyricEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setBluetoothLyricEnabled(enabled) }
                            playerViewModel?.setBluetoothLyricEnabled(enabled)
                        }
                    )

                    SwitchPreference(
                        title = "车载歌词传递翻译",
                        summary = "开启后用当前歌词翻译替换媒体歌手行。",
                        enabled = bluetoothLyricEnabled,
                        checked = bluetoothLyricTranslation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setBluetoothLyricTranslation(enabled) }
                            playerViewModel?.setBluetoothLyricTranslation(enabled)
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
