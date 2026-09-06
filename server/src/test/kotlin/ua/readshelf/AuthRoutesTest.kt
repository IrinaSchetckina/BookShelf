package ua.readshelf

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.JwtService
import ua.readshelf.auth.UserRecord
import ua.readshelf.contract.AuthResponseDto
import ua.readshelf.contract.ErrorResponseDto
import ua.readshelf.contract.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

private suspend fun ApplicationTestBuilder.register(
    email: String = "reader@example.com",
    password: String = "password1",
): HttpResponse = client.post("/auth/register") {
    contentType(ContentType.Application.Json)
    setBody("""{"email":"$email","password":"$password"}""")
}

private suspend fun ApplicationTestBuilder.login(
    email: String = "reader@example.com",
    password: String = "password1",
): HttpResponse = client.post("/auth/login") {
    contentType(ContentType.Application.Json)
    setBody("""{"email":"$email","password":"$password"}""")
}

private suspend fun HttpResponse.authBody(): AuthResponseDto =
    json.decodeFromString(AuthResponseDto.serializer(), bodyAsText())

class AuthRoutesTest {

    @Test
    fun `registers a new user and returns a token`() = testApplication {
        application { module(testAuthModule()) }

        val response = register(email = "  Reader@Example.com ")

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.authBody()
        assertTrue(body.token.isNotBlank(), "expected a token, got: ${response.bodyAsText()}")
        assertEquals("reader@example.com", body.user.email)
        assertTrue(body.user.id.isNotBlank())
    }

    @Test
    fun `never returns the password or its hash`() = testApplication {
        application { module(testAuthModule()) }

        val text = register(password = "password1").bodyAsText()

        assertFalse(text.contains("password1"), "actual body: $text")
        assertFalse(text.contains("\$2a\$"), "actual body: $text")
    }

    @Test
    fun `rejects a duplicate email regardless of case`() = testApplication {
        application { module(testAuthModule()) }
        register(email = "reader@example.com")

        val response = register(email = "READER@Example.com")

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `rejects an invalid email with 400`() = testApplication {
        application { module(testAuthModule()) }

        assertEquals(HttpStatusCode.BadRequest, register(email = "not-an-email").status)
        assertEquals(HttpStatusCode.BadRequest, register(email = "").status)
        assertEquals(HttpStatusCode.BadRequest, register(email = "reader@localhost").status)
    }

    @Test
    fun `rejects a short password with 400`() = testApplication {
        application { module(testAuthModule()) }

        val response = register(password = "short")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val message = json.decodeFromString(ErrorResponseDto.serializer(), response.bodyAsText()).message
        assertTrue("8" in message, "actual message: $message")
    }

    @Test
    fun `rejects a password longer than bcrypt can hash`() = testApplication {
        application { module(testAuthModule()) }

        assertEquals(HttpStatusCode.BadRequest, register(password = "a".repeat(73)).status)
    }

    @Test
    fun `rejects a malformed body with 400`() = testApplication {
        application { module(testAuthModule()) }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"reader@example.com"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `logs in with valid credentials`() = testApplication {
        application { module(testAuthModule()) }
        register()

        val response = login()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.authBody().token.isNotBlank())
    }

    @Test
    fun `rejects a wrong password with 401`() = testApplication {
        application { module(testAuthModule()) }
        register()

        assertEquals(HttpStatusCode.Unauthorized, login(password = "wrong-password").status)
    }

    @Test
    fun `answers the same for an unknown email as for a wrong password`() = testApplication {
        application { module(testAuthModule()) }
        register()

        val unknown = login(email = "nobody@example.com")
        val wrongPassword = login(password = "wrong-password")

        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals(wrongPassword.bodyAsText(), unknown.bodyAsText())
    }

    @Test
    fun `returns the current user for a valid token`() = testApplication {
        application { module(testAuthModule()) }
        val registered = register().authBody()

        val response = client.get("/me") {
            header(HttpHeaders.Authorization, "Bearer ${registered.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val user = json.decodeFromString(UserDto.serializer(), response.bodyAsText())
        assertEquals(registered.user, user)
    }

    @Test
    fun `rejects me without a token with 401`() = testApplication {
        application { module(testAuthModule()) }

        val response = client.get("/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val message = json.decodeFromString(ErrorResponseDto.serializer(), response.bodyAsText()).message
        assertTrue(message.isNotBlank(), "the challenge must explain itself, got an empty body")
    }

    @Test
    fun `rejects me with a malformed token with 401`() = testApplication {
        application { module(testAuthModule()) }

        val response = client.get("/me") {
            header(HttpHeaders.Authorization, "Bearer not-a-jwt")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `rejects me with a token signed by another secret`() = testApplication {
        application { module(testAuthModule()) }
        val foreignToken = JwtService(AuthConfig(secret = "attacker-secret"))
            .issueToken(UserRecord(id = "user-1", email = "reader@example.com", passwordHash = "hash"))

        val response = client.get("/me") {
            header(HttpHeaders.Authorization, "Bearer $foreignToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
