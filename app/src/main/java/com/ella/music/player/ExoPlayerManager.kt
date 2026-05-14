package com.ella.music.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor
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
    private var shuffleMode = SettingsManager.SHUFFLE_MODE_PSEUDO
    private var virtualPlaylistCurrentIndex: Int? = null
    private var playWhenConnected = false
    private var pendingPlaylist: PendingPlaylist? = null

    private val directExecutor = Executor { it.run() }

    fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            setupListener()
        }, directExecutor)
    }

    fun disconnect() {
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun setupListener() {
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
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
        virtualPlaylistCurrentIndex = null
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
        val safeIndex = currentIndex.coerceIn(songs.indices)
        virtualPlaylistCurrentIndex = safeIndex
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
        }
        savePlaybackQueue(force = true)
    }

    fun skipToPrevious() {
        mediaController?.seekToPrevious()
        savePlaybackQueue(force = true)
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        savePlaybackQueue(force = true)
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
        savePlaybackQueue()
    }

    fun updatePosition() {
        if (_currentSong.value == null && (mediaController?.mediaItemCount ?: 0) > 0) {
            refreshStateFromController()
        }
        _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0) ?: 0L
        _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
        if (_currentSong.value != null) savePlaybackQueue()
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

        val metadata = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(displayArtist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.coverUrl.takeIf { it.isNotBlank() }?.toUri())
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_ONLINE_SOURCE, song.onlineSource)
                    putString(EXTRA_ONLINE_ID, song.onlineId)
                }
            )
            .build()

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
            playlist.clear()
            for (index in 0 until mediaItemCount) {
                playlist += controller.getMediaItemAt(index).toSong()
            }
        }
        _playlist.value = playlist.toList()
        updateCurrentSong()
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(song.path.toUri())
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.coverUrl.takeIf { it.isNotBlank() }?.toUri())
                    .setExtras(
                        Bundle().apply {
                            putString(EXTRA_ONLINE_SOURCE, song.onlineSource)
                            putString(EXTRA_ONLINE_ID, song.onlineId)
                        }
                    )
                    .build()
            )

        if (song.mimeType.isNotBlank()) {
            builder.setMimeType(song.mimeType)
        }

        return builder.build()
    }

    private fun updateCurrentSong() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val restoredSong = if (currentIndex in playlist.indices) {
            playlist[virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices } ?: currentIndex]
        } else {
            controller.currentMediaItem?.toSong()
        }
        _currentSong.value = restoredSong
        _duration.value = controller.duration.coerceAtLeast(0)
        savePlaybackQueue(force = true)
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

        val controller = mediaController
        val index = controller?.currentMediaItemIndex?.takeIf { it >= 0 } ?: playlist.indexOfFirst { it.id == _currentSong.value?.id }
        val payload = JSONObject()
            .put("index", index.coerceAtLeast(0))
            .put("positionMs", controller?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value)
            .put("repeatMode", controller?.repeatMode ?: _repeatMode.value)
            .put("shuffle", controller?.shuffleModeEnabled ?: _shuffleEnabled.value)
            .put("speed", controller?.playbackParameters?.speed ?: _playbackSpeed.value)
            .put("pitch", controller?.playbackParameters?.pitch ?: _playbackPitch.value)
            .put("songs", JSONArray().apply {
                playlist.forEach { song -> put(song.toJson()) }
            })

        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, payload.toString())
            .apply()
    }

    private fun clearSavedQueue() {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QUEUE)
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
            SavedQueue(
                songs = songs,
                index = payload.optInt("index", 0),
                positionMs = payload.optLong("positionMs", 0L),
                repeatMode = payload.optInt("repeatMode", Player.REPEAT_MODE_OFF),
                shuffle = payload.optBoolean("shuffle", false),
                speed = payload.optDouble("speed", 1.0).toFloat(),
                pitch = payload.optDouble("pitch", 1.0).toFloat()
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

    private data class PendingPlaylist(
        val songs: List<Song>,
        val startIndex: Int
    )

    private fun MediaItem.toSong(): Song {
        val metadata = mediaMetadata
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
        const val PLAYBACK_PREFS = "ella_playback_state"
        const val KEY_QUEUE = "queue"
    }
}
