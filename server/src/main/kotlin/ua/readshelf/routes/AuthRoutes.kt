package ua.readshelf.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ua.readshelf.auth.AuthResult
import ua.readshelf.auth.AuthService
import ua.readshelf.auth.JwtService
import ua.readshelf.auth.UserRepository
import ua.readshelf.contract.AuthResponseDto
import ua.readshelf.contract.ErrorResponseDto
import ua.readshelf.contract.LoginRequestDto
import ua.readshelf.contract.RegisterRequestDto
import ua.readshelf.contract.UserDto
import ua.readshelf.domain.User
import ua.readshelf.plugins.JWT_AUTH
import ua.readshelf.plugins.MISSING_OR_INVALID_TOKEN

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequestDto>()
            val result = authService.register(request.email, request.password)
            call.respondToAuthResult(result, successStatus = HttpStatusCode.Created)
        }

        post("/login") {
            val request = call.receive<LoginRequestDto>()
            val result = authService.login(request.email, request.password)
            call.respondToAuthResult(result, successStatus = HttpStatusCode.OK)
        }
    }
}

fun Route.meRoute(userRepository: UserRepository) {
    authenticate(JWT_AUTH) {
        get("/me") {
            val userId = call.principal<JWTPrincipal>()
                ?.payload
                ?.getClaim(JwtService.USER_ID_CLAIM)
                ?.asString()

            // A well-signed token for a user who is no longer stored: possible
            // once the in-memory store is dropped on restart.
            val user = userId?.let { userRepository.findById(it) }
                ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponseDto(MISSING_OR_INVALID_TOKEN),
                )

            call.respond(UserDto(id = user.id, email = user.email))
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondToAuthResult(
    result: AuthResult,
    successStatus: HttpStatusCode,
) = when (result) {
    is AuthResult.Success -> respond(
        successStatus,
        AuthResponseDto(token = result.token, user = result.user.toDto()),
    )

    is AuthResult.ValidationFailed -> respond(
        HttpStatusCode.BadRequest,
        ErrorResponseDto(result.message),
    )

    AuthResult.EmailAlreadyTaken -> respond(
        HttpStatusCode.Conflict,
        ErrorResponseDto("This email address is already registered"),
    )

    // Same answer for an unknown address and a wrong password, so the endpoint
    // cannot be used to enumerate who has an account.
    AuthResult.InvalidCredentials -> respond(
        HttpStatusCode.Unauthorized,
        ErrorResponseDto("Invalid email or password"),
    )
}

private fun User.toDto(): UserDto = UserDto(id = id, email = email)
