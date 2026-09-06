package ua.readshelf.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import ua.readshelf.contract.ErrorResponseDto

/**
 * Caller mistakes answered in our own error shape. Handlers stay per-exception on
 * purpose: a catch-all for [Throwable] would dress real 500s up as client errors
 * and hide our own bugs.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        // Without this a body that does not fit the request DTO surfaces as a 500
        // and looks like the backend fell over.
        exception<BadRequestException> { call, cause ->
            call.application.environment.log.debug("Rejected a malformed request body", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Request body is malformed"))
        }

        // A body Ktor cannot even attempt to deserialize, typically because the
        // Content-Type is missing or is not JSON. Its own answer is a raw string
        // naming the DTO class it failed to build: nothing the caller can use, and
        // an internal name we would rather not publish.
        exception<ContentTransformationException> { call, cause ->
            call.application.environment.log.debug("Rejected a request with an unsupported content type", cause)
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ErrorResponseDto("Request body must be sent as ${ContentType.Application.Json}"),
            )
        }
    }
}
