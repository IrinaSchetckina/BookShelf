package ua.readshelf

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse

private const val CANARY = "canary-password-must-never-be-logged"

/** Message plus the whole `Caused by` chain: the body is quoted in a nested cause. */
private fun ILoggingEvent.renderedWithCauses(): String = buildString {
    append(formattedMessage)
    var proxy: IThrowableProxy? = throwableProxy
    while (proxy != null) {
        append(' ').append(proxy.className).append(' ').append(proxy.message)
        proxy = proxy.cause
    }
}

class PasswordLoggingTest {

    /**
     * Runs at TRACE on purpose. The password must stay out of the log because
     * nothing writes it, not because the shipped level happens to hide it — the
     * first person debugging a production incident turns the level up.
     */
    @Test
    fun `never writes a password to the log, even at trace level`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = root.level

        root.level = Level.TRACE
        root.addAppender(appender)
        try {
            testApplication {
                application { module(testAuthModule()) }
                // Truncated JSON: the parser quotes what it choked on, password included.
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"leak@example.com","password":"$CANARY"""")
                }
            }
        } finally {
            root.detachAppender(appender)
            root.level = previousLevel
            appender.stop()
        }

        val logged = appender.list.joinToString("\n") { it.renderedWithCauses() }
        assertFalse(CANARY in logged, "the password reached the log:\n$logged")
    }
}
