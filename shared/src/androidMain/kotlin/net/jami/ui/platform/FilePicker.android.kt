package net.jami.ui.platform

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            onFilePicked(null)
            return@rememberLauncherForActivityResult
        }
        // Copy content URI to cache dir so we have a real file path
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val fileName = "import_${System.currentTimeMillis()}.gz"
                val cacheFile = File(context.cacheDir, fileName)
                cacheFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                onFilePicked(cacheFile.absolutePath)
            } else {
                onFilePicked(null)
            }
        } catch (e: Exception) {
            onFilePicked(null)
        }
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(mimeTypes.toTypedArray())
        }
    }
}
