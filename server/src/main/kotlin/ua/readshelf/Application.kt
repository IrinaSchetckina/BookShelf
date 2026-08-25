package ua.readshelf

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import ua.readshelf.data.remote.OpenLibraryClient
import ua.readshelf.plugins.configureCors
import ua.readshelf.plugins.configureSerialization
import ua.readshelf.routes.searchRoutes

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(openLibraryClient: OpenLibraryClient = OpenLibraryClient()) {
    configureSerialization()
    configureCors()
    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        searchRoutes(openLibraryClient)
    }
}
