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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean = true,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    usePlayerColors: Boolean = false,
    topSpacer: androidx.compose.ui.unit.Dp = 200.dp,
    bottomSpacer: androidx.compose.ui.unit.Dp = 300.dp,
    onLineClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                fontFamily = fontFamily,
                color = if (usePlayerColors) Color.White.copy(alpha = 0.72f) else MiuixTheme.colorScheme.onSurfaceVariantSummary
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
        item { Box(modifier = Modifier.height(topSpacer)) }

        itemsIndexed(lyrics) { index, line ->
            val isActive = index == currentIndex
            val isPast = index < currentIndex
            val lineTextAlign = line.ttmlTextAlign()
            val backgroundTextAlign = line.ttmlBackgroundTextAlign()

            val textColor = when {
                usePlayerColors && isActive -> Color.White.copy(alpha = 0.96f)
                usePlayerColors && isPast -> Color.White.copy(alpha = 0.34f)
                usePlayerColors -> Color.White.copy(alpha = 0.58f)
                isActive -> MiuixTheme.colorScheme.primary
                isPast -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f)
            }
            val lineModifier = Modifier
                .fillMaxWidth()
                .clickable { onLineClick(line) }

            if (showPronunciation && !line.pronunciation.isNullOrBlank()) {
                Text(
                    text = line.pronunciation.orEmpty().lineBreakSafeText(),
                    fontSize = if (isActive) 13.sp else 11.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.58f),
                    textAlign = lineTextAlign,
                    modifier = lineModifier
                        .padding(bottom = 2.dp)
                )
            }
            Text(
                text = line.text.ifBlank { "♪" }.lineBreakSafeText(),
                fontSize = if (isActive) 18.sp else 15.sp,
                fontFamily = fontFamily,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = lineTextAlign,
                modifier = lineModifier
                    .padding(vertical = if (isActive) 4.dp else 0.dp)
            )
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Text(
                    text = line.translation.orEmpty().lineBreakSafeText(),
                    fontSize = if (isActive) 14.sp else 12.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.72f),
                    textAlign = lineTextAlign,
                    modifier = lineModifier
                        .padding(top = 2.dp)
                )
            }
            if (!line.backgroundText.isNullOrBlank()) {
                Text(
                    text = line.backgroundText.orEmpty().lineBreakSafeText(),
                    fontSize = if (isActive) 14.sp else 12.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.56f),
                    textAlign = backgroundTextAlign,
                    modifier = lineModifier
                        .padding(top = 2.dp)
                )
            }
            if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) {
                Text(
                    text = line.backgroundTranslation.orEmpty().lineBreakSafeText(),
                    fontSize = if (isActive) 13.sp else 11.sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.48f),
                    textAlign = backgroundTextAlign,
                    modifier = lineModifier
                        .padding(top = 2.dp)
                )
            }
        }

        item { Box(modifier = Modifier.height(bottomSpacer)) }
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
    fontScale: Float = 1f,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    topSpacer: androidx.compose.ui.unit.Dp = 180.dp,
    bottomSpacer: androidx.compose.ui.unit.Dp = 420.dp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 22.dp,
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
    var autoScrolling by remember { mutableStateOf(false) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }
    val isUserScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    LaunchedEffect(currentIndex) {
        if (!userBrowsing && currentIndex >= 0 && currentIndex < lyrics.size) {
            autoScrolling = true
            try {
                listState.animateScrollToItem(
                    index = currentIndex,
                    scrollOffset = -140
                )
            } finally {
                autoScrolling = false
            }
        }
    }

    LaunchedEffect(isUserScrolling) {
        if (isUserScrolling && !autoScrolling) {
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
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Box(modifier = Modifier.height(topSpacer)) }

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
                distance == 1 -> 0.42f
                distance == 2 -> 0.24f
                else -> 0.14f
            }
            val targetScale = when {
                userBrowsing -> 1f
                isActive -> 1.01f
                distance == 1 -> 0.96f
                else -> 0.92f
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
                targetValue = if (userBrowsing || isActive) 0f else distance.coerceAtMost(2) * 0.18f,
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
                            displayText = line.pronunciation.orEmpty(),
                            words = line.pronunciationWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = lineTextAlign,
                            fontSizeSp = scaledLyricFontSp(12, fontScale, minSp = 9),
                            fontFamily = fontFamily,
                            fontWeight = fontWeight.softenedLyricWeight(),
                            currentColor = Color.White.copy(alpha = 0.86f),
                            sungColor = Color.White.copy(alpha = 0.62f),
                            pendingColor = Color.White.copy(alpha = 0.32f),
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    } else {
                        Text(
                            text = line.pronunciation.orEmpty().lineBreakSafeText(),
                            fontSize = fittedLyricFontSp(line.pronunciation.orEmpty(), scaledLyricFontSp(if (isActive) 14 else 11, fontScale, minSp = 9), minSp = 9).sp,
                            fontFamily = fontFamily,
                            color = pronunciationColor,
                            textAlign = lineTextAlign,
                            maxLines = if (isActive) 3 else 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp)
                        )
                    }
                }
                if (line.words.isNotEmpty() && isActive) {
                    WordLine(
                        displayText = line.text,
                        words = line.words,
                        currentPositionMs = currentPositionMs,
                        textAlign = lineTextAlign,
                        fontSizeSp = fittedLyricFontSp(line.text, scaledLyricFontSp(32, fontScale, minSp = 9), minSp = 9),
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        currentColor = Color.White,
                        sungColor = Color.White.copy(alpha = 0.92f),
                        pendingColor = Color.White.copy(alpha = 0.38f)
                    )
                } else {
                    val textColor = when {
                        isActive -> Color.White
                        index < currentIndex -> Color.White.copy(alpha = 0.32f)
                        else -> Color.White.copy(alpha = 0.42f)
                    }
                    Text(
                        text = line.text.ifBlank { "♪" }.lineBreakSafeText(),
                        fontSize = fittedLyricFontSp(line.text, scaledLyricFontSp(if (isActive) 32 else 22, fontScale, minSp = if (isActive) 9 else 8), minSp = if (isActive) 9 else 8).sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isActive) fontWeight else fontWeight.softenedLyricWeight(),
                        color = textColor,
                        textAlign = lineTextAlign,
                        maxLines = if (isActive) 4 else 3,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
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
                        text = line.translation.orEmpty().lineBreakSafeText(),
                        fontSize = fittedLyricFontSp(line.translation.orEmpty(), scaledLyricFontSp(if (isActive) 18 else 13, fontScale, minSp = 8), minSp = 8).sp,
                        fontFamily = fontFamily,
                        color = translationColor,
                        textAlign = lineTextAlign,
                        maxLines = if (isActive) 3 else 2,
                        softWrap = true,
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
                    if (isActive && line.backgroundWords.isNotEmpty()) {
                        WordLine(
                            displayText = line.backgroundText.orEmpty(),
                            words = line.backgroundWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = backgroundTextAlign,
                            fontSizeSp = fittedLyricFontSp(line.backgroundText.orEmpty(), scaledLyricFontSp(22, fontScale, minSp = 8), minSp = 8),
                            fontFamily = fontFamily,
                            fontWeight = fontWeight.softenedLyricWeight(),
                            currentColor = Color.White.copy(alpha = 0.86f),
                            sungColor = Color.White.copy(alpha = 0.62f),
                            pendingColor = Color.White.copy(alpha = 0.32f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    } else {
                        Text(
                            text = line.backgroundText.orEmpty().lineBreakSafeText(),
                            fontSize = fittedLyricFontSp(line.backgroundText.orEmpty(), scaledLyricFontSp(if (isActive) 22 else 13, fontScale, minSp = 8), minSp = 8).sp,
                            fontFamily = fontFamily,
                            color = backgroundColor,
                            textAlign = backgroundTextAlign,
                            maxLines = if (isActive) 3 else 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip,
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
                        text = line.backgroundTranslation.orEmpty().lineBreakSafeText(),
                        fontSize = fittedLyricFontSp(line.backgroundTranslation.orEmpty(), scaledLyricFontSp(if (isActive) 12 else 10, fontScale, minSp = 8), minSp = 8).sp,
                        fontFamily = fontFamily,
                        color = backgroundTranslationColor,
                        textAlign = backgroundTextAlign,
                        maxLines = if (isActive) 3 else 2,
                        softWrap = true,
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

        item { Box(modifier = Modifier.height(bottomSpacer)) }
    }
}

@Composable
private fun WordLine(
    displayText: String,
    words: List<LyricWord>,
    currentPositionMs: Long,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 18,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    currentColor: Color = Color.White,
    sungColor: Color = Color.White.copy(alpha = 0.92f),
    pendingColor: Color = Color.White.copy(alpha = 0.38f)
) {
    val text = remember(displayText, words) {
        displayText.ifBlank { words.joinToString("") { it.text } }.ifBlank { "♪" }.lineBreakSafeText()
    }
    val annotatedText = remember(text, words, currentPositionMs, currentColor, sungColor, pendingColor, fontWeight) {
        buildAnnotatedString {
            var appended = false
            words.forEach { word ->
                val wordText = word.text.lineBreakSafeText()
                if (wordText.isEmpty()) return@forEach
                appendTimedLyricWord(
                    text = wordText,
                    startMs = word.startMs,
                    endMs = word.endMs,
                    currentPositionMs = currentPositionMs,
                    currentColor = currentColor,
                    sungColor = sungColor,
                    pendingColor = pendingColor,
                    fontWeight = fontWeight,
                    inactiveWeight = fontWeight.softenedLyricWeight()
                )
                appended = true
            }
            if (!appended) append(text)
        }
    }

    BasicText(
        text = annotatedText,
        style = TextStyle(
            color = pendingColor,
            fontSize = fontSizeSp.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            textAlign = textAlign
        ),
        maxLines = 4,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    )
}

private fun AnnotatedString.Builder.appendTimedLyricWord(
    text: String,
    startMs: Long,
    endMs: Long,
    currentPositionMs: Long,
    currentColor: Color,
    sungColor: Color,
    pendingColor: Color,
    fontWeight: FontWeight,
    inactiveWeight: FontWeight
) {
    val durationMs = (endMs - startMs).coerceAtLeast(1L)
    val isCurrent = currentPositionMs in startMs until endMs
    val isSung = currentPositionMs >= endMs
    when {
        isCurrent -> {
            val progress = ((currentPositionMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
            appendStyledLyricText(
                value = text,
                color = currentColor,
                fontWeight = fontWeight,
                brush = lyricSweepBrush(
                    progress = progress,
                    activeColor = currentColor,
                    pendingColor = pendingColor
                ),
                shadow = Shadow(
                    color = currentColor.copy(alpha = 0.18f),
                    offset = Offset.Zero,
                    blurRadius = 12f
                ),
                baselineShift = BaselineShift(0.045f)
            )
        }
        isSung -> appendStyledLyricText(text, sungColor, inactiveWeight)
        else -> appendStyledLyricText(text, pendingColor, inactiveWeight)
    }
}

private fun AnnotatedString.Builder.appendStyledLyricText(
    value: String,
    color: Color,
    fontWeight: FontWeight,
    brush: Brush? = null,
    shadow: Shadow? = null,
    baselineShift: BaselineShift? = null
) {
    if (value.isEmpty()) return
    val style = if (brush != null) {
        SpanStyle(
            brush = brush,
            fontWeight = fontWeight,
            shadow = shadow,
            baselineShift = baselineShift
        )
    } else {
        SpanStyle(
            color = color,
            fontWeight = fontWeight,
            shadow = shadow,
            baselineShift = baselineShift
        )
    }
    pushStyle(
        style
    )
    append(value)
    pop()
}

private fun lyricSweepBrush(
    progress: Float,
    activeColor: Color,
    pendingColor: Color
): Brush {
    val edge = progress.coerceIn(0.002f, 0.998f)
    val beforeEdge = (edge - 0.04f).coerceAtLeast(0f)
    val afterEdge = (edge + 0.015f).coerceAtMost(1f)
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to activeColor,
            beforeEdge to activeColor,
            edge to activeColor.copy(alpha = 0.72f),
            afterEdge to pendingColor,
            1f to pendingColor
        )
    )
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
    if (!isTtml) return TextAlign.Start
    if (agent.isNullOrBlank()) {
        return TextAlign.Start
    }
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun LyricLine.ttmlBackgroundTextAlign(): TextAlign {
    return ttmlTextAlign()
}

private fun LyricLine.ttmlAlignment(): Alignment.Horizontal {
    if (!isTtml || agent.isNullOrBlank()) return Alignment.Start
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

private fun scaledLyricFontSp(baseSp: Int, fontScale: Float, minSp: Int): Int {
    val maxSp = (baseSp * 1.12f).toInt().coerceAtLeast(baseSp)
    return (baseSp * fontScale).toInt().coerceIn(minSp, maxSp)
}

private fun FontWeight.softenedLyricWeight(): FontWeight {
    return FontWeight((weight - 200).coerceIn(100, 900))
}

private fun String.lineBreakSafeText(): String {
    if (length < 2) return this
    val builder = StringBuilder(length)
    forEachIndexed { index, char ->
        if (index > 0 && char.isForbiddenLineStartPunctuation()) {
            builder.append('\u2060')
        }
        builder.append(char)
    }
    return builder.toString()
}

private fun Char.isForbiddenLineStartPunctuation(): Boolean {
    return this in setOf(
        ' ', '\t',
        ',', '.', '?', '!', ':', ';',
        '，', '。', '？', '！', '：', '；', '、',
        ')', ']', '}', '）', '】', '〕', '〉', '》',
        '…', '～', '~'
    )
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

