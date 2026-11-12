package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 阶段 2 W6 文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentPointsCopyTest/ChildScheduleCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b），英文 one/other 用 pointsWord 钉死；
 * 跨表同值钉死（T6 追加）。覆盖 W6 八张表；T2 先落心愿两页
 * （childWishGoal / parentWishGoal），T3 追加周报总览 / 成长报告两表
 * （weeklyTracker / growthReport），T4 追加证明审核 / 证明提交 / 照片查看三表
 * （parentProofReview / childProofCapture / photoViewer），T5 追加周报通知表
 * （weeklyReport，通知正文在发出时取词，见该表 KDoc）。
 *
 * 心愿两页的特殊面：`goal.name`/`goal.description` 是 canonical 存储值（3 个示例心愿由 W6 注册表
 * 译出、家长自建原样），表函数只负责包装句式，名字本身由页面包 [localizedPresetText] ——
 * 所以这里钉死的是"名字原样嵌入"的渲染结果，不假设名字有英文译文。
 */
class WishCopyTest {

    @Test
    fun childWishGoalCopyDiffers() {
        val zh = AppStringsZh.childWishGoal
        val en = AppStringsEn.childWishGoal
        assertDiffers(
            zh.title to en.title,
            zh.introTitle to en.introTitle,
            zh.introBody to en.introBody,
            zh.emptyTitle to en.emptyTitle,
            zh.emptyHint to en.emptyHint,
            zh.completedSection to en.completedSection,
            zh.primaryBadge to en.primaryBadge,
            zh.milestone75 to en.milestone75,
            zh.milestone50 to en.milestone50,
            zh.milestone25 to en.milestone25,
            zh.milestoneStart to en.milestoneStart,
            zh.estPending to en.estPending,
            zh.amountLabel to en.amountLabel,
            zh.allRemaining to en.allRemaining,
            zh.invalidAmount to en.invalidAmount,
            zh.confirmDeposit to en.confirmDeposit,
            zh.insufficient to en.insufficient,
        )
        // 静态标题两语逐字钉死：EN 用阶段 1 锁定术语 Wish Pool（不是 Wish Jar）
        assertEquals("🐷 心愿存钱罐", zh.title)
        assertEquals("🐷 Wish Pool", en.title)
        assertEquals("💡 什么是心愿存钱罐？", zh.introTitle)
        assertEquals("💡 What is the Wish Pool?", en.introTitle)
    }

    @Test
    fun childWishGoalInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childWishGoal
        val en = AppStringsEn.childWishGoal
        assertEquals("200 / 800 分", zh.progressValue(200, 800))
        assertEquals("200 / 800 points", en.progressValue(200, 800))
        assertEquals("1 / 1 point", en.progressValue(1, 1))
        assertEquals("还差 5 分达成心愿", zh.remaining(5))
        assertEquals("5 points to go", en.remaining(5))
        assertEquals("1 point to go", en.remaining(1))
        assertEquals("📅 照最近的速度，大约再攒 1 天就能实现啦！", zh.estDays(1))
        assertEquals("📅 At your recent pace, about 1 more day to go!", en.estDays(1))
        assertEquals("📅 照最近的速度，大约再攒 3 天就能实现啦！", zh.estDays(3))
        assertEquals("📅 At your recent pace, about 3 more days to go!", en.estDays(3))
        assertEquals("💰 存入积分（当前可用 12 分）", zh.depositButton(12))
        assertEquals("💰 Save points (12 available)", en.depositButton(12))
        // 庆祝文案带一个真实换行（原页面 "🎉 心愿达成！\n${goal.name}"）
        assertEquals("🎉 心愿达成！\n一个小玩具", zh.celebrate("一个小玩具"))
        assertEquals("🎉 Wish achieved!\n一个小玩具", en.celebrate("一个小玩具"))
        assertEquals("✅ 已存入 50 积分到「一个小玩具」", zh.depositSuccess(50, "一个小玩具"))
        assertEquals("✅ Saved 50 points into “一个小玩具”", en.depositSuccess(50, "一个小玩具"))
        assertEquals("✅ Saved 1 point into “一个小玩具”", en.depositSuccess(1, "一个小玩具"))
        assertEquals("已达成 · 共存入 200 分", zh.completedLine(200))
        assertEquals("Achieved · 200 points saved", en.completedLine(200))
        assertEquals("Achieved · 1 point saved", en.completedLine(1))
        assertEquals("💰 存入积分到「一个小玩具」", zh.depositDialogTitle("一个小玩具"))
        assertEquals("💰 Save points into “一个小玩具”", en.depositDialogTitle("一个小玩具"))
        assertEquals("当前可用积分：12 分\n还差 5 分达成心愿", zh.depositDialogInfo(12, 5))
        assertEquals("Available: 12 points\n5 points to go", en.depositDialogInfo(12, 5))
        assertEquals("Available: 1 point\n1 point to go", en.depositDialogInfo(1, 1))
    }

    @Test
    fun parentWishGoalCopyDiffers() {
        val zh = AppStringsZh.parentWishGoal
        val en = AppStringsEn.parentWishGoal
        assertDiffers(
            zh.title to en.title,
            zh.hint to en.hint,
            zh.emptyTitle to en.emptyTitle,
            zh.emptyHint to en.emptyHint,
            zh.samplesLoaded to en.samplesLoaded,
            zh.loadSamplesButton to en.loadSamplesButton,
            zh.addOwnHint to en.addOwnHint,
            zh.newWishContentDescription to en.newWishContentDescription,
            zh.savedMessage to en.savedMessage,
            zh.achievedBadge to en.achievedBadge,
            zh.dialogTitleNew to en.dialogTitleNew,
            zh.dialogTitleEdit to en.dialogTitleEdit,
            zh.nameLabel to en.nameLabel,
            zh.descLabel to en.descLabel,
            zh.targetPointsLabel to en.targetPointsLabel,
            zh.iconPickerLabel to en.iconPickerLabel,
        )
    }

    @Test
    fun parentWishGoalInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentWishGoal
        val en = AppStringsEn.parentWishGoal
        assertEquals("已删除「一个小玩具」", zh.removedMessage("一个小玩具"))
        assertEquals("Deleted “一个小玩具”", en.removedMessage("一个小玩具"))
        assertEquals("进行中 · 200/800分", zh.activeLine(200, 800))
        assertEquals("In progress · 200/800 points", en.activeLine(200, 800))
        assertEquals("In progress · 1/1 point", en.activeLine(1, 1))
    }

    @Test
    fun weeklyTrackerCopyDiffers() {
        val zh = AppStringsZh.weeklyTracker
        val en = AppStringsEn.weeklyTracker
        assertDiffers(
            zh.titleParent to en.titleParent,
            zh.titleChild to en.titleChild,
            zh.weeklyPointsLabel to en.weeklyPointsLabel,
            zh.totalBalanceLabel to en.totalBalanceLabel,
            zh.basicsTitle to en.basicsTitle,
            zh.enhanceTitle to en.enhanceTitle,
            zh.habitTitle to en.habitTitle,
            zh.dailySummaryTitle to en.dailySummaryTitle,
            zh.pointsRowLabel to en.pointsRowLabel,
            zh.noTasks to en.noTasks,
        )
    }

    /**
     * 周报总览页没有函数型字段（Ruling 20-b 意义上的"插值文案"），动态面只剩 [weekdayNames]：
     * 七个表头逐条双语钉死，顺序必须周一到周日（与页面 `dayIcons` 七枚一一对应）。
     * 页面侧的数字模板（`"+$weeklyPoints"`、`"⭐ $totalPoints"`、`"+$dayPoints"`）不属本表字段，
     * `task.title` 的渲染包装（[com.hstudy.app.i18n.localizedPresetText]）发生在组合期，
     * 本仓库没有 Compose UI 测试依赖，两者都无法在这里单测——前者由页面 diff 复核，后者归注册表测试。
     */
    @Test
    fun weeklyTrackerInterpolatedCopyPinsBothLanguages() {
        assertEquals(
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
            AppStringsZh.weeklyTracker.weekdayNames,
        )
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            AppStringsEn.weeklyTracker.weekdayNames,
        )
    }

    @Test
    fun growthReportCopyDiffers() {
        val zh = AppStringsZh.growthReport
        val en = AppStringsEn.growthReport
        assertDiffers(
            zh.title to en.title,
            zh.heading to en.heading,
            zh.subtitle to en.subtitle,
            zh.shareButton to en.shareButton,
            zh.shareHint to en.shareHint,
            zh.lifetimeLabel to en.lifetimeLabel,
            zh.streakLabel to en.streakLabel,
            zh.petLabel to en.petLabel,
            zh.todayTasksLabel to en.todayTasksLabel,
            zh.exchangesLabel to en.exchangesLabel,
            zh.wishesLabel to en.wishesLabel,
            zh.streakSubPositive to en.streakSubPositive,
            zh.streakSubZero to en.streakSubZero,
            zh.petSub to en.petSub,
            zh.todaySubAll to en.todaySubAll,
            zh.todaySubMore to en.todaySubMore,
            zh.exchangesSub to en.exchangesSub,
            zh.wishesSub to en.wishesSub,
        )
    }

    /**
     * 成长报告六个数值子模板 + 7 参分享文案逐字双语钉死。
     *
     * `reportText` 的宠物阶段名实参故意传原始中文串 `"🥚 宠物蛋"`：那 8 个阶段名属 W7
     * （计划 Ruling 3），本波原样嵌入，所以**两种语言**的分享文本里都会出现它——
     * 这条断言既是文档也是回归保护：若有人在本波顺手"译"了阶段名，这里会红。
     * 中文八行保留迁移前 `appendLine` 的尾随换行（分享文本中文态逐字节不变），英文无尾随换行。
     * 英文另钉一条 `streak = 1` 的整串（行内 `if (streak == 1) "day" else "days"` 的单数分支）。
     */
    @Test
    fun growthReportInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.growthReport
        val en = AppStringsEn.growthReport
        assertEquals("1 分", zh.lifetimeValue(1))
        assertEquals("300 分", zh.lifetimeValue(300))
        assertEquals("1 point", en.lifetimeValue(1))
        assertEquals("300 points", en.lifetimeValue(300))
        assertEquals("当前余额 300 分", zh.balanceSub(300))
        assertEquals("Current balance: 300 points", en.balanceSub(300))
        assertEquals("Current balance: 1 point", en.balanceSub(1))
        assertEquals("1 天", zh.streakValue(1))
        assertEquals("3 天", zh.streakValue(3))
        assertEquals("1 day", en.streakValue(1))
        assertEquals("3 days", en.streakValue(3))
        assertEquals("3/5 个", zh.todayValue(3, 5))
        assertEquals("3/5", en.todayValue(3, 5))
        assertEquals("1 次", zh.exchangesValue(1))
        assertEquals("3 次", zh.exchangesValue(3))
        assertEquals("1 time", en.exchangesValue(1))
        assertEquals("3 times", en.exchangesValue(3))
        assertEquals("2 个", zh.wishesValue(2))
        assertEquals("2", en.wishesValue(2))
        assertEquals(
            "📚 【HStudy 学习成长报告】\n" +
                "🌟 累计获得积分：300 分\n" +
                "🔥 当前连续打卡：3 天\n" +
                "🐾 学习宠物：🥚 宠物蛋\n" +
                "✅ 今日完成任务：3/5 个\n" +
                "🎁 已兑换奖励：2 次\n" +
                "🐷 已达成心愿：1 个\n" +
                "—— 每一份坚持都值得被看见 ❤️\n",
            zh.reportText(300, 3, "🥚 宠物蛋", 3, 5, 2, 1),
        )
        assertEquals(
            "📚 [HStudy Growth Report]\n" +
                "🌟 Total points earned: 300\n" +
                "🔥 Current check-in streak: 3 days\n" +
                "🐾 Study pet: 🥚 宠物蛋\n" +
                "✅ Tasks done today: 3/5\n" +
                "🎁 Rewards redeemed: 2\n" +
                "🐷 Wishes achieved: 1\n" +
                "— Every bit of persistence deserves to be seen ❤️",
            en.reportText(300, 3, "🥚 宠物蛋", 3, 5, 2, 1),
        )
        // 行内 `if (streak == 1) "day" else "days"` 的单数分支：其余实参与上一串相同，
        // 期望串只在第三行换成 "1 day"（整串钉死，避免只测到多元分支）。
        assertEquals(
            "📚 [HStudy Growth Report]\n" +
                "🌟 Total points earned: 300\n" +
                "🔥 Current check-in streak: 1 day\n" +
                "🐾 Study pet: 🥚 宠物蛋\n" +
                "✅ Tasks done today: 3/5\n" +
                "🎁 Rewards redeemed: 2\n" +
                "🐷 Wishes achieved: 1\n" +
                "— Every bit of persistence deserves to be seen ❤️",
            en.reportText(300, 1, "🥚 宠物蛋", 3, 5, 2, 1),
        )
    }

    @Test
    fun parentProofReviewCopyDiffers() {
        val zh = AppStringsZh.parentProofReview
        val en = AppStringsEn.parentProofReview
        assertDiffers(
            zh.title to en.title,
            zh.filterAll to en.filterAll,
            zh.emptyPending to en.emptyPending,
            zh.emptyAll to en.emptyAll,
            zh.statusPending to en.statusPending,
            zh.statusApproved to en.statusApproved,
            zh.statusRejected to en.statusRejected,
            zh.approvedMessage to en.approvedMessage,
            zh.rejectedMessage to en.rejectedMessage,
            zh.rejectButton to en.rejectButton,
            zh.approveButton to en.approveButton,
            zh.photoContentDescription to en.photoContentDescription,
        )
    }

    /**
     * 证明审核页三个函数型字段的双语钉死。`taskName` 的渲染包装不可在这里测：
     * 调用点是 `localizedPresetText(taskNames[id] ?: strings.taskFallback(id), displayLanguage)`
     * （组合期，无 Compose UI 测试依赖）；本测试只钉 [ParentProofReviewStrings.taskFallback]
     * 这一侧——它是**已本地化**的兜底文案，不经过注册表。
     * [ParentProofReviewStrings.noteLine] 的 note 是用户内容（`proof.note` 原样），两语都嵌同一串。
     */
    @Test
    fun parentProofReviewInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentProofReview
        val en = AppStringsEn.parentProofReview
        assertEquals("待审核 (3)", zh.filterPending(3))
        assertEquals("Pending (3)", en.filterPending(3))
        assertEquals("任务#7", zh.taskFallback(7L))
        assertEquals("Task #7", en.taskFallback(7L))
        assertEquals("备注: 很不错", zh.noteLine("很不错"))
        assertEquals("Note: 很不错", en.noteLine("很不错"))
    }

    @Test
    fun childProofCaptureCopyDiffers() {
        val zh = AppStringsZh.childProofCapture
        val en = AppStringsEn.childProofCapture
        assertDiffers(
            zh.title to en.title,
            zh.taskLabel to en.taskLabel,
            zh.takePhotoButton to en.takePhotoButton,
            zh.photoContentDescription to en.photoContentDescription,
            zh.emptyPhotoHint to en.emptyPhotoHint,
            zh.noteLabel to en.noteLabel,
            zh.needPhotoMessage to en.needPhotoMessage,
            zh.submittedMessage to en.submittedMessage,
            zh.submitButton to en.submitButton,
            zh.tipsTitle to en.tipsTitle,
            zh.tipsBody to en.tipsBody,
        )
    }

    /**
     * 证明提交页 11 个字段全是静态文案（无 lambda），所以 differs 已覆盖"有没有译"；
     * 这里再钉 4 条**逐字**双语串，覆盖容易被顺手的语序/标点改动破坏的整句
     * （标题、按钮、提交成功提示、底部说明）。
     */
    @Test
    fun childProofCaptureStaticsPinnedBothLanguages() {
        val zh = AppStringsZh.childProofCapture
        val en = AppStringsEn.childProofCapture
        assertEquals("📷 提交完成证明", zh.title)
        assertEquals("📷 Submit proof", en.title)
        assertEquals("📤 提交完成证明", zh.submitButton)
        assertEquals("📤 Submit proof", en.submitButton)
        assertEquals("✅ 证明已提交，等待父母审核", zh.submittedMessage)
        assertEquals("✅ Proof submitted — waiting for parent review", en.submittedMessage)
        assertEquals(
            "提交后父母会审核。证明用于留痕，积分请在完成打卡后等待家长审批到账。",
            zh.tipsBody,
        )
        assertEquals(
            "Parents will review it after submission. Proofs are kept for the record; " +
                "points arrive after your check-in is approved by a parent.",
            en.tipsBody,
        )
    }

    @Test
    fun photoViewerCopyDiffers() {
        val zh = AppStringsZh.photoViewer
        val en = AppStringsEn.photoViewer
        assertDiffers(
            zh.photoContentDescription to en.photoContentDescription,
            zh.closeContentDescription to en.closeContentDescription,
            zh.zoomHint to en.zoomHint,
        )
    }

    /** 照片查看页没有函数型字段，三条全钉死字面量（differs 之外再钉一遍译文本体）。 */
    @Test
    fun photoViewerStaticsPinnedBothLanguages() {
        val zh = AppStringsZh.photoViewer
        val en = AppStringsEn.photoViewer
        assertEquals("查看照片", zh.photoContentDescription)
        assertEquals("View photo", en.photoContentDescription)
        assertEquals("关闭", zh.closeContentDescription)
        assertEquals("Close", en.closeContentDescription)
        assertEquals("双指缩放查看", zh.zoomHint)
        assertEquals("Pinch to zoom", en.zoomHint)
    }

    @Test
    fun weeklyReportCopyDiffers() {
        val zh = AppStringsZh.weeklyReport
        val en = AppStringsEn.weeklyReport
        assertDiffers(
            zh.title to en.title,
            zh.body to en.body,
            zh.encourageLow to en.encourageLow,
            zh.encourageGood to en.encourageGood,
            zh.fallback to en.fallback,
        )
        // 阶段 1 术语表：周报 = Weekly report（与设置页 weeklyReportTitle 同词）
        assertEquals("每周学习周报", zh.title)
        assertEquals("Weekly report", en.title)
        assertEquals("本周学习周报已生成，来看看孩子的学习情况吧！", zh.body)
        assertEquals("Your child's weekly report is ready — take a look!", en.body)
    }

    /**
     * 周报通知正文的两语整串钉死（spec §8 发出时取词）。
     *
     * ZH [WeeklyReportStrings.summary] 是迁移前 androidMain 接收器 `buildString` 前两次 append 的
     * 拼接结果（逐个字符照录，` · ` 分隔符与全角冒号都在），所以这一串同时是"ZH 态通知正文
     * 逐字节不变"的回归保护。通知正文不是单个字段：接收器把它拼成
     * `summary(...) + encourageLow/Good`（两句之间没有额外分隔符，encourage 自带前导「。」/". "），
     * 这里把空周那一条**整条**钉出来（`focusMin == 0 && reviewCount == 0 && checkins == 0` 分支）；
     * 非空周拼接走同一条 `+ encourageGood` 路径，字符面由两条分句的断言间接覆盖。
     * 英文 one/other 由 [pointsWord] 在 lambda 内分支：15 分走 `points`，1 分走 `point`。
     */
    @Test
    fun weeklyReportSummaryPinsBothLanguages() {
        val zh = AppStringsZh.weeklyReport
        val en = AppStringsEn.weeklyReport
        assertEquals(
            "📊 孩子本周学习周报：打卡5次 · 专注120分钟 · 复习3道错题 · 获得15积分",
            zh.summary(5, 120, 3, 15),
        )
        assertEquals(
            "📊 Your child's week: 5 check-ins · 120 min focused · 3 mistakes reviewed · 15 points earned",
            en.summary(5, 120, 3, 15),
        )
        // 空周整条拼接（接收器 if 分支）：summary + encourageLow，两语各一。
        assertEquals(
            "📊 孩子本周学习周报：打卡0次 · 专注0分钟 · 复习0道错题 · 获得0积分。本周学习较少，多鼓励孩子加油！",
            zh.summary(0, 0, 0, 0) + zh.encourageLow,
        )
        assertEquals(
            "📊 Your child's week: 0 check-ins · 0 min focused · 0 mistakes reviewed · 0 points earned" +
                ". A lighter week — give your child some encouragement!",
            en.summary(0, 0, 0, 0) + en.encourageLow,
        )
        // 英文 one/other：只有积分触到单数分支（其余实参保持 0，不影响词形）。
        assertTrue(en.summary(0, 0, 0, 1).contains("1 point"), en.summary(0, 0, 0, 1))
    }

    /**
     * 跨表 pin（T6）：W6 两页（心愿存钱罐/心愿管理）复用的三个共享词逐字双语钉死。
     *
     * 这三对分别属于别的波次（W2 的 `parentTaskEdit.saveButton`、更早的
     * `common.cancelButton` / `common.deleteContentDescription`），W6 的页面只是**消费方**，
     * 不新建同值槽。钉在这里的用途是：若有人为了 W6 的语序顺手改掉共享词，这条会先红，
     * 而不是等到别的页面的守卫去发现。
     */
    @Test
    fun crossTableVocabularyPinned() {
        assertEquals("保存", AppStringsZh.parentTaskEdit.saveButton)
        assertEquals("Save", AppStringsEn.parentTaskEdit.saveButton)
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
        assertEquals("删除", AppStringsZh.common.deleteContentDescription)
        assertEquals("Delete", AppStringsEn.common.deleteContentDescription)
        // W6：心愿页标题与孩子首页入口共用功能名 Wish Pool（阶段 1 锁定术语）
        assertEquals("Wish Pool", AppStringsEn.childHome.entryWishBankTitle)
        assertTrue(AppStringsEn.childWishGoal.title.contains("Wish Pool"))
    }
}
