package com.hstudy.app.data.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.execSQL
import com.hstudy.app.testutil.FaultInjectingFileSystem
import com.hstudy.app.testutil.testDatabase
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFailureSafetyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val realFs = FileSystem.SYSTEM
    private lateinit var tempDir: Path
    private lateinit var liveDb: Path
    private lateinit var liveDbName: String
    private lateinit var backup: Path
    private val createdDatabaseNames = mutableListOf<String>()
    private var liveRoom: AppDatabase? = null
    private var closeCount = 0
    private var reopenCount = 0

    @Before
    fun setUp() {
        val directory = File(context.cacheDir, "backup-safety-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        tempDir = directory.toOkioPath()
        liveDbName = "backup-safety-live-${UUID.randomUUID()}.db"
        createdDatabaseNames += liveDbName
        liveDb = context.getDatabasePath(liveDbName).toOkioPath()
        backup = tempDir / "hstudy_backup.db"
    }

    @After
    fun tearDown() {
        liveRoom?.close()
        liveRoom = null
        createdDatabaseNames.forEach(context::deleteDatabase)
        if (::tempDir.isInitialized) realFs.deleteRecursively(tempDir)
    }

    @Test
    fun backupFailureKeepsPreviousBackup() = runBlocking {
        createDatabase("old backup").let { realFs.copy(it, backup) }
        createDatabase("new live data").let { realFs.copy(it, liveDb) }
        val oldHash = sha256(backup)
        val helper = helper()

        // A real exclusive SQLite writer makes the helper's real VACUUM INTO fail.
        BundledSQLiteDriver().open(liveDb.toString()).use { blocker ->
            blocker.execSQL("PRAGMA journal_mode=DELETE")
            blocker.execSQL("PRAGMA locking_mode=EXCLUSIVE")
            blocker.execSQL("BEGIN EXCLUSIVE")
            val result = helper.backup()
            blocker.execSQL("ROLLBACK")
            assertTrue(result.isFailure)
        }

        assertEquals(oldHash, sha256(backup))
        assertTrue("old backup must still validate", SqliteBackupValidator(realFs).validate(backup))
        assertNoStagedFiles()
    }

    @Test
    fun restoreCopyFailureKeepsLiveDatabase() = runBlocking {
        createDatabase("live before failed restore").let { realFs.copy(it, liveDb) }
        createDatabase("candidate backup").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        val faultFs = FaultInjectingFileSystem(realFs).apply {
            fault = FaultInjectingFileSystem.Fault(
                FaultInjectingFileSystem.Operation.COPY,
                target = liveDb,
            )
        }
        var reopens = 0
        val helper = BackupHelper(
            faultFs, liveDb, backup, SqliteBackupValidator(faultFs),
            closeDatabase = { closeCount++ },
            reopenDatabase = {
                reopens++
                openFixtureDatabase()
                assertEquals("live before failed restore", queryFixtureTitle())
            },
        )

        assertTrue(helper.restore().isFailure)
        assertEquals(1, faultFs.injectedFailures)
        assertEquals(1, reopens)
        assertEquals(1, closeCount)
        assertEquals(liveHash, sha256(liveDb))
        assertEquals("live before failed restore", queryTitle(liveDb))
        assertFalse(realFs.exists("$liveDb.bak".toPath()))
        assertNoStagedFiles()
    }

    @Test
    fun partialRestoreCopyFailureRestoresOriginalDatabase() = runBlocking {
        createDatabase("live before partial copy failure").let { realFs.copy(it, liveDb) }
        createDatabase("candidate backup").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        val faultFs = FaultInjectingFileSystem(realFs).apply {
            fault = FaultInjectingFileSystem.Fault(
                FaultInjectingFileSystem.Operation.COPY,
                target = liveDb,
                failAfterBytes = 128,
            )
        }

        assertTrue(helper(faultFs).restore().isFailure)

        assertEquals(1, faultFs.injectedFailures)
        assertEquals(1, closeCount)
        assertEquals(1, reopenCount)
        assertEquals(liveHash, sha256(liveDb))
        assertEquals("live before partial copy failure", queryFixtureTitle())
        assertFalse(realFs.exists("$liveDb.bak".toPath()))
        assertNoStagedFiles()
    }

    @Test
    fun restoreRenameFailureRestoresAlreadyMovedDatabaseAndSidecars() = runBlocking {
        createDatabase("live before rename failure").let { realFs.copy(it, liveDb) }
        createDatabase("candidate backup").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        val wal = "$liveDb-wal".toPath()
        val shm = "$liveDb-shm".toPath()
        realFs.write(wal) { writeUtf8("original wal") }
        realFs.write(shm) { writeUtf8("original shm") }
        val faultFs = FaultInjectingFileSystem(realFs).apply {
            fault = FaultInjectingFileSystem.Fault(
                FaultInjectingFileSystem.Operation.RENAME,
                source = wal,
                target = "$wal.bak".toPath(),
            )
        }

        val helper = BackupHelper(
            faultFs, liveDb, backup, SqliteBackupValidator(faultFs),
            closeDatabase = { closeCount++ },
            reopenDatabase = { reopenCount++ },
        )

        assertTrue(helper.restore().isFailure)

        assertEquals(1, faultFs.injectedFailures)
        assertEquals(1, closeCount)
        assertEquals(1, reopenCount)
        assertEquals(liveHash, sha256(liveDb))
        assertEquals("original wal", realFs.read(wal) { readUtf8() })
        assertEquals("original shm", realFs.read(shm) { readUtf8() })
        assertFalse(realFs.exists("$liveDb.bak".toPath()))
        assertFalse(realFs.exists("$wal.bak".toPath()))
        assertNoStagedFiles()
    }

    @Test
    fun failedReopenRestoresPreviousLiveDatabase() = runBlocking {
        createDatabase("live before reopen failure").let { realFs.copy(it, liveDb) }
        createDatabase("candidate backup").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        val helper = BackupHelper(
            realFs, liveDb, backup, SqliteBackupValidator(realFs),
            closeDatabase = {
                closeCount++
                liveRoom?.close()
                liveRoom = null
            },
            reopenDatabase = {
                reopenCount++
                if (reopenCount == 1) {
                    // Reopen may construct/open the candidate before its callback throws.
                    openFixtureDatabase()
                    error("injected reopen failure")
                }
                openFixtureDatabase()
            },
        )

        assertTrue(helper.restore().isFailure)

        assertEquals(2, closeCount)
        assertEquals(2, reopenCount)
        assertEquals(liveHash, sha256(liveDb))
        assertEquals("live before reopen failure", queryFixtureTitle())
        assertNoStagedFiles()
    }

    @Test
    fun postReopenValidationFailureRestoresBackupFiles() = runBlocking {
        createDatabase("live before validation failure").let { realFs.copy(it, liveDb) }
        createDatabase("candidate rejected after reopen").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        var validations = 0
        val validator = object : SqliteBackupValidator(realFs) {
            override fun validate(path: Path): Boolean {
                validations++
                return if (validations == 1) super.validate(path) else false
            }
        }
        var candidateRoom: AppDatabase? = null
        val rollbackFs = object : ForwardingFileSystem(realFs) {
            override fun delete(file: Path, mustExist: Boolean) {
                if (file == liveDb || file == "$liveDb-wal".toPath() || file == "$liveDb-shm".toPath()) {
                    assertCandidateClosedBeforeRollback(candidateRoom)
                }
                super.delete(file, mustExist)
            }

            override fun atomicMove(source: Path, target: Path) {
                if (source == "$liveDb.bak".toPath()) {
                    assertCandidateClosedBeforeRollback(candidateRoom)
                }
                super.atomicMove(source, target)
            }
        }
        val helper = BackupHelper(
            rollbackFs, liveDb, backup, validator,
            closeDatabase = {
                closeCount++
                liveRoom?.close()
                liveRoom = null
            },
            reopenDatabase = {
                reopenCount++
                if (reopenCount == 1) {
                    openFixtureDatabase()
                    candidateRoom = liveRoom
                } else {
                    assertFalse("candidate Room must be closed before restored Room reconstruction", checkNotNull(candidateRoom).isOpen)
                    openFixtureDatabase()
                    assertNotSame("rollback must construct a fresh Room instance", candidateRoom, liveRoom)
                }
            },
        )

        assertTrue(helper.restore().isFailure)

        assertEquals(2, validations)
        assertEquals(2, closeCount)
        assertEquals(2, reopenCount)
        assertEquals(liveHash, sha256(liveDb))
        assertEquals("live before validation failure", queryFixtureTitle())
        assertFalse(realFs.exists("$liveDb.bak".toPath()))
        assertNoStagedFiles()
    }

    @Test
    fun candidateCloseFailurePreservesRecoveryFilesWithoutRollback() = runBlocking {
        createDatabase("live before candidate close failure").let { realFs.copy(it, liveDb) }
        createDatabase("candidate remains installed after close failure").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        var validations = 0
        val validator = object : SqliteBackupValidator(realFs) {
            override fun validate(path: Path): Boolean {
                validations++
                return if (validations == 1) super.validate(path) else false
            }
        }
        var candidateRoom: AppDatabase? = null
        val helper = BackupHelper(
            realFs, liveDb, backup, validator,
            closeDatabase = {
                closeCount++
                if (closeCount == 2) error("injected candidate close failure")
                liveRoom?.close()
                liveRoom = null
            },
            reopenDatabase = {
                reopenCount++
                openFixtureDatabase()
                candidateRoom = liveRoom
            },
        )

        val result = helper.restore()

        assertTrue(result.isFailure)
        assertEquals(2, validations)
        assertEquals(2, closeCount)
        assertEquals("no restored reopen is allowed when candidate close fails", 1, reopenCount)
        assertEquals("candidate remains installed after close failure", queryFixtureTitle())
        assertSame(candidateRoom, liveRoom)
        assertEquals("original live DB must remain in recovery storage", liveHash, sha256("$liveDb.bak".toPath()))
    }

    @Test
    fun corruptImportNeverReplacesBackupOrLive() = runBlocking {
        createDatabase("live stays untouched").let { realFs.copy(it, liveDb) }
        createDatabase("backup stays untouched").let { realFs.copy(it, backup) }
        val liveHash = sha256(liveDb)
        val backupHash = sha256(backup)

        val result = helper().importBackup(envelope("not sqlite".encodeToByteArray()))

        assertTrue(result.isFailure)
        assertEquals(liveHash, sha256(liveDb))
        assertEquals(backupHash, sha256(backup))
        assertNoStagedFiles()
    }

    @Test
    fun wrongSchemaImportIsRejected() = runBlocking {
        createDatabase("live stays untouched").let { realFs.copy(it, liveDb) }
        createDatabase("backup stays untouched").let { realFs.copy(it, backup) }
        val wrongSchema = tempDir / "wrong-schema.db"
        realFs.copy(liveDb, wrongSchema)
        BundledSQLiteDriver().open(wrongSchema.toString()).use {
            it.execSQL("PRAGMA user_version = 13")
        }
        val liveHash = sha256(liveDb)
        val backupHash = sha256(backup)

        assertTrue(helper().importBackup(envelope(realFs.read(wrongSchema) { readByteArray() })).isFailure)

        assertEquals(liveHash, sha256(liveDb))
        assertEquals(backupHash, sha256(backup))
        assertNoStagedFiles()
    }

    @Test
    fun missingRequiredTableImportIsRejected() = runBlocking {
        createDatabase("existing backup").let { realFs.copy(it, backup) }
        val backupHash = sha256(backup)
        val missingTable = tempDir / "missing-table.db"
        realFs.copy(backup, missingTable)
        BundledSQLiteDriver().open(missingTable.toString()).use {
            it.execSQL("DROP TABLE daily_schedules")
        }

        assertFalse(SqliteBackupValidator(realFs).validate(missingTable))
        assertTrue(helper().importBackup(envelope(realFs.read(missingTable) { readByteArray() })).isFailure)
        assertEquals(backupHash, sha256(backup))
        assertNoStagedFiles()
    }

    @Test
    fun maliciousTriggerIsRejectedEvenWhenIntegrityCheckPasses() = runBlocking {
        val candidate = tempDir / "malicious-trigger.db"
        createDatabase("trigger candidate").let { realFs.copy(it, candidate) }
        BundledSQLiteDriver().open(candidate.toString()).use { db ->
            db.execSQL(
                "CREATE TRIGGER steal_points AFTER INSERT ON point_records " +
                    "BEGIN UPDATE point_records SET points = 999 WHERE id = NEW.id; END"
            )
            assertEquals("ok", integrityCheck(candidate))
        }

        assertFalse(SqliteBackupValidator(realFs).validate(candidate))
    }

    @Test
    fun extraViewIsRejected() = runBlocking {
        val candidate = tempDir / "extra-view.db"
        createDatabase("view candidate").let { realFs.copy(it, candidate) }
        BundledSQLiteDriver().open(candidate.toString()).use { db ->
            db.execSQL("CREATE VIEW exposed_tasks AS SELECT * FROM tasks")
        }

        assertFalse(SqliteBackupValidator(realFs).validate(candidate))
    }

    @Test
    fun extraColumnIsRejectedAsMalformedSchema() = runBlocking {
        val candidate = tempDir / "extra-column.db"
        createDatabase("column candidate").let { realFs.copy(it, candidate) }
        BundledSQLiteDriver().open(candidate.toString()).use { db ->
            db.execSQL("ALTER TABLE tasks ADD COLUMN malicious TEXT")
        }

        assertFalse(SqliteBackupValidator(realFs).validate(candidate))
    }

    @Test
    fun extraUserTableIsRejected() = runBlocking {
        val candidate = tempDir / "extra-table.db"
        createDatabase("table candidate").let { realFs.copy(it, candidate) }
        BundledSQLiteDriver().open(candidate.toString()).use { db ->
            db.execSQL("CREATE TABLE attacker_data (payload TEXT)")
        }

        assertFalse(SqliteBackupValidator(realFs).validate(candidate))
    }

    @Test
    fun requiredUniqueIndexCannotBeReplacedByNonUniqueIndex() = runBlocking {
        val candidate = tempDir / "non-unique-index.db"
        createDatabase("index candidate").let { realFs.copy(it, candidate) }
        BundledSQLiteDriver().open(candidate.toString()).use { db ->
            db.execSQL("DROP INDEX index_point_approvals_activeBizKey")
            db.execSQL(
                "CREATE INDEX index_point_approvals_activeBizKey " +
                    "ON point_approvals (activeBizKey)"
            )
        }

        assertFalse(SqliteBackupValidator(realFs).validate(candidate))
    }

    @Test
    fun malformedEnvelopeAndBase64AreRejectedWithoutReplacingBackup() = runBlocking {
        createDatabase("existing backup").let { realFs.copy(it, backup) }
        val backupHash = sha256(backup)
        val helper = helper()

        assertTrue(helper.importBackup("not json").isFailure)
        assertTrue(helper.importBackup("""{"data":""}""").isFailure)
        assertTrue(helper.importBackup("""{"type":"other","version":1,"data":""}""").isFailure)
        assertTrue(helper.importBackup("""{"type":"hstudy_db_backup","version":2,"data":""}""").isFailure)
        assertTrue(helper.importBackup("""{"type":"hstudy_db_backup","version":1,"data":"%%%"}""").isFailure)
        assertEquals(backupHash, sha256(backup))
        assertNoStagedFiles()
    }

    @Test
    fun successfulRestoreReconstructsRoomAndPassesIntegrityCheck() = runBlocking {
        val oldRoom = testDatabase(context, liveDbName)
        liveRoom = oldRoom
        oldRoom.taskDao().insert(
            TaskEntity(title = "live before successful restore", category = TaskCategory.BASIC, points = 5)
        )
        createDatabase("restored candidate").let { realFs.copy(it, backup) }
        val helper = helper()

        assertTrue(helper.restore().isSuccess)

        assertEquals(1, closeCount)
        assertEquals(1, reopenCount)
        assertNotSame("a closed BundledSQLiteDriver Room instance cannot be reused", oldRoom, liveRoom)
        assertEquals("restored candidate", queryFixtureTitle())
        assertEquals("ok", integrityCheck(liveDb))
        assertTrue(SqliteBackupValidator(realFs).validate(liveDb))
        assertNoStagedFiles()
    }

    @Test
    fun cleanupDeleteFailureDoesNotRollbackSuccessfulRestore() = runBlocking {
        createDatabase("live before cleanup failure").let { realFs.copy(it, liveDb) }
        createDatabase("restored before cleanup failure").let { realFs.copy(it, backup) }
        val shm = "$liveDb-shm".toPath()
        realFs.write(shm) { writeUtf8("old shm") }
        val faultFs = FaultInjectingFileSystem(realFs).apply {
            fault = FaultInjectingFileSystem.Fault(
                FaultInjectingFileSystem.Operation.DELETE,
                source = "$liveDb.bak".toPath(),
            )
        }

        assertTrue(helper(faultFs).restore().isSuccess)

        assertEquals(1, faultFs.injectedFailures)
        assertEquals(1, closeCount)
        assertEquals(1, reopenCount)
        assertEquals("restored before cleanup failure", queryFixtureTitle())
        assertTrue(SqliteBackupValidator(realFs).validate(liveDb))
        assertTrue("failed cleanup must preserve recovery data", realFs.exists("$liveDb.bak".toPath()))
        assertFalse(realFs.exists("$shm.bak".toPath()))
    }

    private fun assertCandidateClosedBeforeRollback(candidateRoom: AppDatabase?) {
        assertNotNull("candidate Room must exist before rollback", candidateRoom)
        assertFalse("candidate Room must be closed before rollback touches its files", checkNotNull(candidateRoom).isOpen)
    }

    private fun helper(
        fs: FileSystem = realFs,
        validator: SqliteBackupValidator = SqliteBackupValidator(fs),
    ): BackupHelper = BackupHelper(
        fs, liveDb, backup, validator,
        closeDatabase = {
            closeCount++
            liveRoom?.close()
            liveRoom = null
        },
        reopenDatabase = {
            reopenCount++
            openFixtureDatabase()
        },
    )

    private suspend fun openFixtureDatabase() {
        check(liveRoom == null) { "the old Room instance must be closed before reconstruction" }
        val db = testDatabase(context, liveDbName)
        liveRoom = db
        // A DAO query forces Room to open the new driver-backed instance and run migrations.
        db.taskDao().getActiveTaskCount()
    }

    private suspend fun queryFixtureTitle(): String {
        val tasks = checkNotNull(liveRoom).taskDao().getAllActiveTasksSnapshot()
        assertEquals(1, tasks.size)
        return tasks.single().title
    }

    private suspend fun createDatabase(title: String): Path {
        val name = "backup-safety-${UUID.randomUUID()}.db"
        createdDatabaseNames += name
        val db = testDatabase(context, name)
        val path = try {
            db.taskDao().insert(TaskEntity(title = title, category = TaskCategory.BASIC, points = 5))
            db.taskDao().getActiveTaskCount()
            context.getDatabasePath(name).toOkioPath()
        } finally {
            db.close()
        }
        assertTrue(realFs.exists(path))
        return path
    }

    private fun queryTitle(path: Path): String = BundledSQLiteDriver().open(path.toString()).use { connection ->
        connection.prepare("SELECT title FROM tasks LIMIT 1").use { statement ->
            assertTrue(statement.step())
            statement.getText(0)
        }
    }

    private fun integrityCheck(path: Path): String = BundledSQLiteDriver().open(path.toString()).use { connection ->
        connection.prepare("PRAGMA integrity_check").use { statement ->
            assertTrue(statement.step())
            statement.getText(0)
        }
    }

    private fun envelope(bytes: ByteArray): String =
        """{"type":"hstudy_db_backup","version":1,"data":"${bytes.toByteString().base64()}"}"""

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(realFs.read(path) { readByteArray() })
        .joinToString("") { "%02x".format(it) }

    private fun assertNoStagedFiles() {
        val liveFiles = if (realFs.exists(liveDb.parent!!)) {
            realFs.list(liveDb.parent!!).filter { it.name.startsWith(liveDb.name) }
        } else {
            emptyList()
        }
        val relevantFiles = realFs.list(tempDir) + liveFiles
        val leftovers = relevantFiles.filter {
            it.name.contains(".tmp") || it.name.endsWith(".bak") ||
                it.name.endsWith("-wal.bak") || it.name.endsWith("-shm.bak") ||
                it.name.endsWith(".restore.journal")
        }
        assertTrue("unexpected staged/backup files: $leftovers", leftovers.isEmpty())
    }

    private fun File.toOkioPath(): Path = path.replace('\\', '/').toPath()
}
