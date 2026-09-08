package net.jami.services

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the notification action mapping.
 *
 * The previous version of this file used MockK and JUnit, neither of which has a
 * Kotlin/Native target, so it could never compile for iOS — the whole iosTest source set
 * was unbuildable because of it. It also verified methods that no longer exist
 * (callService.acceptCall, conversationFacade.markConversationAsRead).
 *
 * The mapping is now exercised through [dispatchNotificationAction] with a hand-built
 * [NotificationActions] recorder, which needs no UNNotificationResponse — that class has no
 * public initialiser and cannot be constructed in a test at all.
 */
class IOSNotificationDelegateTest {

    /** Records what the dispatch asked for, so tests can assert on it. */
    private class RecordingActions : NotificationActions {
        val calls = mutableListOf<String>()
        override suspend fun answerCall(accountId: String, callId: String) {
            calls += "answerCall($accountId,$callId)"
        }
        override suspend fun declineCall(accountId: String, callId: String) {
            calls += "declineCall($accountId,$callId)"
        }
        override suspend fun reply(accountId: String, conversationId: String, text: String) {
            calls += "reply($accountId,$conversationId,$text)"
        }
        override suspend fun markRead(accountId: String, conversationId: String) {
            calls += "markRead($accountId,$conversationId)"
        }
        override suspend fun acceptRequest(accountId: String, conversationId: String) {
            calls += "acceptRequest($accountId,$conversationId)"
        }
        override suspend fun declineRequest(accountId: String, conversationId: String) {
            calls += "declineRequest($accountId,$conversationId)"
        }
    }

    private suspend fun dispatch(
        action: String,
        accountId: String? = ACCOUNT,
        callId: String? = null,
        conversationId: String? = null,
        replyText: String? = null,
        actions: NotificationActions,
    ) = dispatchNotificationAction(action, accountId, callId, conversationId, replyText, actions)

    @Test
    fun answerCallAcceptsTheCall() = runTest {
        val actions = RecordingActions()
        val dismiss = dispatch(ACTION_ANSWER_CALL, callId = CALL, actions = actions)

        assertEquals(listOf("answerCall($ACCOUNT,$CALL)"), actions.calls)
        // The call UI takes over, so the notification is left to the system.
        assertFalse(dismiss)
    }

    @Test
    fun declineCallRefusesAndDismisses() = runTest {
        val actions = RecordingActions()
        val dismiss = dispatch(ACTION_DECLINE_CALL, callId = CALL, actions = actions)

        assertEquals(listOf("declineCall($ACCOUNT,$CALL)"), actions.calls)
        assertTrue(dismiss)
    }

    /**
     * The regression this guards: call notifications used to write a "confId" key while the
     * delegate read accountId/callId, so Answer and Decline could never fire.
     */
    @Test
    fun callActionsDoNothingWithoutAccountAndCallId() = runTest {
        val actions = RecordingActions()
        dispatch(ACTION_ANSWER_CALL, accountId = null, callId = CALL, actions = actions)
        dispatch(ACTION_ANSWER_CALL, callId = null, actions = actions)
        dispatch(ACTION_DECLINE_CALL, accountId = null, callId = CALL, actions = actions)

        assertTrue(actions.calls.isEmpty(), "expected no action, got ${actions.calls}")
    }

    @Test
    fun replySendsTheText() = runTest {
        val actions = RecordingActions()
        val dismiss = dispatch(
            ACTION_REPLY_MESSAGE, conversationId = CONVERSATION, replyText = "Hello there",
            actions = actions,
        )

        assertEquals(listOf("reply($ACCOUNT,$CONVERSATION,Hello there)"), actions.calls)
        assertTrue(dismiss)
    }

    @Test
    fun replyIgnoresBlankText() = runTest {
        val actions = RecordingActions()
        dispatch(ACTION_REPLY_MESSAGE, conversationId = CONVERSATION, replyText = "   ", actions = actions)
        dispatch(ACTION_REPLY_MESSAGE, conversationId = CONVERSATION, replyText = null, actions = actions)

        assertTrue(actions.calls.isEmpty(), "expected no send, got ${actions.calls}")
    }

    @Test
    fun markReadMarksTheConversation() = runTest {
        val actions = RecordingActions()
        val dismiss = dispatch(ACTION_MARK_READ, conversationId = CONVERSATION, actions = actions)

        assertEquals(listOf("markRead($ACCOUNT,$CONVERSATION)"), actions.calls)
        assertTrue(dismiss)
    }

    @Test
    fun trustRequestActionsAreHandled() = runTest {
        val accept = RecordingActions()
        assertTrue(dispatch(ACTION_ACCEPT, conversationId = CONVERSATION, actions = accept))
        assertEquals(listOf("acceptRequest($ACCOUNT,$CONVERSATION)"), accept.calls)

        val decline = RecordingActions()
        assertTrue(dispatch(ACTION_REQUEST_DECLINE, conversationId = CONVERSATION, actions = decline))
        assertEquals(listOf("declineRequest($ACCOUNT,$CONVERSATION)"), decline.calls)
    }

    /**
     * A trust-request notification only carries a conversation when the account has exactly
     * one pending request; with several there is nothing unambiguous to act on, so the tap
     * must fall through and open the app rather than guessing.
     */
    @Test
    fun acceptWithoutAConversationOpensTheAppInstead() = runTest {
        val actions = RecordingActions()
        val dismiss = dispatch(ACTION_ACCEPT, conversationId = null, actions = actions)

        assertTrue(actions.calls.isEmpty())
        assertFalse(dismiss)
    }

    @Test
    fun unknownActionIsIgnored() = runTest {
        val actions = RecordingActions()
        val dismiss = dispatch("SOME_UNKNOWN_ACTION", conversationId = CONVERSATION, actions = actions)

        assertTrue(actions.calls.isEmpty())
        assertFalse(dismiss)
    }

    private companion object {
        const val ACCOUNT = "test_account"
        const val CALL = "test_call_id"
        const val CONVERSATION = "test_conv_uri"
    }
}
