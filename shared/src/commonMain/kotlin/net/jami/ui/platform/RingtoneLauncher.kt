package net.jami.ui.platform

import androidx.compose.runtime.Composable

/**
 * Launches the platform ringtone picker when [show] becomes true.
 * [currentUri] is passed as the pre-selected ringtone (empty = system default).
 * [onResult] receives the chosen URI string, or null if cancelled / not supported.
 */
@Composable
expect fun RingtoneLauncherEffect(
    show: Boolean,
    currentUri: String,
    onResult: (uri: String?) -> Unit,
)
