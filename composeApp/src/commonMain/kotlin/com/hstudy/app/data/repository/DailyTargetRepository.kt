package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DailyTargetResetBlocker {
    PENDING_TARGET_APPROVALS,
    CLAIMED_ALL_COMPLETE_BONUS,
}

/** Actionable reset failure; the UI resolves these records before allowing a new target set. */
class DailyTargetResetBlockedException(
    val blockers: Set<DailyTargetResetBlocker>
) : IllegalStateException(
    "Resolve daily-target approvals/bonus before reset: ${blockers.joinToString { it.name }}"
)

/**
 * Serializes daily-target mutations across DataStore state and approval creation.
 *
 * All-complete eligibility is derived from the selected target set and the post-update done
 * set while holding [mutationMutex]; a composable's stale rendered count is never authoritative.
 *
 * Room and DataStore cannot share a transaction. Every call therefore replays the ordered,
 * idempotent Room check-in/undo before the corresponding preference write. Repeating the same
 * [setDone] repairs either store after a partial failure instead of treating an already-matching
 * checkbox value as proof that both stores are consistent.
 */
class DailyTargetRepository(
    private val preferences: UserPreferences,
    private val pointRepository: PointRepository,
    private val clock: AppClock
) {
    private val mutationMutex = Mutex()

    suspend fun reset(date: String, targetIds: Set<String>): Result<Unit> = mutationMutex.withLock {
        runMutation {
            require(date == clock.today()) { "Daily targets can only be reset for today" }
            require(targetIds.isNotEmpty()) { "At least one daily target is required" }

            val approvals = pointRepository.getDailyTargetApprovals(date)
            val blockers = buildSet {
                if (approvals.any {
                        it.status == ApprovalStatus.PENDING &&
                            it.bizKey.startsWith("TARGET:") &&
                            it.bizKey != allCompleteKey(date)
                    }
                ) add(DailyTargetResetBlocker.PENDING_TARGET_APPROVALS)
                if (
                    preferences.dailyTargetBonusDate.first() == date ||
                    approvals.any {
                        it.bizKey == allCompleteKey(date) &&
                            it.status in setOf(ApprovalStatus.PENDING, ApprovalStatus.APPROVED)
                    }
                ) add(DailyTargetResetBlocker.CLAIMED_ALL_COMPLETE_BONUS)
            }
            if (blockers.isNotEmpty()) throw DailyTargetResetBlockedException(blockers)

            preferences.setDailyTargets(date, targetIds)
        }
    }

    suspend fun setDone(date: String, targetId: String, done: Boolean): Result<Unit> = mutationMutex.withLock {
        runMutation {
            require(date == clock.today()) { "Daily targets can only be changed for today" }
            require(preferences.dailyTargetDate.first() == date) { "The displayed target date is stale" }
            val selectedTargets = preferences.dailyTargets.first()
            require(targetId in selectedTargets) { "The target is not in today's selected set" }

            val doneTargets = preferences.dailyTargetsDone.first()
            val bonusDate = preferences.dailyTargetBonusDate.first()
            val targetKey = targetKey(targetId, date)

            if (done) {
                pointRepository.checkIn(
                    bizKey = targetKey,
                    actionType = "DAILY_TARGET",
                    description = "完成每日目标：${targetTitle(targetId)}",
                    points = 2,
                    date = date
                )
                preferences.setDailyTargetDone(targetId, true)
            } else {
                pointRepository.undoCheckIn(targetKey, date = date)
                preferences.setDailyTargetDone(targetId, false)
            }

            val postUpdateDone = if (done) doneTargets + targetId else doneTargets - targetId
            val allComplete = selectedTargets.isNotEmpty() && selectedTargets.all { it in postUpdateDone }
            if (done && allComplete) {
                pointRepository.checkIn(
                    bizKey = allCompleteKey(date),
                    actionType = "DAILY_TARGET",
                    description = "每日学习目标全部达成奖励",
                    points = 10,
                    date = date
                )
                if (bonusDate != date) preferences.setDailyTargetBonusDate(date)
            } else if (!done && !allComplete) {
                pointRepository.undoCheckIn(allCompleteKey(date), date = date)
                preferences.setDailyTargetBonusDate("")
            }
        }
    }

    private suspend fun runMutation(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    private fun targetKey(targetId: String, date: String): String = "TARGET:$targetId:$date"

    private fun allCompleteKey(date: String): String = "TARGET_ALL:$date"

    private fun targetTitle(targetId: String): String = when (targetId) {
        "review_mistakes" -> "复习错题"
        "focus_pomodoro" -> "专注2个番茄钟"
        "homework" -> "完成作业"
        "preview" -> "预习新课"
        "reading" -> "阅读30分钟"
        "english" -> "英语听说训练"
        "organize" -> "整理笔记"
        "exercise" -> "体育锻炼"
        else -> targetId
    }
}
