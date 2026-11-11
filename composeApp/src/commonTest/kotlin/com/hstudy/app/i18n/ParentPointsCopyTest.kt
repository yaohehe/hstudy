package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W3 家长积分/审批/奖励文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentTaskCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b）；跨表同值钉死。
 * 覆盖四张表：parentPoints / parentApproval / parentRewardEdit / parentRewardManage。
 */
class ParentPointsCopyTest {

    @Test
    fun parentPointsCopyDiffers() {
        val zh = AppStringsZh.parentPoints
        val en = AppStringsEn.parentPoints
        assertDiffers(
            zh.adjustContentDescription to en.adjustContentDescription,
            zh.dailyStatsTitle to en.dailyStatsTitle,
            zh.taskRankingTitle to en.taskRankingTitle,
            zh.allRecordsTitle to en.allRecordsTitle,
            zh.emptyRecords to en.emptyRecords,
            zh.adjustDialogTitle to en.adjustDialogTitle,
            zh.adjustPointsLabel to en.adjustPointsLabel,
            zh.adjustNoteLabel to en.adjustNoteLabel,
            zh.typeTaskComplete to en.typeTaskComplete,
            zh.typeBonus to en.typeBonus,
            zh.typeAdjustment to en.typeAdjustment,
            zh.typeRewardExchange to en.typeRewardExchange,
            zh.typeReversal to en.typeReversal,
        )
    }

    @Test
    fun parentPointsInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentPoints
        val en = AppStringsEn.parentPoints
        assertEquals("净12", zh.netValue(12))
        assertEquals("Net 12", en.netValue(12))
        assertEquals("净-3", zh.netValue(-3))
        assertEquals("Net -3", en.netValue(-3))
        assertEquals("任务#42", zh.taskFallbackTitle(42L))
        assertEquals("Task #42", en.taskFallbackTitle(42L))
        assertEquals("1次", zh.timesCount(1))
        assertEquals("1 time", en.timesCount(1))
        assertEquals("3次", zh.timesCount(3))
        assertEquals("3 times", en.timesCount(3))
    }

    @Test
    fun parentApprovalCopyDiffers() {
        val zh = AppStringsZh.parentApproval
        val en = AppStringsEn.parentApproval
        assertDiffers(
            zh.title to en.title,
            zh.approveAllButton to en.approveAllButton,
            zh.historyTab to en.historyTab,
            zh.emptyPendingTitle to en.emptyPendingTitle,
            zh.emptyPendingNote to en.emptyPendingNote,
            zh.alreadyProcessedMessage to en.alreadyProcessedMessage,
            zh.rejectedMessage to en.rejectedMessage,
            zh.emptyHistory to en.emptyHistory,
            zh.approveButton to en.approveButton,
            zh.rejectButton to en.rejectButton,
            zh.statusPending to en.statusPending,
            zh.statusApproved to en.statusApproved,
            zh.statusRejected to en.statusRejected,
            zh.statusCancelled to en.statusCancelled,
            zh.statusRevoked to en.statusRevoked,
        )
    }

    @Test
    fun parentApprovalInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentApproval
        val en = AppStringsEn.parentApproval
        assertEquals("✅ 已全部通过，共发放 12 分", zh.approveAllMessage(12))
        assertEquals("✅ All approved — 12 points awarded", en.approveAllMessage(12))
        assertEquals("✅ 已全部通过，共发放 1 分", zh.approveAllMessage(1))
        assertEquals("✅ All approved — 1 point awarded", en.approveAllMessage(1))
        assertEquals("待审核", zh.pendingTab(0))
        assertEquals("Pending", en.pendingTab(0))
        assertEquals("待审核 1", zh.pendingTab(1))
        assertEquals("Pending (1)", en.pendingTab(1))
        assertEquals("待审核 5", zh.pendingTab(5))
        assertEquals("Pending (5)", en.pendingTab(5))
        assertEquals("✅ 已通过：+8分", zh.approvedMessage(8))
        assertEquals("✅ Approved: +8 points", en.approvedMessage(8))
        assertEquals("✅ 已通过：+1分", zh.approvedMessage(1))
        assertEquals("✅ Approved: +1 point", en.approvedMessage(1))
    }

    @Test
    fun parentRewardEditCopyDiffers() {
        val zh = AppStringsZh.parentRewardEdit
        val en = AppStringsEn.parentRewardEdit
        assertDiffers(
            zh.newTitle to en.newTitle,
            zh.editTitle to en.editTitle,
            zh.saveButton to en.saveButton,
            zh.presetHint to en.presetHint,
            zh.nameLabel to en.nameLabel,
            zh.descriptionLabel to en.descriptionLabel,
            zh.costLabel to en.costLabel,
            zh.categoryHeading to en.categoryHeading,
            zh.categoryPickerLabel to en.categoryPickerLabel,
            zh.iconHeading to en.iconHeading,
            zh.deleteButton to en.deleteButton,
        )
    }

    @Test
    fun rewardCategoryOptionsAreAllRegistered() {
        // 奖励编辑页的 categoryOptions 是 canonical 落库值（Ruling 25），渲染走注册表。
        // 7 条必须全部命中：ZH 恒等（zh==key），EN 必须译出（≠原值）。
        val categories = listOf("娱乐", "美食", "出行", "特权", "实物", "社交", "其他")
        categories.forEach { cat ->
            assertEquals(cat, localizedPresetText(cat, DisplayLanguage.ZH))
            assertEquals(false, localizedPresetText(cat, DisplayLanguage.EN) == cat)
        }
        // 默认值"其他"与错题类型共享 key，译名唯一
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))
    }

    @Test
    fun parentRewardManageCopyDiffers() {
        val zh = AppStringsZh.parentRewardManage
        val en = AppStringsEn.parentRewardManage
        assertDiffers(
            zh.title to en.title,
            zh.addContentDescription to en.addContentDescription,
            zh.emptyHint to en.emptyHint,
            zh.loadPresetsButton to en.loadPresetsButton,
            zh.addOwnHint to en.addOwnHint,
            zh.editContentDescription to en.editContentDescription,
        )
    }

    @Test
    fun parentRewardManageCostValuePinsBothLanguages() {
        val zh = AppStringsZh.parentRewardManage
        val en = AppStringsEn.parentRewardManage
        assertEquals("300 积分", zh.costValue(300))
        assertEquals("300 points", en.costValue(300))
        assertEquals("1 积分", zh.costValue(1))
        assertEquals("1 point", en.costValue(1))
    }

    @Test
    fun crossTableVocabularyPinned() {
        // 「积分管理」标题复用家长首页菜单（不另建同值字段）
        assertEquals("积分管理", AppStringsZh.parentHome.menuPointsTitle)
        assertEquals("Points management", AppStringsEn.parentHome.menuPointsTitle)
        // 「保存」与 W2 任务编辑页同值同译
        assertEquals(AppStringsZh.parentTaskEdit.saveButton, AppStringsZh.parentRewardEdit.saveButton)
        assertEquals(AppStringsEn.parentTaskEdit.saveButton, AppStringsEn.parentRewardEdit.saveButton)
        // 「编辑」与 W1 详情表、W2 任务管理表同值同译（三处一份译名）
        assertEquals(AppStringsZh.mistakeDetail.editContentDescription, AppStringsZh.parentRewardManage.editContentDescription)
        assertEquals(AppStringsEn.mistakeDetail.editContentDescription, AppStringsEn.parentRewardManage.editContentDescription)
        assertEquals(AppStringsZh.parentTaskManage.editContentDescription, AppStringsZh.parentRewardManage.editContentDescription)
        assertEquals(AppStringsEn.parentTaskManage.editContentDescription, AppStringsEn.parentRewardManage.editContentDescription)
        // 空态与载入提示复用家长首页（逐字节同值）
        assertEquals("奖励商店还是空的", AppStringsZh.parentHome.setupRewardsMissing)
        assertEquals("The reward store is still empty", AppStringsEn.parentHome.setupRewardsMissing)
        assertEquals("✅ 已载入预设奖励", AppStringsZh.parentHome.presetRewardsLoadedMessage)
        assertEquals("✅ Preset rewards loaded", AppStringsEn.parentHome.presetRewardsLoadedMessage)
        // 「+N分」复用任务列表积分标签（与 Task 9 ChildHome 先例一致）
        assertEquals("+3分", AppStringsZh.taskList.pointsEarnedLabel(3))
        assertEquals("+3 points", AppStringsEn.taskList.pointsEarnedLabel(3))
        assertEquals("+1分", AppStringsZh.taskList.pointsEarnedLabel(1))
        assertEquals("+1 point", AppStringsEn.taskList.pointsEarnedLabel(1))
    }
}