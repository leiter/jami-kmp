package net.jami.ui.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File

// Resolves the picked document's real display name (e.g. "photo.png") via the
// OpenableColumns.DISPLAY_NAME content-resolver query that ACTION_OPEN_DOCUMENT
// providers are required to support. Falls back to a generic name with an extension
// guessed from the URI's MIME type if the provider doesn't supply one, rather than
// hardcoding ".gz" for every file regardless of type — the .gz name is only correct
// for the account-archive import flow, but this composable is shared by chat file
// sending, avatar pickers, and TLS cert pickers, all of which need a real name/extension.
private fun resolveFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)?.let { return it }
            }
        }
    val extension = context.contentResolver.getType(uri)
        ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return "file_${System.currentTimeMillis()}" + (extension?.let { ".$it" } ?: "")
}

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
                val fileName = resolveFileName(context, uri)
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
