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

import java.util.prefs.Preferences

/**
 * Desktop (JVM) implementation of Settings using java.util.prefs.Preferences.
 */
actual class Settings(nodeName: String = "net/jami") {
    private val prefs: Preferences = Preferences.userRoot().node(nodeName)

    actual fun getString(key: String, defaultValue: String): String =
        prefs.get(key, defaultValue)

    actual fun setString(key: String, value: String) {
        prefs.put(key, value)
        prefs.flush()
    }

    actual fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    actual fun setInt(key: String, value: Int) {
        prefs.putInt(key, value)
        prefs.flush()
    }

    actual fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    actual fun setLong(key: String, value: Long) {
        prefs.putLong(key, value)
        prefs.flush()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    actual fun setBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
        prefs.flush()
    }

    actual fun getFloat(key: String, defaultValue: Float): Float =
        prefs.getFloat(key, defaultValue)

    actual fun setFloat(key: String, value: Float) {
        prefs.putFloat(key, value)
        prefs.flush()
    }

    actual fun contains(key: String): Boolean =
        prefs.get(key, null) != null

    actual fun remove(key: String) {
        prefs.remove(key)
        prefs.flush()
    }

    actual fun clear() {
        prefs.clear()
        prefs.flush()
    }

    actual fun getAllKeys(): Set<String> =
        prefs.keys().toSet()
}
