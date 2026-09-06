package ua.readshelf.contract

import kotlinx.serialization.Serializable

/**
 * Wire format of the ReadShelf auth endpoints, shared by :server and the clients.
 * Passwords travel in requests only; nothing here ever carries a password hash back.
 */
@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
)

@Serializable
data class AuthResponseDto(
    val token: String,
    val user: UserDto,
)
