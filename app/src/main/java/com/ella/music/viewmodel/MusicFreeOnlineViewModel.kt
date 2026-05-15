package com.ella.music.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ella.music.data.musicfree.MusicFreeOnlineSong

class MusicFreeOnlineViewModel : ViewModel() {
    var importUrl by mutableStateOf("")
    var searchQuery by mutableStateOf("")
    var importExpanded by mutableStateOf(false)
    var isBusy by mutableStateOf(false)
    var results by mutableStateOf<List<MusicFreeOnlineSong>>(emptyList())
    var message by mutableStateOf("导入 MusicFree 插件后可搜索在线歌曲")

    fun clearResults(message: String) {
        results = emptyList()
        this.message = message
    }
}
