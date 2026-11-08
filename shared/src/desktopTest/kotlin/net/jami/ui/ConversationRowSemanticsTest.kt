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
package net.jami.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import net.jami.ui.screens.ConversationListItem
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ConversationItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The first Compose UI test in the repository.
 *
 * The accessibility pass of 2026-11-07 gave the conversation row merged semantics, a button
 * role, a state description and four custom actions, and added `TestTags` so a test could
 * find it — but `compose-ui-test-junit4` was wired for instrumented tests only, so none of it
 * had a guard that runs without a device. These assertions run on the JVM.
 *
 * What they protect is the thing a screen reader actually consumes: one node per row rather
 * than five, a description that names the conversation, pinned/muted spoken as state, and
 * long-press actions reachable without a long press.
 */
@OptIn(ExperimentalTestApi::class)
class ConversationRowSemanticsTest {

    private fun conversation(
        pinned: Boolean = false,
        muted: Boolean = false,
    ) = ConversationItem(
        id = "conv1",
        contactId = "peer1",
        displayName = "Anna",
        lastMessage = "see you then",
        timestamp = 0L,
        unreadCount = 0,
        avatarBytes = null,
        isOnline = false,
        isRead = true,
        isPinned = pinned,
        isMuted = muted,
    )

    @Test
    fun theRowIsOneNodeThatNamesTheConversation() = runComposeUiTest {
        setContent {
            JamiTheme { ConversationListItem(conversation = conversation(), onClick = {}) }
        }

        val node = onNodeWithTag(TestTags.CONVERSATION_ROW).fetchSemanticsNode()
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
        assertNotNull(description, "the row has no merged description for a screen reader")
        assertTrue(
            description.any { it.contains("Anna") },
            "the description must name the conversation: $description"
        )
    }

    /** Pinned and muted are state, not part of the name: a reader says them separately. */
    @Test
    fun pinnedAndMutedAreSpokenAsState() = runComposeUiTest {
        setContent {
            JamiTheme {
                ConversationListItem(
                    conversation = conversation(pinned = true, muted = true),
                    onClick = {},
                )
            }
        }

        val node = onNodeWithTag(TestTags.CONVERSATION_ROW).fetchSemanticsNode()
        assertNotNull(
            node.config.getOrNull(SemanticsProperties.StateDescription),
            "pinned/muted must be a state description, not appended to the name"
        )
    }

    /**
     * The four long-press entries are also custom actions, which is how they are reachable
     * at all with a screen reader on: a long press is not something it can perform.
     */
    @Test
    fun theLongPressMenuIsReachableAsCustomActions() = runComposeUiTest {
        setContent {
            JamiTheme { ConversationListItem(conversation = conversation(), onClick = {}) }
        }

        val node = onNodeWithTag(TestTags.CONVERSATION_ROW).fetchSemanticsNode()
        val actions = node.config.getOrNull(SemanticsActions.CustomActions)
        assertNotNull(actions, "no custom actions on the row")
        assertEquals(4, actions.size, "expected pin, mute, block and delete: ${actions.map { it.label }}")
    }
}
