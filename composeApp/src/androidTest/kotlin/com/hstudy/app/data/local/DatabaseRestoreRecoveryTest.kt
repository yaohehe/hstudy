package com.hstudy.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.backup.SqliteBackupValidator
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.testutil.FaultInjectingFileSystem
import com.hstudy.app.testutil.testDatabase
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseRestoreRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fs = FileSystem.SYSTEM
    private lateinit var tempDir: Path
    private lateinit var live: Path
    private val journal: Path get() = DatabaseRestoreRecovery.journalPath(live)

    @Before
    fun setUp() {
        val directory = File(context.cacheDir, "restore-recovery-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        tempDir = directory.toOkioPath()
        live = tempDir / "hstudy_db"
    }

    @After
    fun tearDown() {
        fs.deleteRecursively(tempDir)
    }

    @Test
    fun crashAfterJournalBeforeAnyMoveKeepsLiveDatabase() {
        fs.write(live) { writeUtf8("untouched live") }
        fs.write(journal) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }

        DatabaseRestoreRecovery.recover(fs, live)

        assertEquals("untouched live", fs.read(live) { readUtf8() })
        assertFalse(fs.exists(journal))
    }

    @Test
    fun crashAfterPartialCandidateCopyRestoresOriginalMainAndRemovesSidecars() {
        fs.write(live) { writeUtf8("partial candidate") }
        fs.write("$live-wal".toPath()) { writeUtf8("partial wal") }
        fs.write("$live-shm".toPath()) { writeUtf8("partial shm") }
        fs.write("$live.bak".toPath()) { writeUtf8("original main") }
        fs.write(journal) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }

        DatabaseRestoreRecovery.recover(fs, live)

        assertEquals("original main", fs.read(live) { readUtf8() })
        assertFalse(fs.exists("$live-wal".toPath()))
        assertFalse(fs.exists("$live-shm".toPath()))
        assertFalse(fs.exists("$live.bak".toPath()))
        assertFalse(fs.exists(journal))
    }

    @Test
    fun crashBetweenSidecarMovesRestoresEveryOriginalFile() {
        fs.write(live) { writeUtf8("partial candidate") }
        fs.write("$live-wal".toPath()) { writeUtf8("partial wal") }
        fs.write("$live.bak".toPath()) { writeUtf8("original main") }
        fs.write("$live-wal.bak".toPath()) { writeUtf8("original wal") }
        fs.write(journal) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }

        DatabaseRestoreRecovery.recover(fs, live)

        assertEquals("original main", fs.read(live) { readUtf8() })
        assertEquals("original wal", fs.read("$live-wal".toPath()) { readUtf8() })
        assertFalse(fs.exists("$live-shm".toPath()))
        assertFalse(fs.exists("$live.bak".toPath()))
        assertFalse(fs.exists("$live-wal.bak".toPath()))
        assertFalse(fs.exists(journal))
    }

    @Test
    fun interruptedRecoveryCanBeRetriedWithoutLosingOriginals() {
        fs.write(live) { writeUtf8("partial candidate") }
        fs.write("$live-wal".toPath()) { writeUtf8("partial wal") }
        fs.write("$live-shm".toPath()) { writeUtf8("partial shm") }
        fs.write("$live.bak".toPath()) { writeUtf8("original main") }
        fs.write("$live-wal.bak".toPath()) { writeUtf8("original wal") }
        fs.write("$live-shm.bak".toPath()) { writeUtf8("original shm") }
        fs.write(journal) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }
        val faultFs = FaultInjectingFileSystem(fs).apply {
            fault = FaultInjectingFileSystem.Fault(
                FaultInjectingFileSystem.Operation.RENAME,
                source = "$live-wal.bak".toPath(),
                target = "$live-wal".toPath(),
            )
        }

        assertThrows(IOException::class.java) { DatabaseRestoreRecovery.recover(faultFs, live) }
        assertEquals(1, faultFs.injectedFailures)
        assertTrue(fs.exists(journal))

        DatabaseRestoreRecovery.recover(fs, live)

        assertEquals("original main", fs.read(live) { readUtf8() })
        assertEquals("original wal", fs.read("$live-wal".toPath()) { readUtf8() })
        assertEquals("original shm", fs.read("$live-shm".toPath()) { readUtf8() })
        assertFalse(fs.exists(journal))
    }

    @Test
    fun journalWithoutBackupOrLiveFailsClosedWithoutCreatingDatabase() {
        fs.write(journal) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }

        assertThrows(IOException::class.java) { DatabaseRestoreRecovery.recover(fs, live) }

        assertFalse(fs.exists(live))
        assertTrue(fs.exists(journal))
    }

    @Test
    fun committedRestoreCleansRecoveryFilesWithoutRollingBackCandidate() {
        fs.write(live) { writeUtf8("validated candidate") }
        fs.write("$live.bak".toPath()) { writeUtf8("old live") }
        fs.write(journal) { writeUtf8(DatabaseRestoreRecovery.COMMITTED) }

        DatabaseRestoreRecovery.recover(fs, live)

        assertEquals("validated candidate", fs.read(live) { readUtf8() })
        assertFalse(fs.exists("$live.bak".toPath()))
        assertFalse(fs.exists(journal))
    }

    @Test
    fun databaseConstructionRecoversBeforeRoomOpensTheFile() = runBlocking {
        val name = "restore-construction-${UUID.randomUUID()}"
        val path = context.getDatabasePath(name).toOkioPath()
        val db = testDatabase(context, name)
        try {
            db.taskDao().insert(
                TaskEntity(title = "recovered at construction", category = TaskCategory.BASIC, points = 5)
            )
            db.taskDao().getActiveTaskCount()
        } finally {
            db.close()
        }
        fs.atomicMove(path, "$path.bak".toPath())
        fs.write(path) { writeUtf8("partial candidate") }
        fs.write("$path-wal".toPath()) { writeUtf8("partial wal") }
        fs.write(DatabaseRestoreRecovery.journalPath(path)) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }

        val recovered = buildAppDatabase(path.toString())
        try {
            assertEquals("recovered at construction", recovered.taskDao().getAllActiveTasksSnapshot().single().title)
            assertTrue(SqliteBackupValidator(fs).validate(path))
        } finally {
            recovered.close()
            context.deleteDatabase(name)
        }
        assertFalse(fs.exists(DatabaseRestoreRecovery.journalPath(path)))
    }

    @Test
    fun databaseConstructionWithJournalAndMissingLiveDoesNotCreateFreshDatabase() {
        val name = "restore-fail-closed-${UUID.randomUUID()}"
        val path = context.getDatabasePath(name).toOkioPath()
        path.parent?.let { fs.createDirectories(it) }
        fs.write(DatabaseRestoreRecovery.journalPath(path)) { writeUtf8(DatabaseRestoreRecovery.PREPARED) }

        assertThrows(IOException::class.java) { buildAppDatabase(path.toString()) }

        assertFalse(fs.exists(path))
        context.deleteDatabase(name)
    }

    private fun File.toOkioPath(): Path = path.replace('\\', '/').toPath()
}
