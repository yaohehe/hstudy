package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.PointRecordType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 家长每周学习周报接收器：每周定时汇总孩子本周学习数据并推送通知。
 * 统计口径/文案/通知 id(2002) 与原 ui.parent.WeeklyReportReceiver 一致。
 */
class WeeklyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init()
                // 本周一 0 点作为统计起点
                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val focusMin = AppGraph.focusRepository.getDailyFocusMinutesSince(weekStart).first()
                    .sumOf { it.totalMinutes }
                val reviewCount = AppGraph.mistakeRepository.getReviewsSince(weekStart).first().size
                val records = AppGraph.pointRepository.getAllRecords().first()
                val weekRecords = records.filter { it.date >= weekStart }
                val checkins = weekRecords.count { it.type == PointRecordType.TASK_COMPLETE }
                val earned = weekRecords.filter { it.points > 0 }.sumOf { it.points }

                val message = buildString {
                    append("📊 孩子本周学习周报：")
                    append("打卡${checkins}次 · 专注${focusMin}分钟 · 复习${reviewCount}道错题 · 获得${earned}积分")
                    if (focusMin == 0 && reviewCount == 0 && checkins == 0) append("。本周学习较少，多鼓励孩子加油！")
                    else append("。继续保持，稳步进步！")
                }
                AndroidNotificationScheduler.showNotification(
                    context, ReminderKind.WEEKLY_REPORT, message,
                    AndroidNotificationScheduler.WEEKLY_NOTIFICATION_ID
                )
            } catch (e: Exception) {
                AndroidNotificationScheduler.showNotification(
                    context, ReminderKind.WEEKLY_REPORT,
                    "📊 孩子本周学习周报已生成，打开 App 查看详细报告。",
                    AndroidNotificationScheduler.WEEKLY_NOTIFICATION_ID
                )
            } finally {
                AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
                pendingResult.finish()
            }
        }
    }
}