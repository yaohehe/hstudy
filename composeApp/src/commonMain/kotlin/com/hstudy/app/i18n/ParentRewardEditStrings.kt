package com.hstudy.app.i18n

/**
 * 家长奖励编辑页（ui/parent/ParentRewardEditScreen.kt）文案表（阶段 2 W3）。
 *
 * **存储边界（Ruling 25）**：`categoryOptions`（娱乐/美食/出行/特权/实物/社交/其他）与默认值
 * `"其他"` 是 `RewardEntity.category` 的落库值，**留在页面、不进本表**；渲染（下拉选项 Text 与
 * 选中值 OutlinedTextField.value）包 [localizedPresetText]，W0 注册表已收录全部 7 条
 * （PresetRegistry.kt:201-208，"其他"与错题类型共享 key）。`RewardEntity(category = category)`
 * 写入的仍是 canonical 原值。等价闸门里这 7 条是页面单边残差：`其他` 与 MistakeStrings.subjectOther
 * 两侧同形自然配平，其余 6 条在 DISCLOSED_RESIDUALS 登记（W3 Task 5）。
 * 「保存」与 W2 [ParentTaskEditStrings.saveButton] 同值同译，跨表测试钉住（不上收 CommonStrings，
 * 沿用 W2「编辑」先例；统一上收是 W9 sweep 候选）。
 */
data class ParentRewardEditStrings(
    val newTitle: String,
    val editTitle: String,
    val saveButton: String,
    val presetHint: String,
    val nameLabel: String,
    val descriptionLabel: String,
    val costLabel: String,
    val categoryHeading: String,
    val categoryPickerLabel: String,
    val iconHeading: String,
    val deleteButton: String,
)

val ParentRewardEditStringsZh = ParentRewardEditStrings(
    newTitle = "新建奖励",                                                     // :53
    editTitle = "编辑奖励",                                                    // :53
    saveButton = "保存",                                                       // :72
    presetHint = "💡 常见的奖励（看电影、零食、玩耍等）已预设在奖励列表中，可直接返回修改积分。此处用于添加全新的自定义奖励。", // :91
    nameLabel = "奖励名称",                                                    // :97
    descriptionLabel = "奖励描述",                                             // :100
    costLabel = "所需积分",                                                    // :103
    categoryHeading = "奖励分类",                                              // :107
    categoryPickerLabel = "选择分类",                                          // :117
    iconHeading = "选择图标",                                                  // :138
    deleteButton = "删除此奖励",                                               // :157
)

val ParentRewardEditStringsEn = ParentRewardEditStrings(
    newTitle = "New reward",
    editTitle = "Edit reward",
    saveButton = "Save",
    presetHint = "💡 Common rewards (movies, snacks, playtime, etc.) are already preset in the reward list — go back and adjust their point costs there. Use this page to add a brand-new custom reward.",
    nameLabel = "Reward name",
    descriptionLabel = "Reward description",
    costLabel = "Points required",
    categoryHeading = "Reward category",
    categoryPickerLabel = "Choose a category",
    iconHeading = "Choose an icon",
    deleteButton = "Delete this reward",
)
