package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.DirectTransactionRunner
import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * 积分任务模板
 */
data class TaskTemplate(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val ageRange: String,
    val tasks: List<TaskEntity>
)

data class ReminderCancellationResult(
    val cancelled: List<Long> = emptyList(),
    val failed: List<Long> = emptyList(),
)

class TaskRepository(
    private val taskDao: TaskDao,
    private val dailyScheduleDao: DailyScheduleDao,
    private val transactionRunner: DbTransactionRunner = DirectTransactionRunner,
    private val snapshotWriter: DailyTaskSnapshotWriter? = null,
    private val reminderCanceller: (List<Long>) -> Unit = {},
) {

    /** Test-only compatibility for callers that never soft-delete tasks or capture snapshots. */
    internal constructor(
        taskDao: TaskDao,
        transactionRunner: DbTransactionRunner = DirectTransactionRunner
    ) : this(taskDao, UnusedDailyScheduleDao, transactionRunner)

    fun getAllActiveTasks(): Flow<List<TaskEntity>> =
        taskDao.getAllActiveTasks().onEach { snapshotWriter?.captureObserved(it) }

    // A category emission is only a subset of the active denominator. Snapshot capture belongs
    // to the full active-task flow, mutations, and report queries, never to this filtered flow.
    fun getTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>> =
        taskDao.getTasksByCategory(category)

    suspend fun getTaskById(id: Long): TaskEntity? = taskDao.getTaskById(id)

    suspend fun addTask(task: TaskEntity): Long = transactionRunner.runInTransaction {
        val id = taskDao.insert(task)
        snapshotWriter?.captureActive()
        id
    }

    suspend fun updateTask(task: TaskEntity) {
        transactionRunner.runInTransaction {
            taskDao.update(task)
            snapshotWriter?.captureActive()
        }
    }

    suspend fun deleteTask(id: Long): ReminderCancellationResult {
        requireScheduleDao()
        val unlinkedScheduleIds = mutableListOf<Long>()
        transactionRunner.runInTransaction {
            unlinkedScheduleIds += dailyScheduleDao.getScheduleIdsForTask(id)
            taskDao.softDelete(id)
            dailyScheduleDao.unlinkTask(id)
            snapshotWriter?.captureActive()
        }
        return cancelRemindersBestEffort(unlinkedScheduleIds)
    }

    /**
     * 获取所有预设模板
     */
    fun getPresets(): List<TaskTemplate> = listOf(
        TaskTemplate(
            id = "primary_low",
            name = "小学低年级",
            description = "适合1-3年级，以基础习惯养成为主，积分值较低易于达成",
            emoji = "🌱",
            ageRange = "6-9岁",
            tasks = listOf(
                TaskEntity(title = "完成当天作业", description = "按时完成老师布置的作业", category = TaskCategory.BASIC, points = 3, bonusPoints = 2, sortOrder = 1),
                TaskEntity(title = "整理书包", description = "自己整理明天的书包", category = TaskCategory.BASIC, points = 2, sortOrder = 2),
                TaskEntity(title = "朗读课文", description = "大声朗读一篇课文", category = TaskCategory.BASIC, points = 2, sortOrder = 3),
                TaskEntity(title = "练字15分钟", description = "认真练字15分钟", category = TaskCategory.BASIC, points = 3, sortOrder = 4),
                TaskEntity(title = "背诵一首古诗", description = "背诵并默写一首古诗", category = TaskCategory.BASIC, points = 3, sortOrder = 5),
                TaskEntity(title = "阅读20分钟", description = "课外阅读20分钟", category = TaskCategory.ENHANCEMENT, points = 3, sortOrder = 1),
                TaskEntity(title = "口算练习", description = "完成一页口算练习", category = TaskCategory.ENHANCEMENT, points = 4, sortOrder = 2),
                TaskEntity(title = "讲一个故事", description = "给家人讲一个完整的故事", category = TaskCategory.ENHANCEMENT, points = 3, sortOrder = 3),
                TaskEntity(title = "认识新字词", description = "学习并记住5个新字词", category = TaskCategory.ENHANCEMENT, points = 3, sortOrder = 4),
                TaskEntity(title = "早睡早起", description = "21:00前上床，7:00前起床", category = TaskCategory.HABIT, points = 2, sortOrder = 1),
                TaskEntity(title = "自己穿衣", description = "自己选择并穿好衣服", category = TaskCategory.HABIT, points = 1, sortOrder = 2),
                TaskEntity(title = "帮忙做家务", description = "帮忙擦桌子、扫地等", category = TaskCategory.HABIT, points = 3, sortOrder = 3),
                TaskEntity(title = "礼貌问好", description = "主动跟长辈、老师问好", category = TaskCategory.HABIT, points = 2, sortOrder = 4),
                TaskEntity(title = "收拾玩具", description = "玩完后自己收拾整理玩具", category = TaskCategory.HABIT, points = 2, sortOrder = 5),
            )
        ),
        TaskTemplate(
            id = "primary_high",
            name = "小学高年级",
            description = "适合4-6年级，注重自主学习能力和深度思考",
            emoji = "🌿",
            ageRange = "9-12岁",
            tasks = listOf(
                TaskEntity(title = "完成当天作业", description = "按时完成老师布置的作业", category = TaskCategory.BASIC, points = 5, bonusPoints = 3, sortOrder = 1),
                TaskEntity(title = "错题订正工整", description = "订正错题，书写工整", category = TaskCategory.BASIC, points = 3, bonusPoints = 1, sortOrder = 2),
                TaskEntity(title = "课前预习", description = "预习明天的课程内容", category = TaskCategory.BASIC, points = 3, sortOrder = 3),
                TaskEntity(title = "复习回顾", description = "复习当天所学内容", category = TaskCategory.BASIC, points = 2, sortOrder = 4),
                TaskEntity(title = "英语单词听写", description = "听写并订正10个单词", category = TaskCategory.BASIC, points = 4, sortOrder = 5),
                TaskEntity(title = "完成课外练习", description = "完成1套课外练习", category = TaskCategory.ENHANCEMENT, points = 8, sortOrder = 1),
                TaskEntity(title = "错题原因分析", description = "写出错误点和正确思路", category = TaskCategory.ENHANCEMENT, points = 5, sortOrder = 2),
                TaskEntity(title = "阅读30分钟", description = "课外阅读至少30分钟", category = TaskCategory.ENHANCEMENT, points = 4, sortOrder = 3),
                TaskEntity(title = "写作练习", description = "100字起步写作练习", category = TaskCategory.ENHANCEMENT, points = 6, sortOrder = 4),
                TaskEntity(title = "科学小实验", description = "动手做一个科学小实验", category = TaskCategory.ENHANCEMENT, points = 7, sortOrder = 5),
                TaskEntity(title = "每日阅读打卡", description = "坚持每天阅读", category = TaskCategory.HABIT, points = 2, sortOrder = 1),
                TaskEntity(title = "帮助同学", description = "主动帮助其他同学", category = TaskCategory.HABIT, points = 6, sortOrder = 2),
                TaskEntity(title = "积极参与课堂", description = "上课积极发言参与", category = TaskCategory.HABIT, points = 10, sortOrder = 3),
                TaskEntity(title = "整理房间", description = "整理自己的房间保持整洁", category = TaskCategory.HABIT, points = 4, sortOrder = 4),
                TaskEntity(title = "坚持运动", description = "跳绳、跑步等运动20分钟", category = TaskCategory.HABIT, points = 4, sortOrder = 5),
            )
        ),
        TaskTemplate(
            id = "junior_high",
            name = "初中",
            description = "适合初中生，注重高效学习、自主管理和深度思考",
            emoji = "🌳",
            ageRange = "12-15岁",
            tasks = listOf(
                TaskEntity(title = "完成当天作业", description = "高质量完成所有作业", category = TaskCategory.BASIC, points = 8, bonusPoints = 5, sortOrder = 1),
                TaskEntity(title = "课前预习笔记", description = "预习并做笔记", category = TaskCategory.BASIC, points = 5, sortOrder = 2),
                TaskEntity(title = "课后复习总结", description = "总结当天知识点", category = TaskCategory.BASIC, points = 5, sortOrder = 3),
                TaskEntity(title = "错题深度分析", description = "分析错因+举一反三", category = TaskCategory.BASIC, points = 6, bonusPoints = 3, sortOrder = 4),
                TaskEntity(title = "英语晨读", description = "晨读英语20分钟", category = TaskCategory.BASIC, points = 4, sortOrder = 5),
                TaskEntity(title = "专题突破练习", description = "针对薄弱科目做专项练习", category = TaskCategory.ENHANCEMENT, points = 10, sortOrder = 1),
                TaskEntity(title = "整理知识框架", description = "用思维导图整理一章知识", category = TaskCategory.ENHANCEMENT, points = 8, sortOrder = 2),
                TaskEntity(title = "英语听说训练", description = "30分钟英语听力或口语练习", category = TaskCategory.ENHANCEMENT, points = 6, sortOrder = 3),
                TaskEntity(title = "深度阅读45分钟", description = "阅读并写读后感", category = TaskCategory.ENHANCEMENT, points = 8, sortOrder = 4),
                TaskEntity(title = "难题挑战", description = "独立攻克3道压轴难题", category = TaskCategory.ENHANCEMENT, points = 12, bonusPoints = 5, sortOrder = 5),
                TaskEntity(title = "番茄钟专注2小时", description = "累计完成4个番茄钟", category = TaskCategory.HABIT, points = 8, sortOrder = 1),
                TaskEntity(title = "自主制定学习计划", description = "制定并执行周学习计划", category = TaskCategory.HABIT, points = 10, sortOrder = 2),
                TaskEntity(title = "体育锻炼30分钟", description = "每天坚持运动", category = TaskCategory.HABIT, points = 5, sortOrder = 3),
                TaskEntity(title = "远离手机2小时", description = "学习时段不玩手机", category = TaskCategory.HABIT, points = 6, sortOrder = 4),
                TaskEntity(title = "早睡不熬夜", description = "22:30前睡觉保证睡眠", category = TaskCategory.HABIT, points = 5, sortOrder = 5),
            )
        )
    )

    /**
     * 单任务模板库 - 家长创建任务时可从中快速选择（按类别分组）
     */
    fun getSingleTaskSuggestions(): List<TaskEntity> = listOf(
        // 基础学习
        TaskEntity(title = "完成当天作业", description = "按时完成老师布置的作业", category = TaskCategory.BASIC, points = 5),
        TaskEntity(title = "课前预习", description = "预习明天的课程内容", category = TaskCategory.BASIC, points = 3),
        TaskEntity(title = "课后复习", description = "复习当天所学知识", category = TaskCategory.BASIC, points = 3),
        TaskEntity(title = "错题订正", description = "订正错题并分析原因", category = TaskCategory.BASIC, points = 4),
        TaskEntity(title = "背诵默写", description = "背诵课文/单词并默写", category = TaskCategory.BASIC, points = 4),
        TaskEntity(title = "整理笔记", description = "整理课堂笔记使其清晰", category = TaskCategory.BASIC, points = 3),
        // 提升拓展
        TaskEntity(title = "课外阅读", description = "课外阅读30分钟以上", category = TaskCategory.ENHANCEMENT, points = 4),
        TaskEntity(title = "写作练习", description = "完成一篇写作练习", category = TaskCategory.ENHANCEMENT, points = 6, difficulty = 2),
        TaskEntity(title = "专项练习", description = "针对薄弱点做专项练习", category = TaskCategory.ENHANCEMENT, points = 8, difficulty = 2),
        TaskEntity(title = "思维导图", description = "用思维导图梳理知识结构", category = TaskCategory.ENHANCEMENT, points = 6, difficulty = 2),
        TaskEntity(title = "英语听说", description = "英语听力或口语训练", category = TaskCategory.ENHANCEMENT, points = 5),
        TaskEntity(title = "难题挑战", description = "独立攻克几道难题", category = TaskCategory.ENHANCEMENT, points = 10, difficulty = 3),
        TaskEntity(title = "科学实验", description = "动手做一个科学小实验", category = TaskCategory.ENHANCEMENT, points = 7, difficulty = 3),
        // 习惯态度
        TaskEntity(title = "早睡早起", description = "规律作息不熬夜", category = TaskCategory.HABIT, points = 3),
        TaskEntity(title = "坚持运动", description = "体育锻炼30分钟", category = TaskCategory.HABIT, points = 4),
        TaskEntity(title = "做家务", description = "主动分担家务劳动", category = TaskCategory.HABIT, points = 3),
        TaskEntity(title = "礼貌待人", description = "主动问好、文明礼貌", category = TaskCategory.HABIT, points = 2),
        TaskEntity(title = "整理房间", description = "保持房间整洁有序", category = TaskCategory.HABIT, points = 3),
        TaskEntity(title = "专注学习", description = "学习时段不分心玩手机", category = TaskCategory.HABIT, points = 5),
        TaskEntity(title = "帮助他人", description = "主动帮助同学或家人", category = TaskCategory.HABIT, points = 5),
    )

    /**
     * 应用模板 - 先清除现有任务，再插入模板任务
     */
    suspend fun applyTemplate(template: TaskTemplate): ReminderCancellationResult {
        requireScheduleDao()
        val unlinkedScheduleIds = mutableListOf<Long>()
        transactionRunner.runInTransaction {
            // 软删除所有现有任务，并在同一事务解除其日程关联
            val existingTasks = taskDao.getAllActiveTasksSnapshot()
            existingTasks.forEach { task ->
                unlinkedScheduleIds += dailyScheduleDao.getScheduleIdsForTask(task.id)
                taskDao.softDelete(task.id)
                dailyScheduleDao.unlinkTask(task.id)
            }
            // 插入模板任务
            template.tasks.forEach { task -> taskDao.insert(task) }
            snapshotWriter?.captureActive()
        }
        return cancelRemindersBestEffort(unlinkedScheduleIds)
    }

    /**
     * 初始化默认任务（首次启动时调用）
     *
     * D-02：按 title 幂等播种——插入前查同名活跃任务，存在即跳过，
     * 杜绝重复任务卡与"分别打卡刷双倍审批分"的防刷漏洞。
     */
    suspend fun initDefaultTasks() {
        transactionRunner.runInTransaction {
            seedDefaultTasks()
            snapshotWriter?.captureActive()
        }
    }

    /** 确保预设任务存在：任务表为空时自动补全（惠及老用户/被清空场景） */
    suspend fun ensureDefaultTasks() {
        transactionRunner.runInTransaction {
            if (taskDao.getActiveTaskCount() == 0) seedDefaultTasks()
            snapshotWriter?.captureActive()
        }
    }

    private suspend fun seedDefaultTasks() {
        // 默认使用小学高年级模板
        val defaultTemplate = getPresets()[1] // primary_high
        defaultTemplate.tasks.forEach { task ->
            if (taskDao.getActiveTaskByTitle(task.title) == null) taskDao.insert(task)
        }
    }

    private fun cancelRemindersBestEffort(scheduleIds: List<Long>): ReminderCancellationResult {
        val cancelled = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        scheduleIds.distinct().forEach { id ->
            try {
                reminderCanceller(listOf(id))
                cancelled += id
            } catch (_: Exception) {
                // The database operation is already committed. One platform failure must not
                // turn it into an apparent delete/template failure or skip the remaining IDs.
                failed += id
            }
        }
        return ReminderCancellationResult(cancelled = cancelled, failed = failed)
    }

    private fun requireScheduleDao() {
        check(dailyScheduleDao !== UnusedDailyScheduleDao) {
            "DailyScheduleDao is required before soft-deleting tasks"
        }
    }
}

/** Only used by the legacy test constructor; production always injects the Room schedule DAO. */
private object UnusedDailyScheduleDao : DailyScheduleDao {
    override suspend fun insert(schedule: DailyScheduleEntity): Long = unsupported()
    override suspend fun update(schedule: DailyScheduleEntity) = unsupported()
    override suspend fun delete(id: Long) = unsupported()
    override fun getAllSchedules(): Flow<List<DailyScheduleEntity>> = unsupported()
    override fun getEnabledSchedules(): Flow<List<DailyScheduleEntity>> = unsupported()
    override suspend fun getScheduleById(id: Long): DailyScheduleEntity? = unsupported()
    override suspend fun setEnabled(id: Long, enabled: Boolean) = unsupported()
    override suspend fun unlinkTask(taskId: Long): Int = unsupported()
    override suspend fun getScheduleIdsForTask(taskId: Long): List<Long> = unsupported()
    override suspend fun getEnabledSchedulesSync(): List<DailyScheduleEntity> = unsupported()

    private fun unsupported(): Nothing = error("DailyScheduleDao is required for schedule operations")
}
