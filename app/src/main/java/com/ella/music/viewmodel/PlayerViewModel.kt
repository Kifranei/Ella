package com.ella.music.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ella.music.data.AppLogStore
import com.ella.music.data.PlaylistStore
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.DesktopLyricBridge
import com.ella.music.player.ExoPlayerManager
import com.ella.music.player.LyricGetterBridge
import com.ella.music.player.LyriconBridge
import com.ella.music.player.SuperLyricBridge
import com.ella.music.player.TickerBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playerManager = ExoPlayerManager(application)
    val settingsManager = SettingsManager(application)
    val lyriconBridge = LyriconBridge(application)
    val tickerBridge = TickerBridge(application)
    val desktopLyricBridge = DesktopLyricBridge(application)
    val superLyricBridge = SuperLyricBridge()
    val lyricGetterBridge = LyricGetterBridge(application)
    private val playlistStore = PlaylistStore.getInstance(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)
    private val minPlaybackStatsListenMs = 20_000L

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

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

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

    private val _sleepTimerEndRealtimeMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndRealtimeMs: StateFlow<Long?> = _sleepTimerEndRealtimeMs.asStateFlow()

    private val _stopAfterCurrentEnabled = MutableStateFlow(false)
    val stopAfterCurrentEnabled: StateFlow<Boolean> = _stopAfterCurrentEnabled.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var lastSentPlayingState: Boolean? = null
    private var lastTickerPayload: Pair<String, String?>? = null
    private var statsSongId: Long? = null
    private var statsSong: Song? = null
    private var playCountedSongId: Long? = null
    private var pendingListenMs = 0L
    private var lastStatsTickMs = 0L

    private var bluetoothLyricEnabled = false
    private var bluetoothLyricTranslationEnabled = false
    private var samsungFloatingLyricTranslationEnabled = false
    private var tickerHideNotificationEnabled = false
    private var superLyricTranslationEnabled = true
    private var lyricSourceMode = SettingsManager.LYRIC_SOURCE_AUTO
    private var appliedDecoderMode: Int? = null
    private var appliedAudioFocusDisabled: Boolean? = null
    private var appliedLyricSourceMode: Int? = null
    private var previousButtonAction = SettingsManager.PREVIOUS_BUTTON_PREVIOUS
    private var manualSeekAfterPreviousButton = false
    private var lastBluetoothLyricPayload: Pair<String, String?>? = null
    private var sleepTimerJob: Job? = null
    private var externalLyricResendJob: Job? = null
    private var stopAfterCurrentSongId: Long? = null
    private var lazyOnlineQueue: LazyOnlineQueue? = null
    private var resolvingLazyQueue = false
    private var loadedLyricSongKey: String? = null

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
        initPreviousButtonAction()
        initDecoderMode()
        initAudioFocusMode()
        initLyricSourceMode()
        observeLazyOnlineQueue()
    }

    private fun initLyricon() {
        viewModelScope.launch {
            val enabled = settingsManager.lyriconEnabled.first()
            val translation = settingsManager.lyriconTranslation.first()
            lyriconBridge.setDisplayTranslation(translation)
            lyriconBridge.setEnabled(enabled)
            if (enabled) resendExternalLyrics()
        }
    }

    private fun initTicker() {
        viewModelScope.launch {
            val enabled = settingsManager.tickerEnabled.first()
            val hideNotification = settingsManager.tickerHideNotification.first()
            tickerHideNotificationEnabled = hideNotification
            samsungFloatingLyricTranslationEnabled = settingsManager.samsungFloatingLyricTranslation.first() && !hideNotification
            tickerBridge.setHideNotification(hideNotification)
            tickerBridge.setEnabled(enabled)
            if (enabled) resendTickerLyric()
        }
        viewModelScope.launch {
            settingsManager.tickerHideNotification.distinctUntilChanged().collect { enabled ->
                tickerHideNotificationEnabled = enabled
                tickerBridge.setHideNotification(enabled)
                if (enabled && samsungFloatingLyricTranslationEnabled) {
                    samsungFloatingLyricTranslationEnabled = false
                    settingsManager.setSamsungFloatingLyricTranslation(false)
                }
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.samsungFloatingLyricTranslation.distinctUntilChanged().collect { enabled ->
                samsungFloatingLyricTranslationEnabled = enabled && !tickerHideNotificationEnabled
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric()
            }
        }
    }

    private fun initDesktopLyric() {
        viewModelScope.launch {
            val enabled = settingsManager.desktopLyricEnabled.first()
            desktopLyricBridge.setEnabled(enabled)
            if (enabled) resendDesktopLyric()
        }
    }

    private fun initSuperLyric() {
        viewModelScope.launch {
            val enabled = settingsManager.superLyricEnabled.first()
            superLyricBridge.setEnabled(enabled)
            if (enabled) resendSuperLyric()
        }
        viewModelScope.launch {
            settingsManager.superLyricTranslation.distinctUntilChanged().collect { enabled ->
                superLyricTranslationEnabled = enabled
                if (superLyricBridge.isEnabled()) resendSuperLyric()
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

    private fun initBluetoothLyric() {
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
                lastBluetoothLyricPayload = null
                if (bluetoothLyricEnabled) resendBluetoothLyric()
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

    private fun sendBluetoothLyric(index: Int, lyrics: List<LyricLine>) {
        if (!bluetoothLyricEnabled) return
        if (!playerManager.isPlaying.value) return

        val payload = lyrics.bluetoothPayloadAt(index) ?: return
        if (payload == lastBluetoothLyricPayload) return

        lastBluetoothLyricPayload = payload
        playerManager.updateBluetoothLyric(payload.first, payload.second)
    }

    private fun resendBluetoothLyric(force: Boolean = false) {
        if (!bluetoothLyricEnabled || !isPlaying.value) return

        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        val payload = currentLyrics.bluetoothPayloadAt(index) ?: return
        if (!force && payload == lastBluetoothLyricPayload) return

        lastBluetoothLyricPayload = payload
        playerManager.updateBluetoothLyric(payload.first, payload.second)
    }

    private fun startPositionUpdates() {
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                playerManager.updatePosition()
                updateCurrentLyricIndex()
                updatePlaybackStats()
                updateSleepTimer()

                if (lyriconBridge.isEnabled()) {
                    lyriconBridge.sendPosition(playerManager.currentPosition.value)
                }
                updateDesktopLyricFrame()

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
                    lastTickerPayload = null
                    lastBluetoothLyricPayload = null
                    lyricGetterBridge.clearLyric()
                    val songLyrics = repository.getLyrics(song, lyricSourceMode)
                    repository.getCoverArt(song)
                    loadedLyricSongKey = songKey
                    _lyrics.value = songLyrics
                    _currentLyricIndex.value = -1

                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendSong(song, songLyrics)
                    }
                    superLyricBridge.sendSong(song)
                    if (songLyrics.isEmpty()) {
                        clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
                    } else {
                        scheduleExternalLyricResend()
                    }
                } else {
                    loadedLyricSongKey = null
                    _lyrics.value = emptyList()
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
                        resendDesktopLyric()
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
        if (currentLyrics.isEmpty()) return

        val position = playerManager.currentPosition.value
        var index = -1
        for (i in currentLyrics.indices.reversed()) {
            if (position >= currentLyrics[i].timeMs) {
                index = i
                break
            }
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
    }

    private suspend fun updatePlaybackStats() {
        val now = SystemClock.elapsedRealtime()
        val song = currentSong.value
        val songId = song?.id

        if (songId != statsSongId) {
            flushPlaybackStats()
            statsSongId = songId
            statsSong = song
            playCountedSongId = null
            lastStatsTickMs = now
            return
        }

        if (song != null && isPlaying.value) {
            if (lastStatsTickMs > 0L) {
                pendingListenMs += (now - lastStatsTickMs).coerceIn(0L, 1500L)
            }
            if (playCountedSongId != song.id && pendingListenMs >= minPlaybackStatsListenMs) {
                playbackStatsStore.recordPlay(song)
                playCountedSongId = song.id
            }
            if (playCountedSongId == song.id && pendingListenMs >= 5000L) {
                playbackStatsStore.addListenTime(song, pendingListenMs)
                pendingListenMs = 0L
            }
        } else {
            flushPlaybackStats()
        }
        lastStatsTickMs = now
    }

    private suspend fun flushPlaybackStats() {
        val song = statsSong
        if (song != null && playCountedSongId == song.id && pendingListenMs > 0L) {
            playbackStatsStore.addListenTime(song, pendingListenMs)
        }
        pendingListenMs = 0L
    }

    private suspend fun resendExternalLyrics(force: Boolean = false) {
        val song = currentSong.value ?: return
        val songLyrics = _lyrics.value.ifEmpty { repository.getLyrics(song, lyricSourceMode) }
        if (_lyrics.value.isEmpty()) _lyrics.value = songLyrics
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

    private fun scheduleExternalLyricResend() {
        externalLyricResendJob?.cancel()
        externalLyricResendJob = viewModelScope.launch {
            repeat(3) { attempt ->
                delay(350L + attempt * 550L)
                resendExternalLyrics(force = true)
                resendBluetoothLyric(force = true)
                resendLyricGetter(force = true)
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
        lazyOnlineQueue = null
        playerManager.setPlaylist(songs, startIndex)
    }

    fun setLazyOnlinePlaylist(
        songs: List<Song>,
        startIndex: Int,
        resolvedStartSong: Song,
        resolver: suspend (Song) -> Song
    ) {
        if (songs.isEmpty()) return
        lazyOnlineQueue = LazyOnlineQueue(
            songs = songs,
            index = startIndex.coerceIn(songs.indices),
            resolver = resolver
        )
        playerManager.playResolvedFromVirtualQueue(songs, startIndex, resolvedStartSong)
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
        if (!playLazyOnlineOffset(1)) playerManager.skipToNext()
    }

    fun skipToPrevious() {
        if (shouldReplayCurrentFromPreviousButton()) {
            playerManager.restartCurrent()
            return
        }
        manualSeekAfterPreviousButton = false
        if (!playLazyOnlineOffset(-1)) playerManager.skipToPrevious()
    }

    private fun shouldReplayCurrentFromPreviousButton(): Boolean {
        if (manualSeekAfterPreviousButton) {
            manualSeekAfterPreviousButton = false
            return false
        }
        return previousButtonAction == SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT &&
            currentPosition.value >= SettingsManager.PREVIOUS_REPLAY_THRESHOLD_MS
    }

    fun seekTo(positionMs: Long) {
        manualSeekAfterPreviousButton = true
        playerManager.seekTo(positionMs)
        lyriconBridge.seekTo(positionMs)

        val lyrics = _lyrics.value
        val index = lyrics.indexOfLast { positionMs >= it.timeMs }
        if (index >= 0) {
            _currentLyricIndex.value = index
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
        lazyOnlineQueue = null
        playerManager.addToPlaylist(song)
    }
    fun addToPlaylist(songs: List<Song>) {
        lazyOnlineQueue = null
        playerManager.addToPlaylist(songs)
    }

    fun playNext(song: Song) {
        lazyOnlineQueue = null
        playerManager.playNext(song)
    }

    fun playQueueIndex(index: Int) {
        if (!playLazyOnlineIndex(index)) playerManager.playQueueIndex(index)
    }
    fun clearPlaylist() {
        lazyOnlineQueue = null
        playerManager.clearPlaylist()
    }

    private fun observeLazyOnlineQueue() {
        viewModelScope.launch {
            playerManager.playbackState.collect { state ->
                if (state == Player.STATE_ENDED) playLazyOnlineOffset(1)
            }
        }
    }

    private fun playLazyOnlineOffset(offset: Int): Boolean {
        val queue = lazyOnlineQueue ?: return false
        return playLazyOnlineIndex(queue.index + offset)
    }

    private fun playLazyOnlineIndex(index: Int): Boolean {
        val queue = lazyOnlineQueue ?: return false
        if (index !in queue.songs.indices || resolvingLazyQueue) return false
        resolvingLazyQueue = true
        viewModelScope.launch {
            runCatching {
                val resolved = queue.resolver(queue.songs[index])
                queue.index = index
                playerManager.playResolvedFromVirtualQueue(queue.songs, index, resolved)
            }
            resolvingLazyQueue = false
        }
        return true
    }

    fun requestLocateCurrentSong() {
        _locateCurrentSongRequest.value += 1
    }

    fun cyclePlaybackMode() {
        val shuffle = shuffleEnabled.value
        val repeat = repeatMode.value
        when {
            shuffle -> {
                playerManager.toggleShuffle()
                if (repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                    playerManager.toggleRepeat()
                }
            }
            repeat == androidx.media3.common.Player.REPEAT_MODE_OFF -> {
                playerManager.toggleRepeat()
            }
            repeat == androidx.media3.common.Player.REPEAT_MODE_ALL -> {
                playerManager.toggleRepeat()
            }
            else -> {
                playerManager.toggleRepeat()
                playerManager.toggleShuffle()
            }
        }
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
            settingsManager.setLyricSourceMode(mode)
            lyricSourceMode = mode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
            appliedLyricSourceMode = lyricSourceMode
            currentSong.value?.let { reloadLyrics(it, force = true) }
        }
    }

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    fun refreshCurrentSongAfterExternalEdit(updatedFromLibrary: Song?) {
        val current = currentSong.value ?: return
        viewModelScope.launch {
            val updated = updatedFromLibrary
                ?.takeIf { it.id == current.id || it.path == current.path }
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
        val songLyrics = if (force) {
            repository.reloadLyrics(song, lyricSourceMode)
        } else {
            repository.getLyrics(song, lyricSourceMode)
        }
        loadedLyricSongKey = song.lyricIdentityKey()
        _lyrics.value = songLyrics
        _currentLyricIndex.value = -1
        if (lyriconBridge.isEnabled()) lyriconBridge.sendSong(song, songLyrics)
        superLyricBridge.sendSong(song)
        if (songLyrics.isEmpty()) {
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

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerEndRealtimeMs.value = null
            return
        }
        _sleepTimerEndRealtimeMs.value = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            _sleepTimerEndRealtimeMs.value = null
            playerManager.pause()
        }
    }

    fun setStopAfterCurrentEnabled(enabled: Boolean) {
        _stopAfterCurrentEnabled.value = enabled
        stopAfterCurrentSongId = if (enabled) currentSong.value?.id else null
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndRealtimeMs.value = null
    }

    private fun updateSleepTimer() {
        val targetId = stopAfterCurrentSongId ?: return
        val song = currentSong.value ?: return
        val total = duration.value
        val position = currentPosition.value
        if (song.id != targetId || (total > 0L && total - position <= 850L)) {
            stopAfterCurrentSongId = null
            _stopAfterCurrentEnabled.value = false
            playerManager.pause()
        }
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
            lyriconBridge.setDisplayTranslation(enabled)
        }
    }

    fun setTickerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerEnabled(enabled)
            tickerBridge.setHideNotification(settingsManager.tickerHideNotification.first())
            tickerBridge.setEnabled(enabled)
            lastTickerPayload = null
            if (enabled) resendTickerLyric()
        }
    }

    fun setTickerHideNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerHideNotification(enabled)
            tickerHideNotificationEnabled = enabled
            tickerBridge.setHideNotification(enabled)
            if (enabled && samsungFloatingLyricTranslationEnabled) {
                settingsManager.setSamsungFloatingLyricTranslation(false)
                samsungFloatingLyricTranslationEnabled = false
            }
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            val safeEnabled = enabled && !tickerHideNotificationEnabled
            settingsManager.setSamsungFloatingLyricTranslation(safeEnabled)
            samsungFloatingLyricTranslationEnabled = safeEnabled
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric()
        }
    }

    fun setDesktopLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDesktopLyricEnabled(enabled)
            desktopLyricBridge.setEnabled(enabled)
            if (enabled) resendDesktopLyric()
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
            if (superLyricBridge.isEnabled()) resendSuperLyric()
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
            lastBluetoothLyricPayload = null
            if (bluetoothLyricEnabled) resendBluetoothLyric()
        }
    }

    private fun sendTickerLyric(index: Int, lyrics: List<LyricLine>) {
        if (!tickerBridge.isEnabled() || !playerManager.isPlaying.value) return

        val payload = lyrics.lyricPayloadAt(index, samsungFloatingLyricTranslationEnabled) ?: return
        if (payload == lastTickerPayload) return

        lastTickerPayload = payload
        tickerBridge.sendLyric(payload.first, payload.second)
    }

    private fun LyricLine?.secondaryLyricText(includeTranslation: Boolean): String? {
        if (!includeTranslation) return null
        return this?.translation
            ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
            ?: this?.backgroundTranslation?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    }

    private fun List<LyricLine>.bluetoothPayloadAt(index: Int): Pair<String, String?>? {
        return lyricPayloadAt(index, bluetoothLyricTranslationEnabled)
    }

    private fun List<LyricLine>.lyricPayloadAt(
        index: Int,
        includeTranslation: Boolean
    ): Pair<String, String?>? {
        val line = getOrNull(index) ?: return null
        val text = line.text.cleanBluetoothLyricText() ?: return null
        val directTranslation = line.secondaryLyricText(includeTranslation)?.cleanBluetoothLyricText()

        if (!includeTranslation) return text to null

        if (directTranslation != null) {
            return orderBluetoothLyricPair(text, directTranslation, preferFirstAsPrimary = true)
        }

        return text to null
    }

    private fun orderBluetoothLyricPair(
        first: String,
        second: String,
        preferFirstAsPrimary: Boolean
    ): Pair<String, String> {
        val firstLooksTranslated = first.looksLikeChineseTranslationOf(second)
        val secondLooksTranslated = second.looksLikeChineseTranslationOf(first)
        return when {
            firstLooksTranslated && !secondLooksTranslated -> second to first
            secondLooksTranslated && !firstLooksTranslated -> first to second
            preferFirstAsPrimary -> first to second
            else -> second to first
        }
    }

    private fun String.cleanBluetoothLyricText(): String? =
        trim().takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

    private fun String.looksLikeChineseTranslationOf(other: String): Boolean =
        hasCjkOrHangul() && other.hasLatinLetter()

    private fun String.hasLatinLetter(): Boolean =
        any { it in 'A'..'Z' || it in 'a'..'z' }

    private fun String.hasCjkOrHangul(): Boolean =
        any { char ->
            Character.UnicodeBlock.of(char) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HANGUL_JAMO,
                Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
            )
        }

    private fun String.isMusicSymbolOnly(): Boolean {
        val content = trim()
        if (content.isBlank()) return true
        return content.all { char ->
            char.isWhitespace() ||
                char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
        }
    }

    override fun onCleared() {
        runBlocking {
            flushPlaybackStats()
        }
        super.onCleared()
        externalLyricResendJob?.cancel()
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        tickerBridge.clearLyric()
        lyricGetterBridge.clearLyric()
        superLyricBridge.destroy()
        lyriconBridge.destroy()
        playerManager.disconnect()
    }
}

private data class LazyOnlineQueue(
    val songs: List<Song>,
    var index: Int,
    val resolver: suspend (Song) -> Song
)

private fun Song.lyricIdentityKey(): String {
    return when {
        onlineSource.isNotBlank() || onlineId.isNotBlank() -> "online:$onlineSource:$onlineId:$path"
        path.isNotBlank() -> "path:$path"
        else -> "id:$id"
    }
}
