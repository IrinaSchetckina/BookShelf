package ua.readshelf.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import ua.readshelf.domain.reading.SessionDraft
import ua.readshelf.domain.reading.TrackedBook
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Real Open Library works (see the book-fixtures skill); lengths are set here, not fetched.
private val DUNE = TrackedBook(
    bookKey = "/works/OL893414W",
    title = "Dune",
    authors = listOf("Frank Herbert"),
    coverUrl = "https://covers.openlibrary.org/b/id/11481354-M.jpg",
    totalPages = 300,
)
private val YES_I_CAN = TrackedBook(
    bookKey = "/works/OL4491043W",
    title = "Yes I can",
    authors = listOf("Sammy Davis", "Sammy Davis Jr.", "Jane Boyar", "Burt Boyar"),
    coverUrl = "https://covers.openlibrary.org/b/id/4609059-M.jpg",
    totalPages = null,
)
// Same title, an edition Open Library has no cover for.
private val YES_I_CAN_NO_COVER = TrackedBook(
    bookKey = "/works/OL31256170W",
    title = "Yes I can;",
    authors = listOf("Sammy Davis"),
    coverUrl = null,
    totalPages = null,
)

private val TODAY = LocalDate(2026, 9, 16)

private fun draft(book: TrackedBook, fromPage: Int, toPage: Int) =
    SessionDraft(bookKey = book.bookKey, fromPage = fromPage, toPage = toPage, day = TODAY, recordedAt = null)

class TrackedBookRepositoryImplTest {

    private val databaseFile = TestDatabaseFile()

    @AfterTest
    fun tearDown() = databaseFile.delete()

    @Test
    fun storedBookReadsBackWithAllAuthors() = runTest {
        val repository = TrackedBookRepositoryImpl(databaseFile.open())

        repository.upsert(YES_I_CAN)

        assertEquals(listOf(YES_I_CAN), repository.observeAll().first())
    }

    @Test
    fun missingCoverAndLengthStayMissing() = runTest {
        val repository = TrackedBookRepositoryImpl(databaseFile.open())

        repository.upsert(YES_I_CAN_NO_COVER)

        assertEquals(listOf(YES_I_CAN_NO_COVER), repository.observeAll().first())
    }

    @Test
    fun upsertReplacesBookUnderSameKey() = runTest {
        val repository = TrackedBookRepositoryImpl(databaseFile.open())
        repository.upsert(DUNE.copy(totalPages = null))

        repository.upsert(DUNE)

        assertEquals(listOf(DUNE), repository.observeAll().first())
    }

    // Guards the no-cascade schema: INSERT OR REPLACE is a delete plus an insert.
    @Test
    fun editingBookKeepsItsSessions() = runTest {
        val database = databaseFile.open()
        val books = TrackedBookRepositoryImpl(database)
        val sessions = ReadingSessionRepositoryImpl(database)
        books.upsert(DUNE.copy(totalPages = null))
        val session = sessions.add(draft(DUNE, fromPage = 0, toPage = 118))

        books.upsert(DUNE)

        assertEquals(listOf(session), sessions.observeAll().first())
    }

    @Test
    fun deletingBookRemovesItsSessionsOnly() = runTest {
        val database = databaseFile.open()
        val books = TrackedBookRepositoryImpl(database)
        val sessions = ReadingSessionRepositoryImpl(database)
        books.upsert(DUNE)
        books.upsert(YES_I_CAN)
        sessions.add(draft(DUNE, fromPage = 0, toPage = 118))
        val kept = sessions.add(draft(YES_I_CAN, fromPage = 0, toPage = 40))

        books.delete(DUNE.bookKey)

        assertEquals(listOf(YES_I_CAN), books.observeAll().first())
        assertEquals(listOf(kept), sessions.observeAll().first())
    }

    @Test
    fun bookSurvivesClosingTheDatabase() = runTest {
        TrackedBookRepositoryImpl(databaseFile.open()).upsert(DUNE)
        databaseFile.closeAll()

        val reopened = TrackedBookRepositoryImpl(databaseFile.open())

        assertEquals(listOf(DUNE), reopened.observeAll().first())
    }
}
