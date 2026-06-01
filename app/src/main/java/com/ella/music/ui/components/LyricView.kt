package com.ella.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onLineClick: (LyricLine) -> Unit = {},
    onLineDoubleClick: () -> Unit = {},
    onLineLongClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_lyrics),
                fontSize = 16.sp,
                fontFamily = fontFamily,
                color = if (usePlayerColors) Color.White.copy(alpha = 0.72f) else MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

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
            val backgroundAfterMain = line.isBackgroundAfterMain()
            val lineAlignment = line.ttmlAlignment()

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
                .pointerInput(line) {
                    detectTapGestures(
                        onTap = { onLineClick(line) },
                        onDoubleTap = { onLineDoubleClick() },
                        onLongPress = { onLineLongClick(line) }
                    )
                }

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
            if (!backgroundAfterMain) {
                SimpleBackgroundLyricBlock(
                    line = line,
                    showTranslation = showTranslation,
                    isActive = isActive,
                    textColor = textColor,
                    backgroundTextAlign = backgroundTextAlign,
                    fontFamily = fontFamily,
                    lineModifier = lineModifier
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
            if (backgroundAfterMain) {
                SimpleBackgroundLyricBlock(
                    line = line,
                    showTranslation = showTranslation,
                    isActive = isActive,
                    textColor = textColor,
                    backgroundTextAlign = backgroundTextAlign,
                    fontFamily = fontFamily,
                    lineModifier = lineModifier
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
    isPlaying: Boolean,
    showTranslation: Boolean,
    showPronunciation: Boolean = true,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    topSpacer: androidx.compose.ui.unit.Dp = 180.dp,
    bottomSpacer: androidx.compose.ui.unit.Dp = 420.dp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 22.dp,
    lineHorizontalPadding: androidx.compose.ui.unit.Dp = LYRIC_EDGE_GUARD_DP,
    perspectiveEffect: Boolean = false,
    onLineClick: (LyricLine) -> Unit = {},
    onLineDoubleClick: () -> Unit = {},
    onLineLongClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_lyrics),
                fontSize = 16.sp,
                fontFamily = fontFamily,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val safeHorizontalPadding = horizontalPadding.coerceAtLeast(0.dp)
    val safeLineHorizontalPadding = lineHorizontalPadding.coerceAtLeast(0.dp)
    var userBrowsing by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }
    val isUserScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val smoothPositionMs = rememberSmoothLyricPosition(
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        anchorKey = lyrics.getOrNull(currentIndex)?.lyricRenderKey() ?: currentIndex
    )

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
                    0.12f to Color.Black,
                    0.74f to Color.Black,
                    1f to Color.Transparent
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            }
            .padding(horizontal = safeHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Box(modifier = Modifier.height(topSpacer)) }

        itemsIndexed(
            items = lyrics,
            key = { index, line -> "$index:${line.lyricRenderKey()}" }
        ) { index, line ->
            val isActive = index == currentIndex || line.isActiveAt(smoothPositionMs)
            val nextLine = lyrics.getOrNull(index + 1)
            val lineTextAlign = line.ttmlTextAlign()
            val backgroundTextAlign = line.ttmlBackgroundTextAlign()
            val backgroundAfterMain = line.isBackgroundAfterMain()
            val lineAlignment = line.ttmlAlignment()
            val lineTransformOrigin = line.ttmlTransformOrigin()
            val displayPronunciation = line.displayPronunciationText()
            val displayTranslation = line.displayTranslationText()
            val distance = when {
                currentIndex < 0 -> 2
                index < currentIndex -> currentIndex - index
                else -> index - currentIndex
            }
            val lineAlphaTarget = when {
                userBrowsing -> 0.92f
                isActive -> 1f
                distance == 1 -> 0.54f
                distance == 2 -> 0.32f
                else -> 0.18f
            }
            val lineScaleTarget = when {
                userBrowsing -> 1f
                isActive -> 1.018f
                distance == 1 -> 0.972f
                distance == 2 -> 0.94f
                else -> 0.92f
            }
            val lineBlurTarget = when {
                userBrowsing || isActive -> 0f
                !perspectiveEffect -> 0f
                index < currentIndex -> 1.2f
                else -> (distance * 1.35f).coerceAtMost(4.2f)
            }
            val perspectiveRotationTarget = when {
                userBrowsing || isActive || !perspectiveEffect || currentIndex < 0 -> 0f
                else -> {
                    val direction = if (index < currentIndex) -1f else 1f
                    direction * distance.coerceAtMost(5) * 1.25f
                }
            }
            val perspectiveOffsetTarget = when {
                userBrowsing || isActive || !perspectiveEffect || currentIndex < 0 -> 0f
                else -> {
                    val direction = if (index < currentIndex) -1f else 1f
                    direction * distance.coerceAtMost(5) * 3.2f
                }
            }
            val lineAlpha by animateFloatAsState(
                targetValue = lineAlphaTarget,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                label = "lyric_line_alpha"
            )
            val lineScale by animateFloatAsState(
                targetValue = lineScaleTarget,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "lyric_line_scale"
            )
            val lineBlur by animateFloatAsState(
                targetValue = lineBlurTarget,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "lyric_line_blur"
            )
            val perspectiveRotation by animateFloatAsState(
                targetValue = perspectiveRotationTarget,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "lyric_line_perspective_rotation"
            )
            val perspectiveOffset by animateFloatAsState(
                targetValue = perspectiveOffsetTarget,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                label = "lyric_line_perspective_offset"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = lineAlpha
                        scaleX = lineScale
                        scaleY = lineScale
                        rotationX = perspectiveRotation
                        translationY = with(density) { perspectiveOffset.dp.toPx() }
                        transformOrigin = lineTransformOrigin
                        clip = false
                    }
                    .blur(lineBlur.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .pointerInput(line) {
                        detectTapGestures(
                            onTap = { onLineClick(line) },
                            onDoubleTap = { onLineDoubleClick() },
                            onLongPress = { onLineLongClick(line) }
                        )
                    }
                    .padding(
                        horizontal = safeLineHorizontalPadding,
                        vertical = if (isActive) 6.dp else 2.dp
                    ),
                horizontalAlignment = lineAlignment
            ) {
                if (showPronunciation && !displayPronunciation.isNullOrBlank()) {
                    val pronunciationColor = when {
                        isActive -> Color.White.copy(alpha = 0.62f)
                        index < currentIndex -> Color.White.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.40f)
                    }
                    Text(
                        text = displayPronunciation.lineBreakSafeText(),
                        fontSize = fittedLyricFontSp(displayPronunciation, scaledLyricFontSp(if (isActive) 14 else 11, fontScale, minSp = 9), minSp = 9).sp,
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
                if (!backgroundAfterMain) {
                    WordBackgroundLyricBlock(
                        line = line,
                        isActive = isActive,
                        currentIndex = currentIndex,
                        index = index,
                        smoothPositionMs = smoothPositionMs,
                        showTranslation = showTranslation,
                        backgroundTextAlign = backgroundTextAlign,
                        fontScale = fontScale,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                }
                WordTimedText(
                    text = line.text.ifBlank { "♪" },
                    words = line.words,
                    positionMs = smoothPositionMs,
                    isActive = isActive,
                    isPastLine = index < currentIndex,
                    fontSizeSp = fittedLyricFontSp(line.text, scaledLyricFontSp(if (isActive) 32 else 22, fontScale, minSp = 9), minSp = 9),
                    fontFamily = fontFamily,
                    fontWeight = if (isActive) fontWeight else fontWeight.softenedLyricWeight(),
                    activeFontWeight = fontWeight,
                    textAlign = lineTextAlign,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showTranslation && !displayTranslation.isNullOrBlank()) {
                    val translationColor = Color.White.copy(alpha = 0.58f)
                    Text(
                        text = displayTranslation.lineBreakSafeText(),
                    fontSize = fittedLyricFontSp(displayTranslation, scaledLyricFontSp(if (isActive) 18 else 13, fontScale, minSp = 8), minSp = 8).sp,
                        fontFamily = fontFamily,
                    color = if (isActive) {
                        Color.White.copy(alpha = 0.72f)
                    } else if (index < currentIndex) {
                        Color.White.copy(alpha = 0.38f)
                    } else {
                        translationColor
                    },
                        textAlign = lineTextAlign,
                        maxLines = 3,
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                    )
                }
                if (backgroundAfterMain) {
                    WordBackgroundLyricBlock(
                        line = line,
                        isActive = isActive,
                        currentIndex = currentIndex,
                        index = index,
                        smoothPositionMs = smoothPositionMs,
                        showTranslation = showTranslation,
                        backgroundTextAlign = backgroundTextAlign,
                        fontScale = fontScale,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                }
                if (isActive && line.shouldShowInterlude(nextLine, smoothPositionMs)) {
                    InterludeDots(
                        remainingMs = (nextLine?.timeMs ?: smoothPositionMs) - smoothPositionMs,
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
private fun rememberSmoothLyricPosition(
    currentPositionMs: Long,
    isPlaying: Boolean,
    anchorKey: Any?
): Long {
    var renderedPositionMs by remember(anchorKey) { mutableLongStateOf(currentPositionMs) }
    var anchorPositionMs by remember(anchorKey) { mutableLongStateOf(currentPositionMs) }
    var anchorFrameNanos by remember(anchorKey) { mutableLongStateOf(0L) }

    LaunchedEffect(anchorKey, currentPositionMs) {
        withFrameNanos { frameNanos ->
            if (currentPositionMs < renderedPositionMs || currentPositionMs - renderedPositionMs > 900L) {
                renderedPositionMs = currentPositionMs
            }
            anchorPositionMs = currentPositionMs
            anchorFrameNanos = frameNanos
            if (!isPlaying) renderedPositionMs = currentPositionMs
        }
    }

    LaunchedEffect(anchorKey, isPlaying) {
        if (!isPlaying) {
            renderedPositionMs = currentPositionMs
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (anchorFrameNanos <= 0L) return@withFrameNanos
                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000L).coerceAtLeast(0L)
                val predicted = anchorPositionMs + elapsedMs
                if (predicted >= renderedPositionMs) {
                    renderedPositionMs = predicted
                }
            }
        }
    }

    return renderedPositionMs
}

private fun LyricLine.lyricRenderKey(): String =
    "$timeMs|$endMs|$text|$backgroundText"

@Composable
private fun WordTimedText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    isActive: Boolean,
    isPastLine: Boolean,
    fontSizeSp: Int,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    activeFontWeight: FontWeight,
    textAlign: TextAlign,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    val baseColor = when {
        isActive -> Color.White.copy(alpha = 0.92f)
        isPastLine -> Color.White.copy(alpha = 0.44f)
        else -> Color.White.copy(alpha = 0.70f)
    }
    val annotatedText = remember(text, words, positionMs, isActive, isPastLine, fontWeight, activeFontWeight) {
        buildWordTimedAnnotatedString(
            text = text.lineBreakSafeText(),
            words = words,
            positionMs = positionMs,
            isActive = isActive,
            baseColor = baseColor,
            inactiveFutureColor = Color.White.copy(alpha = 0.36f),
            currentWordColor = Color.White,
            fontWeight = fontWeight,
            activeFontWeight = activeFontWeight
        )
    }
    BasicText(
        text = annotatedText,
        modifier = modifier,
        style = TextStyle(
            color = baseColor,
            fontSize = fontSizeSp.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            textAlign = textAlign
        ),
        maxLines = maxLines,
        softWrap = true,
        overflow = TextOverflow.Clip
    )
}

private fun buildWordTimedAnnotatedString(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    isActive: Boolean,
    baseColor: Color,
    inactiveFutureColor: Color,
    currentWordColor: Color,
    fontWeight: FontWeight,
    activeFontWeight: FontWeight
): AnnotatedString {
    val displayWords = words.filter { it.text.isNotBlank() }
    if (!isActive || displayWords.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        displayWords.forEach { word ->
            val wordText = word.text.lineBreakSafeText()
            val style = when {
                positionMs < word.startMs -> SpanStyle(
                    color = inactiveFutureColor,
                    fontWeight = fontWeight
                )
                positionMs <= word.endMs -> SpanStyle(
                    color = currentWordColor,
                    fontWeight = activeFontWeight
                )
                else -> SpanStyle(
                    color = baseColor,
                    fontWeight = activeFontWeight
                )
            }
            pushStyle(style)
            append(wordText)
            pop()
        }
    }
}

@Composable
private fun SimpleBackgroundLyricBlock(
    line: LyricLine,
    showTranslation: Boolean,
    isActive: Boolean,
    textColor: Color,
    backgroundTextAlign: TextAlign,
    fontFamily: FontFamily?,
    lineModifier: Modifier
) {
    if (!line.backgroundText.isNullOrBlank()) {
        Text(
            text = line.backgroundText.orEmpty().lineBreakSafeText(),
            fontSize = if (isActive) 14.sp else 12.sp,
            fontFamily = fontFamily,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = textColor.copy(alpha = 0.56f),
            textAlign = backgroundTextAlign,
            modifier = lineModifier.padding(top = 2.dp)
        )
    }
    val displayBackgroundTranslation = line.displayBackgroundTranslationText()
    if (showTranslation && !displayBackgroundTranslation.isNullOrBlank()) {
        Text(
            text = displayBackgroundTranslation.lineBreakSafeText(),
            fontSize = if (isActive) 13.sp else 11.sp,
            fontFamily = fontFamily,
            color = textColor.copy(alpha = 0.48f),
            textAlign = backgroundTextAlign,
            modifier = lineModifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun WordBackgroundLyricBlock(
    line: LyricLine,
    isActive: Boolean,
    currentIndex: Int,
    index: Int,
    smoothPositionMs: Long,
    showTranslation: Boolean,
    backgroundTextAlign: TextAlign,
    fontScale: Float,
    fontFamily: FontFamily?,
    fontWeight: FontWeight
) {
    AnimatedVisibility(
        visible = line.isBackgroundVisibleAt(smoothPositionMs),
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!line.backgroundText.isNullOrBlank()) {
                val backgroundColor = when {
                    isActive -> Color.White.copy(alpha = 0.56f)
                    index < currentIndex -> Color.White.copy(alpha = 0.28f)
                    else -> Color.White.copy(alpha = 0.42f)
                }
                Text(
                    text = line.backgroundText.orEmpty().lineBreakSafeText(),
                    fontSize = fittedLyricFontSp(line.backgroundText.orEmpty(), scaledLyricFontSp(16, fontScale, minSp = 8), minSp = 8).sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight.softenedLyricWeight(),
                    color = backgroundColor,
                    textAlign = backgroundTextAlign,
                    maxLines = 3,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                )
            }
            val displayBackgroundTranslation = line.displayBackgroundTranslationText()
            if (showTranslation && !displayBackgroundTranslation.isNullOrBlank()) {
                val backgroundTranslationColor = when {
                    isActive -> Color.White.copy(alpha = 0.44f)
                    index < currentIndex -> Color.White.copy(alpha = 0.24f)
                    else -> Color.White.copy(alpha = 0.36f)
                }
                Text(
                    text = displayBackgroundTranslation.lineBreakSafeText(),
                    fontSize = fittedLyricFontSp(displayBackgroundTranslation, scaledLyricFontSp(10, fontScale, minSp = 8), minSp = 8).sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight.softenedLyricWeight(),
                    color = backgroundTranslationColor,
                    textAlign = backgroundTextAlign,
                    maxLines = 3,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

private fun LyricLine.isBackgroundAfterMain(): Boolean {
    val backgroundStart = backgroundStartMs ?: backgroundWords.minOfOrNull { it.startMs }
    val mainStart = words.minOfOrNull { it.startMs } ?: timeMs
    return backgroundStart == null || backgroundStart > mainStart
}

private fun LyricLine.isBackgroundVisibleAt(positionMs: Long): Boolean {
    val start = backgroundStartMs ?: backgroundWords.minOfOrNull { it.startMs } ?: return true
    val end = backgroundEndMs ?: backgroundWords.maxOfOrNull { it.endMs } ?: endMs
    return positionMs >= start && (end == null || positionMs <= end)
}

private val LYRIC_EDGE_GUARD_DP = 10.dp

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
    if (agent.isNullOrBlank()) {
        return TextAlign.Start
    }
    return if (agent.equals("v2", ignoreCase = true)) TextAlign.End else TextAlign.Start
}

private fun LyricLine.ttmlBackgroundTextAlign(): TextAlign {
    return ttmlTextAlign()
}

private fun LyricLine.ttmlAlignment(): Alignment.Horizontal {
    if (agent.isNullOrBlank()) return Alignment.Start
    return if (agent.equals("v2", ignoreCase = true)) Alignment.End else Alignment.Start
}

private fun LyricLine.ttmlTransformOrigin(): TransformOrigin {
    if (agent.equals("v2", ignoreCase = true)) return TransformOrigin(1f, 0.5f)
    return TransformOrigin(0f, 0.5f)
}

private fun LyricLine.displayPronunciationText(): String? {
    pronunciation?.takeIf { it.isNotBlank() }?.let { return it }
    return romanizationSecondaryCandidate()
}

private fun LyricLine.displayTranslationText(): String? {
    val value = translation?.takeIf { it.isNotBlank() } ?: return null
    if (pronunciation.isNullOrBlank() && isLikelyRomanizationSecondary(text, value)) return null
    return value
}

private fun LyricLine.displayBackgroundTranslationText(): String? {
    val value = backgroundTranslation?.takeIf { it.isNotBlank() } ?: return null
    val primary = backgroundText?.takeIf { it.isNotBlank() } ?: text
    if (pronunciation.isNullOrBlank() && isLikelyRomanizationSecondary(primary, value)) return null
    return value
}

private fun LyricLine.romanizationSecondaryCandidate(): String? {
    translation?.takeIf { isLikelyRomanizationSecondary(text, it) }?.let { return it }
    val primary = backgroundText?.takeIf { it.isNotBlank() } ?: text
    return backgroundTranslation?.takeIf { isLikelyRomanizationSecondary(primary, it) }
}

private fun isLikelyRomanizationSecondary(primary: String?, candidate: String?): Boolean {
    val primaryText = primary?.takeIf { it.isNotBlank() } ?: return false
    val secondary = candidate?.trim()?.takeIf { it.isNotBlank() } ?: return false
    if (!primaryText.hasCjkKanaOrHangul()) return false
    if (!secondary.any { it.isLatinLetter() }) return false
    if (secondary.hasCjkKanaOrHangul()) return false
    val useful = secondary.filterNot { it.isWhitespace() }
    if (useful.isEmpty()) return false
    val romanChars = useful.count { it.isLatinLetter() || it in "-'.`·・" }
    return romanChars.toFloat() / useful.length >= 0.82f
}

private fun String.hasCjkKanaOrHangul(): Boolean = any { char ->
    when (Character.UnicodeBlock.of(char)) {
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> true
        else -> false
    }
}

private fun Char.isLatinLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun LyricLine.isActiveAt(positionMs: Long): Boolean {
    val end = endMs ?: return false
    return positionMs in timeMs until end
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
