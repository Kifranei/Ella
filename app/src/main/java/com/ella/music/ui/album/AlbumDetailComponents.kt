package com.ella.music.ui.album

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.Album
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.PlayNextQuickButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AlbumCopyrightFooter(
    copyright: String,
    genres: List<String>,
    artists: List<String>,
    composers: List<String>,
    lyricists: List<String>,
    year: String?,
    onGenreClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onComposerClick: (String) -> Unit,
    onLyricistClick: (String) -> Unit,
    onYearClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        copyright.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { values ->
            AlbumMetadataGroupCard(
                title = stringResource(R.string.album_copyright),
                values = values
            )
        }
        if (!year.isNullOrBlank() || genres.isNotEmpty()) {
            AlbumMetadataDualCard(
                year = year.orEmpty(),
                genres = genres,
                onYearClick = onYearClick,
                onGenreClick = onGenreClick
            )
        }
        AlbumMetadataGroupCard(
            title = stringResource(R.string.player_detail_artist),
            values = artists,
            onValueClick = onArtistClick
        )
        AlbumMetadataGroupCard(
            title = stringResource(R.string.player_detail_composer),
            values = composers,
            onValueClick = onComposerClick
        )
        AlbumMetadataGroupCard(
            title = stringResource(R.string.player_detail_lyricist),
            values = lyricists,
            onValueClick = onLyricistClick
        )
    }
}

@Composable
private fun AlbumMetadataGroupCard(
    title: String,
    values: List<String>,
    onValueClick: ((String) -> Unit)? = null
) {
    if (values.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        values.forEach { value ->
            Text(
                text = value,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = onValueClick != null) { onValueClick?.invoke(value) }
                    .padding(horizontal = 18.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun AlbumMetadataDualCard(
    year: String,
    genres: List<String>,
    onYearClick: (String) -> Unit,
    onGenreClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (year.isNotBlank()) {
            AlbumMetadataDualItem(
                title = stringResource(R.string.category_year),
                value = year,
                onClick = { onYearClick(year) },
                modifier = Modifier.weight(1f)
            )
        }
        if (genres.isNotEmpty()) {
            val primaryGenre = genres.first()
            AlbumMetadataDualItem(
                title = stringResource(R.string.category_genre),
                value = genres.joinToString(" · "),
                onClick = { onGenreClick(primaryGenre) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AlbumMetadataDualItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DiscHeader(group: AlbumDiscGroup) {
    Text(
        text = stringResource(
            R.string.album_disc_header,
            group.discNumber,
            group.songs.size,
            group.songs.sumOf { it.duration }.formatPlaybackDuration()
        ),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
internal fun AlbumSongRow(
    song: Song,
    index: Int,
    sortedAlbumSongs: List<Song>,
    currentSongId: Long?,
    isFavorite: Boolean,
    showTrackNumber: Boolean,
    mainViewModel: MainViewModel,
    ratingRevision: Int,
    playerViewModel: PlayerViewModel,
    openPlayerOnPlay: Boolean,
    onNavigateToPlayer: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onSelectionClick: () -> Unit,
    onMore: () -> Unit,
    showPlayNextInLists: Boolean
) {
    AlbumTrackRow(
        song = song,
        isCurrent = currentSongId == song.id,
        loadAudioInfo = mainViewModel::getAudioInfo,
        isFavorite = isFavorite,
        loadSongRating = mainViewModel::getSongRating,
        ratingRevision = ratingRevision,
        leadingLabel = if (showTrackNumber) song.displayTrackNumber() else null,
        selectionMode = selectionMode,
        selected = selected,
        onLongClick = onLongClick,
        onClick = {
            if (selectionMode) {
                onSelectionClick()
                return@AlbumTrackRow
            }
            val safeIndex = index.coerceAtLeast(0)
            playerViewModel.setPlaylist(sortedAlbumSongs, safeIndex)
            if (openPlayerOnPlay) onNavigateToPlayer()
        },
        showPlayNextInLists = showPlayNextInLists,
        onPlayNext = { playerViewModel.playNext(song) },
        onMore = onMore
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumTrackRow(
    song: Song,
    isCurrent: Boolean,
    loadAudioInfo: (Song) -> AudioInfo,
    isFavorite: Boolean,
    loadSongRating: (Song) -> Int,
    ratingRevision: Int,
    leadingLabel: String?,
    selectionMode: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    showPlayNextInLists: Boolean,
    onPlayNext: () -> Unit,
    onMore: () -> Unit
) {
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, loadAudioInfo) {
        value = withContext(Dispatchers.IO) { loadAudioInfo(song) }
    }
    val rating by produceState(initialValue = 0, song.id, song.dateModified, ratingRevision, loadSongRating) {
        value = withContext(Dispatchers.IO) { loadSongRating(song) }
    }
    val qualityTag = audioInfo?.let { audioQualitySummary(it).listTag }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 26.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(
                selected = selected,
                checkColor = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = leadingLabel.orEmpty(),
            fontSize = 16.sp,
            color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.width(46.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "♥", fontSize = 12.sp, color = Color(0xFFFF4D6D))
                }
                if (rating > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "★$rating", fontSize = 11.sp, color = Color(0xFFFFB703))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!qualityTag.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(albumDetailQualityColor(qualityTag).copy(alpha = 0.18f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = qualityTag,
                            fontSize = 9.sp,
                            color = albumDetailQualityColor(qualityTag)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = song.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = song.duration.formatPlaybackDuration(),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(end = 4.dp)
        )
        if (!selectionMode && showPlayNextInLists) {
            Spacer(modifier = Modifier.width(8.dp))
            PlayNextQuickButton(onClick = onPlayNext)
        }
        Text(
            text = "⋮",
            fontSize = 24.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onMore)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun AlbumHeader(
    album: Album?,
    albumCoverModel: Any?,
    songCount: Int,
    duration: Long,
    hasNeteaseAlbum: Boolean,
    onNeteaseAlbumClick: () -> Unit,
    onAlbumArtistClick: () -> Unit,
    onReleaseYearClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    val albumArtist = album?.albumArtist?.takeIf { it.isNotBlank() }
        ?: album?.artist?.takeIf { it.isNotBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 26.dp, end = 26.dp, top = 86.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (albumCoverModel != null) {
                    SafeCoverImage(
                        model = albumCoverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        sizePx = 512
                    )
                } else {
                    DefaultAlbumCover(modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.width(22.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = album?.name ?: stringResource(R.string.player_unknown_album),
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!albumArtist.isNullOrBlank()) {
                    Text(
                        text = albumArtist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onAlbumArtistClick)
                    )
                }
                val albumYearText = album?.year?.takeIf { it.isNotBlank() }
                if (albumYearText != null) {
                    Text(
                        text = albumYearText,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.clickable(onClick = onReleaseYearClick)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleStylePlayButton(
                text = stringResource(R.string.album_play_all),
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            )
            if (hasNeteaseAlbum) {
                Text(
                    text = stringResource(R.string.player_netease_album_page),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable(onClick = onNeteaseAlbumClick)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
    }
}

internal fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
