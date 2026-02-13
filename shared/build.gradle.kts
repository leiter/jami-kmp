plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
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
        iosTarget.binaries.framework {
            baseName = "JamiShared"
            isStatic = true
            if (enableJamiBridgeCinterop) {
                linkerOpts("-L$libjamiLibPath", "-lc++")
            }
        }
        if (enableJamiBridgeCinterop) {
            val libName = when (iosTarget) {
                iosArm64Target -> "JamiBridge_ios"
                else -> "JamiBridge_iossim"  // x64 and simulatorArm64 use simulator lib
            }
            iosTarget.compilations.getByName("main") {
                cinterops {
                    create("JamiBridge") {
                        defFile(project.file("src/nativeInterop/cinterop/JamiBridge.def"))
                        includeDirs(jamiBridgePath)
                        extraOpts("-libraryPath", libjamiLibPath)
                    }
                }
                kotlinOptions {
                    freeCompilerArgs = listOf("-linker-options", "-L$libjamiLibPath -l$libName -ljami -lc++")
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
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.koin.core)
                implementation(libs.okio)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)
                implementation(libs.sqldelight.android)
                implementation(libs.zxing.core)
                implementation(libs.androidx.core)
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
