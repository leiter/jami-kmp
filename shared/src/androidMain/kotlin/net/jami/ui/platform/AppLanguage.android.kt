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

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import net.jami.utils.Log
import java.util.Locale

private const val TAG = "AppLanguage"

/**
 * Android's per-app language.
 *
 * On API 33+ the platform owns it: `LocaleManager` stores the choice, the system Settings app
 * shows it alongside the device languages, and the app is recreated with the new locale. The
 * set of offered languages comes from `res/xml/locales_config.xml`, which is generated from
 * the same folders as [ShippedLocales].
 *
 * Below 33 there is no such store, so the choice lives in [LocalPrefs] and is applied by
 * [wrapContext] from `MainActivity.attachBaseContext` — early enough that the whole activity,
 * resources included, comes up in the chosen language.
 */
actual object AppLanguage {

    /**
     * Set by the app's Android host at startup so this object can reach a Context without
     * taking one from every caller. The application context, so it holds nothing leakable.
     */
    var applicationContext: Context? = null

    actual val appliesImmediately: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    actual fun apply(languageTag: String) {
        // Stored either way: pre-33 this is the only record, and on 33+ it keeps the two in
        // step for a user who downgrades their expectations rather than their OS — and costs
        // one string write.
        LocalPrefs.setString(LocalPrefKeys.APP_LANGUAGE, languageTag)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = applicationContext?.getSystemService(android.app.LocaleManager::class.java)
            if (manager == null) {
                Log.w(TAG, "No LocaleManager: language stored only")
                return
            }
            manager.applicationLocales =
                if (languageTag.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(languageTag)
        }
    }

    actual fun displayName(languageTag: String): String {
        if (languageTag.isEmpty()) return languageTag
        val locale = Locale.forLanguageTag(languageTag)
        // In the language itself: someone looking for Polish is scanning for "polski".
        return locale.getDisplayName(locale).ifBlank { languageTag }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * Wraps [base] in the stored language, for `MainActivity.attachBaseContext` on API < 33.
     *
     * A no-op on 33+, where the platform has already applied the per-app locale before the
     * activity is built, and on an empty stored tag, which means "follow the device".
     */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = LocalPrefs.getString(LocalPrefKeys.APP_LANGUAGE, "")
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
