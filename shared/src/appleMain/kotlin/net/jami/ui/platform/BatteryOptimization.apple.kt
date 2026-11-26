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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS has no app-grantable background exemption: the system decides when a suspended app runs, and VoIP continuity comes from PushKit instead.
 */
private object NoBatteryOptimization : BatteryOptimizationState {
    override val isSupported = false
    override val isExempt = true
    override fun request() = Unit
}

@Composable
actual fun rememberBatteryOptimizationState(): BatteryOptimizationState =
    remember { NoBatteryOptimization }
