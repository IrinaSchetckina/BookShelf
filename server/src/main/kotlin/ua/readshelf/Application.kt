package ua.readshelf

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.core.module.Module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ua.readshelf.data.remote.OpenLibraryClient
import ua.readshelf.di.serverModule
import ua.readshelf.plugins.configureCors
import ua.readshelf.plugins.configureSerialization
import ua.readshelf.routes.searchRoutes

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = { module() })
        .start(wait = true)
}

/**
 * @param overrides Koin modules layered on top of [serverModule], so a test can
 * replace a single dependency without rebuilding the whole graph.
 */
fun Application.module(vararg overrides: Module) {
    install(Koin) {
        slf4jLogger()
        modules(serverModule(), *overrides)
    }

    val openLibraryClient by inject<OpenLibraryClient>()

    configureSerialization()
    configureCors()
    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        searchRoutes(openLibraryClient)
    }
}
