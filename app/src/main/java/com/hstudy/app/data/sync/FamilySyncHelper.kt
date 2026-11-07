package com.hstudy.app.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 家庭同步助手：实现家长设备与孩子设备之间的"控制"与"进度"同步。
 *
 * 配对后的核心场景：
 * - 家长→孩子：导出"控制包"（任务/奖励/设置），孩子导入后应用家长的安排
 * - 孩子→家长：导出"进度报告"（积分/打卡/专注/错题摘要），家长导入后查看
 *
 * 通过 Android 分享系统传输文件（蓝牙/WiFi直连/邮件/云盘等任何方式）。
 */
object FamilySyncHelper {

    private const val CONTROL_FILE = "hstudy_parent_control.json"
    private const val PROGRESS_FILE = "hstudy_child_progress.json"

    // ===== 家长→孩子：控制包（任务+奖励+设置） =====

    /** 导出家长的控制配置（当前任务列表+奖励列表）为JSON文件 */
    suspend fun exportControlConfig(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val app = HStudyApp.instance
            val tasks = app.taskRepository.getAllActiveTasks().first()
            val rewards = app.rewardRepository.getAllActiveRewards().first()

            val json = JSONObject().apply {
                put("type", "parent_control")
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("deviceName", app.userPreferences.deviceName.first() ?: "家长设备")

                // 任务列表
                val tasksArr = JSONArray()
                tasks.forEach { t ->
                    tasksArr.put(JSONObject().apply {
                        put("title", t.title)
                        put("points", t.points)
                        put("difficulty", t.difficulty)
                        put("frequency", t.frequency.name)
                        put("category", t.category.name)
                    })
                }
                put("tasks", tasksArr)

                // 奖励列表
                val rewardsArr = JSONArray()
                rewards.forEach { r ->
                    rewardsArr.put(JSONObject().apply {
                        put("title", r.name)
                        put("pointsCost", r.pointsCost)
                        put("emoji", r.iconEmoji)
                    })
                }
                put("rewards", rewardsArr)
            }

            val file = File(context.getExternalFilesDir(null), CONTROL_FILE)
            file.writeText(json.toString(2))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 孩子设备导入家长的控制配置（替换当前任务和奖励） */
    suspend fun importControlConfig(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext Result.failure(Exception("无法读取文件"))
            val json = JSONObject(text)

            if (json.optString("type") != "parent_control") {
                return@withContext Result.failure(Exception("不是家长控制文件"))
            }

            val app = HStudyApp.instance
            val deviceName = json.optString("deviceName", "家长")

            // 导入任务（软删除现有→添加家长的）
            val tasksArr = json.getJSONArray("tasks")
            val existingTasks = app.taskRepository.getAllActiveTasks().first()
            existingTasks.forEach { app.taskRepository.deleteTask(it.id) }
            for (i in 0 until tasksArr.length()) {
                val t = tasksArr.getJSONObject(i)
                val freq = try { TaskFrequency.valueOf(t.getString("frequency")) } catch (_: Exception) { TaskFrequency.DAILY }
                val cat = try { TaskCategory.valueOf(t.optString("category", "BASIC")) } catch (_: Exception) { TaskCategory.BASIC }
                app.taskRepository.addTask(TaskEntity(
                    title = t.getString("title"),
                    category = cat,
                    points = t.getInt("points"),
                    difficulty = t.optInt("difficulty", 1),
                    frequency = freq
                ))
            }

            // 导入奖励（软删除现有→添加家长的）
            val rewardsArr = json.getJSONArray("rewards")
            val existingRewards = app.rewardRepository.getAllActiveRewards().first()
            existingRewards.forEach { app.rewardRepository.deleteReward(it.id) }
            for (i in 0 until rewardsArr.length()) {
                val r = rewardsArr.getJSONObject(i)
                app.rewardRepository.addReward(RewardEntity(
                    name = r.getString("title"),
                    pointsCost = r.getInt("pointsCost"),
                    iconEmoji = r.optString("emoji", "🎁")
                ))
            }

            Result.success("已应用${deviceName}的设置：${tasksArr.length()}个任务、${rewardsArr.length()}个奖励")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== 孩子→家长：进度报告 =====

    /** 导出孩子的学习进度摘要为JSON文件 */
    suspend fun exportProgressReport(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val app = HStudyApp.instance
            val totalPoints = app.pointRepository.getTotalPoints().first()
            val streak = app.pointRepository.getCurrentStreak()
            val totalCompletions = app.pointRepository.getTotalCompletionCount()
            val totalFocusMin = app.focusRepository.getTotalFocusMinutes().first()
            val mistakes = app.mistakeRepository.getAllMistakes().first()
            val masteredCount = app.mistakeRepository.getMasteredCount().first()
            val deviceName = app.userPreferences.deviceName.first() ?: "孩子设备"

            val todayStr = com.hstudy.app.data.repository.PointRepository.getTodayString()
            val todayCompleted = app.pointRepository.getCompletedTaskIdsForDate(todayStr).first().size
            val todayFocus = app.focusRepository.getTotalFocusMinutesForDate(todayStr).first()

            val json = JSONObject().apply {
                put("type", "child_progress")
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("deviceName", deviceName)
                put("totalPoints", totalPoints)
                put("streak", streak)
                put("totalCompletions", totalCompletions)
                put("totalFocusMinutes", totalFocusMin)
                put("todayCompletedTasks", todayCompleted)
                put("todayFocusMinutes", todayFocus)
                put("totalMistakes", mistakes.size)
                put("masteredMistakes", masteredCount)
                put("unmasteredMistakes", mistakes.size - masteredCount)
            }

            val file = File(context.getExternalFilesDir(null), PROGRESS_FILE)
            file.writeText(json.toString(2))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 家长设备导入孩子的进度报告（返回摘要供显示） */
    suspend fun importProgressReport(context: Context, uri: Uri): Result<ChildProgressSummary> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext Result.failure(Exception("无法读取文件"))
            val json = JSONObject(text)

            if (json.optString("type") != "child_progress") {
                return@withContext Result.failure(Exception("不是孩子进度文件"))
            }

            val summary = ChildProgressSummary(
                deviceName = json.optString("deviceName", "孩子设备"),
                exportedAt = json.optLong("exportedAt", 0),
                totalPoints = json.optInt("totalPoints", 0),
                streak = json.optInt("streak", 0),
                totalCompletions = json.optInt("totalCompletions", 0),
                totalFocusMinutes = json.optInt("totalFocusMinutes", 0),
                todayCompletedTasks = json.optInt("todayCompletedTasks", 0),
                todayFocusMinutes = json.optInt("todayFocusMinutes", 0),
                totalMistakes = json.optInt("totalMistakes", 0),
                masteredMistakes = json.optInt("masteredMistakes", 0),
                unmasteredMistakes = json.optInt("unmasteredMistakes", 0)
            )
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 分享文件到另一台设备 */
    fun shareFile(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
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
