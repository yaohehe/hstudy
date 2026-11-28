package com.hstudy.app.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.preferences.UserPreferences
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParentPasswordSecurityTest {

    @Test
    fun hashesUseFresh128BitSaltAndVersionedFormat() = runBlocking {
        val password = "correct horse battery staple"
        val first = ParentPasswordHasher.hash(password)
        val second = ParentPasswordHasher.hash(password)
        val parts = first.split('$')

        assertNotEquals(first, second)
        assertEquals(4, parts.size)
        assertEquals("pbkdf2-sha256", parts[0])
        assertEquals("600000", parts[1])
        assertEquals(16, Base64.getDecoder().decode(parts[2]).size)
        assertEquals(32, Base64.getDecoder().decode(parts[3]).size)
        assertNotEquals(parts[2], second.split('$')[2])
        assertTrue(ParentPasswordHasher.verify(password, first))
        assertFalse(ParentPasswordHasher.verify("wrong password", first))
    }

    @Test
    fun setParentPasswordPersistsOnlyEncodedHash() = runBlocking {
        withPreferences { preferences, store, _ ->
            val password = "parent-password-must-not-be-stored"

            preferences.setParentPassword(password)

            val raw = store.data.first()
            val stored = checkNotNull(raw[UserPreferences.PARENT_PASSWORD])
            assertNotEquals(password, stored)
            assertFalse(stored.contains(password))
            assertFalse(raw.asMap().values.any { it == password })
            assertTrue(stored.startsWith("pbkdf2-sha256$600000$"))
            assertEquals("ENCODED_V1", raw[UserPreferences.PARENT_PASSWORD_FORMAT])
            assertTrue(preferences.verifyParentPassword(password))
            assertFalse(preferences.verifyParentPassword("wrong password"))
        }
    }

    @Test
    fun legacyPlaintextMigratesAtomicallyOnlyAfterCorrectPassword() = runBlocking {
        withPreferences { preferences, store, file ->
            val legacy = "legacy-parent-password"
            store.edit { it[UserPreferences.PARENT_PASSWORD] = legacy }
            assertNull(store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])

            val bytesBeforeWrongPassword = file.readBytes()
            assertFalse(preferences.verifyParentPassword("wrong password"))
            assertArrayEquals(bytesBeforeWrongPassword, file.readBytes())
            assertEquals(legacy, store.data.first()[UserPreferences.PARENT_PASSWORD])
            assertNull(store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])

            assertTrue(preferences.verifyParentPassword(legacy))
            val migratedPreferences = store.data.first()
            val migrated = checkNotNull(migratedPreferences[UserPreferences.PARENT_PASSWORD])
            assertTrue(migrated.startsWith("pbkdf2-sha256$600000$"))
            assertEquals("ENCODED_V1", migratedPreferences[UserPreferences.PARENT_PASSWORD_FORMAT])
            assertNotEquals(legacy, migrated)
            assertFalse(migratedPreferences.asMap().values.any { it == legacy })
            assertTrue(ParentPasswordHasher.verify(legacy, migrated))

            assertTrue(preferences.verifyParentPassword(legacy))
            assertEquals("verification must not rehash an existing encoded value", migrated, store.data.first()[UserPreferences.PARENT_PASSWORD])
        }
    }

    @Test
    fun encodedWrongPasswordLeavesRawDataStoreBytesUnchanged() = runBlocking {
        withPreferences { preferences, _, file ->
            val password = "encoded-parent-password"
            preferences.setParentPassword(password)
            val bytesBeforeWrongPassword = file.readBytes()

            assertFalse(preferences.verifyParentPassword("wrong password"))

            assertArrayEquals(bytesBeforeWrongPassword, file.readBytes())
        }
    }

    @Test
    fun malformedMarkedEncodedValueFailsClosedWithoutChangingDataStore() = runBlocking {
        withPreferences { preferences, store, file ->
            val malformed = "pbkdf2-sha256\$600000\$not-base64\$not-base64"
            store.edit {
                it[UserPreferences.PARENT_PASSWORD] = malformed
                it[UserPreferences.PARENT_PASSWORD_FORMAT] = "ENCODED_V1"
            }
            val bytesBeforeWrongPassword = file.readBytes()

            assertFalse(preferences.verifyParentPassword(malformed))

            assertArrayEquals(bytesBeforeWrongPassword, file.readBytes())
            assertEquals(malformed, store.data.first()[UserPreferences.PARENT_PASSWORD])
            assertEquals("ENCODED_V1", store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])
        }
    }

    @Test
    fun legacyPlaintextBeginningWithHashPrefixMigratesWhenMarkerIsAbsent() = runBlocking {
        withPreferences { preferences, store, file ->
            val legacy = "pbkdf2-sha256\$legacy-prefix-password"
            store.edit { it[UserPreferences.PARENT_PASSWORD] = legacy }
            val bytesBeforeWrongPassword = file.readBytes()

            assertFalse(preferences.verifyParentPassword("wrong password"))
            assertArrayEquals(bytesBeforeWrongPassword, file.readBytes())
            assertNull(store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])

            assertTrue(preferences.verifyParentPassword(legacy))
            val migrated = store.data.first()
            assertTrue(checkNotNull(migrated[UserPreferences.PARENT_PASSWORD]).startsWith("pbkdf2-sha256$600000$"))
            assertEquals("ENCODED_V1", migrated[UserPreferences.PARENT_PASSWORD_FORMAT])
            assertTrue(ParentPasswordHasher.verify(legacy, checkNotNull(migrated[UserPreferences.PARENT_PASSWORD])))
        }
    }

    @Test
    fun unsupportedLookingLegacyValueUsesExplicitLegacyMarker() = runBlocking {
        withPreferences { preferences, store, file ->
            val legacy = "pbkdf2-sha256\$1\$unsupported\$legacy"
            store.edit {
                it[UserPreferences.PARENT_PASSWORD] = legacy
                it[UserPreferences.PARENT_PASSWORD_FORMAT] = "LEGACY"
            }
            val bytesBeforeWrongPassword = file.readBytes()

            assertFalse(preferences.verifyParentPassword("pbkdf2-sha256\$600000\$unsupported\$legacy"))
            assertArrayEquals(bytesBeforeWrongPassword, file.readBytes())
            assertEquals("LEGACY", store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])

            assertTrue(preferences.verifyParentPassword(legacy))
            val migrated = store.data.first()
            assertNotEquals(legacy, migrated[UserPreferences.PARENT_PASSWORD])
            assertEquals("ENCODED_V1", migrated[UserPreferences.PARENT_PASSWORD_FORMAT])
            assertTrue(ParentPasswordHasher.verify(legacy, checkNotNull(migrated[UserPreferences.PARENT_PASSWORD])))
        }
    }

    @Test
    fun clearParentPasswordRemovesValueAndFormatMarker() = runBlocking {
        withPreferences { preferences, store, _ ->
            preferences.setParentPassword("parent-password")
            assertEquals("ENCODED_V1", store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])

            preferences.clearParentPassword()

            val cleared = store.data.first()
            assertNull(cleared[UserPreferences.PARENT_PASSWORD])
            assertNull(cleared[UserPreferences.PARENT_PASSWORD_FORMAT])
        }
    }

    @Test
    fun rawSnapshotRestorePreservesEncodedCredentialAndFormatMarker() = runBlocking {
        withPreferences { preferences, store, _ ->
            val original = "pbkdf2-sha256\$600000\$AAECAwQFBgcICQoLDA0ODw==\$" +
                "7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="
            store.edit {
                it[UserPreferences.PARENT_PASSWORD] = original
                it[UserPreferences.PARENT_PASSWORD_FORMAT] = "ENCODED_V1"
            }
            val snapshot = preferences.snapshotParentPasswordRawForTest()

            preferences.setParentPassword("replacement-password")
            preferences.restoreParentPasswordRawForTest(snapshot)

            val restored = store.data.first()
            assertEquals(original, restored[UserPreferences.PARENT_PASSWORD])
            assertEquals("ENCODED_V1", restored[UserPreferences.PARENT_PASSWORD_FORMAT])
            assertTrue(preferences.verifyParentPassword("correct horse battery staple"))

            val legacyWithoutMarker = "pbkdf2-sha256\$legacy-without-marker"
            store.edit {
                it[UserPreferences.PARENT_PASSWORD] = legacyWithoutMarker
                it.remove(UserPreferences.PARENT_PASSWORD_FORMAT)
            }
            val legacySnapshot = preferences.snapshotParentPasswordRawForTest()
            preferences.setParentPassword("replacement-password")
            preferences.restoreParentPasswordRawForTest(legacySnapshot)
            assertEquals(legacyWithoutMarker, store.data.first()[UserPreferences.PARENT_PASSWORD])
            assertNull(store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])

            store.edit {
                it.remove(UserPreferences.PARENT_PASSWORD)
                it.remove(UserPreferences.PARENT_PASSWORD_FORMAT)
            }
            val absentSnapshot = preferences.snapshotParentPasswordRawForTest()
            preferences.setParentPassword("another-replacement")
            preferences.restoreParentPasswordRawForTest(absentSnapshot)
            assertNull(store.data.first()[UserPreferences.PARENT_PASSWORD])
            assertNull(store.data.first()[UserPreferences.PARENT_PASSWORD_FORMAT])
        }
    }

    @Test
    fun knownPbkdf2VectorVerifies() {
        val encoded = "pbkdf2-sha256\$600000\$AAECAwQFBgcICQoLDA0ODw==\$" +
            "7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="

        assertTrue(ParentPasswordHasher.verify("correct horse battery staple", encoded))
        assertFalse(ParentPasswordHasher.verify("correct horse battery stapler", encoded))
    }

    private suspend fun withPreferences(
        block: suspend (UserPreferences, DataStore<Preferences>, File) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "parent-password-security-${UUID.randomUUID()}"
        val file: File = context.preferencesDataStoreFile(name)
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            block(UserPreferences(store), store, file)
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }
}
