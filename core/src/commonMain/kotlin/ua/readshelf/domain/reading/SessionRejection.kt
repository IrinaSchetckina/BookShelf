package ua.readshelf.domain.reading

/** Why a [SessionDraft] cannot be saved. */
enum class SessionRejection {
    /** The start page is below zero. */
    NegativeFrom,

    /** The end page is not past the start page, so nothing was read. */
    NotForward,

    /** The session is dated after the current reading day. */
    DayInFuture,

    /** The end page is past the last page of a book whose length is known. */
    BeyondTotalPages,
}
