package com.ella.music.desktop

import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * A JVM player backed by JavaCV's platform FFmpeg binaries. It keeps decoding inside the app,
 * rather than depending on an installed `ffmpeg`, and therefore handles the common lossless and
 * lossy local formats on both supported desktop targets.
 */
class FfmpegAudioEngine(
    private val onState: (isPlaying: Boolean, positionMs: Long) -> Unit,
    private val onCompleted: () -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "halcyon-audio").apply { isDaemon = true }
    }
    private val gate = Object()
    private val requestedSeekMs = AtomicLong(NO_SEEK_REQUEST)
    private val playbackGeneration = AtomicLong(0L)

    @Volatile private var stopRequested = false
    @Volatile private var paused = false
    @Volatile private var outputLine: SourceDataLine? = null
    @Volatile private var currentSongId: String? = null
    @Volatile private var lastReportedPositionMs = 0L
    @Volatile private var volume = 1f

    fun play(song: DesktopSong, startPositionMs: Long = 0L) {
        stop()
        val generation = playbackGeneration.incrementAndGet()
        currentSongId = song.id
        stopRequested = false
        paused = false
        lastReportedPositionMs = startPositionMs.coerceAtLeast(0L)
        requestedSeekMs.set(lastReportedPositionMs)
        executor.execute { runPlayback(song, generation) }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }

    fun pause() {
        paused = true
        outputLine?.stop()
        onState(false, lastReportedPositionMs)
    }

    fun resume() {
        if (currentSongId == null) return
        paused = false
        outputLine?.start()
        onState(true, lastReportedPositionMs)
        synchronized(gate) { gate.notifyAll() }
    }

    fun seekTo(positionMs: Long) {
        requestedSeekMs.set(positionMs.coerceAtLeast(0L))
        synchronized(gate) { gate.notifyAll() }
    }

    fun stop() {
        playbackGeneration.incrementAndGet()
        stopRequested = true
        paused = false
        outputLine?.let { line ->
            runCatching { line.stop() }
            runCatching { line.flush() }
            runCatching { line.close() }
        }
        outputLine = null
        synchronized(gate) { gate.notifyAll() }
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }

    private fun runPlayback(song: DesktopSong, generation: Long) {
        var grabber: FFmpegFrameGrabber? = null
        var line: SourceDataLine? = null
        var lastPositionMs = lastReportedPositionMs
        var lastUiUpdateMs = Long.MIN_VALUE
        try {
            grabber = FFmpegFrameGrabber(song.path).apply {
                audioChannels = 2
                sampleFormat = AV_SAMPLE_FMT_S16
                start()
            }
            val sampleRate = grabber.sampleRate.coerceAtLeast(8_000)
            val channels = grabber.audioChannels.coerceIn(1, 2)
            val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
            line = AudioSystem.getSourceDataLine(format).apply {
                open(format)
                start()
            }
            outputLine = line
            onState(true, lastPositionMs)
            lastUiUpdateMs = lastPositionMs

            while (isCurrentPlayback(song, generation)) {
                awaitIfPaused(lastPositionMs)
                if (!isCurrentPlayback(song, generation)) break

                val seek = requestedSeekMs.getAndSet(NO_SEEK_REQUEST)
                if (seek != NO_SEEK_REQUEST) {
                    grabber.timestamp = seek * 1_000L
                    line.flush()
                    lastPositionMs = seek
                    lastReportedPositionMs = seek
                    onState(!paused, seek)
                    lastUiUpdateMs = seek
                }

                val frame = grabber.grabSamples() ?: break
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                val bytes = samples.asLittleEndianBytes(volume)
                if (bytes.isNotEmpty()) line.write(bytes, 0, bytes.size)
                lastPositionMs = (grabber.timestamp / 1_000L).coerceAtLeast(0L)
                lastReportedPositionMs = lastPositionMs
                if (lastPositionMs - lastUiUpdateMs >= PLAYBACK_STATE_INTERVAL_MS) {
                    onState(!paused, lastPositionMs)
                    lastUiUpdateMs = lastPositionMs
                }
            }

            if (isCurrentPlayback(song, generation)) {
                line.drain()
                if (isCurrentPlayback(song, generation)) onCompleted()
            }
        } catch (error: Throwable) {
            if (isCurrentPlayback(song, generation)) {
                onError("Unable to play ${song.title}: ${error.message ?: error.javaClass.simpleName}")
            }
        } finally {
            if (outputLine === line) outputLine = null
            runCatching { line?.stop() }
            runCatching { line?.close() }
            runCatching { grabber?.stop() }
            runCatching { grabber?.release() }
        }
    }

    private fun awaitIfPaused(positionMs: Long) {
        synchronized(gate) {
            while (paused && !stopRequested) {
                onState(false, positionMs)
                gate.wait()
            }
        }
    }

    private fun isCurrentPlayback(song: DesktopSong, generation: Long): Boolean =
        !stopRequested && currentSongId == song.id && playbackGeneration.get() == generation

    private fun ShortBuffer.asLittleEndianBytes(volume: Float): ByteArray {
        val source = duplicate()
        val bytes = ByteArray(source.remaining() * 2)
        var index = 0
        val gain = volume.coerceIn(0f, 1f)
        while (source.hasRemaining()) {
            val sample = (source.get().toInt() * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes[index++] = (sample and 0xFF).toByte()
            bytes[index++] = ((sample ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private companion object {
        const val NO_SEEK_REQUEST = Long.MIN_VALUE
        const val PLAYBACK_STATE_INTERVAL_MS = 80L
    }
}
