package com.ella.music.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val SHARE_CARD_WIDTH = 1080
private const val SHARE_CARD_MIN_HEIGHT = 820
private const val SHARE_CARD_MAX_HEIGHT = 1720
private const val SHARE_CARD_HORIZONTAL_PADDING = 92f
private const val SHARE_CARD_TOP_PADDING = 88f
private const val SHARE_CARD_BOTTOM_PADDING = 72f
private const val SHARE_CARD_COVER_SIZE = 120f
private const val SHARE_CARD_MAX_BLOCKS = 8

fun shareLyricCard(
    context: Context,
    song: Song?,
    line: LyricLine,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String = "",
    customInfo: String = ""
) {
    shareLyricCard(
        context = context,
        song = song,
        lines = listOf(line),
        cover = cover,
        backgroundColors = backgroundColors,
        annotation = annotation,
        customInfo = customInfo
    )
}

fun shareLyricCard(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String = "",
    customInfo: String = ""
) {
    runCatching {
        val shareLines = lines.filter { it.sharePrimaryText().isNotBlank() }.ifEmpty {
            lines.take(1)
        }
        val bitmap = createLyricShareCard(
            context = context,
            song = song,
            lines = shareLines,
            cover = cover,
            backgroundColors = backgroundColors,
            annotation = annotation,
            customInfo = customInfo
        )
        val uri = writeLyricShareCard(context, bitmap)
        bitmap.recycle()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra("com.mocharealm.compound.EXTRA_SOURCE_NAME", context.getString(R.string.app_name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Ella Music Lyric Card", uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.lyric_share_chooser_title)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.lyric_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun createLyricShareCard(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String,
    customInfo: String
): Bitmap {
    val palette = cover.extractSharePalette(backgroundColors)
    val blocks = lines
        .mapNotNull { it.toShareLyricBlock() }
        .ifEmpty { listOf(ShareLyricBlock("\u266a", emptyList())) }
        .take(SHARE_CARD_MAX_BLOCKS)

    val header = measureShareHeader(
        title = song?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lyric_share_unknown_song),
        annotation = annotation,
        artist = song?.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lyric_share_unknown_artist)
    )
    val lyricMeasure = measureShareLyrics(blocks)
    val footerMeasure = measureShareFooter(lyricShareFooter(context, customInfo))
    val lyricTopBase = SHARE_CARD_TOP_PADDING + header.height + 68f
    val contentHeight = (
        lyricTopBase +
            lyricMeasure.height +
            footerMeasure.height +
            SHARE_CARD_BOTTOM_PADDING
        ).roundToInt()
    val height = contentHeight.coerceIn(SHARE_CARD_MIN_HEIGHT, SHARE_CARD_MAX_HEIGHT)
    val bitmap = Bitmap.createBitmap(SHARE_CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    drawShareBackground(canvas, SHARE_CARD_WIDTH, height, cover, palette)

    val headerTop = SHARE_CARD_TOP_PADDING
    val lyricsExtraSpace = (height - contentHeight).coerceAtLeast(0)
    val lyricTop = lyricTopBase + lyricsExtraSpace * 0.24f
    val footerBaseline = height - SHARE_CARD_BOTTOM_PADDING - footerMeasure.paint.fontMetrics.descent

    drawShareHeader(
        canvas = canvas,
        cover = cover,
        measure = header,
        top = headerTop
    )
    drawShareLyrics(canvas, lyricMeasure, top = lyricTop)
    drawShareFooter(canvas, footerMeasure, footerBaseline)
    return bitmap
}

private data class ShareHeaderMeasure(
    val titleLayout: StaticLayout,
    val annotationLayout: StaticLayout?,
    val artistLayout: StaticLayout,
    val contentLeft: Float,
    val coverRect: RectF,
    val height: Float
)

private data class MeasuredLyricLayout(
    val layout: StaticLayout,
    val paint: TextPaint
)

private data class MeasuredShareBlock(
    val primary: MeasuredLyricLayout,
    val secondary: List<MeasuredLyricLayout>,
    val spacingAfter: Float
)

private data class ShareLyricMeasure(
    val blocks: List<MeasuredShareBlock>,
    val height: Float
)

private data class ShareFooterMeasure(
    val text: String,
    val paint: TextPaint,
    val height: Float
)

internal data class ShareLyricBlock(
    val primary: String,
    val secondary: List<String>
)

private fun measureShareHeader(
    title: String,
    annotation: String,
    artist: String
): ShareHeaderMeasure {
    val coverRect = RectF(
        SHARE_CARD_HORIZONTAL_PADDING,
        0f,
        SHARE_CARD_HORIZONTAL_PADDING + SHARE_CARD_COVER_SIZE,
        SHARE_CARD_COVER_SIZE
    )
    val contentLeft = coverRect.right + 28f
    val contentWidth = (SHARE_CARD_WIDTH - contentLeft - SHARE_CARD_HORIZONTAL_PADDING).roundToInt()
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        setShadowLayer(18f, 0f, 8f, Color.argb(76, 0, 0, 0))
    }
    val annotationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 255, 255, 255)
        textSize = 27f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(168, 255, 255, 255)
        textSize = 26f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val titleLayout = buildLayout(title, titlePaint, contentWidth, maxLines = 2, lineSpacingAdd = 4f, lineSpacingMult = 1.02f)
    val annotationLayout = annotation.trim().takeIf { it.isNotBlank() }?.let {
        buildLayout(it, annotationPaint, contentWidth, maxLines = 1, lineSpacingAdd = 2f, lineSpacingMult = 1f)
    }
    val artistLayout = buildLayout(artist, artistPaint, contentWidth, maxLines = 2, lineSpacingAdd = 2f, lineSpacingMult = 1f)
    val stackedHeight = titleLayout.height +
        (annotationLayout?.height?.plus(10) ?: 0) +
        12 +
        artistLayout.height
    val height = max(SHARE_CARD_COVER_SIZE, stackedHeight.toFloat())
    return ShareHeaderMeasure(
        titleLayout = titleLayout,
        annotationLayout = annotationLayout,
        artistLayout = artistLayout,
        contentLeft = contentLeft,
        coverRect = coverRect,
        height = height
    )
}

private fun measureShareLyrics(blocks: List<ShareLyricBlock>): ShareLyricMeasure {
    val maxWidth = (SHARE_CARD_WIDTH - SHARE_CARD_HORIZONTAL_PADDING * 2).roundToInt()
    val longestLine = blocks.maxOfOrNull { it.primary.length } ?: 0
    val preferredSizes = buildList {
        add(baseShareLyricTextSize(blocks.size, longestLine))
        add(76f)
        add(70f)
        add(64f)
        add(58f)
        add(54f)
        add(50f)
    }.distinct()

    var best: ShareLyricMeasure? = null
    preferredSizes.forEach { primarySize ->
        val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = primarySize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setShadowLayer(20f, 0f, 8f, Color.argb(92, 0, 0, 0))
        }
        val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(188, 255, 255, 255)
            textSize = (primarySize * 0.43f).coerceIn(24f, 34f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            setShadowLayer(10f, 0f, 4f, Color.argb(72, 0, 0, 0))
        }
        val measuredBlocks = blocks.mapIndexed { index, block ->
            val primaryLayout = buildLayout(
                text = block.primary,
                paint = primaryPaint,
                width = maxWidth,
                maxLines = if (blocks.size <= 2) 4 else 3,
                lineSpacingAdd = (primarySize * 0.07f).coerceIn(4f, 10f),
                lineSpacingMult = 1f
            )
            val secondaryLayouts = block.secondary.take(1).map {
                MeasuredLyricLayout(
                    layout = buildLayout(
                        text = it,
                        paint = secondaryPaint,
                        width = maxWidth,
                        maxLines = 2,
                        lineSpacingAdd = 4f,
                        lineSpacingMult = 1f
                    ),
                    paint = secondaryPaint
                )
            }
            MeasuredShareBlock(
                primary = MeasuredLyricLayout(primaryLayout, primaryPaint),
                secondary = secondaryLayouts,
                spacingAfter = if (index == blocks.lastIndex) 0f else (primarySize * 0.24f).coerceIn(20f, 34f)
            )
        }
        val totalHeight = measuredBlocks.fold(0f) { total, block ->
            total +
                block.primary.layout.height +
                block.secondary.fold(0f) { secondaryTotal, secondary ->
                    secondaryTotal + secondary.layout.height + 10f
                } +
                block.spacingAfter
        }
        val measure = ShareLyricMeasure(measuredBlocks, totalHeight)
        best = measure
        if (totalHeight <= 980f) return measure
    }
    return best ?: ShareLyricMeasure(emptyList(), 0f)
}

private fun measureShareFooter(text: String): ShareFooterMeasure {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(112, 255, 255, 255)
        textSize = 24f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val height = paint.fontMetrics.run { bottom - top } + 8f
    return ShareFooterMeasure(text = text, paint = paint, height = height)
}

private fun drawShareHeader(
    canvas: Canvas,
    cover: Bitmap?,
    measure: ShareHeaderMeasure,
    top: Float
) {
    val coverRect = RectF(
        measure.coverRect.left,
        top,
        measure.coverRect.right,
        top + measure.coverRect.height()
    )
    drawShareHeaderCover(canvas, cover, coverRect)
    var textTop = top + 6f
    drawLayout(canvas, measure.titleLayout, measure.contentLeft, textTop)
    textTop += measure.titleLayout.height + 10f
    measure.annotationLayout?.let {
        drawLayout(canvas, it, measure.contentLeft, textTop)
        textTop += it.height + 12f
    }
    drawLayout(canvas, measure.artistLayout, measure.contentLeft, textTop)

    val dividerY = top + measure.height + 30f
    val dividerLeft = SHARE_CARD_HORIZONTAL_PADDING
    val dividerRight = SHARE_CARD_WIDTH - SHARE_CARD_HORIZONTAL_PADDING
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            dividerLeft,
            dividerY,
            dividerRight,
            dividerY,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(74, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        strokeWidth = 2f
    }
    canvas.drawLine(dividerLeft, dividerY, dividerRight, dividerY, dividerPaint)
}

private fun drawShareHeaderCover(canvas: Canvas, cover: Bitmap?, rect: RectF) {
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 0, 0, 0)
        setShadowLayer(24f, 0f, 10f, Color.argb(72, 0, 0, 0))
    }
    canvas.drawRoundRect(rect, 30f, 30f, shadowPaint)
    if (cover != null) {
        drawRoundedCover(canvas, cover, rect, 30f)
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.argb(120, 255, 255, 255),
                    Color.argb(34, 255, 255, 255)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, 30f, 30f, placeholderPaint)
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(208, 255, 255, 255)
            textSize = 42f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val baseline = rect.centerY() - (notePaint.descent() + notePaint.ascent()) / 2f
        canvas.drawText("\u266a", rect.centerX(), baseline, notePaint)
    }
}

private fun drawShareLyrics(
    canvas: Canvas,
    measure: ShareLyricMeasure,
    top: Float
) {
    var y = top
    measure.blocks.forEach { block ->
        drawLayout(canvas, block.primary.layout, SHARE_CARD_HORIZONTAL_PADDING, y)
        y += block.primary.layout.height + 12f
        block.secondary.forEach { secondary ->
            drawLayout(canvas, secondary.layout, SHARE_CARD_HORIZONTAL_PADDING, y)
            y += secondary.layout.height + 10f
        }
        y += block.spacingAfter
    }
}

private fun drawShareFooter(
    canvas: Canvas,
    measure: ShareFooterMeasure,
    baseline: Float
) {
    canvas.drawText(measure.text, SHARE_CARD_HORIZONTAL_PADDING, baseline, measure.paint)
}

private fun drawShareBackground(
    canvas: Canvas,
    width: Int,
    height: Int,
    cover: Bitmap?,
    colors: List<Int>
) {
    val fallbackColors = listOf(
        Color.rgb(69, 78, 110),
        Color.rgb(36, 61, 92),
        Color.rgb(19, 25, 34)
    )
    val picked = colors.filter { Color.alpha(it) > 0 }.ifEmpty { fallbackColors }
    val c1 = picked.first().boostForShare()
    val c2 = picked.getOrElse(1) { c1 }.boostForShare()
    val c3 = picked.last().boostForShare()

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                c1.lightenForShare(1.02f),
                c2.darkenForShare(0.76f),
                c3.darkenForShare(0.58f)
            ),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }

    drawShareColorBlob(canvas, width * 0.14f, height * 0.18f, width * 0.72f, c1.lightenForShare(1.08f), 92)
    drawShareColorBlob(canvas, width * 0.82f, height * 0.28f, width * 0.58f, c2.lightenForShare(1.04f), 72)
    drawShareColorBlob(canvas, width * 0.48f, height * 0.82f, width * 0.76f, c3.lightenForShare(1.08f), 82)

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            width * 0.38f,
            height * 0.42f,
            width * 0.82f,
            intArrayOf(
                Color.argb(56, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }

    cover?.let {
        val accentRect = RectF(width * 0.55f, height * 0.12f, width * 0.96f, height * 0.54f)
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                accentRect.left,
                accentRect.top,
                accentRect.right,
                accentRect.bottom,
                intArrayOf(
                    Color.argb(42, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(accentRect, 52f, 52f, this)
        }
    }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.argb(74, 4, 7, 12),
                Color.argb(18, 4, 7, 12),
                Color.argb(96, 4, 7, 12)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(20, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }
}

private fun drawShareColorBlob(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
    alpha: Int
) {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, this)
    }
}

private fun drawRoundedCover(canvas: Canvas, cover: Bitmap, rect: RectF, radius: Float) {
    val path = Path().apply {
        addRoundRect(rect, radius, radius, Path.Direction.CW)
    }
    val save = canvas.save()
    canvas.clipPath(path)
    val cropped = cover.centerCropScaled(rect.width().roundToInt(), rect.height().roundToInt())
    canvas.drawBitmap(
        cropped,
        rect.left,
        rect.top,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    )
    cropped.recycle()
    canvas.restoreToCount(save)
}

private fun Bitmap.centerCropScaled(width: Int, height: Int): Bitmap {
    val scale = max(width / this.width.toFloat(), height / this.height.toFloat())
    val scaledWidth = (this.width * scale).toInt().coerceAtLeast(width)
    val scaledHeight = (this.height * scale).toInt().coerceAtLeast(height)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    val left = ((scaledWidth - width) / 2).coerceAtLeast(0)
    val top = ((scaledHeight - height) / 2).coerceAtLeast(0)
    val result = Bitmap.createBitmap(scaled, left, top, width, height)
    if (scaled !== this && scaled !== result) {
        scaled.recycle()
    }
    return result
}

private fun buildLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int,
    lineSpacingAdd: Float,
    lineSpacingMult: Float
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(lineSpacingAdd, lineSpacingMult)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
}

private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
    val save = canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restoreToCount(save)
}

private fun writeLyricShareCard(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, "lyric_share").apply {
        deleteRecursively()
        mkdirs()
    }
    val file = File(dir, "ella_lyric_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

internal fun LyricLine.sharePrimaryText(): String {
    return text.trim().ifBlank {
        backgroundText?.trim().orEmpty()
    }
}

internal fun LyricLine.toShareLyricBlock(): ShareLyricBlock? {
    val primary = sharePrimaryText().takeIf { it.isNotBlank() } ?: return null
    val secondary = listOfNotNull(
        translation?.trim()?.takeIf { it.isNotBlank() },
        backgroundText?.trim()?.takeIf { it.isNotBlank() && it != primary },
        backgroundTranslation?.trim()?.takeIf { it.isNotBlank() }
    ).distinct()
    return ShareLyricBlock(primary = primary, secondary = secondary)
}

private fun baseShareLyricTextSize(blockCount: Int, longestLine: Int): Float {
    return when {
        blockCount <= 1 && longestLine <= 18 -> 90f
        blockCount <= 2 && longestLine <= 22 -> 82f
        blockCount <= 3 && longestLine <= 24 -> 76f
        blockCount <= 4 -> 70f
        blockCount <= 6 -> 62f
        else -> 56f
    }
}

private fun Bitmap?.extractSharePalette(fallback: List<Int>): List<Int> {
    if (this == null || width <= 0 || height <= 0) return fallback
    val step = (minOf(width, height) / 42).coerceAtLeast(1)
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var weightSum = 0.0
    val hsv = FloatArray(3)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            if (Color.alpha(pixel) > 32) {
                Color.colorToHSV(pixel, hsv)
                val saturation = hsv[1].coerceIn(0f, 1f)
                val value = hsv[2].coerceIn(0f, 1f)
                val weight = (0.28 + saturation * 1.9 + value * 0.7).let {
                    if (value < 0.10f) it * 0.20 else it
                }
                red += Color.red(pixel) * weight
                green += Color.green(pixel) * weight
                blue += Color.blue(pixel) * weight
                weightSum += weight
            }
            x += step
        }
        y += step
    }
    if (weightSum <= 0.0) return fallback
    val base = Color.rgb(
        (red / weightSum).toInt().coerceIn(0, 255),
        (green / weightSum).toInt().coerceIn(0, 255),
        (blue / weightSum).toInt().coerceIn(0, 255)
    ).boostForShare()
    return listOf(
        base.lightenForShare(1.05f),
        base.darkenForShare(0.82f),
        base.darkenForShare(0.62f)
    )
}

private fun Int.boostForShare(): Int {
    val r = Color.red(this)
    val g = Color.green(this)
    val b = Color.blue(this)
    val maxChannel = maxOf(r, g, b).coerceAtLeast(1)
    val boost = (174f / maxChannel).coerceIn(1.06f, 2.20f)
    return Color.rgb(
        (r * boost).toInt().coerceIn(0, 255),
        (g * boost).toInt().coerceIn(0, 255),
        (b * boost).toInt().coerceIn(0, 255)
    )
}

private fun Int.lightenForShare(factor: Float): Int {
    return Color.rgb(
        (Color.red(this) * factor).toInt().coerceIn(0, 255),
        (Color.green(this) * factor).toInt().coerceIn(0, 255),
        (Color.blue(this) * factor).toInt().coerceIn(0, 255)
    )
}

private fun Int.darkenForShare(factor: Float): Int {
    return Color.rgb(
        (Color.red(this) * factor).toInt().coerceIn(0, 255),
        (Color.green(this) * factor).toInt().coerceIn(0, 255),
        (Color.blue(this) * factor).toInt().coerceIn(0, 255)
    )
}

private fun lyricShareFooter(context: Context, customInfo: String): String {
    val normalized = customInfo.trim().removePrefix("@").trim()
    return if (normalized.isBlank()) {
        context.getString(R.string.lyric_share_footer_default)
    } else {
        context.getString(R.string.lyric_share_footer_custom, normalized)
    }
}
