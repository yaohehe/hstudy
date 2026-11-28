package com.hstudy.app.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.dao.DailyTaskSnapshotDao
import com.hstudy.app.data.local.dao.FamilyProgressDao
import com.hstudy.app.data.local.dao.ProgressAggregate
import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import com.hstudy.app.data.local.entity.FocusSessionEntity
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.DailyTaskSnapshotWriter
import com.hstudy.app.platform.FileSharer
import com.hstudy.app.platform.ShareResult
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.FixedAppClock
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilySyncSafetyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var db: AppDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var cacheDir: Path
    private lateinit var cacheDirectory: File
    private val sharer = RecordingFileSharer()

    @Before
    fun setUp() {
        db = testDatabase(context)
        preferences = UserPreferences(testDataStore(context, "family-sync-${UUID.randomUUID()}"))
        cacheDirectory = File(context.cacheDir, "family-sync-${UUID.randomUUID()}").apply {
            assertTrue(mkdirs())
        }
        cacheDir = cacheDirectory.path.replace('\\', '/').toPath()
    }

    @After
    fun tearDown() {
        db.close()
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun missingControlFieldsAreRejectedBeforeDeletingTenTasksAndRewards() = runBlocking {
        seedTenTasksAndRewards()

        val result = helper().importControlConfig("""{"type":"parent_control"}""")

        assertFalse(result.isSuccess)
        assertEquals(10, db.taskDao().getAllActiveTasksSnapshot().size)
        assertEquals(10, db.rewardDao().getActiveRewardCount())
    }

    @Test
    fun invalidControlValuesAreRejectedBeforeStartingTransaction() = runBlocking {
        seedTenTasksAndRewards()
        val transactionRunner = CountingTransactionRunner(roomTransactionRunner(db))
        val invalid = controlJson(
            tasks = buildJsonArrayOfTaskTitles(listOf("new-task")),
            taskPoints = -1,
        )

        val result = helper(transactionRunner = transactionRunner).importControlConfig(invalid.toString())

        assertFalse(result.isSuccess)
        assertEquals(0, transactionRunner.calls)
        assertEquals(10, db.taskDao().getAllActiveTasksSnapshot().size)
        assertEquals(10, db.rewardDao().getActiveRewardCount())
    }

    @Test
    fun malformedControlEnumsAreRejectedBeforeStartingTransaction() = runBlocking {
        seedTenTasksAndRewards()
        val transactionRunner = CountingTransactionRunner(roomTransactionRunner(db))
        val invalidFrequency = controlJson(
            tasks = buildJsonArrayOfTaskTitles(listOf("new-task"), frequency = "FORTNIGHTLY"),
        )
        val invalidCategory = controlJson(
            tasks = buildJsonArrayOfTaskTitles(listOf("new-task"), category = "NOT_A_CATEGORY"),
        )

        assertFalse(helper(transactionRunner = transactionRunner).importControlConfig(invalidFrequency.toString()).isSuccess)
        assertFalse(helper(transactionRunner = transactionRunner).importControlConfig(invalidCategory.toString()).isSuccess)
        assertEquals(0, transactionRunner.calls)
        assertEquals(10, db.taskDao().getAllActiveTasksSnapshot().size)
        assertEquals(10, db.rewardDao().getActiveRewardCount())
    }

    @Test
    fun validControlReplacementCommitsTasksAndRewards() = runBlocking {
        seedTenTasksAndRewards()

        val result = helper().importControlConfig(validControlJson().toString())

        assertTrue(result.isSuccess)
        assertEquals(listOf("new-task-1", "new-task-2"), db.taskDao().getAllActiveTasksSnapshot().map { it.title })
        assertEquals(setOf("new-reward-1", "new-reward-2"), activeRewardNames().toSet())
    }

    @Test
    fun firstOfDayControlImportCapturesReplacementActiveDenominator() = runBlocking {
        db.taskDao().insert(
            TaskEntity(title = "old-task", category = TaskCategory.BASIC, points = 5)
        )
        val clock = FixedAppClock(NOW)
        val snapshotWriter = DailyTaskSnapshotWriter(db.taskDao(), db.dailyTaskSnapshotDao(), clock)
        assertEquals(TODAY, clock.today())
        assertNull(db.dailyTaskSnapshotDao().getByDateSync(TODAY))

        val result = helper(snapshotWriter = snapshotWriter).importControlConfig(validControlJson().toString())

        assertTrue(result.isSuccess)
        val snapshot = db.dailyTaskSnapshotDao().getByDateSync(TODAY)!!
        val importedIds = db.taskDao().getAllActiveTasksSnapshot().map { it.id }.sorted()
        assertEquals(importedIds, parseSnapshotIds(snapshot.taskIdsJson))
        assertEquals(2, snapshot.totalCount)
        assertEquals(clock.nowMillis(), snapshot.createdAt)
    }

    @Test
    fun snapshotFailureRollsBackEntireControlReplacement() = runBlocking {
        seedTenTasksAndRewards()
        val originalTasks = db.taskDao().getAllActiveTasksSnapshot()
        val originalRewards = activeRewardNames()
        val failingSnapshotDao = object : DailyTaskSnapshotDao by db.dailyTaskSnapshotDao() {
            override suspend fun insertIfAbsent(snapshot: DailyTaskSnapshotEntity) =
                error("injected snapshot failure")
        }
        val snapshotWriter = DailyTaskSnapshotWriter(db.taskDao(), failingSnapshotDao, FixedAppClock(NOW))

        val result = helper(snapshotWriter = snapshotWriter)
            .importControlConfig(validControlJson().toString())

        assertFalse(result.isSuccess)
        assertEquals("injected snapshot failure", result.exceptionOrNull()?.message)
        assertEquals(originalTasks, db.taskDao().getAllActiveTasksSnapshot())
        assertEquals(originalRewards, activeRewardNames())
        assertNull(db.dailyTaskSnapshotDao().getByDateSync(TODAY))
    }

    @Test
    fun controlReplacementUnlinksSchedulesForEverySoftDeletedTask() = runBlocking {
        val firstTaskId = db.taskDao().insert(
            TaskEntity(title = "original-task-1", category = TaskCategory.BASIC, points = 5)
        )
        val secondTaskId = db.taskDao().insert(
            TaskEntity(title = "original-task-2", category = TaskCategory.HABIT, points = 5)
        )
        val firstScheduleId = db.dailyScheduleDao().insert(
            DailyScheduleEntity(time = "16:00", title = "first", linkedTaskId = firstTaskId)
        )
        val secondScheduleId = db.dailyScheduleDao().insert(
            DailyScheduleEntity(time = "17:00", title = "second", linkedTaskId = secondTaskId)
        )

        val result = helper().importControlConfig(validControlJson().toString())

        assertTrue(result.isSuccess)
        assertEquals(false, db.taskDao().getTaskById(firstTaskId)?.isActive)
        assertEquals(false, db.taskDao().getTaskById(secondTaskId)?.isActive)
        assertNull(db.dailyScheduleDao().getScheduleById(firstScheduleId)?.linkedTaskId)
        assertNull(db.dailyScheduleDao().getScheduleById(secondScheduleId)?.linkedTaskId)
    }

    @Test
    fun scheduleUnlinkFailureRollsBackEntireControlReplacement() = runBlocking {
        seedTenTasksAndRewards()
        val originalTasks = db.taskDao().getAllActiveTasksSnapshot()
        val originalRewards = activeRewardNames()
        val taskId = originalTasks.first().id
        val scheduleId = db.dailyScheduleDao().insert(
            DailyScheduleEntity(time = "16:00", title = "linked", linkedTaskId = taskId)
        )
        val failingScheduleDao = object : DailyScheduleDao by db.dailyScheduleDao() {
            override suspend fun unlinkTask(taskId: Long): Int = error("injected schedule unlink failure")
        }

        val result = helper(dailyScheduleDao = failingScheduleDao)
            .importControlConfig(validControlJson().toString())

        assertFalse(result.isSuccess)
        assertEquals(originalTasks, db.taskDao().getAllActiveTasksSnapshot())
        assertEquals(originalRewards, activeRewardNames())
        assertEquals(taskId, db.dailyScheduleDao().getScheduleById(scheduleId)?.linkedTaskId)
    }

    @Test
    fun secondReplacementInsertFailureRollsBackTaskAndRewardReplacement() = runBlocking {
        seedTenTasksAndRewards()
        val originalTasks = db.taskDao().getAllActiveTasksSnapshot()
        val originalRewards = activeRewardNames()
        var inserts = 0
        val failingRewardDao = object : RewardDao by db.rewardDao() {
            override suspend fun insertReward(reward: RewardEntity): Long {
                inserts++
                if (inserts == 2) error("injected second replacement insert failure")
                return db.rewardDao().insertReward(reward)
            }
        }

        val result = helper(rewardDao = failingRewardDao).importControlConfig(validControlJson().toString())

        assertFalse(result.isSuccess)
        assertEquals(2, inserts)
        assertEquals(originalTasks, db.taskDao().getAllActiveTasksSnapshot())
        assertEquals(originalRewards, activeRewardNames())
    }

    @Test
    fun emptyReplacementRequiresExplicitAllowEmpty() = runBlocking {
        seedTenTasksAndRewards()
        val dto = controlJson(tasks = JsonArray(emptyList()), rewards = JsonArray(emptyList()), allowEmpty = false)

        assertFalse(helper().importControlConfig(dto.toString()).isSuccess)
        assertEquals(10, db.taskDao().getAllActiveTasksSnapshot().size)
        assertEquals(10, db.rewardDao().getActiveRewardCount())

        val allowed = helper().importControlConfig(
            controlJson(tasks = JsonArray(emptyList()), rewards = JsonArray(emptyList()), allowEmpty = true).toString()
        )
        assertTrue(allowed.isSuccess)
        assertEquals(0, db.taskDao().getAllActiveTasksSnapshot().size)
        assertEquals(0, db.rewardDao().getActiveRewardCount())
    }

    @Test
    fun omittedAllowEmptyIsRejectedBeforeStartingTransaction() = runBlocking {
        seedTenTasksAndRewards()
        val transactionRunner = CountingTransactionRunner(roomTransactionRunner(db))
        val omittedAllowEmpty = JsonObject(
            controlJson(tasks = JsonArray(emptyList()), rewards = JsonArray(emptyList()))
                .filterKeys { it != "allowEmpty" }
        )

        val result = helper(transactionRunner = transactionRunner).importControlConfig(omittedAllowEmpty.toString())

        assertFalse(result.isSuccess)
        assertEquals(0, transactionRunner.calls)
        assertEquals(10, db.taskDao().getAllActiveTasksSnapshot().size)
        assertEquals(10, db.rewardDao().getActiveRewardCount())
    }

    @Test
    fun controlExportContainsSchemaVersionAndExportTime() = runBlocking {
        seedTenTasksAndRewards()

        val path = helper().exportControlConfig().getOrThrow()
        val dto = json.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonObject

        assertEquals("parent_control", dto.getValue("type").jsonPrimitive.content)
        assertEquals(1, dto.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(NOW, dto.getValue("exportedAt").jsonPrimitive.long)
        assertFalse(dto.getValue("allowEmpty").jsonPrimitive.boolean)
    }

    @Test
    fun progressExportUsesOneAggregateAndDisclosesDataStoreSampleTime() = runBlocking {
        seedProgress()
        preferences.setDeviceName("Child tablet")
        var aggregateReads = 0
        val gatedProgressDao = object : FamilyProgressDao by db.familyProgressDao() {
            override suspend fun getProgressAggregate(today: String, since: Long): ProgressAggregate {
                aggregateReads++
                val snapshot = db.familyProgressDao().getProgressAggregate(today, since)
                // This write lands after the sole aggregate read. The exported DTO must remain
                // the old, internally consistent snapshot rather than mixing old/new reads.
                db.pointDao().insertPointRecord(
                    PointRecordEntity(points = 5, type = PointRecordType.ADJUSTMENT, note = "after snapshot", date = NOW)
                )
                return snapshot
            }
        }

        val path = helper(progressDao = gatedProgressDao).exportProgressReport().getOrThrow()
        val dto = json.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonObject

        assertEquals(1, aggregateReads)
        assertEquals("child_progress", dto.getValue("type").jsonPrimitive.content)
        assertEquals(1, dto.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(NOW, dto.getValue("exportedAt").jsonPrimitive.long)
        assertEquals(NOW, dto.getValue("preferencesSampledAt").jsonPrimitive.long)
        assertEquals("Child tablet", dto.getValue("deviceName").jsonPrimitive.content)
        assertEquals(10, dto.getValue("totalPoints").jsonPrimitive.int)
        assertEquals(1, dto.getValue("totalCompletions").jsonPrimitive.int)
        assertEquals(20, dto.getValue("totalFocusMinutes").jsonPrimitive.int)
        assertEquals(1, dto.getValue("todayCompletedTasks").jsonPrimitive.int)
        assertEquals(20, dto.getValue("todayFocusMinutes").jsonPrimitive.int)
        assertEquals(3, dto.getValue("totalMistakes").jsonPrimitive.int)
        assertEquals(1, dto.getValue("masteredMistakes").jsonPrimitive.int)
        assertEquals(2, dto.getValue("unmasteredMistakes").jsonPrimitive.int)
        assertEquals(15, db.pointDao().getTotalPoints().first().let { it })
    }

    @Test
    fun progressExportCapturesDbSnapshotTimeInsideTransaction() = runBlocking {
        seedProgress()
        val clockValues = ArrayDeque(listOf(NOW, NOW, NOW + 60_000))
        var nowCalls = 0

        val path = helper(
            now = {
                nowCalls++
                clockValues.removeFirst()
            },
        ).exportProgressReport().getOrThrow()
        val dto = json.parseToJsonElement(FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }).jsonObject

        assertEquals(NOW, dto.getValue("preferencesSampledAt").jsonPrimitive.long)
        assertEquals(NOW, dto.getValue("exportedAt").jsonPrimitive.long)
        assertEquals(2, nowCalls)
    }

    @Test
    fun progressImportAcceptsValidVersionedSummary() = runBlocking {
        val summary = helper().importProgressReport(validProgressJson().toString()).getOrThrow()

        assertEquals(1, summary.schemaVersion)
        assertEquals(NOW, summary.exportedAt)
        assertEquals(NOW, summary.preferencesSampledAt)
        assertEquals(10, summary.totalPoints)
        assertEquals(2, summary.unmasteredMistakes)
    }

    @Test
    fun progressImportRejectsWrongTypeAndSchemaVersion() = runBlocking {
        val wrongType = validProgressJson { put("type", "parent_control") }
        val wrongSchema = validProgressJson { put("schemaVersion", 2) }

        assertFalse(helper().importProgressReport(wrongType.toString()).isSuccess)
        assertFalse(helper().importProgressReport(wrongSchema.toString()).isSuccess)
    }

    @Test
    fun progressImportRejectsNegativePointsAndFocus() = runBlocking {
        val negativePoints = validProgressJson { put("totalPoints", -1) }
        val negativeFocus = validProgressJson { put("totalFocusMinutes", -1) }
        val negativeTodayFocus = validProgressJson { put("todayFocusMinutes", -1) }

        assertFalse(helper().importProgressReport(negativePoints.toString()).isSuccess)
        assertFalse(helper().importProgressReport(negativeFocus.toString()).isSuccess)
        assertFalse(helper().importProgressReport(negativeTodayFocus.toString()).isSuccess)
    }

    @Test
    fun progressImportRejectsImpossibleMistakeTotals() = runBlocking {
        val masteredTooLarge = validProgressJson {
            put("totalMistakes", 1)
            put("masteredMistakes", 2)
            put("unmasteredMistakes", 0)
        }
        val inconsistentUnmastered = validProgressJson { put("unmasteredMistakes", 1) }

        assertFalse(helper().importProgressReport(masteredTooLarge.toString()).isSuccess)
        assertFalse(helper().importProgressReport(inconsistentUnmastered.toString()).isSuccess)
    }

    @Test
    fun progressImportRejectsImpossibleDailyTotalsAndFutureExportTime() = runBlocking {
        val tooManyToday = validProgressJson {
            put("totalCompletions", 1)
            put("todayCompletedTasks", 2)
        }
        val tooMuchTodayFocus = validProgressJson {
            put("totalFocusMinutes", 5)
            put("todayFocusMinutes", 6)
        }
        val futureExport = validProgressJson { put("exportedAt", NOW + 60_000) }

        assertFalse(helper().importProgressReport(tooManyToday.toString()).isSuccess)
        assertFalse(helper().importProgressReport(tooMuchTodayFocus.toString()).isSuccess)
        assertFalse(helper().importProgressReport(futureExport.toString()).isSuccess)
    }

    @Test
    fun shareFileReturnsPlatformFailureInsteadOfSwallowingIt() = runBlocking {
        val path = helper().exportControlConfig().getOrThrow()

        val result = helper().shareFile(path.toString(), "share")

        assertEquals(ShareResult.Failed("injected share failure"), result)
    }

    private suspend fun seedTenTasksAndRewards() {
        repeat(10) { index ->
            db.taskDao().insert(
                TaskEntity(
                    title = "original-task-${index + 1}",
                    category = TaskCategory.BASIC,
                    points = 5,
                )
            )
            db.rewardDao().insertReward(RewardEntity(name = "original-reward-${index + 1}", pointsCost = 20))
        }
    }

    private suspend fun activeRewardNames(): List<String> =
        db.rewardDao().getAllActiveRewards().first().map { it.name }

    private suspend fun seedProgress() {
        db.pointDao().insertPointRecord(
            PointRecordEntity(points = 10, type = PointRecordType.ADJUSTMENT, note = "seed", date = NOW)
        )
        db.pointDao().insertCompletion(
            TaskCompletionEntity(taskId = 1, pointsEarned = 10, date = TODAY, completedAt = NOW)
        )
        db.focusDao().insertSession(
            FocusSessionEntity(durationMinutes = 20, date = TODAY, completedAt = NOW)
        )
        repeat(3) { index ->
            db.mistakeDao().insert(
                MistakeEntity(
                    subject = MistakeSubject.MATH,
                    title = "mistake-${index + 1}",
                    isMastered = index == 0,
                )
            )
        }
    }

    private fun helper(
        taskDao: TaskDao = db.taskDao(),
        dailyScheduleDao: DailyScheduleDao = db.dailyScheduleDao(),
        snapshotWriter: DailyTaskSnapshotWriter = DailyTaskSnapshotWriter(
            db.taskDao(), db.dailyTaskSnapshotDao(), FixedAppClock(NOW)
        ),
        rewardDao: RewardDao = db.rewardDao(),
        progressDao: FamilyProgressDao = db.familyProgressDao(),
        transactionRunner: DbTransactionRunner = roomTransactionRunner(db),
        now: () -> Long = { NOW },
    ) = FamilySyncHelper(
        database = db,
        userPreferences = preferences,
        transactionRunner = transactionRunner,
        taskDao = taskDao,
        dailyScheduleDao = dailyScheduleDao,
        snapshotWriter = snapshotWriter,
        rewardDao = rewardDao,
        familyProgressDao = progressDao,
        fs = FileSystem.SYSTEM,
        cacheDir = cacheDir,
        fileSharer = sharer,
        now = now,
        today = { TODAY },
    )

    private fun parseSnapshotIds(json: String): List<Long> =
        kotlinx.serialization.json.Json.decodeFromString(json)

    private fun controlJson(
        tasks: JsonArray = buildJsonArrayOfTaskTitles(listOf("new-task-1", "new-task-2")),
        rewards: JsonArray = buildJsonArrayOfRewardNames(listOf("new-reward-1", "new-reward-2")),
        allowEmpty: Boolean = false,
        taskPoints: Int = 5,
    ) = buildJsonObject {
        put("type", "parent_control")
        put("schemaVersion", 1)
        put("exportedAt", NOW)
        put("deviceName", "Parent tablet")
        put("allowEmpty", allowEmpty)
        put("tasks", tasks)
        put("rewards", rewards)
        if (tasks.isNotEmpty()) {
            // Individual task point overrides are applied by the dedicated invalid-input test.
            if (taskPoints != 5) {
                put("tasks", buildJsonArrayOfTaskTitles(listOf("new-task-1", "new-task-2"), taskPoints))
            }
        }
    }

    private fun validControlJson() = controlJson()

    private fun buildJsonArrayOfTaskTitles(
        titles: List<String>,
        points: Int = 5,
        frequency: String = "DAILY",
        category: String = "BASIC",
    ): JsonArray = JsonArray(
        titles.map { title ->
            buildJsonObject {
                put("title", title)
                put("points", points)
                put("difficulty", 1)
                put("frequency", frequency)
                put("category", category)
            }
        }
    )

    private fun buildJsonArrayOfRewardNames(names: List<String>): JsonArray = JsonArray(
        names.map { name ->
            buildJsonObject {
                put("title", name)
                put("pointsCost", 20)
                put("emoji", "🎁")
            }
        }
    )

    private fun validProgressJson(override: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("type", "child_progress")
        put("schemaVersion", 1)
        put("exportedAt", NOW)
        put("preferencesSampledAt", NOW)
        put("deviceName", "Child tablet")
        put("totalPoints", 10)
        put("streak", 1)
        put("totalCompletions", 2)
        put("totalFocusMinutes", 30)
        put("todayCompletedTasks", 1)
        put("todayFocusMinutes", 5)
        put("totalMistakes", 3)
        put("masteredMistakes", 1)
        put("unmasteredMistakes", 2)
        override()
    }

    private class CountingTransactionRunner(
        private val delegate: DbTransactionRunner,
    ) : DbTransactionRunner {
        var calls = 0
            private set

        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
            calls++
            return delegate.runInTransaction(block)
        }
    }

    private class RecordingFileSharer : FileSharer {
        override fun shareTextFile(
            fileName: String,
            content: String,
            mimeType: String,
            onResult: (ShareResult) -> Unit,
        ): ShareResult = ShareResult.Failed("injected share failure")

        override fun pickTextFile(onResult: (String?) -> Unit) = Unit
    }

    private companion object {
        val DATE = LocalDate(2026, 11, 25)
        val NOW = DATE.atTime(12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val TODAY = DATE.toString()
    }
}
