package com.ella.music.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ella.music.data.AppLogStore
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.DesktopLyricBridge
import com.ella.music.player.ExoPlayerManager
import com.ella.music.player.LyriconBridge
import com.ella.music.player.SuperLyricBridge
import com.ella.music.player.TickerBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val shuffleEnabled: StateFlow<Boolean> = playerManager.shuffleEnabled
    val repeatMode: StateFlow<Int> = playerManager.repeatMode
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val playbackPitch: StateFlow<Float> = playerManager.playbackPitch
    val playlist: StateFlow<List<Song>> = playerManager.playlistFlow

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
    private var superLyricTranslationEnabled = true
    private var lyricSourceMode = SettingsManager.LYRIC_SOURCE_AUTO
    private var lastBluetoothLyricPayload: Pair<String, String?>? = null
    private var sleepTimerJob: Job? = null
    private var externalLyricResendJob: Job? = null
    private var stopAfterCurrentSongId: Long? = null
    private var lazyOnlineQueue: LazyOnlineQueue? = null
    private var resolvingLazyQueue = false

    init {
        playerManager.connect()
        startPositionUpdates()
        observeCurrentSong()
        observePlayState()
        initLyricon()
        initTicker()
        initDesktopLyric()
        initSuperLyric()
        initLyricPageTranslation()
        initBluetoothLyric()
        initShuffleMode()
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
            samsungFloatingLyricTranslationEnabled = settingsManager.samsungFloatingLyricTranslation.first()
            tickerBridge.setEnabled(enabled)
            if (enabled) resendTickerLyric()
        }
        viewModelScope.launch {
            settingsManager.samsungFloatingLyricTranslation.collect { enabled ->
                samsungFloatingLyricTranslationEnabled = enabled
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
            settingsManager.superLyricTranslation.collect { enabled ->
                superLyricTranslationEnabled = enabled
                if (superLyricBridge.isEnabled()) resendSuperLyric()
            }
        }
    }

    private fun initBluetoothLyric() {
        viewModelScope.launch {
            settingsManager.bluetoothLyricEnabled.collect { enabled ->
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
            settingsManager.bluetoothLyricTranslation.collect { enabled ->
                bluetoothLyricTranslationEnabled = enabled
                lastBluetoothLyricPayload = null
                if (bluetoothLyricEnabled) resendBluetoothLyric()
            }
        }
    }

    private fun initShuffleMode() {
        viewModelScope.launch {
            settingsManager.shuffleMode.collect { mode ->
                playerManager.setShuffleMode(mode)
            }
        }
    }

    private fun initDecoderMode() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.decoderMode.collect { mode ->
                if (!initialized) {
                    initialized = true
                    return@collect
                }
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Decoder mode changed to $mode")
            }
        }
    }

    private fun initAudioFocusMode() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.audioFocusDisabled.collect { disabled ->
                if (!initialized) {
                    initialized = true
                    return@collect
                }
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Audio focus disabled changed to $disabled")
            }
        }
    }

    private fun initLyricSourceMode() {
        viewModelScope.launch {
            settingsManager.lyricSourceMode.collect { mode ->
                lyricSourceMode = mode
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
            playerManager.currentSong.collect { song ->
                if (song != null) {
                    lastTickerPayload = null
                    lastBluetoothLyricPayload = null
                    val songLyrics = repository.getLyrics(song, lyricSourceMode)
                    repository.getCoverArt(song)
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
                        desktopLyricBridge.clearLyric()
                        superLyricBridge.sendStop()
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
            if (playCountedSongId != song.id) {
                playbackStatsStore.recordPlay(song)
                playCountedSongId = song.id
            }
            if (lastStatsTickMs > 0L) {
                pendingListenMs += (now - lastStatsTickMs).coerceIn(0L, 1500L)
            }
            if (pendingListenMs >= 5000L) {
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
        if (song != null && pendingListenMs > 0L) {
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
    }

    private fun resendTickerLyric(force: Boolean = false) {
        if (!tickerBridge.isEnabled() || !isPlaying.value) return
        if (force) lastTickerPayload = null
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        sendTickerLyric(index, currentLyrics)
    }

    private fun resendDesktopLyric() {
        if (!desktopLyricBridge.isEnabled() || !isPlaying.value) return
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        desktopLyricBridge.sendLyric(
            line = currentLyrics.getOrNull(index),
            positionMs = currentPosition.value,
            showTranslation = _showLyricTranslation.value
        )
    }

    private fun updateDesktopLyricFrame() {
        if (!desktopLyricBridge.isEnabled() || !isPlaying.value) return
        val index = _currentLyricIndex.value
        val line = _lyrics.value.getOrNull(index) ?: return
        desktopLyricBridge.sendLyric(line, currentPosition.value, _showLyricTranslation.value)
    }

    private fun resendSuperLyric(force: Boolean = false) {
        if (!superLyricBridge.isEnabled() || !isPlaying.value) return
        val index = _currentLyricIndex.value
        val line = _lyrics.value.getOrNull(index) ?: return
        superLyricBridge.sendLyric(line, currentPosition.value, _showLyricTranslation.value && superLyricTranslationEnabled, force)
    }

    private fun scheduleExternalLyricResend() {
        externalLyricResendJob?.cancel()
        externalLyricResendJob = viewModelScope.launch {
            repeat(3) { attempt ->
                delay(350L + attempt * 550L)
                resendExternalLyrics(force = true)
                resendBluetoothLyric(force = true)
            }
        }
    }

    private fun clearExternalLyrics(clearLyricon: Boolean, clearSuperLyricSong: Boolean) {
        externalLyricResendJob?.cancel()
        lastTickerPayload = null
        lastBluetoothLyricPayload = null
        tickerBridge.clearLyric()
        desktopLyricBridge.clearLyric()
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
            settingsManager.lyricPageTranslation.collect { enabled ->
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

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun skipToNext() {
        if (!playLazyOnlineOffset(1)) playerManager.skipToNext()
    }

    fun skipToPrevious() {
        if (!playLazyOnlineOffset(-1)) playerManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
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
    fun addToPlaylist(song: Song) {
        lazyOnlineQueue = null
        playerManager.addToPlaylist(song)
    }
    fun addToPlaylist(songs: List<Song>) {
        lazyOnlineQueue = null
        playerManager.addToPlaylist(songs)
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

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song,1200)

    fun getAudioInfo(song: Song) = repository.getAudioInfo(song)

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
            currentSong.value?.let { reloadLyrics(it, force = true) }
        }
    }

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    private suspend fun reloadLyrics(song: Song, force: Boolean = false) {
        lastTickerPayload = null
        lastBluetoothLyricPayload = null
        val songLyrics = if (force) {
            repository.reloadLyrics(song, lyricSourceMode)
        } else {
            repository.getLyrics(song, lyricSourceMode)
        }
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
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            scheduleExternalLyricResend()
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            playerManager.pause()
        }
    }

    fun stopAfterCurrentSong() {
        stopAfterCurrentSongId = currentSong.value?.id
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        stopAfterCurrentSongId = null
    }

    private fun updateSleepTimer() {
        val targetId = stopAfterCurrentSongId ?: return
        val song = currentSong.value ?: return
        val total = duration.value
        val position = currentPosition.value
        if (song.id != targetId || (total > 0L && total - position <= 850L)) {
            stopAfterCurrentSongId = null
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
            tickerBridge.setEnabled(enabled)
            lastTickerPayload = null
            if (enabled) resendTickerLyric()
        }
    }

    fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSamsungFloatingLyricTranslation(enabled)
            samsungFloatingLyricTranslationEnabled = enabled
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
