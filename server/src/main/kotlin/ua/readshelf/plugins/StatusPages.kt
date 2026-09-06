package ua.readshelf.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import ua.readshelf.contract.ErrorResponseDto

/**
 * A body that does not fit the request DTO is the caller's mistake, but without
 * this it surfaces as a 500 and looks like the backend fell over.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.application.environment.log.debug("Rejected a malformed request body", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Request body is malformed"))
        }
    }
}
