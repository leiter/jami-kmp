/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import net.jami.services.expect.HardwareService

internal actual fun HardwareService.daemonStartedCapture(device: String) = onDaemonStartCapture(device)

internal actual fun HardwareService.daemonStoppedCapture(device: String) = onDaemonStopCapture(device)
