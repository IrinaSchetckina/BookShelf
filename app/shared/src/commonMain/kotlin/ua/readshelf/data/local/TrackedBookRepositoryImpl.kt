package ua.readshelf.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ua.readshelf.db.ReadShelfDatabase
import ua.readshelf.db.TrackedBookEntity
import ua.readshelf.domain.reading.TrackedBook
import ua.readshelf.domain.reading.TrackedBookRepository

class TrackedBookRepositoryImpl(
    private val database: ReadShelfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TrackedBookRepository {

    private val books = database.trackedBookEntityQueries
    private val sessions = database.readingSessionEntityQueries

    override fun observeAll(): Flow<List<TrackedBook>> =
        books.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(book: TrackedBook) {
        withContext(dispatcher) {
            books.upsert(
                bookKey = book.bookKey,
                title = book.title,
                authors = book.authors,
                coverUrl = book.coverUrl,
                totalPages = book.totalPages?.toLong(),
            )
        }
    }

    /** Sessions go in the same transaction: the schema has no cascading foreign key. */
    override suspend fun delete(bookKey: String) {
        withContext(dispatcher) {
            database.transaction {
                sessions.deleteByBook(bookKey)
                books.deleteByKey(bookKey)
            }
        }
    }
}

private fun TrackedBookEntity.toDomain(): TrackedBook =
    TrackedBook(
        bookKey = bookKey,
        title = title,
        authors = authors,
        coverUrl = coverUrl,
        totalPages = totalPages?.toInt(),
    )
