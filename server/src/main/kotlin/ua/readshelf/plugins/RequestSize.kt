package ua.readshelf.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.contentLength
import io.ktor.server.response.respond
import ua.readshelf.contract.ErrorCodes
import ua.readshelf.contract.ErrorResponseDto

/** Generous for the JSON these routes accept, small enough to be harmless. */
const val MAX_REQUEST_BODY_BYTES: Long = 64 * 1024

/**
 * Turns away oversized bodies before anything reads them. Every route here takes a
 * small JSON object, so a megabyte-sized one is either a mistake or an attempt to
 * make the server hold it in memory.
 *
 * Only declared lengths are checked. A chunked upload arrives without one and slips
 * past; closing that needs a limit on the read itself, which is worth doing when
 * this backend starts accepting uploads.
 */
val RequestSizeLimit = createApplicationPlugin("RequestSizeLimit") {
    onCall { call ->
        val declaredLength = call.request.contentLength() ?: return@onCall
        if (declaredLength > MAX_REQUEST_BODY_BYTES) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponseDto(
                    "Request body must not be larger than $MAX_REQUEST_BODY_BYTES bytes",
                    ErrorCodes.VALIDATION_FAILED,
                ),
            )
        }
    }
}

fun Application.configureRequestSizeLimit() {
    install(RequestSizeLimit)
}
