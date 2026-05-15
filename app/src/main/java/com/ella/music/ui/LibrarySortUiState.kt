package com.ella.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object LibrarySortUiState {
    var librarySongSortIndex by mutableIntStateOf(0)
    var albumListSortIndex by mutableIntStateOf(0)
    var artistListSortIndex by mutableIntStateOf(0)
    var albumDetailSongSortIndex by mutableIntStateOf(0)
    var artistDetailSongSortIndex by mutableIntStateOf(0)
    var folderDetailSongSortIndex by mutableIntStateOf(0)
}
