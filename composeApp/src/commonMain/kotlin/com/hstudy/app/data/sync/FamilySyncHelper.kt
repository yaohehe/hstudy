package com.hstudy.app.data.sync

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.dao.FamilyProgressDao
import com.hstudy.app.data.local.dao.ProgressAggregate
import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.DailyTaskSnapshotWriter
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.platform.FileSharer
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ShareResult
import com.hstudy.app.platform.ioDispatcher
import com.hstudy.app.platform.systemFileSystem
import com.hstudy.app.time.SystemAppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 家庭同步助手：实现家长设备与孩子设备之间的"控制"与"进度"同步。
 *
 * 控制包导入在完整校验后用一个 Room 事务替换任务/奖励；进度导出用一条 Room 聚合语句
 * 取得同一 SQLite 快照，并另外披露 DataStore 设备名的采样时间。分享结果不再被吞掉。
 */
class FamilySyncHelper internal constructor(
    private val database: AppDatabase = AppGraph.database,
    private val userPreferences: UserPreferences = AppGraph.userPreferences,
    private val transactionRunner: DbTransactionRunner = roomTransactionRunner(database),
    private val taskDao: TaskDao = database.taskDao(),
    private val dailyScheduleDao: DailyScheduleDao = database.dailyScheduleDao(),
    private val snapshotWriter: DailyTaskSnapshotWriter = DailyTaskSnapshotWriter(
        taskDao, database.dailyTaskSnapshotDao(), SystemAppClock
    ),
    private val rewardDao: RewardDao = database.rewardDao(),
    private val familyProgressDao: FamilyProgressDao = database.familyProgressDao(),
    private val fs: FileSystem = systemFileSystem,
    private val cacheDir: Path = Platform.cacheDir.toPath(),
    private val fileSharer: FileSharer = Platform.fileSharer,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val today: () -> String = { PointRepository.getTodayString() },
) {
    companion object {
        private const val CONTROL_FILE = "hstudy_parent_control.json"
        private const val PROGRESS_FILE = "hstudy_child_progress.json"
        private const val SCHEMA_VERSION = 1
        private const val MAX_ITEMS = 1_000

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        suspend fun exportControlConfig(): Result<String> = FamilySyncHelper().exportControlConfig()
        suspend fun importControlConfig(text: String): Result<String> = FamilySyncHelper().importControlConfig(text)
        suspend fun exportProgressReport(): Result<String> = FamilySyncHelper().exportProgressReport()
        suspend fun importProgressReport(text: String): Result<ChildProgressSummary> =
            FamilySyncHelper().importProgressReport(text)

        fun shareFile(
            filePath: String,
            title: String,
            onResult: (ShareResult) -> Unit = {},
        ): ShareResult = FamilySyncHelper().shareFile(filePath, title, onResult)
    }

    @Serializable
    private data class ControlTaskDto(
        val title: String,
        val points: Int,
        val difficulty: Int,
        val frequency: String,
        val category: String,
    )

    @Serializable
    private data class ControlRewardDto(
        val title: String,
        val pointsCost: Int,
        val emoji: String,
    )

    @Serializable
    private data class ParentControlDto(
        val type: String,
        val schemaVersion: Int,
        val exportedAt: Long,
        val deviceName: String,
        val allowEmpty: Boolean = false,
        val tasks: List<ControlTaskDto>,
        val rewards: List<ControlRewardDto>,
    )

    @Serializable
    private data class ChildProgressDto(
        val type: String,
        val schemaVersion: Int,
        val exportedAt: Long,
        val preferencesSampledAt: Long,
        val deviceName: String,
        val totalPoints: Int,
        val streak: Int,
        val totalCompletions: Int,
        val totalFocusMinutes: Int,
        val todayCompletedTasks: Int,
        val todayFocusMinutes: Int,
        val totalMistakes: Int,
        val masteredMistakes: Int,
        val unmasteredMistakes: Int,
    )

    private data class PreparedControl(
        val deviceName: String,
        val tasks: List<TaskEntity>,
        val rewards: List<RewardEntity>,
    )

    private data class ProgressSnapshot(
        val exportedAt: Long,
        val aggregate: ProgressAggregate,
    )

    /** 导出家长的控制配置（当前任务列表+奖励列表）为JSON文件，返回文件路径 */
    suspend fun exportControlConfig(): Result<String> = withContext(ioDispatcher) {
        try {
            val tasks = taskDao.getAllActiveTasks().first()
            val rewards = rewardDao.getAllActiveRewards().first()
            val exportedAt = now()
            val dto = ParentControlDto(
                type = "parent_control",
                schemaVersion = SCHEMA_VERSION,
                exportedAt = exportedAt,
                deviceName = userPreferences.deviceName.first() ?: "家长设备",
                allowEmpty = tasks.isEmpty() && rewards.isEmpty(),
                tasks = tasks.map { task ->
                    ControlTaskDto(
                        title = task.title,
                        points = task.points,
                        difficulty = task.difficulty,
                        frequency = task.frequency.name,
                        category = task.category.name,
                    )
                },
                rewards = rewards.map { reward ->
                    ControlRewardDto(
                        title = reward.name,
                        pointsCost = reward.pointsCost,
                        emoji = reward.iconEmoji,
                    )
                },
            )
            // Do not emit a file that this app's own importer would reject.
            prepareControl(dto, exportedAt)
            val file = cacheDir / CONTROL_FILE
            fs.write(file) { writeUtf8(json.encodeToString(dto)) }
            Result.success(file.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 孩子设备导入家长的控制配置（替换当前任务和奖励）。
     * 空列表必须由文件中的 `allowEmpty = true` 明确授权；所有字段均在事务前校验。
     */
    suspend fun importControlConfig(text: String): Result<String> = withContext(ioDispatcher) {
        try {
            val dto = json.decodeFromString<ParentControlDto>(text)
            val prepared = prepareControl(dto, now())
            val message = "已应用${prepared.deviceName}的设置：${prepared.tasks.size}个任务、${prepared.rewards.size}个奖励"

            transactionRunner.runInTransaction {
                val existingTasks = taskDao.getAllActiveTasksSnapshot()
                existingTasks.forEach { task ->
                    taskDao.softDelete(task.id)
                    dailyScheduleDao.unlinkTask(task.id)
                }
                prepared.tasks.forEach { taskDao.insert(it) }

                val existingRewards = rewardDao.getAllActiveRewards().first()
                existingRewards.forEach { rewardDao.softDeleteReward(it.id) }
                prepared.rewards.forEach { rewardDao.insertReward(it) }
                snapshotWriter.captureActive()
            }
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 导出孩子的学习进度摘要为JSON文件，返回文件路径 */
    suspend fun exportProgressReport(): Result<String> = withContext(ioDispatcher) {
        try {
            // DataStore is outside Room's transaction. Timestamp the observed preference value so
            // consumers can distinguish it from the DB snapshot time (exportedAt).
            val deviceName = userPreferences.deviceName.first() ?: "孩子设备"
            val preferencesSampledAt = now()
            val reportDate = today()
            val since = preferencesSampledAt - 60L * 24 * 60 * 60 * 1000
            val snapshot = transactionRunner.runInTransaction {
                // Sample at snapshot start, before the aggregate query, so exportedAt truthfully
                // identifies the DB snapshot rather than the later file-write time.
                ProgressSnapshot(
                    exportedAt = now(),
                    aggregate = familyProgressDao.getProgressAggregate(reportDate, since),
                )
            }
            val exportedAt = snapshot.exportedAt
            val aggregate = snapshot.aggregate
            val completionDates = aggregate.completionDates
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            val dto = ChildProgressDto(
                type = "child_progress",
                schemaVersion = SCHEMA_VERSION,
                exportedAt = exportedAt,
                preferencesSampledAt = preferencesSampledAt,
                deviceName = deviceName,
                totalPoints = aggregate.totalPoints,
                streak = currentStreak(completionDates, reportDate),
                totalCompletions = aggregate.totalCompletions,
                totalFocusMinutes = aggregate.totalFocusMinutes,
                todayCompletedTasks = aggregate.todayCompletedTasks,
                todayFocusMinutes = aggregate.todayFocusMinutes,
                totalMistakes = aggregate.totalMistakes,
                masteredMistakes = aggregate.masteredMistakes,
                unmasteredMistakes = aggregate.totalMistakes - aggregate.masteredMistakes,
            )
            validateProgress(dto, exportedAt)
            val file = cacheDir / PROGRESS_FILE
            fs.write(file) { writeUtf8(json.encodeToString(dto)) }
            Result.success(file.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 家长设备导入孩子的进度报告（返回摘要供显示）。[text] 为文件完整内容 */
    suspend fun importProgressReport(text: String): Result<ChildProgressSummary> = withContext(ioDispatcher) {
        try {
            val dto = json.decodeFromString<ChildProgressDto>(text)
            validateProgress(dto, now())
            Result.success(
                ChildProgressSummary(
                    deviceName = dto.deviceName,
                    schemaVersion = dto.schemaVersion,
                    exportedAt = dto.exportedAt,
                    preferencesSampledAt = dto.preferencesSampledAt,
                    totalPoints = dto.totalPoints,
                    streak = dto.streak,
                    totalCompletions = dto.totalCompletions,
                    totalFocusMinutes = dto.totalFocusMinutes,
                    todayCompletedTasks = dto.todayCompletedTasks,
                    todayFocusMinutes = dto.todayFocusMinutes,
                    totalMistakes = dto.totalMistakes,
                    masteredMistakes = dto.masteredMistakes,
                    unmasteredMistakes = dto.unmasteredMistakes,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 分享导出的JSON文件；读取/平台分享失败均以 [ShareResult.Failed] 暴露。 */
    fun shareFile(
        filePath: String,
        @Suppress("UNUSED_PARAMETER") title: String,
        onResult: (ShareResult) -> Unit = {},
    ): ShareResult = try {
        val path = filePath.toPath()
        val content = fs.read(path) { readUtf8() }
        fileSharer.shareTextFile(
            fileName = path.name,
            content = content,
            mimeType = "application/json",
            onResult = onResult,
        )
    } catch (e: Exception) {
        ShareResult.Failed(e.message ?: "Could not read the exported file")
    }

    private fun prepareControl(dto: ParentControlDto, currentTime: Long): PreparedControl {
        require(dto.type == "parent_control") { "不是家长控制文件" }
        require(dto.schemaVersion == SCHEMA_VERSION) { "Unsupported parent control schema version" }
        requireValidTimestamp(dto.exportedAt, currentTime, "parent control export")
        requireValidDeviceName(dto.deviceName)
        require(dto.tasks.size <= MAX_ITEMS && dto.rewards.size <= MAX_ITEMS) { "Control file contains too many items" }
        if (!dto.allowEmpty) {
            require(dto.tasks.isNotEmpty() && dto.rewards.isNotEmpty()) {
                "Empty control replacement requires explicit allowEmpty=true"
            }
        }
        val tasks = dto.tasks.map { task ->
            require(task.title.isNotBlank() && task.title.length <= 200) { "Invalid task title" }
            require(task.points in 0..100_000) { "Invalid task points" }
            require(task.difficulty in 1..3) { "Invalid task difficulty" }
            TaskEntity(
                title = task.title.trim(),
                category = TaskCategory.valueOf(task.category),
                points = task.points,
                difficulty = task.difficulty,
                frequency = TaskFrequency.valueOf(task.frequency),
            )
        }
        val rewards = dto.rewards.map { reward ->
            require(reward.title.isNotBlank() && reward.title.length <= 200) { "Invalid reward title" }
            require(reward.pointsCost in 0..100_000) { "Invalid reward cost" }
            require(reward.emoji.isNotBlank() && reward.emoji.length <= 16) { "Invalid reward emoji" }
            RewardEntity(
                name = reward.title.trim(),
                pointsCost = reward.pointsCost,
                iconEmoji = reward.emoji,
            )
        }
        return PreparedControl(dto.deviceName.trim(), tasks, rewards)
    }

    private fun validateProgress(dto: ChildProgressDto, currentTime: Long) {
        require(dto.type == "child_progress") { "不是孩子进度文件" }
        require(dto.schemaVersion == SCHEMA_VERSION) { "Unsupported child progress schema version" }
        requireValidTimestamp(dto.exportedAt, currentTime, "child progress export")
        requireValidTimestamp(dto.preferencesSampledAt, dto.exportedAt, "preference sample")
        requireValidDeviceName(dto.deviceName)
        require(dto.totalPoints >= 0) { "Points cannot be negative" }
        require(dto.totalCompletions >= 0) { "Completion count cannot be negative" }
        require(dto.totalFocusMinutes >= 0) { "Focus minutes cannot be negative" }
        require(dto.todayCompletedTasks >= 0) { "Today's completion count cannot be negative" }
        require(dto.todayFocusMinutes >= 0) { "Today's focus minutes cannot be negative" }
        require(dto.totalMistakes >= 0) { "Mistake count cannot be negative" }
        require(dto.masteredMistakes >= 0) { "Mastered mistake count cannot be negative" }
        require(dto.unmasteredMistakes >= 0) { "Unmastered mistake count cannot be negative" }
        require(dto.streak in 0..dto.totalCompletions) { "Streak is inconsistent with completions" }
        require(dto.todayCompletedTasks <= dto.totalCompletions) { "Today's completions exceed total completions" }
        require(dto.todayFocusMinutes <= dto.totalFocusMinutes) { "Today's focus exceeds total focus" }
        require(dto.masteredMistakes <= dto.totalMistakes) { "Mastered mistakes exceed total mistakes" }
        require(dto.unmasteredMistakes == dto.totalMistakes - dto.masteredMistakes) {
            "Unmastered mistakes do not match total minus mastered"
        }
    }

    private fun requireValidTimestamp(value: Long, upperBound: Long, label: String) {
        require(value > 0 && value <= upperBound) { "Invalid $label timestamp" }
    }

    private fun requireValidDeviceName(value: String) {
        require(value.isNotBlank() && value.length <= 100) { "Invalid device name" }
    }

    private fun currentStreak(completionDates: Set<String>, reportDate: String): Int {
        var cursor = LocalDate.parse(reportDate)
        if (reportDate !in completionDates) {
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        var streak = 0
        while (cursor.toString() in completionDates) {
            streak++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }
}

/** 孩子进度摘要（家长设备查看用） */
data class ChildProgressSummary(
    val deviceName: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val preferencesSampledAt: Long,
    val totalPoints: Int,
    val streak: Int,
    val totalCompletions: Int,
    val totalFocusMinutes: Int,
    val todayCompletedTasks: Int,
    val todayFocusMinutes: Int,
    val totalMistakes: Int,
    val masteredMistakes: Int,
    val unmasteredMistakes: Int,
)
