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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.viewmodel.makeTestServiceStack
import net.jami.model.ConfigKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two conversation-history daemon calls from messaging plan F7: `countInteractions`, used for
 * the daemon-side commit count in the sync panel, and `clearCache`, which releases the daemon's
 * in-memory copy of a conversation's history.
 */
class ConversationHistoryApiTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
        const val ME = "me"
        const val BOB = "bob"
    }

    private suspend fun TestScope.setUp(): Pair<StubDaemonBridge, ConversationFacade> {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_USERNAME.key to ME,
        )
        stub.conversations[ACC] = listOf(CONV)
        stub.conversationInfo[CONV] = mapOf("mode" to "0")
        stub.conversationMembers[CONV] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to BOB, "role" to "member"),
        )
        val stack = makeTestServiceStack(stub, this)
        stack.accountService.loadAccounts()
        advanceUntilIdle()
        stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()
        return stub to stack.conversationFacade
    }

    @Test
    fun theCommitCountComesFromTheDaemon() = runTest {
        val (stub, facade) = setUp()
        stub.interactionCounts["$ACC:$CONV"] = 42

        assertEquals(42, facade.countInteractions(ACC, CONV))
    }

    /**
     * The panel used to report the locally loaded history as the message count, which made a
     * fully synced conversation look half-empty until it had been scrolled.
     */
    @Test
    fun theSyncPanelReportsBothTheDaemonCountAndWhatIsLoaded() = runTest {
        val (stub, facade) = setUp()
        stub.interactionCounts["$ACC:$CONV"] = 128

        val info = facade.snapshotConversationSyncInfo(ACC).single { it.conversationId == CONV }

        assertEquals(128, info.messageCount)
        // Nothing has been paged in, so the two differ — which is the point of showing both.
        assertEquals(0, info.loadedMessageCount)
    }

    @Test
    fun clearingTheCacheReachesTheDaemon() = runTest {
        val (stub, facade) = setUp()

        facade.clearDaemonCache(ACC, CONV)

        assertEquals(listOf(ACC to CONV), stub.clearedCaches)
    }

    /** A conversation the daemon knows nothing about counts as zero rather than failing. */
    @Test
    fun anUnknownConversationCountsAsZero() = runTest {
        val (_, facade) = setUp()

        assertEquals(0, facade.countInteractions(ACC, "no-such-conversation"))
    }

    @Test
    fun theSnapshotStillWorksWhenTheCountIsUnavailable() = runTest {
        val (_, facade) = setUp()

        val info = facade.snapshotConversationSyncInfo(ACC)

        assertTrue(info.isNotEmpty())
    }
}
