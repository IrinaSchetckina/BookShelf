package ua.readshelf.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.JwtService
import ua.readshelf.contract.ErrorResponseDto

const val JWT_AUTH: String = "auth-jwt"

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
            // client with nothing to show the reader.
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponseDto("Missing or invalid authentication token"),
                )
            }
        }
    }
}
