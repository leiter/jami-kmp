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
package net.jami.ui.platform

import net.jami.services.Settings

/**
 * macOS implementation of [LocalPrefs] backed by NSUserDefaults via [Settings].
 */
actual object LocalPrefs {
    private val settings = Settings()

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        settings.getBoolean(key, default)

    actual fun setBoolean(key: String, value: Boolean) =
        settings.setBoolean(key, value)

    actual fun getInt(key: String, default: Int): Int =
        settings.getInt(key, default)

    actual fun setInt(key: String, value: Int) =
        settings.setInt(key, value)

    actual fun getString(key: String, default: String): String =
        settings.getString(key, default)

    actual fun setString(key: String, value: String) =
        settings.setString(key, value)
}
