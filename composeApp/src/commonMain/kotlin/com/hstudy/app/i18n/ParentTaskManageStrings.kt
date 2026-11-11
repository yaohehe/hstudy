package com.hstudy.app.i18n

/**
 * 家长任务管理页（ui/parent/ParentTaskManageScreen.kt）文案表（阶段 2 W2）。
 *
 * 复用而非新建（同一中文值一个英文出处）：
 * - 标题「任务管理」= [ParentHomeStrings.menuTaskManageTitle]（家长首页菜单已译 "Task management"）；
 * - 分类筛选「全部/基础/提升/习惯」= [TaskListStrings.filterAll/filterBasic/filterEnhancement/filterHabits]；
 * - 「删除」= [CommonStrings.deleteContentDescription]；「编辑」无既有共享字段，本表新建
 *   [editContentDescription]（与 W1 [MistakeDetailStrings.editContentDescription] 同值同译，跨表测试钉住）。
 * 本表新增 3 个字段：添加/编辑 contentDescription 与积分行（"基础 N分 · 奖励 +N分"，英文 one/other 用 [pointsWord]）。
 * 任务标题/描述是 canonical/用户数据，渲染点包 [localizedPresetText]，不进表。
 */
data class ParentTaskManageStrings(
    val addContentDescription: String,
    val editContentDescription: String,
    val pointsLine: (base: Int, bonus: Int) -> String,
)

val ParentTaskManageStringsZh = ParentTaskManageStrings(
    addContentDescription = "添加任务",
    editContentDescription = "编辑",
    pointsLine = { base, bonus -> "基础 ${base}分${if (bonus > 0) " · 奖励 +${bonus}分" else ""}" },
)

val ParentTaskManageStringsEn = ParentTaskManageStrings(
    addContentDescription = "Add task",
    editContentDescription = "Edit",
    pointsLine = { base, bonus ->
        "Base $base ${pointsWord(base)}${if (bonus > 0) " · Bonus +$bonus ${pointsWord(bonus)}" else ""}"
    },
)