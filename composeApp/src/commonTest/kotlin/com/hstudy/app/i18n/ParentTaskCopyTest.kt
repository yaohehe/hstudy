package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W2 家长任务管理文案表守卫（沿用 StringsCopyTest/MistakeCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b）；跨表同值钉死。
 */
class ParentTaskCopyTest {

    @Test
    fun parentTaskManageCopyDiffers() {
        val zh = AppStringsZh.parentTaskManage
        val en = AppStringsEn.parentTaskManage
        assertDiffers(
            zh.addContentDescription to en.addContentDescription,
            zh.editContentDescription to en.editContentDescription,
        )
    }

    @Test
    fun parentTaskManagePointsLinePinsBothLanguages() {
        val zh = AppStringsZh.parentTaskManage
        val en = AppStringsEn.parentTaskManage
        assertEquals("基础 3分 · 奖励 +2分", zh.pointsLine(3, 2))
        assertEquals("Base 3 points · Bonus +2 points", en.pointsLine(3, 2))
        assertEquals("基础 1分", zh.pointsLine(1, 0))
        assertEquals("Base 1 point", en.pointsLine(1, 0))
    }

    @Test
    fun parentTaskEditCopyDiffers() {
        val zh = AppStringsZh.parentTaskEdit
        val en = AppStringsEn.parentTaskEdit
        assertDiffers(
            zh.newTitle to en.newTitle,
            zh.editTitle to en.editTitle,
            zh.saveButton to en.saveButton,
            zh.titleLabel to en.titleLabel,
            zh.titlePlaceholder to en.titlePlaceholder,
            zh.suggestionsHeading to en.suggestionsHeading,
            zh.descriptionLabel to en.descriptionLabel,
            zh.descriptionPlaceholder to en.descriptionPlaceholder,
            zh.categoryHeading to en.categoryHeading,
            zh.categoryBasic to en.categoryBasic,
            zh.categoryEnhancement to en.categoryEnhancement,
            zh.categoryHabit to en.categoryHabit,
            zh.frequencyHeading to en.frequencyHeading,
            zh.freqDaily to en.freqDaily,
            zh.freqWeekly to en.freqWeekly,
            zh.freqMonthly to en.freqMonthly,
            zh.freqOnce to en.freqOnce,
            zh.mandatoryTitle to en.mandatoryTitle,
            zh.difficultyHeading to en.difficultyHeading,
            zh.difficultyNormal to en.difficultyNormal,
            zh.difficultyAdvanced to en.difficultyAdvanced,
            zh.difficultyChallenge to en.difficultyChallenge,
            zh.difficultyNoteAdvanced to en.difficultyNoteAdvanced,
            zh.difficultyNoteChallenge to en.difficultyNoteChallenge,
            zh.difficultyNoteNormal to en.difficultyNoteNormal,
            zh.basePointsLabel to en.basePointsLabel,
            zh.bonusPointsLabel to en.bonusPointsLabel,
            zh.deleteButton to en.deleteButton,
        )
    }

    @Test
    fun parentTaskEditInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentTaskEdit
        val en = AppStringsEn.parentTaskEdit
        assertEquals("📝 完成当天作业 +3", zh.suggestionChip("📝", "完成当天作业", 3))
        assertEquals("📝 Finish today's homework +3", en.suggestionChip("📝", "Finish today's homework", 3))
        assertEquals("若当天未完成此任务，将自动扣减 5 分", zh.mandatoryHint(5))
        assertEquals("If not completed that day, 5 points will be deducted automatically", en.mandatoryHint(5))
        assertEquals("If not completed that day, 1 point will be deducted automatically", en.mandatoryHint(1))
    }

    @Test
    fun parentAssignTaskCopyDiffers() {
        val zh = AppStringsZh.parentAssignTask
        val en = AppStringsEn.parentAssignTask
        assertDiffers(
            zh.title to en.title,
            zh.formHeading to en.formHeading,
            zh.templateQuickSelect to en.templateQuickSelect,
            zh.titleLabel to en.titleLabel,
            zh.titlePlaceholder to en.titlePlaceholder,
            zh.descriptionLabel to en.descriptionLabel,
            zh.pointsLabel to en.pointsLabel,
            zh.noteLabel to en.noteLabel,
            zh.notePlaceholder to en.notePlaceholder,
            zh.assignedMessage to en.assignedMessage,
            zh.assignButton to en.assignButton,
            zh.emptyState to en.emptyState,
            zh.completedStatus to en.completedStatus,
            zh.pendingStatus to en.pendingStatus,
            zh.deletedMessage to en.deletedMessage,
        )
    }

    @Test
    fun parentAssignTaskInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentAssignTask
        val en = AppStringsEn.parentAssignTask
        assertEquals("完成当天作业 +3", zh.suggestionChip("完成当天作业", 3))
        assertEquals("Finish today's homework +3", en.suggestionChip("Finish today's homework", 3))
        assertEquals("📋 今日已布置 (2)", zh.todayHeading(2))
        assertEquals("📋 Assigned today (2)", en.todayHeading(2))
        assertEquals("+3分", zh.pointsBadge(3))
        assertEquals("+3 points", en.pointsBadge(3))
        assertEquals("+1分", zh.pointsBadge(1))
        assertEquals("+1 point", en.pointsBadge(1))
    }

    @Test
    fun parentTaskTemplateCopyDiffers() {
        val zh = AppStringsZh.parentTaskTemplate
        val en = AppStringsEn.parentTaskTemplate
        assertDiffers(
            zh.title to en.title,
            zh.usageHeading to en.usageHeading,
            zh.usageHint to en.usageHint,
            zh.previewHeading to en.previewHeading,
            zh.confirmButton to en.confirmButton,
            zh.applyButton to en.applyButton,
        )
    }

    @Test
    fun parentTaskTemplateInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentTaskTemplate
        val en = AppStringsEn.parentTaskTemplate
        assertEquals("当前共有 14 个任务", zh.currentTaskCount(14))
        assertEquals("You currently have 14 tasks", en.currentTaskCount(14))
        assertEquals("🌱 应用「小学低年级」模板？", zh.applyDialogTitle("🌱", "小学低年级"))
        assertEquals("🌱 Apply the “Lower primary” template?", en.applyDialogTitle("🌱", "Lower primary"))
        assertEquals("将替换当前所有任务为模板预设的 14 个任务。", zh.replaceHint(14))
        assertEquals("This replaces all current tasks with the template's 14 tasks.", en.replaceHint(14))
        assertEquals("📝 完成当天作业 (+3分)", zh.previewLine("📝", "完成当天作业", 3))
        assertEquals("📝 Finish today's homework (+3 points)", en.previewLine("📝", "Finish today's homework", 3))
        assertEquals("...还有9个任务", zh.moreTasks(9))
        assertEquals("...and 9 more tasks", en.moreTasks(9))
        assertEquals("✅ 已应用「小学低年级」模板", zh.appliedMessage("小学低年级"))
        assertEquals("✅ Applied the “Lower primary” template", en.appliedMessage("Lower primary"))
        assertEquals("📝 基础×5", zh.basicCount(5))
        assertEquals("📝 Basic ×5", en.basicCount(5))
        assertEquals("📈 提升×4", zh.enhancementCount(4))
        assertEquals("📈 Advanced ×4", en.enhancementCount(4))
        assertEquals("🏆 习惯×5", zh.habitCount(5))
        assertEquals("🏆 Habits ×5", en.habitCount(5))
    }

    @Test
    fun crossTableVocabularyPinned() {
        // 任务管理页复用的分类词与任务列表同源（中文值/英文值都相等）
        assertEquals("全部", AppStringsZh.taskList.filterAll)
        assertEquals("基础", AppStringsZh.taskList.filterBasic)
        assertEquals("提升", AppStringsZh.taskList.filterEnhancement)
        assertEquals("习惯", AppStringsZh.taskList.filterHabits)
        // 「任务管理」标题复用家长首页菜单（不另建同值字段）
        assertEquals("任务管理", AppStringsZh.parentHome.menuTaskManageTitle)
        assertEquals("Task management", AppStringsEn.parentHome.menuTaskManageTitle)
        // 「编辑」contentDescription 与 W1 详情表同值同译
        assertEquals(AppStringsZh.mistakeDetail.editContentDescription, AppStringsZh.parentTaskManage.editContentDescription)
        assertEquals(AppStringsEn.mistakeDetail.editContentDescription, AppStringsEn.parentTaskManage.editContentDescription)
        // 难度芯片英文 = W0 注册表难度译名 + 星数
        assertEquals(localizedPresetText("普通", DisplayLanguage.EN) + " ⭐", AppStringsEn.parentTaskEdit.difficultyNormal)
        assertEquals(localizedPresetText("进阶", DisplayLanguage.EN) + " ⭐⭐", AppStringsEn.parentTaskEdit.difficultyAdvanced)
        assertEquals(localizedPresetText("挑战", DisplayLanguage.EN) + " ⭐⭐⭐", AppStringsEn.parentTaskEdit.difficultyChallenge)
        // 模板分类计数英文词形与任务列表筛选词一致
        assertEquals("📝 " + AppStringsEn.taskList.filterBasic + " ×3", AppStringsEn.parentTaskTemplate.basicCount(3))
        assertEquals("📈 " + AppStringsEn.taskList.filterEnhancement + " ×2", AppStringsEn.parentTaskTemplate.enhancementCount(2))
        assertEquals("🏆 " + AppStringsEn.taskList.filterHabits + " ×1", AppStringsEn.parentTaskTemplate.habitCount(1))
    }
}