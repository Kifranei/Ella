package io.github.proify.lyricon.lyric.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.OverScroller
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.interfaces.ILyricWord
import android.view.Choreographer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), UpdatableColor {

    companion object {
        private const val LINE_MIN_ALPHA = 0.18f
        private const val LINE_MAX_ALPHA = 0.94f
        private const val FADE_LINES = 4
        private const val SCALE_HIGHLIGHT = 1.0f
        private const val SCALE_NORMAL = 0.98f
        private const val POP_SCALE = 1.0f
        private const val POP_UP_MS = 300L
        private const val POP_DOWN_MS = 200L
        private const val EXIT_MS = 250L
        private const val LAYOUT_SHIFT_MS = 280L
        private const val BOTTOM_FADE_RATIO = 0.2775f
        private const val TOP_OFFSET_RATIO = 0.08f
        private const val BOTTOM_OFFSET_RATIO = 0.5f
        private const val LINE_GAP_DP = 12f
        private const val LINE_PAD_TOP_DP = 2f
        private const val LINE_PAD_BOTTOM_DP = 6f
        private const val TRANS_GAP_DP = 3.5f
        private const val FEATHER_WIDTH_DP = 30f
        private const val TEXT_CLIP_BLEED_DP = 4f
        private const val RIGHT_ALIGN_GLYPH_SAFE_INSET_DP = 3f
        private const val TEXT_EDGE_SAFE_INSET_DP = 10f
        private const val SCROLL_ANIM_MS = 400L
        private const val AUTO_SCROLL_RESUME_MS = 5000L
        private const val INTERLUDE_MIN_GAP_MS = 7000L
        private const val INTERLUDE_DOT_SIZE_DP = 10f
        private const val INTERLUDE_DOT_SPACING_DP = 6f
        private const val INTERLUDE_ENTER_MS = 750L
        private const val INTERLUDE_PULSE_MS = 4000f
        private const val INTERLUDE_PULSE_AMPLITUDE = 0.2f
        private const val INTERLUDE_EXIT_UP_MS = 750L
        private const val INTERLUDE_EXIT_DOWN_MS = 250L
        private const val INTERLUDE_EXTRA_DP = 48f
        private const val INTERLUDE_EXPAND_MS = 500L
        private const val INTERLUDE_COLLAPSE_MS = 300L
        private const val MARQUEE_HOLD_MS = 900L
        private const val MARQUEE_SPEED_DP_PER_SEC = 36f
        private const val MARQUEE_RESTART_GAP_DP = 48f
        private const val LINE_BLUR_RADIUS_DP = 4f
        private const val LINE_OFFSET_MIN_MS = 480L
        private const val LINE_OFFSET_MAX_MS = 750L
        private const val LINE_OFFSET_GAP_MIN_MS = 200L
        private const val LINE_OFFSET_GAP_MAX_MS = 750L
        private const val KARAOKE_WORD_OFFSET_MS = 100L
        private const val SECONDARY_TRANSLATION_SEPARATOR = "\u000B"
    }

    private val density = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f * sp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f * sp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dimPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f * sp
        typeface = Typeface.DEFAULT
    }
    private val transPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * sp
        typeface = Typeface.DEFAULT
    }
    private val hlTransPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * sp
        typeface = Typeface.DEFAULT
    }
    private val dimTransPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * sp
        typeface = Typeface.DEFAULT
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f * sp
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    private var lyrics: List<IRichLyricLine> = emptyList()
    private var lyricWindows: List<LyricViewLineWindow> = emptyList()
    private var currentIndex = -1
    private var activeHighlightIndices: Set<Int> = emptySet()
    private var currentPosMs = 0L
    private var lastPositionWallTime = 0L
    private var playbackActive = true
    private var lineOffsetMs = LINE_OFFSET_MIN_MS
    private var displayTranslation = false
    private var displayRoma = false
    private var anchorOffsetPx = 0f
    private var edgeFadeEnabled = false
    private var fullLayerBlurEnabled = false
    private var nonCurrentLineBlurEnabled = false
    private var nonCurrentLineBlurDistance = 2
    private var continuousFrameUpdatesEnabled = true
    private var lineAlphaAnimationsEnabled = true
    private var pronunciationAboveMainEnabled = false
    private var autoScrollResumeEnabled = true
    private var userScrollEnabled = true
    private var centerUnalignedLinesEnabled = false
    private var forcedTextAlignment = -1 // -1 = lyric metadata, 0 = left, 1 = center, 2 = right
    private var forcedVerticalAlignment = -1 // -1 = lyric scroll anchor, 0 = top, 1 = center, 2 = bottom
    private var maxMainLines = 0 // 0 = unlimited
    private var singleLineMarqueeEnabled = false
    private var placeholderFormat = PlaceholderFormat.NAME_ARTIST
    private var currentStyleConfig: RichLyricLineConfig? = null
    private var songName: String? = null
    private var songArtist: String? = null
    private var pronunciationWordsByBegin: Map<Long, List<LyricWord>> = emptyMap()
    private var enableAnim = true
    private var topContentPadding = 0f

    private var hlColor = Color.WHITE
    private var dimColor = Color.argb(90, 255, 255, 255)
    private var transColor = Color.argb(140, 255, 255, 255)
    private var hlTransColor = Color.argb(200, 255, 255, 255)
    private var dimTransColor = Color.argb(60, 255, 255, 255)

    private var lineGapPx = LINE_GAP_DP * density
    private val linePadTopPx = LINE_PAD_TOP_DP * density
    private val linePadBottomPx = LINE_PAD_BOTTOM_DP * density
    private val transGapPx = TRANS_GAP_DP * density
    private val featherWidthPx = FEATHER_WIDTH_DP * density
    private val lineBlurRadiusPx = LINE_BLUR_RADIUS_DP * density
    private var blurRenderNode: RenderNode? = null
    private var blurNodeW = 0
    private var blurNodeH = 0

    private var scrollY = 0f
    private var maxScrollY = 0f
    private var isUserScrolling = false
    private var autoScrollResumeTime = 0L
    private var autoScrollResumeLineIndex = -1
    private var isDragging = false
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var lastTouchY = 0f
    private var longPressHandled = false
    private var velocityTracker: VelocityTracker? = null
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var scrollAnimator: ValueAnimator? = null
    private var positionInitialized = false
    private var secondaryVisibilitySignature = 0

    private val popPathUp = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f)
    private val popPathDown = PathInterpolator(0.25f, 0.0f, 1.0f, 0.2f)
    private val scrollPath = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
    private val lineScales = ConcurrentHashMap<Int, Float>()
    private val popAnimators = ConcurrentHashMap<Int, ValueAnimator>()
    private var previousIndex = -1

    private var totalHeight = 0f
    private val lineAlphas = ConcurrentHashMap<Int, Float>()
    private var layoutShiftAnimator: ValueAnimator? = null
    private var layoutShiftProgress = 1f
    private var layoutShiftOffsetsByBegin: Map<Long, Float> = emptyMap()
    private data class LineEntry(
        val yTop: Float,
        val preH: Float,
        val mainH: Float,
        val secondaryH: Float,
        val secondaryTranslationH: Float,
        val transH: Float,
        val romaH: Float,
        val totalH: Float,
        val preText: String?,
        val mainText: String?,
        val secondaryText: String?,
        val secondaryTranslationText: String?,
        val transText: String?,
        val romaText: String?,
        val words: List<LyricWord>?,
        val pronunciationWords: List<LyricWord>?,
        val secondaryWords: List<LyricWord>?,
        val secondaryStart: Long?,
        val secondaryEnd: Long?,
        val alignedRight: Boolean,
        val centered: Boolean,
        val begin: Long,
        val end: Long,
    )
    private data class KaraokeWordDrawInfo(
        val word: LyricWord,
        val text: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val visualLine: Int,
    )
    private var entries = listOf<LineEntry>()
    private val distantLineBlur = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
    private val sustainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val choreographer = Choreographer.getInstance()
    private var framePosted = false
    private val frameCb = Choreographer.FrameCallback {
        framePosted = false
        tickAnimations()
        invalidate()
        postFrame()
    }

    private val interludePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argbEvaluator = ArgbEvaluator()
    // Derived from the current text color so the interlude dots invert on a light theme too.
    private var interludeColorDim = Color.argb(46, 255, 255, 255)
    private var interludeColorBright = Color.argb(240, 255, 255, 255)
    private val interludeColorPath = PathInterpolator(0f, 0.25f, 1f, 0.58f)
    private val interludeAccel = AccelerateInterpolator()
    private val interludeExitUpPath = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f)
    private val interludeExitDownPath = PathInterpolator(0.25f, 0.0f, 1.0f, 0.2f)
    private var interludePrevIdx = -1
    private var interludeNextIdx = -1
    private var interludeGapStart = -1L
    private var interludeGapEnd = -1L
    private var interludeEnterStart = 0L
    private var interludeExpandProgress = 0f
    private var interludeExpandStartTime = 0L
    private var interludeCollapsing = false
    private var interludeCollapseStartTime = 0L
    private val dotSizePx get() = INTERLUDE_DOT_SIZE_DP * density
    private val dotSpacingPx get() = INTERLUDE_DOT_SPACING_DP * density

    val lyricCountChangeListeners = CopyOnWriteArraySet<LyricCountChangeListener>()

    interface LyricCountChangeListener {
        fun onLyricTextChanged(old: String, new: String) {}
        fun onLyricChanged(news: List<IRichLyricLine>, removes: List<IRichLyricLine>) {}
    }

    interface OnLineClickListener {
        fun onLineClick(beginMs: Long)
    }
    var onLineClickListener: OnLineClickListener? = null

    fun interface OnLineDoubleClickListener {
        fun onLineDoubleClick(beginMs: Long)
    }
    var onLineDoubleClickListener: OnLineDoubleClickListener? = null

    fun interface OnLineLongClickListener {
        fun onLineLongClick(beginMs: Long)
    }
    var onLineLongClickListener: OnLineLongClickListener? = null

    fun setCenterUnalignedLinesEnabled(enabled: Boolean) {
        if (centerUnalignedLinesEnabled == enabled) return
        centerUnalignedLinesEnabled = enabled
        rebuildEntries()
        invalidate()
    }

    fun setForcedTextAlignment(alignment: Int) {
        val safeAlignment = alignment.coerceIn(-1, 2)
        if (forcedTextAlignment == safeAlignment) return
        forcedTextAlignment = safeAlignment
        rebuildEntries()
        invalidate()
    }

    fun setForcedVerticalAlignment(alignment: Int) {
        val safeAlignment = alignment.coerceIn(-1, 2)
        if (forcedVerticalAlignment == safeAlignment) return
        forcedVerticalAlignment = safeAlignment
        rebuildEntries()
        scrollToCurrentLine(false)
        invalidate()
    }

    fun setMaxMainLines(lines: Int) {
        if (maxMainLines == lines) return
        maxMainLines = lines
        rebuildEntries()
        invalidate()
    }

    fun setSingleLineMarqueeEnabled(enabled: Boolean) {
        if (singleLineMarqueeEnabled == enabled) return
        singleLineMarqueeEnabled = enabled
        rebuildEntries()
        scrollToCurrentLine(false)
        invalidate()
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val tapY = e.y + scrollY
            for (entry in entries) {
                if (tapY in entry.yTop..(entry.yTop + entry.totalH)) {
                    onLineClickListener?.onLineClick(entry.begin)
                    return true
                }
            }
            return false
        }
        override fun onLongPress(e: MotionEvent) {
            if (isDragging) return
            val tapY = e.y + scrollY
            for (entry in entries) {
                if (tapY in entry.yTop..(entry.yTop + entry.totalH)) {
                    longPressHandled = true
                    onLineLongClickListener?.onLineLongClick(entry.begin)
                    break
                }
            }
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isDragging) return false
            val tapY = e.y + scrollY
            for (entry in entries) {
                if (tapY in entry.yTop..(entry.yTop + entry.totalH)) {
                    onLineDoubleClickListener?.onLineDoubleClick(entry.begin)
                    return true
                }
            }
            return false
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!isUserScrolling) return false
            scroller.fling(
                0, scrollY.toInt(), 0, (-velocityY).toInt(),
                0, 0, 0, maxScrollY.toInt()
            )
            autoScrollResumeTime = SystemClock.uptimeMillis() + AUTO_SCROLL_RESUME_MS
            invalidate()
            return true
        }
    })

    var song: Song? = null
        set(value) {
            val old = field
            field = value
            onSongChanged(old, value)
        }

    fun setPosition(positionMs: Long) {
        val previousPositionMs = currentPosMs
        currentPosMs = positionMs
        lastPositionWallTime = SystemClock.elapsedRealtime()
        val previousHighlights = activeHighlightIndices
        activeHighlightIndices = resolveActiveHighlightIndices(positionMs)
        updateSecondaryVisibilityIfNeeded()
        val newIndex = resolveLyricViewIndex(
            positionMs = positionMs,
            previousPositionMs = previousPositionMs,
            currentIndex = currentIndex,
            currentPreviewOffsetMs = lineOffsetMs,
            lines = lyricWindows
        )
        if (newIndex != currentIndex) {
            val shouldResumeForNextLine = autoScrollResumeEnabled &&
                isUserScrolling &&
                !isDragging &&
                autoScrollResumeLineIndex >= 0 &&
                newIndex != autoScrollResumeLineIndex
            onLineChanged(currentIndex, newIndex, previousHighlights, activeHighlightIndices)
            currentIndex = newIndex
            if (shouldResumeForNextLine) {
                isUserScrolling = false
                autoScrollResumeLineIndex = -1
            }
            if (!isUserScrolling) {
                scrollToCurrentLine(positionInitialized && playbackActive)
            }
            positionInitialized = true
            updateLineOffset(newIndex)
        } else if (!positionInitialized) {
            scrollToCurrentLine(false)
            positionInitialized = true
        }
        detectInterlude()
        invalidate()
        postFrame()
    }

    fun setPlaybackActive(active: Boolean) {
        if (playbackActive == active) return
        playbackActive = active
        lastPositionWallTime = if (active) SystemClock.elapsedRealtime() else 0L
        invalidate()
        postFrame()
    }

    fun updateDisplayTranslation(displayTranslation: Boolean, displayRoma: Boolean) {
        if (this.displayTranslation == displayTranslation && this.displayRoma == displayRoma) return
        this.displayTranslation = displayTranslation
        this.displayRoma = displayRoma
        rebuildEntries()
        invalidate()
    }

    fun setPronunciationWordsByBegin(wordsByBegin: Map<Long, List<LyricWord>>) {
        if (pronunciationWordsByBegin == wordsByBegin) return
        pronunciationWordsByBegin = wordsByBegin
        rebuildEntries()
        invalidate()
    }

    fun updateAnchorOffset(offset: Float) {
        if (abs(anchorOffsetPx - offset) < 0.5f) return
        anchorOffsetPx = offset
        scrollToCurrentLine(false)
        invalidate()
    }

    fun setEdgeFadeEnabled(enabled: Boolean) {
        if (edgeFadeEnabled == enabled) return
        edgeFadeEnabled = enabled
        invalidate()
    }

    fun setNonCurrentLineBlurEnabled(enabled: Boolean) {
        if (nonCurrentLineBlurEnabled == enabled) return
        nonCurrentLineBlurEnabled = enabled
        if (!enabled) clearBlurCache()
        invalidate()
    }

    fun setNonCurrentLineBlurDistance(distance: Int) {
        val safeDistance = distance.coerceAtLeast(1)
        if (nonCurrentLineBlurDistance == safeDistance) return
        nonCurrentLineBlurDistance = safeDistance
        invalidate()
    }

    fun setContinuousFrameUpdatesEnabled(enabled: Boolean) {
        if (continuousFrameUpdatesEnabled == enabled) return
        continuousFrameUpdatesEnabled = enabled
        if (!enabled && framePosted) {
            choreographer.removeFrameCallback(frameCb)
            framePosted = false
        } else {
            postFrame()
        }
    }

    fun setLineAlphaAnimationsEnabled(enabled: Boolean) {
        if (lineAlphaAnimationsEnabled == enabled) return
        lineAlphaAnimationsEnabled = enabled
        if (!enabled) lineAlphas.clear()
        invalidate()
    }

    fun setPronunciationAboveMainEnabled(enabled: Boolean) {
        if (pronunciationAboveMainEnabled == enabled) return
        pronunciationAboveMainEnabled = enabled
        rebuildEntries()
        scrollToCurrentLine(false)
        invalidate()
    }

    /** When false, the user can't scroll the lyrics by dragging (taps still work). */
    fun setUserScrollEnabled(enabled: Boolean) {
        userScrollEnabled = enabled
    }

    fun setAutoScrollResumeEnabled(enabled: Boolean) {
        if (autoScrollResumeEnabled == enabled) return
        autoScrollResumeEnabled = enabled
        if (!enabled) {
            autoScrollResumeTime = Long.MAX_VALUE
            autoScrollResumeLineIndex = -1
        } else if (isUserScrolling) {
            autoScrollResumeTime = SystemClock.uptimeMillis() + AUTO_SCROLL_RESUME_MS
            autoScrollResumeLineIndex = currentIndex
        }
    }

    fun setTopContentPadding(paddingPx: Float) {
        if (abs(topContentPadding - paddingPx) < 0.5f) return
        topContentPadding = paddingPx
        rebuildEntries()
        scrollToCurrentLine(false)
        invalidate()
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        if (primary.isNotEmpty()) {
            hlColor = primary[0]
            dimColor = blendAlpha(hlColor, LINE_MIN_ALPHA)
        }
        if (highlight.isNotEmpty()) {
            hlColor = highlight[0]
        }
        if (background.isNotEmpty()) {
            dimColor = background[0]
        }
        applyInterludeColors()
        applyColors()
        invalidate()
    }

    private fun applyInterludeColors() {
        interludeColorDim = blendAlpha(hlColor, 46f / 255f)
        interludeColorBright = blendAlpha(hlColor, 240f / 255f)
    }

    fun setStyle(config: RichLyricLineConfig) {
        if (currentStyleConfig == config) return
        currentStyleConfig = config
        val mainSize = config.primary.textSize
        if (mainSize > 0) {
            mainPaint.textSize = mainSize
            hlPaint.textSize = mainSize
            dimPaint.textSize = mainSize
        }
        val secSize = config.secondary.textSize
        if (secSize > 0) {
            transPaint.textSize = secSize
            hlTransPaint.textSize = secSize
            dimTransPaint.textSize = secSize
        }
        if (config.primary.textColor.isNotEmpty()) {
            hlColor = config.primary.textColor[0]
            dimColor = blendAlpha(hlColor, LINE_MIN_ALPHA)
        }
        if (config.syllable.highlightColor.isNotEmpty()) {
            hlColor = config.syllable.highlightColor[0]
        }
        if (config.syllable.backgroundColor.isNotEmpty()) {
            dimColor = config.syllable.backgroundColor[0]
        }
        if (config.secondary.textColor.isNotEmpty()) {
            transColor = blendAlpha(config.secondary.textColor[0], 0.55f)
            hlTransColor = blendAlpha(config.secondary.textColor[0], 0.8f)
            dimTransColor = blendAlpha(config.secondary.textColor[0], 0.25f)
        }
        applyInterludeColors()
        placeholderFormat = config.placeholderFormat
        enableAnim = config.enableAnim
        mainPaint.typeface = config.primary.typeface
        hlPaint.typeface = config.primary.typeface
        dimPaint.typeface = Typeface.create(config.primary.typeface, Typeface.NORMAL)
        transPaint.typeface = config.secondary.typeface
        hlTransPaint.typeface = config.secondary.typeface
        dimTransPaint.typeface = config.secondary.typeface
        applyColors()
        rebuildEntries()
        invalidate()
    }

    private fun applyColors() {
        hlPaint.color = hlColor
        dimPaint.color = dimColor
        mainPaint.color = hlColor
        hlTransPaint.color = hlTransColor
        transPaint.color = transColor
        dimTransPaint.color = dimTransColor
        placeholderPaint.color = dimColor
    }

    private fun onSongChanged(old: Song?, new: Song?) {
        val oldLines = old?.lyrics
        val newLines = new?.lyrics
        songName = new?.name
        songArtist = new?.artist
        lyrics = newLines ?: emptyList()
        lyricWindows = lyrics.map { LyricViewLineWindow(begin = it.begin, end = it.end) }
        currentIndex = -1
        activeHighlightIndices = emptySet()
        currentPosMs = 0L
        lineOffsetMs = LINE_OFFSET_MIN_MS
        secondaryVisibilitySignature = 0
        lineAlphas.clear()
        lineScales.clear()
        clearBlurCache()
        clearInterlude()
        popAnimators.values.toList().forEach { it.cancel() }
        popAnimators.clear()
        layoutShiftAnimator?.cancel()
        clearLayoutShiftAnimation()
        scrollAnimator?.cancel()
        scrollAnimator = null
        previousIndex = -1
        positionInitialized = false
        rebuildEntries()
        scrollY = 0f
        scrollToCurrentLine(false)
        val oldList: List<IRichLyricLine> = oldLines ?: emptyList()
        val newList: List<IRichLyricLine> = newLines ?: emptyList()
        lyricCountChangeListeners.forEach {
            it.onLyricChanged(newList, oldList)
            it.onLyricTextChanged(oldList.joinToString("\n") { l -> l.text ?: "" }, newList.joinToString("\n") { l -> l.text ?: "" })
        }
        requestLayout()
        invalidate()
    }

    /** Override the vertical gap between lyric lines (in dp). Pass a negative value to reset to default. */
    fun setLineGapDp(dp: Float) {
        val newGap = if (dp < 0f) LINE_GAP_DP * density else dp * density
        if (newGap == lineGapPx) return
        lineGapPx = newGap
        rebuildEntries()
        scrollToCurrentLine(false)
        requestLayout()
        invalidate()
    }

    private fun rebuildEntries() {
        val oldCentersByBegin = if (enableAnim && positionInitialized && !isUserScrolling && entries.isNotEmpty()) {
            entries.associate { it.begin to it.yTop + it.totalH / 2f }
        } else {
            emptyMap()
        }
        clearBlurCache()
        clearInterlude()
        val result = mutableListOf<LineEntry>()
        val minTopPad = if (height > 0) height * TOP_OFFSET_RATIO else 0f
        var y = max(topContentPadding, minTopPad)
        for (line in lyrics) {
            val mainText = line.text ?: continue
            val preText = if (pronunciationAboveMainEnabled && displayRoma && !line.roma.isNullOrBlank()) line.roma else null
            val pronunciationWords = preText?.let { pronunciationWordsByBegin[line.begin]?.takeIf { words -> words.isNotEmpty() } }
            val preH = preText?.let { max(measureTransHeight(it), measureWordsHeight(pronunciationWords, transPaint)) + transGapPx } ?: 0f
            val mainH = max(measureMainHeight(mainText), measureWordsHeight(line.words, mainPaint))
            val secondaryBlock = line.secondary?.splitSecondaryBlock()
            val secondaryText = secondaryBlock?.first?.takeIf { it.isNotBlank() }
            val secondaryTranslationText = secondaryBlock?.second?.takeIf { displayTranslation && it.isNotBlank() }
            val secondaryStart = line.secondaryWords?.minOfOrNull { it.begin }
            val secondaryEnd = line.secondaryWords?.maxOfOrNull { it.end }
            val lineIndex = result.size
            val secondaryVisible = secondaryText != null &&
                (isLineHighlighted(lineIndex) || isSecondaryVisible(secondaryStart, secondaryEnd, line.end, currentPosMs))
            val secondaryH = if (secondaryVisible) {
                max(measureTransHeight(secondaryText), measureWordsHeight(line.secondaryWords, transPaint)) + transGapPx
            } else {
                0f
            }
            val secondaryTranslationH = if (secondaryVisible) {
                secondaryTranslationText?.let { measureTransHeight(it) + transGapPx } ?: 0f
            } else {
                0f
            }
            var transH = 0f
            var romaH = 0f
            var transText: String? = null
            var romaText: String? = null
            // When pronunciation is shown BELOW the main line, romaji sits between the main line and
            // the translation (main → romaji → translation), so measure it independently instead of
            // sharing one exclusive "below" slot with the translation.
            if (displayRoma && !pronunciationAboveMainEnabled && !line.roma.isNullOrBlank()) {
                romaText = line.roma!!
                romaH = measureTransHeight(romaText) + transGapPx
            }
            if (displayTranslation && !line.translation.isNullOrBlank()) {
                transText = line.translation!!
                transH = measureTransHeight(transText) + transGapPx
            }
            val totalH = linePadTopPx + preH + mainH + secondaryH + secondaryTranslationH + romaH + transH + linePadBottomPx + lineGapPx
            result.add(
                LineEntry(
                    yTop = y,
                    preH = preH,
                    mainH = mainH,
                    secondaryH = secondaryH,
                    secondaryTranslationH = secondaryTranslationH,
                    transH = transH,
                    romaH = romaH,
                    totalH = totalH,
                    preText = preText,
                    mainText = mainText,
                    secondaryText = secondaryText,
                    secondaryTranslationText = secondaryTranslationText,
                    transText = transText,
                    romaText = romaText,
                    words = line.words,
                    pronunciationWords = pronunciationWords,
                    secondaryWords = line.secondaryWords,
                    secondaryStart = secondaryStart,
                    secondaryEnd = secondaryEnd,
                    alignedRight = forcedTextAlignment == 2 || (forcedTextAlignment < 0 && line.isAlignedRight),
                    centered = forcedTextAlignment == 1 || (forcedTextAlignment < 0 && centerUnalignedLinesEnabled && !line.isAlignedRight),
                    begin = line.begin,
                    end = line.end
                )
            )
            y += totalH
        }
        entries = result.applyForcedVerticalAlignment()
        startLayoutShiftAnimation(oldCentersByBegin, entries)
        totalHeight = entries.maxOfOrNull { it.yTop + it.totalH } ?: y
        val bottomPad = if (height > 0) height * BOTTOM_OFFSET_RATIO else 0f
        maxScrollY = max(0f, totalHeight + bottomPad - height)
    }

    private fun startLayoutShiftAnimation(
        oldCentersByBegin: Map<Long, Float>,
        newEntries: List<LineEntry>
    ) {
        if (oldCentersByBegin.isEmpty() || newEntries.isEmpty() || height <= 0) {
            layoutShiftAnimator?.cancel()
            clearLayoutShiftAnimation()
            return
        }
        val offsets = newEntries.mapNotNull { entry ->
            val oldCenter = oldCentersByBegin[entry.begin] ?: return@mapNotNull null
            val newCenter = entry.yTop + entry.totalH / 2f
            val offset = oldCenter - newCenter
            if (abs(offset) > 0.5f) entry.begin to offset else null
        }.toMap()
        if (offsets.isEmpty()) {
            layoutShiftAnimator?.cancel()
            clearLayoutShiftAnimation()
            return
        }
        layoutShiftAnimator?.cancel()
        layoutShiftOffsetsByBegin = offsets
        layoutShiftProgress = 0f
        layoutShiftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LAYOUT_SHIFT_MS
            interpolator = scrollPath
            addUpdateListener {
                layoutShiftProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (layoutShiftAnimator == animation) clearLayoutShiftAnimation()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (layoutShiftAnimator == animation) clearLayoutShiftAnimation()
                }
            })
        }
        layoutShiftAnimator?.start()
    }

    private fun clearLayoutShiftAnimation() {
        layoutShiftAnimator = null
        layoutShiftProgress = 1f
        layoutShiftOffsetsByBegin = emptyMap()
    }

    private fun LineEntry.layoutShiftOffset(): Float =
        (layoutShiftOffsetsByBegin[begin] ?: 0f) * (1f - layoutShiftProgress)

    private fun MutableList<LineEntry>.applyForcedVerticalAlignment(): List<LineEntry> {
        if (forcedVerticalAlignment < 0 || height <= 0 || isEmpty()) return this
        val firstTop = first().yTop
        val lastBottom = maxOf { it.yTop + it.totalH }
        val contentHeight = (lastBottom - firstTop).coerceAtLeast(0f)
        val available = (height - contentHeight).coerceAtLeast(0f)
        val targetTop = when (forcedVerticalAlignment) {
            1 -> available / 2f
            2 -> available
            else -> 0f
        }
        val delta = targetTop - firstTop
        if (abs(delta) < 0.5f) return this
        return map { it.copy(yTop = it.yTop + delta) }
    }

    private fun updateSecondaryVisibilityIfNeeded() {
        val signature = computeSecondaryVisibilitySignature(currentPosMs)
        if (signature == secondaryVisibilitySignature) return
        secondaryVisibilitySignature = signature
        val browsing = isUserScrolling
        rebuildEntries()
        if (positionInitialized && !browsing) {
            scrollToCurrentLine(false)
        } else {
            clampScroll()
        }
    }

    private fun computeSecondaryVisibilitySignature(positionMs: Long): Int {
        var result = 17
        val highlightedIndices = resolveActiveHighlightIndices(positionMs)
        lyrics.forEachIndexed { index, line ->
            val hasSecondary = line.secondary?.splitSecondaryBlock()?.first?.isNotBlank() == true
            if (!hasSecondary) return@forEachIndexed
            val start = line.secondaryWords?.minOfOrNull { it.begin }
            val end = line.secondaryWords?.maxOfOrNull { it.end }
            result = 31 * result + index
            result = 31 * result + if (index in highlightedIndices || isSecondaryVisible(start, end, line.end, positionMs)) 1 else 0
        }
        return result
    }

    private fun measureMainHeight(text: String): Float {
        if (singleLineMarqueeEnabled) return mainPaint.fontSpacing
        val w = safeTextLayoutWidth(alignedRight = false)
        if (w <= 0) return mainPaint.fontSpacing
        val layout = buildLayout(text, mainPaint, w, forMain = true)
        return layout.height.toFloat()
    }

    private fun String.splitSecondaryBlock(): Pair<String, String?> {
        val markerIndex = indexOf(SECONDARY_TRANSLATION_SEPARATOR)
        if (markerIndex < 0) return this to null
        val primary = substring(0, markerIndex)
        val translation = substring(markerIndex + SECONDARY_TRANSLATION_SEPARATOR.length)
        return primary to translation
    }

    private fun measureTransHeight(text: String): Float {
        if (singleLineMarqueeEnabled) return transPaint.fontSpacing
        val w = safeTextLayoutWidth(alignedRight = false)
        if (w <= 0) return transPaint.fontSpacing
        val layout = buildLayout(text, transPaint, w)
        return layout.height.toFloat()
    }

    private fun measureWordsHeight(words: List<LyricWord>?, paint: TextPaint): Float {
        if (singleLineMarqueeEnabled) return 0f
        if (words.isNullOrEmpty()) return 0f
        val availW = safeTextLayoutWidth(alignedRight = false).toFloat()
        if (availW <= 0f) return paint.fontSpacing
        val wordInfos = layoutKaraokeWords(
            words = words,
            paint = paint,
            startX = 0f,
            baseline = 0f,
            availableWidth = availW,
            centered = false,
        )
        if (wordInfos.isEmpty()) return 0f
        val lines = wordInfos.maxOf { it.visualLine } + 1
        val fm = paint.fontMetrics
        val directDrawHeight = -fm.ascent + (lines - 1) * paint.fontSpacing + fm.descent +
            (fm.bottom - fm.descent).coerceAtLeast(0f)
        return max(lines * paint.fontSpacing, directDrawHeight) + linePadTopPx
    }

    private fun safeTextLayoutWidth(alignedRight: Boolean): Int {
        val edgeInset = TEXT_EDGE_SAFE_INSET_DP * density
        val alignInset = if (alignedRight) RIGHT_ALIGN_GLYPH_SAFE_INSET_DP * density else 0f
        return (width - paddingLeft - paddingRight - edgeInset - alignInset).toInt().coerceAtLeast(1)
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        widthPx: Int,
        alignedRight: Boolean = false,
        centered: Boolean = false,
        forMain: Boolean = false
    ): StaticLayout {
        val alignment = when {
            alignedRight -> Layout.Alignment.ALIGN_OPPOSITE
            centered -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, widthPx))
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
        if (forMain && maxMainLines > 0) {
            builder.setMaxLines(maxMainLines)
        }
        return builder.build()
    }

    private fun findCurrentLine(posMs: Long): Int {
        return previewLyricViewIndexAt(posMs, lyricWindows)
    }

    private fun updateLineOffset(currentIdx: Int) {
        lineOffsetMs = computeLyricViewPreviewOffsetMs(currentIdx, lyricWindows)
    }

    private fun onLineChanged(
        oldIndex: Int,
        newIndex: Int,
        previousHighlights: Set<Int>,
        newHighlights: Set<Int>
    ) {
        if (oldIndex >= 0 && enableAnim && oldIndex !in newHighlights) {
            startExitAnim(oldIndex)
        }
        val enteringIndices = newHighlights - previousHighlights
        if (newIndex >= 0 && enableAnim && newIndex in enteringIndices) {
            startPopAnim(newIndex)
        }
        previousIndex = oldIndex
        postFrame()
    }

    private fun startPopAnim(index: Int) {
        popAnimators[index]?.cancel()
        lineScales[index] = SCALE_NORMAL
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = POP_UP_MS + POP_DOWN_MS
            interpolator = popPathUp
            addUpdateListener {
                val progress = it.animatedValue as Float
                val scale = if (progress < 0.5f) {
                    SCALE_NORMAL + (POP_SCALE - SCALE_NORMAL) * (progress * 2f)
                } else {
                    POP_SCALE - (POP_SCALE - SCALE_HIGHLIGHT) * ((progress - 0.5f) * 2f)
                }
                lineScales[index] = scale
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    lineScales[index] = SCALE_HIGHLIGHT
                    popAnimators.remove(index)
                    invalidate()
                }
            })
        }
        popAnimators[index] = animator
        animator.start()
    }

    private fun startExitAnim(index: Int) {
        val currentScale = lineScales[index] ?: SCALE_HIGHLIGHT
        val animator = ValueAnimator.ofFloat(currentScale, SCALE_NORMAL).apply {
            duration = EXIT_MS
            interpolator = popPathDown
            addUpdateListener {
                lineScales[index] = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    lineScales[index] = SCALE_NORMAL
                    popAnimators.remove(index)
                    invalidate()
                }
            })
        }
        popAnimators[index] = animator
        animator.start()
    }

    private fun scrollToCurrentLine(animated: Boolean) {
        if (currentIndex < 0 || currentIndex >= entries.size) return
        val entry = entries[currentIndex]
        val anchorY = (height / 2f + anchorOffsetPx).coerceIn(height * 0.08f, height * 0.92f)
        val target = (entry.yTop + entry.totalH / 2f - anchorY).coerceIn(0f, maxScrollY)
        if (animated) {
            animateScrollTo(target)
        } else {
            scrollY = target
            invalidate()
        }
    }

    private fun animateScrollTo(target: Float) {
        scrollAnimator?.cancel()
        val start = scrollY
        val delta = target - start
        if (abs(delta) < 1f) return
        scrollAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SCROLL_ANIM_MS
            interpolator = scrollPath
            addUpdateListener {
                val progress = it.animatedValue as Float
                scrollY = start + delta * progress
                clampScroll()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (scrollAnimator == animation) scrollAnimator = null
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (scrollAnimator == animation) scrollAnimator = null
                }
            })
        }
        scrollAnimator?.start()
    }

    private fun clampScroll() {
        scrollY = scrollY.coerceIn(0f, maxScrollY)
    }

    private fun tickAnimations() {
        if (!scroller.isFinished) {
            if (scroller.computeScrollOffset()) {
                scrollY = scroller.currY.toFloat()
                clampScroll()
            }
        }
        if (autoScrollResumeEnabled && isUserScrolling && SystemClock.uptimeMillis() > autoScrollResumeTime) {
            isUserScrolling = false
            autoScrollResumeLineIndex = -1
            scrollToCurrentLine(true)
        }
        var anyAlphaAnimating = false
        if (lineAlphaAnimationsEnabled) {
            for (i in entries.indices) {
                val target = calculateTargetAlpha(i)
                val current = lineAlphas[i]
                if (current == null) {
                    lineAlphas[i] = target
                } else if (abs(current - target) > 0.002f) {
                    lineAlphas[i] = current + (target - current) * 0.12f
                    anyAlphaAnimating = true
                } else {
                    lineAlphas[i] = target
                }
            }
        }
        val interludeAnimating = updateInterludeExpand()
        if (anyAlphaAnimating || interludeAnimating) {
            invalidate()
            postFrame()
        }
    }

    private fun updateInterludeExpand(): Boolean {
        if (interludePrevIdx < 0 && !interludeCollapsing) return false
        val now = SystemClock.uptimeMillis()
        if (!interludeCollapsing) {
            val t = ((now - interludeExpandStartTime).toFloat() / INTERLUDE_EXPAND_MS).coerceIn(0f, 1f)
            interludeExpandProgress = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f).getInterpolation(t)
            return t < 1f
        } else {
            val t = ((now - interludeCollapseStartTime).toFloat() / INTERLUDE_COLLAPSE_MS).coerceIn(0f, 1f)
            interludeExpandProgress = 1f - PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f).getInterpolation(t)
            if (t >= 1f) {
                clearInterlude()
                return false
            }
            return true
        }
    }

    private fun postFrame() {
        val alphaSnapshot = if (lineAlphaAnimationsEnabled) lineAlphas.toMap() else emptyMap()
        val needFrame = (playbackActive && continuousFrameUpdatesEnabled && lyrics.any {
            it.words?.isNotEmpty() == true ||
                it.secondaryWords?.isNotEmpty() == true ||
                pronunciationWordsByBegin[it.begin]?.isNotEmpty() == true
        }) ||
                (playbackActive && singleLineMarqueeEnabled && entries.isNotEmpty()) ||
                (autoScrollResumeEnabled && isUserScrolling) ||
                !scroller.isFinished ||
                popAnimators.isNotEmpty() ||
                isInterludeActive() ||
                (lineAlphaAnimationsEnabled && alphaSnapshot.any { (index, alpha) -> abs(alpha - calculateTargetAlpha(index)) > 0.005f })
        if (needFrame && !framePosted) {
            framePosted = true
            choreographer.postFrameCallback(frameCb)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        rebuildEntries()
        maxScrollY = max(0f, totalHeight - h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildEntries()
        scrollToCurrentLine(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) {
            drawPlaceholder(canvas)
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val saveCount = canvas.saveLayer(0f, 0f, w, h, null)
        if (fullLayerBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isUserScrolling) {
            val node = blurRenderNode?.takeIf { blurNodeW == width && blurNodeH == height }
                ?: RenderNode("nonCurrentBlur").also {
                    it.setPosition(0, 0, width, height)
                    it.setRenderEffect(RenderEffect.createBlurEffect(lineBlurRadiusPx, lineBlurRadiusPx, Shader.TileMode.CLAMP))
                    blurRenderNode = it
                    blurNodeW = width
                    blurNodeH = height
                }
            val rnCanvas = node.beginRecording(width, height)
            drawLines(rnCanvas, excludeCurrent = true)
            node.endRecording()
            canvas.drawRenderNode(node)
            drawHighlightedLines(canvas)
        } else {
            drawLines(canvas, excludeCurrent = false)
        }
        drawInterlude(canvas)
        drawEdgeFade(canvas, w, h)
        canvas.restoreToCount(saveCount)
    }

    private fun drawPlaceholder(canvas: Canvas) {
        val text = when (placeholderFormat) {
            PlaceholderFormat.NAME_ARTIST -> "${songName ?: ""} - ${songArtist ?: ""}"
            PlaceholderFormat.NAME -> songName ?: ""
        }
        if (text.isBlank()) return
        placeholderPaint.alpha = (dimColor ushr 24)
        canvas.drawText(text, width / 2f, height / 2f, placeholderPaint)
    }

    private fun drawLines(canvas: Canvas, excludeCurrent: Boolean = false) {
        val viewH = height.toFloat()
        val viewW = width.toFloat()
        val expandOffset = getInterludeExpandOffset()
        for (i in entries.indices) {
            if (excludeCurrent && isLineHighlighted(i)) continue
            val entry = entries[i]
            val targetAlpha = calculateTargetAlpha(i)
            val alpha = if (lineAlphaAnimationsEnabled) lineAlphas.getOrPut(i) { targetAlpha } else targetAlpha
            val scale = lineScales[i] ?: if (isLineHighlighted(i)) SCALE_HIGHLIGHT else SCALE_NORMAL
            val yExtra = if (expandOffset > 0f && i > interludePrevIdx) expandOffset else 0f
            val lineCenterY = entry.yTop + entry.totalH / 2f - scrollY + yExtra + entry.layoutShiftOffset()
            if (lineCenterY + entry.totalH < -50f || lineCenterY - entry.totalH > viewH + 50f) continue
            val farBlur = nonCurrentLineBlurEnabled &&
                !isUserScrolling &&
                !isLineHighlighted(i) &&
                currentIndex >= 0 &&
                abs(i - currentIndex) >= nonCurrentLineBlurDistance
            canvas.save()
            val pivotX = entry.pivotX()
            val pivotY = lineCenterY + entry.totalH / 2f
            canvas.scale(scale, scale, pivotX, pivotY)
            canvas.saveLayerAlpha(0f, lineCenterY - entry.totalH, viewW, lineCenterY + entry.totalH, (alpha * 255).toInt())
            drawSingleLine(canvas, entry, i, lineCenterY, farBlur)
            canvas.restore()
            canvas.restore()
        }
    }

    private fun drawHighlightedLines(canvas: Canvas) {
        val highlighted = buildSet<Int> {
            addAll(activeHighlightIndices)
            if (currentIndex >= 0) add(currentIndex)
        }
            .filter { it in entries.indices }
            .sorted()
        if (highlighted.isEmpty()) return
        val expandOffset = getInterludeExpandOffset()
        val viewH = height.toFloat()
        val viewW = width.toFloat()
        highlighted.forEach { index ->
            val entry = entries[index]
            val alpha = if (lineAlphaAnimationsEnabled) {
                lineAlphas.getOrPut(index) { calculateTargetAlpha(index) }
            } else {
                calculateTargetAlpha(index)
            }
            val scale = lineScales[index] ?: SCALE_HIGHLIGHT
            val yExtra = if (expandOffset > 0f && index > interludePrevIdx) expandOffset else 0f
            val lineCenterY = entry.yTop + entry.totalH / 2f - scrollY + yExtra + entry.layoutShiftOffset()
            if (lineCenterY + entry.totalH < -50f || lineCenterY - entry.totalH > viewH + 50f) return@forEach
            canvas.save()
            val pivotX = entry.pivotX()
            val pivotY = lineCenterY + entry.totalH / 2f
            canvas.scale(scale, scale, pivotX, pivotY)
            canvas.saveLayerAlpha(0f, lineCenterY - entry.totalH, viewW, lineCenterY + entry.totalH, (alpha * 255).toInt())
            drawSingleLine(canvas, entry, index, lineCenterY, farBlur = false)
            canvas.restore()
            canvas.restore()
        }
    }

    private fun calculateTargetAlpha(index: Int): Float {
        if (isUserScrolling) return LINE_MAX_ALPHA
        if (currentIndex < 0) return LINE_MAX_ALPHA
        if (index in activeHighlightIndices) return LINE_MAX_ALPHA
        val distance = abs(index - currentIndex)
        if (distance == 0) return LINE_MAX_ALPHA
        return (LINE_MIN_ALPHA + (LINE_MAX_ALPHA - LINE_MIN_ALPHA) * (1f - distance.toFloat() / FADE_LINES))
            .coerceIn(LINE_MIN_ALPHA, LINE_MAX_ALPHA)
    }

    private fun drawSingleLine(canvas: Canvas, entry: LineEntry, index: Int, lineCenterY: Float, farBlur: Boolean) {
        val isCurrent = index == currentIndex
        val isHighlighted = isLineHighlighted(index)
        val contentTop = lineCenterY - entry.totalH / 2f + linePadTopPx
        val textStartX = paddingLeft.toFloat()
        if (entry.preText != null) {
            val preBaseline = contentTop + transGapPx + (-transPaint.fontMetrics.ascent)
            val pPaint = if (isHighlighted) hlTransPaint else dimTransPaint
            if (!singleLineMarqueeEnabled && !entry.pronunciationWords.isNullOrEmpty() && isHighlighted) {
                drawKaraokeWords(
                    canvas = canvas,
                    entry = entry,
                    lineIndex = index,
                    startX = textStartX,
                    baseline = preBaseline,
                    alignedRight = entry.alignedRight,
                    centered = entry.centered,
                    useSecondary = true,
                    wordsOverride = entry.pronunciationWords,
                    textOverride = entry.preText
                )
            } else {
                drawTextAligned(canvas, entry.preText, pPaint, textStartX, preBaseline, entry.alignedRight, entry.centered, farBlur)
            }
        }
        val mainTopY = contentTop + entry.preH
        val mainBottomY = mainTopY + entry.mainH
        // setIncludePad(true) 时 StaticLayout.height 包含 top padding
        // 第一行基线 = mainTopY + getLineBaseline(0) ≈ mainTopY + (-ascent + topPad)
        // 但卡拉OK直接用 baseline 绘制，不含 topPad，所以需要分开处理
        val topPad = mainPaint.fontMetrics.let { it.top - it.ascent }.coerceAtLeast(0f)
        val mainBaseline = mainTopY + topPad + (-mainPaint.fontMetrics.ascent)
        if (!singleLineMarqueeEnabled && !entry.words.isNullOrEmpty() && isHighlighted) {
            drawKaraokeWords(canvas, entry, index, textStartX, mainBaseline, entry.alignedRight, entry.centered)
        } else {
            val paint = when {
                isHighlighted -> hlPaint
                index == previousIndex -> dimPaint
                else -> dimPaint
            }
            drawTextAligned(canvas, entry.mainText ?: "", paint, textStartX, mainBaseline, entry.alignedRight, entry.centered, farBlur, forMain = true)
        }
        var secondaryBaseY = mainBottomY
        // Below-the-main pronunciation renders first (main → romaji → translation).
        if (entry.romaText != null) {
            val romaBaseline = secondaryBaseY + transGapPx + (-transPaint.fontMetrics.ascent)
            val tPaint = if (isHighlighted) hlTransPaint else dimTransPaint
            drawTextAligned(canvas, entry.romaText, tPaint, textStartX, romaBaseline, entry.alignedRight, entry.centered, farBlur)
            secondaryBaseY += entry.romaH
        }
        if (entry.transText != null) {
            val transBaseline = secondaryBaseY + transGapPx + (-transPaint.fontMetrics.ascent)
            val tPaint = if (isHighlighted) hlTransPaint else dimTransPaint
            drawTextAligned(canvas, entry.transText, tPaint, textStartX, transBaseline, entry.alignedRight, entry.centered, farBlur)
            secondaryBaseY += entry.transH
        }
        val shouldDrawSecondary = entry.secondaryText != null && (isHighlighted || entry.isSecondaryVisible(currentPosMs))
        if (entry.secondaryText != null && shouldDrawSecondary) {
            val secondaryBaseline = secondaryBaseY + transGapPx + (-transPaint.fontMetrics.ascent)
            val tPaint = if (isHighlighted) hlTransPaint else dimTransPaint
            if (!singleLineMarqueeEnabled && !entry.secondaryWords.isNullOrEmpty() && isHighlighted) {
                drawKaraokeWords(canvas, entry, index, textStartX, secondaryBaseline, entry.alignedRight, entry.centered, useSecondary = true)
            } else {
                drawTextAligned(canvas, entry.secondaryText, tPaint, textStartX, secondaryBaseline, entry.alignedRight, entry.centered, farBlur)
            }
            secondaryBaseY += entry.secondaryH
        }
        if (entry.secondaryTranslationText != null && shouldDrawSecondary) {
            val secondaryTranslationBaseline = secondaryBaseY + transGapPx + (-transPaint.fontMetrics.ascent)
            val tPaint = if (isHighlighted) hlTransPaint else dimTransPaint
            drawTextAligned(canvas, entry.secondaryTranslationText, tPaint, textStartX, secondaryTranslationBaseline, entry.alignedRight, entry.centered, farBlur)
            secondaryBaseY += entry.secondaryTranslationH
        }
    }

    private fun isLineHighlighted(index: Int): Boolean =
        isLyricViewLineHighlighted(
            index = index,
            currentIndex = currentIndex,
            activeHighlightIndices = activeHighlightIndices
        )

    private fun resolveActiveHighlightIndices(positionMs: Long): Set<Int> {
        if (lyricWindows.isEmpty()) return emptySet()
        return buildSet {
            lyricWindows.forEachIndexed { index, window ->
                if (positionMs >= window.begin && positionMs < window.end) {
                    add(index)
                }
            }
        }
    }

    private fun LineEntry.isSecondaryVisible(positionMs: Long): Boolean {
        return isSecondaryVisible(secondaryStart, secondaryEnd, end, positionMs)
    }

    private fun isSecondaryVisible(start: Long?, end: Long?, fallbackEnd: Long, positionMs: Long): Boolean {
        val actualStart = start ?: return true
        val actualEnd = end ?: fallbackEnd
        return positionMs in actualStart..actualEnd
    }

    private fun LineEntry.pivotX(): Float =
        when {
            centered -> width / 2f
            alignedRight -> width - paddingRight.toFloat()
            else -> paddingLeft.toFloat()
        }

    private fun drawTextAligned(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        startX: Float,
        baseline: Float,
        alignedRight: Boolean,
        centered: Boolean,
        blur: Boolean = false,
        forMain: Boolean = false
    ) {
        val w = width - paddingLeft - paddingRight
        if (w <= 0) return
        val oldMask = paint.maskFilter
        if (blur) paint.maskFilter = distantLineBlur
        if (singleLineMarqueeEnabled) {
            drawSingleLineMarqueeText(canvas, text, paint, startX, baseline, w.toFloat(), alignedRight, centered)
            paint.maskFilter = oldMask
            return
        }
        val edgeInset = TEXT_EDGE_SAFE_INSET_DP * density
        val alignInset = if (alignedRight) RIGHT_ALIGN_GLYPH_SAFE_INSET_DP * density else 0f
        val safeLayoutWidth = (w - edgeInset - alignInset).toInt().coerceAtLeast(1)
        val layout = buildLayout(text, paint, safeLayoutWidth, alignedRight, centered, forMain)
        canvas.save()
        // StaticLayout line 0 baseline is at getLineTop(0) + getLineBaseline(0) - getLineTop(0)
        // = getLineBaseline(0). With includePad=true, getLineTop(0) includes top padding.
        // We want line 0 baseline at `baseline`, so translate by baseline - line0Baseline.
        val line0Baseline = layout.getLineBaseline(0).toFloat()
        canvas.translate(startX, baseline - line0Baseline)
        layout.draw(canvas)
        canvas.restore()
        paint.maskFilter = oldMask
    }

    private fun drawSingleLineMarqueeText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        startX: Float,
        baseline: Float,
        availableWidth: Float,
        alignedRight: Boolean,
        centered: Boolean,
    ) {
        if (text.isBlank() || availableWidth <= 0f) return
        val textWidth = paint.measureText(text)
        val drawX = if (textWidth <= availableWidth) {
            when {
                alignedRight -> startX + availableWidth - textWidth
                centered -> startX + (availableWidth - textWidth) / 2f
                else -> startX
            }
        } else {
            startX - marqueeOffsetPx(textWidth, availableWidth)
        }
        val fm = paint.fontMetrics
        val bleed = TEXT_CLIP_BLEED_DP * density
        canvas.save()
        canvas.clipRect(
            startX - bleed,
            baseline + fm.top - density * 2f,
            startX + availableWidth + bleed,
            baseline + fm.bottom + density * 2f
        )
        canvas.drawText(text, drawX, baseline, paint)
        canvas.restore()
    }

    private fun marqueeOffsetPx(textWidth: Float, availableWidth: Float): Float {
        val overflow = (textWidth - availableWidth).coerceAtLeast(0f)
        if (overflow <= 0f) return 0f
        val speed = (MARQUEE_SPEED_DP_PER_SEC * density).coerceAtLeast(1f)
        val restartGap = MARQUEE_RESTART_GAP_DP * density
        val scrollDistance = overflow + restartGap
        val scrollMs = ((scrollDistance / speed) * 1000f).toLong().coerceAtLeast(1L)
        val cycleMs = MARQUEE_HOLD_MS + scrollMs
        val phase = SystemClock.uptimeMillis() % cycleMs
        return when {
            phase < MARQUEE_HOLD_MS -> 0f
            else -> scrollDistance * ((phase - MARQUEE_HOLD_MS).toFloat() / scrollMs.toFloat())
        }
    }

    private fun clearBlurCache() {
        blurRenderNode?.discardDisplayList()
        blurRenderNode = null
        blurNodeW = 0
        blurNodeH = 0
    }

    private fun detectInterlude() {
        if (currentIndex < 0 || currentIndex >= entries.size - 1) {
            if (isInterludeActive()) startInterludeCollapse()
            return
        }
        val entry = entries[currentIndex]
        val nextEntry = entries[currentIndex + 1]
        val gap = nextEntry.begin - entry.end
        if (gap < INTERLUDE_MIN_GAP_MS || currentPosMs < entry.end || currentPosMs >= nextEntry.begin) {
            if (isInterludeActive()) startInterludeCollapse()
            return
        }
        if (interludePrevIdx != currentIndex) {
            interludeCollapsing = false
            interludePrevIdx = currentIndex
            interludeNextIdx = currentIndex + 1
            interludeGapStart = entry.end
            interludeGapEnd = nextEntry.begin
            interludeEnterStart = SystemClock.uptimeMillis()
            interludeExpandStartTime = SystemClock.uptimeMillis()
            interludeExpandProgress = 0f
        }
    }

    private fun startInterludeCollapse() {
        if (interludeCollapsing) return
        interludeCollapsing = true
        interludeCollapseStartTime = SystemClock.uptimeMillis()
    }

    private fun clearInterlude() {
        interludePrevIdx = -1
        interludeNextIdx = -1
        interludeGapStart = -1L
        interludeGapEnd = -1L
        interludeEnterStart = 0L
        interludeExpandProgress = 0f
        interludeExpandStartTime = 0L
        interludeCollapsing = false
        interludeCollapseStartTime = 0L
    }

    private fun isInterludeActive(): Boolean = interludePrevIdx >= 0

    private fun getInterludeExpandOffset(): Float {
        if (interludePrevIdx < 0 && !interludeCollapsing) return 0f
        return interludeExpandProgress * INTERLUDE_EXTRA_DP * density
    }

    private fun drawInterlude(canvas: Canvas) {
        if (interludePrevIdx < 0 || interludeNextIdx < 0) return
        val prevEntry = entries.getOrNull(interludePrevIdx) ?: return

        val viewH = height.toFloat()
        val ds = dotSizePx
        val dg = dotSpacingPx
        val dotGroupWidth = ds * 3f + dg * 2f
        val availableWidth = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(dotGroupWidth)
        val edgeInset = TEXT_EDGE_SAFE_INSET_DP * density
        val alignInset = RIGHT_ALIGN_GLYPH_SAFE_INSET_DP * density
        val startX = when {
            prevEntry.alignedRight -> width - paddingRight.toFloat() - edgeInset - alignInset - dotGroupWidth
            prevEntry.centered -> paddingLeft.toFloat() + (availableWidth - dotGroupWidth) / 2f
            else -> paddingLeft.toFloat() + edgeInset
        }
        val expandOffset = getInterludeExpandOffset()
        if (expandOffset < ds) return
        val prevBottom = prevEntry.yTop + prevEntry.totalH - scrollY
        val centerY = prevBottom + expandOffset / 2f

        if (centerY + ds < 0f || centerY - ds > viewH) return

        val now = SystemClock.uptimeMillis()
        val elapsed = (now - interludeEnterStart).toFloat()
        val gapDuration = interludeGapEnd - interludeGapStart
        val effectiveDuration = (gapDuration - 800L).coerceAtLeast(1L).toFloat()

        val enterProgress = (elapsed / INTERLUDE_ENTER_MS).coerceIn(0f, 1f)
        val enterAlpha = interludeAccel.getInterpolation(enterProgress)

        val exitTotalMs = INTERLUDE_EXIT_UP_MS + INTERLUDE_EXIT_DOWN_MS
        val exitStartAt = gapDuration.toFloat() - exitTotalMs
        val exitElapsed = (elapsed - exitStartAt).coerceAtLeast(0f)

        var exitScale = 1f
        var exitAlpha = 1f
        if (exitElapsed > 0f) {
            if (exitElapsed < INTERLUDE_EXIT_UP_MS) {
                val t = exitElapsed / INTERLUDE_EXIT_UP_MS
                exitScale = 1f + 0.2f * interludeExitUpPath.getInterpolation(t)
            } else {
                val t = ((exitElapsed - INTERLUDE_EXIT_UP_MS) / INTERLUDE_EXIT_DOWN_MS).coerceIn(0f, 1f)
                val eased = interludeExitDownPath.getInterpolation(t)
                exitScale = 1.2f - 0.7f * eased
                exitAlpha = 1f - eased
            }
        }

        val pulsePhase = (elapsed / INTERLUDE_PULSE_MS) * 2f * PI.toFloat()
        val pulseScale = 1f + INTERLUDE_PULSE_AMPLITUDE * sin(pulsePhase)
        val totalScale = pulseScale * exitScale
        val overallAlpha = (enterAlpha * exitAlpha * 255).toInt().coerceIn(0, 255)

        if (overallAlpha <= 0) return

        val dotCenterX = startX + ds / 2f
        val pivotY = centerY

        canvas.save()
        canvas.scale(totalScale, totalScale, dotCenterX, pivotY)

        for (i in 0..2) {
            val cx = startX + i * (ds + dg) + ds / 2f

            val dotStartF = i / 3f
            val dotEndF = (i + 1) / 3f
            val rawProgress = (elapsed / effectiveDuration - dotStartF) / (dotEndF - dotStartF)
            val dotProgress = rawProgress.coerceIn(0f, 1f)
            val easedProgress = interludeColorPath.getInterpolation(dotProgress)

            val color = argbEvaluator.evaluate(easedProgress, interludeColorDim, interludeColorBright) as Int
            interludePaint.color = color
            interludePaint.alpha = overallAlpha

            canvas.drawCircle(cx, centerY, ds / 2f, interludePaint)
        }

        canvas.restore()
    }

    private fun drawKaraokeWords(
        canvas: Canvas,
        entry: LineEntry,
        lineIndex: Int,
        startX: Float,
        baseline: Float,
        alignedRight: Boolean,
        centered: Boolean,
        useSecondary: Boolean = false,
        wordsOverride: List<LyricWord>? = null,
        textOverride: String? = null
    ) {
        val words = wordsOverride ?: (if (useSecondary) entry.secondaryWords else entry.words) ?: return
        val karaokePos = currentKaraokePosition()
        if (alignedRight) {
            drawAlignedKaraokeWords(canvas, entry, words, startX, baseline, karaokePos, useSecondary, textOverride)
            return
        }
        val availW = (width - paddingLeft - paddingRight - TEXT_EDGE_SAFE_INSET_DP * density).coerceAtLeast(1f)
        val activePaint = if (useSecondary) hlTransPaint else hlPaint
        val inactivePaint = if (useSecondary) dimTransPaint else dimPaint
        val displayText = buildKaraokeDisplayText(entry, words, useSecondary, textOverride)
        if (displayText.isNotBlank() && words.any { activePaint.measureText(it.text.orEmpty()) > availW }) {
            drawTextAligned(
                canvas = canvas,
                text = displayText,
                paint = activePaint,
                startX = startX,
                baseline = baseline,
                alignedRight = alignedRight,
                centered = centered,
                forMain = !useSecondary
            )
            return
        }

        val wordInfos = layoutKaraokeWords(
            words = words,
            paint = activePaint,
            startX = startX,
            baseline = baseline,
            availableWidth = availW,
            centered = centered,
        )

        if (wordInfos.isEmpty()) return

        fun KaraokeWordDrawInfo.drawX(): Float = x

        canvas.save()

        for (info in wordInfos) {
            canvas.drawText(info.text, info.drawX(), info.y, inactivePaint)
        }

        val sweepFraction = calculateSweepFraction(entry, karaokePos, words, activePaint)
        if (sweepFraction > 0f) {
            val lineGroups = wordInfos.groupBy { it.visualLine }.toSortedMap()
            val lineWidths = lineGroups.mapValues { (_, infos) -> infos.sumOf { it.w.toDouble() }.toFloat() }
            val totalW = lineWidths.values.sumOf { it.toDouble() }.toFloat()
            if (totalW <= 0f) { canvas.restore(); return }
            val sungWidth = sweepFraction.coerceIn(0f, 1f) * totalW
            val featherPx = featherWidthPx

            val fmTop = activePaint.fontMetrics.top
            val fmBottom = activePaint.fontMetrics.bottom
            var lineCumBefore = 0f

            for ((_, lineWords) in lineGroups) {
                if (lineWords.isEmpty()) continue

                val lineStartX = lineWords.first().drawX()
                val lineEndX = lineWords.last().drawX() + lineWords.last().w
                val lineW = lineEndX - lineStartX
                val lineY = lineWords.first().y
                val mTop = lineY + fmTop - 4f
                val mBottom = lineY + fmBottom + 4f

                val lineCumAfter = lineCumBefore + lineW

                if (sungWidth <= lineCumBefore) break

                val effectiveSungInLine = (sungWidth - lineCumBefore).coerceIn(0f, lineW)
                val sweepX = if (alignedRight) lineEndX - effectiveSungInLine else lineStartX + effectiveSungInLine
                val effectiveFeatherPx = if (alignedRight) {
                    min(featherPx, sweepX - lineStartX)
                } else {
                    min(featherPx, lineEndX - sweepX)
                }
                val featherStart = (sweepX - effectiveFeatherPx).coerceAtLeast(lineStartX)
                val featherEnd = (sweepX + effectiveFeatherPx).coerceAtMost(lineEndX)

                val saveCount = canvas.saveLayer(lineStartX, mTop, lineEndX, mBottom, null)
                for (info in lineWords) {
                    canvas.drawText(info.text, info.drawX(), info.y, activePaint)
                }
                val maskColors = if (alignedRight) {
                    intArrayOf(
                        Color.argb(0, 0, 0, 0),
                        Color.argb(0, 0, 0, 0),
                        Color.argb(255, 0, 0, 0),
                        Color.argb(255, 0, 0, 0),
                    )
                } else {
                    intArrayOf(
                        Color.argb(255, 0, 0, 0),
                        Color.argb(255, 0, 0, 0),
                        Color.argb(0, 0, 0, 0),
                        Color.argb(0, 0, 0, 0),
                    )
                }
                val maskPositions = if (alignedRight) {
                    floatArrayOf(
                        0f,
                        ((sweepX - lineStartX) / lineW).coerceIn(0f, 1f),
                        ((featherEnd - lineStartX) / lineW).coerceIn(0f, 1f),
                        1f,
                    )
                } else {
                    floatArrayOf(
                        0f,
                        ((featherStart - lineStartX) / lineW).coerceIn(0f, 1f),
                        ((sweepX - lineStartX) / lineW).coerceIn(0f, 1f),
                        1f,
                    )
                }
                maskPaint.shader = LinearGradient(
                    lineStartX, 0f, lineEndX, 0f,
                    maskColors, maskPositions, Shader.TileMode.CLAMP
                )
                maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                canvas.drawRect(lineStartX, mTop, lineEndX, mBottom, maskPaint)
                maskPaint.xfermode = null
                maskPaint.shader = null
                canvas.restoreToCount(saveCount)
                lineCumBefore = lineCumAfter
            }
        }

        if (words.allowsSustainGlow(entry, displayText)) {
            wordInfos.firstOrNull { info -> karaokePos in info.word.begin..info.word.end }
                ?.let { activeInfo ->
                    drawSustainGlowIfNeeded(canvas, activeInfo.text, activeInfo.drawX(), activeInfo.y, activeInfo.w, activePaint, activeInfo.word, karaokePos)
                }
        }

        canvas.restore()
    }

    private fun layoutKaraokeWords(
        words: List<LyricWord>,
        paint: TextPaint,
        startX: Float,
        baseline: Float,
        availableWidth: Float,
        centered: Boolean,
    ): List<KaraokeWordDrawInfo> {
        val rawInfos = mutableListOf<KaraokeWordDrawInfo>()
        val safeWidth = availableWidth.coerceAtLeast(1f)
        val lineSpacing = paint.fontSpacing
        var cursorX = startX
        var cursorY = baseline
        var visualLine = 0

        for (word in words) {
            val rawWordText = word.text ?: continue
            var wordText = if (cursorX == startX) rawWordText.trimStart() else rawWordText
            if (wordText.isBlank()) continue
            var wordW = paint.measureText(wordText)
            if (cursorX + wordW > startX + safeWidth && cursorX > startX) {
                cursorX = startX
                cursorY += lineSpacing
                visualLine++
                wordText = rawWordText.trimStart()
                if (wordText.isBlank()) continue
                wordW = paint.measureText(wordText)
            }
            rawInfos.add(KaraokeWordDrawInfo(word, wordText, cursorX, cursorY, wordW, visualLine))
            cursorX += wordW
        }

        if (!centered || rawInfos.isEmpty()) return rawInfos
        val lineOffsets = rawInfos.groupBy { it.visualLine }.mapValues { (_, infos) ->
            val lineStart = infos.minOf { it.x }
            val lineEnd = infos.maxOf { it.x + it.w }
            startX + (safeWidth - (lineEnd - lineStart)) / 2f - lineStart
        }
        return rawInfos.map { info ->
            info.copy(x = info.x + (lineOffsets[info.visualLine] ?: 0f))
        }
    }

    private data class KaraokeCharProgress(
        val completedChars: Int,
        val activeWord: LyricWord? = null,
        val activeWordText: String? = null,
        val activeWordStartChar: Int = completedChars,
        val activeWordProgress: Float = 0f,
    )

    private fun drawAlignedKaraokeWords(
        canvas: Canvas,
        entry: LineEntry,
        words: List<LyricWord>,
        startX: Float,
        baseline: Float,
        karaokePos: Long,
        useSecondary: Boolean,
        textOverride: String? = null,
    ) {
        val activePaint = if (useSecondary) hlTransPaint else hlPaint
        val inactivePaint = if (useSecondary) dimTransPaint else dimPaint
        val text = buildKaraokeDisplayText(entry, words, useSecondary, textOverride)
        if (text.isBlank()) return
        val availW = (width - paddingLeft - paddingRight - TEXT_EDGE_SAFE_INSET_DP * density).toInt().coerceAtLeast(1)
        if (availW <= 0) return

        val activeLayout = buildLayout(text, activePaint, availW, alignedRight = true)
        val inactiveLayout = buildLayout(text, inactivePaint, availW, alignedRight = true)
        val progress = calculateKaraokeCharProgress(entry, words, karaokePos)
        val translateY = baseline - activeLayout.getLineBaseline(0).toFloat()

        canvas.save()
        canvas.translate(startX, translateY)
        inactiveLayout.draw(canvas)

        val activeWordText = progress.activeWordText
        val activeWord = progress.activeWord
        drawAlignedCompletedText(
            canvas = canvas,
            layout = activeLayout,
            text = text,
            completedChars = progress.completedChars.coerceIn(0, text.length),
            featherLastLine = activeWord == null || progress.activeWordProgress <= 0f
        )

        if (activeWord != null && !activeWordText.isNullOrEmpty()) {
            val wordStart = progress.activeWordStartChar.coerceIn(0, text.length)
            val wordEnd = (wordStart + activeWordText.length).coerceIn(wordStart, text.length)
            if (wordStart < wordEnd && progress.activeWordProgress > 0f) {
                val startLine = activeLayout.getLineForOffset(wordStart)
                val endLine = activeLayout.getLineForOffset((wordEnd - 1).coerceAtLeast(wordStart))
                var remainingProgress = progress.activeWordProgress.coerceIn(0f, 1f)
                val totalWordWidth = activePaint.measureText(activeWordText).coerceAtLeast(1f)

                for (line in startLine..endLine) {
                    val lineStartOffset = max(wordStart, activeLayout.getLineStart(line))
                    val lineEndOffset = min(wordEnd, activeLayout.getLineEnd(line))
                    if (lineStartOffset >= lineEndOffset) continue

                    val lineText = text.substring(lineStartOffset, lineEndOffset)
                    val lineWordWidth = activePaint.measureText(lineText)
                    if (lineWordWidth <= 0f) continue

                    val lineProgress = min(remainingProgress * totalWordWidth, lineWordWidth) / lineWordWidth
                    if (lineProgress <= 0f) break

                    val left = min(
                        layoutHorizontalForOffset(activeLayout, line, lineStartOffset),
                        layoutHorizontalForOffset(activeLayout, line, lineEndOffset)
                    )
                    val right = max(
                        layoutHorizontalForOffset(activeLayout, line, lineStartOffset),
                        layoutHorizontalForOffset(activeLayout, line, lineEndOffset)
                    )
                    val partialRight = left + (right - left) * lineProgress.coerceIn(0f, 1f)
                    val lineTop = activeLayout.getLineTop(line).toFloat()
                    val lineBottom = activeLayout.getLineBottom(line).toFloat()
                    if (lineProgress >= 0.999f || partialRight >= right) {
                        canvas.save()
                        canvas.clipRect(left, lineTop, right, lineBottom)
                        activeLayout.draw(canvas)
                        canvas.restore()
                    } else {
                        drawFeatheredLayoutSegment(
                            canvas = canvas,
                            layout = activeLayout,
                            segmentLeft = left,
                            segmentRight = right,
                            visibleRight = partialRight,
                            top = lineTop,
                            bottom = lineBottom
                        )
                    }

                    remainingProgress -= lineWordWidth / totalWordWidth
                    if (remainingProgress <= 0f) break
                }
            }

            val glowLine = activeLayout.getLineForOffset(progress.activeWordStartChar.coerceIn(0, (text.length - 1).coerceAtLeast(0)))
            val glowStart = progress.activeWordStartChar.coerceIn(0, text.length)
            val glowEnd = (glowStart + activeWordText.length).coerceIn(glowStart, text.length)
            if (glowStart < glowEnd) {
                val glowX = min(
                    layoutHorizontalForOffset(activeLayout, glowLine, glowStart),
                    layoutHorizontalForOffset(activeLayout, glowLine, glowEnd)
                )
                val glowWidth = max(
                    layoutHorizontalForOffset(activeLayout, glowLine, glowStart),
                    layoutHorizontalForOffset(activeLayout, glowLine, glowEnd)
                ) - glowX
                val glowBaseline = activeLayout.getLineBaseline(glowLine).toFloat()
                if (words.allowsSustainGlow(entry, text)) {
                    drawSustainGlowIfNeeded(
                        canvas = canvas,
                        text = activeWordText,
                        x = glowX,
                        baseline = glowBaseline,
                        width = glowWidth,
                        sourcePaint = activePaint,
                        word = activeWord,
                        position = karaokePos
                    )
                }
            }
        }

        canvas.restore()
    }

    private fun List<LyricWord>.allowsSustainGlow(entry: LineEntry, displayText: String): Boolean {
        val visibleWords = filter { !it.text.isNullOrBlank() }
        if (visibleWords.isEmpty()) return false
        if (visibleWords.size > 1) return true
        val only = visibleWords.single()
        val normalizedWord = only.text.orEmpty().trim().replace(Regex("""\s+"""), " ")
        val normalizedLine = displayText.trim().replace(Regex("""\s+"""), " ")
        val coversWholeLine = only.begin <= entry.begin + 20L && only.end >= entry.end - 20L
        return !(normalizedWord == normalizedLine && coversWholeLine)
    }

    private fun currentKaraokePosition(): Long {
        val elapsed = SystemClock.elapsedRealtime() - lastPositionWallTime
        val pos = if (playbackActive && lastPositionWallTime > 0 && elapsed in 0..2000) currentPosMs + elapsed else currentPosMs
        return pos + KARAOKE_WORD_OFFSET_MS
    }

    private fun drawAlignedCompletedText(
        canvas: Canvas,
        layout: StaticLayout,
        text: String,
        completedChars: Int,
        featherLastLine: Boolean,
    ) {
        if (completedChars <= 0 || text.isEmpty()) return
        val clampedEnd = completedChars.coerceIn(0, text.length)
        if (clampedEnd <= 0) return

        val lastOffset = (clampedEnd - 1).coerceAtLeast(0)
        val lastLine = layout.getLineForOffset(lastOffset)
        for (line in 0..lastLine) {
            val lineLeft = layout.getLineLeft(line)
            val lineRight = layout.getLineRight(line)
            if (lineRight <= lineLeft) continue

            val clipRight = if (line < lastLine) {
                lineRight
            } else {
                val offsetOnLine = clampedEnd
                    .coerceAtMost(layout.getLineEnd(line))
                    .coerceAtLeast(layout.getLineStart(line))
                max(lineLeft, min(lineRight, layoutHorizontalForOffset(layout, line, offsetOnLine)))
            }

            if (clipRight <= lineLeft) continue

            val top = layout.getLineTop(line).toFloat()
            val bottom = layout.getLineBottom(line).toFloat()
            if (line == lastLine && featherLastLine && clipRight < lineRight) {
                drawFeatheredLayoutSegment(
                    canvas = canvas,
                    layout = layout,
                    segmentLeft = lineLeft,
                    segmentRight = lineRight,
                    visibleRight = clipRight,
                    top = top,
                    bottom = bottom
                )
            } else {
                canvas.save()
                canvas.clipRect(lineLeft, top, clipRight, bottom)
                layout.draw(canvas)
                canvas.restore()
            }
        }
    }

    private fun drawFeatheredLayoutSegment(
        canvas: Canvas,
        layout: StaticLayout,
        segmentLeft: Float,
        segmentRight: Float,
        visibleRight: Float,
        top: Float,
        bottom: Float,
    ) {
        if (segmentRight <= segmentLeft || visibleRight <= segmentLeft) return
        val clampedVisibleRight = visibleRight.coerceIn(segmentLeft, segmentRight)
        val effectiveFeatherPx = min(featherWidthPx, clampedVisibleRight - segmentLeft)
        if (effectiveFeatherPx <= 0f || clampedVisibleRight >= segmentRight) {
            canvas.save()
            canvas.clipRect(segmentLeft, top, clampedVisibleRight, bottom)
            layout.draw(canvas)
            canvas.restore()
            return
        }

        val featherStart = (clampedVisibleRight - effectiveFeatherPx).coerceAtLeast(segmentLeft)
        val segmentWidth = (segmentRight - segmentLeft).coerceAtLeast(1f)
        val saveCount = canvas.saveLayer(segmentLeft, top, segmentRight, bottom, null)
        layout.draw(canvas)
        maskPaint.shader = LinearGradient(
            segmentLeft,
            0f,
            segmentRight,
            0f,
            intArrayOf(
                Color.argb(255, 0, 0, 0),
                Color.argb(255, 0, 0, 0),
                Color.argb(0, 0, 0, 0),
                Color.argb(0, 0, 0, 0),
            ),
            floatArrayOf(
                0f,
                ((featherStart - segmentLeft) / segmentWidth).coerceIn(0f, 1f),
                ((clampedVisibleRight - segmentLeft) / segmentWidth).coerceIn(0f, 1f),
                1f,
            ),
            Shader.TileMode.CLAMP
        )
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(segmentLeft, top, segmentRight, bottom, maskPaint)
        maskPaint.xfermode = null
        maskPaint.shader = null
        canvas.restoreToCount(saveCount)
    }

    private fun layoutHorizontalForOffset(
        layout: StaticLayout,
        line: Int,
        offset: Int,
    ): Float {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val visibleEnd = layout.getLineVisibleEnd(line)
        val clampedOffset = offset.coerceIn(lineStart, lineEnd)
        return when {
            clampedOffset <= lineStart -> layout.getLineLeft(line)
            clampedOffset >= visibleEnd -> layout.getLineRight(line)
            else -> layout.getPrimaryHorizontal(clampedOffset)
        }
    }

    private fun buildKaraokeDisplayText(
        entry: LineEntry,
        words: List<LyricWord>,
        useSecondary: Boolean,
        textOverride: String? = null,
    ): String {
        if (!textOverride.isNullOrBlank()) return textOverride
        val primaryText = if (useSecondary) entry.secondaryText else entry.mainText
        if (!primaryText.isNullOrBlank()) return primaryText
        return words.joinToString(separator = "") { it.text.orEmpty() }
    }

    private fun calculateKaraokeCharProgress(
        entry: LineEntry,
        words: List<LyricWord>,
        pos: Long,
    ): KaraokeCharProgress {
        if (words.isEmpty()) return KaraokeCharProgress(completedChars = 0)
        // Use the words' own earliest begin as the activation bound so that secondary
        // (background) words animate even when they start before the main line begin.
        val beginBound = words.minOfOrNull { it.begin } ?: entry.begin
        if (pos < beginBound) return KaraokeCharProgress(completedChars = 0)

        var charCursor = 0
        for (word in words) {
            val wordText = word.text.orEmpty()
            if (wordText.isEmpty()) continue
            if (pos < word.begin) {
                return KaraokeCharProgress(completedChars = charCursor)
            }
            if (pos < word.end) {
                val progress = if (word.duration > 0L) {
                    ((pos - word.begin).toFloat() / word.duration).coerceIn(0f, 1f)
                } else {
                    1f
                }
                return KaraokeCharProgress(
                    completedChars = charCursor,
                    activeWord = word,
                    activeWordText = wordText,
                    activeWordStartChar = charCursor,
                    activeWordProgress = progress
                )
            }
            charCursor += wordText.length
        }
        return KaraokeCharProgress(completedChars = charCursor)
    }

    private fun drawSustainGlowIfNeeded(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        width: Float,
        sourcePaint: TextPaint,
        word: LyricWord,
        position: Long
    ) {
        val duration = word.duration
        if (duration < 900L || width <= 0f) return
        val elapsed = (position - word.begin).coerceIn(0L, duration)
        val triggerDelay = min(420L, (duration * 0.36f).toLong().coerceAtLeast(1L))
        if (elapsed < triggerDelay || elapsed >= duration) return
        val progress = ((elapsed - triggerDelay).toFloat() / (duration - triggerDelay).coerceAtLeast(1L)).coerceIn(0f, 1f)
        val edgeFade = when {
            progress < 0.18f -> progress / 0.18f
            progress > 0.82f -> (1f - progress) / 0.18f
            else -> 1f
        }.coerceIn(0f, 1f)
        if (edgeFade <= 0f) return

        val baseColor = sourcePaint.color
        val glowColor = Color.argb(
            (82 * edgeFade).toInt().coerceIn(0, 110),
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
        canvas.save()
        canvas.clipRect(x - 8f * density, 0f, x + width + 8f * density, height.toFloat())
        sustainPaint.set(sourcePaint)
        sustainPaint.shader = null
        sustainPaint.maskFilter = BlurMaskFilter(7f * density * edgeFade, BlurMaskFilter.Blur.NORMAL)
        sustainPaint.style = Paint.Style.STROKE
        sustainPaint.strokeWidth = (1.8f * density).coerceAtLeast(1f)
        sustainPaint.color = glowColor
        canvas.drawText(text, x, baseline, sustainPaint)
        canvas.restore()
    }

    private fun calculateSweepFraction(entry: LineEntry, pos: Long, words: List<LyricWord>, measurePaint: TextPaint): Float {
        if (words.isEmpty()) return 0f
        // Use the words' own timing bounds instead of the line's begin/end so that secondary
        // (background/accompaniment) words animate correctly even when they start before or
        // end after the main lyric line. For primary words this is equivalent.
        val beginBound = words.minOfOrNull { it.begin } ?: entry.begin
        val endBound = words.maxOfOrNull { it.end } ?: entry.end
        if (pos < beginBound) return 0f
        if (pos >= endBound) return 1f

        var totalW = 0f
        for (word in words) totalW += measurePaint.measureText(word.text ?: "")
        if (totalW <= 0f) return 0f

        var cumW = 0f
        for (word in words) {
            val wordW = measurePaint.measureText(word.text ?: "")
            if (pos < word.begin) {
                return cumW / totalW
            }
            if (pos < word.end) {
                val wordProgress = if (word.duration > 0) (pos - word.begin).toFloat() / word.duration else 1f
                return (cumW + wordW * wordProgress.coerceIn(0f, 1f)) / totalW
            }
            cumW += wordW
        }
        return cumW / totalW
    }

    private fun drawEdgeFade(canvas: Canvas, w: Float, h: Float) {
        if (!edgeFadeEnabled) return
        val bottomFadeLen = h * BOTTOM_FADE_RATIO
        val topFadeLen = maxOf(h * BOTTOM_FADE_RATIO, topContentPadding).coerceAtMost(h * 0.6f)

        // 顶部渐隐
        if (topFadeLen > 0f) {
            val fadeEnd = topFadeLen
            val fadeStart = maxOf(0f, fadeEnd - h * BOTTOM_FADE_RATIO)
            if (fadeStart > 0f) {
                edgePaint.shader = null
                canvas.save()
                canvas.drawRect(0f, 0f, w, fadeStart, edgePaint.apply { color = Color.TRANSPARENT })
                edgePaint.color = Color.BLACK
                canvas.restore()
            }
            val blackAlpha5 = Color.argb(13, 0, 0, 0)
            val topShader = LinearGradient(
                0f, fadeStart, 0f, fadeEnd,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, blackAlpha5, Color.BLACK),
                floatArrayOf(0f, 0.86f, 0.93f, 1f),
                Shader.TileMode.CLAMP
            )
            edgePaint.shader = topShader
            canvas.drawRect(0f, fadeStart, w, fadeEnd, edgePaint)
        }

        // 底部渐隐：先在底部渐变区上方绘制不透明遮罩，保留歌词可见
        if (bottomFadeLen > 0f) {
            val bottomStart = h - bottomFadeLen
            edgePaint.shader = null
            edgePaint.color = Color.BLACK
            canvas.drawRect(0f, 0f, w, bottomStart, edgePaint)

            val bottomShader = LinearGradient(
                0f, bottomStart, 0f, h,
                intArrayOf(Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            edgePaint.shader = bottomShader
            canvas.drawRect(0f, bottomStart, w, h, edgePaint)
        }

        edgePaint.shader = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollAnimator?.cancel()
                scroller.abortAnimation()
                isDragging = false
                longPressHandled = false
                downTouchX = event.x
                downTouchY = event.y
                lastTouchY = event.y
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!userScrollEnabled) {
                    // Mini-lyric preview: never scroll on drag; taps still open the lyrics page.
                    return true
                }
                if (!isDragging) {
                    val totalDx = event.x - downTouchX
                    val totalDy = event.y - downTouchY
                    if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                        if (abs(totalDx) > abs(totalDy)) {
                            parent.requestDisallowInterceptTouchEvent(false)
                            velocityTracker?.recycle()
                            velocityTracker = null
                            return false
                        }
                        scrollAnimator?.cancel()
                        scroller.abortAnimation()
                        isDragging = true
                        isUserScrolling = true
                        autoScrollResumeLineIndex = currentIndex
                        parent.requestDisallowInterceptTouchEvent(true)
                        postFrame()
                    }
                }
                if (isDragging) {
                    val dy = event.y - lastTouchY
                    scrollY -= dy
                    clampScroll()
                    lastTouchY = event.y
                    autoScrollResumeTime = SystemClock.uptimeMillis() + AUTO_SCROLL_RESUME_MS
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                if (isDragging && abs(vy) > 500f) {
                    scroller.fling(
                        0, scrollY.toInt(), 0, -vy.toInt(),
                        0, 0, 0, maxScrollY.toInt()
                    )
                    autoScrollResumeTime = SystemClock.uptimeMillis() + AUTO_SCROLL_RESUME_MS
                    autoScrollResumeLineIndex = currentIndex
                } else if (isDragging) {
                    autoScrollResumeTime = SystemClock.uptimeMillis() + AUTO_SCROLL_RESUME_MS
                    autoScrollResumeLineIndex = currentIndex
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                postFrame()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (!scroller.isFinished) {
            if (scroller.computeScrollOffset()) {
                scrollY = scroller.currY.toFloat()
                clampScroll()
                invalidate()
            }
        }
    }

    private fun blendAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        framePosted = false
        choreographer.removeFrameCallback(frameCb)
        clearBlurCache()
        clearInterlude()
        popAnimators.values.toList().forEach { it.cancel() }
        popAnimators.clear()
        scrollAnimator?.cancel()
        scrollAnimator = null
        scroller.abortAnimation()
    }
}

internal fun isLyricViewLineHighlighted(
    index: Int,
    currentIndex: Int,
    activeHighlightIndices: Set<Int>
): Boolean = index in activeHighlightIndices ||
    (activeHighlightIndices.isEmpty() && index == currentIndex)
