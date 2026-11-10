# i18n 阶段 2 W2（家长任务管理）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把家长任务管理 4 页（任务管理/任务编辑/布置任务/积分模板）迁入双语文案表：新增 4 张页面表，复用既有共享值（分类词、通用按钮、家长首页菜单标题），预设内容（模板名/描述/年龄段、建议任务标题）在渲染点包 `localizedPresetText`；等价闸门登记本波。

**Architecture:** 沿用阶段 1/W1 机制（`AppStrings` 聚合 + `stringsFor` + `LocalStrings`/`LocalDisplayLanguage` + 函数型插值字段）。**复用优先**：`全部/基础/提升/习惯` 复用 `TaskListStrings.filter*`；`编辑/删除/取消` 复用 `CommonStrings`；`任务管理` 复用 `ParentHomeStrings.menuTaskManageTitle`（已存在，EN "Task management"）——不新建同值字段。预设 canonical 内容（`TaskTemplate.name/description/ageRange`、`getSingleTaskSuggestions().title`、任务卡 `task.title/description`）只在**渲染表达式**包 `localizedPresetText(raw, displayLanguage)`；芯片 `onClick` 写入 state 的仍是 canonical 原值（Ruling 25：落库不本地化）。

**Tech Stack:** Kotlin 2.1.20 / Compose Multiplatform 1.7.3（不升级）；commonTest 经 `:composeApp:testDebugUnitTest`；Python 闸门脚本。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§12 W2 行；§4 注册表渲染点；§5 Ruling 25）

## Global Constraints（逐字摘自 spec，每个任务隐含包含）

- 不改 schema、不写迁移、不动 `data/**` 的任何文件（含种子、账本描述、同步 DTO、偏好键）。（§1）
- 文案表/注册表的任何字符串**不得**到达：DB 写入、bizKey 构造、幂等/去重比较、同步 DTO 字段、偏好键值（Ruling 25）。芯片 `onClick` 把 `s.title`/`t.title` 写进 state（最终落库）——**保持 canonical 原值**，只有 `label` 渲染包 `localizedPresetText`。（§5）
- `localizedPresetText` 及一切 `strings.*` 只允许出现在渲染表达式里。（§5）
- 不升级 Compose Multiplatform / Kotlin 版本；不动 `Plurals.kt` 机制（本波复用既有 `pointsWord`，不新增窄函数）。（§1）
- W2 唯一改动形态：`"文本"` → `strings.xxx` / `localizedPresetText(...)` 等价替换；**逻辑行零变化**（含 `onClick` 赋值、`when` 分支结构、状态变量）。
- 本波不迁难度/分类的**注册表映射**（属 W9）；但页面里 `when` 出来的分类/难度**芯片标签**是 UI 文案，本波入表（与 W1 科目标签同理）。预设任务标题/模板名等 canonical 内容本波在渲染点包 `localizedPresetText`（§4），阶段 1 已迁页面的同类 sweep 仍属 W9。
- 平台文件零改动（本波无通知/分享）。
- 每波本地四闸 + BRANCH_PAGES 登记必须绿。（§12）
- **按波提交，一波一个 commit**：Task 1–4 只验证不提交，Task 5 落唯一 W2 commit。（§12）
- 推送经用户逐次批准；本计划**不含推送步骤**。PAT 绝不写入仓库、文档或日志。
- 不以"CI 绿"表述"双语已验证"（§11）。

## 环境与命令（零上下文执行者必读）

- 工作目录：`D:\game\hstudy-1`（Git Bash）。Python 脚本带 `PYTHONIOENCODING=utf-8`。
- Gradle 内联：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew …`
- 基线：**146 tests 全绿**（`5deed68`，W1 收口）。W2 新增 9 条（ParentTaskCopyTest），终态 **155 tests / 0 failures**。
- 迁移前 rev：**`5deed68`**（W1 收口提交）。
- 零 CJK 验收口径：**去注释后的代码字面量**（中文注释属 Ruling 18，不删不译）。本波 4 页无落库原文字面量，故代码字面量应归零。

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `i18n/ParentTaskManageStrings.kt` | 新增 | 任务管理页 3 个新字段（标题复用 parentHome、分类复用 taskList、删除复用 common；新增 添加说明/编辑说明/积分行） |
| `i18n/ParentTaskEditStrings.kt` | 新增 | 任务编辑页 30 个字段 |
| `i18n/ParentAssignTaskStrings.kt` | 新增 | 布置任务页 18 个字段 |
| `i18n/ParentTaskTemplateStrings.kt` | 新增 | 积分模板页 15 个字段 |
| `i18n/Strings.kt` | 修改 | `AppStrings` 加 4 字段 + Zh/En 各 4 行 |
| `ui/parent/ParentTaskManageScreen.kt` | 修改 | 12 处字面量 → 表/复用；task.title/description 渲染包注册表 |
| `ui/parent/ParentTaskEditScreen.kt` | 修改 | 26 处字面量 → 表；建议芯片 label 包注册表 |
| `ui/parent/ParentAssignTaskScreen.kt` | 修改 | 17 处字面量 → 表；建议芯片 label 包注册表 |
| `ui/parent/ParentTaskTemplateScreen.kt` | 修改 | 16 处字面量 → 表；模板名/描述/年龄段/预览标题包注册表 |
| `commonTest/i18n/ParentTaskCopyTest.kt` | 新增 | 9 条：4 表漏译/函数钉死 + 跨表词汇钉死 |
| `scripts/i18n_template_diff.py` | 修改 | `W2_REV` + 4 BRANCH_PAGES + 4 BRANCH_TABLES（无新披露） |
| `docs/i18n-stage1-verification.md` | 修改 | §2 追加 V-40…V-43 |

（i18n 路径前缀 `composeApp/src/commonMain/kotlin/com/hstudy/app/`；commonTest 前缀 `composeApp/src/commonTest/kotlin/com/hstudy/app/`。）

**复用决定（为什么不再建同值字段）：** `任务管理` 已是 `ParentHomeStrings.menuTaskManageTitle`（ZH "任务管理"/EN "Task management"）；`全部/基础/提升/习惯` 已是 `TaskListStrings.filter*`（EN All/Basic/Advanced/Habits）；`编辑/删除/取消` 已是 `CommonStrings`。同一个中文值只允许一个英文出处（CommonStrings KDoc 既定原则），跨表同值由 `ParentTaskCopyTest.crossTableVocabularyPinned` 钉住。

---

### Task 1: ParentTaskManageStrings + 任务管理页

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentTaskManageStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentTaskManageScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ParentTaskCopyTest.kt`（新建）

**Interfaces:**
- Consumes: `TaskListStrings.filter*`、`CommonStrings`、`ParentHomeStrings.menuTaskManageTitle`、`localizedPresetText`、`pointsWord`。
- Produces: `data class ParentTaskManageStrings(addContentDescription, editContentDescription, pointsLine)`、`AppStrings.parentTaskManage`。

- [ ] **Step 1: 写测试（编译红）**

创建 `ParentTaskCopyTest.kt`：

```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W2 家长任务管理文案表守卫（沿用 StringsCopyTest/MistakeCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b）；跨表同值钉死。
 */
class ParentTaskCopyTest {

    @Test
    fun parentTaskManageCopyDiffers() {
        val zh = AppStringsZh.parentTaskManage
        val en = AppStringsEn.parentTaskManage
        assertDiffers(
            zh.addContentDescription to en.addContentDescription,
            zh.editContentDescription to en.editContentDescription,
        )
    }

    @Test
    fun parentTaskManagePointsLinePinsBothLanguages() {
        val zh = AppStringsZh.parentTaskManage
        val en = AppStringsEn.parentTaskManage
        assertEquals("基础 3分 · 奖励 +2分", zh.pointsLine(3, 2))
        assertEquals("Base 3 points · Bonus +2 points", en.pointsLine(3, 2))
        assertEquals("基础 1分", zh.pointsLine(1, 0))
        assertEquals("Base 1 point", en.pointsLine(1, 0))
    }
}
```

- [ ] **Step 2: 确认红**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.ParentTaskCopyTest"`
Expected: `Unresolved reference 'parentTaskManage'`。

- [ ] **Step 3: 实现**

3a. 创建 `ParentTaskManageStrings.kt`：

```kotlin
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
```

3b. `Strings.kt`：`AppStrings` 加 `val parentTaskManage: ParentTaskManageStrings,`；Zh/En 各加一行。

3c. 迁移 `ParentTaskManageScreen.kt`（外层 `val strings = LocalStrings.current.parentTaskManage`、`val taskList = LocalStrings.current.taskList`、`val common = LocalStrings.current.common`、`val parentHome = LocalStrings.current.parentHome`、`val displayLanguage = LocalDisplayLanguage.current`；`TaskManageCard` 内同样读这些 Local）：

| 原名面量 | 替换 |
|---|---|
| `title = "任务管理"`（:37） | `title = parentHome.menuTaskManageTitle` |
| `contentDescription = "添加任务"`（:40） | `strings.addContentDescription` |
| `Text("全部")`（:53） | `Text(taskList.filterAll)` |
| `Text("基础")`（:58） | `Text(taskList.filterBasic)` |
| `Text("提升")`（:63） | `Text(taskList.filterEnhancement)` |
| `Text("习惯")`（:68） | `Text(taskList.filterHabits)` |
| `Text(task.title, …)`（:117） | `Text(localizedPresetText(task.title, displayLanguage), …)` |
| `Text(task.description, …)`（:126） | `Text(localizedPresetText(task.description, displayLanguage), …)` |
| `Text("基础 ${task.points}分${if (task.bonusPoints > 0) " · 奖励 +${task.bonusPoints}分" else ""}", …)`（:128） | `Text(strings.pointsLine(task.points, task.bonusPoints), …)` |
| `contentDescription = "编辑"`（:131） | `strings.editContentDescription` |
| `contentDescription = "删除"`（:132） | `common.deleteContentDescription` |

顶部补 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。

验证：去注释 CJK 扫描 `ParentTaskManageScreen.kt` → 0 代码字面量。

- [ ] **Step 4: 确认绿**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.ParentTaskCopyTest"`
Expected: 2 条通过。

---

### Task 2: ParentTaskEditStrings + 任务编辑页

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentTaskEditStrings.kt`
- Modify: `Strings.kt`、`ui/parent/ParentTaskEditScreen.kt`、`ParentTaskCopyTest.kt`（+2 测试）

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun parentTaskEditCopyDiffers() {
        val zh = AppStringsZh.parentTaskEdit
        val en = AppStringsEn.parentTaskEdit
        assertDiffers(
            zh.newTitle to en.newTitle,
            zh.editTitle to en.editTitle,
            zh.saveButton to en.saveButton,
            zh.titleLabel to en.titleLabel,
            zh.titlePlaceholder to en.titlePlaceholder,
            zh.suggestionsHeading to en.suggestionsHeading,
            zh.descriptionLabel to en.descriptionLabel,
            zh.descriptionPlaceholder to en.descriptionPlaceholder,
            zh.categoryHeading to en.categoryHeading,
            zh.categoryBasic to en.categoryBasic,
            zh.categoryEnhancement to en.categoryEnhancement,
            zh.categoryHabit to en.categoryHabit,
            zh.frequencyHeading to en.frequencyHeading,
            zh.freqDaily to en.freqDaily,
            zh.freqWeekly to en.freqWeekly,
            zh.freqMonthly to en.freqMonthly,
            zh.freqOnce to en.freqOnce,
            zh.mandatoryTitle to en.mandatoryTitle,
            zh.difficultyHeading to en.difficultyHeading,
            zh.difficultyNormal to en.difficultyNormal,
            zh.difficultyAdvanced to en.difficultyAdvanced,
            zh.difficultyChallenge to en.difficultyChallenge,
            zh.difficultyNoteAdvanced to en.difficultyNoteAdvanced,
            zh.difficultyNoteChallenge to en.difficultyNoteChallenge,
            zh.difficultyNoteNormal to en.difficultyNoteNormal,
            zh.basePointsLabel to en.basePointsLabel,
            zh.bonusPointsLabel to en.bonusPointsLabel,
            zh.deleteButton to en.deleteButton,
        )
    }

    @Test
    fun parentTaskEditInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentTaskEdit
        val en = AppStringsEn.parentTaskEdit
        assertEquals("📝 完成当天作业 +3", zh.suggestionChip("📝", "完成当天作业", 3))
        assertEquals("📝 Finish today's homework +3", en.suggestionChip("📝", "Finish today's homework", 3))
        assertEquals("若当天未完成此任务，将自动扣减 5 分", zh.mandatoryHint(5))
        assertEquals("If not completed that day, 5 points will be deducted automatically", en.mandatoryHint(5))
        assertEquals("If not completed that day, 1 point will be deducted automatically", en.mandatoryHint(1))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'parentTaskEdit'`）

- [ ] **Step 3: 实现**

3a. 创建 `ParentTaskEditStrings.kt`（30 字段）：

```kotlin
package com.hstudy.app.i18n

/**
 * 家长任务编辑页（ui/parent/ParentTaskEditScreen.kt）文案表（阶段 2 W2）。
 *
 * 分类芯片「基础学习/提升学习/习惯态度」与频率「每日/每周/每月/一次性」是 UI 文案（when 映射），入表；
 * 难度芯片带星数（"普通⭐"…）也是 UI 文案，英文与 W0 注册表难度译名对齐（Normal/Advanced/Challenge + 星）。
 * 建议芯片 label 里的任务标题是 canonical（getSingleTaskSuggestions），渲染点包 [localizedPresetText]；
 * onClick 写入 state 的仍是原值（Ruling 25）。[suggestionChip] 收已本地化的标题与 emoji/积分。
 * 「保存」无既有共享字段，本表新建；「删除此任务」是按钮文案（非 contentDescription），本表新建。
 */
data class ParentTaskEditStrings(
    val newTitle: String,
    val editTitle: String,
    val saveButton: String,
    val titleLabel: String,
    val titlePlaceholder: String,
    val suggestionsHeading: String,
    val suggestionChip: (emoji: String, title: String, points: Int) -> String,
    val descriptionLabel: String,
    val descriptionPlaceholder: String,
    val categoryHeading: String,
    val categoryBasic: String,
    val categoryEnhancement: String,
    val categoryHabit: String,
    val frequencyHeading: String,
    val freqDaily: String,
    val freqWeekly: String,
    val freqMonthly: String,
    val freqOnce: String,
    val mandatoryTitle: String,
    val mandatoryHint: (points: Int) -> String,
    val difficultyHeading: String,
    val difficultyNormal: String,
    val difficultyAdvanced: String,
    val difficultyChallenge: String,
    val difficultyNoteAdvanced: String,
    val difficultyNoteChallenge: String,
    val difficultyNoteNormal: String,
    val basePointsLabel: String,
    val bonusPointsLabel: String,
    val deleteButton: String,
)

val ParentTaskEditStringsZh = ParentTaskEditStrings(
    newTitle = "新建任务",
    editTitle = "编辑任务",
    saveButton = "保存",
    titleLabel = "任务名称",
    titlePlaceholder = "如：完成当天作业",
    suggestionsHeading = "💡 从模板快速选择",
    suggestionChip = { emoji, title, points -> "$emoji $title +$points" },
    descriptionLabel = "任务描述",
    descriptionPlaceholder = "补充说明（选填）",
    categoryHeading = "任务分类",
    categoryBasic = "基础学习",
    categoryEnhancement = "提升学习",
    categoryHabit = "习惯态度",
    frequencyHeading = "任务频率",
    freqDaily = "每日",
    freqWeekly = "每周",
    freqMonthly = "每月",
    freqOnce = "一次性",
    mandatoryTitle = "⚠️ 每日必须完成",
    mandatoryHint = { points -> "若当天未完成此任务，将自动扣减 $points 分" },
    difficultyHeading = "难度等级",
    difficultyNormal = "普通⭐",
    difficultyAdvanced = "进阶⭐⭐",
    difficultyChallenge = "挑战⭐⭐⭐",
    difficultyNoteAdvanced = "进阶任务积分×1.2倍",
    difficultyNoteChallenge = "挑战任务积分×1.5倍，鼓励孩子挑战高难度！",
    difficultyNoteNormal = "普通难度，按基础积分计算",
    basePointsLabel = "基础积分",
    bonusPointsLabel = "奖励积分",
    deleteButton = "删除此任务",
)

val ParentTaskEditStringsEn = ParentTaskEditStrings(
    newTitle = "New task",
    editTitle = "Edit task",
    saveButton = "Save",
    titleLabel = "Task name",
    titlePlaceholder = "e.g. Finish today's homework",
    suggestionsHeading = "💡 Quick pick from templates",
    suggestionChip = { emoji, title, points -> "$emoji $title +$points" },
    descriptionLabel = "Description",
    descriptionPlaceholder = "Extra details (optional)",
    categoryHeading = "Category",
    categoryBasic = "Basic learning",
    categoryEnhancement = "Advanced learning",
    categoryHabit = "Habits & attitude",
    frequencyHeading = "Frequency",
    freqDaily = "Daily",
    freqWeekly = "Weekly",
    freqMonthly = "Monthly",
    freqOnce = "One-time",
    mandatoryTitle = "⚠️ Must-do daily",
    mandatoryHint = { points ->
        "If not completed that day, $points ${pointsWord(points)} will be deducted automatically"
    },
    difficultyHeading = "Difficulty",
    difficultyNormal = "Normal ⭐",
    difficultyAdvanced = "Advanced ⭐⭐",
    difficultyChallenge = "Challenge ⭐⭐⭐",
    difficultyNoteAdvanced = "Advanced tasks earn ×1.2 points",
    difficultyNoteChallenge = "Challenge tasks earn ×1.5 points — encourage your child to take on hard tasks!",
    difficultyNoteNormal = "Normal difficulty, scored at base points",
    basePointsLabel = "Base points",
    bonusPointsLabel = "Bonus points",
    deleteButton = "Delete this task",
)
```

3b. `Strings.kt` 加 `val parentTaskEdit: ParentTaskEditStrings,` + 两行聚合。

3c. 迁移 `ParentTaskEditScreen.kt`（`val strings = LocalStrings.current.parentTaskEdit`、`val displayLanguage = LocalDisplayLanguage.current`）：

| 原名面量 | 替换 |
|---|---|
| `if (taskId == 0L) "新建任务" else "编辑任务"`（:65） | `if (taskId == 0L) strings.newTitle else strings.editTitle` |
| `Text("保存", …)`（:87） | `Text(strings.saveButton, …)` |
| `label = { Text("任务名称") }`（:103） | `strings.titleLabel` |
| `placeholder = { Text("如：完成当天作业") }`（:104） | `strings.titlePlaceholder` |
| `Text("💡 从模板快速选择", …)`（:110） | `Text(strings.suggestionsHeading, …)` |
| `label = { Text("$catEmoji ${s.title} +${s.points}") }`（:130） | `label = { Text(strings.suggestionChip(catEmoji, localizedPresetText(s.title, displayLanguage), s.points)) }`（onClick 的 `title = s.title` 等**不动**） |
| `label = { Text("任务描述") }`（:139） | `strings.descriptionLabel` |
| `placeholder = { Text("补充说明（选填）") }`（:140） | `strings.descriptionPlaceholder` |
| `Text("任务分类", …)`（:147） | `Text(strings.categoryHeading, …)` |
| `"基础学习"/"提升学习"/"习惯态度"`（:152-154） | `strings.categoryBasic/categoryEnhancement/categoryHabit` |
| `Text("任务频率", …)`（:166） | `Text(strings.frequencyHeading, …)` |
| `"每日"/"每周"/"每月"/"一次性"`（:171-174） | `strings.freqDaily/freqWeekly/freqMonthly/freqOnce` |
| `Text("⚠️ 每日必须完成", …)`（:196） | `Text(strings.mandatoryTitle, …)` |
| `Text("若当天未完成此任务，将自动扣减 ${points.toIntOrNull() ?: 5} 分", …)`（:197） | `Text(strings.mandatoryHint(points.toIntOrNull() ?: 5), …)` |
| `Text("难度等级", …)`（:210） | `Text(strings.difficultyHeading, …)` |
| `listOf(1 to "普通⭐", 2 to "进阶⭐⭐", 3 to "挑战⭐⭐⭐")`（:213） | `listOf(1 to strings.difficultyNormal, 2 to strings.difficultyAdvanced, 3 to strings.difficultyChallenge)` |
| `2 -> "进阶任务积分×1.2倍"`（:223） | `strings.difficultyNoteAdvanced` |
| `3 -> "挑战任务积分×1.5倍，鼓励孩子挑战高难度！"`（:224） | `strings.difficultyNoteChallenge` |
| `else -> "普通难度，按基础积分计算"`（:225） | `strings.difficultyNoteNormal` |
| `label = { Text("基础积分") }`（:237） | `strings.basePointsLabel` |
| `label = { Text("奖励积分") }`（:244） | `strings.bonusPointsLabel` |
| `Text("删除此任务")`（:263） | `Text(strings.deleteButton)` |

顶部补 import：`LocalStrings`、`LocalDisplayLanguage`、`localizedPresetText`。

验证：去注释 CJK 扫描 → 0 代码字面量。

- [ ] **Step 4: 确认绿**（ParentTaskCopyTest 4 条）

---

### Task 3: ParentAssignTaskStrings + 布置任务页

**Files:**
- Create: `i18n/ParentAssignTaskStrings.kt`；Modify: `Strings.kt`、`ui/parent/ParentAssignTaskScreen.kt`、`ParentTaskCopyTest.kt`（+2）

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun parentAssignTaskCopyDiffers() {
        val zh = AppStringsZh.parentAssignTask
        val en = AppStringsEn.parentAssignTask
        assertDiffers(
            zh.title to en.title,
            zh.formHeading to en.formHeading,
            zh.templateQuickSelect to en.templateQuickSelect,
            zh.titleLabel to en.titleLabel,
            zh.titlePlaceholder to en.titlePlaceholder,
            zh.descriptionLabel to en.descriptionLabel,
            zh.pointsLabel to en.pointsLabel,
            zh.noteLabel to en.noteLabel,
            zh.notePlaceholder to en.notePlaceholder,
            zh.assignedMessage to en.assignedMessage,
            zh.assignButton to en.assignButton,
            zh.emptyState to en.emptyState,
            zh.completedStatus to en.completedStatus,
            zh.pendingStatus to en.pendingStatus,
            zh.deletedMessage to en.deletedMessage,
        )
    }

    @Test
    fun parentAssignTaskInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentAssignTask
        val en = AppStringsEn.parentAssignTask
        assertEquals("完成当天作业 +3", zh.suggestionChip("完成当天作业", 3))
        assertEquals("Finish today's homework +3", en.suggestionChip("Finish today's homework", 3))
        assertEquals("📋 今日已布置 (2)", zh.todayHeading(2))
        assertEquals("📋 Assigned today (2)", en.todayHeading(2))
        assertEquals("+3分", zh.pointsBadge(3))
        assertEquals("+3 points", en.pointsBadge(3))
        assertEquals("+1分", zh.pointsBadge(1))
        assertEquals("+1 point", en.pointsBadge(1))
    }
```

- [ ] **Step 2: 确认红**

- [ ] **Step 3: 实现**

3a. 创建 `ParentAssignTaskStrings.kt`（18 字段）：

```kotlin
package com.hstudy.app.i18n

/**
 * 家长布置任务页（ui/parent/ParentAssignTaskScreen.kt）文案表（阶段 2 W2）。
 * 建议芯片 label 的任务标题是 canonical（getSingleTaskSuggestions），渲染包 [localizedPresetText]；
 * onClick 写 state 用原值（Ruling 25）。已布置任务的 a.title/a.parentNote 是用户/落库数据，原样显示。
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
```

3b. `Strings.kt` 加 `val parentAssignTask: ParentAssignTaskStrings,` + 两行。

3c. 迁移 `ParentAssignTaskScreen.kt`（`val strings = LocalStrings.current.parentAssignTask`、`val common = LocalStrings.current.common`、`val displayLanguage = LocalDisplayLanguage.current`）：

| 原名面量 | 替换 |
|---|---|
| `title = "📣 布置今日任务"`（:38） | `strings.title` |
| `Text("✍️ 布置新任务", …)`（:51） | `strings.formHeading` |
| `Text("从模板快速选择：", …)`（:54） | `strings.templateQuickSelect` |
| `label = { Text("${t.title} +${t.points}") }`（:64） | `Text(strings.suggestionChip(localizedPresetText(t.title, displayLanguage), t.points))`（onClick `title = t.title` 等不动） |
| `Text("任务标题")`（:71） | `strings.titleLabel` |
| `Text("如：今天帮妈妈洗碗")`（:72） | `strings.titlePlaceholder` |
| `Text("任务描述（选填）")`（:77） | `strings.descriptionLabel` |
| `Text("积分值")`（:83） | `strings.pointsLabel` |
| `Text("鼓励语（选填）")`（:88） | `strings.noteLabel` |
| `Text("如：相信你一定能做到！")`（:89） | `strings.notePlaceholder` |
| `showSnackbar("✅ 任务已布置给孩子！")`（:105） | `strings.assignedMessage` |
| `Text("📤 布置给孩子")`（:111） | `strings.assignButton` |
| `Text("📋 今日已布置 (${todayAssignments.size})", …)`（:118） | `strings.todayHeading(todayAssignments.size)` |
| `Text("今天还没有布置任务", …)`（:124） | `strings.emptyState` |
| `Text("+${a.points}分", …)`（:138） | `strings.pointsBadge(a.points)` |
| `if (a.isCompleted) "✅ 孩子已完成" else "⏳ 待完成"`（:146） | `if (a.isCompleted) strings.completedStatus else strings.pendingStatus` |
| `showSnackbar("已删除任务")`（:154） | `strings.deletedMessage` |
| `contentDescription = "删除"`（:157） | `common.deleteContentDescription` |

`Text(a.title, …)`（:136）与 `Text("💬 ${a.parentNote}", …)`（:142）**不动**（用户/落库数据；后者无汉字字面量）。

验证：去注释 CJK 扫描 → 0 代码字面量。

- [ ] **Step 4: 确认绿**（ParentTaskCopyTest 6 条）

---

### Task 4: ParentTaskTemplateStrings + 积分模板页（注册表渲染接线）

**Files:**
- Create: `i18n/ParentTaskTemplateStrings.kt`；Modify: `Strings.kt`、`ui/parent/ParentTaskTemplateScreen.kt`、`ParentTaskCopyTest.kt`（+2）

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun parentTaskTemplateCopyDiffers() {
        val zh = AppStringsZh.parentTaskTemplate
        val en = AppStringsEn.parentTaskTemplate
        assertDiffers(
            zh.title to en.title,
            zh.usageHeading to en.usageHeading,
            zh.usageHint to en.usageHint,
            zh.previewHeading to en.previewHeading,
            zh.confirmButton to en.confirmButton,
            zh.applyButton to en.applyButton,
        )
    }

    @Test
    fun parentTaskTemplateInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentTaskTemplate
        val en = AppStringsEn.parentTaskTemplate
        assertEquals("当前共有 14 个任务", zh.currentTaskCount(14))
        assertEquals("You currently have 14 tasks", en.currentTaskCount(14))
        assertEquals("🌱 应用「小学低年级」模板？", zh.applyDialogTitle("🌱", "小学低年级"))
        assertEquals("🌱 Apply the “Lower primary” template?", en.applyDialogTitle("🌱", "Lower primary"))
        assertEquals("将替换当前所有任务为模板预设的 14 个任务。", zh.replaceHint(14))
        assertEquals("This replaces all current tasks with the template's 14 tasks.", en.replaceHint(14))
        assertEquals("📝 完成当天作业 (+3分)", zh.previewLine("📝", "完成当天作业", 3))
        assertEquals("📝 Finish today's homework (+3 points)", en.previewLine("📝", "Finish today's homework", 3))
        assertEquals("...还有9个任务", zh.moreTasks(9))
        assertEquals("...and 9 more tasks", en.moreTasks(9))
        assertEquals("✅ 已应用「小学低年级」模板", zh.appliedMessage("小学低年级"))
        assertEquals("✅ Applied the “Lower primary” template", en.appliedMessage("Lower primary"))
        assertEquals("📝 基础×5", zh.basicCount(5))
        assertEquals("📝 Basic ×5", en.basicCount(5))
        assertEquals("📈 提升×4", zh.enhancementCount(4))
        assertEquals("📈 Advanced ×4", en.enhancementCount(4))
        assertEquals("🏆 习惯×5", zh.habitCount(5))
        assertEquals("🏆 Habits ×5", en.habitCount(5))
    }
```

- [ ] **Step 2: 确认红**

- [ ] **Step 3: 实现**

3a. 创建 `ParentTaskTemplateStrings.kt`（15 字段）：

```kotlin
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
    currentTaskCount = { count -> "You currently have $count tasks" },
    applyDialogTitle = { emoji, name -> "$emoji Apply the “$name” template?" },
    replaceHint = { count -> "This replaces all current tasks with the template's $count tasks." },
    previewHeading = "Task preview:",
    previewLine = { emoji, title, points -> "$emoji $title (+$points ${pointsWord(points)})" },
    moreTasks = { count -> "...and $count more tasks" },
    appliedMessage = { name -> "✅ Applied the “$name” template" },
    confirmButton = "Apply",
    applyButton = "Apply this template",
    basicCount = { count -> "📝 Basic ×$count" },
    enhancementCount = { count -> "📈 Advanced ×$count" },
    habitCount = { count -> "🏆 Habits ×$count" },
)
```

3b. `Strings.kt` 加 `val parentTaskTemplate: ParentTaskTemplateStrings,` + 两行。

3c. 迁移 `ParentTaskTemplateScreen.kt`（外层 `val strings = LocalStrings.current.parentTaskTemplate`、`val common = LocalStrings.current.common`、`val displayLanguage = LocalDisplayLanguage.current`；`TemplateCard` 内读 `LocalStrings.current.parentTaskTemplate` 与 `LocalDisplayLanguage.current`）：

| 原名面量 | 替换 |
|---|---|
| `title = "📋 积分模板"`（:34） | `strings.title` |
| `Text("💡 使用说明", …)`（:48） | `strings.usageHeading` |
| `Text("选择一个适合孩子年龄的模板，一键应用到任务列表。应用后会替换当前所有任务。", …)`（:50） | `strings.usageHint` |
| `Text("当前共有 ${tasks.size} 个任务", …)`（:52） | `strings.currentTaskCount(tasks.size)` |
| `Text("${template.emoji} 应用「${template.name}」模板？")`（:72） | `Text(strings.applyDialogTitle(template.emoji, localizedPresetText(template.name, displayLanguage)))` |
| `Text("将替换当前所有任务为模板预设的 ${template.tasks.size} 个任务。", …)`（:75） | `strings.replaceHint(template.tasks.size)` |
| `Text("任务预览:", …)`（:78） | `strings.previewHeading` |
| `Text("$catEmoji ${task.title} (+${task.points}分)", …)`（:85） | `Text(strings.previewLine(catEmoji, localizedPresetText(task.title, displayLanguage), task.points), …)` |
| `Text("...还有${template.tasks.size - 5}个任务", …)`（:89） | `strings.moreTasks(template.tasks.size - 5)` |
| `showSnackbar("✅ 已应用「${template.name}」模板")`（:99） | `showSnackbar(strings.appliedMessage(localizedPresetText(template.name, displayLanguage)))` |
| `Text("确认应用")`（:101） | `strings.confirmButton` |
| `Text("取消")`（:103） | `common.cancelButton` |
| `Text(template.name, …)`（:122） | `Text(localizedPresetText(template.name, displayLanguage), …)` |
| `Text(template.ageRange, …)`（:123） | `Text(localizedPresetText(template.ageRange, displayLanguage), …)` |
| `Text(template.description, …)`（:128） | `Text(localizedPresetText(template.description, displayLanguage), …)` |
| `Text("📝 基础×${basic}")`（:138） | `Text(strings.basicCount(basic))` |
| `Text("📈 提升×${enhance}")`（:139） | `Text(strings.enhancementCount(enhance))` |
| `Text("🏆 习惯×${habit}")`（:140） | `Text(strings.habitCount(habit))` |
| `Text("一键应用此模板")`（:151） | `strings.applyButton` |

`applyTemplate(template)`（:97）落库调用**不动**。

验证：去注释 CJK 扫描 → 0 代码字面量。

- [ ] **Step 4: 确认绿**（ParentTaskCopyTest 8 条）

---

### Task 5: 等价闸门登记 + 跨表钉死 + 人工清单 + 四闸 + 唯一 W2 commit

**Files:**
- Modify: `scripts/i18n_template_diff.py`、`docs/i18n-stage1-verification.md`、`ParentTaskCopyTest.kt`（+1）

- [ ] **Step 1: 追加跨表钉死测试**

```kotlin
    @Test
    fun crossTableVocabularyPinned() {
        // 任务管理页复用的分类词与任务列表同源（中文值/英文值都相等）
        assertEquals("全部", AppStringsZh.taskList.filterAll)
        assertEquals("基础", AppStringsZh.taskList.filterBasic)
        assertEquals("提升", AppStringsZh.taskList.filterEnhancement)
        assertEquals("习惯", AppStringsZh.taskList.filterHabits)
        // 「任务管理」标题复用家长首页菜单（不另建同值字段）
        assertEquals("任务管理", AppStringsZh.parentHome.menuTaskManageTitle)
        assertEquals("Task management", AppStringsEn.parentHome.menuTaskManageTitle)
        // 「编辑」contentDescription 与 W1 详情表同值同译
        assertEquals(AppStringsZh.mistakeDetail.editContentDescription, AppStringsZh.parentTaskManage.editContentDescription)
        assertEquals(AppStringsEn.mistakeDetail.editContentDescription, AppStringsEn.parentTaskManage.editContentDescription)
        // 难度芯片英文 = W0 注册表难度译名 + 星数
        assertEquals(localizedPresetText("普通", DisplayLanguage.EN) + " ⭐", AppStringsEn.parentTaskEdit.difficultyNormal)
        assertEquals(localizedPresetText("进阶", DisplayLanguage.EN) + " ⭐⭐", AppStringsEn.parentTaskEdit.difficultyAdvanced)
        assertEquals(localizedPresetText("挑战", DisplayLanguage.EN) + " ⭐⭐⭐", AppStringsEn.parentTaskEdit.difficultyChallenge)
        // 模板分类计数英文词形与任务列表筛选词一致
        assertEquals("📝 " + AppStringsEn.taskList.filterBasic + " ×3", AppStringsEn.parentTaskTemplate.basicCount(3))
        assertEquals("📈 " + AppStringsEn.taskList.filterEnhancement + " ×2", AppStringsEn.parentTaskTemplate.enhancementCount(2))
        assertEquals("🏆 " + AppStringsEn.taskList.filterHabits + " ×1", AppStringsEn.parentTaskTemplate.habitCount(1))
    }
```

- [ ] **Step 2: 登记 BRANCH_PAGES / BRANCH_TABLES**

`scripts/i18n_template_diff.py`：在 `W1_REV` 常量后加 `W2_REV = "5deed68"`（W2 迁移前 = W1 收口提交）。
`BRANCH_PAGES` 末尾追加：

```python
    # ---- 阶段 2 W2（家长任务管理，W2_REV）----
    (W2_REV, "com/hstudy/app/ui/parent/ParentTaskManageScreen.kt"),
    (W2_REV, "com/hstudy/app/ui/parent/ParentTaskEditScreen.kt"),
    (W2_REV, "com/hstudy/app/ui/parent/ParentAssignTaskScreen.kt"),
    (W2_REV, "com/hstudy/app/ui/parent/ParentTaskTemplateScreen.kt"),
```

`BRANCH_TABLES` 元组推导追加 4 个表名：`"ParentTaskManageStrings", "ParentTaskEditStrings", "ParentAssignTaskStrings", "ParentTaskTemplateStrings"`。

**无新披露**：本波 4 页无落库原文字面量（`assignTask`/`applyTemplate`/`addTask`/`updateTask`/`deleteTask` 均用 state/对象实参，无中文字面量）；所有 UI 字面量都进了表或复用既有表。若 `--branch` 报 missing/invented，按 Ruling 4 修实现/表值，**不得加披露掩盖**。

- [ ] **Step 3: 人工清单追加**

`docs/i18n-stage1-verification.md` §2 末尾追加：

```markdown
### 阶段 2 W2（家长任务管理）追加项
- [ ] V-40 英文态任务管理页：标题/分类筛选/积分行/空态全英文；任务标题与描述为预设时显示英文（注册表），家长自建内容原样
- [ ] V-41 英文态任务编辑页：分类/频率/难度芯片与说明、必填扣分提示、保存/删除全英文；建议芯片标题预设英文、点击后写入仍为 canonical 中文（落库不变）
- [ ] V-42 英文态布置任务页：表单/建议芯片/已布置列表/完成状态全英文；已布置任务标题为家长输入，原样
- [ ] V-43 英文态积分模板页：模板名/年龄段/描述/预览任务标题显示英文（注册表），分类计数与确认对话框全英文；应用后落库任务仍为 canonical 中文
```

- [ ] **Step 4: 全量四闸（顺序，逐条 exit 0）**

```bash
PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py
PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py
PYTHONIOENCODING=utf-8 python scripts/i18n_template_diff.py --branch
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest
```

Expected: 边界 exit 0；自测 195 断言全 OK；`--branch` exit 0（[A] 0 missing/0 invented，W2 四表四页并入整枝组，无新披露）；assembleDebug OK；**155 tests / 0 failures**（146 + 9）。任何一闸红：修到绿，不许带红提交。

- [ ] **Step 5: 落唯一 W2 commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentTaskManageStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentTaskEditStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentAssignTaskStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentTaskTemplateStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentTaskManageScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentTaskEditScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentAssignTaskScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentTaskTemplateScreen.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ParentTaskCopyTest.kt \
        scripts/i18n_template_diff.py \
        docs/i18n-stage1-verification.md
git commit -m "feat(i18n): 阶段 2 W2——家长任务管理四页双语化（4 张表）、预设渲染接注册表、等价闸门登记"
git status
```

Expected: commit 成功，工作树干净。**到此为止，不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译、155 单测、字节等价闸门整枝组、存储边界、中文态逐字节不变、注册表渲染点 ZH 恒等（zh==key）。
- 不可自动证明：英文态四页视觉呈现（人工 V-40…V-43）；预设标题在英文态的实际显示（注册表命中由 W0 交叉校验保证，视觉待人工）。
- 不以"CI 绿"表述"双语已验证"。
