package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.ui.mistake.ReviewReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 错题复习每日提醒接收器：智能查询实际到期错题数，有到期才推送并显示数量。
 * 行为与原 ui.mistake.ReviewReminderReceiver 一致（通知 id=2001）。
 */
class ReviewReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init()
                val copy = ReviewReminderReceiver.localizedCopy()
                val mistakes = AppGraph.mistakeRepository.getAllMistakes().first()
                val dueCount = mistakes.count { isDueForReview(it) }
                if (dueCount > 0) {
                    AndroidNotificationScheduler.showNotification(
                        context, ReminderKind.REVIEW,
                        copy.dueBody(dueCount),
                        AndroidNotificationScheduler.REVIEW_NOTIFICATION_ID,
                        titleOverride = copy.title
                    )
                }
            } catch (e: Exception) {
                // 兜底：查询失败时仍推送通用提醒（文案同样按当前语言取；取词再失败则静默跳过）
                runCatching {
                    val copy = ReviewReminderReceiver.localizedCopy()
                    AndroidNotificationScheduler.showNotification(
                        context, ReminderKind.REVIEW,
                        copy.fallbackBody,
                        AndroidNotificationScheduler.REVIEW_NOTIFICATION_ID,
                        titleOverride = copy.title
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