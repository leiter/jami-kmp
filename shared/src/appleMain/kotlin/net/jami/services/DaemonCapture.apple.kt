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

/**
 * The daemon's StartCapture/StopCapture signals, which [DaemonBridge] (shared by iOS and macOS)
 * forwards to the platform's [HardwareService].
 *
 * Not members of the common `expect class HardwareService`: only Apple's capture is client-owned
 * in a way that needs them on iOS, and adding them to the common declaration would oblige the
 * Android actual too. Code in appleMain sees only that common declaration, so each Apple target
 * supplies these.
 */
internal expect fun HardwareService.daemonStartedCapture(device: String)

internal expect fun HardwareService.daemonStoppedCapture(device: String)
