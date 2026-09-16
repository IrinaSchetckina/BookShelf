package ua.readshelf.data.local

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** File-backed database in the app's private storage; the driver creates and migrates the schema. */
class AndroidSqlDriverFactory(private val context: Context) : SqlDriverFactory {

    override suspend fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver =
        AndroidSqliteDriver(schema.synchronous(), context, READSHELF_DATABASE_NAME)
}
