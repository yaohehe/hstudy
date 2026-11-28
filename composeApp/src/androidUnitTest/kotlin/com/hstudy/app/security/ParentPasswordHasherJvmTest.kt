package com.hstudy.app.security

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ParentPasswordHasherJvmTest {

    @Test
    fun verifiesIndependentPbkdf2Sha256VectorAt600000Iterations() {
        val encoded = "pbkdf2-sha256\$600000\$AAECAwQFBgcICQoLDA0ODw==\$" +
            "7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="

        assertTrue(ParentPasswordHasher.verify("correct horse battery staple", encoded))
        assertFalse(ParentPasswordHasher.verify("correct horse battery stapler", encoded))
    }

    @Test
    fun generatedHashesHaveRandomSaltAndFixedKeyLengths() {
        val password = "jvm-parent-password"
        val first = ParentPasswordHasher.hash(password)
        val second = ParentPasswordHasher.hash(password)
        val parts = first.split('$')

        assertNotEquals(first, second)
        assertEquals(listOf("pbkdf2-sha256", "600000"), parts.take(2))
        assertEquals(16, Base64.getDecoder().decode(parts[2]).size)
        assertEquals(32, Base64.getDecoder().decode(parts[3]).size)
        assertNotEquals(parts[2], second.split('$')[2])
        assertTrue(ParentPasswordHasher.verify(password, first))
        assertFalse(ParentPasswordHasher.verify("wrong", first))
    }

    @Test
    fun malformedOrUnsupportedEncodedValuesFailClosed() {
        val validSalt = "AAECAwQFBgcICQoLDA0ODw=="
        val validHash = "7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="
        val malformedValues = listOf(
            "",
            "password",
            "pbkdf2-sha512\$600000\$$validSalt\$$validHash",
            "pbkdf2-sha256\$1\$$validSalt\$$validHash",
            "pbkdf2-sha256\$600000\$not-base64\$$validHash",
            "pbkdf2-sha256\$600000\$$validSalt\$not-base64",
            "pbkdf2-sha256\$600000\$AA==\$$validHash",
            "pbkdf2-sha256\$600000\$$validSalt\$AA==",
            "pbkdf2-sha256\$600000\$$validSalt\$$validHash\$extra",
        )

        malformedValues.forEach { encoded ->
            assertFalse(ParentPasswordHasher.verify("password", encoded), encoded)
        }

        val nonCanonicalValues = listOf(
            // Java's decoder accepts omitted padding and non-zero pad bits;
            // the stored encoding must nevertheless be the canonical re-encoding.
            "pbkdf2-sha256\$600000\$${validSalt.dropLast(2)}\$$validHash",
            "pbkdf2-sha256\$600000\$$validSalt\$${validHash.dropLast(1)}",
            "pbkdf2-sha256\$600000\$${validSalt.dropLast(2)}===\$$validHash",
            "pbkdf2-sha256\$600000\$$validSalt\$${validHash}==",
            "pbkdf2-sha256\$600000\$${validSalt.take(8)} ${validSalt.drop(8)}\$$validHash",
            "pbkdf2-sha256\$600000\$$validSalt\$${validHash.take(8)}\n${validHash.drop(8)}",
            "pbkdf2-sha256\$600000\$${validSalt.dropLast(3)}x==\$$validHash",
            "pbkdf2-sha256\$600000\$$validSalt\$${validHash.dropLast(2)}Z=",
        )

        nonCanonicalValues.forEach { encoded ->
            assertFalse(
                ParentPasswordHasher.verify("correct horse battery staple", encoded),
                encoded,
            )
        }
    }
}
