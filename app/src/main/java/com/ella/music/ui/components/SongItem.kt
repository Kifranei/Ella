package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false,
    albumArtUri: Uri? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    loadAudioInfo: ((Song) -> AudioInfo)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAddToQueue: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    leadingLabel: String? = null,
    leadingLabelBeforeCover: Boolean = false,
    showAlbumInSubtitle: Boolean = true,
    isFavorite: Boolean = false,
    loadSongRating: ((Song) -> Int)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.common_unknown)
    val unknownArtist = stringResource(R.string.player_unknown_artist)
    val settingsManager = remember { SettingsManager(context) }
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = true)
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, loadAudioInfo) {
        value = withContext(Dispatchers.IO) { loadAudioInfo?.invoke(song) }
    }
    val preferEmbeddedCover = song.fileName.substringAfterLast('.', song.path.substringAfterLast('.'))
        .lowercase() in setOf("wav", "wave", "aif", "aiff")
    val shouldLoadEmbeddedCover = song.coverUrl.isBlank() &&
        loadCoverArt != null &&
        (albumArtUri == null || preferEmbeddedCover)
    val embeddedCover by produceState<Bitmap?>(initialValue = null, song.id, song.dateModified, song.fileSize, shouldLoadEmbeddedCover) {
        value = if (shouldLoadEmbeddedCover) {
            withContext(Dispatchers.IO) { loadCoverArt.invoke(song) }
        } else {
            null
        }
    }
    val qualityTag = audioInfo?.let { audioQualitySummary(it).listTag }
    val rating by produceState(initialValue = 0, song.id, song.dateModified, loadSongRating) {
        value = withContext(Dispatchers.IO) { loadSongRating?.invoke(song) ?: 0 }
    }
    val coverModel = song.coverUrl.takeIf { it.isNotBlank() }
        ?: if (preferEmbeddedCover) embeddedCover ?: albumArtUri else albumArtUri ?: embeddedCover

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (leadingLabelBeforeCover && !leadingLabel.isNullOrBlank()) {
            Text(
                text = leadingLabel,
                fontSize = 14.sp,
                color = if (isCurrent) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop,
                    sizePx = 128
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (!leadingLabelBeforeCover && !leadingLabel.isNullOrBlank()) {
            Text(
                text = leadingLabel,
                fontSize = 14.sp,
                color = if (isCurrent) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(28.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    color = if (isCurrent) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification_favorite_filled),
                        contentDescription = stringResource(R.string.common_favorite),
                        tint = Color(0xFFFF4D6D),
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (rating > 0) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "★$rating",
                        fontSize = 11.sp,
                        color = Color(0xFFFFB703),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (qualityTag != null) {
                    AudioQualityBadge(qualityTag)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (showAlbumInSubtitle) {
                        listOf(song.artist, song.album)
                            .map { it.ifBlank { unknown } }
                            .joinToString(" · ")
                    } else {
                        song.artist.ifBlank { unknownArtist }
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (!selectionMode && showPlayNextInLists && onAddToQueue != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onAddToQueue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Add,
                    contentDescription = stringResource(R.string.common_add_to_queue),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = song.durationText,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        if (!selectionMode && onDownload != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onDownload),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↓",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
        if (!selectionMode && onRemove != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE5484D).copy(alpha = 0.12f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    fontSize = 16.sp,
                    color = Color(0xFFE5484D)
                )
            }
        }
        if (!selectionMode && onMore != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⋮",
                    fontSize = 22.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        if (!selectionMode && trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
private fun AudioQualityBadge(tag: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(audioQualityColor(tag).copy(alpha = 0.18f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (tag == "Dolby") "ᴰᴰ" else tag,
            fontSize = 9.sp,
            color = audioQualityColor(tag)
        )
    }
}

private fun audioQualityColor(tag: String): Color {
    return when (tag) {
        "Dolby" -> Color(0xFF6EE7FF)
        "MQ" -> Color(0xFFFF8F3D)
        "HR" -> Color(0xFFFFC23A)
        "SQ" -> Color(0xFF69B7FF)
        "HQ" -> Color(0xFF3D83FF)
        "LQ" -> Color(0xFF34C56E)
        else -> Color(0xFF9E9E9E)
    }
}
