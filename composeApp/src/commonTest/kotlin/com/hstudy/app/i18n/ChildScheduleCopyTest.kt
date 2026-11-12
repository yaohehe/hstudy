package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W5 文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentPointsCopyTest/ChildRewardStoreCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b），英文 one/other 用 pointsWord 钉死；
 * 跨表同值钉死。覆盖六张表：childDailyTarget / parentSchedule / scheduleAlarm /
 * childHabitChallenge / childGoalTracker / childScheduleTimeline。
 */
class ChildScheduleCopyTest {

    @Test
    fun childDailyTargetCopyDiffers() {
        val zh = AppStringsZh.childDailyTarget
        val en = AppStringsEn.childDailyTarget
        assertDiffers(
            zh.title to en.title,
            zh.setGoalsHint to en.setGoalsHint,
            zh.recommendTitle to en.recommendTitle,
            zh.recommendSubtitle to en.recommendSubtitle,
            zh.adoptAllButton to en.adoptAllButton,
            zh.pickGoalsTitle to en.pickGoalsTitle,
            zh.needOneGoal to en.needOneGoal,
            zh.goalsSet to en.goalsSet,
            zh.todayProgressTitle to en.todayProgressTitle,
            zh.allDonePraise to en.allDonePraise,
            zh.allDoneBonus to en.allDoneBonus,
            zh.resetGoalsButton to en.resetGoalsButton,
            zh.reflectionTitle to en.reflectionTitle,
            zh.reflectionDone to en.reflectionDone,
            zh.reflectionPrompt to en.reflectionPrompt,
            zh.starLabel1 to en.starLabel1,
            zh.starLabel2 to en.starLabel2,
            zh.starLabel3 to en.starLabel3,
            zh.starLabel4 to en.starLabel4,
            zh.starLabel5 to en.starLabel5,
            zh.rateFirst to en.rateFirst,
            zh.reflectionSaved to en.reflectionSaved,
            zh.saveReflectionButton to en.saveReflectionButton,
        )
    }

    @Test
    fun childDailyTargetInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childDailyTarget
        val en = AppStringsEn.childDailyTarget
        assertEquals("🎯 设定今日目标（3个）", zh.setGoalsButton(3))
        assertEquals("🎯 Set today's goals (3)", en.setGoalsButton(3))
        assertEquals("✅ 「完成作业」完成 +2分（待家长审核）", zh.goalDonePending("完成作业"))
        assertEquals("✅ “Finish homework” done +2 points (awaiting parent approval)", en.goalDonePending("Finish homework"))
        assertEquals("↩️ 已取消「完成作业」", zh.goalUndone("完成作业"))
        assertEquals("↩️ “Finish homework” unchecked", en.goalUndone("Finish homework"))
        assertEquals("今日自评：⭐⭐⭐", zh.selfRating(3))
        assertEquals("Today's self-rating: ⭐⭐⭐", en.selfRating(3))
        assertEquals("我的反思：今天很专注、完成了难题", zh.reflectionTags(listOf("今天很专注", "完成了难题")))
        assertEquals("My reflections: Focused today, Solved a hard problem", en.reflectionTags(listOf("Focused today", "Solved a hard problem")))
    }

    @Test
    fun parentScheduleCopyDiffers() {
        val zh = AppStringsZh.parentSchedule
        val en = AppStringsEn.parentSchedule
        assertDiffers(
            zh.title to en.title,
            zh.addSchedule to en.addSchedule,
            zh.hint to en.hint,
            zh.emptyTitle to en.emptyTitle,
            zh.emptyHint to en.emptyHint,
            zh.samplesLoaded to en.samplesLoaded,
            zh.loadSamplesButton to en.loadSamplesButton,
            zh.addOwnHint to en.addOwnHint,
            zh.addedMessage to en.addedMessage,
            zh.updatedMessage to en.updatedMessage,
            zh.editTitle to en.editTitle,
            zh.timeLabel to en.timeLabel,
            zh.titleFieldLabel to en.titleFieldLabel,
            zh.linkedTaskOptional to en.linkedTaskOptional,
            zh.none to en.none,
            zh.enableReminder to en.enableReminder,
        )
    }

    @Test
    fun parentScheduleInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentSchedule
        val en = AppStringsEn.parentSchedule
        assertEquals("已删除「起床啦」", zh.removedMessage("起床啦"))
        assertEquals("Deleted “Time to get up”", en.removedMessage("Time to get up"))
        assertEquals("🔗 关联任务：完成作业", zh.linkedTaskLabel("完成作业"))
        assertEquals("🔗 Linked task: Finish homework", en.linkedTaskLabel("Finish homework"))
        assertEquals("每天", zh.repeatDays(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("Every day", en.repeatDays(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("周一、三、日", zh.repeatDays(listOf(1, 3, 7)))
        assertEquals("Mon, Wed, Sun", en.repeatDays(listOf(1, 3, 7)))
    }

    @Test
    fun scheduleAlarmCopyDiffers() {
        val zh = AppStringsZh.scheduleAlarm
        val en = AppStringsEn.scheduleAlarm
        assertDiffers(
            zh.rememberCheckIn to en.rememberCheckIn,
            zh.bodySeparator to en.bodySeparator,   // ""（ZH 保旧字节）vs " "（EN 防粘连）
        )
    }

    @Test
    fun scheduleAlarmBodyPinsBothLanguages() {
        val zh = AppStringsZh.scheduleAlarm
        val en = AppStringsEn.scheduleAlarm
        assertEquals("🌅 起床啦 时间到啦！", zh.timeUp("🌅", "起床啦"))
        assertEquals("🌅 Time to get up — time's up!", en.timeUp("🌅", "Time to get up"))
        // ZH 分隔符为空串：组合结果与迁移前逐字节一致
        assertEquals("🌅 起床啦 时间到啦！完成后记得打卡哦～", zh.timeUp("🌅", "起床啦") + zh.bodySeparator + zh.rememberCheckIn)
        assertEquals("🌅 Time to get up — time's up! Remember to check in when you're done!", en.timeUp("🌅", "Time to get up") + en.bodySeparator + en.rememberCheckIn)
    }

    @Test
    fun childHabitChallengeCopyDiffers() {
        val zh = AppStringsZh.childHabitChallenge
        val en = AppStringsEn.childHabitChallenge
        assertDiffers(
            zh.title to en.title,
            zh.newChallengeContentDescription to en.newChallengeContentDescription,
            zh.emptyMessage to en.emptyMessage,
            zh.activeSectionTitle to en.activeSectionTitle,
            zh.alreadyCheckedInMessage to en.alreadyCheckedInMessage,
            zh.challengeCreated to en.challengeCreated,
            zh.completedSectionTitle to en.completedSectionTitle,
            zh.checkedInToday to en.checkedInToday,
            zh.checkInButton to en.checkInButton,
            zh.newChallengeDialogTitle to en.newChallengeDialogTitle,
            zh.nameLabel to en.nameLabel,
            zh.descLabel to en.descLabel,
            zh.daysLabel to en.daysLabel,
            zh.iconLabel to en.iconLabel,
            zh.createButton to en.createButton,
        )
    }

    @Test
    fun childHabitChallengeInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childHabitChallenge
        val en = AppStringsEn.childHabitChallenge
        assertEquals("🎉 恭喜完成挑战: 每天跑步! +5分待家长审核", zh.challengeCompleteMessage("每天跑步", 5))
        assertEquals("🎉 Challenge complete: 每天跑步! +1 point awaiting parent approval", en.challengeCompleteMessage("每天跑步", 1))
        assertEquals("✅ 打卡成功 +5分（待家长审核）(3/21)", zh.checkInSuccessMessage(5, 3, 21))
        assertEquals("✅ Checked in +5 points (awaiting parent approval) (3/21)", en.checkInSuccessMessage(5, 3, 21))
        assertEquals("3 / 21 天", zh.daysProgress(3, 21))
        assertEquals("3 / 21 days", en.daysProgress(3, 21))
        assertEquals("1 / 1 day", en.daysProgress(1, 1))
        assertEquals("7天", zh.daysOption(7))
        assertEquals("7 days", en.daysOption(7))
    }

    @Test
    fun childGoalTrackerCopyDiffers() {
        val zh = AppStringsZh.childGoalTracker
        val en = AppStringsEn.childGoalTracker
        assertDiffers(
            zh.title to en.title,
            zh.completionRateTitle to en.completionRateTitle,
            zh.streakLabel to en.streakLabel,
            zh.todayDoneLabel to en.todayDoneLabel,
            zh.todayTotalLabel to en.todayTotalLabel,
            zh.dailyGoalsTitle to en.dailyGoalsTitle,
            zh.suggestionAllTasks to en.suggestionAllTasks,
            zh.suggestionThreeBasic to en.suggestionThreeBasic,
            zh.suggestionPomodoro to en.suggestionPomodoro,
            zh.suggestionReview to en.suggestionReview,
            zh.encouragePerfect to en.encouragePerfect,
            zh.encourageHigh to en.encourageHigh,
            zh.encourageMid to en.encourageMid,
            zh.encourageStart to en.encourageStart,
        )
    }

    @Test
    fun childGoalTrackerInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childGoalTracker
        val en = AppStringsEn.childGoalTracker
        assertEquals("3 / 5 任务已完成", zh.tasksCompleted(3, 5))
        assertEquals("3 / 5 tasks completed", en.tasksCompleted(3, 5))
        assertEquals("1 / 1 task completed", en.tasksCompleted(1, 1))
    }

    @Test
    fun childScheduleTimelineCopyDiffers() {
        val zh = AppStringsZh.childScheduleTimeline
        val en = AppStringsEn.childScheduleTimeline
        assertDiffers(
            zh.title to en.title,
            zh.subtitle to en.subtitle,
            zh.emptyTitle to en.emptyTitle,
            zh.emptyHint to en.emptyHint,
            zh.nowBadge to en.nowBadge,
        )
    }

    @Test
    fun childScheduleTimelineInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childScheduleTimeline
        val en = AppStringsEn.childScheduleTimeline
        assertEquals("现在 07:30", zh.nowLabel("07:30"))
        assertEquals("Now 07:30", en.nowLabel("07:30"))
        assertEquals("🔗 完成作业 · 完成后记得打卡哦", zh.linkedTaskLine("完成作业"))
        assertEquals("🔗 Finish homework · remember to check in when done", en.linkedTaskLine("Finish homework"))
    }

    @Test
    fun crossTableVocabularyPinned() {
        // 「保存」与 W2/W3 编辑页同值同译
        assertEquals("保存", AppStringsZh.parentTaskEdit.saveButton)
        assertEquals("Save", AppStringsEn.parentTaskEdit.saveButton)
        // 「取消」「删除」复用公共表
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
        assertEquals("删除", AppStringsZh.common.deleteContentDescription)
        assertEquals("Delete", AppStringsEn.common.deleteContentDescription)
    }
}
