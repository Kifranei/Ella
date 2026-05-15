# ProGuard rules for Ella Music

# JNI entry points are resolved by class and method names.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Lyricon is a provider-facing API; keep its published model names stable.
-keep class io.github.proify.lyricon.** { *; }
-dontwarn io.github.proify.lyricon.**

# SuperLyricApi references this hidden framework class on supported systems.
-dontwarn android.os.ServiceManager

# jaudiotagger uses Class.getPackage().getName() while initializing readers.
-keeppackagenames org.jaudiotagger.**
-keepnames class org.jaudiotagger.**

# FFmpeg native symbols use Java_androidx_media3_decoder_ffmpeg_* names.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
