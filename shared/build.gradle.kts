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

// macOS links against its own set of libraries. lib/ holds iOS-device arm64
// slices, so pointing the macOS target at it produced "building for macOS but
// linking against a file built for iOS" errors rather than a usable framework.
// Populate this with scripts/make_macos_links.py.
//
// Per-architecture, because the daemon's contrib tree is built for one host
// architecture at a time: an arm64 contrib build cannot satisfy macosX64. Only
// lib-macos/ (arm64) is populated on an Apple-silicon machine, so macosX64
// compiles but does not link — which is the honest outcome, and a clearer
// failure than feeding ld libraries of the wrong architecture.
val libjamiMacosLibPath = "${projectDir}/src/nativeInterop/cinterop/lib-macos"
val libjamiMacosX64LibPath = "${projectDir}/src/nativeInterop/cinterop/lib-macos-x64"

/**
 * Derives the macOS `-l` flags from whatever is actually in lib-macos/.
 *
 * The iOS device side hardcodes its library list in two places (here and
 * OTHER_LDFLAGS in project.pbxproj) and goes stale every time the daemon
 * submodule moves and its contrib dependency set changes — a link error, never
 * a compile error. Discovering the list avoids repeating that on macOS.
 *
 * libjami and the bridge wrapper come first: they are the archives that
 * reference symbols in everything else, and ld resolves archives in order.
 * Returns an empty string when lib-macos/ has not been populated, so that a
 * checkout without the macOS libraries still configures (and fails at link
 * time with a clear undefined-symbol error) instead of breaking the build for
 * Android-only work.
 */
fun macosLinkerLibs(libPath: String): String {
    val dir = file(libPath)
    if (!dir.isDirectory) return ""
    val names = dir.listFiles()
        ?.filter { it.name.startsWith("lib") && it.name.endsWith(".a") }
        ?.map { it.name.removePrefix("lib").removeSuffix(".a") }
        ?.sorted()
        ?: return ""
    val first = listOf("jami", "JamiBridge_macos").filter { it in names }
    return (first + (names - first.toSet())).joinToString(" ") { "-l$it" }
}

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
    val macosX64Target = macosX64()
    val macosArm64Target = macosArm64()

    listOf(macosX64Target, macosArm64Target).forEach { macosTarget ->
        val macLibPath = when (macosTarget) {
            macosArm64Target -> libjamiMacosLibPath
            else -> libjamiMacosX64LibPath
        }
        macosTarget.binaries.framework {
            baseName = "JamiShared"
            isStatic = true
            if (enableJamiBridgeCinterop) {
                linkerOpts(
                    listOf("-L$macLibPath") +
                        macosLinkerLibs(macLibPath).split(" ").filter { it.isNotEmpty() } +
                        "-lc++"
                )
            }
        }
        if (enableJamiBridgeCinterop) {
            macosTarget.compilations.getByName("main") {
                cinterops {
                    create("JamiBridge") {
                        defFile(project.file("src/nativeInterop/cinterop/JamiBridge.def"))
                        includeDirs(jamiBridgePath)
                        extraOpts("-libraryPath", macLibPath)
                    }
                }
                kotlinOptions {
                    freeCompilerArgs = listOf(
                        "-linker-options",
                        "-L$macLibPath ${macosLinkerLibs(macLibPath)} -lc++",
                    )
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

    // JVM unit tests run against android.jar stubs: return defaults instead of throwing, so
    // common tests can construct Android service actuals (e.g. HardwareService's placeholder Context).
    testOptions {
        unitTests.isReturnDefaultValues = true
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

// Android JVM unit tests run the common tests against the Android actuals, but without an Android
// runtime (no Robolectric): these classes exercise Android platform services that need Koin-backed
// SharedPreferences (LocalPrefs), AudioManager or MediaProjection and cannot run there. They run on
// the desktop target (`:shared:desktopTest`), where those services are plain implementations.
tasks.withType<Test>().matching { it.name.endsWith("UnitTest") }.configureEach {
    filter {
        excludeTestsMatching("net.jami.services.HardwareServiceTest")
        excludeTestsMatching("net.jami.viewmodel.AppSettingsViewModelTest")
        excludeTestsMatching("net.jami.viewmodel.AppViewModelTest")
        excludeTestsMatching("net.jami.viewmodel.CallViewModelTest")
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

