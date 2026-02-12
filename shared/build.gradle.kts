plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Set to true when Objective-C++ adapters are built as a framework
// Note: The libjami C++ headers cannot be used directly - cinterop only supports C APIs
// iOS/macOS integration requires Objective-C++ adapters from jami-client-ios
val enableCinterop = false
val libjamiHeadersPath = "${projectDir}/src/nativeInterop/cinterop/headers"
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
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "JamiShared"
            isStatic = true
        }
        if (enableCinterop) {
            iosTarget.compilations.getByName("main") {
                cinterops {
                    create("libjami") {
                        defFile(project.file("src/nativeInterop/cinterop/libjami.def"))
                        includeDirs(libjamiHeadersPath)
                    }
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
        }
        if (enableCinterop) {
            macosTarget.compilations.getByName("main") {
                cinterops {
                    create("libjami") {
                        defFile(project.file("src/nativeInterop/cinterop/libjami.def"))
                        includeDirs(libjamiHeadersPath)
                    }
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
