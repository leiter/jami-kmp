/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.utils.Log
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreVideo.CVImageBufferRef
import platform.ReplayKit.RPSampleBufferTypeVideo
import platform.ReplayKit.RPScreenRecorder

/**
 * Screen sharing on iOS (I9): ReplayKit's in-app capture, `RPScreenRecorder.startCapture`.
 *
 * It captures Jami's own screen, and only while Jami is in front; iOS pauses it in the
 * background. Sharing the whole device, other apps included, takes a Broadcast Upload
 * Extension: a separate process that would have to hand every frame to the app's daemon
 * through the App Group. jami-client-ios shares no screen at all.
 *
 * iOS asks the user once per app launch before the first capture starts.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSScreenCapture(private val onFrame: (CVImageBufferRef) -> Unit) {
    private val tag = "IOSScreenCapture"
    private val recorder = RPScreenRecorder.sharedRecorder()

    val isAvailable: Boolean get() = recorder.available

    val isCapturing: Boolean get() = recorder.recording

    /** Starts capturing; [onResult] gets true once frames flow, false if refused or failed. */
    fun start(onResult: (Boolean) -> Unit) {
        if (recorder.recording) {
            onResult(true)
            return
        }
        recorder.microphoneEnabled = false
        recorder.startCaptureWithHandler(
            captureHandler = { sampleBuffer, type, error ->
                if (error == null && type == RPSampleBufferTypeVideo && sampleBuffer != null) {
                    CMSampleBufferGetImageBuffer(sampleBuffer)?.let(onFrame)
                }
            },
            completionHandler = { error ->
                if (error != null) Log.w(tag, "Screen capture refused or failed: ${error.localizedDescription}")
                else Log.d(tag, "Screen capture started")
                onResult(error == null)
            },
        )
    }

    fun stop() {
        if (!recorder.recording) return
        recorder.stopCaptureWithHandler { error ->
            if (error != null) Log.w(tag, "Stopping screen capture: ${error.localizedDescription}")
            else Log.d(tag, "Screen capture stopped")
        }
    }
}
