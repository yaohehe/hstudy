package com.hstudy.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 平台环境入口：Android 在 Application.onCreate 初始化，iOS 在 App 启动时初始化。
 * 共享层通过它获取数据库/偏好目录与平台服务，不再依赖 android.content.Context。
 */
object Platform {
    lateinit var filesDir: String
        private set
    lateinit var cacheDir: String
        private set
    /** Android 为应用 dataDir（数据库/偏好历史位置所在），iOS 与 filesDir 一致 */
    lateinit var dataDir: String
        private set
    var notifications: NotificationScheduler = NoopNotificationScheduler
    var fileSharer: FileSharer = NoopFileSharer
    var camera: CameraLauncher = NoopCameraLauncher
    var deviceDiscovery: DeviceDiscovery = NoopDeviceDiscovery
    var analytics: Analytics = NoopAnalytics
    var appChannel: String = "release"

    private val _pendingRoute = MutableStateFlow<String?>(null)

    /** Route offered by a notification click; consumed only after role/password authorization. */
    var pendingRoute: String?
        internal set(value) { _pendingRoute.value = value }
        get() = _pendingRoute.value

    /** Observable view used by App so warm-start clicks are not missed between recompositions. */
    val pendingRouteFlow: StateFlow<String?> = _pendingRoute.asStateFlow()

    fun consumePendingRoute(): String? {
        while (true) {
            val route = _pendingRoute.value ?: return null
            if (_pendingRoute.compareAndSet(route, null)) return route
        }
    }

    /** Cancels only schedule reminders, never same-number review/weekly requests. */
    fun cancelScheduleReminders(scheduleIds: List<Long>) {
        scheduleIds.forEach { id ->
            require(id in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "Schedule reminder id cannot be represented by NotificationScheduler: $id"
            }
            notifications.cancel(id.toInt(), ReminderKind.SCHEDULE)
        }
    }

    fun init(
        filesDir: String,
        cacheDir: String,
        dataDir: String = filesDir,
        notifications: NotificationScheduler = NoopNotificationScheduler,
        fileSharer: FileSharer = NoopFileSharer,
        camera: CameraLauncher = NoopCameraLauncher,
        deviceDiscovery: DeviceDiscovery = NoopDeviceDiscovery,
        analytics: Analytics = NoopAnalytics
    ) {
        this.filesDir = filesDir
        this.cacheDir = cacheDir
        this.dataDir = dataDir
        this.notifications = notifications
        this.fileSharer = fileSharer
        this.camera = camera
        this.deviceDiscovery = deviceDiscovery
        this.analytics = analytics
    }
}

data class ScheduledReminder(
    val id: Int,
    val kind: ReminderKind,
    val hour: Int,
    val minute: Int,
    val title: String,
    val body: String,
    val repeatDaily: Boolean = true,
    val dayOfWeek: Int = -1,
    val repeatDays: Set<Int> = emptySet(),
)

enum class ReminderKind { SCHEDULE, REVIEW, WEEKLY_REPORT }

/** Generation-aware request identifiers used by iOS; legacy IDs remain in every cleanup set. */
internal object ReminderNotificationIds {
    const val PREFIX = "hstudy.reminder."

    fun base(kind: ReminderKind, id: Long): String = "$PREFIX${kind.name}.$id"

    fun identifiers(kind: ReminderKind, id: Long, generation: Long): List<String> {
        require(generation > 0L)
        return (listOf<Int?>(null) + (1..7)).map { weekday ->
            identifier(kind, id, generation, weekday)
        }
    }

    fun identifier(kind: ReminderKind, id: Long, generation: Long, isoWeekday: Int? = null): String {
        require(generation > 0L)
        require(isoWeekday == null || isoWeekday in 1..7)
        return "${base(kind, id)}.g$generation" + (isoWeekday?.let { ".$it" } ?: "")
    }

    fun legacyIdentifiers(kind: ReminderKind, id: Long): List<String> =
        (listOf<Int?>(null) + (1..7)).map { weekday ->
            base(kind, id) + (weekday?.let { ".$it" } ?: "")
        }
}

/** Stable notification destinations shared by Android and iOS. */
object NotificationRoutes {
    const val EXTRA_ROUTE = "extra_route"
    const val SCHEDULE = "child_schedule_timeline"
    const val REVIEW = "child_mistake_list"
    const val WEEKLY_REPORT = "parent_weekly_report"

    fun forKind(kind: ReminderKind): String = when (kind) {
        ReminderKind.SCHEDULE -> SCHEDULE
        ReminderKind.REVIEW -> REVIEW
        ReminderKind.WEEKLY_REPORT -> WEEKLY_REPORT
    }

    fun isAllowed(route: String): Boolean =
        route == SCHEDULE || route == REVIEW || route == WEEKLY_REPORT

    fun roleFor(route: String): String? = when (route) {
        SCHEDULE, REVIEW -> "child"
        WEEKLY_REPORT -> "parent"
        else -> null
    }
}

interface NotificationScheduler {
    fun schedule(reminder: ScheduledReminder)
    fun cancel(id: Int)
    fun cancel(id: Int, kind: ReminderKind)
    fun cancelKind(kind: ReminderKind)
    fun rescheduleAll(reminders: List<ScheduledReminder>)
}

/** Observable outcome of handing a file to the platform share UI. */
sealed interface ShareResult {
    /** The chooser/share sheet was launched successfully; delivery is not yet known. */
    data object Presented : ShareResult
    /** The platform reported a completed share action. */
    data object Completed : ShareResult
    /** The user dismissed the share UI without completing an action. */
    data object Cancelled : ShareResult
    data class Failed(val message: String) : ShareResult
}

interface FileSharer {
    /**
     * 弹出系统分享面板分享文本文件（JSON 备份/家庭同步）。
     * Android can only report [ShareResult.Presented]. iOS reports later completion/cancel/failure
     * through [onResult] when UIActivityViewController invokes its completion handler.
     */
    fun shareTextFile(
        fileName: String,
        content: String,
        mimeType: String = "application/json",
        onResult: (ShareResult) -> Unit = {},
    ): ShareResult

    /** 打开系统文件选择器导入文本，回调返回内容；取消时回调 null */
    fun pickTextFile(onResult: (String?) -> Unit)
}

interface CameraLauncher {
    /** 拍照并保存到应用私有目录，回调返回文件绝对路径；取消/失败回调 null */
    fun takePhoto(onResult: (String?) -> Unit)
}

data class DiscoveredDevice(val name: String, val address: String, val port: Int)

interface DeviceDiscovery {
    fun startDiscovery(onFound: (DiscoveredDevice) -> Unit)
    fun stopDiscovery()
}

object NoopNotificationScheduler : NotificationScheduler {
    override fun schedule(reminder: ScheduledReminder) {}
    override fun cancel(id: Int) {}
    override fun cancel(id: Int, kind: ReminderKind) {}
    override fun cancelKind(kind: ReminderKind) {}
    override fun rescheduleAll(reminders: List<ScheduledReminder>) {}
}

object NoopFileSharer : FileSharer {
    override fun shareTextFile(
        fileName: String,
        content: String,
        mimeType: String,
        onResult: (ShareResult) -> Unit,
    ): ShareResult = ShareResult.Failed("File sharing is unavailable")

    override fun pickTextFile(onResult: (String?) -> Unit) { onResult(null) }
}

object NoopCameraLauncher : CameraLauncher {
    override fun takePhoto(onResult: (String?) -> Unit) { onResult(null) }
}

object NoopDeviceDiscovery : DeviceDiscovery {
    override fun startDiscovery(onFound: (DiscoveredDevice) -> Unit) {}
    override fun stopDiscovery() {}
}