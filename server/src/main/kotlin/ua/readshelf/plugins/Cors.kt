package ua.readshelf.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * The web client is served from the Kotlin/Wasm dev server on a different port,
 * so the browser needs CORS to reach this backend.
 */
fun Application.configureCors() {
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        // Without these the browser blocks every login and every authenticated
        // request before it leaves the page, which reads as an auth bug.
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
}
