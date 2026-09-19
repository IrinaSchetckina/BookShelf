package ua.readshelf.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Opens the platform SQLite driver. Each platform supplies its own implementation;
 * the schema is async because the web driver only exists in async form.
 */
fun interface SqlDriverFactory {
    suspend fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver
}
