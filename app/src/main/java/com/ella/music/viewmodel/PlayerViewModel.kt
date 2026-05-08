package com.ella.music.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.ExoPlayerManager
import com.ella.music.player.LyriconBridge
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
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val shuffleEnabled: StateFlow<Boolean> = playerManager.shuffleEnabled
    val repeatMode: StateFlow<Int> = playerManager.repeatMode
    val playlist: StateFlow<List<Song>> = playerManager.playlistFlow

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _showLyricTranslation = MutableStateFlow(true)
    val showLyricTranslation: StateFlow<Boolean> = _showLyricTranslation.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var lastSentPlayingState: Boolean? = null
    private var lastTickerLine: String? = null
    private var statsSongId: Long? = null
    private var statsSong: Song? = null
    private var playCountedSongId: Long? = null
    private var pendingListenMs = 0L
    private var lastStatsTickMs = 0L

    init {
        playerManager.connect()
        startPositionUpdates()
        observeCurrentSong()
        observePlayState()
        initLyricon()
        initTicker()
        initLyricPageTranslation()
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
            tickerBridge.setEnabled(enabled)
            if (enabled) resendTickerLyric()
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                playerManager.updatePosition()
                updateCurrentLyricIndex()
                updatePlaybackStats()

                if (lyriconBridge.isEnabled()) {
                    lyriconBridge.sendPosition(playerManager.currentPosition.value)
                }

                delay(50)
            }
        }
    }

    private fun observeCurrentSong() {
        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                if (song != null) {
                    lastTickerLine = null
                    val songLyrics = repository.getLyrics(song)
                    _lyrics.value = songLyrics
                    _currentLyricIndex.value = -1

                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendSong(song, songLyrics)
                    }
                } else {
                    _lyrics.value = emptyList()
                    _currentLyricIndex.value = -1
                    lyriconBridge.clearSong()
                    tickerBridge.clearLyric()
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
                    if (!playing) tickerBridge.clearLyric()
                }
            }
        }
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
                val line = currentLyrics[index].text.takeUnless { it.isMusicSymbolOnly() }
                if (line != null && line != lastTickerLine) {
                    lastTickerLine = line
                    tickerBridge.sendLyric(line)
                } else if (line != null && lastTickerLine == null && playerManager.isPlaying.value) {
                    lastTickerLine = line
                    tickerBridge.sendLyric(line)
                }
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

    private suspend fun resendExternalLyrics() {
        val song = currentSong.value ?: return
        val songLyrics = _lyrics.value.ifEmpty { repository.getLyrics(song) }
        if (_lyrics.value.isEmpty()) _lyrics.value = songLyrics
        lyriconBridge.sendSong(song, songLyrics)
        lyriconBridge.sendPlaybackState(isPlaying.value)
        lyriconBridge.sendPosition(currentPosition.value)
        resendTickerLyric()
    }

    private fun resendTickerLyric() {
        if (!tickerBridge.isEnabled() || !isPlaying.value) return
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        val line = currentLyrics.getOrNull(index)?.text?.takeUnless { it.isMusicSymbolOnly() }
        if (line != null) {
            lastTickerLine = line
            tickerBridge.sendLyric(line)
        } else {
            lastTickerLine = null
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
        playerManager.setPlaylist(songs, startIndex)
    }

    fun playSong(song: Song) {
        playerManager.playSong(song)
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun skipToNext() = playerManager.skipToNext()
    fun skipToPrevious() = playerManager.skipToPrevious()

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        lyriconBridge.seekTo(positionMs)
    }

    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeat() = playerManager.toggleRepeat()
    fun addToPlaylist(song: Song) = playerManager.addToPlaylist(song)
    fun playQueueIndex(index: Int) = playerManager.playQueueIndex(index)

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

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song)

    fun getAudioInfo(song: Song) = repository.getAudioInfo(song)

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    fun setShowLyrics(show: Boolean) {
        _showLyrics.value = show
    }

    fun setLyricPageTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyricPageTranslation(enabled)
            _showLyricTranslation.value = enabled
        }
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
            lastTickerLine = null
            if (enabled) {
                val index = _currentLyricIndex.value
                val currentLyrics = _lyrics.value
                if (index in currentLyrics.indices) {
                    val line = currentLyrics[index].text.takeUnless { it.isMusicSymbolOnly() }
                    if (line != null) {
                        lastTickerLine = line
                        tickerBridge.sendLyric(line)
                    }
                }
            }
        }
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
        positionUpdateJob?.cancel()
        tickerBridge.clearLyric()
        lyriconBridge.destroy()
        playerManager.disconnect()
    }
}
