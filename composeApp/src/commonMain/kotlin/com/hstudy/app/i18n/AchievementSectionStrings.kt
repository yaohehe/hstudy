package com.hstudy.app.i18n

/**
 * 成就徽章区（`ui/common/AchievementManager.kt` 的 `AchievementBadges`）文案表（阶段 2 W8 T3）。
 *
 * 本表只收徽章区的三条骨架文案。徽章的标题/描述**不进本表**：渲染走
 * `achievementCopy[lang]?.get(type)`（W0 注册表，键是稳定业务键 `AchievementDef.type`），
 * 未命中回落实体/定义里的原标题（Ruling 25：注册表只做显示取词，不参与存储）——已解锁 chip
 * 用实体 `achievement.type`，待解锁 chip 用定义 `def.type`；进度 `$cur/$target` 是纯数字拼接，
 * 不随语言变化。
 *
 * ZH 值逐字节等于迁移前 `AchievementBadges`（`:156`/`:160`/`:175`）的字面量；
 * 两语由 `W8CopyTest.achievementSectionCopyDiffers` 与 `StringsCopyTest` 的整表扫描兜住。
 */
data class AchievementSectionStrings(
    val sectionTitle: String,
    val emptyLocked: String,
    val lockedTitle: String,
)

val AchievementSectionStringsZh = AchievementSectionStrings(
    sectionTitle = "🏅 成就徽章",               // :156
    emptyLocked = "还没有解锁任何成就，加油吧！",  // :160
    lockedTitle = "🔒 待解锁（加油达成）",        // :175
)

val AchievementSectionStringsEn = AchievementSectionStrings(
    sectionTitle = "🏅 Achievement badges",
    emptyLocked = "No achievements unlocked yet — keep going!",
    lockedTitle = "🔒 Locked (keep working on them)",
)
