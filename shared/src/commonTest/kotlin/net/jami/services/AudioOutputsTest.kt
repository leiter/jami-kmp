/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import net.jami.services.expect.AudioOutput
import net.jami.services.expect.AudioOutputType.BLUETOOTH
import net.jami.services.expect.AudioOutputType.INTERNAL
import net.jami.services.expect.AudioOutputType.SPEAKERS
import net.jami.services.expect.AudioOutputType.WIRED
import net.jami.services.expect.AudioState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The call screen's audio-route rules, mirrored from jami-client-android's CallFragment (S8). */
class AudioOutputsTest {
    private val earpiece = AudioOutput(INTERNAL)
    private val speaker = AudioOutput(SPEAKERS)
    private val buds = AudioOutput(BLUETOOTH, "Buds", "bt-1")
    private val car = AudioOutput(BLUETOOTH, "Car", "bt-2")

    @Test
    fun theEarpieceIsNotOfferedDuringVideo() {
        val state = AudioState(speaker, listOf(earpiece, speaker, buds))
        assertEquals(listOf(earpiece, speaker, buds), AudioOutputs.pickable(state, hasVideo = false))
        assertEquals(listOf(speaker, buds), AudioOutputs.pickable(state, hasVideo = true))
    }

    @Test
    fun aStateWithoutAListOffersItsOwnRoute() {
        assertEquals(listOf(speaker), AudioOutputs.pickable(AudioState(speaker), hasVideo = false))
    }

    @Test
    fun onlyAHeadsetOrBluetoothCallsForThePicker() {
        assertFalse(AudioOutputs.hasExternalDevice(listOf(earpiece, speaker)))
        assertTrue(AudioOutputs.hasExternalDevice(listOf(earpiece, speaker, buds)))
        assertTrue(AudioOutputs.hasExternalDevice(listOf(earpiece, AudioOutput(WIRED))))
    }

    @Test
    fun theSimpleToggleSwapsEarpieceAndSpeaker() {
        val outputs = listOf(earpiece, speaker)
        assertEquals(speaker, AudioOutputs.nextSimple(earpiece, outputs))
        assertEquals(earpiece, AudioOutputs.nextSimple(speaker, outputs))
    }

    @Test
    fun withoutAnEarpieceTheToggleHasNowhereElseToGo() {
        assertNull(AudioOutputs.nextSimple(speaker, listOf(speaker)))
    }

    @Test
    fun twoBluetoothDevicesAreToldApartById() {
        assertTrue(AudioOutputs.isSame(buds, buds.copy(outputName = null)))
        assertFalse(AudioOutputs.isSame(buds, car))
        // Without ids on both sides only the type counts.
        assertTrue(AudioOutputs.isSame(AudioOutput(SPEAKERS, outputId = "x"), speaker))
        assertFalse(AudioOutputs.isSame(earpiece, null))
    }
}
