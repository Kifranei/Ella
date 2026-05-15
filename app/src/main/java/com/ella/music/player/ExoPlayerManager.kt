package com.ella.music.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.net.toUri
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
import com.ella.music.data.scanner.MusicScanner
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
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
    private val artworkScanner = MusicScanner(context)
    private val notificationArtworkCache = object : LruCache<Long, ByteArray>(4 * 1024) {
        override fun sizeOf(key: Long, value: ByteArray): Int = value.size / 1024
    }
    private val missingNotificationArtworkIds = mutableSetOf<Long>()
    private var artworkAppliedSongId: Long? = null
    private var sessionMetadataSongId: Long? = null

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
        delay(300)
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
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && shouldUseTrueRandomShuffle()) {
                    if (playTrueRandomItem()) return
                }
                updateCurrentSong()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                refreshStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
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
        sessionMetadataSongId = null
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
            savePlaybackQueue(force = true)
            return
        }

        controller.apply {
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
        sessionMetadataSongId = null
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

    fun playQueueIndex(index: Int) {
        if (index !in playlist.indices) return
        mediaController?.seekToDefaultPosition(index)
        mediaController?.play()
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    fun clearPlaylist() {
        virtualPlaylistCurrentIndex = null
        playlist.clear()
        _playlist.value = emptyList()
        _currentSong.value = null
        sessionMetadataSongId = null
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
            mediaController?.seekToNext()
            updateCurrentSong()
        }
        savePlaybackQueue(force = true)
    }

    fun skipToPrevious() {
        mediaController?.seekToPrevious()
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        savePlaybackState(force = true)
    }

    fun toggleShuffle() {
        mediaController?.shuffleModeEnabled = !(mediaController?.shuffleModeEnabled ?: false)
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
        mediaController?.repeatMode = next
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

        val displayTitle = lyricText ?: song.title
        val displayArtist = if (lyricText != null) {
            secondaryText?.takeIf { it.isNotBlank() } ?: "${song.title} · ${song.artist}"
        } else {
            song.artist
        }

        val metadata = song.mediaMetadata(displayTitle, displayArtist, notificationArtworkCache.get(song.id))

        val newItem = currentItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()

        runCatching {
            controller.replaceMediaItem(index, newItem)
        }
    }

    fun clearBluetoothLyric() {
        updateBluetoothLyric(null)
    }
    fun refreshStateFromController() {
        val controller = mediaController ?: return
        _isPlaying.value = controller.isPlaying
        _playbackState.value = controller.playbackState
        _shuffleEnabled.value = controller.shuffleModeEnabled
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

    private fun songToMediaItem(song: Song): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(song.playbackUri())
            .setMediaId(song.id.toString())
            .setMediaMetadata(song.mediaMetadata())

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
        artworkData: ByteArray? = null
    ): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(titleOverride ?: title)
            .setArtist(artistOverride ?: artist)
            .setAlbumTitle(album)
            .apply {
                if (artworkData != null) {
                    setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_ONLINE_SOURCE, onlineSource)
                    putString(EXTRA_ONLINE_ID, onlineId)
                    putString(EXTRA_SONG_JSON, this@mediaMetadata.toJson().toString())
                }
            )
            .build()
    }

    private fun updateCurrentSong() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val restoredSong = if (currentIndex in playlist.indices) {
            playlist[virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices } ?: currentIndex]
        } else {
            controller.currentMediaItem?.toSong()
        }
        val previousSongId = _currentSong.value?.id
        _currentSong.value = restoredSong
        _duration.value = controller.duration.coerceAtLeast(0)
        if (restoredSong != null && previousSongId != restoredSong.id) {
            refreshCurrentSessionMetadata(controller, restoredSong)
        }
        savePlaybackState(force = true)
        refreshCurrentNotificationArtwork(restoredSong)
    }

    private fun refreshCurrentSessionMetadata(controller: MediaController, song: Song) {
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        if (index < 0 || sessionMetadataSongId == song.id) return

        runCatching {
            sessionMetadataSongId = song.id
            controller.replaceMediaItem(
                index,
                currentItem.buildUpon()
                    .setMediaMetadata(song.mediaMetadata(artworkData = notificationArtworkCache.get(song.id)))
                    .build()
            )
        }
    }

    private fun refreshCurrentNotificationArtwork(song: Song?) {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val index = controller.currentMediaItemIndex
        if (song == null || index < 0 || artworkAppliedSongId == song.id) return
        if (song.coverUrl.isNotBlank() || song.albumId > 0L) {
            artworkAppliedSongId = song.id
            return
        }
        val cached = notificationArtworkCache.get(song.id)
        if (cached != null) {
            replaceCurrentItemArtwork(controller, currentItem, index, song, cached)
            return
        }
        if (missingNotificationArtworkIds.contains(song.id)) return

        persistenceScope.launch {
            val data = runCatching {
                artworkScanner.extractCoverArt(song.path)
            }.getOrElse { error ->
                AppLogStore.warn(context, "PlayerArtwork", "Failed to load notification artwork for ${song.title}", error)
                null
            }
            if (data == null) {
                withContext(Dispatchers.Main.immediate) {
                    missingNotificationArtworkIds += song.id
                }
                return@launch
            }
            if (data.size > MAX_NOTIFICATION_ARTWORK_BYTES) {
                AppLogStore.warn(context, "PlayerArtwork", "Skip oversized notification artwork for ${song.title}: ${data.size} bytes")
                withContext(Dispatchers.Main.immediate) {
                    missingNotificationArtworkIds += song.id
                }
                return@launch
            }
            notificationArtworkCache.put(song.id, data)
            withContext(Dispatchers.Main.immediate) {
                val latestController = mediaController ?: return@withContext
                val latestIndex = latestController.currentMediaItemIndex
                if (_currentSong.value?.id == song.id && latestIndex >= 0) {
                    replaceCurrentItemArtwork(latestController, latestController.currentMediaItem ?: return@withContext, latestIndex, song, data)
                }
            }
        }
    }

    private fun replaceCurrentItemArtwork(
        controller: MediaController,
        currentItem: MediaItem,
        index: Int,
        song: Song,
        artworkData: ByteArray
    ) {
        runCatching {
            artworkAppliedSongId = song.id
            controller.replaceMediaItem(
                index,
                currentItem.buildUpon()
                    .setMediaMetadata(song.mediaMetadata(artworkData = artworkData))
                    .build()
            )
        }
    }

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

    private fun restoreSavedQueueIfNeeded() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 0 || playlist.isNotEmpty()) return

        val saved = loadSavedQueue() ?: return
        if (saved.songs.isEmpty()) return

        playlist.clear()
        playlist.addAll(saved.songs)
        _playlist.value = playlist.toList()

        val index = saved.index.coerceIn(0, playlist.lastIndex)
        controller.setMediaItems(playlist.map(::songToMediaItem), index, saved.positionMs.coerceAtLeast(0L))
        controller.repeatMode = saved.repeatMode
        controller.shuffleModeEnabled = saved.shuffle
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
            shuffle = controller?.shuffleModeEnabled ?: _shuffleEnabled.value,
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
        const val EXTRA_ONLINE_SOURCE = "com.ella.music.extra.ONLINE_SOURCE"
        const val EXTRA_ONLINE_ID = "com.ella.music.extra.ONLINE_ID"
        const val EXTRA_SONG_JSON = "com.ella.music.extra.SONG_JSON"
        const val MAX_NOTIFICATION_ARTWORK_BYTES = 2 * 1024 * 1024
        const val PLAYBACK_PREFS = "ella_playback_state"
        const val KEY_QUEUE = "queue"
        const val KEY_STATE = "state"
    }
}
