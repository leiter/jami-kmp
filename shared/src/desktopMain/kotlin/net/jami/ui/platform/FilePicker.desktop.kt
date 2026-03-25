package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    LaunchedEffect(show) {
        if (show) {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Select file"
            chooser.fileFilter = FileNameExtensionFilter("Archive files", "gz", "zip", "tar")
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                onFilePicked(chooser.selectedFile.absolutePath)
            } else {
                onFilePicked(null)
            }
        }
    }
}
