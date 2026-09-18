package net.jami.services

import net.jami.database.JamiDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SQLDelight-backed durable outbox. Desktop only: the Android JVM unit tests have no SQLite
 * (createTestDriver() throws there).
 */
class SqlDelightOutboxStoreTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
    }

    @Test
    fun storePersistsEntries() {
        val driver = createTestDriver()
        val store = SqlDelightOutboxStore(JamiDatabase(driver))
        val first = store.insert(ACC, CONV, "a", null, 1L)
        store.insert(ACC, CONV, "b", "parent", 2L)
        store.insert(ACC, "other", "c", null, 3L)

        // A second store on the same database (as after a restart) sees the rows.
        val reopened = SqlDelightOutboxStore(JamiDatabase(driver))
        assertEquals(listOf("a", "b"), reopened.forConversation(ACC, CONV).map { it.body })
        assertEquals("parent", reopened.forConversation(ACC, CONV)[1].replyTo)
        assertEquals(3, reopened.all().size)

        reopened.delete(first.id)
        assertEquals(listOf("b"), store.forConversation(ACC, CONV).map { it.body })
        driver.close()
    }
}
