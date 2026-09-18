package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun FileSaverEffect(
    sourcePath: String?,
    mimeType: String,
    deleteSource: Boolean,
    onResult: (FileSaveResult) -> Unit,
) {
    LaunchedEffect(sourcePath) {
        val source = sourcePath?.let { File(it) } ?: return@LaunchedEffect
        val chooser = JFileChooser()
        chooser.dialogTitle = "Save file"
        chooser.selectedFile = File(source.name)
        val result = if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                source.copyTo(chooser.selectedFile, overwrite = true)
                FileSaveResult.SAVED
            } catch (e: Exception) {
                FileSaveResult.FAILED
            }
        } else {
            FileSaveResult.CANCELLED
        }
        if (deleteSource) source.delete()
        onResult(result)
    }
}
