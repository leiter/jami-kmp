/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.utils.Log
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerCameraDevice
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerQualityTypeMedium
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private const val UTI_MOVIE = "public.movie"
private const val TAG = "VideoCapture"

/**
 * The picker holds its delegate weakly, so the composable keeps this alive with remember {},
 * as FilePickerEffect does with its own.
 */
private class VideoRecordDelegate : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    var onDone: ((String?) -> Unit)? = null

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
        // The recording lives in a directory the picker cleans up once it is dismissed.
        val path = url?.let(::keepRecording)
        picker.dismissViewControllerAnimated(true, null)
        finish(path)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        finish(null)
    }

    private fun finish(path: String?) {
        val callback = onDone
        onDone = null
        callback?.invoke(path)
    }
}

/** Moves the recording into our tmp directory under a name the recipient can read: video_20261125_143012.mov. */
@OptIn(ExperimentalForeignApi::class)
private fun keepRecording(url: NSURL): String? {
    val source = url.path ?: return null
    val stamp = NSDateFormatter().apply { dateFormat = "yyyyMMdd_HHmmss" }.stringFromDate(NSDate())
    val extension = url.pathExtension?.takeIf { it.isNotEmpty() } ?: "mov"
    val destination = "${NSTemporaryDirectory().removeSuffix("/")}/video_$stamp.$extension"
    val manager = NSFileManager.defaultManager
    manager.removeItemAtPath(destination, error = null)
    if (manager.moveItemAtPath(source, toPath = destination, error = null)) return destination
    return if (manager.copyItemAtPath(source, toPath = destination, error = null)) destination else null
}

private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) vc = vc.presentedViewController ?: return vc
}

private fun canRecordVideo(): Boolean {
    val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    if (!UIImagePickerController.isSourceTypeAvailable(camera)) return false
    return UIImagePickerController.availableMediaTypesForSourceType(camera)?.contains(UTI_MOVIE) == true
}

@Composable
actual fun VideoCaptureEffect(
    show: Boolean,
    onVideoCaptured: (path: String?) -> Unit,
    onUnavailable: () -> Unit,
) {
    val delegate = remember { VideoRecordDelegate() }
    val currentOnVideoCaptured by rememberUpdatedState(onVideoCaptured)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        if (!canRecordVideo()) {
            Log.w(TAG, "No camera that records video on this device")
            currentOnUnavailable()
            return@LaunchedEffect
        }
        val presenter = topViewController()
        if (presenter == null) {
            currentOnVideoCaptured(null)
            return@LaunchedEffect
        }
        delegate.onDone = { path -> currentOnVideoCaptured(path) }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.mediaTypes = listOf(UTI_MOVIE)
        // The capture mode can only be set once the media types allow it.
        picker.cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModeVideo
        val front = UIImagePickerControllerCameraDevice.UIImagePickerControllerCameraDeviceFront
        if (UIImagePickerController.isCameraDeviceAvailable(front)) picker.cameraDevice = front
        // The reference asks Android's recorder for its low quality; iOS's low is 144p, so the
        // middle setting keeps a message small without making it unwatchable.
        picker.videoQuality = UIImagePickerControllerQualityTypeMedium
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}
