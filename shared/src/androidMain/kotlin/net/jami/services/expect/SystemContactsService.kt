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
package net.jami.services.expect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri as AndroidUri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jami.model.Uri
import net.jami.utils.Log

actual class SystemContactsService(private val context: Context) {

    actual fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    actual suspend fun findSystemContact(query: String): SystemContactMatch? {
        if (query.isEmpty() || !hasPermission()) return null
        return withContext(Dispatchers.IO) {
            // A phone-style number resolves through PhoneLookup (handles normalization);
            // hex IDs/usernames never match a phone number, so go straight to the SIP/IM path.
            val byPhone = if (!Uri.fromString(query).isHexId) findByPhoneLookup(query) else null
            byPhone ?: findBySipOrImAddress(query)
        }
    }

    /** Mirrors ContactServiceImpl.findContactByNumberFromSystem(). */
    private fun findByPhoneLookup(number: String): SystemContactMatch? {
        val uri = AndroidUri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            AndroidUri.encode(number)
        )
        try {
            context.contentResolver.query(uri, PHONELOOKUP_PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val key = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.LOOKUP_KEY))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                    val photoId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_ID))
                    return SystemContactMatch(id, key, name, photoId, fillPhoneNumbers(id))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "findByPhoneLookup: error looking up $number: $e")
        }
        return null
    }

    /** Mirrors ContactServiceImpl.findContactBySipNumberFromSystem(). */
    private fun findBySipOrImAddress(address: String): SystemContactMatch? {
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                DATA_PROJECTION,
                "${ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS}=? AND (" +
                    "${ContactsContract.Data.MIMETYPE}=? OR ${ContactsContract.Data.MIMETYPE}=?)",
                arrayOf(
                    address,
                    ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
                ),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID))
                    val key = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.LOOKUP_KEY))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME_PRIMARY))
                    val photoId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_ID))
                    val phones = fillPhoneNumbers(id)
                    if (phones.isEmpty()) return null
                    return SystemContactMatch(id, key, name, photoId, phones)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "findBySipOrImAddress: error looking up $address: $e")
        }
        return null
    }

    /** Mirrors ContactServiceImpl.fillContactDetails() — plain phone numbers + SIP/IM addresses. */
    private fun fillPhoneNumbers(systemContactId: Long): List<SystemContactNumber> {
        val numbers = mutableListOf<SystemContactNumber>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PHONES_PROJECTION,
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(systemContactId.toString()),
                null
            )?.use { cursor ->
                val indexNumber = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val indexType = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val indexLabel = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
                while (cursor.moveToNext()) {
                    numbers.add(
                        SystemContactNumber(
                            number = cursor.getString(indexNumber),
                            category = cursor.getInt(indexType),
                            label = cursor.getString(indexLabel),
                            isSip = false
                        )
                    )
                }
            }

            val dataUri = AndroidUri.withAppendedPath(
                android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, systemContactId),
                ContactsContract.Contacts.Data.CONTENT_DIRECTORY
            )
            context.contentResolver.query(
                dataUri,
                SIP_PROJECTION,
                "${ContactsContract.Data.MIMETYPE}=? OR ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(
                    ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
                ),
                null
            )?.use { cursor ->
                val indexMime = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                val indexAddress = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS)
                val indexType = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.SipAddress.TYPE)
                val indexLabel = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.SipAddress.LABEL)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(indexMime)
                    val address = cursor.getString(indexAddress) ?: continue
                    val label = cursor.getString(indexLabel)
                    // Only trust an Im row as a Jami address if it's hex-shaped or explicitly
                    // labelled "ring" — an arbitrary IM row (e.g. a Skype handle) is not one.
                    val isJamiIm = mime == ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE &&
                        (Uri.fromString(address).isHexId || label.equals("ring", ignoreCase = true))
                    if (mime == ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE || isJamiIm) {
                        numbers.add(
                            SystemContactNumber(
                                number = address,
                                category = cursor.getInt(indexType),
                                label = label,
                                isSip = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "fillPhoneNumbers: error reading numbers for contact $systemContactId: $e")
        }
        return numbers
    }

    companion object {
        private const val TAG = "SystemContactsService"

        private val PHONELOOKUP_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Contacts.PHOTO_ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        private val DATA_PROJECTION = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_ID
        )
        private val PHONES_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        private val SIP_PROJECTION = arrayOf(
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
            ContactsContract.CommonDataKinds.SipAddress.TYPE,
            ContactsContract.CommonDataKinds.SipAddress.LABEL
        )
    }
}
