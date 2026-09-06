package ua.readshelf.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/**
 * Issues and verifies the access tokens. The user id travels in a [USER_ID_CLAIM]
 * claim rather than in `sub`, so the token stays valid if the address changes.
 */
class JwtService(private val config: AuthConfig) {

    private val algorithm: Algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun issueToken(user: UserRecord, issuedAt: Date = Date()): String =
        JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim(USER_ID_CLAIM, user.id)
            .withSubject(user.email)
            .withIssuedAt(issuedAt)
            .withExpiresAt(Date(issuedAt.time + config.tokenLifetime.inWholeMilliseconds))
            .sign(algorithm)

    companion object {
        const val USER_ID_CLAIM: String = "userId"
    }
}
