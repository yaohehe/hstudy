package com.hstudy.app.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual object ParentPasswordHasher {
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    actual fun hash(password: String): String {
        val salt = ByteArray(PARENT_PASSWORD_SALT_SIZE).also(secureRandom::nextBytes)
        val derivedKey = deriveKey(password, salt)
        return buildString {
            append(PARENT_PASSWORD_HASH_PREFIX)
            append(PARENT_PASSWORD_HASH_ITERATIONS)
            append('$')
            append(base64Encoder.encodeToString(salt))
            append('$')
            append(base64Encoder.encodeToString(derivedKey))
        }
    }

    actual fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 ||
            parts[0] != "pbkdf2-sha256" ||
            parts[1] != PARENT_PASSWORD_HASH_ITERATIONS.toString()
        ) {
            return false
        }

        val salt = decodeBase64(parts[2]) ?: return false
        val expectedKey = decodeBase64(parts[3]) ?: return false
        if (salt.size != PARENT_PASSWORD_SALT_SIZE || expectedKey.size != PARENT_PASSWORD_KEY_SIZE) {
            return false
        }

        val actualKey = deriveKey(password, salt)
        return constantTimeEquals(actualKey, expectedKey)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PARENT_PASSWORD_HASH_ITERATIONS,
            PARENT_PASSWORD_KEY_SIZE * 8,
        )
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeBase64(value: String): ByteArray? = try {
        val decoded = base64Decoder.decode(value)
        // The basic decoder accepts some non-canonical spellings (for example
        // omitted padding). Persisted encodings must round-trip byte-for-byte.
        decoded.takeIf { base64Encoder.encodeToString(it) == value }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun constantTimeEquals(actual: ByteArray, expected: ByteArray): Boolean {
        if (actual.size != expected.size) return false
        var difference = 0
        for (index in actual.indices) {
            difference = difference or (actual[index].toInt() xor expected[index].toInt())
        }
        return difference == 0
    }
}
