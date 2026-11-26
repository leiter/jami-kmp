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

import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode

/**
 * Apple platforms: the app's language is the `AppleLanguages` default, which Foundation reads
 * when the process starts. Writing it is the supported way to override the app's language
 * without touching the device's, and it takes effect at the next launch — bundles, including
 * the one Compose resources resolve against, are loaded once.
 */
actual object AppLanguage {

    private const val APPLE_LANGUAGES = "AppleLanguages"

    actual val appliesImmediately: Boolean = false

    actual fun apply(languageTag: String) {
        LocalPrefs.setString(LocalPrefKeys.APP_LANGUAGE, languageTag)
        val defaults = NSUserDefaults.standardUserDefaults
        if (languageTag.isEmpty()) defaults.removeObjectForKey(APPLE_LANGUAGES)
        else defaults.setObject(listOf(languageTag), APPLE_LANGUAGES)
        defaults.synchronize()
    }

    actual fun displayName(languageTag: String): String {
        if (languageTag.isEmpty()) return languageTag
        // Asked of the locale itself, so the name comes back in that language.
        val locale = NSLocale(localeIdentifier = languageTag)
        val code = languageTag.substringBefore('-')
        return locale.localizedStringForLanguageCode(code)
            ?: NSLocale.currentLocale.localizedStringForLanguageCode(code)
            ?: languageTag
    }
}
