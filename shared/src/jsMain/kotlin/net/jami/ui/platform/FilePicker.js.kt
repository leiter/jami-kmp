package net.jami.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    // Web file picker requires HTML <input type="file"> integration
    // Stub for now - will be implemented when web app wrapper is built
}
