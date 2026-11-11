# i18n 阶段 2 W3（家长积分/审批/奖励）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把家长积分/审批/奖励 4 页（积分管理/积分审核/奖励编辑/奖励商店管理）迁入双语文案表：新增 4 张页面表，复用既有共享值（家长首页菜单标题与空态提示、通用按钮、任务列表积分标签），奖励预设内容（名称/描述/分类）在渲染点包 `localizedPresetText`；等价闸门登记本波（含 6 条奖励分类的页面单边披露）。

**Architecture:** 沿用阶段 1/W1/W2 机制（`AppStrings` 聚合 + `stringsFor` + `LocalStrings`/`LocalDisplayLanguage` + 函数型插值字段 + `Plurals.kt` 窄函数）。**复用优先**：`积分管理` 复用 `ParentHomeStrings.menuPointsTitle`；`确认/取消/删除` 复用 `CommonStrings`；`+N分` 复用 `TaskListStrings.pointsEarnedLabel`；`奖励商店还是空的`/`✅ 已载入预设奖励` 复用 `ParentHomeStrings.setupRewardsMissing`/`presetRewardsLoadedMessage`。奖励分类是 **canonical 落库值**（`RewardEntity.category: String`）：选项列表与默认值 `"其他"` 原地不动（Ruling 25），只有渲染表达式包 `localizedPresetText`（W0 注册表已收录全部 7 条分类 + 27 奖励名 + 27 奖励描述）。积分账本 `record.note` 与审批 `approval.description` 是**动态业务数据**（spec §1 第 17 行明列不本地化），原样渲染。

**Tech Stack:** Kotlin 2.1.20 / Compose Multiplatform 1.7.3（不升级）；commonTest 经 `:composeApp:testDebugUnitTest`；Python 闸门脚本。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§12 W3 行；§4 注册表渲染点；§5 Ruling 25；§1 第 17 行动态数据不本地化；§13 术语沿用阶段 1 §11：积分=Points、奖励=Reward、家长审核=Approval——本波无新 domain 术语，不改 spec）

## Global Constraints（逐字摘自 spec，每个任务隐含包含）

- 不改 schema、不写迁移、不动 `data/**` 的任何文件（含种子、账本描述、同步 DTO、偏好键）。（§1）
- 文案表/注册表的任何字符串**不得**到达：DB 写入、bizKey 构造、幂等/去重比较、同步 DTO 字段、偏好键值（Ruling 25）。奖励编辑页 `category` state（初始 `"其他"`、选项 canonical 列表赋值）最终写进 `RewardEntity.category`——**保持 canonical 原值**，只有 `Text`/`OutlinedTextField.value` 渲染包 `localizedPresetText`。（§5）
- `localizedPresetText` 及一切 `strings.*` 只允许出现在渲染表达式里。（§5）
- 不升级 Compose Multiplatform / Kotlin 版本；不动 `Plurals.kt` 机制（本波复用既有 `pointsWord`/`timesWord`，**不新增窄函数**）。（§1）
- W3 唯一改动形态：`"文本"` → `strings.xxx` / 复用字段 / `localizedPresetText(...)` 等价替换；**逻辑行零变化**。两处机械形变除外：① `statusLabel(status)` 加一个 `strings` 参数（非 composable 私有函数取表的唯一途径）及其唯一调用点；② 排行榜标题 `task?.title ?: "任务#${taskId}"` 改写为 `task?.let { localizedPresetText(it.title, displayLanguage) } ?: strings.taskFallbackTitle(taskId)`（渲染语义不变：预设译出、自建原样、缺失回退）。
- 动态业务数据不本地化：`record.note`（积分账本备注）、`approval.description`（审批描述，写入时语言冻结）原样渲染，**不包** `localizedPresetText`。（§1 第 17 行）
- 平台文件零改动（本波无通知/分享）。
- 每波本地四闸 + BRANCH_PAGES 登记必须绿。（§12）
- **按波提交，一波一个 commit**：Task 1–4 只验证不提交，Task 5 落唯一 W3 commit。（§12）
- 推送经用户逐次批准；本计划**不含推送步骤**。W2 的三个本地 commit（31903be/f59cb63/aae364e）尚未推送，本波继续在其上堆叠本地 commit。PAT 绝不写入仓库、文档或日志。
- 不以"CI 绿"表述"双语已验证"（§11）。

## 环境与命令（零上下文执行者必读）

- 工作目录：`D:\game\hstudy-1`（Git Bash）。Python 脚本带 `PYTHONIOENCODING=utf-8`。
- Gradle 内联：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew …`
- 基线：**155 tests 全绿**（`aae364e`，W2 收口）。W3 新增 9 条（ParentPointsCopyTest），终态 **164 tests / 0 failures**。
- 迁移前 rev：**`aae364e`**（W2 收口提交，= 当前 HEAD）。
- 零 CJK 验收口径：**去注释后的代码字面量**（中文注释属 Ruling 18，不删不译）。积分/审批/奖励管理 3 页应归零；**奖励编辑页保留 8 处 canonical 字面量**（`categoryOptions` 7 条 + 默认值 `"其他"` 1 处，Ruling 25 落库值，不进表）。

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `i18n/ParentPointsStrings.kt` | 新增 | 积分管理页 16 字段（13 静态 + 3 函数；标题复用 parentHome、确认/取消复用 common） |
| `i18n/ParentApprovalStrings.kt` | 新增 | 积分审核页 18 字段（15 静态 + 3 函数；+N分复用 taskList） |
| `i18n/ParentRewardEditStrings.kt` | 新增 | 奖励编辑页 11 静态字段（分类 canonical 不入表） |
| `i18n/ParentRewardManageStrings.kt` | 新增 | 奖励商店管理页 7 字段（6 静态 + 1 函数；空态/载入提示复用 parentHome、删除复用 common） |
| `i18n/Strings.kt` | 修改 | `AppStrings` 加 4 字段 + Zh/En 各 4 行（20 → 24 张表） |
| `ui/parent/ParentPointsScreen.kt` | 修改 | 19 处字面量 → 表/复用；排行榜任务标题渲染包注册表 |
| `ui/parent/ParentApprovalScreen.kt` | 修改 | 19 处字面量 → 表/复用；statusLabel 加 strings 参数 |
| `ui/parent/ParentRewardEditScreen.kt` | 修改 | 11 处字面量 → 表；分类下拉渲染包注册表（canonical 列表/默认值不动） |
| `ui/parent/ParentRewardManageScreen.kt` | 修改 | 10 处字面量 → 表/复用；奖励名/描述/分类渲染包注册表 |
| `commonTest/i18n/ParentPointsCopyTest.kt` | 新增 | 9 条：4 表漏译/函数钉死 + 分类注册表覆盖 + 跨表词汇钉死 |
| `commonTest/i18n/StringsCopyTest.kt` | 修改 | `EN_STATIC_POINTS_MENTIONS` 追加 14 条（闭集守卫，只加不改） |
| `scripts/i18n_template_diff.py` | 修改 | `W3_REV` + 4 BRANCH_PAGES + 4 BRANCH_TABLES + 6 条分类披露 + 文档读数 |
| `docs/i18n-stage1-verification.md` | 修改 | §1 命令读数/表数、§2 追加 V-44…V-47、§6 整枝组读数 |

（i18n 路径前缀 `composeApp/src/commonMain/kotlin/com/hstudy/app/`；commonTest 前缀 `composeApp/src/commonTest/kotlin/com/hstudy/app/`。）

**复用与新建决定（Ruling，为什么这样选）：**

1. **「编辑」「保存」新建字段 + 跨表钉死，不上收 CommonStrings。** W2 已确立先例：`编辑` 在 `MistakeDetailStrings`（W1）与 `ParentTaskManageStrings`（W2）各有一份，由 `crossTableVocabularyPinned` 钉住同值同译；`保存` 在 `ParentTaskEditStrings`（W2）。上收 CommonStrings 需要同时改 W1/W2 的页面、表与穷举测试，扩大本波审查面；本波沿用先例（`ParentRewardManageStrings.editContentDescription`、`ParentRewardEditStrings.saveButton`），跨表钉死防漂移。三处「编辑」的统一上收是 W9 sweep 候选，不在本波。
2. **奖励分类 canonical 列表留在页面，渲染走注册表，6 条页面单边披露。** `categoryOptions`（娱乐/美食/出行/特权/实物/社交/其他）与默认值 `"其他"` 是 `RewardEntity.category` 的落库值（Ruling 25），不进文案表；W0 注册表已收录全部 7 条（`PresetRegistry.kt:201-208`），渲染包 `localizedPresetText`。等价闸门在迁移前页面里看得见这 7 条字面量而表侧没有：`其他` 与 `MistakeStrings.subjectOther`（"其他"）两侧同形自然配平，**无需披露**；其余 6 条登记页面单边披露（Task 5）。不新建分类文案表——那会给同一 canonical 值造出第二份英文出处，与注册表漂移。
3. **动态账本/审批数据原样。** `record.note`、`approval.description` 不包注册表也不进表（spec §1 第 17 行：写入时语言冻结的业务数据）。
4. **W3 页面自己的渲染点本波接线**（奖励名/描述/分类、排行榜任务标题），与 W2 接 `task.title`/模板名同理；阶段 1 已迁页面的同类 sweep 与枚举→注册表映射仍属 W9。

---

### Task 1: ParentPointsStrings + 积分管理页

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentPointsStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentPointsScreen.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`（闭集 +6）
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ParentPointsCopyTest.kt`（新建）

**Interfaces:**
- Consumes: `ParentHomeStrings.menuPointsTitle`、`CommonStrings.confirmButton/cancelButton`、`localizedPresetText`、`timesWord`、`assertDiffers`（commonTest 共享 helper，`AssertDiffers.kt`）。
- Produces: `data class ParentPointsStrings`（16 字段，见下）、`AppStrings.parentPoints`。

- [ ] **Step 1: 写测试（编译红）**

创建 `ParentPointsCopyTest.kt`：

```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W3 家长积分/审批/奖励文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentTaskCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b）；跨表同值钉死。
 * 覆盖四张表：parentPoints / parentApproval / parentRewardEdit / parentRewardManage。
 */
class ParentPointsCopyTest {

    @Test
    fun parentPointsCopyDiffers() {
        val zh = AppStringsZh.parentPoints
        val en = AppStringsEn.parentPoints
        assertDiffers(
            zh.adjustContentDescription to en.adjustContentDescription,
            zh.dailyStatsTitle to en.dailyStatsTitle,
            zh.taskRankingTitle to en.taskRankingTitle,
            zh.allRecordsTitle to en.allRecordsTitle,
            zh.emptyRecords to en.emptyRecords,
            zh.adjustDialogTitle to en.adjustDialogTitle,
            zh.adjustPointsLabel to en.adjustPointsLabel,
            zh.adjustNoteLabel to en.adjustNoteLabel,
            zh.typeTaskComplete to en.typeTaskComplete,
            zh.typeBonus to en.typeBonus,
            zh.typeAdjustment to en.typeAdjustment,
            zh.typeRewardExchange to en.typeRewardExchange,
            zh.typeReversal to en.typeReversal,
        )
    }

    @Test
    fun parentPointsInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentPoints
        val en = AppStringsEn.parentPoints
        assertEquals("净12", zh.netValue(12))
        assertEquals("Net 12", en.netValue(12))
        assertEquals("净-3", zh.netValue(-3))
        assertEquals("Net -3", en.netValue(-3))
        assertEquals("任务#42", zh.taskFallbackTitle(42L))
        assertEquals("Task #42", en.taskFallbackTitle(42L))
        assertEquals("1次", zh.timesCount(1))
        assertEquals("1 time", en.timesCount(1))
        assertEquals("3次", zh.timesCount(3))
        assertEquals("3 times", en.timesCount(3))
    }
}
```

- [ ] **Step 2: 确认红**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.ParentPointsCopyTest"`
Expected: `Unresolved reference 'parentPoints'`。

- [ ] **Step 3: 实现**

3a. 创建 `ParentPointsStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 家长积分管理页（ui/parent/ParentPointsScreen.kt）文案表（阶段 2 W3）。
 *
 * 复用而非新建（同一中文值一个英文出处）：
 * - 标题「积分管理」= [ParentHomeStrings.menuPointsTitle]（家长首页菜单已译 "Points management"）；
 * - 调整对话框「确认/取消」= [CommonStrings.confirmButton/cancelButton]。
 * 排行榜任务标题是 canonical/用户数据：`task?.let { localizedPresetText(it.title, …) }`，
 * 缺失回退 [taskFallbackTitle]（"任务#N" 是 UI 文案，入表）。
 * [timesCount] 英文 one/other 用 [timesWord]；[netValue] 是纯数值标签（Net -3），无复数。
 * 记录卡 `record.note` 是积分账本备注（spec §1：写入时语言冻结的动态数据），原样渲染不入表。
 * 类型标签词汇与既有表对齐：撤销 = check-in undo/reversal（[TaskListStrings.undoClawedBackMessage]），
 * 兑换 = redeem/redemption（[RoleStrings.childDesc] EN "Redeem rewards"）。
 */
data class ParentPointsStrings(
    val adjustContentDescription: String,
    val dailyStatsTitle: String,
    val taskRankingTitle: String,
    val allRecordsTitle: String,
    val emptyRecords: String,
    val adjustDialogTitle: String,
    val adjustPointsLabel: String,
    val adjustNoteLabel: String,
    val typeTaskComplete: String,
    val typeBonus: String,
    val typeAdjustment: String,
    val typeRewardExchange: String,
    val typeReversal: String,
    val netValue: (net: Int) -> String,
    val taskFallbackTitle: (taskId: Long) -> String,
    val timesCount: (count: Int) -> String,
)

val ParentPointsStringsZh = ParentPointsStrings(
    adjustContentDescription = "调整积分",                                     // :56
    dailyStatsTitle = "📅 每日得分统计（近7天）",                                // :69
    taskRankingTitle = "📋 任务积分排行（近7天）",                               // :111
    allRecordsTitle = "全部积分记录",                                          // :125
    emptyRecords = "暂无积分记录",                                             // :132
    adjustDialogTitle = "手动调整积分",                                        // :146
    adjustPointsLabel = "积分数（正=加分，负=扣分）",                            // :152
    adjustNoteLabel = "备注说明",                                              // :159
    typeTaskComplete = "✅ 任务完成",                                          // :185
    typeBonus = "🎁 学习奖励",                                                 // :186
    typeAdjustment = "✏️ 手动调整",                                            // :187
    typeRewardExchange = "🛒 兑换消耗",                                        // :188
    typeReversal = "↩️ 撤销扣回",                                              // :189
    netValue = { net -> "净$net" },                                           // :88
    taskFallbackTitle = { taskId -> "任务#$taskId" },                          // :100
    timesCount = { count -> "${count}次" },                                   // :116
)

val ParentPointsStringsEn = ParentPointsStrings(
    adjustContentDescription = "Adjust points",
    dailyStatsTitle = "📅 Daily scores (last 7 days)",
    taskRankingTitle = "📋 Task points ranking (last 7 days)",
    allRecordsTitle = "All point records",
    emptyRecords = "No point records yet",
    adjustDialogTitle = "Adjust points manually",
    adjustPointsLabel = "Points (positive = add, negative = deduct)",
    adjustNoteLabel = "Note",
    typeTaskComplete = "✅ Task completed",
    typeBonus = "🎁 Study bonus",
    typeAdjustment = "✏️ Manual adjustment",
    typeRewardExchange = "🛒 Reward redemption",
    typeReversal = "↩️ Check-in reversal",
    netValue = { net -> "Net $net" },
    taskFallbackTitle = { taskId -> "Task #$taskId" },
    timesCount = { count -> "$count ${timesWord(count)}" },
)
```

3b. `Strings.kt`：`AppStrings` 加 `val parentPoints: ParentPointsStrings,`（放在 `parentTaskTemplate` 之后），`AppStringsZh`/`AppStringsEn` 各加 `parentPoints = ParentPointsStringsZh,` / `parentPoints = ParentPointsStringsEn,`。

3c. 迁移 `ParentPointsScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。主 composable 开头（`val app = AppGraph` 附近）加：

```kotlin
    val strings = LocalStrings.current.parentPoints
    val common = LocalStrings.current.common
    val parentHome = LocalStrings.current.parentHome
    val displayLanguage = LocalDisplayLanguage.current
```

替换表（行号为迁移前 `aae364e` 版本）：

| 原名面量 | 替换 |
|---|---|
| `title = "积分管理"`（:49） | `title = parentHome.menuPointsTitle` |
| `contentDescription = "调整积分"`（:56） | `contentDescription = strings.adjustContentDescription` |
| `Text("📅 每日得分统计（近7天）", …)`（:69） | `Text(strings.dailyStatsTitle, …)` |
| `Text("净${earned - spent}", …)`（:88） | `Text(strings.netValue(earned - spent), …)` |
| `val title = task?.title ?: "任务#${taskId}"`（:100） | `val title = task?.let { localizedPresetText(it.title, displayLanguage) } ?: strings.taskFallbackTitle(taskId)` |
| `Text("📋 任务积分排行（近7天）", …)`（:111） | `Text(strings.taskRankingTitle, …)` |
| `Text("${count}次", …)`（:116） | `Text(strings.timesCount(count), …)` |
| `Text("全部积分记录", …)`（:125） | `Text(strings.allRecordsTitle, …)` |
| `Text("暂无积分记录", …)`（:132） | `Text(strings.emptyRecords, …)` |
| `title = { Text("手动调整积分") }`（:146） | `title = { Text(strings.adjustDialogTitle) }` |
| `label = { Text("积分数（正=加分，负=扣分）") }`（:152） | `label = { Text(strings.adjustPointsLabel) }` |
| `label = { Text("备注说明") }`（:159） | `label = { Text(strings.adjustNoteLabel) }` |
| `Text("确认")`（:173） | `Text(common.confirmButton)` |
| `Text("取消")`（:176） | `Text(common.cancelButton)` |

`PointRecordCard`（private @Composable，:183）内部开头加 `val strings = LocalStrings.current.parentPoints`，`when` 五支替换：

| 原名面量 | 替换 |
|---|---|
| `PointRecordType.TASK_COMPLETE -> "✅ 任务完成"`（:185） | `-> strings.typeTaskComplete` |
| `PointRecordType.BONUS -> "🎁 学习奖励"`（:186） | `-> strings.typeBonus` |
| `PointRecordType.ADJUSTMENT -> "✏️ 手动调整"`（:187） | `-> strings.typeAdjustment` |
| `PointRecordType.REWARD_EXCHANGE -> "🛒 兑换消耗"`（:188） | `-> strings.typeRewardExchange` |
| `PointRecordType.REVERSAL -> "↩️ 撤销扣回"`（:189） | `-> strings.typeReversal` |

**不动**：`record.note`（:202 原样）、`adjustPoints(pts, adjustNote)`（:168 落库调用）、日期格式串、`"+${earned}"`/`"-${spent}"`/`"+${pts}"`/record.points 纯数值插值（无汉字，不入闸门集合）。

3d. `StringsCopyTest.kt` 的 `EN_STATIC_POINTS_MENTIONS` 末尾（`parentTaskTemplate.title` 之后）追加：

```kotlin
    // ---- W3 Task 1 积分管理页新增 6 条（英文原文逐条留在行尾注释里）。
    "parentPoints.adjustContentDescription",  // "Adjust points"
    "parentPoints.taskRankingTitle",          // "📋 Task points ranking (last 7 days)"
    "parentPoints.allRecordsTitle",           // "All point records"
    "parentPoints.emptyRecords",              // "No point records yet"
    "parentPoints.adjustDialogTitle",         // "Adjust points manually"
    "parentPoints.adjustPointsLabel",         // "Points (positive = add, negative = deduct)"
```

验证：去注释 CJK 扫描 → 本页代码字面量 0 条。

- [ ] **Step 4: 确认绿**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.ParentPointsCopyTest" --tests "com.hstudy.app.i18n.StringsCopyTest"`
Expected: ParentPointsCopyTest 2 条 PASS；StringsCopyTest 63 条 PASS（闭集双向相等）。

---

### Task 2: ParentApprovalStrings + 积分审核页

**Files:**
- Create: `i18n/ParentApprovalStrings.kt`；Modify: `Strings.kt`、`ui/parent/ParentApprovalScreen.kt`、`StringsCopyTest.kt`（闭集 +5）、`ParentPointsCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `TaskListStrings.pointsEarnedLabel`、`pointsWord`。
- Produces: `data class ParentApprovalStrings`（18 字段）、`AppStrings.parentApproval`。

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun parentApprovalCopyDiffers() {
        val zh = AppStringsZh.parentApproval
        val en = AppStringsEn.parentApproval
        assertDiffers(
            zh.title to en.title,
            zh.approveAllButton to en.approveAllButton,
            zh.historyTab to en.historyTab,
            zh.emptyPendingTitle to en.emptyPendingTitle,
            zh.emptyPendingNote to en.emptyPendingNote,
            zh.alreadyProcessedMessage to en.alreadyProcessedMessage,
            zh.rejectedMessage to en.rejectedMessage,
            zh.emptyHistory to en.emptyHistory,
            zh.approveButton to en.approveButton,
            zh.rejectButton to en.rejectButton,
            zh.statusPending to en.statusPending,
            zh.statusApproved to en.statusApproved,
            zh.statusRejected to en.statusRejected,
            zh.statusCancelled to en.statusCancelled,
            zh.statusRevoked to en.statusRevoked,
        )
    }

    @Test
    fun parentApprovalInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.parentApproval
        val en = AppStringsEn.parentApproval
        assertEquals("✅ 已全部通过，共发放 12 分", zh.approveAllMessage(12))
        assertEquals("✅ All approved — 12 points awarded", en.approveAllMessage(12))
        assertEquals("✅ 已全部通过，共发放 1 分", zh.approveAllMessage(1))
        assertEquals("✅ All approved — 1 point awarded", en.approveAllMessage(1))
        assertEquals("待审核", zh.pendingTab(0))
        assertEquals("Pending", en.pendingTab(0))
        assertEquals("待审核 1", zh.pendingTab(1))
        assertEquals("Pending (1)", en.pendingTab(1))
        assertEquals("待审核 5", zh.pendingTab(5))
        assertEquals("Pending (5)", en.pendingTab(5))
        assertEquals("✅ 已通过：+8分", zh.approvedMessage(8))
        assertEquals("✅ Approved: +8 points", en.approvedMessage(8))
        assertEquals("✅ 已通过：+1分", zh.approvedMessage(1))
        assertEquals("✅ Approved: +1 point", en.approvedMessage(1))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'parentApproval'`）

- [ ] **Step 3: 实现**

3a. 创建 `ParentApprovalStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 家长积分审核页（ui/parent/ParentApprovalScreen.kt）文案表（阶段 2 W3）。
 *
 * 复用：卡片「+N分」= [TaskListStrings.pointsEarnedLabel]（同一中文值、同一语义 = 某件事值多少积分，
 * 与 Task 9 ChildHomeScreen 的复用先例一致；等价闸门里该值已被 Task 9 双边披露覆盖）。
 * 标题带 📋 前缀，与家长首页菜单 [ParentHomeStrings.menuApprovalTitle]（"积分审核"，无 emoji）
 * 是**两个值**，故本表自建 [title]（与 W2 ParentTaskTemplateStrings.title 同理）。
 * [pendingTab] 的 ZH 侧保留原文的条件计数结构（`待审核` + 可选 ` N`），塌缩后与页面原文同形；
 * EN 用 "Pending (N)"。[approveAllMessage]/[approvedMessage] 英文 one/other 用 [pointsWord]。
 * `approval.description` 是审批描述（写入时语言冻结的动态业务数据，spec §1），原样渲染不入表。
 * 状态词汇与既有表对齐：撤销扣回 = points removed（[TaskListStrings.undoClawedBackMessage]）。
 */
data class ParentApprovalStrings(
    val title: String,
    val approveAllButton: String,
    val historyTab: String,
    val emptyPendingTitle: String,
    val emptyPendingNote: String,
    val alreadyProcessedMessage: String,
    val rejectedMessage: String,
    val emptyHistory: String,
    val approveButton: String,
    val rejectButton: String,
    val statusPending: String,
    val statusApproved: String,
    val statusRejected: String,
    val statusCancelled: String,
    val statusRevoked: String,
    val approveAllMessage: (points: Int) -> String,
    val pendingTab: (count: Int) -> String,
    val approvedMessage: (points: Int) -> String,
)

val ParentApprovalStringsZh = ParentApprovalStrings(
    title = "📋 积分审核",                                                     // :46
    approveAllButton = "全部通过",                                             // :56
    historyTab = "历史记录",                                                   // :69
    emptyPendingTitle = "暂无待审核的积分申请",                                  // :78
    emptyPendingNote = "孩子完成学习行为后会在这里提交积分申请",                   // :79
    alreadyProcessedMessage = "该申请已处理，未重复加分",                         // :98
    rejectedMessage = "已拒绝该申请",                                           // :105
    emptyHistory = "暂无审核记录",                                              // :116
    approveButton = "通过",                                                    // :196
    rejectButton = "拒绝",                                                     // :204
    statusPending = "待审核",                                                  // :213
    statusApproved = "已通过",                                                 // :214
    statusRejected = "已拒绝",                                                 // :215
    statusCancelled = "已取消打卡（未计分）",                                    // :216
    statusRevoked = "已撤销（积分已扣回）",                                      // :217
    approveAllMessage = { points -> "✅ 已全部通过，共发放 $points 分" },         // :54
    pendingTab = { count -> "待审核${if (count > 0) " $count" else ""}" },      // :67
    approvedMessage = { points -> "✅ 已通过：+${points}分" },                   // :97
)

val ParentApprovalStringsEn = ParentApprovalStrings(
    title = "📋 Point approvals",
    approveAllButton = "Approve all",
    historyTab = "History",
    emptyPendingTitle = "No point requests to review",
    emptyPendingNote = "Point requests appear here after your child completes study activities",
    alreadyProcessedMessage = "Already handled — points were not added again",
    rejectedMessage = "Request rejected",
    emptyHistory = "No review history yet",
    approveButton = "Approve",
    rejectButton = "Reject",
    statusPending = "Pending",
    statusApproved = "Approved",
    statusRejected = "Rejected",
    statusCancelled = "Check-in cancelled (not scored)",
    statusRevoked = "Revoked (points removed)",
    approveAllMessage = { points -> "✅ All approved — $points ${pointsWord(points)} awarded" },
    pendingTab = { count -> if (count > 0) "Pending ($count)" else "Pending" },
    approvedMessage = { points -> "✅ Approved: +$points ${pointsWord(points)}" },
)
```

3b. `Strings.kt` 加 `val parentApproval: ParentApprovalStrings,` + 两行。

3c. 迁移 `ParentApprovalScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalStrings`（**不需要** LocalDisplayLanguage/localizedPresetText——本页无注册表渲染点）。主 composable 开头加：

```kotlin
    val strings = LocalStrings.current.parentApproval
    val taskList = LocalStrings.current.taskList
```

| 原名面量 | 替换 |
|---|---|
| `title = "📋 积分审核"`（:46） | `title = strings.title` |
| `showSnackbar("✅ 已全部通过，共发放 $awarded 分")`（:54） | `showSnackbar(strings.approveAllMessage(awarded))` |
| `Text("全部通过")`（:56） | `Text(strings.approveAllButton)` |
| `Text("待审核${if (pendingApprovals.isNotEmpty()) " ${pendingApprovals.size}" else ""}")`（:67） | `Text(strings.pendingTab(pendingApprovals.size))` |
| `Text("历史记录")`（:69） | `Text(strings.historyTab)` |
| `Text("暂无待审核的积分申请", …)`（:78） | `Text(strings.emptyPendingTitle, …)` |
| `Text("孩子完成学习行为后会在这里提交积分申请", …)`（:79） | `Text(strings.emptyPendingNote, …)` |
| `if (awarded > 0) "✅ 已通过：+${awarded}分" else "该申请已处理，未重复加分"`（:97-98） | `if (awarded > 0) strings.approvedMessage(awarded) else strings.alreadyProcessedMessage` |
| `showSnackbar("已拒绝该申请")`（:105） | `showSnackbar(strings.rejectedMessage)` |
| `Text("暂无审核记录", …)`（:116） | `Text(strings.emptyHistory, …)` |
| `statusLabel(approval.status)`（:138） | `statusLabel(approval.status, strings)` |
| `Text("通过")`（:196） | `Text(strings.approveButton)` |
| `Text("拒绝")`（:204） | `Text(strings.rejectButton)` |

`ApprovalCard`（private @Composable，:161）内部开头加：

```kotlin
    val strings = LocalStrings.current.parentApproval
    val taskList = LocalStrings.current.taskList
```

| 原名面量 | 替换 |
|---|---|
| `Text("+${approval.requestedPoints}分", …)`（:186） | `Text(taskList.pointsEarnedLabel(approval.requestedPoints), …)` |

`statusLabel`（:212）改签名并换值（KDoc 保留）：

```kotlin
/** 审核状态文案（含撤销/作废，保证生命周期展示一致） */
private fun statusLabel(status: ApprovalStatus, strings: ParentApprovalStrings): String = when (status) {
    ApprovalStatus.PENDING -> strings.statusPending
    ApprovalStatus.APPROVED -> strings.statusApproved
    ApprovalStatus.REJECTED -> strings.statusRejected
    ApprovalStatus.CANCELLED -> strings.statusCancelled
    ApprovalStatus.REVOKED -> strings.statusRevoked
}
```

**不动**：`approval.description`（:136/:182 原样）、`approveAllAndAward()`/`approveAndAward(id)`/`rejectApproval(id)` 落库调用、`"+${approval.requestedPoints}"`（:144 纯数值）、`formatTime`、typeEmoji 的 `when`（ASCII 键 + emoji，无汉字）。

3d. `EN_STATIC_POINTS_MENTIONS` 追加：

```kotlin
    // ---- W3 Task 2 积分审核页新增 5 条（英文原文逐条留在行尾注释里）。
    "parentApproval.title",                   // "📋 Point approvals"
    "parentApproval.emptyPendingTitle",       // "No point requests to review"
    "parentApproval.emptyPendingNote",        // "Point requests appear here after your child completes study activities"
    "parentApproval.alreadyProcessedMessage", // "Already handled — points were not added again"
    "parentApproval.statusRevoked",           // "Revoked (points removed)"
```

验证：去注释 CJK 扫描 → 本页代码字面量 0 条。

- [ ] **Step 4: 确认绿**（ParentPointsCopyTest 4 条 + StringsCopyTest 63 条）

---

### Task 3: ParentRewardEditStrings + 奖励编辑页（分类渲染接注册表）

**Files:**
- Create: `i18n/ParentRewardEditStrings.kt`；Modify: `Strings.kt`、`ui/parent/ParentRewardEditScreen.kt`、`StringsCopyTest.kt`（闭集 +2）、`ParentPointsCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `localizedPresetText`（分类 7 条已在 W0 注册表）、`ParentTaskEditStrings.saveButton`（跨表钉死用）。
- Produces: `data class ParentRewardEditStrings`（11 静态字段）、`AppStrings.parentRewardEdit`。

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun parentRewardEditCopyDiffers() {
        val zh = AppStringsZh.parentRewardEdit
        val en = AppStringsEn.parentRewardEdit
        assertDiffers(
            zh.newTitle to en.newTitle,
            zh.editTitle to en.editTitle,
            zh.saveButton to en.saveButton,
            zh.presetHint to en.presetHint,
            zh.nameLabel to en.nameLabel,
            zh.descriptionLabel to en.descriptionLabel,
            zh.costLabel to en.costLabel,
            zh.categoryHeading to en.categoryHeading,
            zh.categoryPickerLabel to en.categoryPickerLabel,
            zh.iconHeading to en.iconHeading,
            zh.deleteButton to en.deleteButton,
        )
    }

    @Test
    fun rewardCategoryOptionsAreAllRegistered() {
        // 奖励编辑页的 categoryOptions 是 canonical 落库值（Ruling 25），渲染走注册表。
        // 7 条必须全部命中：ZH 恒等（zh==key），EN 必须译出（≠原值）。
        val categories = listOf("娱乐", "美食", "出行", "特权", "实物", "社交", "其他")
        categories.forEach { cat ->
            assertEquals(cat, localizedPresetText(cat, DisplayLanguage.ZH))
            assertEquals(false, localizedPresetText(cat, DisplayLanguage.EN) == cat)
        }
        // 默认值"其他"与错题类型共享 key，译名唯一
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'parentRewardEdit'`）

- [ ] **Step 3: 实现**

3a. 创建 `ParentRewardEditStrings.kt`：

```kotlin
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
```

3b. `Strings.kt` 加 `val parentRewardEdit: ParentRewardEditStrings,` + 两行。

3c. 迁移 `ParentRewardEditScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。主 composable 开头加：

```kotlin
    val strings = LocalStrings.current.parentRewardEdit
    val displayLanguage = LocalDisplayLanguage.current
```

| 原名面量 | 替换 |
|---|---|
| `title = if (rewardId == 0L) "新建奖励" else "编辑奖励"`（:53） | `title = if (rewardId == 0L) strings.newTitle else strings.editTitle` |
| `Text("保存", …)`（:72） | `Text(strings.saveButton, …)` |
| `Text("💡 常见的奖励（看电影、零食、玩耍等）已预设在奖励列表中，可直接返回修改积分。此处用于添加全新的自定义奖励。", …)`（:91） | `Text(strings.presetHint, …)` |
| `label = { Text("奖励名称") }`（:97） | `label = { Text(strings.nameLabel) }` |
| `label = { Text("奖励描述") }`（:100） | `label = { Text(strings.descriptionLabel) }` |
| `label = { Text("所需积分") }`（:103） | `label = { Text(strings.costLabel) }` |
| `Text("奖励分类", …)`（:107） | `Text(strings.categoryHeading, …)` |
| `value = category`（:114， readOnly 下拉框） | `value = localizedPresetText(category, displayLanguage)` |
| `label = { Text("选择分类") }`（:117） | `label = { Text(strings.categoryPickerLabel) }` |
| `text = { Text(opt) }`（:127，DropdownMenuItem） | `text = { Text(localizedPresetText(opt, displayLanguage)) }` |
| `Text("选择图标", …)`（:138） | `Text(strings.iconHeading, …)` |
| `Text("删除此奖励")`（:157） | `Text(strings.deleteButton)` |

**不动（Ruling 25 落库路径）**：`var category by remember { mutableStateOf("其他") }`（:31）、`val categoryOptions = listOf("娱乐", "美食", "出行", "特权", "实物", "社交", "其他")`（:48）、`onClick = { category = opt; … }`（:129）、`RewardEntity(… category = category)`（:65）、`addReward`/`updateReward`/`deleteReward` 调用、`emojiOptions`（纯 emoji）。

3d. `EN_STATIC_POINTS_MENTIONS` 追加：

```kotlin
    // ---- W3 Task 3 奖励编辑页新增 2 条（英文原文逐条留在行尾注释里）。
    "parentRewardEdit.presetHint",            // "… adjust their point costs there …"
    "parentRewardEdit.costLabel",             // "Points required"
```

验证：去注释 CJK 扫描 → 本页仅剩 8 处 canonical 字面量（categoryOptions 7 条 + 默认值"其他"）。

- [ ] **Step 4: 确认绿**（ParentPointsCopyTest 6 条 + StringsCopyTest 63 条）

---

### Task 4: ParentRewardManageStrings + 奖励商店管理页（注册表渲染接线）

**Files:**
- Create: `i18n/ParentRewardManageStrings.kt`；Modify: `Strings.kt`、`ui/parent/ParentRewardManageScreen.kt`、`StringsCopyTest.kt`（闭集 +1）、`ParentPointsCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `ParentHomeStrings.setupRewardsMissing/presetRewardsLoadedMessage`、`CommonStrings.deleteContentDescription`、`localizedPresetText`、`pointsWord`。
- Produces: `data class ParentRewardManageStrings`（7 字段）、`AppStrings.parentRewardManage`。

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun parentRewardManageCopyDiffers() {
        val zh = AppStringsZh.parentRewardManage
        val en = AppStringsEn.parentRewardManage
        assertDiffers(
            zh.title to en.title,
            zh.addContentDescription to en.addContentDescription,
            zh.emptyHint to en.emptyHint,
            zh.loadPresetsButton to en.loadPresetsButton,
            zh.addOwnHint to en.addOwnHint,
            zh.editContentDescription to en.editContentDescription,
        )
    }

    @Test
    fun parentRewardManageCostValuePinsBothLanguages() {
        val zh = AppStringsZh.parentRewardManage
        val en = AppStringsEn.parentRewardManage
        assertEquals("300 积分", zh.costValue(300))
        assertEquals("300 points", en.costValue(300))
        assertEquals("1 积分", zh.costValue(1))
        assertEquals("1 point", en.costValue(1))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'parentRewardManage'`）

- [ ] **Step 3: 实现**

3a. 创建 `ParentRewardManageStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 家长奖励商店管理页（ui/parent/ParentRewardManageScreen.kt）文案表（阶段 2 W3）。
 *
 * 复用而非新建：空态标题「奖励商店还是空的」= [ParentHomeStrings.setupRewardsMissing]、
 * 载入提示「✅ 已载入预设奖励」= [ParentHomeStrings.presetRewardsLoadedMessage]
 * （家长首页快速配置助手已译，逐字节同值同语义）；「删除」= [CommonStrings.deleteContentDescription]。
 * 「编辑」无共享字段，本表新建 [editContentDescription]（与 W1 MistakeDetailStrings、
 * W2 ParentTaskManageStrings 同值同译，跨表测试钉住；上收 CommonStrings 是 W9 sweep 候选）。
 * 标题「奖励商店管理」与菜单 [ParentHomeStrings.menuRewardStoreTitle]（"奖励商店"）差一个「管理」，
 * 是**两个值**，本表自建 [title]。
 * 奖励名/描述/分类是 canonical 种子（W0 注册表已收录 27 名 + 27 描述 + 7 分类），
 * 渲染点包 [localizedPresetText]；家长自建内容未命中则原样。`initDefaultRewards()` 落库调用不动。
 * [costValue] 英文 one/other 用 [pointsWord]。
 */
data class ParentRewardManageStrings(
    val title: String,
    val addContentDescription: String,
    val emptyHint: String,
    val loadPresetsButton: String,
    val addOwnHint: String,
    val editContentDescription: String,
    val costValue: (cost: Int) -> String,
)

val ParentRewardManageStringsZh = ParentRewardManageStrings(
    title = "奖励商店管理",                                                    // :34
    addContentDescription = "添加奖励",                                        // :38
    emptyHint = "点击下方按钮一键载入 27 个常见奖励（看电影、零食、玩耍、满足心愿等），之后再逐个调整积分即可。", // :65
    loadPresetsButton = "✨ 一键载入预设奖励",                                   // :73
    addOwnHint = "或点击右下角 + 自行添加",                                      // :74
    editContentDescription = "编辑",                                           // :115
    costValue = { cost -> "$cost 积分" },                                     // :113
)

val ParentRewardManageStringsEn = ParentRewardManageStrings(
    title = "Reward store management",
    addContentDescription = "Add reward",
    emptyHint = "Tap the button below to load 27 common rewards (movies, snacks, playtime, granting wishes, and more) in one go, then adjust their point costs one by one.",
    loadPresetsButton = "✨ Load preset rewards",
    addOwnHint = "Or tap + at the bottom right to add your own",
    editContentDescription = "Edit",
    costValue = { cost -> "$cost ${pointsWord(cost)}" },
)
```

3b. `Strings.kt` 加 `val parentRewardManage: ParentRewardManageStrings,` + 两行。

3c. 迁移 `ParentRewardManageScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。主 composable 开头加：

```kotlin
    val strings = LocalStrings.current.parentRewardManage
    val common = LocalStrings.current.common
    val parentHome = LocalStrings.current.parentHome
    val displayLanguage = LocalDisplayLanguage.current
```

| 原名面量 | 替换 |
|---|---|
| `title = "奖励商店管理"`（:34） | `title = strings.title` |
| `contentDescription = "添加奖励"`（:38） | `contentDescription = strings.addContentDescription` |
| `Text("奖励商店还是空的", …)`（:64） | `Text(parentHome.setupRewardsMissing, …)` |
| `Text("点击下方按钮一键载入 27 个常见奖励（看电影、零食、玩耍、满足心愿等），之后再逐个调整积分即可。", …)`（:65） | `Text(strings.emptyHint, …)` |
| `showSnackbar("✅ 已载入预设奖励")`（:71） | `showSnackbar(parentHome.presetRewardsLoadedMessage)` |
| `Text("✨ 一键载入预设奖励")`（:73） | `Text(strings.loadPresetsButton)` |
| `Text("或点击右下角 + 自行添加", …)`（:74） | `Text(strings.addOwnHint, …)` |

`RewardManageCard`（private @Composable，:88）内部开头加：

```kotlin
    val strings = LocalStrings.current.parentRewardManage
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
```

| 原名面量 | 替换 |
|---|---|
| `Text(reward.name, …)`（:102） | `Text(localizedPresetText(reward.name, displayLanguage), …)` |
| `Text(reward.category, …)`（:106，AssistChip label） | `Text(localizedPresetText(reward.category, displayLanguage), …)` |
| `Text(reward.description, …)`（:111） | `Text(localizedPresetText(reward.description, displayLanguage), …)` |
| `Text("${reward.pointsCost} 积分", …)`（:113） | `Text(strings.costValue(reward.pointsCost), …)` |
| `contentDescription = "编辑"`（:115） | `contentDescription = strings.editContentDescription` |
| `contentDescription = "删除"`（:116） | `contentDescription = common.deleteContentDescription` |

**不动**：`initDefaultRewards()`（:70 落库调用，无字符串实参）、`deleteReward(reward.id)`、`reward.iconEmoji`（:98 纯 emoji）。

3d. `EN_STATIC_POINTS_MENTIONS` 追加：

```kotlin
    // ---- W3 Task 4 奖励商店管理页新增 1 条（英文原文逐条留在行尾注释里）。
    "parentRewardManage.emptyHint",           // "… adjust their point costs one by one."
```

验证：去注释 CJK 扫描 → 本页代码字面量 0 条。

- [ ] **Step 4: 确认绿**（ParentPointsCopyTest 8 条 + StringsCopyTest 63 条）

---

### Task 5: 等价闸门登记 + 跨表钉死 + 人工清单 + 四闸 + 唯一 W3 commit

**Files:**
- Modify: `scripts/i18n_template_diff.py`、`docs/i18n-stage1-verification.md`、`ParentPointsCopyTest.kt`（+1）

- [ ] **Step 1: 追加跨表钉死测试**

```kotlin
    @Test
    fun crossTableVocabularyPinned() {
        // 「积分管理」标题复用家长首页菜单（不另建同值字段）
        assertEquals("积分管理", AppStringsZh.parentHome.menuPointsTitle)
        assertEquals("Points management", AppStringsEn.parentHome.menuPointsTitle)
        // 「保存」与 W2 任务编辑页同值同译
        assertEquals(AppStringsZh.parentTaskEdit.saveButton, AppStringsZh.parentRewardEdit.saveButton)
        assertEquals(AppStringsEn.parentTaskEdit.saveButton, AppStringsEn.parentRewardEdit.saveButton)
        // 「编辑」与 W1 详情表、W2 任务管理表同值同译（三处一份译名）
        assertEquals(AppStringsZh.mistakeDetail.editContentDescription, AppStringsZh.parentRewardManage.editContentDescription)
        assertEquals(AppStringsEn.mistakeDetail.editContentDescription, AppStringsEn.parentRewardManage.editContentDescription)
        assertEquals(AppStringsZh.parentTaskManage.editContentDescription, AppStringsZh.parentRewardManage.editContentDescription)
        assertEquals(AppStringsEn.parentTaskManage.editContentDescription, AppStringsEn.parentRewardManage.editContentDescription)
        // 空态与载入提示复用家长首页（逐字节同值）
        assertEquals("奖励商店还是空的", AppStringsZh.parentHome.setupRewardsMissing)
        assertEquals("The reward store is still empty", AppStringsEn.parentHome.setupRewardsMissing)
        assertEquals("✅ 已载入预设奖励", AppStringsZh.parentHome.presetRewardsLoadedMessage)
        assertEquals("✅ Preset rewards loaded", AppStringsEn.parentHome.presetRewardsLoadedMessage)
        // 「+N分」复用任务列表积分标签（与 Task 9 ChildHome 先例一致）
        assertEquals("+3分", AppStringsZh.taskList.pointsEarnedLabel(3))
        assertEquals("+3 points", AppStringsEn.taskList.pointsEarnedLabel(3))
        assertEquals("+1分", AppStringsZh.taskList.pointsEarnedLabel(1))
        assertEquals("+1 point", AppStringsEn.taskList.pointsEarnedLabel(1))
    }
```

- [ ] **Step 2: 登记 BRANCH_PAGES / BRANCH_TABLES / 披露**

`scripts/i18n_template_diff.py`：

2a. 在 `W2_REV` 常量后加：

```python
W3_REV = "aae364e"        # 阶段 2 W3（家长积分/审批/奖励）迁移前 = W2 收口提交
```

2b. `BRANCH_PAGES` 末尾追加：

```python
    # ---- 阶段 2 W3（家长积分/审批/奖励，W3_REV）----
    (W3_REV, "com/hstudy/app/ui/parent/ParentPointsScreen.kt"),
    (W3_REV, "com/hstudy/app/ui/parent/ParentApprovalScreen.kt"),
    (W3_REV, "com/hstudy/app/ui/parent/ParentRewardEditScreen.kt"),
    (W3_REV, "com/hstudy/app/ui/parent/ParentRewardManageScreen.kt"),
```

2c. `BRANCH_TABLES` 元组推导追加 4 个表名：`"ParentPointsStrings", "ParentApprovalStrings", "ParentRewardEditStrings", "ParentRewardManageStrings"`。

2d. `DISCLOSED_RESIDUALS` 末尾追加 6 条（奖励分类 canonical 落库值，页面单边）：

```python
    # ---- 阶段 2 W3：奖励分类 canonical 落库值（页面单边；值不进表，渲染走注册表）----
    # ParentRewardEditScreen 的 categoryOptions 与默认值"其他"是 RewardEntity.category 的落库值
    # （Ruling 25），留在页面；W0 注册表已收录全部 7 条译名，渲染包 localizedPresetText。
    # "其他"与 MistakeStrings.subjectOther 两侧同形自然配平，不需要披露；其余 6 条在此登记。
    {"page": "娱乐", "table": None,
     "reason": "W3 Ruling 25：ParentRewardEditScreen.kt:48 categoryOptions 落库值，不本地化；渲染走注册表"},
    {"page": "美食", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "出行", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "特权", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "实物", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "社交", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
```

2e. 文档读数同步（脚本内三处 + 登记表读数注释）：
- 文件头用法行 `# 整枝组：全部已迁页面 × 全部二十张表` → `二十四张表`；
- 「哪些东西今天能钉回历史位置」段：`全部二十张` → `全部二十四张`、`[BRANCH_TABLES] 二十张全读一遍` → `二十四张`、`阶段 2 W2 那四张家长任务表是 W2 提交才落进历史的，所以**可用的最早 rev 就是 W2 提交本身**；在它之前（W2 之前的 HEAD 与 W1 收口 5deed68、4d28c8e、c3bd94a）一律 git show …失败` → 改为 W3 口径：`阶段 2 W3 那四张家长积分/审批/奖励表是 W3 提交才落进历史的，所以**可用的最早 rev 就是 W3 提交本身**；在它之前（W2 收口 aae364e、W1 收口 5deed68、4d28c8e、c3bd94a）一律 git show …失败`（其下的 W2 期实测退出码块保留作历史）；
- `--tables-rev` help 文本：`只对已经含全部二十张表的 rev 可用（本分支上阶段 2 W2 提交起…）…读满 BRANCH_TABLES 二十张` → `二十四张` / `阶段 2 W3 提交起`；
- 「登记表的当前实况读数」注释：`27 条（7 条两侧同形 + 13 条只看页面 + 7 条只看表侧）` → `33 条（7 条两侧同形 + 19 条只看页面 + 7 条只看表侧）`；`另外 21 条默认组用不到` → `27 条`；`--branch 组碰到 26 条` → `32 条`。**这些数字以 Step 4 实跑输出为准**，若与实跑不符按实跑改正。

- [ ] **Step 3: 人工清单与验证文档读数**

3a. `docs/i18n-stage1-verification.md` §2 末尾（V-43 之后）追加：

```markdown
### 阶段 2 W3（家长积分/审批/奖励）追加项
- [ ] V-44 英文态积分管理页：每日得分统计/任务排行/记录列表/调整对话框全英文；记录类型标签（任务完成/学习奖励/手动调整/兑换消耗/撤销扣回）英文；排行榜任务标题为预设时显示英文（注册表）、自建原样、缺失显示 "Task #N"；账本备注 record.note 原样（动态数据）。注意排行行 "N times" 与 "Net N" 在 36.dp/50.dp 固定宽度内的排版
- [ ] V-45 英文态积分审核页：待审核/历史选项卡、空态、通过/拒绝按钮、snackbar（含 one/other：1 point awarded）、五种状态标签全英文；审批描述 approval.description 原样（动态数据）
- [ ] V-46 英文态奖励编辑页：表单标签/预设提示/保存/删除全英文；分类下拉选项与选中值显示英文（注册表 Entertainment/Food/Outings/Privileges/Items/Social/Other），保存后落库分类仍为 canonical 中文
- [ ] V-47 英文态奖励商店管理页：空态引导/一键载入/积分成本（one/other：1 point / 300 points）全英文；奖励名/描述/分类为预设时显示英文（注册表），家长自建内容原样；载入预设后落库仍为 canonical 中文
```

3b. 同文件读数更新（以 Step 4 实跑输出为准）：
- §1 命令块 `--branch` 行：`25 条页面组条目 / 24 个文件 × 全部 20 张表）：distinct 614/607，披露后 594/594，[strict] 593 条整串相同 + 1 条拆分` → `29 条页面组条目 / 28 个文件 × 全部 24 张表`，distinct/披露后/strict 数字按实跑填；
- §1 `./gradlew :composeApp:testDebugUnitTest` 行：`155 tests` → `164 tests（含阶段 2 W1 的 12 条、W2 的 9 条与 W3 的 9 条…）`；
- §1 `StringsCopyTest（63 条）覆盖二十张表` 段：`二十张` → `二十四张`（两处），字段枚举末尾追加 `+ W3 的 parentPoints / parentApproval / parentRewardEdit / parentRewardManage 四张`，`grep -c … → 20` → `→ 24`；并在 W2 行后加一行 `W3 的漏译/跨表守卫另在 ParentPointsCopyTest（9 条，含 crossTableVocabularyPinned、rewardCategoryOptionsAreAllRegistered 与英文 one/other 逐字钉死）。`；
- §6 整枝组条目：`阶段 2 W2 登记后实测` → `阶段 2 W3 登记后实测`，distinct/披露后/strict 按实跑填，`25 条页面组条目 / 24 个文件，含 W1 的六页与 W2 的四页` → `29 条页面组条目 / 28 个文件，含 W1 的六页、W2 的四页与 W3 的四页`，`全部 20 张表` → `全部 24 张表`，`登记表（现 27 条）` → `（现 33 条）`，`实跑 26 条命中打印` → `32 条`，`其余 21 条默认组用不到` → `27 条`。

- [ ] **Step 4: 全量四闸（顺序，逐条 exit 0）**

```bash
PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py
PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py
PYTHONIOENCODING=utf-8 python scripts/i18n_template_diff.py --branch
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest
```

Expected: 边界 exit 0；自测 195 断言全 OK；`--branch` exit 0（[A] 0 missing/0 invented，[B] 0 diff，W3 四表四页并入整枝组，6 条新披露全部"已披露"命中，无登记过期）；assembleDebug OK；**164 tests / 0 failures**（155 + 9）。任何一闸红：修到绿，不许带红提交；若 `--branch` 报 missing/invented，按 Ruling 修实现/表值，**不得加披露掩盖**（6 条分类披露之外不许新增）。

- [ ] **Step 5: 落唯一 W3 commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentPointsStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentApprovalStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentRewardEditStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentRewardManageStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentPointsScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentApprovalScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentRewardEditScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentRewardManageScreen.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ParentPointsCopyTest.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt \
        scripts/i18n_template_diff.py \
        docs/i18n-stage1-verification.md
git commit -m "feat(i18n): 阶段 2 W3——家长积分/审批/奖励四页双语化（4 张表）、奖励预设渲染接注册表、等价闸门登记"
git status
```

Expected: commit 成功，工作树干净。**到此为止，不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译、164 单测、字节等价闸门整枝组（含 6 条分类披露）、存储边界、中文态逐字节不变、注册表渲染点 ZH 恒等（zh==key）、分类 7 条注册表全覆盖。
- 不可自动证明：英文态四页视觉呈现（人工 V-44…V-47，含固定宽度行的英文排版）；预设奖励名/描述在英文态的实际显示（注册表命中由 W0 交叉校验保证，视觉待人工）。
- 不以"CI 绿"表述"双语已验证"。