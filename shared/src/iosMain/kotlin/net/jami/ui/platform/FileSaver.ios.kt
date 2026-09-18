package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/**
 * UIDocumentPickerViewController only holds its delegate weakly, so the composable keeps this
 * instance alive via remember {}.
 */
private class SaveDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onDone: ((picked: Boolean) -> Unit)? = null

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onDone?.invoke(didPickDocumentsAtURLs.isNotEmpty())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone?.invoke(false)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FileSaverEffect(
    sourcePath: String?,
    mimeType: String,
    deleteSource: Boolean,
    onResult: (FileSaveResult) -> Unit,
) {
    val delegate = remember { SaveDelegate() }
    val currentOnResult by rememberUpdatedState(onResult)

    LaunchedEffect(sourcePath) {
        val path = sourcePath ?: return@LaunchedEffect
        val source = NSURL.fileURLWithPath(path)
        val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (rootVc == null) {
            if (deleteSource) NSFileManager.defaultManager.removeItemAtURL(source, error = null)
            currentOnResult(FileSaveResult.FAILED)
            return@LaunchedEffect
        }
        delegate.onDone = { picked ->
            delegate.onDone = null
            // asCopy = true: the picker copied the file to the chosen location, so the temp
            // original can go either way.
            if (deleteSource) NSFileManager.defaultManager.removeItemAtURL(source, error = null)
            currentOnResult(if (picked) FileSaveResult.SAVED else FileSaveResult.CANCELLED)
        }
        // "Save to Files" — exports a copy of the temp file to a user-chosen location.
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(source), asCopy = true)
        picker.delegate = delegate
        val presenter = rootVc.presentedViewController ?: rootVc
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}
