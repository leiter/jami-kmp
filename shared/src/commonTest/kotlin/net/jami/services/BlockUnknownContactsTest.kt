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

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.viewmodel.TEST_ACCOUNT_ID
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.prepareAccountInService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "Block unknown contacts" privacy setting, labelled "Only accept messages from your
 * contacts". It was stored and read back by the settings screen and consumed nowhere else, so
 * the app made a privacy promise it did not keep (`doc/manual-test-plan-2026-09-10.md` B14).
 *
 * Two surfaces enforce it: an incoming call from a stranger is refused in [CallService], and a
 * conversation request from a stranger never reaches the pending list — neither when it arrives
 * nor when the daemon is polled on the next launch.
 */
class BlockUnknownContactsTest {

    // ==================== Incoming calls ====================

    @Test
    fun aStrangersCallIsRefused() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.callService.onIncomingCall(TEST_ACCOUNT_ID, CALL_ID, STRANGER, emptyList())
        advanceUntilIdle()

        assertEquals(
            listOf(TEST_ACCOUNT_ID to CALL_ID),
            stack.stub.refusedCalls,
            "the caller must be told, not left listening to ringback until the daemon times out"
        )
        assertNull(
            stack.callService.getCall(CALL_ID),
            "a refused call must not be registered — callUpdates feeds the notification, the " +
                "call UI, Telecom and CallKit"
        )
        assertTrue(stack.callService.currentCalls.value.isEmpty())
    }

    @Test
    fun aContactsCallGoesThroughWithTheSettingOn() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.contacts[TEST_ACCOUNT_ID] = listOf(confirmedContact(PEER_ID))
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.callService.onIncomingCall(TEST_ACCOUNT_ID, CALL_ID, "jami:$PEER_ID", emptyList())
        advanceUntilIdle()

        assertTrue(stack.stub.refusedCalls.isEmpty())
        assertNotNull(stack.callService.getCall(CALL_ID))
    }

    /**
     * The identifier key is read as "id" with "uri" accepted as a fallback, so a daemon build
     * that supplies the other spelling is not treated as a list of strangers. Pinned because
     * getting this key wrong is silent: it does not fail, it refuses every caller.
     */
    @Test
    fun aContactsCallGoesThroughWhenTheDaemonKeysTheContactByUri() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.contacts[TEST_ACCOUNT_ID] = listOf(
            mapOf("uri" to PEER_ID, "confirmed" to "true", "banned" to "false")
        )
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.callService.onIncomingCall(TEST_ACCOUNT_ID, CALL_ID, "jami:$PEER_ID", emptyList())
        advanceUntilIdle()

        assertTrue(stack.stub.refusedCalls.isEmpty())
        assertNotNull(stack.callService.getCall(CALL_ID))
    }

    @Test
    fun aStrangersCallGoesThroughWithTheSettingOff() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)

        stack.callService.onIncomingCall(TEST_ACCOUNT_ID, CALL_ID, STRANGER, emptyList())
        advanceUntilIdle()

        assertTrue(stack.stub.refusedCalls.isEmpty())
        assertNotNull(stack.callService.getCall(CALL_ID))
    }

    /** A banned peer is still *listed* by the daemon, with `banned=true`. It is not a contact. */
    @Test
    fun aBannedContactsCallIsRefused() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.contacts[TEST_ACCOUNT_ID] = listOf(
            confirmedContact(PEER_ID) + mapOf("banned" to "true")
        )
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.callService.onIncomingCall(TEST_ACCOUNT_ID, CALL_ID, "jami:$PEER_ID", emptyList())
        advanceUntilIdle()

        assertEquals(listOf(TEST_ACCOUNT_ID to CALL_ID), stack.stub.refusedCalls)
        assertNull(stack.callService.getCall(CALL_ID))
    }

    // ==================== Conversation requests ====================

    @Test
    fun aStrangersRequestNeverReachesThePendingList() = runTest {
        val stack = makeTestServiceStack(scope = this)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to STRANGER_ID)
        )
        advanceUntilIdle()

        assertTrue(account.getPending().isEmpty())
    }

    @Test
    fun aContactsRequestStillReachesThePendingList() = runTest {
        val stack = makeTestServiceStack(scope = this)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.contacts[TEST_ACCOUNT_ID] = listOf(confirmedContact(PEER_ID))
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()

        assertEquals(1, account.getPending().size)
    }

    /**
     * The request screens also poll the daemon, to catch requests that arrived before the app
     * started. Without this filter the setting would only hold until the next launch.
     */
    @Test
    fun theDaemonPollIsFilteredToo() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.contacts[TEST_ACCOUNT_ID] = listOf(confirmedContact(PEER_ID))
        stack.stub.conversationRequests += mapOf("id" to CONVERSATION_ID, "from" to STRANGER_ID)
        stack.stub.conversationRequests += mapOf("id" to "conv_from_contact", "from" to PEER_ID)

        assertEquals(
            2,
            stack.conversationFacade.getConversationRequests(TEST_ACCOUNT_ID).size,
            "with the setting off, nothing is filtered"
        )

        stack.settingsRepository.updateBlockUnknownContacts(true)

        val filtered = stack.conversationFacade.getConversationRequests(TEST_ACCOUNT_ID)
        assertEquals(listOf("conv_from_contact"), filtered.map { it["id"] })
    }

    /** No sender means no way to establish that it came from a contact, so it is withheld. */
    @Test
    fun aRequestWithNoSenderIsWithheldWhileBlocking() = runTest {
        val stack = makeTestServiceStack(scope = this)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.conversationRequests += mapOf("id" to CONVERSATION_ID)
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, emptyMap()
        )
        advanceUntilIdle()

        assertTrue(account.getPending().isEmpty())
        assertTrue(stack.conversationFacade.getConversationRequests(TEST_ACCOUNT_ID).isEmpty())
    }

    private companion object {
        const val CALL_ID = "call_001"
        const val CONVERSATION_ID = "conv_001"
        const val PEER_ID = "1234567890abcdef1234567890abcdef12345678"
        const val STRANGER_ID = "fedcba9876543210fedcba9876543210fedcba98"
        const val STRANGER = "jami:$STRANGER_ID"

        /**
         * The shape the daemon actually returns from `getContacts`, transcribed from a real
         * account's contact list on a device (Pixel 7a, 2026-11-08) rather than invented.
         *
         * The identifier is keyed **"id"**. This fixture used to say "uri", matching the
         * production code's own mistake, so every test here passed while the feature was
         * totally broken on a device: `isConfirmedContact` never found a contact, and the
         * setting refused calls from confirmed contacts with SIP 603. "uri" is a conversation
         * *member* key, not a contact key — see [isConfirmedContact].
         */
        fun confirmedContact(id: String): Map<String, String> = mapOf(
            "id" to id,
            "confirmed" to "true",
            "banned" to "false",
            "added" to "1762531860",
            "conversationId" to "b54fc4f5fd4c7c98f43fe429be3f1223ddc4df50",
        )
    }
}
