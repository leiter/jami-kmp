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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Contact
import net.jami.model.Uri
import net.jami.model.settings.PresenceNote
import net.jami.model.settings.PresenceNoteError
import net.jami.model.settings.PresenceSettings
import net.jami.model.settings.SettingsKeys
import net.jami.repository.SettingsRepository
import net.jami.testHardwareService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The presence status note (daemon-audit F6).
 *
 * libjami's `publish()` forwards only the note on a Jami account, and the receiving daemon digs
 * it back out of the PIDF document with `<basic>([\w\s]+)</basic>`. Those two facts drive every
 * rule tested here: what may be published, how a note is cleared, and why the note has to be
 * republished by the client after a restart.
 */
class PresenceNoteTest {

    private val account = "acc1"
    private val alice = Uri.fromId("aaaa1111")

    /** ContactService plus the AccountService whose contact cache it writes presence into. */
    private class Fixture(val contacts: ContactService, val accounts: AccountService) {
        fun contact(accountId: String, uri: Uri): Contact =
            accounts.getAccount(accountId)!!.getContactFromCache(uri)
    }

    private suspend fun TestScope.makeFixture(stub: StubDaemonBridge): Fixture {
        stub.accountIds = listOf(account)
        stub.accountDetails.getOrPut(account) { mutableMapOf() }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val accountService = AccountService(stub, testHardwareService(), StubDeviceRuntimeService(), scope)
        accountService.loadAccounts()
        advanceUntilIdle()
        val contacts = ContactService(scope, accountService, stub, VCardService(StubDeviceRuntimeService()))
        return Fixture(contacts, accountService)
    }

    // ==================== Validation ====================

    @Test
    fun plainAsciiNotesAreAccepted() {
        assertNull(PresenceNote.validate("away"))
        assertNull(PresenceNote.validate("In a meeting until 3"))
        assertNull(PresenceNote.validate("on_holiday"))
        assertNull(PresenceNote.validate(""))
    }

    /**
     * The peer's regex is `[\w\s]+` on a `char` sequence, so anything outside ASCII word
     * characters makes the whole match fail and the peer is told nothing at all. Rejecting here
     * is the difference between an error the user can see and a status that silently vanishes.
     */
    @Test
    fun notesTheReceivingDaemonCannotParseAreRejected() {
        for (note in listOf("Sorry, busy!", "déjeuner", "在开会", "away 🙂", "back @ 5", "a-b")) {
            assertEquals(
                PresenceNoteError.UNSUPPORTED_CHARACTERS,
                PresenceNote.validate(note),
                "'$note' would not survive the peer's PIDF regex"
            )
        }
    }

    @Test
    fun anOverlongNoteIsRejected() {
        assertNull(PresenceNote.validate("a".repeat(PresenceNote.MAX_LENGTH)))
        assertEquals(
            PresenceNoteError.TOO_LONG,
            PresenceNote.validate("a".repeat(PresenceNote.MAX_LENGTH + 1))
        )
    }

    /**
     * `[\w\s]+` needs at least one character, so an empty note matches nothing and would leave
     * peers showing the previous note for good. Clearing goes out as a single space instead.
     */
    @Test
    fun clearingTravelsAsASpaceAndComesBackEmpty() {
        assertEquals(PresenceNote.CLEARED, PresenceNote.toWire(""))
        assertEquals(PresenceNote.CLEARED, PresenceNote.toWire("   "))
        assertTrue(PresenceNote.CLEARED.isNotEmpty(), "an empty wire value cannot match the regex")
        assertEquals("", PresenceNote.fromWire(PresenceNote.CLEARED))
        assertEquals("away", PresenceNote.toWire("  away  "))
        assertEquals("away", PresenceNote.fromWire(" away "))
    }

    // ==================== Publishing ====================

    @Test
    fun settingANotePublishesItAndStoresIt() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount(account)
        advanceUntilIdle()

        assertNull(repo.updateStatusNote("In a meeting"))
        advanceUntilIdle()

        assertEquals(
            listOf(Triple(account, true, "In a meeting")),
            stub.publishedPresenceNotes
        )
        assertEquals("In a meeting", repo.presenceSettings.value.statusNote)
        assertTrue(
            stub.accountDetails[account]!!.containsKey(SettingsKeys.PRESENCE),
            "the note must be stored, since the daemon does not keep it"
        )
    }

    @Test
    fun anInvalidNoteIsNeitherStoredNorPublished() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount(account)
        advanceUntilIdle()

        assertEquals(PresenceNoteError.UNSUPPORTED_CHARACTERS, repo.updateStatusNote("busy!"))
        advanceUntilIdle()

        assertTrue(stub.publishedPresenceNotes.isEmpty())
        assertEquals("", repo.presenceSettings.value.statusNote)
    }

    @Test
    fun clearingPublishesTheClearedMarkerRatherThanAnEmptyNote() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount(account)
        advanceUntilIdle()

        repo.updateStatusNote("away")
        advanceUntilIdle()
        repo.updateStatusNote("")
        advanceUntilIdle()

        assertEquals(
            listOf(
                Triple(account, true, "away"),
                Triple(account, true, PresenceNote.CLEARED),
            ),
            stub.publishedPresenceNotes
        )
        assertEquals("", repo.presenceSettings.value.statusNote)
    }

    /**
     * The daemon holds the published note in memory only (`JamiAccount::presenceNote_`), so
     * after an app or daemon restart the client has to publish it again or contacts keep seeing
     * a stale status — or none.
     */
    @Test
    fun aStoredNoteIsRepublishedWhenSettingsLoad() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf(
            SettingsKeys.PRESENCE to """{"statusNote":"on holiday"}"""
        )
        val repo = SettingsRepository(stub, this)

        repo.observeAccount(account)
        advanceUntilIdle()

        assertEquals("on holiday", repo.presenceSettings.value.statusNote)
        assertEquals(listOf(Triple(account, true, "on holiday")), stub.publishedPresenceNotes)
    }

    @Test
    fun loadingAnAccountWithNoNotePublishesNothing() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf(
            SettingsKeys.PRESENCE to """{"statusNote":""}"""
        )
        val repo = SettingsRepository(stub, this)

        repo.observeAccount(account)
        advanceUntilIdle()

        assertTrue(stub.publishedPresenceNotes.isEmpty())
    }

    @Test
    fun repeatingTheSameNoteDoesNotRepublishIt() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount(account)
        advanceUntilIdle()

        repo.updateStatusNote("away")
        advanceUntilIdle()
        repo.updateStatusNote("away")
        repo.updateStatusNote("  away  ")
        advanceUntilIdle()

        assertEquals(1, stub.publishedPresenceNotes.size)
    }

    // ==================== Receiving ====================

    @Test
    fun aPeersNoteArrivesOnTheBuddyNotification() = runTest {
        val stub = StubDaemonBridge()
        val f = makeFixture(stub)

        f.contacts.onPresenceUpdate(account, alice.rawRingId, 2, "In a meeting")
        advanceUntilIdle()

        val contact = f.contact(account, alice)
        assertEquals("In a meeting", contact.statusNote.value)
        assertEquals(Contact.PresenceStatus.CONNECTED, contact.presenceStatus.value)
    }

    /**
     * Plain online/offline notifications carry an empty lineStatus (only the PIDF path fills
     * it), so an empty value means "nothing new said" and must not wipe a current note.
     */
    @Test
    fun anOrdinaryPresenceUpdateLeavesTheNoteAlone() = runTest {
        val stub = StubDaemonBridge()
        val f = makeFixture(stub)

        f.contacts.onPresenceUpdate(account, alice.rawRingId, 2, "away")
        f.contacts.onPresenceUpdate(account, alice.rawRingId, 1, "")
        advanceUntilIdle()

        assertEquals("away", f.contact(account, alice).statusNote.value)
    }

    @Test
    fun thePeerClearingTheirNoteClearsItHere() = runTest {
        val stub = StubDaemonBridge()
        val f = makeFixture(stub)

        f.contacts.onPresenceUpdate(account, alice.rawRingId, 2, "away")
        f.contacts.onPresenceUpdate(account, alice.rawRingId, 2, PresenceNote.CLEARED)
        advanceUntilIdle()

        assertEquals("", f.contact(account, alice).statusNote.value)
    }

    /** A contact that went offline is no longer saying anything about itself. */
    @Test
    fun goingOfflineDropsTheNote() = runTest {
        val stub = StubDaemonBridge()
        val f = makeFixture(stub)

        f.contacts.onPresenceUpdate(account, alice.rawRingId, 2, "away")
        f.contacts.onPresenceUpdate(account, alice.rawRingId, 0, "")
        advanceUntilIdle()

        val contact = f.contact(account, alice)
        assertEquals("", contact.statusNote.value)
        assertEquals(Contact.PresenceStatus.OFFLINE, contact.presenceStatus.value)
    }

    @Test
    fun presenceSettingsResetWhenTheAccountGoesAway() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails[account] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount(account)
        advanceUntilIdle()
        repo.updateStatusNote("away")
        advanceUntilIdle()

        repo.stopObserving()

        assertEquals(PresenceSettings(), repo.presenceSettings.value)
    }
}
