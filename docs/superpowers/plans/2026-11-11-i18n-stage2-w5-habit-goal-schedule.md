# i18n 阶段 2 W5——习惯/目标/日程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** W5 六源双语化：孩子每日目标/家长日程管理/孩子习惯挑战/孩子目标追踪/孩子时间线五页 + `ScheduleAlarmHelper` 通知文案；注册表扩 19 条页面内预设（目标标题 8 / 复盘标签 6 / 日程示例 5），6 张新表进 `AppStrings`（28→34），24 条 canonical 页面字面量入闸门披露，全波 15 条新测试、唯一 W5 commit。

**Architecture:** 沿用阶段 2 方案 A：UI 文案进 `data class …Strings`（ZH/EN 实例），页面 `LocalStrings.current` 读取；页面内预设目录（日常目标标题/复盘标签/日程示例标题）是"会被落库或进账本 description 的 canonical 值"——沿用 W0 注册表机制，`presetTextRegistry` 扩 19 条，渲染点包 `localizedPresetText`；`ScheduleAlarmHelper` 按 spec §8 在**发出时**用 `currentDisplayLanguage()` + `stringsFor(...)` 取词，`schedule.title` 走 `localizedPresetText`（suspend 化，调用点均已在协程内）。

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform 1.7.3、Kotlin 2.1.20；commonTest；Python 等价/边界闸门。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§12 W5 行、§8 通知文案、§1 非目标、§10 测试策略、§11 验证上限）

## Global Constraints

- **Ruling 25 存储边界（硬约束）**：文案表/注册表字符串不得进入写库调用或业务判等参数。本波 canonical 落库/账本字面量（**全部保持原字面量**，并逐条进闸门披露）：`checkIn(description = "完成每日目标：${def.title}")`、`description = "每日学习目标全部达成奖励"`、`checkIn(description = "习惯打卡: ${challenge.title}")`、`tryUnlockAchievement("habit_complete", "习惯达人", "完成一个21天挑战", "🏆")`、8 个 `DailyTargetDef` 标题、6 个复盘标签、5 个日程示例标题。
- **不动**：`data/**`、schema/迁移、`DateTimeCompat.kt`、`Plurals.kt`（复用 `pointsWord`，不新增 helper）、平台源集（androidMain/iosMain）、`AppGraph`（两处通知接线属 W9）、`HabitRepository`/`PointRepository`/`ScheduleRepository`。
- **唯一改动形态**：`"文本"` → `strings.xxx` / `localizedPresetText(...)` 等价替换；逻辑零变化。**两处例外（本计划显式授权）**：①`ScheduleAlarmHelper.schedule()` 由非 suspend 改为 suspend（spec §8 取词必需，4 个调用点均在协程内，无需改调用点）；②`ParentScheduleManageScreen.repeatDaysText` 的**显示格式化**移入表函数 `repeatDays`（纯渲染逻辑迁移，解析逻辑留在页面）。
- **中文态字节等价**：既有页面中文渲染逐字节不变（等价闸门证明；注册表 ZH 恒等由既有测试保证）。
- 复用既有值必须逐字节同值：`ParentTaskEditStrings.saveButton`（"保存"/"Save"）、`CommonStrings.cancelButton`（"取消"/"Cancel"）、`CommonStrings.deleteContentDescription`（"删除"/"Delete"）。
- 英文复数只允许复用 `pointsWord(n)`；`days` 单复数用行内条件（`if (total == 1) "day" else "days"`，W4 Ruling 1 先例）。
- **注册表守卫是双向集合相等**（`PresetRegistryTest.registryKeysEqualSeedCanonicalsExactly`）：新增 19 条必须同步扩该测试的 `expected` 集合（新增来源组注释），否则测试红。
- 每波本地四闸（顺序、逐条 exit 0）：`i18n_storage_boundary.py` → `tests/i18n_storage_boundary_selftest.py` → `i18n_template_diff.py --branch` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`。
- 波次纪律：任务内只 `git add` 暂存、**不提交**；Task 7 落唯一 W5 commit；**不推送**。
- 环境：Gradle 需内联 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home"`；Python 闸门需 `PYTHONIOENCODING=utf-8`。
- **闭集逐任务加**（W4 Ruling 3 先例）：新增含 `point` 的 EN 静态值在**引入它的同一任务**入 `EN_STATIC_POINTS_MENTIONS`。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt`（改） | 注册表 +19 条 W5 页面内预设（分组注释） |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt`（改） | `expected` 集合扩 19 条 + 1 条新测试（W5 预设两语解析） |
| `.../i18n/ChildDailyTargetStrings.kt`（新建） | 每日学习目标页 28 字段 |
| `.../i18n/ParentScheduleStrings.kt`（新建） | 家长日程管理页 19 字段 + 复用 3 值 |
| `.../i18n/ScheduleAlarmStrings.kt`（新建） | 日程闹钟通知 2 字段 |
| `.../i18n/ChildHabitChallengeStrings.kt`（新建） | 习惯挑战页 19 字段 |
| `.../i18n/ChildGoalTrackerStrings.kt`（新建） | 目标追踪页 15 字段 |
| `.../i18n/ChildScheduleTimelineStrings.kt`（新建） | 今日时间线页 7 字段 |
| `.../i18n/Strings.kt`（改） | AppStrings +6 表（6 字段 + 6 Zh + 6 En 行） |
| `.../ui/target/ChildDailyTargetScreen.kt`（改） | 文案替换 + 目标标题/复盘标签渲染包注册表 |
| `.../ui/schedule/ParentScheduleManageScreen.kt`（改） | 文案替换 + 日程标题/任务标题渲染包注册表 + `repeatDaysText` 迁入表函数 |
| `.../ui/schedule/ScheduleAlarmHelper.kt`（改） | suspend 取词 + 通知正文/标题本地化 |
| `.../ui/habit/ChildHabitChallengeScreen.kt`（改） | 文案替换（账本/成就 canonical 字面量不动） |
| `.../ui/habit/ChildGoalTrackerScreen.kt`（改） | 文案替换 |
| `.../ui/schedule/ChildScheduleTimelineScreen.kt`（改） | 文案替换 + 日程/任务标题渲染包注册表 |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildScheduleCopyTest.kt`（新建） | W5 六表守卫 14 条测试（含 notificationBody 双语钉死与跨表 pin） |
| `.../i18n/StringsCopyTest.kt`（改） | 闭集 +1 |
| `scripts/i18n_template_diff.py`（改） | W5_REV/6 源/6 表/24 披露/读数 |
| `docs/i18n-stage1-verification.md`（改） | V-52…V-57 + 全量读数 |

---

### Task 1: 注册表扩展 19 条（页面内预设）+ 守卫测试

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt`

**Interfaces:**
- Produces: `presetTextRegistry` 新增 19 个 key（8 目标标题 + 6 复盘标签 + 5 日程示例），供 T2/T3 页与后续波次渲染使用。

- [ ] **Step 1: 追加测试（红）**

在 `PresetRegistryTest` 类内追加（`import` 无需新增）：

```kotlin
    @Test
    fun w5PageLocalPresetsResolvePerLanguage() {
        // W5 页面内预设目录：日常目标 8 + 复盘标签 6 + 日程示例 5（canonical 会被落库/进账本）
        val presets = mapOf(
            "复习错题" to "Review mistakes",
            "专注2个番茄钟" to "Focus for 2 Pomodoros",
            "完成作业" to "Finish homework",
            "预习新课" to "Preview new lessons",
            "阅读30分钟" to "Read for 30 minutes",
            "英语听说训练" to "English listening & speaking practice",
            "整理笔记" to "Organize notes",
            "体育锻炼" to "Exercise",
            "今天很专注" to "Focused today",
            "完成了难题" to "Solved a hard problem",
            "复习了错题" to "Reviewed mistakes",
            "有点分心" to "A bit distracted",
            "时间不够用" to "Not enough time",
            "明天会更好" to "Tomorrow will be better",
            "起床啦" to "Time to get up",
            "吃早餐" to "Breakfast",
            "开始写作业" to "Homework time",
            "阅读时间" to "Reading time",
            "准备睡觉" to "Getting ready for bed",
        )
        for ((zh, en) in presets) {
            assertEquals(zh, localizedPresetText(zh, DisplayLanguage.ZH), "ZH 必须恒等：$zh")
            assertEquals(en, localizedPresetText(zh, DisplayLanguage.EN), "EN 译文漂移：$zh")
        }
    }
```

- [ ] **Step 2: 确认红**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.PresetRegistryTest"`
Expected: 新测试 FAIL（`assertEquals` 收到原文中文，EN 未命中）。

- [ ] **Step 3: 实现**

3a. `PresetRegistry.kt` 的 `presetTextRegistry` map 内、按现有分组风格**追加分组**（放在最后一组之后、map 结尾之前）：

```kotlin
    // ===== W5 页面内预设目录（日常目标 8 + 复盘标签 6 + 日程示例 5）=====
    // 这些 canonical 会被落库（日程示例写入 daily_schedules；复盘标签写入偏好）
    // 或进入积分账本 description（每日目标），渲染/发通知时才本地化（Ruling 25）。
    preset("复习错题", "Review mistakes"),
    preset("专注2个番茄钟", "Focus for 2 Pomodoros"),
    preset("完成作业", "Finish homework"),
    preset("预习新课", "Preview new lessons"),
    preset("阅读30分钟", "Read for 30 minutes"),
    preset("英语听说训练", "English listening & speaking practice"),
    preset("整理笔记", "Organize notes"),
    preset("体育锻炼", "Exercise"),
    preset("今天很专注", "Focused today"),
    preset("完成了难题", "Solved a hard problem"),
    preset("复习了错题", "Reviewed mistakes"),
    preset("有点分心", "A bit distracted"),
    preset("时间不够用", "Not enough time"),
    preset("明天会更好", "Tomorrow will be better"),
    preset("起床啦", "Time to get up"),
    preset("吃早餐", "Breakfast"),
    preset("开始写作业", "Homework time"),
    preset("阅读时间", "Reading time"),
    preset("准备睡觉", "Getting ready for bed"),
```

（沿用文件现有 `preset(...)` 构造写法；若该文件用 `"key" to PresetCopy("zh","en")` 形式则按现有形式逐条加——**先读文件再照现有形状写**。）

3b. `PresetRegistryTest.registryKeysEqualSeedCanonicalsExactly` 的 `expected` 中追加（保持既有分组注释风格）：

```kotlin
        val w5PageLocalPresets = setOf( // PresetRegistry.kt「W5 页面内预设目录」组（目标 8 + 标签 6 + 日程示例 5）
            "复习错题", "专注2个番茄钟", "完成作业", "预习新课", "阅读30分钟", "英语听说训练", "整理笔记", "体育锻炼",
            "今天很专注", "完成了难题", "复习了错题", "有点分心", "时间不够用", "明天会更好",
            "起床啦", "吃早餐", "开始写作业", "阅读时间", "准备睡觉",
        )
```
并把 `expected` 的加法行改为包含 `+ w5PageLocalPresets`。类 KDoc 的"种子采集"说明补一句：`W5 起的页面内预设目录组由本测试按源码字面量逐字钉住（与 deviceFallbacks/diagnostics 同法）`。

- [ ] **Step 4: 确认绿**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.PresetRegistryTest"`
Expected: 全类 PASS（含既有 6 条 + 新 1 条）。全量 **173/0**。

- [ ] **Step 5: 暂存**：`git add` 两文件。

---

### Task 2: ChildDailyTargetStrings + 每日学习目标页

**Files:**
- Create: `.../i18n/ChildDailyTargetStrings.kt`
- Modify: `Strings.kt`、`ui/target/ChildDailyTargetScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildScheduleCopyTest.kt`

**Interfaces:**
- Consumes: `localizedPresetText`（目标标题）、`LocalStrings`/`LocalDisplayLanguage`。
- Produces: `data class ChildDailyTargetStrings`（28 字段）、`AppStrings.childDailyTarget`；`ChildScheduleCopyTest` 骨架。

- [ ] **Step 1: 追加测试（红）**：创建 `ChildScheduleCopyTest.kt`：

```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W5 文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentPointsCopyTest/ChildRewardStoreCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b），英文 one/other 用 pointsWord 钉死；
 * 跨表同值钉死。覆盖六张表：childDailyTarget / parentSchedule / scheduleAlarm /
 * childHabitChallenge / childGoalTracker / childScheduleTimeline。
 */
class ChildScheduleCopyTest {

    @Test
    fun childDailyTargetCopyDiffers() {
        val zh = AppStringsZh.childDailyTarget
        val en = AppStringsEn.childDailyTarget
        assertDiffers(
            zh.title to en.title,
            zh.setGoalsHint to en.setGoalsHint,
            zh.recommendTitle to en.recommendTitle,
            zh.recommendSubtitle to en.recommendSubtitle,
            zh.adoptAllButton to en.adoptAllButton,
            zh.pickGoalsTitle to en.pickGoalsTitle,
            zh.needOneGoal to en.needOneGoal,
            zh.goalsSet to en.goalsSet,
            zh.todayProgressTitle to en.todayProgressTitle,
            zh.allDonePraise to en.allDonePraise,
            zh.allDoneBonus to en.allDoneBonus,
            zh.resetGoalsButton to en.resetGoalsButton,
            zh.reflectionTitle to en.reflectionTitle,
            zh.reflectionDone to en.reflectionDone,
            zh.reflectionPrompt to en.reflectionPrompt,
            zh.starLabel1 to en.starLabel1,
            zh.starLabel2 to en.starLabel2,
            zh.starLabel3 to en.starLabel3,
            zh.starLabel4 to en.starLabel4,
            zh.starLabel5 to en.starLabel5,
            zh.rateFirst to en.rateFirst,
            zh.reflectionSaved to en.reflectionSaved,
            zh.saveReflectionButton to en.saveReflectionButton,
        )
    }

    @Test
    fun childDailyTargetInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childDailyTarget
        val en = AppStringsEn.childDailyTarget
        assertEquals("🎯 设定今日目标（3个）", zh.setGoalsButton(3))
        assertEquals("🎯 Set today's goals (3)", en.setGoalsButton(3))
        assertEquals("✅ 「完成作业」完成 +2分（待家长审核）", zh.goalDonePending("完成作业"))
        assertEquals("✅ “Finish homework” done +2 points (awaiting parent approval)", en.goalDonePending("Finish homework"))
        assertEquals("↩️ 已取消「完成作业」", zh.goalUndone("完成作业"))
        assertEquals("↩️ “Finish homework” unchecked", en.goalUndone("Finish homework"))
        assertEquals("今日自评：⭐⭐⭐", zh.selfRating(3))
        assertEquals("Today's self-rating: ⭐⭐⭐", en.selfRating(3))
        assertEquals("我的反思：今天很专注、完成了难题", zh.reflectionTags(listOf("今天很专注", "完成了难题")))
        assertEquals("My reflections: Focused today, Solved a hard problem", en.reflectionTags(listOf("Focused today", "Solved a hard problem")))
    }
}
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'childDailyTarget'`）

- [ ] **Step 3: 实现**

3a. 创建 `ChildDailyTargetStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端每日学习目标页（ui/target/ChildDailyTargetScreen.kt）文案表（阶段 2 W5）。
 *
 * 8 个日常目标标题是页面内预设 canonical（写进积分账本 description），留在页面、
 * 渲染包 [localizedPresetText]（注册表 W5 组已收录，T1 落地）。
 * 6 个复盘标签是落偏好 canonical（saveDailyReflection 存中文），渲染时逐个包注册表后
 * 传入 [reflectionTags]（分隔符随语言：ZH「、」/ EN ", "）。
 * 5 个星级评语是纯渲染 → 本表字段（星数本身落偏好，不含文案）。
 * [selfRating] 的星星符号 "⭐" 两语相同。
 */
data class ChildDailyTargetStrings(
    val title: String,
    val setGoalsHint: String,
    val recommendTitle: String,
    val recommendSubtitle: String,
    val adoptAllButton: String,
    val pickGoalsTitle: String,
    val needOneGoal: String,
    val goalsSet: String,
    val setGoalsButton: (count: Int) -> String,
    val todayProgressTitle: String,
    val allDonePraise: String,
    val allDoneBonus: String,
    val goalDonePending: (title: String) -> String,
    val goalUndone: (title: String) -> String,
    val resetGoalsButton: String,
    val reflectionTitle: String,
    val selfRating: (stars: Int) -> String,
    val reflectionTags: (tags: List<String>) -> String,
    val reflectionDone: String,
    val reflectionPrompt: String,
    val starLabel1: String,
    val starLabel2: String,
    val starLabel3: String,
    val starLabel4: String,
    val starLabel5: String,
    val rateFirst: String,
    val reflectionSaved: String,
    val saveReflectionButton: String,
)

val ChildDailyTargetStringsZh = ChildDailyTargetStrings(
    title = "🎯 每日学习目标",                                                  // :78
    setGoalsHint = "💡 自己定目标，主动学习更有效！挑选今天想完成的目标（建议 3-5 个）。", // :93
    recommendTitle = "🤖 智能推荐",                                             // :105
    recommendSubtitle = "根据你的学习数据生成",                                  // :107
    adoptAllButton = "✨ 一键采纳推荐目标",                                      // :127
    pickGoalsTitle = "选择今日学习目标",                                         // :132
    needOneGoal = "请至少选择一个目标",                                          // :148
    goalsSet = "✅ 今日目标已设定，加油！",                                      // :153
    setGoalsButton = { count -> "🎯 设定今日目标（${count}个）" },               // :159
    todayProgressTitle = "📅 今日目标达成",                                      // :172
    allDonePraise = "🎉 太棒了！今日目标全部达成，你真是个自律的学习者！",          // :183
    allDoneBonus = "🎉 今日目标全部达成！额外奖励 +10 分（待审核）",              // :230
    goalDonePending = { title -> "✅ 「$title」完成 +2分（待家长审核）" },        // :232
    goalUndone = { title -> "↩️ 已取消「$title」" },                            // :244
    resetGoalsButton = "✏️ 重新设定今日目标",                                    // :265
    reflectionTitle = "🔍 每日复盘",                                            // :273
    selfRating = { stars -> "今日自评：${"⭐".repeat(stars)}" },                 // :276
    reflectionTags = { tags -> "我的反思：${tags.joinToString("、")}" },         // :279
    reflectionDone = "✅ 已完成今日复盘，反思让学习更高效！",                      // :282
    reflectionPrompt = "今天学得怎么样？给自己打个分吧～",                        // :285
    starLabel1 = "需努力",                                                      // :302
    starLabel2 = "还不错",                                                      // :302
    starLabel3 = "不错哦",                                                      // :302
    starLabel4 = "很棒",                                                        // :302
    starLabel5 = "太棒了",                                                      // :302
    rateFirst = "请先给自己打个分吧",                                            // :323
    reflectionSaved = "✅ 复盘完成！反思让你进步更快",                            // :327
    saveReflectionButton = "📝 完成今日复盘",                                    // :333
)

val ChildDailyTargetStringsEn = ChildDailyTargetStrings(
    title = "🎯 Daily learning goals",
    setGoalsHint = "💡 Set your own goals — taking charge makes learning more effective! Pick what you want to finish today (3–5 recommended).",
    recommendTitle = "🤖 Smart recommendations",
    recommendSubtitle = "Generated from your learning data",
    adoptAllButton = "✨ Adopt all recommended goals",
    pickGoalsTitle = "Choose today's learning goals",
    needOneGoal = "Pick at least one goal",
    goalsSet = "✅ Today's goals are set — go for it!",
    setGoalsButton = { count -> "🎯 Set today's goals ($count)" },
    todayProgressTitle = "📅 Today's goal progress",
    allDonePraise = "🎉 Amazing — all of today's goals are done. You're a truly self-disciplined learner!",
    allDoneBonus = "🎉 All goals achieved! Bonus +10 points (awaiting approval)",
    goalDonePending = { title -> "✅ “$title” done +2 points (awaiting parent approval)" },
    goalUndone = { title -> "↩️ “$title” unchecked" },
    resetGoalsButton = "✏️ Reset today's goals",
    reflectionTitle = "🔍 Daily reflection",
    selfRating = { stars -> "Today's self-rating: ${"⭐".repeat(stars)}" },
    reflectionTags = { tags -> "My reflections: ${tags.joinToString(", ")}" },
    reflectionDone = "✅ Reflection saved — reflecting makes learning more effective!",
    reflectionPrompt = "How did today go? Give yourself a rating!",
    starLabel1 = "Needs work",
    starLabel2 = "Not bad",
    starLabel3 = "Good",
    starLabel4 = "Great",
    starLabel5 = "Excellent",
    rateFirst = "Pick a star rating first",
    reflectionSaved = "✅ Reflection complete — reflecting helps you improve faster",
    saveReflectionButton = "📝 Save today's reflection",
)
```

3b. `Strings.kt`：`AppStrings` 末尾加 `    val childDailyTarget: ChildDailyTargetStrings,`；`AppStringsZh` 加 `    childDailyTarget = ChildDailyTargetStringsZh,`；`AppStringsEn` 加 `    childDailyTarget = ChildDailyTargetStringsEn,`。

3c. 迁移 `ChildDailyTargetScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。主 composable 开头加：
```kotlin
    val strings = LocalStrings.current.childDailyTarget
    val displayLanguage = LocalDisplayLanguage.current
```

| 原名面量 | 替换 |
|---|---|
| `title = "🎯 每日学习目标"`（:78） | `title = strings.title` |
| `Text("💡 自己定目标，主动学习更有效！挑选今天想完成的目标（建议 3-5 个）。",`（:93） | `Text(strings.setGoalsHint,` |
| `Text("🤖 智能推荐", …)`（:105） | `Text(strings.recommendTitle, …)` |
| `Text("根据你的学习数据生成", …)`（:107） | `Text(strings.recommendSubtitle, …)` |
| `Text("✨ 一键采纳推荐目标")`（:127） | `Text(strings.adoptAllButton)` |
| `Text("选择今日学习目标", …)`（:132） | `Text(strings.pickGoalsTitle, …)` |
| `label = { Text("${def.emoji} ${def.title}") }`（:140） | `label = { Text("${def.emoji} ${localizedPresetText(def.title, displayLanguage)}") }` |
| `snackbarHostState.showSnackbar("请至少选择一个目标")`（:148） | `snackbarHostState.showSnackbar(strings.needOneGoal)` |
| `snackbarHostState.showSnackbar("✅ 今日目标已设定，加油！")`（:153） | `snackbarHostState.showSnackbar(strings.goalsSet)` |
| `Text("🎯 设定今日目标（${picking.size}个）")`（:159） | `Text(strings.setGoalsButton(picking.size))` |
| `Text("📅 今日目标达成", …)`（:172） | `Text(strings.todayProgressTitle, …)` |
| `Text("🎉 太棒了！今日目标全部达成，你真是个自律的学习者！",`（:183） | `Text(strings.allDonePraise,` |
| `snackbarHostState.showSnackbar("🎉 今日目标全部达成！额外奖励 +10 分（待审核）")`（:230） | `snackbarHostState.showSnackbar(strings.allDoneBonus)` |
| `snackbarHostState.showSnackbar("✅ 「${def.title}」完成 +2分（待家长审核）")`（:232） | `snackbarHostState.showSnackbar(strings.goalDonePending(localizedPresetText(def.title, displayLanguage)))` |
| `snackbarHostState.showSnackbar("↩️ 已取消「${def.title}」")`（:244） | `snackbarHostState.showSnackbar(strings.goalUndone(localizedPresetText(def.title, displayLanguage)))` |
| `Text(def.title,`（:252） | `Text(localizedPresetText(def.title, displayLanguage),` |
| `Text("✏️ 重新设定今日目标")`（:265） | `Text(strings.resetGoalsButton)` |
| `Text("🔍 每日复盘", …)`（:273） | `Text(strings.reflectionTitle, …)` |
| `Text("今日自评：" + "⭐".repeat(reflectionStars.coerceAtLeast(1)),`（:276） | `Text(strings.selfRating(reflectionStars.coerceAtLeast(1)),` |
| `Text("我的反思：${reflectionTags.joinToString("、")}",`（:279） | `Text(strings.reflectionTags(reflectionTags.map { localizedPresetText(it, displayLanguage) }),` |
| `Text("✅ 已完成今日复盘，反思让学习更高效！",`（:282） | `Text(strings.reflectionDone,` |
| `Text("今天学得怎么样？给自己打个分吧～", …)`（:285） | `Text(strings.reflectionPrompt, …)` |
| `when (pickStars) { 1 -> "需努力"; 2 -> "还不错"; 3 -> "不错哦"; 4 -> "很棒"; else -> "太棒了" }`（:302） | `when (pickStars) { 1 -> strings.starLabel1; 2 -> strings.starLabel2; 3 -> strings.starLabel3; 4 -> strings.starLabel4; else -> strings.starLabel5 }` |
| `snackbarHostState.showSnackbar("请先给自己打个分吧")`（:323） | `snackbarHostState.showSnackbar(strings.rateFirst)` |
| `snackbarHostState.showSnackbar("✅ 复盘完成！反思让你进步更快")`（:327） | `snackbarHostState.showSnackbar(strings.reflectionSaved)` |
| `Text("📝 完成今日复盘")`（:333） | `Text(strings.saveReflectionButton)` |

**不动**（Ruling 25）：`description = "完成每日目标：${def.title}"`（:215）、`description = "每日学习目标全部达成奖励"`（:225）、`bizKey`/`actionType`、8 个 `DailyTargetDef` 标题、6 个 `tagOptions` 标签、`rec.title`/`rec.reason`（GoalRecommendationEngine 输出，W8 归属，原样渲染）。

验证：去注释 CJK 去重后 = 8 目标标题 + 6 标签 + 2 条账本字面量 = **16 条**（预期列表见任务报告）。

- [ ] **Step 4: 确认绿**：focused 2 + 63；全量 **175/0**。

- [ ] **Step 5: 暂存**（4 文件：新表、Strings.kt、页面、测试文件）。

---

### Task 3: ParentScheduleStrings + 家长日程管理页

**Files:**
- Create: `.../i18n/ParentScheduleStrings.kt`
- Modify: `Strings.kt`、`ui/schedule/ParentScheduleManageScreen.kt`、`ChildScheduleCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `ParentTaskEditStrings.saveButton`、`CommonStrings.cancelButton`/`deleteContentDescription`、`localizedPresetText`、`LocalStrings`/`LocalDisplayLanguage`。
- Produces: `data class ParentScheduleStrings`（19 字段）、`AppStrings.parentSchedule`。

- [ ] **Step 1: 追加测试（红）**

```kotlin
    @Test
    fun parentScheduleCopyDiffers() {
        val zh = AppStringsZh.parentSchedule
        val en = AppStringsEn.parentSchedule
        assertDiffers(
            zh.title to en.title,
            zh.addSchedule to en.addSchedule,
            zh.hint to en.hint,
            zh.emptyTitle to en.emptyTitle,
            zh.emptyHint to en.emptyHint,
            zh.samplesLoaded to en.samplesLoaded,
            zh.loadSamplesButton to en.loadSamplesButton,
            zh.addOwnHint to en.addOwnHint,
            zh.addedMessage to en.addedMessage,
            zh.updatedMessage to en.updatedMessage,
            zh.editTitle to en.editTitle,
            zh.timeLabel to en.timeLabel,
            zh.titleFieldLabel to en.titleFieldLabel,
            zh.linkedTaskOptional to en.linkedTaskOptional,
            zh.none to en.none,
            zh.enableReminder to en.enableReminder,
        )
    }

    @Test
    fun parentScheduleInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentSchedule
        val en = AppStringsEn.parentSchedule
        assertEquals("已删除「起床啦」", zh.deletedMessage("起床啦"))
        assertEquals("Deleted “Time to get up”", en.deletedMessage("Time to get up"))
        assertEquals("🔗 关联任务：完成作业", zh.linkedTaskLabel("完成作业"))
        assertEquals("🔗 Linked task: Finish homework", en.linkedTaskLabel("Finish homework"))
        assertEquals("每天", zh.repeatDays(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("Every day", en.repeatDays(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("周一、周三、周日", zh.repeatDays(listOf(1, 3, 7)))
        assertEquals("Mon, Wed, Sun", en.repeatDays(listOf(1, 3, 7)))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'parentSchedule'`）

- [ ] **Step 3: 实现**

3a. 创建 `ParentScheduleStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 家长端每日日程管理页（ui/schedule/ParentScheduleManageScreen.kt）文案表（阶段 2 W5）。
 *
 * 复用而非新建：保存 = [ParentTaskEditStrings.saveButton]、取消 = [CommonStrings.cancelButton]、
 * 删除 = [CommonStrings.deleteContentDescription]。
 * 5 个示例日程标题是 canonical 落库值（addSchedule 原样写入），留在页面、渲染/发通知时包
 * [localizedPresetText]（注册表 W5 组已收录）。日程标题与关联任务标题同理：用户自建未命中原样。
 * [repeatDays]：把原页面 repeatDaysText 的显示格式化迁入本表（纯渲染；"每天"/"周X"/星期名/分隔符
 * 均在 lambda 内，两语不同构）；页面保留 "1,2,3,4,5,6,7" 解析与 bizKey 逻辑不动。
 */
data class ParentScheduleStrings(
    val title: String,
    val addSchedule: String,
    val hint: String,
    val emptyTitle: String,
    val emptyHint: String,
    val samplesLoaded: String,
    val loadSamplesButton: String,
    val addOwnHint: String,
    val deletedMessage: (title: String) -> String,
    val addedMessage: String,
    val updatedMessage: String,
    val linkedTaskLabel: (title: String) -> String,
    val repeatDays: (days: List<Int>) -> String,
    val editTitle: String,
    val timeLabel: String,
    val titleFieldLabel: String,
    val linkedTaskOptional: String,
    val none: String,
    val enableReminder: String,
)

val ParentScheduleStringsZh = ParentScheduleStrings(
    title = "⏰ 每日日程",                                                      // :41
    addSchedule = "添加日程",                                                   // :45/:228
    hint = "💡 为孩子设置每日时间日程（如 7:00 起床、16:00 写作业），到点自动通知提醒。可关联具体任务，引导孩子打卡。", // :59
    emptyTitle = "还没有日程安排",                                              // :74
    emptyHint = "点击下方按钮一键载入示例日程（起床/早餐/写作业/睡觉等），再按需调整时间即可。", // :75
    samplesLoaded = "✅ 已载入示例日程",                                        // :91
    loadSamplesButton = "✨ 一键载入示例日程",                                   // :93
    addOwnHint = "或点击右下角 + 自行添加",                                      // :94
    deletedMessage = { title -> "已删除「$title」" },                           // :118
    addedMessage = "✅ 已添加日程",                                             // :141
    updatedMessage = "✅ 已更新日程",                                           // :159
    linkedTaskLabel = { title -> "🔗 关联任务：$title" },                       // :195
    repeatDays = { days ->
        val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
        if (days.size == 7) "每天" else "周" + days.mapNotNull { names[it] }.joinToString("、")
    },                                                                        // :300-304
    editTitle = "编辑日程",                                                     // :228
    timeLabel = "时间",                                                        // :233
    titleFieldLabel = "日程标题（如：起床、写作业）",                              // :241
    linkedTaskOptional = "关联任务（可选）",                                     // :255
    none = "无",                                                              // :259/:265
    enableReminder = "启用提醒",                                                // :273
)

val ParentScheduleStringsEn = ParentScheduleStrings(
    title = "⏰ Daily schedule",
    addSchedule = "Add schedule",
    hint = "💡 Set a daily timetable for your child (e.g. 7:00 get up, 16:00 homework) and get a reminder when it's time. You can link a task to guide check-ins.",
    emptyTitle = "No schedule yet",
    emptyHint = "Tap the button below to load sample schedules (wake up / breakfast / homework / bedtime, etc.), then adjust the times as needed.",
    samplesLoaded = "✅ Sample schedules loaded",
    loadSamplesButton = "✨ Load sample schedules",
    addOwnHint = "Or tap + at the bottom right to add your own",
    deletedMessage = { title -> "Deleted “$title”" },
    addedMessage = "✅ Schedule added",
    updatedMessage = "✅ Schedule updated",
    linkedTaskLabel = { title -> "🔗 Linked task: $title" },
    repeatDays = { days ->
        val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        if (days.size == 7) "Every day" else days.mapNotNull { names[it] }.joinToString(", ")
    },
    editTitle = "Edit schedule",
    timeLabel = "Time",
    titleFieldLabel = "Schedule title (e.g. wake up, homework)",
    linkedTaskOptional = "Linked task (optional)",
    none = "None",
    enableReminder = "Enable reminder",
)
```

3b. `Strings.kt`：字段/Zh/En 三处（childDailyTarget 之后）。

3c. 迁移 `ParentScheduleManageScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。主 composable 开头加：
```kotlin
    val strings = LocalStrings.current.parentSchedule
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
```
`ScheduleManageCard` 与 `ScheduleEditDialog` 各加：
```kotlin
    val strings = LocalStrings.current.parentSchedule
```
（`ScheduleManageCard` 另加 `displayLanguage`；`ScheduleEditDialog` 另加 `common` 与 `displayLanguage`。）

| 原名面量 | 替换 |
|---|---|
| `title = "⏰ 每日日程"`（:41） | `title = strings.title` |
| `contentDescription = "添加日程"`（:45） | `contentDescription = strings.addSchedule` |
| `Text("💡 为孩子设置每日时间日程（如 7:00 起床、16:00 写作业），到点自动通知提醒。可关联具体任务，引导孩子打卡。",`（:59） | `Text(strings.hint,` |
| `Text("还没有日程安排", …)`（:74） | `Text(strings.emptyTitle, …)` |
| `Text("点击下方按钮一键载入示例日程（起床/早餐/写作业/睡觉等），再按需调整时间即可。",`（:75） | `Text(strings.emptyHint,` |
| `snackbarHostState.showSnackbar("✅ 已载入示例日程")`（:91） | `snackbarHostState.showSnackbar(strings.samplesLoaded)` |
| `}) { Text("✨ 一键载入示例日程") }`（:93） | `}) { Text(strings.loadSamplesButton) }` |
| `Text("或点击右下角 + 自行添加", …)`（:94） | `Text(strings.addOwnHint, …)` |
| `snackbarHostState.showSnackbar("已删除「${schedule.title}」")`（:118） | `snackbarHostState.showSnackbar(strings.deletedMessage(localizedPresetText(schedule.title, displayLanguage)))` |
| `snackbarHostState.showSnackbar("✅ 已添加日程")`（:141） | `snackbarHostState.showSnackbar(strings.addedMessage)` |
| `snackbarHostState.showSnackbar("✅ 已更新日程")`（:159） | `snackbarHostState.showSnackbar(strings.updatedMessage)` |
| `Text(schedule.title, …)`（:192） | `Text(localizedPresetText(schedule.title, displayLanguage), …)` |
| `Text("🔗 关联任务：$linkedTaskTitle", …)`（:195） | `Text(strings.linkedTaskLabel(localizedPresetText(linkedTaskTitle, displayLanguage)), …)` |
| `Text(repeatDaysText(schedule.repeatDays), …)`（:198） | `Text(strings.repeatDays(schedule.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }), …)` |
| `contentDescription = "删除"`（:204） | `contentDescription = common.deleteContentDescription` |
| `title = { Text(if (schedule == null) "添加日程" else "编辑日程") }`（:228） | `title = { Text(if (schedule == null) strings.addSchedule else strings.editTitle) }` |
| `Text("时间", …)`（:233） | `Text(strings.timeLabel, …)` |
| `label = { Text("日程标题（如：起床、写作业）") }`（:241） | `label = { Text(strings.titleFieldLabel) }` |
| `Text("关联任务（可选）", …)`（:255） | `Text(strings.linkedTaskOptional, …)` |
| `value = tasks.find { it.id == linkedTaskId }?.title ?: "无",`（:259） | `value = tasks.find { it.id == linkedTaskId }?.title?.let { localizedPresetText(it, displayLanguage) } ?: strings.none,` |
| `DropdownMenuItem(text = { Text("无") }, …)`（:265） | `DropdownMenuItem(text = { Text(strings.none) }, …)` |
| `DropdownMenuItem(text = { Text(t.title) }, …)`（:267） | `DropdownMenuItem(text = { Text(localizedPresetText(t.title, displayLanguage)) }, …)` |
| `Text("启用提醒", …)`（:273） | `Text(strings.enableReminder, …)` |
| `}) { Text("保存") }`（:294） | `}) { Text(LocalStrings.current.parentTaskEdit.saveButton) }`（或把 parentTaskEdit 读入上方 `strings` 旁：`val taskEdit = LocalStrings.current.parentTaskEdit`，用 `taskEdit.saveButton`） |
| `dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }`（:296） | `… { Text(common.cancelButton) } }` |

删除 `private fun repeatDaysText(...)`（:300-305，整体被表函数取代）。
**不动**（Ruling 25）：5 个示例 `DailyScheduleEntity(title = "…")`（:81-85）、`saved` 回调的实体写入、`"1,2,3,4,5,6,7"` 默认 repeatDays、`"HH:mm"` 时间解析。

验证：去注释 CJK 去重后 = 5 个示例标题 = **5 条**。

- [ ] **Step 4: 确认绿**：focused 4 + 63；全量 **177/0**。

- [ ] **Step 5: 暂存**（4 文件）。

---

### Task 4: ScheduleAlarmStrings + ScheduleAlarmHelper（suspend 取词）

**Files:**
- Create: `.../i18n/ScheduleAlarmStrings.kt`
- Modify: `Strings.kt`、`ui/schedule/ScheduleAlarmHelper.kt`、`ChildScheduleCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `currentDisplayLanguage()`、`stringsFor(...)`、`localizedPresetText`（W1 `ReviewReminderReceiver` 同型）。
- Produces: `data class ScheduleAlarmStrings`（2 字段）、`AppStrings.scheduleAlarm`、`ScheduleAlarmHelper.notificationBody(schedule, strings, lang)`。

- [ ] **Step 1: 追加测试（红）**

```kotlin
    @Test
    fun scheduleAlarmCopyDiffers() {
        val zh = AppStringsZh.scheduleAlarm
        val en = AppStringsEn.scheduleAlarm
        assertDiffers(zh.rememberCheckIn to en.rememberCheckIn)
    }

    @Test
    fun scheduleAlarmBodyPinsBothLanguages() {
        val zh = AppStringsZh.scheduleAlarm
        val en = AppStringsEn.scheduleAlarm
        assertEquals("🌅 起床啦 时间到啦！", zh.timeUp("🌅", "起床啦"))
        assertEquals("🌅 Time to get up — time's up!", en.timeUp("🌅", "Time to get up"))
        assertEquals("🌅 起床啦 时间到啦！完成后记得打卡哦～", zh.timeUp("🌅", "起床啦") + zh.rememberCheckIn)
        assertEquals("🌅 Time to get up — time's up!Remember to check in when you're done!", en.timeUp("🌅", "Time to get up") + en.rememberCheckIn)
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'scheduleAlarm'`）

- [ ] **Step 3: 实现**

3a. 创建 `ScheduleAlarmStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 日程闹钟通知文案（ui/schedule/ScheduleAlarmHelper.kt，spec §8）：
 * 发出时 `currentDisplayLanguage()` → [stringsFor] 取词，日程标题走 [localizedPresetText]
 * （预设示例译出、家长自建原样）。已排程通知不追更；日程闹钟按日重排，切语言后最迟次日生效。
 */
data class ScheduleAlarmStrings(
    val timeUp: (emoji: String, title: String) -> String,
    val rememberCheckIn: String,
)

val ScheduleAlarmStringsZh = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title 时间到啦！" },                     // :64
    rememberCheckIn = "完成后记得打卡哦～",                                      // :65
)

val ScheduleAlarmStringsEn = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title — time's up!" },
    rememberCheckIn = "Remember to check in when you're done!",
)
```

3b. `Strings.kt`：字段/Zh/En 三处（parentSchedule 之后）。

3c. 迁移 `ScheduleAlarmHelper.kt`：

```kotlin
import com.hstudy.app.i18n.ScheduleAlarmStrings
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.stringsFor
```
- `fun schedule(...)` → `suspend fun schedule(...)`，函数体开头：
```kotlin
        val lang = currentDisplayLanguage()
        val strings = stringsFor(lang).scheduleAlarm
```
  且 `title = schedule.title` → `title = localizedPresetText(schedule.title, lang)`，`body = notificationBody(schedule)` → `body = notificationBody(schedule, strings, lang)`。
- `suspend fun rescheduleAll()` 同样在开头取 `lang`/`strings`，映射体内 `title`/`body` 同上改法。
- 通知正文：
```kotlin
    /** 通知正文：标题部分按注册表本地化；正文按生效语言取词 */
    fun notificationBody(schedule: DailyScheduleEntity, strings: ScheduleAlarmStrings, lang: DisplayLanguage): String = buildString {
        append(strings.timeUp(schedule.emoji, localizedPresetText(schedule.title, lang)))
        if (schedule.linkedTaskId != null) append(strings.rememberCheckIn)
    }
```
（`DisplayLanguage` import：`com.hstudy.app.i18n.DisplayLanguage`。）
- KDoc 更新：说明 suspend 化原因（spec §8 取词）与 4 个调用点均在协程内。

**调用点核对**：`ParentScheduleManageScreen` 的 4 处 `ScheduleAlarmHelper.schedule(...)` 均在 `scope.launch {}` 内（:89/:109/:139/:158），**无需改动**；`rescheduleAll()` 已是 suspend。

- [ ] **Step 4: 确认绿**：focused 6 + 63；全量 **179/0**；`assembleDebug` 编译通过（suspend 化整树验证）。

- [ ] **Step 5: 暂存**（4 文件）。

---

### Task 5: ChildHabitChallengeStrings + 习惯挑战页

**Files:**
- Create: `.../i18n/ChildHabitChallengeStrings.kt`
- Modify: `Strings.kt`、`ui/habit/ChildHabitChallengeScreen.kt`、`ChildScheduleCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `pointsWord`、`CommonStrings.cancelButton`、`LocalStrings`。
- Produces: `data class ChildHabitChallengeStrings`（19 字段）、`AppStrings.childHabitChallenge`。

- [ ] **Step 1: 追加测试（红）**

```kotlin
    @Test
    fun childHabitChallengeCopyDiffers() {
        val zh = AppStringsZh.childHabitChallenge
        val en = AppStringsEn.childHabitChallenge
        assertDiffers(
            zh.title to en.title,
            zh.newChallengeContentDescription to en.newChallengeContentDescription,
            zh.emptyMessage to en.emptyMessage,
            zh.activeSectionTitle to en.activeSectionTitle,
            zh.alreadyCheckedInMessage to en.alreadyCheckedInMessage,
            zh.challengeCreated to en.challengeCreated,
            zh.completedSectionTitle to en.completedSectionTitle,
            zh.checkedInToday to en.checkedInToday,
            zh.checkInButton to en.checkInButton,
            zh.newChallengeDialogTitle to en.newChallengeDialogTitle,
            zh.nameLabel to en.nameLabel,
            zh.descLabel to en.descLabel,
            zh.daysLabel to en.daysLabel,
            zh.iconLabel to en.iconLabel,
            zh.createButton to en.createButton,
        )
    }

    @Test
    fun childHabitChallengeInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childHabitChallenge
        val en = AppStringsEn.childHabitChallenge
        assertEquals("🎉 恭喜完成挑战: 每天跑步! +5分待家长审核", zh.challengeCompleteMessage("每天跑步", 5))
        assertEquals("🎉 Challenge complete: 每天跑步! +1 point awaiting parent approval", en.challengeCompleteMessage("每天跑步", 1))
        assertEquals("✅ 打卡成功 +5分（待家长审核）(3/21)", zh.checkInSuccessMessage(5, 3, 21))
        assertEquals("✅ Checked in +5 points (awaiting parent approval) (3/21)", en.checkInSuccessMessage(5, 3, 21))
        assertEquals("3 / 21 天", zh.daysProgress(3, 21))
        assertEquals("3 / 21 days", en.daysProgress(3, 21))
        assertEquals("1 / 1 day", en.daysProgress(1, 1))
        assertEquals("7天", zh.daysOption(7))
        assertEquals("7 days", en.daysOption(7))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'childHabitChallenge'`）

- [ ] **Step 3: 实现**

3a. 创建 `ChildHabitChallengeStrings.kt`（字段与值如测试与下表；KDoc 注明：挑战标题/描述是用户内容原样；`"习惯打卡: ${challenge.title}"` 账本字面量与 `tryUnlockAchievement` 的 canonical 成就文案留在页面（Ruling 25，披露在 T7）；取消复用 [CommonStrings.cancelButton]）。

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端习惯挑战页（ui/habit/ChildHabitChallengeScreen.kt）文案表（阶段 2 W5）。
 *
 * [challengeCompleteMessage]/[checkInSuccessMessage] 的挑战标题/描述是**用户内容**，原样传入。
 * **落库字面量**（Ruling 25，保持 canonical 原样、T7 登记披露）：
 * `checkIn(description = "习惯打卡: ${challenge.title}")` 与
 * `tryUnlockAchievement("habit_complete", "习惯达人", "完成一个21天挑战", "🏆")`。
 * 「取消」复用 [CommonStrings.cancelButton]。[daysProgress] 英文单复数用行内条件。
 */
data class ChildHabitChallengeStrings(
    val title: String,
    val newChallengeContentDescription: String,
    val emptyMessage: String,
    val activeSectionTitle: String,
    val alreadyCheckedInMessage: String,
    val challengeCompleteMessage: (title: String, points: Int) -> String,
    val checkInSuccessMessage: (points: Int, done: Int, total: Int) -> String,
    val challengeCreated: String,
    val completedSectionTitle: String,
    val daysProgress: (done: Int, total: Int) -> String,
    val checkedInToday: String,
    val checkInButton: String,
    val newChallengeDialogTitle: String,
    val nameLabel: String,
    val descLabel: String,
    val daysLabel: String,
    val daysOption: (days: Int) -> String,
    val iconLabel: String,
    val createButton: String,
)

val ChildHabitChallengeStringsZh = ChildHabitChallengeStrings(
    title = "🏆 习惯挑战",                                                      // :46
    newChallengeContentDescription = "新建挑战",                                 // :49
    emptyMessage = "开始你的21天习惯挑战吧！",                                    // :59
    activeSectionTitle = "🔥 进行中的挑战",                                      // :69
    alreadyCheckedInMessage = "😊 今天已经打过卡啦，明天再来！",                   // :79
    challengeCompleteMessage = { title, points -> "🎉 恭喜完成挑战: $title! +$points分待家长审核" }, // :92
    checkInSuccessMessage = { points, done, total -> "✅ 打卡成功 +$points分（待家长审核）($done/$total)" }, // :107
    challengeCreated = "🎯 挑战已创建！",                                        // :144
    completedSectionTitle = "✅ 已完成的挑战",                                   // :122
    daysProgress = { done, total -> "$done / $total 天" },                       // :193
    checkedInToday = "✅ 今日已打卡",                                            // :199
    checkInButton = "打卡",                                                     // :203
    newChallengeDialogTitle = "🎯 新建习惯挑战",                                  // :232
    nameLabel = "挑战名称",                                                     // :239
    descLabel = "描述（可选）",                                                  // :241
    daysLabel = "天数: ",                                                       // :242
    daysOption = { days -> "${days}天" },                                       // :245
    iconLabel = "图标:",                                                        // :248
    createButton = "创建",                                                      // :257
)

val ChildHabitChallengeStringsEn = ChildHabitChallengeStrings(
    title = "🏆 Habit Challenge",
    newChallengeContentDescription = "New challenge",
    emptyMessage = "Start your 21-day habit challenge!",
    activeSectionTitle = "🔥 Active challenges",
    alreadyCheckedInMessage = "😊 You've already checked in today — come back tomorrow!",
    challengeCompleteMessage = { title, points -> "🎉 Challenge complete: $title! +$points ${pointsWord(points)} awaiting parent approval" },
    checkInSuccessMessage = { points, done, total -> "✅ Checked in +$points ${pointsWord(points)} (awaiting parent approval) ($done/$total)" },
    challengeCreated = "🎯 Challenge created!",
    completedSectionTitle = "✅ Completed challenges",
    daysProgress = { done, total -> "$done / $total ${if (total == 1) "day" else "days"}" },
    checkedInToday = "✅ Checked in today",
    checkInButton = "Check in",
    newChallengeDialogTitle = "🎯 New habit challenge",
    nameLabel = "Challenge name",
    descLabel = "Description (optional)",
    daysLabel = "Days: ",
    daysOption = { days -> "$days days" },
    iconLabel = "Icon:",
    createButton = "Create",
)
```

3b. `Strings.kt`：字段/Zh/En 三处（scheduleAlarm 之后）。

3c. 迁移 `ChildHabitChallengeScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalStrings`。主 composable 开头 `val strings = LocalStrings.current.childHabitChallenge`（对话框内再读 `common`）。替换：`ChallengeCard` 加 `val strings = …`；`AddChallengeDialog` 加 `val strings = …`、`val common = LocalStrings.current.common`。

| 原名面量 | 替换 |
|---|---|
| `title = "🏆 习惯挑战"`（:46） | `title = strings.title` |
| `contentDescription = "新建挑战"`（:49） | `contentDescription = strings.newChallengeContentDescription` |
| `Text("开始你的21天习惯挑战吧！", …)`（:59） | `Text(strings.emptyMessage, …)` |
| `Text("🔥 进行中的挑战", …)`（:69） | `Text(strings.activeSectionTitle, …)` |
| `showSnackbar("😊 今天已经打过卡啦，明天再来！")`（:79） | `showSnackbar(strings.alreadyCheckedInMessage)` |
| `showSnackbar("🎉 恭喜完成挑战: ${challenge.title}! +${challenge.dailyPoints}分待家长审核")`（:92） | `showSnackbar(strings.challengeCompleteMessage(challenge.title, challenge.dailyPoints))` |
| `showSnackbar("✅ 打卡成功 +${challenge.dailyPoints}分（待家长审核）(${challenge.completedDays + 1}/${challenge.totalDays})")`（:107） | `showSnackbar(strings.checkInSuccessMessage(challenge.dailyPoints, challenge.completedDays + 1, challenge.totalDays))` |
| `showSnackbar("🎯 挑战已创建！")`（:144） | `showSnackbar(strings.challengeCreated)` |
| `Text("✅ 已完成的挑战", …)`（:122） | `Text(strings.completedSectionTitle, …)` |
| `Text("${challenge.completedDays} / ${challenge.totalDays} 天",`（:193） | `Text(strings.daysProgress(challenge.completedDays, challenge.totalDays),` |
| `Text("✅ 今日已打卡", …)`（:199） | `Text(strings.checkedInToday, …)` |
| `Text("打卡")`（:203） | `Text(strings.checkInButton)` |
| `Text("🎯 新建习惯挑战")`（:232） | `Text(strings.newChallengeDialogTitle)` |
| `label = { Text("挑战名称") }`（:239） | `label = { Text(strings.nameLabel) }` |
| `label = { Text("描述（可选）") }`（:241） | `label = { Text(strings.descLabel) }` |
| `Text("天数: ", …)`（:242） | `Text(strings.daysLabel, …)` |
| `label = { Text("${d}天") }`（:245） | `label = { Text(strings.daysOption(d)) }` |
| `Text("图标:", …)`（:248） | `Text(strings.iconLabel, …)` |
| `{ Text("创建") }`（:257） | `{ Text(strings.createButton) }` |
| `{ Text("取消") }`（:259） | `{ Text(common.cancelButton) }` |

**不动**（Ruling 25）：`description = "习惯打卡: ${challenge.title}"`（:87/:102）、`tryUnlockAchievement("habit_complete", "习惯达人", "完成一个21天挑战", "🏆")`（:93-95）、`bizKey`/`actionType`、`challenge.title`/`challenge.description` 卡片渲染（用户内容原样）。

验证：去注释 CJK 去重后 = `习惯打卡: ${challenge.title}` + `习惯达人` + `完成一个21天挑战` = **3 条**。

- [ ] **Step 4: 确认绿**：focused 8 + 63；全量 **181/0**。

- [ ] **Step 5: 暂存**（4 文件）。

---

### Task 6: ChildGoalTrackerStrings + ChildScheduleTimelineStrings + 两页

**Files:**
- Create: `.../i18n/ChildGoalTrackerStrings.kt`、`.../i18n/ChildScheduleTimelineStrings.kt`
- Modify: `Strings.kt`、`ui/habit/ChildGoalTrackerScreen.kt`、`ui/schedule/ChildScheduleTimelineScreen.kt`、`ChildScheduleCopyTest.kt`（+4）

**Interfaces:**
- Consumes: `localizedPresetText`（时间线页日程/任务标题）、`LocalStrings`/`LocalDisplayLanguage`。
- Produces: 两张表（15 + 7 字段）、`AppStrings.childGoalTracker` / `AppStrings.childScheduleTimeline`。

- [ ] **Step 1: 追加测试（红）**

```kotlin
    @Test
    fun childGoalTrackerCopyDiffers() {
        val zh = AppStringsZh.childGoalTracker
        val en = AppStringsEn.childGoalTracker
        assertDiffers(
            zh.title to en.title,
            zh.completionRateTitle to en.completionRateTitle,
            zh.streakLabel to en.streakLabel,
            zh.todayDoneLabel to en.todayDoneLabel,
            zh.todayTotalLabel to en.todayTotalLabel,
            zh.dailyGoalsTitle to en.dailyGoalsTitle,
            zh.suggestionAllTasks to en.suggestionAllTasks,
            zh.suggestionThreeBasic to en.suggestionThreeBasic,
            zh.suggestionPomodoro to en.suggestionPomodoro,
            zh.suggestionReview to en.suggestionReview,
            zh.encouragePerfect to en.encouragePerfect,
            zh.encourageHigh to en.encourageHigh,
            zh.encourageMid to en.encourageMid,
            zh.encourageStart to en.encourageStart,
        )
    }

    @Test
    fun childGoalTrackerInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childGoalTracker
        val en = AppStringsEn.childGoalTracker
        assertEquals("3 / 5 任务已完成", zh.tasksCompleted(3, 5))
        assertEquals("3 / 5 tasks completed", en.tasksCompleted(3, 5))
        assertEquals("1 / 1 tasks completed", en.tasksCompleted(1, 1))
    }

    @Test
    fun childScheduleTimelineCopyDiffers() {
        val zh = AppStringsZh.childScheduleTimeline
        val en = AppStringsEn.childScheduleTimeline
        assertDiffers(
            zh.title to en.title,
            zh.subtitle to en.subtitle,
            zh.emptyTitle to en.emptyTitle,
            zh.emptyHint to en.emptyHint,
            zh.nowBadge to en.nowBadge,
        )
    }

    @Test
    fun childScheduleTimelineInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childScheduleTimeline
        val en = AppStringsEn.childScheduleTimeline
        assertEquals("现在 07:30", zh.nowLabel("07:30"))
        assertEquals("Now 07:30", en.nowLabel("07:30"))
        assertEquals("🔗 完成作业 · 完成后记得打卡哦", zh.linkedTaskLine("完成作业"))
        assertEquals("🔗 Finish homework · remember to check in when done", en.linkedTaskLine("Finish homework"))
    }
```

（`tasksCompleted(1,1)` 的英文单复数：`"$done / $total ${if (total == 1) "task" else "tasks"} completed"`。）

- [ ] **Step 2: 确认红**（两个 unresolved reference）

- [ ] **Step 3: 实现**

3a. 创建 `ChildGoalTrackerStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端目标追踪页（ui/habit/ChildGoalTrackerScreen.kt）文案表（阶段 2 W5）。
 * 四条"每日目标建议"与四条鼓励语是纯渲染文案；完成率/数量均为数字格式，不本地化。
 * [tasksCompleted] 英文单复数用行内条件。
 */
data class ChildGoalTrackerStrings(
    val title: String,
    val completionRateTitle: String,
    val tasksCompleted: (done: Int, total: Int) -> String,
    val streakLabel: String,
    val todayDoneLabel: String,
    val todayTotalLabel: String,
    val dailyGoalsTitle: String,
    val suggestionAllTasks: String,
    val suggestionThreeBasic: String,
    val suggestionPomodoro: String,
    val suggestionReview: String,
    val encouragePerfect: String,
    val encourageHigh: String,
    val encourageMid: String,
    val encourageStart: String,
)

val ChildGoalTrackerStringsZh = ChildGoalTrackerStrings(
    title = "📈 目标追踪",                                                      // :49
    completionRateTitle = "今日目标完成率",                                      // :61
    tasksCompleted = { done, total -> "$done / $total 任务已完成" },             // :65
    streakLabel = "连续打卡天数",                                               // :85
    todayDoneLabel = "今日完成",                                                // :86
    todayTotalLabel = "今日总任务",                                             // :87
    dailyGoalsTitle = "📋 每日学习目标",                                        // :94
    suggestionAllTasks = "完成当天所有学习任务",                                  // :98
    suggestionThreeBasic = "至少完成3个基础任务",                                // :99
    suggestionPomodoro = "番茄钟专注至少25分钟",                                  // :100
    suggestionReview = "复习1道错题",                                           // :101
    encouragePerfect = "🎉 完美！今日目标全部达成！",                             // :123
    encourageHigh = "💪 快完成了，再接再厉！",                                    // :124
    encourageMid = "👍 不错的进展，继续加油！",                                   // :125
    encourageStart = "🚀 开始行动吧，每一步都算数！",                             // :126
)

val ChildGoalTrackerStringsEn = ChildGoalTrackerStrings(
    title = "📈 Goal tracking",
    completionRateTitle = "Today's completion rate",
    tasksCompleted = { done, total -> "$done / $total ${if (total == 1) "task" else "tasks"} completed" },
    streakLabel = "Check-in streak (days)",
    todayDoneLabel = "Done today",
    todayTotalLabel = "Total tasks today",
    dailyGoalsTitle = "📋 Daily learning goals",
    suggestionAllTasks = "Finish all of today's learning tasks",
    suggestionThreeBasic = "Complete at least 3 basic tasks",
    suggestionPomodoro = "Focus for at least 25 minutes with Pomodoro",
    suggestionReview = "Review 1 mistake",
    encouragePerfect = "🎉 Perfect — all of today's goals achieved!",
    encourageHigh = "💪 Almost there — keep it up!",
    encourageMid = "👍 Good progress — keep going!",
    encourageStart = "🚀 Get started — every step counts!",
)
```

3b. 创建 `ChildScheduleTimelineStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端今日时间线页（ui/schedule/ChildScheduleTimelineScreen.kt）文案表（阶段 2 W5）。
 * 日程标题与关联任务标题在渲染点包 [localizedPresetText]（预设译出、自建原样）。
 */
data class ChildScheduleTimelineStrings(
    val title: String,
    val nowLabel: (time: String) -> String,
    val subtitle: String,
    val emptyTitle: String,
    val emptyHint: String,
    val nowBadge: String,
    val linkedTaskLine: (title: String) -> String,
)

val ChildScheduleTimelineStringsZh = ChildScheduleTimelineStrings(
    title = "📅 今日时间线",                                                    // :47
    nowLabel = { time -> "现在 $time" },                                        // :68
    subtitle = "看看今天还有哪些安排吧～",                                        // :70
    emptyTitle = "今天没有日程安排",                                             // :86
    emptyHint = "轻松的一天，自由安排吧！",                                       // :87
    nowBadge = "👈 现在",                                                       // :162
    linkedTaskLine = { title -> "🔗 $title · 完成后记得打卡哦" },               // :170
)

val ChildScheduleTimelineStringsEn = ChildScheduleTimelineStrings(
    title = "📅 Today's timeline",
    nowLabel = { time -> "Now $time" },
    subtitle = "Here's what's left today",
    emptyTitle = "No schedule today",
    emptyHint = "A free day — plan it your way!",
    nowBadge = "👈 Now",
    linkedTaskLine = { title -> "🔗 $title · remember to check in when done" },
)
```

3c. `Strings.kt`：两组字段/Zh/En 各三处（childHabitChallenge 之后）。

3d. 迁移 `ChildGoalTrackerScreen.kt`：import `com.hstudy.app.i18n.LocalStrings`；主 composable `val strings = LocalStrings.current.childGoalTracker`。替换：titulo :49 → `strings.title`；completionRateTitle :61；`Text("${todayCompleted} / ${todayTotal} 任务已完成", …)` :65 → `Text(strings.tasksCompleted(todayCompleted, todayTotal), …)`；三标签 :85-87 → `strings.streakLabel/todayDoneLabel/todayTotalLabel`；dailyGoalsTitle :94；suggestions 列表 :98-101 四条 → `strings.suggestionAllTasks to (…)` 等；四 encouragement :123-126 → `strings.encouragePerfect/High/Mid/Start`。

3e. 迁移 `ChildScheduleTimelineScreen.kt`：import `LocalDisplayLanguage`/`LocalStrings`/`localizedPresetText`；主 composable `val strings = LocalStrings.current.childScheduleTimeline`；`TimelineItem` 加 `val displayLanguage = LocalDisplayLanguage.current` 与 `val strings = LocalStrings.current.childScheduleTimeline`。替换：title :47；`Text("现在 ${…}", …)` :68 → `Text(strings.nowLabel(com.hstudy.app.ui.dt.strFormat("%02d:%02d", …)), …)`；subtitle :70；emptyTitle :86；emptyHint :87；`Text(schedule.title, …)` :159 → `Text(localizedPresetText(schedule.title, displayLanguage), …)`；`Text("👈 现在", …)` :162 → `Text(strings.nowBadge, …)`；`Text("🔗 $linkedTaskTitle · 完成后记得打卡哦", …)` :170 → `Text(strings.linkedTaskLine(localizedPresetText(linkedTaskTitle, displayLanguage)), …)`。

验证：两页去注释 CJK 均为 `[]`。

- [ ] **Step 4: 确认绿**：focused 12 + 63；全量 **185/0**。

- [ ] **Step 5: 暂存**（6 文件：2 新表、Strings.kt、2 页面、测试文件）。

---

### Task 7: 闭集 + 跨表钉死 + 闸门登记 + 人工清单 + 四闸 + 唯一 W5 commit

**Files:**
- Modify: `ChildScheduleCopyTest.kt`（+1）、`StringsCopyTest.kt`（闭集 +1）、`scripts/i18n_template_diff.py`、`docs/i18n-stage1-verification.md`

- [ ] **Step 1: 追加跨表钉死测试**

```kotlin
    @Test
    fun crossTableVocabularyPinned() {
        // 「保存」与 W2/W3 编辑页同值同译
        assertEquals("保存", AppStringsZh.parentTaskEdit.saveButton)
        assertEquals("Save", AppStringsEn.parentTaskEdit.saveButton)
        // 「取消」「删除」复用公共表
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
        assertEquals("删除", AppStringsZh.common.deleteContentDescription)
        assertEquals("Delete", AppStringsEn.common.deleteContentDescription)
    }
```

- [ ] **Step 2: 闭集 +1**：`EN_STATIC_POINTS_MENTIONS` 末尾（W4 块之后）追加：

```kotlin
    // ---- W5 Task 2 每日目标页新增 1 条（英文原文逐条留在行尾注释里）。
    "childDailyTarget.allDoneBonus",          // "🎉 All goals achieved! Bonus +10 points (awaiting approval)"
```

（其余 W5 新静态值均不含 `point`；函数型字段不进闭集。）

- [ ] **Step 3: 等价闸门登记**

3a. `W4_REV` 后加：
```python
W5_REV = "5d65128"        # 阶段 2 W5（习惯/目标/日程）迁移前 = W4 收口提交
```
3b. `BRANCH_PAGES` 末尾追加：
```python
    # ---- 阶段 2 W5（习惯/目标/日程，W5_REV）----
    (W5_REV, "com/hstudy/app/ui/target/ChildDailyTargetScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/schedule/ParentScheduleManageScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/schedule/ScheduleAlarmHelper.kt"),
    (W5_REV, "com/hstudy/app/ui/habit/ChildHabitChallengeScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/habit/ChildGoalTrackerScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/schedule/ChildScheduleTimelineScreen.kt"),
```
3c. `BRANCH_TABLES` 追加 6 个表名：`"ChildDailyTargetStrings", "ParentScheduleStrings", "ScheduleAlarmStrings", "ChildHabitChallengeStrings", "ChildGoalTrackerStrings", "ChildScheduleTimelineStrings"`。
3d. `DISCLOSED_RESIDUALS` 末尾追加 24 条（Ruling 25 canonical 页面字面量）：

```python
    # ---- 阶段 2 W5：页面内预设 canonical 与账本/成就字面量（页面单边；值不进表，渲染走注册表/localizedPresetText）----
    # 日常目标标题 8 + 复盘标签 6：落库/账本 canonical（注册表 W5 组收录译文，渲染时本地化）。
    {"page": "复习错题", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:23 dailyTargetDefs 标题，账本 description 用 canonical"},
    {"page": "专注2个番茄钟", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "完成作业", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "预习新课", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "阅读30分钟", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "英语听说训练", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "整理笔记", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "体育锻炼", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "今天很专注", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:310 tagOptions 落偏好 canonical"},
    {"page": "完成了难题", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "复习了错题", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "有点分心", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "时间不够用", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "明天会更好", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    # 账本 description 模板（canonical，含目标标题/挑战标题）
    {"page": "完成每日目标：⟨$⟩", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:215 checkIn description（账本）"},
    {"page": "每日学习目标全部达成奖励", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:225 checkIn description（账本）"},
    {"page": "习惯打卡: ⟨$⟩", "table": None, "reason": "W5 Ruling 25：ChildHabitChallengeScreen.kt:87/:102 checkIn description（账本）"},
    # 日程示例标题 5：落库 canonical（注册表 W5 组收录译文）
    {"page": "起床啦", "table": None, "reason": "W5 Ruling 25：ParentScheduleManageScreen.kt:81 示例日程落库标题"},
    {"page": "吃早餐", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    {"page": "开始写作业", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    {"page": "阅读时间", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    {"page": "准备睡觉", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    # 成就解锁 canonical（tryUnlockAchievement 写库参数）
    {"page": "习惯达人", "table": None, "reason": "W5 Ruling 25：ChildHabitChallengeScreen.kt:94 tryUnlockAchievement 写库参数（achievementCopy canonical）"},
    {"page": "完成一个21天挑战", "table": None, "reason": "W5 Ruling 25：同上（成就描述）"},
```

3e. 文档读数同步（脚本内三处 + 读数注释）：
- 文件头 `全部二十八张表` → `全部三十四张表`；「哪些东西今天能钉回历史位置」段 `二十八张` → `三十四张` 两处；最早 rev 口径改为 W5：`阶段 2 W5 那六张表是 W5 提交才落进历史的…可用最早 rev 就是 W5 提交本身；在它之前（W4 收口 5d65128、W3 收口 14c4d82、W2 收口 aae364e、W1 收口 5deed68、4d28c8e、c3bd94a）一律 git show …失败`（W2 期退出码块保留作历史）；`--tables-rev` help `二十八张` → `三十四张`、`阶段 2 W4 提交起` → `阶段 2 W5 提交起`；
- 读数注释：`表里现在 **59 条**`（7 条两侧同形 + 45 条只看页面 + 7 条只看表侧）；默认组 6 条、另外 **53** 条默认组用不到；`--branch` 组碰到 **58** 条、只剩 **1** 条"（未用到）"；归因 `W5 Task 7 复算`。**数字以 Step 5 实跑为准**。

- [ ] **Step 4: 人工清单与验证文档读数**

4a. `docs/i18n-stage1-verification.md` §2 末尾（V-51 之后）追加：

```markdown
### 阶段 2 W5（习惯/目标/日程）追加项
- [ ] V-52 英文态每日学习目标：设定/推荐/复盘三块全英文；8 个目标标题与 6 个复盘标签显示英文（注册表）、历史账本 note 仍为中文 canonical；智能推荐卡片标题/理由仍是中文（GoalRecommendationEngine 属 W8，spec §14 明示残留）
- [ ] V-53 英文态家长日程管理：提示/空态/示例载入/编辑对话框/星期行（Every day / Mon, Tue…）全英文；示例日程标题显示英文（注册表）、家长自建原样；删除 snackbar 的标题已本地化
- [ ] V-54 英文态日程闹钟通知：到点通知标题与正文为英文（发出时取词，切语言后最迟次日生效）；关联任务的"完成后记得打卡"句随语言；预设日程标题译出、自建原样
- [ ] V-55 英文态习惯挑战：列表/空态/对话框/打卡按钮与成功文案全英文（+N points one/other）；挑战标题/描述是用户内容原样；账本 note 与成就解锁数据仍为中文 canonical
- [ ] V-56 英文态目标追踪：完成率卡/统计标签/四条建议/四条鼓励语全英文
- [ ] V-57 英文态今日时间线：当前时间提示/空态/时间线卡片/👈 Now 徽标全英文；日程与关联任务标题预设译出、自建原样
```

4b. 同文件读数更新（以 Step 5 实跑为准）：§1 `--branch` 行 → `39 条页面组条目 / 38 个文件 × 全部 34 张表` + 实跑 distinct/披露后/strict；测试行 `172 tests` → `186 tests（含 W1 12、W2 9、W3 9、W4 8 与 W5 14 条…）`；StringsCopyTest `二十八张` → `三十四张`（两处）、枚举追加 `+ W5 的 childDailyTarget / parentSchedule / scheduleAlarm / childHabitChallenge / childGoalTracker / childScheduleTimeline 六张`、`grep -c … → 28` → `→ 34`；W4 守卫行后加 `W5 的六源守卫另在 ChildScheduleCopyTest（13 条，含通知正文双语钉死与跨表 pin）。`；L-19 `（现 35 条）` → `（现 59 条）`、`"34 命中 / 1 未用到"` → `"58 命中 / 1 未用到"`（其余逐字节不动）；§6 对应读数（W5 登记后实测、39/38、34 张表、59 条、58 命中、53 条未用到）。

- [ ] **Step 5: 全量四闸（顺序，逐条 exit 0）**

```bash
PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py
PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py
PYTHONIOENCODING=utf-8 python scripts/i18n_template_diff.py --branch
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest
```
Expected: 边界 exit 0；自测 195 断言全 OK；`--branch` exit 0（[A] 0 missing/0 invented，[B] 0 diff，6 新表 6 新源并入整枝组，24 条新披露"已披露"命中，无登记过期）；assembleDebug OK；**186 tests / 0 failures**（172 + 14）。任何一闸红：修到绿；`--branch` 报 missing/invented 时按 Ruling 修实现/表值，**不得加披露掩盖**（24 条之外不许新增）。

- [ ] **Step 6: 落唯一 W5 commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildDailyTargetStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentScheduleStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ScheduleAlarmStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildHabitChallengeStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildGoalTrackerStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildScheduleTimelineStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/target/ChildDailyTargetScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ParentScheduleManageScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ScheduleAlarmHelper.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildHabitChallengeScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildGoalTrackerScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ChildScheduleTimelineScreen.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildScheduleCopyTest.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt \
        scripts/i18n_template_diff.py \
        docs/i18n-stage1-verification.md
git commit -m "feat(i18n): 阶段 2 W5——习惯/目标/日程五页双语化（6 张表）、注册表扩页面内预设、日程闹钟发出时取词、等价闸门登记"
git status
```
Expected: commit 成功，工作树干净。**到此为止，不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译、186 单测、字节等价闸门整枝组（含 24 条 canonical 披露）、存储边界、中文态逐字节不变、注册表 19 条新 key 的两语解析与 ZH 恒等、`notificationBody` 双语钉死。
- 不可自动证明：英文态五页视觉呈现与通知实际弹出语言（人工 V-52…V-57）；智能推荐卡片（GoalRecommendationEngine）英文态仍是中文（W8 归属，spec §14 明示残留）。
- 不以"CI 绿"表述"双语已验证"。
