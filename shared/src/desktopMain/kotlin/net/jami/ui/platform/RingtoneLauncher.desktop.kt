package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

actual val hasRingtonePicker: Boolean = false

@Composable
actual fun RingtoneLauncherEffect(
    show: Boolean,
    currentUri: String,
    onResult: (uri: String?) -> Unit,
) {
    LaunchedEffect(show) {
        if (show) onResult(null)
    }
}
