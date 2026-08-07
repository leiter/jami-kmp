package net.jami.services.expect

/**
 * Web has no system address-book access.
 */
actual class SystemContactsService {
    actual fun hasPermission(): Boolean = false
    actual suspend fun findSystemContact(query: String): SystemContactMatch? = null
}
