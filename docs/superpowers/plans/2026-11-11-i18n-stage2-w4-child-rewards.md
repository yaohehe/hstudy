# i18n 阶段 2 W4——孩子商店/积分/抽奖/成就 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 孩子端四页（奖励商店/每日幸运抽奖/积分明细/成就中心）双语化：4 张新文案表进 `AppStrings`（24→28 张），奖励名/描述/分类与 13 个成就走注册表渲染，账本 note 字面量按 Ruling 25 留在 canonical 层并入等价闸门披露，全波 8 条新测试、唯一 W4 commit。

**Architecture:** 沿用阶段 2 既有架构：文案进 `data class …Strings`（ZH/EN 两实例），页面 `LocalStrings.current` 读取；DB 保持 canonical 中文，`localizedPresetText(raw, lang)` 只在渲染点包奖励内容；成就标题/描述用 W0 的 `achievementCopy[displayLanguage]?.get(def.type)` 渲染（未命中回落 canonical）；`adjustPoints`/`spendPoints` 的账本 note 是落库值，**不本地化**（Ruling 25）。

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform 1.7.3、Kotlin 2.1.20；commonTest（`:composeApp:testDebugUnitTest`）；Python 等价闸门 `scripts/i18n_template_diff.py`。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§12 W4 行、§1 非目标、§10 测试策略、§11 验证上限）

## Global Constraints

- **Ruling 25 存储边界（硬约束）**：任何文案表字符串不得进入写库调用或参与业务判等参数。凡传进 `adjustPoints` / `spendPoints` / `exchangeReward` 的 note/name 参数，一律保持 canonical 中文原字面量，不接 `strings.*`、不包 `localizedPresetText`——注册表/文案表字符串只允许出现在渲染点（`Text(...)` 等）。
- **不动**：`data/**`、schema/迁移、`DateTimeCompat.kt`、`Plurals.kt`（现有 `pointsWord` 复用，不新增 helper）、平台源集（androidMain/iosMain）、`AchievementManager`、`RewardRepository`/`PointRepository`。
- **唯一改动形态**：`"文本"` → `strings.xxx` / `localizedPresetText(...)` 等价替换；逻辑行零变化。diff 出现逻辑变化 = 越界。
- **中文态字节等价**：既有页面中文渲染逐字节不变（等价闸门证明）。
- 复用既有值必须逐字节同值：`ChildHomeStrings.entryPointsDetailTitle`（"积分明细"/"Points details"）、`ChildHomeStrings.entryRewardStoreTitle`（"奖励商店"/"Reward Store"）、`ParentHomeStrings.setupRewardsMissing`（"奖励商店还是空的"/"The reward store is still empty"）、`CommonStrings.cancelButton`（"取消"/"Cancel"）。
- 英文复数只允许复用 `pointsWord(n)`；唯一例外是 `bannerAffordable` 里 reward/rewards 的行内条件（无既有 helper，不新增 helper，行内 `if (count == 1) "reward" else "rewards"`）。
- 每波本地四闸（顺序、逐条 exit 0）：`i18n_storage_boundary.py` → `tests/i18n_storage_boundary_selftest.py` → `i18n_template_diff.py --branch` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`。
- 波次纪律：任务内只 `git add` 暂存、**不提交**；Task 5 落唯一 W4 commit；**不推送**（推送由用户逐次批准）。
- 环境：Gradle 需内联 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home"`；Python 闸门需 `PYTHONIOENCODING=utf-8`。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildRewardStoreStrings.kt`（新建） | 奖励商店页 16 字段 |
| `.../i18n/ChildLuckyDrawStrings.kt`（新建） | 每日幸运抽奖页 13 字段 |
| `.../i18n/ChildPointsStrings.kt`（新建） | 积分明细页 10 字段 |
| `.../i18n/ChildAchievementsStrings.kt`（新建） | 成就中心页 4 字段 |
| `.../i18n/Strings.kt`（改） | `AppStrings` +3 表聚合（4 字段 + 4 Zh + 4 En 行） |
| `.../ui/child/ChildRewardStoreScreen.kt`（改） | 文案替换 + 注册表渲染（名/描述/分类/下一目标名/庆祝/snackbar） |
| `.../ui/child/ChildLuckyDrawScreen.kt`（改） | 文案替换；`adjustPoints` note 原样 |
| `.../ui/child/ChildPointsScreen.kt`（改） | 文案替换（含 5 条记录类型回退标签） |
| `.../ui/child/ChildAchievementsScreen.kt`（改） | 文案替换 + `achievementCopy` 渲染 def.title/description |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildRewardStoreCopyTest.kt`（新建） | W4 四表守卫 8 条测试 |
| `.../i18n/StringsCopyTest.kt`（改） | 闭集 `EN_STATIC_POINTS_MENTIONS` +6 |
| `scripts/i18n_template_diff.py`（改） | W4_REV/4 页/4 表/2 披露/读数 |
| `docs/i18n-stage1-verification.md`（改） | V-48…V-51 + 全量读数 |

---

### Task 1: ChildRewardStoreStrings + 奖励商店页（注册表渲染接线）

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildRewardStoreStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（:37 后加字段、:64 后加 Zh、:90 后加 En）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildRewardStoreScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildRewardStoreCopyTest.kt`

**Interfaces:**
- Consumes: `ChildHomeStrings.entryRewardStoreTitle`、`ParentHomeStrings.setupRewardsMissing`、`CommonStrings.cancelButton`、`localizedPresetText(raw, DisplayLanguage)`、`LocalStrings`/`LocalDisplayLanguage`、`pointsWord(n)`。
- Produces: `data class ChildRewardStoreStrings`（16 字段）、`AppStrings.childRewardStore`；测试文件骨架（后续任务追加）。

- [ ] **Step 1: 追加测试（编译红）**

创建 `ChildRewardStoreCopyTest.kt`：

```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W4 孩子端文案表守卫（沿用 StringsCopyTest/MistakeCopyTest/ParentPointsCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b），英文 one/other 用 pointsWord 全覆盖。
 * 覆盖四张表：childRewardStore / childLuckyDraw / childPoints / childAchievements。
 */
class ChildRewardStoreCopyTest {

    @Test
    fun childRewardStoreCopyDiffers() {
        val zh = AppStringsZh.childRewardStore
        val en = AppStringsEn.childRewardStore
        assertDiffers(
            zh.historyTitle to en.historyTitle,
            zh.exchangedFallback to en.exchangedFallback,
            zh.emptyHint to en.emptyHint,
            zh.confirmExchange to en.confirmExchange,
            zh.insufficientPoints to en.insufficientPoints,
            zh.redeemButton to en.redeemButton,
        )
    }

    @Test
    fun childRewardStoreInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childRewardStore
        val en = AppStringsEn.childRewardStore
        assertEquals("🎉 你有 12 分，现在可以兑换 3 个奖励啦！", zh.bannerAffordable(12, 3))
        assertEquals("🎉 You have 12 points — enough to redeem 3 rewards!", en.bannerAffordable(12, 3))
        assertEquals("🎉 你有 1 分，现在可以兑换 1 个奖励啦！", zh.bannerAffordable(1, 1))
        assertEquals("🎉 You have 1 point — enough to redeem 1 reward!", en.bannerAffordable(1, 1))
        assertEquals("💪 你有 5 分，完成任务赚积分来兑换吧！", zh.bannerKeepEarning(5))
        assertEquals("💪 You have 1 point — complete tasks to earn more and redeem rewards!", en.bannerKeepEarning(1))
        assertEquals("再攒 5 分就能兑换「🎮 玩游戏」啦～", zh.nextGoalMessage(5, "🎮", "玩游戏"))
        assertEquals("Just 1 more point to redeem “🎮 Playtime”!", en.nextGoalMessage(1, "🎮", "Playtime"))
        assertEquals("娱乐奖励", zh.categoryTitle("娱乐"))
        assertEquals("Entertainment rewards", en.categoryTitle("Entertainment"))
        assertEquals("-50分", zh.spentValue(50))
        assertEquals("-50 points", en.spentValue(50))
        assertEquals("-1 point", en.spentValue(1))
        assertEquals("需要 300 积分", zh.costNeeded(300))
        assertEquals("Costs 1 point", en.costNeeded(1))
        assertEquals("当前余额：12 积分", zh.currentBalance(12))
        assertEquals("Current balance: 12 points", en.currentBalance(12))
        assertEquals("🎉 兑换成功！看电影", zh.exchangeSuccess("看电影"))
        assertEquals("🎉 Redeemed! Movies", en.exchangeSuccess("Movies"))
        assertEquals("还差 20 分", zh.shortBy(20))
        assertEquals("1 point short", en.shortBy(1))
        assertEquals("⭐ 300 积分", zh.priceLabel(300))
        assertEquals("⭐ 1 point", en.priceLabel(1))
    }
}
```

- [ ] **Step 2: 确认红**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.ChildRewardStoreCopyTest"`
Expected: 编译失败 `Unresolved reference 'childRewardStore'`。

- [ ] **Step 3: 实现**

3a. 创建 `ChildRewardStoreStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端奖励商店页（ui/child/ChildRewardStoreScreen.kt）文案表（阶段 2 W4）。
 *
 * 复用而非新建：页标题「奖励商店」= [ChildHomeStrings.entryRewardStoreTitle]（孩子首页入口同值），
 * 空态标题「奖励商店还是空的」= [ParentHomeStrings.setupRewardsMissing]（W3 已核逐字节同值），
 * 「取消」= [CommonStrings.cancelButton]。
 * 奖励名/描述/分类是 canonical（W0 注册表 27 名 + 27 描述 + 7 分类），渲染点包 [localizedPresetText]；
 * 家长自建内容未命中原样。分类分组键 categoryOrder/categoryEmoji 是落库值（Ruling 25），留在页面。
 * **落库字面量**：`spendPoints(cost, "兑换：${reward.name}")` 的 note 是账本数据，保持 canonical
 * 中文原样、不进本表（等价闸门 Task 5 登记披露"兑换：⟨$⟩"）。
 * [bannerAffordable] 的英文 reward/rewards 用行内条件（无既有复数 helper，不新增）；
 * 其余数量词一律 [pointsWord]。
 */
data class ChildRewardStoreStrings(
    val bannerAffordable: (points: Int, count: Int) -> String,
    val bannerKeepEarning: (points: Int) -> String,
    val nextGoalMessage: (need: Int, emoji: String, name: String) -> String,
    val categoryTitle: (category: String) -> String,
    val historyTitle: String,
    val exchangedFallback: String,
    val spentValue: (points: Int) -> String,
    val emptyHint: String,
    val confirmExchange: String,
    val costNeeded: (cost: Int) -> String,
    val currentBalance: (points: Int) -> String,
    val exchangeSuccess: (name: String) -> String,
    val insufficientPoints: String,
    val redeemButton: String,
    val shortBy: (diff: Int) -> String,
    val priceLabel: (cost: Int) -> String,
)

val ChildRewardStoreStringsZh = ChildRewardStoreStrings(
    bannerAffordable = { points, count -> "🎉 你有 $points 分，现在可以兑换 $count 个奖励啦！" }, // :68
    bannerKeepEarning = { points -> "💪 你有 $points 分，完成任务赚积分来兑换吧！" },            // :69
    nextGoalMessage = { need, emoji, name -> "再攒 $need 分就能兑换「$emoji $name」啦～" },       // :74
    categoryTitle = { category -> "$category奖励" },                                          // :107
    historyTitle = "我的兑换记录",                                                             // :157
    exchangedFallback = "已兑换",                                                              // :166
    spentValue = { points -> "-$points分" },                                                   // :167
    emptyHint = "请爸爸妈妈到「奖励商店管理」里点一下“一键载入预设奖励”，就能出现好多奖品啦！",   // :187
    confirmExchange = "确认兑换",                                                              // :204/:235
    costNeeded = { cost -> "需要 $cost 积分" },                                                // :212
    currentBalance = { points -> "当前余额：$points 积分" },                                   // :213
    exchangeSuccess = { name -> "🎉 兑换成功！$name" },                                        // :227
    insufficientPoints = "积分不足哦，继续加油！💪",                                           // :230
    redeemButton = "兑换",                                                                     // :304
    shortBy = { diff -> "还差 $diff 分" },                                                     // :304
    priceLabel = { cost -> "⭐ $cost 积分" },                                                  // :295
)

val ChildRewardStoreStringsEn = ChildRewardStoreStrings(
    bannerAffordable = { points, count ->
        "🎉 You have $points ${pointsWord(points)} — enough to redeem $count ${if (count == 1) "reward" else "rewards"}!"
    },
    bannerKeepEarning = { points -> "💪 You have $points ${pointsWord(points)} — complete tasks to earn more and redeem rewards!" },
    nextGoalMessage = { need, emoji, name -> "Just $need more ${pointsWord(need)} to redeem “$emoji $name”!" },
    categoryTitle = { category -> "$category rewards" },
    historyTitle = "My redemptions",
    exchangedFallback = "Redeemed",
    spentValue = { points -> "-$points ${pointsWord(points)}" },
    emptyHint = "Ask your parents to open “Reward store management” and tap “Load preset rewards” — then lots of prizes will appear!",
    confirmExchange = "Confirm redemption",
    costNeeded = { cost -> "Costs $cost ${pointsWord(cost)}" },
    currentBalance = { points -> "Current balance: $points ${pointsWord(points)}" },
    exchangeSuccess = { name -> "🎉 Redeemed! $name" },
    insufficientPoints = "Not enough points yet — keep going! 💪",
    redeemButton = "Redeem",
    shortBy = { diff -> "$diff ${pointsWord(diff)} short" },
    priceLabel = { cost -> "⭐ $cost ${pointsWord(cost)}" },
)
```

3b. `Strings.kt`：
- `:37` 后加 `    val childRewardStore: ChildRewardStoreStrings,`
- `:64` 后加 `    childRewardStore = ChildRewardStoreStringsZh,`
- `:90` 后加 `    childRewardStore = ChildRewardStoreStringsEn,`

3c. 迁移 `ChildRewardStoreScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.localizedPresetText`。主 composable 在 `val isTablet = …` 后加：

```kotlin
    val strings = LocalStrings.current.childRewardStore
    val parentHome = LocalStrings.current.parentHome
    val common = LocalStrings.current.common
    val childHome = LocalStrings.current.childHome
    val displayLanguage = LocalDisplayLanguage.current
```

| 原名面量 | 替换 |
|---|---|
| `title = "奖励商店"`（:43） | `title = childHome.entryRewardStoreTitle` |
| `if (affordableCount > 0) "🎉 你有 $totalPoints 分，现在可以兑换 $affordableCount 个奖励啦！"`（:68） | `if (affordableCount > 0) strings.bannerAffordable(totalPoints, affordableCount)` |
| `else "💪 你有 $totalPoints 分，完成任务赚积分来兑换吧！"`（:69） | `else strings.bannerKeepEarning(totalPoints)` |
| `Text("再攒 $need 分就能兑换「${nextGoal.iconEmoji} ${nextGoal.name}」啦～",`（:74） | `Text(strings.nextGoalMessage(need, nextGoal.iconEmoji, localizedPresetText(nextGoal.name, displayLanguage)),` |
| `Text("${cat}奖励", …)`（:107） | `Text(strings.categoryTitle(localizedPresetText(cat, displayLanguage)), …)` |
| `Text("我的兑换记录", …)`（:157） | `Text(strings.historyTitle, …)` |
| `Text(exchange.note.ifEmpty { "已兑换" }, …)`（:166） | `Text(exchange.note.ifEmpty { strings.exchangedFallback }, …)` |
| `Text("-${exchange.pointsSpent}分", …)`（:167） | `Text(strings.spentValue(exchange.pointsSpent), …)` |
| `Text("奖励商店还是空的", …)`（:186） | `Text(parentHome.setupRewardsMissing, …)` |
| `Text("请爸爸妈妈到「奖励商店管理」里点一下“一键载入预设奖励”，就能出现好多奖品啦！",`（:187） | `Text(strings.emptyHint,` |
| `title = { Text("确认兑换") }`（:204） | `title = { Text(strings.confirmExchange) }` |
| `Text("${reward.iconEmoji} ${reward.name}", …)`（:207） | `Text("${reward.iconEmoji} ${localizedPresetText(reward.name, displayLanguage)}", …)` |
| `Text(reward.description, …)`（:209） | `Text(localizedPresetText(reward.description, displayLanguage), …)` |
| `Text("需要 ${reward.pointsCost} 积分", …)`（:212） | `Text(strings.costNeeded(reward.pointsCost), …)` |
| `Text("当前余额：$totalPoints 积分", …)`（:213） | `Text(strings.currentBalance(totalPoints), …)` |
| `celebrationMsg = "🎉 ${reward.iconEmoji} ${reward.name}"`（:225） | `celebrationMsg = "🎉 ${reward.iconEmoji} ${localizedPresetText(reward.name, displayLanguage)}"` |
| `snackbarHostState.showSnackbar("🎉 兑换成功！${reward.name}")`（:227） | `snackbarHostState.showSnackbar(strings.exchangeSuccess(localizedPresetText(reward.name, displayLanguage)))` |
| `snackbarHostState.showSnackbar("积分不足哦，继续加油！💪")`（:230） | `snackbarHostState.showSnackbar(strings.insufficientPoints)` |
| `) { Text("确认兑换") }`（:235） | `) { Text(strings.confirmExchange) }` |
| `{ Text("取消") }`（:238） | `{ Text(common.cancelButton) }` |
| `Text(reward.name, …)`（:287，RewardStoreCard） | `Text(localizedPresetText(reward.name, displayLanguage), …)` |
| `Text(reward.description, …)`（:291） | `Text(localizedPresetText(reward.description, displayLanguage), …)` |
| `Text("⭐ ${reward.pointsCost} 积分", …)`（:295） | `Text(strings.priceLabel(reward.pointsCost), …)` |
| `Text(if (canAfford) "兑换" else "还差 ${reward.pointsCost - currentPoints} 分")`（:304） | `Text(if (canAfford) strings.redeemButton else strings.shortBy(reward.pointsCost - currentPoints))` |

`RewardStoreCard`（private @Composable，:256）开头加：

```kotlin
    val strings = LocalStrings.current.childRewardStore
    val displayLanguage = LocalDisplayLanguage.current
```

**不动**（Ruling 25）：`categoryOrder`（:89，7 个 canonical 分类键）、`categoryEmoji`（:90-93，canonical 键 + emoji）、`rewards.groupBy { it.category }`、`app.rewardRepository.exchangeReward(reward.id, reward.pointsCost, reward.name)`（:221）、`app.pointRepository.spendPoints(reward.pointsCost, "兑换：${reward.name}")`（:222）、`reward.iconEmoji` / `exchange.note`（动态数据）、`reward.description.isNotEmpty()` 守卫（:208，测 canonical 值）。

验证：去注释 CJK 扫描 → 原始命中 = categoryOrder 的 7 个值 + categoryEmoji 的 7 个键（同值重复出现）+ `"兑换：${reward.name}"`；**去重后** = `['娱乐','美食','出行','特权','实物','社交','其他','兑换：${reward.name}']`（8 条：7 个 canonical 分类 + 1 条账本 note）。

- [ ] **Step 4: 确认绿**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.ChildRewardStoreCopyTest" --tests "com.hstudy.app.i18n.StringsCopyTest"`
Expected: ChildRewardStoreCopyTest 2 条 PASS；StringsCopyTest 63 条 PASS。全量：**166/0**。

- [ ] **Step 5: 暂存**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildRewardStoreStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildRewardStoreScreen.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildRewardStoreCopyTest.kt
```

---

### Task 2: ChildLuckyDrawStrings + 每日幸运抽奖页

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildLuckyDrawStrings.kt`
- Modify: `Strings.kt`（childLuckyDraw 字段/Zh/En）、`ChildLuckyDrawScreen.kt`、`ChildRewardStoreCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `pointsWord`、`LocalStrings`。
- Produces: `data class ChildLuckyDrawStrings`（13 字段）、`AppStrings.childLuckyDraw`。

- [ ] **Step 1: 追加测试（编译红）**

在 `ChildRewardStoreCopyTest` 类内追加：

```kotlin
    @Test
    fun childLuckyDrawCopyDiffers() {
        val zh = AppStringsZh.childLuckyDraw
        val en = AppStringsEn.childLuckyDraw
        assertDiffers(
            zh.title to en.title,
            zh.headline to en.headline,
            zh.subtitle to en.subtitle,
            zh.alreadyDrawn to en.alreadyDrawn,
            zh.drawing to en.drawing,
            zh.drawButton to en.drawButton,
            zh.comeTomorrow to en.comeTomorrow,
            zh.infoTitle to en.infoTitle,
            zh.infoPool to en.infoPool,
            zh.infoStreak to en.infoStreak,
        )
    }

    @Test
    fun childLuckyDrawInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childLuckyDraw
        val en = AppStringsEn.childLuckyDraw
        assertEquals("+3 分", zh.pointsWonValue(3))
        assertEquals("+3 points", en.pointsWonValue(3))
        assertEquals("+1 point", en.pointsWonValue(1))
        assertEquals("恭喜获得 3 积分奖励！", zh.winMessage(3))
        assertEquals("Congratulations — you won 3 points!", en.winMessage(3))
        assertEquals("Congratulations — you won 1 point!", en.winMessage(1))
        assertEquals("🎉 抽中 3 积分！", zh.drawSnackbar(3))
        assertEquals("🎉 You won 3 points!", en.drawSnackbar(3))
        assertEquals("🎉 You won 1 point!", en.drawSnackbar(1))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'childLuckyDraw'`）

- [ ] **Step 3: 实现**

3a. 创建 `ChildLuckyDrawStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端每日幸运抽奖页（ui/child/ChildLuckyDrawScreen.kt）文案表（阶段 2 W4）。
 *
 * 数量词一律 [pointsWord]。**落库字面量**：`adjustPoints(won, "每日幸运抽奖")` 的 note 是账本数据，
 * 保持 canonical 中文原样、不进本表（等价闸门 Task 5 登记披露"每日幸运抽奖"）。
 */
data class ChildLuckyDrawStrings(
    val title: String,
    val headline: String,
    val subtitle: String,
    val pointsWonValue: (points: Int) -> String,
    val winMessage: (points: Int) -> String,
    val alreadyDrawn: String,
    val drawSnackbar: (points: Int) -> String,
    val drawing: String,
    val drawButton: String,
    val comeTomorrow: String,
    val infoTitle: String,
    val infoPool: String,
    val infoStreak: String,
)

val ChildLuckyDrawStringsZh = ChildLuckyDrawStrings(
    title = "🎰 每日幸运抽奖",                                                  // :63
    headline = "试试你的手气！",                                                // :72
    subtitle = "每天可抽奖一次，随机获得奖励积分",                                // :73
    pointsWonValue = { points -> "+$points 分" },                              // :86
    winMessage = { points -> "恭喜获得 $points 积分奖励！" },                    // :96
    alreadyDrawn = "今天已经抽过奖啦，明天再来吧～",                              // :99
    drawSnackbar = { points -> "🎉 抽中 $points 积分！" },                      // :117
    drawing = "抽奖中…",                                                       // :124
    drawButton = "🎲 立即抽奖",                                                 // :124
    comeTomorrow = "明日再来",                                                  // :124
    infoTitle = "💡 奖励说明",                                                  // :135
    infoPool = "奖池包含 1~5 积分，小奖励更容易抽中，大奖凭运气！",                // :136
    infoStreak = "坚持每天打卡后再来抽奖，养成好习惯～",                          // :138
)

val ChildLuckyDrawStringsEn = ChildLuckyDrawStrings(
    title = "🎰 Daily Lucky Draw",
    headline = "Try your luck!",
    subtitle = "One draw per day — win bonus points at random",
    pointsWonValue = { points -> "+$points ${pointsWord(points)}" },
    winMessage = { points -> "Congratulations — you won $points ${pointsWord(points)}!" },
    alreadyDrawn = "You've already drawn today — come back tomorrow!",
    drawSnackbar = { points -> "🎉 You won $points ${pointsWord(points)}!" },
    drawing = "Drawing…",
    drawButton = "🎲 Draw now",
    comeTomorrow = "Come back tomorrow",
    infoTitle = "💡 Prize info",
    infoPool = "The prize pool holds 1–5 points — small prizes are more likely, and big wins are pure luck!",
    infoStreak = "Check in every day, then come draw — build a good habit!",
)
```

3b. `Strings.kt`：字段/Zh/En 三处（childRewardStore 之后）。

3c. 迁移 `ChildLuckyDrawScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalStrings`。主 composable 开头加：
```kotlin
    val strings = LocalStrings.current.childLuckyDraw
```

| 原名面量 | 替换 |
|---|---|
| `title = "🎰 每日幸运抽奖"`（:63） | `title = strings.title` |
| `Text("试试你的手气！", …)`（:72） | `Text(strings.headline, …)` |
| `Text("每天可抽奖一次，随机获得奖励积分", …)`（:73） | `Text(strings.subtitle, …)` |
| `Text("+$resultPoints 分", …)`（:86） | `Text(strings.pointsWonValue(resultPoints), …)` |
| `Text("恭喜获得 $resultPoints 积分奖励！",`（:96） | `Text(strings.winMessage(resultPoints),` |
| `Text("今天已经抽过奖啦，明天再来吧～",`（:99） | `Text(strings.alreadyDrawn,` |
| `snackbarHostState.showSnackbar("🎉 抽中 $won 积分！")`（:117） | `snackbarHostState.showSnackbar(strings.drawSnackbar(won))` |
| `if (isDrawing) "抽奖中…" else if (canDraw) "🎲 立即抽奖" else "明日再来",`（:124） | `if (isDrawing) strings.drawing else if (canDraw) strings.drawButton else strings.comeTomorrow,` |
| `Text("💡 奖励说明", …)`（:135） | `Text(strings.infoTitle, …)` |
| `Text("奖池包含 1~5 积分，小奖励更容易抽中，大奖凭运气！",`（:136） | `Text(strings.infoPool,` |
| `Text("坚持每天打卡后再来抽奖，养成好习惯～",`（:138） | `Text(strings.infoStreak,` |

**不动**（Ruling 25）：`app.pointRepository.adjustPoints(won, "每日幸运抽奖")`（:115）。

验证：去注释 CJK 扫描 → `['每日幸运抽奖']`（1 条）。

- [ ] **Step 4: 确认绿**（ChildRewardStoreCopyTest 4 条 + StringsCopyTest 63 条；全量 **168/0**）

- [ ] **Step 5: 暂存**（4 文件：新表、Strings.kt、页面、测试文件）

---

### Task 3: ChildPointsStrings + 积分明细页

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildPointsStrings.kt`
- Modify: `Strings.kt`、`ChildPointsScreen.kt`、`ChildRewardStoreCopyTest.kt`（+2）

**Interfaces:**
- Consumes: `ChildHomeStrings.entryPointsDetailTitle`、`pointsWord`、`LocalStrings`。
- Produces: `data class ChildPointsStrings`（10 字段）、`AppStrings.childPoints`。

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun childPointsCopyDiffers() {
        val zh = AppStringsZh.childPoints
        val en = AppStringsEn.childPoints
        assertDiffers(
            zh.balanceTitle to en.balanceTitle,
            zh.recordsTitle to en.recordsTitle,
            zh.emptyRecords to en.emptyRecords,
            zh.typeTaskComplete to en.typeTaskComplete,
            zh.typeBonus to en.typeBonus,
            zh.typeAdjustment to en.typeAdjustment,
            zh.typeRewardExchange to en.typeRewardExchange,
            zh.typeReversal to en.typeReversal,
        )
    }

    @Test
    fun childPointsInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.childPoints
        val en = AppStringsEn.childPoints
        assertEquals("⭐ 12 分", zh.balanceValue(12))
        assertEquals("⭐ 12 points", en.balanceValue(12))
        assertEquals("⭐ 1 point", en.balanceValue(1))
        assertEquals("⏳ 待家长审核 +3 分（通过后计入余额）", zh.pendingNote(3))
        assertEquals("⏳ Pending parent approval: +3 points (added to your balance once approved)", en.pendingNote(3))
        assertEquals("⏳ Pending parent approval: +1 point (added to your balance once approved)", en.pendingNote(1))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'childPoints'`）

- [ ] **Step 3: 实现**

3a. 创建 `ChildPointsStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子端积分明细页（ui/child/ChildPointsScreen.kt）文案表（阶段 2 W4）。
 *
 * 页标题「积分明细」复用 [ChildHomeStrings.entryPointsDetailTitle]（孩子首页入口同值）。
 * 记录行优先显示账本 note（动态数据，原样）；note 为空时按 [PointRecordType] 回退到本表
 * 五条类型标签（与家长端 ParentPointsStrings.type* 文案不同值，不跨表复用）。
 * 数量词一律 [pointsWord]。
 */
data class ChildPointsStrings(
    val balanceTitle: String,
    val balanceValue: (points: Int) -> String,
    val pendingNote: (points: Int) -> String,
    val recordsTitle: String,
    val emptyRecords: String,
    val typeTaskComplete: String,
    val typeBonus: String,
    val typeAdjustment: String,
    val typeRewardExchange: String,
    val typeReversal: String,
)

val ChildPointsStringsZh = ChildPointsStrings(
    balanceTitle = "总积分余额",                                                // :49
    balanceValue = { points -> "⭐ $points 分" },                              // :50
    pendingNote = { points -> "⏳ 待家长审核 +$points 分（通过后计入余额）" },    // :55
    recordsTitle = "积分变动记录",                                             // :65
    emptyRecords = "还没有积分记录哦，快去打卡吧！",                              // :74
    typeTaskComplete = "完成任务",                                            // :105
    typeBonus = "获得奖励",                                                   // :106
    typeAdjustment = "家长调整",                                              // :107
    typeRewardExchange = "兑换奖励",                                          // :108
    typeReversal = "撤销扣回",                                                // :109
)

val ChildPointsStringsEn = ChildPointsStrings(
    balanceTitle = "Total points balance",
    balanceValue = { points -> "⭐ $points ${pointsWord(points)}" },
    pendingNote = { points -> "⏳ Pending parent approval: +$points ${pointsWord(points)} (added to your balance once approved)" },
    recordsTitle = "Point history",
    emptyRecords = "No point records yet — go check in!",
    typeTaskComplete = "Task completed",
    typeBonus = "Bonus earned",
    typeAdjustment = "Parent adjustment",
    typeRewardExchange = "Reward redeemed",
    typeReversal = "Reversed",
)
```

3b. `Strings.kt`：字段/Zh/En 三处（childLuckyDraw 之后）。

3c. 迁移 `ChildPointsScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalStrings`。主 composable 开头加：
```kotlin
    val strings = LocalStrings.current.childPoints
    val childHome = LocalStrings.current.childHome
```
`PointHistoryItem`（private @Composable，:86）开头加：
```kotlin
    val strings = LocalStrings.current.childPoints
```

| 原名面量 | 替换 |
|---|---|
| `title = "积分明细"`（:34） | `title = childHome.entryPointsDetailTitle` |
| `Text("总积分余额", …)`（:49） | `Text(strings.balanceTitle, …)` |
| `Text("⭐ $totalPoints 分", …)`（:50） | `Text(strings.balanceValue(totalPoints), …)` |
| `"⏳ 待家长审核 +$pendingPoints 分（通过后计入余额）",`（:55） | `strings.pendingNote(pendingPoints),` |
| `Text("积分变动记录", …)`（:65） | `Text(strings.recordsTitle, …)` |
| `Text("还没有积分记录哦，快去打卡吧！", …)`（:74） | `Text(strings.emptyRecords, …)` |
| `PointRecordType.TASK_COMPLETE -> "完成任务"`（:105） | `PointRecordType.TASK_COMPLETE -> strings.typeTaskComplete` |
| `PointRecordType.BONUS -> "获得奖励"`（:106） | `PointRecordType.BONUS -> strings.typeBonus` |
| `PointRecordType.ADJUSTMENT -> "家长调整"`（:107） | `PointRecordType.ADJUSTMENT -> strings.typeAdjustment` |
| `PointRecordType.REWARD_EXCHANGE -> "兑换奖励"`（:108） | `PointRecordType.REWARD_EXCHANGE -> strings.typeRewardExchange` |
| `PointRecordType.REVERSAL -> "撤销扣回"`（:109） | `PointRecordType.REVERSAL -> strings.typeReversal` |

**不动**：`record.note` 判空与显示（动态数据）、`SimpleDateFormat`（日期格式不本地化，spec §6）。

验证：去注释 CJK 扫描 → `[]`（0 条）。

- [ ] **Step 4: 确认绿**（ChildRewardStoreCopyTest 6 条 + StringsCopyTest 63；全量 **170/0**）

- [ ] **Step 5: 暂存**（4 文件）

---

### Task 4: ChildAchievementsStrings + 成就中心页（achievementCopy 接线）

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildAchievementsStrings.kt`
- Modify: `Strings.kt`、`ChildAchievementsScreen.kt`、`ChildRewardStoreCopyTest.kt`（+1）

**Interfaces:**
- Consumes: `achievementCopy: Map<DisplayLanguage, Map<String, AchievementCopy>>`（PresetRegistry.kt，键=成就 `type`，值含 `title`/`description`）、`LocalDisplayLanguage`、`LocalStrings`。
- Produces: `data class ChildAchievementsStrings`（4 字段）、`AppStrings.childAchievements`。

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun childAchievementsCopyDiffers() {
        val zh = AppStringsZh.childAchievements
        val en = AppStringsEn.childAchievements
        assertDiffers(
            zh.title to en.title,
            zh.myAchievements to en.myAchievements,
            zh.allUnlocked to en.allUnlocked,
            zh.keepGoing to en.keepGoing,
        )
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'childAchievements'`）

- [ ] **Step 3: 实现**

3a. 创建 `ChildAchievementsStrings.kt`：

```kotlin
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
```

3b. `Strings.kt`：字段/Zh/En 三处（childPoints 之后）。

3c. 迁移 `ChildAchievementsScreen.kt`。新增 import：`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`、`com.hstudy.app.i18n.achievementCopy`。主 composable 开头加：
```kotlin
    val strings = LocalStrings.current.childAchievements
    val displayLanguage = LocalDisplayLanguage.current
```

| 原名面量 | 替换 |
|---|---|
| `title = "🏅 成就中心"`（:41） | `title = strings.title` |
| `Text("🏅 我的成就", …)`（:56） | `Text(strings.myAchievements, …)` |
| `if (unlockedCount == allDefs.size) "🎉 太厉害了！已解锁全部成就！"`（:67） | `if (unlockedCount == allDefs.size) strings.allUnlocked` |
| `else "继续加油，解锁更多成就勋章！",`（:68） | `else strings.keepGoing,` |
| `Text(def.title, …)`（:89） | `Text(achievementCopy[displayLanguage]?.get(def.type)?.title ?: def.title, …)` |
| `Text(def.description, …)`（:90） | `Text(achievementCopy[displayLanguage]?.get(def.type)?.description ?: def.description, …)` |

**不动**：`AchievementManager.allAchievements` / `getProgress`（W8 归属）、`"$cur/$target"`（纯数字）、`def.emoji`、`"✅"`。

验证：去注释 CJK 扫描 → `[]`（0 条）。

- [ ] **Step 4: 确认绿**（ChildRewardStoreCopyTest 7 条 + StringsCopyTest 63；全量 **171/0**）

- [ ] **Step 5: 暂存**（4 文件）

---

### Task 5: 等价闸门登记 + 闭集 + 跨表钉死 + 人工清单 + 四闸 + 唯一 W4 commit

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildRewardStoreCopyTest.kt`（+1）
- Modify: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`（闭集 +6）
- Modify: `scripts/i18n_template_diff.py`
- Modify: `docs/i18n-stage1-verification.md`

- [ ] **Step 1: 追加跨表钉死测试**

在 `ChildRewardStoreCopyTest` 类内追加：

```kotlin
    @Test
    fun crossTableVocabularyPinned() {
        // 页标题复用孩子首页入口（不另建同值字段）
        assertEquals("积分明细", AppStringsZh.childHome.entryPointsDetailTitle)
        assertEquals("Points details", AppStringsEn.childHome.entryPointsDetailTitle)
        assertEquals("奖励商店", AppStringsZh.childHome.entryRewardStoreTitle)
        assertEquals("Reward Store", AppStringsEn.childHome.entryRewardStoreTitle)
        // 空态标题复用家长奖励商店管理（W3 同值）
        assertEquals("奖励商店还是空的", AppStringsZh.parentHome.setupRewardsMissing)
        assertEquals("The reward store is still empty", AppStringsEn.parentHome.setupRewardsMissing)
        // 取消复用公共表
        assertEquals("取消", AppStringsZh.common.cancelButton)
        assertEquals("Cancel", AppStringsEn.common.cancelButton)
    }
```

- [ ] **Step 2: 闭集 +6**

`StringsCopyTest.kt` 的 `EN_STATIC_POINTS_MENTIONS` 末尾（W3 Task 4 块之后）追加：

```kotlin
    // ---- W4 Task 1/2/3 孩子端新增 6 条（英文原文逐条留在行尾注释里）。
    "childRewardStore.insufficientPoints",    // "Not enough points yet — keep going! 💪"
    "childLuckyDraw.subtitle",                // "One draw per day — win bonus points at random"
    "childLuckyDraw.infoPool",                // "The prize pool holds 1–5 points — small prizes are more likely, and big wins are pure luck!"
    "childPoints.balanceTitle",               // "Total points balance"
    "childPoints.recordsTitle",               // "Point history"
    "childPoints.emptyRecords",               // "No point records yet — go check in!"
```

- [ ] **Step 3: 等价闸门登记**

`scripts/i18n_template_diff.py`：

3a. `W3_REV` 后加：
```python
W4_REV = "14c4d82"        # 阶段 2 W4（孩子商店/积分/抽奖/成就）迁移前 = W3 收口提交
```

3b. `BRANCH_PAGES` 末尾追加：
```python
    # ---- 阶段 2 W4（孩子商店/积分/抽奖/成就，W4_REV）----
    (W4_REV, "com/hstudy/app/ui/child/ChildRewardStoreScreen.kt"),
    (W4_REV, "com/hstudy/app/ui/child/ChildLuckyDrawScreen.kt"),
    (W4_REV, "com/hstudy/app/ui/child/ChildPointsScreen.kt"),
    (W4_REV, "com/hstudy/app/ui/child/ChildAchievementsScreen.kt"),
```

3c. `BRANCH_TABLES` 元组追加 4 个表名：`"ChildRewardStoreStrings", "ChildLuckyDrawStrings", "ChildPointsStrings", "ChildAchievementsStrings"`。

3d. `DISCLOSED_RESIDUALS` 末尾追加 2 条（Ruling 25 账本 note，页面单边）：
```python
    # ---- 阶段 2 W4：账本 note 字面量（页面单边；值不进表，spec §1 非目标）----
    # adjustPoints / spendPoints 的 note 是落库数据，保持 canonical 中文原样（Ruling 25）。
    {"page": "每日幸运抽奖", "table": None,
     "reason": "W4 Ruling 25：ChildLuckyDrawScreen.kt:115 adjustPoints 账本 note，值留在 Canonical 层"},
    {"page": "兑换：⟨$⟩", "table": None,
     "reason": "W4 Ruling 25：ChildRewardStoreScreen.kt:222 spendPoints 账本 note，值留在 Canonical 层"},
```

3e. 文档读数同步（脚本内三处 + 登记表读数注释）：
- 文件头用法行 `全部二十四张表` → `全部二十八张表`；
- 「哪些东西今天能钉回历史位置」段：`全部二十四张` → `全部二十八张`、`[BRANCH_TABLES] 二十四张全读一遍` → `二十八张`；W3 口径改为 W4 口径：`阶段 2 W4 那四张孩子端表是 W4 提交才落进历史的，所以**可用的最早 rev 就是 W4 提交本身**；在它之前（W3 收口 14c4d82、W2 收口 aae364e、W1 收口 5deed68、4d28c8e、c3bd94a）一律 git show …失败`（其下 W2 期实测退出码块保留作历史）；
- `--tables-rev` help 文本：`全部二十四张` → `全部二十八张`、`BRANCH_TABLES 二十四张` → `二十八张`、`阶段 2 W3 提交起` → `阶段 2 W4 提交起`；
- 「登记表的当前实况读数」注释：`**35 条**`（7 条两侧同形 + 21 条只看页面 + 7 条只看表侧）；默认组 6 条、另外 **29** 条用不到；`--branch` 组碰到 **34** 条、只剩 **1** 条报"（未用到）"；归因改为 `W4 Task 5 复算`。**数字以 Step 5 实跑输出为准**，若与实跑不符按实跑改正。

- [ ] **Step 4: 人工清单与验证文档读数**

4a. `docs/i18n-stage1-verification.md` §2 末尾（V-47 之后）追加：

```markdown
### 阶段 2 W4（孩子商店/积分/抽奖/成就）追加项
- [ ] V-48 英文态孩子奖励商店：激励横幅/分类标题/兑换卡片/确认对话框/兑换成功庆祝与 snackbar 全英文；预设奖励名/描述/分类显示英文（注册表）、家长自建原样；兑换后积分账本 note 仍为中文 canonical（动态数据）。注意兑换卡片 160.dp 固定宽度内英文名/描述的单行截断
- [ ] V-49 英文态每日幸运抽奖：标题/说明/按钮/结果文案/奖池说明全英文；one/other（+1 point / +3 points）；抽奖后账本 note 仍为中文 canonical
- [ ] V-50 英文态积分明细：余额卡/待家长审核提示/记录类型回退标签全英文；账本 note 原样（动态数据）；日期格式保持 MM/dd HH:mm
- [ ] V-51 英文态成就中心：标题/汇总文案全英文；13 个成就标题与描述显示英文（achievementCopy 注册表）；进度 N/M 数字格式不变
```

4b. 同文件读数更新（以 Step 5 实跑输出为准）：
- §1 命令块 `--branch` 行：`29 条页面组条目 / 28 个文件 × 全部 24 张表` → `33 条页面组条目 / 32 个文件 × 全部 28 张表`，distinct/披露后/strict 按实跑填；
- §1 `./gradlew :composeApp:testDebugUnitTest` 行：`164 tests` → `172 tests（含阶段 2 W1 的 12 条、W2 的 9 条、W3 的 9 条与 W4 的 8 条…）`；
- §1 `StringsCopyTest（63 条）覆盖二十四张表` 段：`二十四张` → `二十八张`（两处），字段枚举末尾追加 `+ W4 的 childRewardStore / childLuckyDraw / childPoints / childAchievements 四张`，`grep -c … → 24` → `→ 28`；W3 守卫行后加：`W4 的孩子端四页守卫另在 ChildRewardStoreCopyTest（8 条，含 crossTableVocabularyPinned 与英文 one/other 逐字钉死）。`
- L-19 行：`（现 33 条）` → `（现 35 条）`、`"32 命中 / 1 未用到"` → `"34 命中 / 1 未用到"`（其余逐字节不动；「第 19 条」序号按终审裁决留 W9 sweep）；
- §6 整枝组条目：`阶段 2 W3 登记后实测` → `阶段 2 W4 登记后实测`，distinct/披露后/strict 按实跑填，`29 条页面组条目 / 28 个文件，含 W1 的六页、W2 的四页与 W3 的四页` → `33 条页面组条目 / 32 个文件，含 W1 的六页、W2 的四页、W3 的四页与 W4 的四页`，`全部 24 张表` → `全部 28 张表`，`登记表（现 33 条）` → `（现 35 条）`，`实跑 32 条命中打印` → `34 条`，`其余 27 条默认组用不到` → `29 条`。

- [ ] **Step 5: 全量四闸（顺序，逐条 exit 0）**

```bash
PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py
PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py
PYTHONIOENCODING=utf-8 python scripts/i18n_template_diff.py --branch
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest
```

Expected: 边界 exit 0；自测 195 断言全 OK；`--branch` exit 0（[A] 0 missing/0 invented，[B] 0 diff，W4 四表四页并入整枝组，2 条新披露"已披露"命中，无登记过期）；assembleDebug OK；**172 tests / 0 failures**（164 + 8）。任何一闸红：修到绿，不许带红提交；若 `--branch` 报 missing/invented，按 Ruling 修实现/表值，**不得加披露掩盖**（2 条账本 note 披露之外不许新增）。

- [ ] **Step 6: 落唯一 W4 commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildRewardStoreStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildLuckyDrawStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildPointsStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildAchievementsStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildRewardStoreScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildLuckyDrawScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildPointsScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildAchievementsScreen.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/ChildRewardStoreCopyTest.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt \
        scripts/i18n_template_diff.py \
        docs/i18n-stage1-verification.md
git commit -m "feat(i18n): 阶段 2 W4——孩子商店/积分/抽奖/成就四页双语化（4 张表）、奖励与成就渲染接注册表、等价闸门登记"
git status
```

Expected: commit 成功，工作树干净。**到此为止，不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译、172 单测、字节等价闸门整枝组（含 2 条账本 note 披露）、存储边界、中文态逐字节不变、注册表渲染点 ZH 恒等（zh==key）、成就注册表 13 条键覆盖（W0 测试）。
- 不可自动证明：英文态四页视觉呈现（人工 V-48…V-51，含 160.dp 卡片内英文截断）；预设奖励名/描述在英文态的实际显示（注册表命中由 W0 交叉校验保证）。
- 不以"CI 绿"表述"双语已验证"。
