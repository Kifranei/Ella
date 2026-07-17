package com.ella.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale

fun main() = application {
    val controller = remember { DesktopController() }
    Window(
        onCloseRequest = {
            controller.close()
            exitApplication()
        },
        title = "Halcyon",
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when {
                    event.key == Key.MediaPlayPause ||
                        event.isCtrlPressed && event.key == Key.Spacebar -> {
                        controller.togglePlayback()
                        true
                    }

                    event.key == Key.MediaNext -> {
                        controller.next()
                        true
                    }

                    event.key == Key.MediaPrevious -> {
                        controller.previous()
                        true
                    }

                    event.isCtrlPressed && event.key == Key.O -> {
                        controller.chooseAndAddLibraryRoot()
                        true
                    }

                    event.isCtrlPressed && event.key == Key.R -> {
                        controller.rescan()
                        true
                    }

                    else -> false
                }
            }
        }
    ) {
        MaterialTheme {
            HalcyonDesktopApp(controller)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HalcyonDesktopApp(controller: DesktopController) {
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    val currentSong = controller.playback.song
    val currentLyric = DesktopLyrics.lineAt(controller.lyrics, controller.playback.positionMs)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Halcyon", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    OutlinedButton(
                        onClick = controller::chooseAndAddLibraryRoot,
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("Add music folder (Ctrl+O)") }
                    Button(onClick = controller::rescan, enabled = !controller.isScanning) { Text("Scan (Ctrl+R)") }
                }
            )
            if (controller.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            controller.scanMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    DesktopSection.entries.forEach { section ->
                        NavigationRailItem(
                            selected = controller.section == section,
                            onClick = {
                                controller.section = section
                                if (section != DesktopSection.PLAYLISTS) controller.selectedPlaylistId = null
                            },
                            icon = { Text(section.label.first().toString()) },
                            label = { Text(section.label) }
                        )
                    }
                }
                VerticalDivider(modifier = Modifier.fillMaxHeight())
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    when (controller.section) {
                        DesktopSection.LIBRARY -> LibraryScreen(controller)
                        DesktopSection.ALBUMS -> GroupScreen(
                            title = "Albums",
                            groups = controller.library.songs.groupBy { it.displayAlbum }
                        ) { songs -> controller.play(songs.first(), songs) }
                        DesktopSection.ARTISTS -> GroupScreen(
                            title = "Artists",
                            groups = controller.library.songs.groupBy { it.displayArtist }
                        ) { songs -> controller.play(songs.first(), songs) }
                        DesktopSection.GENRES -> GroupScreen(
                            title = "Genres",
                            groups = controller.library.songs.groupBy { it.genre.ifBlank { "Unknown genre" } }
                        ) { songs -> controller.play(songs.first(), songs) }
                        DesktopSection.PLAYLISTS -> PlaylistsScreen(
                            controller = controller,
                            onCreate = { showPlaylistDialog = true }
                        )
                        DesktopSection.SETTINGS -> SettingsScreen(controller)
                    }
                }
                VerticalDivider(modifier = Modifier.fillMaxHeight())
                LyricsPanel(
                    currentSong = currentSong,
                    currentLyric = currentLyric,
                    lines = controller.lyrics
                )
            }
            PlayerBar(
                state = controller.playback,
                shuffleEnabled = controller.library.shuffleEnabled,
                repeatMode = controller.library.repeatMode,
                volume = controller.library.playbackVolume,
                onPrevious = controller::previous,
                onToggle = controller::togglePlayback,
                onNext = controller::next,
                onSeek = controller::seekTo,
                onReveal = controller::revealCurrentSong,
                onShuffleChange = controller::setShuffleEnabled,
                onCycleRepeat = controller::cycleRepeatMode,
                onVolumeChange = controller::setVolume,
                onVolumeChangeFinished = controller::persistPlaybackOptions
            )
        }
    }

    if (controller.library.floatingLyricsEnabled) {
        FloatingLyricsWindow(
            song = currentSong,
            lyric = currentLyric,
            onClose = { controller.setFloatingLyrics(false) }
        )
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.createPlaylist(playlistName)
                    playlistName = ""
                    showPlaylistDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LibraryScreen(controller: DesktopController) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = controller.searchQuery,
            onValueChange = { controller.searchQuery = it },
            label = { Text("Search title, artist, album, genre, or path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        SongList(
            songs = controller.visibleSongs,
            activeSongId = controller.playback.song?.id,
            onPlay = { controller.play(it, controller.visibleSongs) },
            onAddToPlaylist = { song, playlist -> controller.addSongToPlaylist(song, playlist.id) },
            playlists = controller.library.playlists,
            emptyMessage = "No tracks yet. Add a music folder and scan it."
        )
    }
}

@Composable
private fun GroupScreen(
    title: String,
    groups: Map<String, List<DesktopSong>>,
    onPlay: (List<DesktopSong>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (groups.isEmpty()) {
            EmptyState("No $title available yet.")
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups.entries.sortedBy { it.key.lowercase(Locale.ROOT) }, key = { it.key }) { (name, songs) ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onPlay(songs) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${songs.size} tracks", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onPlay(songs) }) { Text("Play") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(controller: DesktopController, onCreate: () -> Unit) {
    val selectedId = controller.selectedPlaylistId
    val selectedPlaylist = controller.library.playlists.firstOrNull { it.id == selectedId }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = onCreate) { Text("New playlist") }
        }
        Spacer(Modifier.height(12.dp))
        if (selectedPlaylist == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(controller.library.playlists, key = { it.id }) { playlist ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { controller.selectedPlaylistId = playlist.id }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold)
                            Text("${playlist.songIds.size} tracks")
                        }
                    }
                }
            }
            if (controller.library.playlists.isEmpty()) EmptyState("Create a playlist, then add songs from Library.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { controller.selectedPlaylistId = null }) { Text("‹ All playlists") }
                Text(selectedPlaylist.name, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            SongList(
                songs = controller.visibleSongs,
                activeSongId = controller.playback.song?.id,
                onPlay = { controller.play(it, controller.visibleSongs) },
                onRemove = { controller.removeSongFromPlaylist(it, selectedPlaylist.id) },
                emptyMessage = "This playlist has no tracks."
            )
        }
    }
}

@Composable
private fun SettingsScreen(controller: DesktopController) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Music folders", style = MaterialTheme.typography.titleMedium)
                controller.library.libraryRoots.forEach { root ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(root, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { controller.removeLibraryRoot(root) }) { Text("Remove") }
                    }
                }
                OutlinedButton(onClick = controller::chooseAndAddLibraryRoot) { Text("Add folder") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating lyrics", style = MaterialTheme.typography.titleMedium)
                    Text("Show the active local lyric line in an always-on-top desktop window.")
                }
                Switch(
                    checked = controller.library.floatingLyricsEnabled,
                    onCheckedChange = controller::setFloatingLyrics
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Desktop media engine", style = MaterialTheme.typography.titleMedium)
                Text("Playback uses bundled FFmpeg native binaries. It supports local MP3, FLAC, M4A/AAC, WAV, AIFF, Ogg/Opus, WMA, and APE files without an external player.")
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<DesktopSong>,
    activeSongId: String?,
    onPlay: (DesktopSong) -> Unit,
    emptyMessage: String,
    playlists: List<DesktopPlaylist> = emptyList(),
    onAddToPlaylist: ((DesktopSong, DesktopPlaylist) -> Unit)? = null,
    onRemove: ((DesktopSong) -> Unit)? = null
) {
    if (songs.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(songs, key = { it.id }) { song ->
            val active = song.id == activeSongId
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(song) },
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverThumbnail(song)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.displayArtist} • ${song.displayAlbum}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(song.durationText, style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { onPlay(song) }) { Text("Play") }
                    if (playlists.isNotEmpty() && onAddToPlaylist != null) {
                        PlaylistAddButton(song, playlists, onAddToPlaylist)
                    }
                    if (onRemove != null) TextButton(onClick = { onRemove(song) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun PlaylistAddButton(
    song: DesktopSong,
    playlists: List<DesktopPlaylist>,
    onAdd: (DesktopSong, DesktopPlaylist) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = !expanded }) { Text("Add") }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            playlists.forEach { playlist ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(playlist.name) },
                    onClick = {
                        onAdd(song, playlist)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CoverThumbnail(song: DesktopSong) {
    val cover = song.coverPath?.let(::File)?.takeIf(File::isFile)
    if (cover != null) {
        AsyncImage(
            model = cover,
            contentDescription = "Album artwork for ${song.title}",
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(song.title.firstOrNull()?.uppercase() ?: "♪", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LyricsPanel(currentSong: DesktopSong?, currentLyric: DesktopLyricLine?, lines: List<DesktopLyricLine>) {
    Column(
        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).fillMaxHeight().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge)
        Text(currentSong?.title ?: "Nothing playing", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (currentLyric != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(currentLyric.text, style = MaterialTheme.typography.titleMedium)
                    currentLyric.translation?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (lines.isEmpty()) {
            Text("Add a sidecar .lrc, .elrc, or .ttml file with the same name as a track.", style = MaterialTheme.typography.bodySmall)
        } else {
            val nearby = lines.filter { line -> kotlin.math.abs(line.timeMs - (currentLyric?.timeMs ?: 0L)) <= 20_000L }.take(8)
            nearby.forEach { line ->
                Text(
                    line.text,
                    color = if (line == currentLyric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (line == currentLyric) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PlayerBar(
    state: DesktopPlaybackState,
    shuffleEnabled: Boolean,
    repeatMode: DesktopRepeatMode,
    volume: Float,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onReveal: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit
) {
    val song = state.song
    var isSeeking by remember(song?.id) { mutableStateOf(false) }
    var pendingPosition by remember(song?.id) { mutableStateOf(0f) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(song?.title ?: "No track selected", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.displayArtist ?: "Add a library folder to begin", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onReveal, enabled = song != null) { Text("Show file") }
                TextButton(onClick = onPrevious, enabled = song != null) { Text("Previous") }
                ElevatedButton(onClick = onToggle) { Text(if (state.isPlaying) "Pause" else "Play") }
                TextButton(onClick = onNext, enabled = song != null) { Text("Next") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onShuffleChange(!shuffleEnabled) }) {
                    Text(if (shuffleEnabled) "Shuffle: on" else "Shuffle: off")
                }
                TextButton(onClick = onCycleRepeat) {
                    Text("Repeat: ${repeatMode.label}")
                }
                Spacer(Modifier.width(12.dp))
                Text("Volume", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = onVolumeChangeFinished,
                    valueRange = 0f..1f,
                    modifier = Modifier.width(150.dp)
                )
                Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            val duration = song?.durationMs?.takeIf { it > 0L } ?: 1L
            Slider(
                value = if (isSeeking) {
                    pendingPosition.coerceIn(0f, duration.toFloat())
                } else {
                    state.positionMs.coerceIn(0L, duration).toFloat()
                },
                onValueChange = { position ->
                    isSeeking = true
                    pendingPosition = position
                },
                onValueChangeFinished = {
                    onSeek(pendingPosition.toLong())
                    isSeeking = false
                },
                valueRange = 0f..duration.toFloat(),
                enabled = song != null && song.durationMs > 0L,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.positionMs.formatDuration(), style = MaterialTheme.typography.labelSmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text((song?.durationMs ?: 0L).formatDuration(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FloatingLyricsWindow(song: DesktopSong?, lyric: DesktopLyricLine?, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = "Halcyon Lyrics",
        alwaysOnTop = true,
        transparent = true,
        undecorated = true,
        resizable = true
    ) {
        MaterialTheme {
            Box(
                modifier = Modifier.fillMaxSize().padding(18.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xD9202024)).padding(horizontal = 24.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(lyric?.text ?: song?.title ?: "Halcyon", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    lyric?.translation?.takeIf(String::isNotBlank)?.let { Text(it, color = Color(0xFFD7D1DC), fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
