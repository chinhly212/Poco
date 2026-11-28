plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.contentautomation"
    compileSdk = 34
    ndkVersion = "29.0.14206865"

    // Tạo APK riêng cho từng kiến trúc ARM để tránh gói universal quá lớn.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    defaultConfig {
        applicationId = "com.contentautomation"
        minSdk = 26
        targetSdk = 34
        // CI tăng phiên bản theo số lần chạy workflow; build local giữ phiên bản nền.
        val buildNumber = providers.gradleProperty("buildNumber").orNull?.toIntOrNull() ?: 0
        versionCode = 9 + buildNumber
        versionName = "1.7.$buildNumber"
        multiDexEnabled = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("nichi-release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Avoid release-only PNG crunching errors in existing image resources.
            isCrunchPngs = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }

        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {

    // ==============================
    // Jetpack Compose
    // ==============================

    val composeBom =
        platform("androidx.compose:compose-bom:2024.09.02")

    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // ==============================
    // Android UI & Lifecycle
    // ==============================

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.core:core-ktx:1.13.1")

    // ==============================
    // Kotlin Serialization
    // ==============================

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ==============================
    // Room Database
    // ==============================

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    ksp("androidx.room:room-compiler:2.6.1")

    // ==============================
    // WorkManager
    // ==============================

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ==============================
    // Coroutines
    // ==============================

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // On-device OCR for sampled video frames (bundled Latin script model)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // ==============================
    // Network
    // ==============================

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation("com.google.code.gson:gson:2.11.0")

    // ==============================
    // YouTube-DL Android
    // ==============================

    val youtubeDlVersion = "0.18.1"

    implementation(
        "io.github.junkfood02.youtubedl-android:library:$youtubeDlVersion"
    )

    // ==============================
    // FFmpeg for YouTube-DL Android
    // ==============================

    implementation(
        "io.github.junkfood02.youtubedl-android:ffmpeg:$youtubeDlVersion"
    )

    // ==============================
    // Existing FFmpegKit
    // ==============================

    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    // FFmpegKit references this Android exception formatter at runtime.
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Google Identity authorization for per-user Google Drive access
    implementation("com.google.android.gms:play-services-auth:22.0.0")

    // ==============================
    // Google Drive
    // ==============================

    implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")

    implementation(
        "com.google.apis:google-api-services-drive:v3-rev20240509-2.0.0"
    )

    implementation("com.google.http-client:google-http-client-gson:1.43.3")

    // ==============================
    // Secure Preferences & DataStore
    // ==============================

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ==============================
    // Coil Image Loading
    // ==============================

    implementation("io.coil-kt:coil-compose:2.7.0")

    // ==============================
    // Haze UI Effects
    // ==============================

    implementation("dev.chrisbanes.haze:haze:1.2.2")

    // ==============================
    // Media3 Transformer (Video Compression)
    // ==============================

    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
}
