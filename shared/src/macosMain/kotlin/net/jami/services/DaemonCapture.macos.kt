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
import net.jami.utils.Log

// On macOS the daemon opens the camera itself (no client-owned capture), so there is
// nothing to start or stop here; logged so a trace shows the signal arrived.
internal actual fun HardwareService.daemonStartedCapture(device: String) {
    Log.d("HardwareService", "daemon started capture on $device (daemon-owned on macOS)")
}

internal actual fun HardwareService.daemonStoppedCapture(device: String) {
    Log.d("HardwareService", "daemon stopped capture on $device (daemon-owned on macOS)")
}
