package com.hstudy.app.platform

/**
 * 设备即时反馈契约（震动 / 提示音 / 计时进行中的常驻通知）。
 *
 * 为什么单独建文件：Platform.kt 由构建侧持有（只读），本契约按同样的「接口 + Noop 默认实现 +
 * Platform 属性」形态提供，平台实现仍在各自 init 里安装：
 * - Android：HStudyApplication.onCreate → installDeviceFeedback(AndroidDeviceFeedback(this))
 * - iOS：IosPlatform.initIosPlatform → installDeviceFeedback(IosDeviceFeedback())
 *
 * 典型用法（番茄钟）：
 * ```
 * Platform.feedback.playAlarm()
 * Platform.feedback.vibrate(300)
 * Platform.feedback.showRunningNotification(title, text, percent)
 * ```
 */
interface DeviceFeedback {
    /** 震动反馈；iOS 无公开时长 API，按系统默认强度触发一次 */
    fun vibrate(durationMs: Long = 300)

    /** 播放系统通知提示音（番茄钟专注/休息结束） */
    fun playAlarm()

    /**
     * 计时进行中的常驻通知（息屏也能看到进度）。
     * @param progressPercent 0..100 的完成进度
     */
    fun showRunningNotification(title: String, text: String, progressPercent: Int)

    /** 取消常驻通知 */
    fun cancelRunningNotification()
}

/** 未安装平台实现时的兜底：全部空操作，保证共享层调用不崩 */
object NoopDeviceFeedback : DeviceFeedback {
    override fun vibrate(durationMs: Long) {}
    override fun playAlarm() {}
    override fun showRunningNotification(title: String, text: String, progressPercent: Int) {}
    override fun cancelRunningNotification() {}
}

/** 平台实现注册表（由 androidMain/iosMain 的启动入口写入） */
object DeviceFeedbackHolder {
    var impl: DeviceFeedback = NoopDeviceFeedback
}

/** 与 [Platform.notifications] 一致的取用写法 */
val Platform.feedback: DeviceFeedback get() = DeviceFeedbackHolder.impl

/** 平台启动入口调用：安装本平台的设备反馈实现 */
fun installDeviceFeedback(impl: DeviceFeedback) {
    DeviceFeedbackHolder.impl = impl
}
