package com.hstudy.app.security

internal const val PARENT_PASSWORD_HASH_PREFIX = "pbkdf2-sha256$"
internal const val PARENT_PASSWORD_HASH_ITERATIONS = 600_000
internal const val PARENT_PASSWORD_SALT_SIZE = 16
internal const val PARENT_PASSWORD_KEY_SIZE = 32

/**
 * Versioned parent-password hashing. The encoded representation is
 * `pbkdf2-sha256$600000$<base64 salt>$<base64 hash>`.
 */
expect object ParentPasswordHasher {
    fun hash(password: String): String

    /** Returns false for a password mismatch or a malformed/unsupported encoded value. */
    fun verify(password: String, encoded: String): Boolean
}
