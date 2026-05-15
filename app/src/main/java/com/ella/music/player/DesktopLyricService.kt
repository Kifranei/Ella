package com.ella.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.text.TextPaint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ella.music.data.SettingsManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ella.music.R
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class DesktopLyricService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var rootView: LinearLayout? = null
    private var lyricView: DesktopLyricView? = null
    private var controlsView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var locked = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var fontScale = 1f
    private var movedDuringTouch = false
    private var lastTapTimeMs = 0L
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val hideControlsRunnable = Runnable { hideControls() }
    private val panelBackground by lazy {
        GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(54, 18, 18, 18))
            setStroke(dp(1), Color.argb(42, 255, 255, 255))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync().also { future ->
            Futures.addCallback(
                future,
                object : FutureCallback<MediaController> {
                    override fun onSuccess(result: MediaController?) {
                        if (controllerFuture !== future) return
                        controller = result
                    }

                    override fun onFailure(t: Throwable) = Unit
                },
                mainExecutor
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE -> {
                userHidden = false
                if (rootView == null) stopSelf()
            }
            ACTION_SHOW, ACTION_UPDATE -> showOrUpdate(intent)
            ACTION_HIDE -> stopSelf()
            ACTION_UNLOCK -> setLocked(false)
            ACTION_FONT_SMALLER -> updateFontScale(-0.08f)
            ACTION_FONT_LARGER -> updateFontScale(0.08f)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        rootView?.removeCallbacks(hideControlsRunnable)
        rootView?.let { runCatching { windowManager.removeView(it) } }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        notificationManager.cancel(NOTIFICATION_ID)
        rootView = null
        lyricView = null
        controlsView = null
        layoutParams = null
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOrUpdate(intent: Intent) {
        if (!canDrawOverlay()) return
        if (userHidden) {
            stopSelf()
            return
        }
        if (rootView == null) addLyricView()
        lyricView?.setLyric(
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            pronunciation = intent.getStringExtra(EXTRA_PRONUNCIATION).orEmpty(),
            translation = intent.getStringExtra(EXTRA_TRANSLATION).orEmpty(),
            positionMs = intent.getLongExtra(EXTRA_POSITION, 0L),
            agent = intent.getStringExtra(EXTRA_AGENT).orEmpty(),
            isTtml = intent.getBooleanExtra(EXTRA_IS_TTML, false),
            backgroundText = intent.getStringExtra(EXTRA_BACKGROUND_TEXT).orEmpty(),
            backgroundTranslation = intent.getStringExtra(EXTRA_BACKGROUND_TRANSLATION).orEmpty(),
            wordTexts = intent.getStringArrayExtra(EXTRA_WORD_TEXTS)?.toList().orEmpty(),
            wordStarts = intent.getLongArrayExtra(EXTRA_WORD_STARTS) ?: LongArray(0),
            wordEnds = intent.getLongArrayExtra(EXTRA_WORD_ENDS) ?: LongArray(0),
            pronunciationWordTexts = intent.getStringArrayExtra(EXTRA_PRONUNCIATION_WORD_TEXTS)?.toList().orEmpty(),
            pronunciationWordStarts = intent.getLongArrayExtra(EXTRA_PRONUNCIATION_WORD_STARTS) ?: LongArray(0),
            pronunciationWordEnds = intent.getLongArrayExtra(EXTRA_PRONUNCIATION_WORD_ENDS) ?: LongArray(0),
            backgroundWordTexts = intent.getStringArrayExtra(EXTRA_BACKGROUND_WORD_TEXTS)?.toList().orEmpty(),
            backgroundWordStarts = intent.getLongArrayExtra(EXTRA_BACKGROUND_WORD_STARTS) ?: LongArray(0),
            backgroundWordEnds = intent.getLongArrayExtra(EXTRA_BACKGROUND_WORD_ENDS) ?: LongArray(0)
        )
    }

    private fun addLyricView() {
        val lyric = DesktopLyricView(this)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(0))
            addControl("⏮", "上一首") { controller?.seekToPrevious() }
            addControl("Ⅱ", "播放/暂停") { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
            addControl("⏭", "下一首") { controller?.seekToNext() }
            addControl("A-", "缩小歌词") { updateFontScale(-0.08f) }
            addControl("A+", "放大歌词") { updateFontScale(0.08f) }
            addControl("🔒", "锁定") { setLocked(true) }
            addControl("×", "关闭") { closeByUser() }
            visibility = View.GONE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // 纯文字悬浮窗：不要黑色背景、不要描边、不要阴影卡片
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f

            // 高度收窄，避免像一整块大卡片
            addView(lyric, LinearLayout.LayoutParams(dp(620), dp(96)))

            // 控制按钮仍保留，双击歌词时显示
            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            setOnTouchListener(::onDrag)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(96)
        }

        rootView = root
        lyricView = lyric
        controlsView = controls
        layoutParams = params
        windowManager.addView(root, params)
        clampToScreen(root, params)
    }

    private fun LinearLayout.addControl(label: String, description: String, action: () -> Unit) {
        addView(TextView(context).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(72, 255, 255, 255))
            }
            setPadding(0, 0, 0, dp(1))
            setOnClickListener {
                action()
                if (!locked && rootView != null) scheduleControlsAutoHide()
            }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        })
    }

    private fun updateFontScale(delta: Float) {
        fontScale = (fontScale + delta).coerceIn(0.72f, 1.35f)
        lyricView?.setFontScale(fontScale)
    }

    private fun closeByUser() {
        userHidden = true
        serviceScope.launch { SettingsManager(this@DesktopLyricService).setDesktopLyricEnabled(false) }
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        lyricView = null
        controlsView = null
        layoutParams = null
        stopSelf()
    }

    private fun showControlsWithTimeout() {
        if (locked) return
        controlsView?.visibility = View.VISIBLE
        setPanelVisible(true)
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        rootView?.removeCallbacks(hideControlsRunnable)
        rootView?.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS)
    }

    private fun hideControls() {
        if (!locked) {
            controlsView?.visibility = View.GONE
            setPanelVisible(false)
        }
    }

    private fun setLocked(lock: Boolean) {
        locked = lock
        controlsView?.visibility = if (lock) View.GONE else View.VISIBLE
        setPanelVisible(!lock)
        if (!lock) scheduleControlsAutoHide()
        val params = layoutParams ?: return
        params.flags = if (lock) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        rootView?.let { windowManager.updateViewLayout(it, params) }
        if (lock) postUnlockNotification() else notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun setPanelVisible(visible: Boolean) {
        val root = rootView ?: return
        root.background = if (visible) panelBackground else null
        root.setPadding(
            dp(if (visible) 12 else 8),
            dp(if (visible) 8 else 4),
            dp(if (visible) 12 else 8),
            dp(if (visible) 8 else 4)
        )
    }

    private fun onDrag(view: View, event: MotionEvent): Boolean {
        if (locked) return false
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                movedDuringTouch = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) movedDuringTouch = true
                params.x = startX + dx.toInt()
                params.y = startY + dy.toInt()
                clampToScreen(view, params)
                windowManager.updateViewLayout(view, params)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!movedDuringTouch) handleTap()
                return true
            }
        }
        return true
    }

    private fun handleTap() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTapTimeMs <= DOUBLE_TAP_TIMEOUT_MS) {
            showControlsWithTimeout()
            lastTapTimeMs = 0L
        } else {
            lastTapTimeMs = now
        }
    }

    private fun clampToScreen(view: View, params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val halfWidth = ((view.width.takeIf { it > 0 } ?: dp(636)) / 2)

        val maxX = (metrics.widthPixels / 2 - halfWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - (view.height.takeIf { it > 0 } ?: dp(112))).coerceAtLeast(0)
        params.x = params.x.coerceIn(-maxX, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun postUnlockNotification() {
        ensureNotificationChannel()
        val intent = PendingIntent.getService(
            this,
            0,
            Intent(this, DesktopLyricService::class.java).setAction(ACTION_UNLOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        notificationManager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle("桌面歌词已锁定")
                .setContentText("点击解除锁定")
                .setContentIntent(intent)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun canDrawOverlay(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class DesktopLyricView(context: Context) : View(context) {
        private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            textSize = 20f * resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }

        private val activePaint = Paint(pendingPaint).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(10f, 0f, 2f, Color.argb(210, 0, 0, 0))
        }

        private val glowPaint = Paint(activePaint).apply {
            color = Color.argb(150, 125, 205, 255)
            setShadowLayer(18f, 0f, 0f, color)
        }

        private val pronunciationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(155, 255, 255, 255)
            textSize = 12f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            setShadowLayer(7f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }

        private val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(165, 255, 255, 255)
            textSize = 13f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            setShadowLayer(7f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }

        private var lyricText = "Ella Music"
        private var pronunciation = ""
        private var translation = ""
        private var agent = ""
        private var isTtml = false
        private var backgroundText = ""
        private var backgroundTranslation = ""
        private var fontScale = 1f
        private var positionMs = 0L
        private var words = emptyList<DesktopWord>()
        private var pronunciationWords = emptyList<DesktopWord>()
        private var backgroundWords = emptyList<DesktopWord>()

        fun setFontScale(scale: Float) {
            fontScale = scale
            invalidate()
        }

        fun setLyric(
            text: String,
            pronunciation: String,
            translation: String,
            positionMs: Long,
            agent: String,
            isTtml: Boolean,
            backgroundText: String,
            backgroundTranslation: String,
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
            this.lyricText = text.ifBlank { if (backgroundText.isBlank()) "♪" else "" }
            this.pronunciation = pronunciation
            this.translation = translation
            this.agent = agent
            this.isTtml = isTtml
            this.backgroundText = backgroundText
            this.backgroundTranslation = backgroundTranslation
            this.positionMs = positionMs
            words = wordTexts.mapIndexedNotNull { index, word ->
                val start = wordStarts.getOrNull(index) ?: return@mapIndexedNotNull null
                val end = wordEnds.getOrNull(index) ?: return@mapIndexedNotNull null
                DesktopWord(word, start, end)
            }
            pronunciationWords = pronunciationWordTexts.mapIndexedNotNull { index, word ->
                val start = pronunciationWordStarts.getOrNull(index) ?: return@mapIndexedNotNull null
                val end = pronunciationWordEnds.getOrNull(index) ?: return@mapIndexedNotNull null
                DesktopWord(word, start, end)
            }
            backgroundWords = backgroundWordTexts.mapIndexedNotNull { index, word ->
                val start = backgroundWordStarts.getOrNull(index) ?: return@mapIndexedNotNull null
                val end = backgroundWordEnds.getOrNull(index) ?: return@mapIndexedNotNull null
                DesktopWord(word, start, end)
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val hasBackground = backgroundText.isNotBlank() || backgroundWords.isNotEmpty()
            val hasPronunciation = pronunciation.isNotBlank() || pronunciationWords.isNotEmpty()
            val primaryAlign = ttmlAlignForPrimary()
            val backgroundAlign = primaryAlign.opposite()
            if (hasPronunciation && !hasBackground) {
                drawSmallLine(
                    canvas = canvas,
                    fallbackText = pronunciation,
                    lineWords = pronunciationWords,
                    anchorX = width / 2f,
                    baseline = height * 0.24f,
                    maxWidth = width * 0.88f,
                    align = AnchorAlign.Center
                )
                drawLine(canvas, lyricText, words, width / 2f, height * 0.54f, width * 0.9f, AnchorAlign.Center, true)
                if (translation.isNotBlank()) {
                    drawFittedText(canvas, translation, width / 2f, height * 0.82f, width * 0.88f, AnchorAlign.Center, translationPaint)
                }
            } else if (hasBackground) {
                val primaryBaseline = height * 0.34f
                val backgroundBaseline = height * 0.62f
                drawLine(canvas, lyricText, words, primaryAlign.anchorX(width), primaryBaseline, width * 0.82f, primaryAlign, true)
                drawLine(
                    canvas = canvas,
                    fallbackText = backgroundText.ifBlank { backgroundWords.joinToString("") { it.text } },
                    lineWords = backgroundWords,
                    anchorX = backgroundAlign.anchorX(width),
                    baseline = backgroundBaseline,
                    maxWidth = width * 0.82f,
                    align = backgroundAlign,
                    primary = false
                )
                val translationText = listOf(translation, backgroundTranslation).firstOrNull { it.isNotBlank() }.orEmpty()
                if (translationText.isNotBlank()) {
                    drawFittedText(canvas, translationText, width / 2f, height * 0.86f, width * 0.88f, AnchorAlign.Center, translationPaint)
                }
            } else {
                val baseline = height / 2f - if (translation.isBlank()) -5f else 10f
                drawLine(canvas, lyricText, words, primaryAlign.anchorX(width), baseline, width * 0.9f, primaryAlign, true)
                if (translation.isNotBlank()) {
                    drawFittedText(canvas, translation, width / 2f, baseline + 25f * resources.displayMetrics.scaledDensity, width * 0.88f, AnchorAlign.Center, translationPaint)
                }
            }
        }

        private fun drawLine(canvas: Canvas, fallbackText: String, lineWords: List<DesktopWord>, anchorX: Float, baseline: Float, maxWidth: Float, align: AnchorAlign, primary: Boolean) {
            val oldPending = pendingPaint.textSize
            val oldActive = activePaint.textSize
            val oldGlow = glowPaint.textSize
            val targetSize = 20f * resources.displayMetrics.scaledDensity * fontScale
            pendingPaint.textSize = targetSize
            activePaint.textSize = targetSize
            glowPaint.textSize = targetSize
            if (lineWords.isEmpty()) {
                drawFittedText(canvas, fallbackText, anchorX, baseline, maxWidth, align, if (primary) activePaint else pendingPaint)
            } else {
                drawWords(canvas, anchorX, baseline, maxWidth, align, fallbackText, lineWords, primary)
            }
            pendingPaint.textSize = oldPending
            activePaint.textSize = oldActive
            glowPaint.textSize = oldGlow
        }

        private fun drawSmallLine(canvas: Canvas, fallbackText: String, lineWords: List<DesktopWord>, anchorX: Float, baseline: Float, maxWidth: Float, align: AnchorAlign) {
            val oldPendingSize = pendingPaint.textSize
            val oldActiveSize = activePaint.textSize
            val oldGlowSize = glowPaint.textSize
            val oldPronunciationSize = pronunciationPaint.textSize
            val targetSize = 12f * resources.displayMetrics.scaledDensity * fontScale
            pendingPaint.textSize = targetSize
            activePaint.textSize = targetSize
            glowPaint.textSize = targetSize
            pronunciationPaint.textSize = targetSize
            if (lineWords.isEmpty()) {
                drawFittedText(canvas, fallbackText, anchorX, baseline, maxWidth, align, pronunciationPaint)
            } else {
                drawWords(canvas, anchorX, baseline, maxWidth, align, fallbackText, lineWords, false)
            }
            pendingPaint.textSize = oldPendingSize
            activePaint.textSize = oldActiveSize
            glowPaint.textSize = oldGlowSize
            pronunciationPaint.textSize = oldPronunciationSize
        }

        private fun drawWords(
            canvas: Canvas,
            anchorX: Float,
            baseline: Float,
            maxWidth: Float,
            align: AnchorAlign,
            lineText: String,
            lineWords: List<DesktopWord>,
            primary: Boolean
        ) {
            val originalPending = pendingPaint.textSize
            val originalActive = activePaint.textSize
            val originalGlow = glowPaint.textSize
            val originalPendingAlign = pendingPaint.textAlign
            val originalActiveAlign = activePaint.textAlign
            val originalGlowAlign = glowPaint.textAlign
            val hasNaturalSpacing = lineText.any { it.isWhitespace() }
            val compactCjkLine = !hasNaturalSpacing && lineText.any { it.isCjkChar() }
            val displayWords = lineWords.mapNotNull { word ->
                val text = if (hasNaturalSpacing) word.text else word.text.trim()
                text.takeIf { it.isNotBlank() }?.let { word.copy(text = it) }
            }
            if (displayWords.isEmpty()) return
            val widths = displayWords.map { pendingPaint.measureText(it.text) }
            val rawGap = if (hasNaturalSpacing || compactCjkLine) 0f else 6f * resources.displayMetrics.density
            val totalWidth = widths.sum() + rawGap * (widths.size - 1).coerceAtLeast(0)
            val scale = (maxWidth / totalWidth.coerceAtLeast(1f)).coerceAtMost(1f)
            val gap = rawGap * scale
            pendingPaint.textSize *= scale
            activePaint.textSize *= scale
            glowPaint.textSize *= scale
            pendingPaint.textAlign = Paint.Align.LEFT
            activePaint.textAlign = Paint.Align.LEFT
            glowPaint.textAlign = Paint.Align.LEFT
            val scaledWidths = displayWords.map { pendingPaint.measureText(it.text) }
            val scaledTotalWidth = scaledWidths.sum() + gap * (scaledWidths.size - 1).coerceAtLeast(0)
            var x = align.startX(anchorX, scaledTotalWidth)
            displayWords.forEachIndexed { index, word ->
                val token = word.text
                val isCurrent = positionMs in word.startMs..word.endMs
                val isPast = positionMs > word.endMs
                val paint = when {
                    isCurrent && primary -> activePaint
                    isPast -> Paint(activePaint).apply { color = Color.argb(210, 255, 255, 255) }
                    else -> pendingPaint
                }
                if (isCurrent && word.endMs - word.startMs >= 900L) {
                    val pulse = 0.42f + 0.34f * ((sin(positionMs / 145.0).toFloat() + 1f) / 2f)
                    glowPaint.alpha = (pulse * 255).toInt().coerceIn(0, 255)
                    canvas.drawText(token, x, baseline, glowPaint)
                }
                canvas.drawText(token, x, baseline, paint)
                x += scaledWidths[index] + gap
            }
            pendingPaint.textSize = originalPending
            activePaint.textSize = originalActive
            glowPaint.textSize = originalGlow
            pendingPaint.textAlign = originalPendingAlign
            activePaint.textAlign = originalActiveAlign
            glowPaint.textAlign = originalGlowAlign
        }

        private fun drawFittedText(canvas: Canvas, value: String, anchorX: Float, baseline: Float, maxWidth: Float, align: AnchorAlign, paint: Paint) {
            if (value.isBlank()) return
            val oldSize = paint.textSize
            val oldAlign = paint.textAlign
            val measured = paint.measureText(value)
            if (measured > maxWidth) paint.textSize = oldSize * (maxWidth / measured).coerceIn(0.34f, 1f)
            paint.textAlign = align.paintAlign
            val fitted = if (paint.measureText(value) > maxWidth) {
                TextUtils.ellipsize(value, TextPaint(paint), maxWidth, TextUtils.TruncateAt.END).toString()
            } else {
                value
            }
            canvas.drawText(fitted, anchorX, baseline, paint)
            paint.textSize = oldSize
            paint.textAlign = oldAlign
        }

        private fun Char.isCjkChar(): Boolean =
            Character.UnicodeBlock.of(this) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES
            )

        private fun ttmlAlignForPrimary(): AnchorAlign {
            if (!isTtml) return AnchorAlign.Center
            if (agent.isBlank()) {
                return if (backgroundText.isNotBlank() || backgroundWords.isNotEmpty()) AnchorAlign.Left else AnchorAlign.Center
            }
            return if (agent.equals("v2", ignoreCase = true)) AnchorAlign.Right else AnchorAlign.Left
        }
    }

    private data class DesktopWord(val text: String, val startMs: Long, val endMs: Long)

    private enum class AnchorAlign(val paintAlign: Paint.Align) {
        Left(Paint.Align.LEFT),
        Center(Paint.Align.CENTER),
        Right(Paint.Align.RIGHT);

        fun anchorX(width: Int): Float = when (this) {
            Left -> width * 0.08f
            Center -> width / 2f
            Right -> width * 0.92f
        }

        fun startX(anchorX: Float, lineWidth: Float): Float = when (this) {
            Left -> anchorX
            Center -> anchorX - lineWidth / 2f
            Right -> anchorX - lineWidth
        }

        fun opposite(): AnchorAlign = when (this) {
            Left -> Right
            Right -> Left
            Center -> Center
        }
    }

    companion object {
        const val ACTION_SHOW = "com.ella.music.action.SHOW_DESKTOP_LYRIC"
        const val ACTION_UPDATE = "com.ella.music.action.UPDATE_DESKTOP_LYRIC"
        const val ACTION_ENABLE = "com.ella.music.action.ENABLE_DESKTOP_LYRIC"
        const val ACTION_HIDE = "com.ella.music.action.HIDE_DESKTOP_LYRIC"
        const val ACTION_UNLOCK = "com.ella.music.action.UNLOCK_DESKTOP_LYRIC"
        const val ACTION_FONT_SMALLER = "com.ella.music.action.DESKTOP_LYRIC_FONT_SMALLER"
        const val ACTION_FONT_LARGER = "com.ella.music.action.DESKTOP_LYRIC_FONT_LARGER"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PRONUNCIATION = "pronunciation"
        const val EXTRA_TRANSLATION = "translation"
        const val EXTRA_POSITION = "position"
        const val EXTRA_AGENT = "agent"
        const val EXTRA_IS_TTML = "is_ttml"
        const val EXTRA_BACKGROUND_TEXT = "background_text"
        const val EXTRA_BACKGROUND_TRANSLATION = "background_translation"
        const val EXTRA_WORD_TEXTS = "word_texts"
        const val EXTRA_WORD_STARTS = "word_starts"
        const val EXTRA_WORD_ENDS = "word_ends"
        const val EXTRA_PRONUNCIATION_WORD_TEXTS = "pronunciation_word_texts"
        const val EXTRA_PRONUNCIATION_WORD_STARTS = "pronunciation_word_starts"
        const val EXTRA_PRONUNCIATION_WORD_ENDS = "pronunciation_word_ends"
        const val EXTRA_BACKGROUND_WORD_TEXTS = "background_word_texts"
        const val EXTRA_BACKGROUND_WORD_STARTS = "background_word_starts"
        const val EXTRA_BACKGROUND_WORD_ENDS = "background_word_ends"
        private const val CHANNEL_ID = "ella_desktop_lyric"
        private const val NOTIFICATION_ID = 0x454c4459
        private const val CONTROLS_AUTO_HIDE_MS = 4_000L
        private const val DOUBLE_TAP_TIMEOUT_MS = 360L
        private var userHidden = false
    }
}
