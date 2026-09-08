package net.jami

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.jami.services.DaemonBridgeApi
import net.jami.services.IOSCameraService
import net.jami.services.StubDaemonBridge
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

/**
 * Starts a minimal graph satisfying the iOS HardwareService's injected dependencies.
 * Only the two bindings it actually resolves are provided.
 */
actual fun ensurePlatformTestKoin() {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        modules(module {
            single<DaemonBridgeApi> { StubDaemonBridge() }
            single { IOSCameraService(CoroutineScope(SupervisorJob() + Dispatchers.Default), get()) }
        })
    }
}
