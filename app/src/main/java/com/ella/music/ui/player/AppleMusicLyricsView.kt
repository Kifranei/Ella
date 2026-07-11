package com.ella.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** A native, independently implemented focus-lyrics renderer. */
@Composable
internal fun AppleMusicLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    lyricTextAlign: Int,
    contentColor: Color,
    onLineClick: (LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            BasicText(
                text = "♪",
                style = TextStyle(fontSize = 28.sp, color = contentColor.copy(alpha = 0.58f), fontFamily = fontFamily)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val activeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    LaunchedEffect(activeIndex) {
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        // Negative offset positions the line safely below the header rather than underneath it.
        listState.animateScrollToItem(activeIndex, -(viewportHeight * 0.24f).toInt())
    }
    val defaultTextAlign = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 72.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics, key = { index, line -> "${line.timeMs}-$index" }) { index, line ->
            val duetActive = line.isDuetLine() && line.isActiveAt(currentPositionMs)
            AppleMusicLyricLine(
                line = line,
                active = index == activeIndex || duetActive,
                distance = (index - activeIndex).coerceIn(-4, 4),
                currentPositionMs = currentPositionMs,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontScale = fontScale,
                secondaryFontScale = secondaryFontScale,
                primaryTextSizeSp = primaryTextSizeSp,
                secondaryTextSizeSp = secondaryTextSizeSp,
                defaultTextAlign = defaultTextAlign,
                contentColor = contentColor,
                onClick = { onLineClick(line) },
                onDoubleClick = onLineDoubleClick,
                onLongClick = { onLineLongClick(line) }
            )
        }
    }
}

@Composable
private fun AppleMusicLyricLine(
    line: LyricLine,
    active: Boolean,
    distance: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    defaultTextAlign: TextAlign,
    contentColor: Color,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val textAlign = line.duetTextAlign(defaultTextAlign)
    val focus by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 310f),
        label = "appleLyricsFocus"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.91f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
        label = "appleLyricsScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else (0.24f - abs(distance) * 0.025f).coerceAtLeast(0.13f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 360f),
        label = "appleLyricsAlpha"
    )
    val westernLift = if (line.text.isPredominantlyWesternLyric()) -9f * focus else 0f
    val primaryStyle = TextStyle(
        fontSize = (primaryTextSizeSp * fontScale).sp,
        lineHeight = (primaryTextSizeSp * fontScale * 1.18f).sp,
        fontWeight = if (active) fontWeight else FontWeight.Bold,
        fontFamily = fontFamily,
        color = contentColor.copy(alpha = alpha),
        textAlign = textAlign,
        shadow = if (active) Shadow(
            color = contentColor.copy(alpha = 0.40f * focus),
            offset = Offset(0f, 5f),
            blurRadius = 24f
        ) else null
    )
    val secondaryStyle = TextStyle(
        fontSize = (secondaryTextSizeSp * fontScale * secondaryFontScale).sp,
        lineHeight = (secondaryTextSizeSp * fontScale * secondaryFontScale * 1.28f).sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = fontFamily,
        color = contentColor.copy(alpha = alpha * 0.74f),
        textAlign = textAlign
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = ((distance * -2f) + westernLift) * density
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (textAlign) {
                        TextAlign.End -> 1f
                        TextAlign.Center -> 0.5f
                        else -> 0f
                    },
                    pivotFractionY = 0.5f
                )
            }
            .pointerInput(line) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 2.dp),
        horizontalAlignment = when (textAlign) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        val pronunciation = line.pronunciation.orEmpty()
        if (showPronunciation && pronunciation.isNotBlank()) {
            BasicText(text = pronunciation, style = secondaryStyle, modifier = Modifier.fillMaxWidth())
        }
        TimedLyricText(
            text = line.text.ifBlank { line.backgroundText.orEmpty().ifBlank { "♪" } },
            words = line.words,
            positionMs = currentPositionMs,
            active = active,
            style = primaryStyle,
            contentColor = contentColor,
            modifier = Modifier.fillMaxWidth()
        )
        line.translation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
            BasicText(
                text = translation,
                style = secondaryStyle,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
            )
        }
        line.backgroundText?.trim()?.takeIf { it.isNotBlank() && line.text.isNotBlank() }?.let { background ->
            TimedLyricText(
                text = background,
                words = line.backgroundWords,
                positionMs = currentPositionMs,
                active = active,
                style = secondaryStyle.copy(color = contentColor.copy(alpha = alpha * 0.72f)),
                contentColor = contentColor,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
            )
            line.backgroundTranslation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
                BasicText(
                    text = translation,
                    style = secondaryStyle.copy(color = contentColor.copy(alpha = alpha * 0.62f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun TimedLyricText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    active: Boolean,
    style: TextStyle,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val timedWords = remember(text, words) { words.withDisplaySpacing(text) }
    if (timedWords.isEmpty()) {
        BasicText(text = text, style = style, modifier = modifier)
        return
    }
    // Keep the timed units as individual layout children. This is the same important distinction
    // as the smooth renderer: a long timed line breaks between singable units, not at arbitrary
    // glyphs, so highlighted and dim lines retain identical visual rows.
    FlowRow(
        modifier = modifier,
        horizontalArrangement = when (style.textAlign) {
            TextAlign.End -> Arrangement.End
            TextAlign.Center -> Arrangement.Center
            else -> Arrangement.Start
        },
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        timedWords.forEach { word ->
            val color = word.karaokeColor(
                positionMs = positionMs,
                active = active,
                baseColor = style.color,
                contentColor = contentColor
            )
            BasicText(text = word.text, style = style.copy(color = color))
        }
    }
}

private fun LyricWord.karaokeColor(
    positionMs: Long,
    active: Boolean,
    baseColor: Color,
    contentColor: Color
) : Color {
    if (!active) return baseColor
    val progress = ((positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    val dim = contentColor.copy(alpha = baseColor.alpha * 0.36f)
    return androidx.compose.ui.graphics.lerp(dim, contentColor.copy(alpha = baseColor.alpha), progress)
}

private fun List<LyricWord>.withDisplaySpacing(lineText: String): List<LyricWord> {
    if (isEmpty() || lineText.isBlank()) return emptyList()
    val result = mutableListOf<LyricWord>()
    var cursor = 0
    forEachIndexed { index, word ->
        if (word.text.isBlank() || word.endMs <= word.startMs) return@forEachIndexed
        val start = lineText.indexOf(word.text, cursor)
        if (start < 0) return emptyList()
        val end = start + word.text.length
        val nextStart = getOrNull(index + 1)?.text?.let { next -> lineText.indexOf(next, end) } ?: -1
        val suffix = when {
            nextStart > end -> lineText.substring(end, nextStart)
            index == lastIndex && end < lineText.length -> lineText.substring(end)
            else -> ""
        }
        result += word.copy(text = word.text + suffix)
        cursor = end + suffix.length
    }
    return result
}

private fun LyricLine.isDuetLine(): Boolean = agent.equals("v1", true) || agent.equals("v2", true)

private fun LyricLine.isActiveAt(positionMs: Long): Boolean {
    val timedEnd = endMs ?: words.maxOfOrNull { it.endMs } ?: backgroundEndMs ?: timeMs + 4_000L
    return positionMs in timeMs until timedEnd.coerceAtLeast(timeMs + 1L)
}

private fun LyricLine.duetTextAlign(default: TextAlign): TextAlign = when {
    agent.equals("v2", true) -> TextAlign.End
    agent.equals("v1", true) -> TextAlign.Start
    else -> default
}

private fun String.isPredominantlyWesternLyric(): Boolean {
    val visible = filter { it.isLetterOrDigit() }
    if (visible.isEmpty()) return false
    val latin = visible.count { it.isLetter() && it.code <= 0x024F }
    return latin.toFloat() / visible.length >= 0.60f
}
