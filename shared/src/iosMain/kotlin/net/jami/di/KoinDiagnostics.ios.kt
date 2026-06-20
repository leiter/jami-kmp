/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.di

import org.koin.core.annotation.KoinInternalApi
import org.koin.mp.KoinPlatform
import platform.Foundation.NSLog
import kotlin.reflect.KClass
import app.cash.sqldelight.db.SqlDriver
import net.jami.database.JamiDatabase
import net.jami.services.BiometricService
import net.jami.services.CallKitManagerApi
import net.jami.services.DaemonBridgeApi
import net.jami.services.DeviceRuntimeService
import net.jami.services.HistoryService
import net.jami.services.IOSCameraService
import net.jami.services.NotificationService
import net.jami.services.PreferencesService
import net.jami.services.Settings
import net.jami.services.SyncManager
import net.jami.services.expect.AudioRecorderService
import net.jami.services.expect.HardwareService

/**
 * Startup diagnostics + resilient ViewModel resolution for the iOS Koin DI path.
 *
 * The recurring startup NoDefinitionFoundException is caused by Kotlin/Native giving
 * a different KClass object (with a different identity-hash fallback name) at the
 * lookup call site than at the registration call site. Koin builds its index key from
 * `KClass.getFullName()`, so the two sites compute different keys and the lookup misses.
 *
 * Fix: don't trust the lookup-site KClass. Scan Koin's OWN registry for the factory
 * whose registered primaryType matches by name, then resolve using that exact
 * registered KClass object — guaranteeing the index key matches.
 *
 * If no matching factory exists in the live registry, we crash via a distinctly-named
 * function so the (message-less) TestFlight crash report still tells us the failure
 * mode by symbol name alone.
 */
// ── KClass probe ────────────────────────────────────────────────────────────
// Build 17 proved startKoin throws inside platformModule registration because
// KClass.hashCode() throws (KClassUnsupportedImpl) for some registered key type.
// Each candidate is tested in registration order; the FIRST whose ::class.hashCode()
// throws routes to a distinctly-named crash function so the (message-less) crash
// report names the exact culprit type by symbol.
private fun bad_DaemonBridgeApi(): Nothing = error("KCLASS DaemonBridgeApi")
private fun bad_SqlDriver(): Nothing = error("KCLASS SqlDriver")
private fun bad_JamiDatabase(): Nothing = error("KCLASS JamiDatabase")
private fun bad_DeviceRuntimeService(): Nothing = error("KCLASS DeviceRuntimeService")
private fun bad_IOSCameraService(): Nothing = error("KCLASS IOSCameraService")
private fun bad_HardwareService(): Nothing = error("KCLASS HardwareService")
private fun bad_AudioRecorderService(): Nothing = error("KCLASS AudioRecorderService")
private fun bad_CallKitManager(): Nothing = error("KCLASS CallKitManager")
private fun bad_NotificationService(): Nothing = error("KCLASS NotificationService")
private fun bad_HistoryService(): Nothing = error("KCLASS HistoryService")
private fun bad_PreferencesService(): Nothing = error("KCLASS PreferencesService")
private fun bad_Settings(): Nothing = error("KCLASS Settings")
private fun bad_BiometricService(): Nothing = error("KCLASS BiometricService")
private fun bad_SyncManager(): Nothing = error("KCLASS SyncManager")

@PublishedApi
internal fun jamiProbePlatformKClasses() {
    runCatching { DaemonBridgeApi::class.hashCode() }.onFailure { bad_DaemonBridgeApi() }
    runCatching { SqlDriver::class.hashCode() }.onFailure { bad_SqlDriver() }
    runCatching { JamiDatabase::class.hashCode() }.onFailure { bad_JamiDatabase() }
    runCatching { DeviceRuntimeService::class.hashCode() }.onFailure { bad_DeviceRuntimeService() }
    runCatching { IOSCameraService::class.hashCode() }.onFailure { bad_IOSCameraService() }
    runCatching { HardwareService::class.hashCode() }.onFailure { bad_HardwareService() }
    runCatching { AudioRecorderService::class.hashCode() }.onFailure { bad_AudioRecorderService() }
    runCatching { CallKitManagerApi::class.hashCode() }.onFailure { bad_CallKitManager() }
    runCatching { NotificationService::class.hashCode() }.onFailure { bad_NotificationService() }
    runCatching { HistoryService::class.hashCode() }.onFailure { bad_HistoryService() }
    runCatching { PreferencesService::class.hashCode() }.onFailure { bad_PreferencesService() }
    runCatching { Settings::class.hashCode() }.onFailure { bad_Settings() }
    runCatching { BiometricService::class.hashCode() }.onFailure { bad_BiometricService() }
    runCatching { SyncManager::class.hashCode() }.onFailure { bad_SyncManager() }
    jamiKoinLog("JAMI_KCLASS probe: all platform key types OK")
}

internal fun jamiKoinLog(message: String) {
    println(message)
    // NSLog's "%@" vararg path does NOT bridge a Kotlin String to NSString on
    // Kotlin/Native — pass the message as the format string with '%' escaped instead.
    NSLog(message.replace("%", "%%"))
}

/** Crash markers — their names surface in the backtrace of the (message-less) crash report. */
private fun jamiCrash_HolderKoinNull(name: String?): Nothing =
    error("JAMI_KOIN held koin is NULL when resolving '$name'")

private fun jamiCrash_KoinRegistryEmpty(name: String?): Nothing =
    error("JAMI_KOIN registry EMPTY when resolving '$name'")

private fun jamiCrash_ViewModelNotInRegistry(name: String?): Nothing =
    error("JAMI_KOIN ViewModel '$name' NOT in non-empty registry")

@PublishedApi
@OptIn(KoinInternalApi::class)
internal fun jamiResolveViewModel(reqQualifiedName: String?, reqSimpleName: String?): Any {
    // Diagnostic comparison: the global-context koin vs the koin we explicitly captured
    // from initKoin's return value. Build 15 proved the global-context registry is empty
    // at lookup, so we resolve from the captured instance instead.
    val globalKoin = KoinPlatform.getKoinOrNull()
    val heldKoin = JamiKoinHolder.koin
    jamiKoinLog(
        "JAMI_KOIN_GET resolve simpleName=$reqSimpleName qn=$reqQualifiedName " +
            "globalKoin=${globalKoin?.hashCode()} globalSize=${globalKoin?.instanceRegistry?.instances?.size} " +
            "heldKoin=${heldKoin?.hashCode()} heldSize=${heldKoin?.instanceRegistry?.instances?.size}"
    )

    val koin = heldKoin ?: jamiCrash_HolderKoinNull(reqSimpleName)
    val instances = koin.instanceRegistry.instances
    instances.keys.sorted().forEach { jamiKoinLog("JAMI_KOIN_GET   regKey=$it") }

    if (instances.isEmpty()) jamiCrash_KoinRegistryEmpty(reqSimpleName)

    val match = instances.values.firstOrNull { f ->
        val pt = f.beanDefinition.primaryType
        (reqQualifiedName != null && pt.qualifiedName == reqQualifiedName) ||
            (reqSimpleName != null && pt.simpleName == reqSimpleName)
    } ?: jamiCrash_ViewModelNotInRegistry(reqSimpleName)

    val primaryType = match.beanDefinition.primaryType
    jamiKoinLog(
        "JAMI_KOIN_GET matched primaryType=${primaryType.simpleName} " +
            "hash=${primaryType.hashCode()} — resolving via registered KClass"
    )
    // Resolve with the EXACT registered KClass object → index key is guaranteed to match.
    return koin.get(primaryType)
}
