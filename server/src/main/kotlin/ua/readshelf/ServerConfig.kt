package ua.readshelf

/** Port to bind when the platform does not inject one. */
const val DEFAULT_PORT: Int = 8080

const val PORT_ENV: String = "PORT"

/**
 * Port to listen on, taken from [PORT_ENV] because hosting platforms inject it.
 *
 * A value that is set but not a usable port fails the startup instead of falling back:
 * the platform routes its health check to the port it injected, so binding 8080 anyway
 * would leave the service unreachable with nothing in the log saying why.
 * An unset or blank variable is the developer case and yields [DEFAULT_PORT].
 */
fun serverPortFromEnv(readEnv: (String) -> String? = System::getenv): Int {
    val raw = readEnv(PORT_ENV)?.trim()
    if (raw.isNullOrEmpty()) return DEFAULT_PORT

    val port = raw.toIntOrNull()
    require(port != null && port in 1..65535) {
        "$PORT_ENV must be a number between 1 and 65535, was \"$raw\"."
    }
    return port
}
