package ua.readshelf.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Talks to the public Open Library API. Clients never reach it directly,
 * they go through the ReadShelf backend.
 *
 * Takes ownership of [httpClient]: [close] shuts it down, so do not hand in a
 * client that outlives this instance.
 */
class OpenLibraryClient(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val baseUrl: String = OPEN_LIBRARY_BASE_URL,
) : AutoCloseable {
    suspend fun search(query: String, limit: Int): OpenLibrarySearchResponseDto =
        httpClient.get("$baseUrl/search.json") {
            parameter("q", query)
            parameter("limit", limit)
            parameter("fields", REQUESTED_FIELDS)
        }.body()

    /** Releases the engine's connection pool and its threads. */
    override fun close() {
        httpClient.close()
    }

    companion object {
        const val OPEN_LIBRARY_BASE_URL: String = "https://openlibrary.org"
        private const val REQUESTED_FIELDS = "key,title,author_name,first_publish_year,cover_i"

        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
