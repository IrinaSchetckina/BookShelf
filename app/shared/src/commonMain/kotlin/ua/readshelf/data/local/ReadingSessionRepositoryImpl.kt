package ua.readshelf.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ua.readshelf.db.ReadShelfDatabase
import ua.readshelf.db.ReadingSessionEntity
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.ReadingSessionRepository
import ua.readshelf.domain.reading.SessionDraft
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ReadingSessionRepositoryImpl(
    database: ReadShelfDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val newId: () -> String = { Uuid.random().toString() },
) : ReadingSessionRepository {

    private val queries = database.readingSessionEntityQueries

    override fun observeAll(): Flow<List<ReadingSession>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(draft: SessionDraft): ReadingSession = withContext(dispatcher) {
        val session = ReadingSession(
            id = newId(),
            bookKey = draft.bookKey,
            fromPage = draft.fromPage,
            toPage = draft.toPage,
            day = draft.day,
            recordedAt = draft.recordedAt,
        )
        queries.insert(
            id = session.id,
            bookKey = session.bookKey,
            fromPage = session.fromPage.toLong(),
            toPage = session.toPage.toLong(),
            day = session.day.toString(),
            recordedAtMillis = session.recordedAt?.toEpochMilliseconds(),
        )
        session
    }

    override suspend fun update(session: ReadingSession) {
        withContext(dispatcher) {
            queries.update(
                bookKey = session.bookKey,
                fromPage = session.fromPage.toLong(),
                toPage = session.toPage.toLong(),
                day = session.day.toString(),
                recordedAtMillis = session.recordedAt?.toEpochMilliseconds(),
                id = session.id,
            )
        }
    }

    override suspend fun delete(id: String) {
        withContext(dispatcher) { queries.deleteById(id) }
    }
}

private fun ReadingSessionEntity.toDomain(): ReadingSession =
    ReadingSession(
        id = id,
        bookKey = bookKey,
        fromPage = fromPage.toInt(),
        toPage = toPage.toInt(),
        day = LocalDate.parse(day),
        recordedAt = recordedAtMillis?.let(Instant::fromEpochMilliseconds),
    )
