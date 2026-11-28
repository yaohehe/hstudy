package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.currentDisplayLanguageSnapshot
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.ui.schedule.ScheduleReminderValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 日程提醒闹钟接收器：到点弹出本地通知提醒孩子，并重注册明天的闹钟。
 *
 * 标题/正文（W9 Task 2 接线；标题覆盖规则按 W9 Ruling 7 收口为 EN-only）：
 * - 标题 = `EXTRA_TITLE`（排程时按生效语言取好的日程名：预设示例译出、家长自建原样）。
 *   **只有 EN 态**把它作为 `showNotification` 的 `titleOverride`；ZH 态传 null，沿用渠道默认
 *   「HStudy 日程提醒」——与迁移前逐字节一致（W1 终审 `5deed68` 对复习通知的同一裁决）。
 *   额外值缺失或为空（历史/外部 PendingIntent）时回落到 `ScheduleAlarmStrings.alarmTitle`。
 * - 正文 = `EXTRA_BODY`（排程时冻结；W5 起含语言分隔符）。两者都缺失时的兜底正文
 *   `"$title 时间到啦！"` **保持迁移前的中文形状**：应用内排程恒带 EXTRA_BODY，此分支只服务
 *   外部/历史闹钟（与 androidMain/WeeklyReportReceiver 的紧急兜底同一披露口径）。
 *
 * 语言解析：数据库校验需要 suspend 查询，因此 onReceive 使用 goAsync + IO 协程；校验通过后
 * 用 [currentDisplayLanguageSnapshot]（进程内语言快照 + 系统语言，不阻塞线程）决定标题覆盖规则。
 * 闹钟可能比日程/关联任务活得更久：缺失、停用或关联任务已失效时静默取消孤儿闹钟且不通知。
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_ID, -1)
                if (id < 0) return@launch
                AppGraph.init()
                val schedule = AppGraph.scheduleRepository.getScheduleById(id.toLong())
                val linkedTask = schedule?.linkedTaskId?.let { AppGraph.taskRepository.getTaskById(it) }
                if (!ScheduleReminderValidator.isActive(schedule, linkedTask)) {
                    runCatching {
                        AndroidNotificationScheduler(context).cancel(id, ReminderKind.SCHEDULE)
                    }
                    return@launch
                }

                val lang = currentDisplayLanguageSnapshot()
                val strings = stringsFor(lang).scheduleAlarm
                val title = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: strings.alarmTitle
                val message = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_BODY)
                    ?.takeIf { it.isNotBlank() } ?: "$title 时间到啦！"

                AndroidNotificationScheduler.showNotification(
                    context, ReminderKind.SCHEDULE, message,
                    System.currentTimeMillis().toInt(),
                    // ZH 态不覆盖标题：沿用渠道默认「HStudy 日程提醒」（与迁移前逐字节一致）；仅 EN 态覆盖
                    titleOverride = if (lang == DisplayLanguage.EN) title else null
                )
                // SCHEDULE 类每日重复：触发后重注册下一天。
                AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
            } catch (_: Exception) {
                // Validation is fail-closed: a database/platform failure must not emit a stale reminder.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
