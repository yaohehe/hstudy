package com.hstudy.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.repository.CheckInResult
import com.hstudy.app.data.repository.PointRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationV13To14Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Before
    fun setUp() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                """INSERT INTO point_approvals
                    (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt, taskId, bizKey, awardedRecordId, date)
                    VALUES
                    (1, 'TASK_COMPLETE', 'first', 5, 'CANCELLED', 100, 101, 1, '$duplicateKey', NULL, '2026-11-25'),
                    (2, 'TASK_COMPLETE', 'second', 5, 'PENDING', 200, 0, 1, '$duplicateKey', NULL, '2026-11-25')""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO achievements (id, type, title, description, emoji, unlockedAt) VALUES
                    (1, 'perfect_day', 'first', 'first description', 'A', 100),
                    (2, 'perfect_day', 'duplicate', 'duplicate description', 'B', 200)""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO focus_sessions (id, durationMinutes, taskTitle, completedAt, earnedPoints, date)
                    VALUES (1, 25, 'historical focus', 100, 0, '2026-11-25')"""
            )
            db.execSQL(
                """INSERT INTO rewards (id, name, description, pointsCost, iconEmoji, category, isActive, sortOrder, createdAt) VALUES
                    (1, '$expectedPresetKey', 'custom description', 80, '📺', '娱乐', 1, 1, 100),
                    (2, '$expectedPresetKey', '看一集喜欢的动画片', 81, '📺', '娱乐', 1, 1, 110),
                    (3, '$expectedPresetKey', '看一集喜欢的动画片', 80, 'C', '娱乐', 1, 1, 120),
                    (4, '$expectedPresetKey', '看一集喜欢的动画片', 80, '📺', '其他', 1, 1, 130),
                    (5, '$expectedPresetKey', '看一集喜欢的动画片', 80, '📺', '娱乐', 1, 99, 140),
                    (6, '$expectedPresetKey', '看一集喜欢的动画片', 80, '📺', '娱乐', 1, 1, 150),
                    (7, '$expectedPresetKey', '看一集喜欢的动画片', 80, '📺', '娱乐', 1, 1, 200),
                    (8, '完全自定义奖励', 'custom', 99, 'C', '其他', 1, 2, 300)""".trimIndent()
            )
        }
    }

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun duplicateHistorySurvivesAndCanonicalKeysAreUnique() = runBlocking {
        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migrations.MIGRATION_13_14)
        val db = Room.databaseBuilder(instrumentation.targetContext, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            assertEquals(0, db.petScenePurchaseDao().getPurchasedSceneKeys().first().size)
            val focusRows = db.focusDao().getAllSessions().first()
            assertEquals(1, focusRows.size)
            assertEquals("historical focus", focusRows.single().taskTitle)
            assertNull(focusRows.single().sessionKey)
            val approvalRows = db.pointApprovalDao().getAllApprovals().first()
            assertEquals(2, approvalRows.size)
            assertEquals(1, approvalRows.count { it.activeBizKey == duplicateKey })
            assertEquals(2, approvalRows.count { it.bizKey == duplicateKey })
            assertEquals("second", approvalRows.single { it.activeBizKey == duplicateKey }.description)
            assertNull(approvalRows.single { it.id == 1L }.activeBizKey)

            val achievementRows = db.habitDao().getAllAchievements().first()
            assertEquals(2, achievementRows.size)
            assertEquals(1, achievementRows.count { it.canonicalType == "perfect_day" })
            assertEquals(1L, achievementRows.single { it.canonicalType == "perfect_day" }.id)

            db.openHelper.writableDatabase.query("SELECT * FROM rewards").use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow("id")
                val presetColumn = cursor.getColumnIndexOrThrow("presetKey")
                val rows = mutableListOf<Pair<Long, String?>>()
                while (cursor.moveToNext()) rows += cursor.getLong(idColumn) to cursor.getString(presetColumn)
                assertEquals(8, rows.size)
                val sameNameRewardRows = rows.filter { (id, _) -> id in 1L..7L }
                val exactPresetRows = sameNameRewardRows.filter { (id, _) -> id in 6L..7L }
                assertEquals(7, sameNameRewardRows.size)
                assertEquals(2, exactPresetRows.size)
                assertEquals(1, sameNameRewardRows.count { it.second == expectedPresetKey })
                assertEquals(6L, rows.single { it.second == expectedPresetKey }.first)
                (1L..5L).forEach { id -> assertNull(rows.single { it.first == id }.second) }
                assertNull(rows.single { it.first == 7L }.second)
                assertNull(rows.single { it.first == 8L }.second)
            }

            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    db.pointApprovalDao().insert(
                        PointApprovalEntity(
                            actionType = "TASK_COMPLETE",
                            description = "second active row",
                            requestedPoints = 5,
                            activeBizKey = duplicateKey
                        )
                    )
                }
            }
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun canonicalApprovalPrefersPendingThenApprovedThenLatest() = runBlocking {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                """INSERT INTO point_approvals
                    (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt, taskId, bizKey, awardedRecordId, date)
                    VALUES
                    (1, 'TASK_COMPLETE', 'old approved', 5, 'APPROVED', 100, 101, 1, '$approvedKey', 30, '2026-11-25'),
                    (2, 'TASK_COMPLETE', 'pending', 5, 'PENDING', 200, 0, 1, '$approvedKey', NULL, '2026-11-25'),
                    (3, 'TASK_COMPLETE', 'old rejected', 5, 'REJECTED', 300, 301, 1, '$rejectedKey', NULL, '2026-11-25'),
                    (4, 'TASK_COMPLETE', 'new rejected', 5, 'REJECTED', 400, 401, 1, '$rejectedKey', NULL, '2026-11-25'),
                    (5, 'OTHER', 'empty key', 5, 'PENDING', 500, 0, NULL, '', NULL, ''),
                    (6, 'TASK_COMPLETE', 'cancelled only', 5, 'CANCELLED', 600, 601, 1, '$cancelledKey', NULL, '2026-11-25'),
                    (7, 'TASK_COMPLETE', 'revoked only', 5, 'REVOKED', 700, 701, 1, '$revokedKey', NULL, '2026-11-25')""".trimIndent()
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migrations.MIGRATION_13_14)
        val db = Room.databaseBuilder(instrumentation.targetContext, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val rows = db.pointApprovalDao().getAllApprovals().first()
            assertEquals(7, rows.size)
            assertEquals(2L, rows.single { it.activeBizKey == approvedKey }.id)
            listOf(rejectedKey, cancelledKey, revokedKey).forEach { key ->
                assertTrue("terminal-only group must stay unkeyed: $key", rows.none { it.activeBizKey == key })
            }
            assertNull(rows.single { it.id == 5L }.activeBizKey)

            val runner = roomTransactionRunner(db)
            val pointRepository = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            listOf(rejectedKey, cancelledKey, revokedKey).forEach { key ->
                assertEquals(
                    "terminal-only history must not block retry: $key",
                    CheckInResult.CREATED,
                    pointRepository.checkIn(
                        bizKey = key,
                        actionType = "TASK_COMPLETE",
                        description = "retry $key",
                        points = 5,
                        taskId = 1L,
                        date = "2026-11-25",
                    ),
                )
            }
            val retried = db.pointApprovalDao().getAllApprovals().first()
            listOf(rejectedKey, cancelledKey, revokedKey).forEach { key ->
                assertEquals(1, retried.count { it.activeBizKey == key })
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun frequencyPeriodsAreBackfilledForEveryCadenceWithoutDeletingHistory() = runBlocking {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                """INSERT INTO tasks
                    (id, title, description, category, points, bonusPoints, frequency, difficulty, isMandatory, isActive, sortOrder, createdAt)
                    VALUES
                    (1, 'daily', '', '学习', 5, 0, 'DAILY', 1, 0, 1, 1, 100),
                    (2, 'weekly', '', '学习', 5, 0, 'WEEKLY', 1, 0, 1, 2, 100),
                    (3, 'monthly', '', '学习', 5, 0, 'MONTHLY', 1, 0, 1, 3, 100),
                    (4, 'once', '', '学习', 5, 0, 'ONCE', 1, 0, 1, 4, 100)""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO task_completions (id, taskId, completedAt, pointsEarned, note, date)
                    VALUES
                    (1, 1, 100, 5, 'daily', '2026-03-30'),
                    (2, 2, 200, 5, 'weekly', '2026-04-01'),
                    (3, 3, 300, 5, 'monthly', '2026-04-01'),
                    (4, 4, 400, 5, 'once', '2025-11-25'),
                    (5, 999, 500, 5, 'missing task', '2026-04-01')""".trimIndent()
            )
            db.execSQL(
                """INSERT INTO point_approvals
                    (id, actionType, description, requestedPoints, status, requestedAt, reviewedAt, taskId, bizKey, awardedRecordId, date)
                    VALUES
                    (1, 'TASK_COMPLETE', 'daily', 5, 'PENDING', 100, 0, 1, 'TASK:1:2026-03-30', NULL, '2026-03-30'),
                    (2, 'TASK_COMPLETE', 'weekly', 5, 'PENDING', 200, 0, 2, 'TASK:2:2026-04-01', NULL, '2026-04-01'),
                    (3, 'TASK_COMPLETE', 'monthly', 5, 'PENDING', 300, 0, 3, 'TASK:3:2026-04-01', NULL, '2026-04-01'),
                    (4, 'TASK_COMPLETE', 'once', 5, 'PENDING', 400, 0, 4, 'TASK:4:2025-11-25', NULL, '2025-11-25'),
                    (5, 'OTHER', 'non-task', 5, 'PENDING', 500, 0, 1, 'OTHER:1', NULL, '2026-04-01'),
                    (6, 'TASK_COMPLETE', 'missing task', 5, 'PENDING', 600, 0, 999, 'TASK:999:2026-04-01', NULL, '2026-04-01')""".trimIndent()
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migrations.MIGRATION_13_14)
        val db = Room.databaseBuilder(instrumentation.targetContext, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            db.openHelper.writableDatabase.query("SELECT id, frequencyPeriod FROM task_completions ORDER BY id").use { cursor ->
                val periods = mutableMapOf<Long, String?>()
                while (cursor.moveToNext()) {
                    periods[cursor.getLong(0)] = if (cursor.isNull(1)) null else cursor.getString(1)
                }
                assertEquals(
                    mapOf(
                        1L to "2026-03-30",
                        2L to "WEEK:2026-03-30",
                        3L to "MONTH:2026-04",
                        4L to "ONCE",
                        5L to null
                    ),
                    periods
                )
            }
            val approvals = db.pointApprovalDao().getAllApprovals().first().sortedBy { it.id }
            assertEquals(6, approvals.size)
            assertEquals(listOf("2026-03-30", "WEEK:2026-03-30", "MONTH:2026-04", "ONCE", null, null), approvals.map { it.frequencyPeriod })
            assertEquals(5, db.pointDao().getTotalCompletionCount())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-v13-v14-test"
        const val duplicateKey = "TASK:1:2026-11-25"
        const val approvedKey = "TASK:2:2026-11-25"
        const val rejectedKey = "TASK:3:2026-11-25"
        const val cancelledKey = "TASK:4:2026-11-25"
        const val revokedKey = "TASK:5:2026-11-25"
        const val expectedPresetKey = "看一集动画片"
    }
}
