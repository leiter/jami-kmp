package net.jami.ui.platform

import androidx.compose.runtime.Composable

/**
 * Whether this platform has a ringtone picker for [RingtoneLauncherEffect] to open.
 *
 * Only Android does. Elsewhere the effect answers null at once, so a ringtone row would be a
 * setting that does nothing; settings screens hide it instead. On iOS CallKit plays the
 * ringtone, and the reference client (jami-client-ios) offers no picker either.
 */
expect val hasRingtonePicker: Boolean

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
