package com.hstudy.app.i18n

/**
 * 家长布置任务页（ui/parent/ParentAssignTaskScreen.kt）文案表（阶段 2 W2）。
 *
 * 任务标题的两种来源都走注册表：建议芯片 label 与已布置列表里的 a.title 都是 canonical
 * （芯片来自 getSingleTaskSuggestions，列表里的标题若是从模板快选写入的预设，未改过就还是 canonical），
 * 渲染点一律包 [localizedPresetText]——预设标题译出，家长手输的标题未收录、原样通过。
 * onClick 写 state 用原值（Ruling 25），a.parentNote 与编辑框里的用户输入原样显示。
 * 「删除」contentDescription 复用 [CommonStrings.deleteContentDescription]。
 */
data class ParentAssignTaskStrings(
    val title: String,
    val formHeading: String,
    val templateQuickSelect: String,
    val suggestionChip: (title: String, points: Int) -> String,
    val titleLabel: String,
    val titlePlaceholder: String,
    val descriptionLabel: String,
    val pointsLabel: String,
    val noteLabel: String,
    val notePlaceholder: String,
    val assignedMessage: String,
    val assignButton: String,
    val todayHeading: (count: Int) -> String,
    val emptyState: String,
    val pointsBadge: (points: Int) -> String,
    val completedStatus: String,
    val pendingStatus: String,
    val deletedMessage: String,
)

val ParentAssignTaskStringsZh = ParentAssignTaskStrings(
    title = "📣 布置今日任务",
    formHeading = "✍️ 布置新任务",
    templateQuickSelect = "从模板快速选择：",
    suggestionChip = { title, points -> "$title +$points" },
    titleLabel = "任务标题",
    titlePlaceholder = "如：今天帮妈妈洗碗",
    descriptionLabel = "任务描述（选填）",
    pointsLabel = "积分值",
    noteLabel = "鼓励语（选填）",
    notePlaceholder = "如：相信你一定能做到！",
    assignedMessage = "✅ 任务已布置给孩子！",
    assignButton = "📤 布置给孩子",
    todayHeading = { count -> "📋 今日已布置 ($count)" },
    emptyState = "今天还没有布置任务",
    pointsBadge = { points -> "+${points}分" },
    completedStatus = "✅ 孩子已完成",
    pendingStatus = "⏳ 待完成",
    deletedMessage = "已删除任务",
)

val ParentAssignTaskStringsEn = ParentAssignTaskStrings(
    title = "📣 Assign today's task",
    formHeading = "✍️ New assignment",
    templateQuickSelect = "Quick pick from templates:",
    suggestionChip = { title, points -> "$title +$points" },
    titleLabel = "Task title",
    titlePlaceholder = "e.g. Help Mom wash the dishes today",
    descriptionLabel = "Description (optional)",
    pointsLabel = "Points",
    noteLabel = "Encouragement (optional)",
    notePlaceholder = "e.g. I believe you can do it!",
    assignedMessage = "✅ Task assigned to your child!",
    assignButton = "📤 Assign to child",
    todayHeading = { count -> "📋 Assigned today ($count)" },
    emptyState = "No tasks assigned yet today",
    pointsBadge = { points -> "+$points ${pointsWord(points)}" },
    completedStatus = "✅ Completed by child",
    pendingStatus = "⏳ Pending",
    deletedMessage = "Task deleted",
)