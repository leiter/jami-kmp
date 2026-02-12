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

import platform.Foundation.NSUserDefaults

/**
 * macOS implementation of Settings using NSUserDefaults.
 * Same as iOS since both use Foundation framework.
 */
actual class Settings(private val suiteName: String? = null) {
    private val defaults: NSUserDefaults = suiteName?.let {
        NSUserDefaults(suiteName = it)
    } ?: NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    actual fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getInt(key: String, defaultValue: Int): Int {
        return if (defaults.objectForKey(key) != null) {
            defaults.integerForKey(key).toInt()
        } else {
            defaultValue
        }
    }

    actual fun setInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
        defaults.synchronize()
    }

    actual fun getLong(key: String, defaultValue: Long): Long {
        return if (defaults.objectForKey(key) != null) {
            defaults.integerForKey(key)
        } else {
            defaultValue
        }
    }

    actual fun setLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            defaultValue
        }
    }

    actual fun setBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getFloat(key: String, defaultValue: Float): Float {
        return if (defaults.objectForKey(key) != null) {
            defaults.floatForKey(key)
        } else {
            defaultValue
        }
    }

    actual fun setFloat(key: String, value: Float) {
        defaults.setFloat(value, forKey = key)
        defaults.synchronize()
    }

    actual fun contains(key: String): Boolean =
        defaults.objectForKey(key) != null

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }

    actual fun clear() {
        val dictionary = defaults.dictionaryRepresentation()
        dictionary.keys.forEach { key ->
            (key as? String)?.let { defaults.removeObjectForKey(it) }
        }
        defaults.synchronize()
    }

    actual fun getAllKeys(): Set<String> =
        defaults.dictionaryRepresentation().keys.mapNotNull { it as? String }.toSet()
}
