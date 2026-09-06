package ua.readshelf

import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import ua.readshelf.data.remote.OpenLibraryClient
import ua.readshelf.di.serverModule
import kotlin.test.Test
import kotlin.test.assertFails

class ServerModuleTest {

    @Test
    fun `releases the open library client when the graph stops`() = runTest {
        // A standalone graph, so this never touches the global Koin context the
        // Ktor plugin uses.
        val koinApplication = koinApplication { modules(serverModule()) }
        val openLibraryClient = koinApplication.koin.get<OpenLibraryClient>()

        koinApplication.close()

        // A live client would try to reach the network here; a closed one refuses
        // outright, which is what proves the engine and its threads were released.
        assertFails("the client outlived the graph, so its connection pool leaked") {
            openLibraryClient.search("dune", limit = 1)
        }
    }
}
