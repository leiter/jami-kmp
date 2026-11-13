/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.services

import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.UIKit.UIDeviceOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Device orientation → capture orientation, as cached for reading off the main thread. */
class CaptureOrientationTest {

    @Test
    fun theFourHeldOrientationsMap() {
        assertEquals(AVCaptureVideoOrientationPortrait, captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationPortrait))
        assertEquals(AVCaptureVideoOrientationPortraitUpsideDown, captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown))
        // Mirrored between the two enums by Apple's definition.
        assertEquals(AVCaptureVideoOrientationLandscapeRight, captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationLandscapeLeft))
        assertEquals(AVCaptureVideoOrientationLandscapeLeft, captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationLandscapeRight))
    }

    @Test
    fun aPhoneLyingFlatKeepsThePreviousOrientation() {
        // null = keep the last value; this used to reset a landscape call to portrait.
        assertNull(captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationFaceUp))
        assertNull(captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationFaceDown))
        assertNull(captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationUnknown))
    }

    /**
     * Outgoing frames are captured in a fixed landscape-left orientation and carry the turn the
     * receiver applies. Values from jami-client-ios CameraOrientationState.outgoingAngle, with
     * its landscape rows swapped onto Apple's device→capture mapping (see outgoingFrameAngle).
     * A wrong entry shows the peer a sideways or upside-down image, which no check on this side
     * of the call would notice.
     */
    @Test
    fun outgoingFramesCarryTheNativeClientsRotation() {
        assertEquals(90, outgoingFrameAngle(AVCaptureVideoOrientationPortrait, isFront = false))
        assertEquals(270, outgoingFrameAngle(AVCaptureVideoOrientationPortrait, isFront = true))
        assertEquals(270, outgoingFrameAngle(AVCaptureVideoOrientationPortraitUpsideDown, isFront = false))
        assertEquals(90, outgoingFrameAngle(AVCaptureVideoOrientationPortraitUpsideDown, isFront = true))
    }

    @Test
    fun landscapeFollowsTheDeviceNotTheNativeClientsEnumMapping() {
        // Held device-landscape-right: the capture orientation itself, upright as captured.
        val heldRight = captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationLandscapeRight)!!
        assertEquals(0, outgoingFrameAngle(heldRight, isFront = false))
        assertEquals(0, outgoingFrameAngle(heldRight, isFront = true))
        // Held device-landscape-left: the native client's `.landscapeLeft -> 180` row.
        val heldLeft = captureOrientationFor(UIDeviceOrientation.UIDeviceOrientationLandscapeLeft)!!
        assertEquals(180, outgoingFrameAngle(heldLeft, isFront = false))
        assertEquals(180, outgoingFrameAngle(heldLeft, isFront = true))
    }
}
