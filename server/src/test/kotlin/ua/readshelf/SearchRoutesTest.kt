package ua.readshelf

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ua.readshelf.contract.SearchResponseDto
import ua.readshelf.data.remote.OpenLibraryClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

private val OPEN_LIBRARY_PAYLOAD = """
    {
      "numFound": 2,
      "docs": [
        {
          "key": "/works/OL45804W",
          "title": "Dune",
          "author_name": ["Frank Herbert"],
          "first_publish_year": 1965,
          "cover_i": 8501992,
          "unexpected_field": "ignored"
        },
        { "key": "/works/OL1234W", "title": "Dune Messiah" }
      ]
    }
""".trimIndent()

private fun mockOpenLibraryClient(engine: MockEngine): OpenLibraryClient =
    OpenLibraryClient(
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
    )

/** Replaces the real Open Library client in the backend's Koin graph. */
private fun openLibraryOverride(engine: MockEngine): Module = module {
    single { mockOpenLibraryClient(engine) }
}

class SearchRoutesTest {

    @Test
    fun `returns mapped books for a valid query`() = testApplication {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = OPEN_LIBRARY_PAYLOAD,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        application { module(testAuthModule(), openLibraryOverride(engine)) }

        val response = client.get("/search?q=dune&limit=5")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<SearchResponseDto>(response.bodyAsText())
        assertEquals("dune", body.query)
        assertEquals(2, body.total)
        assertEquals(2, body.books.size)

        val first = body.books.first()
        assertEquals("/works/OL45804W", first.id)
        assertEquals("Dune", first.title)
        assertEquals(listOf("Frank Herbert"), first.authors)
        assertEquals(1965, first.firstPublishYear)
        assertEquals("https://covers.openlibrary.org/b/id/8501992-M.jpg", first.coverUrl)

        val second = body.books[1]
        assertEquals(emptyList(), second.authors)
        assertEquals(null, second.firstPublishYear)
        assertEquals(null, second.coverUrl)

        assertTrue(requestedUrl.contains("q=dune"), "actual url: $requestedUrl")
        assertTrue(requestedUrl.contains("limit=5"), "actual url: $requestedUrl")
    }

    @Test
    fun `rejects a blank query with 400`() = testApplication {
        val engine = MockEngine { error("Open Library must not be called for a blank query") }
        application { module(testAuthModule(), openLibraryOverride(engine)) }

        assertEquals(HttpStatusCode.BadRequest, client.get("/search?q=").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/search?q=%20%20").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/search").status)
    }

    @Test
    fun `maps an upstream failure to 502`() = testApplication {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        application { module(testAuthModule(), openLibraryOverride(engine)) }

        assertEquals(HttpStatusCode.BadGateway, client.get("/search?q=dune").status)
    }

    @Test
    fun `clamps an oversized limit`() = testApplication {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """{"numFound":0,"docs":[]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        application { module(testAuthModule(), openLibraryOverride(engine)) }

        client.get("/search?q=dune&limit=999")

        assertTrue(requestedUrl.contains("limit=50"), "actual url: $requestedUrl")
    }
}
