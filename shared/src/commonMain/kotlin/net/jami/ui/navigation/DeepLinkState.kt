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
package net.jami.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton that carries an incoming deep-link query (Android ACTION_VIEW / ACTION_DIAL for
 * the ring:, jami:, sip:, tel: schemes) from the platform layer into the Compose navigation
 * graph.
 *
 * Mirrors [ShareState]. Mimics jami-android-client's HomeFragment.handleIntent(), which
 * pre-fills the search box with the URI's identifier rather than placing a call directly —
 * the target of an externally-supplied link is not trusted enough to auto-dial.
 *
 * Lifecycle:
 *  1. Platform code strips the URI scheme down to the bare identifier (Jami hash ID,
 *     username, or SIP/tel address) and calls [request].
 *  2. JamiNavigation observes [navigateToSearch] and navigates to SearchScreen, then calls
 *     [consumeNavSignal].
 *  3. SearchScreen calls [consumeQuery] on entry to pre-fill and run the search.
 */
object DeepLinkState {
    private val _navigateToSearch = MutableStateFlow(false)
    val navigateToSearch: StateFlow<Boolean> = _navigateToSearch.asStateFlow()

    var pendingQuery: String? = null
        private set

    /** Called by the platform layer with the scheme-stripped identifier. */
    fun request(query: String) {
        pendingQuery = query
        _navigateToSearch.value = true
    }

    /** Consumed by JamiNavigation once it has navigated to Search. */
    fun consumeNavSignal() {
        _navigateToSearch.value = false
    }

    /** Returns and clears the pending query. */
    fun consumeQuery(): String? {
        val q = pendingQuery
        pendingQuery = null
        return q
    }
}
