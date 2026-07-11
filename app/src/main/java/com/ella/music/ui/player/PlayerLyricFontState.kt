package com.ella.music.ui.player

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.ScriptFontPaths
import com.ella.music.ui.settings.SYSTEM_FONT_PATH

internal data class PlayerLyricFontState(
    val fontFamily: FontFamily?,
    val fontPath: String,
    val fontWeight: FontWeight,
    val fontScale: Float,
    val secondaryFontScale: Float,
    val compactPrimaryTextSizeSp: Float,
    val compactSecondaryTextSizeSp: Float,
    val widePrimaryTextSizeSp: Float,
    val wideSecondaryTextSizeSp: Float,
    val shareTypeface: Typeface?
)

@Composable
internal fun rememberPlayerLyricFontState(
    context: Context,
    settingsManager: SettingsManager
): PlayerLyricFontState {
    val lyricFontPath by settingsManager.lyricFontPath.collectAsState(initial = "")
    val lyricWesternFontPath by settingsManager.lyricWesternFontPath.collectAsState(initial = "")
    val lyricCjkFontPath by settingsManager.lyricCjkFontPath.collectAsState(initial = "")
    val lyricFontWeightValue by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    val lyricFontScaleValue by settingsManager.lyricFontScale.collectAsState(initial = 100)
    val lyricSecondaryFontScaleValue by settingsManager.lyricSecondaryFontScale.collectAsState(initial = 100)
    val lyricCompactPrimaryTextSizeValue by settingsManager.lyricCompactPrimaryTextSize.collectAsState(
        initial = SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP
    )
    val lyricCompactSecondaryTextSizeValue by settingsManager.lyricCompactSecondaryTextSize.collectAsState(
        initial = SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP
    )
    val lyricWidePrimaryTextSizeValue by settingsManager.lyricWidePrimaryTextSize.collectAsState(
        initial = SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP
    )
    val lyricWideSecondaryTextSizeValue by settingsManager.lyricWideSecondaryTextSize.collectAsState(
        initial = SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP
    )
    val lyricShareUseLyricFont by settingsManager.lyricShareUseLyricFont.collectAsState(initial = false)
    val lyricFontApplyToPage by settingsManager.lyricFontApplyToPage.collectAsState(initial = true)
    val bundledInterPath = remember(context) { ensureBundledInterPath(context) }
    val bundledCjkPath = remember(context) { ensureBundledMiSansSemiboldPath(context) }
    val defaultCjkPath = remember(bundledCjkPath) {
        bundledCjkPath.takeIf { !isXiaomiFamilyPlayerDevice() }.orEmpty()
    }
    val migratedLegacyCjkPath = remember(lyricFontPath) {
        lyricFontPath.takeUnless { it.contains("Inter", ignoreCase = true) }.orEmpty()
    }
    val effectiveWesternPath = lyricWesternFontPath.ifBlank { bundledInterPath }
    val effectiveCjkPath = lyricCjkFontPath.ifBlank {
        migratedLegacyCjkPath.ifBlank { defaultCjkPath.ifBlank { SYSTEM_FONT_PATH } }
    }
    val effectiveLyricFontPath = remember(effectiveWesternPath, effectiveCjkPath) {
        ScriptFontPaths(effectiveWesternPath, effectiveCjkPath).encode()
    }
    val effectiveLyricFontWeightValue = lyricFontWeightValue
    val lyricFontFamily = remember(effectiveLyricFontPath, effectiveLyricFontWeightValue) {
        effectiveLyricFontPath.toPlayerLyricFontFamily(
            weight = effectiveLyricFontWeightValue,
            italic = false
        )
    }
    val lyricFontWeight = remember(effectiveLyricFontWeightValue) {
        FontWeight(effectiveLyricFontWeightValue.coerceIn(100, 900))
    }
    val lyricFontScale = remember(lyricFontScaleValue) {
        lyricFontScaleValue.coerceIn(
            SettingsManager.LYRIC_FONT_SCALE_MIN,
            SettingsManager.LYRIC_FONT_SCALE_ULTRA_WIDE_MAX
        ) / 100f
    }
    val lyricSecondaryFontScale = remember(lyricSecondaryFontScaleValue) {
        lyricSecondaryFontScaleValue.coerceIn(
            SettingsManager.LYRIC_SECONDARY_FONT_SCALE_MIN,
            SettingsManager.LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX
        ) / 100f
    }
    val lyricCompactPrimaryTextSize = remember(lyricCompactPrimaryTextSizeValue) {
        lyricCompactPrimaryTextSizeValue.coerceIn(
            SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP,
            SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP
        ).toFloat()
    }
    val lyricCompactSecondaryTextSize = remember(lyricCompactSecondaryTextSizeValue) {
        lyricCompactSecondaryTextSizeValue.coerceIn(
            SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP,
            SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP
        ).toFloat()
    }
    val lyricWidePrimaryTextSize = remember(lyricWidePrimaryTextSizeValue) {
        lyricWidePrimaryTextSizeValue.coerceIn(
            SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP,
            SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP
        ).toFloat()
    }
    val lyricWideSecondaryTextSize = remember(lyricWideSecondaryTextSizeValue) {
        lyricWideSecondaryTextSizeValue.coerceIn(
            SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP,
            SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP
        ).toFloat()
    }
    val lyricShareTypeface = remember(lyricShareUseLyricFont, effectiveLyricFontPath, effectiveLyricFontWeightValue) {
        if (lyricShareUseLyricFont) {
            effectiveLyricFontPath.toPlayerLyricTypeface(effectiveLyricFontWeightValue)
        } else {
            null
        }
    }

    return PlayerLyricFontState(
        // fontFamily drives the PlayerSongMetaText group (song title + artist + annotation) on
        // the player/lyrics pages. fontPath drives the lyric body (SmoothLyricView). Both honour
        // the "apply font to page" toggle — when off, the entire page (title, artist, AND lyrics)
        // falls back to the global/system font so the switch is actually effective.
        fontFamily = if (lyricFontApplyToPage) lyricFontFamily else null,
        fontPath = if (lyricFontApplyToPage) effectiveLyricFontPath else "",
        fontWeight = lyricFontWeight,
        fontScale = lyricFontScale,
        secondaryFontScale = lyricSecondaryFontScale,
        compactPrimaryTextSizeSp = lyricCompactPrimaryTextSize,
        compactSecondaryTextSizeSp = lyricCompactSecondaryTextSize,
        widePrimaryTextSizeSp = lyricWidePrimaryTextSize,
        wideSecondaryTextSizeSp = lyricWideSecondaryTextSize,
        shareTypeface = lyricShareTypeface
    )
}

internal fun PlayerLyricFontState.primaryTextSizeSp(profile: PlayerLyricLayoutProfile): Float =
    when (profile) {
        PlayerLyricLayoutProfile.Wide -> widePrimaryTextSizeSp
        PlayerLyricLayoutProfile.Compact -> compactPrimaryTextSizeSp
    }

internal fun PlayerLyricFontState.secondaryTextSizeSp(profile: PlayerLyricLayoutProfile): Float =
    when (profile) {
        PlayerLyricLayoutProfile.Wide -> wideSecondaryTextSizeSp
        PlayerLyricLayoutProfile.Compact -> compactSecondaryTextSizeSp
    }
