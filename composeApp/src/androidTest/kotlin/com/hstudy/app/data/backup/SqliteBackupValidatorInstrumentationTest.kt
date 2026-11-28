package com.hstudy.app.data.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.APP_SCHEMA_VERSION
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.execSQL
import com.hstudy.app.testutil.testDatabase
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteBackupValidatorInstrumentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val realFs = FileSystem.SYSTEM
    private lateinit var tempDir: Path
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        val directory = File(context.cacheDir, "backup-schema-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        tempDir = directory.toOkioPath()
        databaseName = "backup-schema-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        if (::tempDir.isInitialized) realFs.deleteRecursively(tempDir)
    }

    @Test
    fun validatorContractMatchesExportedV14AndRuntimePragmas() = runBlocking {
        val exported = Json.parseToJsonElement(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.hstudy.app.data.local.AppDatabase/$APP_SCHEMA_VERSION.json")
                .bufferedReader().use { it.readText() }
        ).jsonObject["database"]!!.jsonObject
        assertEquals(APP_SCHEMA_VERSION, exported["version"]!!.jsonPrimitive.content.toInt())

        val exportedColumns = exported["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to obj["fields"]!!.jsonArray.map { raw ->
                val field = raw.jsonObject
                BackupColumnContract(
                    name = field["columnName"]!!.jsonPrimitive.content,
                    affinity = field["affinity"]!!.jsonPrimitive.content,
                    notNull = field["notNull"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    defaultValue = field["defaultValue"]?.jsonPrimitive?.content,
                )
            }
        }
        val exportedPrimaryKeys = exported["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to
                obj["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        }
        val exportedForeignKeys = exported["entities"]!!.jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to (obj["foreignKeys"]?.jsonArray.orEmpty()).map { raw ->
                val foreignKey = raw.jsonObject
                BackupForeignKeyContract(
                    columns = foreignKey["columns"]!!.jsonArray.map { it.jsonPrimitive.content },
                    referencedTable = foreignKey["table"]!!.jsonPrimitive.content,
                    referencedColumns = foreignKey["referencedColumns"]!!.jsonArray.map { it.jsonPrimitive.content },
                    onUpdate = foreignKey["onUpdate"]!!.jsonPrimitive.content,
                    onDelete = foreignKey["onDelete"]!!.jsonPrimitive.content,
                )
            }
        }
        assertEquals(exportedColumns, BACKUP_EXPECTED_COLUMNS)
        assertEquals(exportedPrimaryKeys, BACKUP_EXPECTED_PRIMARY_KEYS)
        assertEquals(exportedForeignKeys, BACKUP_EXPECTED_FOREIGN_KEYS)

        val candidate = createCandidate()
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            val runtimeColumns = mutableMapOf<String, List<BackupColumnContract>>()
            val runtimePrimaryKeys = mutableMapOf<String, List<String>>()
            val runtimeForeignKeys = mutableMapOf<String, List<BackupForeignKeyContract>>()
            val hiddenColumns = mutableListOf<String>()
            BACKUP_EXPECTED_COLUMNS.keys.forEach { table ->
                val primaryKeyPositions = mutableListOf<Pair<Int, String>>()
                runtimeColumns[table] = connection.prepare("PRAGMA table_xinfo(`$table`)").use { statement ->
                    buildList {
                        while (statement.step()) {
                            val name = statement.getText(1)
                            val primaryKeyPosition = statement.getLong(5).toInt()
                            if (primaryKeyPosition > 0) primaryKeyPositions += primaryKeyPosition to name
                            if (statement.getLong(6) != 0L) hiddenColumns += "$table.$name"
                            add(
                                BackupColumnContract(
                                    name = name,
                                    affinity = statement.getText(2).uppercase(),
                                    notNull = statement.getLong(3) != 0L,
                                    defaultValue = if (statement.isNull(4)) null else statement.getText(4),
                                )
                            )
                        }
                    }
                }
                runtimePrimaryKeys[table] = primaryKeyPositions.sortedBy { it.first }.map { it.second }
                runtimeForeignKeys[table] = readForeignKeys(connection, table)
            }
            assertTrue("v14 must not contain generated or hidden columns", hiddenColumns.isEmpty())
            assertEquals(exportedColumns, runtimeColumns)
            assertEquals(exportedPrimaryKeys, runtimePrimaryKeys)
            assertEquals(exportedForeignKeys, runtimeForeignKeys)
        }
    }

    @Test
    fun wrongAffinityIsRejected() = runBlocking {
        val candidate = createCandidate()
        replaceTableDefinition(candidate, "tasks") { sql ->
            sql.replaceExactlyOnce("`title` TEXT NOT NULL", "`title` BLOB NOT NULL")
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun wrongNullabilityIsRejected() = runBlocking {
        val candidate = createCandidate()
        replaceTableDefinition(candidate, "tasks") { sql ->
            sql.replaceExactlyOnce("`title` TEXT NOT NULL", "`title` TEXT")
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun wrongDefaultValueIsRejected() = runBlocking {
        val candidate = createCandidate()
        replaceTableDefinition(candidate, "rewards") { sql ->
            sql.replaceExactlyOnce("`category` TEXT NOT NULL DEFAULT '其他'", "`category` TEXT NOT NULL DEFAULT 'tampered'")
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun wrongPrimaryKeyIsRejected() = runBlocking {
        val candidate = createCandidate()
        replaceTableDefinition(candidate, "daily_task_snapshots") { sql ->
            sql.replaceExactlyOnce("PRIMARY KEY(`date`)", "PRIMARY KEY(`createdAt`)")
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun unexpectedForeignKeyIsRejected() = runBlocking {
        val candidate = createCandidate()
        replaceTableDefinition(candidate, "daily_schedules") { sql ->
            sql.removeSuffix(")") +
                ", FOREIGN KEY(`linkedTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        }
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            connection.prepare("PRAGMA foreign_key_list(`daily_schedules`)").use { statement ->
                assertTrue("fixture must contain the unexpected foreign key", statement.step())
            }
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun generatedColumnIsRejected() = runBlocking {
        val candidate = createCandidate()
        replaceTableDefinition(candidate, "tasks", generatedColumn = "bonusPoints") { sql ->
            sql.replaceExactlyOnce(
                "`bonusPoints` INTEGER NOT NULL",
                "`bonusPoints` INTEGER NOT NULL GENERATED ALWAYS AS (`points` * 2) VIRTUAL",
            )
        }
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            val hidden = connection.prepare("PRAGMA table_xinfo(`tasks`)").use { statement ->
                var generatedHidden = false
                while (statement.step()) {
                    if (statement.getText(1) == "bonusPoints") generatedHidden = statement.getLong(6) != 0L
                }
                generatedHidden
            }
            assertTrue("fixture must be a generated column, not merely an extra column", hidden)
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun sqlitePrefixedTriggerIsRejected() = runBlocking {
        val candidate = createCandidate()
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            connection.execSQL(
                "CREATE TRIGGER sqliteXsteal_points AFTER INSERT ON point_records " +
                    "BEGIN UPDATE point_records SET points = 999 WHERE id = NEW.id; END"
            )
            connection.prepare(
                "SELECT type FROM sqlite_master WHERE name = 'sqliteXsteal_points'"
            ).use { statement ->
                assertTrue("fixture must contain the wildcard-bypassing trigger", statement.step())
                assertEquals("trigger", statement.getText(0))
                assertFalse(statement.step())
            }
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    @Test
    fun metadataNamesDoNotExemptWrongObjectTypes() = runBlocking {
        val candidate = createCandidate()
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            connection.execSQL("DROP TABLE IF EXISTS room_master_table")
            connection.execSQL("DROP TABLE IF EXISTS android_metadata")
            connection.execSQL("CREATE VIEW room_master_table AS SELECT 1 AS id")
            connection.execSQL("CREATE VIEW android_metadata AS SELECT 1 AS id")
            val objectTypes = connection.prepare(
                "SELECT name, type FROM sqlite_master " +
                    "WHERE name IN ('room_master_table', 'android_metadata') ORDER BY name"
            ).use { statement ->
                buildList {
                    while (statement.step()) add(statement.getText(0) to statement.getText(1))
                }
            }
            assertEquals(
                listOf("android_metadata" to "view", "room_master_table" to "view"),
                objectTypes,
            )
        }
        assertRejectedWithHealthyIntegrity(candidate)
    }

    private suspend fun createCandidate(): Path {
        val db = testDatabase(context, databaseName)
        val source = try {
            db.taskDao().insert(
                TaskEntity(title = "schema fixture", category = TaskCategory.BASIC, points = 5)
            )
            db.taskDao().getActiveTaskCount()
            context.getDatabasePath(databaseName).toOkioPath()
        } finally {
            db.close()
        }
        val candidate = tempDir / "candidate.db"
        realFs.copy(source, candidate)
        assertTrue("unmodified Room database must validate", SqliteBackupValidator(realFs).validate(candidate))
        return candidate
    }

    private fun replaceTableDefinition(
        candidate: Path,
        table: String,
        generatedColumn: String? = null,
        transform: (String) -> String,
    ) {
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            val originalSql = connection.prepare(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?"
            ).use { statement ->
                statement.bindText(1, table)
                assertTrue(statement.step())
                statement.getText(0)
            }
            val indexSql = connection.prepare(
                "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL ORDER BY name"
            ).use { statement ->
                statement.bindText(1, table)
                buildList {
                    while (statement.step()) add(statement.getText(0))
                }
            }
            val originalColumns = connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
                buildList {
                    while (statement.step()) add(statement.getText(1))
                }
            }
            val temporaryTable = "${table}__schema_fixture"
            val temporarySql = transform(originalSql.replaceFirst("`$table`", "`$temporaryTable`"))
            assertTrue("mutation must change the table definition", temporarySql != originalSql)
            connection.execSQL(temporarySql)
            val copiedColumns = originalColumns.filterNot { it == generatedColumn }
                .joinToString(", ") { "`$it`" }
            connection.execSQL(
                "INSERT INTO `$temporaryTable` ($copiedColumns) SELECT $copiedColumns FROM `$table`"
            )
            connection.execSQL("DROP TABLE `$table`")
            connection.execSQL("ALTER TABLE `$temporaryTable` RENAME TO `$table`")
            indexSql.forEach(connection::execSQL)
        }
    }

    private fun readForeignKeys(
        connection: androidx.sqlite.SQLiteConnection,
        table: String,
    ): List<BackupForeignKeyContract> {
        data class Row(
            val id: Long,
            val sequence: Long,
            val referencedTable: String,
            val from: String,
            val to: String,
            val onUpdate: String,
            val onDelete: String,
        )
        val rows = connection.prepare("PRAGMA foreign_key_list(`$table`)").use { statement ->
            buildList {
                while (statement.step()) {
                    add(
                        Row(
                            id = statement.getLong(0),
                            sequence = statement.getLong(1),
                            referencedTable = statement.getText(2),
                            from = statement.getText(3),
                            to = statement.getText(4),
                            onUpdate = statement.getText(5),
                            onDelete = statement.getText(6),
                        )
                    )
                }
            }
        }
        return rows.groupBy { it.id }.values.map { group ->
            val ordered = group.sortedBy { it.sequence }
            BackupForeignKeyContract(
                columns = ordered.map { it.from },
                referencedTable = ordered.first().referencedTable,
                referencedColumns = ordered.map { it.to },
                onUpdate = ordered.first().onUpdate,
                onDelete = ordered.first().onDelete,
            )
        }.sortedWith(
            compareBy<BackupForeignKeyContract> { it.referencedTable }
                .thenBy { it.columns.joinToString(",") }
                .thenBy { it.referencedColumns.joinToString(",") }
                .thenBy { it.onUpdate }
                .thenBy { it.onDelete }
        )
    }

    private fun assertRejectedWithHealthyIntegrity(candidate: Path) {
        BundledSQLiteDriver().open(candidate.toString()).use { connection ->
            connection.prepare("PRAGMA integrity_check").use { statement ->
                assertTrue(statement.step())
                assertEquals("ok", statement.getText(0))
                assertFalse(statement.step())
            }
        }
        assertFalse(SqliteBackupValidator(realFs).validate(candidate))
    }

    private fun String.replaceExactlyOnce(old: String, new: String): String {
        assertEquals("schema fixture fragment must occur exactly once: $old", 1, split(old).size - 1)
        return replaceFirst(old, new)
    }

    private fun File.toOkioPath(): Path = path.replace('\\', '/').toPath()
}
