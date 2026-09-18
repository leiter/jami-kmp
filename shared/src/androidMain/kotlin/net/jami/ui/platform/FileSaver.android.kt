package net.jami.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jami.utils.PendingActivityResultTracker
import java.io.File

@Composable
actual fun FileSaverEffect(
    sourcePath: String?,
    mimeType: String,
    onResult: (FileSaveResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSource by rememberUpdatedState(sourcePath)
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { uri ->
        // Clear first, unconditionally — see PendingActivityResultTracker's kdoc.
        PendingActivityResultTracker.end()
        val source = currentSource?.let { File(it) }
        if (source == null) {
            currentOnResult(FileSaveResult.FAILED)
            return@rememberLauncherForActivityResult
        }
        if (uri == null) {
            source.delete()
            currentOnResult(FileSaveResult.CANCELLED)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        source.inputStream().use { it.copyTo(output) }
                        true
                    } ?: false
                } catch (e: Exception) {
                    false
                } finally {
                    source.delete()
                }
            }
            currentOnResult(if (saved) FileSaveResult.SAVED else FileSaveResult.FAILED)
        }
    }

    LaunchedEffect(sourcePath) {
        val path = sourcePath ?: return@LaunchedEffect
        // Marked *before* launch so JamiNavigation's biometric-lock swap can't dispose this
        // composable — and this launcher with it — before CreateDocument's result arrives.
        PendingActivityResultTracker.begin()
        launcher.launch(File(path).name)
    }
}
