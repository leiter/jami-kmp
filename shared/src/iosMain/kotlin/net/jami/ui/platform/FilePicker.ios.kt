package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.bridge.JamiBridgeWrapper

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    LaunchedEffect(show) {
        if (show) {
            JamiBridgeWrapper.shared().presentDocumentPickerWithMimeTypes(mimeTypes) { path ->
                onFilePicked(path)
            }
        }
    }
}
