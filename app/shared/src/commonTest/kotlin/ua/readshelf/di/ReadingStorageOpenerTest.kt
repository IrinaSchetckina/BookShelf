package ua.readshelf.di

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.ReadingSessionRepository
import ua.readshelf.domain.reading.SessionDraft
import ua.readshelf.domain.reading.TrackedBook
import ua.readshelf.domain.reading.TrackedBookRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private object UnusedSessions : ReadingSessionRepository {
    override fun observeAll(): Flow<List<ReadingSession>> = emptyFlow()
    override suspend fun add(draft: SessionDraft): ReadingSession = error("not used")
    override suspend fun update(session: ReadingSession) = error("not used")
    override suspend fun delete(id: String) = error("not used")
}

private object UnusedBooks : TrackedBookRepository {
    override fun observeAll(): Flow<List<TrackedBook>> = emptyFlow()
    override suspend fun upsert(book: TrackedBook) = error("not used")
    override suspend fun delete(bookKey: String) = error("not used")
}

private fun storage() = ReadingStorage(UnusedSessions, UnusedBooks)

/** Counts opens; each open suspends on [gate] when one is set, and fails while [failing] is set. */
private class FakeOpen {
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null
    var failing: Throwable? = null

    suspend fun open(): ReadingStorage {
        calls++
        gate?.await()
        failing?.let { throw it }
        return storage()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingStorageOpenerTest {

    // A supervisor, as in AppContainer: a failed open must not take the scope down with it.
    private fun TestScope.openerWith(fake: FakeOpen) =
        ReadingStorageOpener(
            scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])),
            open = fake::open,
        )

    @Test
    fun concurrentCallersShareOneOpen() = runTest {
        val fake = FakeOpen().apply { gate = CompletableDeferred() }
        val opener = openerWith(fake)

        val first = async { opener.get() }
        val second = async { opener.get() }
        advanceUntilIdle()
        fake.gate?.complete(Unit)

        assertSame(first.await(), second.await())
        assertEquals(1, fake.calls)
    }

    // The web bug: a cancelled caller used to abandon a half-open database and a second open failed.
    @Test
    fun cancelledCallerDoesNotRestartTheOpen() = runTest {
        val fake = FakeOpen().apply { gate = CompletableDeferred() }
        val opener = openerWith(fake)
        val cancelled = launch { opener.get() }
        advanceUntilIdle()

        cancelled.cancel()
        val next = async { opener.get() }
        advanceUntilIdle()
        fake.gate?.complete(Unit)
        next.await()

        assertEquals(1, fake.calls)
    }

    @Test
    fun failedOpenIsTriedAgain() = runTest {
        val fake = FakeOpen().apply { failing = IllegalStateException("locked by another tab") }
        val opener = openerWith(fake)
        assertFailsWith<IllegalStateException> { opener.get() }

        fake.failing = null
        opener.get()

        assertEquals(2, fake.calls)
    }

    @Test
    fun openedStorageIsReused() = runTest {
        val fake = FakeOpen()
        val opener = openerWith(fake)

        val first = opener.get()
        val second = opener.get()

        assertSame(first, second)
        assertEquals(1, fake.calls)
    }
}
