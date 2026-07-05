// Native Oboe-backed audio output for Halcyon.
//
// Media3/ExoPlayer's DefaultAudioSink writes PCM to a Java AudioTrack. To route audio through
// AAudio or OpenSL ES instead, OboeAudioSink (Kotlin) forwards decoded PCM to this native layer,
// which opens an Oboe output stream in blocking-write mode and pushes the buffers to it.
//
// Encoding ids (must match SettingsManager / OboeAudioOutput):
//   0 = PCM 16-bit, 1 = PCM 24-bit (packed), 2 = PCM 32-bit, 3 = float32
// AudioApi ids: 0 = unspecified (let Oboe choose), 1 = AAudio, 2 = OpenSL ES

#include <jni.h>
#include <oboe/Oboe.h>
#include <mutex>

namespace {

struct OboeSink {
    std::shared_ptr<oboe::AudioStream> stream;
    std::mutex mutex;
    int channelCount = 2;
    int bytesPerFrame = 4;
};

oboe::AudioFormat toOboeFormat(int encoding) {
    switch (encoding) {
        case 0: return oboe::AudioFormat::I16;
        case 1: return oboe::AudioFormat::I24;
        case 2: return oboe::AudioFormat::I32;
        case 3: return oboe::AudioFormat::Float;
        default: return oboe::AudioFormat::I16;
    }
}

int bytesPerSample(oboe::AudioFormat format) {
    switch (format) {
        case oboe::AudioFormat::I16: return 2;
        case oboe::AudioFormat::I24: return 3;
        case oboe::AudioFormat::I32: return 4;
        case oboe::AudioFormat::Float: return 4;
        default: return 2;
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeOpen(
        JNIEnv*, jobject, jint audioApi, jint sampleRate, jint channelCount,
        jint encoding, jboolean exclusive, jint deviceId) {
    auto* sink = new OboeSink();

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setSharingMode(exclusive ? oboe::SharingMode::Exclusive : oboe::SharingMode::Shared)
            ->setPerformanceMode(oboe::PerformanceMode::None)
            ->setFormat(toOboeFormat(encoding))
            ->setChannelCount(channelCount)
            ->setSampleRate(sampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music);

    if (audioApi == 1) {
        builder.setAudioApi(oboe::AudioApi::AAudio);
    } else if (audioApi == 2) {
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
    }
    if (deviceId > 0) {
        builder.setDeviceId(deviceId);
    }

    oboe::Result result = builder.openStream(sink->stream);
    if (result != oboe::Result::OK || !sink->stream) {
        delete sink;
        return 0;
    }
    sink->channelCount = sink->stream->getChannelCount();
    sink->bytesPerFrame = sink->channelCount * bytesPerSample(sink->stream->getFormat());
    sink->stream->requestStart();
    return reinterpret_cast<jlong>(sink);
}

// Blocking write of a direct ByteBuffer region. Returns the number of BYTES consumed, or -1 on error.
JNIEXPORT jint JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeWrite(
        JNIEnv* env, jobject, jlong handle, jobject buffer, jint offset, jint length, jlong timeoutNanos) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink == nullptr || !sink->stream) return -1;
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (base == nullptr) return -1;

    std::lock_guard<std::mutex> lock(sink->mutex);
    const int numFrames = length / sink->bytesPerFrame;
    if (numFrames <= 0) return 0;

    auto result = sink->stream->write(base + offset, numFrames, timeoutNanos);
    if (!result) {
        return (result.error() == oboe::Result::ErrorDisconnected) ? -2 : -1;
    }
    return result.value() * sink->bytesPerFrame;
}

// Frames actually consumed by the device — used to derive the current playback position.
JNIEXPORT jlong JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeGetFramesRead(JNIEnv*, jobject, jlong handle) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink == nullptr || !sink->stream) return 0;
    return static_cast<jlong>(sink->stream->getFramesRead());
}

JNIEXPORT jint JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeGetSampleRate(JNIEnv*, jobject, jlong handle) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink == nullptr || !sink->stream) return 0;
    return sink->stream->getSampleRate();
}

JNIEXPORT void JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativePause(JNIEnv*, jobject, jlong handle) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink != nullptr && sink->stream) sink->stream->requestPause();
}

JNIEXPORT void JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeStart(JNIEnv*, jobject, jlong handle) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink != nullptr && sink->stream) sink->stream->requestStart();
}

JNIEXPORT void JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeFlush(JNIEnv*, jobject, jlong handle) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink == nullptr || !sink->stream) return;
    std::lock_guard<std::mutex> lock(sink->mutex);
    sink->stream->requestPause();
    sink->stream->requestFlush();
}

JNIEXPORT void JNICALL
Java_com_ella_music_player_OboeAudioOutput_nativeClose(JNIEnv*, jobject, jlong handle) {
    auto* sink = reinterpret_cast<OboeSink*>(handle);
    if (sink == nullptr) return;
    {
        std::lock_guard<std::mutex> lock(sink->mutex);
        if (sink->stream) {
            sink->stream->requestStop();
            sink->stream->close();
            sink->stream.reset();
        }
    }
    delete sink;
}

} // extern "C"
