package ua.readshelf.data.local

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** File-backed database in the app sandbox; the driver creates and migrates the schema. */
class NativeSqlDriverFactory : SqlDriverFactory {

    override suspend fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
        NativeSqliteDriver(schema.synchronous(), READSHELF_DATABASE_NAME)
}
