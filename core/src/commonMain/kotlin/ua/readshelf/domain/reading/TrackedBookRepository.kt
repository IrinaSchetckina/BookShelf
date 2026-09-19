package ua.readshelf.domain.reading

import kotlinx.coroutines.flow.Flow

/** Local store of the books the reader tracks. Implemented in :app:shared. */
interface TrackedBookRepository {

    /** Emits every tracked book now and again after each change. */
    fun observeAll(): Flow<List<TrackedBook>>

    suspend fun upsert(book: TrackedBook)

    /** Removes the book together with all of its sessions. */
    suspend fun delete(bookKey: String)
}
