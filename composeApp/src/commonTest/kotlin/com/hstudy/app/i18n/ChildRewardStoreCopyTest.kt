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
        )
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
