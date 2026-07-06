package com.ella.music.ui.artist

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.viewmodel.MainViewModel

@Composable
internal fun ArtistScreenSurfaces(
    context: Context,
    mainViewModel: MainViewModel,
    playlists: List<UserPlaylist>,
    actionSong: Song?,
    onActionSongChange: (Song?) -> Unit,
    playerViewModel: com.ella.music.viewmodel.PlayerViewModel,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    playlistPickerSong: Song?,
    onPlaylistPickerSongChange: (Song?) -> Unit,
    createPlaylistSong: Song?,
    onCreatePlaylistSongChange: (Song?) -> Unit,
    playlistPickerSongs: List<Song>?,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    createPlaylistSongs: List<Song>?,
    onCreatePlaylistSongsChange: (List<Song>?) -> Unit,
    pendingDeleteSongs: List<Song>,
    onPendingDeleteSongsChange: (List<Song>) -> Unit,
    onRequestDeleteSongs: (List<Song>) -> Unit,
    onFinishSelectionMode: () -> Unit,
    tagEditorSong: Song?,
    onTagEditorSongChange: (Song?) -> Unit,
    songInfoSheetSong: Song?,
    onSongInfoSheetSongChange: (Song?) -> Unit,
    aiInterpretationSong: Song?,
    onAiInterpretationSongChange: (Song?) -> Unit
) {
    com.ella.music.ui.components.SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { onActionSongChange(null) },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )

    playlistPickerSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { onPlaylistPickerSongChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists
                    .sortedWith(compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                onDismiss = { onPlaylistPickerSongChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongChange(song)
                    onPlaylistPickerSongChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song), appendToEnd)
                    }
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onPlaylistPickerSongChange(null)
                }
            )
        }
    }

    createPlaylistSong?.let { song ->
        ArtistCreatePlaylistSheet(
            onDismiss = { onCreatePlaylistSongChange(null) },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                    onCreatePlaylistSongChange(null)
                }
            }
        )
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { onPlaylistPickerSongsChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists
                    .sortedWith(compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                songCount = songsToAdd.size,
                onDismiss = { onPlaylistPickerSongsChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongsChange(songsToAdd)
                    onPlaylistPickerSongsChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
                    }
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onPlaylistPickerSongsChange(null)
                    onFinishSelectionMode()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        ArtistCreatePlaylistSheet(
            onDismiss = { onCreatePlaylistSongsChange(null) },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    onCreatePlaylistSongsChange(null)
                    onFinishSelectionMode()
                }
            }
        )
    }

    ConfirmDangerDialog(
        show = pendingDeleteSongs.isNotEmpty(),
        title = stringResource(R.string.song_more_delete_song_title),
        message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
        confirmText = stringResource(R.string.song_more_delete_permanently),
        onDismiss = { onPendingDeleteSongsChange(emptyList()) },
        onConfirm = {
            val songsToDelete = pendingDeleteSongs
            onPendingDeleteSongsChange(emptyList())
            onRequestDeleteSongs(songsToDelete)
            onFinishSelectionMode()
        }
    )

    tagEditorSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_edit_tags_title),
            onDismissRequest = { onTagEditorSongChange(null) }
        ) {
            ArtistTagEditorMenu(
                song = song,
                onDismiss = { onTagEditorSongChange(null) },
                onOptionClick = { option ->
                    launchTagEditorOption(context, option)
                    onTagEditorSongChange(null)
                }
            )
        }
    }

    songInfoSheetSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_song_info),
            onDismissRequest = { onSongInfoSheetSongChange(null) }
        ) {
            ArtistSongInfoMenu(
                song = song,
                mainViewModel = mainViewModel,
                onAiInterpret = {
                    onSongInfoSheetSongChange(null)
                    onAiInterpretationSongChange(song)
                },
                onDismiss = { onSongInfoSheetSongChange(null) }
            )
        }
    }

    aiInterpretationSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_ai_title),
            onDismissRequest = { onAiInterpretationSongChange(null) }
        ) {
            ArtistAiInterpretationMenu(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { onAiInterpretationSongChange(null) }
            )
        }
    }
}
