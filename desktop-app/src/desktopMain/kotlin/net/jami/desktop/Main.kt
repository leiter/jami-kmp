package net.jami.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.jami.di.initKoin
import net.jami.ui.JamiApp

fun main() = application {
    // Initialize Koin dependency injection
    initKoin()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Jami"
    ) {
        JamiApp()
    }
}
