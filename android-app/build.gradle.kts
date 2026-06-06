plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.material3)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.runtime)
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.koin.android)
                implementation(libs.koin.compose)
            }
        }
    }
}

android {
    namespace = "net.jami.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.jami.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        // x86_64 is emulator-only; excluding it keeps the APK 16KB-page-clean
        // (the x86_64 jami-core libs are 4KB-aligned and can't be recompiled here).
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "jami-kmp-debug"
            keyAlias = "jami-kmp-debug"
            keyPassword = "jami-kmp-debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkDependencies = false
        checkReleaseBuilds = false
        abortOnError = false
        ignoreWarnings = true
        quiet = true
    }
}
