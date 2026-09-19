package ua.readshelf.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Creates or migrates the schema, tracking the version in `PRAGMA user_version`.
 * The Android and native drivers do this themselves; drivers without that built in
 * (the web worker) must call it, or a persistent database would be re-created on every start.
 */
internal suspend fun SqlDriver.createOrMigrate(schema: SqlSchema<QueryResult.AsyncValue<Unit>>) {
    val current = userVersion()
    when {
        current == 0L -> schema.create(this).await()
        current < schema.version -> schema.migrate(this, current, schema.version).await()
        else -> return
    }
    // PRAGMA takes no bound parameters; the value is a Long, so formatting it in is safe.
    execute(null, "PRAGMA user_version = ${schema.version}", 0).await()
}

private suspend fun SqlDriver.userVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            // Advance eagerly: synchronous drivers close the cursor as soon as the mapper returns,
            // so a lazy AsyncValue would read a closed result set, see no row and report version 0.
            when (val hasRow = cursor.next()) {
                is QueryResult.Value -> QueryResult.Value(if (hasRow.value) cursor.getLong(0) ?: 0L else 0L)
                is QueryResult.AsyncValue -> QueryResult.AsyncValue {
                    if (hasRow.await()) cursor.getLong(0) ?: 0L else 0L
                }
            }
        },
        parameters = 0,
    ).await()
