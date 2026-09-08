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
package net.jami.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether a system Activity result (file picker, camera capture, ringtone picker, …) is
 * currently in flight from a composable living under the biometric-lock-gated part of the nav
 * graph (Android only — see `JamiNavigation.kt`'s `isLocked` early return, which swaps the whole
 * `MainNavigation` tree for `BiometricLockScreen`).
 *
 * That swap disposes whatever composable launched the system Activity, and with it its
 * `rememberLauncherForActivityResult` — dropping the pending result silently if it arrives after
 * disposal. `AppViewModel.lockIfNeeded()` consults [hasPending] to defer applying the lock until
 * any in-flight result has actually been delivered, instead of locking the instant the app goes
 * to `ON_STOP` (which is also the instant a system picker Activity comes to the foreground).
 *
 * A plain process-wide singleton rather than a Koin service: every caller just needs to
 * increment/decrement one shared counter, and `AppViewModel` needs to read it — no per-instance
 * state, no lifecycle of its own, so DI would only add ceremony (and risk breaking existing
 * `AppViewModel(...)` test call sites with a new constructor parameter).
 *
 * A no-op elsewhere (iOS/macOS/Desktop/Web don't dispose the launching composable this way), but
 * implemented in commonMain since [net.jami.ui.viewmodel.AppViewModel] is common and the
 * bookkeeping itself is trivial cross-platform.
 */
object PendingActivityResultTracker {
    private var count = 0
    private val _hasPending = MutableStateFlow(false)
    val hasPending: StateFlow<Boolean> = _hasPending

    /** Call immediately before launching a system Activity whose result must not be dropped. */
    fun begin() {
        count++
        _hasPending.value = true
    }

    /** Call as the first thing in the Activity-result callback — success, cancel, or failure alike. */
    fun end() {
        count = (count - 1).coerceAtLeast(0)
        _hasPending.value = count > 0
    }
}
