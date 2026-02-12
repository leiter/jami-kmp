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
package net.jami.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VCardUtilsTest {

    @Test
    fun testParseSimpleVCard() {
        val vcardString = """
            BEGIN:VCARD
            VERSION:2.1
            FN:John Doe
            UID:jami:abc123
            END:VCARD
        """.trimIndent()

        val vcard = VCardUtils.parseVCard(vcardString)

        assertNotNull(vcard)
        assertEquals("John Doe", vcard.formattedName)
        assertEquals("jami:abc123", vcard.uid)
        assertNull(vcard.photo)
    }

    @Test
    fun testParseVCardCaseInsensitive() {
        val vcardString = """
            begin:vcard
            version:2.1
            fn:Jane Smith
            uid:test-uid
            end:vcard
        """.trimIndent()

        val vcard = VCardUtils.parseVCard(vcardString)

        assertNotNull(vcard)
        assertEquals("Jane Smith", vcard.formattedName)
        assertEquals("test-uid", vcard.uid)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testParseVCardWithPhoto() {
        val photoData = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val base64Photo = Base64.encode(photoData)

        val vcardString = """
            BEGIN:VCARD
            VERSION:2.1
            FN:Photo User
            PHOTO;ENCODING=BASE64;TYPE=JPEG:$base64Photo
            END:VCARD
        """.trimIndent()

        val vcard = VCardUtils.parseVCard(vcardString)

        assertNotNull(vcard)
        assertEquals("Photo User", vcard.formattedName)
        assertNotNull(vcard.photo)
        assertTrue(vcard.photo!!.contentEquals(photoData))
        assertEquals("JPEG", vcard.photoType)
    }

    @Test
    fun testParseInvalidVCard() {
        val invalid = "This is not a vcard"
        val vcard = VCardUtils.parseVCard(invalid)
        assertNull(vcard)
    }

    @Test
    fun testVcardToString() {
        val vcard = VCard(
            formattedName = "Test User",
            uid = "test-uid-123",
            version = "2.1"
        )

        val vcardString = VCardUtils.vcardToString(vcard)

        assertNotNull(vcardString)
        assertTrue(vcardString.contains("BEGIN:VCARD"))
        assertTrue(vcardString.contains("END:VCARD"))
        assertTrue(vcardString.contains("FN:Test User"))
        assertTrue(vcardString.contains("UID:test-uid-123"))
        assertTrue(vcardString.contains("VERSION:2.1"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testVcardToStringWithPhoto() {
        val photoData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val vcard = VCard(
            formattedName = "Photo Test",
            photo = photoData,
            photoType = "JPEG"
        )

        val vcardString = VCardUtils.vcardToString(vcard)

        assertNotNull(vcardString)
        assertTrue(vcardString.contains("PHOTO;ENCODING=BASE64;TYPE=JPEG:"))
        assertTrue(vcardString.contains(Base64.encode(photoData)))
    }

    @Test
    fun testRoundTrip() {
        val original = VCard(
            formattedName = "Round Trip",
            uid = "round-trip-uid"
        )

        val vcardString = VCardUtils.vcardToString(original)
        val parsed = VCardUtils.parseVCard(vcardString!!)

        assertNotNull(parsed)
        assertEquals(original.formattedName, parsed.formattedName)
        assertEquals(original.uid, parsed.uid)
    }

    @Test
    fun testWriteData() {
        val vcard = VCardUtils.writeData(
            uri = "jami:xyz789",
            displayName = "Created User",
            picture = byteArrayOf(0x00, 0x11)
        )

        assertEquals("Created User", vcard.formattedName)
        assertEquals("jami:xyz789", vcard.uid)
        assertNotNull(vcard.photo)
        assertEquals("JPEG", vcard.photoType)
    }

    @Test
    fun testReadData() {
        val vcard = VCard(
            formattedName = "Read Test",
            photo = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        )

        val (name, photo) = VCardUtils.readData(vcard)

        assertEquals("Read Test", name)
        assertNotNull(photo)
        assertEquals(2, photo.size)
    }

    @Test
    fun testReadDataNull() {
        val (name, photo) = VCardUtils.readData(null)
        assertNull(name)
        assertNull(photo)
    }

    @Test
    fun testParseMimeAttributes() {
        val mime = "text/x-vcf;part=1,total=3"
        val attrs = VCardUtils.parseMimeAttributes(mime)

        assertEquals("text/x-vcf", attrs[VCardUtils.VCARD_KEY_MIME_TYPE])
        assertEquals("1", attrs["part"])
        assertEquals("3", attrs["total"])
    }

    @Test
    fun testParseMimeAttributesInvalid() {
        val mime = "text/plain"
        val attrs = VCardUtils.parseMimeAttributes(mime)
        assertTrue(attrs.isEmpty())
    }

    @Test
    fun testPictureTypeFromMime() {
        assertEquals("JPEG", VCardUtils.pictureTypeFromMime("image/jpeg"))
        assertEquals("PNG", VCardUtils.pictureTypeFromMime("image/png"))
        assertEquals("GIF", VCardUtils.pictureTypeFromMime("image/gif"))
        assertEquals("JPEG", VCardUtils.pictureTypeFromMime("image/unknown"))
        assertEquals("", VCardUtils.pictureTypeFromMime(null))
    }

    @Test
    fun testVCardIsEmpty() {
        val empty1 = VCard()
        assertTrue(VCardUtils.isEmpty(empty1))
        assertTrue(empty1.isEmpty)

        val empty2 = VCard(formattedName = "")
        assertTrue(VCardUtils.isEmpty(empty2))

        val notEmpty1 = VCard(formattedName = "Name")
        assertFalse(VCardUtils.isEmpty(notEmpty1))

        val notEmpty2 = VCard(photo = byteArrayOf(0x01))
        assertFalse(VCardUtils.isEmpty(notEmpty2))
    }

    @Test
    fun testDefaultProfile() {
        val profile = VCardUtils.defaultProfile("account123")
        assertEquals("account123", profile.uid)
        assertNull(profile.formattedName)
        assertNull(profile.photo)
    }

    @Test
    fun testVCardEquality() {
        val vcard1 = VCard(formattedName = "Test", uid = "uid1", photo = byteArrayOf(0x01))
        val vcard2 = VCard(formattedName = "Test", uid = "uid1", photo = byteArrayOf(0x01))
        val vcard3 = VCard(formattedName = "Test", uid = "uid2", photo = byteArrayOf(0x01))

        assertEquals(vcard1, vcard2)
        assertEquals(vcard1.hashCode(), vcard2.hashCode())
        assertFalse(vcard1 == vcard3)
    }
}
