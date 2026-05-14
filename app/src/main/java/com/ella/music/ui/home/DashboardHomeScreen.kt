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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToFolder: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToLxOnline: () -> Unit,
    onNavigateToMusicFreeOnline: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val history by mainViewModel.playbackHistory.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val cardText = if (isDark) Color.White else Color(0xFF15151A)
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF5F6FA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SmallTopAppBar(
            title = "听音乐",
            color = pageBackground,
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = "设置",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                cornerRadius = 18.dp,
                onClick = {
                    val randomSong = songs.randomOrNull()
                    if (randomSong != null) {
                        playerViewModel.setPlaylist(songs, songs.indexOf(randomSong))
                        onNavigateToPlayer()
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF4DD6B6), Color(0xFFFFD166), Color(0xFFFF7A90))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "每日精选",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101014)
                        )
                        Text(
                            text = currentSong?.let { "正在播放：${it.title}" } ?: "从你的音乐库随机开始",
                            fontSize = 14.sp,
                            color = Color(0xFF33333A),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = {
                            val randomSong = songs.randomOrNull()
                            if (randomSong != null) {
                                playerViewModel.setPlaylist(songs, songs.indexOf(randomSong))
                                onNavigateToPlayer()
                            }
                        }) {
                            Text(text = "播放", color = Color(0xFF101014))
                        }
                    }
                }
            }

            SectionTitle("音乐库")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("歌曲", "${songs.size} 首", Color(0xFF2EC4B6), onNavigateToLibrary, Modifier.weight(1f))
                HomeTile("专辑", "${albums.size} 张", Color(0xFFFF9F1C), onNavigateToAlbum, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("文件夹", "按目录浏览", Color(0xFF5E60CE), onNavigateToFolder, Modifier.weight(1f))
                HomeTile("听歌统计", "历史和热力图", Color(0xFFE71D36), onNavigateToAnalytics, Modifier.weight(1f))
            }

            SectionTitle("在线音乐")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("LX 在线音乐", "搜索和下载", Color(0xFF118AB2), onNavigateToLxOnline, Modifier.weight(1f))
                HomeTile("MusicFree 在线音乐", "插件来源", Color(0xFFEF476F), onNavigateToMusicFreeOnline, Modifier.weight(1f))
            }

            SectionTitle("最近听过")
            val recent = history.take(3)
            if (recent.isEmpty()) {
                Text(
                    text = "还没有听歌历史",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                recent.forEach { entry ->
                    Text(
                        text = "${entry.title} - ${entry.artist}",
                        color = cardText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
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
