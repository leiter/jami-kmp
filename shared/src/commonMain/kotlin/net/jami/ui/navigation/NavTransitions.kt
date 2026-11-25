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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

// Screen-change motion for the NavHosts in JamiNavigation.
//
// Mirrors jami-android-client: a screen pushed onto the stack slides in from the trailing edge
// while the one below slides out to the leading edge, and Back plays it in reverse
// (BaseSupportFragment.replaceFragmentWithSlide: slide_in_right / slide_out_left /
// slide_in_left / slide_out_right, decelerate interpolator, @integer/config_activityDefaultDur).
// Start/End rather than left/right, so the direction flips in right-to-left languages.
//
// Full-screen overlays fade instead: the media viewer, whose image already flies out of its
// bubble as a shared element (a slide underneath would fight that), the video player, and the
// call screens, which take over the whole display as a separate activity would.

/** Android's config_activityDefaultDur: the reference's slide duration. */
internal const val NAV_TRANSITION_MS = 220

private val overlayRoutes = setOf(
    Screen.MediaViewer.route,
    Screen.VideoPlayer.route,
    Screen.Call.ROUTE,
    Screen.ViewCall.ROUTE,
)

internal fun isOverlayRoute(route: String?): Boolean = route in overlayRoutes

private fun <T> slideSpec() = tween<T>(NAV_TRANSITION_MS, easing = LinearOutSlowInEasing)

// Navigation Compose's own default (fadeIn/fadeOut over 700 ms), kept for the overlays: the media
// shared element flies for as long as this transition runs, and its timing was checked on devices
// (doc/manual-test-plan-2026-11-19.md, section A) with this duration.
private const val OVERLAY_FADE_MS = 700
private fun <T> fadeSpec() = tween<T>(OVERLAY_FADE_MS)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(): EnterTransition =
    if (isOverlayRoute(targetState.destination.route)) fadeIn(fadeSpec())
    else slideIntoContainer(SlideDirection.Start, slideSpec())

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(): ExitTransition =
    if (isOverlayRoute(targetState.destination.route)) fadeOut(fadeSpec())
    else slideOutOfContainer(SlideDirection.Start, slideSpec())

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnter(): EnterTransition =
    if (isOverlayRoute(initialState.destination.route)) fadeIn(fadeSpec())
    else slideIntoContainer(SlideDirection.End, slideSpec())

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExit(): ExitTransition =
    if (isOverlayRoute(initialState.destination.route)) fadeOut(fadeSpec())
    else slideOutOfContainer(SlideDirection.End, slideSpec())
