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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationFacadeTest {

    @Test
    fun testSearchResultEmpty() {
        val empty = SearchResult.EMPTY_RESULT
        assertEquals("", empty.query)
        assertTrue(empty.result.isEmpty())
    }

    @Test
    fun testConversationListEmpty() {
        val list = ConversationList()
        assertTrue(list.isEmpty())
        assertEquals(0, list.getCombinedSize())
    }

    @Test
    fun testConversationListGetHeader() {
        val list = ConversationList()
        assertEquals(ConversationItemViewModel.Title.None, list.getHeader(0))
    }

    @Test
    fun testConversationListWithSearchResults() {
        val searchResult = SearchResult("test", emptyList())
        val list = ConversationList(searchResult = searchResult, latestQuery = "test")
        assertEquals("test", list.latestQuery)
        // With empty search result list, header should be None
        assertEquals(ConversationItemViewModel.Title.None, list.getHeader(0))
    }

    @Test
    fun testConversationItemViewModelTitle() {
        assertEquals(ConversationItemViewModel.Title.None, ConversationItemViewModel.Title.None)
        assertEquals(ConversationItemViewModel.Title.Conversations, ConversationItemViewModel.Title.Conversations)
        assertEquals(ConversationItemViewModel.Title.PublicDirectory, ConversationItemViewModel.Title.PublicDirectory)
    }
}
