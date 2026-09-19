package ua.readshelf.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

internal actual fun createReadShelfWorkerDriver(): SqlDriver = WebWorkerDriver(createReadShelfWorker())

// wasmJs only allows js() as the whole body of a top-level function; js follows the same shape.
@OptIn(ExperimentalWasmJsInterop::class)
private fun createReadShelfWorker(): Worker =
    js("""new Worker(new URL("./readshelf-sqlite.worker.js", import.meta.url), { type: "module" })""")
