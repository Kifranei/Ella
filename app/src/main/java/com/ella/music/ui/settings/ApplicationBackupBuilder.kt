package com.ella.music.ui.settings

import android.content.Context
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal suspend fun buildCompleteApplicationBackupJson(
    context: Context,
    librarySongs: List<Song> = emptyList()
): JSONObject = withContext(Dispatchers.IO) {
    JSONObject()
        .put("version", 1)
        .put("exportedAt", System.currentTimeMillis())
        .put("settings", SettingsManager.getInstance(context).exportSettingsJson())
        .put("playlists", PlaylistStore.getInstance(context).exportJson())
        .put("playback", PlaybackStatsStore.getInstance(context).exportJson(librarySongs))
        .put("aiChat", exportAiChatBackupJson(context))
}
