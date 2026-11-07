package com.hstudy.app.ui.mistake

import com.hstudy.app.AppGraph
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder
import kotlinx.coroutines.flow.first

/**
 * 错题复习每日提醒（跨平台版）：
 * 原实现是 BroadcastReceiver + AlarmManager（通知 id/请求码 2001）。跨平台层把「注册/取消」
 * 交给 [Platform.notifications]，到点通知由平台侧接收器发出（androidMain 的 ReviewReminderReceiver）。
 *
 * id 约定：与 AppGraph.bootstrap()/AndroidReminderBootstrap 一致使用 9001（REVIEW 类唯一 id），
 * 保证「开关联动」与「开机重注册」不会重复挂两个闹钟。
 */
object ReviewReminderReceiver {

    /** 与平台侧渠道/通知 id 保持一致（仅作文档与取消用） */
    const val CHANNEL_ID = "hstudy_review_reminder"
    const val NOTIFICATION_ID = 2001
    /** REVIEW 类提醒的稳定 id */
    const val REMINDER_ID = 9001

    const val TITLE = "错题复习提醒"

    /** 安排每日复习提醒（每天 hour 点触发） */
    fun schedule(hour: Int) {
        Platform.notifications.schedule(
            ScheduledReminder(
                id = REMINDER_ID,
                kind = ReminderKind.REVIEW,
                hour = hour.coerceIn(0, 23),
                minute = 0,
                title = TITLE,
                body = defaultBody,
                repeatDaily = true
            )
        )
    }

    /** 取消每日复习提醒 */
    fun cancel() {
        Platform.notifications.cancel(REMINDER_ID)
    }

    /**
     * 到期错题数文案：原接收器在触发时会先查实际到期错题数，有到期才推送并带上数量。
     * 平台侧接收器（无数据库以外的智能判断时）可直接用 [defaultBody]；
     * 需要保留「智能提醒」语义时调用本方法取动态文案。
     */
    suspend fun reminderBody(): String = try {
        val mistakes = AppGraph.mistakeRepository.getAllMistakes().first()
        val dueCount = mistakes.count { SpacedRepetition.isDueForReview(it) }
        if (dueCount > 0) "📚 今日有 $dueCount 道错题待复习，趁热打铁及时复习，记得更牢～" else defaultBody
    } catch (_: Exception) {
        fallbackBody
    }

    private const val defaultBody = "今天还有错题等着复习，快去看看吧！"
    private const val fallbackBody = "📚 该复习错题啦！及时复习，记得更牢～"
}
