package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val UTI_ITEM = "public.item"
private const val UTI_IMAGE = "public.image"
private const val UTI_MOVIE = "public.movie"

/**
 * UIDocumentPickerViewController only holds its delegate weakly, so the composable keeps this
 * instance alive via remember {} — same arrangement as [FileSaverEffect]'s delegate.
 */
private class PickDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onDone: ((NSURL?) -> Unit)? = null

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onDone?.invoke(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone?.invoke(null)
    }
}

/**
 * Delegate for the photo-library picker. Held alive by the composable for the same reason as
 * [PickDelegate].
 *
 * The chosen item arrives as an NSItemProvider, not a path: the file it
 * hands back exists only for the duration of the load callback, so it is copied into our tmp
 * directory there and the caller is answered on the main thread afterwards.
 */
private class PhotoPickDelegate : NSObject(), PHPickerViewControllerDelegateProtocol {
    var onDone: ((String?) -> Unit)? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        // Unlike the document picker, PHPicker never dismisses itself.
        picker.dismissViewControllerAnimated(true, null)

        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            finish(null)
            return
        }
        // A video has to be asked for as a movie; asking for public.image would either fail or
        // hand back a poster frame.
        val typeIdentifier = when {
            provider.hasItemConformingToTypeIdentifier(UTI_MOVIE) -> UTI_MOVIE
            provider.hasItemConformingToTypeIdentifier(UTI_IMAGE) -> UTI_IMAGE
            else -> provider.registeredTypeIdentifiers.firstOrNull() as? String
        }
        if (typeIdentifier == null) {
            finish(null)
            return
        }
        provider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, _ ->
            val path = url?.let { copyToTemp(it) }
            dispatch_async(dispatch_get_main_queue()) { finish(path) }
        }
    }

    private fun finish(path: String?) {
        val callback = onDone
        onDone = null
        callback?.invoke(path)
    }
}

/**
 * Resolve the caller's MIME filter to the content types UIDocumentPickerViewController wants.
 *
 * The trap this exists to avoid: `UTType.typeWithMIMEType` does **not** return null for a string
 * that is not a real MIME type. For a wildcard such as image, video or audio followed by
 * slash-star, or the bare any-type wildcard, it mints a *dynamic* type (`dyn.ah62d…`), and a
 * picker opened with one accepts nothing at all — every file in the browser is greyed out and
 * Cancel is the only way back. So wildcards are mapped to their UTI here instead of being handed
 * to the MIME lookup, an entry that is already a UTI (CertificatesScreen passes
 * "public.x509-certificate") is taken as one, and anything that still resolves to a dynamic type
 * is dropped rather than being allowed to blank out the picker.
 */
private fun contentTypesFor(mimeTypes: List<String>): List<UTType> {
    val resolved = mutableListOf<UTType>()
    for (raw in mimeTypes) {
        val mime = raw.trim().lowercase()
        if (mime.isEmpty()) continue
        val type = when {
            mime == "*" || mime == "*/*" -> UTType.typeWithIdentifier(UTI_ITEM)
            mime.endsWith("/*") -> when (mime.substringBefore('/')) {
                "image" -> UTType.typeWithIdentifier(UTI_IMAGE)
                "video" -> UTType.typeWithIdentifier(UTI_MOVIE)
                "audio" -> UTType.typeWithIdentifier("public.audio")
                "text" -> UTType.typeWithIdentifier("public.text")
                else -> UTType.typeWithIdentifier(UTI_ITEM)
            }
            // No slash: this is a UTI, not a MIME type.
            !mime.contains('/') -> UTType.typeWithIdentifier(raw)
            else -> UTType.typeWithMIMEType(raw)
        }
        val identifier = type?.identifier ?: continue
        if (identifier.startsWith("dyn.")) continue
        if (resolved.none { it.identifier == identifier }) resolved += type
    }
    // Nothing usable in the filter is "show me everything", never "show me nothing".
    if (resolved.isEmpty()) UTType.typeWithIdentifier(UTI_ITEM)?.let { resolved += it }
    return resolved
}

/**
 * True when the caller only wants photos or videos. Those live in the photo library, which the
 * Files browser cannot see, so a document picker would show the user everything *except* what
 * they came for. The photo picker is the right control for this filter.
 */
private fun isMediaOnly(mimeTypes: List<String>): Boolean {
    val cleaned = mimeTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    return cleaned.isNotEmpty() && cleaned.all { it.startsWith("image/") || it.startsWith("video/") }
}

/** The photo-library filter matching a media-only MIME request. */
private fun photoFilterFor(mimeTypes: List<String>): PHPickerFilter {
    val cleaned = mimeTypes.map { it.trim().lowercase() }
    val wantsImages = cleaned.any { it.startsWith("image/") }
    val wantsVideos = cleaned.any { it.startsWith("video/") }
    return when {
        wantsImages && wantsVideos -> PHPickerFilter.anyFilterMatchingSubfilters(
            listOf(PHPickerFilter.imagesFilter(), PHPickerFilter.videosFilter())
        )
        wantsVideos -> PHPickerFilter.videosFilter()
        else -> PHPickerFilter.imagesFilter()
    }
}

/** The view controller a sheet should be presented from: the topmost one currently on screen. */
private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) vc = vc.presentedViewController ?: return vc
}

/**
 * Copy the picked file into our own tmp directory so the caller gets a plain path it can read
 * for as long as it needs to, outside the picker's security-scoped access window.
 */
@OptIn(ExperimentalForeignApi::class)
private fun copyToTemp(url: NSURL): String? {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val sourcePath = url.path ?: return null
        val fileName = url.lastPathComponent ?: "picked_file"
        val stamp = Random.nextLong(0, Long.MAX_VALUE)
        val destination = "${NSTemporaryDirectory().removeSuffix("/")}/jami_picked_${stamp}_$fileName"
        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath(destination, error = null)
        return if (manager.copyItemAtPath(sourcePath, toPath = destination, error = null)) destination else null
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String>,
    onFilePicked: (path: String?) -> Unit,
) {
    val documentDelegate = remember { PickDelegate() }
    val photoDelegate = remember { PhotoPickDelegate() }
    val currentOnFilePicked by rememberUpdatedState(onFilePicked)

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        val presenter = topViewController()
        if (presenter == null) {
            currentOnFilePicked(null)
            return@LaunchedEffect
        }

        if (isMediaOnly(mimeTypes)) {
            photoDelegate.onDone = { path -> currentOnFilePicked(path) }
            // PHPickerConfiguration built without a photo library is the read-only variant: the
            // user picks out of process and the app never asks for library permission.
            val configuration = PHPickerConfiguration()
            configuration.selectionLimit = 1
            configuration.filter = photoFilterFor(mimeTypes)
            val picker = PHPickerViewController(configuration)
            picker.delegate = photoDelegate
            presenter.presentViewController(picker, animated = true, completion = null)
            return@LaunchedEffect
        }

        documentDelegate.onDone = { url ->
            documentDelegate.onDone = null
            currentOnFilePicked(url?.let { copyToTemp(it) })
        }
        // asCopy = true: iOS hands over a readable copy instead of a reference into another
        // app's container, so the path survives the picker being dismissed.
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = contentTypesFor(mimeTypes),
            asCopy = true,
        )
        picker.allowsMultipleSelection = false
        picker.delegate = documentDelegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}
