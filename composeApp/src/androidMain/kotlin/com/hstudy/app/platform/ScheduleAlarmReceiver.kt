package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.currentDisplayLanguageSnapshot
import com.hstudy.app.i18n.stringsFor

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
 * 语言解析：`onReceive` 是同步回调，且当前实现全部在同一次回调里做完（改成 goAsync + 协程
 * 属于逻辑变更，不在本波授权内），所以用 [currentDisplayLanguageSnapshot]（进程内语言快照 +
 * 系统语言，不阻塞主线程）。它决定 ZH/EN 的标题覆盖规则，并提供兜底标题。
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
        // SCHEDULE 类每日重复：触发后重注册下一天
        AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
    }
}
