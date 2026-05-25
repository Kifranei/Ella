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
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?: listOf(file("release.jks"), rootProject.file("release.jks"))
            .firstOrNull { it.exists() }
        ?: file("release.jks")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "kidn0x1"
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "kidn0x1"
    val hasReleaseSigning = releaseStoreFile.exists() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()

    defaultConfig {
        applicationId = "com.ella.music"
        minSdk = 31
        targetSdk = 37
        versionCode = 17
        versionName = "1.1.6"

        val buildTime = providers.environmentVariable("BUILD_TIME")
            .orElse("local")
            .get()
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    splits {
        abi {
            val abiIncludes = providers.gradleProperty("ellaAbi")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a")

            isEnable = true
            reset()
            include(*abiIncludes.toTypedArray())
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!hasReleaseSigning) {
                throw GradleException(
                    "Release signing is not configured. Put release.jks in app/ or project root, " +
                        "or set RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD."
                )
            }
            signingConfig = signingConfigs.getByName("release")
        }

    create("fastRelease") {
        initWith(getByName("release"))
        isMinifyEnabled = false
        isShrinkResources = false
        matchingFallbacks += listOf("release")
        signingConfig = signingConfigs.getByName("release")
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lyricon.provider)
    implementation(libs.lyric.getter.api)
    implementation("com.github.HChenX:SuperLyricApi:3.4")
    implementation(libs.backdrop)
    implementation(libs.taglib)
    implementation(libs.jaudiotagger)
    implementation("wang.harlon.quickjs:wrapper-android:2.4.0")
    implementation(project(":ffmpeg-decoder"))

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
}
