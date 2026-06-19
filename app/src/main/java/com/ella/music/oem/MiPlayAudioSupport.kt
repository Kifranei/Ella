package com.ella.music.oem

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

internal object MiPlayAudioSupport {
    private const val TAG = "MiPlayAudioSupport"
    private const val ACTION_MIPLAY_DETAIL = "miui.intent.action.ACTIVITY_MIPLAY_DETAIL"
    private const val AUDIO_RECORD_CLASS = "miui.media.MiuiAudioPlaybackRecorder"
    private const val PACKAGE_NAME = "com.milink.service"
    private const val SERVICE_NAME = "com.miui.miplay.audio.service.CoreService"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val WHITE_TARGET = "com.milink.service:hide_foreground"
    private const val FOREGROUND_NOTIFICATION_WHITELIST = "system_foreground_notification_whitelist"

    fun openMiPlayDetailIfSupported(context: Context): Boolean {
        if (!supportMiPlay(context)) return false
        return runCatching {
            context.startActivity(
                Intent(ACTION_MIPLAY_DETAIL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrElse { error ->
            Log.w(TAG, "Failed to open MiPlay detail panel", error)
            false
        }
    }

    fun supportMiPlay(context: Context): Boolean {
        if (!isXiaomiFamilyDevice()) return false
        return runCatching {
            context.packageManager.getServiceInfoCompat(
                ComponentName(PACKAGE_NAME, SERVICE_NAME)
            )
            context.classLoader.loadClass(AUDIO_RECORD_CLASS)

            !isInternationalBuild() &&
                systemUIReady(context) &&
                notificationReady(context)
        }.getOrDefault(false)
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer in setOf("xiaomi", "redmi", "poco") ||
            brand in setOf("xiaomi", "redmi", "poco")
    }

    private fun isInternationalBuild(): Boolean =
        runCatching {
            val clazz = Class.forName("miui.os.Build")
            val field = clazz.getField("IS_INTERNATIONAL_BUILD")
            field.isAccessible = true
            field.getBoolean(null)
        }.getOrDefault(false)

    private fun systemUIReady(context: Context): Boolean {
        val intent = Intent(ACTION_MIPLAY_DETAIL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) != null
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun notificationReady(context: Context): Boolean =
        runCatching {
            val systemUiAppInfo = context.packageManager.getApplicationInfoCompat(SYSTEM_UI_PACKAGE)
            val resources = context.packageManager.getResourcesForApplication(systemUiAppInfo)
            val identifier = @SuppressLint("DiscouragedApi") resources.getIdentifier(
                FOREGROUND_NOTIFICATION_WHITELIST,
                "array",
                SYSTEM_UI_PACKAGE
            )
            identifier > 0 && resources.getStringArray(identifier).contains(WHITE_TARGET)
        }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun PackageManager.getServiceInfoCompat(componentName: ComponentName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getServiceInfo(
                componentName,
                PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            getServiceInfo(componentName, PackageManager.MATCH_ALL)
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getApplicationInfoCompat(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            getApplicationInfo(packageName, 0)
        }
}
