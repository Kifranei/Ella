package com.ella.music.player

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.ui.components.loadAndroidTypeface
import com.ella.music.ui.player.AppleMusicSingleLyricLine

/** Compose-backed renderer for the system overlay and status-bar lyric surfaces. */
internal class DesktopComposeLyricView(context: Context) : FrameLayout(context) {
    var windowTouchHandler: ((View, MotionEvent) -> Boolean)? = null

    private val composeLifecycleOwner = DesktopComposeLifecycleOwner()
    private var currentLine by mutableStateOf(
        LyricLine(timeMs = 0L, text = "Halcyon", endMs = 4_000L)
    )
    private var currentPositionMs by mutableLongStateOf(0L)
    private var playbackRunning by mutableStateOf(true)
    private var fontScale by mutableFloatStateOf(1f)
    private var translationScale by mutableFloatStateOf(1.1f)
    private var opacityPercent by mutableIntStateOf(100)
    private var textColor by mutableIntStateOf(AndroidColor.WHITE)
    private var statusBarMode by mutableStateOf(false)
    private var statusBarSecondaryMode by mutableIntStateOf(SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF)
    private var statusBarSecondaryOpacity by mutableIntStateOf(67)
    private var statusBarMergeSecondary by mutableStateOf(false)
    private var statusBarInlineSecondaryText by mutableStateOf("")
    private var statusBarTextAlign by mutableIntStateOf(SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT)
    private var statusBarVerticalAlign by mutableIntStateOf(SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP)
    private var lyricFontPath by mutableStateOf("")
    private var lyricFontWeight by mutableIntStateOf(800)
    private var lyricFontItalic by mutableStateOf(false)
    private var wordLiftEnabled by mutableStateOf(true)

    init {
        setViewTreeLifecycleOwner(composeLifecycleOwner)
        composeLifecycleOwner.start()
        addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    DesktopLyricContent()
                }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean =
        windowTouchHandler?.invoke(this, event) ?: true

    override fun onDetachedFromWindow() {
        composeLifecycleOwner.destroy()
        super.onDetachedFromWindow()
    }

    fun setPlaybackActive(isPlaying: Boolean) {
        playbackRunning = isPlaying
    }

    fun setStyle(
        fontScale: Float,
        translationScale: Float,
        opacityPercent: Int,
        textColor: Int,
        statusBarMode: Boolean = false,
        statusBarSecondaryMode: Int = SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF,
        statusBarSecondaryOpacity: Int = 67,
        statusBarMergeSecondary: Boolean = false,
        statusBarTextAlign: Int = SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT,
        statusBarVerticalAlign: Int = SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP,
        lyricFontPath: String = "",
        lyricFontWeight: Int = 800,
        lyricFontItalic: Boolean = false,
        wordLiftEnabled: Boolean = true
    ) {
        this.fontScale = fontScale.coerceIn(0.8f, 2.2f)
        this.translationScale = translationScale.coerceIn(0.8f, 2.2f)
        this.opacityPercent = opacityPercent.coerceIn(35, 100)
        this.textColor = textColor
        this.statusBarMode = statusBarMode
        this.statusBarSecondaryMode = statusBarSecondaryMode.coerceIn(0, 2)
        this.statusBarSecondaryOpacity = statusBarSecondaryOpacity.coerceIn(20, 100)
        this.statusBarMergeSecondary = statusBarMergeSecondary
        this.statusBarTextAlign = statusBarTextAlign.coerceIn(0, 2)
        this.statusBarVerticalAlign = statusBarVerticalAlign.coerceIn(0, 2)
        this.lyricFontPath = lyricFontPath
        this.lyricFontWeight = lyricFontWeight.coerceIn(100, 900)
        this.lyricFontItalic = lyricFontItalic
        this.wordLiftEnabled = wordLiftEnabled

    }

    fun setLyric(
        text: String,
        pronunciation: String,
        translation: String,
        positionMs: Long,
        lineStartMs: Long,
        lineEndMs: Long?,
        agent: String,
        isTtml: Boolean,
        backgroundText: String,
        backgroundTranslation: String,
        backgroundStartMs: Long?,
        backgroundEndMs: Long?,
        wordTexts: List<String>,
        wordStarts: LongArray,
        wordEnds: LongArray,
        pronunciationWordTexts: List<String>,
        pronunciationWordStarts: LongArray,
        pronunciationWordEnds: LongArray,
        backgroundWordTexts: List<String>,
        backgroundWordStarts: LongArray,
        backgroundWordEnds: LongArray
    ) {
        currentPositionMs = positionMs
        val words = buildLyricWords(wordTexts, wordStarts, wordEnds)
        val pronunciationWords = buildLyricWords(
            pronunciationWordTexts,
            pronunciationWordStarts,
            pronunciationWordEnds
        )
        val backgroundWords = buildLyricWords(
            backgroundWordTexts,
            backgroundWordStarts,
            backgroundWordEnds
        )
        val inferredStart = sequenceOf(
            lineStartMs.takeIf { it >= 0L },
            words.minOfOrNull { it.startMs },
            pronunciationWords.minOfOrNull { it.startMs },
            backgroundStartMs,
            backgroundWords.minOfOrNull { it.startMs },
            positionMs
        ).filterNotNull().first()
        val inferredEnd = sequenceOf(
            lineEndMs,
            words.maxOfOrNull { it.endMs },
            pronunciationWords.maxOfOrNull { it.endMs },
            backgroundEndMs,
            backgroundWords.maxOfOrNull { it.endMs },
            inferredStart + 4_000L
        ).filterNotNull().first().coerceAtLeast(inferredStart + 1L)

        val inferredPronunciation = pronunciation.ifBlank {
            when {
                isLikelyRomanizationSecondary(text, translation) -> translation
                isLikelyRomanizationSecondary(backgroundText.ifBlank { text }, backgroundTranslation) -> {
                    backgroundTranslation
                }
                else -> ""
            }
        }
        val displayTranslation = if (
            pronunciation.isBlank() && isLikelyRomanizationSecondary(text, translation)
        ) "" else translation
        val displayBackgroundTranslation = if (
            pronunciation.isBlank() &&
            isLikelyRomanizationSecondary(backgroundText.ifBlank { text }, backgroundTranslation)
        ) "" else backgroundTranslation

        val mainText = text.ifBlank { backgroundText }.ifBlank { "♪" }
        val statusBarSecondaryText = when (statusBarSecondaryMode) {
                SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION -> displayTranslation
                SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION -> inferredPronunciation
                else -> ""
            }.trim()
        currentLine = if (statusBarMode) {
            LyricLine(
                timeMs = inferredStart,
                text = mainText,
                words = if (text.isBlank() && backgroundText.isNotBlank()) backgroundWords else words,
                translation = if (
                    !statusBarMergeSecondary &&
                    statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION
                ) displayTranslation else null,
                pronunciation = if (
                    !statusBarMergeSecondary &&
                    statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION
                ) inferredPronunciation else null,
                pronunciationWords = if (
                    !statusBarMergeSecondary &&
                    statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION
                ) pronunciationWords else emptyList(),
                isTtml = isTtml,
                endMs = inferredEnd
            )
        } else {
            LyricLine(
                timeMs = inferredStart,
                text = text,
                words = words,
                translation = displayTranslation,
                pronunciation = inferredPronunciation,
                pronunciationWords = pronunciationWords,
                agent = agent,
                backgroundText = backgroundText,
                backgroundWords = backgroundWords,
                backgroundTranslation = displayBackgroundTranslation,
                backgroundStartMs = backgroundStartMs,
                backgroundEndMs = backgroundEndMs,
                isTtml = isTtml,
                endMs = inferredEnd
            )
        }
        statusBarInlineSecondaryText = if (statusBarMode && statusBarMergeSecondary) {
            statusBarSecondaryText
        } else {
            ""
        }
    }

    @Composable
    private fun DesktopLyricContent() {
        val line = currentLine
        var smoothPositionMs by remember { mutableLongStateOf(currentPositionMs) }
        LaunchedEffect(currentPositionMs, playbackRunning) {
            val anchorPositionMs = currentPositionMs
            val anchorFrameNs = withFrameNanos { it }
            smoothPositionMs = anchorPositionMs
            while (playbackRunning) {
                val frameNs = withFrameNanos { it }
                smoothPositionMs = anchorPositionMs + ((frameNs - anchorFrameNs) / 1_000_000L)
            }
        }
        val fontFamily = remember(lyricFontPath, lyricFontWeight, lyricFontItalic) {
            FontFamily(
                loadAndroidTypeface(
                    fontPath = lyricFontPath,
                    weight = lyricFontWeight,
                    italic = lyricFontItalic,
                    boldFallback = true
                )
            )
        }
        val effectiveAlign = when {
            statusBarMode -> statusBarTextAlign
            !line.isTtml && !line.agent.isDuetAgent() -> SettingsManager.PLAYER_LYRIC_ALIGN_CENTER
            else -> SettingsManager.PLAYER_LYRIC_ALIGN_LEFT
        }
        val verticalAlignment = when {
            !statusBarMode -> Alignment.Center
            statusBarVerticalAlign == SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_CENTER -> Alignment.Center
            statusBarVerticalAlign == SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_BOTTOM -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (statusBarMode) 2.dp else 6.dp),
            contentAlignment = verticalAlignment
        ) {
            AppleMusicSingleLyricLine(
                line = line,
                currentPositionMs = smoothPositionMs,
                showTranslation = true,
                showPronunciation = true,
                fontFamily = fontFamily,
                fontWeight = FontWeight(lyricFontWeight),
                fontScale = fontScale,
                secondaryFontScale = translationScale,
                primaryTextSizeSp = if (statusBarMode) 12.5f else 24f,
                secondaryTextSizeSp = if (statusBarMode) 9.5f else 14f,
                lyricTextAlign = effectiveAlign,
                contentColor = Color(textColor).copy(alpha = opacityPercent / 100f),
                wordLiftEnabled = wordLiftEnabled,
                singleLine = statusBarMode,
                inlineStaticSecondaryText = statusBarInlineSecondaryText,
                statusBarMarquee = statusBarMode,
                secondaryAlpha = if (statusBarMode) statusBarSecondaryOpacity / 100f else 0.74f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    private fun buildLyricWords(
        texts: List<String>,
        starts: LongArray,
        ends: LongArray
    ): List<LyricWord> = texts.mapIndexedNotNull { index, text ->
        val start = starts.getOrNull(index) ?: return@mapIndexedNotNull null
        val end = ends.getOrNull(index) ?: return@mapIndexedNotNull null
        if (text.isBlank() || end <= start) return@mapIndexedNotNull null
        LyricWord(text = text, startMs = start, endMs = end)
    }

    private fun isLikelyRomanizationSecondary(primary: String, candidate: String): Boolean {
        val primaryText = primary.takeIf { it.isNotBlank() } ?: return false
        val secondary = candidate.trim().takeIf { it.isNotBlank() } ?: return false
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
    private fun String?.isDuetAgent(): Boolean =
        equals("v1", ignoreCase = true) || equals("v2", ignoreCase = true)
}

private class DesktopComposeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private var destroyed = false

    override val lifecycle: Lifecycle = registry

    fun start() {
        if (destroyed) return
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

internal fun mergeDesktopStatusBarLyric(
    mainText: String,
    secondaryText: String,
    mergeSecondary: Boolean
): String = if (mergeSecondary && secondaryText.isNotBlank()) {
    "${mainText.trimEnd()} ${secondaryText.trimStart()}"
} else {
    mainText
}
