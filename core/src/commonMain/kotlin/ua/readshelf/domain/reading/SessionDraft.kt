package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A session as entered, before validation and before storage assigns it an id. */
data class SessionDraft(
    val bookKey: String,
    val fromPage: Int,
    val toPage: Int,
    val day: LocalDate,
    val recordedAt: Instant?,
)
