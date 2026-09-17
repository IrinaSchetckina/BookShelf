package ua.readshelf.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * SQLite in a web worker, persisted in the Origin Private File System.
 * The stock SQLDelight sql.js worker keeps the database in memory and loses it on reload,
 * which would break the tracker's local-first promise on the web.
 *
 * The worker script is an asset of the web app (`:app:webApp`, `readshelf-sqlite.worker.js`):
 * a library's resources are not copied into the app bundle, so it must ship next to the app.
 */
class WebWorkerSqlDriverFactory : SqlDriverFactory {

    override suspend fun create(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver {
        val driver = createReadShelfWorkerDriver()
        try {
            driver.createOrMigrate(schema)
        } catch (error: Throwable) {
            // Closing terminates the worker, which releases its exclusive OPFS lock for a retry.
            driver.close()
            throw error
        }
        return driver
    }
}

/**
 * Starts `readshelf-sqlite.worker.js` and wraps it in a driver. Split per target because
 * the driver's `Worker` is an expect type in the shared web metadata, so `org.w3c.dom.Worker`
 * only matches it once compiled for js or wasmJs.
 */
internal expect fun createReadShelfWorkerDriver(): SqlDriver
