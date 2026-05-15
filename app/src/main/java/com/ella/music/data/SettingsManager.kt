package com.ella.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ella_settings")

data class LxSourceConfig(
    val id: String,
    val url: String,
    val name: String,
    val script: String
)

data class MusicFreePluginConfig(
    val id: String,
    val url: String,
    val name: String,
    val script: String
)

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_LYRICON_ENABLED = booleanPreferencesKey("lyricon_enabled")
        val KEY_LYRICON_TRANSLATION = booleanPreferencesKey("lyricon_translation")
        val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val KEY_GAPLESS = booleanPreferencesKey("gapless_playback")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_TICKER_ENABLED = booleanPreferencesKey("ticker_enabled")
        val KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION = booleanPreferencesKey("samsung_floating_lyric_translation")
        val KEY_DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val KEY_SUPER_LYRIC_ENABLED = booleanPreferencesKey("super_lyric_enabled")
        val KEY_SUPER_LYRIC_TRANSLATION = booleanPreferencesKey("super_lyric_translation")
        val KEY_MIN_DURATION = intPreferencesKey("min_duration_sec")
        val KEY_REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
        val KEY_AUDIO_FOCUS_DISABLED = booleanPreferencesKey("audio_focus_disabled")
        val KEY_SHUFFLE_MODE = intPreferencesKey("shuffle_mode")
        val KEY_LYRIC_SOURCE_MODE = intPreferencesKey("lyric_source_mode")
        val KEY_LYRIC_PAGE_TRANSLATION = booleanPreferencesKey("lyric_page_translation")
        val KEY_PLAYER_HDR_GLOW = booleanPreferencesKey("player_hdr_glow")
        val KEY_AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey("audio_visualizer_enabled")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val KEY_WEBDAV_LAST_URL = stringPreferencesKey("webdav_last_url")
        val KEY_LX_SOURCE_URL = stringPreferencesKey("lx_source_url")
        val KEY_LX_SOURCE_NAME = stringPreferencesKey("lx_source_name")
        val KEY_LX_SOURCE_SCRIPT = stringPreferencesKey("lx_source_script")
        val KEY_LX_SOURCES_JSON = stringPreferencesKey("lx_sources_json")
        val KEY_LX_SELECTED_SOURCE_ID = stringPreferencesKey("lx_selected_source_id")
        val KEY_MUSICFREE_PLUGINS_JSON = stringPreferencesKey("musicfree_plugins_json")
        val KEY_MUSICFREE_SELECTED_PLUGIN_ID = stringPreferencesKey("musicfree_selected_plugin_id")
        val KEY_OPEN_PLAYER_ON_PLAY = booleanPreferencesKey("online_auto_open_player")
        val KEY_STARTUP_AUTO_PLAY = booleanPreferencesKey("startup_auto_play")
        val KEY_STARTUP_PLAY_MODE = intPreferencesKey("startup_play_mode")
        val KEY_LYRIC_FONT_NAME = stringPreferencesKey("lyric_font_name")
        val KEY_LYRIC_FONT_PATH = stringPreferencesKey("lyric_font_path")
        val KEY_LYRIC_FONT_WEIGHT = intPreferencesKey("lyric_font_weight")
        val KEY_SCAN_INCLUDE_FOLDERS = stringPreferencesKey("scan_include_folders")
        val KEY_SCAN_EXCLUDE_FOLDERS = stringPreferencesKey("scan_exclude_folders")
        val KEY_DECODER_MODE = intPreferencesKey("decoder_mode")

        val KEY_BLUETOOTH_LYRIC_ENABLED = booleanPreferencesKey("bluetooth_lyric_enabled")
        val KEY_BLUETOOTH_LYRIC_TRANSLATION = booleanPreferencesKey("bluetooth_lyric_translation")

        const val SHUFFLE_MODE_PSEUDO = 0
        const val SHUFFLE_MODE_TRUE_RANDOM = 1

        const val STARTUP_PLAY_OFF = 0
        const val STARTUP_PLAY_RANDOM = 1
        const val STARTUP_PLAY_RESUME = 2

        const val LYRIC_SOURCE_AUTO = 0
        const val LYRIC_SOURCE_EXTERNAL = 1
        const val LYRIC_SOURCE_EMBEDDED = 2

        const val PLAYER_FLOW_EFFECT_DARK = 0
    }

    val lyriconEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_ENABLED] ?: false }
    val lyriconTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_TRANSLATION] ?: true }
    val autoScan: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SCAN] ?: false }
    val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[KEY_GAPLESS] ?: true }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    val tickerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_ENABLED] ?: false }
    val samsungFloatingLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] ?: false }
    val desktopLyricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_ENABLED] ?: false }
    val superLyricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_ENABLED] ?: false }
    val superLyricTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_TRANSLATION] ?: true }
    val minDurationSec: Flow<Int> = context.dataStore.data.map { it[KEY_MIN_DURATION] ?: 15 }
    val replayGainEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REPLAYGAIN_ENABLED] ?: false }
    val audioFocusDisabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_FOCUS_DISABLED] ?: false }
    val shuffleMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_SHUFFLE_MODE] ?: SHUFFLE_MODE_PSEUDO }
    val lyricSourceMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_LYRIC_SOURCE_MODE] ?: LYRIC_SOURCE_AUTO }
    val lyricPageTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PAGE_TRANSLATION] ?: true }
    val playerHdrGlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PLAYER_HDR_GLOW] ?: false }
    val audioVisualizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUDIO_VISUALIZER_ENABLED] ?: false }
    val webDavUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
    val webDavUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_USERNAME] ?: "" }
    val webDavPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }
    val webDavLastUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_LAST_URL] ?: "" }
    val lxSources: Flow<List<LxSourceConfig>> = context.dataStore.data.map { prefs -> prefs.lxSources() }
    val selectedLxSourceId: Flow<String> = context.dataStore.data.map { it[KEY_LX_SELECTED_SOURCE_ID] ?: "" }
    val selectedLxSource: Flow<LxSourceConfig?> = context.dataStore.data.map { prefs ->
        val sources = prefs.lxSources()
        val selectedId = prefs[KEY_LX_SELECTED_SOURCE_ID].orEmpty()
        sources.firstOrNull { it.id == selectedId } ?: sources.firstOrNull()
    }
    val lxSourceUrl: Flow<String> = selectedLxSource.map { it?.url.orEmpty() }
    val lxSourceName: Flow<String> = selectedLxSource.map { it?.name.orEmpty() }
    val lxSourceScript: Flow<String> = selectedLxSource.map { it?.script.orEmpty() }
    val musicFreePlugins: Flow<List<MusicFreePluginConfig>> =
        context.dataStore.data.map { prefs -> prefs.musicFreePlugins() }
    val selectedMusicFreePluginId: Flow<String> =
        context.dataStore.data.map { it[KEY_MUSICFREE_SELECTED_PLUGIN_ID] ?: "" }
    val selectedMusicFreePlugin: Flow<MusicFreePluginConfig?> = context.dataStore.data.map { prefs ->
        val plugins = prefs.musicFreePlugins()
        val selectedId = prefs[KEY_MUSICFREE_SELECTED_PLUGIN_ID].orEmpty()
        plugins.firstOrNull { it.id == selectedId } ?: plugins.firstOrNull()
    }
    val openPlayerOnPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_OPEN_PLAYER_ON_PLAY] ?: true }
    val startupAutoPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_STARTUP_AUTO_PLAY] ?: false }
    val startupPlayMode: Flow<Int> = context.dataStore.data.map {
        it[KEY_STARTUP_PLAY_MODE]
            ?: if (it[KEY_STARTUP_AUTO_PLAY] == true) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF
    }
    val lyricFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_NAME] ?: "" }
    val lyricFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_PATH] ?: "" }
    val lyricFontWeight: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_FONT_WEIGHT] ?: 800 }
    val scanIncludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_INCLUDE_FOLDERS] ?: "" }
    val scanExcludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_EXCLUDE_FOLDERS] ?: "" }
    val decoderMode: Flow<Int> = context.dataStore.data.map { it[KEY_DECODER_MODE] ?: 2 }

    val bluetoothLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_ENABLED] ?: false }
    val bluetoothLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_TRANSLATION] ?: false }
    suspend fun setLyriconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_ENABLED] = enabled }
    }

    suspend fun setLyriconTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_TRANSLATION] = enabled }
    }

    suspend fun setAutoScan(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCAN] = enabled }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GAPLESS] = enabled }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setTickerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_ENABLED] = enabled }
    }

    suspend fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setDesktopLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_ENABLED] = enabled }
    }

    suspend fun setSuperLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_ENABLED] = enabled }
    }

    suspend fun setSuperLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setBluetoothLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_ENABLED] = enabled }
    }

    suspend fun setBluetoothLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setMinDurationSec(seconds: Int) {
        context.dataStore.edit { it[KEY_MIN_DURATION] = seconds }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REPLAYGAIN_ENABLED] = enabled }
    }

    suspend fun setAudioFocusDisabled(disabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_FOCUS_DISABLED] = disabled }
    }

    suspend fun setShuffleMode(mode: Int) {
        context.dataStore.edit { it[KEY_SHUFFLE_MODE] = mode.coerceIn(SHUFFLE_MODE_PSEUDO, SHUFFLE_MODE_TRUE_RANDOM) }
    }

    suspend fun setLyricSourceMode(mode: Int) {
        context.dataStore.edit { it[KEY_LYRIC_SOURCE_MODE] = mode.coerceIn(LYRIC_SOURCE_AUTO, LYRIC_SOURCE_EMBEDDED) }
    }

    suspend fun setLyricPageTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_TRANSLATION] = enabled }
    }

    suspend fun setPlayerHdrGlow(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_HDR_GLOW] = enabled }
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_VISUALIZER_ENABLED] = enabled }
    }

    suspend fun setWebDavConfig(url: String, username: String, password: String) {
        context.dataStore.edit {
            it[KEY_WEBDAV_URL] = url.trim()
            it[KEY_WEBDAV_USERNAME] = username
            it[KEY_WEBDAV_PASSWORD] = password
            it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    suspend fun setWebDavLastUrl(url: String) {
        context.dataStore.edit {
            if (url.isBlank()) it.remove(KEY_WEBDAV_LAST_URL) else it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    suspend fun clearWebDavConfig() {
        context.dataStore.edit {
            it.remove(KEY_WEBDAV_URL)
            it.remove(KEY_WEBDAV_USERNAME)
            it.remove(KEY_WEBDAV_PASSWORD)
            it.remove(KEY_WEBDAV_LAST_URL)
        }
    }

    suspend fun setLxSource(url: String, name: String, script: String) {
        context.dataStore.edit {
            val source = LxSourceConfig(
                id = url.toLxSourceId(script),
                url = url.trim(),
                name = name.ifBlank { "LX源" },
                script = script
            )
            val sources = it.lxSources().filterNot { existing -> existing.id == source.id } + source
            it[KEY_LX_SOURCES_JSON] = sources.toJson()
            it[KEY_LX_SELECTED_SOURCE_ID] = source.id
            it[KEY_LX_SOURCE_URL] = source.url
            it[KEY_LX_SOURCE_NAME] = source.name
            it[KEY_LX_SOURCE_SCRIPT] = source.script
        }
    }

    suspend fun clearLxSource() {
        context.dataStore.edit {
            it.remove(KEY_LX_SOURCES_JSON)
            it.remove(KEY_LX_SELECTED_SOURCE_ID)
            it.remove(KEY_LX_SOURCE_URL)
            it.remove(KEY_LX_SOURCE_NAME)
            it.remove(KEY_LX_SOURCE_SCRIPT)
        }
    }

    suspend fun selectLxSource(id: String) {
        context.dataStore.edit { prefs ->
            val source = prefs.lxSources().firstOrNull { it.id == id } ?: return@edit
            prefs[KEY_LX_SELECTED_SOURCE_ID] = source.id
            prefs[KEY_LX_SOURCE_URL] = source.url
            prefs[KEY_LX_SOURCE_NAME] = source.name
            prefs[KEY_LX_SOURCE_SCRIPT] = source.script
        }
    }

    suspend fun removeLxSource(id: String) {
        context.dataStore.edit { prefs ->
            val sources = prefs.lxSources().filterNot { it.id == id }
            if (sources.isEmpty()) {
                prefs.remove(KEY_LX_SOURCES_JSON)
                prefs.remove(KEY_LX_SELECTED_SOURCE_ID)
                prefs.remove(KEY_LX_SOURCE_URL)
                prefs.remove(KEY_LX_SOURCE_NAME)
                prefs.remove(KEY_LX_SOURCE_SCRIPT)
            } else {
                val selected = sources.firstOrNull { it.id == prefs[KEY_LX_SELECTED_SOURCE_ID] } ?: sources.first()
                prefs[KEY_LX_SOURCES_JSON] = sources.toJson()
                prefs[KEY_LX_SELECTED_SOURCE_ID] = selected.id
                prefs[KEY_LX_SOURCE_URL] = selected.url
                prefs[KEY_LX_SOURCE_NAME] = selected.name
                prefs[KEY_LX_SOURCE_SCRIPT] = selected.script
            }
        }
    }

    suspend fun setMusicFreePlugin(url: String, name: String, script: String) {
        context.dataStore.edit {
            val plugin = MusicFreePluginConfig(
                id = url.toMusicFreePluginId(script),
                url = url.trim(),
                name = name.ifBlank { "MusicFree 插件" },
                script = script
            )
            val plugins = it.musicFreePlugins().filterNot { existing -> existing.id == plugin.id } + plugin
            it[KEY_MUSICFREE_PLUGINS_JSON] = plugins.toMusicFreeJson()
            it[KEY_MUSICFREE_SELECTED_PLUGIN_ID] = plugin.id
        }
    }

    suspend fun setMusicFreePlugins(importedPlugins: List<MusicFreePluginConfig>) {
        if (importedPlugins.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs.musicFreePlugins()
            val merged = (existing + importedPlugins)
                .asReversed()
                .distinctBy { it.id }
                .asReversed()
            prefs[KEY_MUSICFREE_PLUGINS_JSON] = merged.toMusicFreeJson()
            prefs[KEY_MUSICFREE_SELECTED_PLUGIN_ID] = importedPlugins.first().id
        }
    }

    suspend fun selectMusicFreePlugin(id: String) {
        context.dataStore.edit { prefs ->
            val plugin = prefs.musicFreePlugins().firstOrNull { it.id == id } ?: return@edit
            prefs[KEY_MUSICFREE_SELECTED_PLUGIN_ID] = plugin.id
        }
    }

    suspend fun removeMusicFreePlugin(id: String) {
        context.dataStore.edit { prefs ->
            val plugins = prefs.musicFreePlugins().filterNot { it.id == id }
            if (plugins.isEmpty()) {
                prefs.remove(KEY_MUSICFREE_PLUGINS_JSON)
                prefs.remove(KEY_MUSICFREE_SELECTED_PLUGIN_ID)
            } else {
                val selected = plugins.firstOrNull { it.id == prefs[KEY_MUSICFREE_SELECTED_PLUGIN_ID] } ?: plugins.first()
                prefs[KEY_MUSICFREE_PLUGINS_JSON] = plugins.toMusicFreeJson()
                prefs[KEY_MUSICFREE_SELECTED_PLUGIN_ID] = selected.id
            }
        }
    }

    suspend fun clearMusicFreePlugins() {
        context.dataStore.edit {
            it.remove(KEY_MUSICFREE_PLUGINS_JSON)
            it.remove(KEY_MUSICFREE_SELECTED_PLUGIN_ID)
        }
    }

    suspend fun setOpenPlayerOnPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPEN_PLAYER_ON_PLAY] = enabled }
    }

    suspend fun setStartupAutoPlay(enabled: Boolean) {
        setStartupPlayMode(if (enabled) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF)
    }

    suspend fun setStartupPlayMode(mode: Int) {
        val safeMode = mode.coerceIn(STARTUP_PLAY_OFF, STARTUP_PLAY_RESUME)
        context.dataStore.edit {
            it[KEY_STARTUP_PLAY_MODE] = safeMode
            it[KEY_STARTUP_AUTO_PLAY] = safeMode != STARTUP_PLAY_OFF
        }
    }

    suspend fun setLyricFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_FONT_NAME] = name.ifBlank { "自定义字体" }
            it[KEY_LYRIC_FONT_PATH] = path
        }
    }

    suspend fun clearLyricFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_FONT_NAME)
            it.remove(KEY_LYRIC_FONT_PATH)
        }
    }

    suspend fun setLyricFontWeight(weight: Int) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_WEIGHT] = weight.coerceIn(100, 900) }
    }

    suspend fun setScanIncludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_INCLUDE_FOLDERS] = folders.trim() }
    }

    suspend fun exportSettingsJson(): JSONObject {
        val prefs = context.dataStore.data.first()
        val payload = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            when (value) {
                is Boolean -> payload.put(key.name, value)
                is Int -> payload.put(key.name, value)
                is String -> payload.put(key.name, value)
            }
        }
        return payload
    }

    suspend fun restoreSettingsJson(payload: JSONObject) {
        context.dataStore.edit { prefs ->
            fun setBoolean(key: Preferences.Key<Boolean>) {
                if (payload.has(key.name) && !payload.isNull(key.name)) prefs[key] = payload.optBoolean(key.name)
            }
            fun setInt(key: Preferences.Key<Int>) {
                if (payload.has(key.name) && !payload.isNull(key.name)) prefs[key] = payload.optInt(key.name)
            }
            fun setString(key: Preferences.Key<String>) {
                if (payload.has(key.name) && !payload.isNull(key.name)) prefs[key] = payload.optString(key.name)
            }

            setBoolean(KEY_LYRICON_ENABLED)
            setBoolean(KEY_LYRICON_TRANSLATION)
            setBoolean(KEY_AUTO_SCAN)
            setBoolean(KEY_GAPLESS)
            setBoolean(KEY_TICKER_ENABLED)
            setBoolean(KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION)
            setBoolean(KEY_DESKTOP_LYRIC_ENABLED)
            setBoolean(KEY_SUPER_LYRIC_ENABLED)
            setBoolean(KEY_SUPER_LYRIC_TRANSLATION)
            setBoolean(KEY_REPLAYGAIN_ENABLED)
            setBoolean(KEY_AUDIO_FOCUS_DISABLED)
            setBoolean(KEY_LYRIC_PAGE_TRANSLATION)
            setBoolean(KEY_PLAYER_HDR_GLOW)
            setBoolean(KEY_AUDIO_VISUALIZER_ENABLED)
            setBoolean(KEY_BLUETOOTH_LYRIC_ENABLED)
            setBoolean(KEY_BLUETOOTH_LYRIC_TRANSLATION)
            setBoolean(KEY_OPEN_PLAYER_ON_PLAY)
            setBoolean(KEY_STARTUP_AUTO_PLAY)

            setInt(KEY_THEME_MODE)
            setInt(KEY_MIN_DURATION)
            setInt(KEY_SHUFFLE_MODE)
            setInt(KEY_STARTUP_PLAY_MODE)
            setInt(KEY_LYRIC_SOURCE_MODE)
            setInt(KEY_DECODER_MODE)
            setInt(KEY_LYRIC_FONT_WEIGHT)

            setString(KEY_WEBDAV_URL)
            setString(KEY_WEBDAV_USERNAME)
            setString(KEY_WEBDAV_PASSWORD)
            setString(KEY_WEBDAV_LAST_URL)
            setString(KEY_LX_SOURCE_URL)
            setString(KEY_LX_SOURCE_NAME)
            setString(KEY_LX_SOURCE_SCRIPT)
            setString(KEY_LX_SOURCES_JSON)
            setString(KEY_LX_SELECTED_SOURCE_ID)
            setString(KEY_MUSICFREE_PLUGINS_JSON)
            setString(KEY_MUSICFREE_SELECTED_PLUGIN_ID)
            setString(KEY_LYRIC_FONT_NAME)
            setString(KEY_LYRIC_FONT_PATH)
            setString(KEY_SCAN_INCLUDE_FOLDERS)
            setString(KEY_SCAN_EXCLUDE_FOLDERS)
        }
    }

    suspend fun setScanExcludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_EXCLUDE_FOLDERS] = folders.trim() }
    }

    suspend fun setDecoderMode(mode: Int) {
        context.dataStore.edit { it[KEY_DECODER_MODE] = mode.coerceIn(0, 2) }
    }

    private fun Preferences.lxSources(): List<LxSourceConfig> {
        val parsed = parseLxSources(this[KEY_LX_SOURCES_JSON].orEmpty())
        if (parsed.isNotEmpty()) return parsed

        val legacyUrl = this[KEY_LX_SOURCE_URL].orEmpty()
        val legacyScript = this[KEY_LX_SOURCE_SCRIPT].orEmpty()
        if (legacyUrl.isBlank() && legacyScript.isBlank()) return emptyList()

        return listOf(
            LxSourceConfig(
                id = legacyUrl.toLxSourceId(legacyScript),
                url = legacyUrl,
                name = this[KEY_LX_SOURCE_NAME].orEmpty().ifBlank { "LX源" },
                script = legacyScript
            )
        )
    }

    private fun parseLxSources(json: String): List<LxSourceConfig> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                LxSourceConfig(
                    id = item.optString("id"),
                    url = item.optString("url"),
                    name = item.optString("name").ifBlank { "LX源" },
                    script = item.optString("script")
                )
            }.filter { it.id.isNotBlank() && it.script.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun List<LxSourceConfig>.toJson(): String {
        val array = JSONArray()
        forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("url", source.url)
                    .put("name", source.name)
                    .put("script", source.script)
            )
        }
        return array.toString()
    }

    private fun Preferences.musicFreePlugins(): List<MusicFreePluginConfig> {
        val json = this[KEY_MUSICFREE_PLUGINS_JSON].orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MusicFreePluginConfig(
                    id = item.optString("id"),
                    url = item.optString("url"),
                    name = item.optString("name").ifBlank { "MusicFree 插件" },
                    script = item.optString("script")
                )
            }.filter { it.id.isNotBlank() && it.script.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun List<MusicFreePluginConfig>.toMusicFreeJson(): String {
        val array = JSONArray()
        forEach { plugin ->
            array.put(
                JSONObject()
                    .put("id", plugin.id)
                    .put("url", plugin.url)
                    .put("name", plugin.name)
                    .put("script", plugin.script)
            )
        }
        return array.toString()
    }

    private fun String.toLxSourceId(script: String): String {
        val source = trim().ifBlank { script.take(64) }
        return "lx_${source.hashCode()}"
    }

    private fun String.toMusicFreePluginId(script: String): String {
        val source = trim().ifBlank { script.take(64) }
        return "musicfree_${source.hashCode()}"
    }
}
