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

enum class BottomBarGlassEffect {
    Blur,
    LiquidGlass
}

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_LYRICON_ENABLED = booleanPreferencesKey("lyricon_enabled")
        val KEY_LYRICON_TRANSLATION = booleanPreferencesKey("lyricon_translation")
        val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val KEY_GAPLESS = booleanPreferencesKey("gapless_playback")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_BOTTOM_BAR_GLASS_EFFECT = stringPreferencesKey("bottom_bar_glass_effect")
        val KEY_TICKER_ENABLED = booleanPreferencesKey("ticker_enabled")
        val KEY_TICKER_HIDE_NOTIFICATION = booleanPreferencesKey("ticker_hide_notification")
        val KEY_TICKER_HIDE_WHEN_PAUSED = booleanPreferencesKey("ticker_hide_when_paused")
        val KEY_TICKER_HEADS_UP_LYRICS = booleanPreferencesKey("ticker_heads_up_lyrics")
        val KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION = booleanPreferencesKey("samsung_floating_lyric_translation")
        val KEY_DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED = booleanPreferencesKey("desktop_lyric_hide_when_paused")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_MODE = booleanPreferencesKey("desktop_lyric_status_bar_mode")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET = intPreferencesKey("desktop_lyric_status_bar_top_offset")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION = intPreferencesKey("desktop_lyric_status_bar_position")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY = intPreferencesKey("desktop_lyric_status_bar_secondary")
        val KEY_DESKTOP_LYRIC_LOCKED = booleanPreferencesKey("desktop_lyric_locked")
        val KEY_DESKTOP_LYRIC_FONT_SCALE = intPreferencesKey("desktop_lyric_font_scale")
        val KEY_DESKTOP_LYRIC_TRANSLATION_SCALE = intPreferencesKey("desktop_lyric_translation_scale")
        val KEY_DESKTOP_LYRIC_OPACITY = intPreferencesKey("desktop_lyric_opacity")
        val KEY_DESKTOP_LYRIC_TEXT_COLOR = intPreferencesKey("desktop_lyric_text_color")
        val KEY_DESKTOP_LYRIC_SHADOW_STRENGTH = intPreferencesKey("desktop_lyric_shadow_strength")
        val KEY_DESKTOP_LYRIC_X = intPreferencesKey("desktop_lyric_x")
        val KEY_DESKTOP_LYRIC_Y = intPreferencesKey("desktop_lyric_y")
        val KEY_SUPER_LYRIC_ENABLED = booleanPreferencesKey("super_lyric_enabled")
        val KEY_SUPER_LYRIC_TRANSLATION = booleanPreferencesKey("super_lyric_translation")
        val KEY_LYRIC_GETTER_ENABLED = booleanPreferencesKey("lyric_getter_enabled")
        val KEY_MIN_DURATION = intPreferencesKey("min_duration_sec")
        val KEY_REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
        val KEY_AUDIO_FOCUS_DISABLED = booleanPreferencesKey("audio_focus_disabled")
        val KEY_SHUFFLE_MODE = intPreferencesKey("shuffle_mode")
        val KEY_PREVIOUS_BUTTON_ACTION = intPreferencesKey("previous_button_action")
        val KEY_LYRIC_SOURCE_MODE = intPreferencesKey("lyric_source_mode")
        val KEY_LYRIC_PAGE_TRANSLATION = booleanPreferencesKey("lyric_page_translation")
        val KEY_LYRIC_PAGE_KEEP_SCREEN_ON = booleanPreferencesKey("lyric_page_keep_screen_on")
        val KEY_MINI_PLAYER_LYRIC_TRANSLATION = booleanPreferencesKey("mini_player_lyric_translation")
        val KEY_MINI_PLAYER_COVER_ROTATION = booleanPreferencesKey("mini_player_cover_rotation")
        val KEY_MINI_PLAYER_LYRICS_ENABLED = booleanPreferencesKey("mini_player_lyrics_enabled")
        val KEY_PLAYER_HDR_GLOW = booleanPreferencesKey("player_hdr_glow")
        val KEY_PLAYER_IMMERSIVE_COVER = booleanPreferencesKey("player_immersive_cover")
        val KEY_PLAYER_DYNAMIC_FLOW_ENABLED = booleanPreferencesKey("player_dynamic_flow_enabled")
        val KEY_AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey("audio_visualizer_enabled")
        val KEY_DYNAMIC_COVER_ENABLED = booleanPreferencesKey("dynamic_cover_enabled")
        val KEY_SHOW_PLAY_NEXT_IN_LISTS = booleanPreferencesKey("show_play_next_in_lists")
        val KEY_LYRIC_SHARE_CUSTOM_INFO = stringPreferencesKey("lyric_share_custom_info")
        val KEY_SHOW_ALBUM_ARTISTS = booleanPreferencesKey("show_album_artists")
        val KEY_METADATA_EDITOR_ID = stringPreferencesKey("metadata_editor_id")
        val KEY_LYRIC_TIMING_EDITOR_ID = stringPreferencesKey("lyric_timing_editor_id")
        val KEY_SLEEP_TIMER_CUSTOM_MINUTES = intPreferencesKey("sleep_timer_custom_minutes")
        val KEY_SLEEP_TIMER_STOP_AFTER_CURRENT = booleanPreferencesKey("sleep_timer_stop_after_current")
        val KEY_SHORTCUT_LIBRARY_LABEL = stringPreferencesKey("shortcut_library_label")
        val KEY_SHORTCUT_PLAYLISTS_LABEL = stringPreferencesKey("shortcut_playlists_label")
        val KEY_SHORTCUT_FOLDER_LABEL = stringPreferencesKey("shortcut_folder_label")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val KEY_WEBDAV_LAST_URL = stringPreferencesKey("webdav_last_url")
        val KEY_LX_SOURCE_URL = stringPreferencesKey("lx_source_url")
        val KEY_LX_SOURCE_NAME = stringPreferencesKey("lx_source_name")
        val KEY_LX_SOURCE_SCRIPT = stringPreferencesKey("lx_source_script")
        val KEY_LX_SOURCES_JSON = stringPreferencesKey("lx_sources_json")
        val KEY_LX_SELECTED_SOURCE_ID = stringPreferencesKey("lx_selected_source_id")
        val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        val KEY_OPENAI_MODEL = stringPreferencesKey("openai_model")
        val KEY_OPEN_PLAYER_ON_PLAY = booleanPreferencesKey("online_auto_open_player")
        val KEY_STARTUP_AUTO_PLAY = booleanPreferencesKey("startup_auto_play")
        val KEY_STARTUP_PLAY_MODE = intPreferencesKey("startup_play_mode")
        val KEY_LYRIC_FONT_NAME = stringPreferencesKey("lyric_font_name")
        val KEY_LYRIC_FONT_PATH = stringPreferencesKey("lyric_font_path")
        val KEY_LYRIC_FONT_WEIGHT = intPreferencesKey("lyric_font_weight")
        val KEY_LYRIC_FONT_SCALE = intPreferencesKey("lyric_font_scale")
        val KEY_LYRIC_PERSPECTIVE_EFFECT = booleanPreferencesKey("lyric_perspective_effect")
        val KEY_SCAN_INCLUDE_FOLDERS = stringPreferencesKey("scan_include_folders")
        val KEY_SCAN_EXCLUDE_FOLDERS = stringPreferencesKey("scan_exclude_folders")
        val KEY_USE_ANDROID_MEDIA_LIBRARY = booleanPreferencesKey("use_android_media_library")
        val KEY_INITIAL_SCAN_PROMPT_HANDLED = booleanPreferencesKey("initial_scan_prompt_handled")
        val KEY_ARTIST_SEPARATORS = stringPreferencesKey("artist_separators")
        val KEY_ARTIST_PROTECTED_NAMES = stringPreferencesKey("artist_protected_names")
        val KEY_GENRE_SEPARATORS = stringPreferencesKey("genre_separators")
        val KEY_GENRE_PROTECTED_NAMES = stringPreferencesKey("genre_protected_names")
        val KEY_TAG_IGNORE_CASE = booleanPreferencesKey("tag_ignore_case")
        val KEY_DECODER_MODE = intPreferencesKey("decoder_mode")
        val KEY_SORT_LIBRARY_SONG = intPreferencesKey("sort_library_song")
        val KEY_SORT_ALBUM_LIST = intPreferencesKey("sort_album_list")
        val KEY_SORT_ARTIST_LIST = intPreferencesKey("sort_artist_list")
        val KEY_SORT_ALBUM_DETAIL_SONG = intPreferencesKey("sort_album_detail_song")
        val KEY_SORT_ARTIST_DETAIL_SONG = intPreferencesKey("sort_artist_detail_song")
        val KEY_SORT_FOLDER_LIST = intPreferencesKey("sort_folder_list")
        val KEY_SORT_FOLDER_DETAIL_SONG = intPreferencesKey("sort_folder_detail_song")
        val KEY_SORT_PLAYLIST_LIST = intPreferencesKey("sort_playlist_list")
        val KEY_SORT_PLAYLIST_DETAIL_SONG = intPreferencesKey("sort_playlist_detail_song")
        val KEY_CATEGORY_GRID_COLUMNS = intPreferencesKey("category_grid_columns")
        val KEY_HOME_DAILY_MIX_VISIBLE = booleanPreferencesKey("home_daily_mix_visible")
        val KEY_HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        val KEY_HOME_HIDDEN_SECTIONS = stringPreferencesKey("home_hidden_sections")
        val KEY_HOME_LIBRARY_TILE_ORDER = stringPreferencesKey("home_library_tile_order")
        val KEY_HOME_HIDDEN_LIBRARY_TILES = stringPreferencesKey("home_hidden_library_tiles")

        val KEY_BLUETOOTH_LYRIC_ENABLED = booleanPreferencesKey("bluetooth_lyric_enabled")
        val KEY_BLUETOOTH_LYRIC_TRANSLATION = booleanPreferencesKey("bluetooth_lyric_translation")

        const val SHUFFLE_MODE_PSEUDO = 0
        const val SHUFFLE_MODE_TRUE_RANDOM = 1

        const val PREVIOUS_BUTTON_PREVIOUS = 0
        const val PREVIOUS_BUTTON_REPLAY_CURRENT = 1
        const val PREVIOUS_REPLAY_THRESHOLD_MS = 20_000L

        const val STARTUP_PLAY_OFF = 0
        const val STARTUP_PLAY_RANDOM = 1
        const val STARTUP_PLAY_RESUME = 2

        const val LYRIC_SOURCE_AUTO = 0
        const val LYRIC_SOURCE_EXTERNAL = 1
        const val LYRIC_SOURCE_EMBEDDED = 2

        const val PLAYER_FLOW_EFFECT_DARK = 0
        const val DESKTOP_LYRIC_STATUS_POSITION_LEFT = 0
        const val DESKTOP_LYRIC_STATUS_POSITION_CENTER = 1
        const val DESKTOP_LYRIC_STATUS_POSITION_RIGHT = 2
        const val DESKTOP_LYRIC_STATUS_SECONDARY_OFF = 0
        const val DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION = 1
        const val DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION = 2

        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4.1-mini"
        const val DEFAULT_SHORTCUT_LIBRARY_LABEL = "音乐库"
        const val DEFAULT_SHORTCUT_PLAYLISTS_LABEL = "歌单"
        const val DEFAULT_SHORTCUT_FOLDER_LABEL = "文件夹"
        const val DEFAULT_HOME_SECTION_ORDER = "library,online,recent"
        const val DEFAULT_HOME_LIBRARY_TILE_ORDER = "artist,album,folder,folder_tree,playlist,analytics,genre,year,composer,lyricist"
    }

    private fun metadataCategorySortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_${type.safePreferenceSuffix()}")

    private fun metadataCategoryDetailSongSortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_detail_song_${type.safePreferenceSuffix()}")

    private fun metadataCategoryDetailAlbumSortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_detail_album_${type.safePreferenceSuffix()}")

    private fun String.safePreferenceSuffix(): String =
        lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "unknown" }

    val lyriconEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_ENABLED] ?: false }
    val lyriconTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_TRANSLATION] ?: true }
    val autoScan: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SCAN] ?: true }
    val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[KEY_GAPLESS] ?: true }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    val bottomBarGlassEffect: Flow<BottomBarGlassEffect> = context.dataStore.data.map { preferences ->
        runCatching {
            BottomBarGlassEffect.valueOf(
                preferences[KEY_BOTTOM_BAR_GLASS_EFFECT] ?: BottomBarGlassEffect.Blur.name
            )
        }.getOrDefault(BottomBarGlassEffect.Blur)
    }
    val tickerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_ENABLED] ?: false }
    val tickerHideNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HIDE_NOTIFICATION] ?: true }
    val tickerHideWhenPaused: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HIDE_WHEN_PAUSED] ?: false }
    val tickerHeadsUpLyrics: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HEADS_UP_LYRICS] ?: false }
    val samsungFloatingLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] ?: false }
    val desktopLyricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_ENABLED] ?: false }
    val desktopLyricHideWhenPaused: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED] ?: false }
    val desktopLyricStatusBarMode: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_STATUS_BAR_MODE] ?: false }
    val desktopLyricStatusBarTopOffset: Flow<Int> =
        context.dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET] ?: 16).coerceIn(0, 120) }
    val desktopLyricStatusBarPosition: Flow<Int> =
        context.dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION] ?: DESKTOP_LYRIC_STATUS_POSITION_CENTER).coerceIn(0, 2) }
    val desktopLyricStatusBarSecondary: Flow<Int> =
        context.dataStore.data.map { (it[KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY] ?: DESKTOP_LYRIC_STATUS_SECONDARY_OFF).coerceIn(0, 2) }
    val desktopLyricLocked: Flow<Boolean> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_LOCKED] ?: false }
    val desktopLyricFontScale: Flow<Int> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_FONT_SCALE] ?: 100 }
    val desktopLyricTranslationScale: Flow<Int> =
        context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_TRANSLATION_SCALE] ?: 110 }
    val desktopLyricOpacity: Flow<Int> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_OPACITY] ?: 100 }
    val desktopLyricTextColor: Flow<Int> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_TEXT_COLOR] ?: -1 }
    val desktopLyricShadowStrength: Flow<Int> =
        context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_SHADOW_STRENGTH] ?: 100 }
    val desktopLyricX: Flow<Int> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_X] ?: Int.MIN_VALUE }
    val desktopLyricY: Flow<Int> = context.dataStore.data.map { it[KEY_DESKTOP_LYRIC_Y] ?: Int.MIN_VALUE }
    val superLyricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_ENABLED] ?: false }
    val superLyricTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_TRANSLATION] ?: true }
    val lyricGetterEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_GETTER_ENABLED] ?: false }
    val minDurationSec: Flow<Int> = context.dataStore.data.map { it[KEY_MIN_DURATION] ?: 15 }
    val replayGainEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REPLAYGAIN_ENABLED] ?: false }
    val audioFocusDisabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_FOCUS_DISABLED] ?: false }
    val shuffleMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_SHUFFLE_MODE] ?: SHUFFLE_MODE_PSEUDO }
    val previousButtonAction: Flow<Int> =
        context.dataStore.data.map { it[KEY_PREVIOUS_BUTTON_ACTION] ?: PREVIOUS_BUTTON_PREVIOUS }
    val lyricSourceMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_LYRIC_SOURCE_MODE] ?: LYRIC_SOURCE_AUTO }
    val lyricPageTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PAGE_TRANSLATION] ?: true }
    val lyricPageKeepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LYRIC_PAGE_KEEP_SCREEN_ON] ?: false }
    val miniPlayerLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] ?: true }
    val miniPlayerCoverRotation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_COVER_ROTATION] ?: true }

    val miniPlayerLyricsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_LYRICS_ENABLED] ?: true }
    val playerHdrGlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PLAYER_HDR_GLOW] ?: false }
    val playerImmersiveCover: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_IMMERSIVE_COVER] ?: true }
    val playerDynamicFlowEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_DYNAMIC_FLOW_ENABLED] ?: false }
    val audioVisualizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUDIO_VISUALIZER_ENABLED] ?: false }
    val dynamicCoverEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DYNAMIC_COVER_ENABLED] ?: false }
    val showPlayNextInLists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_PLAY_NEXT_IN_LISTS] ?: true }
    val lyricShareCustomInfo: Flow<String> =
        context.dataStore.data.map { it[KEY_LYRIC_SHARE_CUSTOM_INFO] ?: "" }
    val showAlbumArtists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_ALBUM_ARTISTS] ?: false }
    val metadataEditorId: Flow<String> =
        context.dataStore.data.map { it[KEY_METADATA_EDITOR_ID] ?: "" }
    val lyricTimingEditorId: Flow<String> =
        context.dataStore.data.map { it[KEY_LYRIC_TIMING_EDITOR_ID] ?: "" }
    val sleepTimerCustomMinutes: Flow<Int> =
        context.dataStore.data.map { it[KEY_SLEEP_TIMER_CUSTOM_MINUTES]?.coerceIn(5, 120) ?: 45 }
    val sleepTimerStopAfterCurrent: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SLEEP_TIMER_STOP_AFTER_CURRENT] ?: false }
    val shortcutLibraryLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_LIBRARY_LABEL] ?: DEFAULT_SHORTCUT_LIBRARY_LABEL }
    val shortcutPlaylistsLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_PLAYLISTS_LABEL] ?: DEFAULT_SHORTCUT_PLAYLISTS_LABEL }
    val shortcutFolderLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_FOLDER_LABEL] ?: DEFAULT_SHORTCUT_FOLDER_LABEL }
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
    val openAiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_OPENAI_API_KEY] ?: "" }
    val openAiBaseUrl: Flow<String> =
        context.dataStore.data.map { it[KEY_OPENAI_BASE_URL] ?: DEFAULT_OPENAI_BASE_URL }
    val openAiModel: Flow<String> =
        context.dataStore.data.map { it[KEY_OPENAI_MODEL] ?: DEFAULT_OPENAI_MODEL }
    val openPlayerOnPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_OPEN_PLAYER_ON_PLAY] ?: true }
    val startupAutoPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_STARTUP_AUTO_PLAY] ?: false }
    val startupPlayMode: Flow<Int> = context.dataStore.data.map {
        it[KEY_STARTUP_PLAY_MODE]
            ?: if (it[KEY_STARTUP_AUTO_PLAY] == true) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF
    }
    val lyricFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_NAME] ?: "" }
    val lyricFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_PATH] ?: "" }
    val lyricFontWeight: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_FONT_WEIGHT] ?: 800 }
    val lyricFontScale: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_FONT_SCALE] ?: 100 }
    val lyricPerspectiveEffect: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PERSPECTIVE_EFFECT] ?: false }
    val scanIncludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_INCLUDE_FOLDERS] ?: "" }
    val scanExcludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_EXCLUDE_FOLDERS] ?: "" }
    val useAndroidMediaLibrary: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_USE_ANDROID_MEDIA_LIBRARY] ?: true }
    val initialScanPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_INITIAL_SCAN_PROMPT_HANDLED] ?: false }
    val artistSeparators: Flow<String> = context.dataStore.data.map { it[KEY_ARTIST_SEPARATORS] ?: "" }
    val artistProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_ARTIST_PROTECTED_NAMES] ?: "" }
    val genreSeparators: Flow<String> = context.dataStore.data.map { it[KEY_GENRE_SEPARATORS] ?: "" }
    val genreProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_GENRE_PROTECTED_NAMES] ?: "" }
    val tagIgnoreCase: Flow<Boolean> = context.dataStore.data.map { it[KEY_TAG_IGNORE_CASE] ?: false }
    val decoderMode: Flow<Int> = context.dataStore.data.map { it[KEY_DECODER_MODE] ?: 2 }
    val librarySongSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_LIBRARY_SONG] ?: 0 }
    val albumListSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_ALBUM_LIST] ?: 0 }
    val artistListSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_ARTIST_LIST] ?: 0 }
    val albumDetailSongSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_ALBUM_DETAIL_SONG] ?: 0 }
    val artistDetailSongSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_ARTIST_DETAIL_SONG] ?: 0 }
    val folderListSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_FOLDER_LIST] ?: 0 }
    val folderDetailSongSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_FOLDER_DETAIL_SONG] ?: 0 }
    val playlistListSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_PLAYLIST_LIST] ?: 2 }
    val playlistDetailSongSortIndex: Flow<Int> = context.dataStore.data.map { it[KEY_SORT_PLAYLIST_DETAIL_SONG] ?: 2 }
    val categoryGridColumns: Flow<Int> = context.dataStore.data.map {
        (it[KEY_CATEGORY_GRID_COLUMNS] ?: 2).coerceIn(1, 4)
    }
    val homeDailyMixVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_DAILY_MIX_VISIBLE] ?: true }
    val homeSectionOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_SECTION_ORDER] ?: DEFAULT_HOME_SECTION_ORDER }
    val homeHiddenSections: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_SECTIONS] ?: "" }
    val homeLibraryTileOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_LIBRARY_TILE_ORDER] ?: DEFAULT_HOME_LIBRARY_TILE_ORDER }
    val homeHiddenLibraryTiles: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_LIBRARY_TILES] ?: "" }
    fun metadataCategorySortIndex(type: String): Flow<Int> =
        context.dataStore.data.map { it[metadataCategorySortKey(type)] ?: 0 }

    fun metadataCategoryDetailSongSortIndex(type: String): Flow<Int> =
        context.dataStore.data.map { it[metadataCategoryDetailSongSortKey(type)] ?: 0 }

    fun metadataCategoryDetailAlbumSortIndex(type: String): Flow<Int> =
        context.dataStore.data.map { it[metadataCategoryDetailAlbumSortKey(type)] ?: 0 }

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

    suspend fun setBottomBarGlassEffect(effect: BottomBarGlassEffect) {
        context.dataStore.edit { it[KEY_BOTTOM_BAR_GLASS_EFFECT] = effect.name }
    }

    suspend fun setTickerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_ENABLED] = enabled }
    }

    suspend fun setTickerHideNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HIDE_NOTIFICATION] = enabled }
    }

    suspend fun setTickerHideWhenPaused(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HIDE_WHEN_PAUSED] = enabled }
    }

    suspend fun setTickerHeadsUpLyrics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HEADS_UP_LYRICS] = enabled }
    }

    suspend fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setDesktopLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_ENABLED] = enabled }
    }

    suspend fun setDesktopLyricHideWhenPaused(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED] = enabled }
    }

    suspend fun setDesktopLyricStatusBarMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_MODE] = enabled }
    }

    suspend fun setDesktopLyricStatusBarTopOffset(offsetDp: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET] = offsetDp.coerceIn(0, 120) }
    }

    suspend fun setDesktopLyricStatusBarPosition(position: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION] = position.coerceIn(0, 2) }
    }

    suspend fun setDesktopLyricStatusBarSecondary(mode: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY] = mode.coerceIn(0, 2) }
    }

    suspend fun setDesktopLyricLocked(locked: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_LOCKED] = locked }
    }

    suspend fun setDesktopLyricFontScale(scale: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_FONT_SCALE] = scale.coerceIn(80, 220) }
    }

    suspend fun setDesktopLyricTranslationScale(scale: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_TRANSLATION_SCALE] = scale.coerceIn(80, 220) }
    }

    suspend fun setDesktopLyricOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_OPACITY] = opacity.coerceIn(35, 100) }
    }

    suspend fun setDesktopLyricTextColor(color: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_TEXT_COLOR] = color }
    }

    suspend fun setDesktopLyricShadowStrength(strength: Int) {
        context.dataStore.edit { it[KEY_DESKTOP_LYRIC_SHADOW_STRENGTH] = strength.coerceIn(0, 160) }
    }

    suspend fun setDesktopLyricPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[KEY_DESKTOP_LYRIC_X] = x
            it[KEY_DESKTOP_LYRIC_Y] = y
        }
    }

    suspend fun resetDesktopLyricPosition() {
        context.dataStore.edit {
            it.remove(KEY_DESKTOP_LYRIC_X)
            it.remove(KEY_DESKTOP_LYRIC_Y)
        }
    }

    suspend fun setSuperLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_ENABLED] = enabled }
    }

    suspend fun setSuperLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setLyricGetterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_GETTER_ENABLED] = enabled }
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

    suspend fun setPreviousButtonAction(action: Int) {
        context.dataStore.edit {
            it[KEY_PREVIOUS_BUTTON_ACTION] = action.coerceIn(PREVIOUS_BUTTON_PREVIOUS, PREVIOUS_BUTTON_REPLAY_CURRENT)
        }
    }

    suspend fun setLyricSourceMode(mode: Int) {
        context.dataStore.edit { it[KEY_LYRIC_SOURCE_MODE] = mode.coerceIn(LYRIC_SOURCE_AUTO, LYRIC_SOURCE_EMBEDDED) }
    }

    suspend fun setLyricPageTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_TRANSLATION] = enabled }
    }

    suspend fun setLyricPageKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLyricPerspectiveEffect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PERSPECTIVE_EFFECT] = enabled }
    }

    suspend fun setMiniPlayerLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setMiniPlayerCoverRotation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_COVER_ROTATION] = enabled }
    }

    suspend fun setMiniPlayerLyricsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_LYRICS_ENABLED] = enabled }
    }

    suspend fun setPlayerHdrGlow(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_HDR_GLOW] = enabled }
    }

    suspend fun setPlayerImmersiveCover(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_IMMERSIVE_COVER] = enabled }
    }

    suspend fun setPlayerDynamicFlowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_DYNAMIC_FLOW_ENABLED] = enabled }
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_VISUALIZER_ENABLED] = enabled }
    }

    suspend fun setDynamicCoverEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COVER_ENABLED] = enabled }
    }

    suspend fun setShowPlayNextInLists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PLAY_NEXT_IN_LISTS] = enabled }
    }

    suspend fun setLyricShareCustomInfo(info: String) {
        context.dataStore.edit {
            val trimmed = info.trim().removePrefix("@").trim()
            if (trimmed.isBlank()) {
                it.remove(KEY_LYRIC_SHARE_CUSTOM_INFO)
            } else {
                it[KEY_LYRIC_SHARE_CUSTOM_INFO] = trimmed
            }
        }
    }

    suspend fun setShowAlbumArtists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_ALBUM_ARTISTS] = enabled }
    }

    suspend fun setMetadataEditorId(id: String) {
        context.dataStore.edit {
            val safeId = id.trim()
            if (safeId.isBlank()) it.remove(KEY_METADATA_EDITOR_ID) else it[KEY_METADATA_EDITOR_ID] = safeId
        }
    }

    suspend fun setLyricTimingEditorId(id: String) {
        context.dataStore.edit {
            val safeId = id.trim()
            if (safeId.isBlank()) it.remove(KEY_LYRIC_TIMING_EDITOR_ID) else it[KEY_LYRIC_TIMING_EDITOR_ID] = safeId
        }
    }

    suspend fun setSleepTimerCustomMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_CUSTOM_MINUTES] = minutes.coerceIn(5, 120) }
    }

    suspend fun setSleepTimerStopAfterCurrent(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_STOP_AFTER_CURRENT] = enabled }
    }

    suspend fun setShortcutLibraryLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_LIBRARY_LABEL, label, DEFAULT_SHORTCUT_LIBRARY_LABEL)
    }

    suspend fun setShortcutPlaylistsLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_PLAYLISTS_LABEL, label, DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    }

    suspend fun setShortcutFolderLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_FOLDER_LABEL, label, DEFAULT_SHORTCUT_FOLDER_LABEL)
    }

    private suspend fun setShortcutLabel(
        key: Preferences.Key<String>,
        label: String,
        defaultLabel: String
    ) {
        context.dataStore.edit {
            val safeLabel = label.trim().take(24)
            if (safeLabel.isBlank() || safeLabel == defaultLabel) it.remove(key) else it[key] = safeLabel
        }
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

    suspend fun setOpenAiApiKey(apiKey: String) {
        context.dataStore.edit {
            val trimmed = apiKey.trim()
            if (trimmed.isBlank()) it.remove(KEY_OPENAI_API_KEY) else it[KEY_OPENAI_API_KEY] = trimmed
        }
    }

    suspend fun setOpenAiBaseUrl(baseUrl: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_BASE_URL] = baseUrl.trim().ifBlank { DEFAULT_OPENAI_BASE_URL }
        }
    }

    suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_MODEL] = model.trim().ifBlank { DEFAULT_OPENAI_MODEL }
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

    suspend fun setLyricFontScale(scale: Int) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_SCALE] = scale.coerceIn(75, 130) }
    }

    suspend fun setLibrarySongSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_LIBRARY_SONG] = index.coerceAtLeast(0) }
    }

    suspend fun setAlbumListSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_ALBUM_LIST] = index.coerceAtLeast(0) }
    }

    suspend fun setArtistListSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_ARTIST_LIST] = index.coerceAtLeast(0) }
    }

    suspend fun setAlbumDetailSongSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_ALBUM_DETAIL_SONG] = index.coerceAtLeast(0) }
    }

    suspend fun setArtistDetailSongSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_ARTIST_DETAIL_SONG] = index.coerceAtLeast(0) }
    }

    suspend fun setFolderListSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_FOLDER_LIST] = index.coerceAtLeast(0) }
    }

    suspend fun setFolderDetailSongSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_FOLDER_DETAIL_SONG] = index.coerceAtLeast(0) }
    }

    suspend fun setPlaylistListSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_PLAYLIST_LIST] = index.coerceAtLeast(0) }
    }

    suspend fun setPlaylistDetailSongSortIndex(index: Int) {
        context.dataStore.edit { it[KEY_SORT_PLAYLIST_DETAIL_SONG] = index.coerceAtLeast(0) }
    }

    suspend fun setCategoryGridColumns(columns: Int) {
        context.dataStore.edit { it[KEY_CATEGORY_GRID_COLUMNS] = columns.coerceIn(1, 4) }
    }

    suspend fun setHomeDailyMixVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_DAILY_MIX_VISIBLE] = visible }
    }

    suspend fun setHomeSectionOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_SECTION_ORDER] = order.trim() }
    }

    suspend fun setHomeHiddenSections(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_SECTIONS] = hidden.trim() }
    }

    suspend fun setHomeLibraryTileOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_LIBRARY_TILE_ORDER] = order.trim() }
    }

    suspend fun setHomeHiddenLibraryTiles(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_LIBRARY_TILES] = hidden.trim() }
    }

    suspend fun setMetadataCategorySortIndex(type: String, index: Int) {
        context.dataStore.edit { it[metadataCategorySortKey(type)] = index.coerceAtLeast(0) }
    }

    suspend fun setMetadataCategoryDetailSongSortIndex(type: String, index: Int) {
        context.dataStore.edit { it[metadataCategoryDetailSongSortKey(type)] = index.coerceAtLeast(0) }
    }

    suspend fun setMetadataCategoryDetailAlbumSortIndex(type: String, index: Int) {
        context.dataStore.edit { it[metadataCategoryDetailAlbumSortKey(type)] = index.coerceAtLeast(0) }
    }

    suspend fun setScanIncludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_INCLUDE_FOLDERS] = folders.trim() }
    }

    suspend fun setUseAndroidMediaLibrary(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USE_ANDROID_MEDIA_LIBRARY] = enabled }
    }

    suspend fun setInitialScanPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_INITIAL_SCAN_PROMPT_HANDLED] = handled }
    }

    suspend fun setArtistSeparators(separators: String) {
        context.dataStore.edit { it[KEY_ARTIST_SEPARATORS] = separators.trim() }
    }

    suspend fun setArtistProtectedNames(names: String) {
        context.dataStore.edit { it[KEY_ARTIST_PROTECTED_NAMES] = names.trim() }
    }

    suspend fun setGenreSeparators(separators: String) {
        context.dataStore.edit { it[KEY_GENRE_SEPARATORS] = separators.trim() }
    }

    suspend fun setGenreProtectedNames(names: String) {
        context.dataStore.edit { it[KEY_GENRE_PROTECTED_NAMES] = names.trim() }
    }

    suspend fun setTagIgnoreCase(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TAG_IGNORE_CASE] = enabled }
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
            setBoolean(KEY_TICKER_HIDE_NOTIFICATION)
            setBoolean(KEY_TICKER_HIDE_WHEN_PAUSED)
            setBoolean(KEY_TICKER_HEADS_UP_LYRICS)
            setBoolean(KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION)
            setBoolean(KEY_DESKTOP_LYRIC_ENABLED)
            setBoolean(KEY_DESKTOP_LYRIC_LOCKED)
            setBoolean(KEY_SUPER_LYRIC_ENABLED)
            setBoolean(KEY_SUPER_LYRIC_TRANSLATION)
            setBoolean(KEY_LYRIC_GETTER_ENABLED)
            setBoolean(KEY_REPLAYGAIN_ENABLED)
            setBoolean(KEY_AUDIO_FOCUS_DISABLED)
            setBoolean(KEY_LYRIC_PAGE_TRANSLATION)
            setBoolean(KEY_LYRIC_PAGE_KEEP_SCREEN_ON)
            setBoolean(KEY_LYRIC_PERSPECTIVE_EFFECT)
            setBoolean(KEY_MINI_PLAYER_LYRIC_TRANSLATION)
            setBoolean(KEY_PLAYER_HDR_GLOW)
            setBoolean(KEY_PLAYER_IMMERSIVE_COVER)
            setBoolean(KEY_AUDIO_VISUALIZER_ENABLED)
            setBoolean(KEY_DYNAMIC_COVER_ENABLED)
            setBoolean(KEY_SHOW_PLAY_NEXT_IN_LISTS)
            setBoolean(KEY_SHOW_ALBUM_ARTISTS)
            setBoolean(KEY_USE_ANDROID_MEDIA_LIBRARY)
            setBoolean(KEY_INITIAL_SCAN_PROMPT_HANDLED)
            setBoolean(KEY_TAG_IGNORE_CASE)
            setBoolean(KEY_BLUETOOTH_LYRIC_ENABLED)
            setBoolean(KEY_BLUETOOTH_LYRIC_TRANSLATION)
            setBoolean(KEY_OPEN_PLAYER_ON_PLAY)
            setBoolean(KEY_STARTUP_AUTO_PLAY)
            setBoolean(KEY_HOME_DAILY_MIX_VISIBLE)

            setInt(KEY_THEME_MODE)
            setInt(KEY_MIN_DURATION)
            setInt(KEY_SHUFFLE_MODE)
            setInt(KEY_PREVIOUS_BUTTON_ACTION)
            setInt(KEY_STARTUP_PLAY_MODE)
            setInt(KEY_LYRIC_SOURCE_MODE)
            setInt(KEY_DESKTOP_LYRIC_FONT_SCALE)
            setInt(KEY_DESKTOP_LYRIC_TRANSLATION_SCALE)
            setInt(KEY_DESKTOP_LYRIC_OPACITY)
            setInt(KEY_DESKTOP_LYRIC_TEXT_COLOR)
            setInt(KEY_DESKTOP_LYRIC_SHADOW_STRENGTH)
            setInt(KEY_DESKTOP_LYRIC_X)
            setInt(KEY_DESKTOP_LYRIC_Y)
            setInt(KEY_DECODER_MODE)
            setInt(KEY_LYRIC_FONT_WEIGHT)
            setInt(KEY_LYRIC_FONT_SCALE)
            setInt(KEY_SORT_LIBRARY_SONG)
            setInt(KEY_SORT_ALBUM_LIST)
            setInt(KEY_SORT_ARTIST_LIST)
            setInt(KEY_SORT_ALBUM_DETAIL_SONG)
            setInt(KEY_SORT_ARTIST_DETAIL_SONG)
            setInt(KEY_SORT_FOLDER_LIST)
            setInt(KEY_SORT_FOLDER_DETAIL_SONG)
            setInt(KEY_SORT_PLAYLIST_LIST)
            setInt(KEY_SORT_PLAYLIST_DETAIL_SONG)
            setInt(KEY_CATEGORY_GRID_COLUMNS)

            val dynamicSortKeyPrefixes = listOf(
                "sort_metadata_category_",
                "sort_metadata_category_detail_song_",
                "sort_metadata_category_detail_album_"
            )
            val payloadKeys = payload.keys()
            while (payloadKeys.hasNext()) {
                val keyName = payloadKeys.next()
                if (dynamicSortKeyPrefixes.any { keyName.startsWith(it) } && !payload.isNull(keyName)) {
                    prefs[intPreferencesKey(keyName)] = payload.optInt(keyName)
                }
            }

            setString(KEY_WEBDAV_URL)
            setString(KEY_WEBDAV_USERNAME)
            setString(KEY_WEBDAV_PASSWORD)
            setString(KEY_WEBDAV_LAST_URL)
            setString(KEY_LX_SOURCE_URL)
            setString(KEY_LX_SOURCE_NAME)
            setString(KEY_LX_SOURCE_SCRIPT)
            setString(KEY_LX_SOURCES_JSON)
            setString(KEY_LX_SELECTED_SOURCE_ID)
            setString(KEY_OPENAI_API_KEY)
            setString(KEY_OPENAI_BASE_URL)
            setString(KEY_OPENAI_MODEL)
            setString(KEY_LYRIC_FONT_NAME)
            setString(KEY_LYRIC_FONT_PATH)
            setString(KEY_LYRIC_SHARE_CUSTOM_INFO)
            setString(KEY_METADATA_EDITOR_ID)
            setString(KEY_LYRIC_TIMING_EDITOR_ID)
            setString(KEY_SHORTCUT_LIBRARY_LABEL)
            setString(KEY_SHORTCUT_PLAYLISTS_LABEL)
            setString(KEY_SHORTCUT_FOLDER_LABEL)
            setString(KEY_SCAN_INCLUDE_FOLDERS)
            setString(KEY_SCAN_EXCLUDE_FOLDERS)
            setString(KEY_ARTIST_SEPARATORS)
            setString(KEY_ARTIST_PROTECTED_NAMES)
            setString(KEY_GENRE_SEPARATORS)
            setString(KEY_GENRE_PROTECTED_NAMES)
            setString(KEY_HOME_SECTION_ORDER)
            setString(KEY_HOME_HIDDEN_SECTIONS)
            setString(KEY_HOME_LIBRARY_TILE_ORDER)
            setString(KEY_HOME_HIDDEN_LIBRARY_TILES)
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

    private fun String.toLxSourceId(script: String): String {
        val source = trim().ifBlank { script.take(64) }
        return "lx_${source.hashCode()}"
    }
}
