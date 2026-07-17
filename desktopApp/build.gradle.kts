import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // The Compose plugin brings Kotlin onto the build classpath. Applying the JVM plugin without
    // a second version declaration keeps it compatible with this Android + Compose build.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val ffmpegPlatform = when {
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows-x86_64"
    System.getProperty("os.name").startsWith("Linux", ignoreCase = true) -> "linux-x86_64"
    else -> error("Halcyon Desktop supports Windows and Linux build hosts only.")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bytedeco.javacv)
    implementation(libs.bytedeco.ffmpeg)
    runtimeOnly("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:$ffmpegPlatform")
    implementation(libs.jaudiotagger)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.ella.music.desktop.DesktopMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Halcyon"
            packageVersion = "1.2.1"
            description = "Halcyon local music player"
            vendor = "Halcyon"

            windows {
                menuGroup = "Halcyon"
                upgradeUuid = "bb8e3a60-6fc7-41a2-92ef-829a9448a886"
            }
            linux {
                debMaintainer = "Halcyon"
                appCategory = "Audio"
            }
        }
    }
}
