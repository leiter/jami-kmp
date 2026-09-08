package net.jami.services.expect

/**
 * Desktop (JVM) has no system address-book integration.
 */
actual class SystemContactsService {
    actual fun hasPermission(): Boolean = false
    actual suspend fun findSystemContact(query: String): SystemContactMatch? = null
}
