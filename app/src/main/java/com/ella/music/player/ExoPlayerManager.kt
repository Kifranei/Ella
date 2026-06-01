package com.ella.music.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import org.json.JSONArray
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
    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlistFlow: StateFlow<List<Song>> = _playlist.asStateFlow()
    private var playerListener: Player.Listener? = null
    private var lastQueueSaveMs = 0L
    private var lastStateSaveMs = 0L
    private var shuffleMode = SettingsManager.SHUFFLE_MODE_PSEUDO
    private var virtualPlaylistCurrentIndex: Int? = null
    private var playWhenConnected = false
    private var pendingPlaylist: PendingPlaylist? = null

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkRepository = MusicRepository(context)
    private val notificationArtworkCache = object : LruCache<String, ByteArray>(4 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val missingNotificationArtworkKeys = mutableSetOf<String>()
    private var notificationArtworkJob: Job? = null
    private var artworkAppliedSongId: Long? = null
    private var sessionMetadataSongId: Long? = null
    private var bluetoothMetadataSongId: Long? = null
    private var bluetoothMetadataPayload: Pair<String?, String?>? = null

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
        sessionMetadataSongId = null
        artworkAppliedSongId = null
        bluetoothMetadataSongId = null
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
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && shouldUseTrueRandomShuffle()) {
                    if (playTrueRandomItem()) return
                }
                updateCurrentSong()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                refreshStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shuffleModeEnabled) {
                    mediaController?.shuffleModeEnabled = false
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
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
            setPlaylist(pending.songs, pending.startIndex)
        } else {
            restoreSavedQueueIfNeeded()
        }
        refreshStateFromController()
        if (playWhenConnected) {
            playWhenConnected = false
            play()
        }
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        AppLogStore.debug(context, "PlayerQueue", "setPlaylist size=${songs.size} start=$startIndex")
        virtualPlaylistCurrentIndex = null
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongId = null
        artworkAppliedSongId = null
        bluetoothMetadataSongId = null
        bluetoothMetadataPayload = null
        playlist.clear()
        playlist.addAll(songs)
        _playlist.value = playlist.toList()

        val safeIndex = startIndex.coerceIn(songs.indices)
        val mediaItems = songs.map(::songToMediaItem)
        val controller = mediaController
        if (controller == null) {
            pendingPlaylist = PendingPlaylist(songs.toList(), safeIndex)
            _currentSong.value = songs.getOrNull(safeIndex)
            _duration.value = songs.getOrNull(safeIndex)?.duration ?: 0L
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

    fun playResolvedFromVirtualQueue(songs: List<Song>, currentIndex: Int, resolvedSong: Song) {
        if (songs.isEmpty()) return
        AppLogStore.debug(context, "PlayerQueue", "playResolvedVirtual size=${songs.size} index=$currentIndex title=${resolvedSong.title}")
        val safeIndex = currentIndex.coerceIn(songs.indices)
        virtualPlaylistCurrentIndex = safeIndex
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongId = null
        artworkAppliedSongId = null
        bluetoothMetadataSongId = null
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
        virtualPlaylistCurrentIndex = null
        val controller = mediaController
        val insertIndex = ((controller?.currentMediaItemIndex ?: playlist.indexOfFirst { it.id == _currentSong.value?.id }) + 1)
            .coerceIn(0, playlist.size)
        AppLogStore.debug(context, "PlayerQueue", "playNext title=${song.title} index=$insertIndex")
        playlist.add(insertIndex, song)
        _playlist.value = playlist.toList()
        controller?.addMediaItem(insertIndex, songToMediaItem(song))
        if ((controller?.mediaItemCount ?: 0) == 1) {
            controller?.prepare()
        }
        savePlaybackQueue(force = true)
    }

    fun playQueueIndex(index: Int) {
        if (index !in playlist.indices) return
        mediaController?.seekToDefaultPosition(index)
        mediaController?.play()
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    fun removeFromPlaylist(index: Int) {
        if (index !in playlist.indices) return
        virtualPlaylistCurrentIndex = null
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

    fun clearPlaylist() {
        virtualPlaylistCurrentIndex = null
        playlist.clear()
        _playlist.value = emptyList()
        _currentSong.value = null
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongId = null
        artworkAppliedSongId = null
        bluetoothMetadataSongId = null
        bluetoothMetadataPayload = null
        _currentPosition.value = 0L
        _duration.value = 0L
        mediaController?.run {
            stop()
            clearMediaItems()
        }
        clearSavedQueue()
    }

    fun playSong(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index >= 0) {
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
        if (!playTrueRandomItem()) {
            if (!seekAdjacentMediaItemInRepeatOne(1)) {
                mediaController?.seekToNextMediaItem()
            }
            updateCurrentSong()
        }
        savePlaybackQueue(force = true)
    }

    fun skipToPrevious() {
        if (!seekAdjacentMediaItemInRepeatOne(-1)) {
            mediaController?.seekToPreviousMediaItem()
        }
        updateCurrentSong()
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
            playlist.indexOfFirst { it.id == current.id || it.path == current.path }
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

    private fun seekAdjacentMediaItemInRepeatOne(offset: Int): Boolean {
        val controller = mediaController ?: return false
        if (controller.repeatMode != Player.REPEAT_MODE_ONE) return false

        val itemCount = controller.mediaItemCount
        val currentIndex = controller.currentMediaItemIndex
        if (itemCount <= 0 || currentIndex !in 0 until itemCount) return false

        val targetIndex = if (itemCount == 1) {
            currentIndex
        } else {
            Math.floorMod(currentIndex + offset, itemCount)
        }
        controller.seekToDefaultPosition(targetIndex)
        controller.play()
        _currentPosition.value = 0L
        return true
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        savePlaybackState(force = true)
    }

    fun toggleShuffle() {
        val enableShuffle = !_shuffleEnabled.value
        if (enableShuffle) {
            shufflePlaylistKeepingCurrent()
            mediaController?.let { controller ->
                controller.shuffleModeEnabled = false
                if (controller.repeatMode != Player.REPEAT_MODE_ALL) {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                }
            }
        } else {
            mediaController?.shuffleModeEnabled = false
        }
        _shuffleEnabled.value = enableShuffle
        savePlaybackQueue(force = true)
    }

    fun setShuffleMode(mode: Int) {
        shuffleMode = mode.coerceIn(
            SettingsManager.SHUFFLE_MODE_PSEUDO,
            SettingsManager.SHUFFLE_MODE_TRUE_RANDOM
        )
    }

    fun toggleRepeat() {
        val current = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        if (next != Player.REPEAT_MODE_ALL && mediaController?.shuffleModeEnabled == true) {
            mediaController?.shuffleModeEnabled = false
        }
        if (next != Player.REPEAT_MODE_ALL) {
            _shuffleEnabled.value = false
        }
        mediaController?.repeatMode = next
        savePlaybackQueue(force = true)
    }

    fun cyclePlaybackMode() {
        val controller = mediaController ?: return
        when {
            _shuffleEnabled.value -> {
                _shuffleEnabled.value = false
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }

            controller.repeatMode == Player.REPEAT_MODE_OFF -> {
                _shuffleEnabled.value = false
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ALL
            }

            controller.repeatMode == Player.REPEAT_MODE_ALL -> {
                _shuffleEnabled.value = false
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ONE
            }

            else -> {
                _shuffleEnabled.value = true
                controller.repeatMode = Player.REPEAT_MODE_ALL
                shufflePlaylistKeepingCurrent()
                controller.shuffleModeEnabled = false
            }
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
        val lyricText = text?.takeIf { it.isNotBlank() }
        val lyricSecondaryText = secondaryText?.takeIf { it.isNotBlank() }
        val payload = lyricText to lyricSecondaryText

        if (lyricText == null && bluetoothMetadataSongId != song.id) return
        if (bluetoothMetadataSongId == song.id && bluetoothMetadataPayload == payload) return

        val displayTitle = lyricText ?: song.title
        val displayArtist = if (lyricText != null) {
            lyricSecondaryText ?: "${song.title} · ${song.artist}"
        } else {
            song.artist
        }

        if (currentItem.mediaMetadata.title == displayTitle &&
            currentItem.mediaMetadata.artist == displayArtist
        ) {
            bluetoothMetadataSongId = if (lyricText == null) null else song.id
            bluetoothMetadataPayload = if (lyricText == null) null else payload
            return
        }

        val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
        val metadata = song.mediaMetadata(
            titleOverride = displayTitle,
            artistOverride = displayArtist,
            artworkData = cachedArtwork,
            includeArtworkUri = cachedArtwork != null
        )

        val newItem = currentItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()

        runCatching {
            controller.replaceMediaItem(index, newItem)
            bluetoothMetadataSongId = if (lyricText == null) null else song.id
            bluetoothMetadataPayload = if (lyricText == null) null else payload
        }
    }

    fun clearBluetoothLyric() {
        updateBluetoothLyric(null)
    }
    fun refreshStateFromController() {
        val controller = mediaController ?: return
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
            val currentMediaId = controller.currentMediaItem?.mediaId?.toLongOrNull()
            val savedCurrentIndex = currentMediaId?.let { id -> saved?.songs?.indexOfFirst { it.id == id } } ?: -1
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

    fun updateCurrentSongMetadata(updatedSong: Song) {
        val controller = mediaController
        val current = _currentSong.value ?: return
        if (current.id != updatedSong.id && current.path != updatedSong.path) return

        val playlistIndex = playlist.indexOfFirst { it.id == current.id || it.path == current.path }
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
        artworkAppliedSongId = null
        sessionMetadataSongId = null

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
        if (path.startsWith("content://", ignoreCase = true) ||
            path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            path.startsWith("file://", ignoreCase = true)
        ) {
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
            putString(EXTRA_SONG_JSON, this@mediaMetadata.toJson().toString())
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
            .setDurationMs(duration.takeIf { it > 0L } ?: C.TIME_UNSET)
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setDiscNumber(discNumber.takeIf { it > 0 })
            .setExtras(extras)
            .apply {
                if (artworkData != null) {
                    setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                if (includeArtworkUri) {
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
        val currentIndex = controller.currentMediaItemIndex
        val restoredSong = if (currentIndex in playlist.indices) {
            playlist[virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices } ?: currentIndex]
        } else {
            controller.currentMediaItem?.toSong()
        }
        val previousSong = _currentSong.value
        _currentSong.value = restoredSong
        _duration.value = controller.duration.coerceAtLeast(0)
        if (!previousSong.isSamePlaybackIdentity(restoredSong)) {
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            artworkAppliedSongId = null
            sessionMetadataSongId = null
        }
        if (restoredSong != null && !previousSong.isSamePlaybackIdentity(restoredSong)) {
            refreshCurrentSessionMetadata(controller, restoredSong)
        }
        savePlaybackState(force = true)
        refreshCurrentNotificationArtwork(restoredSong)
    }

    private fun shufflePlaylistKeepingCurrent() {
        val controller = mediaController ?: return
        if (virtualPlaylistCurrentIndex != null || playlist.size <= 1) return

        val currentIndex = controller.currentMediaItemIndex
            .takeIf { it in playlist.indices }
            ?: playlist.indexOfFirst { it.isSamePlaybackIdentity(_currentSong.value) }
                .takeIf { it >= 0 }
            ?: return
        val current = playlist[currentIndex]
        val shuffled = playlist
            .filterIndexed { index, _ -> index != currentIndex }
            .shuffled(Random(SystemClock.elapsedRealtimeNanos()))
        val newPlaylist = listOf(current) + shuffled
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying
        val oldPlaylist = playlist.toList()

        playlist.clear()
        playlist.addAll(newPlaylist)
        _playlist.value = newPlaylist
        if (!moveControllerQueue(controller, oldPlaylist, newPlaylist)) {
            controller.setMediaItems(newPlaylist.map(::songToMediaItem), 0, positionMs)
            controller.prepare()
            if (wasPlaying) controller.play()
        }
        updateCurrentSong()
    }

    private fun moveControllerQueue(
        controller: MediaController,
        oldPlaylist: List<Song>,
        newPlaylist: List<Song>
    ): Boolean {
        if (controller.mediaItemCount != newPlaylist.size) return false
        val currentOrder = oldPlaylist.toMutableList()
        if (currentOrder.size != newPlaylist.size) return false

        newPlaylist.forEachIndexed { targetIndex, targetSong ->
            val currentIndex = currentOrder.indexOfFirst { it.isSamePlaybackIdentity(targetSong) }
            if (currentIndex < 0) return false
            if (currentIndex != targetIndex) {
                controller.moveMediaItem(currentIndex, targetIndex)
                val moved = currentOrder.removeAt(currentIndex)
                currentOrder.add(targetIndex, moved)
            }
        }
        return true
    }

    private fun refreshCurrentSessionMetadata(controller: MediaController, song: Song) {
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        if (index < 0 || sessionMetadataSongId == song.id) return
        if (!currentItem.matchesSong(song)) return

        runCatching {
            val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
            sessionMetadataSongId = song.id
            controller.replaceMediaItem(
                index,
                currentItem.buildUpon()
                    .setMediaMetadata(
                        song.mediaMetadata(
                            artworkData = cachedArtwork,
                            includeArtworkUri = cachedArtwork != null
                        )
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
        if (song == null || index < 0 || artworkAppliedSongId == song.id) return
        if (song.coverUrl.isNotBlank() || song.albumId > 0L) {
            if (song.coverUrl.isNotBlank()) {
                artworkAppliedSongId = song.id
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
            artworkAppliedSongId = song.id
            controller.replaceMediaItem(
                index,
                latestItem.buildUpon()
                    .setMediaMetadata(song.mediaMetadata(artworkData = artworkData))
                    .build()
            )
            Log.d(TIMING_TAG, "artwork metadata updated mediaId=${song.id}")
        }
    }

    private fun MediaItem.matchesSong(song: Song): Boolean {
        if (song.id > 0L && mediaId == song.id.toString()) return true
        val itemSong = toSongFromMediaItemExtras()
        if (itemSong != null) {
            return itemSong.isSamePlaybackIdentity(song)
        }
        return localConfiguration?.uri?.toString().orEmpty() == song.path
    }

    private fun Song?.isSamePlaybackIdentity(other: Song?): Boolean {
        if (this == null || other == null) return this == other
        if (id > 0L && id == other.id) return true
        return path.isNotBlank() && path == other.path
    }

    private fun Song.notificationArtworkKey(): String =
        "${id}:${path}:${dateModified}:${fileSize}:${coverUrl}"

    private fun shouldUseTrueRandomShuffle(): Boolean =
        shuffleMode == SettingsManager.SHUFFLE_MODE_TRUE_RANDOM && _shuffleEnabled.value

    private fun playTrueRandomItem(): Boolean {
        if (!shouldUseTrueRandomShuffle()) return false
        if (virtualPlaylistCurrentIndex != null) return false

        val controller = mediaController ?: return false
        val itemCount = controller.mediaItemCount
        if (itemCount <= 0) return false

        val randomIndex = Random.nextInt(itemCount)
        controller.seekToDefaultPosition(randomIndex)
        controller.play()
        updateCurrentSong()
        return true
    }

    private fun restartCurrentInRepeatOne(): Boolean {
        val controller = mediaController ?: return false
        if (controller.repeatMode != Player.REPEAT_MODE_ONE) return false

        val itemCount = controller.mediaItemCount
        val currentIndex = controller.currentMediaItemIndex
        if (itemCount <= 0 || currentIndex !in 0 until itemCount) return false

        controller.seekToDefaultPosition(currentIndex)
        controller.play()
        _currentPosition.value = 0L
        return true
    }

    private fun restoreSavedQueueIfNeeded() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 0) return

        val saved = loadSavedQueue() ?: return
        if (saved.songs.isEmpty()) return

        playlist.clear()
        playlist.addAll(saved.songs)
        _playlist.value = playlist.toList()

        val index = saved.index.coerceIn(0, playlist.lastIndex)
        controller.setMediaItems(playlist.map(::songToMediaItem), index, saved.positionMs.coerceAtLeast(0L))
        controller.repeatMode = saved.repeatMode
        controller.shuffleModeEnabled = false
        controller.playbackParameters = PlaybackParameters(saved.speed.coerceIn(0.5f, 2f), saved.pitch.coerceIn(0.5f, 2f))
        controller.prepare()

        _currentSong.value = playlist.getOrNull(index)
        _currentPosition.value = saved.positionMs.coerceAtLeast(0L)
        _repeatMode.value = saved.repeatMode
        _shuffleEnabled.value = saved.shuffle
        _playbackSpeed.value = saved.speed
        _playbackPitch.value = saved.pitch
    }

    private fun savePlaybackQueue(force: Boolean = false) {
        if (playlist.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastQueueSaveMs < 2_500L) return
        lastQueueSaveMs = now

        val songs = playlist.toList()
        val snapshot = capturePlaybackState()

        persistenceScope.launch {
            val payload = JSONObject()
                .put("index", snapshot.index)
                .put("positionMs", snapshot.positionMs)
                .put("repeatMode", snapshot.repeatMode)
                .put("shuffle", snapshot.shuffle)
                .put("speed", snapshot.speed)
                .put("pitch", snapshot.pitch)
                .put("songs", JSONArray().apply {
                    songs.forEach { song -> put(song.toJson()) }
                })

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
            ?: playlist.indexOfFirst { it.id == _currentSong.value?.id }
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
            .apply()
    }

    private fun loadSavedQueue(): SavedQueue? {
        val raw = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, null)
            ?: return null

        return runCatching {
            val payload = JSONObject(raw)
            val songsArray = payload.optJSONArray("songs") ?: JSONArray()
            val songs = (0 until songsArray.length())
                .mapNotNull { songsArray.optJSONObject(it)?.toSongOrNull() }
            val state = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATE, null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            SavedQueue(
                songs = songs,
                index = state?.optInt("index", 0) ?: payload.optInt("index", 0),
                positionMs = state?.optLong("positionMs", 0L) ?: payload.optLong("positionMs", 0L),
                repeatMode = state?.optInt("repeatMode", Player.REPEAT_MODE_OFF)
                    ?: payload.optInt("repeatMode", Player.REPEAT_MODE_OFF),
                shuffle = state?.optBoolean("shuffle", false) ?: payload.optBoolean("shuffle", false),
                speed = (state?.optDouble("speed", 1.0) ?: payload.optDouble("speed", 1.0)).toFloat(),
                pitch = (state?.optDouble("pitch", 1.0) ?: payload.optDouble("pitch", 1.0)).toFloat()
            )
        }.getOrNull()
    }

    private fun Song.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("albumId", albumId)
        .put("duration", duration)
        .put("path", path)
        .put("fileName", fileName)
        .put("fileSize", fileSize)
        .put("mimeType", mimeType)
        .put("dateAdded", dateAdded)
        .put("dateModified", dateModified)
        .put("trackNumber", trackNumber)
        .put("discNumber", discNumber)
        .put("albumArtist", albumArtist)
        .put("genre", genre)
        .put("year", year)
        .put("composer", composer)
        .put("lyricist", lyricist)
        .put("coverUrl", coverUrl)
        .put("onlineSource", onlineSource)
        .put("onlineId", onlineId)

    private fun JSONObject.toSongOrNull(): Song? {
        val path = optString("path").takeIf { it.isNotBlank() } ?: return null
        return Song(
            id = optLong("id", path.hashCode().toLong()),
            title = optString("title").ifBlank { optString("fileName").ifBlank { path.substringAfterLast('/') } },
            artist = optString("artist").ifBlank { "Unknown" },
            album = optString("album").ifBlank { "Music" },
            albumId = optLong("albumId", 0L),
            duration = optLong("duration", 0L),
            path = path,
            fileName = optString("fileName").ifBlank { path.substringAfterLast('/') },
            fileSize = optLong("fileSize", 0L),
            mimeType = optString("mimeType"),
            dateAdded = optLong("dateAdded", 0L),
            dateModified = optLong("dateModified", 0L),
            trackNumber = optInt("trackNumber", 0),
            discNumber = optInt("discNumber", 0),
            albumArtist = optString("albumArtist"),
            genre = optString("genre"),
            year = optString("year"),
            composer = optString("composer"),
            lyricist = optString("lyricist"),
            coverUrl = optString("coverUrl"),
            onlineSource = optString("onlineSource"),
            onlineId = optString("onlineId")
        )
    }

    private data class SavedQueue(
        val songs: List<Song>,
        val index: Int,
        val positionMs: Long,
        val repeatMode: Int,
        val shuffle: Boolean,
        val speed: Float,
        val pitch: Float
    )

    private data class PlaybackStateSnapshot(
        val index: Int,
        val positionMs: Long,
        val repeatMode: Int,
        val shuffle: Boolean,
        val speed: Float,
        val pitch: Float
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("index", index)
            .put("positionMs", positionMs)
            .put("repeatMode", repeatMode)
            .put("shuffle", shuffle)
            .put("speed", speed)
            .put("pitch", pitch)
    }

    fun hasSavedQueue(): Boolean = loadSavedQueue()?.songs?.isNotEmpty() == true

    private data class PendingPlaylist(
        val songs: List<Song>,
        val startIndex: Int
    )

    private fun MediaItem.toSong(): Song {
        val metadata = mediaMetadata
        metadata.extras
            ?.getString(EXTRA_SONG_JSON)
            ?.let { raw -> runCatching { JSONObject(raw).toSongOrNull() }.getOrNull() }
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
        const val EXTRA_ONLINE_SOURCE = "com.ella.music.extra.ONLINE_SOURCE"
        const val EXTRA_ONLINE_ID = "com.ella.music.extra.ONLINE_ID"
        const val EXTRA_SONG_JSON = "com.ella.music.extra.SONG_JSON"
        const val MAX_NOTIFICATION_ARTWORK_BYTES = 2 * 1024 * 1024
        const val PLAYBACK_PREFS = "ella_playback_state"
        const val KEY_QUEUE = "queue"
        const val KEY_STATE = "state"
    }
}
