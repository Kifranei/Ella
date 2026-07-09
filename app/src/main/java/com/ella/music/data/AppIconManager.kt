package com.ella.music.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {

    private const val DEFAULT_ALIAS = ".DefaultLauncherAlias"
    private const val ANIME_ALIAS = ".AnimeLauncherAlias"

    fun apply(context: Context, style: String) {
        val normalizedStyle = normalize(style)
        val packageName = context.packageName
        val packageManager = context.packageManager

        setAliasEnabled(
            packageManager = packageManager,
            componentName = ComponentName(packageName, "$packageName$DEFAULT_ALIAS"),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_DEFAULT
        )
        setAliasEnabled(
            packageManager = packageManager,
            componentName = ComponentName(packageName, "$packageName$ANIME_ALIAS"),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_ANIME
        )
    }

    fun normalize(style: String?): String =
        when (style) {
            SettingsManager.APP_ICON_STYLE_ANIME -> SettingsManager.APP_ICON_STYLE_ANIME
            else -> SettingsManager.APP_ICON_STYLE_DEFAULT
        }

    private fun setAliasEnabled(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean
    ) {
        val targetState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(componentName) == targetState) return
        packageManager.setComponentEnabledSetting(
            componentName,
            targetState,
            PackageManager.DONT_KILL_APP
        )
    }
}
