package com.ella.music.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.isUriAudioSource
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

class ExoPlayerManager(private val context: Context) {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private var playlist = mutableListOf<Song>()

    private data class ExternalSnapshotGuard(
        val mediaId: String?,
        val song: Song
    )

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlistFlow: StateFlow<List<Song>> = _playlist.asStateFlow()
    private var playerListener: Player.Listener? = null
    private var lastQueueSaveMs = 0L
    private var lastStateSaveMs = 0L
    private var shuffleMode = SettingsManager.SHUFFLE_MODE_PSEUDO
    private var playNextMode = SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK
    private var virtualPlaylistCurrentIndex: Int? = null
    private var playWhenConnected = false
    private var pendingPlaylist: PendingPlaylist? = null
    private var reorderingPlaylistForShuffle = false
    private var playlistBeforeShuffle: List<Song>? = null
    private var playNextAnchorKey: String? = null
    private var playNextForwardCount = 0
    private var replayGainVolume = 1f
    private var externalSnapshotGuard: ExternalSnapshotGuard? = null

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkRepository = MusicRepository.getInstance(context)
    private val notificationArtworkCache = object : LruCache<String, ByteArray>(4 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val missingNotificationArtworkKeys = mutableSetOf<String>()
    private var notificationArtworkJob: Job? = null
    private var currentSongRefreshJob: Job? = null
    private var artworkAppliedSongKey: String? = null
    private var sessionMetadataSongKey: String? = null
    private var bluetoothMetadataSongKey: String? = null
    private var bluetoothMetadataPayload: Pair<String?, String?>? = null
    private var suppressExternalSnapshotsUntilMs = 0L

    init {
        _shuffleEnabled.value = loadAppShuffleEnabled()
    }

    fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        Futures.addCallback(
            future,
            object : FutureCallback<MediaController> {
                override fun onSuccess(result: MediaController?) {
                    if (controllerFuture !== future || result == null) return
                    mediaController = result
                    setupListener()
                }

                override fun onFailure(t: Throwable) {
                    if (controllerFuture !== future) return
                    AppLogStore.error(context, "PlayerController", "Failed to connect media controller", t)
                }
            },
            context.mainExecutor
        )
    }

    fun disconnect() {
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        playerListener = null
        mediaController = null
    }

    /**
     * Ensures the media controller is connected to the (possibly killed/recreated) playback
     * service. When the app is backgrounded for a while — especially over Bluetooth — the
     * system can tear down the session, leaving a stale, disconnected controller whose
     * commands are silently dropped. Call this on app foreground and before issuing commands.
     */
    fun ensureConnected(refreshStateIfConnected: Boolean = true) {
        val controller = mediaController
        if (controller != null && controller.isConnected) {
            if (refreshStateIfConnected) refreshStateFromController()
            return
        }
        if (controller != null) disconnect()
        if (controllerFuture == null) connect()
    }

    private fun activeController(): MediaController? = mediaController?.takeIf { it.isConnected }

    suspend fun recreatePlaybackService() {
        val resumePlayback = _isPlaying.value
        savePlaybackQueue(force = true)
        savePlaybackState(force = true)
        playWhenConnected = resumePlayback
        AppLogStore.info(context, "PlayerDecoder", "Recreate playback service for decoder change")

        disconnect()
        context.stopService(Intent(context, PlaybackService::class.java))
        playlist.clear()
        _playlist.value = emptyList()
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        bluetoothMetadataSongKey = null
        bluetoothMetadataPayload = null
        delay(650)
        connect()
    }

    private fun setupListener() {
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                savePlaybackState(force = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = playbackState
                _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.d(TIMING_TAG, "controller state BUFFERING mediaId=${mediaController?.currentMediaItem?.mediaId}")
                    Player.STATE_READY -> Log.d(TIMING_TAG, "controller state READY mediaId=${mediaController?.currentMediaItem?.mediaId}")
                    Player.STATE_ENDED -> Log.d(TIMING_TAG, "controller state ENDED mediaId=${mediaController?.currentMediaItem?.mediaId}")
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TIMING_TAG, "controller media transition reason=$reason mediaId=${mediaItem?.mediaId}")
                externalSnapshotGuard = null
                clearBluetoothMetadataPatchState()
                updateCurrentSong()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _currentPosition.value = newPosition.positionMs.coerceAtLeast(0L)
                _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    updateCurrentSong()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reorderingPlaylistForShuffle) return
                refreshStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) {
                    _shuffleEnabled.value = true
                    persistAppShuffleEnabled(true)
                    shufflePlaylistKeepingCurrent()
                    mediaController?.shuffleModeEnabled = false
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
                // The combined playback-mode button in the media notification changes the app-level
                // shuffle flag (persisted, not part of Media3 state) together with the repeat mode.
                // Re-read it here so the player page stays in sync with notification-driven changes.
                val persistedShuffle = loadAppShuffleEnabled()
                if (_shuffleEnabled.value != persistedShuffle) {
                    _shuffleEnabled.value = persistedShuffle
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                _playbackPitch.value = playbackParameters.pitch
            }

            override fun onPlayerError(error: PlaybackException) {
                val song = _currentSong.value
                AppLogStore.error(
                    context,
                    "PlayerError",
                    "Playback failed code=${error.errorCodeName} song=${song?.title.orEmpty()} uri=${mediaController?.currentMediaItem?.localConfiguration?.uri}",
                    error
                )
                skipToNext()
            }
        }
        mediaController?.addListener(playerListener!!)

        val pending = pendingPlaylist
        if (pending != null) {
            pendingPlaylist = null
            setPlaylist(pending.songs, pending.startIndex, honorShuffle = pending.honorShuffle)
        } else {
            restoreSavedQueueIfNeeded()
        }
        refreshStateFromController()
        if (playWhenConnected) {
            playWhenConnected = false
            play()
        }
    }

    /**
     * Cap the queue handed to the media session so it never overflows the Binder transaction limit
     * on pathologically large libraries; play a window centered on the chosen song instead.
     */
    private fun List<Song>.windowedForController(index: Int): Pair<List<Song>, Int> {
        if (size <= MAX_CONTROLLER_QUEUE) return this to index
        val from = (index - MAX_CONTROLLER_QUEUE / 2).coerceIn(0, size - MAX_CONTROLLER_QUEUE)
        return subList(from, from + MAX_CONTROLLER_QUEUE).toList() to (index - from)
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        setPlaylist(songs, startIndex, honorShuffle = true)
    }

    private fun setPlaylist(songs: List<Song>, startIndex: Int, honorShuffle: Boolean) {
        if (songs.isEmpty()) return
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        AppLogStore.debug(context, "PlayerQueue", "setPlaylist size=${songs.size} start=$startIndex")
        virtualPlaylistCurrentIndex = null
        resetPlayNextForwardStack()
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        bluetoothMetadataSongKey = null
        bluetoothMetadataPayload = null
        // The whole queue is shipped to the playback service over Binder (~1MB transaction limit);
        // a 60k-song library overflows it and crashes with TransactionTooLargeException. For
        // pathologically large queues, play a window around the chosen song instead of everything.
        val requestedIndex = startIndex.coerceIn(songs.indices)
        val prepared = preparePlaybackQueue(songs, requestedIndex, honorShuffle)
        val queueSongs = prepared.songs
        val safeIndex = prepared.startIndex
        playlistBeforeShuffle = prepared.sourceOrderBeforeShuffle
        playlist.clear()
        playlist.addAll(queueSongs)
        _playlist.value = playlist.toList()

        val mediaItems = queueSongs.map(::songToMediaItem)
        val controller = activeController()
        if (controller == null) {
            // No live controller (first launch, or the session was torn down while backgrounded).
            // Reconnect and queue the request so it is applied once the controller is back, and
            // optimistically reflect the requested song in the UI right away.
            ensureConnected()
            pendingPlaylist = PendingPlaylist(queueSongs, safeIndex, honorShuffle = false)
            _currentSong.value = queueSongs.getOrNull(safeIndex)
            _duration.value = queueSongs.getOrNull(safeIndex)?.duration ?: 0L
            _repeatMode.value = Player.REPEAT_MODE_ALL
            savePlaybackQueue(force = true)
            return
        }

        controller.apply {
            if (repeatMode == Player.REPEAT_MODE_OFF) {
                repeatMode = Player.REPEAT_MODE_ALL
            }
            setMediaItems(mediaItems, safeIndex, 0L)
            prepare()
            play()
        }
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    private data class PreparedPlaybackQueue(
        val songs: List<Song>,
        val startIndex: Int,
        val sourceOrderBeforeShuffle: List<Song>?
    )

    private fun preparePlaybackQueue(
        songs: List<Song>,
        requestedIndex: Int,
        honorShuffle: Boolean
    ): PreparedPlaybackQueue {
        if (!honorShuffle || !_shuffleEnabled.value || songs.size <= 1) {
            val (queueSongs, safeIndex) = songs.windowedForController(requestedIndex)
            return PreparedPlaybackQueue(queueSongs, safeIndex, sourceOrderBeforeShuffle = null)
        }

        val currentSong = songs[requestedIndex]
        val shuffleSeed = if (shuffleMode == SettingsManager.SHUFFLE_MODE_TRUE_RANDOM) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            buildPseudoShuffleSeed(songs, currentSong)
        }
        val shuffledSongs = songs
            .filterIndexed { index, _ -> index != requestedIndex }
            .shuffled(Random(shuffleSeed))
        val shuffledQueue = listOf(currentSong) + shuffledSongs

        // Keep the original-order controller window so turning shuffle off never tries to send a
        // huge source library through the media-session Binder transaction.
        val (sourceWindow, _) = songs.windowedForController(requestedIndex)
        val (queueSongs, safeIndex) = shuffledQueue.windowedForController(0)
        return PreparedPlaybackQueue(queueSongs, safeIndex, sourceWindow)
    }

    fun playResolvedFromVirtualQueue(songs: List<Song>, currentIndex: Int, resolvedSong: Song) {
        if (songs.isEmpty()) return
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        AppLogStore.debug(context, "PlayerQueue", "playResolvedVirtual size=${songs.size} index=$currentIndex title=${resolvedSong.title}")
        val safeIndex = currentIndex.coerceIn(songs.indices)
        virtualPlaylistCurrentIndex = safeIndex
        resetPlayNextForwardStack()
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        bluetoothMetadataSongKey = null
        bluetoothMetadataPayload = null
        playlist.clear()
        playlist.addAll(songs.mapIndexed { index, song -> if (index == safeIndex) resolvedSong else song })
        _playlist.value = playlist.toList()

        mediaController?.apply {
            setMediaItems(listOf(songToMediaItem(resolvedSong)), 0, 0L)
            prepare()
            play()
        }
        _currentSong.value = resolvedSong
        _duration.value = resolvedSong.duration
        savePlaybackQueue(force = true)
    }

    fun addToPlaylist(song: Song) {
        virtualPlaylistCurrentIndex = null
        playlistBeforeShuffle = null
        resetPlayNextForwardStack()
        AppLogStore.debug(context, "PlayerQueue", "add title=${song.title}")
        val item = songToMediaItem(song)
        playlist.add(song)
        _playlist.value = playlist.toList()
        mediaController?.addMediaItem(item)
        if ((mediaController?.mediaItemCount ?: 0) == 1) {
            mediaController?.prepare()
        }
        savePlaybackQueue(force = true)
    }

    fun addToPlaylist(songs: List<Song>) {
        if (songs.isEmpty()) return
        virtualPlaylistCurrentIndex = null
        playlistBeforeShuffle = null
        resetPlayNextForwardStack()
        AppLogStore.debug(context, "PlayerQueue", "addMany size=${songs.size}")
        playlist.addAll(songs)
        _playlist.value = playlist.toList()
        mediaController?.addMediaItems(songs.map(::songToMediaItem))
        if ((mediaController?.mediaItemCount ?: 0) == songs.size) {
            mediaController?.prepare()
        }
        savePlaybackQueue(force = true)
    }

    fun playNext(song: Song) {
        playNext(listOf(song))
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        virtualPlaylistCurrentIndex = null
        playlistBeforeShuffle = null
        val controller = mediaController
        val insertIndex = playNextInsertIndex(controller, songs.size)
        AppLogStore.debug(context, "PlayerQueue", "playNextMany size=${songs.size} index=$insertIndex mode=$playNextMode")
        playlist.addAll(insertIndex, songs)
        _playlist.value = playlist.toList()
        controller?.addMediaItems(insertIndex, songs.map(::songToMediaItem))
        if ((controller?.mediaItemCount ?: 0) == songs.size) {
            controller?.prepare()
        }
        savePlaybackQueue(force = true)
    }

    private fun playNextInsertIndex(controller: MediaController?, insertCount: Int): Int {
        val currentIndex = currentQueueIndex(controller)
        val anchorKey = currentSongQueueKey(controller, currentIndex)
        val baseIndex = (currentIndex + 1).coerceIn(0, playlist.size)
        if (playNextMode == SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK && anchorKey != null) {
            if (playNextAnchorKey != anchorKey) {
                playNextAnchorKey = anchorKey
                playNextForwardCount = 0
            }
            val insertIndex = (baseIndex + playNextForwardCount).coerceIn(0, playlist.size)
            playNextForwardCount += insertCount
            return insertIndex
        }
        if (playNextMode != SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK) {
            resetPlayNextForwardStack()
        }
        return baseIndex
    }

    private fun currentQueueIndex(controller: MediaController?): Int {
        val controllerIndex = controller?.currentMediaItemIndex ?: C.INDEX_UNSET
        if (controllerIndex in playlist.indices) return controllerIndex
        val currentSong = _currentSong.value
        val currentSongIndex = playlist.indexOfFirst { it.isSamePlaybackIdentity(currentSong) }
        return if (currentSongIndex >= 0) currentSongIndex else -1
    }

    private fun currentSongQueueKey(controller: MediaController?, currentIndex: Int): String? {
        val song = when {
            currentIndex in playlist.indices -> playlist[currentIndex]
            else -> controller?.currentMediaItem?.toSongFromMediaItemExtras()
                ?: controller?.currentMediaItem?.toSong()
                ?: _currentSong.value
        }
        return song?.playbackStackKey()
    }

    private fun resetPlayNextForwardStack() {
        playNextAnchorKey = null
        playNextForwardCount = 0
    }

    fun playQueueIndex(index: Int) {
        if (index !in playlist.indices) return
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        resetPlayNextForwardStack()
        mediaController?.seekToDefaultPosition(index)
        mediaController?.play()
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    fun removeFromPlaylist(index: Int) {
        if (index !in playlist.indices) return
        virtualPlaylistCurrentIndex = null
        playlistBeforeShuffle = null
        resetPlayNextForwardStack()
        AppLogStore.debug(context, "PlayerQueue", "remove index=$index title=${playlist[index].title}")
        if (playlist.size == 1) {
            clearPlaylist()
            return
        }

        playlist.removeAt(index)
        _playlist.value = playlist.toList()
        mediaController?.let { controller ->
            if (index < controller.mediaItemCount) {
                controller.removeMediaItem(index)
            }
            if (controller.mediaItemCount > 0 && controller.currentMediaItemIndex == C.INDEX_UNSET) {
                controller.seekToDefaultPosition(index.coerceAtMost(controller.mediaItemCount - 1))
            }
            updateCurrentSong()
        } ?: run {
            _currentSong.value = playlist.firstOrNull()
            _duration.value = _currentSong.value?.duration ?: 0L
        }
        savePlaybackQueue(force = true)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices || fromIndex == toIndex) return
        virtualPlaylistCurrentIndex = null
        playlistBeforeShuffle = null
        resetPlayNextForwardStack()
        val movedSong = playlist.removeAt(fromIndex)
        playlist.add(toIndex, movedSong)
        _playlist.value = playlist.toList()
        mediaController?.let { controller ->
            if (fromIndex < controller.mediaItemCount && toIndex < controller.mediaItemCount) {
                controller.moveMediaItem(fromIndex, toIndex)
            }
            updateCurrentSong()
        } ?: run {
            _currentSong.value = playlist.firstOrNull()
            _duration.value = _currentSong.value?.duration ?: 0L
        }
        savePlaybackQueue(force = true)
    }

    fun clearPlaylist() {
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = SystemClock.elapsedRealtime() + CLEAR_EXTERNAL_SNAPSHOT_SUPPRESSION_MS
        currentSongRefreshJob?.cancel()
        currentSongRefreshJob = null
        virtualPlaylistCurrentIndex = null
        playlistBeforeShuffle = null
        resetPlayNextForwardStack()
        playlist.clear()
        _playlist.value = emptyList()
        _currentSong.value = null
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        bluetoothMetadataSongKey = null
        bluetoothMetadataPayload = null
        _currentPosition.value = 0L
        _duration.value = 0L
        _isPlaying.value = false
        _playbackState.value = Player.STATE_IDLE
        mediaController?.run {
            stop()
            clearMediaItems()
        }
        clearSavedQueue()
    }

    fun playSong(song: Song) {
        val index = playlist.indexOfFirst { it.isSamePlaybackIdentity(song) }
        if (index >= 0) {
            resetPlayNextForwardStack()
            mediaController?.seekToDefaultPosition(index)
            mediaController?.play()
            updateCurrentSong()
            savePlaybackQueue(force = true)
        } else {
            setPlaylist(listOf(song), 0)
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    /**
     * Live playback position straight from the controller. Must be called on the main
     * thread (the controller's looper). The [currentPosition] StateFlow is only sampled
     * periodically, so remote readers (MCP) get a stale value mid-track without this.
     */
    fun livePositionMs(): Long =
        mediaController?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value

    fun play() {
        val controller = mediaController
        if (controller == null) {
            playWhenConnected = true
            return
        }
        if (controller.mediaItemCount > 0) {
            controller.play()
            refreshStateFromController()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
        scheduleCurrentSongRefresh()
        savePlaybackQueue(force = true)
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
        scheduleCurrentSongRefresh()
        savePlaybackQueue(force = true)
    }

    fun restartCurrent() {
        mediaController?.run {
            seekToDefaultPosition(currentMediaItemIndex.coerceAtLeast(0))
            play()
        }
        _currentPosition.value = 0L
        updateCurrentSong()
        savePlaybackState(force = true)
    }

    fun restartSong(song: Song?) {
        val controller = mediaController ?: return
        val target = song ?: _currentSong.value
        val targetIndex = target?.let { current ->
            playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
        } ?: -1
        val safeIndex = targetIndex.takeIf { it >= 0 } ?: controller.currentMediaItemIndex
        if (safeIndex < 0) return
        controller.seekToDefaultPosition(safeIndex)
        controller.play()
        _currentPosition.value = 0L
        updateCurrentSong()
        savePlaybackQueue(force = true)
        savePlaybackState(force = true)
    }

    private fun scheduleCurrentSongRefresh() {
        currentSongRefreshJob?.cancel()
        currentSongRefreshJob = persistenceScope.launch {
            delay(150L)
            withContext(Dispatchers.Main.immediate) {
                refreshStateFromController()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val duration = controller.duration
        // Never let a seek land on/after the final frame: some formats (notably E-AC-3 / AC-3)
        // treat that as end-of-stream and Media3 auto-advances to the next track, so dragging the
        // progress bar to the very end "skips a song". Keep a small guard before the reported end.
        val target = if (duration > 0L) {
            positionMs.coerceIn(0L, (duration - SEEK_END_GUARD_MS).coerceAtLeast(0L))
        } else {
            positionMs.coerceAtLeast(0L)
        }
        controller.seekTo(target)
        savePlaybackState(force = true)
    }

    fun toggleShuffle() {
        val nextShuffle = !_shuffleEnabled.value
        applyPlaybackMode(
            shuffle = nextShuffle,
            repeatMode = if (nextShuffle) Player.REPEAT_MODE_ALL else mediaController?.repeatMode ?: _repeatMode.value,
            reorderForShuffleChange = true
        )
    }

    fun setShuffleMode(mode: Int) {
        shuffleMode = mode.coerceIn(
            SettingsManager.SHUFFLE_MODE_PSEUDO,
            SettingsManager.SHUFFLE_MODE_TRUE_RANDOM
        )
    }

    fun setPlayNextMode(mode: Int) {
        playNextMode = mode.coerceIn(
            SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK,
            SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK
        )
        resetPlayNextForwardStack()
    }

    fun toggleRepeat() {
        val current = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        applyPlaybackMode(
            shuffle = _shuffleEnabled.value,
            repeatMode = next,
            reorderForShuffleChange = false
        )
    }

    fun cyclePlaybackMode() {
        val controller = mediaController ?: return
        val currentShuffle = _shuffleEnabled.value
        val currentRepeat = controller.repeatMode
        val (nextShuffle, nextRepeat) = when {
            currentShuffle -> false to Player.REPEAT_MODE_OFF
            currentRepeat == Player.REPEAT_MODE_OFF -> false to Player.REPEAT_MODE_ALL
            currentRepeat == Player.REPEAT_MODE_ALL -> false to Player.REPEAT_MODE_ONE
            else -> true to Player.REPEAT_MODE_ALL
        }
        applyPlaybackMode(
            shuffle = nextShuffle,
            repeatMode = nextRepeat,
            reorderForShuffleChange = nextShuffle != currentShuffle
        )
    }

    fun applyExternalPlaybackMode(shuffle: Boolean, repeatMode: Int) {
        val needsQueueReorder = shuffle != _shuffleEnabled.value ||
            (shuffle && playlistBeforeShuffle == null) ||
            (!shuffle && playlistBeforeShuffle != null)
        applyPlaybackMode(
            shuffle = shuffle,
            repeatMode = repeatMode,
            reorderForShuffleChange = needsQueueReorder
        )
    }

    private fun applyPlaybackMode(
        shuffle: Boolean,
        repeatMode: Int,
        reorderForShuffleChange: Boolean
    ) {
        val controller = mediaController ?: return
        val previousShuffle = _shuffleEnabled.value
        controller.shuffleModeEnabled = false
        if (reorderForShuffleChange && shuffle != previousShuffle) {
            if (shuffle) {
                shufflePlaylistKeepingCurrent()
            } else {
                restorePlaylistOrderAfterShuffle()
            }
        }
        _shuffleEnabled.value = shuffle
        persistAppShuffleEnabled(shuffle)
        if (controller.repeatMode != repeatMode) {
            controller.repeatMode = repeatMode
        } else {
            _repeatMode.value = repeatMode
        }
        savePlaybackQueue(force = true)
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        mediaController?.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
        _playbackSpeed.value = safeSpeed
        _playbackPitch.value = safePitch
        savePlaybackState()
    }

    fun setReplayGainVolume(volume: Float) {
        replayGainVolume = volume.coerceIn(0f, 1f)
        mediaController?.volume = replayGainVolume
    }

    fun updatePosition() {
        if (_currentSong.value == null && (mediaController?.mediaItemCount ?: 0) > 0) {
            refreshStateFromController()
        }
        _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0) ?: 0L
        _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
        if (_currentSong.value != null) savePlaybackState()
    }

    fun updateBluetoothLyric(text: String?, secondaryText: String? = null) {
        val controller = mediaController ?: return
        val song = _currentSong.value ?: return
        val index = controller.currentMediaItemIndex

        if (index < 0 || index >= controller.mediaItemCount) return

        val currentItem = controller.currentMediaItem ?: return
        if (!currentItem.matchesSong(song)) {
            clearBluetoothMetadataPatchState()
            return
        }
        val lyricText = text?.takeIf { it.isNotBlank() }
        val lyricSecondaryText = secondaryText?.takeIf { it.isNotBlank() }
        val payload = lyricText to lyricSecondaryText
        val songKey = song.playbackStackKey()

        if (lyricText == null && bluetoothMetadataSongKey != songKey) return
        if (bluetoothMetadataSongKey == songKey && bluetoothMetadataPayload == payload) return

        val displayTitle = lyricText ?: song.title
        val displayArtist = if (lyricText != null) {
            lyricSecondaryText ?: "${song.title} · ${song.artist}"
        } else {
            song.artist
        }

        if (currentItem.mediaMetadata.title == displayTitle &&
            currentItem.mediaMetadata.artist == displayArtist
        ) {
            bluetoothMetadataSongKey = if (lyricText == null) null else songKey
            bluetoothMetadataPayload = if (lyricText == null) null else payload
            return
        }

        val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
        val metadata = song.mediaMetadata(
            titleOverride = displayTitle,
            artistOverride = displayArtist,
            artworkData = cachedArtwork,
            includeArtworkUri = cachedArtwork != null
        ).withPatchedExtrasFrom(currentItem, PATCH_REASON_BLUETOOTH_LYRIC)

        val newItem = currentItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()

        runCatching {
            controller.replaceMediaItem(index, newItem)
            bluetoothMetadataSongKey = if (lyricText == null) null else songKey
            bluetoothMetadataPayload = if (lyricText == null) null else payload
        }
    }

    fun clearBluetoothLyric() {
        updateBluetoothLyric(null)
    }
    fun refreshStateFromController() {
        val controller = mediaController ?: return
        if (shouldIgnoreStaleControllerSong(controller)) return

        _isPlaying.value = controller.isPlaying
        _playbackState.value = controller.playbackState
        _repeatMode.value = controller.repeatMode
        _playbackSpeed.value = controller.playbackParameters.speed
        _playbackPitch.value = controller.playbackParameters.pitch
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0)
        _duration.value = controller.duration.coerceAtLeast(0)

        val mediaItemCount = controller.mediaItemCount
        if (mediaItemCount > 0 && playlist.isEmpty()) {
            val saved = loadSavedQueue()
            val currentItemSong = controller.currentMediaItem?.toSongFromMediaItemExtras()
                ?: controller.currentMediaItem?.toSong()
            val savedCurrentIndex = currentItemSong?.let { song ->
                saved?.songs?.indexOfFirst { it.isSamePlaybackIdentity(song) }
            } ?: -1
            if (saved != null && saved.songs.isNotEmpty() && (saved.songs.size == mediaItemCount || savedCurrentIndex >= 0)) {
                playlist.addAll(saved.songs)
                virtualPlaylistCurrentIndex = savedCurrentIndex.takeIf { mediaItemCount == 1 && it >= 0 }
            } else {
                for (index in 0 until mediaItemCount) {
                    playlist += controller.getMediaItemAt(index).toSong()
                }
            }
        }
        _playlist.value = playlist.toList()
        updateCurrentSong()
    }

    fun applyExternalPlaybackSnapshot(snapshot: PlaybackExternalSnapshot) {
        val snapshotSong = snapshot.mediaItem?.toSongFromMediaItemExtras()
            ?: snapshot.mediaItem?.toSong()
        if (snapshotSong != null && SystemClock.elapsedRealtime() < suppressExternalSnapshotsUntilMs) {
            return
        }

        _isPlaying.value = snapshot.isPlaying
        _playbackState.value = snapshot.playbackState
        _repeatMode.value = snapshot.repeatMode
        _currentPosition.value = snapshot.positionMs.coerceAtLeast(0L)
        _duration.value = snapshot.durationMs.coerceAtLeast(0L)

        if (snapshotSong == null) {
            if (snapshot.mediaItemCount <= 0) {
                externalSnapshotGuard = null
                playlist.clear()
                _playlist.value = emptyList()
                _currentSong.value = null
                _duration.value = 0L
                return
            }
            refreshStateFromController()
            return
        }

        if (isDisplayOnlyMetadataPatchSnapshot(
                isMetadataOnlyPatch = snapshot.mediaItem?.isMetadataOnlyPatch() == true,
                snapshotSong = snapshotSong,
                currentSong = _currentSong.value
            )
        ) {
            externalSnapshotGuard = null
            _duration.value = snapshot.durationMs.takeIf { it > 0L }
                ?: snapshotSong.duration.coerceAtLeast(0L)
            return
        }

        externalSnapshotGuard = ExternalSnapshotGuard(
            mediaId = snapshot.mediaItem?.mediaId,
            song = snapshotSong
        )

        val index = snapshot.mediaItemIndex
        if (index in playlist.indices && !playlist[index].isSamePlaybackIdentity(snapshotSong)) {
            playlist[index] = snapshotSong
            _playlist.value = playlist.toList()
        } else if (playlist.isEmpty() && snapshot.mediaItemCount == 1) {
            playlist.add(snapshotSong)
            _playlist.value = playlist.toList()
        }

        val previousSong = _currentSong.value
        _currentSong.value = snapshotSong
        _duration.value = snapshot.durationMs.takeIf { it > 0L }
            ?: snapshotSong.duration.coerceAtLeast(0L)

        if (!previousSong.isSamePlaybackIdentity(snapshotSong)) {
            resetPlayNextForwardStack()
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            artworkAppliedSongKey = null
            sessionMetadataSongKey = null
            clearBluetoothMetadataPatchState()
        }

        refreshCurrentNotificationArtwork(snapshotSong)
    }

    fun updateCurrentSongMetadata(updatedSong: Song) {
        val controller = mediaController
        val current = _currentSong.value ?: return
        if (!current.isSamePlaybackIdentity(updatedSong)) return

        val playlistIndex = playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
        if (playlistIndex >= 0) {
            playlist[playlistIndex] = updatedSong
            _playlist.value = playlist.toList()
        }

        _currentSong.value = updatedSong
        notificationArtworkCache.remove(current.notificationArtworkKey())
        notificationArtworkCache.remove(updatedSong.notificationArtworkKey())
        missingNotificationArtworkKeys.remove(current.notificationArtworkKey())
        missingNotificationArtworkKeys.remove(updatedSong.notificationArtworkKey())
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        artworkAppliedSongKey = null
        sessionMetadataSongKey = null

        if (controller != null && controller.currentMediaItemIndex >= 0) {
            refreshCurrentSessionMetadata(controller, updatedSong)
            refreshCurrentNotificationArtwork(updatedSong)
        }
        savePlaybackQueue(force = true)
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
        val builder = MediaItem.Builder()
            .setUri(song.playbackUri())
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                song.mediaMetadata(
                    artworkData = cachedArtwork,
                    includeArtworkUri = cachedArtwork != null
                )
            )

        if (song.mimeType.isNotBlank()) {
            builder.setMimeType(song.mimeType)
        }

        return builder.build()
    }

    private fun Song.playbackUri(): Uri {
        if (path.isUriAudioSource()) {
            return path.toUri()
        }
        if (onlineSource.isBlank() && path.startsWith("/") && id > 0L) {
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        }
        return if (path.startsWith("/")) Uri.fromFile(File(path)) else path.toUri()
    }

    private fun Song.mediaMetadata(
        titleOverride: CharSequence? = null,
        artistOverride: CharSequence? = null,
        artworkData: ByteArray? = null,
        includeArtworkUri: Boolean = true
    ): MediaMetadata {
        val extras = toMediaItemExtras().apply {
            putString(EXTRA_ONLINE_SOURCE, onlineSource)
            putString(EXTRA_ONLINE_ID, onlineId)
            putString(EXTRA_SONG_JSON, this@mediaMetadata.toPlaybackQueueJson().toString())
        }
        return MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setTitle(titleOverride ?: title)
            .setArtist(artistOverride ?: artist)
            .setAlbumTitle(album)
            .setAlbumArtist(artist)
            .setDisplayTitle(titleOverride ?: title)
            .setSubtitle(artistOverride ?: artist)
            .setDescription(album)
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setDiscNumber(discNumber.takeIf { it > 0 })
            .setExtras(extras)
            .apply {
                duration.takeIf { it > 0L }?.let(::setDurationMs)
                if (artworkData != null) {
                    setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                } else if (includeArtworkUri) {
                    artworkUriForMediaCenter()?.let(::setArtworkUri)
                }
            }
            .build()
    }

    private fun Song.artworkUriForMediaCenter(): Uri? {
        coverUrl.takeIf { it.isNotBlank() }?.let { return it.toUri() }
        if (albumId > 0L) {
            return Uri.parse("content://media/external/audio/albumart/$albumId")
        }
        return null
    }

    private fun updateCurrentSong() {
        val controller = mediaController ?: return
        if (shouldIgnoreStaleControllerSong(controller)) return

        val currentIndex = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem
        val itemSong = currentItem?.toSongFromMediaItemExtras() ?: currentItem?.toSong()
        val playlistIndex = virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices } ?: currentIndex
        val playlistSong = playlist.getOrNull(playlistIndex)
        val restoredSong = if (currentIndex in playlist.indices) {
            itemSong?.takeUnless { it.isSamePlaybackIdentity(playlistSong) } ?: playlistSong
        } else {
            itemSong
        }
        val previousSong = _currentSong.value
        _currentSong.value = restoredSong
        _duration.value = controller.duration.coerceAtLeast(0)
        if (!previousSong.isSamePlaybackIdentity(restoredSong)) {
            resetPlayNextForwardStack()
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            artworkAppliedSongKey = null
            sessionMetadataSongKey = null
            clearBluetoothMetadataPatchState()
        }
        savePlaybackState(force = true)
    }

    private fun shouldIgnoreStaleControllerSong(controller: MediaController): Boolean {
        val guard = externalSnapshotGuard ?: return false
        if (controller.matchesExternalSnapshot(guard)) {
            return false
        }
        val currentSong = _currentSong.value
        val currentItem = controller.currentMediaItem
        if (currentSong != null &&
            currentItem?.isMetadataOnlyPatch() == true &&
            currentItem.matchesSong(currentSong)
        ) {
            externalSnapshotGuard = null
            return false
        }
        return true
    }

    private fun MediaController.matchesExternalSnapshot(guard: ExternalSnapshotGuard?): Boolean {
        guard ?: return true
        val item = currentMediaItem ?: return false
        if (guard.mediaId != null && item.mediaId == guard.mediaId) return true
        return item.matchesSong(guard.song)
    }

    private fun shufflePlaylistKeepingCurrent() {
        val controller = mediaController ?: return
        if (reorderingPlaylistForShuffle) return
        if (virtualPlaylistCurrentIndex != null || playlist.size <= 1) return
        if (playlistBeforeShuffle == null) {
            playlistBeforeShuffle = playlist.toList()
        }
        val sourceOrder = playlistBeforeShuffle ?: playlist.toList()
        val current = resolveCurrentPlaybackSong(controller) ?: return
        val currentIndex = sourceOrder.indexOfFirst { it.isSamePlaybackIdentity(current) }
            .takeIf { it >= 0 }
            ?: return
        val currentSong = sourceOrder[currentIndex]
        val shuffleSeed = if (shuffleMode == SettingsManager.SHUFFLE_MODE_TRUE_RANDOM) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            buildPseudoShuffleSeed(sourceOrder, currentSong)
        }
        val shuffled = sourceOrder
            .filterIndexed { index, _ -> index != currentIndex }
            .shuffled(Random(shuffleSeed))
        val newPlaylist = listOf(currentSong) + shuffled
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        reorderingPlaylistForShuffle = true
        try {
            applyControllerPlaylistOrder(
                controller = controller,
                targetOrder = newPlaylist,
                targetIndex = 0,
                positionMs = positionMs,
                wasPlaying = wasPlaying
            )
            playlist.clear()
            playlist.addAll(newPlaylist)
            _playlist.value = newPlaylist
            updateCurrentSong()
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    private fun restorePlaylistOrderAfterShuffle() {
        val original = playlistBeforeShuffle ?: return
        if (original.isEmpty()) {
            playlistBeforeShuffle = null
            return
        }
        val controller = mediaController ?: run {
            playlist.clear()
            playlist.addAll(original)
            _playlist.value = original
            playlistBeforeShuffle = null
            return
        }
        if (reorderingPlaylistForShuffle) return

        val current = resolveCurrentPlaybackSong(controller)
        val targetIndex = original.indexOfFirst { it.isSamePlaybackIdentity(current) }
            .takeIf { it >= 0 }
            ?: controller.currentMediaItemIndex.coerceIn(0, original.lastIndex)
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        reorderingPlaylistForShuffle = true
        try {
            applyControllerPlaylistOrder(
                controller = controller,
                targetOrder = original,
                targetIndex = targetIndex,
                positionMs = positionMs,
                wasPlaying = wasPlaying
            )
            playlist.clear()
            playlist.addAll(original)
            _playlist.value = original
            updateCurrentSong()
            playlistBeforeShuffle = null
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    private fun resolveCurrentPlaybackSong(controller: MediaController): Song? {
        val controllerIndex = controller.currentMediaItemIndex
        val itemSong = controller.currentMediaItem?.toSongFromMediaItemExtras()
            ?: controller.currentMediaItem?.toSong()
        if (controllerIndex in playlist.indices) {
            val playlistSong = playlist[controllerIndex]
            return itemSong?.takeUnless { it.isSamePlaybackIdentity(playlistSong) } ?: playlistSong
        }
        return itemSong
            ?: _currentSong.value
    }

    private fun buildPseudoShuffleSeed(sourceOrder: List<Song>, current: Song): Long {
        var seed = 0x9E3779B97F4A7C15uL.toLong()
        sourceOrder.forEachIndexed { index, song ->
            val part = "${song.id}|${song.path}|${song.dateModified}|${song.fileSize}|$index".hashCode().toLong()
            seed = seed xor (part + 0x9E3779B97F4A7C15uL.toLong() + (seed shl 6) + (seed ushr 2))
        }
        seed = seed xor "${current.id}|${current.path}".hashCode().toLong()
        return seed
    }

    private fun applyControllerPlaylistOrder(
        controller: MediaController,
        targetOrder: List<Song>,
        targetIndex: Int,
        positionMs: Long,
        wasPlaying: Boolean
    ) {
        if (targetOrder.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(targetOrder.indices)
        // Always use setMediaItems (single IPC call) instead of N moveMediaItem calls
        // to avoid main-thread freezes with large playlists
        controller.setMediaItems(targetOrder.map(::songToMediaItem), safeIndex, positionMs)
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        if (wasPlaying) controller.play()
    }

    private fun refreshCurrentSessionMetadata(controller: MediaController, song: Song) {
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        val songKey = song.playbackStackKey()
        if (index < 0 || sessionMetadataSongKey == songKey) return
        if (!currentItem.matchesSong(song)) return

        runCatching {
            val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
            sessionMetadataSongKey = songKey
            controller.replaceMediaItem(
                index,
                currentItem.buildUpon()
                    .setMediaMetadata(
                        song.mediaMetadata(
                            artworkData = cachedArtwork,
                            includeArtworkUri = cachedArtwork != null
                        ).withPatchedExtrasFrom(currentItem, PATCH_REASON_BASE_SESSION_METADATA)
                    )
                    .build()
            )
            Log.d(TIMING_TAG, "base metadata updated mediaId=${song.id}")
        }
    }

    private fun refreshCurrentNotificationArtwork(song: Song?) {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val index = controller.currentMediaItemIndex
        val songKey = song?.notificationArtworkKey()
        if (song == null || index < 0 || artworkAppliedSongKey == songKey) return
        if (song.coverUrl.isNotBlank() || song.albumId > 0L) {
            if (song.coverUrl.isNotBlank()) {
                artworkAppliedSongKey = songKey
                return
            }
        }
        if (!currentItem.matchesSong(song)) return

        val artworkKey = song.notificationArtworkKey()
        val cached = notificationArtworkCache.get(artworkKey)
        if (cached != null) {
            Log.d(TIMING_TAG, "artwork cache hit mediaId=${song.id}")
            replaceCurrentItemArtwork(controller, index, song, cached)
            return
        }
        if (missingNotificationArtworkKeys.contains(artworkKey)) return

        notificationArtworkJob?.cancel()
        notificationArtworkJob = persistenceScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.d(TIMING_TAG, "artwork load start mediaId=${song.id}")
            val data = runCatching {
                artworkRepository.getCoverArt(song)
            }.getOrElse { error ->
                AppLogStore.warn(context, "PlayerArtwork", "Failed to load notification artwork for ${song.title}", error)
                null
            }
            if (data == null) {
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms missing")
                withContext(Dispatchers.Main.immediate) {
                    if (mediaController?.currentMediaItem?.matchesSong(song) == true) {
                        missingNotificationArtworkKeys += artworkKey
                    }
                }
                return@launch
            }
            if (data.size > MAX_NOTIFICATION_ARTWORK_BYTES) {
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms oversized=${data.size}")
                AppLogStore.warn(context, "PlayerArtwork", "Skip oversized notification artwork for ${song.title}: ${data.size} bytes")
                withContext(Dispatchers.Main.immediate) {
                    if (mediaController?.currentMediaItem?.matchesSong(song) == true) {
                        missingNotificationArtworkKeys += artworkKey
                    }
                }
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                val latestController = mediaController ?: return@withContext
                val latestIndex = latestController.currentMediaItemIndex
                val latestItem = latestController.currentMediaItem ?: return@withContext
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms")
                if (_currentSong.value.isSamePlaybackIdentity(song) &&
                    latestItem.matchesSong(song) &&
                    latestIndex >= 0
                ) {
                    notificationArtworkCache.put(artworkKey, data)
                    replaceCurrentItemArtwork(latestController, latestIndex, song, data)
                }
            }
        }
    }

    private fun replaceCurrentItemArtwork(
        controller: MediaController,
        index: Int,
        song: Song,
        artworkData: ByteArray
    ) {
        if (index != controller.currentMediaItemIndex) return
        val latestItem = controller.currentMediaItem ?: return
        if (!latestItem.matchesSong(song)) return
        runCatching {
            artworkAppliedSongKey = song.notificationArtworkKey()
            controller.replaceMediaItem(
                index,
                latestItem.buildUpon()
                    .setMediaMetadata(
                        song.mediaMetadata(artworkData = artworkData)
                            .withPatchedExtrasFrom(latestItem, PATCH_REASON_NOTIFICATION_ARTWORK)
                    )
                    .build()
            )
            Log.d(TIMING_TAG, "artwork metadata updated mediaId=${song.id}")
        }
    }

    private fun MediaItem.matchesSong(song: Song): Boolean {
        val itemSong = toSongFromMediaItemExtras()
        if (itemSong != null) {
            return itemSong.isSamePlaybackIdentity(song)
        }
        if (song.path.isNotBlank() && localConfiguration?.uri?.toString().orEmpty() == song.path) return true
        if (song.id > 0L && mediaId == song.id.toString()) return true
        return localConfiguration?.uri?.toString().orEmpty() == song.path
    }

    private fun clearBluetoothMetadataPatchState() {
        bluetoothMetadataSongKey = null
        bluetoothMetadataPayload = null
    }

    private fun MediaMetadata.withPatchedExtrasFrom(item: MediaItem, reason: String): MediaMetadata {
        val mergedExtras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY)
        extras?.let(mergedExtras::putAll)
        mergedExtras.markMetadataOnlyPatch(reason)
        return buildUpon()
            .setExtras(mergedExtras)
            .build()
    }

    private fun restoreSavedQueueIfNeeded() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 0) return

        val saved = loadSavedQueue() ?: return
        if (saved.songs.isEmpty()) return

        val requestedIndex = saved.index.coerceIn(saved.songs.indices)
        val (queueSongs, safeIndex) = saved.songs.windowedForController(requestedIndex)
        playlist.clear()
        playlist.addAll(queueSongs)
        _playlist.value = playlist.toList()

        controller.setMediaItems(queueSongs.map(::songToMediaItem), safeIndex, saved.positionMs.coerceAtLeast(0L))
        controller.repeatMode = saved.repeatMode
        controller.shuffleModeEnabled = false
        controller.playbackParameters = PlaybackParameters(saved.speed.coerceIn(0.5f, 2f), saved.pitch.coerceIn(0.5f, 2f))
        controller.prepare()

        _currentSong.value = playlist.getOrNull(safeIndex)
        _currentPosition.value = saved.positionMs.coerceAtLeast(0L)
        _repeatMode.value = saved.repeatMode
        _shuffleEnabled.value = saved.shuffle
        persistAppShuffleEnabled(saved.shuffle)
        _playbackSpeed.value = saved.speed
        _playbackPitch.value = saved.pitch
        if (saved.songs.size > MAX_CONTROLLER_QUEUE) savePlaybackQueue(force = true)
    }

    private fun savePlaybackQueue(force: Boolean = false) {
        if (playlist.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastQueueSaveMs < 2_500L) return
        lastQueueSaveMs = now

        val songs = playlist.toList()
        val snapshot = capturePlaybackState()

        persistenceScope.launch {
            val payload = playbackQueueJson(snapshot, songs)

            context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUEUE, payload.toString())
                .putString(KEY_STATE, snapshot.toJson().toString())
                .apply()
        }
    }

    private fun savePlaybackState(force: Boolean = false) {
        if (playlist.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastStateSaveMs < 2_500L) return
        lastStateSaveMs = now

        val snapshot = capturePlaybackState()
        persistenceScope.launch {
            context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, snapshot.toJson().toString())
                .apply()
        }
    }

    private fun capturePlaybackState(): PlaybackStateSnapshot {
        val controller = mediaController
        val index = controller?.currentMediaItemIndex?.takeIf { it >= 0 }
            ?: _currentSong.value?.let { current ->
                playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
            }
            ?: -1
        return PlaybackStateSnapshot(
            index = index.coerceAtLeast(0),
            positionMs = controller?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value,
            repeatMode = controller?.repeatMode ?: _repeatMode.value,
            shuffle = _shuffleEnabled.value,
            speed = controller?.playbackParameters?.speed ?: _playbackSpeed.value,
            pitch = controller?.playbackParameters?.pitch ?: _playbackPitch.value
        )
    }

    private fun clearSavedQueue() {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QUEUE)
            .remove(KEY_STATE)
            .remove(KEY_APP_SHUFFLE)
            .apply()
    }

    private fun persistAppShuffleEnabled(enabled: Boolean) {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_SHUFFLE, enabled)
            .apply()
    }

    private fun loadAppShuffleEnabled(): Boolean =
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_SHUFFLE, _shuffleEnabled.value)

    private fun loadSavedQueue(): SavedQueue? {
        val prefs = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
        val raw = prefs
            .getString(KEY_QUEUE, null)
            ?: return null

        return parseSavedQueue(raw, prefs.getString(KEY_STATE, null))
    }

    fun hasSavedQueue(): Boolean = loadSavedQueue()?.songs?.isNotEmpty() == true

    private fun MediaItem.toSong(): Song {
        val metadata = mediaMetadata
        metadata.extras
            ?.getString(EXTRA_SONG_JSON)
            ?.let { raw -> runCatching { JSONObject(raw).toPlaybackQueueSongOrNull() }.getOrNull() }
            ?.let { return it }

        val path = localConfiguration?.uri?.toString().orEmpty()
        val mediaIdValue = mediaId.toLongOrNull() ?: path.hashCode().toLong()
        val fileName = path.substringAfterLast('/').ifBlank { metadata.title?.toString().orEmpty() }
        return Song(
            id = mediaIdValue,
            title = metadata.title?.toString()?.ifBlank { fileName } ?: fileName,
            artist = metadata.artist?.toString()?.ifBlank { "Unknown" } ?: "Unknown",
            album = metadata.albumTitle?.toString()?.ifBlank { "Music" } ?: "Music",
            albumId = 0L,
            duration = mediaController?.duration?.coerceAtLeast(0) ?: 0L,
            path = path,
            fileName = fileName,
            mimeType = localConfiguration?.mimeType.orEmpty(),
            coverUrl = metadata.artworkUri?.toString().orEmpty(),
            onlineSource = metadata.extras?.getString(EXTRA_ONLINE_SOURCE).orEmpty(),
            onlineId = metadata.extras?.getString(EXTRA_ONLINE_ID).orEmpty()
        )
    }

    private companion object {
        const val TIMING_TAG = "EllaPlaybackTiming"
        // Guard so a seek never lands on the last frame and trips end-of-stream auto-advance.
        const val SEEK_END_GUARD_MS = 600L
        const val CLEAR_EXTERNAL_SNAPSHOT_SUPPRESSION_MS = 3_000L
        // Max items handed to the media session at once; larger queues overflow the ~1MB Binder
        // transaction limit (TransactionTooLargeException) on very large libraries.
        const val MAX_CONTROLLER_QUEUE = 1000
        const val EXTRA_ONLINE_SOURCE = "com.ella.music.extra.ONLINE_SOURCE"
        const val EXTRA_ONLINE_ID = "com.ella.music.extra.ONLINE_ID"
        const val EXTRA_SONG_JSON = "com.ella.music.extra.SONG_JSON"
        const val MAX_NOTIFICATION_ARTWORK_BYTES = 2 * 1024 * 1024
        const val PLAYBACK_PREFS = "ella_playback_state"
        const val KEY_QUEUE = "queue"
        const val KEY_STATE = "state"
        const val KEY_APP_SHUFFLE = "app_shuffle_enabled"
    }
}
