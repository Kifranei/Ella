package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.matchesGenreName
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun PlayerDetailPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    tagInfo: SongTagInfo?,
    neteaseInfo: NeteaseKeyInfo?,
    librarySongs: List<Song>,
    albumArtForAlbum: (Long) -> Any?,
    palette: PlayerPalette,
    currentPositionMs: Long,
    isPlaying: Boolean,
    beautifulLyricsBackground: Boolean,
    useBlurBackground: Boolean,
    playerBackgroundEnabled: Boolean,
    customBackgroundUri: String,
    customBackgroundOpacity: Float = 1f,
    customBackgroundDim: Float = 0.26f,
    drawBackground: Boolean = true,
    dynamicFlowEnabled: Boolean = false,
    onAlbum: () -> Unit,
    onArtist: (String) -> Unit,
    onComposer: (String) -> Unit,
    onLyricist: (String) -> Unit,
    onYear: (String) -> Unit,
    onGenre: (String) -> Unit,
    onNeteaseSong: () -> Unit,
    onNeteaseArtist: (String) -> Unit,
    onNeteaseAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composerNames = remember(tagInfo?.composer, song?.composer) {
        splitArtistNames(tagInfo?.composer?.ifBlank { song?.composer.orEmpty() }.orEmpty())
    }
    val lyricistNames = remember(tagInfo?.lyricist, song?.lyricist) {
        splitArtistNames(tagInfo?.lyricist?.ifBlank { song?.lyricist.orEmpty() }.orEmpty())
    }
    val artistNames = remember(tagInfo?.artist, song?.artist) {
        splitArtistNames(tagInfo?.artist?.ifBlank { song?.artist.orEmpty() }.orEmpty())
    }
    var showNeteaseArtistPicker by remember(neteaseInfo) { mutableStateOf(false) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }
    val aliasText = remember(neteaseInfo?.aliases) {
        neteaseInfo
            ?.aliases
            .orEmpty()
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" · ")
    }
    val effectiveLibrarySongs = remember(librarySongs, song) {
        librarySongs.ifEmpty { song?.let(::listOf).orEmpty() }
    }
    val artistDetails = remember(artistNames, effectiveLibrarySongs) {
        artistNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.artist).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val composerDetails = remember(composerNames, effectiveLibrarySongs) {
        composerNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.composer).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val lyricistDetails = remember(lyricistNames, effectiveLibrarySongs) {
        lyricistNames.map { name ->
            PlayerDetailEntity(
                name = name,
                songs = effectiveLibrarySongs.filter { candidate ->
                    splitArtistNames(candidate.lyricist).any { it.equals(name, ignoreCase = true) }
                }
            )
        }
    }
    val albumSongs = remember(song?.albumIdentityId(), effectiveLibrarySongs) {
        val albumId = song?.albumIdentityId() ?: 0L
        effectiveLibrarySongs.filter { it.albumIdentityId() == albumId }
    }
    val year = remember(song?.year) {
        Regex("""\d{4}""").find(song?.year.orEmpty())?.value.orEmpty()
    }
    val genre = song?.genre.orEmpty().trim()
    val genreCategoryName = remember(genre) { splitGenreNames(genre).firstOrNull().orEmpty() }
    val yearSongs = remember(year, effectiveLibrarySongs) {
        effectiveLibrarySongs.filter { it.year.trim().equals(year, ignoreCase = true) }
    }
    val genreSongs = remember(genre, effectiveLibrarySongs) {
        effectiveLibrarySongs.filter { it.genre.matchesGenreName(genreCategoryName) }
    }

    if (showNeteaseArtistPicker) {
        PlayerDetailNeteaseArtistPickerSheet(
            artists = neteaseArtists,
            onDismiss = { showNeteaseArtistPicker = false },
            onArtistSelected = { artistId ->
                showNeteaseArtistPicker = false
                onNeteaseArtist(artistId)
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (drawBackground) {
            SharedPlayerPageBackground(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                playerBackgroundEnabled = playerBackgroundEnabled,
                playerBackgroundUri = customBackgroundUri,
                playerBackgroundOpacity = customBackgroundOpacity,
                playerBackgroundDim = customBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                dynamicFlowEnabled = dynamicFlowEnabled,
                useBlurBackground = useBlurBackground,
                modifier = Modifier.fillMaxSize()
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.player_song_details),
                    color = LocalPlayerContentColor.current,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.player_detail_song),
                    color = LocalPlayerContentColor.current.copy(alpha = 0.68f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song?.title.orEmpty().ifBlank { stringResource(R.string.player_unknown_song) },
                    color = LocalPlayerContentColor.current.copy(alpha = 0.96f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                aliasText.takeIf { it.isNotBlank() }?.let { alias ->
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerDetailInfoLine(stringResource(R.string.player_detail_alias), alias)
                }
                tagInfo?.displayComment?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerDetailInfoLine(stringResource(R.string.player_detail_comment), it)
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (artistDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_artist_label)) {
                        artistDetails.forEach { detail ->
                            val representativeSong = detail.songs.firstOrNull()
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats().personSummary(),
                                coverModel = representativeSong?.coverUrl?.takeIf { it.isNotBlank() }
                                    ?: representativeSong?.albumId?.let(albumArtForAlbum),
                                circularCover = true,
                                onClick = { onArtist(detail.name) }
                            )
                        }
                    }
                }
            }

            if (song?.album.orEmpty().isNotBlank()) {
                item {
                    val albumArtist = song?.albumArtist.orEmpty()
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_album)) {
                        PlayerDetailGroupedActionRow(
                            title = song?.album.orEmpty(),
                            summary = albumSongs.stats().albumSummary(albumArtist),
                            coverModel = song?.coverUrl?.takeIf { it.isNotBlank() }
                                ?: song?.albumId?.let(albumArtForAlbum),
                            onClick = onAlbum
                        )
                    }
                }
            }

            if (year.isNotBlank() || genre.isNotBlank()) {
                item {
                    PlayerDetailDualInfoCard(
                        year = year,
                        yearSongCount = yearSongs.size,
                        yearDuration = yearSongs.sumOf { it.duration },
                        genre = genre,
                        genreSongCount = genreSongs.size,
                        genreDuration = genreSongs.sumOf { it.duration },
                        onYearClick = { onYear(year) },
                        onGenreClick = { onGenre(genreCategoryName) }
                    )
                }
            }

            if (composerDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_composer)) {
                        composerDetails.forEach { detail ->
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats().personSummary(),
                                onClick = { onComposer(detail.name) }
                            )
                        }
                    }
                }
            }

            if (lyricistDetails.isNotEmpty()) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_detail_lyricist)) {
                        lyricistDetails.forEach { detail ->
                            PlayerDetailGroupedActionRow(
                                title = detail.name,
                                summary = detail.songs.stats().personSummary(),
                                onClick = { onLyricist(detail.name) }
                            )
                        }
                    }
                }
            }

            if (neteaseInfo?.hasDecodedContent == true) {
                item {
                    PlayerDetailGroupCard(title = stringResource(R.string.player_netease_section)) {
                        if (neteaseInfo.musicId.isNotBlank()) {
                            PlayerDetailGroupedActionRow(
                                title = stringResource(R.string.player_netease_song_page),
                                summary = neteaseInfo.musicName.ifBlank { neteaseInfo.musicId },
                                onClick = onNeteaseSong
                            )
                        }
                        neteaseInfo.artists
                            .joinToString(" / ") { it.name.ifBlank { it.id } }
                            .takeIf { it.isNotBlank() }
                            ?.let { artistSummary ->
                                PlayerDetailGroupedActionRow(
                                    title = stringResource(R.string.player_netease_artist_page),
                                    summary = artistSummary,
                                    enabled = neteaseArtists.isNotEmpty(),
                                    onClick = {
                                        if (neteaseArtists.size == 1) {
                                            onNeteaseArtist(neteaseArtists.first().id)
                                        } else {
                                            showNeteaseArtistPicker = true
                                        }
                                    }
                                )
                            }
                        if (neteaseInfo.albumId.isNotBlank()) {
                            PlayerDetailGroupedActionRow(
                                title = stringResource(R.string.player_netease_album_page),
                                summary = neteaseInfo.albumName.ifBlank { neteaseInfo.albumId },
                                onClick = onNeteaseAlbum
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PlayerDetailEntity(
    val name: String,
    val songs: List<Song>
)

private data class PlayerDetailStats(
    val songCount: Int,
    val totalDuration: Long,
    val albumCount: Int
)

private fun List<Song>.stats(): PlayerDetailStats = PlayerDetailStats(
    songCount = size,
    totalDuration = sumOf { it.duration },
    albumCount = map { it.albumIdentityId() }.distinct().count()
)

@Composable
private fun PlayerDetailStats.personSummary(): String = stringResource(
    R.string.player_detail_person_summary,
    songCount,
    totalDuration.formatPlaybackDuration(),
    albumCount
)

@Composable
private fun PlayerDetailStats.albumSummary(albumArtist: String): String =
    if (albumArtist.isBlank()) {
        stringResource(
            R.string.player_detail_song_count_duration,
            songCount,
            totalDuration.formatPlaybackDuration()
        )
    } else {
        stringResource(
            R.string.player_detail_album_summary,
            songCount,
            totalDuration.formatPlaybackDuration(),
            albumArtist
        )
    }
