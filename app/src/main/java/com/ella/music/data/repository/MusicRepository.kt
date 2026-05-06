package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.ella.music.data.model.Album
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.scanner.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val scanner = MusicScanner(context)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val lyricsCache = mutableMapOf<Long, List<LyricLine>>()
    private val replayGainCache = mutableMapOf<Long, Float?>()
    private val coverArtCache = mutableMapOf<Long, ByteArray?>()

    suspend fun scanMusic(minDurationMs: Long = 0) {
        _isScanning.value = true
        try {
            _songs.value = scanner.scanAllSongs(minDurationMs)
            _albums.value = scanner.scanAlbums()
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun getLyrics(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        lyricsCache[song.id]?.let { return@withContext it }

        Log.d("MusicRepo", "Loading lyrics for: ${song.title} path=${song.path}")

        val lrcContent = LrcParser.findLrcFile(song.path)
        if (lrcContent != null) {
            val parsed = LrcParser.parse(lrcContent)
            Log.d("MusicRepo", "LRC parsed: ${parsed.lyrics.size} lines for ${song.title}")
            lyricsCache[song.id] = parsed.lyrics
            return@withContext parsed.lyrics
        }

        Log.d("MusicRepo", "No LRC file found, trying embedded lyrics for ${song.title}")
        val embedded = scanner.extractEmbeddedLyrics(song.path)
        if (!embedded.isNullOrBlank()) {
            Log.d("MusicRepo", "Embedded lyrics found (${embedded.length} chars) for ${song.title}")
            val parsed = LrcParser.parse(embedded)
            if (parsed.lyrics.isNotEmpty()) {
                Log.d("MusicRepo", "Embedded lyrics parsed as LRC: ${parsed.lyrics.size} lines")
                lyricsCache[song.id] = parsed.lyrics
                return@withContext parsed.lyrics
            }

            Log.d("MusicRepo", "Embedded lyrics not LRC format, using plain text")
            val result = mutableListOf<LyricLine>()
            val lines = embedded.lines()
            var timeOffset = 0L
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    result.add(LyricLine(timeMs = timeOffset, text = trimmed, words = emptyList()))
                    timeOffset += 3000L
                }
            }
            if (result.isNotEmpty()) {
                Log.d("MusicRepo", "Plain text lyrics: ${result.size} lines")
                lyricsCache[song.id] = result
                return@withContext result
            }
        }

        Log.d("MusicRepo", "No lyrics found for ${song.title}")
        lyricsCache[song.id] = emptyList()
        emptyList()
    }

    fun getReplayGain(song: Song): Float? {
        replayGainCache[song.id]?.let { return it }
        val gain = scanner.extractReplayGain(song.path)
        replayGainCache[song.id] = gain
        return gain
    }

    fun getCoverArt(song: Song): ByteArray? {
        coverArtCache[song.id]?.let { return it }
        val art = scanner.extractCoverArt(song.path)
        coverArtCache[song.id] = art
        return art
    }

    fun getCoverArtBitmap(song: Song): Bitmap? {
        val data = getCoverArt(song) ?: return null
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return _songs.value.filter { it.albumId == albumId }
    }

    fun clearCache() {
        lyricsCache.clear()
        replayGainCache.clear()
        coverArtCache.clear()
    }
}
