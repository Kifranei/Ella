package com.ella.music.data.remote

import android.content.Context
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.toPlaylistSong
import com.ella.music.data.model.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Holds the active OpenSubsonic server's read-only playlist snapshot and server-side favorites.
 * It deliberately stays separate from [com.ella.music.data.PlaylistStore] so changing a server
 * playlist never mutates the user's local playlist file.
 */
class OpenSubsonicCollectionsStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val settingsManager = SettingsManager.getInstance(appContext)
    private val service = NavidromeService(appContext)
    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    private val _favoriteSongKeys = MutableStateFlow<Set<String>>(emptySet())
    private var activeServerId: String = ""

    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()
    val favoriteSongKeys: StateFlow<Set<String>> = _favoriteSongKeys.asStateFlow()

    suspend fun refreshForLibrarySource(source: String): Boolean = withContext(Dispatchers.IO) {
        if (source != SettingsManager.LIBRARY_SOURCE_OPENSUBSONIC) {
            clear()
            return@withContext true
        }
        val config = settingsManager.openSubsonicConfig.first()
        val serverId = settingsManager.openSubsonicActiveServerId.first().ifBlank { config.baseUrl }
        if (!config.isConfigured || serverId.isBlank()) {
            clear()
            return@withContext false
        }
        if (activeServerId.isNotBlank() && activeServerId != serverId) {
            clear()
        }
        val collections = runCatching { service.loadCollections(config) }.getOrElse {
            return@withContext false
        }
        applyCollections(config.provider, serverId, collections, config.remoteWriteEnabled)
        true
    }

    fun isManagedFavorite(song: Song): Boolean =
        song.onlineSource == RemoteMusicProvider.OpenSubsonic.id && song.onlineId.isNotBlank()

    /** Returns null when [song] does not belong to the active OpenSubsonic library. */
    suspend fun toggleFavorite(song: Song): Boolean? = withContext(Dispatchers.IO) {
        if (!isManagedFavorite(song)) return@withContext null
        if (settingsManager.librarySource.first() != SettingsManager.LIBRARY_SOURCE_OPENSUBSONIC) {
            return@withContext null
        }
        val config = settingsManager.openSubsonicConfig.first()
        val serverId = settingsManager.openSubsonicActiveServerId.first().ifBlank { config.baseUrl }
        if (!config.isConfigured || serverId.isBlank() || serverId != activeServerId) {
            return@withContext null
        }
        val key = song.playlistIdentityKey()
        val favorite = key !in _favoriteSongKeys.value
        service.setFavorite(config, song.onlineId, favorite)

        val nextSongs = if (favorite) {
            listOf(song) + favoriteSongs()
        } else {
            favoriteSongs().filterNot { it.playlistIdentityKey() == key }
        }
        updateFavorites(config.provider, serverId, nextSongs)
        favorite
    }

    fun clear() {
        activeServerId = ""
        _playlists.value = emptyList()
        _favoriteSongKeys.value = emptySet()
    }

    suspend fun renamePlaylist(playlistId: String, name: String): Boolean = mutatePlaylist(playlistId) { config, playlist ->
        service.updatePlaylist(config, playlist.remotePlaylistId, name = name)
    }

    suspend fun deletePlaylist(playlistId: String): Boolean = mutatePlaylist(playlistId) { config, playlist ->
        service.deletePlaylist(config, playlist.remotePlaylistId)
    }

    suspend fun addSongs(playlistId: String, songs: Collection<Song>): Boolean = mutatePlaylist(playlistId) { config, playlist ->
        service.updatePlaylist(config, playlist.remotePlaylistId, addSongIds = songs.map { it.onlineId })
    }

    suspend fun removeSongs(playlistId: String, songKeys: Set<String>): Boolean = mutatePlaylist(playlistId) { config, playlist ->
        val indices = playlist.songs.mapIndexedNotNull { index, song -> index.takeIf { song.key in songKeys } }
        service.updatePlaylist(config, playlist.remotePlaylistId, removeIndices = indices)
    }

    suspend fun reorderSongs(playlistId: String, orderedKeys: List<String>): Boolean = mutatePlaylist(playlistId) { config, playlist ->
        val songByKey = playlist.songs.associateBy { it.key }
        val orderedIds = orderedKeys.mapNotNull { songByKey[it]?.onlineId }
        if (orderedIds.size == playlist.songs.size) {
            service.updatePlaylist(
                config,
                playlist.remotePlaylistId,
                addSongIds = orderedIds,
                removeIndices = playlist.songs.indices.toList()
            )
        }
    }

    private suspend fun mutatePlaylist(
        playlistId: String,
        block: suspend (RemoteMusicSourceConfig, UserPlaylist) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val playlist = _playlists.value.firstOrNull { it.id == playlistId && it.remoteWritable }
            ?: return@withContext false
        if (playlist.remotePlaylistId == REMOTE_FAVORITES_ID) return@withContext false
        val config = settingsManager.openSubsonicConfig.first()
        if (!config.isConfigured || !config.remoteWriteEnabled) return@withContext false
        block(config, playlist)
        refreshForLibrarySource(SettingsManager.LIBRARY_SOURCE_OPENSUBSONIC)
    }

    private fun applyCollections(
        provider: RemoteMusicProvider,
        serverId: String,
        collections: SubsonicLibraryCollections,
        remoteWritable: Boolean
    ) {
        activeServerId = serverId
        val favorites = toPlaylist(
            provider = provider,
            serverId = serverId,
            playlistId = REMOTE_FAVORITES_ID,
            name = appContext.getString(
                R.string.remote_playlist_favorites,
                appContext.getString(R.string.remote_source_opensubsonic)
            ),
            songs = collections.favoriteSongs,
            remoteWritable = false
        )
        _favoriteSongKeys.value = collections.favoriteSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
        _playlists.value = listOf(favorites) + collections.playlists.map { playlist ->
            toPlaylist(
                provider = provider,
                serverId = serverId,
                playlistId = playlist.id,
                name = playlist.name,
                songs = playlist.songs,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                remoteWritable = remoteWritable
            )
        }
    }

    private fun updateFavorites(
        provider: RemoteMusicProvider,
        serverId: String,
        songs: List<Song>
    ) {
        _favoriteSongKeys.value = songs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
        val favoritePlaylist = toPlaylist(
            provider = provider,
            serverId = serverId,
            playlistId = REMOTE_FAVORITES_ID,
            name = appContext.getString(
                R.string.remote_playlist_favorites,
                appContext.getString(R.string.remote_source_opensubsonic)
            ),
            songs = songs
        )
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.remotePlaylistId == REMOTE_FAVORITES_ID) favoritePlaylist else playlist
        }
    }

    private fun favoriteSongs(): List<Song> =
        _playlists.value.firstOrNull { it.remotePlaylistId == REMOTE_FAVORITES_ID }
            ?.songs
            ?.map { it.toSong() }
            .orEmpty()

    private fun toPlaylist(
        provider: RemoteMusicProvider,
        serverId: String,
        playlistId: String,
        name: String,
        songs: List<Song>,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
        remoteWritable: Boolean = false
    ): UserPlaylist = UserPlaylist(
        id = "remote:${provider.id}:$serverId:$playlistId",
        name = name,
        songs = songs.map { it.toPlaylistSong() },
        createdAt = createdAt,
        updatedAt = updatedAt,
        remoteSource = provider.id,
        remoteServerId = serverId,
        remotePlaylistId = playlistId,
        remoteWritable = remoteWritable
    )

    companion object {
        const val REMOTE_FAVORITES_ID = "favorites"

        @Volatile
        private var instance: OpenSubsonicCollectionsStore? = null

        fun getInstance(context: Context): OpenSubsonicCollectionsStore =
            instance ?: synchronized(this) {
                instance ?: OpenSubsonicCollectionsStore(context).also { instance = it }
            }
    }
}
