package com.ella.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

/** A native, independently implemented focus-lyrics renderer. */
@Composable
internal fun AppleMusicLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
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
    val interludes = remember(lyrics) { lyrics.interludes() }
    var smoothPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    LaunchedEffect(currentPositionMs, isPlaying) {
        val anchorPositionMs = currentPositionMs
        val anchorFrameNs = withFrameNanos { it }
        smoothPositionMs = anchorPositionMs
        while (isPlaying) {
            val frameNs = withFrameNanos { it }
            smoothPositionMs = anchorPositionMs + ((frameNs - anchorFrameNs) / 1_000_000L)
        }
    }
    val activeInterlude = interludes.firstOrNull { it.isActiveAt(smoothPositionMs) }
    val activeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val scrollTargetIndex = activeInterlude?.let { interlude ->
        interlude.nextLineIndex + interludes.count { it.nextLineIndex < interlude.nextLineIndex }
    } ?: activeIndex + interludes.count { it.nextLineIndex <= activeIndex }
    LaunchedEffect(scrollTargetIndex) {
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        // Negative offset positions the line safely below the header rather than underneath it.
        listState.animateScrollToItem(scrollTargetIndex, -(viewportHeight * 0.24f).toInt())
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
        lyrics.forEachIndexed { index, line ->
            interludes.firstOrNull { it.nextLineIndex == index }?.let { interlude ->
                item(key = "interlude-${interlude.startMs}-${interlude.endMs}") {
                    AppleMusicInterlude(
                        interlude = interlude,
                        positionMs = smoothPositionMs,
                        contentColor = contentColor,
                        textAlign = lyrics[if (interlude.nextLineIndex == 0) 0 else interlude.nextLineIndex - 1]
                            .duetTextAlign(defaultTextAlign)
                    )
                }
            }
            item(key = "${line.timeMs}-$index") {
            val duetActive = line.isDuetLine() && line.isActiveAt(smoothPositionMs)
            val lineIsActive = activeInterlude == null && (index == activeIndex || duetActive)
            AppleMusicLyricLine(
                line = line,
                active = lineIsActive,
                distance = (index - activeIndex).coerceIn(-4, 4),
                userScrolling = listState.isScrollInProgress,
                // Do not invalidate every retained LazyColumn row for every playback tick.
                // Only the active (or simultaneous duet) line needs a changing karaoke position.
                currentPositionMs = if (lineIsActive) smoothPositionMs else Long.MIN_VALUE,
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
}

/** Matches Apple Music's instrumental marker: three 10dp dots, separated by 6dp. */
@Composable
private fun AppleMusicInterlude(
    interlude: AppleMusicInterlude,
    positionMs: Long,
    contentColor: Color,
    textAlign: TextAlign
) {
    val visible = interlude.isActiveAt(positionMs)
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.78f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 480f)) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = when (textAlign) {
                TextAlign.End -> Alignment.CenterEnd
                TextAlign.Center -> Alignment.Center
                else -> Alignment.CenterStart
            }
        ) {
            Row {
                // Match the legacy renderer's Apple Music-inspired four-second breath,
                // while keeping the compact dot group within a restrained 0.9x–1.1x range.
                val pulseScale = 1f + 0.1f * sin(
                    ((positionMs - interlude.startMs).toFloat() / 4_000f) * 2f * PI.toFloat()
                )
                val progress = ((positionMs - interlude.startMs).toFloat() /
                    (interlude.endMs - interlude.startMs - 800L).coerceAtLeast(1L))
                    .coerceIn(0f, 1f)
                // Each dot gets a 16dp cell (10dp dot + 6dp spacing).  Scaling the drawing
                // within that cell reserves enough room for the 1.1x breath and prevents the
                // third dot from being clipped by Compose's animated item layer.
                Row {
                    repeat(3) { index ->
                        val dotProgress = ((progress - index / 3f) * 3f).coerceIn(0f, 1f)
                        val dotAlpha by animateFloatAsState(
                            targetValue = 0.18f + 0.67f * dotProgress,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 440f),
                            label = "appleInterludeDot$index"
                        )
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(
                                color = contentColor.copy(alpha = dotAlpha),
                                radius = 5.dp.toPx() * pulseScale
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleMusicLyricLine(
    line: LyricLine,
    active: Boolean,
    distance: Int,
    userScrolling: Boolean,
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
        shadow = null
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
            .then(if (!userScrolling && !active && abs(distance) >= 2) Modifier.blur((2 + abs(distance)).dp) else Modifier)
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
            AnimatedVisibility(
                visible = line.isBackgroundActiveAt(currentPositionMs),
                enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.72f), initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
            ) {
                Column {
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
            AppleMusicKaraokeWord(
                word = word,
                positionMs = positionMs,
                active = active,
                baseStyle = style,
                contentColor = contentColor
            )
        }
    }
}

@Composable
private fun AppleMusicKaraokeWord(
    word: LyricWord,
    positionMs: Long,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color
 ) {
    val progress = if (active) ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    else 0f
    val bright = contentColor.copy(alpha = baseStyle.color.alpha)
    val dim = contentColor.copy(alpha = baseStyle.color.alpha * 0.36f)
    when {
        progress <= 0f -> BasicText(text = word.text, style = baseStyle.copy(color = dim))
        progress >= 1f -> BasicText(text = word.text, style = baseStyle.copy(color = bright))
        else -> Box {
            BasicText(text = word.text, style = baseStyle.copy(color = dim))
            val featherStart = (progress - 0.15f).coerceAtLeast(0f)
            BasicText(
                text = word.text,
                style = baseStyle.copy(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to bright,
                            featherStart to bright,
                            progress to Color.Transparent,
                            1f to Color.Transparent
                        )
                    )
                )
            )
            word.sustainGlowAlpha(positionMs, active).takeIf { it > 0f }?.let { glowAlpha ->
                BasicText(
                    text = word.text,
                    style = baseStyle.copy(
                        color = bright.copy(alpha = glowAlpha * 0.38f),
                        shadow = Shadow(
                            color = bright.copy(alpha = glowAlpha * 0.58f),
                            offset = Offset(0f, 0f),
                            blurRadius = 16f
                        )
                    )
                )
            }
        }
    }
}

private fun LyricWord.sustainGlowAlpha(positionMs: Long, active: Boolean): Float {
    if (!active) return 0f
    val duration = endMs - startMs
    if (duration < 900L || positionMs !in startMs until endMs) return 0f
    val elapsed = positionMs - startMs
    val delay = minOf(420L, (duration * 0.36f).toLong().coerceAtLeast(1L))
    if (elapsed < delay) return 0f
    val progress = ((elapsed - delay).toFloat() / (duration - delay).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    return when {
        progress < 0.18f -> progress / 0.18f
        progress > 0.82f -> (1f - progress) / 0.18f
        else -> 1f
    }.coerceIn(0f, 1f)
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

private fun LyricLine.isBackgroundActiveAt(positionMs: Long): Boolean {
    val start = backgroundStartMs ?: backgroundWords.minOfOrNull { it.startMs } ?: return false
    val end = backgroundEndMs ?: backgroundWords.maxOfOrNull { it.endMs } ?: endMs ?: return false
    return positionMs in start until end.coerceAtLeast(start + 1L)
}

private const val INTERLUDE_MIN_GAP_MS = 7_000L

private data class AppleMusicInterlude(
    val startMs: Long,
    val endMs: Long,
    val nextLineIndex: Int
) {
    fun isActiveAt(positionMs: Long): Boolean = positionMs in startMs until endMs
}

private fun List<LyricLine>.interludes(): List<AppleMusicInterlude> {
    if (isEmpty()) return emptyList()
    val lines = this
    return buildList {
        lines.first().takeIf { it.timeMs >= INTERLUDE_MIN_GAP_MS }?.let { firstLine ->
            add(AppleMusicInterlude(startMs = 0L, endMs = firstLine.timeMs, nextLineIndex = 0))
        }
        for (index in 1..lines.lastIndex) {
            val previous = lines[index - 1]
            val next = lines[index]
            val gapStart = previous.primaryEndMs(nextLine = next)
            if (next.timeMs - gapStart >= INTERLUDE_MIN_GAP_MS) {
                add(AppleMusicInterlude(startMs = gapStart, endMs = next.timeMs, nextLineIndex = index))
            }
        }
    }
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
