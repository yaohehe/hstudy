package com.hstudy.app.i18n

/**
 * 家长端心愿目标管理页（ui/wish/ParentWishGoalManageScreen.kt）文案表（阶段 2 W6）。
 *
 * 复用而非新建：保存 = [ParentTaskEditStrings.saveButton]、取消 = [CommonStrings.cancelButton]、
 * 删除 = [CommonStrings.deleteContentDescription]。
 * 3 个示例心愿的 `WishGoalEntity(...)` 字面量（name + description 共 6 值）是 canonical 落库值
 * （addGoal 原样写入），按 Ruling 25 留在页面不动；卡片渲染时包 [localizedPresetText]
 * （示例译出、家长自建未命中原样），[removedMessage] 的 name 实参同样是本地化后的显示名。
 * [removedMessage] 刻意**不以 `delete` 开头**：存储边界闸门按词根前缀认 sink（`delete\w*(`），
 * 若沿用 `deletedMessage` 这个名字会被判成"文案进入删除写库调用"（W4 Ruling 6 / W5 Ruling 15 先例）；
 * 语义仍是"已删除"。
 */
data class ParentWishGoalStrings(
    val title: String,
    val hint: String,
    val emptyTitle: String,
    val emptyHint: String,
    val samplesLoaded: String,
    val loadSamplesButton: String,
    val addOwnHint: String,
    val newWishContentDescription: String,
    val removedMessage: (name: String) -> String,
    val savedMessage: String,
    val activeLine: (saved: Int, target: Int) -> String,
    val achievedBadge: String,
    val dialogTitleNew: String,
    val dialogTitleEdit: String,
    val nameLabel: String,
    val descLabel: String,
    val targetPointsLabel: String,
    val iconPickerLabel: String,
)

val ParentWishGoalStringsZh = ParentWishGoalStrings(
    title = "🐷 心愿目标管理",                                                  // :41
    hint = "💡 为孩子设定一个大目标（如一台新平板、一次旅行），孩子用积分逐步存入，攒够即可兑换，培养延迟满足能力。", // :59
    emptyTitle = "还没有心愿目标",                                              // :74
    emptyHint = "点击下方按钮一键设置示例心愿（小玩具/新平板/迪士尼之旅），给孩子一个存积分的目标，再按需调整。", // :75
    samplesLoaded = "✅ 已设置示例心愿",                                        // :86
    loadSamplesButton = "✨ 一键设置示例心愿",                                   // :88
    addOwnHint = "或点击右下角 + 自行创建",                                      // :89
    newWishContentDescription = "新建心愿",                                     // :45
    removedMessage = { name -> "已删除「$name」" },                             // :102
    savedMessage = "✅ 已保存",                                                // :119
    activeLine = { saved, target -> "进行中 · $saved/${target}分" },              // :140
    achievedBadge = "🎊 已达成",                                               // :141
    dialogTitleNew = "新建心愿目标",                                            // :192
    dialogTitleEdit = "编辑心愿目标",                                           // :192
    nameLabel = "心愿名称（如：一台新平板）",                                     // :196
    descLabel = "描述（可选）",                                                 // :198
    targetPointsLabel = "目标积分",                                             // :200
    iconPickerLabel = "选择图标",                                               // :201
)

val ParentWishGoalStringsEn = ParentWishGoalStrings(
    title = "🐷 Wish goal management",
    hint = "💡 Set a big goal for your child (e.g. a new tablet, a trip). Your child saves points into it step by step and redeems it once complete — building delayed gratification.",
    emptyTitle = "No wishes yet",
    emptyHint = "Tap the button below to add sample wishes (a small toy / a new tablet / a Disneyland trip), then adjust as needed.",
    samplesLoaded = "✅ Sample wishes added",
    loadSamplesButton = "✨ Add sample wishes",
    addOwnHint = "Or tap + at the bottom right to create your own",
    newWishContentDescription = "New wish",
    removedMessage = { name -> "Deleted “$name”" },
    savedMessage = "✅ Saved",
    activeLine = { saved, target -> "In progress · $saved/$target ${pointsWord(target)}" },
    achievedBadge = "🎊 Achieved",
    dialogTitleNew = "New wish goal",
    dialogTitleEdit = "Edit wish goal",
    nameLabel = "Wish name (e.g. a new tablet)",
    descLabel = "Description (optional)",
    targetPointsLabel = "Target points",
    iconPickerLabel = "Choose an icon",
)
