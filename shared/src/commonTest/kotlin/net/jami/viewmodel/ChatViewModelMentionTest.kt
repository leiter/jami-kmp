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
package net.jami.viewmodel

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Contact
import net.jami.model.Conversation
import net.jami.model.MemberRole
import net.jami.model.Uri
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.testAudioRecorderService
import net.jami.ui.utils.Mentions
import net.jami.ui.viewmodel.ChatViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The composer half of @mentions: a group's members are offered, a 1:1's are not, and
 * choosing one leaves text that reads correctly on a client that knows nothing about mentions
 * — which is every other client, a swarm message having no mention field.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelMentionTest {

    private fun TestScope.mentionViewModel(group: Boolean): ChatViewModel {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val account = prepareAccountInService(stub, accountService)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)

        stub.conversations[account.accountId] = listOf(CONVERSATION_ID)
        val mode = if (group) Conversation.Mode.InvitesOnly else Conversation.Mode.OneToOne
        val conversation = account.newSwarm(CONVERSATION_ID, mode)
        conversation.addContact(Contact(Uri.fromString("me"), isUser = true), MemberRole.MEMBER)
        if (group) {
            conversation.addContact(namedContact("anna"), MemberRole.MEMBER)
            conversation.addContact(namedContact("bob"), MemberRole.MEMBER)
        } else {
            conversation.addContact(namedContact("anna"), MemberRole.MEMBER)
        }
        account.conversationStarted(conversation)

        val vmScope = viewModelScope()
        return ChatViewModel(
            facade, accountService, StubDeviceRuntimeService(),
            net.jami.repository.DraftRepository(
                net.jami.services.InMemoryClientSettingsStore(), vmScope
            ),
            testAudioRecorderService(),
            makeSendQueue(stub, accountService, facade, this), vmScope,
        ).also {
            it.loadConversation(CONVERSATION_ID)
        }
    }

    private fun namedContact(name: String): Contact =
        Contact(Uri.fromId(name)).apply { username = name }

    @Test
    fun typingAnAtInAGroupOffersItsMembers() = runTest {
        val vm = mentionViewModel(group = true)
        advanceUntilIdle()

        vm.updateInput("hello @an")
        advanceUntilIdle()

        assertEquals(listOf("anna"), vm.state.value.mentionSuggestions)
    }

    @Test
    fun choosingOneCompletesTheTextAndClosesTheList() = runTest {
        val vm = mentionViewModel(group = true)
        advanceUntilIdle()
        vm.updateInput("hello @an")
        advanceUntilIdle()

        vm.insertMention("anna")
        advanceUntilIdle()

        assertEquals("hello @anna ", vm.state.value.inputText)
        assertTrue(vm.state.value.mentionSuggestions.isEmpty())
        // What goes on the wire is the literal text, which is what other clients show.
        assertTrue(Mentions.mentions(vm.state.value.inputText, "anna"))
    }

    /** One other person needs no disambiguating, so a 1:1 never shows the list. */
    @Test
    fun aOneToOneConversationOffersNothing() = runTest {
        val vm = mentionViewModel(group = false)
        advanceUntilIdle()

        vm.updateInput("hello @an")
        advanceUntilIdle()

        assertTrue(vm.state.value.memberNames.isEmpty())
        assertTrue(vm.state.value.mentionSuggestions.isEmpty())
    }

    private companion object {
        const val CONVERSATION_ID = "1ead1ead"
    }
}
