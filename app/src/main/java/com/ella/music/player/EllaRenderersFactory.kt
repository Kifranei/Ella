package com.ella.music.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.ella.music.data.SettingsManager

/**
 * Custom [DefaultRenderersFactory] that injects the software [EqualizerAudioProcessor]
 * into ExoPlayer's audio sink so the custom 10-band EQ is applied to every playback.
 */
@UnstableApi
class EllaRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    private var equalizerAudioProcessor: EqualizerAudioProcessor? = null
    private var playbackOutputSettings: PlaybackOutputSettings = PlaybackOutputSettings()

    fun setEqualizerAudioProcessor(processor: EqualizerAudioProcessor?) {
        this.equalizerAudioProcessor = processor
    }

    fun setPlaybackOutputSettings(settings: PlaybackOutputSettings) {
        this.playbackOutputSettings = settings
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        val processors = buildList {
            equalizerAudioProcessor?.let { add(it) }
            if (playbackOutputSettings.needsFormatProcessor) {
                add(OutputFormatAudioProcessor(playbackOutputSettings))
            }
        }

        // Native AAudio / OpenSL ES output via Oboe. USB exclusive mode also routes here (AAudio
        // exclusive sharing mode pinned to the USB DAC). Falls back to the AudioTrack path if the
        // native library fails to load, so playback is never left without a sink.
        val backend = playbackOutputSettings.backend
        val usbExclusive = playbackOutputSettings.usbExclusive
        val useOboe = usbExclusive ||
            backend == SettingsManager.AUDIO_OUTPUT_BACKEND_AAUDIO ||
            backend == SettingsManager.AUDIO_OUTPUT_BACKEND_OPENSLES
        if (useOboe && OboeAudioOutput.ensureLoaded()) {
            // USB exclusive needs AAudio (exclusive sharing mode); otherwise honour the selection.
            val audioApi = if (!usbExclusive && backend == SettingsManager.AUDIO_OUTPUT_BACKEND_OPENSLES) 2 else 1
            val deviceId = if (usbExclusive) resolveUsbOutputDeviceId(context) else 0
            return OboeAudioSink(
                audioApi = audioApi,
                exclusive = usbExclusive,
                processors = processors,
                deviceId = deviceId
            )
        }

        return if (processors.isNotEmpty() || playbackOutputSettings.forceFloatOutput) {
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput || playbackOutputSettings.forceFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(processors.toTypedArray<AudioProcessor>())
                .build()
        } else {
            super.buildAudioSink(
                context,
                enableFloatOutput,
                enableAudioTrackPlaybackParams
            )
        }
    }

    private fun resolveUsbOutputDeviceId(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            ?.id
            ?: 0
    }
}
