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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Eight inputs, so all eight are stated rather than sampled. */
class ProximityPolicyTest {

    @Test
    fun anEarpieceAudioCallWithTheSettingOnBlanksTheScreen() {
        assertTrue(ProximityPolicy.shouldBlankScreen(true, onSpeaker = false, settingEnabled = true))
    }

    /** The regression this exists for: the setting was stored and read by nothing on iOS. */
    @Test
    fun theSettingIsObeyedWhenItIsOff() {
        assertFalse(ProximityPolicy.shouldBlankScreen(true, onSpeaker = false, settingEnabled = false))
    }

    @Test
    fun speakerphoneMeansThePhoneIsNotAtTheEar() {
        assertFalse(ProximityPolicy.shouldBlankScreen(true, onSpeaker = true, settingEnabled = true))
        assertFalse(ProximityPolicy.shouldBlankScreen(true, onSpeaker = true, settingEnabled = false))
    }

    /** A video call never blanks: blanking the picture the user is watching is a bug report. */
    @Test
    fun aVideoCallNeverBlanks() {
        assertFalse(ProximityPolicy.shouldBlankScreen(false, onSpeaker = false, settingEnabled = true))
        assertFalse(ProximityPolicy.shouldBlankScreen(false, onSpeaker = true, settingEnabled = true))
        assertFalse(ProximityPolicy.shouldBlankScreen(false, onSpeaker = false, settingEnabled = false))
        assertFalse(ProximityPolicy.shouldBlankScreen(false, onSpeaker = true, settingEnabled = false))
    }
}
