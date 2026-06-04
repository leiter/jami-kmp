package net.jami.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Desktop stub — video rendering not yet implemented on Desktop/JVM.
@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Video ($callId)")
    }
}
