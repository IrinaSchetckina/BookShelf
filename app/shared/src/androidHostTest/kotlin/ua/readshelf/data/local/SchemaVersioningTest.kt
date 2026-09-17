package ua.readshelf.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import ua.readshelf.db.ReadShelfDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaVersioningTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun freshDatabaseGetsSchemaAndVersion() = runTest {
        driver.createOrMigrate(ReadShelfDatabase.Schema)

        assertEquals(ReadShelfDatabase.Schema.version, driver.userVersion())
        assertEquals(listOf("readingSessionEntity", "trackedBookEntity"), driver.tableNames())
    }

    // A second start must not run CREATE TABLE again: it would fail on the existing tables.
    @Test
    fun secondStartLeavesExistingSchemaAlone() = runTest {
        driver.createOrMigrate(ReadShelfDatabase.Schema)

        driver.createOrMigrate(ReadShelfDatabase.Schema)

        assertEquals(ReadShelfDatabase.Schema.version, driver.userVersion())
    }
}

private fun SqlDriver.userVersion(): Long =
    executeQuery(null, "PRAGMA user_version", { cursor ->
        cursor.next()
        QueryResult.Value(cursor.getLong(0) ?: 0L)
    }, 0).value

private fun SqlDriver.tableNames(): List<String> =
    executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", { cursor ->
        val names = mutableListOf<String>()
        while (cursor.next().value) names += cursor.getString(0).orEmpty()
        QueryResult.Value(names)
    }, 0).value
