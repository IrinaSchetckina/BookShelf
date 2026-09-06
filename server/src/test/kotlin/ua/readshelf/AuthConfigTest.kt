package ua.readshelf

import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.authConfigFromEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthConfigTest {

    @Test
    fun `reads the secret and overrides from the environment`() {
        val config = authConfigFromEnv { name ->
            when (name) {
                AuthConfig.SECRET_ENV -> "env-secret"
                AuthConfig.ISSUER_ENV -> "other-issuer"
                else -> null
            }
        }

        assertEquals("env-secret", config.secret)
        assertEquals("other-issuer", config.issuer)
        assertEquals(AuthConfig.DEFAULT_AUDIENCE, config.audience)
    }

    @Test
    fun `fails when the secret is missing`() {
        val error = assertFailsWith<IllegalStateException> { authConfigFromEnv { null } }

        assertTrue(AuthConfig.SECRET_ENV in error.message.orEmpty(), "actual message: ${error.message}")
    }

    @Test
    fun `fails when the secret is blank`() {
        assertFailsWith<IllegalStateException> {
            authConfigFromEnv { name -> "   ".takeIf { name == AuthConfig.SECRET_ENV } }
        }
    }
}
