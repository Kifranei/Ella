package com.ella.music.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.ella.music.MainActivity
import com.ella.music.R

/** 4×1 compact playback widget. */
class PlaybackCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PlaybackWidgetUpdater.updateCompact(context, ids)
    }
}

/** 4×2 playback widget with the current song's larger identity block. */
class PlaybackExpandedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PlaybackWidgetUpdater.updateExpanded(context, ids)
    }
}

internal object PlaybackWidgetUpdater {
    private data class Snapshot(
        val title: String = "Halcyon",
        val artist: String = "点击播放以继续聆听",
        val isPlaying: Boolean = false
    )

    @Volatile
    private var snapshot = Snapshot()

    fun updateFromPlayer(context: Context, player: Player) {
        val metadata = player.mediaMetadata
        snapshot = Snapshot(
            title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Halcyon",
            artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
                ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
                ?: "正在播放",
            isPlaying = player.isPlaying
        )
        updateAll(context)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        updateCompact(context, manager.getAppWidgetIds(ComponentName(context, PlaybackCompactWidgetProvider::class.java)))
        updateExpanded(context, manager.getAppWidgetIds(ComponentName(context, PlaybackExpandedWidgetProvider::class.java)))
    }

    fun updateCompact(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val views = createRemoteViews(context, R.layout.widget_playback_compact)
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    fun updateExpanded(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val views = createRemoteViews(context, R.layout.widget_playback_expanded)
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    private fun createRemoteViews(context: Context, layoutId: Int): RemoteViews =
        RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.widget_title, snapshot.title)
            setTextViewText(R.id.widget_artist, snapshot.artist)
            setImageViewResource(
                R.id.widget_play_pause,
                if (snapshot.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
            )
            setOnClickPendingIntent(R.id.widget_root, mainActivityIntent(context))
            setOnClickPendingIntent(R.id.widget_previous, serviceIntent(context, PlaybackService.ACTION_WIDGET_PREVIOUS, 1))
            setOnClickPendingIntent(R.id.widget_play_pause, serviceIntent(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE, 2))
            setOnClickPendingIntent(R.id.widget_next, serviceIntent(context, PlaybackService.ACTION_WIDGET_NEXT, 3))
        }

    private fun mainActivityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
