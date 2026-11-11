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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Account
import net.jami.model.Conversation
import net.jami.model.DataTransfer
import net.jami.model.Interaction
import net.jami.model.Uri
import net.jami.viewmodel.TEST_ACCOUNT_ID
import net.jami.viewmodel.TestServiceStack
import net.jami.viewmodel.connectDaemonCallbacks
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.prepareAccountInService
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One test per daemon callback that iOS never received until the 2026-09-08 bridge work
 * (`ios_implementation_gap.md` §2.1). Each is fired through [StubDaemonBridge.fireDaemonEvent]
 * into the production [DaemonCallbacksImpl], so it covers the routing layer and the service
 * handler together, and asserts the one thing a user would notice if the event were lost.
 *
 * What these cannot cover is the Obj-C++ half: whether `JamiBridgeWrapper` registers the signal
 * and `DaemonBridge.ios.kt` forwards it to [DaemonCallbacks]. That needs the native daemon.
 * The video-decoding signals of §2.1 go to `HardwareService` rather than [DaemonCallbacks], so
 * they are not here either.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DaemonCallbackRoutingTest {

    private class Env(val scope: CoroutineScope, val stack: TestServiceStack, val account: Account)

    private fun TestScope.env(): Env {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        connectDaemonCallbacks(stack, scope)
        return Env(scope, stack, account)
    }

    /** Collects account events from before the event fires; the flow does not replay. */
    private fun Env.accountEvents(): MutableList<AccountEvent> {
        val seen = mutableListOf<AccountEvent>()
        scope.launch { stack.accountService.accountEvents.collect { seen.add(it) } }
        return seen
    }

    private fun Env.conversationEvents(): MutableList<ConversationEvent> {
        val seen = mutableListOf<ConversationEvent>()
        scope.launch { stack.conversationFacade.conversationEvents.collect { seen.add(it) } }
        return seen
    }

    // ---------------------------------------------------------------- account callbacks

    /** Was broken on iOS: user-directory search fired its query and never got results. */
    @Test
    fun userSearchResultsArrive() = runTest {
        val env = env()
        val seen = env.accountEvents()
        advanceUntilIdle()

        val results = listOf(mapOf("username" to "alice", "id" to PEER_ID))
        env.stack.stub.fireDaemonEvent { onUserSearchEnded(TEST_ACCOUNT_ID, 0, "ali", results) }
        advanceUntilIdle()

        val event = assertNotNull(seen.filterIsInstance<AccountEvent.UserSearchEnded>().singleOrNull())
        assertEquals("ali", event.query)
        assertEquals(results, event.results)
    }

    /** Was broken on iOS: registration and device-online state never refreshed. */
    @Test
    fun volatileDetailsReachTheAccount() = runTest {
        val env = env()
        val seen = env.accountEvents()
        advanceUntilIdle()

        val details = mapOf("Account.registrationStatus" to "REGISTERED", "Account.deviceAnnounced" to "true")
        env.stack.stub.fireDaemonEvent { onVolatileAccountDetailsChanged(TEST_ACCOUNT_ID, details) }
        advanceUntilIdle()

        val account = assertNotNull(env.stack.accountService.getAccount(TEST_ACCOUNT_ID))
        assertEquals("true", account.volatileDetails["Account.deviceAnnounced"])
        assertTrue(seen.any { it is AccountEvent.VolatileDetailsChanged && it.details == details })
    }

    /** Was broken on iOS: device linking / QR pairing showed no progress. */
    @Test
    fun addDeviceProgressArrives() = runTest {
        val env = env()
        val seen = env.accountEvents()
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent {
            onAddDeviceStateChanged(TEST_ACCOUNT_ID, 42L, 2, mapOf("token" to "jami-auth://x"))
        }
        advanceUntilIdle()

        val event = assertNotNull(seen.filterIsInstance<AccountEvent.AddDeviceStateChanged>().singleOrNull())
        assertEquals(42L, event.opId)
        assertEquals(2, event.state)
    }

    /** Was broken on iOS: the revoke-device UI never completed. Success also drops the device. */
    @Test
    fun aSuccessfulRevocationRemovesTheDevice() = runTest {
        val env = env()
        env.stack.accountService.getAccount(TEST_ACCOUNT_ID)!!.devices["dev-old"] = "Old phone"
        val seen = env.accountEvents()
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent { onDeviceRevocationEnded(TEST_ACCOUNT_ID, "dev-old", 0) }
        advanceUntilIdle()

        val account = assertNotNull(env.stack.accountService.getAccount(TEST_ACCOUNT_ID))
        assertFalse("dev-old" in account.devices, "a successful revocation must remove the device")
        assertTrue(seen.any { it is AccountEvent.DeviceRevocationEnded && it.deviceId == "dev-old" && it.state == 0 })
    }

    /** A failed revocation is reported but keeps the device. */
    @Test
    fun aFailedRevocationKeepsTheDevice() = runTest {
        val env = env()
        env.stack.accountService.getAccount(TEST_ACCOUNT_ID)!!.devices["dev-old"] = "Old phone"
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent { onDeviceRevocationEnded(TEST_ACCOUNT_ID, "dev-old", 2) }
        advanceUntilIdle()

        assertTrue("dev-old" in env.stack.accountService.getAccount(TEST_ACCOUNT_ID)!!.devices)
    }

    /** Was broken on iOS: account migration never completed. */
    @Test
    fun migrationEndArrives() = runTest {
        val env = env()
        val seen = env.accountEvents()
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent { onMigrationEnded(TEST_ACCOUNT_ID, "SUCCESS") }
        advanceUntilIdle()

        assertTrue(seen.any { it is AccountEvent.MigrationEnded && it.state == "SUCCESS" }, "$seen")
    }

    /** Was broken on iOS: non-swarm / SIP text messages were dropped. */
    @Test
    fun anAccountMessageArrives() = runTest {
        val env = env()
        val seen = mutableListOf<IncomingMessage>()
        env.scope.launch { env.stack.accountService.incomingMessages.collect { seen.add(it) } }
        advanceUntilIdle()

        val body = mapOf("text/plain" to "hello over SIP")
        env.stack.stub.fireDaemonEvent { onIncomingAccountMessage(TEST_ACCOUNT_ID, "m1", null, PEER_URI, body) }
        advanceUntilIdle()

        val message = assertNotNull(seen.singleOrNull())
        assertEquals(PEER_URI, message.from)
        assertEquals(body, message.messages)
    }

    /**
     * Was broken on iOS: own profile/avatar updates were not received. This one goes through the
     * ordered account-task channel rather than a fire-and-forget launch.
     */
    @Test
    fun ownProfileUpdateArrives() = runTest {
        val env = env()
        val seen = env.accountEvents()
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent { onAccountProfileReceived(TEST_ACCOUNT_ID, "New Name", "") }
        advanceUntilIdle()

        assertTrue(seen.any { it is AccountEvent.ProfileReceived && it.name == "New Name" }, "$seen")
    }

    // ----------------------------------------------------------- conversation callbacks

    /** Was broken on iOS: in-conversation search was dead. */
    @Test
    fun searchResultsArrive() = runTest {
        val env = env()
        val seen = env.conversationEvents()
        advanceUntilIdle()

        val found = listOf(mapOf("id" to "msg-1", "body" to "needle"))
        env.stack.stub.fireDaemonEvent { onMessagesFound(7, TEST_ACCOUNT_ID, CONVERSATION_ID, found) }
        advanceUntilIdle()

        val event = assertNotNull(seen.filterIsInstance<ConversationEvent.MessagesFound>().singleOrNull())
        assertEquals(CONVERSATION_ID, event.conversationId)
        assertEquals(found, event.messages)
    }

    /** Was broken on iOS: no sent/delivered/read receipts. */
    @Test
    fun aDeliveryReceiptArrives() = runTest {
        val env = env()
        env.account.newSwarm(CONVERSATION_ID, Conversation.Mode.OneToOne)
        val seen = env.conversationEvents()
        advanceUntilIdle()

        val delivered = Interaction.MessageStates.SUCCESS.value
        env.stack.stub.fireDaemonEvent {
            onAccountMessageStatusChanged(TEST_ACCOUNT_ID, CONVERSATION_ID, "msg-1", PEER_ID, delivered)
        }
        advanceUntilIdle()

        val event = assertNotNull(seen.filterIsInstance<ConversationEvent.MessageStatusChanged>().singleOrNull())
        assertEquals("msg-1", event.messageId)
        assertEquals(delivered, event.status)
    }

    /** Was broken on iOS: preference changes made on another device were ignored. */
    @Test
    fun preferencesFromAnotherDeviceAreStored() = runTest {
        val env = env()
        advanceUntilIdle()

        val prefs = mapOf("color" to "#ff0000", "ignoreNotifications" to "true")
        env.stack.stub.fireDaemonEvent {
            onConversationPreferencesUpdated(TEST_ACCOUNT_ID, CONVERSATION_ID, prefs)
        }
        advanceUntilIdle()

        val stored = env.stack.preferencesService.getConversationPreferences(
            TEST_ACCOUNT_ID, Uri(Uri.SWARM_SCHEME, CONVERSATION_ID)
        )
        assertEquals(prefs, stored)
    }

    /** Was broken on iOS: a declined request stayed in the request list. */
    @Test
    fun aDeclinedRequestLeavesThePendingList() = runTest {
        val env = env()
        val pending = Conversation(TEST_ACCOUNT_ID, Uri(Uri.SWARM_SCHEME, CONVERSATION_ID), Conversation.Mode.OneToOne)
        env.account.addPendingConversation(pending)
        assertTrue(env.account.getPending().isNotEmpty())
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent { onConversationRequestDeclined(TEST_ACCOUNT_ID, CONVERSATION_ID) }
        advanceUntilIdle()

        assertTrue(env.account.getPending().isEmpty(), "the declined request is still pending")
    }

    /** Was broken on iOS: the group-call "join ongoing call" banner never appeared. */
    @Test
    fun activeCallsReachTheConversation() = runTest {
        val env = env()
        val conversation = env.account.newSwarm(CONVERSATION_ID, Conversation.Mode.InvitesOnly)
        val seen = env.conversationEvents()
        advanceUntilIdle()

        val call = mapOf(
            Conversation.ActiveCall.KEY_CONF_ID to "conf-9",
            Conversation.ActiveCall.KEY_URI to PEER_URI,
            Conversation.ActiveCall.KEY_DEVICE to "device-a",
        )
        env.stack.stub.fireDaemonEvent { onActiveCallsChanged(TEST_ACCOUNT_ID, CONVERSATION_ID, listOf(call)) }
        advanceUntilIdle()

        assertEquals(listOf("conf-9"), conversation.activeCallsFlow.value.map { it.confId })
        assertTrue(seen.any { it is ConversationEvent.ActiveCallsChanged && it.conversationId == CONVERSATION_ID })
    }

    /**
     * Was broken on iOS: file transfers never reported progress or completion. Also covers the
     * per-conversation channel, which this event is queued on.
     */
    @Test
    fun aFinishedTransferIsMarkedFinished() = runTest {
        val env = env()
        val conversation = env.account.newSwarm(CONVERSATION_ID, Conversation.Mode.OneToOne)
        val transfer = DataTransfer(conversation, PEER_ID, TEST_ACCOUNT_ID, "photo.jpg", true, 1000L, 0L, FILE_ID)
            .apply { setSwarmInfo(CONVERSATION_ID, "msg-file", null) }
        conversation.addSwarmElement(transfer, false)
        advanceUntilIdle()

        env.stack.stub.fireDaemonEvent { onDataTransferEvent(TEST_ACCOUNT_ID, CONVERSATION_ID, "msg-file", FILE_ID, 6) }
        advanceUntilIdle()

        val stored = conversation.getMessage("msg-file") as DataTransfer
        assertEquals(Interaction.TransferStatus.TRANSFER_FINISHED, stored.transferStatus)
    }

    // ------------------------------------------------------------------- call callbacks

    /**
     * Was received on iOS and then discarded, so the conference participant grid never updated.
     * Needs a conference to exist first, which the daemon announces with ConferenceCreated.
     */
    @Test
    fun conferenceInfoReachesTheParticipantGrid() = runTest {
        val env = env()
        advanceUntilIdle()
        env.stack.stub.fireDaemonEvent { onConferenceCreated(TEST_ACCOUNT_ID, "", CONF_ID) }
        advanceUntilIdle()
        val conference = assertNotNull(env.stack.callService.getConference(CONF_ID))

        val participant = mapOf("uri" to PEER_URI, "x" to "0", "y" to "0", "w" to "640", "h" to "360")
        env.stack.stub.fireDaemonEvent { onConferenceInfoUpdated(CONF_ID, listOf(participant)) }
        advanceUntilIdle()

        val info = conference.participantInfoFlow.value
        assertEquals(1, info.size, "the participant grid did not get the daemon's layout: $info")
        assertEquals(640, info.single().w)
    }

    private companion object {
        const val CONVERSATION_ID = "conv-routing"
        const val PEER_ID = "f00dcafef00dcafef00dcafef00dcafef00dcafe"
        const val PEER_URI = "jami:$PEER_ID"
        const val FILE_ID = "12345"
        const val CONF_ID = "conf-routing"
    }
}
