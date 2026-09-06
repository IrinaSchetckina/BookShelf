package ua.readshelf.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * The web client is served from the Kotlin/Wasm dev server on a different port,
 * so the browser needs CORS to reach this backend.
 *
 * @param allowedOrigins exact origins to admit. Empty means development: any
 * localhost port, because the dev server picks its own.
 */
fun Application.configureCors(allowedOrigins: List<String> = corsOriginsFromEnv()) {
    val isAllowed: (String) -> Boolean =
        if (allowedOrigins.isEmpty()) {
            { origin -> LOCAL_DEV_ORIGIN.matches(origin) }
        } else {
            { origin -> origin in allowedOrigins }
        }

    install(CORS) {
        // Not anyHost(): these routes now carry authentication, and anyHost lets any
        // page on the internet drive them from a visitor's browser and read the
        // answer. An unset CORS_ALLOWED_ORIGINS fails closed to localhost rather
        // than open to everyone.
        allowOrigins(isAllowed)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        // Without these the browser blocks every login and every authenticated
        // request before it leaves the page, which reads as an auth bug.
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
}

const val CORS_ORIGINS_ENV: String = "CORS_ALLOWED_ORIGINS"

/** Comma-separated exact origins, e.g. `https://readshelf.app,https://www.readshelf.app`. */
fun corsOriginsFromEnv(readEnv: (String) -> String? = System::getenv): List<String> =
    readEnv(CORS_ORIGINS_ENV)
        .orEmpty()
        .split(",")
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotEmpty() }

private val LOCAL_DEV_ORIGIN = Regex("""^http://(localhost|127\.0\.0\.1)(:\d+)?$""")
