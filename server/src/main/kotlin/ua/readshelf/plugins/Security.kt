package ua.readshelf.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.header
import io.ktor.server.response.respond
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.JwtService
import ua.readshelf.contract.ErrorResponseDto

const val JWT_AUTH: String = "auth-jwt"

/** Shared with the /me route so both answers to a rejected token read the same. */
const val MISSING_OR_INVALID_TOKEN: String = "Missing or invalid authentication token"

fun Application.configureSecurity(config: AuthConfig, jwtService: JwtService) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = config.realm
            verifier(jwtService.verifier)
            validate { credential ->
                credential.payload
                    .getClaim(JwtService.USER_ID_CLAIM)
                    .asString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { JWTPrincipal(credential.payload) }
            }
            // Ktor's default challenge answers with an empty body, which leaves the
            // client with nothing to show the reader. Replacing it also drops the
            // WWW-Authenticate header Ktor would have sent, and RFC 9110 requires
            // one on a 401 — without it a client cannot tell which scheme to use.
            challenge { defaultScheme, realm ->
                call.response.header(
                    HttpHeaders.WWWAuthenticate,
                    HttpAuthHeader.Parameterized(defaultScheme, mapOf(HttpAuthHeader.Parameters.Realm to realm))
                        .render(),
                )
                call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto(MISSING_OR_INVALID_TOKEN))
            }
        }
    }
}
