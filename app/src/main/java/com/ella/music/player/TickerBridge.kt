package com.ella.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ella.music.R

class TickerBridge(private val context: Context) {

    companion object {
        private const val TAG = "TickerBridge"
        private const val CHANNEL_ID = "ella_ticker_lyrics"
        private const val NOTIFICATION_ID = 0x454c4c41
        private const val ACTION_SEND_LYRIC = "com.meizu.flyme.ticker.ACTION_SEND"
        private const val ACTION_CLEAR_LYRIC = "com.meizu.flyme.ticker.ACTION_CLEAR"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    private var enabled = true
    private var lastText: String? = null
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) clearLyric()
    }

    fun isEnabled() = enabled

    fun sendLyric(text: String?) {
        if (!enabled) return
        if (text == lastText) return
        lastText = text

        try {
            if (text.isNullOrEmpty()) {
                clearLyric()
                return
            }
            val intent = Intent(ACTION_SEND_LYRIC).apply {
                putExtra("ticker_text", text)
                putExtra("lyric", text)
                putExtra("text", text)
                putExtra("content", text)
                putExtra("ticker_package", context.packageName)
                putExtra("package", context.packageName)
                putExtra("ticker_app_name", "Ella Music")
                putExtra("app_name", "Ella Music")
            }
            sendFlymeBroadcast(intent)
            postTickerNotification(text)
            Log.d(TAG, "Ticker lyric sent: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ticker lyric", e)
        }
    }

    fun clearLyric() {
        lastText = null
        try {
            val intent = Intent(ACTION_CLEAR_LYRIC).apply {
                putExtra("ticker_package", context.packageName)
                putExtra("package", context.packageName)
            }
            sendFlymeBroadcast(intent)
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun sendFlymeBroadcast(intent: Intent) {
        context.sendBroadcast(intent)
        context.sendBroadcast(Intent(intent).setPackage(SYSTEM_UI_PACKAGE))
    }

    private fun postTickerNotification(text: String) {
        ensureNotificationChannel()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("Ella Music")
            .setContentText(text)
            .setTicker(text)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ticker 歌词",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于向支持 AOSP ticker 的系统状态栏推送歌词"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
