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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ella.music.R
import com.ella.music.MainActivity
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials

class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(NoArtworkMediaNotificationProvider(this))
        val settingsManager = SettingsManager(this)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(currentWebDavHeaders(settingsManager))
        serviceScope.launch {
            combine(
                settingsManager.webDavUsername,
                settingsManager.webDavPassword
            ) { username, password ->
                if (username.isNotBlank() || password.isNotBlank()) {
                    mapOf("Authorization" to Credentials.basic(username, password, Charsets.UTF_8))
                } else {
                    emptyMap()
                }
            }.collect { headers ->
                httpDataSourceFactory.setDefaultRequestProperties(headers)
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
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        Log.i(TAG, "PlaybackService created")
        AppLogStore.info(this, TAG, "PlaybackService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
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

    private fun currentWebDavHeaders(settingsManager: SettingsManager): Map<String, String> {
        return runBlocking(Dispatchers.IO) {
            val username = settingsManager.webDavUsername.first()
            val password = settingsManager.webDavPassword.first()
            if (username.isNotBlank() || password.isNotBlank()) {
                mapOf("Authorization" to Credentials.basic(username, password, Charsets.UTF_8))
            } else {
                emptyMap()
            }
        }
    }

    private fun Int.decoderModeLabel(): String = when (this) {
        0 -> "system"
        1 -> "ffmpeg-prefer"
        2 -> "auto-system-first"
        else -> "unknown"
    }

    private class NoArtworkMediaNotificationProvider(
        private val service: PlaybackService
    ) : MediaNotification.Provider {
        private companion object {
            const val NOTIFICATION_ID = 1001
            const val CHANNEL_ID = "ella_music_playback"
            const val CHANNEL_NAME = "播放控制"
        }

        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            ensureChannel()
            val player = mediaSession.player
            val metadata = player.mediaMetadata
            val builder = NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flyme_ticker)
                .setContentTitle(metadata.title?.takeIf { it.isNotBlank() } ?: service.getString(R.string.app_name))
                .setContentText(metadata.artist?.takeIf { it.isNotBlank() } ?: metadata.albumTitle ?: "")
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
            return MediaNotification(NOTIFICATION_ID, builder.build())
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
