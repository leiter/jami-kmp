package net.jami.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    // iOS file picker requires UIDocumentPickerViewController integration
    // Stub for now - will be implemented when iOS app wrapper is built
}
