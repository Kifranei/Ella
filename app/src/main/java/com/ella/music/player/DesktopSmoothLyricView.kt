package com.ella.music.player

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.ui.components.buildLyriconRichLineConfig
import com.ella.music.ui.components.loadAndroidTypeface
import com.ella.music.ui.components.toLyriconWords
import com.ella.music.ui.components.toLyriconSong
import io.github.proify.lyricon.lyric.view.LyricView
import kotlin.math.roundToInt

internal class DesktopSmoothLyricView(context: Context) : FrameLayout(context) {
    var windowTouchHandler: ((View, MotionEvent) -> Boolean)? = null

    private val lyricView = LyricView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setPadding(0, 0, 0, 0)
        setEdgeFadeEnabled(false)
        setLineAlphaAnimationsEnabled(false)
        setNonCurrentLineBlurEnabled(false)
        setContinuousFrameUpdatesEnabled(true)
        setPronunciationAboveMainEnabled(true)
        setAutoScrollResumeEnabled(false)
        updateDisplayTranslation(true, true)
    }
    private var currentLine: LyricLine = LyricLine(timeMs = 0L, text = "Halcyon", endMs = 4_000L)
    private var currentPositionMs = 0L
    private var fontScale = 1f
    private var translationScale = 1.1f
    private var opacityPercent = 100
    private var textColor = Color.WHITE
    private var statusBarMode = false
    private var statusBarSecondaryMode = SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF
    private var statusBarSecondaryOpacity = 67
    private var statusBarMergeSecondary = false
    private var statusBarTextAlign = SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT
    private var statusBarVerticalAlign = SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP
    private var lyricFontPath = ""
    private var lyricFontWeight = 800
    private var lyricFontItalic = false
    private var songKey: String? = null

    init {
        addView(lyricView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return windowTouchHandler?.invoke(this, event) ?: true
    }

    fun setPlaybackActive(isPlaying: Boolean) {
        lyricView.setPlaybackActive(isPlaying)
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
        lyricFontItalic: Boolean = false
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
        lyricView.setSingleLineMarqueeEnabled(statusBarMode)
        lyricView.setMaxMainLines(if (statusBarMode) 1 else 0)
        lyricView.setForcedTextAlignment(if (statusBarMode) this.statusBarTextAlign else -1)
        lyricView.setForcedVerticalAlignment(if (statusBarMode) this.statusBarVerticalAlign else 0)
        this.lyricFontPath = lyricFontPath
        this.lyricFontWeight = lyricFontWeight.coerceIn(100, 900)
        this.lyricFontItalic = lyricFontItalic
        applySmoothStyle()
        updateSong(force = true)
    }

    fun setLyric(
        text: String, pronunciation: String, translation: String, positionMs: Long,
        lineStartMs: Long, lineEndMs: Long?, agent: String, isTtml: Boolean,
        backgroundText: String, backgroundTranslation: String,
        backgroundStartMs: Long?, backgroundEndMs: Long?,
        wordTexts: List<String>, wordStarts: LongArray, wordEnds: LongArray,
        pronunciationWordTexts: List<String>, pronunciationWordStarts: LongArray, pronunciationWordEnds: LongArray,
        backgroundWordTexts: List<String>, backgroundWordStarts: LongArray, backgroundWordEnds: LongArray
    ) {
        currentPositionMs = positionMs
        val words = buildLyricWords(wordTexts, wordStarts, wordEnds)
        val pronunciationWords = buildLyricWords(pronunciationWordTexts, pronunciationWordStarts, pronunciationWordEnds)
        val backgroundWords = buildLyricWords(backgroundWordTexts, backgroundWordStarts, backgroundWordEnds)
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
                isLikelyRomanizationSecondary(backgroundText.ifBlank { text }, backgroundTranslation) -> backgroundTranslation
                else -> ""
            }
        }
        val displayTranslation = if (pronunciation.isBlank() && isLikelyRomanizationSecondary(text, translation)) "" else translation
        val displayBackgroundTranslation = if (
            pronunciation.isBlank() && isLikelyRomanizationSecondary(backgroundText.ifBlank { text }, backgroundTranslation)
        ) "" else backgroundTranslation

        currentLine = if (statusBarMode) {
            val mainText = text.ifBlank { backgroundText }.ifBlank { "♪" }
            val secondaryText = when (statusBarSecondaryMode) {
                SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION -> displayTranslation
                SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION -> inferredPronunciation
                else -> ""
            }.trim()
            val mergedMainText = mergeDesktopStatusBarLyric(mainText, secondaryText, statusBarMergeSecondary)
            LyricLine(
                timeMs = inferredStart, text = mergedMainText,
                words = if (text.isBlank() && backgroundText.isNotBlank()) backgroundWords else words,
                translation = if (!statusBarMergeSecondary && statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION) displayTranslation else null,
                pronunciation = if (!statusBarMergeSecondary && statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION) inferredPronunciation else null,
                pronunciationWords = if (!statusBarMergeSecondary && statusBarSecondaryMode == SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION) pronunciationWords else emptyList(),
                agent = null, isTtml = isTtml, endMs = inferredEnd
            )
        } else {
            LyricLine(
                timeMs = inferredStart, text = text, words = words,
                translation = displayTranslation, pronunciation = inferredPronunciation,
                pronunciationWords = pronunciationWords, agent = agent,
                backgroundText = backgroundText, backgroundWords = backgroundWords,
                backgroundTranslation = displayBackgroundTranslation,
                backgroundStartMs = backgroundStartMs, backgroundEndMs = backgroundEndMs,
                isTtml = isTtml, endMs = inferredEnd
            )
        }
        updateSong(force = false)
        lyricView.setPosition(positionMs)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateLyricLayoutOffsets()
    }

    private fun applySmoothStyle() {
        val scaledDensity = resources.displayMetrics.scaledDensity
        val primarySp = if (statusBarMode) 12.5f else 24f
        val secondarySp = if (statusBarMode) 9.5f else 14f
        val secondaryAlpha = if (statusBarMode) (255 * (statusBarSecondaryOpacity / 100f)).roundToInt() else 190
        val primaryTypeface = loadAndroidTypeface(fontPath = lyricFontPath, weight = lyricFontWeight, italic = lyricFontItalic, boldFallback = true)
        val secondaryTypeface = loadAndroidTypeface(fontPath = lyricFontPath, weight = (lyricFontWeight - 200).coerceIn(100, 900), italic = lyricFontItalic, boldFallback = false)
        lyricView.setStyle(
            buildLyriconRichLineConfig(
                primaryTextSizePx = primarySp * scaledDensity * fontScale,
                secondaryTextSizePx = secondarySp * scaledDensity * fontScale * translationScale,
                primaryTypeface = primaryTypeface, secondaryTypeface = secondaryTypeface,
                primaryTextColor = colorWithAlpha(textColor, 255),
                secondaryTextColor = colorWithAlpha(textColor, secondaryAlpha),
                syllableHighlightColor = colorWithAlpha(textColor, 255),
                syllableBackgroundColor = colorWithAlpha(textColor, if (statusBarMode) 30 else 42)
            )
        )
        lyricView.updateDisplayTranslation(true, true)
        updateLyricLayoutOffsets()
    }

    private fun updateSong(force: Boolean) {
        val pronunciationWordsByBegin = currentLine.pronunciationWords.toLyriconWords()
            .takeIf { it.isNotEmpty() }
            ?.let { mapOf(currentLine.timeMs to it) }
            ?: emptyMap()
        val key = "${currentLine.timeMs}|${currentLine.endMs}|${currentLine.text}|${currentLine.translation}|${currentLine.pronunciation}|${currentLine.pronunciationWords.hashCode()}|${currentLine.backgroundText}|${currentLine.agent}|${currentLine.isTtml}|$statusBarMode|$statusBarSecondaryMode|$statusBarSecondaryOpacity|$statusBarMergeSecondary|$statusBarTextAlign|$statusBarVerticalAlign"
        if (!force && key == songKey) {
            lyricView.setPronunciationWordsByBegin(pronunciationWordsByBegin)
            lyricView.setPosition(currentPositionMs)
            return
        }
        lyricView.setCenterUnalignedLinesEnabled(!statusBarMode && currentLine.shouldCenterUnalignedDesktopLine())
        val currentSong = listOf(currentLine).toLyriconSong(songId = -1L, songTitle = "Halcyon", songArtist = "")
        lyricView.setPronunciationWordsByBegin(pronunciationWordsByBegin)
        lyricView.song = currentSong
        lyricView.tag = currentSong
        songKey = key
    }

    private fun LyricLine.shouldCenterUnalignedDesktopLine(): Boolean = !isTtml && !agent.isDuetAgent()
    private fun String?.isDuetAgent(): Boolean = equals("v1", ignoreCase = true) || equals("v2", ignoreCase = true)

    private fun updateLyricLayoutOffsets() {
        if (height <= 0) return
        val statusOffset = when (statusBarVerticalAlign) {
            SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_CENTER -> 0f
            SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_BOTTOM -> height * 0.40f
            else -> -height * 0.42f
        }
        lyricView.updateAnchorOffset(if (statusBarMode) statusOffset else 0f)
        lyricView.setTopContentPadding(0f)
    }

    private fun buildLyricWords(texts: List<String>, starts: LongArray, ends: LongArray): List<LyricWord> =
        texts.mapIndexedNotNull { index, text ->
            val start = starts.getOrNull(index) ?: return@mapIndexedNotNull null
            val end = ends.getOrNull(index) ?: return@mapIndexedNotNull null
            if (text.isBlank() || end <= start) return@mapIndexedNotNull null
            LyricWord(text = text, startMs = start, endMs = end)
        }

    private fun colorWithAlpha(color: Int, alpha: Int): Int {
        val appliedAlpha = (alpha * (opacityPercent / 100f)).roundToInt().coerceIn(0, 255)
        return Color.argb(appliedAlpha, Color.red(color), Color.green(color), Color.blue(color))
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
