package ua.readshelf.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

/** Guards register and login; the rest of the API is not worth brute-forcing. */
val AUTH_RATE_LIMIT: RateLimitName = RateLimitName("auth")

/**
 * Both auth routes run BCrypt, so they are the expensive ones to call and the
 * only ones worth guessing against. Without a ceiling, an attacker can walk a
 * password list at whatever rate the network allows, and read /auth/register's
 * 409 as a yes-or-no answer about any address they like.
 *
 * Keyed by client address, which a shared NAT makes coarse. That is the safe
 * direction to be wrong in: it throttles a few honest neighbours rather than
 * letting anyone bypass it by changing a header we do not verify.
 */
fun Application.configureRateLimits(requestsPerMinute: Int = DEFAULT_AUTH_REQUESTS_PER_MINUTE) {
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = requestsPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
}

const val DEFAULT_AUTH_REQUESTS_PER_MINUTE: Int = 20
