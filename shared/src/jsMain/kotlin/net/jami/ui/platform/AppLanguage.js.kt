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

/**
 * Web: the browser's language list is the page's language and a page cannot change its own.
 * The choice is stored so the rest of the app can read it, and nothing else happens — which
 * is why the picker says the choice applies at the next launch here too.
 */
actual object AppLanguage {

    actual val appliesImmediately: Boolean = false

    actual fun apply(languageTag: String) {
        LocalPrefs.setString(LocalPrefKeys.APP_LANGUAGE, languageTag)
    }

    actual fun displayName(languageTag: String): String = languageTag
}
