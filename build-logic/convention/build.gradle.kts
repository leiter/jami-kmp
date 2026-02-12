plugins {
    `kotlin-dsl`
}

group = "net.jami.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.plugins.androidApplication.get().run { "$pluginId:$version" }.let { gradleApi() })
    compileOnly(libs.plugins.kotlinMultiplatform.get().run { "$pluginId:$version" }.let { gradleApi() })
}

gradlePlugin {
    plugins {
        // Convention plugins can be registered here
        // register("jamiKmpLibrary") {
        //     id = "jami.kmp.library"
        //     implementationClass = "JamiKmpLibraryConventionPlugin"
        // }
    }
}
