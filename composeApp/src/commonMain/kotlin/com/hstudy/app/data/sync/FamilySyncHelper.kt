package com.hstudy.app.data.sync

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ioDispatcher
import com.hstudy.app.platform.systemFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 家庭同步助手：实现家长设备与孩子设备之间的"控制"与"进度"同步。
 *
 * 配对后的核心场景：
 * - 家长→孩子：导出"控制包"（任务/奖励/设置），孩子导入后应用家长的安排
 * - 孩子→家长：导出"进度报告"（积分/打卡/专注/错题摘要），家长导入后查看
 *
 * 通过平台分享系统传输文件（蓝牙/WiFi直连/邮件/云盘等任何方式）。
 *
 * KMP 说明：org.json 改为 kotlinx.serialization，JSON 字段名与旧版导出文件完全一致，
 * 旧文件仍可导入；Context/Uri/FileProvider 改为 [Platform.fileSharer] 与 okio。
 * 导出函数返回导出文件路径（String）；导入函数直接接收文件文本内容
 * （由 UI 通过 Platform.fileSharer.pickTextFile 获取）。
 */
object FamilySyncHelper {

    private const val CONTROL_FILE = "hstudy_parent_control.json"
    private const val PROGRESS_FILE = "hstudy_child_progress.json"

    private val fs: FileSystem get() = systemFileSystem
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ===== JSON DTO（字段名与旧版 org.json 导出完全一致） =====

    @Serializable
    private data class ControlTaskDto(
        val title: String,
        val points: Int,
        val difficulty: Int = 1,
        val frequency: String = "DAILY",
        val category: String = "BASIC"
    )

    @Serializable
    private data class ControlRewardDto(
        val title: String,
        val pointsCost: Int,
        val emoji: String = "🎁"
    )

    @Serializable
    private data class ParentControlDto(
        val type: String = "",
        val version: Int = 1,
        val exportedAt: Long = 0,
        val deviceName: String = "家长",
        val tasks: List<ControlTaskDto> = emptyList(),
        val rewards: List<ControlRewardDto> = emptyList()
    )

    @Serializable
    private data class ChildProgressDto(
        val type: String = "",
        val version: Int = 1,
        val exportedAt: Long = 0,
        val deviceName: String = "孩子设备",
        val totalPoints: Int = 0,
        val streak: Int = 0,
        val totalCompletions: Int = 0,
        val totalFocusMinutes: Int = 0,
        val todayCompletedTasks: Int = 0,
        val todayFocusMinutes: Int = 0,
        val totalMistakes: Int = 0,
        val masteredMistakes: Int = 0,
        val unmasteredMistakes: Int = 0
    )

    // ===== 家长→孩子：控制包（任务+奖励+设置） =====

    /** 导出家长的控制配置（当前任务列表+奖励列表）为JSON文件，返回文件路径 */
    suspend fun exportControlConfig(): Result<String> = withContext(ioDispatcher) {
        try {
            val app = AppGraph
            val tasks = app.taskRepository.getAllActiveTasks().first()
            val rewards = app.rewardRepository.getAllActiveRewards().first()

            val dto = ParentControlDto(
                type = "parent_control",
                version = 1,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                deviceName = app.userPreferences.deviceName.first() ?: "家长设备",
                tasks = tasks.map { t ->
                    ControlTaskDto(
                        title = t.title,
                        points = t.points,
                        difficulty = t.difficulty,
                        frequency = t.frequency.name,
                        category = t.category.name
                    )
                },
                rewards = rewards.map { r ->
                    ControlRewardDto(
                        title = r.name,
                        pointsCost = r.pointsCost,
                        emoji = r.iconEmoji
                    )
                }
            )

            val file = exportPath(CONTROL_FILE)
            fs.write(file) { writeUtf8(json.encodeToString(dto)) }
            Result.success(file.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 孩子设备导入家长的控制配置（替换当前任务和奖励）。[text] 为文件完整内容 */
    suspend fun importControlConfig(text: String): Result<String> = withContext(ioDispatcher) {
        try {
            val dto = json.decodeFromString<ParentControlDto>(text)

            if (dto.type != "parent_control") {
                return@withContext Result.failure(Exception("不是家长控制文件"))
            }

            val app = AppGraph
            val deviceName = dto.deviceName

            // 导入任务（软删除现有→添加家长的）
            val existingTasks = app.taskRepository.getAllActiveTasks().first()
            existingTasks.forEach { app.taskRepository.deleteTask(it.id) }
            dto.tasks.forEach { t ->
                val freq = try { TaskFrequency.valueOf(t.frequency) } catch (_: Exception) { TaskFrequency.DAILY }
                val cat = try { TaskCategory.valueOf(t.category) } catch (_: Exception) { TaskCategory.BASIC }
                app.taskRepository.addTask(TaskEntity(
                    title = t.title,
                    category = cat,
                    points = t.points,
                    difficulty = t.difficulty,
                    frequency = freq
                ))
            }

            // 导入奖励（软删除现有→添加家长的）
            val existingRewards = app.rewardRepository.getAllActiveRewards().first()
            existingRewards.forEach { app.rewardRepository.deleteReward(it.id) }
            dto.rewards.forEach { r ->
                app.rewardRepository.addReward(RewardEntity(
                    name = r.title,
                    pointsCost = r.pointsCost,
                    iconEmoji = r.emoji
                ))
            }

            Result.success("已应用${deviceName}的设置：${dto.tasks.size}个任务、${dto.rewards.size}个奖励")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== 孩子→家长：进度报告 =====

    /** 导出孩子的学习进度摘要为JSON文件，返回文件路径 */
    suspend fun exportProgressReport(): Result<String> = withContext(ioDispatcher) {
        try {
            val app = AppGraph
            val totalPoints = app.pointRepository.getTotalPoints().first()
            val streak = app.pointRepository.getCurrentStreak()
            val totalCompletions = app.pointRepository.getTotalCompletionCount()
            val totalFocusMin = app.focusRepository.getTotalFocusMinutes().first()
            val mistakes = app.mistakeRepository.getAllMistakes().first()
            val masteredCount = app.mistakeRepository.getMasteredCount().first()
            val deviceName = app.userPreferences.deviceName.first() ?: "孩子设备"

            val todayStr = PointRepository.getTodayString()
            val todayCompleted = app.pointRepository.getCompletedTaskIdsForDate(todayStr).first().size
            val todayFocus = app.focusRepository.getTotalFocusMinutesForDate(todayStr).first()

            val dto = ChildProgressDto(
                type = "child_progress",
                version = 1,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                deviceName = deviceName,
                totalPoints = totalPoints,
                streak = streak,
                totalCompletions = totalCompletions,
                totalFocusMinutes = totalFocusMin,
                todayCompletedTasks = todayCompleted,
                todayFocusMinutes = todayFocus,
                totalMistakes = mistakes.size,
                masteredMistakes = masteredCount,
                unmasteredMistakes = mistakes.size - masteredCount
            )

            val file = exportPath(PROGRESS_FILE)
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

            if (dto.type != "child_progress") {
                return@withContext Result.failure(Exception("不是孩子进度文件"))
            }

            val summary = ChildProgressSummary(
                deviceName = dto.deviceName,
                exportedAt = dto.exportedAt,
                totalPoints = dto.totalPoints,
                streak = dto.streak,
                totalCompletions = dto.totalCompletions,
                totalFocusMinutes = dto.totalFocusMinutes,
                todayCompletedTasks = dto.todayCompletedTasks,
                todayFocusMinutes = dto.todayFocusMinutes,
                totalMistakes = dto.totalMistakes,
                masteredMistakes = dto.masteredMistakes,
                unmasteredMistakes = dto.unmasteredMistakes
            )
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 分享导出的JSON文件到另一台设备 */
    fun shareFile(filePath: String, title: String) {
        try {
            val content = fs.read(filePath.toPath()) { readUtf8() }
            Platform.fileSharer.shareTextFile(
                fileName = filePath.toPath().name,
                content = content,
                mimeType = "application/json"
            )
        } catch (_: Exception) {
            // 分享失败不影响主流程
        }
    }

    private fun exportPath(fileName: String): Path = Platform.cacheDir.toPath() / fileName
}

/** 孩子进度摘要（家长设备查看用） */
data class ChildProgressSummary(
    val deviceName: String,
    val exportedAt: Long,
    val totalPoints: Int,
    val streak: Int,
    val totalCompletions: Int,
    val totalFocusMinutes: Int,
    val todayCompletedTasks: Int,
    val todayFocusMinutes: Int,
    val totalMistakes: Int,
    val masteredMistakes: Int,
    val unmasteredMistakes: Int
)