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
}
