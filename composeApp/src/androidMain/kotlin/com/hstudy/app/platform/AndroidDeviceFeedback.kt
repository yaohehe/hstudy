package com.hstudy.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Android 设备反馈：逻辑从 commonMain 的 PomodoroTimer 原样搬来（渠道 id=pomodoro_timer、
 * 通知 id=2001、IMPORTANCE_LOW、setShowBadge(false)、静音常驻通知、系统默认通知音 + 300ms 震动），
 * 行为与迁移前完全一致。
 */
class AndroidDeviceFeedback(private val context: Context) : DeviceFeedback {

    private var ringtone: Ringtone? = null

    override fun vibrate(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* 忽略震动失败 */ }
    }

    override fun playAlarm() {
        try {
            // 系统通知音（与原 playCompletionAlert 一致）
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(context, notification)?.also { it.play() }
        } catch (_: Exception) { /* 忽略提示音失败 */ }
    }

    /** 更新专注通知栏（显示剩余时间与进度，息屏后也能看进度） */
    override fun showRunningNotification(title: String, text: String, progressPercent: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 创建通知渠道（Android 8+）
            if (nm.getNotificationChannel(FOCUS_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(FOCUS_CHANNEL_ID, "番茄钟计时", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "专注/休息计时进度显示"; setShowBadge(false) }
                )
            }
            val notif = NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, progressPercent.coerceIn(0, 100), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
            nm.notify(FOCUS_NOTIF_ID, notif)
        } catch (_: Exception) { /* 忽略通知失败 */ }
    }

    /** 取消专注通知 */
    override fun cancelRunningNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(FOCUS_NOTIF_ID)
            ringtone?.stop()
            ringtone = null
        } catch (_: Exception) { /* 忽略 */ }
    }

    companion object {
        private const val FOCUS_CHANNEL_ID = "pomodoro_timer"
        private const val FOCUS_NOTIF_ID = 2001
    }
}
