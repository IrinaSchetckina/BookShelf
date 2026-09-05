package ua.readshelf

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import ua.readshelf.data.BookRepositoryImpl
import ua.readshelf.data.remote.ReadShelfApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val BACKEND_PAYLOAD = """
{
  "query": "dune",
  "total": 1,
  "books": [
    {
      "id": "/works/OL45804W",
      "title": "Dune",
      "authors": ["Frank Herbert"],
      "firstPublishYear": 1965,
      "coverUrl": "https://covers.openlibrary.org/b/id/8501992-M.jpg"
    }
  ]
}
"""

private fun repositoryWith(engine: MockEngine) = BookRepositoryImpl(
    ReadShelfApi(
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
        baseUrl = "http://test.local",
    ),
)

class BookRepositoryImplTest {

    @Test
    fun mapsBackendResponseToDomain() = runTest {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = BACKEND_PAYLOAD,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val books = repositoryWith(engine).search("dune", limit = 5)

        assertEquals(1, books.size)
        val book = books.single()
        assertEquals("/works/OL45804W", book.id)
        assertEquals("Dune", book.title)
        assertEquals(listOf("Frank Herbert"), book.authors)
        assertEquals(1965, book.firstPublishYear)
        assertTrue(requestedUrl.contains("/search"), "actual url: $requestedUrl")
        assertTrue(requestedUrl.contains("q=dune"), "actual url: $requestedUrl")
        assertTrue(requestedUrl.contains("limit=5"), "actual url: $requestedUrl")
    }

    @Test
    fun propagatesBackendFailure() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.BadGateway) }

        assertFailsWith<Exception> {
            repositoryWith(engine).search("dune", limit = 5)
        }
    }
}
