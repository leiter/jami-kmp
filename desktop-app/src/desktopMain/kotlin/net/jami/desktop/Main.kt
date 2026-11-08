package net.jami.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.jami.di.initKoin
import net.jami.ui.JamiApp
import net.jami.ui.platform.AppLanguage

fun main() = application {
    // Before anything resolves a resource: on the JVM the app language is the default Locale,
    // and Compose reads it once per composition. A stored choice has to be in place first.
    AppLanguage.applyStored()

    // Initialize Koin dependency injection
    initKoin()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Jami"
    ) {
        JamiApp()
    }
}
