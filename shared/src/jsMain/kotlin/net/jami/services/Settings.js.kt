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

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * JavaScript implementation of Settings using localStorage.
 */
actual class Settings(private val prefix: String = "jami_") {

    private fun prefixedKey(key: String): String = "$prefix$key"

    actual fun getString(key: String, defaultValue: String): String =
        localStorage[prefixedKey(key)] ?: defaultValue

    actual fun setString(key: String, value: String) {
        localStorage[prefixedKey(key)] = value
    }

    actual fun getInt(key: String, defaultValue: Int): Int =
        localStorage[prefixedKey(key)]?.toIntOrNull() ?: defaultValue

    actual fun setInt(key: String, value: Int) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun getLong(key: String, defaultValue: Long): Long =
        localStorage[prefixedKey(key)]?.toLongOrNull() ?: defaultValue

    actual fun setLong(key: String, value: Long) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val value = localStorage[prefixedKey(key)] ?: return defaultValue
        return value == "true"
    }

    actual fun setBoolean(key: String, value: Boolean) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun getFloat(key: String, defaultValue: Float): Float =
        localStorage[prefixedKey(key)]?.toFloatOrNull() ?: defaultValue

    actual fun setFloat(key: String, value: Float) {
        localStorage[prefixedKey(key)] = value.toString()
    }

    actual fun contains(key: String): Boolean =
        localStorage[prefixedKey(key)] != null

    actual fun remove(key: String) {
        localStorage.removeItem(prefixedKey(key))
    }

    actual fun clear() {
        val keysToRemove = mutableListOf<String>()
        for (i in 0 until localStorage.length) {
            val key = localStorage.key(i)
            if (key?.startsWith(prefix) == true) {
                keysToRemove.add(key)
            }
        }
        keysToRemove.forEach { localStorage.removeItem(it) }
    }

    actual fun getAllKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        for (i in 0 until localStorage.length) {
            val key = localStorage.key(i)
            if (key?.startsWith(prefix) == true) {
                keys.add(key.removePrefix(prefix))
            }
        }
        return keys
    }
}
