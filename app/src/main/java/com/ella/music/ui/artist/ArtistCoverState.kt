package com.ella.music.ui.artist

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.ella.music.data.ArtistCoverAsset
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberArtistCoverUri(
    artistName: String,
    folderLocation: String,
    mainViewModel: MainViewModel
): Uri? {
    val state by produceState<Uri?>(
        initialValue = null,
        artistName,
        folderLocation
    ) {
        value = if (artistName.isBlank() || folderLocation.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                mainViewModel.getArtistCoverUri(artistName, folderLocation)
            }
        }
    }
    return state
}

@Composable
internal fun rememberArtistCoverAsset(
    artistName: String,
    folderLocation: String,
    mainViewModel: MainViewModel
): ArtistCoverAsset? {
    val state by produceState<ArtistCoverAsset?>(
        initialValue = null,
        artistName,
        folderLocation
    ) {
        value = if (artistName.isBlank() || folderLocation.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                mainViewModel.getArtistCoverAsset(artistName, folderLocation)
            }
        }
    }
    return state
}

@Composable
internal fun rememberArtistCoverAssets(
    artistName: String,
    folderLocation: String,
    mainViewModel: MainViewModel
): List<ArtistCoverAsset> {
    val state by produceState<List<ArtistCoverAsset>>(
        initialValue = emptyList(),
        artistName,
        folderLocation
    ) {
        value = if (artistName.isBlank() || folderLocation.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                mainViewModel.getArtistCoverAssets(artistName, folderLocation)
            }
        }
    }
    return state
}
