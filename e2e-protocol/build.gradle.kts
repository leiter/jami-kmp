plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // api so consumers (runner + Android agent) get the serialization runtime for HarnessJson
    api(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(17)
}
