package com.hstudy.app.i18n

/**
 * 错题详情页（ui/mistake/ChildMistakeDetailScreen.kt）文案表（阶段 2 W1）。
 *
 * 迁移前（`4d28c8e`）全部 UI 字面量入表；**五条落库原文不迁**（Ruling 25，见 Task 6 披露登记）：
 * `"主动回忆-做对"` / `"主动回忆-没记住"`（reviewMistake note）、`"错题复习做对: ${title}"` /
 * `"错题复习: ${title}"`（submitApproval description）、`"举一反三思考: ${title}"`
 * （submitApproval description）。
 *
 * 形状要点：
 * - [screenTitle] 收 emoji（科目 emoji 由页面 `when` 求得），中文模板 `"$emoji 错题详情"` 的
 *   折叠形状与迁移前页面字面量一致（等价闸门按折叠模板判等）；
 * - [reviewHistoryLine] 的积分单位复用 [pointsWord]（英文 one/other）；
 * - 15 条举一反三引导问题按科目分组存 [variationPromptsMath]…[variationPromptsOther]，
 *   每组 3 条、顺序与页面一致（中文逐项钉死在 MistakeCopyTest）；
 * - 通用按钮词「取消」「删除」不在此表重复：页面复用 `LocalStrings.current.common`
 *   （`cancelButton` / `deleteContentDescription`，阶段 1 已上收）。
 * - `"✅ 已掌握"` / `"⏳ 未掌握"` / `"错题照片"` 复用共享表 [MistakeStrings]。
 */
data class MistakeDetailStrings(
    val screenTitle: (emoji: String) -> String,
    val editContentDescription: String,
    val editHeading: String,
    val titleLabel: String,
    val contentLabel: String,
    val wrongReasonLabel: String,
    val correctMethodLabel: String,
    val errorTypeLabel: String,
    val updatedMessage: String,
    val saveButton: String,
    val addedOn: (date: String) -> String,
    val contentHeading: String,
    val recallHeading: String,
    val recallHint: String,
    val wrongReasonHeading: String,
    val correctMethodHeading: String,
    val photoHeading: String,
    val masteredCard: String,
    val revealButton: String,
    val selfCheckPrompt: String,
    val gotItButton: String,
    val missedButton: String,
    val gotItMessage: String,
    val missedMessage: String,
    val reviewDoneCard: String,
    val variationTitle: String,
    val variationDone: String,
    val variationPromptsMath: List<String>,
    val variationPromptsChinese: List<String>,
    val variationPromptsEnglish: List<String>,
    val variationPromptsScience: List<String>,
    val variationPromptsOther: List<String>,
    val variationThink: String,
    val variationNotesHeading: String,
    val variationInputLabel: String,
    val variationDoneMessage: String,
    val variationButton: String,
    val reviewHistoryTitle: (count: Int) -> String,
    val reviewHistoryLine: (date: String, points: Int) -> String,
    val deleteDialogTitle: String,
    val deleteDialogText: (title: String) -> String,
)

val MistakeDetailStringsZh = MistakeDetailStrings(
    screenTitle = { emoji -> "$emoji 错题详情" },
    editContentDescription = "编辑",
    editHeading = "✏️ 编辑错题",
    titleLabel = "标题",
    contentLabel = "题目内容",
    wrongReasonLabel = "错误原因",
    correctMethodLabel = "正确解法",
    errorTypeLabel = "错误类型",
    updatedMessage = "✅ 错题已更新",
    saveButton = "保存修改",
    addedOn = { date -> "添加于 $date" },
    contentHeading = "📋 题目描述",
    recallHeading = "先自己回想一下这道题怎么做",
    recallHint = "主动回忆比直接看答案记得更牢！想好了再点击下方按钮核对。",
    wrongReasonHeading = "❌ 错误原因",
    correctMethodHeading = "✅ 正确解法",
    photoHeading = "📷 题目照片",
    masteredCard = "✅ 这道题已掌握，真棒！偶尔回顾一下保持记忆。",
    revealButton = "👀 我想好了，显示答案核对",
    selfCheckPrompt = "📝 对照答案，你这次做对了吗？",
    gotItButton = "✅ 做对了 (+5)",
    missedButton = "❌ 没记住 (+2)",
    gotItMessage = "🎉 做对了！+3分已提交家长审核",
    missedMessage = "💪 +1分已提交审核，多复习几次就记住了",
    reviewDoneCard = "✅ 本次复习完成！系统会按遗忘曲线自动安排下次复习时间。",
    variationTitle = "💡 举一反三",
    variationDone = "✅ 已完成",
    variationPromptsMath = listOf(
        "这道题考查的是什么知识点？",
        "改变数字或条件，你还会解吗？",
        "你能自己编一道类似的题吗？",
    ),
    variationPromptsChinese = listOf(
        "这道题涉及哪些字词/知识点？",
        "换个语境你还能用对吗？",
        "你能造一个类似的句子吗？",
    ),
    variationPromptsEnglish = listOf(
        "这道题的语法/单词重点是什么？",
        "换个时态/句型你还会吗？",
        "你能用这个知识点造个句吗？",
    ),
    variationPromptsScience = listOf(
        "这道题背后的科学原理是什么？",
        "改变实验条件会怎样？",
        "生活中还有哪些类似现象？",
    ),
    variationPromptsOther = listOf(
        "这道题的核心要点是什么？",
        "换个角度你会怎么思考？",
        "你能举一个类似的例子吗？",
    ),
    variationThink = "🤔 试着想一想：",
    variationNotesHeading = "📝 我的举一反三笔记：",
    variationInputLabel = "写下你的思考/变式题（可选）",
    variationDoneMessage = "💡 举一反三完成 +3分已提交家长审核",
    variationButton = "💡 完成举一反三思考 (+3分)",
    reviewHistoryTitle = { count -> "复习记录 ($count)" },
    reviewHistoryLine = { date, points -> "$date +${points}分" },
    deleteDialogTitle = "删除错题",
    deleteDialogText = { title -> "确定要删除「$title」吗？删除后不可恢复。" },
)

val MistakeDetailStringsEn = MistakeDetailStrings(
    screenTitle = { emoji -> "$emoji Mistake Details" },
    editContentDescription = "Edit",
    editHeading = "✏️ Edit mistake",
    titleLabel = "Title",
    contentLabel = "Question",
    wrongReasonLabel = "What went wrong",
    correctMethodLabel = "Correct solution",
    errorTypeLabel = "Error type",
    updatedMessage = "✅ Mistake updated",
    saveButton = "Save changes",
    addedOn = { date -> "Added on $date" },
    contentHeading = "📋 Question",
    recallHeading = "Try to recall how to solve this first",
    recallHint = "Active recall beats re-reading — you'll remember far better. When you're ready, tap below to check your answer.",
    wrongReasonHeading = "❌ What went wrong",
    correctMethodHeading = "✅ Correct solution",
    photoHeading = "📷 Photo of the question",
    masteredCard = "✅ You've mastered this one — great job! Review it now and then to keep it fresh.",
    revealButton = "👀 I'm ready — show the answer",
    selfCheckPrompt = "📝 Check your answer — did you get it right?",
    gotItButton = "✅ Got it right (+5)",
    missedButton = "❌ Didn't remember (+2)",
    gotItMessage = "🎉 Right! +3 points submitted for parent approval",
    missedMessage = "💪 +1 point submitted for approval — a few more reviews and you'll remember it",
    reviewDoneCard = "✅ Review complete! The next review is scheduled on the forgetting curve.",
    variationTitle = "💡 Variation",
    variationDone = "✅ Done",
    variationPromptsMath = listOf(
        "What concept does this question test?",
        "Can you solve it with different numbers or conditions?",
        "Can you make up a similar question yourself?",
    ),
    variationPromptsChinese = listOf(
        "Which words or concepts does this question involve?",
        "Can you use them correctly in a different context?",
        "Can you write a similar sentence?",
    ),
    variationPromptsEnglish = listOf(
        "What grammar or vocabulary point does this question test?",
        "Can you handle it in a different tense or sentence pattern?",
        "Can you make a sentence with this point?",
    ),
    variationPromptsScience = listOf(
        "What science principle is behind this question?",
        "What if the experiment conditions changed?",
        "Where else in daily life do you see similar phenomena?",
    ),
    variationPromptsOther = listOf(
        "What is the core idea of this question?",
        "How would you think about it from another angle?",
        "Can you give a similar example?",
    ),
    variationThink = "🤔 Think about it:",
    variationNotesHeading = "📝 My variation notes:",
    variationInputLabel = "Write your thoughts or a variation question (optional)",
    variationDoneMessage = "💡 Variation complete — +3 points submitted for parent approval",
    variationButton = "💡 Complete variation thinking (+3 points)",
    reviewHistoryTitle = { count -> "Review history ($count)" },
    reviewHistoryLine = { date, points -> "$date +$points ${pointsWord(points)}" },
    deleteDialogTitle = "Delete mistake",
    deleteDialogText = { title -> "Delete “$title”? This cannot be undone." },
)
