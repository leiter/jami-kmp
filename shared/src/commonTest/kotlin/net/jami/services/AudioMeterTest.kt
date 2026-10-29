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

import kotlinx.coroutines.test.runTest
import net.jami.viewmodel.makeAccountService
import net.jami.viewmodel.makeCallService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Microphone level metering (mini-plan notifications F9). The daemon only emits AudioMeter while
 * metering is switched on for a ring buffer, and it computes RMS on every captured frame while it
 * is, so switching it off again matters as much as switching it on.
 */
class AudioMeterTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope): Pair<StubDaemonBridge, CallService> {
        val stub = StubDaemonBridge()
        return stub to makeCallService(stub, makeAccountService(stub, scope), scope = scope)
    }

    @Test
    fun meteringIsOffUntilSomethingAsksForIt() = runTest {
        val (stub, calls) = env(this)
        assertEquals(0f, calls.localAudioLevel.value)
        assertTrue(stub.audioMeterStates.isEmpty(), "the daemon is not told anything on its own")
    }

    @Test
    fun enablingMetersTheCaptureBuffer() = runTest {
        val (stub, calls) = env(this)
        calls.setMicrophoneMeterEnabled(true)
        assertEquals(true, stub.audioMeterStates[AudioMeter.CAPTURE_ID])
    }

    @Test
    fun capturedLevelsReachTheFlow() = runTest {
        val (_, calls) = env(this)
        calls.setMicrophoneMeterEnabled(true)
        calls.onAudioMeter(AudioMeter.CAPTURE_ID, 0.42f)
        assertEquals(0.42f, calls.localAudioLevel.value)
    }

    /** Every metered buffer reports under its own id; a call's stream is not the microphone. */
    @Test
    fun otherBuffersAreIgnored() = runTest {
        val (_, calls) = env(this)
        calls.setMicrophoneMeterEnabled(true)
        calls.onAudioMeter("call-1", 0.9f)
        assertEquals(0f, calls.localAudioLevel.value)
    }

    @Test
    fun levelsAreClampedToTheDocumentedRange() = runTest {
        val (_, calls) = env(this)
        calls.onAudioMeter(AudioMeter.CAPTURE_ID, 4f)
        assertEquals(1f, calls.localAudioLevel.value)
        calls.onAudioMeter(AudioMeter.CAPTURE_ID, -1f)
        assertEquals(0f, calls.localAudioLevel.value)
    }

    @Test
    fun disablingStopsTheDaemonAndClearsTheLastLevel() = runTest {
        val (stub, calls) = env(this)
        calls.setMicrophoneMeterEnabled(true)
        calls.onAudioMeter(AudioMeter.CAPTURE_ID, 0.5f)

        calls.setMicrophoneMeterEnabled(false)

        assertEquals(false, stub.audioMeterStates[AudioMeter.CAPTURE_ID])
        assertEquals(0f, calls.localAudioLevel.value, "a stale bar must not be left on screen")
    }

    /** Leaving and re-entering the call screen must not leave the daemon metering forever. */
    @Test
    fun repeatedEnablesDoNotStack() = runTest {
        val (stub, calls) = env(this)
        calls.setMicrophoneMeterEnabled(true)
        calls.setMicrophoneMeterEnabled(true)
        calls.setMicrophoneMeterEnabled(false)
        assertEquals(false, stub.audioMeterStates[AudioMeter.CAPTURE_ID])
    }
}
