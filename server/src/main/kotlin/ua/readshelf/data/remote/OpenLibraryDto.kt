package ua.readshelf.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of the Open Library `search.json` response we rely on.
 * The API returns many more fields, hence [kotlinx.serialization.json.Json.ignoreUnknownKeys].
 */
@Serializable
data class OpenLibrarySearchResponseDto(
    val numFound: Int = 0,
    val docs: List<OpenLibraryDocDto> = emptyList(),
)

@Serializable
data class OpenLibraryDocDto(
    val key: String,
    val title: String? = null,
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    @SerialName("cover_i") val coverId: Int? = null,
)
