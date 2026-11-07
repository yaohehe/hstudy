package com.hstudy.app

import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.getAppDatabase
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.*
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 跨平台依赖容器：替代原 HStudyApp(Application) 单例 */
object AppGraph {
    lateinit var database: AppDatabase
        private set
    lateinit var taskRepository: TaskRepository
        private set
    lateinit var pointRepository: PointRepository
        private set
    lateinit var rewardRepository: RewardRepository
        private set
    lateinit var mistakeRepository: MistakeRepository
        private set
    lateinit var focusRepository: FocusRepository
        private set
    lateinit var habitRepository: HabitRepository
        private set
    lateinit var proofRepository: ProofRepository
        private set
    lateinit var assignmentRepository: AssignmentRepository
        private set
    lateinit var wishGoalRepository: WishGoalRepository
        private set
    lateinit var scheduleRepository: ScheduleRepository
        private set
    lateinit var pointApprovalRepository: PointApprovalRepository
        private set
    lateinit var userPreferences: UserPreferences
        private set

    /**
     * 启动时预读的语言偏好，供 App 根首帧使用。
     *
     * bootstrap() 由平台入口（Android HStudyApplication.onCreate / iOS initIosPlatform）在任何组合之前调用，
     * 这里的预读通常能在首帧组合开始前完成，于是 collectAsState 的 initial 不再是 null，
     * 避免"存储选择与设备语言相反"的设备（zh 设备存 en、en 设备存 zh）首帧渲染错语言再翻转。
     * 局限（诚实说明）：预读本身是异步的，若首帧早于读取完成，此值仍为 null 并退化为 SYSTEM 推导；
     * 它只收窄窗口，不消除窗口，也不会插入空白帧。
     */
    var startupLanguage: String? = null
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        database = getAppDatabase()
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
        pointApprovalRepository = PointApprovalRepository(database.pointApprovalDao())
        userPreferences = UserPreferences()
    }

    /** 启动引导：默认数据补全 + 恢复提醒（对应原 HStudyApp.onCreate 的启动逻辑） */
    fun bootstrap() {
        init()
        appScope.launch {
            // 首帧语言快照：放在同一次 launch 的最前面，复用既有协程，不新增作用域。
            // 该读取与下一行 hasOnboarded.first() 出自同一个 dataStore.data，失败模式完全相同，
            // 因此这里不加 try/catch 也不会引入新的中断路径；组合侧由 App.kt 的 catch 降级。
            startupLanguage = userPreferences.appLanguage.first()
            if (!userPreferences.hasOnboarded.first()) {
                taskRepository.initDefaultTasks()
                rewardRepository.initDefaultRewards()
                userPreferences.setOnboarded(true)
            } else {
                taskRepository.ensureDefaultTasks()
                rewardRepository.ensureDefaultRewards()
            }
            if (userPreferences.reviewReminderEnabled.first()) {
                val hour = userPreferences.reviewReminderHour.first()
                Platform.notifications.schedule(
                    ScheduledReminder(
                        id = 9001, kind = ReminderKind.REVIEW, hour = hour, minute = 0,
                        title = "错题复习提醒", body = "今天还有错题等着复习，快去看看吧！"
                    )
                )
            }
            if (userPreferences.weeklyReportEnabled.first()) {
                Platform.notifications.schedule(
                    ScheduledReminder(
                        id = 9002, kind = ReminderKind.WEEKLY_REPORT, hour = 20, minute = 0,
                        title = "每周学习周报", body = "本周学习周报已生成，来看看孩子的学习情况吧！",
                        dayOfWeek = 7
                    )
                )
            }
        }
    }
}