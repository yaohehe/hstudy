package com.hstudy.app

import android.app.Application
import androidx.room.Room
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.MigrationSafetyHelper
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HStudyApp : Application() {

    lateinit var database: AppDatabase
    lateinit var taskRepository: TaskRepository
    lateinit var pointRepository: PointRepository
    lateinit var rewardRepository: RewardRepository
    lateinit var mistakeRepository: MistakeRepository
    lateinit var focusRepository: FocusRepository
    lateinit var habitRepository: HabitRepository
    lateinit var proofRepository: ProofRepository
    lateinit var assignmentRepository: AssignmentRepository
    lateinit var wishGoalRepository: WishGoalRepository
    lateinit var scheduleRepository: ScheduleRepository
    lateinit var pointApprovalRepository: com.hstudy.app.data.repository.PointApprovalRepository
    lateinit var userPreferences: UserPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "hstudy_db"
        )
            // 正式迁移路径（v1→v2→…→v12），升级时保留用户数据，不再使用破坏性迁移
            .addMigrations(*com.hstudy.app.data.local.Migrations.ALL)
            // 迁移安全网：迁移前自动备份、迁移后做完整性校验（见 MigrationSafetyHelper）
            .addCallback(MigrationSafetyHelper.callback())
            .build()

        taskRepository = TaskRepository(database.taskDao())
        pointRepository = PointRepository(database.pointDao(), database.pointApprovalDao())
        rewardRepository = RewardRepository(database.rewardDao())
        mistakeRepository = MistakeRepository(database.mistakeDao())
        focusRepository = FocusRepository(database.focusDao())
        habitRepository = HabitRepository(database.habitDao())
        proofRepository = ProofRepository(database.habitDao())
        assignmentRepository = AssignmentRepository(database.dailyAssignmentDao())
        wishGoalRepository = WishGoalRepository(database.wishGoalDao(), database.pointDao())
        scheduleRepository = ScheduleRepository(database.dailyScheduleDao())
        pointApprovalRepository = com.hstudy.app.data.repository.PointApprovalRepository(database.pointApprovalDao())
        userPreferences = UserPreferences(applicationContext)

        // 初始化默认数据
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            // 迁移前自动备份当前数据库（无现有库时静默跳过）
            MigrationSafetyHelper.backupIfExists(applicationContext)

            if (!userPreferences.hasOnboarded.first()) {
                taskRepository.initDefaultTasks()
                rewardRepository.initDefaultRewards()
                userPreferences.setOnboarded(true)
            } else {
                // 老用户/数据曾被清空场景：检测到空表时自动补全预设，避免“奖励/任务什么都没有”
                taskRepository.ensureDefaultTasks()
                rewardRepository.ensureDefaultRewards()
            }
            // 启动时重新注册所有日程闹钟（重启后 AlarmManager 会清空）
            com.hstudy.app.ui.schedule.ScheduleAlarmHelper.rescheduleAll(applicationContext)
            // 启动时恢复错题复习每日提醒闹钟
            if (userPreferences.reviewReminderEnabled.first()) {
                val hour = userPreferences.reviewReminderHour.first()
                com.hstudy.app.ui.mistake.ReviewReminderReceiver.schedule(applicationContext, hour)
            }
            // 启动时恢复家长每周学习周报闹钟
            if (userPreferences.weeklyReportEnabled.first()) {
                com.hstudy.app.ui.parent.WeeklyReportReceiver.schedule(applicationContext)
            }
        }
    }

    companion object {
        lateinit var instance: HStudyApp
            private set
    }
}
