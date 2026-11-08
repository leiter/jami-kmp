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
package net.jami

import android.content.ContextWrapper
import net.jami.services.expect.HardwareService

/**
 * Under Robolectric this is a real Context, so the service's AudioManager and PowerManager
 * calls run for real instead of against android.jar stubs. Elsewhere it stays the placeholder
 * wrapper the non-Robolectric Android unit tests have always used.
 */
actual fun testHardwareService(): HardwareService =
    HardwareService(robolectricApplication() ?: ContextWrapper(null))
