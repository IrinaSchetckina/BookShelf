package ua.readshelf.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ua.readshelf.contract.ErrorCodes
import ua.readshelf.contract.ErrorResponseDto
import ua.readshelf.contract.SearchResponseDto
import ua.readshelf.data.remote.OpenLibraryClient
import ua.readshelf.data.toBookDtos
import ua.readshelf.domain.BookRepository

private const val MAX_LIMIT = 50

fun Route.searchRoutes(openLibraryClient: OpenLibraryClient) {
    get("/search") {
        val query = call.request.queryParameters["q"].orEmpty().trim()
        if (query.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto("Query parameter 'q' must not be blank", ErrorCodes.VALIDATION_FAILED, field = "q"),
            )
            return@get
        }

        val limit = call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?.coerceIn(1, MAX_LIMIT)
            ?: BookRepository.DEFAULT_SEARCH_LIMIT

        val response = runCatching { openLibraryClient.search(query, limit) }
            .getOrElse { error ->
                call.application.environment.log.warn("Open Library search failed for '$query'", error)
                call.respond(
                    HttpStatusCode.BadGateway,
                    ErrorResponseDto(
                        "Book catalogue is unavailable, please try again later",
                        ErrorCodes.UPSTREAM_UNAVAILABLE,
                    ),
                )
                return@get
            }

        call.respond(
            SearchResponseDto(
                query = query,
                total = response.numFound,
                books = response.docs.toBookDtos(),
            ),
        )
    }
}
