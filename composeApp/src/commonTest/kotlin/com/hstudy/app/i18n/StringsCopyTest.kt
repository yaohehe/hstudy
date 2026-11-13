package com.hstudy.app.i18n

import com.hstudy.app.ui.dt.strFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [englishTableContainsNoChineseLeftovers] 的显式豁免表：语言的**自称词**（endonym）。
 *
 * 为什么"中文"可以出现在英文文案表里：语言选项在两种 locale 下都写作该语言自己的名字
 * （中文 / English），不随界面语言翻译——这是语言切换控件的通行惯例（iOS/Android 系统设置、
 * 各家 App 均如此），否则切到英文的用户看到的是 "Chinese"，反而不容易认出自己在选哪一种语言，
 * 而且 "English/中文" 一对里混进译名会破坏"两项都是自称"的对称性。
 * 该同值由 [languageOptionLabelsAreEndonymsInBothLocales] 用等值断言钉死，
 * 两个自称词字段也因此刻意不进 assertDiffers 漏译清单（见 Ruling 6）。
 *
 * 维护规则：只登记确属自称词的 token。**不要**为了跑绿把漏译字段加进来，
 * 也**不要**把 SettingsStringsEn.languageZh 顺手译成 "Chinese" 来消除本豁免（见 SettingsStrings.kt 头注释）。
 * 完整性没有被削弱：新增字段直接抄中文进英文表仍会被整表扫描抓到（已用变异测试证明：
 * 把 languageSystem 的 EN 值改成"跟随系统"后，本断言是唯一失败的断言）。
 */
private val EN_TABLE_ENDONYMS = setOf("中文")

/**
 * 英文**静态**文案里合法提到积分（`point` / `points`）的字段全集（`分组.字段名`）。
 *
 * 为什么需要这份清单：[pomodoroAndFocusMinuteUnitsAreMinutesNotPoints] 那两条清单
 * （"时长字段的英文不得有 points"与"该提积分的字段清单"）都是**开放枚举**——
 * 以后新增一条时长字段，把 `min` 误译成 `points`，只要它没被加进任何一份清单，
 * 两条断言都看不见它，守卫形同装饰（Task 7b 第 1 轮审查第 5 条）。
 * 本清单配合 [englishPointsMentionsAreAClosedSetBothDirections] 把方向反过来：
 * **从整表渲染串里算出**到底哪些静态值写着 point，再断言它与这份声明**集合相等**（两个方向都要成立）。
 *
 * 覆盖范围与其上限（务必先读再改）：
 * - 只有 `data class` 的静态 String 字段能进这份清单，函数型字段的返回值不在 `toString()` 里
 *   （Ruling 20-b 的老盲区），那一部分仍靠逐字段字面量断言，见 [pointsAndMinutesSharingOneLineUseSeparateSlots]。
 * - 因此**新增静态字段**（含时长字段）一定被本断言点名：要么它不提 points（无事），
 *   要么它提了 points 却没登记 → 红；登记了却不提 → 也红。
 */
private val EN_STATIC_POINTS_MENTIONS = setOf(
    "common.pointsUnit",            // 单位标签本身：`分` = points（Task 6 的同形异义对）
    "role.brandTitle",              // "Study Points & Rewards"（§11 品牌行）
    "role.parentDesc",              // "… Manage points …"（名词）
    "role.childDesc",               // "… Earn points …"（名词）
    "pomodoro.pointsRulesTitle",    // "💡 Points rules"（名词，不接数值）
    "pomodoro.pointsLadderNote",    // "15 min +3 points …"（同句两种 `分`，第 4 处词形都出自 pointsWord）
    "focus.dailyPracticeHint",      // "… you earn points too!"（名词）
    // ---- Task 9 孩子首页新增 14 条。英文原文逐条留在行尾注释里：下一个改译文的人
    // 看得见自己删掉的是哪一条（白名单会过期，这正是这条守卫的用途，不是装饰）。
    // 注意本页还有一类**不该**提积分的字段：scoreUnit（评分单位 pts），见 childHomeScoreUnitIsNotPoints。
    "childHome.excellentBonusMessage",       // "🌟 Great study today! Bonus +2 points!"
    "childHome.claimExcellentBonusButton",   // "🌟 Claim the +2 points bonus"
    "childHome.mistakesDueNote",             // "… it earns points too!"
    "childHome.myApprovalRequestsTitle",     // "📝 My point requests"（§11 积分 = Points）
    "childHome.weeklyChallengeClaimMessage", // "🏅 Challenge complete! +5 points"
    "childHome.entryStartCheckinSubtitle",   // "Complete tasks to earn points"
    "childHome.entryMyPetSubtitle",          // "Earn points to feed it and let it grow"
    "childHome.step4Subtitle",               // "… study analysis and points details"
    "childHome.entryLuckyDrawSubtitle",      // "Draw every day to win points"
    "childHome.entryPointsDetailTitle",      // "Points details"
    "childHome.entryPointsDetailSubtitle",   // "View your points history"
    "childHome.entryRewardStoreSubtitle",    // "Trade points for rewards"
    "childHome.entryWishBankSubtitle",       // "Save points toward a big wish"
    "childHome.earnPointsForRewardsNote",    // "Finish tasks, earn points, …"
    // ---- Task 10 家长首页 / 数据看板新增 11 条（英文原文逐条留在行尾注释里）。
    // 两页各有一类**不该**提积分的 `分`：parentDashboard.scoreUnit（评分 pts）与
    // parentDashboard.focusMinutesValue / childPerformanceLine 里的分钟，见
    // parentDashboardScoreUnitIsNotPoints 与 parentDashboardDurationUnitComesFromCommon。
    "parentHome.menuTemplateTitle",           // "Points templates"（§11 积分 = Points，功能入口名）
    "parentHome.menuPointsTitle",             // "Points management"
    "parentHome.menuApprovalTitle",           // "Approve point requests"（§11 家长审核 = Approval）
    "parentHome.menuApprovalSubtitle",        // "Review the point requests your child sent"
    "parentHome.menuWeeklyReportSubtitle",    // "Weekly check-ins and points summary"（§11 周报 = Weekly Report）
    "parentDashboard.totalPointsLabel",       // "Current total points"
    "parentDashboard.comparePointsLabel",     // "⭐ Points earned"
    "parentDashboard.trendTitle",             // "📈 Points earned over 7 days"
    "parentDashboard.weakAreaNoTasksNote",    // "… apply a points template or add one by hand"
    "parentDashboard.recentChangesTitle",     // "Recent points activity"
    "parentDashboard.changeFallbackLabel",    // "Points change"（record.note 为空时的兜底显示，不进存储）
    // ---- Task 11 家长设置页新增 4 条（英文原文逐条留在行尾注释里）。
    // 本页唯一一枚裸 `分`（:275）是**分钟**，走 CommonStrings.minuteLabel，不在这份清单里，
    // 见 settingsDurationUnitComesFromCommon；本页没有评分单位（pts）与积分单位裸槽出场。
    "settings.passwordMissingNote",           // "…can't change points or task rules on their own."
    "settings.weeklyReportNote",              // "…check-ins, focus, review and points — no need to open the app."
    "settings.backupNote",                    // "Back up your child's study data — points, mistakes, …"
    "settings.progressPointsLabel",           // "⭐ Total points"（§11 积分 = Points）
    // ---- W1 Task 3 错题详情页新增 5 条（英文原文逐条留在行尾注释里）。
    "mistakeDetail.gotItMessage",            // "🎉 Right! +3 points submitted for parent approval"
    "mistakeDetail.missedMessage",           // "💪 +1 point submitted for approval — …"
    "mistakeDetail.variationPromptsEnglish", // "What grammar or vocabulary point …?" / "… a sentence with this point?"
    "mistakeDetail.variationDoneMessage",    // "💡 Variation complete — +3 points submitted for parent approval"
    "mistakeDetail.variationButton",         // "💡 Complete variation thinking (+3 points)"
    // ---- W2 Task 2 任务编辑页新增 5 条（英文原文逐条留在行尾注释里）。
    "parentTaskEdit.basePointsLabel",         // "Base points"
    "parentTaskEdit.bonusPointsLabel",        // "Bonus points"
    "parentTaskEdit.difficultyNoteAdvanced",  // "Advanced tasks earn ×1.2 points"
    "parentTaskEdit.difficultyNoteChallenge", // "Challenge tasks earn ×1.5 points — …"
    "parentTaskEdit.difficultyNoteNormal",    // "Normal difficulty, scored at base points"
    // ---- W2 Task 3 布置任务页新增 1 条（英文原文逐条留在行尾注释里）。
    "parentAssignTask.pointsLabel",           // "Points"
    // ---- W2 Task 4 积分模板页新增 1 条（英文原文逐条留在行尾注释里）。
    "parentTaskTemplate.title",               // "📋 Point templates"（§11 积分 = Points）
    // ---- W3 Task 1 积分管理页新增 6 条（英文原文逐条留在行尾注释里）。
    "parentPoints.adjustContentDescription",  // "Adjust points"
    "parentPoints.taskRankingTitle",          // "📋 Task points ranking (last 7 days)"
    "parentPoints.allRecordsTitle",           // "All point records"
    "parentPoints.emptyRecords",              // "No point records yet"
    "parentPoints.adjustDialogTitle",         // "Adjust points manually"
    "parentPoints.adjustPointsLabel",         // "Points (positive = add, negative = deduct)"
    // ---- W3 Task 2 积分审核页新增 5 条（英文原文逐条留在行尾注释里）。
    "parentApproval.title",                   // "📋 Point approvals"
    "parentApproval.emptyPendingTitle",       // "No point requests to review"
    "parentApproval.emptyPendingNote",        // "Point requests appear here after your child completes study activities"
    "parentApproval.alreadyProcessedMessage", // "Already handled — points were not added again"
    "parentApproval.statusRevoked",           // "Revoked (points removed)"
    // ---- W3 Task 3 奖励编辑页新增 2 条（英文原文逐条留在行尾注释里）。
    "parentRewardEdit.presetHint",            // "… adjust their point costs there …"
    "parentRewardEdit.costLabel",             // "Points required"
    // ---- W3 Task 4 奖励商店管理页新增 1 条（英文原文逐条留在行尾注释里）。
    "parentRewardManage.emptyHint",           // "… adjust their point costs one by one."
    // ---- W4 Task 1 奖励商店页新增 1 条（英文原文逐条留在行尾注释里）。
    "childRewardStore.insufficientPoints",    // "Not enough points yet — keep going! 💪"
    // ---- W4 Task 2 每日幸运抽奖页新增 2 条（英文原文逐条留在行尾注释里）。
    "childLuckyDraw.subtitle",                // "One draw per day — win bonus points at random"
    "childLuckyDraw.infoPool",                // "The prize pool holds 1–5 points — small prizes are more likely, and big wins are pure luck!"
    // ---- W4 Task 3 积分明细页新增 3 条（英文原文逐条留在行尾注释里）。
    "childPoints.balanceTitle",               // "Total points balance"
    "childPoints.recordsTitle",               // "Point history"
    "childPoints.emptyRecords",               // "No point records yet — go check in!"
    // ---- W5 Task 2 每日目标页新增 1 条（英文原文逐条留在行尾注释里）。
    "childDailyTarget.allDoneBonus",          // "🎉 All goals achieved! Bonus +10 points (awaiting approval)"
    // ---- W6 Task 2 心愿两页新增 7 条（英文原文逐条留在行尾注释里）。
    // 简短说明与 brief 的笔误：brief 只列了 insufficient / targetPointsLabel 两条，
    // 但闭集是从整表静态英文**算出来**的（见上），这七条都含 `point(s)`，少登记一条都会红。
    "childWishGoal.introBody",                // "Set a big goal and save your points into it bit by bit …"
    "childWishGoal.milestoneStart",           // "🌱 First step taken — keep saving points!"
    "childWishGoal.estPending",               // "📅 Once you start earning points, …"
    "childWishGoal.insufficient",             // "Not enough points to save"
    "childWishGoal.invalidAmount",            // "Not enough points or invalid amount"
    "parentWishGoal.hint",                    // "… Your child saves points into it step by step …"
    "parentWishGoal.targetPointsLabel",       // "Target points"
    // ---- W6 Task 3 周报总览 + 成长报告新增 6 条（英文原文逐条留在行尾注释里）。
    // brief 只列了 4 条（weeklyPointsLabel / totalBalanceLabel / pointsRowLabel / lifetimeLabel），
    // 漏掉 dailySummaryTitle 与 petSub——闭集是从整表静态英文**算出来**的（见上），
    // 少登记一条都会红（与 T2 的 7 条同型）。两表其余静态英文都不提积分；`weekdayNames` 是
    // List 字段（toString 里的 `[Mon, …]` 不含 point），六个数值子模板与 reportText 是函数型
    // 字段，按 Ruling 20-b 由 WishCopyTest 逐字钉住。
    "weeklyTracker.weeklyPointsLabel",        // "Points earned this week"
    "weeklyTracker.totalBalanceLabel",        // "Total points balance"
    "weeklyTracker.dailySummaryTitle",        // "📊 Daily points summary"
    "weeklyTracker.pointsRowLabel",           // "💰 Points"
    "growthReport.lifetimeLabel",             // "Total points earned"
    "growthReport.petSub",                    // "The more points you earn, the stronger the pet"
    // ---- W6 Task 4 证明审核 + 证明提交 + 照片查看新增 3 条（英文原文逐条留在行尾注释里）。
    // brief 只列了 parentProofReview 的两条，漏掉 childProofCapture.tipsBody（"… points arrive after
    // your check-in is approved …"）——闭集是从整表静态英文**算出来**的（见上），少登记一条都会红
    // （与 T2 的 7 条、T3 的 6 条同型）。照片查看表三条都是无积分词的短句（View photo / Close /
    // Pinch to zoom）；证明审核的三条函数型字段（filterPending / taskFallback / noteLine）与证明提交
    // 整表其余静态英文都不提积分，前者按 Ruling 20-b 由 WishCopyTest 逐字钉住。
    "parentProofReview.approvedMessage",      // "✅ Approved (points are issued via task check-in approval)"
    "parentProofReview.approveButton",        // "Approve (+5 points)"（迁移前「通过 (+5分)」照录）
    "childProofCapture.tipsBody",             // "… Proofs are kept for the record; points arrive after …"
    // ---- W7 Task 2 宠物页 + 预览卡新增 4 条（英文原文逐条留在行尾注释里）。
    // brief 猜了 statusHungry / moodHungry / careTip，但实际三条都不含 `point`
    // （"… please feed me!" / "Tip: … visit often!"）；闭集是从整表静态英文**算出来**的
    // （见上），最终命中 4 条。notEnoughPoints 与 feedButton/playButton/stageUnlock/scenePrice
    // 等是函数型字段（Ruling 20-b），不在本清单。
    "pet.shopHint",                           // "Spend points to give your pet a beautiful home!"
    "pet.statusCritical",                     // "😵 I can't hold on… please save me with points (feed/play)!"
    "pet.statusBored",                        // "😔 I'm so bored… spend some points playing with me!"
    "pet.moodBored",                          // "😔 I'm so bored… spend some points playing with me!"
    // ---- W8 Task 3 效率报表页新增 1 条。本表其余静态英文都不提积分；四个指标行的值/单位与
    // 学习建议是函数型字段（Ruling 20-b），由 W8CopyTest.efficiencyReportInterpolatedPinsBothLanguages
    // 逐字钉住——其中 scoreUnit 是评分 pts、focusUnit 是分钟 min，都不含 point。
    "efficiencyReport.statPointsLabel",       // "Total points earned"
)

/**
 * Task 11：家长设置页 `SettingsStrings` 里**必须进 ZH≠EN 穷举清单**的静态字段名（57 个）。
 *
 * 为什么把字段名单独做成一份**集合**而不是只写一长排 `zh.x to en.x`：
 * [settingsCopyDiffers] 里那份手写清单是**开放枚举**——Task 9/10 的教训（见
 * [EN_STATIC_POINTS_MENTIONS] 头注释）是"新增字段没进清单，守卫就看不见它"。
 * 这里把 [staticCopyValues] 从表里**算出来**的键集与这份声明做集合相等，两个方向都红：
 * 加了字段没登记 → 红；登记了却删掉/改名 → 也红。
 * 两个自称词 [SettingsStrings.languageZh] / [SettingsStrings.languageEn] 按 Ruling 6 **不在**清单里
 * （它们故意同值），由 [languageOptionLabelsAreEndonymsInBothLocales] 保护；
 * 11 个函数型字段进不了 `toString()`（Ruling 20-b），由
 * [settingsInterpolatedCopyPinsBothLanguages] 逐字钉住。
 */
private val SETTINGS_STATIC_DIFFERS_FIELDS = setOf(
    // Task 5 的语言分区（languageZh / languageEn 两个自称词除外）
    "languageTitle", "languageSystem", "languageNote", "partialTranslationNote",
    // 顶栏与家长密码
    "pageTitle", "passwordCardTitle", "passwordSetNote", "passwordMissingNote",
    "changePasswordButton", "setPasswordButton", "clearPasswordButton",
    "passwordClearedMessage", "passwordSetMessage",
    // 密码对话框
    "passwordDialogEditTitle", "passwordDialogSetTitle", "newPasswordLabel",
    "confirmPasswordLabel", "passwordTooShortError", "passwordMismatchError", "passwordOkButton",
    // 每周学习周报
    "weeklyReportTitle", "weeklyReportNote", "weeklyReportOnMessage", "weeklyReportOffMessage",
    // 数据备份与恢复
    "backupCardTitle", "backupNote", "backupButton", "backupRunningLabel", "restoreButton",
    "backupSuccessMessage", "restoreDialogTitle", "restoreDialogNote", "restoreConfirmButton",
    "restoreSuccessMessage", "restartAppNote",
    // 家长远程控制
    "remoteControlTitle", "remoteControlNote", "howToTitle", "pushStepNote", "viewStepNote",
    "transportNote", "pushSettingsButton", "viewProgressButton", "processingLabel",
    "exportChooserTitle", "settingsExportedMessage", "progressImportedMessage",
    // 孩子进度卡片
    "progressPointsLabel", "progressStreakLabel", "progressFocusLabel", "progressCheckinLabel",
    "progressMistakesLabel", "progressTodayCheckinLabel",
    // 设备名称
    "deviceNameLabel", "deviceNamePlaceholder", "saveDeviceNameButton", "deviceNameFallbackLabel",
)

/**
 * 把聚合根的 `toString()` 拆成 `分组.字段名 -> 静态 String 值` 对（Task 7b 第 1 轮审查第 5 条的机器）。
 *
 * 为什么用深度扫描而不是正则 `[^)]*`：英文文案里本来就写着括号
 * （`"🎯 Focus training · Schulte grid (warm-up before focusing)"`），
 * 非贪婪正则会在那儿断掉；同理"顶层逗号"也不能简单按逗号切——英文句子里就有逗号
 * （`"Tap the numbers in order, 1→25, as fast as you can…"`），
 * 所以只承认**后面紧跟 `字段名=`** 的那个逗号是分隔符。
 * 函数型字段的值是 lambda 句柄（`…$$Lambda/0x…@…`），不含返回值，按既有约定剔除。
 */
private fun staticCopyValues(root: Any): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    fun walk(prefix: String, body: String) {
        for (part in topLevelSegments(body)) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val name = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim()
            val nested = Regex("""^[A-Za-z][A-Za-z0-9_]*Strings\(.*\)$""").matches(value)
            if (nested) {
                walk(name, value.substring(value.indexOf('(') + 1, value.length - 1))
            } else if (!value.contains("Lambda")) {
                out += (if (prefix.isEmpty()) name else "$prefix.$name") to value
            }
        }
    }
    val rendered = root.toString()
    walk("", rendered.substring(rendered.indexOf('(') + 1, rendered.lastIndexOf(')')))
    return out
}

/** 按"深度 0 且后面紧跟 `字段名=`"的逗号切分（见 [staticCopyValues] 的理由）。 */
private val NEXT_FIELD_AT_COMMA = Regex("""\s*[A-Za-z][A-Za-z0-9_]*=""")

private fun topLevelSegments(text: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '(' -> { depth++; current.append(c) }
            c == ')' -> { depth--; current.append(c) }
            // 用 matchAt（在那个位置起匹配）而不是 matches（整串匹配）：
            // 后者要求把 substring(i+1) 全部吃完，任何带 `=` 之外的尾巴都会让它恒为 false。
            c == ',' && depth == 0 && NEXT_FIELD_AT_COMMA.matchAt(text, i + 1) != null -> {
                out.add(current.toString()); current.setLength(0)
            }
            else -> current.append(c)
        }
        i++
    }
    if (current.isNotEmpty()) out.add(current.toString())
    return out
}

/**
 * 英文文案表漏译守卫（Task 4-11 复制本文件的模板）。
 *
 * 两条断言互补，缺一不可：
 * - [englishTableContainsNoChineseLeftovers]：整表扫描，不依赖任何手工维护的字段清单，
 *   因此"新增字段直接把中文字面量抄进英文表"也会被抓到（豁免表见 [EN_TABLE_ENDONYMS]，只放自称词）；
 * - [roleCopyDiffersBetweenLanguages] / [settingsLanguageCopyDiffers]：逐字段 ZH≠EN，
 *   报错信息能点名是哪条中文没译。
 *
 * 已知上限（评审指出）：data class 的 `toString()` 打印函数型字段本身（本机实测是 lambda 对象的
 * `类名$$Lambda/0x…@哈希`），但**不含它们的返回值**，所以整表扫描对插在函数里的中文是瞎的
 * （Task 6 的 CommonStrings 起出现，Task 7 起大量出现）。
 * 补偿办法是硬性约定：**每个函数型字段都必须有显式双语断言，且期望串逐字打出来**
 * （见 [commonInterpolatedCopyPinsBothLanguages] / [taskListInterpolatedCopyPinsBothLanguages]），
 * 不允许"调 lambda 再和 lambda 自己拼的串比"——那种断言对漏译和语序错都是自证通过的。
 * 该约定是计划全局约束里的 Ruling 20-b（审查闸门强制项：任务审查必须逐字段列出 lambda 与
 * 其断言位置，漏一个即 spec ❌），不是可选加固；类型系统层面不做 `Copy1` 之类的值类型改造。
 *
 * 另有一类 assertDiffers 也抓不到的错：**同形异义**字段的英文值互换（见 [commonUnitLabelsPinnedByLiteral]）。
 */
class StringsCopyTest {
    @Test
    fun roleCopyDiffersBetweenLanguages() {
        val zh = AppStringsZh.role
        val en = AppStringsEn.role
        assertDiffers(
            zh.brandTitle to en.brandTitle,
            zh.tagline to en.tagline,
            zh.parentEntry to en.parentEntry,
            zh.parentDesc to en.parentDesc,
            zh.childEntry to en.childEntry,
            zh.childDesc to en.childDesc,
        )
    }

    /**
     * 整表兜底：英文聚合根 toString() 里不应出现任何汉字（U+4E00–U+9FFF）。
     *
     * 覆盖上面那份手工清单之外的所有 String 字段；标点/空白级差异不属于本断言的职责，
     * 由逐字段清单负责报错。
     *
     * 唯一的豁免是 [EN_TABLE_ENDONYMS]（语言自称词"中文"）：先从渲染串里剔除这些 token，
     * 再扫剩余汉字，因此新增字段的漏译不会被绕过；报错信息把残留字符直接打出来，便于定位。
     */
    @Test
    fun englishTableContainsNoChineseLeftovers() {
        var rendered = AppStringsEn.toString()
        EN_TABLE_ENDONYMS.forEach { token -> rendered = rendered.replace(token, "") }
        val leftovers = rendered.filter { it in '一'..'鿿' }
        assertFalse(
            leftovers.isNotEmpty(),
            "英文文案表中残留未翻译的中文字面量：$leftovers（已豁免自称词 $EN_TABLE_ENDONYMS）",
        )
    }

    /**
     * partialTranslationNote 只在**英文态**渲染（spec §7："英文态在该页附一行说明"）：
     * 这句话是给英文读者看的——他打开设置看到界面还是中文，需要知道"是还没适配"而不是"有 bug"。
     * 中文态读同一句是不通的，Task 11 之后更是直接变成假话，所以页面用
     * `LocalDisplayLanguage.current == DisplayLanguage.EN` 把它守卫起来。
     *
     * 本文件**无法**单测这条渲染分支：它发生在组合期，而仓库没有 Compose UI 测试依赖
     * （Task 12 之前的约定是不新增该依赖）。能被自动证明的只有下面这组 ZH≠EN 漏译守卫，
     * 以及整表扫描；守卫本身靠 ParentSettingsScreen 处的注释与代码路径承担。
     * ZH 侧的值仍然保留：data class 要求两份表字段对齐，Task 11 会复用同一字段。
     */
    @Test
    fun settingsLanguageCopyDiffers() {
        assertDiffers(
            AppStringsZh.settings.languageTitle to AppStringsEn.settings.languageTitle,
            AppStringsZh.settings.languageNote to AppStringsEn.settings.languageNote,
            AppStringsZh.settings.partialTranslationNote to AppStringsEn.settings.partialTranslationNote,
        )
    }

    @Test
    fun languageOptionLabelsAreEndonymsInBothLocales() {
        // 语言选项名不随界面语言翻译：中文/English 在两种语言下都这样显示
        assertEquals("中文", AppStringsZh.settings.languageZh)
        assertEquals("中文", AppStringsEn.settings.languageZh)
        assertEquals("English", AppStringsZh.settings.languageEn)
        assertEquals("English", AppStringsEn.settings.languageEn)
    }

    /**
     * 分区标题的装饰性 emoji 属于文案表，不属于页面代码。
     *
     * 该页既有四个分区标题都写成 "🔒 家长密码" 这种"emoji 在字面量内部"的形式，
     * Task 11 迁移这些标题时是按整串搬到表里做的；如果新代码把 emoji 拼在页面里
     * （`"🌐 ${strings.languageTitle}"`），就凭空多出一条页面内 UI 字面量，
     * 既违反"UI 文案只在文案表"的约定，也会让 Task 11 的整串替换模式对不上。
     * 本断言钉住约定：两种 locale 的 languageTitle 都以 🌐 开头，页面只渲染 strings.languageTitle。
     */
    @Test
    fun sectionTitleCarriesItsEmojiInsideTheTable() {
        assertTrue(
            AppStringsZh.settings.languageTitle.startsWith("🌐"),
            "ZH languageTitle 应自带 🌐，实际：${AppStringsZh.settings.languageTitle}",
        )
        assertTrue(
            AppStringsEn.settings.languageTitle.startsWith("🌐"),
            "EN languageTitle 应自带 🌐，实际：${AppStringsEn.settings.languageTitle}",
        )
    }

    /**
     * 公共控件（Task 6，`ui/common/CommonComponents.kt`）漏译守卫。
     *
     * 清单按值去重后是 5 条，但落成 6 个字段：`"分"` 在该文件出现两次而语义不同——
     * 一次是积分徽章的单位（PointsBadge:127），一次是分钟步进器的标签（HourMinuteStepper:159）。
     * 去重把它们合成一条，英文却必须分写（`points` vs `min`），所以拆成 pointsUnit / minuteLabel。
     * 教训：清单的"去重条数"是**下限**，不能当成字段数直接验收。
     *
     * 另：assertDiffers 只比较**同一字段的 ZH vs EN**，绝不跨字段比——
     * `pointsUnit` 与 `minuteLabel` 的中文都是"分"，若把 ZH(分) vs EN(min) 写成跨字段配对，
     * 两条断言会互相掩盖。**但同形异义本身还有第二种掩盖**：互换两个字段的英文值时，
     * 每个字段各自仍然 ZH≠EN，本清单完全无感。那一条由
     * [commonUnitLabelsPinnedByLiteral] 用字面量钉死。
     * 函数型字段（decreaseLabel / increaseLabel）不在此清单内，
     * 由 [commonInterpolatedCopyPinsBothLanguages] 覆盖。
     */
    @Test
    fun commonCopyDiffers() {
        val zh = AppStringsZh.common
        val en = AppStringsEn.common
        assertDiffers(
            zh.backContentDescription to en.backContentDescription,
            zh.pointsUnit to en.pointsUnit,
            zh.hourLabel to en.hourLabel,
            zh.minuteLabel to en.minuteLabel,
            // 第 1 轮审查第 8 条：三条通用按钮词从 FocusStrings 上收到这里（中文值逐字节未变）
            zh.cancelButton to en.cancelButton,
            zh.confirmButton to en.confirmButton,
            zh.deleteContentDescription to en.deleteContentDescription,
        )
    }

    /**
     * 通用按钮词的字面量钉死（Task 7b 第 1 轮审查第 8 条的上收配套）。
     *
     * 为什么不只是 [commonCopyDiffers] 的 ZH≠EN：`取消` 与 `确认` 是同一对对话框按钮，
     * 把两个英文值互换（cancelButton="Confirm" / confirmButton="Cancel"）后每条 ZH≠EN 仍成立，
     * 整表 CJK 扫描也照样干净——与 [commonUnitLabelsPinnedByLiteral] 面对的是**同一类**盲区，
     * 所以这里同样只钉字面量、不比较关系。
     *
     * 为什么这三条在 CommonStrings 而不是各自页面的表里：`取消` 在全仓约 12 个页面、
     * `删除` 约 10 个页面出现，Task 10 / 11 一定要再写一遍；各页一份就有两份英文可以漂移，
     * 那正是本表 [CommonStrings.pointsUnit] / [CommonStrings.minuteLabel] 那段"同形异义只能有一个出处"
     * 要防的事。上收只改出处，中文值一个字节都没动。
     */
    @Test
    fun commonDialogButtonsPinnedByLiteral() {
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
        assertEquals("确认", AppStringsZh.common.confirmButton)
        assertEquals("Confirm", AppStringsEn.common.confirmButton)
        assertEquals("删除", AppStringsZh.common.deleteContentDescription)
        assertEquals("Delete", AppStringsEn.common.deleteContentDescription)
    }

    /**
     * 同形异义单位的字面量钉死（Task 6 评审 Important 1）。
     *
     * [commonCopyDiffers] 只断言"同一字段的 ZH ≠ EN"，因此**对互换两个英文值完全无感**：
     * 把 `pointsUnit` 写成 "min"、`minuteLabel` 写成 "points"，四条 ZH≠EN 配对依旧全部成立，
     * 整表 CJK 扫描也照样干净（两边都是英文），但设备上会渲染出 `⭐ 12 min`，
     * 且分钟步进器下面挂着 `points` 标签。这类"两个字段中文同形、英文分工"的字段
     * 只能靠**逐字打出来的字面量**钉住，不能靠比较关系钉住——所以本测试不比较任何两个字段，
     * 只把每个字段各自钉在预期的那一个串上。
     *
     * 覆盖范围：pointsUnit / minuteLabel（中文同为 `分`）+ hourLabel（`hr` / `min` 同属步进器
     * 标签，互换是同一类错误，顺手一起钉）。已用变异测试证明本测试是唯一红的：
     * 交换 CommonStringsEn 的 pointsUnit 与 minuteLabel 后，其余测试全部通过。
     */
    @Test
    fun commonUnitLabelsPinnedByLiteral() {
        assertEquals("分", AppStringsZh.common.pointsUnit)
        assertEquals("points", AppStringsEn.common.pointsUnit)
        assertEquals("分", AppStringsZh.common.minuteLabel)
        assertEquals("min", AppStringsEn.common.minuteLabel)
        assertEquals("时", AppStringsZh.common.hourLabel)
        assertEquals("hr", AppStringsEn.common.hourLabel)
    }

    /**
     * 函数型字段的双语显式断言（spec §8.3）——本工程第一批。
     *
     * 期望串全部**手打钉死**，不是把 lambda 的返回值再拼一遍：
     * 若写成 `assertEquals("减少" + label, ...)`，那么把中文原文塞进英文表的漏译、
     * 或者英文语序写歪（"Decrease by hr 5"）都照样绿，等于没测。
     * 参数用页面真实传入的单位标签（中文态 "时"/"分"、英文态 "hr"/"min"），
     * 这样断言覆盖的就是设备上真正渲染出来的那两种组合。
     */
    @Test
    fun commonInterpolatedCopyPinsBothLanguages() {
        // 中文态必须逐字等于迁移前的插值串："减少$label" / "增加$label"
        assertEquals("减少时", AppStringsZh.common.decreaseLabel("时"))
        assertEquals("减少分", AppStringsZh.common.decreaseLabel("分"))
        assertEquals("增加时", AppStringsZh.common.increaseLabel("时"))
        assertEquals("增加分", AppStringsZh.common.increaseLabel("分"))
        // 英文态是 a11y 朗读文本，按英文语序写，不套用中文句式
        assertEquals("Decrease hr", AppStringsEn.common.decreaseLabel("hr"))
        assertEquals("Decrease min", AppStringsEn.common.decreaseLabel("min"))
        assertEquals("Increase hr", AppStringsEn.common.increaseLabel("hr"))
        assertEquals("Increase min", AppStringsEn.common.increaseLabel("min"))
    }

    /**
     * 孩子任务列表页（Task 8，`ui/child/ChildTaskListScreen.kt`）漏译守卫：16 个字面量 → 16 个字段，
     * 一一对应（本页没有 CommonComponents 那种"同形异义"合并，也没有一条字面量需要拆成两个字段）。
     *
     * 7 个函数型字段不在本清单里（`assertDiffers` 收 `Pair<String, String>`，函数不可比），
     * 由下面两个测试覆盖；整表 CJK 扫描同样看不见它们。
     */
    @Test
    fun taskListCopyDiffers() {
        val zh = AppStringsZh.taskList
        val en = AppStringsEn.taskList
        assertDiffers(
            zh.checkInTitle to en.checkInTitle,
            zh.filterAll to en.filterAll,
            zh.filterBasic to en.filterBasic,
            zh.filterEnhancement to en.filterEnhancement,
            zh.filterHabits to en.filterHabits,
            zh.undoButton to en.undoButton,
            zh.completedContentDescription to en.completedContentDescription,
            zh.mandatoryBadge to en.mandatoryBadge,
            zh.proofButton to en.proofButton,
        )
    }

    /**
     * 7 条插值文案的双语显式断言（spec §8.3）。期望串逐字手打，参数取页面真实传入的形状
     * （任务标题、积分整数、成就名列表），这样钉住的是设备上真正渲染出来的句子。
     *
     * 中文侧的期望串同时是"迁移前后字节相同"的证据：空格位置照抄源文件
     * （`扣回 $clawedBack 分` 有空格、`+$earned分` 没有），任何人改动中文措辞都会在这里红。
     */
    @Test
    fun taskListInterpolatedCopyPinsBothLanguages() {
        assertEquals(
            "↩️ 已撤销 每日阅读，扣回 5 分",
            AppStringsZh.taskList.revertedClawedBackMessage("每日阅读", 5),
        )
        assertEquals(
            "↩️ Check-in undone for Daily reading · 5 points removed",
            AppStringsEn.taskList.revertedClawedBackMessage("Daily reading", 5),
        )
        assertEquals(
            "↩️ 已撤销 每日阅读（尚未审批，无积分变动）",
            AppStringsZh.taskList.revertedPendingApprovalMessage("每日阅读"),
        )
        assertEquals(
            "↩️ Check-in undone for Daily reading (not approved yet, so no points changed)",
            AppStringsEn.taskList.revertedPendingApprovalMessage("Daily reading"),
        )
        assertEquals(
            "🏅 解锁成就：启航、周冠军！",
            AppStringsZh.taskList.achievementUnlockedMessage(listOf("启航", "周冠军")),
        )
        assertEquals(
            "🏅 Achievements unlocked: Blast Off, Weekly Champion!",
            AppStringsEn.taskList.achievementUnlockedMessage(listOf("Blast Off", "Weekly Champion")),
        )
        assertEquals("✅ 打卡成功！+5分待家长审核", AppStringsZh.taskList.checkInSuccessMessage(5))
        assertEquals(
            "✅ Checked in! +5 points pending approval",
            AppStringsEn.taskList.checkInSuccessMessage(5),
        )
        assertEquals("✅ 每日阅读 +5分（待审核）", AppStringsZh.taskList.revertCheckInPrompt("每日阅读", 5))
        assertEquals(
            "✅ Daily reading +5 points (pending approval)",
            AppStringsEn.taskList.revertCheckInPrompt("Daily reading", 5),
        )
        assertEquals("+5分", AppStringsZh.taskList.pointsEarnedLabel(5))
        assertEquals("+5 points", AppStringsEn.taskList.pointsEarnedLabel(5))
        assertEquals("奖励+3分", AppStringsZh.taskList.bonusPointsLabel(3))
        assertEquals("Reward +3 points", AppStringsEn.taskList.bonusPointsLabel(3))
    }

    /**
     * 英文 one/other 分支（spec §3 末句 + §8.3 的"覆盖英文 one/other"）。
     *
     * 本页 5 条把积分数值插进句子的文案都必须走单复数：`+1 points` 是英文语法错误，
     * 而 1 分在本工程是真实取值（`TaskEntity.points` 可为 1，`bonusPoints` 同理，
     * `undoTaskCheckIn` 返回值也可为 1），不是假想边界。
     * 同时钉住**中文没有单复数**：中文侧 1 与 5 同形，防止有人把英文的 if 复制回中文表。
     */
    @Test
    fun taskListEnglishPluralsCoverOneAndOther() {
        assertEquals("+1 point", AppStringsEn.taskList.pointsEarnedLabel(1))
        assertEquals("+2 points", AppStringsEn.taskList.pointsEarnedLabel(2))
        assertEquals("Reward +1 point", AppStringsEn.taskList.bonusPointsLabel(1))
        assertEquals("Reward +8 points", AppStringsEn.taskList.bonusPointsLabel(8))
        assertEquals(
            "✅ Checked in! +1 point pending approval",
            AppStringsEn.taskList.checkInSuccessMessage(1),
        )
        assertEquals(
            "✅ Daily reading +1 point (pending approval)",
            AppStringsEn.taskList.revertCheckInPrompt("Daily reading", 1),
        )
        assertEquals(
            "↩️ Check-in undone for Daily reading · 1 point removed",
            AppStringsEn.taskList.revertedClawedBackMessage("Daily reading", 1),
        )
        // 成就同理：一条用单数，两条用复数（上面那条已钉复数）
        assertEquals(
            "🏅 Achievement unlocked: Blast Off!",
            AppStringsEn.taskList.achievementUnlockedMessage(listOf("Blast Off")),
        )
        // 中文没有单复数变化：1 与 5 同形，别把英文的 if 复制回中文表
        assertEquals("+1分", AppStringsZh.taskList.pointsEarnedLabel(1))
        assertEquals("奖励+1分", AppStringsZh.taskList.bonusPointsLabel(1))
        assertEquals("✅ 打卡成功！+1分待家长审核", AppStringsZh.taskList.checkInSuccessMessage(1))
    }

    /**
     * 番茄钟组件（Task 7，`ui/common/PomodoroTimer.kt`）漏译守卫：去重 31 条字面量 → 31 个字段，
     * 一一对应（其中 3 条含插值，见 [pomodoroInterpolatedCopyPinsBothLanguages]）。
     *
     * `☕ 休息中` / `🍅 专注中` 各出现在两个位置（运行通知标题、圆环下方状态文字），
     * `结束` 出现在两个按钮（RUNNING / PAUSED）——**同一条字面量共用一个字段**，
     * 所以"出现 34 次 / 去重 31 条"落地成 31 个字段，而不是 34 个。
     */
    @Test
    fun pomodoroCopyDiffers() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        assertDiffers(
            zh.phaseBreak to en.phaseBreak,
            zh.phaseFocus to en.phaseFocus,
            zh.cheerHalfway to en.cheerHalfway,
            zh.cheerProudest to en.cheerProudest,
            zh.cheerAlmostDone to en.cheerAlmostDone,
            zh.cheerBrainWorking to en.cheerBrainWorking,
            zh.cheerEveryMinute to en.cheerEveryMinute,
            zh.breatheIn to en.breatheIn,
            zh.breatheOut to en.breatheOut,
            zh.phaseStartReady to en.phaseStartReady,
            zh.phaseBreakOver to en.phaseBreakOver,
            zh.breathingGuide to en.breathingGuide,
            zh.tipWalkAround to en.tipWalkAround,
            zh.tipLookFarAway to en.tipLookFarAway,
            zh.tipDrinkWater to en.tipDrinkWater,
            zh.tipBreathe to en.tipBreathe,
            zh.tipStretch to en.tipStretch,
            zh.tipListenMusic to en.tipListenMusic,
            zh.breakCardTitle to en.breakCardTitle,
            zh.breakCardNote to en.breakCardNote,
            zh.startFocusButton to en.startFocusButton,
            zh.skipBreathingButton to en.skipBreathingButton,
            zh.pauseButton to en.pauseButton,
            zh.stopButton to en.stopButton,
            zh.resumeButton to en.resumeButton,
            zh.skipBreakButton to en.skipBreakButton,
            zh.startNextRoundButton to en.startNextRoundButton,
            zh.restFirstButton to en.restFirstButton,
        )
    }

    /**
     * 专注统计页 + 专注力训练页（Task 7，`ui/focus/ChildFocusStatsScreen.kt` /
     * `ChildFocusTrainingScreen.kt`）漏译守卫：两页去重后 16 + 17 = 33 条字面量，跨页同值的
     * `🎯 专注力训练` 只算一个字段、`${todayMinutes}分钟` 与 `${totalMinutes}分钟` 共用一个字段，
     * 于是落成 **31 个字段，其中 11 条函数型**（函数型不在本清单里，见
     * [focusInterpolatedCopyPinsBothLanguages]）。
     */
    @Test
    fun focusCopyDiffers() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertDiffers(
            zh.statsTitle to en.statsTitle,
            zh.todayFocusLabel to en.todayFocusLabel,
            zh.totalFocusLabel to en.totalFocusLabel,
            zh.approxLabel to en.approxLabel,
            zh.trainingTitle to en.trainingTitle,
            zh.sessionsLabel to en.sessionsLabel,
            zh.gridBestLabel to en.gridBestLabel,
            zh.trainingBlurb to en.trainingBlurb,
            zh.last7DaysTitle to en.last7DaysTitle,
            zh.recordsTitle to en.recordsTitle,
            zh.emptyRecordsHint to en.emptyRecordsHint,
            zh.schulteTitle to en.schulteTitle,
            zh.schulteHowTo to en.schulteHowTo,
            zh.verdictExcellent to en.verdictExcellent,
            zh.verdictGood to en.verdictGood,
            zh.verdictPractice to en.verdictPractice,
            zh.newRecordBanner to en.newRecordBanner,
            zh.tryAgainButton to en.tryAgainButton,
            zh.dailyPracticeHint to en.dailyPracticeHint,
            zh.startTrainingButton to en.startTrainingButton,
        )
    }

    /**
     * PomodoroStrings 3 条插值的双语断言（Ruling 20-b：lambda 字段整表 CJK 守卫看不见，
     * 必须逐字钉死，期望串手打而不是把 lambda 再拼一遍）。
     *
     * 中文侧的期望串同时是"迁移前后逐字节相同"的证据：空格位置照抄源文件
     * （`剩余 ${min}分${sec}秒` 只在"剩余"后有空格、分/秒前后没有；`第 X/Y 次深呼吸` 同理），
     * 任何人把空格挪动一下都会在这里红。
     */
    @Test
    fun pomodoroInterpolatedCopyPinsBothLanguages() {
        assertEquals("剩余 12分30秒 · 息屏后仍会继续计时", AppStringsZh.pomodoro.remainTime(12, 30))
        assertEquals("12 min 30 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(12, 30))
        assertEquals("第 2/3 次深呼吸", AppStringsZh.pomodoro.deepBreathCount(2, 3))
        assertEquals("Deep breath 2 of 3", AppStringsEn.pomodoro.deepBreathCount(2, 3))
        assertEquals(
            "🎉 休息结束！已完成 4 个番茄钟，继续加油！",
            AppStringsZh.pomodoro.breakOverSummary(4),
        )
        assertEquals(
            "🎉 Break over! 4 Pomodoros done — keep going!",
            AppStringsEn.pomodoro.breakOverSummary(4),
        )
    }

    /**
     * FocusStrings 11 条插值的双语断言（同上，逐字手打）。
     *
     * 秒数类字段（[FocusStrings.secondsStatValue] / [FocusStrings.elapsedValue] /
     * [FocusStrings.personalBestValue] / [FocusStrings.finishedLine] /
     * [FocusStrings.trainingDoneSnackbar]）的参数类型是**已格式化好的字符串**而不是 Float，
     * 因为页面上的数值来自 `strFormat("%.1f", …)`；断言用 "12.3" 这种真实形状，
     * 保证钉住的就是设备上渲染出来的那一串。
     *
     * 四个"分钟/秒"字段的中文空格位置互不相同（`45分钟` / `45 分钟` / `12.3秒` / `37.5 秒`），
     * 是源文件本来就有的差异；合并任何两个都会让其中一处中文态输出变样，Ruling 20 的 (b) 不允许，
     * 所以这里按源文件逐字钉住四个字段。
     */
    @Test
    fun focusInterpolatedCopyPinsBothLanguages() {
        assertEquals("45分钟", AppStringsZh.focus.minutesStatValue(45))
        assertEquals("45 min", AppStringsEn.focus.minutesStatValue(45))
        assertEquals("45 分钟", AppStringsZh.focus.recordMinutesValue(45))
        assertEquals("45 min", AppStringsEn.focus.recordMinutesValue(45))
        assertEquals("3小时", AppStringsZh.focus.hoursStatValue(3))
        assertEquals("3 hr", AppStringsEn.focus.hoursStatValue(3))
        assertEquals("12次", AppStringsZh.focus.sessionsStatValue(12))
        assertEquals("12 sessions", AppStringsEn.focus.sessionsStatValue(12))
        assertEquals("12.3秒", AppStringsZh.focus.secondsStatValue("12.3"))
        assertEquals("12.3 s", AppStringsEn.focus.secondsStatValue("12.3"))
        assertEquals("37.5 秒", AppStringsZh.focus.elapsedValue("37.5"))
        assertEquals("37.5 s", AppStringsEn.focus.elapsedValue("37.5"))
        assertEquals("🏆 个人最佳：31.2 秒", AppStringsZh.focus.personalBestValue("31.2"))
        assertEquals("🏆 Personal best: 31.2 s", AppStringsEn.focus.personalBestValue("31.2"))
        assertEquals("🎯 累计训练 7 次", AppStringsZh.focus.totalSessionsLine(7))
        assertEquals("🎯 7 sessions so far", AppStringsEn.focus.totalSessionsLine(7))
        assertEquals("🎉 完成！用时 28.4 秒 · 错误 2 次", AppStringsZh.focus.finishedLine("28.4", 2))
        assertEquals("🎉 Done in 28.4 s · 2 mistakes", AppStringsEn.focus.finishedLine("28.4", 2))
        assertEquals(
            "🎯 训练完成！用时32.6秒，注意力提升了！",
            AppStringsZh.focus.trainingDoneSnackbar("32.6"),
        )
        assertEquals(
            "🎯 Training done in 32.6 s — your attention is warmed up!",
            AppStringsEn.focus.trainingDoneSnackbar("32.6"),
        )
        assertEquals("下一个要点：13", AppStringsZh.focus.nextTargetLabel(13))
        assertEquals("Next, tap 13", AppStringsEn.focus.nextTargetLabel(13))
    }

    /**
     * Ruling 20 里唯一一处**把单位从数字格式串内部拆出来**的位点的等价性证据（Task 7 评审 Important 1；
     * `scripts/i18n_template_diff.py` 的 strict 层把这一处当格式串拆分位点自动点名）：`ui/focus/ChildFocusTrainingScreen.kt:111`（迁移前）写
     * `strFormat("%.1f 秒", elapsedMs / 1000f)`，单位 ` 秒` 在格式串**内部**；
     * 迁移后写 `strings.elapsedValue(strFormat("%.1f", elapsedMs / 1000f))`，单位进了文案表。
     * 62 条模板里只有这一条不是整串搬进表里的，所以它必须有自己的断言而不是靠口头论证
     * （§4 的逐字节比对脚本把 `%.1f` 和 `${…}` 一起归一化，恰好会把它显示成"也相同"）。
     *
     * 等价性依赖两个事实，这里一个一条断言：
     * 1) [com.hstudy.app.ui.dt.strFormat] 对转换符之后的普通字符原样追加
     *    （`DateTimeCompat.kt:251-277`：处理完 `%.Nf` 后 `i = j + 1`，剩下的 ` 秒` 走 `sb.append(c)` 分支），
     *    所以"拆开写"和"整串写"输出同一串；
     * 2) [FocusStrings.elapsedValue] 的中文值就是那个带空格的 ` 秒`（已由
     *    [focusInterpolatedCopyPinsBothLanguages] 逐字钉住），即单位确实归文案表接管。
     *
     * 第三条是绝对值断言，防止前两条"两边一起变"仍然相等而自证通过（本文件的硬约定：期望串逐字打）。
     *
     * 为什么放在 StringsCopyTest 而不是新建一个 dt 测试类：这里钉的是**这次迁移没改变渲染输出**
     * 这条 Ruling 20 纪律，不是 strFormat 的通用格式行为（那是 DateTimeCompat 自己的测试职责，
     * 本任务禁止改它）；而且 Task 7 的覆盖测试就是 `--tests "*StringsCopyTest*"`，
     * 新类不在这条命令的覆盖范围内，评审闸门会看不见它。
     *
     * 反证（Task 11 修复轮实测，复现方式就写在下面两条事实里）：把断言 1 改成"strFormat 会丢掉转换符后面的文本"
     * 这一假命题、以及把 DateTimeCompat 的追加分支临时改掉，本测试都变红。
     */
    @Test
    fun elapsedValueKeepsTheSplitFormatPatternByteIdentical() {
        val elapsedMs = 37500L
        val before = strFormat("%.1f 秒", elapsedMs / 1000f)
        val after = AppStringsZh.focus.elapsedValue(strFormat("%.1f", elapsedMs / 1000f))
        assertEquals(strFormat("%.1f", elapsedMs / 1000f) + " 秒", before)
        assertEquals(before, after)
        assertEquals("37.5 秒", before)
        assertEquals("37.5 秒", after)
    }

    /**
     * 英文 one/other（spec §3 末句 + §8.3），并钉住**中文没有单复数**。
     *
     * 本页三个可数名词有真实取值 1：番茄钟个数（`completedPomodoros` 可为 1）、
     * 训练次数（`focusTrainingCount` 首完成即为 1）、错误次数（`mistakes` 可为 1），
     * 所以 "1 Pomodoros" / "1 sessions" / "1 mistakes" 是会真出现在设备上的语法错误。
     * 单位缩写（min / hr / s）按英文惯例不随数变化，故不带 if —— 一并断言，防止有人"顺手加复数"。
     */
    @Test
    fun pomodoroAndFocusEnglishPluralsCoverOneAndOther() {
        assertEquals(
            "🎉 Break over! 1 Pomodoro done — keep going!",
            AppStringsEn.pomodoro.breakOverSummary(1),
        )
        assertEquals(
            "🎉 休息结束！已完成 1 个番茄钟，继续加油！",
            AppStringsZh.pomodoro.breakOverSummary(1),
        )
        assertEquals("1 session", AppStringsEn.focus.sessionsStatValue(1))
        assertEquals("12次", AppStringsZh.focus.sessionsStatValue(12))
        assertEquals("1次", AppStringsZh.focus.sessionsStatValue(1))
        assertEquals("🎯 1 session so far", AppStringsEn.focus.totalSessionsLine(1))
        assertEquals("🎯 累计训练 1 次", AppStringsZh.focus.totalSessionsLine(1))
        assertEquals("🎉 Done in 28.4 s · 1 mistake", AppStringsEn.focus.finishedLine("28.4", 1))
        assertEquals("🎉 完成！用时 28.4 秒 · 错误 1 次", AppStringsZh.focus.finishedLine("28.4", 1))
        // 单位缩写没有单复数
        assertEquals("1 min", AppStringsEn.focus.minutesStatValue(1))
        assertEquals("1 hr", AppStringsEn.focus.hoursStatValue(1))
        assertEquals("1 min 1 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(1, 1))
    }

    /**
     * 同形异义单位（裁定 1）：**这个事实按"字段"说，不按"表"说**。
     *
     * Task 7 时这一条写的是"番茄钟表里没有积分单位"，那句话**只对 `common/PomodoroTimer.kt`
     * 那 31 条成立**；Task 7b 把宿主页面 `ui/focus/ChildPomodoroScreen.kt` 迁进同一张表之后，
     * 表里就同时有了两种 `分`（`每专注 5 分钟 = +1 分` 那一行是两种含义同行），
     * 所以整表 `toString()` 式的绝对断言在这里必然是假的，改成**逐字段点名**：
     * · 时长字段的英文只能是 min / hr / s，出现 points 就是把分钟译成了积分；
     * · 积分字段的英文词形只能出自 [pointsWord]，见 [pointsAndMinutesSharingOneLineUseSeparateSlots]。
     *
     * 与 Task 6 的反例对照：CommonComponents 里 `"分"` 既做积分单位又做分钟标签，所以那里必须拆
     * `pointsUnit` / `minuteLabel` 并靠字面量钉死；本表的时长字段没有一个接 [pointsWord]，
     * 英文一律用不随单复数变化的缩写 min / hr / s（与 [CommonStringsEn.minuteLabel] = "min" 同源）。
     */
    @Test
    fun pomodoroAndFocusMinuteUnitsAreMinutesNotPoints() {
        // 分钟/小时/秒：中文是时长，英文是缩写，两边都不含积分
        assertEquals("12 min 30 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(12, 30))
        assertEquals("90分钟", AppStringsZh.focus.minutesStatValue(90))
        assertEquals("90 min", AppStringsEn.focus.minutesStatValue(90))
        assertEquals("90 分钟", AppStringsZh.focus.recordMinutesValue(90))
        assertEquals("2小时", AppStringsZh.focus.hoursStatValue(2))
        assertEquals("2 hr", AppStringsEn.focus.hoursStatValue(2))
        assertEquals("60秒", AppStringsZh.focus.secondsStatValue("60"))
        // 组件本体（Task 7 的 31 条）里没有积分单位，逐条点名而不是扫整表 toString()
        assertFalse(
            AppStringsZh.pomodoro.remainTime(45, 30).contains("积分") ||
                AppStringsZh.pomodoro.phaseFocus.contains("积分") ||
                AppStringsZh.pomodoro.cheerEveryMinute.contains("积分") ||
                AppStringsZh.pomodoro.tipDrinkWater.contains("积分"),
            "组件文案里不该出现积分单位：PomodoroTimer 的 分 全是分钟",
        )
        val durationEnOutputs = listOf(
            AppStringsEn.pomodoro.remainTime(30, 30),
            AppStringsEn.focus.minutesStatValue(30),
            AppStringsEn.focus.recordMinutesValue(30),
            AppStringsEn.focus.hoursStatValue(5),
            AppStringsEn.focus.secondsStatValue("12.3"),
            AppStringsEn.focus.elapsedValue("12.3"),
            AppStringsEn.focus.personalBestValue("12.3"),
            // Task 7b 宿主页新增的三条时长渲染
            AppStringsEn.pomodoro.durationChip(45, AppStringsEn.common.minuteLabel),
            AppStringsEn.pomodoro.overHoursNote(2),
            AppStringsEn.pomodoro.todayFocusSummary(45, ""),
        )
        assertTrue(
            durationEnOutputs.none { it.contains(pointsWord(2)) },
            "时长字段的英文必须用 min/hr/s，不得混进 points：$durationEnOutputs",
        )
        // 提到 points 的字段是穷举出来的，不是"整表只允许一条"：Task 7 一条 + Task 7b 三条。
        // 穷举本身是**开放**清单（新加一条时长字段若把 min 误译成 points，这里看不见），
        // 那一条由 [englishPointsMentionsAreAClosedSetBothDirections] 从整表渲染串闭合掉。
        listOf(
            AppStringsEn.focus.dailyPracticeHint,
            AppStringsEn.pomodoro.focusDoneSnackbar("Free study", 5),
            AppStringsEn.pomodoro.pointsPerFocusRule(5, 1),
            AppStringsEn.pomodoro.pointsLadderNote,
        ).forEach {
            assertTrue(it.contains("point"), "本该提到积分的字段丢了 points：$it")
        }
        // 记忆训练页那条**不该**提积分（不加账），它以前在这份清单里，第 1 轮审查后挪到上面那条 assertFalse
        assertFalse(
            AppStringsEn.focus.memorySuccessLine(3, 3).contains("point", ignoreCase = true),
            "memorySuccessLine 的英文又承诺加分了：${AppStringsEn.focus.memorySuccessLine(3, 3)}",
        )
        // 组件本体里唯一含"水分"的一处与单位无关，别把它当漏译的单位字段改
        assertEquals("💧 喝杯水，补充水分", AppStringsZh.pomodoro.tipDrinkWater)
        assertEquals("💧 Have a glass of water", AppStringsEn.pomodoro.tipDrinkWater)
    }

    /**
     * 上面那两条开放清单的**闭合版**（Task 7b 第 1 轮审查第 5 条）。
     *
     * [pomodoroAndFocusMinuteUnitsAreMinutesNotPoints] 的两份清单都是"手工枚举"，
     * 对新字段无效：以后加一条时长字段、英文把 `min` 写成 `points`，只要没人把它加进清单，
     * 那两条断言一个都不会红。这里改成从**整表渲染串算出**"哪些静态英文值提到 point"，
     * 再与 [EN_STATIC_POINTS_MENTIONS] 做集合相等（两个方向都要成立）：
     * - 算出来、没登记 → 红（就是上面那种新字段；报错会把多出的字段名打出来）；
     * - 登记了、算不出来 → 红（字段被改名/删掉，或词形被换掉了，白名单不能留僵尸）。
     *
     * 上限写在 [EN_STATIC_POINTS_MENTIONS] 与 [staticCopyValues] 的注释里：函数型字段的返回值
     * 进不了 `toString()`，那一部分仍由逐字段字面量断言负责（Ruling 20-b），本测试不声称覆盖它们。
     * 匹配用 `(?i)\bpoint`（同时抓 Points / points / point），所以 `focus.memorySuccessLine`
     * 那条"英文不许承诺加分"的英文改动在这里也是同一个方向的（它不再出现在算出的集合里）。
     */
    @Test
    fun englishPointsMentionsAreAClosedSetBothDirections() {
        val staticValues = staticCopyValues(AppStringsEn).toMap()
        val mentionPattern = Regex("""(?i)\bpoint""")
        val actuallyMentioning = staticValues.filterValues { mentionPattern.containsMatchIn(it) }.keys

        // 方向二先单独报错，消息才能指出是"白名单里有、表里查无此串"
        EN_STATIC_POINTS_MENTIONS.forEach { label ->
            val value = staticValues[label]
                ?: error("白名单声称 $label 提积分，但英文表里已找不到这个静态字段。解析出的键: ${staticValues.keys.take(40)}")
            assertTrue(
                mentionPattern.containsMatchIn(value),
                "白名单声称 $label 提积分，实际值里没有：$value",
            )
        }
        // 方向一：集合相等（多一条 = 未披露的新字段，少一条 = 白名单需要一起改）
        assertEquals(
            EN_STATIC_POINTS_MENTIONS,
            actuallyMentioning,
            "英文静态文案里提到积分的字段集合变了。多出的是未披露的新字段（时长字段把 min 译成 points " +
                "正是这一类）；缺少的那条要么改回词形，要么把 EN_STATIC_POINTS_MENTIONS 一起改掉。" +
                "本断言的边界与理由见那份清单的注释。",
        )
    }

    /**
     * emoji 归文案表（Ruling 17 在本页的落点）：装饰性 emoji 必须在两种语言里都留在表内，
     * 页面代码不得再拼 `"🍅 ${strings.x}"` 这种串——那等于把 UI 文案放回页面，
     * 而且中文态会多出一份对不上的前缀。
     *
     * 用"第一个空格之前的片段"比较：本页所有带 emoji 的文案都是 `emoji + 空格 + 正文`，
     * 所以两边取到的正好是那枚 emoji（代理对整体比较，不截半）。
     */
    @Test
    fun emojiStaysInsideThePomodoroAndFocusTables() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        val pairs = listOf(
            zh.phaseBreak to en.phaseBreak,
            zh.phaseFocus to en.phaseFocus,
            zh.phaseStartReady to en.phaseStartReady,
            zh.phaseBreakOver to en.phaseBreakOver,
            zh.cheerHalfway to en.cheerHalfway,
            zh.cheerProudest to en.cheerProudest,
            zh.cheerAlmostDone to en.cheerAlmostDone,
            zh.cheerBrainWorking to en.cheerBrainWorking,
            zh.cheerEveryMinute to en.cheerEveryMinute,
            zh.tipWalkAround to en.tipWalkAround,
            zh.tipLookFarAway to en.tipLookFarAway,
            zh.tipDrinkWater to en.tipDrinkWater,
            zh.tipBreathe to en.tipBreathe,
            zh.tipStretch to en.tipStretch,
            zh.tipListenMusic to en.tipListenMusic,
            zh.breakCardTitle to en.breakCardTitle,
            // Task 7b 宿主页面（ui/focus/ChildPomodoroScreen.kt）新增的字段
            zh.pageTitle to en.pageTitle,
            zh.targetHeading to en.targetHeading,
            zh.lengthLockedNote to en.lengthLockedNote,
            zh.breathingSwitchTitle to en.breathingSwitchTitle,
            zh.pointsRulesTitle to en.pointsRulesTitle,
            zh.schulteEntryButton to en.schulteEntryButton,
            zh.memoryEntryButton to en.memoryEntryButton,
        )
        pairs.forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "行首 emoji 应两表一致")
        }
        assertTrue(
            AppStringsEn.pomodoro.breakOverSummary(2).startsWith("🎉"),
            "breakOverSummary 的 🎉 应在表内，实际：${AppStringsEn.pomodoro.breakOverSummary(2)}",
        )
        val focusZh = AppStringsZh.focus
        val focusEn = AppStringsEn.focus
        listOf(
            focusZh.statsTitle to focusEn.statsTitle,
            focusZh.trainingTitle to focusEn.trainingTitle,
            focusZh.verdictExcellent to focusEn.verdictExcellent,
            focusZh.verdictGood to focusEn.verdictGood,
            focusZh.verdictPractice to focusEn.verdictPractice,
            focusZh.newRecordBanner to focusEn.newRecordBanner,
            focusZh.tryAgainButton to focusEn.tryAgainButton,
            focusZh.startTrainingButton to focusEn.startTrainingButton,
            // Task 7b 记忆子页 + 每日计划子页
            focusZh.memoryTitle to focusEn.memoryTitle,
            focusZh.memoryMissPrompt to focusEn.memoryMissPrompt,
            focusZh.memoryRetryButton to focusEn.memoryRetryButton,
            focusZh.dailyPlanTitle to focusEn.dailyPlanTitle,
            focusZh.planAddedSnackbar to focusEn.planAddedSnackbar,
            focusZh.addPlanDialogTitle to focusEn.addPlanDialogTitle,
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "行首 emoji 应两表一致")
        }
        assertTrue(
            AppStringsEn.focus.emptyRecordsHint.endsWith("🍅"),
            "空状态提示的 🍅 留在句尾（与中文态同位置），实际：${AppStringsEn.focus.emptyRecordsHint}",
        )
        // 函数型字段的行首 emoji 同样在表内（Ruling 17 对 lambda 字段的落点，整表扫描看不见它们）
        listOf(
            AppStringsZh.pomodoro.goldenHourNowNote("早上8点") to AppStringsEn.pomodoro.goldenHourNowNote("8:00"),
            AppStringsZh.pomodoro.goldenHourGeneralNote("早上8点") to AppStringsEn.pomodoro.goldenHourGeneralNote("8:00"),
            AppStringsZh.pomodoro.focusDoneSnackbar("自由学习", 5) to AppStringsEn.pomodoro.focusDoneSnackbar("Free study", 5),
            AppStringsZh.focus.memoryBestLine(3) to AppStringsEn.focus.memoryBestLine(3),
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "lambda 的行首 emoji 应两表一致")
        }
    }

    /**
     * 番茄钟宿主页面（Task 7b，`ui/focus/ChildPomodoroScreen.kt`）漏译守卫。
     *
     * 清单：去重 32 条字面量 → **33 个新字段**，其中 10 个函数型（见
     * [pomodoroPageInterpolatedCopyPinsBothLanguages]），本清单只穷举其余 23 个 String 字段。
     * 32 → 33 的差额来自两处反向修正，都不是"多写一条"：
     * · `自由学习` 出现两处（预设项列表 + `focusTarget` 初值）、`${min}分` 出现两处（专注 chips /
     *   休息 chips），同值同义各**共用一个字段**（-2）；
     * · 第 145 行 `今日已专注 $todayFocusMin 分钟${if (…超N小时…)}` 一条字面量落成
     *   [PomodoroStrings.todayFocusSummary] + [PomodoroStrings.overHoursNote] 两个字段（+1），
     *   见该字段的 Ruling 20 披露。
     *
     * `已跳过` / `开启` 与 `专注:` / `休息:` 各自成对，是同一控件的两种状态/两个轴，
     * 没有同形异义风险（`分` 的同形异义处理在 [durationChipReusesTheCommonMinuteLabel]）。
     */
    @Test
    fun pomodoroPageCopyDiffers() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        assertDiffers(
            zh.pageTitle to en.pageTitle,
            zh.targetFreeStudy to en.targetFreeStudy,
            zh.targetReviewMistakes to en.targetReviewMistakes,
            zh.targetPreviewLesson to en.targetPreviewLesson,
            zh.targetFinishHomework to en.targetFinishHomework,
            zh.targetHeading to en.targetHeading,
            zh.focusLengthLabel to en.focusLengthLabel,
            zh.breakLengthLabel to en.breakLengthLabel,
            zh.lengthLockedNote to en.lengthLockedNote,
            zh.breathingSwitchTitle to en.breathingSwitchTitle,
            zh.breathingStateSkipped to en.breathingStateSkipped,
            zh.breathingStateOn to en.breathingStateOn,
            zh.pointsRulesTitle to en.pointsRulesTitle,
            zh.pointsLadderNote to en.pointsLadderNote,
            zh.lockScreenNote to en.lockScreenNote,
            zh.schulteEntryButton to en.schulteEntryButton,
            zh.memoryEntryButton to en.memoryEntryButton,
            zh.goldenPeriodEarlyMorning to en.goldenPeriodEarlyMorning,
            zh.goldenPeriodMorning to en.goldenPeriodMorning,
            zh.goldenPeriodMidday to en.goldenPeriodMidday,
            zh.goldenPeriodAfternoon to en.goldenPeriodAfternoon,
            zh.goldenPeriodEvening to en.goldenPeriodEvening,
            zh.goldenPeriodLateNight to en.goldenPeriodLateNight,
        )
    }

    /**
     * 番茄钟宿主页 10 条函数型字段的双语字面量断言（Ruling 20-b）。
     *
     * 参数取页面真实传入的形状：`goldenLabel` 是 [PomodoroStrings.goldenPeriodHour] 的产物
     * （中文态 `早上8点`、英文态 `Early morning · 8:00`），`todayFocusSummary` 的第二实参是
     * [PomodoroStrings.overHoursNote] 的产物或空串（`else ""` 那条分支同样是设备真实输出），
     * `durationChip` 的单位实参就是页面从 [CommonStrings.minuteLabel] 取到的那一个值。
     */
    @Test
    fun pomodoroPageInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        assertEquals(
            "🌟 现在正是你的黄金专注时段（早上8点），效率最高，抓紧专注！",
            zh.goldenHourNowNote("早上8点"),
        )
        assertEquals(
            "🌟 Right now is your golden focus window (Early morning · 8:00) — that's your best time, make it count!",
            en.goldenHourNowNote("Early morning · 8:00"),
        )
        assertEquals("💡 你的黄金专注时段是 早上8点，那时专注效率最高哦～", zh.goldenHourGeneralNote("早上8点"))
        assertEquals(
            "💡 Your golden focus window is Early morning · 8:00 — that's when you focus best.",
            en.goldenHourGeneralNote("Early morning · 8:00"),
        )
        // 第 145 行拆出来的两个字段：有注记 / 无注记两种都要钉（else "" 是真实分支）
        assertEquals("（超2小时）", zh.overHoursNote(2))
        assertEquals(" (over 2 hr)", en.overHoursNote(2))
        assertEquals("今日已专注 90 分钟（超1小时）", zh.todayFocusSummary(90, zh.overHoursNote(1)))
        assertEquals("You've focused 90 min today (over 1 hr)", en.todayFocusSummary(90, en.overHoursNote(1)))
        assertEquals("今日已专注 25 分钟", zh.todayFocusSummary(25, ""))
        assertEquals("You've focused 25 min today", en.todayFocusSummary(25, ""))
        assertEquals("完成 3 个番茄钟 🍅×3", zh.completedPomodorosLine(3))
        assertEquals("Completed 3 Pomodoros 🍅×3", en.completedPomodorosLine(3))
        assertEquals("专注「自由学习」25分钟", zh.focusRecordDescription("自由学习", 25))
        assertEquals("Focus on Free study for 25 min", en.focusRecordDescription("Free study", 25))
        assertEquals("🍅 「自由学习」专注完成！+5分已提交家长审核", zh.focusDoneSnackbar("自由学习", 5))
        assertEquals(
            "🍅 Focus on 'Free study' complete! +5 points sent for approval",
            en.focusDoneSnackbar("Free study", 5),
        )
        assertEquals("15分", zh.durationChip(15, AppStringsZh.common.minuteLabel))
        assertEquals("15 min", en.durationChip(15, AppStringsEn.common.minuteLabel))
        assertEquals("每专注 5 分钟 = +1 分（所有时长均有效）", zh.pointsPerFocusRule(5, 1))
        assertEquals("Every 5 min of focus = +1 point (every length counts)", en.pointsPerFocusRule(5, 1))
        assertEquals("早上8点", zh.goldenPeriodHour("早上", 8))
        assertEquals("Early morning · 8:00", en.goldenPeriodHour("Early morning", 8))
    }

    /**
     * 数字记忆广度子页（Task 7b，`ui/focus/ChildMemoryTrainingScreen.kt`）漏译守卫：
     * 去重 17 条 → 本表 **15 个**新字段（9 个 String 在本清单 + 6 个函数型见
     * [memoryTrainingInterpolatedCopyPinsBothLanguages]）+ **复用 [FocusStrings.startTrainingButton]**
     * 一条：第 82 行的 `🚀 开始训练` 与舒尔特页第 148 行同值同义，共用字段而不是再造一个
     * + 上收 [CommonStrings.confirmButton] 一条（`确认` 是通用按钮词，第 1 轮审查第 8 条）。
     */
    @Test
    fun memoryTrainingCopyDiffers() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertDiffers(
            zh.memoryTitle to en.memoryTitle,
            zh.memoryHeading to en.memoryHeading,
            zh.memoryHowTo to en.memoryHowTo,
            zh.memoryShowPrompt to en.memoryShowPrompt,
            zh.memoryWatchHint to en.memoryWatchHint,
            zh.memoryPracticeAgainButton to en.memoryPracticeAgainButton,
            zh.memoryMissPrompt to en.memoryMissPrompt,
            zh.memoryEncourageHint to en.memoryEncourageHint,
            zh.memoryRetryButton to en.memoryRetryButton,
        )
    }

    /**
     * 记忆子页 6 条函数型字段的双语字面量断言。
     *
     * 参数是页面上真实算好的值：`挑战 ${level + 1} 位` 传的是 `level + 1`、`回到 ${level - 1} 位`
     * 传的是 `level - 1`（两处 Ruling 20 的"表达式挪到实参位"，与 Task 7 的 deepBreathCount 同形），
     * 所以断言里的 4 和 3 就是设备上出现的数字，而不是把 `level` 又拼一遍。
     */
    @Test
    fun memoryTrainingInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertEquals("🏆 本次最佳：记住 5 位数字", zh.memoryBestLine(5))
        assertEquals("🏆 Best this round: 5 digits remembered", en.memoryBestLine(5))
        assertEquals("准备好了吗？从 3 位数字开始！", zh.memoryReadyLine(3))
        assertEquals("Ready? Let's start with 3 digits!", en.memoryReadyLine(3))
        assertEquals("数字是什么？凭记忆输入（4 位）：", zh.memoryInputPrompt(4))
        assertEquals("What were the digits? Type them from memory (4 digits):", en.memoryInputPrompt(4))
        assertEquals("🎉 太棒了！记住了 3 位数字！+3分", zh.memorySuccessLine(3, 3))
        // 英文不写 "+3 points"：那一页根本不加积分（ChildMemoryTrainingScreen.kt:52 `val points = 0`），
        // 中文的 `+3分` 是"这一轮得 3 分"的口语分数，英文照抄就成了一笔永远不会兑现的加分承诺
        // （第 1 轮审查的 Minor：中文歧义保留、英文侧只改这一处）。
        assertEquals("🎉 Amazing! You remembered 3 of 3 digits!", en.memorySuccessLine(3, 3))
        assertEquals("挑战 4 位 →", zh.memoryChallengeButton(4))
        assertEquals("Try 4 digits →", en.memoryChallengeButton(4))
        assertEquals("回到 3 位", zh.memoryStepBackButton(3))
        assertEquals("Back to 3 digits", en.memoryStepBackButton(3))
    }

    /**
     * 每日计划子页（Task 7b，`ui/focus/ChildDailyPlanScreen.kt`）漏译守卫：14 条字面量 →
     * 本表 **12 个**字段，全是非函数型（清单里插值 0 条）。落在 [FocusStrings] 而不是新造第三张表（Ruling 23）。
     *
     * 少的那两条是 `删除`（:155）与 `取消`（:199）：通用控件词，第 1 轮审查第 8 条把它们上收到
     * [CommonStrings]（见 [commonDialogButtonsPinnedByLiteral]），中文值逐字节未变。
     *
     * `已完成` / `未完成` 是 `contentDescription`（读屏），与 Task 8 的
     * [TaskListStrings.completedContentDescription]（`已完成(点击撤销)`）**不同值**，所以不能复用那条；
     * `开始` / `结束` 是时分步进器的轴标签，与 [CommonStrings.hourLabel] / [minuteLabel] 无关
     * （那两个是单位 `时` / `分`，这里是"Start"/"End"），同理不复用。
     */
    @Test
    fun dailyPlanCopyDiffers() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertDiffers(
            zh.dailyPlanTitle to en.dailyPlanTitle,
            zh.addPlanDescription to en.addPlanDescription,
            zh.dailyPlanEmptyHint to en.dailyPlanEmptyHint,
            zh.planAddedSnackbar to en.planAddedSnackbar,
            zh.planCompletedDescription to en.planCompletedDescription,
            zh.planUncompletedDescription to en.planUncompletedDescription,
            zh.addPlanDialogTitle to en.addPlanDialogTitle,
            zh.planSubjectLabel to en.planSubjectLabel,
            zh.planTimeRangeLabel to en.planTimeRangeLabel,
            zh.planStartLabel to en.planStartLabel,
            zh.planEndLabel to en.planEndLabel,
            zh.addPlanButton to en.addPlanButton,
        )
    }

    /**
     * `开始` / `结束` 这两个词在本工程出现在**三种不同控件**上，英文却必须分别是
     * Start / End / Stop / Start focusing 四种——它们是同形（同值）不同义，
     * [assertDiffers] 只看同一字段的 ZH≠EN，**互换两个英文值照样全绿**，
     * 整表 CJK 扫描也看不见（两边都是英文）。与 [commonUnitLabelsPinnedByLiteral] 同一类盲区，
     * 所以这里用字面量把六个串一次钉死：Task 7b 的两条轴标签 vs 组件的停止按钮 vs 组件的开始专注按钮。
     *
     * 这条断言是 Task 7b 第 1 轮审查的 Minor 补充：`FocusStrings.planEndLabel`（`结束` → "End"）
     * 与 `PomodoroStrings.stopButton`（`结束` → "Stop"）中文同值、跨两张表，
     * 以后任何人"顺手统一一下英文"都会让时间块轴标签变成 "Stop"。
     */
    @Test
    fun startAndEndCopyIsPinnedPerControl() {
        assertEquals("开始", AppStringsZh.focus.planStartLabel)
        assertEquals("Start", AppStringsEn.focus.planStartLabel)
        assertEquals("结束", AppStringsZh.focus.planEndLabel)
        assertEquals("End", AppStringsEn.focus.planEndLabel)
        // 同一个 `结束` 在计时器按钮上是"停下来"，不是"轴的另一端"
        assertEquals("结束", AppStringsZh.pomodoro.stopButton)
        assertEquals("Stop", AppStringsEn.pomodoro.stopButton)
        // 同一个 `开始` 在计时器主按钮上是动词短语，不是步进器轴标签
        assertEquals("开始专注", AppStringsZh.pomodoro.startFocusButton)
        assertEquals("Start focusing", AppStringsEn.pomodoro.startFocusButton)
    }

    /**
     * **同一行两种 `分`**（Task 7b 必须做对的第一件事，裁定 1 在本页的落点）。
     *
     * 宿主页第 246 行 `每专注 5 分钟 = +1 分（所有时长均有效）` 和第 247 行
     * `15分钟+3分 · …`、记忆子页第 106 行 `记住了 $level 位数字！+${level}分`：
     * 前者 `5 分钟` 是时长、`+1 分` 是积分。判错方向的渲染是 "Focus 5 points = +1 min"。
     *
     * 防错手段是**结构上的**，不是口头上的：[PomodoroStrings.pointsPerFocusRule] 把两个数值做成
     * 两个**具名实参** `minutes` / `points`，积分词形只能由 `pointsWord(points)` 产生，
     * 时长词形只能是 `分钟` / `min`，位置互换在类型上就写不出来。下面每个用例都**两条一起断言**
     * （brief Step 5：同行双单位必须两条都断言），并另加一条 `points = 1` 的边界，
     * 因为"1 分 = 1 point"正是这一行的实际取值。
     *
     * [FocusStrings.memorySuccessLine] 也在同一批里，但它的第二个槽**不是积分**：那一页
     * `val points = 0`，中文 `+3分` 是这轮的分数量词，所以英文写 `3 of 3 digits` 而不是 `+3 points`，
     * 并另加一条 assertFalse 钉住"英文不得在这里承诺加分"（第 1 轮审查的 Minor 修复）。
     * 两个槽的分槽结构照旧保留：中文侧仍然一处读位数、一处读分数。
     */
    @Test
    fun pointsAndMinutesSharingOneLineUseSeparateSlots() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        // 第 246 行：时长槽 + 积分槽
        assertEquals("每专注 5 分钟 = +1 分（所有时长均有效）", zh.pointsPerFocusRule(5, 1))
        assertEquals("Every 5 min of focus = +1 point (every length counts)", en.pointsPerFocusRule(5, 1))
        // 同一个字段的边界取值：积分 1 用单数、时长 1 仍用 min（缩写不随单复数变化，§11）
        assertEquals("每专注 1 分钟 = +1 分（所有时长均有效）", zh.pointsPerFocusRule(1, 1))
        assertEquals("Every 1 min of focus = +1 point (every length counts)", en.pointsPerFocusRule(1, 1))
        assertFalse(en.pointsPerFocusRule(5, 1).contains("min = +1 min"), "积分槽被写成了时长槽")
        // 第 247 行：四个"时长+积分"对，中文一个字段照抄，英文每处积分词形都出自 pointsWord
        assertEquals("15分钟+3分 · 25分钟+5分 · 30分钟+6分 · 45分钟+9分", zh.pointsLadderNote)
        assertEquals(
            "15 min +3 points · 25 min +5 points · 30 min +6 points · 45 min +9 points",
            en.pointsLadderNote,
        )
        // 记忆子页第 106 行：`位数字` 是位数、`+N分` 是那一页的口语分数，两个实参在页面里同源（都是 level）。
        // 中文侧照旧逐字节不变；英文侧**不**把分数写成积分入账（同一句里 "points" 是假的承诺），
        // 所以这一条的"分槽"仍在（两个实参各填各的），但英文的第二个槽只能读成"几个记对了"。
        assertEquals("🎉 太棒了！记住了 6 位数字！+6分", AppStringsZh.focus.memorySuccessLine(6, 6))
        assertEquals("🎉 Amazing! You remembered 6 of 6 digits!", AppStringsEn.focus.memorySuccessLine(6, 6))
        assertFalse(
            AppStringsEn.focus.memorySuccessLine(6, 6).contains("point", ignoreCase = true),
            "记忆训练页不加积分（ChildMemoryTrainingScreen.kt:52 的 val points = 0），英文不许承诺加分",
        )
        assertEquals(
            "🍅 「自由学习」专注完成！+1分已提交家长审核",
            zh.focusDoneSnackbar("自由学习", 1),
        )
        assertEquals(
            "🍅 Focus on 'Free study' complete! +1 point sent for approval",
            en.focusDoneSnackbar("Free study", 1),
        )
    }

    /**
     * 步进器/时长 chip 的 `分` **必须复用 [CommonStrings.minuteLabel]**（Task 7b 第二件事），
     * 也就是全工程只有两个 `分` 字段：积分的 `pointsUnit` 与分钟的 `minuteLabel`，
     * 宿主页不再开第三个（否则 Task 6+8 的字面量同形异义守卫就管不到这一处）。
     *
     * [PomodoroStrings.durationChip] 因此把单位做成实参，页面传 `common.minuteLabel` 进来：
     * 中文 `15` + `分` = `15分`（与迁移前逐字节相同，中文模板里本来就没有空格），
     * 英文 `15` + ` ` + `min`（空格在表里，不在页面里）。
     *
     * 两条英文断言合起来才抓得住互换错误：若有人把 `minuteLabel` 的英文改成 "points"，
     * `durationChip` 会渲染出 "15 points"，第三条 assertFalse 立刻红；
     * 若有人把 pointsUnit 改成 "min"，[commonUnitLabelsPinnedByLiteral] 那边红。
     */
    @Test
    fun durationChipReusesTheCommonMinuteLabel() {
        assertEquals("15分", AppStringsZh.pomodoro.durationChip(15, AppStringsZh.common.minuteLabel))
        assertEquals("25分", AppStringsZh.pomodoro.durationChip(25, AppStringsZh.common.minuteLabel))
        assertEquals("3分", AppStringsZh.pomodoro.durationChip(3, AppStringsZh.common.minuteLabel))
        val chipFromMinuteLabel = AppStringsEn.pomodoro.durationChip(15, AppStringsEn.common.minuteLabel)
        assertEquals("15 min", chipFromMinuteLabel)
        assertFalse(
            chipFromMinuteLabel.contains(AppStringsEn.common.pointsUnit),
            "时长 chip 里出现了积分单位：单位实参必须来自 common.minuteLabel，不是 pointsUnit",
        )
        // 休息 chips 与专注 chips 共用同一个字段（同值同义），所以 3/5/10 与 15/25/30/45 走同一条渲染
        assertEquals("5 min", AppStringsEn.pomodoro.durationChip(5, AppStringsEn.common.minuteLabel))
    }

    /**
     * **存储边界**（Task 7b 第 1 轮审查的 Critical；Ruling 25 / spec §5）。
     *
     * 上一轮把 `"自由学习"` 换成 `strings.targetFreeStudy` 之后，那个值被写进了
     * `focus_sessions.taskTitle`，又把 `strings.focusRecordDescription(...)` 写进了
     * `point_approvals.description`（再由 `PointRepository.kt:204` 复制进 `point_records.note`、
     * 被 `:165` 拼进 `撤销打卡扣回：…`）。后果是数据按语言分裂：
     * `ChildEfficiencyReportScreen.kt:335` 按 `taskTitle` 分组，同一逻辑目标**永久裂成两根柱**。
     * 修法不是"再补一份中文表"，而是**存的字节回到迁移前**、只在渲染时本地化：
     * 规范值在 [CanonicalFocusText]，显示走 [PomodoroStrings.targetLabel]。
     *
     * 下面六组断言各自钉住一件事：
     * 1. 规范值逐字 = 迁移前页面里写的那四条中文与那条模板；
     * 2. 规范值 = 中文表的显示值（中文态输出逐字节不变，这是"没有回改历史"的前提）；
     * 3. 存储模板 = 表内中文读侧模板（两处一旦漂移，中文态就会显示两种写法）；
     * 4. 渲染时才本地化：四个已知目标各有英文标签，且英文标签与四个显示字段一致（防表内两份英文漂移）；
     * 5. 认不出的输入**原样通过**：数据库里的任务标题（`tasks.map { it.title }`）与本分支测试期
     *    已经写进去的英文行都仍能显示——这正是"不做迁移、不做模式匹配回填"能成立的条件；
     * 6. 页面侧那七行的调用参数本身**测不到**（没有 Compose UI 测试依赖，spec §9），
     *    能被自动证明的是 `scripts/i18n_storage_boundary.py` 以退出码守住"文案表的值不进 sink"，
     *    复现：`python scripts/i18n_storage_boundary.py`（当前退出码 0）与
     *    `grep -rnE "(submitApproval|recordFocusSession)\(.*strings\." composeApp/src/commonMain`（0 命中）。
     */
    @Test
    fun focusTargetsAreStoredAsCanonicalTextAndLocalizedOnlyAtRender() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        // 1. 存储规范值（写进 taskTitle 与 description 的就是这些字节）
        assertEquals("自由学习", CanonicalFocusText.TARGET_FREE_STUDY)
        assertEquals("复习错题", CanonicalFocusText.TARGET_REVIEW_MISTAKES)
        assertEquals("预习新课", CanonicalFocusText.TARGET_PREVIEW_LESSON)
        assertEquals("完成作业", CanonicalFocusText.TARGET_FINISH_HOMEWORK)
        assertEquals(
            listOf("自由学习", "复习错题", "预习新课", "完成作业"),
            CanonicalFocusText.presetTargets,
        )
        assertEquals(
            "专注「自由学习」25分钟",
            CanonicalFocusText.recordDescription(CanonicalFocusText.TARGET_FREE_STUDY, 25),
        )
        // 2. 中文态：规范值就是显示值，一个字节都没动
        assertEquals(CanonicalFocusText.TARGET_FREE_STUDY, zh.targetFreeStudy)
        assertEquals(CanonicalFocusText.TARGET_REVIEW_MISTAKES, zh.targetReviewMistakes)
        assertEquals(CanonicalFocusText.TARGET_PREVIEW_LESSON, zh.targetPreviewLesson)
        assertEquals(CanonicalFocusText.TARGET_FINISH_HOMEWORK, zh.targetFinishHomework)
        // 3. 存储侧模板与读侧中文模板必须同源同字
        assertEquals(
            zh.focusRecordDescription(CanonicalFocusText.TARGET_FREE_STUDY, 25),
            CanonicalFocusText.recordDescription(CanonicalFocusText.TARGET_FREE_STUDY, 25),
        )
        // 4. 渲染时本地化：四个已知目标
        assertEquals("自由学习", zh.targetLabel(CanonicalFocusText.TARGET_FREE_STUDY))
        assertEquals("复习错题", zh.targetLabel(CanonicalFocusText.TARGET_REVIEW_MISTAKES))
        assertEquals("Free study", en.targetLabel(CanonicalFocusText.TARGET_FREE_STUDY))
        assertEquals("Review mistakes", en.targetLabel(CanonicalFocusText.TARGET_REVIEW_MISTAKES))
        assertEquals("Preview the new lesson", en.targetLabel(CanonicalFocusText.TARGET_PREVIEW_LESSON))
        assertEquals("Finish homework", en.targetLabel(CanonicalFocusText.TARGET_FINISH_HOMEWORK))
        assertEquals(en.targetFreeStudy, en.targetLabel(CanonicalFocusText.TARGET_FREE_STUDY))
        assertEquals(en.targetReviewMistakes, en.targetLabel(CanonicalFocusText.TARGET_REVIEW_MISTAKES))
        assertEquals(en.targetPreviewLesson, en.targetLabel(CanonicalFocusText.TARGET_PREVIEW_LESSON))
        assertEquals(en.targetFinishHomework, en.targetLabel(CanonicalFocusText.TARGET_FINISH_HOMEWORK))
        // 5. 未知输入原样通过（数据库任务标题 / 测试期写入的英文行 / 空值）
        assertEquals("数学作业", zh.targetLabel("数学作业"))
        assertEquals("数学作业", en.targetLabel("数学作业"))
        assertEquals("Free study", en.targetLabel("Free study"))
        assertEquals("", en.targetLabel(""))
        // 6. 中文侧必须是恒等函数：否则中文态显示会与存储值分裂
        listOf("自由学习", "复习错题", "预习新课", "完成作业", "数学作业", "").forEach {
            assertEquals(it, zh.targetLabel(it), "中文侧 targetLabel 不再是恒等：$it")
        }
    }

    /**
     * Task 7b 新增英文 one/other（spec §3 末句 + §8.3），并钉住中文没有单复数。
     *
     * 可达性逐条核过：`todayPomodoroCount` 首完成即 1（`Completed 1 Pomodoros` 是真会出现的错误）；
     * `overHoursNote` 的 hours 在 60–119 分钟时是 1，但 `hr` 是 §11 定的缩写、不随数变化，
     * 所以那条**不该**有 if —— 一并断言，防止有人"顺手加复数"（与 Task 7 的 min/hr/s 断言同一理由）。
     * 记忆子页的"位数字"最小取值是 3（`level` 初值 3，`回到 N 位` 按钮要求 `level > 3`），
     * 所以英文用不带分支的 "digits"；这条可达性写在 [FocusStrings.memoryBestLine] 的字段注释里，
     * 若后续任务放开下限，需要在 `Plurals.kt` 加一个同样窄的 digitsWord（本任务禁止改那个文件）。
     */
    @Test
    fun task7bEnglishPluralsCoverOneAndOther() {
        assertEquals("完成 1 个番茄钟 🍅×1", AppStringsZh.pomodoro.completedPomodorosLine(1))
        assertEquals("Completed 1 Pomodoro 🍅×1", AppStringsEn.pomodoro.completedPomodorosLine(1))
        assertEquals("Completed 2 Pomodoros 🍅×2", AppStringsEn.pomodoro.completedPomodorosLine(2))
        // 缩写单位没有单复数
        assertEquals(" (over 1 hr)", AppStringsEn.pomodoro.overHoursNote(1))
        assertEquals("（超1小时）", AppStringsZh.pomodoro.overHoursNote(1))
        assertEquals("1 min", AppStringsEn.pomodoro.durationChip(1, AppStringsEn.common.minuteLabel))
        assertEquals(
            "Every 15 min of focus = +15 points (every length counts)",
            AppStringsEn.pomodoro.pointsPerFocusRule(15, 15),
        )
        // 中文侧 1 与 15 同形，防止有人把英文的 if 复制回中文表
        assertEquals("完成 15 个番茄钟 🍅×15", AppStringsZh.pomodoro.completedPomodorosLine(15))
    }

    /**
     * Step 4 功能修正（`rememberUpdatedState`）的字面量证据。
     *
     * 改动本身在 `PomodoroTimer.kt` 的 `LaunchedEffect(state)` 里：把闭包捕获的 `strings` 换成
     * `currentStrings`（key 仍是 `state`，计时循环不重启）。本文件不能测组合期行为（没有 Compose UI
     * 测试依赖，spec §8 / Task 12 之前的约定），能被自动证明的是**这两条被通知使用的文案**在两种语言里
     * 逐字是什么——`currentStrings` 与 `strings` 取的是同一个表实例，所以钉住表值就钉住了通知内容；
     * 中文态输出逐字节不变（Ruling 20(b)）由第 1、2、3 条断言承担。
     */
    @Test
    fun runningNotificationCopyIsPinnedInBothLanguages() {
        assertEquals("🍅 专注中", AppStringsZh.pomodoro.phaseFocus)
        assertEquals("☕ 休息中", AppStringsZh.pomodoro.phaseBreak)
        assertEquals("剩余 8分20秒 · 息屏后仍会继续计时", AppStringsZh.pomodoro.remainTime(8, 20))
        assertEquals("🍅 Focusing", AppStringsEn.pomodoro.phaseFocus)
        assertEquals("☕ On a break", AppStringsEn.pomodoro.phaseBreak)
        assertEquals("8 min 20 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(8, 20))
    }

    @Test
    fun stringsForPicksInstance() {
        // assertSame 而非 assertEquals：数据类的结构相等会让"新建一个内容相同实例"也通过，
        // 这里要钉死的是"命中单例表"这一事实。
        assertSame(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertSame(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }

    /**
     * 孩子首页（Task 9，`ui/child/ChildHomeScreen.kt`）漏译守卫：102 个字段里 **85 个静态 String 全数点名**，
     * 17 个函数型字段由 [childHomeInterpolatedCopyPinsBothLanguages] 负责（Ruling 20-b）。
     *
     * 清单是穷举的，不是抽样：新增字段没进来 = 本断言少一条，审查时看得见；
     * 漏译（EN 直接抄中文）则由本断言与 [englishTableContainsNoChineseLeftovers] 双抓。
     * 本页没有跨字段同形异义需要防"英文值互换"的静态对（唯一的 `分` 同形是 [childHomeScoreUnitIsNotPoints]，
     * 它跨的是 CommonStrings，见该断言的理由），所以本清单只做同字段 ZH≠EN。
     */
    @Test
    fun childHomeCopyDiffers() {
        val zh = AppStringsZh.childHome
        val en = AppStringsEn.childHome
        assertDiffers(
            zh.pageTitle to en.pageTitle,
            zh.switchRoleContentDescription to en.switchRoleContentDescription,
            zh.encouragementNoTask to en.encouragementNoTask,
            zh.encouragementFreshStart to en.encouragementFreshStart,
            zh.encouragementAllDone to en.encouragementAllDone,
            zh.encouragementAlmostThere to en.encouragementAlmostThere,
            zh.encouragementOnTrack to en.encouragementOnTrack,
            zh.encouragementKickoff to en.encouragementKickoff,
            zh.todayCheckinTitle to en.todayCheckinTitle,
            zh.todayAllDoneBanner to en.todayAllDoneBanner,
            zh.difficultyChallengeLevel to en.difficultyChallengeLevel,
            zh.difficultyAdvancedLevel to en.difficultyAdvancedLevel,
            zh.difficultyNormalLevel to en.difficultyNormalLevel,
            zh.learningScoreTitle to en.learningScoreTitle,
            zh.scoreUnit to en.scoreUnit,
            zh.scoreDimensionCheckin to en.scoreDimensionCheckin,
            zh.scoreDimensionFocus to en.scoreDimensionFocus,
            zh.scoreDimensionReview to en.scoreDimensionReview,
            zh.weakTipCheckin to en.weakTipCheckin,
            zh.weakTipFocus to en.weakTipFocus,
            zh.weakTipReview to en.weakTipReview,
            zh.improvementSuggestionsHeading to en.improvementSuggestionsHeading,
            zh.excellentBonusMessage to en.excellentBonusMessage,
            zh.claimExcellentBonusButton to en.claimExcellentBonusButton,
            zh.excellentBonusClaimedNote to en.excellentBonusClaimedNote,
            zh.mistakesDueNote to en.mistakesDueNote,
            zh.mistakesAllDoneTitle to en.mistakesAllDoneTitle,
            zh.mistakesAllDoneNote to en.mistakesAllDoneNote,
            zh.viewAllAchievementsButton to en.viewAllAchievementsButton,
            zh.myApprovalRequestsTitle to en.myApprovalRequestsTitle,
            zh.parentSyncTitle to en.parentSyncTitle,
            zh.receiveParentSettingsButton to en.receiveParentSettingsButton,
            zh.sendProgressButton to en.sendProgressButton,
            zh.shareProgressChooserTitle to en.shareProgressChooserTitle,
            zh.progressExportedMessage to en.progressExportedMessage,
            zh.exportFailedMessage to en.exportFailedMessage,
            zh.syncingLabel to en.syncingLabel,
            zh.claimBonusButton to en.claimBonusButton,
            zh.weeklyChallengeTitle to en.weeklyChallengeTitle,
            zh.weeklyChallengeClaimMessage to en.weeklyChallengeClaimMessage,
            zh.claimButton to en.claimButton,
            zh.weeklyChallengeClaimedNote to en.weeklyChallengeClaimedNote,
            zh.weeklyChallengeInProgressNote to en.weeklyChallengeInProgressNote,
            zh.parentAssignmentsTitle to en.parentAssignmentsTitle,
            zh.completeAssignmentButton to en.completeAssignmentButton,
            zh.step1Title to en.step1Title,
            zh.step1Subtitle to en.step1Subtitle,
            zh.entryStartCheckinTitle to en.entryStartCheckinTitle,
            zh.entryStartCheckinSubtitle to en.entryStartCheckinSubtitle,
            zh.entryMyPetTitle to en.entryMyPetTitle,
            zh.entryMyPetSubtitle to en.entryMyPetSubtitle,
            zh.step2Title to en.step2Title,
            zh.step2Subtitle to en.step2Subtitle,
            zh.entryPomodoroTitle to en.entryPomodoroTitle,
            zh.entryPomodoroSubtitle to en.entryPomodoroSubtitle,
            zh.entryMistakeNotebookTitle to en.entryMistakeNotebookTitle,
            zh.entryMistakeNotebookSubtitle to en.entryMistakeNotebookSubtitle,
            zh.step3Title to en.step3Title,
            zh.step3Subtitle to en.step3Subtitle,
            zh.entryDailyPlanTitle to en.entryDailyPlanTitle,
            zh.entryDailyPlanSubtitle to en.entryDailyPlanSubtitle,
            zh.entryDailyTargetTitle to en.entryDailyTargetTitle,
            zh.entryDailyTargetSubtitle to en.entryDailyTargetSubtitle,
            zh.entryScheduleTimelineTitle to en.entryScheduleTimelineTitle,
            zh.entryScheduleTimelineSubtitle to en.entryScheduleTimelineSubtitle,
            zh.entryHabitChallengeTitle to en.entryHabitChallengeTitle,
            zh.entryHabitChallengeSubtitle to en.entryHabitChallengeSubtitle,
            zh.entryGoalTrackerTitle to en.entryGoalTrackerTitle,
            zh.entryGoalTrackerSubtitle to en.entryGoalTrackerSubtitle,
            zh.step4Title to en.step4Title,
            zh.step4Subtitle to en.step4Subtitle,
            zh.entryLuckyDrawTitle to en.entryLuckyDrawTitle,
            zh.entryLuckyDrawSubtitle to en.entryLuckyDrawSubtitle,
            zh.entryEfficiencyReportTitle to en.entryEfficiencyReportTitle,
            zh.entryEfficiencyReportSubtitle to en.entryEfficiencyReportSubtitle,
            zh.entryPointsDetailTitle to en.entryPointsDetailTitle,
            zh.entryPointsDetailSubtitle to en.entryPointsDetailSubtitle,
            zh.entryRewardStoreTitle to en.entryRewardStoreTitle,
            zh.entryRewardStoreSubtitle to en.entryRewardStoreSubtitle,
            zh.entryWishBankTitle to en.entryWishBankTitle,
            zh.entryWishBankSubtitle to en.entryWishBankSubtitle,
            zh.entryWeeklyReportTitle to en.entryWeeklyReportTitle,
            zh.entryWeeklyReportSubtitle to en.entryWeeklyReportSubtitle,
            zh.myGoalTitle to en.myGoalTitle,
            zh.earnPointsForRewardsNote to en.earnPointsForRewardsNote,
        )
    }

    /**
     * 孩子首页 17 个函数型字段的**逐字双语期望串**（Ruling 20-b 的强制形态）。
     *
     * 为什么必须逐字打出来，而不是"调 lambda 再和 lambda 自己拼的串比"：整表 CJK 扫描看不见函数型
     * 字段的返回值（data class 的 toString() 打的是 lambda 句柄），所以插在函数里的中文只有靠这里的
     * 期望串才抓得到。每条两行：中文侧证明迁移只是把字面量搬进表（页面原文另由
     * scripts/i18n_template_diff.py 整页复验），英文侧证明"确实译成了英文、且内容参数还在原位"。
     *
     * 内容透传（brief 本页风险的第 1、2 条）在这里逐条点名：
     * - [ChildHomeStrings.importFailedMessage] 的 detail 是 `result.exceptionOrNull()?.message`，
     *   异常文本原样进串、不吞、不改固定文案；参数类型是 String?，所以 null 也照打 "null"（与迁移前一致）；
     * - [ChildHomeStrings.tackleFirstHint] 把**任务标题**连同星级、难度名一起插进句子，实参位显式命名，
     *   取数逻辑仍在页面里（difficulty -> 星级/名称），只有句式进了表；
     * - [ChildHomeStrings.approvedRequestLine] / [rejectedRequestLine] 透传落库的 description
     *   （规范中文，见 [CanonicalChildHomeText]），本地化的只有行首行尾的框架词；
     * - [ChildHomeStrings.wishGoalNameLabel] / [saveTowardsRewardNote] 透传心愿名与奖励图标+名称。
     * 这四组期望串里**故意保留中文输入**，就是在证明"只改文案来源、没改数据源"。
     */
    @Test
    fun childHomeInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childHome
        val en = AppStringsEn.childHome

        assertEquals("导入失败：boom", zh.importFailedMessage("boom"))
        assertEquals("Import failed: boom", en.importFailedMessage("boom"))
        assertEquals("导入失败：null", zh.importFailedMessage(null))
        assertEquals("Import failed: null", en.importFailedMessage(null))

        assertEquals("⚠️ 有每日必须任务未完成，已自动扣减 3 分", zh.mandatoryPenaltyMessage(3))
        assertEquals("⚠️ Daily must-do tasks were missed — 3 points deducted automatically",
            en.mandatoryPenaltyMessage(3))

        assertEquals("先难后易 · 还有 2 个困难任务", zh.hardTaskFirstTitle(2))
        assertEquals("Hard ones first · 2 hard tasks left", en.hardTaskFirstTitle(2))

        assertEquals("趁精力充沛，先攻克「背 20 个单词」(⭐⭐⭐挑战)，剩下的会更轻松！",
            zh.tackleFirstHint("背 20 个单词", "⭐⭐⭐", "挑战"))
        // 英文侧的第三个实参是**英文难度名**：页面传的是 `strings.difficultyXxxLevel`，
        // 所以英文态到这里时它已经是 "Challenge" 了（内容参数的语言由上游决定，本字段只负责句式）。
        assertEquals("You're at your best right now — finish \"背 20 个单词\" (⭐⭐⭐ Challenge) first, " +
            "and everything after it feels easier!",
            en.tackleFirstHint("背 20 个单词", "⭐⭐⭐", "Challenge"))

        assertEquals("今日有 3 道错题待复习", zh.mistakesDueTitle(3))
        assertEquals("3 mistakes to review today", en.mistakesDueTitle(3))

        assertEquals("⏳ 2 个申请待家长审核（+12 分，通过后计入）", zh.pendingApprovalSummary(2, 12))
        assertEquals("⏳ 2 requests waiting for parent approval (+12 points, counted once approved)",
            en.pendingApprovalSummary(2, 12))

        assertEquals("✅ 数学作业 +5分", zh.approvedRequestLine("数学作业", 5))
        assertEquals("✅ 数学作业 +5 points", en.approvedRequestLine("数学作业", 5))

        assertEquals("❌ 语文朗读（未通过）", zh.rejectedRequestLine("语文朗读"))
        assertEquals("❌ 语文朗读 (not approved)", en.rejectedRequestLine("语文朗读"))

        assertEquals("连续打卡 7 天啦！", zh.streakMilestoneTitle(7))
        assertEquals("7 days in a row — what a streak!", en.streakMilestoneTitle(7))

        assertEquals("达成里程碑，领取 +15 积分奖励！", zh.streakMilestoneBonusNote(15))
        assertEquals("Milestone reached — claim your +15 points reward!", en.streakMilestoneBonusNote(15))

        assertEquals("🎉 领取成功！+5 积分", zh.streakMilestoneClaimedMessage(5))
        assertEquals("🎉 Claimed! +5 points", en.streakMilestoneClaimedMessage(5))

        assertEquals("进度 3/5 天 · 达成领 +5 积分", zh.weeklyChallengeProgressNote(3))
        assertEquals("Progress 3/5 days · reach 5 for +5 points", en.weeklyChallengeProgressNote(3))

        assertEquals("🎉 完成布置任务 +4分（待家长审核）！", zh.assignmentCompletedMessage(4))
        assertEquals("🎉 Assignment done! +4 points (pending approval)", en.assignmentCompletedMessage(4))

        assertEquals("心愿：乐高", zh.wishGoalNameLabel("乐高"))
        assertEquals("Wish: 乐高", en.wishGoalNameLabel("乐高"))

        assertEquals("已存 30/100 分，还差 70 分达成心愿！", zh.wishGoalProgressNote(30, 100, 70))
        assertEquals("30/100 points saved — only 70 points to go!", en.wishGoalProgressNote(30, 100, 70))

        assertEquals("🎉 现在可以兑换 2 个奖励啦，去商店看看吧！", zh.affordableRewardsNote(2))
        assertEquals("🎉 You can afford 2 rewards now — take a look in the store!", en.affordableRewardsNote(2))

        assertEquals("再攒 20 分就能兑换「🎮 游戏时间」", zh.saveTowardsRewardNote(20, "🎮", "游戏时间"))
        assertEquals("Save 20 more points to get \"🎮 游戏时间\"",
            en.saveTowardsRewardNote(20, "🎮", "游戏时间"))
    }

    /**
     * 英文 one/other 覆盖（§8.3）：本页的计数句分布在 12 个函数型字段里。
     *
     * 为什么分支写在 lambda 内部而不是给 [Plurals.kt] 再加一个函数：本期约束不动 Plurals.kt
     * （那里只有积分 pointsWord），所以"任务 / 错题 / 天 / 申请 / 奖励"的词形各就各位。
     * 代价是这类分支以后可能漂，Task 10+ 若再遇到同名次词形应把函数补进 Plurals.kt。
     * 中文侧没有单复数：`n = 1` 与 `n = 2` 的中文只差数字本身，由最后四条断言钉住，
     * 防止有人把英文的 `if` 复制回中文表（与 taskListEnglishPluralsCoverOneAndOther 同一动机）。
     */
    @Test
    fun childHomeEnglishPluralsCoverOneAndOther() {
        val zh = AppStringsZh.childHome
        val en = AppStringsEn.childHome
        assertEquals("⚠️ Daily must-do tasks were missed — 1 point deducted automatically", en.mandatoryPenaltyMessage(1))
        assertEquals("Hard ones first · 1 hard task left", en.hardTaskFirstTitle(1))
        assertEquals("1 mistake to review today", en.mistakesDueTitle(1))
        assertEquals("⏳ 1 request waiting for parent approval (+1 point, counted once approved)",
            en.pendingApprovalSummary(1, 1))
        assertEquals("✅ 数学作业 +1 point", en.approvedRequestLine("数学作业", 1))
        assertEquals("1 day in a row — what a streak!", en.streakMilestoneTitle(1))
        assertEquals("Milestone reached — claim your +1 point reward!", en.streakMilestoneBonusNote(1))
        assertEquals("🎉 Claimed! +1 point", en.streakMilestoneClaimedMessage(1))
        assertEquals("🎉 Assignment done! +1 point (pending approval)", en.assignmentCompletedMessage(1))
        assertEquals("99/100 points saved — only 1 point to go!", en.wishGoalProgressNote(99, 100, 1))
        assertEquals("🎉 You can afford 1 reward now — take a look in the store!", en.affordableRewardsNote(1))
        assertEquals("Save 1 more point to get \"🎮 游戏时间\"", en.saveTowardsRewardNote(1, "🎮", "游戏时间"))

        assertEquals("先难后易 · 还有 1 个困难任务", zh.hardTaskFirstTitle(1))
        assertEquals("今日有 1 道错题待复习", zh.mistakesDueTitle(1))
        assertEquals("连续打卡 1 天啦！", zh.streakMilestoneTitle(1))
        assertEquals("🎉 现在可以兑换 1 个奖励啦，去商店看看吧！", zh.affordableRewardsNote(1))
    }

    /**
     * `分` 的第三种语义（同形异义约定在本页的落点）。
     *
     * ChildHomeScreen.kt:290 的 `Text("分")` 紧跟 `${learningScore.total}`，是"今日学习表现"的
     * **0–100 评分单位**，不是积分：LearningScoreCalculator 的 total 从不入账，入账的是旁边的
     * adjustPoints(2, …)（而那次的 note 是规范中文）。所以英文必须写 `pts`：若复用
     * [CommonStrings.pointsUnit]（英文 "points"），英文态会读出 "87 Points"，把一次评分念成 87 积分——
     * 这正是 CommonStrings 头注释里"同形异义必须分槽、且只能有一个出处"要防的第二份出处。
     * 两条中文值同字（都是 `分`），**跨字段**的英文互换 assertDiffers 抓不到，所以用字面量钉死。
     *
     * 同一段还钉 `提升` 的同形：本页"提升建议"是"改进建议"（How to improve），
     * 不是 [TaskListStrings.filterEnhancement]（任务分类 `提升` = Advanced）；复用会把分类词念成动词。
     */
    @Test
    fun childHomeScoreUnitIsNotPoints() {
        assertEquals("分", AppStringsZh.childHome.scoreUnit)
        assertEquals("pts", AppStringsEn.childHome.scoreUnit)
        assertEquals("分", AppStringsZh.common.pointsUnit)
        assertEquals("points", AppStringsEn.common.pointsUnit)
        assertNotEquals(
            AppStringsEn.common.pointsUnit, AppStringsEn.childHome.scoreUnit,
        )
        assertEquals("💡 提升建议：", AppStringsZh.childHome.improvementSuggestionsHeading)
        assertEquals("💡 How to improve:", AppStringsEn.childHome.improvementSuggestionsHeading)
        assertEquals("提升", AppStringsZh.taskList.filterEnhancement)
        assertEquals("Advanced", AppStringsEn.taskList.filterEnhancement)
    }

    /**
     * 存储边界（Ruling 25）在本页的四条落库/幂等原文：值必须与迁移前**逐字节相同**，
     * 且必须留在 [CanonicalChildHomeText] 而不是文案表里。
     *
     * 为什么值得单独一条断言：`scripts/i18n_storage_boundary.py` 能证明"页面没把 strings.* 传给
     * adjustPoints / checkIn"，但证明不了"规范值本身有没有被人顺手改成英文或改写措辞"；
     * 后者一旦漂移，`PointRepository.kt:256` 的 `pending.description != description` 就会把
     * 每一次完成判定成新业务，历史行按语言分裂。第二个方向（不得进文案表）由 assertFalse 抓：
     * 表里出现这四条原文 = 有人把它接上了语言实例，闸门立刻会红。
     */
    @Test
    fun childHomeStorageCopyStaysCanonical() {
        assertEquals("今日学习表现优秀奖励", CanonicalChildHomeText.EXCELLENT_BONUS_NOTE)
        assertEquals("每周挑战奖励：本周打卡5天", CanonicalChildHomeText.WEEKLY_CHALLENGE_NOTE)
        assertEquals("连续打卡7天里程碑奖励", CanonicalChildHomeText.streakMilestoneNote(7))
        assertEquals("完成布置任务: 数学作业", CanonicalChildHomeText.assignmentDescription("数学作业"))
        val zhTable = AppStringsZh.childHome.toString()
        val enTable = AppStringsEn.childHome.toString()
        listOf("今日学习表现优秀奖励", "每周挑战奖励：本周打卡5天", "连续打卡", "完成布置任务: ")
            .forEach { canonical ->
                assertFalse(zhTable.contains(canonical), "落库原文又回到文案表里了：$canonical")
                assertFalse(enTable.contains(canonical), "英文表里出现落库原文：$canonical")
            }
        // 渲染侧"连续打卡 $days 天啦！"与存储侧"连续打卡N天里程碑奖励"是两条不同的话：
        // 前者可本地化、后者不可。这条断言钉住前者没被顺手改成规范值模板。
        assertEquals("连续打卡 7 天啦！", AppStringsZh.childHome.streakMilestoneTitle(7))
    }

    /**
     * emoji 留在文案表里（Ruling 17 在本页的落点）：页面不得再拼 `"🌟 ${strings.pageTitle}"`。
     *
     * 本页两种形态都有，所以两条检查分开做：
     * - **行首 emoji + 空格 + 正文**（23 条，含一条函数型的 🎉 前缀）用 `substringBefore(' ')` 比较，
     *   代理对整体取，不截半；
     * - **行尾 emoji**（鼓励语 6 条 + [ChildHomeStrings.earnPointsForRewardsNote]）没有空格可切，
     *   改成"两表都以同一枚 emoji 收尾"，并逐字打出期望 emoji（把 emoji 挪到行首也会红）。
     * 4 步引导的 chip 图标（StepEntry 的第一个实参）不在本断言范围：它是**数据位**而不是文案串的一部分，
     * 两种语言都不该跟着变，页面里也没有为它写中文。
     */
    @Test
    fun emojiStaysInsideTheChildHomeTable() {
        val zh = AppStringsZh.childHome
        val en = AppStringsEn.childHome
        listOf(
            zh.pageTitle to en.pageTitle,
            zh.todayCheckinTitle to en.todayCheckinTitle,
            zh.todayAllDoneBanner to en.todayAllDoneBanner,
            zh.scoreDimensionCheckin to en.scoreDimensionCheckin,
            zh.scoreDimensionFocus to en.scoreDimensionFocus,
            zh.scoreDimensionReview to en.scoreDimensionReview,
            zh.weakTipCheckin to en.weakTipCheckin,
            zh.weakTipFocus to en.weakTipFocus,
            zh.weakTipReview to en.weakTipReview,
            zh.improvementSuggestionsHeading to en.improvementSuggestionsHeading,
            zh.excellentBonusMessage to en.excellentBonusMessage,
            zh.claimExcellentBonusButton to en.claimExcellentBonusButton,
            zh.excellentBonusClaimedNote to en.excellentBonusClaimedNote,
            zh.viewAllAchievementsButton to en.viewAllAchievementsButton,
            zh.myApprovalRequestsTitle to en.myApprovalRequestsTitle,
            zh.parentSyncTitle to en.parentSyncTitle,
            zh.receiveParentSettingsButton to en.receiveParentSettingsButton,
            zh.sendProgressButton to en.sendProgressButton,
            zh.claimBonusButton to en.claimBonusButton,
            zh.weeklyChallengeClaimMessage to en.weeklyChallengeClaimMessage,
            zh.weeklyChallengeClaimedNote to en.weeklyChallengeClaimedNote,
            zh.parentAssignmentsTitle to en.parentAssignmentsTitle,
            zh.affordableRewardsNote(2) to en.affordableRewardsNote(2),
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "行首 emoji 应两表一致")
        }
        assertEquals("📝", en.encouragementNoTask.takeLast(2))
        assertEquals("💪", en.encouragementFreshStart.takeLast(2))
        assertEquals("🎉", en.encouragementAllDone.takeLast(2))
        assertEquals("🌟", en.encouragementAlmostThere.takeLast(2))
        assertEquals("👍", en.encouragementOnTrack.takeLast(2))
        assertEquals("🚀", en.encouragementKickoff.takeLast(2))
        assertEquals("💪", en.earnPointsForRewardsNote.takeLast(2))
        listOf(
            zh.encouragementNoTask to en.encouragementNoTask,
            zh.encouragementFreshStart to en.encouragementFreshStart,
            zh.encouragementAllDone to en.encouragementAllDone,
            zh.encouragementAlmostThere to en.encouragementAlmostThere,
            zh.encouragementOnTrack to en.encouragementOnTrack,
            zh.encouragementKickoff to en.encouragementKickoff,
            zh.earnPointsForRewardsNote to en.earnPointsForRewardsNote,
        ).forEach { (chinese, english) ->
            assertEquals(chinese.takeLast(2), english.takeLast(2), "行尾 emoji 应两表一致")
        }
    }

    /**
     * 家长首页 51 个**静态**字段的 ZH≠EN 穷举（8 个函数型见
     * [parentHomeInterpolatedCopyPinsBothLanguages] 与 [parentHomeEnglishPluralsCoverOneAndOther]）。
     */
    @Test
    fun parentHomeCopyDiffers() {
        val zh = AppStringsZh.parentHome
        val en = AppStringsEn.parentHome
        assertDiffers(
            zh.pageTitle to en.pageTitle,
            zh.switchRoleContentDescription to en.switchRoleContentDescription,
            zh.childTodayOverviewTitle to en.childTodayOverviewTitle,
            zh.overviewCheckin to en.overviewCheckin,
            zh.overviewCompletionRate to en.overviewCompletionRate,
            zh.overviewStreak to en.overviewStreak,
            zh.overviewPendingProofs to en.overviewPendingProofs,
            zh.todayAllDoneBanner to en.todayAllDoneBanner,
            zh.presetRewardsLoadedMessage to en.presetRewardsLoadedMessage,
            zh.syncOverdueNote to en.syncOverdueNote,
            zh.menuTaskManageTitle to en.menuTaskManageTitle,
            zh.menuTaskManageSubtitle to en.menuTaskManageSubtitle,
            zh.menuAssignTaskTitle to en.menuAssignTaskTitle,
            zh.menuAssignTaskSubtitle to en.menuAssignTaskSubtitle,
            zh.menuTemplateTitle to en.menuTemplateTitle,
            zh.menuTemplateSubtitle to en.menuTemplateSubtitle,
            zh.menuPointsTitle to en.menuPointsTitle,
            zh.menuPointsSubtitle to en.menuPointsSubtitle,
            zh.menuRewardStoreTitle to en.menuRewardStoreTitle,
            zh.menuRewardStoreSubtitle to en.menuRewardStoreSubtitle,
            zh.menuWishGoalTitle to en.menuWishGoalTitle,
            zh.menuWishGoalSubtitle to en.menuWishGoalSubtitle,
            zh.menuScheduleTitle to en.menuScheduleTitle,
            zh.menuScheduleSubtitle to en.menuScheduleSubtitle,
            zh.menuProofTitle to en.menuProofTitle,
            zh.menuProofSubtitle to en.menuProofSubtitle,
            zh.menuApprovalTitle to en.menuApprovalTitle,
            zh.menuApprovalSubtitle to en.menuApprovalSubtitle,
            zh.menuMistakeAnalysisTitle to en.menuMistakeAnalysisTitle,
            zh.menuMistakeAnalysisSubtitle to en.menuMistakeAnalysisSubtitle,
            zh.menuCoachingTitle to en.menuCoachingTitle,
            zh.menuCoachingSubtitle to en.menuCoachingSubtitle,
            zh.menuDashboardTitle to en.menuDashboardTitle,
            zh.menuDashboardSubtitle to en.menuDashboardSubtitle,
            zh.menuGrowthReportTitle to en.menuGrowthReportTitle,
            zh.menuGrowthReportSubtitle to en.menuGrowthReportSubtitle,
            zh.menuWeeklyReportTitle to en.menuWeeklyReportTitle,
            zh.menuWeeklyReportSubtitle to en.menuWeeklyReportSubtitle,
            zh.menuSettingsTitle to en.menuSettingsTitle,
            zh.menuSettingsSubtitle to en.menuSettingsSubtitle,
            zh.setupAssistantTitle to en.setupAssistantTitle,
            zh.setupAllDoneNote to en.setupAllDoneNote,
            zh.setupIntroNote to en.setupIntroNote,
            zh.setupTasksMissing to en.setupTasksMissing,
            zh.setupTasksAction to en.setupTasksAction,
            zh.setupRewardsMissing to en.setupRewardsMissing,
            zh.setupRewardsAction to en.setupRewardsAction,
            zh.setupWishMissing to en.setupWishMissing,
            zh.setupWishAction to en.setupWishAction,
            zh.setupScheduleMissing to en.setupScheduleMissing,
            zh.setupScheduleAction to en.setupScheduleAction,
        )
        // 三个"去 X"按钮英文必须互不相同：它们同屏并排出现，同译会把四个入口念成同一个动作。
        assertDiffers(
            en.setupTasksAction to en.setupWishAction,
            en.setupTasksAction to en.setupScheduleAction,
            en.setupWishAction to en.setupScheduleAction,
        )
    }

    /** 数据看板 30 个**静态**字段的 ZH≠EN 穷举（6 个函数型见 [parentDashboardInterpolatedCopyPinsBothLanguages]）。 */
    @Test
    fun parentDashboardCopyDiffers() {
        val zh = AppStringsZh.parentDashboard
        val en = AppStringsEn.parentDashboard
        assertDiffers(
            zh.pageTitle to en.pageTitle,
            zh.totalPointsLabel to en.totalPointsLabel,
            zh.weeklyEarnedLabel to en.weeklyEarnedLabel,
            zh.weeklySpentLabel to en.weeklySpentLabel,
            zh.totalFocusLabel to en.totalFocusLabel,
            zh.mistakesMasteredLabel to en.mistakesMasteredLabel,
            zh.childPerformanceTitle to en.childPerformanceTitle,
            zh.scoreUnit to en.scoreUnit,
            zh.verdictNewStart to en.verdictNewStart,
            zh.verdictImproved to en.verdictImproved,
            zh.verdictLevel to en.verdictLevel,
            zh.verdictDeclined to en.verdictDeclined,
            zh.weekCompareTitle to en.weekCompareTitle,
            zh.compareFocusLabel to en.compareFocusLabel,
            zh.compareReviewLabel to en.compareReviewLabel,
            zh.reviewCountUnit to en.reviewCountUnit,
            zh.comparePointsLabel to en.comparePointsLabel,
            zh.trendTitle to en.trendTitle,
            zh.noDataLabel to en.noDataLabel,
            zh.categoryTitle to en.categoryTitle,
            zh.categoryBasicLabel to en.categoryBasicLabel,
            zh.categoryEnhanceLabel to en.categoryEnhanceLabel,
            zh.categoryHabitLabel to en.categoryHabitLabel,
            zh.weakAreasTitle to en.weakAreasTitle,
            zh.weakAreaNoTasksNote to en.weakAreaNoTasksNote,
            zh.goodStatusTitle to en.goodStatusTitle,
            zh.goodStatusNote to en.goodStatusNote,
            zh.recentChangesTitle to en.recentChangesTitle,
            zh.changeFallbackLabel to en.changeFallbackLabel,
            zh.deltaFlatLabel to en.deltaFlatLabel,
        )
        // 四条趋势判词必须互不相同（同一个 when 的四支，两支同值就等于少了一档反馈）
        assertEquals(4, listOf(zh.verdictNewStart, zh.verdictImproved, zh.verdictLevel, zh.verdictDeclined).distinct().size)
        assertEquals(4, listOf(en.verdictNewStart, en.verdictImproved, en.verdictLevel, en.verdictDeclined).distinct().size)
        // Ruling 34 携带项 1：§11"数据看板 = Data dashboard"那行声称本测试保证"菜单入口名与页面顶栏名
        // 同词形"，但跨表比较原本并不存在，这里补上（两行、两侧都查）。
        // 形状与 brief 给的写法**方向相反**，以代码为准：带 emoji 的是**页面顶栏**
        // （ParentDashboardStrings.pageTitle = "📊 数据看板" / "📊 Data dashboard"，ParentDashboardScreen.kt:95），
        // 菜单入口名没有 emoji（ParentHomeStrings.menuDashboardTitle = "数据看板" / "Data dashboard"，
        // ParentHomeScreen.kt:212），所以 `substringAfter(' ')` 只能取在 pageTitle 那一边。
        assertEquals(
            AppStringsEn.parentHome.menuDashboardTitle,
            AppStringsEn.parentDashboard.pageTitle.substringAfter(' '),
            "同一个功能的菜单入口名与页面顶栏名在英文里不是同一个词形",
        )
        assertEquals(
            AppStringsZh.parentHome.menuDashboardTitle,
            AppStringsZh.parentDashboard.pageTitle.substringAfter(' '),
            "同一个功能的菜单入口名与页面顶栏名在中文里不是同一个词形",
        )
    }

    /**
     * 家长首页 8 个函数型字段的**逐字双语期望串**（Ruling 20-b：整表 CJK 扫描看不见函数型返回值，
     * 只有把期望串逐字打出来才抓得到插在函数里的中文）。
     *
     * 内容透传：本表 8 条实参全是页面自己算出来的 `Int` 计数（todayCompleted / pendingProofCount /
     * unmasteredMistakes / syncDaysAgo / taskCount / rewardCount / wishCount / scheduleCount），
     * 没有一条是文案串，所以本页不存在"把中文数据念成英文数据"的风险；
     * 有这种风险的是仪表盘那条跨文件的 grade，见 [parentDashboardInterpolatedCopyPinsBothLanguages]。
     */
    @Test
    fun parentHomeInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentHome
        val en = AppStringsEn.parentHome

        assertEquals("7天", zh.streakDaysValue(7))
        assertEquals("1天", zh.streakDaysValue(1))
        assertEquals("0天", zh.streakDaysValue(0))
        assertEquals("📷 有 3 个证明待审核，点击前往", zh.pendingProofsButton(3))
        assertEquals("📷 有 1 个证明待审核，点击前往", zh.pendingProofsButton(1))
        assertEquals("📚 还有 6 道错题未掌握", zh.unmasteredMistakesNote(6))
        assertEquals("📚 还有 1 道错题未掌握", zh.unmasteredMistakesNote(1))
        assertEquals("设备数据已 9 天未同步", zh.syncOverdueTitle(9))
        assertEquals("设备数据已 1 天未同步", zh.syncOverdueTitle(1))
        assertEquals("学习任务已配置（4个）", zh.setupTasksConfigured(4))
        assertEquals("奖励商店已配置（3个）", zh.setupRewardsConfigured(3))
        assertEquals("心愿存钱罐已配置（2个）", zh.setupWishConfigured(2))
        assertEquals("每日日程已配置（5项）", zh.setupScheduleConfigured(5))

        assertEquals("📷 3 proofs await review — tap to open", en.pendingProofsButton(3))
        assertEquals("📷 1 proof awaits review — tap to open", en.pendingProofsButton(1))
        assertEquals("📚 6 mistakes are still not mastered", en.unmasteredMistakesNote(6))
        assertEquals("📚 1 mistake is still not mastered", en.unmasteredMistakesNote(1))
        assertEquals("Device data has not been synced for 9 days", en.syncOverdueTitle(9))
        assertEquals("Device data has not been synced for 1 day", en.syncOverdueTitle(1))
        assertEquals("Study tasks set up (4)", en.setupTasksConfigured(4))
        assertEquals("Reward store set up (3)", en.setupRewardsConfigured(3))
        assertEquals("Wish Pool set up (2)", en.setupWishConfigured(2))
        assertEquals("Daily schedule set up (5)", en.setupScheduleConfigured(5))
    }

    /**
     * 家长首页 4 条英文 one/other 分支（Ruling 20 例外：[Plurals.kt] 本期不改，分支写在函数体内）。
     * 形态与 [childHomeEnglishPluralsCoverOneAndOther] 一致：1 与 >=2 两侧逐字打出来，
     * 并用"中文侧只是换数字"的等式钉住中文不分支（改成 if 也会红）。
     */
    @Test
    fun parentHomeEnglishPluralsCoverOneAndOther() {
        val zh = AppStringsZh.parentHome
        val en = AppStringsEn.parentHome
        assertEquals("7 days", en.streakDaysValue(7))
        assertEquals("1 day", en.streakDaysValue(1))
        assertEquals("10 days", en.streakDaysValue(10))
        assertEquals("7 proofs await review — tap to open", en.pendingProofsButton(7).substringAfter("📷 "))
        assertEquals("7 mistakes are still not mastered", en.unmasteredMistakesNote(7).substringAfter("📚 "))
        assertEquals("Device data has not been synced for 10 days", en.syncOverdueTitle(10))
        listOf(
            zh.streakDaysValue(1) to zh.streakDaysValue(7),
            zh.pendingProofsButton(1) to zh.pendingProofsButton(7),
            zh.unmasteredMistakesNote(1) to zh.unmasteredMistakesNote(7),
            zh.syncOverdueTitle(1) to zh.syncOverdueTitle(7),
        ).forEach { (one, other) ->
            assertEquals(one.replace("1", "7"), other, "中文侧必须只换数字、不能出现单复数分支")
        }
    }

    /**
     * 数据看板 6 个函数型字段的逐字双语期望串（Ruling 20-b）。
     *
     * **内容透传的正面证据**：[ParentDashboardStrings.childPerformanceLine] 的第一个实参 `grade`
     * 来自跨文件的 `ui/common/LearningScoreCalculator`，本表只搬句式、不翻译内容位，所以这里的
     * 中文实参是**桩**：它证明模板对等级名原样透传（同一句模板喂英文实参也只换内容位）。
     * W8 T2 起计算器带 `lang` 参数、等级/鼓励语走 `LearningScoreCopy` 按生效语言取词，英文态这一格
     * 读出 "Excellent · Check-in 3/5 …"；取值链路由 `W8CopyTest.learningScoreCopyPinsBothLanguages`
     * 与本条模板断言分工钉住，"英文态仍读出中文等级名"不再是已知上限。
     * [ParentDashboardStrings.scoreValue] 的单位实参来自同表 [ParentDashboardStrings.scoreUnit]，
     * [ParentDashboardStrings.focusMinutesValue] 的单位实参来自 [CommonStrings.minuteLabel]：
     * 两处都是"单位进实参位"的已披露拆分（见 ParentDashboardStrings 类注释与 DISCLOSED_RESIDUALS）。
     */
    @Test
    fun parentDashboardInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentDashboard
        val en = AppStringsEn.parentDashboard

        assertEquals("优秀 · 打卡3/5 · 专注40分 · 复习2道", zh.childPerformanceLine("优秀", 3, 5, 40, 2))
        assertEquals("优秀 · Check-in 3/5 · Focus 40 min · Reviewed 2", en.childPerformanceLine("优秀", 3, 5, 40, 2))
        assertEquals("待开始 · 打卡0/5 · 专注0分 · 复习0道", zh.childPerformanceLine("待开始", 0, 5, 0, 0))
        assertEquals("数学 · Check-in 1/1 · Focus 60 min · Reviewed 3", en.childPerformanceLine("数学", 1, 1, 60, 3))

        assertEquals("87分", zh.scoreValue(87, zh.scoreUnit))
        assertEquals("0分", zh.scoreValue(0, zh.scoreUnit))
        assertEquals("87 pts", en.scoreValue(87, en.scoreUnit))

        assertEquals("45分", zh.focusMinutesValue(45, AppStringsZh.common.minuteLabel))
        assertEquals("45 min", en.focusMinutesValue(45, AppStringsEn.common.minuteLabel))

        assertEquals("3道", zh.mistakesReviewedValue(3))
        assertEquals("3", en.mistakesReviewedValue(3))

        assertEquals("📚 错题掌握率较低 (4/20)，建议加强复习", zh.weakAreaMistakesNote(4, 20))
        assertEquals("📚 Only 4 of 20 mistakes are mastered — review them more often",
            en.weakAreaMistakesNote(4, 20))

        assertEquals("🍅 专注时长不足 (30分钟)，建议使用番茄钟", zh.weakAreaFocusNote(30))
        assertEquals("🍅 Only 30 min of focus time — try the Pomodoro timer", en.weakAreaFocusNote(30))
        // 时长侧的英文绝不出现积分词：同一屏里 `分`/`分钟` 与"积分"只差一个字，混译就串账
        assertFalse(
            en.weakAreaFocusNote(30).contains("point", ignoreCase = true) ||
                en.focusMinutesValue(30, AppStringsEn.common.minuteLabel).contains("point", ignoreCase = true) ||
                en.childPerformanceLine("优秀", 3, 5, 40, 2).contains("point", ignoreCase = true),
            "时长侧混进了积分词：专注的 `分` 是分钟，不是 Points",
        )
    }

    /**
     * 同形异义 `分` 在数据看板一屏之内有三种语义，逐槽用字面量钉死
     * （`assertDiffers` 对"两个同值字段的英文互换"是瞎的，见 CommonStrings 类注释）。
     * 与 [childHomeScoreUnitIsNotPoints] 同一条理由：0–100 的评分与积分是两本账
     * （`LearningScoreCalculator.total` 从不入账，`adjustPoints` 写的才是 point_records）。
     */
    @Test
    fun parentDashboardScoreUnitIsNotPoints() {
        assertEquals("分", AppStringsZh.parentDashboard.scoreUnit)
        assertEquals("pts", AppStringsEn.parentDashboard.scoreUnit)
        assertEquals("分", AppStringsZh.common.minuteLabel)
        assertEquals("min", AppStringsEn.common.minuteLabel)
        assertEquals("分", AppStringsZh.common.pointsUnit)
        assertEquals("points", AppStringsEn.common.pointsUnit)
        assertNotEquals(AppStringsEn.parentDashboard.scoreUnit, AppStringsEn.common.minuteLabel)
        assertNotEquals(AppStringsEn.parentDashboard.scoreUnit, AppStringsEn.common.pointsUnit)
        assertNotEquals(AppStringsEn.common.minuteLabel, AppStringsEn.common.pointsUnit)
        // 两张表各存一份同一个术语（刻意不跨表引用），一致性由断言而不是由引用保证
        assertEquals(AppStringsZh.childHome.scoreUnit, AppStringsZh.parentDashboard.scoreUnit)
        assertEquals(AppStringsEn.childHome.scoreUnit, AppStringsEn.parentDashboard.scoreUnit)
    }

    /**
     * `:171` 传给 `ParentCompareRow(…, unit)` 的分钟单位**复用** [CommonStrings.minuteLabel]：
     * 本任务清单 36 条里的这一条没有落成本表字段（理由见 ParentDashboardStrings 类注释）。
     * 复用是刻意的——同一个中文值若在本表再开一个分钟槽就有了第二处出处，而漏译守卫抓不到互换，
     * 所以这里既用字面量钉住出处，也用穷举证明本表只有一个 `分` 槽（评分单位 scoreUnit）。
     * 形态与 [durationChipReusesTheCommonMinuteLabel] 一致。
     */
    @Test
    fun parentDashboardDurationUnitComesFromCommon() {
        assertEquals("分", AppStringsZh.common.minuteLabel)
        assertEquals("min", AppStringsEn.common.minuteLabel)
        val dashZhStatics = staticCopyValues(AppStringsZh.parentDashboard).toMap()
        assertEquals(
            setOf("scoreUnit"),
            dashZhStatics.filterValues { it == "分" }.keys,
            "本表出现第二个 `分` 槽就说明分钟那条被从 CommonStrings 挪了过来，复用与残差登记要一起改",
        )
        assertEquals("45分", AppStringsZh.parentDashboard.focusMinutesValue(45, AppStringsZh.common.minuteLabel))
        assertEquals("45 min", AppStringsEn.parentDashboard.focusMinutesValue(45, AppStringsEn.common.minuteLabel))
    }

    /**
     * `道` 是中文量词、英文没有对应词：值槽退化成纯数字，增减单位是**空串**。
     * 空串不是漏译，是 Ruling 20 披露的形态差异（ChildHome 的"今日有 3 道错题待复习" →
     * "3 mistakes to review today" 走的是同一条路：量词整词删掉）。两侧都写死，
     * 这样"顺手把英文填成 道"或"填成 points"都会被点名。
     */
    @Test
    fun parentDashboardEnglishDropsTheMeasureWord() {
        assertEquals("道", AppStringsZh.parentDashboard.reviewCountUnit)
        assertEquals("", AppStringsEn.parentDashboard.reviewCountUnit)
        assertEquals("5道", AppStringsZh.parentDashboard.mistakesReviewedValue(5))
        assertEquals("5", AppStringsEn.parentDashboard.mistakesReviewedValue(5))
        assertNotEquals(AppStringsEn.parentDashboard.reviewCountUnit, AppStringsEn.common.minuteLabel)
        assertNotEquals(AppStringsZh.parentDashboard.reviewCountUnit, AppStringsZh.common.minuteLabel)
    }

    /**
     * §11 的"基础 / 提升 / 习惯 = Basic / Advanced / Habits"在两页必须同词形：
     * 仪表盘是图表行标签、任务列表是筛选芯片，字段各自独立（各带一枚 emoji）但术语必须一致，
     * 否则同一个 `TaskCategory` 在两个页面念成两个名字。
     */
    @Test
    fun categoryWordsMatchTheTaskListFilters() {
        assertEquals(AppStringsEn.taskList.filterBasic, AppStringsEn.parentDashboard.categoryBasicLabel.substringAfter(' '))
        assertEquals(AppStringsEn.taskList.filterEnhancement, AppStringsEn.parentDashboard.categoryEnhanceLabel.substringAfter(' '))
        assertEquals(AppStringsEn.taskList.filterHabits, AppStringsEn.parentDashboard.categoryHabitLabel.substringAfter(' '))
        assertEquals("基础", AppStringsZh.parentDashboard.categoryBasicLabel.substringAfter(' '))
        assertEquals("提升", AppStringsZh.parentDashboard.categoryEnhanceLabel.substringAfter(' '))
        assertEquals("习惯", AppStringsZh.parentDashboard.categoryHabitLabel.substringAfter(' '))
    }

    /**
     * 两页的 emoji 都留在表内（Ruling 17）：页面不得再拼 `"🍅 ${strings.x}"`。
     * 两页只有一种形态——行首 emoji + 空格 + 正文，所以统一用 `substringBefore(' ')` 比较
     * （代理对与 U+FE0F 变体选择符整体取，不截半；`➡️` / `⚠️` 这类带 VS16 的也在这一条里）。
     *
     * 刻意**留在页面**的三处不在本断言范围：`ParentHomeScreen` 的 `Text("⏰")`、`Text("🚀")` 与
     * `SetupRow` 的 `if (ok) "✅" else "⚠️"`，它们是纯状态/装饰位，不含汉字、不随语言变，
     * `i18n_inventory.py` 也不把它们计为可迁移文案（迁移后该页残留计数仍为 0 就是这件事的证据）。
     */
    @Test
    fun emojiStaysInsideTheParentTables() {
        val homeZh = AppStringsZh.parentHome
        val homeEn = AppStringsEn.parentHome
        listOf(
            homeZh.pageTitle to homeEn.pageTitle,
            homeZh.childTodayOverviewTitle to homeEn.childTodayOverviewTitle,
            homeZh.todayAllDoneBanner to homeEn.todayAllDoneBanner,
            homeZh.presetRewardsLoadedMessage to homeEn.presetRewardsLoadedMessage,
            homeZh.setupAllDoneNote to homeEn.setupAllDoneNote,
            homeZh.pendingProofsButton(3) to homeEn.pendingProofsButton(3),
            homeZh.unmasteredMistakesNote(6) to homeEn.unmasteredMistakesNote(6),
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "家长首页行首 emoji 应两表一致")
        }
        val dashZh = AppStringsZh.parentDashboard
        val dashEn = AppStringsEn.parentDashboard
        listOf(
            dashZh.pageTitle to dashEn.pageTitle,
            dashZh.verdictNewStart to dashEn.verdictNewStart,
            dashZh.verdictImproved to dashEn.verdictImproved,
            dashZh.verdictLevel to dashEn.verdictLevel,
            dashZh.verdictDeclined to dashEn.verdictDeclined,
            dashZh.weekCompareTitle to dashEn.weekCompareTitle,
            dashZh.compareFocusLabel to dashEn.compareFocusLabel,
            dashZh.compareReviewLabel to dashEn.compareReviewLabel,
            dashZh.comparePointsLabel to dashEn.comparePointsLabel,
            dashZh.trendTitle to dashEn.trendTitle,
            dashZh.categoryTitle to dashEn.categoryTitle,
            dashZh.categoryBasicLabel to dashEn.categoryBasicLabel,
            dashZh.categoryEnhanceLabel to dashEn.categoryEnhanceLabel,
            dashZh.categoryHabitLabel to dashEn.categoryHabitLabel,
            dashZh.weakAreasTitle to dashEn.weakAreasTitle,
            dashZh.weakAreaNoTasksNote to dashEn.weakAreaNoTasksNote,
            dashZh.goodStatusTitle to dashEn.goodStatusTitle,
            dashZh.weakAreaMistakesNote(4, 20) to dashEn.weakAreaMistakesNote(4, 20),
            dashZh.weakAreaFocusNote(30) to dashEn.weakAreaFocusNote(30),
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "数据看板行首 emoji 应两表一致")
        }
    }

    // =====================================================================
    // Task 11：家长设置页（ui/parent/ParentSettingsScreen.kt）
    // =====================================================================

    /**
     * 家长设置页 57 个**静态**字段的 ZH≠EN 穷举 + 清单本身的闭合校验（Ruling 34 携带项 3 要求的
     * "自称的守卫必须真的存在且真的穷举"）。
     *
     * 两条断言方向相反，缺一条就会漏一类错：
     * - 集合相等：表里算出来的静态字段名必须与 [SETTINGS_STATIC_DIFFERS_FIELDS] 完全一致
     *   （新增字段没登记 → 红；登记了但字段被删/改名 → 也红）。这是 Task 9/10 那份手写清单
     *   的闭合版本，理由见那份清单的注释。
     * - 逐字段 ZH≠EN：报错能点名是哪条中文没译。
     * 两个自称词（[SettingsStrings.languageZh] / [languageEn]）按 Ruling 6 刻意留在集合里做
     * **成员校验**、但不进 assertDiffers；11 个函数型字段由
     * [settingsInterpolatedCopyPinsBothLanguages] 负责（整表扫描与 assertDiffers 都看不见它们）。
     */
    @Test
    fun settingsCopyDiffers() {
        val zh = staticCopyValues(AppStringsZh.settings).toMap()
        val en = staticCopyValues(AppStringsEn.settings).toMap()
        val endonyms = setOf("languageZh", "languageEn")   // Ruling 6：两种 locale 下同值，不参与漏译比较
        assertEquals(
            SETTINGS_STATIC_DIFFERS_FIELDS + endonyms,
            zh.keys,
            "SettingsStrings 的静态字段清单与实际字段不再一致：新增字段必须同时进 " +
                "SETTINGS_STATIC_DIFFERS_FIELDS（或自称词豁免），删掉的字段要从清单里一起撤",
        )
        assertEquals(zh.keys, en.keys, "两张表的字段集合必须对齐（data class 本就同源，这条防的是解析异常）")
        assertDiffers(
            *SETTINGS_STATIC_DIFFERS_FIELDS.sorted().map { zh.getValue(it) to en.getValue(it) }.toTypedArray()
        )
    }

    /**
     * 家长设置页 11 个函数型字段的**逐字双语期望串**（Ruling 20-b：整表 CJK 扫描看不见函数型返回值）。
     *
     * 三条与本页存储边界直接相关的：
     * - [SettingsStrings.deviceNameDisplayLabel] 是"存规范值、渲染本地化"的那一步（Ruling 25/34），
     *   英文侧认得出的只有规范兜底值，认不出的一律原样透传（父级自取的名字、跨设备传来的名字）；
     * - [SettingsStrings.deviceNameSavedMessage] 的实参是**数据**，中文态逐字节等于迁移前的
     *   `"已保存设备名称：$name"`；
     * - [SettingsStrings.progressCardTitle] 的实参是**孩子设备名**（来自对方设备的数据），
     *   渲染位在插值前过一层 [SettingsStrings.deviceNameDisplayLabel]（Ruling 42），于是英文态
     *   至少不会把**规范兜底值**念成中文；父级/孩子自己起的名字仍按数据原样透传，那是已知上限。
     * 四条失败消息（[SettingsStrings.childProgressImportFailedMessage] / [backupFailureMessage] /
     * [restoreFailureMessage] / [exportFailedMessage]）的 `null` 分支**中英各钉一条、共 8 条**：
     * 迁移前 `${it.message}` 在 message 为 null 时渲染 "null"，改成实参位之后必须还是 "null"，
     * 否则就是改了文案（例如顺手写成 `${detail ?: ""}`，中文态会念出"备份失败："这个半截句子）。
     */
    @Test
    fun settingsInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.settings
        val en = AppStringsEn.settings

        assertEquals("导入失败：文件已损坏", zh.childProgressImportFailedMessage("文件已损坏"))
        assertEquals("导入失败：null", zh.childProgressImportFailedMessage(null))
        assertEquals("Import failed: file is broken", en.childProgressImportFailedMessage("file is broken"))
        assertEquals("Import failed: null", en.childProgressImportFailedMessage(null))

        assertEquals("❌ 备份失败：disk full", zh.backupFailureMessage("disk full"))
        assertEquals("❌ 备份失败：null", zh.backupFailureMessage(null))
        assertEquals("❌ Backup failed: disk full", en.backupFailureMessage("disk full"))
        assertEquals("❌ Backup failed: null", en.backupFailureMessage(null))

        assertEquals("❌ 恢复失败：disk full", zh.restoreFailureMessage("disk full"))
        assertEquals("❌ 恢复失败：null", zh.restoreFailureMessage(null))
        assertEquals("❌ Restore failed: disk full", en.restoreFailureMessage("disk full"))
        assertEquals("❌ Restore failed: null", en.restoreFailureMessage(null))

        assertEquals("导出失败：文件已损坏", zh.exportFailedMessage("文件已损坏"))
        assertEquals("导出失败：null", zh.exportFailedMessage(null))
        assertEquals("Export failed: file is broken", en.exportFailedMessage("file is broken"))
        assertEquals("Export failed: null", en.exportFailedMessage(null))

        assertEquals("📊 妈妈的平板的学习进度", zh.progressCardTitle("妈妈的平板"))
        assertEquals("📊 Study progress from Mom's tablet", en.progressCardTitle("Mom's tablet"))
        // 内容透传的反面证据：跨设备传来的中文设备名在英文态仍是它自己的原文（不是漏译，是数据）
        assertEquals("📊 Study progress from 妈妈的平板", en.progressCardTitle("妈妈的平板"))
        // Ruling 42：渲染位（ParentSettingsScreen.kt 的孩子进度卡片标题）现在把设备名先过一层
        // deviceNameDisplayLabel 再插进 progressCardTitle，所以"整页英文态唯一"那句才是真话。
        // 下面两条钉的是**这条管线在规范兜底值上的输出**：英文侧念 "Parent device"、
        // 中文侧逐字节等于迁移前那条 `${cp.deviceName}的学习进度`（恒等映射，字节不变）。
        // 为什么用字面量而不是只写 `en.progressCardTitle(en.deviceNameDisplayLabel(x))`：
        // 两处调用可以一起改错，字面量不能。
        assertEquals(
            "📊 Study progress from Parent device",
            en.progressCardTitle(en.deviceNameDisplayLabel(CanonicalParentSettingsText.DEVICE_NAME_FALLBACK)),
        )
        assertEquals(
            "📊 家长设备的学习进度",
            zh.progressCardTitle(zh.deviceNameDisplayLabel(CanonicalParentSettingsText.DEVICE_NAME_FALLBACK)),
        )

        assertEquals("7天", zh.streakDaysValue(7))
        assertEquals("1天", zh.streakDaysValue(1))
        assertEquals("0天", zh.streakDaysValue(0))

        assertEquals("45分", zh.progressFocusMinutesValue(45, AppStringsZh.common.minuteLabel))
        assertEquals("45 min", en.progressFocusMinutesValue(45, AppStringsEn.common.minuteLabel))

        assertEquals("12次", zh.completionsValue(12))
        assertEquals("1次", zh.completionsValue(1))
        assertEquals("3个", zh.todayCheckinsValue(3))

        assertEquals("已保存设备名称：家长设备", zh.deviceNameSavedMessage(zh.deviceNameFallbackLabel))
        assertEquals("已保存设备名称：妈妈的手机", zh.deviceNameSavedMessage("妈妈的手机"))
        assertEquals("Device name saved: Parent device", en.deviceNameSavedMessage(en.deviceNameFallbackLabel))
        assertEquals("Device name saved: Mom's phone", en.deviceNameSavedMessage("Mom's phone"))

        assertEquals("家长设备", zh.deviceNameDisplayLabel(CanonicalParentSettingsText.DEVICE_NAME_FALLBACK))
        assertEquals("妈妈的手机", zh.deviceNameDisplayLabel("妈妈的手机"))
        assertEquals("Parent device", en.deviceNameDisplayLabel(CanonicalParentSettingsText.DEVICE_NAME_FALLBACK))
        assertEquals("Mom's phone", en.deviceNameDisplayLabel("Mom's phone"))
    }

    /**
     * [SettingsStrings.streakDaysValue] 的英文 one/other（Ruling 20 例外：[Plurals.kt] 本期不改，
     * 分支写在函数体内）。中文侧用"只换数字"的等式钉住它不分支，形态与
     * [parentHomeEnglishPluralsCoverOneAndOther] 一致。
     */
    @Test
    fun settingsEnglishPluralsCoverOneAndOther() {
        val zh = AppStringsZh.settings
        val en = AppStringsEn.settings
        assertEquals("7 days", en.streakDaysValue(7))
        assertEquals("1 day", en.streakDaysValue(1))
        assertEquals("10 days", en.streakDaysValue(10))
        assertEquals(zh.streakDaysValue(1).replace("1", "7"), zh.streakDaysValue(7), "中文侧不该出现单复数分支")
    }

    /**
     * `次`（`:279`）与 `个`（`:283`）是中文量词，英文没有对应词：**整词删掉**，值槽退化成纯数字。
     * 与 §11 的"道"那条、[parentDashboardEnglishDropsTheMeasureWord] 是同一条处置，
     * 差别只在本页连单位字段都不需要开（英文侧没有单位槽）。
     * 两侧都写死，这样"顺手把英文补成 times / items"会被点名。
     */
    @Test
    fun settingsEnglishDropsTheMeasureWords() {
        val zh = AppStringsZh.settings
        val en = AppStringsEn.settings
        assertEquals("0次", zh.completionsValue(0))
        assertEquals("12个", zh.todayCheckinsValue(12))
        assertEquals("12", en.completionsValue(12))
        assertEquals("0", en.completionsValue(0))
        assertEquals("1", en.todayCheckinsValue(1))
        assertEquals("3", en.todayCheckinsValue(3))
        // 中文侧那两枚量词绝不能被误接成分钟单位（同形风险与 `分` 同源）：钉的是**量词字符本身**，
        // 不是"整串 != min"那种在纯数字英文侧恒真的比较（上一轮那条就是这种近真空断言）。
        assertNotEquals(zh.completionsValue(3), zh.progressFocusMinutesValue(3, AppStringsZh.common.minuteLabel))
        assertEquals('次', zh.completionsValue(3).last())
        assertEquals('个', zh.todayCheckinsValue(3).last())
        assertNotEquals(zh.completionsValue(3).last(), AppStringsZh.common.minuteLabel.single())
        assertNotEquals(zh.todayCheckinsValue(3).last(), AppStringsZh.common.minuteLabel.single())
        // 英文侧是**纯数字**：0..12 穷举，任何补出来的单位词（min / times / items）都会在这里点名红，
        // 未穷举的那些数也一并覆盖，不再依赖"上面那四条恰好写过 12 / 0 / 1 / 3"。
        (0..12).forEach { n ->
            assertEquals(n.toString(), en.completionsValue(n), "英文 completions 位只能是纯数字：$n")
            assertEquals(n.toString(), en.todayCheckinsValue(n), "英文 todayCheckins 位只能是纯数字：$n")
        }
    }

    /**
     * `:275` 的 `${cp.totalFocusMinutes}分` 是**分钟**，单位复用 [CommonStrings.minuteLabel]：
     * 本表**不**开任何 `分` 槽。这条穷举（"本表静态值里没有任何一条等于 分"）就是"没重开出处"的证据，
     * 形态与 [parentDashboardDurationUnitComesFromCommon] / [durationChipReusesTheCommonMinuteLabel] 一致；
     * 有人把分钟词抄进 SettingsStrings（哪怕只是 ZH 侧）都会在这里红，而不必等漏译守卫。
     */
    @Test
    fun settingsDurationUnitComesFromCommon() {
        assertEquals("分", AppStringsZh.common.minuteLabel)
        assertEquals("min", AppStringsEn.common.minuteLabel)
        val zhStatics = staticCopyValues(AppStringsZh.settings).toMap()
        assertEquals(
            emptySet(),
            zhStatics.filterValues { it == "分" }.keys,
            "SettingsStrings 出现 `分` 槽就说明分钟那条被从 CommonStrings 挪了过来，复用与残差登记要一起改",
        )
        assertEquals("45分", AppStringsZh.settings.progressFocusMinutesValue(45, AppStringsZh.common.minuteLabel))
        assertEquals("45 min", AppStringsEn.settings.progressFocusMinutesValue(45, AppStringsEn.common.minuteLabel))
        // 本页的时长侧绝不调积分词（`分` 三 senses 之一，另两种见 §11）
        assertFalse(
            AppStringsEn.settings.progressFocusMinutesValue(30, AppStringsEn.common.minuteLabel)
                .contains(pointsWord(2)),
            "专注时长里混进了积分词",
        )
    }

    /**
     * `:192` / `:431` 的 `"取消"` 复用 [CommonStrings.cancelButton]，本表**不**重开一个槽
     * （brief 明令"已在 CommonStrings 的必须复用"）。两条都是字面量钉死：
     * 换出处与互换英文值这两种错都只看得住"值 + 集合"这一对，assertDiffers 对互换是瞎的。
     */
    @Test
    fun settingsCancelButtonComesFromCommon() {
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
        assertEquals(
            emptySet(),
            staticCopyValues(AppStringsZh.settings).toMap().filterValues { it == "取消" }.keys,
            "SettingsStrings 里重新写了 取消，等于给同一个中文值开出第二处出处",
        )
    }

    /**
     * `:429` 的 `"确定"` **不是** [CommonStrings.confirmButton]（`确认`）：两个中文值差一个字、
     * 字节也不同，照 `确认` 复用会改掉中文态字节（brief 的逐字节等价优先），
     * 各写一份又留下两个英文词可以互相漂移。处置是 ZH 各自原样、EN 分词形（OK / Confirm），
     * 并在此逐字钉住四边；§11 已补同名词条。
     */
    @Test
    fun settingsOkButtonIsNotTheCommonConfirmButton() {
        assertEquals("确定", AppStringsZh.settings.passwordOkButton)
        assertEquals("OK", AppStringsEn.settings.passwordOkButton)
        assertEquals("确认", AppStringsZh.common.confirmButton)
        assertEquals("Confirm", AppStringsEn.common.confirmButton)
        assertNotEquals(AppStringsZh.settings.passwordOkButton, AppStringsZh.common.confirmButton)
        assertNotEquals(AppStringsEn.settings.passwordOkButton, AppStringsEn.common.confirmButton)
    }

    /**
     * §11"连续打卡 = Streak"在家长两个页面的**天数值槽**必须同词形：
     * [ParentHomeStrings.streakDaysValue]（ParentHomeScreen.kt:114）与本页
     * [SettingsStrings.streakDaysValue]（ParentSettingsScreen.kt:273）是两张表各存一份
     * 同一个概念（与 [parentDashboardScoreUnitIsNotPoints] 里"评分单位两张表各存一份、
     * 由断言而不是引用保证一致"同一选择，理由也相同：家长设置页不该反向依赖家长首页表）。
     */
    @Test
    fun settingsStreakDaysMatchParentHomeForm() {
        listOf(0, 1, 7, 10).forEach { days ->
            assertEquals(
                AppStringsEn.parentHome.streakDaysValue(days),
                AppStringsEn.settings.streakDaysValue(days),
                "同一个 Streak 天数槽在两张表念成两个词形：$days",
            )
            assertEquals(
                AppStringsZh.parentHome.streakDaysValue(days),
                AppStringsZh.settings.streakDaysValue(days),
            )
        }
        // 同一个 Streak 概念的行标签在两页也必须同词形（本页带一枚 🔥，取空格前那枚 emoji 之后比较，
        // 形态与 categoryWordsMatchTheTaskListFilters 一致）
        assertEquals(
            AppStringsEn.parentHome.overviewStreak,
            AppStringsEn.settings.progressStreakLabel.substringAfter(' '),
        )
        assertEquals(
            AppStringsZh.parentHome.overviewStreak,
            AppStringsZh.settings.progressStreakLabel.substringAfter(' '),
        )
    }

    /**
     * 存储边界（Ruling 25 / 31 / 34）：`:299 → :301 setDeviceName(name)` 的兜底值是**落库值**，
     * 保留迁移前的中文原字节，英文态只在只读渲染位换标签。
     * 三条各自的作用：
     * - 第 1 条钉住落库值本身没被"顺手翻译"（这条一红就意味着 DEVICE_NAME 按语言分裂）；
     * - 第 2 条钉住"规范值与显示字段同源"：表里那条 ZH 与常量必须一模一样，
     *   漂移就说明有人把其中一处改了而另一处没跟上（同一个缺陷形状见
     *   [focusTargetsAreStoredAsCanonicalTextAndLocalizedOnlyAtRender] 的"两个出处必须一模一样"）；
     * - 第 3 条钉住英文态确实给出英文标签，且认不出的输入原样透传（历史行不会被吞）。
     */
    @Test
    fun settingsDeviceNameFallbackStaysCanonical() {
        assertEquals("家长设备", CanonicalParentSettingsText.DEVICE_NAME_FALLBACK)
        assertEquals(
            CanonicalParentSettingsText.DEVICE_NAME_FALLBACK,
            AppStringsZh.settings.deviceNameFallbackLabel,
            "规范值与显示字段不同源：中文态那一格就会显示另一个串",
        )
        assertEquals("Parent device", AppStringsEn.settings.deviceNameFallbackLabel)
        assertEquals(
            "Parent device",
            AppStringsEn.settings.deviceNameDisplayLabel(CanonicalParentSettingsText.DEVICE_NAME_FALLBACK),
        )
        assertEquals("some phone", AppStringsEn.settings.deviceNameDisplayLabel("some phone"))
    }

    /**
     * 本页 emoji 全部在表内（Ruling 17 / Task 5 那条约定的整页版）：**22** 条静态 + 3 条函数型出场
     * （下面那个 `listOf` 共 25 对，逐对数过），两侧行首 emoji 必须同一枚。
     * 页面若拼 `"🔒 ${strings.x}"` 就等于把文案放回页面代码。
     */
    @Test
    fun emojiStaysInsideTheSettingsTable() {
        val zh = AppStringsZh.settings
        val en = AppStringsEn.settings
        listOf(
            zh.languageTitle to en.languageTitle,
            zh.pageTitle to en.pageTitle,
            zh.passwordCardTitle to en.passwordCardTitle,
            zh.weeklyReportTitle to en.weeklyReportTitle,
            zh.weeklyReportOnMessage to en.weeklyReportOnMessage,
            zh.backupCardTitle to en.backupCardTitle,
            zh.backupSuccessMessage to en.backupSuccessMessage,
            zh.restoreSuccessMessage to en.restoreSuccessMessage,
            zh.remoteControlTitle to en.remoteControlTitle,
            zh.pushStepNote to en.pushStepNote,
            zh.viewStepNote to en.viewStepNote,
            zh.transportNote to en.transportNote,
            zh.pushSettingsButton to en.pushSettingsButton,
            zh.viewProgressButton to en.viewProgressButton,
            zh.settingsExportedMessage to en.settingsExportedMessage,
            zh.progressImportedMessage to en.progressImportedMessage,
            zh.progressPointsLabel to en.progressPointsLabel,
            zh.progressStreakLabel to en.progressStreakLabel,
            zh.progressFocusLabel to en.progressFocusLabel,
            zh.progressCheckinLabel to en.progressCheckinLabel,
            zh.progressMistakesLabel to en.progressMistakesLabel,
            zh.progressTodayCheckinLabel to en.progressTodayCheckinLabel,
            zh.backupFailureMessage("x") to en.backupFailureMessage("x"),
            zh.restoreFailureMessage("x") to en.restoreFailureMessage("x"),
            zh.progressCardTitle("Phone") to en.progressCardTitle("Phone"),
        ).forEach { (chinese, english) ->
            assertEquals(
                chinese.substringBefore(' '), english.substringBefore(' '),
                "家长设置页行首 emoji 应两表一致：$chinese / $english",
            )
        }
    }

    /**
     * 家长密码门（`ui/navigation/AppNavGraph.kt` 的 RoleSelectDestination，终审 Ruling 41）三条字面量的
     * ZH≠EN 漏译守卫。这个对话框是**所有已迁移页面的入口**：英文态下它是英文 App 里的一面中文墙，
     * 所以它的文案必须与页面本体同源、同纪律。
     */
    @Test
    fun parentGateCopyDiffers() {
        assertDiffers(
            AppStringsZh.parentGate.passwordGateTitle to AppStringsEn.parentGate.passwordGateTitle,
            AppStringsZh.parentGate.passwordGateInputLabel to AppStringsEn.parentGate.passwordGateInputLabel,
            AppStringsZh.parentGate.passwordGateWrongPasswordError to
                AppStringsEn.parentGate.passwordGateWrongPasswordError,
        )
    }

    /**
     * 密码门三条 ZH 值与两个复用按钮的**逐字面量**钉子（`assertDiffers` 对"两边一起改"与
     * "同形异义互换"都是瞎的，理由见 [CommonStrings] 类注释那段）。
     *
     * 复用而非重造（Ruling 41 的后半句）：门里的 `确定` / `取消` **不新建字段**，
     * 分别取 [SettingsStrings.passwordOkButton] 与 [CommonStrings.cancelButton]。
     * 下面最后两条把这两条复用钉成"值必须逐字节等于那两个既有字段的值"，
     * 于是英文态要冒出第三种 OK/Confirm/Cancel 词形就会先在这里红。
     */
    @Test
    fun parentGateButtonsComeFromTheirOwnTables() {
        assertEquals("🔒 请输入家长密码", AppStringsZh.parentGate.passwordGateTitle)
        assertEquals("🔒 Enter parent password", AppStringsEn.parentGate.passwordGateTitle)
        assertEquals("家长密码", AppStringsZh.parentGate.passwordGateInputLabel)
        assertEquals("Parent password", AppStringsEn.parentGate.passwordGateInputLabel)
        assertEquals("密码错误，请重试", AppStringsZh.parentGate.passwordGateWrongPasswordError)
        assertEquals("Wrong password, please try again", AppStringsEn.parentGate.passwordGateWrongPasswordError)
        // 标题的装饰性 🔒 在表里、两侧同一枚（约定同 emojiStaysInsideTheSettingsTable）
        assertEquals(
            AppStringsZh.parentGate.passwordGateTitle.substringBefore(' '),
            AppStringsEn.parentGate.passwordGateTitle.substringBefore(' '),
        )
        assertEquals("确定", AppStringsZh.settings.passwordOkButton)
        assertEquals("取消", AppStringsZh.common.cancelButton)
    }

    /**
     * §11"累计专注 = Total focus"（终审 Ruling 44）：同一个中文值在 [FocusStrings] 与
     * [ParentDashboardStrings] 两张表里**必须同一个英文词形**。
     *
     * 本波次之前的状态是真实漂移：FocusStrings 写 "All-time focus"、ParentDashboardStrings 写
     * "Total focus"。`assertDiffers` 抓不到它（两边都不等于中文），跨表的"同词同形"必须先
     * **点名这两张表**才谈得上比对——形态与 [settingsStreakDaysMatchParentHomeForm] 一致。
     * 取值理由：同卡片相邻两格已是 Total points / Total check-ins。
     */
    @Test
    fun totalFocusLabelIsTheSameTwoWordsInBothTables() {
        assertEquals("累计专注", AppStringsZh.focus.totalFocusLabel)
        assertEquals("Total focus", AppStringsEn.focus.totalFocusLabel)
        assertEquals("累计专注", AppStringsZh.parentDashboard.totalFocusLabel)
        assertEquals("Total focus", AppStringsEn.parentDashboard.totalFocusLabel)
        assertEquals(
            AppStringsEn.parentDashboard.totalFocusLabel,
            AppStringsEn.focus.totalFocusLabel,
            "同一个「累计专注」在两张表念成两个词形（Ruling 44 已统一为 Total focus）",
        )
        assertEquals(
            AppStringsZh.parentDashboard.totalFocusLabel,
            AppStringsZh.focus.totalFocusLabel,
        )
    }

    /**
     * 英文侧省略号只有一个形状（终审 Ruling 44）：`…`（U+2026），不写三个句点。
     *
     * 中文侧**两种都有**且必须保持原字节（`正在处理…` 与 `备份中...` 来自迁移前的不同行），
     * 所以本断言只扫英文表。上限要说清楚：data class 的 `toString()` 不含函数型字段的返回值
     * （Ruling 20-b 的老盲区），因此这条只覆盖静态 String 字段；函数型英文值靠各自那段
     * 逐字双语期望串（`settingsInterpolatedCopyPinsBothLanguages` 等）里的字面量承担。
     */
    @Test
    fun englishTableUsesOneEllipsisForm() {
        val rendered = AppStringsEn.toString()
        assertFalse(
            rendered.contains("..."),
            "英文表里出现三个句点：本仓英文表统一用 U+2026（Ruling 44）",
        )
        assertTrue(rendered.contains('…'), "英文表应至少有一条 U+2026 省略号，否则上面那条断言是装饰")
    }
}
