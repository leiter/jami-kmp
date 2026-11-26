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
package net.jami.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * The plumbing for a shared-element transition between a thumbnail and the full-screen viewer.
 *
 * **Switched off since 2026-11-26:** the graph no longer provides [LocalSharedTransitionScope],
 * because the `SharedTransitionLayout` it needs crashed the iOS app (see the note above the
 * `NavHost` in JamiNavigation). Everything here stays a no-op until that layout comes back,
 * once a Compose Multiplatform update no longer crashes on it.
 *
 * A shared element needs two scopes: the [SharedTransitionScope] that owns the overlay the
 * element flies through, and the [AnimatedVisibilityScope] of the destination being animated in
 * or out. The first belongs to a layout wrapping the whole `NavHost`; the second is the
 * receiver of each `composable {}` block. Neither is near the composable that draws a thumbnail,
 * which sits inside a message bubble inside a list inside a screen.
 *
 * Threading both through every signature in between would put animation parameters on
 * composables that have nothing else to do with animation, so they travel as composition
 * locals instead and [sharedMediaElement] reads them where the image is drawn.
 *
 * Both default to null, and the modifier is then a no-op: a screen rendered outside the main
 * navigation graph — a preview, a test, or a host that has not opted in — draws its images
 * normally rather than crashing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/** The animated destination currently being entered or left; see [LocalSharedTransitionScope]. */
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/**
 * Mark this image as one end of a shared-element transition.
 *
 * [key] has to name the *content*, not the place it is drawn, so that the thumbnail in a chat
 * bubble and the full-screen image in the viewer resolve to the same element. The file's path
 * is that name; [mediaSharedKey] builds it so the two call sites cannot drift apart.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedMediaElement(key: String): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope == null || animatedScope == null) {
        this
    } else {
        with(sharedScope) {
            this@composed.sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope,
            )
        }
    }
}

/** The shared-element key for a media file, so both ends of the transition agree on it. */
fun mediaSharedKey(filePath: String): String = "media:$filePath"
