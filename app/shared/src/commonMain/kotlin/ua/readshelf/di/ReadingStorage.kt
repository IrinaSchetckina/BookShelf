package ua.readshelf.di

import ua.readshelf.domain.reading.ReadingSessionRepository
import ua.readshelf.domain.reading.TrackedBookRepository

/** The opened local store, exposed only through its domain interfaces. */
class ReadingStorage(
    val sessions: ReadingSessionRepository,
    val books: TrackedBookRepository,
)
