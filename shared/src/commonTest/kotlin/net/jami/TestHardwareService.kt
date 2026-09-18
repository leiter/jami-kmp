package net.jami

import net.jami.services.expect.HardwareService

/**
 * A [HardwareService] usable in common tests. Desktop/iOS/macOS/JS construct it directly; the
 * Android actual needs a Context, which JVM unit tests don't have, so it gets a placeholder
 * (Android stubs return default values — see testOptions.unitTests.isReturnDefaultValues).
 */
expect fun testHardwareService(): HardwareService
