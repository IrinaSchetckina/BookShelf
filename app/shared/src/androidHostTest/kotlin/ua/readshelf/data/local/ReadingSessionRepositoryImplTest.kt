package ua.readshelf.data.local

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import ua.readshelf.domain.reading.BuildReadingSummaryUseCase
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.SessionDraft
import ua.readshelf.domain.reading.TrackedBook
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// Real Open Library works (see the book-fixtures skill); lengths are set here, not fetched.
private val DUNE = TrackedBook(
    bookKey = "/works/OL893414W",
    title = "Dune",
    authors = listOf("Frank Herbert"),
    coverUrl = "https://covers.openlibrary.org/b/id/11481354-M.jpg",
    totalPages = 300,
)
private val HOBBIT = TrackedBook(
    bookKey = "/works/OL27482W",
    title = "The Hobbit",
    authors = listOf("J.R.R. Tolkien"),
    coverUrl = "https://covers.openlibrary.org/b/id/14627509-M.jpg",
    totalPages = null,
)

private val TODAY = LocalDate(2026, 9, 16)
private val YESTERDAY = LocalDate(2026, 9, 15)

// 2026-09-16T22:10:00+03:00; whole milliseconds, which is what the column stores.
private val TEN_PAST_TEN_PM = Instant.fromEpochMilliseconds(1_789_585_800_000)

private fun draft(book: TrackedBook, fromPage: Int, toPage: Int, day: LocalDate = TODAY, recordedAt: Instant? = TEN_PAST_TEN_PM) =
    SessionDraft(bookKey = book.bookKey, fromPage = fromPage, toPage = toPage, day = day, recordedAt = recordedAt)

/** Hands out s1, s2, ... so stored sessions can be compared field by field. */
private fun sequentialIds(): () -> String {
    var next = 0
    return { "s${++next}" }
}

class ReadingSessionRepositoryImplTest {

    private val databaseFile = TestDatabaseFile()

    @AfterTest
    fun tearDown() = databaseFile.delete()

    @Test
    fun storedSessionReadsBackFieldByField() = runTest {
        val repository = ReadingSessionRepositoryImpl(databaseFile.open(), newId = sequentialIds())

        val added = repository.add(draft(DUNE, fromPage = 92, toPage = 118))

        val expected = ReadingSession("s1", DUNE.bookKey, 92, 118, TODAY, TEN_PAST_TEN_PM)
        assertEquals(expected, added)
        assertEquals(listOf(expected), repository.observeAll().first())
    }

    @Test
    fun unknownTimeIsStoredAsUnknown() = runTest {
        val repository = ReadingSessionRepositoryImpl(databaseFile.open())

        repository.add(draft(DUNE, fromPage = 0, toPage = 40, day = YESTERDAY, recordedAt = null))

        assertEquals(null, repository.observeAll().first().single().recordedAt)
    }

    @Test
    fun updateReplacesStoredValues() = runTest {
        val repository = ReadingSessionRepositoryImpl(databaseFile.open(), newId = sequentialIds())
        val added = repository.add(draft(DUNE, fromPage = 92, toPage = 118))

        repository.update(added.copy(toPage = 130))

        assertEquals(listOf(added.copy(toPage = 130)), repository.observeAll().first())
    }

    @Test
    fun deleteRemovesOnlyThatSession() = runTest {
        val repository = ReadingSessionRepositoryImpl(databaseFile.open(), newId = sequentialIds())
        val kept = repository.add(draft(DUNE, fromPage = 0, toPage = 92, day = YESTERDAY))
        val removed = repository.add(draft(DUNE, fromPage = 92, toPage = 118))

        repository.delete(removed.id)

        assertEquals(listOf(kept), repository.observeAll().first())
    }

    @Test
    fun sessionsAreListedByDay() = runTest {
        val repository = ReadingSessionRepositoryImpl(databaseFile.open(), newId = sequentialIds())
        val today = repository.add(draft(DUNE, fromPage = 92, toPage = 118))
        val yesterday = repository.add(draft(DUNE, fromPage = 0, toPage = 92, day = YESTERDAY))

        val listed = repository.observeAll().first()

        assertEquals(listOf(yesterday, today), listed)
    }

    @Test
    fun observersSeeNewSessionWithoutRereading() = runTest {
        val repository = ReadingSessionRepositoryImpl(databaseFile.open(), newId = sequentialIds())
        val emitted = async { repository.observeAll().first { it.isNotEmpty() } }
        yield()

        val added = repository.add(draft(DUNE, fromPage = 92, toPage = 118))

        assertEquals(listOf(added), emitted.await())
    }

    @Test
    fun sessionSurvivesClosingTheDatabase() = runTest {
        val added = ReadingSessionRepositoryImpl(databaseFile.open(), newId = sequentialIds())
            .add(draft(DUNE, fromPage = 92, toPage = 118))
        databaseFile.closeAll()

        val reopened = ReadingSessionRepositoryImpl(databaseFile.open())

        assertEquals(listOf(added), reopened.observeAll().first())
    }

    @Test
    fun summaryIsIdenticalAfterReopening() = runTest {
        val buildSummary = BuildReadingSummaryUseCase()
        val database = databaseFile.open()
        val sessions = ReadingSessionRepositoryImpl(database, newId = sequentialIds())
        val books = TrackedBookRepositoryImpl(database)
        books.upsert(DUNE)
        books.upsert(HOBBIT)
        sessions.add(draft(DUNE, fromPage = 0, toPage = 92, day = YESTERDAY))
        sessions.add(draft(DUNE, fromPage = 92, toPage = 118))
        sessions.add(draft(HOBBIT, fromPage = 0, toPage = 40))
        val before = buildSummary(sessions.observeAll().first(), books.observeAll().first(), TODAY)
        databaseFile.closeAll()

        val reopened = databaseFile.open()
        val after = buildSummary(
            ReadingSessionRepositoryImpl(reopened).observeAll().first(),
            TrackedBookRepositoryImpl(reopened).observeAll().first(),
            TODAY,
        )

        assertEquals(before, after)
    }
}
