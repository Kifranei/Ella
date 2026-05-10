import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

android {
    namespace = "com.ella.music"
    compileSdk = 37
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")?.let { file(it) } ?: file("release.jks")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "kidn0x1"
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "kidn0x1"
    val hasReleaseSigning = releaseStoreFile.exists() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()

    defaultConfig {
        applicationId = "com.ella.music"
        minSdk = 29
        targetSdk = 37
        versionCode = 8
        versionName = "1.0.7"

        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lyricon.provider)
    implementation(libs.backdrop)
    implementation(libs.taglib)
    implementation(libs.jaudiotagger)
    implementation("wang.harlon.quickjs:wrapper-android:2.4.0")
    implementation(project(":ffmpeg-decoder"))

    implementation("top.yukonga.miuix.kmp:miuix-ui")
    implementation("top.yukonga.miuix.kmp:miuix-icons")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android")
    implementation("top.yukonga.miuix.kmp:miuix-preference")
}
