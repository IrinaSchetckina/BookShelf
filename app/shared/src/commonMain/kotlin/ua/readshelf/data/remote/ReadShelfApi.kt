package ua.readshelf.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ua.readshelf.contract.SearchResponseDto

/**
 * Client of the ReadShelf backend. The public catalogue is never called directly.
 */
class ReadShelfApi(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val baseUrl: String = apiBaseUrl,
) {
    suspend fun search(query: String, limit: Int): SearchResponseDto =
        httpClient.get("$baseUrl/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
