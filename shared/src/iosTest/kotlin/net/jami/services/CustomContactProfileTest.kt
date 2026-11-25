/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import net.jami.model.Contact
import net.jami.model.Profile
import net.jami.model.Uri
import net.jami.utils.FileUtils
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The name and photo a user gives a 1:1 peer on this device (S9, conversation details): stored
 * apart from the daemon's profiles, preferred over the peer's own everywhere, and removable.
 * On iOS because it writes real files; the logic is common code.
 */
class CustomContactProfileTest {

    private val accountId = "acc1"
    private val peer = "0123456789abcdef0123456789abcdef01234567"

    private fun tempRuntime(): DeviceRuntimeService {
        val root = NSTemporaryDirectory() + "custom-profile-" + NSUUID().UUIDString
        FileUtils.mkdirs("$root/data")
        FileUtils.mkdirs("$root/cache")
        return object : DeviceRuntimeService by StubDeviceRuntimeService() {
            override fun getDataPath() = "$root/data"
            override fun getCachePath() = "$root/cache"
        }
    }

    @Test
    fun aCustomNameAndPhotoAreStoredAndWinOverThePeers() {
        val vCards = VCardService(tempRuntime())
        val photo = byteArrayOf(9, 8, 7)

        assertEquals(Profile.EMPTY_PROFILE, vCards.loadCustomProfile(accountId, peer))
        assertTrue(vCards.saveCustomProfile(accountId, peer, "  Granny  ", photo))

        val stored = vCards.loadCustomProfile(accountId, peer)
        assertEquals("Granny", stored.displayName)
        assertContentEquals(photo, stored.avatar)
        // Every avatar on screen comes through here, so this is what makes it show everywhere.
        assertContentEquals(photo, vCards.loadPeerAvatar(accountId, peer))
    }

    @Test
    fun clearingBothPartsRemovesTheCustomProfile() {
        val vCards = VCardService(tempRuntime())
        vCards.saveCustomProfile(accountId, peer, "Granny", byteArrayOf(1))

        assertTrue(vCards.saveCustomProfile(accountId, peer, null, null))

        assertEquals(Profile.EMPTY_PROFILE, vCards.loadCustomProfile(accountId, peer))
        // Back to the peer's own picture, which this peer does not have.
        assertNull(vCards.loadPeerAvatar(accountId, peer))
    }

    @Test
    fun renamingKeepsThePhotoAndTheContactTakesTheName() = runTest {
        val runtime = tempRuntime()
        val vCards = VCardService(runtime)
        // The services' own coroutines on a child job, cancelled at the end, so runTest does not
        // wait on their collectors.
        val services = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob())
        val accountService = AccountService(StubDaemonBridge(), net.jami.testHardwareService(), runtime, services)
        val contactService = ContactService(services, accountService, StubDaemonBridge(), vCards)
        val contact = Contact(Uri.fromId(peer))
        contact.loadedProfile = Profile("Margaret", null)

        contactService.saveCustomProfile(accountId, contact, null, byteArrayOf(5))
        val effective = contactService.saveCustomProfile(accountId, contact, "Granny", contact.customProfile.avatar)

        assertEquals("Granny", contact.displayName)
        assertEquals("Granny", effective.displayName)
        assertContentEquals(byteArrayOf(5), effective.avatar)

        // Reset the name: the peer's own name is back, the photo stays.
        contactService.saveCustomProfile(accountId, contact, null, contact.customProfile.avatar)
        assertEquals("Margaret", contact.displayName)
        assertContentEquals(byteArrayOf(5), contact.customProfile.avatar)

        // Restored at startup onto a fresh object for the same peer.
        val restored = Contact(Uri.fromId(peer))
        contactService.restoreProfileFromDisk(accountId, restored)
        assertContentEquals(byteArrayOf(5), restored.customProfile.avatar)
        assertNull(restored.customProfile.displayName)
        services.cancel()
    }
}
