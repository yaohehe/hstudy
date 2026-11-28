package com.hstudy.app.data.repository

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.dao.MandatoryPenaltyRunDao
import com.hstudy.app.data.local.entity.MandatoryPenaltyRunEntity
import com.hstudy.app.data.local.entity.effectivePoints
import com.hstudy.app.data.local.roomTransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 每日必须任务扣分处理器。
 *
 * 正确性由 Room `mandatory_penalty_runs.periodKey` 主键认领保证，不再由 DataStore 日期把关：
 * 每个日期在一个事务内完成「认领运行标记 → 复读任务/完成记录/余额 → 写 PENALTY 流水 → 回写实扣数」。
 * 并发竞争者拿到 -1 后零写入；进程死亡会同时回滚流水与标记。DataStore 日期只在首次升级时
 * 用于回填持久游标，提交后尽力刷新；写失败或丢失都不会影响后续持久标记判定。
 */
object MandatoryPenaltyHelper {

    /** 最多向前追溯处理的天数，避免长期未打开时一次性扣过多分。 */
    private const val MAX_LOOKBACK_DAYS = 7

    /** Actual non-negative deduction after applying the available-balance cap. */
    fun cappedPenalty(balance: Int, penalty: Int): Int =
        penalty.coerceAtLeast(0).coerceAtMost(balance.coerceAtLeast(0))

    /** 生产入口；缓存刷新失败不影响已经提交的数据库事实。 */
    suspend fun processPenalties(app: AppGraph): Int {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val legacyCursor = try {
            app.userPreferences.lastMandatoryPenaltyDate.first()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A lost cache must not prevent durable processing; first-run fallback remains yesterday.
            null
        }
        val deducted = processPenalties(
            runDao = app.database.mandatoryPenaltyRunDao(),
            transactionRunner = roomTransactionRunner(app.database),
            pointRepository = app.pointRepository,
            today = today,
            legacyCursor = legacyCursor,
        )
        try {
            app.userPreferences.setLastMandatoryPenaltyDate(today.minus(1, DateTimeUnit.DAY).toString())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best-effort cache only. The durable run markers are authoritative.
        }
        return deducted
    }

    /**
     * 可注入真实 Room 的测试/装配入口。每天独立事务，已有标记的日期直接跳过；
     * 因此重放不会重复扣分。空标记表先把合法的旧 DataStore 日期回填为持久标记，
     * 避免升级时把昨天重新扣一遍；缓存落后时按新游标追赶，超前时不会压制新周期。
     */
    internal suspend fun processPenalties(
        runDao: MandatoryPenaltyRunDao,
        transactionRunner: DbTransactionRunner,
        pointRepository: PointRepository,
        today: LocalDate,
        legacyCursor: String? = null,
    ): Int {
        val endDate = today.minus(1, DateTimeUnit.DAY)
        val earliest = today.minus(MAX_LOOKBACK_DAYS, DateTimeUnit.DAY)
        if (earliest > endDate) return 0

        val legacyDate = legacyCursor?.let(::parseDateOrNull)
        if (legacyDate != null) {
            transactionRunner.runInTransaction {
                // The count and INSERT IGNORE share one transaction. Concurrent bootstraps
                // serialize here, and the primary key remains a second line of defense.
                if (runDao.getRunCount() == 0) {
                    runDao.insertIfAbsent(
                        MandatoryPenaltyRunEntity(
                            periodKey = legacyDate.toString(),
                            processedAt = Clock.System.now().toEpochMilliseconds(),
                            deductedPoints = 0,
                        )
                    )
                }
            }
        }

        // The durable marker cursor preserves the historical first-run behavior (yesterday only)
        // while remaining correct when DataStore is absent, invalid, or lost after bootstrap.
        val latest = runDao.getLatestPeriodKey(endDate.toString())
        var cursor = latest?.let(::parseDateOrNull)?.plus(1, DateTimeUnit.DAY) ?: endDate
        if (cursor < earliest) cursor = earliest
        if (cursor > endDate) return 0

        var totalDeducted = 0
        while (cursor <= endDate) {
            val date = cursor.toString()
            totalDeducted += transactionRunner.runInTransaction {
                val claimed = runDao.insertIfAbsent(
                    MandatoryPenaltyRunEntity(
                        periodKey = date,
                        processedAt = Clock.System.now().toEpochMilliseconds(),
                        deductedPoints = 0,
                    )
                )
                if (claimed == -1L) return@runInTransaction 0

                val mandatoryTasks = runDao.getMandatoryDailyTasks()
                val completedIds = runDao.getCompletedTaskIds(date).toSet()
                val penalty = mandatoryTasks
                    .filter { it.id !in completedIds }
                    .sumOf { it.effectivePoints().coerceAtLeast(0) }
                val actual = cappedPenalty(runDao.getBalance(), penalty)
                if (actual > 0) {
                    pointRepository.applyPenalty(actual, "每日必须任务未完成自动扣分")
                }
                runDao.updateDeductedPoints(date, actual)
                actual
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return totalDeducted
    }
}
