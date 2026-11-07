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

/**
 * When the screen should blank because the phone is at the user's ear.
 *
 * Android holds a `PROXIMITY_SCREEN_OFF_WAKE_LOCK` for this and iOS turns on
 * `UIDevice.proximityMonitoringEnabled`; what they share is the decision, which belongs in one
 * place because the two actuals disagreeing is invisible until someone holds both phones.
 *
 * Kept as a function of its three inputs rather than reading settings itself, so it can be
 * tested exhaustively without a device, a call or a repository.
 */
object ProximityPolicy {

    /**
     * @param isAudioOnly no video in the call: with video the user is looking at the screen.
     * @param onSpeaker the phone is being held away from the ear.
     * @param settingEnabled `CallSettings.proximityEnabled`, the user's own choice.
     */
    fun shouldBlankScreen(
        isAudioOnly: Boolean,
        onSpeaker: Boolean,
        settingEnabled: Boolean,
    ): Boolean = isAudioOnly && !onSpeaker && settingEnabled
}
