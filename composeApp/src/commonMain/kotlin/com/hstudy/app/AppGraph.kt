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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
     * 语言偏好的**进程内快照**，供 App 根与设置页的 `collectAsState(initial = …)` 当首帧值。
     *
     * bootstrap() 由平台入口（Android HStudyApplication.onCreate / iOS initIosPlatform）在任何组合之前调用，
     * 这里的预读通常能在首帧组合开始前完成，于是 initial 不再是 null，
     * 避免"存储选择与设备语言相反"的设备（zh 设备存 en、en 设备存 zh）首帧渲染错语言再翻转。
     *
     * **终审 Ruling 46：这个值现在跟随存储持续刷新**，不再只在启动时写一次。
     * 理由：`App.kt:62` 与 `ParentSettingsScreen.kt:326` 都把它当 `collectAsState` 的 initial，
     * 而 initial 在**每次订阅重建**时都会重新取一次。原先只有 bootstrap() 写它，
     * 于是会话内切过一次语言之后，任何一次根组合重建（Android 配置变更最典型：旋转屏幕会重建
     * Activity，`remember { flow }` 随之作废、重新订阅）都会把界面**回锚到启动时的那个语言**，
     * 用户看到的是"我选了英文，转一下屏幕又变回中文"，要等 DataStore 首次 emission 才纠正回来。
     *
     * 写入点选择（为什么在这里而不是调用方）：语言的唯一写路径是 `UserPreferences.setAppLanguage`
     * （数据层源集，本阶段禁改），页面侧有三个出场（三枚 FilterChip），在调用方刷就是三处赋值、
     * 而且新增第四个调用点时会静默漏刷；`Flow` 本身是"语言被写入"这件事的唯一可靠通知源，
     * 所以在持有 [startupLanguage] 的这个对象里订阅一次、赋值一处，是**最少惊讶**的形状：
     * 字段和它的刷新住在同一个文件、同一个对象里，读代码的人不需要先去 UI 层找"谁记得刷它"。
     * 代价（诚实说明）：预读本身是异步的，若首帧早于首次 emission，此值仍为 null 并退化为 SYSTEM 推导；
     * 它只收窄窗口，不消除窗口，也不会插入空白帧。另一个代价是这条订阅随进程存活
     * （`appScope` 与进程同寿，bootstrap() 由 `initialized` 保证只跑一次，不存在重复订阅）。
     *
     * **为什么它是 `MutableStateFlow` 而不是一个裸 `var`（终审 Minor 3）**：写入点是 [bootstrap] 里那条
     * `appScope.launch { … collect { … } }`，跑在 `Dispatchers.Default` 的线程上；读取点是 `App.kt` 与
     * `ParentSettingsScreen.kt` 的**组合线程**。普通 `var` 在这两者之间没有 happens-before，是数据竞争：
     * JVM 上可能长期读到过期值，Kotlin/Native（iOS 是本分支的一等目标平台）对并发可变状态更是未定义。
     * commonMain 里**用不了 `@Volatile`**（`kotlin.jvm.Volatile` 是 JVM 专有注解，公共源码集不能引用），
     * 所以选 `StateFlow`：它的 `value` 自带安全发布（一次写入对所有读者可见，读到的永远是完整引用），
     * 顺带把"这个值会被别的线程改"写进了类型里——比 `var … private set` 更不容易被下一个人误用。
     * **行为一字不变**：仍然只由那一条订阅写、仍然每次语言写入都刷新、仍然在 bootstrap() 里播种。
     * 对外只给只读的 [StateFlow]（等价于原先的 `private set`），两个消费者各取一次 `.value` 当
     * `collectAsState(initial = …)` 的首帧值；**不要**在这里改用 `collectAsState()`，那会把"取一次快照"
     * 变成"再挂一条跟随存储的订阅"，与 DataStore 那条流重复。
     */
    private val _startupLanguage = MutableStateFlow<String?>(null)

    /** [_startupLanguage] 的只读视图；唯一的写入点在 [bootstrap] 的那条订阅里。 */
    val startupLanguage: StateFlow<String?> = _startupLanguage

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
        // Ruling 46：首帧语言快照的**唯一赋值点**（见上面 [startupLanguage] 那段为什么在这里）。
        // 单独一条 launch：collect 永不返回，混进下面那条就会挡住补默认数据与恢复提醒的整段逻辑。
        appScope.launch { userPreferences.appLanguage.collect { _startupLanguage.value = it } }
        appScope.launch {
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