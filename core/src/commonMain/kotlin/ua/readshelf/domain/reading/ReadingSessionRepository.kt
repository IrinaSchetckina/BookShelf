package ua.readshelf.domain.reading

import kotlinx.coroutines.flow.Flow

/** Local, offline-first store of reading sessions. Implemented in :app:shared. */
interface ReadingSessionRepository {

    /** Emits every session now and again after each change. */
    fun observeAll(): Flow<List<ReadingSession>>

    /**
     * Stores [draft] under a new id and returns the stored session.
     * Returns only once the write is durable, so callers may confirm it to the reader.
     */
    suspend fun add(draft: SessionDraft): ReadingSession

    suspend fun update(session: ReadingSession)

    suspend fun delete(id: String)
}
