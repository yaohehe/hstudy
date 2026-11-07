package com.hstudy.app.platform

import platform.AudioToolbox.AudioServicesPlayAlertSound
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.random.Random

/**
 * iOS 设备反馈：震动 / 提示音 / 计时进行中的可见提醒。
 *
 * 与 Android 的差异（平台客观限制，已做「可见降级」而非静默丢弃）：
 * - 震动：iOS 无「按毫秒震动」的公开 API。短震动用 UIFeedbackGenerator（中等强度触觉），
 *   长震动（>=400ms）用系统震动 AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)。
 * - 提示音：AudioServicesPlayAlertSound(1005)（系统默认通知音 Tri-tone）。
 * - 计时中的常驻通知：iOS 没有可持续刷新的常驻通知（Live Activity 需额外 entitlement），
 *   这里做两件事保证「看得见」：
 *   ① 应用图标角标显示进度百分比（息屏可见，无需额外权限）；
 *   ② 以固定 identifier 追加即时本地通知，App 在后台时可见（前台由系统折叠，此时 App 内圆环即进度）。
 * - 结束提醒：声音+震动之外再补一条带提示音的本地通知，确保用户不在前台时也不漏提醒。
 */
class IosDeviceFeedback : DeviceFeedback {

    private val center: UNUserNotificationCenter? = UNUserNotificationCenter.currentNotificationCenter()

    init {
        center?.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { _, _ -> }
    }

    override fun vibrate(durationMs: Long) {
        runCatching {
            if (durationMs >= VIBRATE_LONG_MS) {
                AudioServicesPlaySystemSound(SYSTEM_VIBRATE_SOUND_ID)
            } else {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                generator.prepare()
                generator.impactOccurred()
            }
        }
    }

    override fun playAlarm() {
        runCatching { AudioServicesPlayAlertSound(DEFAULT_ALERT_SOUND_ID) }
        // 补一条即时本地通知：用户不在前台时同样收到提醒（不静默丢弃）
        postNotification(
            identifier = "$ALERT_ID_PREFIX${Random.nextInt(100000)}",
            title = "⏰ 时间到啦",
            body = "番茄钟告一段落，看看屏幕上的进度，休息一下再继续吧～",
            withSound = true
        )
    }

    override fun showRunningNotification(title: String, text: String, progressPercent: Int) {
        val percent = progressPercent.coerceIn(0, 100)
        // ① 角标显示进度（息屏可见，等价于 Android 的通知栏进度条）
        dispatch_async(dispatch_get_main_queue()) {
            runCatching {
                @Suppress("DEPRECATION")
                UIApplication.sharedApplication.applicationIconBadgeNumber = percent.toLong()
            }
        }
        // ② 同 id 覆盖式本地通知：后台时能看到最新进度文案
        postNotification(RUNNING_ID, title, text, withSound = false)
    }

    override fun cancelRunningNotification() {
        dispatch_async(dispatch_get_main_queue()) {
            runCatching {
                @Suppress("DEPRECATION")
                UIApplication.sharedApplication.applicationIconBadgeNumber = 0L
            }
        }
        center?.removeDeliveredNotificationsWithIdentifiers(listOf(RUNNING_ID))
        center?.removePendingNotificationRequestsWithIdentifiers(listOf(RUNNING_ID))
    }

    /** trigger 为 null 的本地通知会立即投递；同 identifier 覆盖上一条，用于刷新进度 */
    private fun postNotification(identifier: String, title: String, body: String, withSound: Boolean) {
        val notificationCenter = center ?: return
        runCatching {
            val content = UNMutableNotificationContent().apply {
                this.title = title
                this.body = body
                sound = if (withSound) UNNotificationSound.defaultSound else null
            }
            val request = UNNotificationRequest.requestWithIdentifier(identifier, content, null)
            notificationCenter.addNotificationRequest(request) { _ -> }
        }
    }

    private companion object {
        const val RUNNING_ID = "hstudy.pomodoro.running"
        const val ALERT_ID_PREFIX = "hstudy.pomodoro.alert."

        /** AudioToolbox: kSystemSoundID_Vibrate */
        const val SYSTEM_VIBRATE_SOUND_ID: UInt = 4095u

        /** iOS 系统提示音 ID 1005（Tri-tone，等同默认通知音） */
        const val DEFAULT_ALERT_SOUND_ID: UInt = 1005u
        const val VIBRATE_LONG_MS = 400L
    }
}
