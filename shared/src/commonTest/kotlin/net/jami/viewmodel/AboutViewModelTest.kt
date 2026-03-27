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

import kotlinx.coroutines.test.runTest
import net.jami.ui.viewmodel.AboutState
import net.jami.ui.viewmodel.AboutViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AboutViewModelTest {

    @Test
    fun initialStateHasCorrectVersion() = runTest {
        val vm = AboutViewModel(scope = this)
        assertEquals(AboutState.VERSION, vm.state.value.version)
    }

    @Test
    fun initialStateHasCopyright() = runTest {
        val vm = AboutViewModel(scope = this)
        assertEquals(AboutState.COPYRIGHT, vm.state.value.copyright)
        assertNotNull(vm.state.value.copyright)
    }

    @Test
    fun initialStateHasDescription() = runTest {
        val vm = AboutViewModel(scope = this)
        assertEquals(AboutState.DESCRIPTION, vm.state.value.description)
    }

    @Test
    fun stateFlowIsNotNull() = runTest {
        val vm = AboutViewModel(scope = this)
        assertNotNull(vm.state)
        assertNotNull(vm.state.value)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val vm = AboutViewModel(scope = disposableScope())
        vm.onCleared()
    }
}
