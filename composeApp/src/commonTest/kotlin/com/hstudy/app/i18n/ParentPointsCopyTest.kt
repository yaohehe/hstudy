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
            // v3.3 W2 P2-01：系统自动扣分 PENALTY 类型标签
            zh.typePenalty to en.typePenalty,
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
            // v3.3 W2 P1-02：历史卡按状态如实展示（CANCELLED/REJECTED 分值、已撤销筛选钮）
            zh.scoreCancelled to en.scoreCancelled,
            zh.scoreRejected to en.scoreRejected,
            zh.historyFilterUndone to en.historyFilterUndone,
            // v3.3 W4 §3.3.3-2：ApprovalCard 头部业务类型 chip（8 个 when 支必须互不相同，
            // 两支同值等于两类业务念成同一个标签）
            zh.typeChipTask to en.typeChipTask,
            zh.typeChipHabit to en.typeChipHabit,
            zh.typeChipAssignment to en.typeChipAssignment,
            zh.typeChipTarget to en.typeChipTarget,
            zh.typeChipMistake to en.typeChipMistake,
            zh.typeChipFocus to en.typeChipFocus,
            zh.typeChipVariation to en.typeChipVariation,
            zh.typeChipOther to en.typeChipOther,
            // v3.3.1 D-10 家长撤回审批：钮面 / 弹窗标题 / 终态如实反馈三条静态槽
            zh.revokeApprovalButton to en.revokeApprovalButton,
            zh.revokeConfirmTitle to en.revokeConfirmTitle,
            zh.revokeTerminalMessage to en.revokeTerminalMessage,
            // v3.3.1 D-11 超期待审清理：清理钮面 / 弹窗标题两条静态槽（EN 侧不提 points，
            // 刻意不进 StringsCopyTest 的 EN_STATIC_POINTS_MENTIONS 闭集）
            zh.staleCleanButton to en.staleCleanButton,
            zh.staleConfirmTitle to en.staleConfirmTitle,
        )
        assertEquals(8, listOf(
            zh.typeChipTask, zh.typeChipHabit, zh.typeChipAssignment, zh.typeChipTarget,
            zh.typeChipMistake, zh.typeChipFocus, zh.typeChipVariation, zh.typeChipOther,
        ).distinct().size, "八类业务 chip 中文词面必须互不相同")
        assertEquals(8, listOf(
            en.typeChipTask, en.typeChipHabit, en.typeChipAssignment, en.typeChipTarget,
            en.typeChipMistake, en.typeChipFocus, en.typeChipVariation, en.typeChipOther,
        ).distinct().size, "八类业务 chip 英文词面必须互不相同")
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
        // v3.3 W2 P1-02：历史卡分值语义（REVOKED 扣回 / PENDING 待审），§四 状态机口径
        assertEquals("-8 (已扣回)", zh.scoreRevoked(8))
        assertEquals("-8 points removed", en.scoreRevoked(8))
        assertEquals("-1 point removed", en.scoreRevoked(1))
        assertEquals("+5 (待审)", zh.scorePending(5))
        assertEquals("+5 points pending", en.scorePending(5))
        assertEquals("+1 point pending", en.scorePending(1))
        // v3.3 W4 §3.3.3-1：待审看板两行 + 批量钮 + 确认对话框（中文侧只换数字、不分支）
        assertEquals("待审总笔数：3 笔", zh.pendingCountLabel(3))
        assertEquals("待审总笔数：1 笔", zh.pendingCountLabel(1))
        assertEquals("3 requests pending review", en.pendingCountLabel(3))
        assertEquals("1 request pending review", en.pendingCountLabel(1))
        assertEquals("待发放总积分：+12 分", zh.pendingPointsLabel(12))
        assertEquals("+12 points to award", en.pendingPointsLabel(12))
        assertEquals("+1 point to award", en.pendingPointsLabel(1))
        assertEquals("全部通过 (+12分)", zh.approveAllPointsButton(12))
        assertEquals("Approve all (+12 points)", en.approveAllPointsButton(12))
        assertEquals("Approve all (+1 point)", en.approveAllPointsButton(1))
        assertEquals("确认一次通过全部 3 笔申请，共发放 +12 分？", zh.approveAllDialogMessage(3, 12))
        assertEquals("确认一次通过全部 1 笔申请，共发放 +1 分？", zh.approveAllDialogMessage(1, 1))
        assertEquals("Approve all 3 pending requests and award +12 points now?", en.approveAllDialogMessage(3, 12))
        assertEquals("Approve your only pending request and award +1 point now?", en.approveAllDialogMessage(1, 1))
        assertEquals("Approve all 3 pending requests and award +5 points now?", en.approveAllDialogMessage(3, 5))
        // v3.3.1 D-10 家长撤回审批：确认正文与成功 snackbar（指令 §4.4 词面；EN 单复数走 pointsWord）
        assertEquals("撤回后将扣回已发放的 8 积分，并将申请状态置为已撤销。", zh.revokeConfirmMessage(8))
        assertEquals("撤回后将扣回已发放的 1 积分，并将申请状态置为已撤销。", zh.revokeConfirmMessage(1))
        assertEquals(
            "Revoking removes 8 points already awarded and marks the request as revoked.",
            en.revokeConfirmMessage(8)
        )
        assertEquals(
            "Revoking removes 1 point already awarded and marks the request as revoked.",
            en.revokeConfirmMessage(1)
        )
        assertEquals("🔄 已撤回审批，扣回 8 分", zh.revokeSuccessMessage(8))
        assertEquals("🔄 已撤回审批，扣回 1 分", zh.revokeSuccessMessage(1))
        assertEquals("🔄 Approval revoked — 8 points removed", en.revokeSuccessMessage(8))
        assertEquals("🔄 Approval revoked — 1 point removed", en.revokeSuccessMessage(1))
        // v3.3.1 D-11 超期待审清理：提示条 / 确认正文 / 清理结果 snackbar 三条函数槽
        // （笔数进实参位；中文侧量词 笔 只换数字、EN 侧手写单复数分支，沿 pendingCountLabel 先例）
        assertEquals("3 笔申请已超过 30 天未审核", zh.stalePendingNote(3))
        assertEquals("1 笔申请已超过 30 天未审核", zh.stalePendingNote(1))
        assertEquals("3 requests have been pending for over 30 days", en.stalePendingNote(3))
        assertEquals("1 request has been pending for over 30 days", en.stalePendingNote(1))
        assertEquals(
            "将把 3 笔超过 30 天仍未审核的申请置为已取消。这些申请从未发放积分，取消后余额不变。",
            zh.staleConfirmMessage(3)
        )
        assertEquals(
            "The request that has been pending for over 30 days will be marked cancelled. " +
                "It never awarded points, so your balance stays unchanged.",
            en.staleConfirmMessage(1)
        )
        assertEquals(
            "These 3 requests, each pending for over 30 days, will be marked cancelled. " +
                "They never awarded points, so your balance stays unchanged.",
            en.staleConfirmMessage(3)
        )
        assertEquals("🧹 已清理 3 笔超期申请", zh.staleCleanedMessage(3))
        assertEquals("🧹 已清理 0 笔超期申请", zh.staleCleanedMessage(0))
        assertEquals("🧹 Cleaned 3 stale requests", en.staleCleanedMessage(3))
        assertEquals("🧹 Cleaned 1 stale request", en.staleCleanedMessage(1))
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
        // v3.3 W4 §3.3.4：时间轴分组「今天」与错题预测首元素同值同译（Today，一个英文出处两个消费页）
        assertEquals("今天", AppStringsZh.mistakeList.forecastLabels.first())
        assertEquals(AppStringsZh.mistakeList.forecastLabels.first(), AppStringsZh.childPoints.sectionToday)
        assertEquals(AppStringsEn.mistakeList.forecastLabels.first(), AppStringsEn.childPoints.sectionToday)
        // v3.3 W4 §3.3.4：家长端积分管理页看板/分组/标签跨表消费 childPoints 槽（不重开同值槽），
        // PENALTY 标签用无 emoji 前缀的孩子端词面（家长端 typePenalty 带 ⚠️ 前缀是两个值）
        assertEquals("打卡撤销", AppStringsZh.childPoints.tagCheckInUndo)
        assertEquals("Check-in undo", AppStringsEn.childPoints.tagCheckInUndo)
    }
}