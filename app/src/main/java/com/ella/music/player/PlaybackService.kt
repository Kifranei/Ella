package com.ella.music.player

import android.app.PendingIntent
import android.app.NotificationManager
import android.os.Build
import android.content.Intent
import android.util.Log
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
import com.ella.music.R
import com.ella.music.MainActivity
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
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

class PlaybackService : MediaLibraryService() {

    companion object {
        private const val TAG = "PlaybackService"
        private const val LIBRARY_ROOT_ID = "ella_music_root"
        private const val LIBRARY_QUEUE_ID = "ella_music_current_queue"
    }

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var previousButtonAction = SettingsManager.PREVIOUS_BUTTON_PREVIOUS

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(NoArtworkMediaNotificationProvider(this))
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
        PlaybackAudioSession.update(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackAudioSession.update(audioSessionId)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                notifyLibraryChanged(player.mediaItemCount)
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
                    .setTitle("当前播放")
                    .setDisplayTitle("当前播放")
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
            if (!seekAdjacentMediaItemInRepeatOne(1)) {
                super.seekToNextMediaItem()
            }
        }

        override fun seekToNext() {
            if (!seekAdjacentMediaItemInRepeatOne(1)) {
                super.seekToNext()
            }
        }

        override fun seekToPreviousMediaItem() {
            if (!restartCurrentFromPreviousButton() && !seekAdjacentMediaItemInRepeatOne(-1)) {
                super.seekToPreviousMediaItem()
            }
        }

        override fun seekToPrevious() {
            if (!restartCurrentFromPreviousButton() && !seekAdjacentMediaItemInRepeatOne(-1)) {
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
            const val CHANNEL_NAME = "播放控制"
            const val FLAG_ALWAYS_SHOW_TICKER_FALLBACK = 0x1000000
            const val FLAG_ONLY_UPDATE_TICKER_FALLBACK = 0x2000000
        }

        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
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
            val builder = NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flyme_ticker)
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

            addAction(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                androidx.media3.session.R.drawable.media3_icon_previous,
                "上一首"
            )
            addAction(
                Player.COMMAND_PLAY_PAUSE,
                if (player.isPlaying) androidx.media3.session.R.drawable.media3_icon_pause else androidx.media3.session.R.drawable.media3_icon_play,
                if (player.isPlaying) "暂停" else "播放"
            )
            addAction(
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                androidx.media3.session.R.drawable.media3_icon_next,
                "下一首"
            )

            val style = MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(*compactIndices.toIntArray())
            builder.setStyle(style)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            }
            val notification = builder.build()
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

        override fun handleCustomCommand(session: MediaSession, action: String, extras: android.os.Bundle): Boolean = false

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, CHANNEL_NAME)
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = service.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
