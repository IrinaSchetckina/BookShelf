package ua.readshelf.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opens local storage at most once, however many callers ask and however they are cancelled.
 *
 * The open runs in [scope], not in the caller's coroutine: a caller that is cancelled mid-open
 * (a composition leaving, for instance) must not abandon a half-opened database. On the web that
 * would leave a worker holding the exclusive OPFS lock while the next caller starts a second one,
 * which then fails. A failed open is not kept, so a later call tries again.
 */
class ReadingStorageOpener(
    private val scope: CoroutineScope,
    private val open: suspend () -> ReadingStorage,
) {
    private val lock = Mutex()
    private var opening: Deferred<ReadingStorage>? = null

    suspend fun get(): ReadingStorage {
        val current = lock.withLock {
            // A Deferred that failed reports itself as cancelled.
            opening?.takeUnless { it.isCancelled } ?: scope.async { open() }.also { opening = it }
        }
        return current.await()
    }
}
