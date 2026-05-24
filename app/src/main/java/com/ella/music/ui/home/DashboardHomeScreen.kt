package com.ella.music.ui.home

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.navigation.Screen
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
import java.util.Locale

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToFolder: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLxOnline: () -> Unit,
    onNavigateToWebDav: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToMetadataCategory: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val history by mainViewModel.playbackHistory.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = false)
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = false)
    val homeDailyMixVisible by settingsManager.homeDailyMixVisible.collectAsState(initial = true)
    val homeSectionOrder by settingsManager.homeSectionOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_SECTION_ORDER)
    val homeHiddenSections by settingsManager.homeHiddenSections.collectAsState(initial = "")
    val homeLibraryTileOrder by settingsManager.homeLibraryTileOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
    val homeHiddenLibraryTiles by settingsManager.homeHiddenLibraryTiles.collectAsState(initial = "")
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = ellaPageBackground()
    val cardText = if (isDark) Color.White else Color(0xFF15151A)
    val featuredSongs = remember(songs) {
        when {
            songs.size <= 3 -> songs
            else -> listOf(songs.first(), songs[songs.size / 2], songs.last())
        }
    }
    val artistCount = remember(songs, showAlbumArtists, tagIgnoreCase) {
        songs
            .flatMap {
                if (showAlbumArtists) splitArtistNames(it.artist) + splitArtistNames(it.albumArtist)
                else splitArtistNames(it.artist)
            }
            .distinctBy { it.tagIdentityKey() }
            .size
    }
    val folderCount = remember(songs) { mainViewModel.getMetadataCategoryItems("folder").size }
    val genreCount = remember(songs) { mainViewModel.getMetadataCategoryItems("genre").size }
    val yearCount = remember(songs) { mainViewModel.getMetadataCategoryItems("year").size }
    val composerCount = remember(songs) { mainViewModel.getMetadataCategoryItems("composer").size }
    val lyricistCount = remember(songs) { mainViewModel.getMetadataCategoryItems("lyricist").size }
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
            if (homeDailyMixVisible) {
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
            }

            val hiddenSections = remember(homeHiddenSections) { homeHiddenSections.csvIdSet() }
            val sectionOrder = remember(homeSectionOrder) {
                homeSectionOrder.csvIds(SettingsManager.DEFAULT_HOME_SECTION_ORDER)
            }
            val hiddenTiles = remember(homeHiddenLibraryTiles) { homeHiddenLibraryTiles.csvIdSet() }
            val tileOrder = remember(homeLibraryTileOrder) {
                homeLibraryTileOrder.csvIds(SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
            }
            val libraryTiles = remember(
                tileOrder,
                hiddenTiles,
                artistCount,
                albums.size,
                folderCount,
                playlists.size,
                genreCount,
                yearCount,
                composerCount,
                lyricistCount
            ) {
                val all = mapOf(
                    "artist" to HomeTileSpec("artist", "艺术家", "$artistCount 位", Color(0xFF118AB2), Screen.Artist.route, onNavigateToArtist),
                    "album" to HomeTileSpec("album", "专辑", "${albums.size} 张", Color(0xFFFF9F1C), Screen.Album.route, onNavigateToAlbum),
                    "folder" to HomeTileSpec("folder", "文件夹", "$folderCount 个", Color(0xFF5E60CE), Screen.MetadataCategory.createRoute("folder")) { onNavigateToMetadataCategory("folder") },
                    "folder_tree" to HomeTileSpec("folder_tree", "文件夹层次结构", "按嵌套目录浏览", Color(0xFF8338EC), Screen.Folder.route, onNavigateToFolder),
                    "playlist" to HomeTileSpec("playlist", "歌单", "${playlists.size} 个", Color(0xFFEF476F), Screen.Playlists.route, onNavigateToPlaylists),
                    "analytics" to HomeTileSpec("analytics", "听歌统计", "历史和热力图", Color(0xFFE71D36), Screen.Analytics.route, onNavigateToAnalytics),
                    "genre" to HomeTileSpec("genre", "流派", "$genreCount 种", Color(0xFF06D6A0), Screen.MetadataCategory.createRoute("genre")) { onNavigateToMetadataCategory("genre") },
                    "year" to HomeTileSpec("year", "年份", "$yearCount 个", Color(0xFF4CC9F0), Screen.MetadataCategory.createRoute("year")) { onNavigateToMetadataCategory("year") },
                    "composer" to HomeTileSpec("composer", "作曲家", "$composerCount 位", Color(0xFFB5179E), Screen.MetadataCategory.createRoute("composer")) { onNavigateToMetadataCategory("composer") },
                    "lyricist" to HomeTileSpec("lyricist", "作词家", "$lyricistCount 位", Color(0xFFFF6D00), Screen.MetadataCategory.createRoute("lyricist")) { onNavigateToMetadataCategory("lyricist") }
                )
                tileOrder.mapNotNull { all[it] }.filterNot { it.id in hiddenTiles }
            }

            sectionOrder.filterNot { it in hiddenSections }.forEach { section ->
                when (section) {
                    "library" -> HomeTileSection("音乐库", libraryTiles, context)
                    "online" -> {
                        SectionTitle("在线音乐")
                        HomeTileGrid(
                            tiles = listOf(
                                HomeTileSpec("lx", "LX Music", "导入 API 源", Color(0xFF00A896), Screen.LxOnline.route, onNavigateToLxOnline),
                                HomeTileSpec("webdav", "WebDAV", "连接云端音乐", Color(0xFF5E60CE), Screen.WebDav.route, onNavigateToWebDav)
                            ),
                            context = context
                        )
                    }
                    "recent" -> {
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

private data class HomeTileSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val route: String,
    val onClick: () -> Unit
)

@Composable
private fun HomeTileSection(title: String, tiles: List<HomeTileSpec>, context: android.content.Context) {
    if (tiles.isEmpty()) return
    SectionTitle(title)
    HomeTileGrid(tiles = tiles, context = context)
}

@Composable
private fun HomeTileGrid(tiles: List<HomeTileSpec>, context: android.content.Context) {
    tiles.chunked(2).forEachIndexed { index, rowTiles ->
        if (index > 0) Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            rowTiles.forEach { tile ->
                HomeTile(
                    title = tile.title,
                    subtitle = tile.subtitle,
                    color = tile.color,
                    onClick = tile.onClick,
                    onPinClick = {
                        val ok = requestPinnedEllaShortcut(context, "home_${tile.id}", tile.title, tile.route)
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "已请求添加桌面快捷方式" else "当前桌面不支持固定快捷方式",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
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
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(top = 6.dp)
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
    onPinClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) 0.34f else 0.22f))
            .combinedClickable(onClick = onClick, onLongClick = onPinClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (onPinClick != null) {
                Text(
                    text = "+",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onPinClick)
                        .padding(horizontal = 6.dp)
                )
            }
        }
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1
        )
    }
}

private fun String.csvIdSet(): Set<String> =
    split(',', '，', ';', '；')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.csvIds(defaultValue: String): List<String> {
    val ids = csvIdSet().toList()
    val defaults = defaultValue.csvIdSet().toList()
    return (ids + defaults).distinct()
}
