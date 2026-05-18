package net.jami.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
)
