package com.ella.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.ui.player.LocalPlayerSurfaceActive
import io.github.proify.lyricon.lyric.view.LyricView
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.abs

@Composable
fun SmoothLyricView(
    songId: Long,
    songTitle: String,
    songArtist: String,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    showTranslation: Boolean,
    showPronunciation: Boolean = true,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    fontPath: String = "",
    fontWeight: FontWeight = FontWeight.ExtraBold,
    italic: Boolean = false,
    contentColor: Color = Color.White,
    primaryTextSizeSp: Float = 28f,
    secondaryTextSizeSp: Float = 15f,
    secondaryFontScale: Float = 1f,
    anchorOffsetRatio: Float = -0.12f,
    topContentPadding: Dp = 0.dp,
    nonCurrentLineBlurDistance: Int = 2,
    nonCurrentLineBlurEnabled: Boolean = true,
    lineAlphaAnimationsEnabled: Boolean = true,
    autoScrollResumeEnabled: Boolean = true,
    userScrollEnabled: Boolean = true,
    lineGapDp: Float? = null,
    lyricTextAlign: Int = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
    onLineClick: (LyricLine) -> Unit = {},
    onLineDoubleClick: () -> Unit = {},
    onLineLongClick: (LyricLine) -> Unit = {}
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_lyrics),
                fontSize = 16.sp,
                color = contentColor.copy(alpha = 0.72f)
            )
        }
        return
    }

    val density = LocalDensity.current
    val surfaceActive = LocalPlayerSurfaceActive.current
    val context = LocalContext.current
    // Global preference: show romaji/phonetic guides below the main line (main → romaji → translation).
    val pronunciationBelow by remember(context) { SettingsManager.getInstance(context).lyricPronunciationBelow }
        .collectAsState(initial = false)
    // Key only on songId + lyric content, NOT songTitle/songArtist: media-notification lyrics patch
    // the media title with the current line, and if that briefly leaks into the song title it must
    // not rebuild the LyricView (which resets scroll → flicker). title/artist only feed the empty
    // placeholder, so capturing them at first composition is fine.
    val lyriconSong = remember(songId, lyrics) {
        lyrics.toLyriconSong(songId, songTitle, songArtist)
    }
    val pronunciationWordsByBegin = remember(lyrics) {
        lyrics.mapNotNull { line ->
            val words = line.pronunciationWords.toLyriconWords()
            if (words.isEmpty()) null else line.timeMs to words
        }.toMap()
    }
    val hasTimedWordAnimations = remember(lyrics) {
        lyrics.any { line ->
            line.words.isNotEmpty() ||
                line.pronunciationWords.isNotEmpty() ||
                line.backgroundWords.isNotEmpty()
        }
    }
    val forcedTextAlignment = remember(lyrics, lyricTextAlign) {
        if (lyrics.hasProtectedLyricAlignment()) {
            -1
        } else {
            lyricTextAlign.coerceIn(
                SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
                SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT
            )
        }
    }
    val lyricTypeface = remember(fontPath, fontWeight, italic) {
        loadAndroidTypeface(fontPath, fontWeight.weight, italic = italic, boldFallback = true)
    }
    val secondaryTypeface = remember(fontPath, fontWeight, italic) {
        loadAndroidTypeface(
            fontPath = fontPath,
            weight = (fontWeight.weight - 200).coerceIn(100, 900),
            italic = italic,
            boldFallback = false
        )
    }
    val contentArgb = contentColor.toArgb()
    val syllableGlowColor = if (contentColor.luminance() < 0.45f) {
        Color.Black.copy(alpha = 0.58f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }
    val style = remember(
        fontScale,
        density.fontScale,
        lyricTypeface,
        secondaryTypeface,
        primaryTextSizeSp,
        secondaryTextSizeSp,
        secondaryFontScale,
        contentArgb,
        syllableGlowColor
    ) {
        buildLyriconRichLineConfig(
            primaryTextSizePx = with(density) { (primaryTextSizeSp.sp * fontScale).toPx() },
            secondaryTextSizePx = with(density) { (secondaryTextSizeSp.sp * fontScale * secondaryFontScale).toPx() },
            primaryTypeface = lyricTypeface,
            secondaryTypeface = secondaryTypeface,
            primaryTextColor = contentArgb,
            secondaryTextColor = contentColor.copy(alpha = 0.745f).toArgb(),
            syllableBackgroundColor = syllableGlowColor.toArgb()
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LyricView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(0, 0, 0, 0)
            }
        },
        update = { view ->
            view.setPronunciationWordsByBegin(pronunciationWordsByBegin)
            if (view.tag !== lyriconSong) {
                view.song = lyriconSong
                view.tag = lyriconSong
            }
            view.setStyle(style)
            view.setNonCurrentLineBlurEnabled(nonCurrentLineBlurEnabled)
            view.setNonCurrentLineBlurDistance(nonCurrentLineBlurDistance)
            view.setEdgeFadeEnabled(false)
            view.setLineAlphaAnimationsEnabled(lineAlphaAnimationsEnabled && surfaceActive)
            view.setContinuousFrameUpdatesEnabled(hasTimedWordAnimations && surfaceActive)
            // Treat a hidden-but-resident player surface as not playing so the Choreographer frame
            // loop (karaoke word sweep) stops instead of animating full-speed behind another screen.
            view.setPlaybackActive(isPlaying && surfaceActive)
            view.setPronunciationAboveMainEnabled(!pronunciationBelow)
            view.setAutoScrollResumeEnabled(autoScrollResumeEnabled)
            view.setUserScrollEnabled(userScrollEnabled)
            view.setLineGapDp(lineGapDp ?: -1f)
            view.setForcedTextAlignment(forcedTextAlignment)
            view.updateAnchorOffset(view.height * anchorOffsetRatio)
            view.setTopContentPadding(with(density) { topContentPadding.toPx() })
            view.updateDisplayTranslation(showTranslation, showPronunciation)
            view.onLineClickListener = object : LyricView.OnLineClickListener {
                override fun onLineClick(beginMs: Long) {
                    val line = lyrics.minByOrNull { abs(it.timeMs - beginMs) }
                    if (line != null) onLineClick(line)
                }
            }
            view.onLineDoubleClickListener = LyricView.OnLineDoubleClickListener {
                onLineDoubleClick()
            }
            view.onLineLongClickListener = LyricView.OnLineLongClickListener { beginMs ->
                val line = lyrics.minByOrNull { abs(it.timeMs - beginMs) }
                if (line != null) onLineLongClick(line)
            }
            view.setPosition(currentPositionMs)
        }
    )
}

private fun List<LyricLine>.hasProtectedLyricAlignment(): Boolean =
    any { line ->
        line.isTtml ||
            line.agent.isDuetAgent() ||
            !line.backgroundText.isNullOrBlank() ||
            line.backgroundWords.isNotEmpty()
    }

private fun String?.isDuetAgent(): Boolean =
    equals("v1", ignoreCase = true) || equals("v2", ignoreCase = true)
