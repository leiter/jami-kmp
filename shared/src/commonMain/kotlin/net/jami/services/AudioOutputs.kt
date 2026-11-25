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
import net.jami.services.expect.AudioOutputType
import net.jami.services.expect.AudioState

/**
 * The call screen's audio-output rules, as jami-client-android's `CallFragment` applies them
 * (`availableAudioOutputs`, `hasExternalAudioDevice`, `nextSimpleAudioOutput`,
 * `isCurrentAudioOutput`).
 *
 * With only the earpiece and the speaker the button is a plain toggle; once a headset or a
 * Bluetooth device is attached a single tap can no longer say where the audio should go, so
 * the button opens a picker instead.
 */
object AudioOutputs {

    /** The routes worth offering. The earpiece is left out during video: nobody holds a video call to their ear. */
    fun pickable(state: AudioState, hasVideo: Boolean): List<AudioOutput> {
        val outputs = state.availableOutputs.ifEmpty { listOf(state.output) }
        return outputs.filter { !hasVideo || it.type != AudioOutputType.INTERNAL }
    }

    fun hasExternalDevice(outputs: List<AudioOutput>): Boolean =
        outputs.any { it.type == AudioOutputType.WIRED || it.type == AudioOutputType.BLUETOOTH }

    /** Speaker ⇄ earpiece, the toggle the button stays while nothing else is attached. */
    fun nextSimple(current: AudioOutput, outputs: List<AudioOutput>): AudioOutput? {
        val speaker = outputs.firstOrNull { it.type == AudioOutputType.SPEAKERS }
        val internal = outputs.firstOrNull { it.type == AudioOutputType.INTERNAL }
        return if (isSame(current, speaker)) {
            internal ?: outputs.firstOrNull { !isSame(current, it) }
        } else {
            speaker ?: outputs.firstOrNull { !isSame(current, it) }
        }
    }

    /** Matched by device id when both sides have one (two Bluetooth headsets), else by type. */
    fun isSame(current: AudioOutput, candidate: AudioOutput?): Boolean {
        if (candidate == null) return false
        return if (current.outputId != null && candidate.outputId != null) {
            current.outputId == candidate.outputId
        } else {
            current.type == candidate.type
        }
    }
}
