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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import kotlinx.coroutines.delay
import kotlin.math.sin
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
                            displayText = line.pronunciation.orEmpty(),
                            words = line.pronunciationWords,
                            currentPositionMs = currentPositionMs,
                            textAlign = lineTextAlign,
                            fontSizeSp = scaledLyricFontSp(12, fontScale, minSp = 9),
                            fontFamily = fontFamily,
                            currentColor = Color.White.copy(alpha = 0.76f),
                            sungColor = Color.White.copy(alpha = 0.54f),
                            pendingColor = Color.White.copy(alpha = 0.38f),
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
                        fontFamily = fontFamily
                    )
                } else {
                    val textColor = when {
                        isActive -> Color.White
                        index < currentIndex -> Color.White.copy(alpha = 0.36f)
                        else -> Color.White.copy(alpha = 0.56f)
                    }
                    Text(
                        text = line.text.ifBlank { "♪" }.lineBreakSafeText(),
                        fontSize = fittedLyricFontSp(line.text, scaledLyricFontSp(if (isActive) 32 else 22, fontScale, minSp = if (isActive) 9 else 8), minSp = if (isActive) 9 else 8).sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
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
                            currentColor = Color.White.copy(alpha = 0.78f),
                            sungColor = Color.White.copy(alpha = 0.56f),
                            pendingColor = Color.White.copy(alpha = 0.42f),
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

        item { Box(modifier = Modifier.height(420.dp)) }
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
    currentColor: Color = Color.White,
    sungColor: Color = Color.White.copy(alpha = 0.82f),
    pendingColor: Color = Color.White.copy(alpha = 0.56f)
) {
    val text = remember(displayText, words) {
        displayText.ifBlank { words.joinToString("") { it.text } }.ifBlank { "♪" }.lineBreakSafeText()
    }
    var textLayout by remember(text, fontSizeSp, textAlign) { mutableStateOf<TextLayoutResult?>(null) }
    val progress by animateFloatAsState(
        targetValue = text.progressFraction(words, currentPositionMs),
        animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
        label = "lyric_sentence_progress"
    )
    val sustainWord = words.firstOrNull {
        currentPositionMs in it.startMs..it.endMs && it.endMs - it.startMs >= 900L
    }
    val sustainRange = sustainWord?.let { word ->
        var start = 0f
        for (item in words) {
            if (item == word) break
            start += item.text.visualLength().coerceAtLeast(0.5f)
        }
        start to (start + word.text.visualLength().coerceAtLeast(0.5f))
    }
    val glowPulse = if (sustainWord != null) {
        0.38f + 0.32f * ((sin(currentPositionMs / 145.0).toFloat() + 1f) / 2f)
    } else {
        0f
    }
    val baseStyle = TextStyle(
        color = pendingColor,
        fontSize = fontSizeSp.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.ExtraBold,
        textAlign = textAlign
    )
    val highlightStyle = TextStyle(
        color = currentColor,
        fontSize = fontSizeSp.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.ExtraBold,
        textAlign = textAlign
    )

    Box(modifier = modifier.fillMaxWidth()) {
        BasicText(
            text = text,
            style = baseStyle,
            maxLines = 4,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
        )
        if (glowPulse > 0f) {
            BasicText(
                text = text,
                style = highlightStyle.copy(color = currentColor.copy(alpha = currentColor.alpha * glowPulse)),
                maxLines = 4,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .graphicsLayer {
                        scaleX = 1.018f
                        scaleY = 1.018f
                    }
                    .blur(9.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .drawWithContent {
                        drawTextVisualRange(textLayout, sustainRange)
                    }
            )
        }
        BasicText(
            text = text,
            style = highlightStyle,
            maxLines = 4,
            overflow = TextOverflow.Clip,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .drawWithContent {
                    drawTextLineProgress(textLayout, progress)
                }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawTextVisualRange(
    layout: TextLayoutResult?,
    visualRange: Pair<Float, Float>?
) {
    val result = layout
    val range = visualRange
    if (result == null || result.lineCount == 0 || range == null) {
        drawContent()
        return
    }

    val rangeStart = range.first.coerceAtLeast(0f)
    val rangeEnd = range.second.coerceAtLeast(rangeStart)
    val contentScope = this
    var lineStartVisual = 0f
    for (line in 0 until result.lineCount) {
        val start = result.getLineStart(line)
        val end = result.getLineEnd(line, visibleEnd = true).coerceAtLeast(start)
        val lineText = result.layoutInput.text.text.substring(start, end)
        val lineVisual = lineText.visualLength().coerceAtLeast(1f)
        val lineEndVisual = lineStartVisual + lineVisual

        val overlapStart = maxOf(rangeStart, lineStartVisual)
        val overlapEnd = minOf(rangeEnd, lineEndVisual)
        if (overlapEnd > overlapStart) {
            val left = result.getLineLeft(line)
            val right = result.getLineRight(line)
            val top = result.getLineTop(line)
            val bottom = result.getLineBottom(line)
            val lineWidth = (right - left).coerceAtLeast(1f)
            val localStart = ((overlapStart - lineStartVisual) / lineVisual).coerceIn(0f, 1f)
            val localEnd = ((overlapEnd - lineStartVisual) / lineVisual).coerceIn(0f, 1f)
            val feather = (lineWidth * 0.05f).coerceIn(6f, 18f)
            clipRect(
                left = (left + lineWidth * localStart - feather).coerceAtLeast(left),
                top = top,
                right = (left + lineWidth * localEnd + feather).coerceAtMost(right),
                bottom = bottom
            ) {
                contentScope.drawContent()
            }
        }
        lineStartVisual = lineEndVisual
    }
}

private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawTextLineProgress(
    layout: TextLayoutResult?,
    progress: Float
) {
    val result = layout
    if (result == null || result.lineCount == 0) {
        drawContent()
        return
    }
    val target = (result.layoutInput.text.text.visualLength() * progress.coerceIn(0f, 1f))
        .coerceAtLeast(0f)
    val contentScope = this
    var lineStartVisual = 0f
    for (line in 0 until result.lineCount) {
        val start = result.getLineStart(line)
        val end = result.getLineEnd(line, visibleEnd = true).coerceAtLeast(start)
        val lineText = result.layoutInput.text.text.substring(start, end)
        val lineVisual = lineText.visualLength()
        val lineProgress = ((target - lineStartVisual) / lineVisual).coerceIn(0f, 1f)
        lineStartVisual += lineVisual
        if (lineProgress <= 0f) continue

        val left = result.getLineLeft(line)
        val right = result.getLineRight(line)
        val top = result.getLineTop(line)
        val bottom = result.getLineBottom(line)
        val lineWidth = (right - left).coerceAtLeast(1f)
        val feather = (lineWidth * 0.08f).coerceIn(8f, 28f)
        val clipRight = (left + lineWidth * lineProgress + feather).coerceAtMost(right)
        clipRect(left = left, top = top, right = clipRight, bottom = bottom) {
            contentScope.drawContent()
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
    return (baseSp * fontScale).toInt().coerceIn(minSp, baseSp)
}

private fun String.progressFraction(words: List<LyricWord>, positionMs: Long): Float {
    if (words.isEmpty()) return 0f
    val total = visualLength().coerceAtLeast(1f)
    if (total <= 0f) return 0f
    var passed = 0f
    words.forEach { word ->
        val weight = word.text.visualLength().coerceAtLeast(0.5f)
        when {
            positionMs >= word.endMs -> passed += weight
            positionMs <= word.startMs -> return (passed / total).coerceIn(0f, 1f)
            else -> {
                val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                val local = ((positionMs - word.startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                return ((passed + weight * local) / total).coerceIn(0f, 1f)
            }
        }
    }
    return 1f
}

private fun lyricProgressBrush(progress: Float, color: Color): Brush {
    val p = progress.coerceIn(0f, 1f)
    val feather = 0.10f
    val fadeStart = (p - feather).coerceIn(0f, 1f)
    val fadeEnd = (p + feather).coerceIn(0f, 1f)
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to color,
            fadeStart to color,
            p to color.copy(alpha = color.alpha * 0.82f),
            fadeEnd to color.copy(alpha = 0f),
            1f to color.copy(alpha = 0f)
        )
    )
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

