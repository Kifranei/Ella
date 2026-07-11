package com.ella.music.ui.playlist

internal fun shouldSelectPlaylistOnLongPress(
    selectionMode: Boolean,
    alreadySelected: Boolean
): Boolean = !selectionMode || !alreadySelected
