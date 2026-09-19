import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: CI decodes secrets.ANDROID_KEYSTORE_BASE64 into
// android/keystore/release.keystore and exports KEYSTORE_PASSWORD /
// KEY_ALIAS / KEY_PASSWORD. Without it the "release" build is signed with
// the debug key so the pipeline still yields an installable APK.
val keystoreFile: File = rootProject.file("keystore/release.keystore")
val hasReleaseKey = keystoreFile.exists()
    && !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()
    && !System.getenv("KEY_ALIAS").isNullOrBlank()
    && !System.getenv("KEY_PASSWORD").isNullOrBlank()

android {
    namespace = "dev.recompkit_nfsmw.android"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "dev.recompkit_nfsmw.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            // The game library is arm64-only (the release zip is validated).
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-O2", "-fvisibility=hidden")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        // Game files stream from the APK: keep them uncompressed so the
        // first-launch copy is a straight read.
        noCompress += listOf(
            "tga", "dds", "bik", "vp6", "smk", "mpg", "mp4",
            "ogg", "wav", "mp3", "sf2", "mus", "lzc",
            "big", "bun", "dat", "bin", "tpk", "popt", "pack"
        )
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
