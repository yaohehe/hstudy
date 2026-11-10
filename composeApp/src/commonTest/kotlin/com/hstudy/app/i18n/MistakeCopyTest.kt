package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 阶段 2 W1 错题域文案表的漏译/语序守卫（沿用 StringsCopyTest 的约定）：
 * - 每个静态字段 ZH≠EN（assertDiffers 由 AssertDiffers.kt 提供）；
 * - 每个函数型字段必须有**逐字打出的双语期望**（Ruling 20-b）；
 * - 跨表共享的同一个中文值只允许一个英文出处，由 pinned 测试钉住。
 */
class MistakeCopyTest {

    @Test
    fun mistakeSharedStaticCopyDiffers() {
        val zh = AppStringsZh.mistake
        val en = AppStringsEn.mistake
        assertDiffers(
            zh.subjectMath to en.subjectMath,
            zh.subjectChinese to en.subjectChinese,
            zh.subjectEnglish to en.subjectEnglish,
            zh.subjectScience to en.subjectScience,
            zh.subjectOther to en.subjectOther,
            zh.subjectMathChip to en.subjectMathChip,
            zh.subjectChineseChip to en.subjectChineseChip,
            zh.subjectEnglishChip to en.subjectEnglishChip,
            zh.subjectScienceChip to en.subjectScienceChip,
            zh.subjectOtherChip to en.subjectOtherChip,
            zh.masteredStatus to en.masteredStatus,
            zh.unmasteredStatus to en.unmasteredStatus,
            zh.photoContentDescription to en.photoContentDescription,
            zh.proficiencyNone to en.proficiencyNone,
            zh.proficiencyFair to en.proficiencyFair,
            zh.proficiencyStrong to en.proficiencyStrong,
            zh.nextReviewMastered to en.nextReviewMastered,
            zh.nextReviewToday to en.nextReviewToday,
            zh.nextReviewTomorrow to en.nextReviewTomorrow,
        )
    }

    @Test
    fun mistakeSharedFunctionCopyPinsBothLanguages() {
        val zh = AppStringsZh.mistake
        val en = AppStringsEn.mistake
        // proficiencyTimes 与 nextReviewInDays 都是函数型字段：逐字钉死两种语言
        assertEquals("复习1次", zh.proficiencyTimes(1))
        assertEquals("复习2次", zh.proficiencyTimes(2))
        assertEquals("Reviewed 1 time", en.proficiencyTimes(1))
        assertEquals("Reviewed 2 times", en.proficiencyTimes(2))
        assertEquals("3天后复习", zh.nextReviewInDays(3))
        assertEquals("Review in 3 days", en.nextReviewInDays(3))
    }

    @Test
    fun mistakeListCopyDiffers() {
        val zh = AppStringsZh.mistakeList
        val en = AppStringsEn.mistakeList
        assertDiffers(
            zh.title to en.title,
            zh.addContentDescription to en.addContentDescription,
            zh.streakEncouragementHigh to en.streakEncouragementHigh,
            zh.streakEncouragementMid to en.streakEncouragementMid,
            zh.streakEncouragementStart to en.streakEncouragementStart,
            zh.streakEncouragementNone to en.streakEncouragementNone,
            zh.calendarTitle to en.calendarTitle,
            zh.calendarHint to en.calendarHint,
            zh.milestoneName5 to en.milestoneName5,
            zh.milestoneName15 to en.milestoneName15,
            zh.milestoneName30 to en.milestoneName30,
            zh.milestoneName50 to en.milestoneName50,
            zh.milestoneName100 to en.milestoneName100,
            zh.masteryProgressTitle to en.masteryProgressTitle,
            zh.masteryAllUnlocked to en.masteryAllUnlocked,
            zh.forecastTitle to en.forecastTitle,
            zh.dueBannerHint to en.dueBannerHint,
            zh.reminderTitle to en.reminderTitle,
            zh.reminderHint to en.reminderHint,
            zh.reminderOffMessage to en.reminderOffMessage,
            zh.reminderTimeLabel to en.reminderTimeLabel,
            zh.searchPlaceholder to en.searchPlaceholder,
            zh.clearSearchContentDescription to en.clearSearchContentDescription,
            zh.filterAll to en.filterAll,
            zh.filterUnmasteredOnly to en.filterUnmasteredOnly,
            zh.emptyState to en.emptyState,
            zh.dueBadge to en.dueBadge,
            zh.reviewButton to en.reviewButton,
            zh.markMasteredContentDescription to en.markMasteredContentDescription,
        )
        // 列表型字段：逐项钉死（assertDiffers 看不到 List 内部）
        assertEquals(listOf("今天", "明天", "后天", "3天后", "4天后", "5天后", "6天后"), zh.forecastLabels)
        assertEquals(listOf("Today", "Tomorrow", "In 2 days", "In 3 days", "In 4 days", "In 5 days", "In 6 days"), en.forecastLabels)
    }

    @Test
    fun mistakeListInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.mistakeList
        val en = AppStringsEn.mistakeList
        assertEquals("已掌握 7", zh.masteredBadge(7))
        assertEquals("Mastered: 7", en.masteredBadge(7))
        assertEquals("📖 复习完成！+3分已提交家长审核 明天复习", zh.reviewSuccessMessage("明天复习"))
        assertEquals("📖 Review complete! +3 points submitted for parent approval. Review tomorrow", en.reviewSuccessMessage("Review tomorrow"))
        assertEquals("连续复习 6 天", zh.streakTitle(6))
        assertEquals("Review streak: 6 days", en.streakTitle(6))
        assertEquals("Review streak: 1 day", en.streakTitle(1))
        assertEquals("🎉 连续复习3天达成！奖励 +3 分！", zh.milestoneClaimedMessage(3, 3))
        assertEquals("🎉 3-day review streak reached! Reward: +3 points!", en.milestoneClaimedMessage(3, 3))
        assertEquals("🎁 领取连续复习3天奖励 +3分", zh.milestoneClaimButton(3, 3))
        assertEquals("🎁 Claim 3-day streak reward +3 points", en.milestoneClaimButton(3, 3))
        assertEquals("近28天复习 5 天", zh.calendarSummary(5))
        assertEquals("Reviewed 5 days in the last 28 days", en.calendarSummary(5))
        assertEquals("再掌握 3 道，即可解锁「错题新手」里程碑！", zh.masteryNextHint(3, zh.milestoneName5))
        assertEquals("Master 3 more to unlock the “Mistake Rookie” milestone!", en.masteryNextHint(3, en.milestoneName5))
        assertEquals("重点攻克薄弱科目：数学", zh.weakSubjectTitle("数学"))
        assertEquals("Focus on your weakest subject: Math", en.weakSubjectTitle("Math"))
        assertEquals("点击筛选数学未掌握错题，针对性突破", zh.weakSubjectHint("数学"))
        assertEquals("Tap to filter unmastered Math mistakes and target your weak spots", en.weakSubjectHint("Math"))
        assertEquals("今日有 4 道错题待复习", zh.dueBannerTitle(4))
        assertEquals("Due for review today: 4", en.dueBannerTitle(4))
        assertEquals("✅ 已开启每日19点复习提醒", zh.reminderOnMessage(19))
        assertEquals("✅ Daily review reminder on at 19:00", en.reminderOnMessage(19))
        assertEquals("19点", zh.reminderHourChip(19))
        assertEquals("19:00", en.reminderHourChip(19))
        assertEquals("今日待复习 4", zh.filterDue(4))
        assertEquals("Due today 4", en.filterDue(4))
        assertEquals("今日待复习", zh.filterDue(0))
        assertEquals("Due today", en.filterDue(0))
    }

    @Test
    fun mistakeDetailCopyDiffers() {
        val zh = AppStringsZh.mistakeDetail
        val en = AppStringsEn.mistakeDetail
        assertDiffers(
            zh.editContentDescription to en.editContentDescription,
            zh.editHeading to en.editHeading,
            zh.titleLabel to en.titleLabel,
            zh.contentLabel to en.contentLabel,
            zh.wrongReasonLabel to en.wrongReasonLabel,
            zh.correctMethodLabel to en.correctMethodLabel,
            zh.errorTypeLabel to en.errorTypeLabel,
            zh.updatedMessage to en.updatedMessage,
            zh.saveButton to en.saveButton,
            zh.contentHeading to en.contentHeading,
            zh.recallHeading to en.recallHeading,
            zh.recallHint to en.recallHint,
            zh.wrongReasonHeading to en.wrongReasonHeading,
            zh.correctMethodHeading to en.correctMethodHeading,
            zh.photoHeading to en.photoHeading,
            zh.masteredCard to en.masteredCard,
            zh.revealButton to en.revealButton,
            zh.selfCheckPrompt to en.selfCheckPrompt,
            zh.gotItButton to en.gotItButton,
            zh.missedButton to en.missedButton,
            zh.gotItMessage to en.gotItMessage,
            zh.missedMessage to en.missedMessage,
            zh.reviewDoneCard to en.reviewDoneCard,
            zh.variationTitle to en.variationTitle,
            zh.variationDone to en.variationDone,
            zh.variationThink to en.variationThink,
            zh.variationNotesHeading to en.variationNotesHeading,
            zh.variationInputLabel to en.variationInputLabel,
            zh.variationDoneMessage to en.variationDoneMessage,
            zh.variationButton to en.variationButton,
            zh.deleteDialogTitle to en.deleteDialogTitle,
        )
    }

    @Test
    fun mistakeDetailInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.mistakeDetail
        val en = AppStringsEn.mistakeDetail
        assertEquals("🔢 错题详情", zh.screenTitle("🔢"))
        assertEquals("🔢 Mistake Details", en.screenTitle("🔢"))
        assertEquals("添加于 2026-11-10", zh.addedOn("2026-11-10"))
        assertEquals("Added on 2026-11-10", en.addedOn("2026-11-10"))
        assertEquals("复习记录 (3)", zh.reviewHistoryTitle(3))
        assertEquals("Review history (3)", en.reviewHistoryTitle(3))
        assertEquals("11-10 +3分", zh.reviewHistoryLine("11-10", 3))
        assertEquals("11-10 +3 points", en.reviewHistoryLine("11-10", 3))
        assertEquals("11-10 +1分", zh.reviewHistoryLine("11-10", 1))
        assertEquals("11-10 +1 point", en.reviewHistoryLine("11-10", 1))
        assertEquals("确定要删除「分数计算」吗？删除后不可恢复。", zh.deleteDialogText("分数计算"))
        assertEquals("Delete “分数计算”? This cannot be undone.", en.deleteDialogText("分数计算"))
        // 15 条举一反三引导问题：逐表逐项钉死
        assertEquals(3, zh.variationPromptsMath.size)
        assertEquals(
            listOf("这道题考查的是什么知识点？", "改变数字或条件，你还会解吗？", "你能自己编一道类似的题吗？"),
            zh.variationPromptsMath,
        )
        assertEquals(
            listOf(
                "What concept does this question test?",
                "Can you solve it with different numbers or conditions?",
                "Can you make up a similar question yourself?",
            ),
            en.variationPromptsMath,
        )
        assertEquals(zh.variationPromptsChinese.size, en.variationPromptsChinese.size)
        assertEquals(zh.variationPromptsEnglish.size, en.variationPromptsEnglish.size)
        assertEquals(zh.variationPromptsScience.size, en.variationPromptsScience.size)
        assertEquals(zh.variationPromptsOther.size, en.variationPromptsOther.size)
        // 中文五组逐项钉死（英文同位置非空且与中文逐项不同）
        val zhAll = zh.variationPromptsChinese + zh.variationPromptsEnglish + zh.variationPromptsScience + zh.variationPromptsOther
        val enAll = en.variationPromptsChinese + en.variationPromptsEnglish + en.variationPromptsScience + en.variationPromptsOther
        assertEquals(12, zhAll.size)
        assertEquals(12, enAll.size)
        zhAll.zip(enAll).forEach { (z, e) ->
            assertNotEquals(z, e, "举一反三问题疑似漏译：$z")
        }
    }

    @Test
    fun mistakeAddCopyDiffers() {
        val zh = AppStringsZh.mistakeAdd
        val en = AppStringsEn.mistakeAdd
        assertDiffers(
            zh.title to en.title,
            zh.subjectLabel to en.subjectLabel,
            zh.titleLabel to en.titleLabel,
            zh.contentLabel to en.contentLabel,
            zh.wrongReasonLabel to en.wrongReasonLabel,
            zh.errorTypeHint to en.errorTypeHint,
            zh.correctMethodLabel to en.correctMethodLabel,
            zh.takePhotoButton to en.takePhotoButton,
            zh.photoTaken to en.photoTaken,
            zh.missingTitleMessage to en.missingTitleMessage,
            zh.saveButton to en.saveButton,
        )
    }

    @Test
    fun parentMistakeReviewCopyDiffers() {
        val zh = AppStringsZh.parentMistakeReview
        val en = AppStringsEn.parentMistakeReview
        assertDiffers(
            zh.title to en.title,
            zh.overviewTitle to en.overviewTitle,
            zh.statTotal to en.statTotal,
            zh.statUnmastered to en.statUnmastered,
            zh.statMastered to en.statMastered,
            zh.subjectDistribution to en.subjectDistribution,
            zh.recentMistakes to en.recentMistakes,
        )
    }

    @Test
    fun reviewReminderCopyDiffers() {
        val zh = AppStringsZh.reviewReminder
        val en = AppStringsEn.reviewReminder
        assertDiffers(
            zh.title to en.title,
            zh.defaultBody to en.defaultBody,
            zh.fallbackBody to en.fallbackBody,
        )
    }

    @Test
    fun reviewReminderFunctionCopyPinsBothLanguages() {
        val zh = AppStringsZh.reviewReminder
        val en = AppStringsEn.reviewReminder
        assertEquals("📚 今日有 3 道错题待复习，趁热打铁及时复习，记得更牢～", zh.dueBody(3))
        assertEquals("📚 3 mistakes due for review today — review while it's fresh to remember longer", en.dueBody(3))
        assertEquals("📚 1 mistake due for review today — review while it's fresh to remember longer", en.dueBody(1))
    }

    @Test
    fun crossTableSharedLabelsPinned() {
        // 同一个中文值只允许一个英文出处：错题列表的「全部」与任务列表的「全部」
        assertEquals(AppStringsZh.taskList.filterAll, AppStringsZh.mistakeList.filterAll)
        assertEquals(AppStringsEn.taskList.filterAll, AppStringsEn.mistakeList.filterAll)
        // 科目「其他」与 W0 注册表 canonical 同值同译
        assertEquals("其他", AppStringsZh.mistake.subjectOther)
        assertEquals("Other", AppStringsEn.mistake.subjectOther)
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))
        // 里程碑名与成就注册表命名一致（克星/大师）
        assertEquals("Mistake Buster", AppStringsEn.mistakeList.milestoneName50)
        assertEquals("Mistake Master", AppStringsEn.mistakeList.milestoneName100)
    }
}
