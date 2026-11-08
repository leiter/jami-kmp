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

import java.util.Locale

/**
 * Desktop: [Locale.setDefault] is what `androidx.compose.ui.text.intl.Locale.current` reads
 * on the JVM, so the choice is applied there and re-applied at startup from [LocalPrefs].
 *
 * Not immediate: a composition already running keeps the resources it resolved, and there is
 * no per-process locale change notification to hang a recomposition off.
 */
actual object AppLanguage {

    actual val appliesImmediately: Boolean = false

    actual fun apply(languageTag: String) {
        LocalPrefs.setString(LocalPrefKeys.APP_LANGUAGE, languageTag)
        if (languageTag.isNotEmpty()) Locale.setDefault(Locale.forLanguageTag(languageTag))
    }

    actual fun displayName(languageTag: String): String {
        if (languageTag.isEmpty()) return languageTag
        val locale = Locale.forLanguageTag(languageTag)
        return locale.getDisplayName(locale).ifBlank { languageTag }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** Called once at startup, before any UI, so a stored choice survives a restart. */
    fun applyStored() {
        val tag = LocalPrefs.getString(LocalPrefKeys.APP_LANGUAGE, "")
        if (tag.isNotEmpty()) Locale.setDefault(Locale.forLanguageTag(tag))
    }
}
