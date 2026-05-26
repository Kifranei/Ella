plugins {
    alias(libs.plugins.androidLibrary)
    id("kotlin-parcelize")
}

android {
    namespace = "com.lonx.audiotag"
    compileSdk = 37
    val buildNative = providers.gradleProperty("ellaBuildNative")
        .map { it.toBoolean() }
        .getOrElse(false)

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        if (buildNative) {
            externalNativeBuild {
                cmake {
                    cppFlags += ""
                }
            }
        }
        ndk {
            val abiIncludes = providers.gradleProperty("ellaAbi")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a")
            abiFilters += abiIncludes
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    if (buildNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation(libs.kotlinx.coroutines.android)
}
