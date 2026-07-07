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
import com.ella.music.data.splitArtistNames
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun PlayerDetailPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    tagInfo: SongTagInfo?,
    neteaseInfo: NeteaseKeyInfo?,
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

            if (artistNames.isNotEmpty()) {
                artistNames.forEach { name ->
                    item(key = "artist_$name") {
                        PlayerDetailActionRow(
                            title = stringResource(R.string.player_detail_artist_label),
                            summary = name,
                            onClick = { onArtist(name) }
                        )
                    }
                }
            } else {
                val artistText = song?.artist.orEmpty()
                if (artistText.isNotBlank()) {
                    item {
                        PlayerDetailActionRow(
                            title = stringResource(R.string.player_detail_artist_label),
                            summary = artistText,
                            onClick = { onArtist(artistText) }
                        )
                    }
                }
            }

            item {
                PlayerDetailActionRow(
                    title = stringResource(R.string.player_detail_album),
                    summary = song?.album.orEmpty().ifBlank { stringResource(R.string.player_no_album_info) },
                    enabled = (song?.albumIdentityId() ?: 0L) > 0L,
                    onClick = onAlbum
                )
            }

            composerNames.forEach { composer ->
                item(key = "composer_$composer") {
                    PlayerDetailActionRow(
                        title = stringResource(R.string.player_detail_composer),
                        summary = composer,
                        enabled = composer.isNotBlank(),
                        onClick = { onComposer(composer) }
                    )
                }
            }

            lyricistNames.forEach { lyricist ->
                item(key = "lyricist_$lyricist") {
                    PlayerDetailActionRow(
                        title = stringResource(R.string.player_detail_lyricist),
                        summary = lyricist,
                        enabled = lyricist.isNotBlank(),
                        onClick = { onLyricist(lyricist) }
                    )
                }
            }

            if (neteaseInfo?.hasDecodedContent == true) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.player_netease_section),
                        color = LocalPlayerContentColor.current.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (neteaseInfo.musicId.isNotBlank()) {
                    item {
                        PlayerDetailActionRow(
                            title = stringResource(R.string.player_netease_song_page),
                            summary = neteaseInfo.musicName.ifBlank { neteaseInfo.musicId },
                            onClick = onNeteaseSong
                        )
                    }
                }
                neteaseInfo.artists
                    .joinToString(" / ") { it.name.ifBlank { it.id } }
                    .takeIf { it.isNotBlank() }
                    ?.let { artistSummary ->
                        item(key = "netease_artists") {
                            PlayerDetailActionRow(
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
                    }
                if (neteaseInfo.albumId.isNotBlank()) {
                    item {
                        PlayerDetailActionRow(
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
