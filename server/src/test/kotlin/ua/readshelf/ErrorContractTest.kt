package ua.readshelf

import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ua.readshelf.contract.ErrorCodes
import ua.readshelf.contract.ErrorResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

/** Every error must parse as the contract, whoever produced it. */
private suspend fun HttpResponse.asError(): ErrorResponseDto =
    json.decodeFromString(ErrorResponseDto.serializer(), bodyAsText())

class ErrorContractTest {

    @Test
    fun `answers an unknown endpoint in the error contract`() = testApplication {
        application { module(testAuthModule()) }

        val response = client.get("/no-such-endpoint")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorCodes.NOT_FOUND, response.asError().code)
    }

    @Test
    fun `answers a wrong method in the error contract`() = testApplication {
        application { module(testAuthModule()) }

        val response = client.get("/auth/login")

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals(ErrorCodes.METHOD_NOT_ALLOWED, response.asError().code)
    }

    @Test
    fun `answers an unacceptable Accept header in the error contract`() = testApplication {
        application { module(testAuthModule()) }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.Plain)
            setBody("""{"email":"reader@example.com","password":"password1"}""")
        }

        // The body has to be JSON even here: the client asked for something we do
        // not speak, and answering with nothing leaves it guessing.
        assertEquals(HttpStatusCode.NotAcceptable, response.status)
        assertEquals(ErrorCodes.NOT_ACCEPTABLE, response.asError().code)
    }
}
