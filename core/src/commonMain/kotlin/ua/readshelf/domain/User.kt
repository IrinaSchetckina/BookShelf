package ua.readshelf.domain

/**
 * A registered reader as the app talks about it. Credentials are a server-side
 * concern and deliberately absent: :core ships to the clients as well.
 */
data class User(
    val id: String,
    val email: String,
)
