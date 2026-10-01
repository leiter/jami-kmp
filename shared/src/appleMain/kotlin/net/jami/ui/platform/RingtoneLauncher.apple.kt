package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun RingtoneLauncherEffect(
    show: Boolean,
    currentUri: String,
    onResult: (uri: String?) -> Unit,
) {
    // iOS does not have a system ringtone picker API accessible from Kotlin/Native.
    LaunchedEffect(show) {
        if (show) onResult(null)
    }
}
