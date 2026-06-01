package com.ella.music.player

import android.app.PendingIntent
import android.app.NotificationManager
import android.os.Build
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Timeline
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.ella.music.R
import com.ella.music.MainActivity
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaylistStore
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.os.Bundle

class PlaybackService : MediaLibraryService() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val LIBRARY_ROOT_ID = "ella_music_root"
        private const val LIBRARY_QUEUE_ID = "ella_music_current_queue"
        const val ACTION_TOGGLE_FAVORITE = "com.ella.music.action.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.ella.music.action.TOGGLE_SHUFFLE"
        private const val TIMING_TAG = "EllaPlaybackTiming"
    }

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var notificationProvider: NoArtworkMediaNotificationProvider
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var previousButtonAction = SettingsManager.PREVIOUS_BUTTON_PREVIOUS

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        notificationProvider = NoArtworkMediaNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)
        val settingsManager = SettingsManager(this)
        var webDavConfig = currentWebDavConfig(settingsManager)
        previousButtonAction = runBlocking(Dispatchers.IO) {
            settingsManager.previousButtonAction.first()
        }
        val httpDataSourceFactory = OkHttpDataSource.Factory(
            WebDavClient.newAuthenticatedOkHttpClient { webDavConfig }
        )
        serviceScope.launch {
            settingsManager.previousButtonAction.collect { action ->
                previousButtonAction = action.coerceIn(
                    SettingsManager.PREVIOUS_BUTTON_PREVIOUS,
                    SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT
                )
            }
        }
        serviceScope.launch {
            combine(
                settingsManager.webDavUrl,
                settingsManager.webDavUsername,
                settingsManager.webDavPassword
            ) { url, username, password ->
                WebDavConfig(url = url, username = username, password = password)
            }.collect { config ->
                webDavConfig = config
            }
        }
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val decoderMode = runBlocking(Dispatchers.IO) {
            settingsManager.decoderMode.first()
        }
        val handleAudioFocus = runBlocking(Dispatchers.IO) {
            !settingsManager.audioFocusDisabled.first()
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(
                when (decoderMode) {
                    1 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
        AppLogStore.info(this, TAG, "Decoder mode=${decoderMode.decoderModeLabel()}")

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        PlaybackAudioSession.update(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackAudioSession.update(audioSessionId)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                notifyLibraryChanged(player.mediaItemCount)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TIMING_TAG, "service media transition reason=$reason mediaId=${mediaItem?.mediaId}")
                updateMediaButtonPreferences()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.d(TIMING_TAG, "player state BUFFERING mediaId=${player.currentMediaItem?.mediaId}")
                    Player.STATE_READY -> Log.d(TIMING_TAG, "player state READY mediaId=${player.currentMediaItem?.mediaId}")
                    Player.STATE_ENDED -> Log.d(TIMING_TAG, "player state ENDED mediaId=${player.currentMediaItem?.mediaId}")
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateMediaButtonPreferences()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateMediaButtonPreferences()
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            RepeatOneLockingPlayer(player) { previousButtonAction },
            EllaLibrarySessionCallback(this)
        )
            .setSessionActivity(pendingIntent)
            .build()

        updateMediaButtonPreferences()

        Log.i(TAG, "PlaybackService created")
        AppLogStore.info(this, TAG, "PlaybackService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlaybackAudioSession.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun launchServiceJob(block: suspend () -> Unit) {
        serviceScope.launch { block() }
    }

    fun handleNotificationCustomAction(action: String): Boolean {
        return when (action) {
            ACTION_TOGGLE_SHUFFLE -> {
                AppLogStore.info(this, TAG, "NotificationAction playback mode clicked")
                mediaSession?.player?.let { player ->
                    player.cycleNotificationPlaybackMode()
                }
                updateMediaButtonPreferences()
                notificationProvider.refresh()
                true
            }

            ACTION_TOGGLE_FAVORITE -> {
                AppLogStore.info(this, TAG, "NotificationAction favorite clicked")
                val song = mediaSession?.player?.currentMediaItem?.toSongFromMediaItemExtras()
                if (song == null) {
                    AppLogStore.warn(this, TAG, "NotificationAction currentMediaItem cannot restore Song")
                    return true
                }
                serviceScope.launch {
                    val added = PlaylistStore.getInstance(this@PlaybackService).toggleFavorite(song)
                    AppLogStore.info(
                        this@PlaybackService,
                        TAG,
                        "NotificationAction favorite toggled added=$added"
                    )
                    updateMediaButtonPreferences()
                    notificationProvider.refresh()
                }
                true
            }

            else -> false
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateMediaButtonPreferences() {
        val session = mediaSession ?: return
        val player = session.player

        val currentSong = player.currentMediaItem?.toSongFromMediaItemExtras()
        val isFavorite = currentSong?.let {
            PlaylistStore.getInstance(this).isFavorite(it)
        } == true

        val playbackModeAction = player.notificationPlaybackModeAction()

        session.setMediaButtonPreferences(
            ImmutableList.of(
                CommandButton.Builder()
                    .setDisplayName(if (isFavorite) getString(R.string.common_unfavorite) else getString(R.string.common_favorite))
                    .setIconResId(
                        if (isFavorite) {
                            R.drawable.ic_notification_favorite_filled
                        } else {
                            R.drawable.ic_notification_favorite
                        }
                    )
                    .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    .build(),

                CommandButton.Builder()
                    .setDisplayName(getString(R.string.common_previous))
                    .setIconResId(R.drawable.ic_skip_previous)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build(),

                CommandButton.Builder()
                    .setDisplayName(if (player.isPlaying) getString(R.string.common_pause) else getString(R.string.common_play))
                    .setIconResId(
                        if (player.isPlaying) {
                            R.drawable.ic_player_pause
                        } else {
                            R.drawable.ic_player_play
                        }
                    )
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .build(),

                CommandButton.Builder()
                    .setDisplayName(getString(R.string.common_next))
                    .setIconResId(R.drawable.ic_skip_next)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build(),

                CommandButton.Builder()
                    .setDisplayName(playbackModeAction.title)
                    .setIconResId(playbackModeAction.icon)
                    .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                    .build()
            )
        )
    }

    private data class MediaButtonPlaybackModeAction(
        val icon: Int,
        val title: String
    )

    private fun Player.notificationPlaybackModeAction(): MediaButtonPlaybackModeAction {
        return when {
            shuffleModeEnabled -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_notification_shuffle,
                title = getString(R.string.notification_action_shuffle)
            )

            repeatMode == Player.REPEAT_MODE_ONE -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_repeat_one,
                title = getString(R.string.notification_action_repeat_one)
            )

            repeatMode == Player.REPEAT_MODE_ALL -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_repeat,
                title = getString(R.string.notification_action_repeat_all)
            )

            else -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_playback_order,
                title = getString(R.string.notification_action_order)
            )
        }
    }

    private fun Player.cycleNotificationPlaybackMode() {
        when {
            shuffleModeEnabled -> {
                shuffleModeEnabled = false
                repeatMode = Player.REPEAT_MODE_OFF
            }

            repeatMode == Player.REPEAT_MODE_OFF -> {
                shuffleModeEnabled = false
                repeatMode = Player.REPEAT_MODE_ALL
            }

            repeatMode == Player.REPEAT_MODE_ALL -> {
                shuffleModeEnabled = false
                repeatMode = Player.REPEAT_MODE_ONE
            }

            else -> {
                repeatMode = Player.REPEAT_MODE_ALL
                shuffleModeEnabled = true
            }
        }
    }

    private fun currentWebDavConfig(settingsManager: SettingsManager): WebDavConfig {
        return runBlocking(Dispatchers.IO) {
            WebDavConfig(
                url = settingsManager.webDavUrl.first(),
                username = settingsManager.webDavUsername.first(),
                password = settingsManager.webDavPassword.first()
            )
        }
    }

    private fun Int.decoderModeLabel(): String = when (this) {
        0 -> "system"
        1 -> "ffmpeg-prefer"
        2 -> "auto-system-first"
        else -> "unknown"
    }

    private fun libraryRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(LIBRARY_ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setDisplayTitle(getString(R.string.app_name))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                    .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun currentQueueFolderItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(LIBRARY_QUEUE_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.notification_current_queue))
                    .setDisplayTitle(getString(R.string.notification_current_queue))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setFolderType(MediaMetadata.FOLDER_TYPE_TITLES)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun currentQueueItems(): List<MediaItem> {
        val player = mediaSession?.player ?: return emptyList()
        return List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).buildUpon()
                .setMediaMetadata(
                    player.getMediaItemAt(index).mediaMetadata.buildUpon()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
    }

    private fun notifyLibraryChanged(itemCount: Int) {
        mediaSession?.notifyChildrenChanged(LIBRARY_ROOT_ID, 1, null)
        mediaSession?.notifyChildrenChanged(LIBRARY_QUEUE_ID, itemCount, null)
    }

    private class EllaLibrarySessionCallback(
        private val service: PlaybackService
    ) : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val handled = service.handleNotificationCustomAction(customCommand.customAction)
            val result = if (handled) {
                SessionResult(SessionResult.RESULT_SUCCESS)
            } else {
                SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            }
            return Futures.immediateFuture(result)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(service.libraryRootItem(), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                LIBRARY_ROOT_ID -> service.libraryRootItem()
                LIBRARY_QUEUE_ID -> service.currentQueueFolderItem()
                else -> service.currentQueueItems().firstOrNull { it.mediaId == mediaId }
            }
            return Futures.immediateFuture(
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = when (parentId) {
                LIBRARY_ROOT_ID -> listOf(service.currentQueueFolderItem())
                LIBRARY_QUEUE_ID -> service.currentQueueItems()
                else -> return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(children.page(page, pageSize), params))
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
            if (page < 0 || pageSize <= 0) return this
            val fromIndex = page * pageSize
            if (fromIndex >= size) return emptyList()
            return subList(fromIndex, minOf(fromIndex + pageSize, size))
        }
    }

    @OptIn(UnstableApi::class)
    private class RepeatOneLockingPlayer(
        player: Player,
        private val previousButtonActionProvider: () -> Int
    ) : ForwardingPlayer(player) {
        override fun seekToNextMediaItem() {
            Log.d(TIMING_TAG, "skipNext command received mediaId=${currentMediaItem?.mediaId}")
            if (!seekAdjacentMediaItemInRepeatOne(1)) {
                Log.d(TIMING_TAG, "seekToNext called")
                super.seekToNextMediaItem()
            }
        }

        override fun seekToNext() {
            Log.d(TIMING_TAG, "skipNext command received mediaId=${currentMediaItem?.mediaId}")
            if (!seekAdjacentMediaItemInRepeatOne(1)) {
                Log.d(TIMING_TAG, "seekToNext called")
                super.seekToNext()
            }
        }

        override fun seekToPreviousMediaItem() {
            Log.d(TIMING_TAG, "skipPrevious command received mediaId=${currentMediaItem?.mediaId}")
            if (!restartCurrentFromPreviousButton() && !seekAdjacentMediaItemInRepeatOne(-1)) {
                Log.d(TIMING_TAG, "seekToPrevious called")
                super.seekToPreviousMediaItem()
            }
        }

        override fun seekToPrevious() {
            Log.d(TIMING_TAG, "skipPrevious command received mediaId=${currentMediaItem?.mediaId}")
            if (!restartCurrentFromPreviousButton() && !seekAdjacentMediaItemInRepeatOne(-1)) {
                Log.d(TIMING_TAG, "seekToPrevious called")
                super.seekToPrevious()
            }
        }

        private fun restartCurrentFromPreviousButton(): Boolean {
            if (previousButtonActionProvider() != SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT) return false
            if (currentPosition < SettingsManager.PREVIOUS_REPLAY_THRESHOLD_MS) return false
            val index = currentMediaItemIndex
            if (mediaItemCount <= 0 || index !in 0 until mediaItemCount) return false
            seekToDefaultPosition(index)
            play()
            return true
        }

        private fun restartCurrentInRepeatOne(): Boolean {
            if (repeatMode != Player.REPEAT_MODE_ONE) return false
            val index = currentMediaItemIndex
            if (mediaItemCount <= 0 || index !in 0 until mediaItemCount) return false
            seekToDefaultPosition(index)
            play()
            return true
        }

        private fun seekAdjacentMediaItemInRepeatOne(offset: Int): Boolean {
            if (repeatMode != Player.REPEAT_MODE_ONE) return false
            val index = currentMediaItemIndex
            if (mediaItemCount <= 0 || index !in 0 until mediaItemCount) return false
            val targetIndex = if (mediaItemCount == 1) {
                index
            } else {
                Math.floorMod(index + offset, mediaItemCount)
            }
            seekToDefaultPosition(targetIndex)
            play()
            return true
        }
    }

    private class NoArtworkMediaNotificationProvider(
        private val service: PlaybackService
    ) : MediaNotification.Provider {
        private companion object {
            const val NOTIFICATION_ID = 1001
            const val CHANNEL_ID = "ella_music_playback"
            const val FLAG_ALWAYS_SHOW_TICKER_FALLBACK = 0x1000000
            const val FLAG_ONLY_UPDATE_TICKER_FALLBACK = 0x2000000
            const val LARGE_ICON_MAX_SIZE = 512
        }
        private data class PlaybackModeAction(
            val icon: Int,
            val title: String
        )

        private val largeIconCache = object : LruCache<String, Bitmap>(6 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
        }
        private var lastMediaSession: MediaSession? = null
        private var lastMediaButtonPreferences: ImmutableList<CommandButton>? = null
        private var lastActionFactory: MediaNotification.ActionFactory? = null
        private var lastCallback: MediaNotification.Provider.Callback? = null

        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            lastMediaSession = mediaSession
            lastMediaButtonPreferences = mediaButtonPreferences
            lastActionFactory = actionFactory
            lastCallback = onNotificationChangedCallback
            PlaybackTickerState.setRefreshCallback {
                onNotificationChangedCallback.onNotificationChanged(
                    createNotification(
                        mediaSession,
                        mediaButtonPreferences,
                        actionFactory,
                        onNotificationChangedCallback
                    )
                )
            }
            ensureChannel()
            val player = mediaSession.player
            val metadata = player.mediaMetadata
            val tickerPayload = PlaybackTickerState.current()
            val largeIcon = resolveLargeIcon(metadata)
            val builder = NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flyme_ticker)
                .setLargeIcon(largeIcon)
                .setContentTitle(metadata.title?.takeIf { it.isNotBlank() } ?: service.getString(R.string.app_name))
                .setContentText(metadata.artist?.takeIf { it.isNotBlank() } ?: metadata.albumTitle ?: "")
                .setTicker(tickerPayload?.text)
                .setContentIntent(mediaSession.sessionActivity)
                .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            val compactIndices = mutableListOf<Int>()
            var actionCount = 0
            fun addAction(command: Int, icon: Int, title: String, compact: Boolean = true) {
                if (!player.isCommandAvailable(command)) return
                val index = actionCount++
                builder.addAction(
                    actionFactory.createMediaAction(
                        mediaSession,
                        IconCompat.createWithResource(service, icon),
                        title,
                        command
                    )
                )
                if (compact) compactIndices += index
            }

            fun addCustomAction(action: String, icon: Int, title: String, compact: Boolean = false) {
                val index = actionCount++
                builder.addAction(
                    actionFactory.createCustomAction(
                        mediaSession,
                        IconCompat.createWithResource(service, icon),
                        title,
                        action,
                        Bundle.EMPTY
                    )
                )
                if (compact) compactIndices += index
            }

            val currentSong = player.currentMediaItem?.toSongFromMediaItemExtras()
            val isFavorite = currentSong?.let {
                PlaylistStore.getInstance(service).isFavorite(it)
            } == true
            val playbackModeAction = player.playbackModeAction()

            addCustomAction(
                ACTION_TOGGLE_FAVORITE,
                if (isFavorite) R.drawable.ic_notification_favorite_filled else R.drawable.ic_notification_favorite,
                if (isFavorite) service.getString(R.string.common_unfavorite) else service.getString(R.string.common_favorite),
                compact = false
            )

            addAction(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                R.drawable.ic_skip_previous,
                service.getString(R.string.common_previous)
            )

            addAction(
                Player.COMMAND_PLAY_PAUSE,
                if (player.isPlaying) {
                    R.drawable.ic_player_pause
                } else {
                    R.drawable.ic_player_play
                },
                if (player.isPlaying) service.getString(R.string.common_pause) else service.getString(R.string.common_play)
            )

            addAction(
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                R.drawable.ic_skip_next,
                service.getString(R.string.common_next)
            )

            addCustomAction(
                ACTION_TOGGLE_SHUFFLE,
                playbackModeAction.icon,
                playbackModeAction.title,
                compact = false
            )

            val style = MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(*compactIndices.toIntArray())
            builder.setStyle(style)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            }
            val notification = builder.build()
            Log.d(TIMING_TAG, "notification update mediaId=${player.currentMediaItem?.mediaId}")
            if (tickerPayload != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    notification.extras.putBoolean("ticker_icon_switch", false)
                    notification.extras.putInt("ticker_icon", R.drawable.ic_flyme_ticker)
                    notification.extras.putString("ticker_text", tickerPayload.text)
                    notification.extras.putString("lyric", tickerPayload.text)
                    tickerPayload.translation?.let { notification.extras.putString("ticker_translation", it) }
                }
                notification.flags = notification.flags or FLAG_ALWAYS_SHOW_TICKER_FALLBACK
                notification.flags = notification.flags or FLAG_ONLY_UPDATE_TICKER_FALLBACK
            }
            return MediaNotification(NOTIFICATION_ID, notification)
        }

        fun refresh() {
            val mediaSession = lastMediaSession ?: return
            val mediaButtonPreferences = lastMediaButtonPreferences ?: return
            val actionFactory = lastActionFactory ?: return
            val callback = lastCallback ?: return
            callback.onNotificationChanged(
                createNotification(
                    mediaSession,
                    mediaButtonPreferences,
                    actionFactory,
                    callback
                )
            )
        }

        private fun Player.playbackModeAction(): PlaybackModeAction {
            return when {
                shuffleModeEnabled -> PlaybackModeAction(
                icon = R.drawable.ic_notification_shuffle,
                    title = service.getString(R.string.notification_action_shuffle)
                )

                repeatMode == Player.REPEAT_MODE_ONE -> PlaybackModeAction(
                    icon = R.drawable.ic_repeat_one,
                    title = service.getString(R.string.notification_action_repeat_one)
                )

                repeatMode == Player.REPEAT_MODE_ALL -> PlaybackModeAction(
                    icon = R.drawable.ic_repeat,
                    title = service.getString(R.string.notification_action_repeat_all)
                )

                else -> PlaybackModeAction(
                    icon = R.drawable.ic_playback_order,
                    title = service.getString(R.string.notification_action_order)
                )
            }
        }

        private fun resolveLargeIcon(metadata: MediaMetadata): Bitmap? {
            metadata.artworkData?.takeIf { it.isNotEmpty() }?.let { data ->
                val key = "data:${data.contentHashCode()}:${data.size}"
                largeIconCache.get(key)?.let { return it }
                decodeArtworkData(data)?.also {
                    largeIconCache.put(key, it)
                    return it
                }
            }

            val uri = metadata.artworkUri ?: return defaultLargeIcon()
            if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                return defaultLargeIcon()
            }
            val key = "uri:$uri"
            largeIconCache.get(key)?.let { return it }
            return decodeArtworkUri(uri)
                ?.also { largeIconCache.put(key, it) }
                ?: defaultLargeIcon()
        }

        private fun defaultLargeIcon(): Bitmap {
            val key = "default"
            largeIconCache.get(key)?.let { return it }
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(
                        android.graphics.Color.rgb(94, 155, 255),
                        android.graphics.Color.rgb(62, 99, 216),
                        android.graphics.Color.rgb(32, 42, 104)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.argb(42, 255, 255, 255)
            canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.34f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.035f
            paint.color = android.graphics.Color.argb(66, 255, 255, 255)
            canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.24f, paint)
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.argb(36, 0, 0, 0)
            canvas.drawCircle(size * 0.52f, size * 0.50f, size * 0.06f, paint)
            largeIconCache.put(key, bitmap)
            return bitmap
        }

        private fun decodeArtworkData(data: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = bounds.notificationArtworkSampleSize()
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return runCatching {
                BitmapFactory.decodeByteArray(data, 0, data.size, options)?.centerCropSquare()
            }.getOrNull()
        }

        private fun decodeArtworkUri(uri: Uri): Bitmap? {
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                service.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = bounds.notificationArtworkSampleSize()
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                service.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }?.centerCropSquare()
            }.getOrNull()
        }

        private fun BitmapFactory.Options.notificationArtworkSampleSize(): Int {
            var sample = 1
            while (outWidth / sample > LARGE_ICON_MAX_SIZE || outHeight / sample > LARGE_ICON_MAX_SIZE) {
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }

        private fun Bitmap.centerCropSquare(): Bitmap {
            if (width == height) return this
            val size = minOf(width, height)
            val x = ((width - size) / 2).coerceAtLeast(0)
            val y = ((height - size) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(this, x, y, size, size)
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle
        ): Boolean {
            return service.handleNotificationCustomAction(action)
        }

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(
                CHANNEL_ID,
                service.getString(R.string.playback_service_notification_channel)
            )
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = service.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    service.getString(R.string.playback_service_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
