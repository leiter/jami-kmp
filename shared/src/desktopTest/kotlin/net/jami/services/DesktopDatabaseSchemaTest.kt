package net.jami.services

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.jami.database.DatabaseDriverFactory
import net.jami.database.JamiDatabase
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The desktop driver opens existing database files instead of recreating the schema on every start. */
class DesktopDatabaseSchemaTest {

    private val dir = Files.createTempDirectory("jami-db-test").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun open(): SqlDriver = DatabaseDriverFactory(dir.absolutePath).createDriver()

    private fun SqlDriver.userVersion(): Long =
        executeQuery(null, "PRAGMA user_version", { c -> c.next(); QueryResult.Value(c.getLong(0)!!) }, 0).value

    private fun storeFor(driver: SqlDriver) = SqlDelightOutboxStore(JamiDatabase(driver))

    @Test
    fun freshDatabaseIsCreatedAtTheCurrentVersion() {
        val driver = open()
        assertEquals(JamiDatabase.Schema.version, driver.userVersion())
        driver.close()
    }

    @Test
    fun reopeningKeepsTheData() {
        open().also { storeFor(it).insert("acc", "conv", "kept", null, 1L); it.close() }
        val reopened = open()
        assertEquals(listOf("kept"), storeFor(reopened).all().map { it.body })
        reopened.close()
    }

    @Test
    fun unversionedVersion1DatabaseIsMigrated() {
        // What the old factory left behind: the v1 tables, user_version never set.
        JdbcSqliteDriver("jdbc:sqlite:${dir.absolutePath}/jami.db").also { legacy ->
            JamiDatabase.Schema.create(legacy)
            legacy.execute(null, "DROP TABLE outbox_message", 0)
            legacy.close()
        }
        val driver = open()
        assertEquals(JamiDatabase.Schema.version, driver.userVersion())
        storeFor(driver).insert("acc", "conv", "after migration", null, 1L)
        assertEquals(1, storeFor(driver).all().size)
        driver.close()
    }

    @Test
    fun unversionedCurrentDatabaseIsAdoptedAsIs() {
        JdbcSqliteDriver("jdbc:sqlite:${dir.absolutePath}/jami.db").also { legacy ->
            JamiDatabase.Schema.create(legacy)
            SqlDelightOutboxStore(JamiDatabase(legacy)).insert("acc", "conv", "old row", null, 1L)
            legacy.close()
        }
        val driver = open()
        assertEquals(JamiDatabase.Schema.version, driver.userVersion())
        assertEquals(listOf("old row"), storeFor(driver).all().map { it.body })
        driver.close()
    }
}
