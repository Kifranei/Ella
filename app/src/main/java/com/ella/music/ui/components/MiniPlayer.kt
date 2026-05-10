package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    lyricText: String? = null,
    lyricTranslation: String? = null,
    albumArtUri: Uri? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song.id, loadCoverArt) {
        value = if (song.coverUrl.isNotBlank()) null else withContext(Dispatchers.IO) { loadCoverArt?.invoke(song) }
    }
    val coverModel = embeddedCover ?: song.coverUrl.takeIf { it.isNotBlank() } ?: if (loadCoverArt == null) albumArtUri else null
    val shape = RoundedCornerShape(if (liquidGlass) 24.dp else 0.dp)
    val glassBackdrop = if (liquidGlass) backdrop else null
    val useGlassLayout = liquidGlass
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val glassSurface = if (isLight) Color(0xFFF8F8FA).copy(alpha = 0.48f) else Color(0xFF111114).copy(alpha = 0.58f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (useGlassLayout) 16.dp else 0.dp, vertical = if (useGlassLayout) 6.dp else 0.dp)
            .pointerInput(song.id) {
                var dragAmount = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragAmount += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (abs(dragAmount) > 96f) {
                            if (dragAmount < 0f) onSkipNext() else onSkipPrevious()
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                )
            }
            .clickable(onClick = onClick)
            .then(
                if (glassBackdrop != null) {
                    Modifier
                        .clip(shape)
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { shape },
                            effects = {
                                blur(30f.dp.toPx())
                            },
                            highlight = { Highlight.Default.copy(alpha = if (isLight) 0.26f else 0.16f) },
                            shadow = { Shadow.Default.copy(color = Color.Black.copy(alpha = if (isLight) 0.12f else 0.30f)) },
                            onDrawSurface = {
                                drawRect(glassSurface)
                            }
                        )
                } else if (useGlassLayout) {
                    Modifier
                        .clip(shape)
                        .background(glassSurface, shape)
                } else {
                    Modifier.background(surfaceContainer)
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 128
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        val primaryText = lyricText ?: song.title
        val secondaryText = if (lyricText != null) {
            lyricTranslation ?: song.artist
        } else {
            song.artist
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (lyricText != null) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondaryText,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = "上一首",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = "下一首",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
