package com.ella.music.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ella.music.MainActivity

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
            componentName = launcherAliasComponent(packageName, DEFAULT_ALIAS),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_DEFAULT
        )
        setAliasEnabled(
            packageManager = packageManager,
            componentName = launcherAliasComponent(packageName, ANIME_ALIAS),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_ANIME
        )
        setAliasEnabled(
            packageManager = packageManager,
            componentName = launcherAliasComponent(packageName, BLACK_HAIR_ALIAS),
            enabled = normalizedStyle == SettingsManager.APP_ICON_STYLE_BLACK_HAIR
        )
        setAliasEnabled(
            packageManager = packageManager,
            componentName = launcherAliasComponent(packageName, LOLI_ALIAS),
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
        runCatching {
            // A repackaged build can have a different applicationId while the component class
            // remains in Halcyon's source namespace. Missing/rewritten aliases must never crash
            // Application.onCreate; icon switching simply becomes unavailable for that package.
            packageManager.getActivityInfo(componentName, PackageManager.MATCH_DISABLED_COMPONENTS)
            if (packageManager.getComponentEnabledSetting(componentName) != targetState) {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "Launcher alias unavailable: ${componentName.className}", error)
        }
    }

    private fun launcherAliasComponent(applicationId: String, aliasSuffix: String): ComponentName =
        ComponentName(applicationId, launcherAliasClassName(aliasSuffix))

    internal fun launcherAliasClassName(aliasSuffix: String): String =
        "${MainActivity::class.java.packageName}$aliasSuffix"

    private const val TAG = "AppIconManager"
}
