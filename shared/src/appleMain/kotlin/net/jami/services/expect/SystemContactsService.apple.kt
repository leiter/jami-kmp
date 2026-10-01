package net.jami.services.expect

/**
 * iOS system address-book integration (CNContactStore) is not implemented yet — this is a
 * stub, not a completed port. Tracked as a follow-up; see gap-analysis-2026-08.md.
 */
actual class SystemContactsService {
    actual fun hasPermission(): Boolean = false
    actual suspend fun findSystemContact(query: String): SystemContactMatch? = null
}
