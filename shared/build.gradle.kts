plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

// Set to true when JamiBridge Objective-C++ wrapper is compiled as a static library
// The JamiBridgeWrapper.mm must be compiled and linked with libjami.a first
// See: shared/src/nativeInterop/cinterop/JamiBridge/README.md for build instructions
val enableJamiBridgeCinterop = true
val jamiBridgePath = "${projectDir}/src/nativeInterop/cinterop"
val libjamiLibPath = "${projectDir}/src/nativeInterop/cinterop/lib"

kotlin {
    // Android
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // iOS
    val iosArm64Target = iosArm64()
    val iosX64Target = iosX64()
    val iosSimArm64Target = iosSimulatorArm64()

    listOf(iosArm64Target, iosX64Target, iosSimArm64Target).forEach { iosTarget ->
        val libPath = when (iosTarget) {
            iosArm64Target -> libjamiLibPath
            else -> "${projectDir}/src/nativeInterop/cinterop/lib-sim"
        }
        iosTarget.binaries.framework {
            baseName = "JamiShared"
            isStatic = true
            if (enableJamiBridgeCinterop) {
                linkerOpts("-L$libPath", "-lc++")
            }
        }
        if (enableJamiBridgeCinterop) {
            val libName = when (iosTarget) {
                iosArm64Target -> "JamiBridge_ios"
                else -> "JamiBridge_iossim"
            }
            iosTarget.compilations.getByName("main") {
                cinterops {
                    create("JamiBridge") {
                        defFile(project.file("src/nativeInterop/cinterop/JamiBridge.def"))
                        includeDirs(jamiBridgePath)
                        extraOpts("-libraryPath", libPath)
                    }
                }
                kotlinOptions {
                    freeCompilerArgs = listOf("-linker-options", "-L$libPath -l$libName -lc++")
                }
            }
        }
    }

    // macOS
    listOf(
        macosX64(),
        macosArm64()
    ).forEach { macosTarget ->
        macosTarget.binaries.framework {
            baseName = "JamiShared"
            isStatic = true
            if (enableJamiBridgeCinterop) {
                linkerOpts("-L$libjamiLibPath", "-lJamiBridge_macos", "-ljami", "-lc++")
            }
        }
        if (enableJamiBridgeCinterop) {
            macosTarget.compilations.getByName("main") {
                cinterops {
                    create("JamiBridge") {
                        defFile(project.file("src/nativeInterop/cinterop/JamiBridge.def"))
                        includeDirs(jamiBridgePath)
                        extraOpts("-libraryPath", libjamiLibPath)
                    }
                }
                kotlinOptions {
                    freeCompilerArgs = listOf("-linker-options", "-L$libjamiLibPath -lJamiBridge_macos -ljami -lc++")
                }
            }
        }
    }

    // Desktop (JVM)
    jvm("desktop")

    // Web (JS)
    js {
        browser {
            webpackTask {
                mainOutputFileName = "jami-shared.js"
            }
        }
        binaries.executable()
    }

    // Apply default hierarchy template
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.atomicfu)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.okio)
                implementation(libs.ktor.client.core)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.navigation.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
                // Force espresso-core 3.6.1+ — 3.5.x crashes on Android 16 (API 36)
                // because InputManager.getInstance() was removed from the public API.
                implementation(libs.espresso.core)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)
                implementation(libs.sqldelight.android)
                implementation(libs.zxing.core)
                implementation(libs.camerax.core)
                implementation(libs.camerax.camera2)
                implementation(libs.camerax.lifecycle)
                implementation(libs.camerax.view)
                implementation(libs.androidx.core)
                implementation(libs.androidx.biometric)
                implementation(libs.osmdroid)
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.ui)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native)
            }
        }

        val macosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.jvm)
                implementation(libs.zxing.core)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

android {
    namespace = "net.jami.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Include SWIG-generated Java sources and native libraries
    sourceSets {
        getByName("main") {
            java.srcDirs("src/androidMain/java")
            jniLibs.srcDirs("src/androidMain/jniLibs")
        }
    }

    lint {
        checkDependencies = false
        checkReleaseBuilds = false
        abortOnError = false
        ignoreWarnings = true
        quiet = true
    }
}

sqldelight {
    databases {
        create("JamiDatabase") {
            packageName.set("net.jami.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

