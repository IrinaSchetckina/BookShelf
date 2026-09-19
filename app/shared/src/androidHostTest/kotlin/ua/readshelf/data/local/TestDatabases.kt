package ua.readshelf.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ua.readshelf.db.ReadShelfDatabase
import java.io.File
import java.util.Properties

/**
 * A file-backed SQLite database for host tests. Unlike an in-memory one it can be
 * closed and opened again, which is what "survives an app restart" means for storage.
 *
 * Schema versioning goes through [createOrMigrate], the same path the web worker uses,
 * so reopening also proves the schema is not recreated over existing data.
 *
 * Foreign keys are switched on (SQLite leaves them off by default) so that a cascading key
 * added to the schema later would show up in the tests instead of being silently ignored.
 */
internal class TestDatabaseFile {

    private val file: File = File.createTempFile("readshelf-test", ".db").also { it.deleteOnExit() }
    private val drivers = mutableListOf<SqlDriver>()

    suspend fun open(): ReadShelfDatabase =
        createReadShelfDatabase { schema ->
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties().apply { setProperty("foreign_keys", "true") })
                .also { it.createOrMigrate(schema) }
                .also { drivers += it }
        }

    /** Closes every connection opened so far, as a process exit would. */
    fun closeAll() {
        drivers.forEach { it.close() }
        drivers.clear()
    }

    fun delete() {
        closeAll()
        file.delete()
    }
}
