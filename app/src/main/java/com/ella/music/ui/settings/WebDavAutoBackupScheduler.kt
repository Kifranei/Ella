package com.ella.music.ui.settings

import android.content.Context
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object WebDavAutoBackupScheduler {
    private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L

    fun start(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        val settings = SettingsManager.getInstance(appContext)
        scope.launch {
            while (isActive) {
                runCatching { backupIfDue(appContext, settings) }
                    .onFailure { Log.w("WebDavAutoBackup", "Automatic backup failed", it) }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun backupIfDue(context: Context, settings: SettingsManager) {
        if (!settings.webDavAutoBackupEnabled.first()) return
        val now = System.currentTimeMillis()
        val intervalMs = settings.webDavAutoBackupIntervalHours.first() * 60L * 60L * 1000L
        if (now - settings.webDavAutoBackupLastAt.first() < intervalMs) return

        val baseUrl = settings.webDavBackupUrl.first().ifBlank { settings.webDavUrl.first() }.trim()
        if (baseUrl.isBlank()) return
        val config = WebDavConfig(
            url = baseUrl,
            username = settings.webDavBackupUsername.first().ifBlank { settings.webDavUsername.first() },
            password = settings.webDavBackupPassword.first().ifBlank { settings.webDavPassword.first() }
        )
        val path = settings.webDavBackupPath.first().trim().ifBlank { "halcyon_backup" }
        val targetUrl = "${baseUrl.trimEnd('/')}/$path/halcyon_backup_auto_latest.json"
        val backup = buildCompleteApplicationBackupJson(context).toString(2)
        WebDavClient.uploadFileFromString(targetUrl, config, backup)
        settings.setWebDavAutoBackupLastAt(now)
        Log.i("WebDavAutoBackup", "Automatic backup completed")
    }
}
