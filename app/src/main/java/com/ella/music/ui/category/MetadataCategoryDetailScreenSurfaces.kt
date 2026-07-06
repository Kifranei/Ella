package com.ella.music.ui.category

import android.content.Context
import android.widget.Toast
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
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

@Composable
internal fun MetadataCategoryDetailScreenSurfaces(
    context: Context,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    actionSong: Song?,
    onActionSongChange: (Song?) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    playlists: List<UserPlaylist>,
    playlistPickerSongs: List<Song>?,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    createPlaylistSongs: List<Song>?,
    onCreatePlaylistSongsChange: (List<Song>?) -> Unit,
    pendingDeleteSongs: List<Song>,
    onPendingDeleteSongsChange: (List<Song>) -> Unit,
    onDeleteSelectedSongs: (List<Song>) -> Unit,
    onClearSelection: () -> Unit
) {
    com.ella.music.ui.components.SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { onActionSongChange(null) },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist),
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
                    Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                    onPlaylistPickerSongsChange(null)
                    onClearSelection()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CategoryCreatePlaylistAndAddSelectedSheet(
            songCount = songsToAdd.size,
            onDismiss = { onCreatePlaylistSongsChange(null) },
            onCreate = { playlistName ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    Toast.makeText(context, context.getString(R.string.player_added_to_playlist_named, playlist.name), Toast.LENGTH_SHORT).show()
                    onCreatePlaylistSongsChange(null)
                    onClearSelection()
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
            onDeleteSelectedSongs(songsToDelete)
        }
    )
}
