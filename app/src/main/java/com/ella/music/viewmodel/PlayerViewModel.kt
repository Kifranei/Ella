package com.ella.music.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.AppLogStore
import com.ella.music.data.PlaylistStore
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.shiftedBy
import com.ella.music.data.parser.EllaLyricsParser
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.DesktopLyricBridge
import com.ella.music.player.ExoPlayerManager
import com.ella.music.player.LyricGetterBridge
import com.ella.music.player.LyriconBridge
import com.ella.music.player.PlaybackService
import com.ella.music.player.SuperLyricBridge
import com.ella.music.player.TickerBridge
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val repository = MusicRepository.getInstance(application)
    val playerManager = ExoPlayerManager(application)
    val settingsManager = SettingsManager.getInstance(application)
    val lyriconBridge = LyriconBridge(application)
    val tickerBridge = TickerBridge(application)
    val desktopLyricBridge = DesktopLyricBridge(application)
    val superLyricBridge = SuperLyricBridge()
    val lyricGetterBridge = LyricGetterBridge(application)
    private val playlistStore = PlaylistStore.getInstance(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)
    private val playbackStatsTracker = PlayerPlaybackStatsTracker(playbackStatsStore)
    private val lazyOnlineQueueController = PlayerLazyOnlineQueueController(viewModelScope, playerManager)

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val shuffleEnabled: StateFlow<Boolean> = playerManager.shuffleEnabled
    val repeatMode: StateFlow<Int> = playerManager.repeatMode
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val playbackPitch: StateFlow<Float> = playerManager.playbackPitch
    val playlist: StateFlow<List<Song>> = playerManager.playlistFlow
    val userPlaylists: StateFlow<List<UserPlaylist>> = playlistStore.playlists
    val favoriteSongKeys: StateFlow<Set<String>> = playlistStore.playlists
        .map { playlists ->
            playlists
                .firstOrNull { it.isFavorites }
                ?.songs
                ?.mapTo(mutableSetOf()) { it.key }
                ?: emptySet()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, playlistStore.favoriteSongKeys())

    private val _rawLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()
    private val _currentLyricOffsetMs = MutableStateFlow(0L)
    val currentLyricOffsetMs: StateFlow<Long> = _currentLyricOffsetMs.asStateFlow()

    private val _lyricFormatAvailability = MutableStateFlow(MusicRepository.LyricFormatAvailability())
    val lyricFormatAvailability: StateFlow<MusicRepository.LyricFormatAvailability> =
        _lyricFormatAvailability.asStateFlow()

    private val _preferTtmlLyrics = MutableStateFlow<Boolean?>(null)
    val preferTtmlLyrics: StateFlow<Boolean?> = _preferTtmlLyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _showLyricTranslation = MutableStateFlow(true)
    val showLyricTranslation: StateFlow<Boolean> = _showLyricTranslation.asStateFlow()

    private val _showLyricPronunciation = MutableStateFlow(true)
    val showLyricPronunciation: StateFlow<Boolean> = _showLyricPronunciation.asStateFlow()

    private val _locateCurrentSongRequest = MutableStateFlow(0)
    val locateCurrentSongRequest: StateFlow<Int> = _locateCurrentSongRequest.asStateFlow()

    private val sleepTimerController = PlayerSleepTimerController(
        scope = viewModelScope,
        currentSong = { currentSong.value },
        duration = { duration.value },
        currentPosition = { currentPosition.value },
        onPause = { playerManager.pause() }
    )
    val sleepTimerEndRealtimeMs: StateFlow<Long?> = sleepTimerController.sleepTimerEndRealtimeMs
    val stopAfterCurrentEnabled: StateFlow<Boolean> = sleepTimerController.stopAfterCurrentEnabled

    private var positionUpdateJob: Job? = null
    private var lastSentPlayingState: Boolean? = null
    private var lastTickerPayload: Pair<String, String?>? = null
    private var bluetoothLyricEnabled = false
    private var bluetoothLyricTranslationEnabled = true
    private var bluetoothLyricPronunciationEnabled = false
    private var lyriconTranslationEnabled = true
    private var lyriconPronunciationEnabled = false
    private var samsungFloatingLyricTranslationEnabled = false
    private var statusBarAllowPhoneticEnabled = false
    private var tickerHideNotificationEnabled = false
    private var desktopLyricHideWhenPausedEnabled = false
    private var superLyricTranslationEnabled = true
    private var superLyricPronunciationEnabled = false
    private var lyricSourceMode = SettingsManager.LYRIC_SOURCE_AUTO
    private var lyricOffsetOverrides = emptyMap<String, Long>()
    private var lyricBlacklistRules = emptyList<LyricBlacklistRule>()
    private var appliedDecoderMode: Int? = null
    private var appliedAudioFocusDisabled: Boolean? = null
    private var appliedLyricSourceMode: Int? = null
    private var previousButtonAction = SettingsManager.PREVIOUS_BUTTON_PREVIOUS
    private var manualSeekAfterPreviousButton = false
    private var lastBluetoothLyricPayload: Pair<String, String?>? = null
    private var externalLyricResendJob: Job? = null
    private var loadedLyricSongKey: String? = null
    private var lastLyricPositionSongKey: String? = null
    private var lastLyricPositionMs: Long = 0L
    private var suppressLeadingZeroLyric = false

    init {
        playerManager.connect()
        startPositionUpdates()
        observeCurrentSong()
        observePlayState()
        initLyricon()
        initTicker()
        initDesktopLyric()
        initSuperLyric()
        initLyricGetter()
        initLyricPageTranslation()
        initBluetoothLyric()
        initShuffleMode()
        initPlayNextMode()
        initPreviousButtonAction()
        initDecoderMode()
        initAudioFocusMode()
        initReplayGain()
        initLyricSourceMode()
        initLyricLineBlacklist()
        initLyricHeaderTagFilter()
        initLyricOffsetOverrides()
        initBluetoothAutoPlay()
        initExternalPlaybackSync()
        lazyOnlineQueueController.observePlaybackEnd()
    }

    private fun initLyricon() {
        viewModelScope.launch {
            val enabled = settingsManager.lyriconEnabled.first()
            lyriconTranslationEnabled = settingsManager.lyriconTranslation.first()
            lyriconPronunciationEnabled = settingsManager.lyriconPronunciation.first()
            if (lyriconTranslationEnabled && lyriconPronunciationEnabled) {
                lyriconTranslationEnabled = false
                settingsManager.setLyriconTranslation(false)
            }
            lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
            lyriconBridge.setEnabled(enabled)
            if (enabled) resendExternalLyrics()
        }
        viewModelScope.launch {
            settingsManager.lyriconTranslation.distinctUntilChanged().collect { enabled ->
                lyriconTranslationEnabled = enabled
                if (enabled && lyriconPronunciationEnabled) {
                    lyriconPronunciationEnabled = false
                    settingsManager.setLyriconPronunciation(false)
                }
                lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
                if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.lyriconPronunciation.distinctUntilChanged().collect { enabled ->
                lyriconPronunciationEnabled = enabled
                if (enabled && lyriconTranslationEnabled) {
                    lyriconTranslationEnabled = false
                    settingsManager.setLyriconTranslation(false)
                }
                lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
                if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
            }
        }
    }

    private fun initTicker() {
        viewModelScope.launch {
            val enabled = settingsManager.tickerEnabled.first()
            val hideNotification = true
            if (settingsManager.tickerHideNotification.first() != hideNotification) {
                settingsManager.setTickerHideNotification(hideNotification)
            }
            tickerHideNotificationEnabled = hideNotification
            samsungFloatingLyricTranslationEnabled = settingsManager.samsungFloatingLyricTranslation.first()
            statusBarAllowPhoneticEnabled = settingsManager.statusBarAllowPhonetic.first()
            tickerBridge.setHideNotification(hideNotification)
            tickerBridge.setHeadsUpLyricsEnabled(settingsManager.tickerHeadsUpLyrics.first())
            tickerBridge.setEnabled(enabled)
            if (enabled) resendTickerLyric()
        }
        viewModelScope.launch {
            settingsManager.tickerHideNotification.distinctUntilChanged().collect { enabled ->
                if (!enabled) {
                    settingsManager.setTickerHideNotification(true)
                    return@collect
                }
                tickerHideNotificationEnabled = true
                tickerBridge.setHideNotification(true)
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.tickerHeadsUpLyrics.distinctUntilChanged().collect { enabled ->
                tickerBridge.setHeadsUpLyricsEnabled(enabled)
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.samsungFloatingLyricTranslation.distinctUntilChanged().collect { enabled ->
                samsungFloatingLyricTranslationEnabled = enabled
                if (samsungFloatingLyricTranslationEnabled && statusBarAllowPhoneticEnabled) {
                    statusBarAllowPhoneticEnabled = false
                    settingsManager.setStatusBarAllowPhonetic(false)
                }
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric()
            }
        }
        viewModelScope.launch {
            settingsManager.statusBarAllowPhonetic.distinctUntilChanged().collect { enabled ->
                statusBarAllowPhoneticEnabled = enabled
                if (enabled && samsungFloatingLyricTranslationEnabled) {
                    samsungFloatingLyricTranslationEnabled = false
                    settingsManager.setSamsungFloatingLyricTranslation(false)
                }
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
    }

    private fun initDesktopLyric() {
        viewModelScope.launch {
            val enabled = settingsManager.desktopLyricEnabled.first()
            desktopLyricHideWhenPausedEnabled = settingsManager.desktopLyricHideWhenPaused.first()
            desktopLyricBridge.setEnabled(enabled)
            if (enabled) resendDesktopLyric()
        }
        viewModelScope.launch {
            settingsManager.desktopLyricHideWhenPaused.distinctUntilChanged().collect { enabled ->
                desktopLyricHideWhenPausedEnabled = enabled
                if (enabled && !isPlaying.value) {
                    desktopLyricBridge.clearLyric()
                } else {
                    resendDesktopLyric()
                }
            }
        }
    }

    private fun initSuperLyric() {
        viewModelScope.launch {
            val enabled = settingsManager.superLyricEnabled.first()
            superLyricTranslationEnabled = settingsManager.superLyricTranslation.first()
            superLyricPronunciationEnabled = settingsManager.superLyricPronunciation.first()
            if (superLyricTranslationEnabled && superLyricPronunciationEnabled) {
                superLyricTranslationEnabled = false
                settingsManager.setSuperLyricTranslation(false)
            }
            superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
            superLyricBridge.setEnabled(enabled)
            if (enabled) resendSuperLyric()
        }
        viewModelScope.launch {
            settingsManager.superLyricTranslation.distinctUntilChanged().collect { enabled ->
                superLyricTranslationEnabled = enabled
                if (enabled && superLyricPronunciationEnabled) {
                    superLyricPronunciationEnabled = false
                    settingsManager.setSuperLyricPronunciation(false)
                }
                superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
                if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.superLyricPronunciation.distinctUntilChanged().collect { enabled ->
                superLyricPronunciationEnabled = enabled
                if (enabled && superLyricTranslationEnabled) {
                    superLyricTranslationEnabled = false
                    settingsManager.setSuperLyricTranslation(false)
                }
                superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
                if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
            }
        }
    }

    private fun initLyricGetter() {
        viewModelScope.launch {
            settingsManager.lyricGetterEnabled.distinctUntilChanged().collect { enabled ->
                lyricGetterBridge.setEnabled(enabled)
                if (enabled) resendLyricGetter(force = true)
            }
        }
    }

    private fun currentLyriconSecondaryMode(): LyriconBridge.SecondaryMode =
        lyriconSecondaryMode(
            translationEnabled = lyriconTranslationEnabled,
            pronunciationEnabled = lyriconPronunciationEnabled
        )

    private fun currentSuperLyricSecondaryMode(): SuperLyricBridge.SecondaryMode =
        superLyricSecondaryMode(
            translationEnabled = superLyricTranslationEnabled,
            pronunciationEnabled = superLyricPronunciationEnabled
        )

    private fun initBluetoothLyric() {
        viewModelScope.launch {
            bluetoothLyricTranslationEnabled = settingsManager.bluetoothLyricTranslation.first()
            bluetoothLyricPronunciationEnabled = settingsManager.bluetoothLyricPronunciation.first()
            if (bluetoothLyricTranslationEnabled && bluetoothLyricPronunciationEnabled) {
                bluetoothLyricTranslationEnabled = false
                settingsManager.setBluetoothLyricTranslation(false)
            }
        }
        viewModelScope.launch {
            settingsManager.bluetoothLyricEnabled.distinctUntilChanged().collect { enabled ->
                bluetoothLyricEnabled = enabled
                lastBluetoothLyricPayload = null

                if (enabled) {
                    resendBluetoothLyric()
                } else {
                    playerManager.clearBluetoothLyric()
                }
            }
        }
        viewModelScope.launch {
            settingsManager.bluetoothLyricTranslation.distinctUntilChanged().collect { enabled ->
                bluetoothLyricTranslationEnabled = enabled
                if (enabled && bluetoothLyricPronunciationEnabled) {
                    bluetoothLyricPronunciationEnabled = false
                    settingsManager.setBluetoothLyricPronunciation(false)
                }
                lastBluetoothLyricPayload = null
                if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.bluetoothLyricPronunciation.distinctUntilChanged().collect { enabled ->
                bluetoothLyricPronunciationEnabled = enabled
                if (enabled && bluetoothLyricTranslationEnabled) {
                    bluetoothLyricTranslationEnabled = false
                    settingsManager.setBluetoothLyricTranslation(false)
                }
                lastBluetoothLyricPayload = null
                if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            }
        }
    }

    private fun initShuffleMode() {
        viewModelScope.launch {
            settingsManager.shuffleMode.distinctUntilChanged().collect { mode ->
                playerManager.setShuffleMode(mode)
            }
        }
    }

    private fun initPlayNextMode() {
        viewModelScope.launch {
            settingsManager.playNextMode.distinctUntilChanged().collect { mode ->
                playerManager.setPlayNextMode(mode)
            }
        }
    }

    private fun initPreviousButtonAction() {
        viewModelScope.launch {
            settingsManager.previousButtonAction.distinctUntilChanged().collect { action ->
                previousButtonAction = action.coerceIn(
                    SettingsManager.PREVIOUS_BUTTON_PREVIOUS,
                    SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT
                )
            }
        }
    }

    private fun initDecoderMode() {
        viewModelScope.launch {
            settingsManager.decoderMode.collect { mode ->
                if (appliedDecoderMode == null) {
                    appliedDecoderMode = mode
                    return@collect
                }
                if (appliedDecoderMode == mode) return@collect
                appliedDecoderMode = mode
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Decoder mode changed to $mode")
            }
        }
    }

    private fun initAudioFocusMode() {
        viewModelScope.launch {
            settingsManager.audioFocusDisabled.distinctUntilChanged().collect { disabled ->
                if (appliedAudioFocusDisabled == null) {
                    appliedAudioFocusDisabled = disabled
                    return@collect
                }
                if (appliedAudioFocusDisabled == disabled) return@collect
                appliedAudioFocusDisabled = disabled
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Audio focus disabled changed to $disabled")
            }
        }
    }

    private fun initLyricSourceMode() {
        viewModelScope.launch {
            settingsManager.lyricSourceMode.distinctUntilChanged().collect { mode ->
                val safeMode = mode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
                if (appliedLyricSourceMode == null) {
                    appliedLyricSourceMode = safeMode
                    lyricSourceMode = safeMode
                    return@collect
                }
                if (appliedLyricSourceMode == safeMode) return@collect
                appliedLyricSourceMode = safeMode
                lyricSourceMode = safeMode
                currentSong.value?.let { reloadLyrics(it, force = true) }
            }
        }
    }

    private fun initReplayGain() {
        viewModelScope.launch {
            combine(
                settingsManager.replayGainEnabled.distinctUntilChanged(),
                currentSong
            ) { enabled, song -> enabled to song }
                .collectLatest { (enabled, song) ->
                    val volume = if (enabled && song != null) {
                        withContext(Dispatchers.IO) {
                            repository.getReplayGain(song)
                        }.toReplayGainVolume()
                    } else {
                        1f
                    }
                    playerManager.setReplayGainVolume(volume)
                }
        }
    }

    private fun initLyricOffsetOverrides() {
        viewModelScope.launch {
            settingsManager.lyricOffsetOverrides.distinctUntilChanged().collect { overrides ->
                lyricOffsetOverrides = overrides
                applyCurrentLyricOffset(notifyExternal = true)
            }
        }
    }

    private fun initBluetoothAutoPlay() {
        viewModelScope.launch {
            PlaybackService.bluetoothConnectEvent.collect {
                if (currentSong.value != null && !isPlaying.value) {
                    playerManager.play()
                    AppLogStore.info(getApplication(), "BtAutoPlay", "Resumed existing queue on Bluetooth connect")
                } else if (currentSong.value == null && playerManager.hasSavedQueue()) {
                    playerManager.play()
                    AppLogStore.info(getApplication(), "BtAutoPlay", "Restored saved queue on Bluetooth connect")
                }
            }
        }
    }

    private fun initLyricLineBlacklist() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.lyricLineBlacklist.distinctUntilChanged().collect { rules ->
                lyricBlacklistRules = rules.map(::LyricBlacklistRule)
                if (!initialized) {
                    initialized = true
                    applyCurrentLyricOffset(notifyExternal = false)
                    return@collect
                }
                applyCurrentLyricOffset(notifyExternal = true)
            }
        }
    }

    private fun initLyricHeaderTagFilter() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.ignoreLyricHeaderTags.distinctUntilChanged().collect {
                if (!initialized) {
                    initialized = true
                    return@collect
                }
                currentSong.value?.let { song -> reloadLyrics(song, force = true) }
            }
        }
    }

    private fun initExternalPlaybackSync() {
        viewModelScope.launch {
            PlaybackService.externalPlaybackChangeEvent.collect { snapshot ->
                playerManager.ensureConnected(refreshStateIfConnected = false)
                playerManager.applyExternalPlaybackSnapshot(snapshot)
            }
        }
        viewModelScope.launch {
            PlaybackService.externalPlaybackModeEvent.collect { snapshot ->
                playerManager.ensureConnected(refreshStateIfConnected = false)
                playerManager.applyExternalPlaybackMode(
                    shuffle = snapshot.shuffle,
                    repeatMode = snapshot.repeatMode
                )
            }
        }
    }

    private fun sendBluetoothLyric(index: Int, lyrics: List<LyricLine>) {
        if (!bluetoothLyricEnabled) return
        if (!playerManager.isPlaying.value) return

        val payload = lyrics.bluetoothPayloadAt(
            index = index,
            includeTranslation = bluetoothLyricTranslationEnabled,
            includePronunciation = bluetoothLyricPronunciationEnabled
        ) ?: return
        if (payload == lastBluetoothLyricPayload) return

        lastBluetoothLyricPayload = payload
        playerManager.updateBluetoothLyric(payload.first, payload.second)
    }

    private fun resendBluetoothLyric(force: Boolean = false) {
        if (!bluetoothLyricEnabled || !isPlaying.value) return

        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        val payload = currentLyrics.bluetoothPayloadAt(
            index = index,
            includeTranslation = bluetoothLyricTranslationEnabled,
            includePronunciation = bluetoothLyricPronunciationEnabled
        ) ?: return
        if (!force && payload == lastBluetoothLyricPayload) return

        lastBluetoothLyricPayload = payload
        playerManager.updateBluetoothLyric(payload.first, payload.second)
    }

    private fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    playerManager.updatePosition()
                    updateCurrentLyricIndex()
                    updatePlaybackStats()
                    updateSleepTimer()

                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendPosition(playerManager.currentPosition.value)
                    }
                    updateDesktopLyricFrame()
                }.onFailure { error ->
                    AppLogStore.warn(
                        getApplication(),
                        "PlayerPosition",
                        "Position update loop iteration failed; keeping ticker alive",
                        error
                    )
                }

                delay(50)
            }
        }
    }

    private fun observeCurrentSong() {
        viewModelScope.launch {
            playerManager.currentSong.collectLatest { song ->
                if (song != null) {
                    val songKey = song.lyricIdentityKey()
                    if (loadedLyricSongKey == songKey) {
                        updateCurrentLyricIndex()
                        return@collectLatest
                    }
                    suppressLeadingZeroLyric = true
                    _rawLyrics.value = emptyList()
                    _lyrics.value = emptyList()
                    _currentLyricIndex.value = -1
                    // Clear external bridge state before async fetch to prevent stale lyrics
                    lastTickerPayload = null
                    lastBluetoothLyricPayload = null
                    lyricGetterBridge.clearLyric()
                    tickerBridge.clearLyric()
                    if (desktopLyricHideWhenPausedEnabled) {
                        desktopLyricBridge.clearLyric()
                    }
                    superLyricBridge.sendStop()
                    // Send song metadata to bridges BEFORE setting lyrics,
                    // so the 50ms update loop can't send new lyrics with old metadata
                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendSong(song, emptyList())
                    }
                    superLyricBridge.sendSong(song)
                    val songLyrics = repository.getLyrics(song, lyricSourceMode)
                    repository.getCoverArt(song)
                    // Verify song hasn't changed during async fetch
                    if (playerManager.currentSong.value?.lyricIdentityKey() != songKey) {
                        return@collectLatest
                    }
                    loadedLyricSongKey = songKey
                    setLoadedLyrics(song, songLyrics, notifyExternal = false)
                    val displayedLyrics = _lyrics.value

                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendSong(song, displayedLyrics)
                    }
                    if (displayedLyrics.isEmpty()) {
                        clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
                    } else {
                        scheduleExternalLyricResend()
                    }
                } else {
                    loadedLyricSongKey = null
                    suppressLeadingZeroLyric = false
                    _rawLyrics.value = emptyList()
                    _lyrics.value = emptyList()
                    _currentLyricOffsetMs.value = 0L
                    _currentLyricIndex.value = -1
                    clearExternalLyrics(clearLyricon = true, clearSuperLyricSong = true)
                }
            }
        }
    }

    private fun observePlayState() {
        viewModelScope.launch {
            playerManager.isPlaying.collect { playing ->
                if (lastSentPlayingState != playing) {
                    lastSentPlayingState = playing
                    lyriconBridge.sendPlaybackState(playing)
                    if (!playing) {
                        tickerBridge.clearLyric()
                        if (desktopLyricHideWhenPausedEnabled) {
                            desktopLyricBridge.clearLyric()
                        } else {
                            resendDesktopLyric()
                        }
                        superLyricBridge.sendStop()
                        lyricGetterBridge.clearLyric()
                        playerManager.clearBluetoothLyric()
                        lastBluetoothLyricPayload = null
                    } else {
                        viewModelScope.launch { resendExternalLyrics(force = true) }
                        resendBluetoothLyric(force = true)
                    }
                }
            }
        }
    }

    private fun sendSuperLyricAt(index: Int, lyrics: List<LyricLine>) {
        if (!superLyricBridge.isEnabled() || !isPlaying.value) return

        val line = lyrics.getOrNull(index) ?: return
        superLyricBridge.sendLyric(
            line = line,
            positionMs = currentPosition.value,
            showTranslation = _showLyricTranslation.value && superLyricTranslationEnabled
        )
    }

    private fun updateCurrentLyricIndex() {
        val currentLyrics = _lyrics.value
        if (currentLyrics.isEmpty()) {
            lastLyricPositionSongKey = currentSong.value?.lyricIdentityKey()
            lastLyricPositionMs = playerManager.currentPosition.value
            return
        }

        val position = playerManager.currentPosition.value
        val songKey = currentSong.value?.lyricIdentityKey()
        val previousPosition = if (lastLyricPositionSongKey == songKey) lastLyricPositionMs else position
        val loopedToStart = playerManager.repeatMode.value == Player.REPEAT_MODE_ONE &&
            previousPosition > 1_500L &&
            position <= 750L &&
            previousPosition - position > 1_500L

        val indexResult = currentLyricIndexAt(
            positionMs = position,
            lyrics = currentLyrics,
            suppressLeadingZero = suppressLeadingZeroLyric
        )
        val index = indexResult.index
        if (!indexResult.suppressedLeadingZero) {
            suppressLeadingZeroLyric = false
        }
        if (loopedToStart && index < 0 && _currentLyricIndex.value >= 0) {
            _currentLyricIndex.value = -1
        }
        if (index != _currentLyricIndex.value) {
            _currentLyricIndex.value = index

            if (index >= 0 && index < currentLyrics.size) {
                sendTickerLyric(index, currentLyrics)
                sendBluetoothLyric(index, currentLyrics)
                sendSuperLyricAt(index, currentLyrics)
                sendLyricGetterAt(index, currentLyrics)
            }
        }
        lastLyricPositionSongKey = songKey
        lastLyricPositionMs = position
    }

    private suspend fun updatePlaybackStats() {
        playbackStatsTracker.update(
            nowMs = SystemClock.elapsedRealtime(),
            song = currentSong.value,
            isPlaying = isPlaying.value
        )
    }

    private suspend fun resendExternalLyrics(force: Boolean = false) {
        val song = currentSong.value ?: return
        val songKey = song.lyricIdentityKey()
        // Guard: if lyrics are loaded for a different song, skip this resend
        if (loadedLyricSongKey != null && loadedLyricSongKey != songKey) return
        if (_lyrics.value.isEmpty()) {
            val loaded = repository.getLyrics(song, lyricSourceMode)
            setLoadedLyrics(song, loaded, notifyExternal = false)
        }
        val songLyrics = _lyrics.value
        // Re-verify after potential async fetch
        if (playerManager.currentSong.value?.lyricIdentityKey() != songKey) return
        lyriconBridge.sendSong(song, songLyrics)
        lyriconBridge.sendPlaybackState(isPlaying.value)
        lyriconBridge.sendPosition(currentPosition.value)
        if (songLyrics.isEmpty()) {
            clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
            return
        }
        resendTickerLyric(force)
        resendDesktopLyric()
        resendSuperLyric(force)
        resendLyricGetter(force)
    }

    private fun resendTickerLyric(force: Boolean = false) {
        if (!tickerBridge.isEnabled() || !isPlaying.value) return
        if (force) lastTickerPayload = null
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        sendTickerLyric(index, currentLyrics)
    }

    private fun resendDesktopLyric() {
        if (!desktopLyricBridge.isEnabled()) return
        if (desktopLyricHideWhenPausedEnabled && !isPlaying.value) return
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        desktopLyricBridge.sendLyric(
            line = currentLyrics.getOrNull(index),
            positionMs = currentPosition.value,
            showTranslation = _showLyricTranslation.value,
            showPronunciation = _showLyricPronunciation.value
        )
    }

    private fun updateDesktopLyricFrame() {
        if (!desktopLyricBridge.isEnabled()) return
        if (desktopLyricHideWhenPausedEnabled && !isPlaying.value) return
        val index = _currentLyricIndex.value
        val line = _lyrics.value.getOrNull(index) ?: return
        desktopLyricBridge.sendLyric(line, currentPosition.value, _showLyricTranslation.value, _showLyricPronunciation.value)
    }

    private fun resendSuperLyric(force: Boolean = false) {
        if (!superLyricBridge.isEnabled() || !isPlaying.value) return
        val index = _currentLyricIndex.value
        val line = _lyrics.value.getOrNull(index) ?: return
        superLyricBridge.sendLyric(line, currentPosition.value, _showLyricTranslation.value && superLyricTranslationEnabled, force)
    }

    private fun sendLyricGetterAt(index: Int, lyrics: List<LyricLine>) {
        if (!lyricGetterBridge.isEnabled() || !isPlaying.value) return
        lyricGetterBridge.sendLyric(lyrics.getOrNull(index))
    }

    private fun resendLyricGetter(force: Boolean = false) {
        if (!lyricGetterBridge.isEnabled() || !isPlaying.value) return
        lyricGetterBridge.sendLyric(_lyrics.value.getOrNull(_currentLyricIndex.value), force)
    }

    private fun setLoadedLyrics(
        song: Song,
        rawLyrics: List<LyricLine>,
        notifyExternal: Boolean
    ) {
        _rawLyrics.value = rawLyrics
        applyCurrentLyricOffset(song = song, notifyExternal = notifyExternal)
    }

    private fun applyCurrentLyricOffset(
        song: Song? = currentSong.value,
        notifyExternal: Boolean = false
    ) {
        if (song == null) {
            _currentLyricOffsetMs.value = 0L
            val nextLyrics = _rawLyrics.value.preparedForDisplay()
            if (_lyrics.value != nextLyrics) {
                _lyrics.value = nextLyrics
                _currentLyricIndex.value = -1
            }
            return
        }
        val offsetMs = lyricOffsetOverrides[song.lyricIdentityKey()] ?: 0L
        _currentLyricOffsetMs.value = offsetMs
        val nextLyrics = _rawLyrics.value
            .filterBlacklistedLyricLines()
            .shiftedBy(offsetMs)
            .withImplicitLineEndTimes()
        val lyricsChanged = _lyrics.value != nextLyrics
        if (lyricsChanged) {
            _lyrics.value = nextLyrics
            _currentLyricIndex.value = -1
            suppressLeadingZeroLyric = true
            updateCurrentLyricIndex()
            lastTickerPayload = null
            lastBluetoothLyricPayload = null
        }
        if (!notifyExternal) return
        if (lyriconBridge.isEnabled()) lyriconBridge.sendSong(song, _lyrics.value)
        superLyricBridge.sendSong(song)
        if (_lyrics.value.isEmpty()) {
            clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
        } else {
            resendTickerLyric(force = true)
            resendDesktopLyric()
            resendSuperLyric(force = true)
            resendLyricGetter(force = true)
            resendBluetoothLyric(force = true)
            scheduleExternalLyricResend()
        }
    }

    private fun scheduleExternalLyricResend() {
        externalLyricResendJob?.cancel()
        val scheduledSongKey = currentSong.value?.lyricIdentityKey()
        externalLyricResendJob = viewModelScope.launch {
            repeat(3) { attempt ->
                delay(350L + attempt * 550L)
                // Skip if song changed since scheduling
                if (currentSong.value?.lyricIdentityKey() != scheduledSongKey) return@launch
                resendExternalLyrics(force = true)
                resendBluetoothLyric(force = true)
                resendLyricGetter(force = true)
            }
        }
    }

    private fun List<LyricLine>.filterBlacklistedLyricLines(): List<LyricLine> =
        filterBlacklistedLyricLines(lyricBlacklistRules)

    private fun LyricLine.withoutBlacklistedParts(rules: List<LyricBlacklistRule>): LyricLine? {
        fun blocked(text: String?): Boolean =
            text?.let {
                EllaLyricsParser.isIgnorableRawLyricLine(it) || EllaLyricsParser.isPlaceholderOnlyLine(it)
            } == true || rules.any { it.matches(text) }
        val textBlocked = blocked(text)
        val translationBlocked = blocked(translation)
        val pronunciationBlocked = blocked(pronunciation)
        val backgroundBlocked = blocked(backgroundText)
        val backgroundTranslationBlocked = blocked(backgroundTranslation)

        val remainingText = text.takeUnless { textBlocked }.orEmpty()
        val remainingTranslation = translation.takeUnless { translationBlocked }
        val remainingPronunciation = pronunciation.takeUnless { pronunciationBlocked }
        val remainingBackgroundText = backgroundText.takeUnless { backgroundBlocked }
        val remainingBackgroundTranslation = backgroundTranslation.takeUnless { backgroundTranslationBlocked }

        val promotedText = remainingText.ifBlank {
            remainingTranslation
                ?.takeIf { it.isNotBlank() }
                ?: remainingPronunciation?.takeIf { it.isNotBlank() }
                ?: remainingBackgroundText?.takeIf { it.isNotBlank() }
                ?: ""
        }
        val promotedFromTranslation = remainingText.isBlank() && promotedText == remainingTranslation
        val promotedFromPronunciation = remainingText.isBlank() && promotedText == remainingPronunciation
        val promotedFromBackground = remainingText.isBlank() && promotedText == remainingBackgroundText

        val filtered = copy(
            text = promotedText,
            words = if (textBlocked || promotedText != text) emptyList() else words,
            translation = remainingTranslation.takeUnless { promotedFromTranslation },
            pronunciation = remainingPronunciation.takeUnless { promotedFromPronunciation },
            pronunciationWords = if (pronunciationBlocked || promotedFromPronunciation) emptyList() else pronunciationWords,
            backgroundText = remainingBackgroundText.takeUnless { promotedFromBackground },
            backgroundWords = if (backgroundBlocked || promotedFromBackground) emptyList() else backgroundWords,
            backgroundTranslation = remainingBackgroundTranslation
        )
        return filtered.takeIf {
            it.text.isNotBlank() ||
                !it.translation.isNullOrBlank() ||
                !it.pronunciation.isNullOrBlank() ||
                !it.backgroundText.isNullOrBlank() ||
                !it.backgroundTranslation.isNullOrBlank()
        }
    }

    private fun List<LyricLine>.preparedForDisplay(): List<LyricLine> =
        preparedForDisplay(lyricBlacklistRules)

    private fun List<LyricLine>.withImplicitLineEndTimes(): List<LyricLine> {
        if (isEmpty()) return this
        return mapIndexed { index, line ->
            val nextStartMs = getOrNull(index + 1)?.timeMs
            if (
                !line.isTtml &&
                line.endMs == null &&
                nextStartMs != null &&
                nextStartMs > line.timeMs &&
                line.words.isEmpty() &&
                line.backgroundWords.isEmpty()
            ) {
                line.copy(endMs = nextStartMs)
            } else {
                line
            }
        }
    }

    private fun clearExternalLyrics(clearLyricon: Boolean, clearSuperLyricSong: Boolean) {
        externalLyricResendJob?.cancel()
        lastTickerPayload = null
        lastBluetoothLyricPayload = null
        tickerBridge.clearLyric()
        desktopLyricBridge.clearLyric()
        lyricGetterBridge.clearLyric()
        playerManager.clearBluetoothLyric()
        if (clearLyricon) lyriconBridge.clearSong()
        if (clearSuperLyricSong) {
            superLyricBridge.destroy()
        } else {
            superLyricBridge.sendStop()
        }
    }

    private fun initLyricPageTranslation() {
        viewModelScope.launch {
            settingsManager.lyricPageTranslation.distinctUntilChanged().collect { enabled ->
                _showLyricTranslation.value = enabled
            }
        }
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        lazyOnlineQueueController.clear()
        playerManager.setPlaylist(songs, startIndex)
    }

    fun setLazyOnlinePlaylist(
        songs: List<Song>,
        startIndex: Int,
        resolvedStartSong: Song,
        resolver: suspend (Song) -> Song
    ) {
        lazyOnlineQueueController.setQueue(
            songs = songs,
            startIndex = startIndex,
            resolvedStartSong = resolvedStartSong,
            resolver = resolver
        )
    }

    fun playSong(song: Song) {
        playerManager.playSong(song)
    }

    fun playRestoredQueue() {
        playerManager.play()
    }

    fun hasSavedPlaybackQueue(): Boolean = playerManager.hasSavedQueue()

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun skipToNext() {
        if (!lazyOnlineQueueController.playOffset(1)) playerManager.skipToNext()
    }

    fun skipToPrevious() {
        if (shouldReplayCurrentFromPreviousButton()) {
            playerManager.restartCurrent()
            return
        }
        manualSeekAfterPreviousButton = false
        if (!lazyOnlineQueueController.playOffset(-1)) playerManager.skipToPrevious()
    }

    /**
     * Always move to the previous track, ignoring the "previous button replays current song"
     * preference. Used by the landscape cover-wall swipe, where the gesture unambiguously means
     * "go to that cover" rather than "restart this one".
     */
    fun skipToPreviousTrack() {
        manualSeekAfterPreviousButton = false
        if (!lazyOnlineQueueController.playOffset(-1)) playerManager.skipToPrevious()
    }

    private fun shouldReplayCurrentFromPreviousButton(): Boolean {
        if (manualSeekAfterPreviousButton) {
            manualSeekAfterPreviousButton = false
            return false
        }
        return shouldReplayFromPreviousButton(
            manualSeekAfterPreviousButton = false,
            previousButtonAction = previousButtonAction,
            currentPositionMs = currentPosition.value
        )
    }

    fun seekTo(positionMs: Long) {
        manualSeekAfterPreviousButton = true
        playerManager.seekTo(positionMs)
        lyriconBridge.seekTo(positionMs)

        val lyrics = _lyrics.value
        val index = currentLyricIndexAt(
            positionMs = positionMs,
            lyrics = lyrics,
            suppressLeadingZero = positionMs in 0L until LEADING_ZERO_LYRIC_SUPPRESSION_MS
        ).index
        _currentLyricIndex.value = index
        if (index >= 0) {
            if (superLyricBridge.isEnabled() && isPlaying.value) {
                superLyricBridge.sendLyric(
                    line = lyrics[index],
                    positionMs = positionMs,
                    showTranslation = _showLyricTranslation.value && superLyricTranslationEnabled
                )
            }
        }
    }

    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeat() = playerManager.toggleRepeat()
    fun setShuffleMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setShuffleMode(mode)
            playerManager.setShuffleMode(mode)
        }
    }

    fun setPlayNextMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setPlayNextMode(mode)
            playerManager.setPlayNextMode(mode)
        }
    }

    fun setPreviousButtonAction(action: Int) {
        previousButtonAction = action.coerceIn(
            SettingsManager.PREVIOUS_BUTTON_PREVIOUS,
            SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT
        )
        viewModelScope.launch {
            settingsManager.setPreviousButtonAction(previousButtonAction)
        }
    }

    fun setDecoderMode(mode: Int) {
        viewModelScope.launch {
            val safeMode = mode.coerceIn(0, 2)
            settingsManager.setDecoderMode(safeMode)
            if (appliedDecoderMode != safeMode) {
                appliedDecoderMode = safeMode
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Decoder mode changed to $safeMode")
            }
        }
    }
    fun addToPlaylist(song: Song) {
        lazyOnlineQueueController.clear()
        playerManager.addToPlaylist(song)
    }
    fun addToPlaylist(songs: List<Song>) {
        lazyOnlineQueueController.clear()
        playerManager.addToPlaylist(songs)
    }

    fun playNext(song: Song) {
        lazyOnlineQueueController.clear()
        playerManager.playNext(song)
    }

    fun playNext(songs: List<Song>) {
        lazyOnlineQueueController.clear()
        playerManager.playNext(songs)
    }

    /** Re-establish the media controller if the playback session was torn down in the background. */
    fun ensurePlayerConnected() {
        playerManager.ensureConnected()
        startPositionUpdates()
    }

    fun livePositionMs(): Long = playerManager.livePositionMs()

    fun playQueueIndex(index: Int) {
        if (!lazyOnlineQueueController.playIndex(index)) playerManager.playQueueIndex(index)
    }

    fun removeFromPlaylist(index: Int) {
        lazyOnlineQueueController.clear()
        playerManager.removeFromPlaylist(index)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        lazyOnlineQueueController.clear()
        playerManager.movePlaylistItem(fromIndex, toIndex)
    }

    fun clearPlaylist() {
        lazyOnlineQueueController.clear()
        playerManager.clearPlaylist()
    }

    fun requestLocateCurrentSong() {
        _locateCurrentSongRequest.value += 1
    }

    fun cyclePlaybackMode() {
        playerManager.cyclePlaybackMode()
    }

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 1200, CoverUsage.Player)

    fun getAudioInfo(song: Song) = repository.getAudioInfo(song)

    fun getSongTagInfo(song: Song) = repository.getSongTagInfo(song)

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    fun setShowLyrics(show: Boolean) {
        _showLyrics.value = show
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackParameters(speed, playbackPitch.value)
    }

    fun setPlaybackPitch(pitch: Float) {
        playerManager.setPlaybackParameters(playbackSpeed.value, pitch)
    }

    fun setLyricSourceMode(mode: Int) {
        viewModelScope.launch {
            _preferTtmlLyrics.value = null
            settingsManager.setLyricSourceMode(mode)
            lyricSourceMode = mode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
            appliedLyricSourceMode = lyricSourceMode
            currentSong.value?.let { reloadLyrics(it, force = true) }
        }
    }

    fun setLyricFormatPreference(preferTtml: Boolean) {
        viewModelScope.launch {
            _preferTtmlLyrics.value = preferTtml
            currentSong.value?.let { reloadLyrics(it, force = true) }
        }
    }

    fun setCurrentLyricOffsetMs(offsetMs: Long) {
        val song = currentSong.value ?: return
        val safeOffset = offsetMs.coerceIn(-5000L, 5000L)
        viewModelScope.launch {
            settingsManager.setLyricOffsetOverride(song.lyricIdentityKey(), safeOffset)
        }
    }

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    fun refreshCurrentSongAfterExternalEdit(updatedFromLibrary: Song?) {
        val current = currentSong.value ?: return
        viewModelScope.launch {
            val updated = updatedFromLibrary
                ?.takeIf { it.lyricIdentityKey() == current.lyricIdentityKey() }
                ?: repository.refreshSongAfterExternalEdit(current)
                ?: current
            repository.clearMetadataCache(current)
            repository.clearMetadataCache(updated)
            playerManager.updateCurrentSongMetadata(updated)
            reloadLyrics(updated, force = true)
        }
    }

    fun toggleCurrentSongFavorite() {
        val song = currentSong.value ?: return
        viewModelScope.launch { playlistStore.toggleFavorite(song) }
    }

    fun isFavorite(song: Song?): Boolean =
        song?.playlistIdentityKey()?.let { it in favoriteSongKeys.value } == true

    private suspend fun reloadLyrics(song: Song, force: Boolean = false) {
        lastTickerPayload = null
        lastBluetoothLyricPayload = null
        val availability = repository.getLyricFormatAvailability(song)
        _lyricFormatAvailability.value = availability
        val formatOverride = _preferTtmlLyrics.value.takeIf { availability.hasBoth }
        if (!availability.hasBoth) _preferTtmlLyrics.value = null
        val songLyrics = if (formatOverride != null) {
            repository.reloadLyricsByFormat(song, formatOverride)
        } else if (force) {
            repository.reloadLyrics(song, lyricSourceMode)
        } else {
            repository.getLyrics(song, lyricSourceMode)
        }
        loadedLyricSongKey = song.lyricIdentityKey()
        setLoadedLyrics(song, songLyrics, notifyExternal = false)
        val displayedLyrics = _lyrics.value
        if (lyriconBridge.isEnabled()) lyriconBridge.sendSong(song, displayedLyrics)
        superLyricBridge.sendSong(song)
        if (displayedLyrics.isEmpty()) {
            clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
        } else {
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            if (desktopLyricBridge.isEnabled()) resendDesktopLyric()
            if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
            if (lyricGetterBridge.isEnabled()) resendLyricGetter(force = true)
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            scheduleExternalLyricResend()
        }
    }

    fun startSleepTimer(
        minutes: Int,
        stopAfterCurrentWhenExpired: Boolean = false
    ) {
        sleepTimerController.start(minutes, stopAfterCurrentWhenExpired)
    }

    fun setStopAfterCurrentEnabled(enabled: Boolean) {
        sleepTimerController.setStopAfterCurrentEnabled(enabled)
    }

    fun cancelSleepTimer() {
        sleepTimerController.cancel()
    }

    private fun updateSleepTimer() {
        sleepTimerController.update()
    }

    fun setLyricPageTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyricPageTranslation(enabled)
            _showLyricTranslation.value = enabled
        }
    }

    fun setLyricPagePronunciation(enabled: Boolean) {
        _showLyricPronunciation.value = enabled
        resendDesktopLyric()
    }

    fun setLyriconEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyriconEnabled(enabled)
            lyriconBridge.setEnabled(enabled)
            if (enabled) {
                currentSong.value?.let { song ->
                    lyriconBridge.sendSong(song, _lyrics.value)
                    lyriconBridge.sendPlaybackState(isPlaying.value)
                }
            }
        }
    }

    fun setLyriconTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyriconTranslation(enabled)
            lyriconTranslationEnabled = enabled
            if (enabled && lyriconPronunciationEnabled) {
                lyriconPronunciationEnabled = false
                settingsManager.setLyriconPronunciation(false)
            }
            lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
            if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
        }
    }

    fun setTickerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerEnabled(enabled)
            if (enabled) {
                settingsManager.setTickerHideNotification(true)
                tickerHideNotificationEnabled = true
            }
            tickerBridge.setHideNotification(true)
            tickerBridge.setHeadsUpLyricsEnabled(settingsManager.tickerHeadsUpLyrics.first())
            tickerBridge.setEnabled(enabled)
            lastTickerPayload = null
            if (enabled) resendTickerLyric()
        }
    }

    fun setTickerHideNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerHideNotification(true)
            tickerHideNotificationEnabled = true
            tickerBridge.setHideNotification(true)
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setTickerHeadsUpLyrics(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerHeadsUpLyrics(enabled)
            tickerBridge.setHeadsUpLyricsEnabled(enabled)
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            val safeEnabled = enabled
            settingsManager.setSamsungFloatingLyricTranslation(safeEnabled)
            samsungFloatingLyricTranslationEnabled = safeEnabled
            if (safeEnabled && statusBarAllowPhoneticEnabled) {
                statusBarAllowPhoneticEnabled = false
                settingsManager.setStatusBarAllowPhonetic(false)
            }
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric()
        }
    }

    fun setStatusBarAllowPhonetic(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setStatusBarAllowPhonetic(enabled)
            statusBarAllowPhoneticEnabled = enabled
            if (enabled && samsungFloatingLyricTranslationEnabled) {
                samsungFloatingLyricTranslationEnabled = false
                settingsManager.setSamsungFloatingLyricTranslation(false)
            }
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setLyriconPronunciation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyriconPronunciation(enabled)
            lyriconPronunciationEnabled = enabled
            if (enabled && lyriconTranslationEnabled) {
                lyriconTranslationEnabled = false
                settingsManager.setLyriconTranslation(false)
            }
            lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
            if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
        }
    }

    fun setDesktopLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDesktopLyricEnabled(enabled)
            desktopLyricBridge.setEnabled(enabled)
            if (enabled) resendDesktopLyric()
        }
    }

    fun setDesktopLyricHideWhenPaused(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDesktopLyricHideWhenPaused(enabled)
            desktopLyricHideWhenPausedEnabled = enabled
            if (enabled && !isPlaying.value) {
                desktopLyricBridge.clearLyric()
            } else {
                resendDesktopLyric()
            }
        }
    }

    fun applyDesktopLyricSettings() {
        desktopLyricBridge.applySettings()
        resendDesktopLyric()
    }

    fun setSuperLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSuperLyricEnabled(enabled)
            superLyricBridge.setEnabled(enabled)
            if (enabled) {
                currentSong.value?.let { superLyricBridge.sendSong(it) }
                resendSuperLyric()
            }
        }
    }

    fun setLyricGetterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyricGetterEnabled(enabled)
            lyricGetterBridge.setEnabled(enabled)
            if (enabled) resendLyricGetter(force = true)
        }
    }

    fun setSuperLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSuperLyricTranslation(enabled)
            superLyricTranslationEnabled = enabled
            if (enabled && superLyricPronunciationEnabled) {
                superLyricPronunciationEnabled = false
                settingsManager.setSuperLyricPronunciation(false)
            }
            superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
            if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
        }
    }

    fun setSuperLyricPronunciation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSuperLyricPronunciation(enabled)
            superLyricPronunciationEnabled = enabled
            if (enabled && superLyricTranslationEnabled) {
                superLyricTranslationEnabled = false
                settingsManager.setSuperLyricTranslation(false)
            }
            superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
            if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
        }
    }

    fun setBluetoothLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBluetoothLyricEnabled(enabled)
            bluetoothLyricEnabled = enabled
            lastBluetoothLyricPayload = null

            if (enabled) {
                resendBluetoothLyric()
            } else {
                playerManager.clearBluetoothLyric()
            }
        }
    }

    fun setBluetoothLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBluetoothLyricTranslation(enabled)
            bluetoothLyricTranslationEnabled = enabled
            if (enabled && bluetoothLyricPronunciationEnabled) {
                bluetoothLyricPronunciationEnabled = false
                settingsManager.setBluetoothLyricPronunciation(false)
            }
            lastBluetoothLyricPayload = null
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
        }
    }

    fun setBluetoothLyricPronunciation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBluetoothLyricPronunciation(enabled)
            bluetoothLyricPronunciationEnabled = enabled
            if (enabled && bluetoothLyricTranslationEnabled) {
                bluetoothLyricTranslationEnabled = false
                settingsManager.setBluetoothLyricTranslation(false)
            }
            lastBluetoothLyricPayload = null
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
        }
    }

    private fun sendTickerLyric(index: Int, lyrics: List<LyricLine>) {
        if (!tickerBridge.isEnabled() || !playerManager.isPlaying.value) return

        val payload = lyrics.lyricPayloadAt(index, samsungFloatingLyricTranslationEnabled) ?: return
        if (payload == lastTickerPayload) return

        lastTickerPayload = payload
        val pronunciation = if (statusBarAllowPhoneticEnabled) {
            lyrics.getOrNull(index)?.pronunciation?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        tickerBridge.sendLyric(payload.first, payload.second, pronunciation)
    }

    override fun onCleared() {
        val pendingStatsFlush = playbackStatsTracker.takePendingFlush()
        if (pendingStatsFlush != null) {
            cleanupScope.launch {
                playbackStatsStore.addListenTime(pendingStatsFlush.song, pendingStatsFlush.listenedMs)
            }
        }
        super.onCleared()
        externalLyricResendJob?.cancel()
        positionUpdateJob?.cancel()
        sleepTimerController.dispose()
        tickerBridge.clearLyric()
        lyricGetterBridge.clearLyric()
        superLyricBridge.destroy()
        lyriconBridge.destroy()
        playerManager.disconnect()
    }
}
