plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Set to true when libjami headers are available at the configured path
val enableCinterop = false
val libjamiHeadersPath = "/Users/user289697/Documents/JAMI/jami-daemon/src"

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

    // Include SWIG-generated Java sources
    sourceSets {
        getByName("main") {
            java.srcDirs("src/androidMain/java")
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
