package ua.readshelf.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory
import ua.readshelf.contract.ErrorResponseDto

/**
 * Caller mistakes answered in our own error shape. Handlers stay per-exception on
 * purpose: a catch-all for [Throwable] would dress real 500s up as client errors
 * and hide our own bugs.
 *
 * Nothing here logs the exception itself. A body that failed to parse is quoted
 * verbatim inside the parser's message, and on these routes that body is someone's
 * password.
 */
private val logger = LoggerFactory.getLogger("ua.readshelf.plugins.StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        // Without this a body that does not fit the request DTO surfaces as a 500
        // and looks like the backend fell over.
        exception<BadRequestException> { call, cause ->
            logger.debug("Rejected a malformed request body on {}: {}", call.request.path(), cause::class.simpleName)
            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Request body is malformed"))
        }

        // A body Ktor cannot even attempt to deserialize, typically because the
        // Content-Type is missing or is not JSON. Its own answer is a raw string
        // naming the DTO class it failed to build: nothing the caller can use, and
        // an internal name we would rather not publish.
        exception<ContentTransformationException> { call, cause ->
            logger.debug(
                "Rejected an unsupported content type on {}: {}",
                call.request.path(),
                cause::class.simpleName,
            )
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ErrorResponseDto("Request body must be sent as ${ContentType.Application.Json}"),
            )
        }
    }
}
