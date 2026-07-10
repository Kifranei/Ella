package com.ella.music.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {

    private const val DEFAULT_ALIAS = ".DefaultLauncherAlias"
    private const val ANIME_ALIAS = ".AnimeLauncherAlias"
    private const val BLACK_HAIR_ALIAS = ".BlackHairLauncherAlias"
    private const val LOLI_ALIAS = ".LoliLauncherAlias"

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
        setAliasEnabled(
            packageManager = packageManager,
            componentName = ComponentName(packageName, "$packageName$BLACK_HAIR_ALIAS"),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_BLACK_HAIR
        )
        setAliasEnabled(
            packageManager = packageManager,
            componentName = ComponentName(packageName, "$packageName$LOLI_ALIAS"),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_LOLI
        )
    }

    fun normalize(style: String?): String =
        when (style) {
            SettingsManager.APP_ICON_STYLE_ANIME -> SettingsManager.APP_ICON_STYLE_ANIME
            SettingsManager.APP_ICON_STYLE_BLACK_HAIR -> SettingsManager.APP_ICON_STYLE_BLACK_HAIR
            SettingsManager.APP_ICON_STYLE_LOLI -> SettingsManager.APP_ICON_STYLE_LOLI
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
