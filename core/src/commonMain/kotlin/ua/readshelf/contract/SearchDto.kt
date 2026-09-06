package ua.readshelf.contract

import kotlinx.serialization.Serializable

/**
 * Wire format of the ReadShelf `/search` endpoint, shared by :server and the clients.
 */
@Serializable
data class SearchResponseDto(
    val query: String,
    val total: Int,
    val books: List<BookDto>,
)

@Serializable
data class BookDto(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val firstPublishYear: Int? = null,
    val coverUrl: String? = null,
)

/**
 * Every error the API returns. [message] is for a human; [code] is what a client
 * branches on, since matching an English sentence breaks the moment it is reworded
 * or translated. [field] names the offending input where there is one.
 *
 * Both are nullable with defaults so older clients keep parsing responses unchanged.
 */
@Serializable
data class ErrorResponseDto(
    val message: String,
    val code: String? = null,
    val field: String? = null,
)

/** The values [ErrorResponseDto.code] can take. Clients may switch on these. */
object ErrorCodes {
    const val VALIDATION_FAILED: String = "validation_failed"
    const val EMAIL_TAKEN: String = "email_taken"
    const val INVALID_CREDENTIALS: String = "invalid_credentials"
    const val UNAUTHENTICATED: String = "unauthenticated"
    const val MALFORMED_BODY: String = "malformed_body"
    const val UNSUPPORTED_MEDIA_TYPE: String = "unsupported_media_type"
    const val NOT_FOUND: String = "not_found"
    const val METHOD_NOT_ALLOWED: String = "method_not_allowed"
    const val NOT_ACCEPTABLE: String = "not_acceptable"
    const val UPSTREAM_UNAVAILABLE: String = "upstream_unavailable"
}
