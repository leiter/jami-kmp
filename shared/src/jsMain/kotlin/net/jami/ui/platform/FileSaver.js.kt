package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun FileSaverEffect(
    sourcePath: String?,
    mimeType: String,
    onResult: (FileSaveResult) -> Unit,
) {
    // Web "Save as" requires a browser download integration
    // Stub for now - will be implemented when web app wrapper is built
    LaunchedEffect(sourcePath) {
        if (sourcePath != null) onResult(FileSaveResult.FAILED)
    }
}
