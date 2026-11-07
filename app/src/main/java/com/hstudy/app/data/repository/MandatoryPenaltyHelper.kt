package com.hstudy.app.data.repository

import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.local.entity.effectivePoints
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 每日必须任务扣分处理器：检查过去的日子里标记为"每日必须"的任务是否完成，
 * 未完成则自动扣减相应积分。每天只在孩子端首页触发一次（通过 lastMandatoryPenaltyDate 去重）。
 */
object MandatoryPenaltyHelper {

    /** 最多向前追溯处理的天数，避免长期未打开时一次性扣过多分 */
    private const val MAX_LOOKBACK_DAYS = 7

    /**
     * 处理每日必须任务的未完成扣分。
     * @return 本次扣减的总积分（0 表示无扣分）
     */
    suspend fun processPenalties(app: HStudyApp): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val lastPenalty = app.userPreferences.lastMandatoryPenaltyDate.first()

        // 结束日期 = 昨天（今天还没结束，不处理今天）
        val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        // 起始日期 = 上次处理日期的下一天；若从未处理过，则从昨天开始（不追溯历史）
        val startCal = if (lastPenalty != null) {
            try {
                Calendar.getInstance().apply {
                    time = fmt.parse(lastPenalty) ?: return@processPenalties 0
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            } catch (_: Exception) {
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            }
        } else {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        }

        // 限制追溯范围，避免一次性扣过多
        val earliest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -MAX_LOOKBACK_DAYS) }
        if (startCal.before(earliest)) startCal.time = earliest.time

        // 无需处理（起始日晚于结束日）
        if (startCal.after(endCal)) {
            return 0
        }

        // 只处理当前仍活跃的"每日必须"每日任务
        val mandatoryTasks = app.taskRepository.getAllActiveTasks().first()
            .filter { it.isMandatory && it.frequency == TaskFrequency.DAILY }
        if (mandatoryTasks.isEmpty()) {
            app.userPreferences.setLastMandatoryPenaltyDate(fmt.format(endCal.time))
            return 0
        }

        var totalPenalty = 0
        val cursor = startCal
        while (!cursor.after(endCal)) {
            val dateStr = fmt.format(cursor.time)
            val completedIds = app.pointRepository.getCompletedTaskIdsForDate(dateStr).first()
            mandatoryTasks.forEach { task ->
                if (task.id !in completedIds) {
                    totalPenalty += task.effectivePoints()
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (totalPenalty > 0) {
            // 扣分不超过当前总积分，避免负分
            val currentTotal = app.pointRepository.getTotalPoints().first()
            val deducted = totalPenalty.coerceAtMost(currentTotal.coerceAtLeast(0))
            if (deducted > 0) {
                app.pointRepository.adjustPoints(-deducted, "每日必须任务未完成自动扣分")
            }
        }

        app.userPreferences.setLastMandatoryPenaltyDate(fmt.format(endCal.time))
        return totalPenalty
    }
}
