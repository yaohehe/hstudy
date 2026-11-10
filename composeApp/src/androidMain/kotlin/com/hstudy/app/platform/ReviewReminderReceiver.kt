package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 错题复习每日提醒接收器：智能查询实际到期错题数，有到期才推送并显示数量。
 * 行为与原 ui.mistake.ReviewReminderReceiver 一致（通知 id=2001）。
 * 标题：ZH 态沿用渠道既有中文标题「HStudy 错题复习提醒」（与 v3.1.0 逐字节一致，不覆盖）；
 * 只有 EN 态才用本地化标题覆盖（正文两种语言都按当前语言现取）。
 */
class ReviewReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val lang = runCatching { currentDisplayLanguage() }.getOrDefault(DisplayLanguage.ZH)
            val copy = stringsFor(lang).reviewReminder
            // ZH 态不覆盖标题：沿用渠道既有中文标题（与 v3.1.0 中文态逐字节一致）；EN 态用本地化标题。
            val titleOverride = if (lang == DisplayLanguage.EN) copy.title else null
            try {
                AppGraph.init()
                val mistakes = AppGraph.mistakeRepository.getAllMistakes().first()
                val dueCount = mistakes.count { isDueForReview(it) }
                if (dueCount > 0) {
                    AndroidNotificationScheduler.showNotification(
                        context, ReminderKind.REVIEW,
                        copy.dueBody(dueCount),
                        AndroidNotificationScheduler.REVIEW_NOTIFICATION_ID,
                        titleOverride = titleOverride
                    )
                }
            } catch (e: Exception) {
                runCatching {
                    AndroidNotificationScheduler.showNotification(
                        context, ReminderKind.REVIEW,
                        copy.fallbackBody,
                        AndroidNotificationScheduler.REVIEW_NOTIFICATION_ID,
                        titleOverride = titleOverride
                    )
                }
            } finally {
                AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
                pendingResult.finish()
            }
        }
    }

    /**
     * 艾宾浩斯间隔复习到期判断（与 SpacedRepetition.isDueForReview 同算法：
     * 间隔 1/2/4/7/15/30 天，未掌握且已到复习时间点）。
     * 此处内联以避免接收器依赖 UI 层工具类。
     */
    private fun isDueForReview(mistake: MistakeEntity, now: Long = System.currentTimeMillis()): Boolean {
        if (mistake.isMastered) return false
        val intervals = listOf(1, 2, 4, 7, 15, 30)
        val days = if (mistake.reviewCount < intervals.size) intervals[mistake.reviewCount] else intervals.last()
        val base = if (mistake.lastReviewAt > 0) mistake.lastReviewAt else mistake.createdAt
        return now >= base + days * 24L * 60 * 60 * 1000
    }
}