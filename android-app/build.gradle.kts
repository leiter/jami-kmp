plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Firebase Cloud Messaging is opt-in at build time: the google-services plugin is applied only
// when a `google-services.json` has been dropped in. Without it the app still builds and runs —
// FirebaseApp.initializeApp() returns null and PushServiceManager falls back to the persistent
// daemon (LOCAL_NODE connectivity). This keeps the repo buildable without Firebase credentials.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
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
                implementation(libs.firebase.messaging)
            }
        }
    }
}

android {
    namespace = "net.jami.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    // E2E test harness flavor (out-of-band agent; see doc/end2endTesting.md).
    // `standard` is the production build; `harness` adds the on-device agent and
    // installs under a separate applicationId so its account storage is isolated.
    // (AGP forbids flavor names starting with "test", hence `harness`.)
    flavorDimensions += "mode"
    productFlavors {
        create("standard") { dimension = "mode" }
        create("harness") {
            dimension = "mode"
            applicationIdSuffix = ".harness"
            versionNameSuffix = "-harness"
        }
    }

    // KMP android-source-set-layout-v2 reads the flavor's *Kotlin* from
    // `src/androidHarness/`, but AGP-managed files (manifest, res) for a flavor
    // still default to the legacy `src/harness/` path. Repoint both the harness
    // manifest and res so the on-device agent's <service> and the flavor's icon
    // override live next to its Kotlin.
    sourceSets.getByName("harness") {
        manifest.srcFile("src/androidHarness/AndroidManifest.xml")
        res.srcDir("src/androidHarness/res")
    }

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

// harness flavor-only dependencies (on-device agent: protocol + Ktor WS client).
dependencies {
    "harnessImplementation"(project(":e2e-protocol"))
    "harnessImplementation"(libs.ktor.client.core)
    "harnessImplementation"(libs.ktor.client.okhttp)
    "harnessImplementation"(libs.ktor.client.websockets)
}
