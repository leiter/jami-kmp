pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jami-kmp"

include(":shared")
include(":android-app")
include(":desktop-app")
include(":web-app")

// End-to-end device test harness (out-of-band coordination; see doc/end2endTesting.md)
include(":e2e-protocol")
include(":e2e-runner")
