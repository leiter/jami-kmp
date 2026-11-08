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
 * The app's display language, independent of the device's.
 *
 * The app ships 95 translations ([ShippedLocales]) and, until now, offered no way to pick one:
 * `UiSettings.language` was stored and read by nothing.
 *
 * Compose resources select a translation from `androidx.compose.ui.text.intl.Locale.current`,
 * and the one hook for overriding that — `LocalComposeEnvironment` — is `internal` to the
 * resources library. So the language is set the way each platform sets it, below the UI
 * toolkit, which also means strings fetched outside composition (notifications) follow along.
 */
expect object AppLanguage {

    /**
     * True when this platform applies a language change to the running UI.
     *
     * False means the choice is stored and takes effect at the next launch, which the picker
     * says out loud rather than leaving the user tapping a setting that appears to do nothing.
     */
    val appliesImmediately: Boolean

    /**
     * Applies [languageTag] (a BCP-47 tag, or empty for the system language) as the app's
     * language, and persists it with the platform where the platform owns that.
     */
    fun apply(languageTag: String)

    /**
     * The name of [languageTag] in that language itself — "Deutsch", not "German" — which is
     * what someone looking for their own language scans for. Falls back to the tag.
     */
    fun displayName(languageTag: String): String
}
