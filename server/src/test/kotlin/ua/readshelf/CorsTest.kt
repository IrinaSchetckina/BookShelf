package ua.readshelf

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import ua.readshelf.plugins.corsOriginsFromEnv
import ua.readshelf.plugins.CORS_ORIGINS_ENV
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private suspend fun ApplicationTestBuilder.preflight(origin: String) =
    client.options("/auth/login") {
        header(HttpHeaders.Origin, origin)
        header(HttpHeaders.AccessControlRequestMethod, "POST")
        header(HttpHeaders.AccessControlRequestHeaders, "content-type,authorization")
    }

class CorsTest {

    @Test
    fun `admits the local dev server on any port`() = testApplication {
        application { module(testAuthModule()) }

        val response = preflight("http://localhost:8081")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.AccessControlAllowOrigin] != null)
    }

    @Test
    fun `turns away an unknown site`() = testApplication {
        application { module(testAuthModule()) }

        val response = preflight("https://evil.example")

        // Any page on the internet could otherwise drive login from a visitor's
        // browser and read the answer.
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(
            response.status == HttpStatusCode.Forbidden,
            "expected the preflight to be refused, got ${response.status}",
        )
    }

    @Test
    fun `reads the production allowlist from the environment`() {
        val origins = corsOriginsFromEnv { name ->
            "https://readshelf.app, https://www.readshelf.app/".takeIf { name == CORS_ORIGINS_ENV }
        }

        assertEquals(listOf("https://readshelf.app", "https://www.readshelf.app"), origins)
    }

    @Test
    fun `treats an unset variable as no production origins`() {
        assertEquals(emptyList(), corsOriginsFromEnv { null })
    }
}
