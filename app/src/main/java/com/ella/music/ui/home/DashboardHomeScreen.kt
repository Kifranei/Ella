package com.ella.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.splitArtistNames
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToFolder: () -> Unit,
    onNavigateToLxOnline: () -> Unit,
    onNavigateToMusicFreeOnline: () -> Unit,
    onNavigateToWebDav: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val history by mainViewModel.playbackHistory.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = ellaPageBackground()
    val cardText = if (isDark) Color.White else Color(0xFF15151A)
    val featuredSongs = remember(songs) { songs.shuffled().take(3) }
    val artistCount = remember(songs) {
        songs
            .flatMap { splitArtistNames(it.artist) }
            .distinctBy { it.lowercase() }
            .size
    }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val recentSongs = remember(history, songsById) {
        history
            .distinctBy { it.songId }
            .take(5)
            .mapNotNull { entry -> songsById[entry.songId] }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "听音乐",
            color = pageBackground
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            DailyMixCard(
                songs = songs,
                featuredSongs = featuredSongs,
                currentSongTitle = currentSong?.title,
                mainViewModel = mainViewModel,
                onPlay = {
                    val randomSong = songs.randomOrNull()
                    if (randomSong != null) {
                        playerViewModel.setPlaylist(songs, songs.indexOf(randomSong))
                        if (openPlayerOnPlay) onNavigateToPlayer()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            SectionTitle("音乐库")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("歌曲", "${songs.size} 首", Color(0xFF2EC4B6), onNavigateToLibrary, Modifier.weight(1f))
                HomeTile("艺术家", "$artistCount 位", Color(0xFF118AB2), onNavigateToArtist, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("专辑", "${albums.size} 张", Color(0xFFFF9F1C), onNavigateToAlbum, Modifier.weight(1f))
                HomeTile("文件夹", "按目录浏览", Color(0xFF5E60CE), onNavigateToFolder, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("听歌统计", "历史和热力图", Color(0xFFE71D36), onNavigateToAnalytics, Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            SectionTitle("在线音乐")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("LX Music", "导入 API 源", Color(0xFF00A896), onNavigateToLxOnline, Modifier.weight(1f))
                HomeTile("MusicFree", "导入插件源", Color(0xFFFF6B6B), onNavigateToMusicFreeOnline, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("WebDAV", "连接云端音乐", Color(0xFF5E60CE), onNavigateToWebDav, Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            SectionTitle("最近听过")
            if (recentSongs.isEmpty()) {
                Text(
                    text = "还没有听歌历史",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                recentSongs.forEach { song ->
                    CompactRecentSongRow(
                        song = song,
                        mainViewModel = mainViewModel,
                        cardText = cardText,
                        onClick = {
                            playerViewModel.playSong(song)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
private fun DailyMixCard(
    songs: List<Song>,
    featuredSongs: List<Song>,
    currentSongTitle: String?,
    mainViewModel: MainViewModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        onClick = onPlay
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4DD6B6), Color(0xFFFFD166), Color(0xFFFF7A90))
                    )
                )
                .padding(20.dp)
        ) {
            featuredSongs.forEachIndexed { index, song ->
                val size = listOf(68, 58, 48).getOrElse(index) { 48 }.dp
                SafeCoverImage(
                    model = mainViewModel.getAlbumArtUri(song.albumId),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-16 - index * 28).dp, y = (14 + index * 14).dp)
                        .size(size)
                        .clip(CircleShape),
                    sizePx = 96
                )
            }

            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "每日精选",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101014)
                )
                Text(
                    text = currentSongTitle?.let { "正在播放：$it" } ?: "${songs.size} 首歌曲随机播放",
                    fontSize = 14.sp,
                    color = Color(0xFF33333A),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Play,
                        contentDescription = "播放每日精选",
                        tint = Color(0xFF101014),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRecentSongRow(
    song: Song,
    mainViewModel: MainViewModel,
    cardText: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SafeCoverImage(
            model = mainViewModel.getAlbumArtUri(song.albumId),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            sizePx = 128
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = song.title,
                color = cardText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = song.artist,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.34f else 0.22f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1
        )
    }
}
