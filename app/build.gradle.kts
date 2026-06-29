plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.audiophile.musicplayer"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.audiophile.musicplayer"
        minSdk = 26 // Requires Oreo or newer for modern audio routing
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "STATION_BACKEND_URL", "\"${project.findProperty("STATION_BACKEND_URL") as? String ?: ""}\"")
        buildConfigField("String", "TORBOX_BASE_URL", "\"https://api.torbox.app/v1/\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_ARM_NEON=ON",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                cFlags += "-std=gnu11 -Wno-incompatible-pointer-types -Wno-implicit-int -Wno-implicit-function-declaration"
            }
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Media3 (ExoPlayer). The FFmpeg decoder module is not published as a normal
    // Google Maven artifact; when the locally-built Media3 FFmpeg module is present,
    // PlaybackService's extension renderer mode will prefer it automatically.
    val media3_version = "1.8.1"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3_version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3_version")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Coil (Compose image loading)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Encrypted credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Palette (artwork color extraction)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Retrofit, OkHttp & Coroutines
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

}

