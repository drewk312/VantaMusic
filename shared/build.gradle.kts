plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Coroutines for shared flow/state patterns
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Optional: JSON serialization for future network use
    implementation("com.google.code.gson:gson:2.10.1")
}
