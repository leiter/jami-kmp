package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSSavePanel
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FileSaverEffect(
    sourcePath: String?,
    mimeType: String,
    deleteSource: Boolean,
    onResult: (FileSaveResult) -> Unit,
) {
    LaunchedEffect(sourcePath) {
        val path = sourcePath ?: return@LaunchedEffect
        val fileManager = NSFileManager.defaultManager
        val source = NSURL.fileURLWithPath(path)
        val panel = NSSavePanel.savePanel()
        panel.nameFieldStringValue = source.lastPathComponent ?: "export"
        val result = if (panel.runModal() == NSModalResponseOK) {
            val target = panel.URL
            if (target != null) {
                // NSFileManager won't overwrite; the panel already confirmed replacing.
                fileManager.removeItemAtURL(target, error = null)
                if (fileManager.copyItemAtURL(source, toURL = target, error = null)) {
                    FileSaveResult.SAVED
                } else {
                    FileSaveResult.FAILED
                }
            } else {
                FileSaveResult.FAILED
            }
        } else {
            FileSaveResult.CANCELLED
        }
        if (deleteSource) fileManager.removeItemAtURL(source, error = null)
        onResult(result)
    }
}
