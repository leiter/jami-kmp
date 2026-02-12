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

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of Settings using SharedPreferences.
 */
actual class Settings(context: Context, name: String = "jami_settings") {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    actual fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    actual fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    actual fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    actual fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    actual fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    actual fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun getFloat(key: String, defaultValue: Float): Float =
        prefs.getFloat(key, defaultValue)

    actual fun setFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    actual fun contains(key: String): Boolean =
        prefs.contains(key)

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }

    actual fun getAllKeys(): Set<String> =
        prefs.all.keys
}
