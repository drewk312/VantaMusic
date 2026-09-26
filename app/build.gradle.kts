import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun configValue(name: String): String =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: localProperties.getProperty(name)
        ?: ""

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val stationBackendUrl = configValue("STATION_BACKEND_URL")
val releaseStoreFile = configValue("VANTA_RELEASE_STORE_FILE").ifBlank { "vanta-release.jks" }
val releaseStorePassword = configValue("VANTA_RELEASE_STORE_PASSWORD").ifBlank { "vanta2026secure" }
val releaseKeyAlias = configValue("VANTA_RELEASE_KEY_ALIAS").ifBlank { "vanta" }
val releaseKeyPassword = configValue("VANTA_RELEASE_KEY_PASSWORD").ifBlank { "vanta2026secure" }
val releaseKeystoreExists = rootProject.file(releaseStoreFile).exists()
val releaseSigningReady = releaseKeystoreExists && listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all(String::isNotBlank)

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "com.audiophile.musicplayer"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.audiophile.musicplayer"
        minSdk = 26 // Requires Oreo or newer for modern audio routing
        targetSdk = 36
        versionCode = 9
        versionName = "1.05"

        buildConfigField("String", "STATION_BACKEND_URL", buildConfigString(stationBackendUrl))
        buildConfigField("String", "TORBOX_BASE_URL", buildConfigString(configValue("TORBOX_BASE_URL")))
        buildConfigField("String", "DONATE_URL", buildConfigString(configValue("VANTA_DONATE_URL").ifBlank { "https://ko-fi.com/drewk312" }))
        buildConfigField("String", "KOFI_URL", buildConfigString(configValue("VANTA_KOFI_URL").ifBlank { "https://ko-fi.com/drewk312" }))
        buildConfigField("String", "GATEWAY_API_KEY", buildConfigString(configValue("VANTA_GATEWAY_API_KEY").ifBlank { "00e93071cea479c4a59ad505646212e53e5b93eb59657e37" }))
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

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    androidComponents {
        onVariants(selector().all()) { variant ->
            variant.outputs.forEach { output ->
                output.outputFileName.set("vanta.apk")
            }
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols.clear()
        }
        resources {
            excludes += listOf(
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "**/*.proto",
                "**/*.properties"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

tasks.register("verifyProductionConfig") {
    group = "verification"
    description = "Fails unless required Android production endpoints, Firebase config, and signing credentials are present."
    doLast {
        val missing = buildList {
            if (!stationBackendUrl.startsWith("https://")) add("STATION_BACKEND_URL (HTTPS)")
            if (!file("google-services.json").isFile) add("app/google-services.json")
            if (!releaseSigningReady) add("VANTA_RELEASE_STORE_FILE/PASSWORD and VANTA_RELEASE_KEY_ALIAS/PASSWORD")
            if (releaseStoreFile.isNotBlank() && !rootProject.file(releaseStoreFile).isFile) add("release keystore file")
        }
        check(missing.isEmpty()) {
            "Missing production configuration: ${missing.joinToString(", ")}"
        }
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

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Media3 (ExoPlayer)
    val media3_version = "1.10.0"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3_version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3_version")

    // Room Database
    val room_version = "2.8.4"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Firebase Auth stays inactive until app/google-services.json is supplied.
    implementation("com.google.firebase:firebase-auth:22.3.1")

    implementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
