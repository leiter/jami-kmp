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

import net.jami.ui.viewmodel.callPeerTitle
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the call screen titles a peer with, in Android CallFragment's order. */
class CallPeerTitleTest {
    private val id = "4f3b0a1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f70"

    @Test
    fun theProfileNameWinsAndTheRegisteredNameGoesUnderneath() {
        assertEquals("Anna" to "anna77", callPeerTitle("Anna", "Contact name", "anna77", id))
    }

    @Test
    fun withoutAProfileNameTheContactNameIsUsed() {
        assertEquals("Anna B." to "anna77", callPeerTitle(null, "Anna B.", "anna77", id))
    }

    @Test
    fun aRegisteredNameAloneIsTheTitleAndNotRepeated() {
        assertEquals("anna77" to "", callPeerTitle(" ", null, "anna77", id))
    }

    @Test
    fun withNothingKnownTheJamiIdIsShownNotTheJamiUri() {
        // The call screen used to show "jami:<id>" for every outgoing call.
        assertEquals(id to "", callPeerTitle(null, "", null, id))
    }
}
