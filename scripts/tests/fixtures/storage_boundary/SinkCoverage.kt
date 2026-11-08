// 通道 F（sink 名单）：Task 9b 缺陷 4 的正面样本。
// **每个** SINK_STEMS 词根与 SINK_EXTRA 名字在这里各有一条违规调用；自测脚本会拿
// 本文件的输出与脚本里的名单逐一对账，名单里多出没有 fixture 的条目、或 fixture 里
// 有名单没报出来的名字，自测直接失败 —— 这就是"不许凭想象往名单里塞名字"的机制，
// 也是旧表里 addRecord/save/upsert/deleteBy/submitProof/recordMistake/saveHabit/setApproval
// 这 8 个不存在条目再也不会悄悄留在表里的原因。
// 9b 审查 Important 4 把对账补成了**双向**：光"有 fixture"是自证循环（fixture 自己就能凭空造一个
// `deleteByTitle(`），所以自测现在还要求 SINK_STEMS/SINK_EXTRA 每一条都能在 commonMain 里找到
// `fun <名字>(` 的真实声明（`dao.deleteMistake(` 那一条原来是 `deleteByTitle(`，真实树 0 处，
// 已换成真实的 `deleteMistake`）。唯一留着没有真实声明的名字是 `upsertHabit`，它对应的词根在
// RESERVED_STEMS 里明写着"预留"，自测反过来还盯着它：**一旦真实树出现 `fun upsert*(`
// 就必须把它搬进 SINK_STEMS**。
// add/set 两个大写后缀家族不在本文件（它们由形状规则覆盖，见 AddSetFamily.kt）。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun SinkCoverageScreen() {
    val app = AppGraph
    val dao = app.database.focusDao()
    val strings = LocalStrings.current.childHome

    // ---- SINK_STEMS（词根前缀） ----
    dao.insertTask(TaskEntity(title = strings.defaultTaskTitle))
    dao.insertSession(FocusSessionEntity(taskTitle = strings.phaseFocus))
    dao.insertPointRecord(PointRecordEntity(note = strings.milestoneBonusNote))
    dao.insertPlan(DailyPlanEntity(taskTitle = strings.planTitleFallback))
    dao.updateTask(TaskEntity(id = 1L, title = strings.defaultTaskTitle))
    dao.updatePlan(DailyPlanEntity(taskTitle = strings.planTitleFallback))
    dao.updateStatus(3L, ApprovalStatus.APPROVED, strings.reviewDoneNote)
    dao.updateReward(RewardEntity(name = strings.rewardStoreNote))
    dao.upsertHabit(HabitEntity(name = strings.habitNameFallback))
    dao.deleteMistake(strings.defaultTaskTitle)
    dao.softDeleteReward(2L, strings.rewardStoreNote)
    dao.markCompleted(9L, strings.weeklyChallengeDescription)
    app.focusRepository.recordFocusSession(25, strings.phaseFocus, 5)
    app.pointApprovalRepository.submitApproval("TASK_COMPLETE", strings.checkInDescription, 2)
    app.pointRepository.adjustPoints(5, strings.milestoneBonusNote)
    app.pointRepository.spendPoints(5, strings.petSpendNote)
    app.rewardRepository.exchangeReward(1L, 20, strings.rewardStoreNote)
    app.assignmentRepository.assignTask(TitleAndContent(strings.defaultTaskTitle, ""))
    app.pointRepository.undoCheckIn(strings.checkInBizKey, 1L, "2026-11-07")
    app.mistakeRepository.reviewMistake(1L, 3, strings.reviewDoneNote)
    app.pointRepository.checkIn("BIZ_1", strings.checkInDescription, 2)
    app.pointRepository.checkInForTask(1L, 2, strings.pointsEarnedLabel(2))
    app.pointRepository.checkInChallenge(HabitChallengeEntity(title = strings.habitNameFallback))
    app.habitRepository.tryUnlockAchievement("STREAK", strings.achievementTitle, strings.checkInDescription, "x")

    // ---- SINK_EXTRA（词根覆盖不到的精确名：add*/set* 前缀太宽，逐字列举） ----
    app.focusRepository.addPlan(DailyPlanEntity(taskTitle = strings.planTitleFallback))
    app.mistakeRepository.addReview(1L, 3, strings.reviewDoneNote)
    app.wishGoalRepository.addSavedPoints(1L, 5, strings.goalNameFallback)
    app.userPreferences.setDeviceName(strings.deviceNameFallback)
    app.userPreferences.setPairedDevice(strings.pairedDeviceFallback)

    // ---- 以下两条由 `save` 词根覆盖（放在这里只是为了和上面的真实仓库调用点连排） ----
    app.userPreferences.saveDailyReflection("2026-11-07", 5, setOf(strings.dailyReflectionTag))
    app.mistakeRepository.saveVariation(mistake, strings.reviewDoneNote)
}
