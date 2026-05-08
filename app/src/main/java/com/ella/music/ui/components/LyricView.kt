package com.ella.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            val targetIndex = (currentIndex + 1).coerceAtMost(lyrics.size)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Box(modifier = Modifier.height(200.dp)) }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == currentIndex
            val isPast = index < currentIndex
            val lineTextAlign = line.ttmlTextAlign()

            val textColor = when {
                isActive -> MiuixTheme.colorScheme.primary
                isPast -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
            }

            Text(
                text = line.text.ifBlank { "♪" },
                fontSize = if (isActive) 18.sp else 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = lineTextAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isActive) 4.dp else 0.dp)
            )
            if (!line.backgroundText.isNullOrBlank()) {
                Text(
                    text = line.backgroundText,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    color = textColor.copy(alpha = 0.56f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) {
                Text(
                    text = line.backgroundTranslation,
                    fontSize = if (isActive) 13.sp else 11.sp,
                    color = textColor.copy(alpha = 0.48f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Text(
                    text = line.translation,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    color = textColor.copy(alpha = 0.72f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }

        item { Box(modifier = Modifier.height(300.dp)) }
    }
}

@Composable
fun WordLyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    modifier: Modifier = Modifier,
    onLineClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            val targetIndex = (currentIndex + 1).coerceAtMost(lyrics.size)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Box(modifier = Modifier.height(200.dp)) }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == currentIndex || line.isActiveAt(currentPositionMs)
            val nextLine = lyrics.getOrNull(index + 1)
            val lineTextAlign = line.ttmlTextAlign()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineClick(line) },
                horizontalAlignment = line.ttmlAlignment()
            ) {
                if (line.words.isNotEmpty() && isActive) {
                    WordLine(
                        words = line.words,
                        currentPositionMs = currentPositionMs,
                        textAlign = lineTextAlign
                    )
                } else {
                    val textColor = when {
                        isActive -> Color.White
                        index < currentIndex -> Color.White.copy(alpha = 0.36f)
                        else -> Color.White.copy(alpha = 0.56f)
                    }
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = if (isActive) 18.sp else 15.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        textAlign = lineTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isActive) 4.dp else 0.dp)
                    )
                }
                if (!line.backgroundText.isNullOrBlank()) {
                    val backgroundColor = when {
                        isActive -> Color.White.copy(alpha = 0.56f)
                        index < currentIndex -> Color.White.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.42f)
                    }
                    if (isActive && line.backgroundWords.isNotEmpty()) {
                        WordLine(
                            words = line.backgroundWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = lineTextAlign,
                            fontSizeSp = 14,
                            currentColor = Color.White.copy(alpha = 0.78f),
                            sungColor = Color.White.copy(alpha = 0.56f),
                            pendingColor = Color.White.copy(alpha = 0.42f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    } else {
                        Text(
                            text = line.backgroundText,
                            fontSize = if (isActive) 14.sp else 12.sp,
                            color = backgroundColor,
                            textAlign = lineTextAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp)
                        )
                    }
                }
                if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) {
                    val backgroundTranslationColor = when {
                        isActive -> Color.White.copy(alpha = 0.44f)
                        index < currentIndex -> Color.White.copy(alpha = 0.24f)
                        else -> Color.White.copy(alpha = 0.36f)
                    }
                    Text(
                        text = line.backgroundTranslation,
                        fontSize = if (isActive) 13.sp else 11.sp,
                        color = backgroundTranslationColor,
                        textAlign = lineTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
                if (showTranslation && !line.translation.isNullOrBlank()) {
                    val translationColor = when {
                        isActive -> Color.White.copy(alpha = 0.72f)
                        index < currentIndex -> Color.White.copy(alpha = 0.36f)
                        else -> Color.White.copy(alpha = 0.50f)
                    }
                    Text(
                        text = line.translation,
                        fontSize = if (isActive) 14.sp else 12.sp,
                        color = translationColor,
                        textAlign = lineTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                    )
                }
                if (isActive && line.shouldShowInterlude(nextLine, currentPositionMs)) {
                    InterludeDots(
                        remainingMs = (nextLine?.timeMs ?: currentPositionMs) - currentPositionMs,
                        horizontalAlignment = line.ttmlAlignment(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )
                }
            }
        }

        item { Box(modifier = Modifier.height(300.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLine(
    words: List<LyricWord>,
    currentPositionMs: Long,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 18,
    currentColor: Color = Color.White,
    sungColor: Color = Color.White.copy(alpha = 0.82f),
    pendingColor: Color = Color.White.copy(alpha = 0.56f)
) {
    val arrangement = when (textAlign) {
        TextAlign.End -> Arrangement.End
        TextAlign.Start -> Arrangement.Start
        else -> Arrangement.Center
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalArrangement = Arrangement.Center
    ) {
        words.forEach { word ->
            val isWordActive = currentPositionMs >= word.startMs
            val isWordCurrent = currentPositionMs in word.startMs..word.endMs

            val color = when {
                isWordCurrent -> currentColor
                isWordActive -> sungColor
                else -> pendingColor
            }

            val displayText = word.text
            val liftFactor = if (word.text.any { it.isCjk() }) 0.055f else 0.065f
            val waveFactor = if (word.text.any { it.isCjk() }) 2.8f else 3.6f
            val liftDp = fontSizeSp * liftFactor * word.motionLift(currentPositionMs, waveFactor)
            val isLongSustain = isWordCurrent && (word.endMs - word.startMs) >= 900L
            val glowPulse = if (isLongSustain) {
                0.5f + 0.32f * ((sin(currentPositionMs / 145.0).toFloat() + 1f) / 2f)
            } else {
                0f
            }

            Box(
                modifier = Modifier.padding(vertical = 5.dp)
            ) {
                if (isLongSustain && displayText.isNotBlank()) {
                    Text(
                        text = displayText,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = currentColor.copy(alpha = glowPulse * 0.34f),
                        modifier = Modifier
                            .offset(y = (-liftDp).dp)
                            .graphicsLayer {
                                scaleX = 1.12f
                                scaleY = 1.12f
                            }
                            .blur(11.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    )
                    Text(
                        text = displayText,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = currentColor.copy(alpha = glowPulse * 0.56f),
                        modifier = Modifier
                            .offset(y = (-liftDp).dp)
                            .graphicsLayer {
                                scaleX = 1.05f
                                scaleY = 1.05f
                            }
                            .blur(5.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    )
                }
                Text(
                    text = displayText,
                    fontSize = fontSizeSp.sp,
                    fontWeight = if (isWordCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                    color = color,
                    modifier = Modifier
                        .offset(y = (-liftDp).dp)
                        .alpha(if (displayText.isBlank()) 0f else 1f)
                )
            }
        }
    }
}

@Composable
private fun InterludeDots(
    remainingMs: Long,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "interlude_dots")
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "interlude_dot_pulse"
    )
    val dotCount = when {
        remainingMs < 700L -> 1
        remainingMs < 1_400L -> 2
        else -> 3
    }
    val arrangement = when (horizontalAlignment) {
        Alignment.End -> Arrangement.End
        Alignment.Start -> Arrangement.Start
        else -> Arrangement.Center
    }

    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val stagger = 1f - index * 0.1f
            Canvas(
                modifier = Modifier
                    .width(14.dp)
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = pulse * stagger
                        scaleY = pulse * stagger
                    }
                    .alpha((0.42f + 0.18f * index).coerceIn(0f, 1f))
            ) {
                drawCircle(Color.White.copy(alpha = 0.72f))
            }
        }
    }
}

private fun LyricLine.ttmlTextAlign(): TextAlign {
    if (!isTtml || agent.isNullOrBlank()) return TextAlign.Center
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun LyricLine.ttmlAlignment(): Alignment.Horizontal {
    if (!isTtml || agent.isNullOrBlank()) return Alignment.CenterHorizontally
    return if (agent.equals("v2", ignoreCase = true)) Alignment.End else Alignment.Start
}

private fun LyricLine.isActiveAt(positionMs: Long): Boolean {
    val end = endMs ?: return false
    return isTtml && positionMs in timeMs until end
}

private fun LyricLine.shouldShowInterlude(nextLine: LyricLine?, positionMs: Long): Boolean {
    val nextStart = nextLine?.timeMs ?: return false
    val lineEnd = endMs ?: nextStart
    return positionMs >= lineEnd &&
        positionMs < nextStart &&
        nextStart - lineEnd >= 1_800L &&
        nextStart - positionMs > 180L
}

private fun LyricWord.motionLift(positionMs: Long, waveFactor: Float): Float {
    val duration = (endMs - startMs).coerceAtLeast(1L).toFloat()
    val progress = ((positionMs - startMs).toFloat() / duration).coerceIn(0f, 1f)
    if (progress <= 0f || progress >= 1f) return 0f
    val waveWindow = (1f / waveFactor).coerceIn(0.18f, 0.42f)
    val distance = abs(progress - 0.42f)
    val normalized = (1f - distance / waveWindow).coerceIn(0f, 1f)
    return easeOutQuint(normalized)
}

private fun Char.isCjk(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
        block == Character.UnicodeBlock.HANGUL_JAMO ||
        block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
}

private fun easeOutQuint(value: Float): Float {
    val inverse = 1f - value
    return 1f - inverse * inverse * inverse * inverse * inverse
}
