package net.jami

/**
 * Starts a minimal Koin graph when the platform's service implementations need one.
 *
 * The iOS HardwareService is a KoinComponent that injects DaemonBridgeApi and
 * IOSCameraService, so constructing it in a test without Koin running throws
 * "KoinApplication has not been started". Desktop's actual has no such dependency.
 *
 * Idempotent — safe to call from every @BeforeTest.
 */
expect fun ensurePlatformTestKoin()
