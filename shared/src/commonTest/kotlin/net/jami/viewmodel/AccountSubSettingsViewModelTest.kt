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

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.AccountSubSettingsViewModel
import net.jami.ui.viewmodel.PortRangeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the validation and secret-handling behaviour from the security-networking plan
 * (F6 port validation, F5 TURN password handling, F19 moderation defaults).
 */
class AccountSubSettingsViewModelTest {

    private fun stubWithAccount(details: Map<String, String> = emptyMap()): StubDaemonBridge =
        StubDaemonBridge().apply {
            accountIds = listOf("acc1")
            accountDetails["acc1"] = mapOf(
                ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI
            ) + details
        }

    private suspend fun kotlinx.coroutines.test.TestScope.viewModel(
        stub: StubDaemonBridge
    ): AccountSubSettingsViewModel {
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob())
        val accountService = net.jami.services.AccountService(
            stub, net.jami.testHardwareService(), net.jami.services.StubDeviceRuntimeService(), scope
        )
        accountService.loadAccounts()
        advanceUntilIdle()
        val settings = net.jami.repository.SettingsRepository(stub, net.jami.services.InMemoryClientSettingsStore(), scope)
        return AccountSubSettingsViewModel(accountService, settings, scope)
    }

    // ==================== F6: RTP port validation ====================

    @Test
    fun aPortAboveTheProtocolLimitIsRejectedAndNotSentToTheDaemon() = runTest {
        val stub = stubWithAccount()
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.setAudioPortMin("99999")
        advanceUntilIdle()

        assertEquals(PortRangeError.OUT_OF_RANGE, vm.state.value.audioPortError)
        // The typed text stays visible so the field does not fight the keyboard…
        assertEquals("99999", vm.state.value.audioPortMin)
        // …but the daemon never sees it.
        assertFalse(stub.accountDetails["acc1"]!!.containsKey(ConfigKey.AUDIO_PORT_MIN.key))
    }

    @Test
    fun aPrivilegedPortIsRejected() = runTest {
        val vm = viewModel(stubWithAccount())
        advanceUntilIdle()

        vm.setVideoPortMin("80")
        advanceUntilIdle()

        assertEquals(PortRangeError.OUT_OF_RANGE, vm.state.value.videoPortError)
    }

    @Test
    fun nonNumericInputIsRejected() = runTest {
        val vm = viewModel(stubWithAccount())
        advanceUntilIdle()

        vm.setAudioPortMin("abc")
        advanceUntilIdle()

        assertEquals(PortRangeError.NOT_A_NUMBER, vm.state.value.audioPortError)
    }

    @Test
    fun aMinimumAboveTheMaximumIsRejected() = runTest {
        val vm = viewModel(stubWithAccount())
        advanceUntilIdle()

        vm.setAudioPortMax("20000")
        advanceUntilIdle()
        vm.setAudioPortMin("30000")
        advanceUntilIdle()

        assertEquals(PortRangeError.MIN_ABOVE_MAX, vm.state.value.audioPortError)
    }

    @Test
    fun aValidPortReachesTheDaemon() = runTest {
        val stub = stubWithAccount()
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.setAudioPortMin("16384")
        advanceUntilIdle()

        assertNull(vm.state.value.audioPortError)
        assertEquals("16384", stub.accountDetails["acc1"]!![ConfigKey.AUDIO_PORT_MIN.key])
    }

    /** Mid-edit emptiness is not an error; the daemon keeps its previous value. */
    @Test
    fun anEmptyFieldIsNotAnError() = runTest {
        val vm = viewModel(stubWithAccount())
        advanceUntilIdle()

        vm.setAudioPortMin("")
        advanceUntilIdle()

        assertNull(vm.state.value.audioPortError)
    }

    // ==================== F5: TURN password handling ====================

    /** The stored secret is not copied into UI state just to render it as dots. */
    @Test
    fun theStoredTurnPasswordIsNotLoadedIntoState() = runTest {
        val vm = viewModel(
            stubWithAccount(
                mapOf(
                    ConfigKey.TURN_ENABLE.key to "true",
                    ConfigKey.TURN_SERVER.key to "turn.example.net",
                    ConfigKey.TURN_PASSWORD.key to "s3cret",
                )
            )
        )
        advanceUntilIdle()

        assertEquals("", vm.state.value.turnPassword)
        assertTrue(vm.state.value.turnPasswordSet)
    }

    /**
     * An account with no TURN password reports the field as unset.
     *
     * Note this has to be forced: AccountService seeds Jami's public TURN credentials
     * (turn.jami.net / "ring") on any account that has none, so a freshly loaded account does
     * have a password set — which is why the other tests here see turnPasswordSet = true.
     */
    @Test
    fun noStoredPasswordIsReportedAsUnset() = runTest {
        val stub = stubWithAccount(
            mapOf(
                ConfigKey.TURN_ENABLE.key to "true",
                ConfigKey.TURN_SERVER.key to "turn.example.net",
                ConfigKey.TURN_PASSWORD.key to "",
            )
        )
        val vm = viewModel(stub)
        advanceUntilIdle()

        assertFalse(vm.state.value.turnPasswordSet)
    }

    @Test
    fun aTypedTurnPasswordReachesTheDaemonAndIsThenForgotten() = runTest {
        val stub = stubWithAccount()
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.setTurnPassword("new-secret")
        advanceUntilIdle()
        assertEquals("new-secret", stub.accountDetails["acc1"]!![ConfigKey.TURN_PASSWORD.key])

        vm.clearTurnPassword()
        assertEquals("", vm.state.value.turnPassword)
    }

    // ==================== F19: moderation defaults ====================

    private val alice = "aaaa1111"
    private val bob = "bbbb2222"

    @Test
    fun theDaemonsSlashJoinedModeratorSetIsParsedIntoAList() = runTest {
        val vm = viewModel(stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to "$alice/$bob")))
        advanceUntilIdle()

        assertEquals(listOf(alice, bob), vm.state.value.defaultModerators)
    }

    @Test
    fun anEmptyOrTrailingSeparatorDoesNotProduceBlankModerators() = runTest {
        // A set the daemon wrote while empty, and one with a trailing separator.
        val empty = viewModel(stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to "")))
        advanceUntilIdle()
        assertTrue(empty.state.value.defaultModerators.isEmpty())

        val trailing = viewModel(
            stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to "$alice/"))
        )
        advanceUntilIdle()
        assertEquals(listOf(alice), trailing.state.value.defaultModerators)
    }

    @Test
    fun addingAModeratorWritesTheSlashJoinedSetBackToTheDaemon() = runTest {
        val stub = stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to alice))
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.addDefaultModerator(bob)
        advanceUntilIdle()

        assertEquals(listOf(alice, bob), vm.state.value.defaultModerators)
        assertEquals("$alice/$bob", stub.accountDetails["acc1"]!![ConfigKey.DEFAULT_MODERATORS.key])
    }

    @Test
    fun addingADuplicateOrBlankModeratorIsIgnored() = runTest {
        val stub = stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to alice))
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.addDefaultModerator(alice)
        vm.addDefaultModerator("   ")
        advanceUntilIdle()

        assertEquals(listOf(alice), vm.state.value.defaultModerators)
    }

    @Test
    fun removingAModeratorLeavesTheRest() = runTest {
        val stub = stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to "$alice/$bob"))
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.removeDefaultModerator(alice)
        advanceUntilIdle()

        assertEquals(listOf(bob), vm.state.value.defaultModerators)
        assertEquals(bob, stub.accountDetails["acc1"]!![ConfigKey.DEFAULT_MODERATORS.key])
    }

    @Test
    fun removingTheLastModeratorWritesAnEmptyValueRatherThanLeavingTheOldOne() = runTest {
        val stub = stubWithAccount(mapOf(ConfigKey.DEFAULT_MODERATORS.key to alice))
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.removeDefaultModerator(alice)
        advanceUntilIdle()

        assertTrue(vm.state.value.defaultModerators.isEmpty())
        assertEquals("", stub.accountDetails["acc1"]!![ConfigKey.DEFAULT_MODERATORS.key])
    }

    @Test
    fun theModerationAndLocalNetworkTogglesReachTheDaemon() = runTest {
        val stub = stubWithAccount()
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.setLocalModeratorsEnabled(true)
        vm.setAllModeratorsEnabled(true)
        vm.setAccountPublishEnabled(true)
        vm.setLanDiscoveryEnabled(true)
        advanceUntilIdle()

        val details = stub.accountDetails["acc1"]!!
        assertEquals("true", details[ConfigKey.LOCAL_MODERATORS_ENABLED.key])
        assertEquals("true", details[ConfigKey.ALL_MODERATORS_ENABLED.key])
        assertEquals("true", details[ConfigKey.ACCOUNT_PUBLISH.key])
        assertEquals("true", details[ConfigKey.ACCOUNT_LAN_DISCOVERY.key])

        assertTrue(vm.state.value.localModeratorsEnabled)
        assertTrue(vm.state.value.allModeratorsEnabled)
        assertTrue(vm.state.value.accountPublishEnabled)
        assertTrue(vm.state.value.lanDiscoveryEnabled)
    }
}
