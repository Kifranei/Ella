package com.ella.music.ui.album

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.ui.components.AppleStylePlayButton
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.util.Locale

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val albums by mainViewModel.albums.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val album = albums.find { it.id == albumId }
    val albumSongs = mainViewModel.getSongsForAlbum(albumId)
    val sortedAlbumSongs = remember(albumSongs) { albumSongs.sortedForAlbumDetail() }
    val albumArtUri = mainViewModel.getAlbumArtUri(album?.artAlbumId ?: albumSongs.firstOrNull()?.albumId ?: 0L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                AlbumHeader(
                    album = album,
                    albumArtUri = albumArtUri,
                    songCount = sortedAlbumSongs.size,
                    onPlayAll = {
                        if (sortedAlbumSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedAlbumSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            item {
                Text(
                    text = "${sortedAlbumSongs.size} 首歌曲 · 曲目顺序",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            itemsIndexed(sortedAlbumSongs) { index, song ->
                SongItem(
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    albumArtUri = albumArtUri,
                    loadCoverArt = mainViewModel::getCoverArtBitmap,
                    loadAudioInfo = mainViewModel::getAudioInfo,
                    onClick = {
                        playerViewModel.setPlaylist(sortedAlbumSongs, index)
                        if (openPlayerOnPlay) onNavigateToPlayer()
                    },
                    onAddToQueue = { playerViewModel.addToPlaylist(song) }
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

    }
}

@Composable
private fun AlbumHeader(
    album: Album?,
    albumArtUri: Uri?,
    songCount: Int,
    onPlayAll: () -> Unit
) {
    val pageBackground = ellaPageBackground()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(448.dp)
    ) {
        if (albumArtUri != null) {
            SafeCoverImage(
                model = albumArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 3000
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.05f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.74f to pageBackground.copy(alpha = 0.78f),
                            1.00f to pageBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = album?.name ?: "未知专辑",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = listOfNotNull(
                    album?.artist?.takeIf { it.isNotBlank() },
                    "$songCount 首歌曲"
                ).joinToString(" · "),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.78f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppleStylePlayButton(
                text = "播放全部",
                onClick = onPlayAll,
                modifier = Modifier
                    .padding(top = 12.dp)
            )
        }
    }
}

private fun List<Song>.sortedForAlbumDetail(): List<Song> {
    return sortedWith(
        compareBy<Song> { it.trackNumber <= 0 }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    )
}
