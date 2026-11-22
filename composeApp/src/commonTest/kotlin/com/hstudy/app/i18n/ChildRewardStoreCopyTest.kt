package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W4 孩子端文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentPointsCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b），英文 one/other（单复数样例）用 pointsWord 逐字钉死。
 * 覆盖四张表：childRewardStore / childLuckyDraw / childPoints / childAchievements。
 */
class ChildRewardStoreCopyTest {

    @Test
    fun childRewardStoreCopyDiffers() {
        val zh = AppStringsZh.childRewardStore
        val en = AppStringsEn.childRewardStore
        assertDiffers(
            zh.historyTitle to en.historyTitle,
            zh.exchangedFallback to en.exchangedFallback,
            zh.emptyHint to en.emptyHint,
            zh.confirmExchange to en.confirmExchange,
            zh.insufficientPoints to en.insufficientPoints,
            zh.redeemButton to en.redeemButton,
        )
    }

    @Test
    fun childRewardStoreInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childRewardStore
        val en = AppStringsEn.childRewardStore
        assertEquals("🎉 你有 12 分，现在可以兑换 3 个奖励啦！", zh.bannerAffordable(12, 3))
        assertEquals("🎉 You have 12 points — enough to redeem 3 rewards!", en.bannerAffordable(12, 3))
        assertEquals("🎉 你有 1 分，现在可以兑换 1 个奖励啦！", zh.bannerAffordable(1, 1))
        assertEquals("🎉 You have 1 point — enough to redeem 1 reward!", en.bannerAffordable(1, 1))
        assertEquals("💪 你有 5 分，完成任务赚积分来兑换吧！", zh.bannerKeepEarning(5))
        assertEquals("💪 You have 1 point — complete tasks to earn more and redeem rewards!", en.bannerKeepEarning(1))
        assertEquals("再攒 5 分就能兑换「🎮 玩游戏」啦～", zh.nextGoalMessage(5, "🎮", "玩游戏"))
        assertEquals("Just 1 more point to redeem “🎮 Playtime”!", en.nextGoalMessage(1, "🎮", "Playtime"))
        assertEquals("娱乐奖励", zh.categoryTitle("娱乐"))
        assertEquals("Entertainment rewards", en.categoryTitle("Entertainment"))
        assertEquals("-50分", zh.spentValue(50))
        assertEquals("-50 points", en.spentValue(50))
        assertEquals("-1 point", en.spentValue(1))
        assertEquals("需要 300 积分", zh.costNeeded(300))
        assertEquals("Costs 1 point", en.costNeeded(1))
        assertEquals("当前余额：12 积分", zh.currentBalance(12))
        assertEquals("Current balance: 12 points", en.currentBalance(12))
        assertEquals("🎉 兑换成功！看电影", zh.redeemSuccess("看电影"))
        assertEquals("🎉 Redeemed! Movies", en.redeemSuccess("Movies"))
        assertEquals("还差 20 分", zh.shortBy(20))
        assertEquals("1 point short", en.shortBy(1))
        // v3.3 W5 §3.4-奖励1：兑换钮「消耗 N 积分」显式成本提示（指令词面），两语逐字钉死
        assertEquals("消耗 20 积分", zh.redeemSpendButton(20))
        assertEquals("Spend 1 point", en.redeemSpendButton(1))
        assertEquals("Spend 300 points", en.redeemSpendButton(300))
        assertEquals("⭐ 300 积分", zh.priceLabel(300))
        assertEquals("⭐ 1 point", en.priceLabel(1))
    }

    @Test
    fun childLuckyDrawCopyDiffers() {
        val zh = AppStringsZh.childLuckyDraw
        val en = AppStringsEn.childLuckyDraw
        assertDiffers(
            zh.title to en.title,
            zh.headline to en.headline,
            zh.subtitle to en.subtitle,
            zh.alreadyDrawn to en.alreadyDrawn,
            zh.drawing to en.drawing,
            zh.drawButton to en.drawButton,
            zh.comeTomorrow to en.comeTomorrow,
            zh.infoTitle to en.infoTitle,
            zh.infoPool to en.infoPool,
            zh.infoStreak to en.infoStreak,
        )
    }

    @Test
    fun childLuckyDrawInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childLuckyDraw
        val en = AppStringsEn.childLuckyDraw
        assertEquals("+3 分", zh.pointsWonValue(3))
        assertEquals("+3 points", en.pointsWonValue(3))
        assertEquals("+1 point", en.pointsWonValue(1))
        assertEquals("恭喜获得 3 积分奖励！", zh.winMessage(3))
        assertEquals("Congratulations — you won 3 points!", en.winMessage(3))
        assertEquals("Congratulations — you won 1 point!", en.winMessage(1))
        assertEquals("🎉 抽中 3 积分！", zh.drawSnackbar(3))
        assertEquals("🎉 You won 3 points!", en.drawSnackbar(3))
        assertEquals("🎉 You won 1 point!", en.drawSnackbar(1))
    }

    @Test
    fun childPointsCopyDiffers() {
        val zh = AppStringsZh.childPoints
        val en = AppStringsEn.childPoints
        assertDiffers(
            zh.balanceTitle to en.balanceTitle,
            zh.recordsTitle to en.recordsTitle,
            zh.emptyRecords to en.emptyRecords,
            zh.typeTaskComplete to en.typeTaskComplete,
            zh.typeBonus to en.typeBonus,
            zh.typeAdjustment to en.typeAdjustment,
            zh.typeRewardExchange to en.typeRewardExchange,
            zh.typeReversal to en.typeReversal,
            // v3.3 W2 P2-01：系统自动扣分 PENALTY 类型标签
            zh.typePenalty to en.typePenalty,
            // v3.3 W4 §3.3.4：资产看板副标 + 时间轴分组标题 + REVERSAL 标签（历史记录带 🕰️ 前缀
            // 与 parentApproval.historyTab 区分成两个值，沿 typePenalty 差前缀先例）
            zh.balanceSubtitle to en.balanceSubtitle,
            zh.sectionToday to en.sectionToday,
            zh.sectionYesterday to en.sectionYesterday,
            zh.sectionWeekEarlier to en.sectionWeekEarlier,
            zh.sectionHistory to en.sectionHistory,
            zh.tagCheckInUndo to en.tagCheckInUndo,
            // v3.3.1 D-08：心愿流水的通用兜底标签（心愿已删除、回查不到名字时用）
            zh.wishDepositGeneric to en.wishDepositGeneric,
            zh.wishRefundGeneric to en.wishRefundGeneric,
            // v3.3.2 D-12：三处里程碑 bonus 的渲染派生静态槽（中文=旧落库原文逐字节，英文新写）
            zh.bonusExcellent to en.bonusExcellent,
            zh.bonusWeekly to en.bonusWeekly,
            // v3.3.2 D-13 fix round 1：转盘发奖派生静态槽（中文=D-13 Task B 旧落库原文逐字节）
            zh.bonusLucky to en.bonusLucky,
        )
        // 四个分组标题两两互不相同（when 分桶的 0..3 支同值等于两段流水念成同一段）
        assertEquals(4, listOf(zh.sectionToday, zh.sectionYesterday, zh.sectionWeekEarlier, zh.sectionHistory).distinct().size)
        assertEquals(4, listOf(en.sectionToday, en.sectionYesterday, en.sectionWeekEarlier, en.sectionHistory).distinct().size)
    }

    @Test
    fun childPointsInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childPoints
        val en = AppStringsEn.childPoints
        assertEquals("⭐ 12 分", zh.balanceValue(12))
        assertEquals("⭐ 12 points", en.balanceValue(12))
        assertEquals("⭐ 1 point", en.balanceValue(1))
        assertEquals("⏳ 待家长审核 +3 分（通过后计入余额）", zh.pendingNote(3))
        assertEquals("⏳ Pending parent approval: +3 points (added to your balance once approved)", en.pendingNote(3))
        assertEquals("⏳ Pending parent approval: +1 point (added to your balance once approved)", en.pendingNote(1))
        // v3.3 W4 §3.3.4-1：🌟 累计获得行（英文单复数 + 中文只换数字）
        assertEquals("🌟 累计获得 40 分", zh.totalEarnedValue(40))
        assertEquals("🌟 累计获得 1 分", zh.totalEarnedValue(1))
        assertEquals("🌟 Total earned: 40 points", en.totalEarnedValue(40))
        assertEquals("🌟 Total earned: 1 point", en.totalEarnedValue(1))
        // v3.3.1 D-08：明细页由 note 业务键派生的心愿流水文案（账本里不再有中文，英文界面全靠这两条槽）。
        // 名字实参是页面已经包过 localizedPresetText 的显示名，表函数只负责包句式（与心愿两页同口径）。
        assertEquals("存入心愿「一台新平板」", zh.wishDeposit("一台新平板"))
        assertEquals("Saved into wish “A new tablet”", en.wishDeposit("A new tablet"))
        assertEquals("心愿退回「一台新平板」", zh.wishRefund("一台新平板"))
        assertEquals("Wish refund for “A new tablet”", en.wishRefund("A new tablet"))
        assertEquals("存入心愿", zh.wishDepositGeneric)
        assertEquals("Wish deposit", en.wishDepositGeneric)
        assertEquals("心愿退回", zh.wishRefundGeneric)
        assertEquals("Wish refund", en.wishRefundGeneric)
        // v3.3.2 D-12：bonus 三键派生。中文**逐字节**等于 D-12 前直写 DB 的原文（三条旧常量
        // CanonicalChildHomeText.EXCELLENT_BONUS_NOTE / WEEKLY_CHALLENGE_NOTE / streakMilestoneNote，
        // 新旧记录中文词面一致）；英文为本缺陷新写（病灶=英文用户在账本里读到中文）。
        assertEquals("今日学习表现优秀奖励", zh.bonusExcellent)
        assertEquals("Excellent study bonus for today", en.bonusExcellent)
        assertEquals("每周挑战奖励：本周打卡5天", zh.bonusWeekly)
        assertEquals("Weekly challenge bonus: 5 check-ins this week", en.bonusWeekly)
        assertEquals("连续打卡7天里程碑奖励", zh.bonusStreak(7))
        assertEquals("连续打卡30天里程碑奖励", zh.bonusStreak(30))
        assertEquals("Streak milestone bonus: 7 days in a row", en.bonusStreak(7))
        assertEquals("Streak milestone bonus: 1 day in a row", en.bonusStreak(1))
        // v3.3.2 D-13 fix round 1：复习/转盘两键派生。中文**逐字节**等于 D-13 Task B 直写 DB 的
        // 旧原文（ChildMistakeListScreen"连续复习N天里程碑奖励" / ChildLuckyDrawScreen"每日幸运抽奖"，
        // 新旧记录中文词面一致）；英文为本修复新写（不提 points，沿 bonusStreak 的 days==1 防御分支）。
        assertEquals("连续复习7天里程碑奖励", zh.bonusReview(7))
        assertEquals("连续复习30天里程碑奖励", zh.bonusReview(30))
        assertEquals("Review milestone bonus: 7 days of review in a row", en.bonusReview(7))
        assertEquals("Review milestone bonus: 1 day of review in a row", en.bonusReview(1))
        assertEquals("每日幸运抽奖", zh.bonusLucky)
        assertEquals("Daily lucky draw bonus", en.bonusLucky)
    }

    @Test
    fun childAchievementsCopyDiffers() {
        val zh = AppStringsZh.childAchievements
        val en = AppStringsEn.childAchievements
        assertDiffers(
            zh.title to en.title,
            zh.myAchievements to en.myAchievements,
            zh.allUnlocked to en.allUnlocked,
            zh.keepGoing to en.keepGoing,
        )
    }

    @Test
    fun crossTableVocabularyPinned() {
        // 页标题复用孩子首页入口（不另建同值字段）
        assertEquals("积分明细", AppStringsZh.childHome.entryPointsDetailTitle)
        assertEquals("Points details", AppStringsEn.childHome.entryPointsDetailTitle)
        assertEquals("奖励商店", AppStringsZh.childHome.entryRewardStoreTitle)
        assertEquals("Reward Store", AppStringsEn.childHome.entryRewardStoreTitle)
        // 空态标题复用家长奖励商店管理（W3 同值）
        assertEquals("奖励商店还是空的", AppStringsZh.parentHome.setupRewardsMissing)
        assertEquals("The reward store is still empty", AppStringsEn.parentHome.setupRewardsMissing)
        // 取消复用公共表
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
    }
}
