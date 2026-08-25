package ua.readshelf.plugins

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
    }
}
