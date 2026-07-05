package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.ui.components.SmoothLyricView
import top.yukonga.miuix.kmp.basic.Text

internal fun miniLyricsPreviewHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    compact: Boolean = false
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    // Single-line (e.g. Chinese only): keep a tall area and let the tighter line gap fit 5 lines.
    0, 1 -> if (compact) 150.dp else 186.dp
    2 -> if (compact) 154.dp else 202.dp
    3 -> if (compact) 168.dp else 220.dp
    else -> if (compact) 176.dp else 232.dp
}

/**
 * Height for the lyric preview in a cramped floating window: just enough for the current line
 * (plus its translation/pronunciation), so the transport controls below stay on-screen.
 */
internal fun miniLyricsCompactHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    0, 1 -> 40.dp
    2 -> 64.dp
    else -> 84.dp
}

@Composable
internal fun MiniLyricsPreview(
    songId: Long,
    songTitle: String,
    songArtist: String,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    currentPositionMs: Long,
    isPlaying: Boolean,
    fontPath: String = "",
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontScale: Float = 1f,
    secondaryFontScale: Float = 1f,
    lyricTextAlign: Int = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
    compact: Boolean = false,
    contentColor: Color = Color.White,
    onLineClick: (LyricLine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }.takeIf { it >= 0 }
        ?: return
    // When only the main line shows (e.g. Chinese with no translation/pronunciation), tighten the
    // line gap so the preview fits ~5 lines instead of ~4.
    val singleLinePreview = compact || (lyrics.getOrNull(safeIndex)
        ?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) <= 1
    // In a cramped floating window, shrink the type so long (e.g. English) lines fit the narrow
    // width instead of overflowing, and take less vertical room.
    val primarySizeSp = if (compact) 15.5f else 19f
    val secondarySizeSp = if (compact) 12.8f else 15.5f
    SmoothLyricView(
        songId = songId,
        songTitle = songTitle,
        songArtist = songArtist,
        lyrics = lyrics,
        currentIndex = safeIndex,
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        showTranslation = showTranslation,
        showPronunciation = showPronunciation,
        fontScale = 0.92f,
        fontPath = fontPath,
        fontWeight = fontWeight,
        lyricTextAlign = lyricTextAlign,
        primaryTextSizeSp = primarySizeSp,
        secondaryTextSizeSp = secondarySizeSp,
        secondaryFontScale = 1f,
        anchorOffsetRatio = -0.01f,
        topContentPadding = 0.dp,
        contentColor = contentColor,
        onLineClick = onLineClick,
        nonCurrentLineBlurEnabled = false,
        nonCurrentLineBlurDistance = Int.MAX_VALUE,
        lineAlphaAnimationsEnabled = false,
        autoScrollResumeEnabled = true,
        // The mini preview is tap-to-open only; don't let it scroll on drag.
        userScrollEnabled = false,
        lineGapDp = if (singleLinePreview) 4f else if (compact) 5f else 7f,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun MiniNoLyricsPreview(
    contentColor: Color,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(R.string.player_no_lyrics),
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 19.sp,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun LyricLine.hasMiniLyric(): Boolean {
    return !pronunciation.isNullOrBlank() ||
        text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !translation.isNullOrBlank() ||
        backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !backgroundTranslation.isNullOrBlank()
}

internal fun LyricLine.miniVisiblePartCount(
    showTranslation: Boolean,
    showPronunciation: Boolean
): Int {
    var count = 0
    if (showPronunciation && !pronunciation.isNullOrBlank()) count++
    if (text.isNotBlank() && !text.isMusicSymbolOnly()) count++
    if (showTranslation && !translation.isNullOrBlank()) count++
    if (!backgroundText.isNullOrBlank() && !backgroundText.isMusicSymbolOnly()) count++
    if (showTranslation && !backgroundTranslation.isNullOrBlank()) count++
    return count
}

internal fun String.isMusicSymbolOnly(): Boolean {
    val cleaned = trim()
    if (cleaned.isEmpty()) return true
    return cleaned.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♮', '♯', '☆', '★', '·', '.', '。', '…')
    }
}
