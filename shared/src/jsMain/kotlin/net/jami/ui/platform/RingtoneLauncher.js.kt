package net.jami.ui.platform

import androidx.compose.runtime.Composable

actual val hasRingtonePicker: Boolean = false

@Composable
actual fun RingtoneLauncherEffect(
    show: Boolean,
    currentUri: String,
    onResult: (uri: String?) -> Unit,
) {
    // Web platform has no ringtone picker.
}
