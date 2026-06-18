package com.ella.music.player

import com.ella.music.data.model.Song

internal fun isDisplayOnlyMetadataPatchSnapshot(
    isMetadataOnlyPatch: Boolean,
    snapshotSong: Song?,
    currentSong: Song?
): Boolean {
    return isMetadataOnlyPatch &&
        snapshotSong != null &&
        snapshotSong.isSamePlaybackIdentity(currentSong)
}
