package com.hstudy.app.i18n

/**
 * 孩子端成就中心页（ui/child/ChildAchievementsScreen.kt）文案表（阶段 2 W4）。
 *
 * 成就标题/描述是键控内容：渲染走 W0 注册表 [achievementCopy]（键 = def.type，13 条两种语言，
 * 覆盖性由 PresetRegistryTest.achievementCopyCoversEveryDefTypeInBothLanguages 保证）；
 * 未命中时回落 canonical `def.title` / `def.description`（防御，正常不会发生）。
 * 进度数字 `$cur/$target` 是纯数字格式，不本地化。
 */
data class ChildAchievementsStrings(
    val title: String,
    val myAchievements: String,
    val allUnlocked: String,
    val keepGoing: String,
)

val ChildAchievementsStringsZh = ChildAchievementsStrings(
    title = "🏅 成就中心",                                                     // :41
    myAchievements = "🏅 我的成就",                                            // :56
    allUnlocked = "🎉 太厉害了！已解锁全部成就！",                              // :67
    keepGoing = "继续加油，解锁更多成就勋章！",                                 // :68
)

val ChildAchievementsStringsEn = ChildAchievementsStrings(
    title = "🏅 Achievement Center",
    myAchievements = "🏅 My achievements",
    allUnlocked = "🎉 Amazing — you've unlocked every achievement!",
    keepGoing = "Keep going and unlock more achievement badges!",
)
