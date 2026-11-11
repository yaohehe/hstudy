package com.hstudy.app.i18n

/**
 * 家长积分模板页（ui/parent/ParentTaskTemplateScreen.kt）文案表（阶段 2 W2）。
 *
 * 模板名/描述/年龄段（TaskTemplate.name/description/ageRange）与预览任务标题都是 canonical 种子，
 * W0 注册表已收录（presetTextRegistry），渲染点包 [localizedPresetText]；未命中（家长改过的）原样。
 * 分类计数芯片「📝 基础×N」英文词形与 [TaskListStrings.filterBasic] 等一致（Basic/Advanced/Habits），
 * 由 crossTableVocabularyPinned 钉住。「取消」复用 [CommonStrings.cancelButton]。
 */
data class ParentTaskTemplateStrings(
    val title: String,
    val usageHeading: String,
    val usageHint: String,
    val currentTaskCount: (count: Int) -> String,
    val applyDialogTitle: (emoji: String, name: String) -> String,
    val replaceHint: (count: Int) -> String,
    val previewHeading: String,
    val previewLine: (emoji: String, title: String, points: Int) -> String,
    val moreTasks: (count: Int) -> String,
    val appliedMessage: (name: String) -> String,
    val confirmButton: String,
    val applyButton: String,
    val basicCount: (count: Int) -> String,
    val enhancementCount: (count: Int) -> String,
    val habitCount: (count: Int) -> String,
)

val ParentTaskTemplateStringsZh = ParentTaskTemplateStrings(
    title = "📋 积分模板",
    usageHeading = "💡 使用说明",
    usageHint = "选择一个适合孩子年龄的模板，一键应用到任务列表。应用后会替换当前所有任务。",
    currentTaskCount = { count -> "当前共有 $count 个任务" },
    applyDialogTitle = { emoji, name -> "$emoji 应用「$name」模板？" },
    replaceHint = { count -> "将替换当前所有任务为模板预设的 $count 个任务。" },
    previewHeading = "任务预览:",
    previewLine = { emoji, title, points -> "$emoji $title (+${points}分)" },
    moreTasks = { count -> "...还有${count}个任务" },
    appliedMessage = { name -> "✅ 已应用「$name」模板" },
    confirmButton = "确认应用",
    applyButton = "一键应用此模板",
    basicCount = { count -> "📝 基础×$count" },
    enhancementCount = { count -> "📈 提升×$count" },
    habitCount = { count -> "🏆 习惯×$count" },
)

val ParentTaskTemplateStringsEn = ParentTaskTemplateStrings(
    title = "📋 Point templates",
    usageHeading = "💡 How to use",
    usageHint = "Pick a template that fits your child's age and apply it in one tap. Applying replaces all current tasks.",
    currentTaskCount = { count -> "You currently have $count ${tasksWord(count)}" },
    applyDialogTitle = { emoji, name -> "$emoji Apply the “$name” template?" },
    replaceHint = { count -> "This replaces all current tasks with the template's $count ${tasksWord(count)}." },
    previewHeading = "Task preview:",
    previewLine = { emoji, title, points -> "$emoji $title (+$points ${pointsWord(points)})" },
    moreTasks = { count -> "...and $count more ${tasksWord(count)}" },
    appliedMessage = { name -> "✅ Applied the “$name” template" },
    confirmButton = "Apply",
    applyButton = "Apply this template",
    basicCount = { count -> "📝 Basic ×$count" },
    enhancementCount = { count -> "📈 Advanced ×$count" },
    habitCount = { count -> "🏆 Habits ×$count" },
)
