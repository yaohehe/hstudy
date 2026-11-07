package com.hstudy.app.i18n

/**
 * 孩子任务列表页（ui/child/ChildTaskListScreen）文案表（Task 8）。
 *
 * ZH 侧 16 条逐字来自该文件迁移前的第 63/64/71/96/97/98/99/128/130/139/140/243/273/277/281/294 行，
 * 与页面字面量一一对应（本页没有"同形异义"需要拆字段的情况，见 CommonStrings 的反例）。
 * 迁移后这些行只剩 `strings.<field>` / `strings.<fn>(args)`，页面代码内 CJK 字面量 0 条。
 * 迁移前的第 47 行「积分飞入」在 `//` 注释里，按 Ruling 18 不是可迁移文案，留在原处。
 *
 * 术语按设计文档 §11：积分 = Points、打卡 = Check in / Checked in、家长审核 = Approval、
 * 任务 = Task、奖励 = Reward、习惯 = Habits。两处按英文而非中文句式重写：
 * - "✅ 打卡成功！+N分待家长审核" → "Checked in! +N points pending approval"
 *   （中文的"待家长审核"是动宾补语挂在句尾，英文要变成后置定语，不能写成 "…need parent to wait audit"）
 * - "已完成(点击撤销)" → "Checked in (tap to undo)"：读屏文本按英文惯例用括号给出下一步动作。
 * 分类芯片的"提升"译作 **Advanced**（与 "Basic" 成对，英文里这才是同一量级轴上的两个词）；
 * 字段名沿用被筛选的 `TaskCategory.ENHANCEMENT`，便于回溯源值。后续页面（ParentDashboard、
 * WeeklyTracker、ParentTaskManage 等）迁移同一概念时应复用 Basic / Advanced，避免一词多译。
 *
 * 7 个函数型字段（[undoClawedBackMessage]、[undoPendingApprovalMessage]、[achievementUnlockedMessage]、
 * [checkInSuccessMessage]、[checkInUndoPrompt]、[pointsEarnedLabel]、[bonusPointsLabel]）：
 * 设计文档 §3 规定带参数文案存函数而不是 `{0}` 模板，因为中英语序不同、位置插值会写出洋泾浜。
 * [achievementUnlockedMessage] 收 `List<String>` 而不是拼好的串，是因为分隔符本身属于文案：
 * 中文用 "、"、英文用 ", "，留在页面里就会在英文态输出 "Blast Off、周冠军"。
 *
 * 函数型字段不在 data class 的 `toString()` 里，因此整表 CJK 扫描（[StringsCopyTest]）看不见它们；
 * 补偿是 `StringsCopyTest.taskListInterpolatedCopyPinsBothLanguages` 与
 * `taskListEnglishPluralsCoverOneAndOther` 的逐字期望串。英文的 one/other 用函数分支表达（§8.3），
 * 由下面这个私有 [pointsWord] 统一，避免五处各写一遍 `if`。中文无单复数，不共用该函数。
 */
data class TaskListStrings(
    val checkInTitle: String,
    val filterAll: String,
    val filterBasic: String,
    val filterEnhancement: String,
    val filterHabits: String,
    val undoButton: String,
    val completedContentDescription: String,
    val mandatoryBadge: String,
    val proofButton: String,
    val undoClawedBackMessage: (title: String, points: Int) -> String,
    val undoPendingApprovalMessage: (title: String) -> String,
    val achievementUnlockedMessage: (titles: List<String>) -> String,
    val checkInSuccessMessage: (points: Int) -> String,
    val checkInUndoPrompt: (title: String, points: Int) -> String,
    val pointsEarnedLabel: (points: Int) -> String,
    val bonusPointsLabel: (points: Int) -> String,
)

/** 英文积分单位的名词形式：1 用单数 point，其余用 points（spec §3：英文只做 one/other）。 */
private fun pointsWord(points: Int): String = if (points == 1) "point" else "points"

val TaskListStringsZh = TaskListStrings(
    checkInTitle = "🎯 今日打卡",
    filterAll = "全部",
    filterBasic = "基础",
    filterEnhancement = "提升",
    filterHabits = "习惯",
    undoButton = "撤销",
    completedContentDescription = "已完成(点击撤销)",
    mandatoryBadge = "⚠必须",
    proofButton = "📷 证明",
    undoClawedBackMessage = { title, points -> "↩️ 已撤销 $title，扣回 $points 分" },
    undoPendingApprovalMessage = { title -> "↩️ 已撤销 $title（尚未审批，无积分变动）" },
    achievementUnlockedMessage = { titles -> "🏅 解锁成就：${titles.joinToString("、")}！" },
    checkInSuccessMessage = { points -> "✅ 打卡成功！+${points}分待家长审核" },
    checkInUndoPrompt = { title, points -> "✅ $title +${points}分（待审核）" },
    pointsEarnedLabel = { points -> "+${points}分" },
    bonusPointsLabel = { points -> "奖励+${points}分" },
)

val TaskListStringsEn = TaskListStrings(
    checkInTitle = "🎯 Today's Check-in",
    filterAll = "All",
    filterBasic = "Basic",
    filterEnhancement = "Advanced",
    filterHabits = "Habits",
    undoButton = "Undo",
    completedContentDescription = "Checked in (tap to undo)",
    mandatoryBadge = "⚠ Must-do",
    proofButton = "📷 Proof",
    undoClawedBackMessage = { title, points ->
        "↩️ Check-in undone for $title · $points ${pointsWord(points)} removed"
    },
    undoPendingApprovalMessage = { title ->
        "↩️ Check-in undone for $title (not approved yet, so no points changed)"
    },
    achievementUnlockedMessage = { titles ->
        if (titles.size == 1) "🏅 Achievement unlocked: ${titles.first()}!"
        else "🏅 Achievements unlocked: ${titles.joinToString(", ")}!"
    },
    checkInSuccessMessage = { points -> "✅ Checked in! +$points ${pointsWord(points)} pending approval" },
    checkInUndoPrompt = { title, points -> "✅ $title +$points ${pointsWord(points)} (pending approval)" },
    pointsEarnedLabel = { points -> "+$points ${pointsWord(points)}" },
    bonusPointsLabel = { points -> "Reward +$points ${pointsWord(points)}" },
)
