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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.jami.model.Contact
import net.jami.model.ContactViewModel
import net.jami.model.Conversation
import net.jami.model.Profile

/**
 * Service for managing contacts and their profiles.
 *
 * This is a stub interface for the ConversationFacade port.
 * Full implementation will be added when contact loading is complete.
 *
 * Ported from: jami-client-android libjamiclient
 */
interface ContactService {

    /**
     * Observe changes to contacts in a conversation.
     */
    fun observeContact(
        accountId: String,
        contacts: List<Contact>,
        withPresence: Boolean
    ): Flow<List<ContactViewModel>>

    /**
     * Get loaded conversation with contact info.
     */
    suspend fun getLoadedConversation(conversation: Conversation): ConversationItemViewModel

    /**
     * Load contact data (profile photo, name).
     */
    suspend fun loadContactData(contact: Contact, accountId: String): Profile
}

/**
 * View model for a conversation in a list.
 */
data class ConversationItemViewModel(
    val conversation: Conversation,
    val profile: Profile,
    val contacts: List<ContactViewModel>,
    val hasPresence: Boolean
) {
    val accountId: String get() = conversation.accountId
    val uri: net.jami.model.Uri get() = conversation.uri
    val isSwarm: Boolean get() = conversation.isSwarm

    fun matches(query: String): Boolean {
        val lowerQuery = query.lowercase()
        if (profile.displayName?.lowercase()?.contains(lowerQuery) == true) return true
        for (contactVm in contacts) {
            if (contactVm.displayName.lowercase().contains(lowerQuery)) return true
            if (contactVm.contact.primaryNumber.lowercase().contains(lowerQuery)) return true
        }
        return false
    }

    enum class Title {
        None,
        Conversations,
        PublicDirectory
    }
}

/**
 * Stub implementation of ContactService for testing.
 */
class StubContactService : ContactService {
    override fun observeContact(
        accountId: String,
        contacts: List<Contact>,
        withPresence: Boolean
    ): Flow<List<ContactViewModel>> {
        return flowOf(contacts.map { contact ->
            ContactViewModel(
                contact = contact,
                profile = Profile.EMPTY_PROFILE
            )
        })
    }

    override suspend fun getLoadedConversation(conversation: Conversation): ConversationItemViewModel {
        val contactViewModels = conversation.contacts.map { contact ->
            ContactViewModel(
                contact = contact,
                profile = Profile.EMPTY_PROFILE
            )
        }
        return ConversationItemViewModel(
            conversation = conversation,
            profile = Profile.EMPTY_PROFILE,
            contacts = contactViewModels,
            hasPresence = false
        )
    }

    override suspend fun loadContactData(contact: Contact, accountId: String): Profile {
        return Profile.EMPTY_PROFILE
    }
}
