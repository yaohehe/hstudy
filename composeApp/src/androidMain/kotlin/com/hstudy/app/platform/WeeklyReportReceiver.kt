package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 家长每周学习周报接收器：每周定时汇总孩子本周学习数据并推送通知。
 * 统计口径/通知 id(2002) 与原 ui.parent.WeeklyReportReceiver 一致；正文按生效语言在触发时取词（文案表 WeeklyReportStrings）。
 *
 * 标题（W9 Task 2）：EN 态用 `WeeklyReportStrings.title` 覆盖；**ZH 态不覆盖**，沿用渠道既有
 * 中文标题「HStudy 每周学习周报」——与 v3.1.0 逐字节一致（W1 终审 Important 对复习通知的同一裁决，
 * 见 androidMain/ReviewReminderReceiver 的 KDoc）。两条路径（正常/兜底）都按同一规则传。
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

                val lang = currentDisplayLanguage()
                val strings = stringsFor(lang).weeklyReport
                val message = strings.summary(checkins, focusMin, reviewCount, earned) +
                    if (focusMin == 0 && reviewCount == 0 && checkins == 0) strings.encourageLow
                    else strings.encourageGood
                AndroidNotificationScheduler.showNotification(
                    context, ReminderKind.WEEKLY_REPORT, message,
                    AndroidNotificationScheduler.WEEKLY_NOTIFICATION_ID,
                    titleOverride = if (lang == DisplayLanguage.EN) strings.title else null
                )
            } catch (e: Exception) {
                // 紧急兜底：init/取词失败时也要能发通知（旧实现的中文兜底文案，不回退为崩溃）
                val lang = runCatching { currentDisplayLanguage() }.getOrDefault(DisplayLanguage.ZH)
                val weekly = stringsFor(lang).weeklyReport
                val fallback = runCatching { weekly.fallback }
                    .getOrDefault("📊 孩子本周学习周报已生成，打开 App 查看详细报告。")
                AndroidNotificationScheduler.showNotification(
                    context, ReminderKind.WEEKLY_REPORT, fallback,
                    AndroidNotificationScheduler.WEEKLY_NOTIFICATION_ID,
                    titleOverride = if (lang == DisplayLanguage.EN) weekly.title else null
                )
            } finally {
                AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
                pendingResult.finish()
            }
        }
    }
}