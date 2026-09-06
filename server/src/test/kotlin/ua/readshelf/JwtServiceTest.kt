package ua.readshelf

import com.auth0.jwt.exceptions.JWTVerificationException
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.JwtService
import ua.readshelf.auth.UserRecord
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val USER = UserRecord(id = "user-1", email = "reader@example.com", passwordHash = "hash")

private fun jwtService(secret: String = "test-secret") = JwtService(AuthConfig(secret = secret))

class JwtServiceTest {

    @Test
    fun `issues a token carrying the user id`() {
        val service = jwtService()

        val payload = service.verifier.verify(service.issueToken(USER))

        assertEquals("user-1", payload.getClaim(JwtService.USER_ID_CLAIM).asString())
        assertEquals("reader@example.com", payload.subject)
    }

    @Test
    fun `rejects a token signed with another secret`() {
        val token = jwtService(secret = "attacker-secret").issueToken(USER)

        assertFailsWith<JWTVerificationException> {
            jwtService(secret = "test-secret").verifier.verify(token)
        }
    }

    @Test
    fun `rejects an expired token`() {
        val service = JwtService(AuthConfig(secret = "test-secret", tokenLifetime = 1.hours))
        val issuedTwoDaysAgo = Date(System.currentTimeMillis() - 48L * 60 * 60 * 1000)

        val token = service.issueToken(USER, issuedAt = issuedTwoDaysAgo)

        assertFailsWith<JWTVerificationException> { service.verifier.verify(token) }
    }

    @Test
    fun `rejects a token minted for another issuer`() {
        val foreign = JwtService(AuthConfig(secret = "test-secret", issuer = "someone-else"))

        // Same secret, different service: without the issuer check this backend
        // would accept tokens another system handed out.
        assertFailsWith<JWTVerificationException> {
            jwtService().verifier.verify(foreign.issueToken(USER))
        }
    }

    @Test
    fun `rejects a token minted for another audience`() {
        val foreign = JwtService(AuthConfig(secret = "test-secret", audience = "someone-elses-clients"))

        assertFailsWith<JWTVerificationException> {
            jwtService().verifier.verify(foreign.issueToken(USER))
        }
    }

    @Test
    fun `keeps the secret out of the config toString`() {
        val rendered = AuthConfig(secret = "super-secret").toString()

        assertTrue("super-secret" !in rendered, "actual toString: $rendered")
    }
}
