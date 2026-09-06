package ua.readshelf.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Everything the JWT setup needs. [secret] is deliberately not printed by
 * [toString]: config gets logged by accident, signing keys must not leak that way.
 */
data class AuthConfig(
    val secret: String,
    val issuer: String = DEFAULT_ISSUER,
    val audience: String = DEFAULT_AUDIENCE,
    val realm: String = DEFAULT_REALM,
    val tokenLifetime: Duration = DEFAULT_TOKEN_LIFETIME,
) {
    override fun toString(): String =
        "AuthConfig(secret=***, issuer=$issuer, audience=$audience, realm=$realm, tokenLifetime=$tokenLifetime)"

    companion object {
        const val DEFAULT_ISSUER: String = "readshelf"
        const val DEFAULT_AUDIENCE: String = "readshelf-clients"
        const val DEFAULT_REALM: String = "ReadShelf"

        /**
         * Long enough to be usable without refresh tokens, which are out of scope.
         */
        val DEFAULT_TOKEN_LIFETIME: Duration = 24.hours

        const val SECRET_ENV: String = "JWT_SECRET"
        const val ISSUER_ENV: String = "JWT_ISSUER"
        const val AUDIENCE_ENV: String = "JWT_AUDIENCE"
    }
}

/**
 * Reads the config from the environment and fails the startup when the secret is
 * missing. A baked-in fallback secret would let a deployment run happily while
 * anyone able to read the source could mint valid tokens.
 */
fun authConfigFromEnv(readEnv: (String) -> String? = System::getenv): AuthConfig {
    val secret = readEnv(AuthConfig.SECRET_ENV)?.takeIf { it.isNotBlank() }
        ?: error(
            "${AuthConfig.SECRET_ENV} is not set. Start the server with " +
                "${AuthConfig.SECRET_ENV}=<secret> so tokens can be signed.",
        )

    return AuthConfig(
        secret = secret,
        issuer = readEnv(AuthConfig.ISSUER_ENV)?.takeIf { it.isNotBlank() } ?: AuthConfig.DEFAULT_ISSUER,
        audience = readEnv(AuthConfig.AUDIENCE_ENV)?.takeIf { it.isNotBlank() } ?: AuthConfig.DEFAULT_AUDIENCE,
    )
}
