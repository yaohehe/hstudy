package com.hstudy.app.platform

/**
 * 平台环境入口：Android 在 Application.onCreate 初始化，iOS 在 App 启动时初始化。
 * 共享层通过它获取数据库/偏好目录与平台服务，不再依赖 android.content.Context。
 */
object Platform {
    lateinit var filesDir: String
        private set
    lateinit var cacheDir: String
        private set
    var notifications: NotificationScheduler = NoopNotificationScheduler
    var fileSharer: FileSharer = NoopFileSharer
    var camera: CameraLauncher = NoopCameraLauncher
    var deviceDiscovery: DeviceDiscovery = NoopDeviceDiscovery

    fun init(
        filesDir: String,
        cacheDir: String,
        notifications: NotificationScheduler = NoopNotificationScheduler,
        fileSharer: FileSharer = NoopFileSharer,
        camera: CameraLauncher = NoopCameraLauncher,
        deviceDiscovery: DeviceDiscovery = NoopDeviceDiscovery
    ) {
        this.filesDir = filesDir
        this.cacheDir = cacheDir
        this.notifications = notifications
        this.fileSharer = fileSharer
        this.camera = camera
        this.deviceDiscovery = deviceDiscovery
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
    val dayOfWeek: Int = -1
)

enum class ReminderKind { SCHEDULE, REVIEW, WEEKLY_REPORT }

interface NotificationScheduler {
    fun schedule(reminder: ScheduledReminder)
    fun cancel(id: Int)
    fun cancelKind(kind: ReminderKind)
    fun rescheduleAll(reminders: List<ScheduledReminder>)
}

interface FileSharer {
    /** 弹出系统分享面板分享文本文件（JSON 备份/家庭同步） */
    fun shareTextFile(fileName: String, content: String, mimeType: String = "application/json")
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
    override fun cancelKind(kind: ReminderKind) {}
    override fun rescheduleAll(reminders: List<ScheduledReminder>) {}
}

object NoopFileSharer : FileSharer {
    override fun shareTextFile(fileName: String, content: String, mimeType: String) {}
    override fun pickTextFile(onResult: (String?) -> Unit) { onResult(null) }
}

object NoopCameraLauncher : CameraLauncher {
    override fun takePhoto(onResult: (String?) -> Unit) { onResult(null) }
}

object NoopDeviceDiscovery : DeviceDiscovery {
    override fun startDiscovery(onFound: (DiscoveredDevice) -> Unit) {}
    override fun stopDiscovery() {}
}