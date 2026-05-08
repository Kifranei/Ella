package com.ella.music.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.data.SettingsManager
import com.ella.music.ui.theme.THEME_DARK
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.ui.theme.THEME_LIGHT
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    val autoScan by settingsManager.autoScan.collectAsState(initial = true)
    val gaplessPlayback by settingsManager.gaplessPlayback.collectAsState(initial = true)
    val lyriconEnabled by settingsManager.lyriconEnabled.collectAsState(initial = true)
    val lyriconTranslation by settingsManager.lyriconTranslation.collectAsState(initial = true)
    val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
    val tickerEnabled by settingsManager.tickerEnabled.collectAsState(initial = true)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val replayGainEnabled by settingsManager.replayGainEnabled.collectAsState(initial = false)
    val themeLabels = listOf("跟随系统", "浅色", "深色")
    val selectedThemeMode = themeMode.coerceIn(themeLabels.indices)
    var themeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "设置",
            color = MiuixTheme.colorScheme.background
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "外观",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                onClick = { themeExpanded = !themeExpanded }
            ) {
                Column {
                    BasicComponent(
                        title = "主题模式",
                        summary = "选择应用明暗外观",
                        endActions = {
                            Text(
                                text = themeLabels[selectedThemeMode],
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    )
                    AnimatedVisibility(
                        visible = themeExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            themeLabels.forEachIndexed { index, label ->
                                BasicComponent(
                                    title = label,
                                    summary = if (index == selectedThemeMode) "当前使用" else null,
                                    onClick = {
                                        themeExpanded = false
                                        scope.launch { settingsManager.setThemeMode(index) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "通用",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                BasicComponent(
                    title = "自动扫描",
                    summary = "启动时自动扫描音乐文件",
                    endActions = {
                        Switch(
                            checked = autoScan,
                            onCheckedChange = {
                                scope.launch { settingsManager.setAutoScan(it) }
                            }
                        )
                    }
                )
            }

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(text = "最短时长过滤", fontSize = 15.sp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "音频",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                BasicComponent(
                    title = "无缝播放",
                    summary = "歌曲之间无间隙切换",
                    endActions = {
                        Switch(
                            checked = gaplessPlayback,
                            onCheckedChange = {
                                scope.launch { settingsManager.setGaplessPlayback(it) }
                            }
                        )
                    }
                )
            }

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                BasicComponent(
                    title = "ReplayGain 音量均衡",
                    summary = "根据音频标签自动调整播放音量",
                    endActions = {
                        Switch(
                            checked = replayGainEnabled,
                            onCheckedChange = {
                                scope.launch { settingsManager.setReplayGainEnabled(it) }
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "词幕 (Lyricon)",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                BasicComponent(
                    title = "启用词幕",
                    summary = "将歌词推送到 Lyricon 词幕悬浮窗",
                    endActions = {
                        Switch(
                            checked = lyriconEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsManager.setLyriconEnabled(enabled) }
                                playerViewModel?.setLyriconEnabled(enabled)
                            }
                        )
                    }
                )
            }

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                BasicComponent(
                    title = "传递翻译歌词",
                    summary = "在词幕中同时显示翻译歌词",
                    enabled = lyriconEnabled,
                    endActions = {
                        Switch(
                            checked = lyriconTranslation,
                            enabled = lyriconEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsManager.setLyriconTranslation(enabled) }
                                playerViewModel?.setLyriconTranslation(enabled)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "魅族 Ticker",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                BasicComponent(
                    title = "启用 Ticker 歌词",
                    summary = "在魅族设备上通过 Ticker 通知显示歌词",
                    endActions = {
                        Switch(
                            checked = tickerEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsManager.setTickerEnabled(enabled) }
                                playerViewModel?.setTickerEnabled(enabled)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "其他",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                onClick = onNavigateToAnalytics
            ) {
                BasicComponent(
                    title = "歌曲库分析",
                    summary = "查看音质占比、听歌时长和播放次数排行",
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                onClick = onNavigateToAbout
            ) {
                BasicComponent(
                    title = "关于",
                    summary = "Ella Music v${BuildConfig.VERSION_NAME}",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Regular.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
