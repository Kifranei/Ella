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
import androidx.compose.runtime.State
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val settingsManager = remember { SettingsManager(context) }
    val appLanguage by settingsManager.appLanguage.collectAsState(initial = SettingsManager.APP_LANGUAGE_SYSTEM)
    val languageOptions = remember {
        listOf(
            SettingsManager.APP_LANGUAGE_SYSTEM,
            SettingsManager.APP_LANGUAGE_ZH_CN,
            SettingsManager.APP_LANGUAGE_EN
        )
    }
    val languageSystemLabel = stringResource(R.string.settings_language_system)
    val languageChineseLabel = stringResource(R.string.settings_language_simplified_chinese)
    val languageEnglishLabel = stringResource(R.string.settings_language_english)
    val languageEntries = remember(languageSystemLabel, languageChineseLabel, languageEnglishLabel) {
        listOf(
            DropdownItem(title = languageSystemLabel),
            DropdownItem(title = languageChineseLabel),
            DropdownItem(title = languageEnglishLabel)
        )
    }
    val selectedLanguageIndex = languageOptions.indexOf(appLanguage).takeIf { it >= 0 } ?: 0
    val languageSummary = when (appLanguage) {
        SettingsManager.APP_LANGUAGE_ZH_CN -> stringResource(R.string.settings_language_summary_simplified_chinese)
        SettingsManager.APP_LANGUAGE_EN -> stringResource(R.string.settings_language_summary_english)
        else -> stringResource(R.string.settings_language_summary_system)
    }

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
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_language),
                        summary = languageSummary,
                        items = languageEntries,
                        selectedIndex = selectedLanguageIndex,
                        onSelectedIndexChange = { index ->
                            languageOptions.getOrNull(index)?.let { language ->
                                scope.launch {
                                    settingsManager.setAppLanguage(language)
                                }
                            }
                        }
                    )
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
    val decoderLabels = listOf(
        stringResource(R.string.settings_audio_decoder_system),
        stringResource(R.string.settings_audio_decoder_ffmpeg),
        stringResource(R.string.settings_audio_decoder_auto)
    )
    val selectedDecoderMode = decoderMode.coerceIn(decoderLabels.indices)
    val shuffleModeLabels = listOf(
        stringResource(R.string.settings_shuffle_mode_pseudo_random),
        stringResource(R.string.settings_shuffle_mode_true_random)
    )
    val selectedShuffleMode = shuffleMode.coerceIn(shuffleModeLabels.indices)
    val previousButtonLabels = listOf(
        stringResource(R.string.settings_previous_button_previous),
        stringResource(R.string.settings_previous_button_replay_current)
    )
    val selectedPreviousButtonAction = previousButtonAction.coerceIn(previousButtonLabels.indices)
    val startupPlayLabels = listOf(
        stringResource(R.string.settings_startup_play_off),
        stringResource(R.string.settings_startup_play_random),
        stringResource(R.string.settings_startup_play_resume)
    )
    val selectedStartupPlayMode = startupPlayMode.coerceIn(startupPlayLabels.indices)
    val startupPlayEntries = listOf(
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_OFF],
            summary = stringResource(R.string.settings_startup_play_off_summary)
        ),
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_RANDOM],
            summary = stringResource(R.string.settings_startup_play_random_summary)
        ),
        DropdownItem(
            title = startupPlayLabels[SettingsManager.STARTUP_PLAY_RESUME],
            summary = stringResource(R.string.settings_startup_play_resume_summary)
        )
    )
    val decoderEntries = listOf(
        DropdownItem(
            title = decoderLabels[0],
            summary = stringResource(R.string.settings_audio_decoder_system_summary)
        ),
        DropdownItem(
            title = decoderLabels[1],
            summary = stringResource(R.string.settings_audio_decoder_ffmpeg_summary)
        ),
        DropdownItem(
            title = decoderLabels[2],
            summary = stringResource(R.string.settings_audio_decoder_auto_summary)
        )
    )
    val shuffleModeEntries = listOf(
        DropdownItem(
            title = shuffleModeLabels[0],
            summary = stringResource(R.string.settings_shuffle_mode_pseudo_random_summary)
        ),
        DropdownItem(
            title = shuffleModeLabels[SettingsManager.SHUFFLE_MODE_TRUE_RANDOM],
            summary = stringResource(R.string.settings_shuffle_mode_true_random_summary)
        )
    )
    val previousButtonEntries = listOf(
        DropdownItem(
            title = previousButtonLabels[SettingsManager.PREVIOUS_BUTTON_PREVIOUS],
            summary = stringResource(R.string.settings_previous_button_previous_summary)
        ),
        DropdownItem(
            title = previousButtonLabels[SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT],
            summary = stringResource(R.string.settings_previous_button_replay_current_summary)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_audio_screen_title),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
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
            SmallTitle(text = stringResource(R.string.settings_playback_section))

            SettingsCardGroup {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.settings_gapless_playback),
                        summary = stringResource(R.string.settings_gapless_playback_summary),
                        checked = gaplessPlayback,
                        onCheckedChange = {
                            scope.launch { settingsManager.setGaplessPlayback(it) }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_replay_gain),
                        summary = stringResource(R.string.settings_replay_gain_summary),
                        checked = replayGainEnabled,
                        onCheckedChange = {
                            scope.launch { settingsManager.setReplayGainEnabled(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_startup_play),
                        summary = stringResource(R.string.settings_current_value, startupPlayLabels[selectedStartupPlayMode]),
                        items = startupPlayEntries,
                        selectedIndex = selectedStartupPlayMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setStartupPlayMode(index) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_shuffle_mode),
                        summary = stringResource(R.string.settings_current_value, shuffleModeLabels[selectedShuffleMode]),
                        items = shuffleModeEntries,
                        selectedIndex = selectedShuffleMode,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setShuffleMode(index) }
                            playerViewModel?.setShuffleMode(index)
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_previous_button),
                        summary = stringResource(R.string.settings_current_value, previousButtonLabels[selectedPreviousButtonAction]),
                        items = previousButtonEntries,
                        selectedIndex = selectedPreviousButtonAction,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsManager.setPreviousButtonAction(index) }
                            playerViewModel?.setPreviousButtonAction(index)
                        }
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.settings_system_section))

            SettingsCardGroup {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.settings_disable_audio_focus),
                        summary = stringResource(R.string.settings_disable_audio_focus_summary),
                        checked = audioFocusDisabled,
                        onCheckedChange = {
                            scope.launch { settingsManager.setAudioFocusDisabled(it) }
                        }
                    )
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_decoder),
                        summary = stringResource(R.string.settings_current_value, decoderLabels[selectedDecoderMode]),
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
                    } ?: error(context.getString(R.string.settings_backup_open_failed))
                }
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_export_failed), Toast.LENGTH_SHORT).show()
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
                    } ?: error(context.getString(R.string.settings_backup_read_failed))
                }
                val root = JSONObject(text)
                settingsManager.restoreSettingsJson(root.optJSONObject("settings") ?: root)
                root.optJSONObject("playback")?.let { playbackStatsStore.restoreJson(it) }
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.settings_backup_restore_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_backup_restore_failed), Toast.LENGTH_SHORT).show()
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
            title = stringResource(R.string.settings_backup),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
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
            SmallTitle(text = stringResource(R.string.settings_backup))

            SettingsCardGroup {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup_export_title),
                        summary = stringResource(R.string.settings_backup_export_summary),
                        onClick = {
                            exportLauncher.launch("ella_backup_${System.currentTimeMillis()}.json")
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_backup_restore_title),
                        summary = stringResource(R.string.settings_backup_restore_summary),
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
    val lyriconPronunciation by settingsManager.lyriconPronunciation.collectAsState(initial = false)
    val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
    val bottomBarGlassEffect by settingsManager.bottomBarGlassEffect.collectAsState(initial = BottomBarGlassEffect.LiquidGlass)
    val tickerEnabled by settingsManager.tickerEnabled.collectAsState(initial = false)
    val tickerHideNotification by settingsManager.tickerHideNotification.collectAsState(initial = false)
    val tickerHeadsUpLyrics by settingsManager.tickerHeadsUpLyrics.collectAsState(initial = false)
    val samsungFloatingLyricTranslation by settingsManager.samsungFloatingLyricTranslation.collectAsState(initial = false)
    val statusBarAllowPhonetic by settingsManager.statusBarAllowPhonetic.collectAsState(initial = false)
    val desktopLyricEnabled by settingsManager.desktopLyricEnabled.collectSettingsState(initialValue = false)
    val desktopLyricHideWhenPaused by settingsManager.desktopLyricHideWhenPaused.collectSettingsState(initialValue = false)
    val desktopLyricStatusBarMode by settingsManager.desktopLyricStatusBarMode.collectSettingsState(initialValue = false)
    val desktopLyricStatusBarTopOffset by settingsManager.desktopLyricStatusBarTopOffset.collectSettingsState(initialValue = 16)
    val desktopLyricStatusBarPosition by settingsManager.desktopLyricStatusBarPosition.collectSettingsState(initialValue = SettingsManager.DESKTOP_LYRIC_STATUS_POSITION_CENTER)
    val desktopLyricStatusBarSecondary by settingsManager.desktopLyricStatusBarSecondary.collectSettingsState(initialValue = SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF)
    val desktopLyricLocked by settingsManager.desktopLyricLocked.collectSettingsState(initialValue = false)
    val desktopLyricFontScale by settingsManager.desktopLyricFontScale.collectSettingsState(initialValue = 100)
    val desktopLyricTranslationScale by settingsManager.desktopLyricTranslationScale.collectSettingsState(initialValue = 110)
    val desktopLyricOpacity by settingsManager.desktopLyricOpacity.collectSettingsState(initialValue = 100)
    val desktopLyricTextColor by settingsManager.desktopLyricTextColor.collectSettingsState(initialValue = -1)
    val desktopLyricShadowStrength by settingsManager.desktopLyricShadowStrength.collectSettingsState(initialValue = 100)
    val superLyricEnabled by settingsManager.superLyricEnabled.collectAsState(initial = false)
    val superLyricTranslation by settingsManager.superLyricTranslation.collectAsState(initial = true)
    val superLyricPronunciation by settingsManager.superLyricPronunciation.collectAsState(initial = false)
    val lyricGetterEnabled by settingsManager.lyricGetterEnabled.collectAsState(initial = false)
    val bluetoothLyricEnabled by settingsManager.bluetoothLyricEnabled.collectAsState(initial = false)
    val bluetoothLyricTranslation by settingsManager.bluetoothLyricTranslation.collectAsState(initial = false)
    val bluetoothLyricPronunciation by settingsManager.bluetoothLyricPronunciation.collectAsState(initial = false)
    val miniPlayerLyricTranslation by settingsManager.miniPlayerLyricTranslation.collectAsState(initial = true)
    val miniPlayerCoverRotation by settingsManager.miniPlayerCoverRotation.collectAsState(initial = true)
    val miniPlayerLyricsEnabled by settingsManager.miniPlayerLyricsEnabled.collectAsState(initial = true)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val lyricFontName by settingsManager.lyricFontName.collectAsState(initial = "")
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val dynamicCoverEnabled by settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val playerImmersiveCover by settingsManager.playerImmersiveCover.collectAsState(initial = true)
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
    val themeLabels = listOf(
        stringResource(R.string.theme_follow_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark)
    )
    val selectedThemeMode = themeMode.coerceIn(themeLabels.indices)
    val themeEntries = remember(themeLabels) { themeLabels.map { DropdownItem(title = it) } }
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
    val categoryGridEntries = remember(context) {
        (1..4).map { columns ->
            DropdownItem(
                title = context.getString(R.string.settings_category_grid_columns_option, columns),
                summary = when (columns) {
                    1 -> context.getString(R.string.settings_category_grid_columns_option_summary_single)
                    4 -> context.getString(R.string.settings_category_grid_columns_option_summary_dense)
                    else -> context.getString(R.string.settings_category_grid_columns_option_summary_default)
                }
            )
        }
    }
    val editorAskEveryTime = stringResource(R.string.settings_editor_ask_every_time)
    val editorBuiltinCustomTag = stringResource(R.string.settings_editor_builtin_custom_tag)
    val editorLunaBeatMetadata = stringResource(R.string.settings_editor_lunabeat_metadata)
    val editorMusicTag = stringResource(R.string.settings_editor_music_tag)
    val editorLunaBeatLyricTiming = stringResource(R.string.settings_editor_lunabeat_lyric_timing)
    val metadataEditorOptions = listOf(
        TagEditorOptionIds.ASK_EACH_TIME to editorAskEveryTime,
        TagEditorOptionIds.BUILTIN_CUSTOM_TAG to editorBuiltinCustomTag,
        TagEditorOptionIds.LYRICO to "Lyrico",
        TagEditorOptionIds.LUNABEAT_METADATA to editorLunaBeatMetadata,
        TagEditorOptionIds.MUSIC_TAG to editorMusicTag
    )
    val lyricTimingEditorOptions = listOf(
        TagEditorOptionIds.ASK_EACH_TIME to editorAskEveryTime,
        TagEditorOptionIds.LUNABEAT_LYRIC_TIMING to editorLunaBeatLyricTiming
    )
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
    val desktopLyricColorPresets = listOf(
        stringResource(R.string.settings_color_white) to android.graphics.Color.WHITE,
        stringResource(R.string.settings_color_silver_gray) to android.graphics.Color.rgb(191, 191, 191),
        stringResource(R.string.settings_color_light_blue) to android.graphics.Color.rgb(145, 205, 255),
        stringResource(R.string.settings_color_sky_blue) to android.graphics.Color.rgb(3, 169, 244),
        stringResource(R.string.settings_color_soft_pink) to android.graphics.Color.rgb(255, 188, 214),
        stringResource(R.string.settings_color_mint_green) to android.graphics.Color.rgb(166, 235, 203),
        stringResource(R.string.settings_color_neon_green) to android.graphics.Color.rgb(26, 201, 125),
        stringResource(R.string.settings_color_light_purple) to android.graphics.Color.rgb(179, 136, 255),
        stringResource(R.string.settings_color_soft_red) to android.graphics.Color.rgb(255, 112, 112),
        stringResource(R.string.settings_color_warm_yellow) to android.graphics.Color.rgb(255, 224, 150),
        stringResource(R.string.settings_color_orange) to android.graphics.Color.rgb(255, 87, 34)
    )
    val desktopLyricColorEntries = remember(desktopLyricColorPresets) {
        desktopLyricColorPresets.map { DropdownItem(title = it.first) }
    }
    val selectedDesktopLyricColorIndex =
        desktopLyricColorPresets.indexOfFirst { it.second == desktopLyricTextColor }.takeIf { it >= 0 } ?: 0
    val statusLyricPositionLeft = stringResource(R.string.settings_status_position_left)
    val statusLyricPositionCenter = stringResource(R.string.settings_status_position_center)
    val statusLyricPositionRight = stringResource(R.string.settings_status_position_right)
    val statusLyricPositionLabels = remember(
        statusLyricPositionLeft,
        statusLyricPositionCenter,
        statusLyricPositionRight
    ) {
        listOf(statusLyricPositionLeft, statusLyricPositionCenter, statusLyricPositionRight)
    }
    val statusLyricPositionEntries = remember(statusLyricPositionLabels) {
        statusLyricPositionLabels.map { DropdownItem(title = it) }
    }
    val statusLyricSecondaryOff = stringResource(R.string.settings_status_secondary_off)
    val statusLyricSecondaryTranslation = stringResource(R.string.settings_status_secondary_translation)
    val statusLyricSecondaryPronunciation = stringResource(R.string.settings_status_secondary_pronunciation)
    val statusLyricSecondaryLabels = remember(
        statusLyricSecondaryOff,
        statusLyricSecondaryTranslation,
        statusLyricSecondaryPronunciation
    ) {
        listOf(statusLyricSecondaryOff, statusLyricSecondaryTranslation, statusLyricSecondaryPronunciation)
    }
    val statusLyricSecondaryEntries = remember(statusLyricSecondaryLabels) {
        statusLyricSecondaryLabels.map { DropdownItem(title = it) }
    }
    val homeSectionItems = listOf(
        HomePreferenceItem("library", stringResource(R.string.settings_home_section_library), stringResource(R.string.settings_home_section_library_summary)),
        HomePreferenceItem("online", stringResource(R.string.settings_home_section_online), stringResource(R.string.settings_home_section_online_summary)),
        HomePreferenceItem("recent", stringResource(R.string.settings_home_section_recent), stringResource(R.string.settings_home_section_recent_summary))
    )
    val homeLibraryTileItems = listOf(
        HomePreferenceItem("artist", stringResource(R.string.settings_library_tile_artist), stringResource(R.string.settings_library_tile_artist_summary)),
        HomePreferenceItem("album", stringResource(R.string.settings_library_tile_album), stringResource(R.string.settings_library_tile_album_summary)),
        HomePreferenceItem("folder", stringResource(R.string.settings_library_tile_folder), stringResource(R.string.settings_library_tile_folder_summary)),
        HomePreferenceItem("folder_tree", stringResource(R.string.settings_library_tile_folder_tree), stringResource(R.string.settings_library_tile_folder_tree_summary)),
        HomePreferenceItem("playlist", stringResource(R.string.settings_library_tile_playlist), stringResource(R.string.settings_library_tile_playlist_summary)),
        HomePreferenceItem("analytics", stringResource(R.string.settings_library_tile_analytics), stringResource(R.string.settings_library_tile_analytics_summary)),
        HomePreferenceItem("genre", stringResource(R.string.settings_library_tile_genre), stringResource(R.string.settings_library_tile_genre_summary)),
        HomePreferenceItem("year", stringResource(R.string.settings_library_tile_year), stringResource(R.string.settings_library_tile_year_summary)),
        HomePreferenceItem("composer", stringResource(R.string.settings_library_tile_composer), stringResource(R.string.settings_library_tile_composer_summary)),
        HomePreferenceItem("lyricist", stringResource(R.string.settings_library_tile_lyricist), stringResource(R.string.settings_library_tile_lyricist_summary))
    )
    var showHomeDisplayPage by remember { mutableStateOf(false) }
    val dynamicCoverPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setDynamicCoverEnabled(granted) }
        if (granted) {
            Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_enabled), Toast.LENGTH_SHORT).show()
        } else {
            val activity = context as? android.app.Activity
            val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                true
            }
            if (!shouldShowRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_permission_grant), Toast.LENGTH_LONG).show()
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            } else {
                Toast.makeText(context, context.getString(R.string.settings_dynamic_cover_permission_denied), Toast.LENGTH_SHORT).show()
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
                showHomeDisplayPage -> stringResource(R.string.settings_home_display)
                showOnlyLyrics -> stringResource(R.string.settings_lyrics)
                else -> stringResource(R.string.settings_preferences)
            },
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = { if (showHomeDisplayPage) showHomeDisplayPage = false else onBack() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
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
                SmallTitle(text = stringResource(R.string.settings_appearance))

                SettingsCardGroup {
                    Column {
                        WindowSpinnerPreference(
                            title = stringResource(R.string.settings_theme_mode),
                            summary = stringResource(R.string.settings_theme_mode_summary),
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
                            title = stringResource(R.string.settings_category_grid_columns),
                            summary = stringResource(
                                R.string.settings_category_grid_columns_summary,
                                categoryGridColumns.coerceIn(1, 4)
                            ),
                            items = categoryGridEntries,
                            selectedIndex = (categoryGridColumns - 1).coerceIn(categoryGridEntries.indices),
                            onSelectedIndexChange = { index ->
                                scope.launch { settingsManager.setCategoryGridColumns(index + 1) }
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_show_album_artists),
                            summary = stringResource(R.string.settings_show_album_artists_summary),
                            checked = showAlbumArtists,
                            onCheckedChange = {
                                scope.launch { settingsManager.setShowAlbumArtists(it) }
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_open_player_on_play),
                            summary = stringResource(R.string.settings_open_player_on_play_summary),
                            checked = openPlayerOnPlay,
                            onCheckedChange = {
                                scope.launch { settingsManager.setOpenPlayerOnPlay(it) }
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_show_play_next_in_lists),
                            summary = stringResource(R.string.settings_show_play_next_in_lists_summary),
                            checked = showPlayNextInLists,
                            onCheckedChange = {
                                scope.launch { settingsManager.setShowPlayNextInLists(it) }
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_dynamic_cover),
                            summary = stringResource(R.string.settings_dynamic_cover_summary),
                            checked = dynamicCoverEnabled,
                            onCheckedChange = ::setDynamicCoverEnabled
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_player_immersive_cover),
                            summary = stringResource(R.string.settings_player_immersive_cover_summary),
                            checked = playerImmersiveCover,
                            onCheckedChange = {
                                scope.launch { settingsManager.setPlayerImmersiveCover(it) }
                            }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_lyric_font),
                            summary = lyricFontName.ifBlank { stringResource(R.string.settings_system_default) },
                            onClick = onNavigateToLyricFont
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_home_customize))

                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.settings_daily_mix),
                            summary = stringResource(R.string.settings_daily_mix_summary),
                            checked = homeDailyMixVisible,
                            onCheckedChange = {
                                scope.launch { settingsManager.setHomeDailyMixVisible(it) }
                            }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_home_display_items),
                            summary = stringResource(R.string.settings_home_display_items_summary),
                            onClick = { showHomeDisplayPage = true }
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_ai_interpretation))

                SettingsCardGroup {
                    Column {
                        SplitSettingTextField(
                            label = "OpenAI API Key",
                            value = openAiApiKey,
                            summary = stringResource(R.string.settings_openai_api_key_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setOpenAiApiKey(value) } }
                        )
                        SplitSettingTextField(
                            label = "OpenAI Base URL",
                            value = openAiBaseUrl,
                            summary = stringResource(R.string.settings_openai_base_url_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setOpenAiBaseUrl(value) } }
                        )
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_openai_model),
                            value = openAiModel,
                            summary = stringResource(R.string.settings_openai_model_summary, SettingsManager.DEFAULT_OPENAI_MODEL),
                            onValueChange = { value -> scope.launch { settingsManager.setOpenAiModel(value) } }
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_lyric_share_card))

                SettingsCardGroup {
                    Column {
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_lyric_share_custom_info),
                            value = lyricShareCustomInfo,
                            summary = stringResource(R.string.settings_lyric_share_custom_info_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setLyricShareCustomInfo(value) } }
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_tag_scraping))

                SettingsCardGroup {
                    Column {
                        WindowSpinnerPreference(
                            title = stringResource(R.string.settings_metadata_editor),
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
                            title = stringResource(R.string.settings_lyric_timing_editor),
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

                SmallTitle(text = stringResource(R.string.settings_desktop_shortcuts))

                SettingsCardGroup {
                    Column {
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_shortcut_library),
                            value = shortcutLibraryLabel,
                            summary = stringResource(R.string.settings_shortcut_summary),
                            singleLine = true,
                            onValueChange = { value -> scope.launch { settingsManager.setShortcutLibraryLabel(value) } }
                        )
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_shortcut_playlists),
                            value = shortcutPlaylistsLabel,
                            summary = stringResource(R.string.settings_shortcut_summary),
                            singleLine = true,
                            onValueChange = { value -> scope.launch { settingsManager.setShortcutPlaylistsLabel(value) } }
                        )
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_shortcut_folder),
                            value = shortcutFolderLabel,
                            summary = stringResource(R.string.settings_shortcut_summary),
                            singleLine = true,
                            onValueChange = { value -> scope.launch { settingsManager.setShortcutFolderLabel(value) } }
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_scan))

                SettingsCardGroup {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.settings_auto_scan),
                            summary = stringResource(R.string.settings_auto_scan_summary),
                            checked = autoScan,
                            onCheckedChange = {
                                scope.launch { settingsManager.setAutoScan(it) }
                            }
                        )

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_min_duration_filter),
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_min_duration_filter_summary, minDurationSec),
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
                                Text(text = stringResource(R.string.settings_seconds_value, 0), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(text = stringResource(R.string.settings_seconds_value, 60), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }

                        SplitSettingTextField(
                            label = stringResource(R.string.settings_artist_separators),
                            value = artistSeparators,
                            summary = stringResource(R.string.settings_artist_separators_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setArtistSeparators(value) } }
                        )
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_artist_protected_names),
                            value = artistProtectedNames,
                            summary = stringResource(R.string.settings_artist_protected_names_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setArtistProtectedNames(value) } }
                        )
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_genre_separators),
                            value = genreSeparators,
                            summary = stringResource(R.string.settings_genre_separators_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setGenreSeparators(value) } }
                        )
                        SplitSettingTextField(
                            label = stringResource(R.string.settings_genre_protected_names),
                            value = genreProtectedNames,
                            summary = stringResource(R.string.settings_genre_protected_names_summary),
                            onValueChange = { value -> scope.launch { settingsManager.setGenreProtectedNames(value) } }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_tag_ignore_case),
                            summary = stringResource(R.string.settings_tag_ignore_case_summary),
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
                        title = stringResource(R.string.settings_mini_player_lyrics),
                        summary = stringResource(R.string.settings_mini_player_lyrics_summary),
                        checked = miniPlayerLyricsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setMiniPlayerLyricsEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_mini_player_translation),
                        summary = stringResource(R.string.settings_mini_player_translation_summary),
                        enabled = miniPlayerLyricsEnabled,
                        checked = miniPlayerLyricTranslation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setMiniPlayerLyricTranslation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_mini_player_cover_rotation),
                        summary = stringResource(R.string.settings_mini_player_cover_rotation_summary),
                        checked = miniPlayerCoverRotation,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsManager.setMiniPlayerCoverRotation(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_lyricon),
                        summary = stringResource(R.string.settings_enable_lyricon_summary),
                        checked = lyriconEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyriconEnabled(enabled)
                                ?: scope.launch { settingsManager.setLyriconEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_lyricon_translation),
                        summary = stringResource(R.string.settings_lyricon_translation_summary),
                        enabled = lyriconEnabled,
                        checked = lyriconTranslation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyriconTranslation(enabled)
                                ?: scope.launch {
                                    settingsManager.setLyriconTranslation(enabled)
                                    if (enabled) settingsManager.setLyriconPronunciation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_lyricon_pronunciation),
                        summary = stringResource(R.string.settings_lyricon_pronunciation_summary),
                        enabled = lyriconEnabled,
                        checked = lyriconPronunciation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyriconPronunciation(enabled)
                                ?: scope.launch {
                                    settingsManager.setLyriconPronunciation(enabled)
                                    if (enabled) settingsManager.setLyriconTranslation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_desktop_lyric),
                        summary = stringResource(R.string.settings_enable_desktop_lyric_summary),
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

                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_status_bar_lyric_position),
                        summary = stringResource(
                            R.string.settings_current_value,
                            statusLyricPositionLabels[desktopLyricStatusBarPosition.coerceIn(0, 2)]
                        ),
                        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
                        items = statusLyricPositionEntries,
                        selectedIndex = desktopLyricStatusBarPosition.coerceIn(0, 2),
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsManager.setDesktopLyricStatusBarPosition(index)
                                applyDesktopLyricSettings()
                            }
                        }
                    )

                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_status_bar_lyric_secondary),
                        summary = stringResource(
                            R.string.settings_current_value,
                            statusLyricSecondaryLabels[desktopLyricStatusBarSecondary.coerceIn(0, 2)]
                        ),
                        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
                        items = statusLyricSecondaryEntries,
                        selectedIndex = desktopLyricStatusBarSecondary.coerceIn(0, 2),
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                settingsManager.setDesktopLyricStatusBarSecondary(index)
                                applyDesktopLyricSettings()
                            }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_lock_desktop_lyric),
                        summary = stringResource(R.string.settings_lock_desktop_lyric_summary),
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
                            text = stringResource(R.string.settings_desktop_lyric_font_scale, desktopLyricFontScale),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_desktop_lyric_font_scale_summary),
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
                            text = stringResource(R.string.settings_desktop_lyric_translation_scale, desktopLyricTranslationScale),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_desktop_lyric_translation_scale_summary),
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
                            text = stringResource(R.string.settings_desktop_lyric_opacity, desktopLyricOpacity),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_desktop_lyric_opacity_summary),
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
                            text = stringResource(R.string.settings_desktop_lyric_shadow_strength, desktopLyricShadowStrength),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_desktop_lyric_shadow_strength_summary),
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
                        title = stringResource(R.string.settings_desktop_lyric_color),
                        summary = stringResource(
                            R.string.settings_current_value,
                            desktopLyricColorPresets[selectedDesktopLyricColorIndex].first
                        ),
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
                        title = stringResource(R.string.settings_enable_super_lyric),
                        summary = stringResource(R.string.settings_enable_super_lyric_summary),
                        checked = superLyricEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSuperLyricEnabled(enabled)
                                ?: scope.launch { settingsManager.setSuperLyricEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_super_lyric_translation),
                        summary = stringResource(R.string.settings_super_lyric_translation_summary),
                        enabled = superLyricEnabled,
                        checked = superLyricTranslation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSuperLyricTranslation(enabled)
                                ?: scope.launch {
                                    settingsManager.setSuperLyricTranslation(enabled)
                                    if (enabled) settingsManager.setSuperLyricPronunciation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_super_lyric_pronunciation),
                        summary = stringResource(R.string.settings_super_lyric_pronunciation_summary),
                        enabled = superLyricEnabled,
                        checked = superLyricPronunciation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSuperLyricPronunciation(enabled)
                                ?: scope.launch {
                                    settingsManager.setSuperLyricPronunciation(enabled)
                                    if (enabled) settingsManager.setSuperLyricTranslation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_lyric_getter),
                        summary = stringResource(R.string.settings_enable_lyric_getter_summary),
                        checked = lyricGetterEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setLyricGetterEnabled(enabled)
                                ?: scope.launch { settingsManager.setLyricGetterEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_flyme_ticker),
                        summary = stringResource(R.string.settings_enable_flyme_ticker_summary),
                        checked = tickerEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setTickerEnabled(enabled)
                                ?: scope.launch { settingsManager.setTickerEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_hide_flyme_ticker_notification),
                        summary = stringResource(R.string.settings_hide_flyme_ticker_notification_summary),
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
                        title = stringResource(R.string.settings_heads_up_lyric_notifications),
                        summary = stringResource(R.string.settings_heads_up_lyric_notifications_summary),
                        enabled = tickerEnabled,
                        checked = tickerHeadsUpLyrics,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setTickerHeadsUpLyrics(enabled)
                                ?: scope.launch { settingsManager.setTickerHeadsUpLyrics(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_samsung_floating_translation),
                        summary = if (tickerHideNotification) {
                            stringResource(R.string.settings_samsung_floating_translation_summary_blocked)
                        } else {
                            stringResource(R.string.settings_samsung_floating_translation_summary)
                        },
                        enabled = tickerEnabled && !tickerHideNotification,
                        checked = samsungFloatingLyricTranslation && !tickerHideNotification,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setSamsungFloatingLyricTranslation(enabled)
                                ?: scope.launch {
                                    settingsManager.setSamsungFloatingLyricTranslation(enabled)
                                    if (enabled) settingsManager.setStatusBarAllowPhonetic(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_status_bar_phonetic_secondary),
                        summary = stringResource(R.string.settings_status_bar_phonetic_secondary_summary),
                        enabled = tickerEnabled,
                        checked = statusBarAllowPhonetic,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setStatusBarAllowPhonetic(enabled)
                                ?: scope.launch {
                                    settingsManager.setStatusBarAllowPhonetic(enabled)
                                    if (enabled) settingsManager.setSamsungFloatingLyricTranslation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_enable_bluetooth_lyric),
                        summary = stringResource(R.string.settings_enable_bluetooth_lyric_summary),
                        checked = bluetoothLyricEnabled,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setBluetoothLyricEnabled(enabled)
                                ?: scope.launch { settingsManager.setBluetoothLyricEnabled(enabled) }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_bluetooth_lyric_translation),
                        summary = stringResource(R.string.settings_bluetooth_lyric_translation_summary),
                        enabled = bluetoothLyricEnabled,
                        checked = bluetoothLyricTranslation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setBluetoothLyricTranslation(enabled)
                                ?: scope.launch {
                                    settingsManager.setBluetoothLyricTranslation(enabled)
                                    if (enabled) settingsManager.setBluetoothLyricPronunciation(false)
                                }
                        }
                    )

                    SwitchPreference(
                        title = stringResource(R.string.settings_bluetooth_lyric_pronunciation),
                        summary = stringResource(R.string.settings_bluetooth_lyric_pronunciation_summary),
                        enabled = bluetoothLyricEnabled,
                        checked = bluetoothLyricPronunciation,
                        onCheckedChange = { enabled ->
                            playerViewModel?.setBluetoothLyricPronunciation(enabled)
                                ?: scope.launch {
                                    settingsManager.setBluetoothLyricPronunciation(enabled)
                                    if (enabled) settingsManager.setBluetoothLyricTranslation(false)
                                }
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
private fun <T> Flow<T>.collectSettingsState(initialValue: T): State<T> {
    val initial = remember(this) {
        runCatching {
            runBlocking(Dispatchers.IO) { first() }
        }.getOrDefault(initialValue)
    }
    return collectAsState(initial = initial)
}

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
        title = stringResource(R.string.settings_home_sections_title),
        items = orderedSections,
        hiddenIds = hiddenSectionIds,
        onHiddenIdsChange = onHiddenSectionsChange
    )
    SmallTitle(text = stringResource(R.string.settings_home_library_grid_title))
    HomeDisplayGroup(
        title = null,
        items = orderedTiles,
        hiddenIds = hiddenTileIds,
        onHiddenIdsChange = onHiddenTilesChange
    )
    SmallTitle(text = stringResource(R.string.settings_display_order_title))
    SettingsCardGroup {
        Column {
            SplitSettingTextField(
                label = stringResource(R.string.settings_home_sections_order),
                value = sectionOrder,
                summary = stringResource(R.string.settings_home_sections_order_summary),
                singleLine = true,
                onValueChange = onSectionOrderChange
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_home_library_grid_order),
                value = tileOrder,
                summary = stringResource(R.string.settings_home_library_grid_order_summary),
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
                    text = stringResource(R.string.common_select_all),
                    modifier = Modifier.weight(1f),
                    onClick = { onHiddenIdsChange("") }
                )
                HomeDisplayCommand(
                    text = stringResource(R.string.common_invert_selection),
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
