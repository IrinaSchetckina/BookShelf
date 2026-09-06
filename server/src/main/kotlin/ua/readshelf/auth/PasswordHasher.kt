package ua.readshelf.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Turns a raw password into something safe to store. An interface rather than a
 * plain object so tests can run at a cheap cost factor: BCrypt at production
 * cost adds hundreds of milliseconds to every test that registers a user.
 */
interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun verify(rawPassword: String, hash: String): Boolean
}

class BcryptPasswordHasher(private val cost: Int = DEFAULT_COST) : PasswordHasher {

    override fun hash(rawPassword: String): String =
        BCrypt.withDefaults().hashToString(cost, rawPassword.toCharArray())

    override fun verify(rawPassword: String, hash: String): Boolean =
        BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified

    companion object {
        const val DEFAULT_COST: Int = 10
    }
}
