package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
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
    wordLiftEnabled: Boolean = true,
    onLineClick: (LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    topContentPadding: Dp = 72.dp,
    bottomContentPadding: Dp = 132.dp,
    lineSpacing: Dp = 25.dp,
    focusOffsetRatio: Float = 0.24f,
    nonCurrentLineBlurEnabled: Boolean = true,
    userScrollEnabled: Boolean = true,
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
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    val scrollSpring = remember { Animatable(0f) }
    var hasPositionedScroll by remember(lyrics) { mutableStateOf(false) }
    var deferAutoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(userDragging) {
        if (userDragging) {
            deferAutoScroll = true
        } else if (deferAutoScroll) {
            // ConePlayer keeps the user's reading position briefly before returning to the
            // current line. Its LyricView uses a 2-second delayed recenter message.
            delay(MANUAL_SCROLL_RECENTER_DELAY_MS)
            deferAutoScroll = false
        }
    }
    var keepLinesSharp by remember { mutableStateOf(!isPlaying) }
    LaunchedEffect(userDragging, isPlaying) {
        when {
            !isPlaying -> keepLinesSharp = true
            userDragging -> keepLinesSharp = true
            else -> {
                delay(MANUAL_SCROLL_BLUR_RESUME_DELAY_MS)
                keepLinesSharp = false
            }
        }
    }
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
    LaunchedEffect(scrollTargetIndex, userDragging, deferAutoScroll) {
        if (userDragging || deferAutoScroll) return@LaunchedEffect
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        val desiredItemOffset = viewportHeight * focusOffsetRatio

        if (!hasPositionedScroll) {
            // Initial positioning should not fly through the whole song when the player is
            // restored in the middle of a track.
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            scrollSpring.snapTo(0f)
            hasPositionedScroll = true
            return@LaunchedEffect
        }

        // ConePlayer does not restart a fixed-duration list animation for each lyric. It changes
        // every row's spring target (damping 1.25, stiffness 200) and lets the retained velocity
        // carry the content into place. Drive the LazyColumn with the same overdamped spring and
        // correct the distance after variable-height rows have entered the viewport.
        repeat(CONE_SCROLL_CORRECTION_PASSES) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@repeat
            val targetItem = visibleItems.firstOrNull { it.index == scrollTargetIndex }
            val distance = if (targetItem != null) {
                targetItem.offset - desiredItemOffset
            } else {
                val firstItem = visibleItems.first()
                val averageItemExtent = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size +
                    layoutInfo.mainAxisItemSpacing
                firstItem.offset - desiredItemOffset +
                    (scrollTargetIndex - firstItem.index) * averageItemExtent
            }
            if (abs(distance) <= CONE_SCROLL_VISIBILITY_THRESHOLD_PX) return@LaunchedEffect

            val animationStart = scrollSpring.value
            var appliedValue = animationStart
            listState.scroll {
                scrollSpring.animateTo(
                    targetValue = animationStart + distance,
                    animationSpec = spring(
                        dampingRatio = CONE_SCROLL_DAMPING_RATIO,
                        stiffness = CONE_SCROLL_STIFFNESS,
                        visibilityThreshold = CONE_SCROLL_VISIBILITY_THRESHOLD_PX
                    )
                ) {
                    val consumed = scrollBy(value - appliedValue)
                    appliedValue += consumed
                }
            }
        }
    }
    val defaultTextAlign = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(lineSpacing),
        userScrollEnabled = userScrollEnabled,
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
                userScrolling = userDragging || keepLinesSharp,
                nonCurrentLineBlurEnabled = nonCurrentLineBlurEnabled,
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
                wordLiftEnabled = wordLiftEnabled,
                onClick = { onLineClick(line) },
                onDoubleClick = onLineDoubleClick,
                onLongClick = { onLineLongClick(line) }
            )
            }
        }
    }
}

/** Shared single-line surface used by the system desktop-lyrics overlay. */
@Composable
internal fun AppleMusicSingleLyricLine(
    line: LyricLine,
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
    wordLiftEnabled: Boolean,
    singleLine: Boolean,
    secondaryAlpha: Float = 0.74f,
    modifier: Modifier = Modifier
) {
    val defaultTextAlign = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }
    AppleMusicLyricLine(
        line = line,
        active = true,
        distance = 0,
        userScrolling = true,
        nonCurrentLineBlurEnabled = false,
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
        wordLiftEnabled = wordLiftEnabled,
        singleLine = singleLine,
        secondaryAlpha = secondaryAlpha,
        onClick = {},
        onDoubleClick = {},
        onLongClick = {},
        modifier = modifier
    )
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
    nonCurrentLineBlurEnabled: Boolean,
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
    wordLiftEnabled: Boolean,
    singleLine: Boolean = false,
    secondaryAlpha: Float = 0.74f,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textAlign = line.duetTextAlign(defaultTextAlign)
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.91f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
        label = "appleLyricsScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else (0.24f - abs(distance) * 0.025f).coerceAtLeast(0.13f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "appleLyricsAlpha"
    )
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
        color = contentColor.copy(alpha = alpha * secondaryAlpha.coerceIn(0f, 1f)),
        textAlign = textAlign
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = (distance * -2f) * density
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (textAlign) {
                        TextAlign.End -> 1f
                        TextAlign.Center -> 0.5f
                        else -> 0f
                    },
                    pivotFractionY = 0.5f
                )
            }
            .then(
                if (nonCurrentLineBlurEnabled && !userScrolling && !active && abs(distance) >= 2) {
                    Modifier.blur((2 + abs(distance)).dp)
                } else {
                    Modifier
                }
            )
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
            wordLiftEnabled = wordLiftEnabled,
            singleLine = singleLine,
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
                // ConePlayer gives BG vocals their own reveal: the main line settles first, then
                // the x-bg layer enters after a 300 ms beat. A full-height upward travel reads as
                // a separate backing vocal instead of a translation suddenly growing the row.
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        delayMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = 300,
                        delayMillis = 300,
                        easing = FastOutSlowInEasing
                    ),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(animationSpec = tween(180)) +
                    slideOutVertically(animationSpec = tween(180), targetOffsetY = { it / 3 })
            ) {
                Column {
            TimedLyricText(
                text = background,
                words = line.backgroundWords,
                positionMs = currentPositionMs,
                active = active,
                style = secondaryStyle.copy(color = contentColor.copy(alpha = alpha * 0.72f)),
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                singleLine = singleLine,
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
    wordLiftEnabled: Boolean,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timedWords = remember(text, words) { words.toAppleMusicRenderWords(text) }
    if (timedWords.isEmpty()) {
        BasicText(text = text, style = style, modifier = modifier)
        return
    }
    // Keep the timed units as individual layout children. This is the same important distinction
    // as the smooth renderer: a long timed line breaks between singable units, not at arbitrary
    // glyphs, so highlighted and dim lines retain identical visual rows.
    val horizontalArrangement = when (style.textAlign) {
        TextAlign.End -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }
    val content: @Composable () -> Unit = {
        timedWords.forEach { renderWord ->
            AppleMusicKaraokeWord(
                renderWord = renderWord,
                positionMs = positionMs,
                active = active,
                baseStyle = style,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled
            )
        }
    }
    if (singleLine) {
        Row(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AppleMusicKaraokeWord(
    renderWord: AppleMusicRenderWord,
    positionMs: Long,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean
 ) {
    val word = renderWord.word
    val progress = if (active) ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    else 0f
    val bright = contentColor.copy(alpha = baseStyle.color.alpha)
    val dim = contentColor.copy(alpha = baseStyle.color.alpha * 0.36f)
    val sustainGlow = renderWord.sustainGlowAlpha(positionMs, active)
    val textSizePx = with(LocalDensity.current) { baseStyle.fontSize.toPx() }
    // The reference renderer moves each word independently by 6% of the text size (at least
    // 5 px), then adds only a 3% bottom-anchored scale during the held-note phase. Keeping the
    // transform on the word rather than the whole line is what creates the floating vocal feel.
    val liftPx = if (wordLiftEnabled) maxOf(textSizePx * 0.06f, 5f) * progress else 0f
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = -liftPx
            scaleX = 1f + 0.03f * sustainGlow
            scaleY = 1f + 0.03f * sustainGlow
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
        val glowShadow = sustainGlow.takeIf { it > 0f }?.let { glowAlpha ->
            Shadow(
                color = contentColor.copy(alpha = baseStyle.color.alpha * glowAlpha),
                offset = Offset.Zero,
                blurRadius = 10f * glowAlpha
            )
        }
        when {
            progress <= 0f -> BasicText(text = word.text, style = baseStyle.copy(color = dim))
            progress >= 1f -> BasicText(
                text = word.text,
                style = baseStyle.copy(color = bright, shadow = glowShadow)
            )
            else -> {
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
                        ),
                        // The glow belongs to the primary karaoke layer, matching ConePlayer's
                        // TextPaint shadow. Attaching it to the narrow sheen made the halo look
                        // like a hard edge and disappear at the start of a held note.
                        shadow = glowShadow
                    )
                )
                // A narrow material sheen follows the karaoke edge. Long-held words strengthen
                // that band and add a restrained halo; ordinary words keep the feathered fill
                // without inheriting a permanent outline around the entire active line.
                val sheenStart = (progress - 0.20f).coerceAtLeast(0f)
                val sheenPeak = (progress - 0.055f).coerceIn(sheenStart, progress)
                val sheenEnd = (progress + 0.045f).coerceAtMost(1f)
                val sheenAlpha = (0.20f + sustainGlow * 0.42f) * baseStyle.color.alpha
                BasicText(
                    text = word.text,
                    style = baseStyle.copy(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                sheenStart to Color.Transparent,
                                sheenPeak to contentColor.copy(alpha = sheenAlpha),
                                sheenEnd to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                    )
                )
            }
        }
    }
}

private fun AppleMusicRenderWord.sustainGlowAlpha(positionMs: Long, active: Boolean): Float {
    val sustainEndMs = sustainEndMs ?: return 0f
    if (!active || positionMs !in word.startMs until sustainEndMs) return 0f
    val duration = sustainEndMs - word.startMs
    val elapsed = positionMs - word.startMs
    // ConePlayer starts the held-note envelope at the beginning of the marked word; it does not
    // wait for a separate attack delay. This is why its halo is already visible around the first
    // sung glyph in a long "Oh" rather than appearing halfway through the word.
    val progress = (elapsed.toFloat() / duration.coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    return if (progress < 0.7f) {
        sin((progress / 0.7f) * (PI.toFloat() / 2f))
    } else {
        cos(((progress - 0.7f) / 0.3f) * (PI.toFloat() / 2f))
    }.coerceIn(0f, 1f)
}

private data class AppleMusicRenderWord(
    val word: LyricWord,
    val sustainEndMs: Long? = null
)

private fun List<LyricWord>.toAppleMusicRenderWords(lineText: String): List<AppleMusicRenderWord> {
    if (isEmpty() || lineText.isBlank()) return emptyList()
    val result = mutableListOf<AppleMusicRenderWord>()
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
        val duration = word.endMs - word.startMs
        val splitForSustain = duration >= 1_200L &&
            word.text.length > 1 &&
            word.text.any { it in 'a'..'z' || it in 'A'..'Z' }
        if (splitForSustain) {
            val chars = word.text.toCharArray()
            val segmentDuration = duration / chars.size
            chars.forEachIndexed { charIndex, char ->
                val segmentStart = word.startMs + segmentDuration * charIndex
                val segmentEnd = if (charIndex == chars.lastIndex) {
                    word.endMs
                } else {
                    segmentStart + segmentDuration
                }
                result += AppleMusicRenderWord(
                    word = LyricWord(
                        text = char.toString() + if (charIndex == chars.lastIndex) suffix else "",
                        startMs = segmentStart,
                        endMs = segmentEnd
                    ),
                    sustainEndMs = word.endMs
                )
            }
        } else {
            result += AppleMusicRenderWord(word.copy(text = word.text + suffix))
        }
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
private const val MANUAL_SCROLL_BLUR_RESUME_DELAY_MS = 3_000L
private const val MANUAL_SCROLL_RECENTER_DELAY_MS = 2_000L
private const val CONE_SCROLL_DAMPING_RATIO = 1.25f
private const val CONE_SCROLL_STIFFNESS = 200f
private const val CONE_SCROLL_VISIBILITY_THRESHOLD_PX = 0.75f
private const val CONE_SCROLL_CORRECTION_PASSES = 2

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
