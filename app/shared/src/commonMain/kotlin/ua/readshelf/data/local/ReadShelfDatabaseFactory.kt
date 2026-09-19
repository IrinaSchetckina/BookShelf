package ua.readshelf.data.local

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import ua.readshelf.db.ReadShelfDatabase
import ua.readshelf.db.TrackedBookEntity

/** Database file name, shared by every platform that stores one. */
const val READSHELF_DATABASE_NAME: String = "readshelf.db"

suspend fun createReadShelfDatabase(driverFactory: SqlDriverFactory): ReadShelfDatabase =
    ReadShelfDatabase(
        driver = driverFactory.create(ReadShelfDatabase.Schema),
        trackedBookEntityAdapter = TrackedBookEntity.Adapter(authorsAdapter = AuthorsColumnAdapter),
    )

/** Authors as a JSON array: names may contain commas, so a delimiter would not round-trip. */
internal object AuthorsColumnAdapter : ColumnAdapter<List<String>, String> {

    private val serializer = ListSerializer(String.serializer())

    override fun decode(databaseValue: String): List<String> = Json.decodeFromString(serializer, databaseValue)

    override fun encode(value: List<String>): String = Json.encodeToString(serializer, value)
}
