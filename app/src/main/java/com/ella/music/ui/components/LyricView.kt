package com.ella.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.sin

@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean = true,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                fontFamily = fontFamily,
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
            val backgroundTextAlign = line.ttmlBackgroundTextAlign()

            val textColor = when {
                isActive -> MiuixTheme.colorScheme.primary
                isPast -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
            }

            if (showPronunciation && !line.pronunciation.isNullOrBlank()) {
                Text(
                    text = line.pronunciation,
                    fontSize = if (isActive) 13.sp else 11.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.58f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }
            Text(
                text = line.text.ifBlank { "♪" },
                fontSize = if (isActive) 18.sp else 15.sp,
                fontFamily = fontFamily,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = lineTextAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isActive) 4.dp else 0.dp)
            )
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Text(
                    text = line.translation,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.72f),
                    textAlign = lineTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            if (!line.backgroundText.isNullOrBlank()) {
                Text(
                    text = line.backgroundText,
                    fontSize = if (isActive) 14.sp else 12.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.56f),
                    textAlign = backgroundTextAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) {
                Text(
                    text = line.backgroundTranslation,
                    fontSize = if (isActive) 13.sp else 11.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.48f),
                    textAlign = backgroundTextAlign,
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
    showPronunciation: Boolean = true,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    onLineClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                fontFamily = fontFamily,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    var userBrowsing by remember { mutableStateOf(false) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }
    val isUserScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    LaunchedEffect(currentIndex) {
        if (!userBrowsing && currentIndex >= 0 && currentIndex < lyrics.size) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -140
            )
        }
    }

    LaunchedEffect(isUserScrolling) {
        if (isUserScrolling) {
            userBrowsing = true
            lastUserScrollMs = android.os.SystemClock.uptimeMillis()
        } else if (userBrowsing) {
            val marker = android.os.SystemClock.uptimeMillis()
            lastUserScrollMs = marker
            delay(USER_BROWSING_TIMEOUT_MS)
            if (lastUserScrollMs == marker) userBrowsing = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val fade = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.10f to Color.Black,
                    0.78f to Color.Black,
                    1f to Color.Transparent
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            }
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Box(modifier = Modifier.height(180.dp)) }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == currentIndex || line.isActiveAt(currentPositionMs)
            val nextLine = lyrics.getOrNull(index + 1)
            val lineTextAlign = line.ttmlTextAlign()
            val backgroundTextAlign = line.ttmlBackgroundTextAlign()
            val distance = when {
                currentIndex < 0 -> 2
                index < currentIndex -> currentIndex - index
                else -> index - currentIndex
            }
            val targetAlpha = when {
                userBrowsing -> 1f
                isActive -> 1f
                distance == 1 -> 0.58f
                distance == 2 -> 0.36f
                else -> 0.22f
            }
            val targetScale = when {
                userBrowsing -> 1f
                isActive -> 1f
                distance == 1 -> 0.94f
                else -> 0.90f
            }
            val lineAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
                label = "lyric_line_alpha"
            )
            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                label = "lyric_line_scale"
            )
            val blur by animateFloatAsState(
                targetValue = if (userBrowsing || isActive || distance <= 1) 0f else (distance - 1).coerceAtMost(3) * 1.35f,
                animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
                label = "lyric_line_blur"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = lineAlpha
                        scaleX = scale
                        scaleY = scale
                    }
                    .blur(blur.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .clickable { onLineClick(line) }
                    .padding(vertical = if (isActive) 6.dp else 2.dp),
                horizontalAlignment = line.ttmlAlignment()
            ) {
                if (showPronunciation && !line.pronunciation.isNullOrBlank()) {
                    val pronunciationColor = when {
                        isActive -> Color.White.copy(alpha = 0.62f)
                        index < currentIndex -> Color.White.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.40f)
                    }
                    if (isActive && line.pronunciationWords.isNotEmpty()) {
                        WordLine(
                            words = line.pronunciationWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = lineTextAlign,
                            fontSizeSp = 12,
                            fontFamily = fontFamily,
                            currentColor = Color.White.copy(alpha = 0.76f),
                            sungColor = Color.White.copy(alpha = 0.54f),
                            pendingColor = Color.White.copy(alpha = 0.38f),
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    } else {
                        Text(
                            text = line.pronunciation,
                            fontSize = fittedLyricFontSp(line.pronunciation.orEmpty(), if (isActive) 14 else 11, minSp = 9).sp,
                            fontFamily = fontFamily,
                            color = pronunciationColor,
                            textAlign = lineTextAlign,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp)
                        )
                    }
                }
                if (line.words.isNotEmpty() && isActive && line.text.visualLength() <= 30f) {
                    WordLine(
                        words = line.words,
                        currentPositionMs = currentPositionMs,
                        textAlign = lineTextAlign,
                        fontSizeSp = fittedLyricFontSp(line.words.joinToString("") { it.text }, 36, minSp = 9),
                        fontFamily = fontFamily
                    )
                } else {
                    val textColor = when {
                        isActive -> Color.White
                        index < currentIndex -> Color.White.copy(alpha = 0.36f)
                        else -> Color.White.copy(alpha = 0.56f)
                    }
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = fittedLyricFontSp(line.text, if (isActive) 36 else 24, minSp = if (isActive) 9 else 8).sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        color = textColor,
                        textAlign = lineTextAlign,
                        maxLines = if (isActive) 2 else 1,
                        softWrap = isActive,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isActive) 4.dp else 0.dp)
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
                        fontSize = fittedLyricFontSp(line.translation.orEmpty(), if (isActive) 20 else 14, minSp = 8).sp,
                        fontFamily = fontFamily,
                        color = translationColor,
                        textAlign = lineTextAlign,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                    )
                }
                if (!line.backgroundText.isNullOrBlank()) {
                    val backgroundColor = when {
                        isActive -> Color.White.copy(alpha = 0.56f)
                        index < currentIndex -> Color.White.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.42f)
                    }
                    if (isActive && line.backgroundWords.isNotEmpty() && line.backgroundText.orEmpty().visualLength() <= 30f) {
                        WordLine(
                            words = line.backgroundWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = backgroundTextAlign,
                            fontSizeSp = fittedLyricFontSp(line.backgroundWords.joinToString("") { it.text }, 24, minSp = 8),
                            fontFamily = fontFamily,
                            currentColor = Color.White.copy(alpha = 0.78f),
                            sungColor = Color.White.copy(alpha = 0.56f),
                            pendingColor = Color.White.copy(alpha = 0.42f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    } else {
                        Text(
                            text = line.backgroundText,
                            fontSize = fittedLyricFontSp(line.backgroundText.orEmpty(), if (isActive) 24 else 14, minSp = 8).sp,
                            fontFamily = fontFamily,
                            color = backgroundColor,
                            textAlign = backgroundTextAlign,
                            maxLines = if (isActive) 2 else 1,
                            softWrap = isActive,
                            overflow = TextOverflow.Ellipsis,
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
                        fontSize = fittedLyricFontSp(line.backgroundTranslation.orEmpty(), if (isActive) 13 else 11, minSp = 8).sp,
                        fontFamily = fontFamily,
                        color = backgroundTranslationColor,
                        textAlign = backgroundTextAlign,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
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

        item { Box(modifier = Modifier.height(420.dp)) }
    }
}

@Composable
private fun WordLine(
    words: List<LyricWord>,
    currentPositionMs: Long,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 18,
    fontFamily: FontFamily? = null,
    currentColor: Color = Color.White,
    sungColor: Color = Color.White.copy(alpha = 0.82f),
    pendingColor: Color = Color.White.copy(alpha = 0.56f)
) {
    val arrangement = when (textAlign) {
        TextAlign.End -> Arrangement.End
        TextAlign.Start -> Arrangement.Start
        else -> Arrangement.Center
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        words.forEach { word ->
            val isWordActive = currentPositionMs >= word.startMs
            val isWordCurrent = currentPositionMs in word.startMs..word.endMs

            val baseColor = when {
                isWordCurrent -> currentColor
                isWordActive -> sungColor
                else -> pendingColor
            }
            val fadeAlpha by animateFloatAsState(
                targetValue = when {
                    isWordCurrent -> 1f
                    isWordActive -> 0.86f
                    else -> 0.52f
                },
                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                label = "word_fade"
            )
            val color = baseColor.copy(alpha = (baseColor.alpha * fadeAlpha).coerceIn(0f, 1f))

            val displayText = word.text.toDisplayToken()
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
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = currentColor.copy(alpha = glowPulse * 0.34f),
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = 1.12f
                                scaleY = 1.12f
                            }
                            .blur(11.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    )
                    Text(
                        text = displayText,
                        fontSize = fontSizeSp.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = currentColor.copy(alpha = glowPulse * 0.56f),
                        modifier = Modifier
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
                    fontFamily = fontFamily,
                    fontWeight = if (isWordCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .alpha(if (displayText.isBlank()) 0f else 1f)
                )
            }
        }
    }
}

private const val USER_BROWSING_TIMEOUT_MS = 3_600L

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
    if (!isTtml) return TextAlign.Center
    if (agent.isNullOrBlank()) {
        return if (!backgroundText.isNullOrBlank() || backgroundWords.isNotEmpty()) TextAlign.Start else TextAlign.Center
    }
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun LyricLine.ttmlBackgroundTextAlign(): TextAlign {
    if (!isTtml) return TextAlign.Center
    return when (ttmlTextAlign()) {
        TextAlign.End -> TextAlign.Start
        TextAlign.Start -> TextAlign.End
        else -> TextAlign.Center
    }
}

private fun LyricLine.ttmlAlignment(): Alignment.Horizontal {
    if (!isTtml || agent.isNullOrBlank()) {
        return if (isTtml && (!backgroundText.isNullOrBlank() || backgroundWords.isNotEmpty())) {
            Alignment.Start
        } else {
            Alignment.CenterHorizontally
        }
    }
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

private fun fittedLyricFontSp(text: String, baseSp: Int, minSp: Int): Int {
    val visualLength = text.visualLength()
    val threshold = when {
        baseSp >= 34 -> 13f
        baseSp >= 24 -> 20f
        baseSp >= 20 -> 30f
        else -> 44f
    }
    if (visualLength <= threshold) return baseSp
    val scaled = (baseSp * threshold / visualLength).toInt()
    return scaled.coerceIn(minSp, baseSp)
}

private fun String.visualLength(): Float {
    if (isBlank()) return 1f
    return sumOf { char ->
        when {
            char.isWhitespace() -> 0.35
            char.isCjk() -> 1.0
            char.isLetterOrDigit() -> 0.58
            else -> 0.45
        }
    }.toFloat().coerceAtLeast(1f)
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

private fun String.toDisplayToken(): String {
    val cleaned = replace(Regex("""[ \t\r\n]+"""), " ").trim()
    if (cleaned.isBlank()) return ""
    return if (cleaned.any { it.isCjk() }) cleaned else "$cleaned "
}
