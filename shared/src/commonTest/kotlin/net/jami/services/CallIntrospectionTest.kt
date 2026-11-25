/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import kotlinx.coroutines.test.runTest
import net.jami.ui.screens.callIntrospectionLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The debug screen's view of the daemon's calls and conferences (mini-plans calling F9). */
class CallIntrospectionTest {

    @Test
    fun introspectionReadsCallsAndConferencesFromTheDaemon() = runTest {
        val stub = StubDaemonBridge().apply {
            callList = listOf("call-1")
            callDetails["call-1"] = mapOf("CALL_STATE" to "CURRENT", "CONF_ID" to "conf-1")
            conferenceList = listOf("conf-1")
            conferenceInfos["conf-1"] = listOf(mapOf("uri" to "jami:peer", "active" to "true"))
        }
        val accountService = net.jami.viewmodel.makeAccountService(stub, this)
        val callService = net.jami.viewmodel.makeCallService(stub, accountService, scope = this)

        val snapshot = callService.introspect("acc")

        assertEquals(listOf(DaemonCallInfo("call-1", mapOf("CALL_STATE" to "CURRENT", "CONF_ID" to "conf-1"))), snapshot.calls)
        assertEquals("conf-1", snapshot.conferences.single().confId)
        assertEquals("true", snapshot.conferences.single().participants.single()["active"])
    }

    @Test
    fun noCallsMeansNoLines() {
        assertTrue(callIntrospectionLines(CallIntrospection()).isEmpty())
    }

    @Test
    fun linesShowStateFlagsAndConference() {
        val lines = callIntrospectionLines(
            CallIntrospection(
                calls = listOf(
                    DaemonCallInfo(
                        "0123456789abcdef",
                        mapOf(
                            "CALL_STATE" to "CURRENT",
                            "PEER_NUMBER" to "jami:fedcba9876543210",
                            "VIDEO_MUTED" to "true",
                            "AUDIO_CODEC" to "opus",
                            "CONF_ID" to "conference-id-xyz",
                        ),
                    ),
                ),
                conferences = listOf(
                    DaemonConferenceInfo(
                        "conference-id-xyz",
                        listOf(
                            mapOf("uri" to "", "active" to "true", "isModerator" to "true"),
                            mapOf("uri" to "jami:fedcba9876543210", "device" to "dev12345678", "videoMuted" to "true"),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                "  calls: 1",
                "    0123456789ab CURRENT fedcba987654 · video muted · opus · conf conference-i",
                "  conferences: 1",
                "    conference-i: 2 stream(s)",
                "      local · active, moderator",
                "      fedcba987654 /dev12345 · video muted",
            ),
            lines,
        )
    }
}
