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
import org.koin.core.context.GlobalContext

/**
 * Android implementation of [LocalPrefs] backed by the Koin-registered [Settings] singleton
 * (which wraps SharedPreferences). Lazily resolved to avoid accessing Koin before it starts.
 */
actual object LocalPrefs {
    private val settings: Settings by lazy {
        GlobalContext.get().get()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        settings.getBoolean(key, default)

    actual fun setBoolean(key: String, value: Boolean) =
        settings.setBoolean(key, value)
}
