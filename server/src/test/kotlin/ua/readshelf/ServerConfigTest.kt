package ua.readshelf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerConfigTest {

    @Test
    fun `falls back to the default port when the variable is absent`() {
        assertEquals(DEFAULT_PORT, serverPortFromEnv { null })
    }

    @Test
    fun `falls back to the default port when the variable is blank`() {
        assertEquals(DEFAULT_PORT, serverPortFromEnv { "   " })
    }

    @Test
    fun `reads the port the platform injected`() {
        assertEquals(3000, serverPortFromEnv { name -> if (name == PORT_ENV) "3000" else null })
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(3000, serverPortFromEnv { " 3000 " })
    }

    @Test
    fun `rejects a value that is not a number`() {
        val error = assertFailsWith<IllegalArgumentException> { serverPortFromEnv { "abc" } }

        // The message has to name the variable and the bad value: this fails at startup on a
        // host where the only diagnostic anyone gets is the crash log.
        assertTrue(error.message.orEmpty().contains(PORT_ENV), "actual: ${error.message}")
        assertTrue(error.message.orEmpty().contains("abc"), "actual: ${error.message}")
    }

    @Test
    fun `rejects a number outside the port range`() {
        assertFailsWith<IllegalArgumentException> { serverPortFromEnv { "0" } }
        assertFailsWith<IllegalArgumentException> { serverPortFromEnv { "65536" } }
        assertFailsWith<IllegalArgumentException> { serverPortFromEnv { "-1" } }
    }

    @Test
    fun `accepts the range boundaries`() {
        assertEquals(1, serverPortFromEnv { "1" })
        assertEquals(65535, serverPortFromEnv { "65535" })
    }

    @Test
    fun `reads only its own variable`() {
        val asked = mutableListOf<String>()

        serverPortFromEnv { name -> asked += name; null }

        assertEquals(listOf(PORT_ENV), asked)
    }
}
