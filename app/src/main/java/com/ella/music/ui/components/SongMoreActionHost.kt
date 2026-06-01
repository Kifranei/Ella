package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ella.music.data.NeteaseArtist

@Composable
fun SongMoreActionHost(
    actionSong: Song?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onDismissAction: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onSongRemovedFromPlaylist: ((Song) -> Unit)? = null,
    deleteFromLibrary: Boolean = true,
    showDelete: Boolean = true,
    showLocalFileActions: Boolean = true,
    resolveSongForAction: (suspend (Song) -> Song)? = null,
    onDeleteSong: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val defaultDangerText = stringResource(R.string.common_delete)
    val actionSheetTitle = stringResource(R.string.song_more_actions_title)
    val addToPlaylistFailed = stringResource(R.string.song_more_add_to_playlist_failed)
    val playNextFailed = stringResource(R.string.song_more_play_next_failed)
    val shareFailed = stringResource(R.string.song_more_share_failed)
    val addedToPlayNext = stringResource(R.string.song_more_added_to_play_next)
    val noArtistJump = stringResource(R.string.song_more_no_artist_jump)
    val noAlbumJump = stringResource(R.string.song_more_no_album_jump)
    val selectArtistTitle = stringResource(R.string.song_more_select_artist)
    val addToPlaylistTitle = stringResource(R.string.song_more_add_to_playlist_title)
    val editTagTitle = stringResource(R.string.song_more_edit_tags_title)
    val lyricTimingTitle = stringResource(R.string.song_more_lyric_timing)
    val aiInterpretTitle = stringResource(R.string.song_more_ai_title)
    val playlists by mainViewModel.playlists.collectAsState(initial = emptyList())
    val metadataEditorId by mainViewModel.settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by mainViewModel.settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val scope = rememberCoroutineScope()
    var playlistSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var tagEditorKind by remember { mutableStateOf(TagEditorOptionKind.Metadata) }
    var customTagSong by remember { mutableStateOf<Song?>(null) }
    var ratingSong by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var aiSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var dangerConfirmTitle by remember { mutableStateOf("") }
    var dangerConfirmMessage by remember { mutableStateOf("") }
    var dangerConfirmText by remember { mutableStateOf("") }
    var dangerConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun closeAction() = onDismissAction()

    fun requestDangerConfirm(
        title: String,
        message: String,
        confirmText: String,
        action: () -> Unit
    ) {
        dangerConfirmTitle = title
        dangerConfirmMessage = message
        dangerConfirmText = confirmText
        dangerConfirmAction = action
    }

    fun runResolvedSongAction(
        sourceSong: Song,
        failureMessage: String,
        action: (Song) -> Unit
    ) {
        scope.launch {
            runCatching {
                resolveSongForAction?.invoke(sourceSong) ?: sourceSong
            }.onSuccess { resolvedSong ->
                action(resolvedSong)
            }.onFailure { error ->
                Toast.makeText(context, error.localizedMessage ?: failureMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    actionSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = song.title.ifBlank { actionSheetTitle },
            onDismissRequest = ::closeAction
        ) {
            SongMoreActionSheet(
                song = song,
                onDismiss = ::closeAction,
                onAddToPlaylist = {
                    closeAction()
                    runResolvedSongAction(song, addToPlaylistFailed) { resolvedSong ->
                        playlistSong = resolvedSong
                    }
                },
                onPlayNext = {
                    closeAction()
                    runResolvedSongAction(song, playNextFailed) { resolvedSong ->
                        playerViewModel.playNext(resolvedSong)
                        Toast.makeText(context, addedToPlayNext, Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    closeAction()
                    runResolvedSongAction(song, shareFailed) { resolvedSong ->
                        shareLocalSong(context, resolvedSong)
                    }
                },
                onSpectrum = {
                    openSongSpectrumWithAspectPro(context, song)
                    closeAction()
                },
                onInfo = {
                    infoSong = song
                    closeAction()
                },
                onRating = {
                    ratingSong = song
                    closeAction()
                },
                onAiInterpret = {
                    aiSong = song
                    closeAction()
                },
                onArtist = {
                    val artists = splitArtistNames(song.artist)
                        .filterNot { it.equals("Unknown", ignoreCase = true) }
                        .distinctBy { it.tagIdentityKey() }
                    when (artists.size) {
                        0 -> Toast.makeText(context, noArtistJump, Toast.LENGTH_SHORT).show()
                        1 -> onNavigateToArtist(artists.first())
                        else -> artistChoices = artists
                    }
                    closeAction()
                },
                onAlbum = {
                    val albumId = song.albumIdentityId()
                    if (albumId > 0L) {
                        onNavigateToAlbum(albumId)
                    } else {
                        Toast.makeText(context, noAlbumJump, Toast.LENGTH_SHORT).show()
                    }
                    closeAction()
                },
                onEditTag = if (showLocalFileActions) {
                    {
                        tagEditorKind = TagEditorOptionKind.Metadata
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onLyricTiming = if (showLocalFileActions) {
                    {
                        tagEditorKind = TagEditorOptionKind.LyricTiming
                        tagEditorSong = song
                        closeAction()
                    }
                } else null,
                onRemoveFromPlaylist = onSongRemovedFromPlaylist?.let {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = context.getString(R.string.playlist_remove_song_title),
                            message = context.getString(
                                R.string.song_more_remove_from_playlist_message,
                                song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                            ),
                            confirmText = context.getString(R.string.common_remove)
                        ) {
                            it(song)
                        }
                    }
                },
                onDelete = if (showDelete) {
                    {
                        closeAction()
                        requestDangerConfirm(
                            title = if (deleteFromLibrary) {
                                context.getString(R.string.song_more_delete_song_title)
                            } else {
                                context.getString(R.string.song_more_remove_from_library_title)
                            },
                            message = if (deleteFromLibrary) {
                                context.getString(
                                    R.string.song_more_delete_song_message,
                                    song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                                )
                            } else {
                                context.getString(
                                    R.string.song_more_remove_from_library_message,
                                    song.title.ifBlank { song.fileName.ifBlank { context.getString(R.string.common_this_song) } }
                                )
                            },
                            confirmText = if (deleteFromLibrary) {
                                context.getString(R.string.song_more_delete_permanently)
                            } else {
                                context.getString(R.string.common_remove)
                            }
                        ) {
                            if (onDeleteSong != null) {
                                onDeleteSong(song)
                            } else if (deleteFromLibrary) {
                                mainViewModel.deleteSongs(listOf(song))
                            } else {
                                mainViewModel.removeSongsFromLibrary(listOf(song))
                            }
                        }
                    }
                } else null,
                showSpectrum = showLocalFileActions
            )
        }
    }

    ConfirmDangerDialog(
        show = dangerConfirmAction != null,
        title = dangerConfirmTitle,
        message = dangerConfirmMessage,
        confirmText = dangerConfirmText,
        onDismiss = { dangerConfirmAction = null },
        onConfirm = {
            val action = dangerConfirmAction
            dangerConfirmAction = null
            action?.invoke()
        }
    )

    if (artistChoices.isNotEmpty()) {
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = selectArtistTitle,
            onDismissRequest = { artistChoices = emptyList() }
        ) {
            ArtistPickerContent(
                artists = artistChoices,
                onArtistSelected = { artist ->
                    artistChoices = emptyList()
                    onNavigateToArtist(artist)
                },
                onDismiss = { artistChoices = emptyList() }
            )
        }
    }

    playlistSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = addToPlaylistTitle,
            onDismissRequest = { playlistSong = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists
                    .filterNot { it.id == FAVORITES_PLAYLIST_ID }
                    .sortedByDescending { it.createdAt },
                onDismiss = { playlistSong = null },
                onCreatePlaylist = {
                    createPlaylistSong = song
                    playlistSong = null
                },
                onPlaylistsConfirm = { selectedPlaylists ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    playlistSong = null
                }
            )
        }
    }

    createPlaylistSong?.let { song ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSong = null },
            onCreate = { name ->
                mainViewModel.createPlaylist(name) { playlist ->
                    if (playlist != null) {
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_added_to_playlist_named, playlist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                createPlaylistSong = null
            }
        )
    }

    tagEditorSong?.let { song ->
        val builtinOption = remember(song.id, tagEditorKind) {
            TagEditorOption(
                id = TagEditorOptionIds.BUILTIN_CUSTOM_TAG,
                label = context.getString(R.string.settings_editor_builtin_custom_tag),
                summary = context.getString(R.string.tag_editor_builtin_custom_tag_summary),
                kind = TagEditorOptionKind.Metadata,
                intents = emptyList(),
                sourceSong = song
            )
        }
        val tagOptions = remember(song.id, song.path, song.mimeType, tagEditorKind, builtinOption) {
            val external = buildTagEditorOptions(context, song)
                .filter { it.kind == tagEditorKind }
            if (tagEditorKind == TagEditorOptionKind.Metadata) listOf(builtinOption) + external else external
        }
        val preferredEditorId = if (tagEditorKind == TagEditorOptionKind.LyricTiming) {
            lyricTimingEditorId
        } else {
            metadataEditorId
        }
        val preferredOption = remember(tagOptions, preferredEditorId) {
            tagOptions.firstOrNull { it.id == preferredEditorId }
        }
        LaunchedEffect(song.id, preferredEditorId, preferredOption, tagEditorKind) {
            if (preferredEditorId.isNotBlank() && preferredOption != null) {
                if (preferredOption.id == TagEditorOptionIds.BUILTIN_CUSTOM_TAG) {
                    customTagSong = song
                } else {
                    launchTagEditorOption(context, preferredOption)
                }
                tagEditorSong = null
            }
        }
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = if (tagEditorKind == TagEditorOptionKind.LyricTiming) lyricTimingTitle else editTagTitle,
            onDismissRequest = { tagEditorSong = null }
        ) {
            SongTagEditorSheet(
                song = song,
                options = tagOptions,
                onDismiss = { tagEditorSong = null },
                onOptionClick = { option ->
                    if (option.id == TagEditorOptionIds.BUILTIN_CUSTOM_TAG) {
                        customTagSong = song
                    } else {
                        launchTagEditorOption(context, option)
                    }
                    tagEditorSong = null
                }
            )
        }
    }

    customTagSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_builtin_custom_tag_title),
            onDismissRequest = { customTagSong = null }
        ) {
            BuiltInCustomTagSheet(
                onDismiss = { customTagSong = null },
                onSave = { key, value ->
                    scope.launch {
                        val result = mainViewModel.writeSongCustomTag(song, key, value)
                        Toast.makeText(
                            context,
                            if (result.isSuccess) context.getString(R.string.song_more_custom_tag_saved)
                            else result.exceptionOrNull()?.localizedMessage
                                ?: context.getString(R.string.song_more_custom_tag_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        customTagSong = null
                    }
                }
            )
        }
    }

    ratingSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_rating_title),
            onDismissRequest = { ratingSong = null }
        ) {
            RatingSheet(
                currentRating = mainViewModel.getSongRating(song),
                onDismiss = { ratingSong = null },
                onRatingSelected = { rating ->
                    scope.launch {
                        val result = mainViewModel.writeSongRating(song, rating)
                        Toast.makeText(
                            context,
                            if (result.isSuccess) context.getString(R.string.song_more_rating_saved)
                            else result.exceptionOrNull()?.localizedMessage
                                ?: context.getString(R.string.song_more_rating_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        ratingSong = null
                    }
                }
            )
        }
    }

    infoSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_song_details),
            onDismissRequest = { infoSong = null }
        ) {
            SongInfoSheet(
                song = song,
                audioInfoLoader = mainViewModel::getAudioInfo,
                tagInfoLoader = mainViewModel::getSongTagInfo,
                onDismiss = { infoSong = null }
            )
        }
    }

    aiSong?.let { song ->
        WindowBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = aiInterpretTitle,
            onDismissRequest = { aiSong = null }
        ) {
            SongAiInterpretationSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { aiSong = null }
            )
        }
    }
}

@Composable
private fun SongMoreActionSheet(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: (() -> Unit)?,
    onLyricTiming: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    showSpectrum: Boolean
) {
    SongSheetColumn {
        SongMenuItem(stringResource(R.string.song_more_add_to_playlist), onAddToPlaylist)
        SongMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        SongMenuItem(stringResource(R.string.common_share), onShare)
        if (showSpectrum) {
            SongMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        }
        SongMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        SongMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        SongMenuItem(stringResource(R.string.song_more_set_rating), onRating)
        SongMenuItem(
            stringResource(
                R.string.song_more_artist_entry,
                song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }
            ),
            onArtist
        )
        SongMenuItem(
            stringResource(
                R.string.song_more_album_entry,
                song.album.ifBlank { stringResource(R.string.player_unknown_album) }
            ),
            onAlbum
        )
        if (onEditTag != null) {
            SongMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        }
        if (onLyricTiming != null) {
            SongMenuItem(stringResource(R.string.song_more_lyric_timing), onLyricTiming)
        }
        if (onRemoveFromPlaylist != null) {
            SongMenuItem(stringResource(R.string.playlist_remove_song_title), onRemoveFromPlaylist, danger = true)
        }
        if (onDelete != null) {
            SongMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
fun AddToPlaylistSheet(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedIds }
    SongSheetColumn {
        SongMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedIds
                SongMenuItem(
                    stringResource(
                        R.string.song_more_playlist_item_summary,
                        if (selected) "\u2713 " else "",
                        playlist.name,
                        playlist.songs.size
                    ),
                    onClick = {
                        selectedIds = if (selected) {
                            selectedIds - playlist.id
                        } else {
                            selectedIds + playlist.id
                        }
                    }
                )
            }
        }
        if (playlists.isNotEmpty()) {
            SongMenuItem(
                stringResource(R.string.song_more_done_selected, selectedIds.size),
                onClick = {
                    if (selectedPlaylists.isNotEmpty()) {
                        onPlaylistsConfirm(selectedPlaylists)
                    }
                }
            )
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun ArtistPickerContent(
    artists: List<String>,
    onArtistSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SongSheetColumn {
        artists.distinctBy { it.tagIdentityKey() }.forEach { artist ->
            BasicComponent(
                title = artist,
                onClick = { onArtistSelected(artist) }
            )
        }
        BasicComponent(
            title = stringResource(R.string.common_cancel),
            onClick = onDismiss
        )
    }
}

@Composable
fun CreatePlaylistAndAddSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                insideMargin = DpSize(12.dp, 10.dp),
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 12.dp,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onCreate(name) }) { Text(stringResource(R.string.common_create)) }
            }
        }
    }
}

@Composable
private fun SongTagEditorSheet(
    song: Song,
    options: List<TagEditorOption>,
    onDismiss: () -> Unit,
    onOptionClick: (TagEditorOption) -> Unit
) {
    SongSheetColumn {
        Text(
            text = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option -> SongMenuItem(option.label, onClick = { onOptionClick(option) }) }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun RatingSheet(
    currentRating: Int,
    onDismiss: () -> Unit,
    onRatingSelected: (Int) -> Unit
) {
    SongSheetColumn {
        SongMenuItem(
            title = if (currentRating <= 0) {
                "\u2713 ${stringResource(R.string.song_more_rating_none)}"
            } else {
                stringResource(R.string.song_more_rating_none)
            },
            onClick = { onRatingSelected(0) }
        )
        (1..5).forEach { rating ->
            val stars = "\u2605".repeat(rating) + "\u2606".repeat(5 - rating)
            SongMenuItem(
                title = if (currentRating == rating) "\u2713 $stars" else stars,
                onClick = { onRatingSelected(rating) }
            )
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
private fun BuiltInCustomTagSheet(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextField(
            value = key,
            onValueChange = { key = it },
            label = stringResource(R.string.song_more_custom_tag_name),
            useLabelAsPlaceholder = true,
            singleLine = true,
            insideMargin = DpSize(12.dp, 10.dp),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            cornerRadius = 12.dp,
            textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        TextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.song_more_custom_tag_value),
            useLabelAsPlaceholder = true,
            singleLine = false,
            insideMargin = DpSize(12.dp, 10.dp),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            cornerRadius = 12.dp,
            textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSave(key, value) }) { Text(stringResource(R.string.common_save)) }
        }
    }
}

@Composable
fun SongInfoSheet(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showNeteaseKeyInfo by remember(song.id) { mutableStateOf(false) }
    var showNeteaseArtistPicker by remember(song.id) { mutableStateOf(false) }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { audioInfoLoader(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { tagInfoLoader(song) }
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }

    if (showNeteaseArtistPicker && neteaseArtists.isNotEmpty()) {
        SongSheetColumn {
            Text(
                text = stringResource(R.string.player_choose_netease_artist),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseArtists.forEach { artist ->
                SongMenuItem(artist.name.ifBlank { "ID ${artist.id}" }, onClick = {
                    openUrl(context, neteaseArtistUrl(artist.id))
                })
            }
            SongMenuItem(stringResource(R.string.song_more_back_to_netease_key), onClick = { showNeteaseArtistPicker = false })
        }
        return
    }

    if (showNeteaseKeyInfo && neteaseInfo != null) {
        SongSheetColumn {
            Text(
                text = stringResource(R.string.song_more_netease_key),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseInfo.musicName.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_song), it) }
            neteaseInfo.aliases
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow(stringResource(R.string.song_more_alias), it) }
            neteaseInfo.artists
                .joinToString(" / ") { it.name.ifBlank { it.id } }
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow(stringResource(R.string.player_detail_artist), it) }
            neteaseInfo.albumName.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_album), it) }
            neteaseInfo.comment.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_comment), it) }
            neteaseInfo.musicId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(stringResource(R.string.player_netease_song_page), onClick = { openUrl(context, neteaseSongUrl(id)) })
            }
            if (neteaseArtists.isNotEmpty()) {
                SongMenuItem(
                    title = stringResource(R.string.player_netease_artist_page),
                    onClick = {
                        if (neteaseArtists.size == 1) {
                            openUrl(context, neteaseArtistUrl(neteaseArtists.first().id))
                        } else {
                            showNeteaseArtistPicker = true
                        }
                    }
                )
            }
            neteaseInfo.albumId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(stringResource(R.string.player_netease_album_page), onClick = { openUrl(context, neteaseAlbumUrl(id)) })
            }
            SongInfoRow(stringResource(R.string.song_more_raw_netease_key), neteaseInfo.raw)
            SongMenuItem(stringResource(R.string.common_back), onClick = { showNeteaseKeyInfo = false })
        }
        return
    }

    SongSheetColumn {
        SongInfoRow(stringResource(R.string.player_detail_song), tagInfo?.title?.ifBlank { song.title } ?: song.title)
        SongInfoRow(stringResource(R.string.player_detail_artist), tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        SongInfoRow(stringResource(R.string.player_detail_album), tagInfo?.album?.ifBlank { song.album } ?: song.album)
        SongInfoRow(stringResource(R.string.song_more_detail_album_artist), tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_genre), tagInfo?.genre?.ifBlank { song.genre }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_year), tagInfo?.year?.ifBlank { song.year }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_composer), tagInfo?.composer?.ifBlank { song.composer }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_lyricist), tagInfo?.lyricist?.ifBlank { song.lyricist }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_comment), tagInfo?.displayComment.orEmpty())
        if (!tagInfo?.neteaseKey.isNullOrBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.song_more_netease_key),
                value = neteaseInfo?.musicName?.ifBlank { null }
                    ?: neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let {
                        context.getString(R.string.song_more_netease_song_id, it)
                    }
                    ?: stringResource(R.string.song_more_view_netease_info),
                onClick = { showNeteaseKeyInfo = true }
            )
        }
        SongInfoRow(stringResource(R.string.song_more_detail_format), audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_duration), song.durationText)
        SongInfoRow(stringResource(R.string.song_more_detail_size), formatFileSize(song.fileSize))
        SongInfoRow(stringResource(R.string.song_more_detail_modified_time), song.dateModified.formatSongDateTime())
        SongInfoRow(stringResource(R.string.song_more_detail_added_time), song.dateAdded.formatSongDateTime())
        SongInfoRow(stringResource(R.string.song_more_detail_file_name), song.fileName.ifBlank { song.path.substringAfterLast('/') })
        SongInfoRow(stringResource(R.string.song_more_detail_path), song.path)
    }
}

@Composable
private fun SongAiInterpretationSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    SongSheetColumn {
        Text(
            text = when {
                result == null -> stringResource(R.string.song_more_loading_ai)
                result?.isSuccess == true -> result?.getOrNull().orEmpty()
                else -> result?.exceptionOrNull()?.message ?: stringResource(R.string.song_more_ai_failed)
            },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        SongMenuItem(stringResource(R.string.common_close), onDismiss)
    }
}

@Composable
private fun SongSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun SongMenuItem(
    title: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    )
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.ROOT, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.ROOT, "%.2f MB", mb)
        else -> String.format(Locale.ROOT, "%.0f KB", kb)
    }
}

private fun Long.formatSongDateTime(): String {
    if (this <= 0L) return ""
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
