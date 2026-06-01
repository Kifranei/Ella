package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumCard(
    album: Album,
    albumArtUri: Uri? = null,
    representativeSong: Song? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    summary: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedSummary = summary ?: "${album.artist} · ${context.getString(R.string.song_count, album.songCount)}"
    val embeddedCover by produceState<Bitmap?>(
        initialValue = null,
        representativeSong?.id,
        representativeSong?.dateModified,
        representativeSong?.fileSize,
        loadCoverArt
    ) {
        val song = representativeSong
        value = if (song == null || loadCoverArt == null || !song.prefersEmbeddedCover()) {
            null
        } else {
            withContext(Dispatchers.IO) { runCatching { loadCoverArt(song) }.getOrNull() }
        }
    }
    val coverModel: Any? = representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
        ?: embeddedCover
        ?: albumArtUri

    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = album.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 384
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.matchParentSize())
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = resolvedSummary,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Song.prefersEmbeddedCover(): Boolean {
    val extension = fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase()
    return extension in setOf("m4a", "mp4", "alac", "flac", "wav", "wave", "aiff", "aif")
}
