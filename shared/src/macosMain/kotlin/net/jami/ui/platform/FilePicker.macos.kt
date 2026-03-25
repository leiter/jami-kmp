package net.jami.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    // macOS file picker requires NSOpenPanel integration
    // Stub for now - will be implemented when macOS app wrapper is built
}
