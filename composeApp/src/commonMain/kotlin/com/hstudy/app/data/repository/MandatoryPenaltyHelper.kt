package com.hstudy.app.data.repository

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.local.entity.effectivePoints
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 每日必须任务扣分处理器：检查过去的日子里标记为"每日必须"的任务是否完成，
 * 未完成则自动扣减相应积分。每天只在孩子端首页触发一次（通过 lastMandatoryPenaltyDate 去重）。
 *
 * KMP 说明：原实现接收 HStudyApp(Application)，现改为跨平台 DI 容器 [AppGraph]；
 * Calendar 日期运算改为 kotlinx-datetime 的 LocalDate 运算，业务语义保持不变。
 */
object MandatoryPenaltyHelper {

    /** 最多向前追溯处理的天数，避免长期未打开时一次性扣过多分 */
    private const val MAX_LOOKBACK_DAYS = 7

    /**
     * 处理每日必须任务的未完成扣分。
     * @return 本次扣减的总积分（0 表示无扣分）
     */
    suspend fun processPenalties(app: AppGraph): Int {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val lastPenalty = app.userPreferences.lastMandatoryPenaltyDate.first()

        // 结束日期 = 昨天（今天还没结束，不处理今天）
        val endDate = today.minus(1, DateTimeUnit.DAY)

        // 起始日期 = 上次处理日期的下一天；若从未处理过（或日期非法），则从昨天开始（不追溯历史）
        var startDate = if (lastPenalty != null) {
            parseDateOrNull(lastPenalty)?.plus(1, DateTimeUnit.DAY) ?: endDate
        } else {
            endDate
        }

        // 限制追溯范围，避免一次性扣过多
        val earliest = today.minus(MAX_LOOKBACK_DAYS, DateTimeUnit.DAY)
        if (startDate < earliest) startDate = earliest

        // 无需处理（起始日晚于结束日）
        if (startDate > endDate) {
            return 0
        }

        // 只处理当前仍活跃的"每日必须"每日任务
        val mandatoryTasks = app.taskRepository.getAllActiveTasks().first()
            .filter { it.isMandatory && it.frequency == TaskFrequency.DAILY }
        if (mandatoryTasks.isEmpty()) {
            app.userPreferences.setLastMandatoryPenaltyDate(endDate.toString())
            return 0
        }

        var totalPenalty = 0
        var cursor: LocalDate = startDate
        while (cursor <= endDate) {
            val dateStr = cursor.toString()
            val completedIds = app.pointRepository.getCompletedTaskIdsForDate(dateStr).first()
            mandatoryTasks.forEach { task ->
                if (task.id !in completedIds) {
                    totalPenalty += task.effectivePoints()
                }
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }

        if (totalPenalty > 0) {
            // 扣分不超过当前总积分，避免负分
            val currentTotal = app.pointRepository.getTotalPoints().first()
            val deducted = totalPenalty.coerceAtMost(currentTotal.coerceAtLeast(0))
            if (deducted > 0) {
                app.pointRepository.adjustPoints(-deducted, "每日必须任务未完成自动扣分")
            }
        }

        app.userPreferences.setLastMandatoryPenaltyDate(endDate.toString())
        return totalPenalty
    }
}