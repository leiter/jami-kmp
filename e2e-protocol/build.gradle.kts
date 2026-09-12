plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // api so consumers (runner + Android agent) get the serialization runtime for HarnessJson
    api(libs.kotlinx.serialization.json)
}

kotlin {
    // 17 bytecode, not jvmToolchain(17): a toolchain needs a JDK 17 *installation*, which
    // this machine does not have (only Android Studio's JBR 21), and no foojay resolver is
    // configured to fetch one. Matches shared/ and android-app/, which set compatibility only.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
