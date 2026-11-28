package com.hstudy.app.data.local

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalMigrationChainTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun exportedAdjacentVersionsPreserveRowsAndValidateSchema() {
        helper.createDatabase(TEST_DB, 12).use { db ->
            seedSharedHistory(db)
            db.execSQL(
                """INSERT INTO point_approvals
                    (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt, taskId, bizKey, awardedRecordId, date)
                    VALUES
                    (1, 'TASK_COMPLETE', 'pending duplicate', 5, 'PENDING', 100, 0, 1, 'TASK:1:2026-11-25', NULL, '2026-11-25'),
                    (2, 'TASK_COMPLETE', 'cancelled duplicate', 5, 'CANCELLED', 200, 201, 1, 'TASK:1:2026-11-25', NULL, '2026-11-25')""".trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migrations.MIGRATION_12_13)
        assertAdjacentState(13)

        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migrations.MIGRATION_13_14)
        assertAdjacentState(14)
        assertCanonicalColumnsAndIndexes()
    }

    @Test
    fun bundledConnectionExecutesEveryEarlyMigrationStep() {
        val earlyMigrations = Migrations.ALL.take(11)
        assertEquals((1..11).toList(), earlyMigrations.map { it.startVersion })
        assertEquals((2..12).toList(), earlyMigrations.map { it.endVersion })

        val databaseFile = context.getDatabasePath(TEST_DB)
        databaseFile.parentFile?.mkdirs()
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        try {
            createV1Schema(connection)
            seedEarlyV1History(connection)
            connection.execSQL("PRAGMA user_version = 1")
            assertEarlyMigrationState(connection, 1)

            earlyMigrations.forEach { migration ->
                migration.migrate(connection)
                connection.execSQL("PRAGMA user_version = ${migration.endVersion}")
                seedRowsIntroducedAt(connection, migration.endVersion)
                assertEarlyMigrationState(connection, migration.endVersion)
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun populatedV1FixtureMigratesThroughTheCompletePhysicalChain() {
        context.openOrCreateDatabase(TEST_DB, 0, null).use { db ->
            createV1Schema(db)
            db.execSQL(
                """INSERT INTO tasks (id, title, description, category, points, bonusPoints, frequency, isActive, sortOrder, createdAt)
                    VALUES
                    (1, 'Duplicate task', 'first', '学习', 5, 1, 'DAILY', 1, 1, 100),
                    (2, 'Duplicate task', 'second', '学习', 7, 2, 'DAILY', 1, 2, 200),
                    (3, 'Other task', 'other', '生活', 9, 3, 'WEEKLY', 1, 3, 300)""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO task_completions (id, taskId, completedAt, pointsEarned, note, date)
                    VALUES (1, 2, 400, 7, 'historical completion', '2026-11-25')""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO point_records (id, taskId, points, type, note, date, weekStart)
                    VALUES (1, 2, 7, 'TASK_COMPLETE', 'historical points', 500, 600)""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO rewards (id, name, description, pointsCost, iconEmoji, isActive, sortOrder, createdAt)
                    VALUES (1, 'Legacy reward', 'legacy description', 80, 'R', 1, 1, 700)""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO reward_exchanges (id, rewardId, pointsSpent, exchangedAt, note)
                    VALUES (1, 1, 80, 800, 'historical exchange')""".trimIndent()
            )
            db.version = 1
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase // Opening Room executes every registered migration.
        } finally {
            db.close()
        }
        assertCompleteChainState()
    }

    private fun seedEarlyV1History(connection: SQLiteConnection) {
        connection.execSQL(
            """INSERT INTO tasks (id, title, description, category, points, bonusPoints, frequency, isActive, sortOrder, createdAt)
                VALUES
                (1, 'Duplicate task', 'first', '学习', 5, 1, 'DAILY', 1, 1, 100),
                (2, 'Duplicate task', 'second', '学习', 7, 2, 'DAILY', 1, 2, 200),
                (3, 'Other task', 'other', '生活', 9, 3, 'WEEKLY', 1, 3, 300)""".trimIndent()
        )
        connection.execSQL(
            """INSERT INTO task_completions (id, taskId, completedAt, pointsEarned, note, date)
                VALUES (1, 2, 400, 7, 'historical completion', '2026-11-25')""".trimIndent()
        )
        connection.execSQL(
            """INSERT INTO point_records (id, taskId, points, type, note, date, weekStart)
                VALUES (1, 2, 7, 'TASK_COMPLETE', 'historical points', 500, 600)""".trimIndent()
        )
        connection.execSQL(
            """INSERT INTO rewards (id, name, description, pointsCost, iconEmoji, isActive, sortOrder, createdAt)
                VALUES (1, 'Legacy reward', 'legacy description', 80, 'R', 1, 1, 700)""".trimIndent()
        )
        connection.execSQL(
            """INSERT INTO reward_exchanges (id, rewardId, pointsSpent, exchangedAt, note)
                VALUES (1, 1, 80, 800, 'historical exchange')""".trimIndent()
        )
    }

    private fun seedRowsIntroducedAt(connection: SQLiteConnection, version: Int) {
        when (version) {
            2 -> {
                connection.execSQL(
                    """INSERT INTO mistakes (id, subject, title, content, wrongReason, correctMethod, isMastered, photoPath, createdAt, masteredAt)
                        VALUES (1, '数学', 'historical mistake', 'content', 'reason', 'method', 0, '', 900, NULL)""".trimIndent()
                )
                connection.execSQL(
                    """INSERT INTO achievements (id, type, title, description, emoji, unlockedAt)
                        VALUES (1, 'perfect_day', 'historical achievement', 'description', 'A', 901)""".trimIndent()
                )
                connection.execSQL(
                    """INSERT INTO habit_challenges
                        (id, title, description, startDate, totalDays, completedDays, isActive, isCompleted, emoji, dailyPoints, createdAt)
                        VALUES (1, 'historical habit', 'description', '2026-11-01', 21, 2, 1, 0, 'H', 5, 902)""".trimIndent()
                )
            }
            4 -> connection.execSQL(
                """INSERT INTO daily_assignments (id, title, description, points, assignedDate, isCompleted, completedAt, parentNote, createdAt)
                    VALUES (1, 'historical assignment', 'description', 10, '2026-11-25', 0, NULL, 'note', 903)""".trimIndent()
            )
            6 -> {
                connection.execSQL(
                    """INSERT INTO wish_goals (id, name, description, targetPoints, savedPoints, iconEmoji, isActive, createdAt, completedAt)
                        VALUES (1, 'historical wish', 'description', 100, 20, 'W', 1, 904, NULL)""".trimIndent()
                )
                connection.execSQL(
                    """INSERT INTO daily_schedules (id, time, title, emoji, linkedTaskId, isEnabled, repeatDays, sortOrder)
                        VALUES (1, '08:00', 'historical schedule', 'S', 1, 1, '1,2,3,4,5', 1)""".trimIndent()
                )
            }
            10 -> connection.execSQL(
                """INSERT INTO point_approvals (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt) VALUES
                    (1, 'TASK_COMPLETE', 'duplicate approval', 5, 'PENDING', 100, 0),
                    (2, 'TASK_COMPLETE', 'duplicate approval', 5, 'PENDING', 200, 0)""".trimIndent()
            )
            12 -> {
                connection.execSQL("UPDATE point_approvals SET bizKey = 'TASK:1:2026-11-25' WHERE id IN (1, 2)")
                // v12 has no unique bizKey index yet; duplicate historical keys must still be representable.
                connection.execSQL(
                    """INSERT INTO point_approvals
                        (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt, taskId, bizKey, awardedRecordId, date)
                        VALUES (3, 'TASK_COMPLETE', 'post-v12 duplicate', 5, 'PENDING', 300, 0, 1, 'TASK:1:2026-11-25', NULL, '2026-11-25')""".trimIndent()
                )
            }
        }
    }

    private fun assertEarlyMigrationState(connection: SQLiteConnection, version: Int) {
        assertEquals(version, connectionScalarInt(connection, "PRAGMA user_version"))
        val expectedTables = expectedEarlyTables(version)
        assertEquals(expectedTables, connectionUserTables(connection))
        assertEquals(expectedTables.size, connectionUserTableCount(connection))
        assertConnectionForeignKeyCheckEmpty(connection)

        assertEquals(3, connectionScalarInt(connection, "SELECT COUNT(*) FROM tasks"))
        assertConnectionScalar(connection, "SELECT description FROM tasks WHERE id = 2", "second")
        assertConnectionScalar(connection, "SELECT note FROM task_completions WHERE id = 1", "historical completion")
        assertConnectionScalar(connection, "SELECT note FROM point_records WHERE id = 1", "historical points")
        assertConnectionScalar(connection, "SELECT description FROM rewards WHERE id = 1", "legacy description")
        assertConnectionScalar(connection, "SELECT note FROM reward_exchanges WHERE id = 1", "historical exchange")
        assertEquals(1, connectionScalarInt(connection, "SELECT isActive FROM tasks WHERE id = 2"))
        if (version >= 3) {
            assertConnectionScalar(connection, "SELECT category FROM rewards WHERE id = 1", "其他")
        }
        if (version >= 6) {
            assertEquals(1, connectionScalarInt(connection, "SELECT difficulty FROM tasks WHERE id = 1"))
        }
        if (version >= 11) {
            assertEquals(0, connectionScalarInt(connection, "SELECT isMandatory FROM tasks WHERE id = 1"))
        }

        when (version) {
            2 -> {
                assertConnectionScalar(connection, "SELECT title FROM mistakes WHERE id = 1", "historical mistake")
                assertConnectionScalar(connection, "SELECT type FROM achievements WHERE id = 1", "perfect_day")
                assertConnectionScalar(connection, "SELECT title FROM habit_challenges WHERE id = 1", "historical habit")
            }
            3 -> assertConnectionScalar(connection, "SELECT lastCheckInDate FROM habit_challenges WHERE id = 1", "")
            5 -> {
                assertConnectionScalar(connection, "SELECT category FROM rewards WHERE id = 1", "其他")
                assertConnectionScalar(connection, "SELECT lastCheckInDate FROM habit_challenges WHERE id = 1", "")
                assertEquals(80, connectionScalarInt(connection, "SELECT pointsCost FROM rewards WHERE id = 1"))
            }
            6 -> {
                assertConnectionScalar(connection, "SELECT name FROM wish_goals WHERE id = 1", "historical wish")
                assertConnectionScalar(connection, "SELECT title FROM daily_schedules WHERE id = 1", "historical schedule")
            }
            7 -> {
                assertEquals(0, connectionScalarInt(connection, "SELECT lastReviewAt FROM mistakes WHERE id = 1"))
                assertEquals(0, connectionScalarInt(connection, "SELECT reviewCount FROM mistakes WHERE id = 1"))
            }
            8 -> {
                assertConnectionScalar(connection, "SELECT variationNote FROM mistakes WHERE id = 1", "")
                assertEquals(0, connectionScalarInt(connection, "SELECT hasVariation FROM mistakes WHERE id = 1"))
            }
            9 -> assertConnectionScalar(connection, "SELECT errorType FROM mistakes WHERE id = 1", "")
            10, 11 -> assertEarlyPointApprovalColumns(connection, version)
            12 -> {
                assertEarlyPointApprovalColumns(connection, version)
                assertEquals(3, connectionScalarInt(connection, "SELECT COUNT(*) FROM point_approvals"))
                assertEquals(3, connectionScalarInt(connection, "SELECT COUNT(*) FROM point_approvals WHERE bizKey = 'TASK:1:2026-11-25'"))
                assertConnectionScalar(connection, "SELECT status FROM point_approvals WHERE id = 1", "PENDING")
                assertConnectionScalar(connection, "SELECT status FROM point_approvals WHERE id = 2", "CANCELLED")
                assertConnectionScalar(connection, "SELECT status FROM point_approvals WHERE id = 3", "PENDING")
                assertTrue(connectionUniqueIndexNames(connection, "point_approvals").isEmpty())
            }
        }
        if (version >= 4) {
            assertConnectionScalar(connection, "SELECT title FROM daily_assignments WHERE id = 1", "historical assignment")
        }
    }

    private fun assertEarlyPointApprovalColumns(connection: SQLiteConnection, version: Int) {
        val expected = mutableListOf(
            "id", "actionType", "description", "requestedPoints", "status", "requestedAt", "reviewedAt"
        )
        if (version >= 12) expected += listOf("taskId", "bizKey", "awardedRecordId", "date")
        assertEquals(expected, connectionColumnNames(connection, "point_approvals"))
    }

    private fun expectedEarlyTables(version: Int): Set<String> = buildSet {
        addAll(listOf("tasks", "point_records", "task_completions", "rewards", "reward_exchanges"))
        if (version >= 2) {
            addAll(
                listOf(
                    "mistakes", "mistake_reviews", "focus_sessions", "daily_plans",
                    "completion_proofs", "habit_challenges", "achievements"
                )
            )
        }
        if (version >= 4) add("daily_assignments")
        if (version >= 6) addAll(listOf("wish_goals", "daily_schedules"))
        if (version >= 10) add("point_approvals")
    }

    private fun connectionScalarInt(connection: SQLiteConnection, sql: String): Int {
        val statement = connection.prepare(sql)
        return try {
            assertTrue(statement.step())
            statement.getLong(0).toInt()
        } finally {
            statement.close()
        }
    }

    private fun assertConnectionScalar(connection: SQLiteConnection, sql: String, expected: String) {
        val statement = connection.prepare(sql)
        try {
            assertTrue(statement.step())
            assertEquals(expected, statement.getText(0))
        } finally {
            statement.close()
        }
    }

    private fun connectionUserTables(connection: SQLiteConnection): Set<String> {
        val statement = connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('sqlite_sequence', 'room_master_table', 'android_metadata')"
        )
        return try {
            val names = mutableSetOf<String>()
            while (statement.step()) names += statement.getText(0)
            names
        } finally {
            statement.close()
        }
    }

    private fun connectionUserTableCount(connection: SQLiteConnection): Int =
        connectionScalarInt(
            connection,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT IN ('sqlite_sequence', 'room_master_table', 'android_metadata')"
        )

    private fun connectionColumnNames(connection: SQLiteConnection, table: String): List<String> {
        val statement = connection.prepare("PRAGMA table_info(`$table`)")
        return try {
            val names = mutableListOf<String>()
            while (statement.step()) names += statement.getText(1)
            names
        } finally {
            statement.close()
        }
    }

    private fun connectionUniqueIndexNames(connection: SQLiteConnection, table: String): Set<String> {
        val statement = connection.prepare("PRAGMA index_list(`$table`)")
        return try {
            val names = mutableSetOf<String>()
            while (statement.step()) {
                if (statement.getLong(2) != 0L) names += statement.getText(1)
            }
            names
        } finally {
            statement.close()
        }
    }

    private fun assertConnectionForeignKeyCheckEmpty(connection: SQLiteConnection) {
        val statement = connection.prepare("PRAGMA foreign_key_check")
        try {
            assertFalse(statement.step())
        } finally {
            statement.close()
        }
    }

    private fun assertAdjacentState(version: Int) {
        val db = context.openOrCreateDatabase(TEST_DB, 0, null)
        db.use {
            assertEquals(version, it.version)
            assertEquals(if (version == 13) 16 else 19, userTableCount(it))
            assertForeignKeyCheckEmpty(it)
            assertScalar(it, "SELECT description FROM tasks WHERE id = 2", "second")
            assertScalar(it, "SELECT pointsEarned FROM task_completions WHERE id = 1", "9")
            assertScalar(it, "SELECT note FROM reward_exchanges WHERE id = 1", "legacy exchange")
            assertScalar(it, "SELECT points FROM point_records WHERE id = 1", "11")
            assertEquals(0, scalarInt(it, "SELECT isActive FROM tasks WHERE id = 2"))
            assertEquals(1, scalarInt(it, "SELECT isActive FROM tasks WHERE id = 1"))
            if (version >= 13) {
                assertPointApprovalColumns(it, version)
            }
            if (version == 14) {
                assertTaskCompletionColumns(it)
                assertFocusSessionColumns(it)
                assertEquals(2, scalarInt(it, "SELECT COUNT(*) FROM focus_sessions"))
                assertEquals(2, scalarInt(it, "SELECT COUNT(*) FROM focus_sessions WHERE sessionKey IS NULL"))
                assertScalar(it, "SELECT taskTitle FROM focus_sessions WHERE id = 1", "historical focus one")
                assertScalar(it, "SELECT frequencyPeriod FROM task_completions WHERE id = 1", "2026-11-25")
                assertScalar(it, "SELECT frequencyPeriod FROM point_approvals WHERE id = 1", "2026-11-25")
                assertPetScenePurchaseSchema(it)
                assertMandatoryPenaltyRunSchema(it)
                assertEquals(2, scalarInt(it, "SELECT COUNT(*) FROM point_approvals"))
                assertEquals(1, scalarInt(it, "SELECT COUNT(*) FROM point_approvals WHERE activeBizKey = 'TASK:1:2026-11-25'"))
                assertNullIndexAllowsMultipleRows(it, "point_approvals", "activeBizKey")
                assertNullIndexAllowsMultipleRows(it, "achievements", "canonicalType")
                assertNullIndexAllowsMultipleRows(it, "rewards", "presetKey")
                assertNullIndexAllowsMultipleRows(it, "focus_sessions", "sessionKey")
            }
        }
    }

    private fun assertCompleteChainState() {
        val sqlite = context.openOrCreateDatabase(TEST_DB, 0, null)
        sqlite.use {
            assertEquals(19, userTableCount(it))
            assertForeignKeyCheckEmpty(it)
            assertEquals(3, scalarInt(it, "SELECT COUNT(*) FROM tasks"))
            assertScalar(it, "SELECT description FROM tasks WHERE id = 2", "second")
            assertScalar(it, "SELECT note FROM task_completions WHERE id = 1", "historical completion")
            assertScalar(it, "SELECT note FROM point_records WHERE id = 1", "historical points")
            assertScalar(it, "SELECT category FROM rewards WHERE id = 1", "其他")
            assertScalar(it, "SELECT note FROM reward_exchanges WHERE id = 1", "historical exchange")
            assertEquals(0, scalarInt(it, "SELECT isActive FROM tasks WHERE id = 2"))
            assertEquals(1, scalarInt(it, "SELECT difficulty FROM tasks WHERE id = 1"))
            assertPointApprovalColumns(it)
            assertTaskCompletionColumns(it)
            assertScalar(it, "SELECT frequencyPeriod FROM task_completions WHERE id = 1", "2026-11-25")
            assertTrue(tableExists(it, "daily_task_snapshots"))
            assertPetScenePurchaseSchema(it)
            assertMandatoryPenaltyRunSchema(it)
        }
        assertCanonicalColumnsAndIndexes()
    }

    private fun assertCanonicalColumnsAndIndexes() {
        val db = context.openOrCreateDatabase(TEST_DB, 0, null)
        db.use {
            assertPointApprovalColumns(it)
            assertTaskCompletionColumns(it)
            assertFocusSessionColumns(it)
            assertEquals(1, scalarInt(it, "SELECT COUNT(*) FROM pragma_index_list('focus_sessions') WHERE name = 'index_focus_sessions_sessionKey' AND \"unique\" = 1"))
            assertEquals(1, scalarInt(it, "SELECT COUNT(*) FROM pragma_index_list('point_approvals') WHERE name = 'index_point_approvals_activeBizKey' AND \"unique\" = 1"))
            assertEquals(1, scalarInt(it, "SELECT COUNT(*) FROM pragma_index_list('achievements') WHERE name = 'index_achievements_canonicalType' AND \"unique\" = 1"))
            assertEquals(1, scalarInt(it, "SELECT COUNT(*) FROM pragma_index_list('rewards') WHERE name = 'index_rewards_presetKey' AND \"unique\" = 1"))
        }
    }

    private fun assertPointApprovalColumns(db: SQLiteDatabase, version: Int = 14) {
        val expected = mutableListOf(
            "id", "actionType", "description", "requestedPoints", "status", "requestedAt",
            "reviewedAt", "taskId", "bizKey", "awardedRecordId", "date"
        )
        if (version >= 14) expected += listOf("activeBizKey", "frequencyPeriod")
        val actual = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`point_approvals`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) actual += cursor.getString(nameIndex)
        }
        assertEquals(expected, actual)
    }

    private fun assertTaskCompletionColumns(db: SQLiteDatabase) {
        val actual = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`task_completions`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) actual += cursor.getString(nameIndex)
        }
        assertEquals(
            listOf("id", "taskId", "completedAt", "pointsEarned", "note", "date", "frequencyPeriod"),
            actual
        )
    }

    private fun assertFocusSessionColumns(db: SQLiteDatabase) {
        val actual = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`focus_sessions`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) actual += cursor.getString(nameIndex)
        }
        assertEquals(
            listOf("id", "durationMinutes", "taskTitle", "completedAt", "earnedPoints", "date", "sessionKey"),
            actual,
        )
    }

    private fun assertNullIndexAllowsMultipleRows(db: SQLiteDatabase, table: String, column: String) {
        db.execSQL(indexedInsert(table, column, null, 100))
        db.execSQL(indexedInsert(table, column, null, 101))
        db.execSQL(indexedInsert(table, column, "duplicate", 102))
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(indexedInsert(table, column, "duplicate", 103))
        }
    }

    private fun indexedInsert(table: String, column: String, value: String?, id: Int): String =
        when (table) {
            "point_approvals" ->
                "INSERT INTO point_approvals (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt, bizKey, date, $column) " +
                    "VALUES ($id, 'OTHER', 'index probe $id', 1, 'PENDING', $id, 0, '', '', ${sqlValue(value)})"
            "achievements" ->
                "INSERT INTO achievements (id, type, title, description, emoji, unlockedAt, $column) " +
                    "VALUES ($id, 'index_probe_$id', 'probe $id', 'probe', 'A', $id, ${sqlValue(value)})"
            "rewards" ->
                "INSERT INTO rewards (id, name, description, pointsCost, iconEmoji, category, isActive, sortOrder, createdAt, $column) " +
                    "VALUES ($id, 'index probe $id', 'probe', 1, 'R', '其他', 1, $id, $id, ${sqlValue(value)})"
            "focus_sessions" ->
                "INSERT INTO focus_sessions (id, durationMinutes, taskTitle, completedAt, earnedPoints, date, $column) " +
                    "VALUES ($id, 1, 'index probe $id', $id, 0, '2026-11-25', ${sqlValue(value)})"
            else -> error("Unexpected table $table")
        }

    private fun sqlValue(value: String?): String = value?.let { "'$it'" } ?: "NULL"

    private fun seedSharedHistory(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO tasks (id, title, description, category, points, bonusPoints, frequency, difficulty, isMandatory, isActive, sortOrder, createdAt)
                VALUES
                (1, 'Duplicate task', 'first', '学习', 5, 1, 'DAILY', 1, 0, 1, 1, 100),
                (2, 'Duplicate task', 'second', '学习', 7, 2, 'DAILY', 1, 0, 1, 2, 200),
                (3, 'Other task', 'other', '生活', 9, 3, 'WEEKLY', 1, 0, 1, 3, 300)""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO task_completions (id, taskId, completedAt, pointsEarned, note, date)
                VALUES (1, 2, 400, 9, 'historical completion', '2026-11-25')""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO point_records (id, taskId, points, type, note, date, weekStart, approvalId)
                VALUES (1, 2, 11, 'TASK_COMPLETE', 'historical points', 500, 600, NULL)""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO rewards (id, name, description, pointsCost, iconEmoji, category, isActive, sortOrder, createdAt)
                VALUES (1, 'Legacy reward', 'legacy description', 80, 'R', '其他', 1, 1, 700)""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO reward_exchanges (id, rewardId, pointsSpent, exchangedAt, note)
                VALUES (1, 1, 80, 800, 'legacy exchange')""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO focus_sessions (id, durationMinutes, taskTitle, completedAt, earnedPoints, date) VALUES
                (1, 25, 'historical focus one', 100, 0, '2026-11-24'),
                (2, 15, 'historical focus two', 200, 0, '2026-11-25')""".trimIndent()
        )
        db.execSQL(
            """INSERT INTO achievements (id, type, title, description, emoji, unlockedAt)
                VALUES (1, 'perfect_day', 'first', 'first description', 'A', 100),
                       (2, 'perfect_day', 'second', 'second description', 'B', 200)""".trimIndent()
        )
    }

    private fun createV1Schema(db: SQLiteDatabase) {
        v1SchemaStatements.forEach { db.execSQL(it) }
    }

    private fun createV1Schema(connection: SQLiteConnection) {
        v1SchemaStatements.forEach { connection.execSQL(it) }
    }

    private val v1SchemaStatements = listOf(
        "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, points INTEGER NOT NULL, bonusPoints INTEGER NOT NULL, frequency TEXT NOT NULL, isActive INTEGER NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
        "CREATE TABLE point_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER, points INTEGER NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL, date INTEGER NOT NULL, weekStart INTEGER NOT NULL)",
        "CREATE TABLE task_completions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, completedAt INTEGER NOT NULL, pointsEarned INTEGER NOT NULL, note TEXT NOT NULL, date TEXT NOT NULL)",
        "CREATE TABLE rewards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, pointsCost INTEGER NOT NULL, iconEmoji TEXT NOT NULL, isActive INTEGER NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
        "CREATE TABLE reward_exchanges (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, rewardId INTEGER NOT NULL, pointsSpent INTEGER NOT NULL, exchangedAt INTEGER NOT NULL, note TEXT NOT NULL)"
    )

    private fun userTableCount(db: SQLiteDatabase): Int =
        scalarInt(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT IN ('sqlite_sequence', 'room_master_table', 'android_metadata')")

    private fun assertForeignKeyCheckEmpty(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        scalarInt(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'") == 1

    private fun assertPetScenePurchaseSchema(db: SQLiteDatabase) {
        assertTrue(tableExists(db, "pet_scene_purchases"))
        val columns = mutableListOf<Triple<String, String, Int>>()
        val primaryKeys = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`pet_scene_purchases`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                columns += Triple(cursor.getString(nameIndex), cursor.getString(typeIndex), cursor.getInt(notNullIndex))
                if (cursor.getInt(primaryKeyIndex) > 0) primaryKeys += cursor.getString(nameIndex)
            }
        }
        assertEquals(
            listOf(Triple("sceneKey", "TEXT", 1), Triple("purchasedAt", "INTEGER", 1)),
            columns
        )
        assertEquals(listOf("sceneKey"), primaryKeys)
        assertEquals(0, scalarInt(db, "SELECT COUNT(*) FROM pet_scene_purchases"))
    }

    private fun assertMandatoryPenaltyRunSchema(db: SQLiteDatabase) {
        assertTrue(tableExists(db, "mandatory_penalty_runs"))
        val columns = mutableListOf<Triple<String, String, Int>>()
        val primaryKeys = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`mandatory_penalty_runs`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                columns += Triple(cursor.getString(nameIndex), cursor.getString(typeIndex), cursor.getInt(notNullIndex))
                if (cursor.getInt(primaryKeyIndex) > 0) primaryKeys += cursor.getString(nameIndex)
            }
        }
        assertEquals(
            listOf(
                Triple("periodKey", "TEXT", 1),
                Triple("processedAt", "INTEGER", 1),
                Triple("deductedPoints", "INTEGER", 1),
            ),
            columns,
        )
        assertEquals(listOf("periodKey"), primaryKeys)
        assertEquals(0, scalarInt(db, "SELECT COUNT(*) FROM mandatory_penalty_runs"))
    }

    private fun scalarInt(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { it.moveToFirst(); it.getInt(0) }

    private fun assertScalar(db: SQLiteDatabase, sql: String, expected: String) {
        db.rawQuery(sql, null).use {
            assertTrue(it.moveToFirst())
            assertEquals(expected, it.getString(0))
        }
    }

    private companion object {
        const val TEST_DB = "physical-migration-chain-test"
    }
}
