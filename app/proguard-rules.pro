# ProGuard rules for Ella Music

# Keep ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep data models
-keep class com.ella.music.data.model.** { *; }

# Keep Lyricon provider and model
-keep class io.github.proify.lyricon.** { *; }
-dontwarn io.github.proify.lyricon.**

# Keep FFmpeg decoder
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
