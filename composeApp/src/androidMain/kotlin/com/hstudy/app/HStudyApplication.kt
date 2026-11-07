package com.hstudy.app

import android.app.Application
import com.hstudy.app.platform.ActivityTracker
import com.hstudy.app.platform.AndroidCameraLauncher
import com.hstudy.app.platform.AndroidDeviceDiscovery
import com.hstudy.app.platform.AndroidDeviceFeedback
import com.hstudy.app.platform.AndroidFileSharer
import com.hstudy.app.platform.AndroidNotificationScheduler
import com.hstudy.app.platform.AndroidReminderBootstrap
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.installDeviceFeedback
import kotlinx.coroutines.launch

/**
 * Android 平台入口：初始化 Platform 服务 + AppGraph 引导。
 * 对应原 HStudyApp.onCreate 的启动逻辑（默认数据补全/提醒恢复由 AppGraph.bootstrap 承担，
 * 日程闹钟重注册在 AndroidReminderBootstrap 中补上）。
 */
class HStudyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(ActivityTracker)

        Platform.init(
            filesDir = filesDir.absolutePath,
            cacheDir = cacheDir.absolutePath,
            dataDir = dataDir.absolutePath,
            notifications = AndroidNotificationScheduler(this),
            fileSharer = AndroidFileSharer(this),
            camera = AndroidCameraLauncher(this),
            deviceDiscovery = AndroidDeviceDiscovery(this)
        )
        // 番茄钟震动/提示音/进行中通知（原 commonMain 直接调 android.* 的实现搬到这里）
        installDeviceFeedback(AndroidDeviceFeedback(this))
        AppGraph.bootstrap()

        // 原 HStudyApp 启动时会重新注册所有日程闹钟（重启后 AlarmManager 会清空）；
        // AppGraph.bootstrap 只恢复复习/周报提醒，这里补上日程闹钟。
        AppGraph.appScope.launch {
            AndroidReminderBootstrap.rescheduleScheduleAlarms()
        }
    }

    companion object {
        lateinit var instance: HStudyApplication
            private set
    }
}