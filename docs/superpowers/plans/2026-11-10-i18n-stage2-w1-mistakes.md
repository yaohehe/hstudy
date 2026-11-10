# i18n 阶段 2 W1（错题域）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把错题域 4 个页面（列表/详情/新增/家长分析）+ 错题复习通知迁入双语文案表：新增 1 张共享表 + 4 张页面表 + 1 张通知表（共 6 张），`SpacedRepetition` 的 8 条标签文案出表，Android/iOS 复习通知标题与正文随生效语言；等价闸门登记本波页面与表。

**Architecture:** 沿用阶段 1 机制（`AppStrings` 聚合 + `stringsFor` + `LocalStrings`/`LocalDisplayLanguage` + 函数型插值字段），不发明新机制。跨 4 页复用的域词汇（科目名、掌握状态、熟练度/下次复习标签、照片 contentDescription）上收为共享表 `MistakeStrings`（仿 `CommonStrings` 的上收先例）；每页一张表。非 Composable 发出点（通知）用 `stringsFor(currentDisplayLanguage())` 取词。存储边界不变：落库文案（账本 note/审批 description）原样留在页面，**不**进表。

**Tech Stack:** Kotlin 2.1.20 / Compose Multiplatform 1.7.3（不升级）；commonTest 经 `:composeApp:testDebugUnitTest`（Android 本地 JVM）执行；Python 闸门脚本。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（已批准，commit 4ee0d02；本计划实现其 §12 W1 行）

## Global Constraints（逐字摘自 spec，每个任务隐含包含）

- 不改 schema、不写迁移、不动 `data/**` 的任何文件（含种子、账本描述、同步 DTO、偏好键）。（§1）
- 文案表/注册表的任何字符串**不得**到达：DB 写入、bizKey 构造、幂等/去重比较、同步 DTO 字段、偏好键值（Ruling 25）。（§5）
- `localizedPresetText` 及一切 `strings.*` 只允许出现在渲染表达式里。（§5）
- 不升级 Compose Multiplatform / Kotlin 版本；不动 `ui/dt/DateTimeCompat.kt`、`Plurals.kt` **机制**（英文复数仍 one/other；按 spec §1 既有约定，`Plurals.kt` 只允许**新增同型的窄函数**）。（§1）
- W1 的唯一改动形态：`"文本"` → `strings.xxx` / `strings.fn(args)` 等价替换；**逻辑行零变化**。本波仅有的三处非纯替换在计划里逐行钉死：`SpacedRepetition` 两个标签函数加 `lang` 参数、common `ReviewReminderReceiver.schedule`/`localizedCopy` 变成 suspend 取词、androidMain 两处平台调用改传本地化文案。（§12）
- **平台文件授权（用户 2026-11-10 明确批准）**：本波允许改 `androidMain/kotlin/com/hstudy/app/platform/ReviewReminderReceiver.kt` 与 `androidMain/.../AndroidNotificationScheduler.kt`（仅：正文改调 common 本地化函数、`showNotification` 增加可选 `titleOverride` 参数且缺省行为不变）。其余平台文件仍零改动；`AndroidFileSharer.kt` 分享标题仍属 W9。（§1 的唯一平台例外在本波扩大一次，仅此两文件、仅通知本地化）
- 本波不迁错题类型/难度/分类/成就/场景的**注册表**映射（属 W9）；本波页面里 `mistakeErrorTypes` 的 chip 标签保持 canonical 原样。（§12 W9 行）
- 每波本地四闸：`i18n_storage_boundary.py` + 自测 → `i18n_template_diff.py --branch` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`。（§12）
- **按波提交，一波一个 commit**：本计划 Task 1–5 只验证不提交，Task 6 落唯一 W1 commit。（§12）
- 推送经用户逐次批准；本计划**不含推送步骤**。PAT 只经环境变量/内联认证使用，绝不写入仓库、文档或日志。
- 不以"CI 绿"表述"双语已验证"；W1 无可视化验证物之外的英文视觉呈现仍进人工清单（§11）。

## 环境与命令（零上下文执行者必读）

- 工作目录：`D:\game\hstudy-1`（Git Bash）。Python 脚本一律带 `PYTHONIOENCODING=utf-8`。
- Gradle 必须内联两个环境变量（本机 PATH 无 java）：
  `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew …`
- 单测跑法：`:composeApp:testDebugUnitTest`（commonTest 在 Android 本地 JVM 执行）。
- 基线：**134 tests 全绿**（v3.1.0 = `4d28c8e` 实测，2026-11-10）。W1 新增 12 条（MistakeCopyTest 11 + SpacedRepetitionTest 1），终态 **146 tests / 0 failures**。
- 闸门 Python 侧不扫 androidMain：平台两文件的验证靠 `assembleDebug` 编译 + 人工核对代码（计划里逐行给出）。
- 迁移前 rev：**`4d28c8e`**（v3.1.0 发布提交，本波所有页面/非页面源文件的迁移前树）。

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeStrings.kt` | 新增 | 共享域词汇：5 科目裸标签 + 5 科目 chip 标签（含 emoji）+ 掌握/未掌握 + 照片 contentDescription + 熟练度/下次复习 8 条标签 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeListStrings.kt` | 新增 | 错题列表页（含状态卡/筛选栏/卡片/提醒开关）43 个字段 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeDetailStrings.kt` | 新增 | 错题详情页 41 个字段（含 15 条举一反三引导问题） |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeAddStrings.kt` | 新增 | 添加错题页 11 个字段 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentMistakeReviewStrings.kt` | 新增 | 家长错题分析页 7 个字段 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ReviewReminderStrings.kt` | 新增 | 复习通知 4 个字段（1 个函数型） |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Plurals.kt` | 修改 | **只新增** 3 个同型窄函数：`timesWord` / `daysWord` / `mistakesWord`；机制不动 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt` | 修改 | `AppStrings` 加 6 个字段 + Zh/En 各 6 行（扩展点即 W0 KDoc 所述，无结构改动） |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/SpacedRepetition.kt` | 修改 | 标签函数加 `lang: DisplayLanguage`，文案取自 `stringsFor(lang).mistake`；间隔算法零改动 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt` | 修改 | 43 处字面量 → 表引用（除 3 条落库原文） |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt` | 修改 | 全部 UI 字面量 → 表引用（除 4 条落库原文） |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeAddScreen.kt` | 修改 | 17 处字面量 → 表引用 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ParentMistakeReviewScreen.kt` | 修改 | 13 处字面量 → 表引用 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ReviewReminderReceiver.kt` | 修改 | 4 条字面量 → 表；`schedule` 变 suspend 取词；新增 `localizedCopy()`；删 `TITLE` 常量与 `reminderBody()`（无调用方） |
| `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/ReviewReminderReceiver.kt` | 修改（授权） | 两处硬编码正文 → `ReviewReminderReceiver.localizedCopy()`；标题走 `titleOverride` |
| `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidNotificationScheduler.kt` | 修改（授权） | `showNotification` 增加可选 `titleOverride: String? = null`（缺省行为不变） |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt` | 新增 | 11 条：6 表漏译/函数钉死 + 跨表共享值钉死 |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/ui/mistake/SpacedRepetitionTest.kt` | 修改 | 现有 12 条适配 `lang` 参数 + 新增 1 条中英标签对照 |
| `scripts/i18n_template_diff.py` | 修改 | `W1_REV` 常量 + 6 条 BRANCH_PAGES + 6 张 BRANCH_TABLES + 7 条落库原文披露 |
| `docs/i18n-stage1-verification.md` | 修改 | §2 人工清单追加 W1 四页（6 条 V-34…V-39） |

**分层决定：** 共享表 `MistakeStrings` 的存在理由与 `CommonStrings` 相同——科目名在 4 个页面出现，若每页各译一份，"其他"会与注册表 `preset("其他","Other")` 及三处页面各自漂移；上收后中文值逐字节不变、英文只有一个出处，并由测试钉住跨表同值。`SpacedRepetition` 只依赖 `i18n` 的 `DisplayLanguage`/`stringsFor`，不依赖任何 UI 表类型。

---

### Task 1: 共享表 MistakeStrings + Plurals 窄函数 + SpacedRepetition 双语化

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Plurals.kt`（只追加）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/SpacedRepetition.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`（新建）
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/ui/mistake/SpacedRepetitionTest.kt`（改）

**Interfaces:**
- Consumes: `DisplayLanguage`、`stringsFor`（既有）；`pointsWord` 模式（Plurals）。
- Produces: `data class MistakeStrings(...)`（16 字段，见下）；`MistakeStringsZh/En`；`AppStrings.mistake`；`SpacedRepetition.proficiencyLabel(reviewCount: Int, lang: DisplayLanguage): String`、`SpacedRepetition.nextReviewLabel(mistake: MistakeEntity, lang: DisplayLanguage, now: Long = com.hstudy.app.ui.dt.nowMillis()): String`；`internal fun timesWord(n: Int)`、`internal fun daysWord(n: Int)`、`internal fun mistakesWord(n: Int)`。Task 2–5 的页面全部消费这些。

- [ ] **Step 1: 写测试（新 API 尚不存在 = 编译红）**

创建 `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`，先只放共享表部分（后续任务在类体内追加，不要删本任务的三个测试）：

```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阶段 2 W1 错题域文案表的漏译/语序守卫（沿用 StringsCopyTest 的约定）：
 * - 每个静态字段 ZH≠EN（assertDiffers 由 AssertDiffers.kt 提供）；
 * - 每个函数型字段必须有**逐字打出的双语期望**（Ruling 20-b）；
 * - 跨表共享的同一个中文值只允许一个英文出处，由 pinned 测试钉住。
 */
class MistakeCopyTest {

    @Test
    fun mistakeSharedStaticCopyDiffers() {
        val zh = AppStringsZh.mistake
        val en = AppStringsEn.mistake
        assertDiffers(
            zh.subjectMath to en.subjectMath,
            zh.subjectChinese to en.subjectChinese,
            zh.subjectEnglish to en.subjectEnglish,
            zh.subjectScience to en.subjectScience,
            zh.subjectOther to en.subjectOther,
            zh.subjectMathChip to en.subjectMathChip,
            zh.subjectChineseChip to en.subjectChineseChip,
            zh.subjectEnglishChip to en.subjectEnglishChip,
            zh.subjectScienceChip to en.subjectScienceChip,
            zh.subjectOtherChip to en.subjectOtherChip,
            zh.masteredStatus to en.masteredStatus,
            zh.unmasteredStatus to en.unmasteredStatus,
            zh.photoContentDescription to en.photoContentDescription,
            zh.proficiencyNone to en.proficiencyNone,
            zh.proficiencyFair to en.proficiencyFair,
            zh.proficiencyStrong to en.proficiencyStrong,
            zh.nextReviewMastered to en.nextReviewMastered,
            zh.nextReviewToday to en.nextReviewToday,
            zh.nextReviewTomorrow to en.nextReviewTomorrow,
        )
    }

    @Test
    fun mistakeSharedFunctionCopyPinsBothLanguages() {
        val zh = AppStringsZh.mistake
        val en = AppStringsEn.mistake
        // proficiencyTimes 与 nextReviewInDays 都是函数型字段：逐字钉死两种语言
        assertEquals("复习1次", zh.proficiencyTimes(1))
        assertEquals("复习2次", zh.proficiencyTimes(2))
        assertEquals("Reviewed 1 time", en.proficiencyTimes(1))
        assertEquals("Reviewed 2 times", en.proficiencyTimes(2))
        assertEquals("3天后复习", zh.nextReviewInDays(3))
        assertEquals("Review in 3 days", en.nextReviewInDays(3))
    }
}
```

并在 `SpacedRepetitionTest.kt` 类体末尾追加一条（本步一起写入，此时编译红）：

```kotlin
    // ---- 阶段 2 W1：标签随语言 ----

    @Test
    fun labelsSwitchWithLanguage() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 0L, lastReviewAt = 0L)
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(3, DisplayLanguage.ZH))
        assertEquals("Familiar", SpacedRepetition.proficiencyLabel(3, DisplayLanguage.EN))
        // nextReviewAt = 4 天；now = 0 → daysUntil = 4
        assertEquals("4天后复习", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH, now = 0L))
        assertEquals("Review in 4 days", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.EN, now = 0L))
    }
```

（该文件顶部补 `import com.hstudy.app.i18n.DisplayLanguage`。）

- [ ] **Step 2: 跑测试确认红**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.MistakeCopyTest"`
Expected: 编译失败（`Unresolved reference 'MistakeStrings'` / `AppStrings.mistake` / `proficiencyTimes` 等）。

- [ ] **Step 3: 实现**

3a. 在 `Plurals.kt` **末尾追加**（不改任何现有行）：

```kotlin

/**
 * 英文 one/other 窄函数（同 [pointsWord] 模式，spec §1：「后续页面遇到别的名词时在本文件再加
 * 一个同样窄的函数，不要写成泛化模板」）：
 * - [timesWord]：复习次数（Reviewed 1 time / 2 times）；
 * - [daysWord]：天（1 day / 2 days）；
 * - [mistakesWord]：错题（1 mistake / 2 mistakes）。
 */
internal fun timesWord(times: Int): String = if (times == 1) "time" else "times"

internal fun daysWord(days: Int): String = if (days == 1) "day" else "days"

internal fun mistakesWord(mistakes: Int): String = if (mistakes == 1) "mistake" else "mistakes"
```

3b. 创建 `MistakeStrings.kt`（完整内容）：

```kotlin
package com.hstudy.app.i18n

/**
 * 错题域共享词汇表（阶段 2 W1）：跨 4 个页面 + SpacedRepetition 的同一批中文值只在这里有一份。
 *
 * 为什么要有共享表（同 CommonStrings 的上收先例）：科目名在列表/详情/新增/家长分析四处出现，
 * 若各页各译一份，"其他"就会与 `PresetRegistry` 的 `preset("其他", "Other")` 以及各页面
 * 各自漂移；上收后中文值逐字节不变、英文只有一个出处，`MistakeCopyTest.crossTableSharedLabelsPinned`
 * 再把它与注册表/其它表钉成同值。
 *
 * 两类科目标签是**两个形状，不是同形异义**（都保留）：
 * - [subjectMath]…[subjectOther]：裸标签，详情页编辑芯片 / 列表筛选与卡片 / 家长分析用；
 * - [subjectMathChip]…[subjectOtherChip]：带 emoji 的芯片标签（"🔢 数学"），只有新增页用；
 *   页面里不再拼 "🔢" + 文本（拼接会留下页侧单边字面量，等价闸门按折叠后模板判等）。
 *
 * 熟练度/下次复习 8 条由 [com.hstudy.app.ui.mistake.SpacedRepetition] 消费：
 * 间隔算法留在 SpacedRepetition（纯域逻辑），文案在这里（测试可独立钉死两种语言）。
 * 英文用 timesWord/daysWord 做 one/other；中文无单复数，直接插数值。
 * 术语：错题 = Mistake(s)（§13）、复习 = Review、掌握 = Mastered。
 */
data class MistakeStrings(
    val subjectMath: String,
    val subjectChinese: String,
    val subjectEnglish: String,
    val subjectScience: String,
    val subjectOther: String,
    val subjectMathChip: String,
    val subjectChineseChip: String,
    val subjectEnglishChip: String,
    val subjectScienceChip: String,
    val subjectOtherChip: String,
    val masteredStatus: String,
    val unmasteredStatus: String,
    val photoContentDescription: String,
    val proficiencyNone: String,
    val proficiencyTimes: (reviewCount: Int) -> String,
    val proficiencyFair: String,
    val proficiencyStrong: String,
    val nextReviewMastered: String,
    val nextReviewToday: String,
    val nextReviewTomorrow: String,
    val nextReviewInDays: (days: Int) -> String,
)

val MistakeStringsZh = MistakeStrings(
    subjectMath = "数学",
    subjectChinese = "语文",
    subjectEnglish = "英语",
    subjectScience = "科学",
    subjectOther = "其他",
    subjectMathChip = "🔢 数学",
    subjectChineseChip = "📝 语文",
    subjectEnglishChip = "🔤 英语",
    subjectScienceChip = "🔬 科学",
    subjectOtherChip = "📋 其他",
    masteredStatus = "✅ 已掌握",
    unmasteredStatus = "⏳ 未掌握",
    photoContentDescription = "错题照片",
    proficiencyNone = "未复习",
    proficiencyTimes = { reviewCount -> "复习${reviewCount}次" },
    proficiencyFair = "较熟练",
    proficiencyStrong = "已巩固",
    nextReviewMastered = "已掌握",
    nextReviewToday = "今天复习",
    nextReviewTomorrow = "明天复习",
    nextReviewInDays = { days -> "${days}天后复习" },
)

val MistakeStringsEn = MistakeStrings(
    subjectMath = "Math",
    subjectChinese = "Chinese",
    subjectEnglish = "English",
    subjectScience = "Science",
    subjectOther = "Other",
    subjectMathChip = "🔢 Math",
    subjectChineseChip = "📝 Chinese",
    subjectEnglishChip = "🔤 English",
    subjectScienceChip = "🔬 Science",
    subjectOtherChip = "📋 Other",
    masteredStatus = "✅ Mastered",
    unmasteredStatus = "⏳ Not mastered",
    photoContentDescription = "Mistake photo",
    proficiencyNone = "Not reviewed",
    proficiencyTimes = { reviewCount -> "Reviewed $reviewCount ${timesWord(reviewCount)}" },
    proficiencyFair = "Familiar",
    proficiencyStrong = "Solid",
    nextReviewMastered = "Mastered",
    nextReviewToday = "Review today",
    nextReviewTomorrow = "Review tomorrow",
    nextReviewInDays = { days -> "Review in $days days" },
)
```

3c. `Strings.kt`：`AppStrings` 加字段 `val mistake: MistakeStrings,`（放在 `val common` 之后），`AppStringsZh`/`AppStringsEn` 各加一行 `mistake = MistakeStringsZh,` / `mistake = MistakeStringsEn,`。

3d. `SpacedRepetition.kt`：只改两个标签函数（其余逐字节不动）：

```kotlin
    /** 复习熟练度描述（复习次数越多越熟练）；文案来自共享表，随生效语言。 */
    fun proficiencyLabel(reviewCount: Int, lang: DisplayLanguage): String {
        val strings = stringsFor(lang).mistake
        return when {
            reviewCount == 0 -> strings.proficiencyNone
            reviewCount < 3 -> strings.proficiencyTimes(reviewCount)
            reviewCount < 5 -> strings.proficiencyFair
            else -> strings.proficiencyStrong
        }
    }

    /** 下次复习的友好描述（如“今天复习”/“明天复习”/“3天后复习”，让间隔复习计划透明可见）。 */
    fun nextReviewLabel(mistake: MistakeEntity, lang: DisplayLanguage, now: Long = com.hstudy.app.ui.dt.nowMillis()): String {
        val strings = stringsFor(lang).mistake
        if (mistake.isMastered) return strings.nextReviewMastered
        val nextAt = nextReviewAt(mistake)
        if (now >= nextAt) return strings.nextReviewToday
        val daysUntil = kotlin.math.ceil((nextAt - now).toDouble() / DAY_MS).toInt()
        return if (daysUntil <= 1) strings.nextReviewTomorrow else strings.nextReviewInDays(daysUntil)
    }
```

顶部补 import：`import com.hstudy.app.i18n.DisplayLanguage`、`import com.hstudy.app.i18n.stringsFor`。

3e. `SpacedRepetitionTest.kt`：现有两条标签测试改为传语言（断言值不变）：

```kotlin
    @Test
    fun proficiencyLabel_returns_correct_labels() {
        assertEquals("未复习", SpacedRepetition.proficiencyLabel(0, DisplayLanguage.ZH))
        assertEquals("复习1次", SpacedRepetition.proficiencyLabel(1, DisplayLanguage.ZH))
        assertEquals("复习2次", SpacedRepetition.proficiencyLabel(2, DisplayLanguage.ZH))
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(3, DisplayLanguage.ZH))
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(4, DisplayLanguage.ZH))
        assertEquals("已巩固", SpacedRepetition.proficiencyLabel(5, DisplayLanguage.ZH))
        assertEquals("已巩固", SpacedRepetition.proficiencyLabel(100, DisplayLanguage.ZH))
    }
```

`nextReviewLabel` 的 4 条测试同样在参数里补 `DisplayLanguage.ZH`（例如 `SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH)`、`SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH, now = …)`）。

- [ ] **Step 4: 跑测试确认绿**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.MistakeCopyTest" --tests "com.hstudy.app.ui.mistake.SpacedRepetitionTest"`
Expected: 14 条通过（MistakeCopyTest 2 + SpacedRepetitionTest 12），0 failures。

---

### Task 2: MistakeListStrings + 错题列表页迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeListStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（加 1 字段 + 2 行）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`（追加 2 条）

**Interfaces:**
- Consumes: Task 1 的 `MistakeStrings`、`SpacedRepetition.*(…, lang)`。
- Produces: `data class MistakeListStrings(...)`（43 字段）、`AppStrings.mistakeList`。Task 6 的闸门登记依赖文件名 `MistakeListStrings.kt` 与对象名 `MistakeListStringsZh`。

- [ ] **Step 1: 追加测试（对不存在的表 = 编译红）**

在 `MistakeCopyTest` 类体内追加：

```kotlin
    @Test
    fun mistakeListCopyDiffers() {
        val zh = AppStringsZh.mistakeList
        val en = AppStringsEn.mistakeList
        assertDiffers(
            zh.title to en.title,
            zh.addContentDescription to en.addContentDescription,
            zh.streakEncouragementHigh to en.streakEncouragementHigh,
            zh.streakEncouragementMid to en.streakEncouragementMid,
            zh.streakEncouragementStart to en.streakEncouragementStart,
            zh.streakEncouragementNone to en.streakEncouragementNone,
            zh.calendarTitle to en.calendarTitle,
            zh.calendarHint to en.calendarHint,
            zh.milestoneName5 to en.milestoneName5,
            zh.milestoneName15 to en.milestoneName15,
            zh.milestoneName30 to en.milestoneName30,
            zh.milestoneName50 to en.milestoneName50,
            zh.milestoneName100 to en.milestoneName100,
            zh.masteryProgressTitle to en.masteryProgressTitle,
            zh.masteryAllUnlocked to en.masteryAllUnlocked,
            zh.forecastTitle to en.forecastTitle,
            zh.dueBannerHint to en.dueBannerHint,
            zh.reminderTitle to en.reminderTitle,
            zh.reminderHint to en.reminderHint,
            zh.reminderOffMessage to en.reminderOffMessage,
            zh.reminderTimeLabel to en.reminderTimeLabel,
            zh.searchPlaceholder to en.searchPlaceholder,
            zh.clearSearchContentDescription to en.clearSearchContentDescription,
            zh.filterAll to en.filterAll,
            zh.filterUnmasteredOnly to en.filterUnmasteredOnly,
            zh.emptyState to en.emptyState,
            zh.dueBadge to en.dueBadge,
            zh.reviewButton to en.reviewButton,
            zh.markMasteredContentDescription to en.markMasteredContentDescription,
        )
        // 列表型字段：逐项钉死（assertDiffers 看不到 List 内部）
        assertEquals(listOf("今天", "明天", "后天", "3天后", "4天后", "5天后", "6天后"), zh.forecastLabels)
        assertEquals(listOf("Today", "Tomorrow", "In 2 days", "In 3 days", "In 4 days", "In 5 days", "In 6 days"), en.forecastLabels)
    }

    @Test
    fun mistakeListInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.mistakeList
        val en = AppStringsEn.mistakeList
        assertEquals("已掌握 7", zh.masteredBadge(7))
        assertEquals("Mastered: 7", en.masteredBadge(7))
        assertEquals("📖 复习完成！+3分已提交家长审核 明天复习", zh.reviewSuccessMessage("明天复习"))
        assertEquals("📖 Review complete! +3 points submitted for parent approval. Review tomorrow", en.reviewSuccessMessage("Review tomorrow"))
        assertEquals("连续复习 6 天", zh.streakTitle(6))
        assertEquals("Review streak: 6 days", en.streakTitle(6))
        assertEquals("Review streak: 1 day", en.streakTitle(1))
        assertEquals("🎉 连续复习3天达成！奖励 +3 分！", zh.milestoneClaimedMessage(3, 3))
        assertEquals("🎉 3-day review streak reached! Reward: +3 points!", en.milestoneClaimedMessage(3, 3))
        assertEquals("🎁 领取连续复习3天奖励 +3分", zh.milestoneClaimButton(3, 3))
        assertEquals("🎁 Claim 3-day streak reward +3 points", en.milestoneClaimButton(3, 3))
        assertEquals("近28天复习 5 天", zh.calendarSummary(5))
        assertEquals("Reviewed 5 days in the last 28 days", en.calendarSummary(5))
        assertEquals("再掌握 3 道，即可解锁「错题新手」里程碑！", zh.masteryNextHint(3, zh.milestoneName5))
        assertEquals("Master 3 more to unlock the “Mistake Rookie” milestone!", en.masteryNextHint(3, en.milestoneName5))
        assertEquals("重点攻克薄弱科目：数学", zh.weakSubjectTitle("数学"))
        assertEquals("Focus on your weakest subject: Math", en.weakSubjectTitle("Math"))
        assertEquals("点击筛选数学未掌握错题，针对性突破", zh.weakSubjectHint("数学"))
        assertEquals("Tap to filter unmastered Math mistakes and target your weak spots", en.weakSubjectHint("Math"))
        assertEquals("今日有 4 道错题待复习", zh.dueBannerTitle(4))
        assertEquals("Due for review today: 4", en.dueBannerTitle(4))
        assertEquals("✅ 已开启每日19点复习提醒", zh.reminderOnMessage(19))
        assertEquals("✅ Daily review reminder on at 19:00", en.reminderOnMessage(19))
        assertEquals("19点", zh.reminderHourChip(19))
        assertEquals("19:00", en.reminderHourChip(19))
        assertEquals("今日待复习 4", zh.filterDue(4))
        assertEquals("Due today 4", en.filterDue(4))
        assertEquals("今日待复习", zh.filterDue(0))
        assertEquals("Due today", en.filterDue(0))
    }
```

**注意：** `zh.weakSubjectTitle("数学")` 直接传字面量即可（不要给表加测试辅助方法）。

- [ ] **Step 2: 跑测试确认红**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.MistakeCopyTest"`
Expected: 编译失败（`Unresolved reference 'mistakeList'`）。

- [ ] **Step 3: 实现**

3a. 创建 `MistakeListStrings.kt`（完整内容）：

```kotlin
package com.hstudy.app.i18n

/**
 * 孩子错题列表页（ui/mistake/ChildMistakeListScreen.kt）文案表（阶段 2 W1）。
 *
 * 来源：迁移前（`4d28c8e`）该文件的状态卡/筛选栏/卡片/提醒开关全部 UI 字面量。
 * **不迁**三条落库原文（Ruling 25）：`间隔复习`（reviewMistake note）、
 * `复习错题：${title}`（submitApproval description）、`连续复习${days}天里程碑奖励`（adjustPoints note）。
 *
 * 字段要点：
 * - [filterAll] 与 [TaskListStringsZh.filterAll] 是同一个中文值「全部」，英文都必须是 "All"，
 *   由 `MistakeCopyTest.crossTableSharedLabelsPinned` 钉住（Task 6 追加）；
 * - [forecastLabels] 是列表字段：等价闸门逐字面量抽取，无需拆成 7 个字段；
 * - 里程碑名与成就注册表（W0）命名对齐：克星 = Mistake Buster、大师 = Mistake Master；
 * - 英文本波统一用 [pointsWord]/[daysWord] 处理 one/other。
 */
data class MistakeListStrings(
    val title: String,
    val masteredBadge: (count: Int) -> String,
    val addContentDescription: String,
    val reviewSuccessMessage: (nextReviewLabel: String) -> String,
    val streakTitle: (days: Int) -> String,
    val streakEncouragementHigh: String,
    val streakEncouragementMid: String,
    val streakEncouragementStart: String,
    val streakEncouragementNone: String,
    val milestoneClaimedMessage: (days: Int, bonus: Int) -> String,
    val milestoneClaimButton: (days: Int, bonus: Int) -> String,
    val calendarTitle: String,
    val calendarSummary: (reviewedDays: Int) -> String,
    val calendarHint: String,
    val milestoneName5: String,
    val milestoneName15: String,
    val milestoneName30: String,
    val milestoneName50: String,
    val milestoneName100: String,
    val masteryProgressTitle: String,
    val masteryNextHint: (need: Int, milestoneName: String) -> String,
    val masteryAllUnlocked: String,
    val forecastTitle: String,
    val forecastLabels: List<String>,
    val weakSubjectTitle: (subjectName: String) -> String,
    val weakSubjectHint: (subjectName: String) -> String,
    val dueBannerTitle: (dueCount: Int) -> String,
    val dueBannerHint: String,
    val reminderTitle: String,
    val reminderHint: String,
    val reminderOnMessage: (hour: Int) -> String,
    val reminderOffMessage: String,
    val reminderTimeLabel: String,
    val reminderHourChip: (hour: Int) -> String,
    val searchPlaceholder: String,
    val clearSearchContentDescription: String,
    val filterAll: String,
    val filterUnmasteredOnly: String,
    val filterDue: (dueCount: Int) -> String,
    val emptyState: String,
    val dueBadge: String,
    val reviewButton: String,
    val markMasteredContentDescription: String,
)

val MistakeListStringsZh = MistakeListStrings(
    title = "📚 错题本",
    masteredBadge = { count -> "已掌握 $count" },
    addContentDescription = "添加错题",
    reviewSuccessMessage = { nextReviewLabel -> "📖 复习完成！+3分已提交家长审核 $nextReviewLabel" },
    streakTitle = { days -> "连续复习 $days 天" },
    streakEncouragementHigh = "太棒了！坚持复习一周以上，记忆超牢固！",
    streakEncouragementMid = "坚持得不错！每天复习，遗忘远离你！",
    streakEncouragementStart = "好的开始！今天也来复习巩固一下吧！",
    streakEncouragementNone = "今天复习错题，开启连续复习 streak 吧！",
    milestoneClaimedMessage = { days, bonus -> "🎉 连续复习${days}天达成！奖励 +${bonus} 分！" },
    milestoneClaimButton = { days, bonus -> "🎁 领取连续复习${days}天奖励 +${bonus}分" },
    calendarTitle = "📅 复习打卡日历",
    calendarSummary = { reviewedDays -> "近28天复习 $reviewedDays 天" },
    calendarHint = "坚持每天复习，让格子都亮起来！",
    milestoneName5 = "错题新手",
    milestoneName15 = "错题进阶",
    milestoneName30 = "错题熟练",
    milestoneName50 = "错题克星",
    milestoneName100 = "错题大师",
    masteryProgressTitle = "🏆 错题清零进度",
    masteryNextHint = { need, milestoneName -> "再掌握 $need 道，即可解锁「$milestoneName」里程碑！" },
    masteryAllUnlocked = "🎉 已解锁全部里程碑，你是真正的错题大师！",
    forecastTitle = "📆 未来7天复习计划",
    forecastLabels = listOf("今天", "明天", "后天", "3天后", "4天后", "5天后", "6天后"),
    weakSubjectTitle = { subjectName -> "重点攻克薄弱科目：$subjectName" },
    weakSubjectHint = { subjectName -> "点击筛选${subjectName}未掌握错题，针对性突破" },
    dueBannerTitle = { dueCount -> "今日有 $dueCount 道错题待复习" },
    dueBannerHint = "按遗忘曲线及时复习，记忆更牢固，点击查看",
    reminderTitle = "每日复习提醒",
    reminderHint = "每天定时提醒你复习错题，不忘复习",
    reminderOnMessage = { hour -> "✅ 已开启每日${hour}点复习提醒" },
    reminderOffMessage = "已关闭复习提醒",
    reminderTimeLabel = "提醒时间",
    reminderHourChip = { hour -> "${hour}点" },
    searchPlaceholder = "搜索错题（题目/错因/解法）",
    clearSearchContentDescription = "清空",
    filterAll = "全部",
    filterUnmasteredOnly = "仅未掌握",
    filterDue = { dueCount -> "今日待复习${if (dueCount > 0) " $dueCount" else ""}" },
    emptyState = "还没有错题记录，点击下方 + 添加",
    dueBadge = "🔔待复习",
    reviewButton = "复习+3",
    markMasteredContentDescription = "标记掌握",
)

val MistakeListStringsEn = MistakeListStrings(
    title = "📚 Mistakes",
    masteredBadge = { count -> "Mastered: $count" },
    addContentDescription = "Add mistake",
    reviewSuccessMessage = { nextReviewLabel ->
        "📖 Review complete! +3 points submitted for parent approval. $nextReviewLabel"
    },
    streakTitle = { days -> "Review streak: $days ${daysWord(days)}" },
    streakEncouragementHigh = "Amazing! Over a week of daily review — your memory is getting rock solid!",
    streakEncouragementMid = "Nice going! Review every day and forgetfulness stays away!",
    streakEncouragementStart = "A good start! Review again today to reinforce what you know!",
    streakEncouragementNone = "Review a mistake today to start your review streak!",
    milestoneClaimedMessage = { days, bonus ->
        "🎉 $days-day review streak reached! Reward: +$bonus ${pointsWord(bonus)}!"
    },
    milestoneClaimButton = { days, bonus ->
        "🎁 Claim $days-day streak reward +$bonus ${pointsWord(bonus)}"
    },
    calendarTitle = "📅 Review calendar",
    calendarSummary = { reviewedDays -> "Reviewed $reviewedDays ${daysWord(reviewedDays)} in the last 28 days" },
    calendarHint = "Review every day and light up every square!",
    milestoneName5 = "Mistake Rookie",
    milestoneName15 = "Mistake Improver",
    milestoneName30 = "Mistake Skilled",
    milestoneName50 = "Mistake Buster",
    milestoneName100 = "Mistake Master",
    masteryProgressTitle = "🏆 Mistake-clearing progress",
    masteryNextHint = { need, milestoneName ->
        "Master $need more to unlock the “$milestoneName” milestone!"
    },
    masteryAllUnlocked = "🎉 All milestones unlocked — you are a true Mistake Master!",
    forecastTitle = "📆 7-day review plan",
    forecastLabels = listOf("Today", "Tomorrow", "In 2 days", "In 3 days", "In 4 days", "In 5 days", "In 6 days"),
    weakSubjectTitle = { subjectName -> "Focus on your weakest subject: $subjectName" },
    weakSubjectHint = { subjectName ->
        "Tap to filter unmastered $subjectName mistakes and target your weak spots"
    },
    dueBannerTitle = { dueCount -> "Due for review today: $dueCount" },
    dueBannerHint = "Review on the forgetting curve to remember longer — tap to view",
    reminderTitle = "Daily review reminder",
    reminderHint = "A daily reminder so review never slips your mind",
    reminderOnMessage = { hour -> "✅ Daily review reminder on at $hour:00" },
    reminderOffMessage = "Review reminder turned off",
    reminderTimeLabel = "Reminder time",
    reminderHourChip = { hour -> "$hour:00" },
    searchPlaceholder = "Search mistakes (title / reason / solution)",
    clearSearchContentDescription = "Clear",
    filterAll = "All",
    filterUnmasteredOnly = "Unmastered only",
    filterDue = { dueCount -> "Due today${if (dueCount > 0) " $dueCount" else ""}" },
    emptyState = "No mistakes yet — tap + below to add one",
    dueBadge = "🔔 Due",
    reviewButton = "Review +3",
    markMasteredContentDescription = "Mark as mastered",
)
```

3b. `Strings.kt`：`AppStrings` 加 `val mistakeList: MistakeListStrings,`；两份聚合各加 `mistakeList = MistakeListStringsZh,` / `mistakeList = MistakeListStringsEn,`。

3c. 迁移 `ChildMistakeListScreen.kt`（逐处替换；`val strings = LocalStrings.current.mistakeList` / `val shared = LocalStrings.current.mistake` / `val displayLanguage = LocalDisplayLanguage.current` 分别放在外层 composable、`StatusCardsPanel`、`FilterBar`、`MistakeListContent`/`MistakeCard` 内）：

| 原名面量（行号为迁移前） | 替换 |
|---|---|
| `title = "📚 错题本"`（:146） | `title = strings.title` |
| `Text("已掌握 ${masteredCount}", …)`（:149） | `Text(strings.masteredBadge(masteredCount), …)` |
| `contentDescription = "添加错题"`（:158） | `contentDescription = strings.addContentDescription` |
| `showSnackbar("📖 复习完成！+3分已提交家长审核 $nextLabel")`（:137） | `showSnackbar(strings.reviewSuccessMessage(nextLabel))` |
| `Text("连续复习 $reviewStreak 天", …)`（:255） | `Text(strings.streakTitle(reviewStreak), …)` |
| 四条 streak 文案（:259-262） | `strings.streakEncouragementHigh/Mid/Start/None` |
| `Text("🎉 连续复习${days}天达成！奖励 +${bonus} 分！")`（:277 内 showSnackbar） | `showSnackbar(strings.milestoneClaimedMessage(days, bonus))` |
| `Text("🎁 领取连续复习${days}天奖励 +${bonus}分")`（:281） | `Text(strings.milestoneClaimButton(days, bonus))` |
| `Text("📅 复习打卡日历", …)`（:296） | `Text(strings.calendarTitle, …)` |
| `Text("近28天复习 $reviewedDays 天", …)`（:298） | `Text(strings.calendarSummary(reviewedDays), …)` |
| `Text("坚持每天复习，让格子都亮起来！", …)`（:320） | `Text(strings.calendarHint, …)` |
| `listOf(5 to "错题新手", 15 to "错题进阶", 30 to "错题熟练", 50 to "错题克星", 100 to "错题大师")`（:328） | `listOf(5 to strings.milestoneName5, 15 to strings.milestoneName15, 30 to strings.milestoneName30, 50 to strings.milestoneName50, 100 to strings.milestoneName100)` |
| `Text("🏆 错题清零进度", …)`（:339） | `Text(strings.masteryProgressTitle, …)` |
| `Text("再掌握 $need 道，即可解锁「${nextMilestone.second}」里程碑！", …)`（:351） | `Text(strings.masteryNextHint(need, nextMilestone.second), …)` |
| `Text("🎉 已解锁全部里程碑，你是真正的错题大师！", …)`（:354） | `Text(strings.masteryAllUnlocked, …)` |
| `Text("📆 未来7天复习计划", …)`（:370） | `Text(strings.forecastTitle, …)` |
| `val forecastLabels = listOf("今天", …)`（:364） | `val forecastLabels = strings.forecastLabels` |
| `when (weakestSubject) { … "数学" … "其他" }`（:394-396） | `when (weakestSubject) { MistakeSubject.MATH -> shared.subjectMath; …; else -> shared.subjectOther }` |
| `Text("重点攻克薄弱科目：$weakName", …)`（:407） | `Text(strings.weakSubjectTitle(weakName), …)` |
| `Text("点击筛选${weakName}未掌握错题，针对性突破", …)`（:409） | `Text(strings.weakSubjectHint(weakName), …)` |
| `Text("今日有 ${dueForReview.size} 道错题待复习", …)`（:430） | `Text(strings.dueBannerTitle(dueForReview.size), …)` |
| `Text("按遗忘曲线及时复习，记忆更牢固，点击查看", …)`（:431） | `Text(strings.dueBannerHint, …)` |
| `Text("每日复习提醒", …)`（:449） | `Text(strings.reminderTitle, …)` |
| `Text("每天定时提醒你复习错题，不忘复习", …)`（:450） | `Text(strings.reminderHint, …)` |
| `showSnackbar("✅ 已开启每日${reviewReminderHour}点复习提醒")`（:460） | `showSnackbar(strings.reminderOnMessage(reviewReminderHour))` |
| `showSnackbar("已关闭复习提醒")`（:463） | `showSnackbar(strings.reminderOffMessage)` |
| `Text("提醒时间", …)`（:470） | `Text(strings.reminderTimeLabel, …)` |
| `label = { Text("${h}点") }`（:481） | `label = { Text(strings.reminderHourChip(h)) }` |
| `placeholder = { Text("搜索错题（题目/错因/解法）") }`（:507） | `placeholder = { Text(strings.searchPlaceholder) }` |
| `contentDescription = "清空"`（:512） | `contentDescription = strings.clearSearchContentDescription` |
| `Text("全部")`（:522） | `Text(strings.filterAll)` |
| `Text("数学") / Text("语文") / Text("英语")`（:523-525） | `Text(shared.subjectMath)` / `shared.subjectChinese` / `shared.subjectEnglish` |
| `Text("仅未掌握")`（:531） | `Text(strings.filterUnmasteredOnly)` |
| `Text("今日待复习${if (dueCount > 0) " $dueCount" else ""}")`（:538） | `Text(strings.filterDue(dueCount))` |
| `Text("还没有错题记录，点击下方 + 添加", …)`（:557） | `Text(strings.emptyState, …)` |
| `Text("🔔待复习", …)`（:629） | `Text(strings.dueBadge, …)` |
| `Text("复习+3", …)`（:661） | `Text(strings.reviewButton, …)` |
| `contentDescription = "标记掌握"`（:669） | `contentDescription = strings.markMasteredContentDescription` |
| `val subjectName = when (mistake.subject) { … "数学" … }`（:601-607） | 映射到 `shared.subjectMath/subjectChinese/subjectEnglish/subjectScience/subjectOther` |
| `SpacedRepetition.proficiencyLabel(mistake.reviewCount)`（:636） | `SpacedRepetition.proficiencyLabel(mistake.reviewCount, displayLanguage)`（`MistakeCard` 内读 `LocalDisplayLanguage.current`） |
| `SpacedRepetition.nextReviewLabel(mistake)`（:641） | `SpacedRepetition.nextReviewLabel(mistake, displayLanguage)` |
| `SpacedRepetition.nextReviewLabel(it)`（:136） | `SpacedRepetition.nextReviewLabel(it, displayLanguage)` |

顶部补 import：`com.hstudy.app.i18n.DisplayLanguage`（仅当需要显式类型）、`com.hstudy.app.i18n.LocalDisplayLanguage`、`com.hstudy.app.i18n.LocalStrings`。

验证（本任务零 CJK 检查）：
`grep -n "[一-鿿]" composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt`
Expected: 仅剩 3 条落库原文：`间隔复习`、`复习错题：…`、`连续复习${days}天里程碑奖励`。

- [ ] **Step 4: 跑测试确认绿**

Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.MistakeCopyTest" --tests "com.hstudy.app.ui.mistake.SpacedRepetitionTest"`
Expected: 16 条全部通过。

---

### Task 3: MistakeDetailStrings + 错题详情页迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeDetailStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`（追加 2 条）

**Interfaces:**
- Consumes: `MistakeStrings`、`CommonStrings`（取消/删除复用）；`pointsWord`。
- Produces: `data class MistakeDetailStrings(...)`（41 字段）、`AppStrings.mistakeDetail`。

- [ ] **Step 1: 追加测试（编译红）**

在 `MistakeCopyTest` 追加：

```kotlin
    @Test
    fun mistakeDetailCopyDiffers() {
        val zh = AppStringsZh.mistakeDetail
        val en = AppStringsEn.mistakeDetail
        assertDiffers(
            zh.editContentDescription to en.editContentDescription,
            zh.editHeading to en.editHeading,
            zh.titleLabel to en.titleLabel,
            zh.contentLabel to en.contentLabel,
            zh.wrongReasonLabel to en.wrongReasonLabel,
            zh.correctMethodLabel to en.correctMethodLabel,
            zh.errorTypeLabel to en.errorTypeLabel,
            zh.updatedMessage to en.updatedMessage,
            zh.saveButton to en.saveButton,
            zh.contentHeading to en.contentHeading,
            zh.recallHeading to en.recallHeading,
            zh.recallHint to en.recallHint,
            zh.wrongReasonHeading to en.wrongReasonHeading,
            zh.correctMethodHeading to en.correctMethodHeading,
            zh.photoHeading to en.photoHeading,
            zh.masteredCard to en.masteredCard,
            zh.revealButton to en.revealButton,
            zh.selfCheckPrompt to en.selfCheckPrompt,
            zh.gotItButton to en.gotItButton,
            zh.missedButton to en.missedButton,
            zh.gotItMessage to en.gotItMessage,
            zh.missedMessage to en.missedMessage,
            zh.reviewDoneCard to en.reviewDoneCard,
            zh.variationTitle to en.variationTitle,
            zh.variationDone to en.variationDone,
            zh.variationThink to en.variationThink,
            zh.variationNotesHeading to en.variationNotesHeading,
            zh.variationInputLabel to en.variationInputLabel,
            zh.variationDoneMessage to en.variationDoneMessage,
            zh.variationButton to en.variationButton,
            zh.deleteDialogTitle to en.deleteDialogTitle,
        )
    }

    @Test
    fun mistakeDetailInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.mistakeDetail
        val en = AppStringsEn.mistakeDetail
        assertEquals("🔢 错题详情", zh.screenTitle("🔢"))
        assertEquals("🔢 Mistake Details", en.screenTitle("🔢"))
        assertEquals("添加于 2026-11-10", zh.addedOn("2026-11-10"))
        assertEquals("Added on 2026-11-10", en.addedOn("2026-11-10"))
        assertEquals("复习记录 (3)", zh.reviewHistoryTitle(3))
        assertEquals("Review history (3)", en.reviewHistoryTitle(3))
        assertEquals("11-10 +3分", zh.reviewHistoryLine("11-10", 3))
        assertEquals("11-10 +3 points", en.reviewHistoryLine("11-10", 3))
        assertEquals("11-10 +1分", zh.reviewHistoryLine("11-10", 1))
        assertEquals("11-10 +1 point", en.reviewHistoryLine("11-10", 1))
        assertEquals("确定要删除「分数计算」吗？删除后不可恢复。", zh.deleteDialogText("分数计算"))
        assertEquals("Delete “分数计算”? This cannot be undone.", en.deleteDialogText("分数计算"))
        // 15 条举一反三引导问题：逐表逐项钉死
        assertEquals(3, zh.variationPromptsMath.size)
        assertEquals(
            listOf("这道题考查的是什么知识点？", "改变数字或条件，你还会解吗？", "你能自己编一道类似的题吗？"),
            zh.variationPromptsMath,
        )
        assertEquals(
            listOf(
                "What concept does this question test?",
                "Can you solve it with different numbers or conditions?",
                "Can you make up a similar question yourself?",
            ),
            en.variationPromptsMath,
        )
        assertEquals(zh.variationPromptsChinese.size, en.variationPromptsChinese.size)
        assertEquals(zh.variationPromptsEnglish.size, en.variationPromptsEnglish.size)
        assertEquals(zh.variationPromptsScience.size, en.variationPromptsScience.size)
        assertEquals(zh.variationPromptsOther.size, en.variationPromptsOther.size)
        // 中文五组逐项钉死（英文同位置非空且与中文逐项不同）
        val zhAll = zh.variationPromptsChinese + zh.variationPromptsEnglish + zh.variationPromptsScience + zh.variationPromptsOther
        val enAll = en.variationPromptsChinese + en.variationPromptsEnglish + en.variationPromptsScience + en.variationPromptsOther
        assertEquals(12, zhAll.size)
        assertEquals(12, enAll.size)
        zhAll.zip(enAll).forEach { (z, e) ->
            assertNotEquals(z, e, "举一反三问题疑似漏译：$z")
        }
    }
```

顶部补 `import kotlin.test.assertNotEquals`。

- [ ] **Step 2: 跑测试确认红**

Expected: `Unresolved reference 'mistakeDetail'`。

- [ ] **Step 3: 实现**

3a. 创建 `MistakeDetailStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 错题详情页（ui/mistake/ChildMistakeDetailScreen.kt）文案表（阶段 2 W1）。
 *
 * 迁移前（`4d28c8e`）全部 UI 字面量入表；**四条落库原文不迁**（Ruling 25，见 Task 6 披露登记）：
 * `"主动回忆-做对"`（reviewMistake note）、`"错题复习做对: ${title}"` / `"错题复习: ${title}"`
 * （submitApproval description）、`"举一反三思考: ${title}"`（submitApproval description）。
 *
 * 形状要点：
 * - [screenTitle] 收 emoji（科目 emoji 由页面 `when` 求得），中文模板 `"$emoji 错题详情"` 的
 *   折叠形状与迁移前页面字面量一致（等价闸门按折叠模板判等）；
 * - [reviewHistoryLine] 的积分单位复用 [pointsWord]（英文 one/other）；
 * - 15 条举一反三引导问题按科目分组存 [variationPromptsMath]…[variationPromptsOther]，
 *   每组 3 条、顺序与页面一致（中文逐项钉死在 MistakeCopyTest）；
 * - 通用按钮词「取消」「删除」不在此表重复：页面复用 `LocalStrings.current.common`
 *   （`cancelButton` / `deleteContentDescription`，阶段 1 已上收）。
 * - `"✅ 已掌握"` / `"⏳ 未掌握"` / `"错题照片"` 复用共享表 [MistakeStrings]。
 */
data class MistakeDetailStrings(
    val screenTitle: (emoji: String) -> String,
    val editContentDescription: String,
    val editHeading: String,
    val titleLabel: String,
    val contentLabel: String,
    val wrongReasonLabel: String,
    val correctMethodLabel: String,
    val errorTypeLabel: String,
    val updatedMessage: String,
    val saveButton: String,
    val addedOn: (date: String) -> String,
    val contentHeading: String,
    val recallHeading: String,
    val recallHint: String,
    val wrongReasonHeading: String,
    val correctMethodHeading: String,
    val photoHeading: String,
    val masteredCard: String,
    val revealButton: String,
    val selfCheckPrompt: String,
    val gotItButton: String,
    val missedButton: String,
    val gotItMessage: String,
    val missedMessage: String,
    val reviewDoneCard: String,
    val variationTitle: String,
    val variationDone: String,
    val variationPromptsMath: List<String>,
    val variationPromptsChinese: List<String>,
    val variationPromptsEnglish: List<String>,
    val variationPromptsScience: List<String>,
    val variationPromptsOther: List<String>,
    val variationThink: String,
    val variationNotesHeading: String,
    val variationInputLabel: String,
    val variationDoneMessage: String,
    val variationButton: String,
    val reviewHistoryTitle: (count: Int) -> String,
    val reviewHistoryLine: (date: String, points: Int) -> String,
    val deleteDialogTitle: String,
    val deleteDialogText: (title: String) -> String,
)

val MistakeDetailStringsZh = MistakeDetailStrings(
    screenTitle = { emoji -> "$emoji 错题详情" },
    editContentDescription = "编辑",
    editHeading = "✏️ 编辑错题",
    titleLabel = "标题",
    contentLabel = "题目内容",
    wrongReasonLabel = "错误原因",
    correctMethodLabel = "正确解法",
    errorTypeLabel = "错误类型",
    updatedMessage = "✅ 错题已更新",
    saveButton = "保存修改",
    addedOn = { date -> "添加于 $date" },
    contentHeading = "📋 题目描述",
    recallHeading = "先自己回想一下这道题怎么做",
    recallHint = "主动回忆比直接看答案记得更牢！想好了再点击下方按钮核对。",
    wrongReasonHeading = "❌ 错误原因",
    correctMethodHeading = "✅ 正确解法",
    photoHeading = "📷 题目照片",
    masteredCard = "✅ 这道题已掌握，真棒！偶尔回顾一下保持记忆。",
    revealButton = "👀 我想好了，显示答案核对",
    selfCheckPrompt = "📝 对照答案，你这次做对了吗？",
    gotItButton = "✅ 做对了 (+5)",
    missedButton = "❌ 没记住 (+2)",
    gotItMessage = "🎉 做对了！+3分已提交家长审核",
    missedMessage = "💪 +1分已提交审核，多复习几次就记住了",
    reviewDoneCard = "✅ 本次复习完成！系统会按遗忘曲线自动安排下次复习时间。",
    variationTitle = "💡 举一反三",
    variationDone = "✅ 已完成",
    variationPromptsMath = listOf(
        "这道题考查的是什么知识点？",
        "改变数字或条件，你还会解吗？",
        "你能自己编一道类似的题吗？",
    ),
    variationPromptsChinese = listOf(
        "这道题涉及哪些字词/知识点？",
        "换个语境你还能用对吗？",
        "你能造一个类似的句子吗？",
    ),
    variationPromptsEnglish = listOf(
        "这道题的语法/单词重点是什么？",
        "换个时态/句型你还会吗？",
        "你能用这个知识点造个句吗？",
    ),
    variationPromptsScience = listOf(
        "这道题背后的科学原理是什么？",
        "改变实验条件会怎样？",
        "生活中还有哪些类似现象？",
    ),
    variationPromptsOther = listOf(
        "这道题的核心要点是什么？",
        "换个角度你会怎么思考？",
        "你能举一个类似的例子吗？",
    ),
    variationThink = "🤔 试着想一想：",
    variationNotesHeading = "📝 我的举一反三笔记：",
    variationInputLabel = "写下你的思考/变式题（可选）",
    variationDoneMessage = "💡 举一反三完成 +3分已提交家长审核",
    variationButton = "💡 完成举一反三思考 (+3分)",
    reviewHistoryTitle = { count -> "复习记录 ($count)" },
    reviewHistoryLine = { date, points -> "$date +${points}分" },
    deleteDialogTitle = "删除错题",
    deleteDialogText = { title -> "确定要删除「$title」吗？删除后不可恢复。" },
)

val MistakeDetailStringsEn = MistakeDetailStrings(
    screenTitle = { emoji -> "$emoji Mistake Details" },
    editContentDescription = "Edit",
    editHeading = "✏️ Edit mistake",
    titleLabel = "Title",
    contentLabel = "Question",
    wrongReasonLabel = "What went wrong",
    correctMethodLabel = "Correct solution",
    errorTypeLabel = "Error type",
    updatedMessage = "✅ Mistake updated",
    saveButton = "Save changes",
    addedOn = { date -> "Added on $date" },
    contentHeading = "📋 Question",
    recallHeading = "Try to recall how to solve this first",
    recallHint = "Active recall beats re-reading — you'll remember far better. When you're ready, tap below to check your answer.",
    wrongReasonHeading = "❌ What went wrong",
    correctMethodHeading = "✅ Correct solution",
    photoHeading = "📷 Photo of the question",
    masteredCard = "✅ You've mastered this one — great job! Review it now and then to keep it fresh.",
    revealButton = "👀 I'm ready — show the answer",
    selfCheckPrompt = "📝 Check your answer — did you get it right?",
    gotItButton = "✅ Got it right (+5)",
    missedButton = "❌ Didn't remember (+2)",
    gotItMessage = "🎉 Right! +3 points submitted for parent approval",
    missedMessage = "💪 +1 point submitted for approval — a few more reviews and you'll remember it",
    reviewDoneCard = "✅ Review complete! The next review is scheduled on the forgetting curve.",
    variationTitle = "💡 Variation",
    variationDone = "✅ Done",
    variationPromptsMath = listOf(
        "What concept does this question test?",
        "Can you solve it with different numbers or conditions?",
        "Can you make up a similar question yourself?",
    ),
    variationPromptsChinese = listOf(
        "Which words or concepts does this question involve?",
        "Can you use them correctly in a different context?",
        "Can you write a similar sentence?",
    ),
    variationPromptsEnglish = listOf(
        "What grammar or vocabulary point does this question test?",
        "Can you handle it in a different tense or sentence pattern?",
        "Can you make a sentence with this point?",
    ),
    variationPromptsScience = listOf(
        "What science principle is behind this question?",
        "What if the experiment conditions changed?",
        "Where else in daily life do you see similar phenomena?",
    ),
    variationPromptsOther = listOf(
        "What is the core idea of this question?",
        "How would you think about it from another angle?",
        "Can you give a similar example?",
    ),
    variationThink = "🤔 Think about it:",
    variationNotesHeading = "📝 My variation notes:",
    variationInputLabel = "Write your thoughts or a variation question (optional)",
    variationDoneMessage = "💡 Variation complete — +3 points submitted for parent approval",
    variationButton = "💡 Complete variation thinking (+3 points)",
    reviewHistoryTitle = { count -> "Review history ($count)" },
    reviewHistoryLine = { date, points -> "$date +$points ${pointsWord(points)}" },
    deleteDialogTitle = "Delete mistake",
    deleteDialogText = { title -> "Delete “$title”? This cannot be undone." },
)
```

3b. `Strings.kt` 加 `val mistakeDetail: MistakeDetailStrings,` + 两行聚合。

3c. 迁移 `ChildMistakeDetailScreen.kt`（外层 `val strings = LocalStrings.current.mistakeDetail`、`val shared = LocalStrings.current.mistake`、`val common = LocalStrings.current.common`）：

| 原名面量 | 替换 |
|---|---|
| `title = "$subjectEmoji 错题详情"`（:70） | `title = strings.screenTitle(subjectEmoji)` |
| `contentDescription = "编辑"`（:85） | `strings.editContentDescription` |
| `contentDescription = "删除"`（:97） | `common.deleteContentDescription` |
| `Text("✏️ 编辑错题", …)`（:110） | `Text(strings.editHeading, …)` |
| 编辑科目芯片五个裸标签（:115-117） | `shared.subjectMath/subjectChinese/subjectEnglish/subjectScience/subjectOther` |
| `label = { Text("标题") }`（:123） | `strings.titleLabel` |
| `label = { Text("题目内容") }`（:125） | `strings.contentLabel` |
| `label = { Text("错误原因") }`（:127） | `strings.wrongReasonLabel` |
| `label = { Text("正确解法") }`（:129） | `strings.correctMethodLabel` |
| `Text("错误类型", …)`（:131） | `Text(strings.errorTypeLabel, …)` |
| `showSnackbar("✅ 错题已更新")`（:149） | `showSnackbar(strings.updatedMessage)` |
| `Text("保存修改")`（:151） | `Text(strings.saveButton)` |
| `Text("取消")`（:152） | `Text(common.cancelButton)` |
| `Text(if (m.isMastered) "✅ 已掌握" else "⏳ 未掌握")`（:163） | `Text(if (m.isMastered) shared.masteredStatus else shared.unmasteredStatus)` |
| `Text("添加于 $dateStr", …)`（:167） | `Text(strings.addedOn(dateStr), …)` |
| `Text("📋 题目描述", …)`（:174） | `Text(strings.contentHeading, …)` |
| `Text("先自己回想一下这道题怎么做", …)`（:190） | `Text(strings.recallHeading, …)` |
| `Text("主动回忆比直接看答案记得更牢！想好了再点击下方按钮核对。", …)`（:191） | `Text(strings.recallHint, …)` |
| `Text("❌ 错误原因", …)`（:201） | `Text(strings.wrongReasonHeading, …)` |
| `Text("✅ 正确解法", …)`（:212） | `Text(strings.correctMethodHeading, …)` |
| `Text("📷 题目照片", …)`（:221） | `Text(strings.photoHeading, …)` |
| `contentDescription = "错题照片"`（:225） | `shared.photoContentDescription` |
| `Text("✅ 这道题已掌握，真棒！偶尔回顾一下保持记忆。", …)`（:241） | `Text(strings.masteredCard, …)` |
| `Text("👀 我想好了，显示答案核对")`（:250） | `Text(strings.revealButton)` |
| `Text("📝 对照答案，你这次做对了吗？", …)`（:254） | `Text(strings.selfCheckPrompt, …)` |
| `Text("✅ 做对了 (+5)")`（:268） | `Text(strings.gotItButton)` |
| `Text("❌ 没记住 (+2)")`（:279） | `Text(strings.missedButton)` |
| `showSnackbar("🎉 做对了！+3分已提交家长审核")`（:264） | `showSnackbar(strings.gotItMessage)` |
| `showSnackbar("💪 +1分已提交审核，多复习几次就记住了")`（:275） | `showSnackbar(strings.missedMessage)` |
| `Text("✅ 本次复习完成！系统会按遗忘曲线自动安排下次复习时间。", …)`（:288） | `Text(strings.reviewDoneCard, …)` |
| `Text("💡 举一反三", …)`（:304） | `Text(strings.variationTitle, …)` |
| `Text("✅ 已完成", …)`（:307） | `Text(strings.variationDone, …)` |
| `val prompts = when (m.subject) { … listOf(…) }`（:311-317） | `val prompts = when (m.subject) { MistakeSubject.MATH -> strings.variationPromptsMath; …; MistakeSubject.OTHER -> strings.variationPromptsOther }` |
| `Text("🤔 试着想一想：", …)`（:318） | `Text(strings.variationThink, …)` |
| `Text("📝 我的举一反三笔记：", …)`（:324） | `Text(strings.variationNotesHeading, …)` |
| `label = { Text("写下你的思考/变式题（可选）") }`（:332） | `strings.variationInputLabel` |
| `showSnackbar("💡 举一反三完成 +3分已提交家长审核")`（:341） | `showSnackbar(strings.variationDoneMessage)` |
| `Text("💡 完成举一反三思考 (+3分)")`（:345） | `Text(strings.variationButton)` |
| `Text("复习记录 (${reviews.size})", …)`（:352） | `Text(strings.reviewHistoryTitle(reviews.size), …)` |
| `Text("$dateStr +${review.earnedPoints}分", …)`（:361） | `Text(strings.reviewHistoryLine(dateStr, review.earnedPoints), …)` |
| `Text("删除错题")`（:375） | `Text(strings.deleteDialogTitle)` |
| `Text("确定要删除「${m.title}」吗？删除后不可恢复。")`（:376） | `Text(strings.deleteDialogText(m.title))` |
| `Text("删除")`（:387） | `Text(common.deleteContentDescription)` |
| `Text("取消")`（:389） | `Text(common.cancelButton)` |

**不动的四条落库原文**：:259 `"主动回忆-做对"`、:260 `"错题复习做对: ${m.title}"`、:273 `"错题复习: ${m.title}"`、:339 `"举一反三思考: ${m.title}"`。

验证：
`grep -n "[一-鿿]" composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt`
Expected: 恰好 4 行（上述落库原文）。

- [ ] **Step 4: 跑测试确认绿**

Expected: 18 条全绿（MistakeCopyTest 6 + SpacedRepetitionTest 12）。

---

### Task 4: MistakeAddStrings + ParentMistakeReviewStrings + 两页迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeAddStrings.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentMistakeReviewStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeAddScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ParentMistakeReviewScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`（追加 2 条）

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun mistakeAddCopyDiffers() {
        val zh = AppStringsZh.mistakeAdd
        val en = AppStringsEn.mistakeAdd
        assertDiffers(
            zh.title to en.title,
            zh.subjectLabel to en.subjectLabel,
            zh.titleLabel to en.titleLabel,
            zh.contentLabel to en.contentLabel,
            zh.wrongReasonLabel to en.wrongReasonLabel,
            zh.errorTypeHint to en.errorTypeHint,
            zh.correctMethodLabel to en.correctMethodLabel,
            zh.takePhotoButton to en.takePhotoButton,
            zh.photoTaken to en.photoTaken,
            zh.missingTitleMessage to en.missingTitleMessage,
            zh.saveButton to en.saveButton,
        )
    }

    @Test
    fun parentMistakeReviewCopyDiffers() {
        val zh = AppStringsZh.parentMistakeReview
        val en = AppStringsEn.parentMistakeReview
        assertDiffers(
            zh.title to en.title,
            zh.overviewTitle to en.overviewTitle,
            zh.statTotal to en.statTotal,
            zh.statUnmastered to en.statUnmastered,
            zh.statMastered to en.statMastered,
            zh.subjectDistribution to en.subjectDistribution,
            zh.recentMistakes to en.recentMistakes,
        )
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'mistakeAdd'`）

- [ ] **Step 3: 实现**

3a. 创建 `MistakeAddStrings.kt`：11 个字段，值如下（ZH 逐字来自迁移前页面；EN 为译文）：

```kotlin
package com.hstudy.app.i18n

/**
 * 添加错题页（ui/mistake/ChildMistakeAddScreen.kt）文案表（阶段 2 W1）。
 * 科目芯片标签走共享表 [MistakeStrings]（带 emoji 的 chip 形状），本表不重复；
 * 错误类型芯片显示 canonical 原值（`mistakeErrorTypes`），本波不映射（W9 注册表接线）。
 */
data class MistakeAddStrings(
    val title: String,
    val subjectLabel: String,
    val titleLabel: String,
    val contentLabel: String,
    val wrongReasonLabel: String,
    val errorTypeHint: String,
    val correctMethodLabel: String,
    val takePhotoButton: String,
    val photoTaken: String,
    val missingTitleMessage: String,
    val saveButton: String,
)

val MistakeAddStringsZh = MistakeAddStrings(
    title = "➕ 添加错题",
    subjectLabel = "科目",
    titleLabel = "题目摘要",
    contentLabel = "题目详细描述",
    wrongReasonLabel = "❌ 错误原因分析",
    errorTypeHint = "错误类型（可选，用于错因分析）",
    correctMethodLabel = "✅ 正确解法",
    takePhotoButton = "拍照记录",
    photoTaken = "📷 已拍照",
    missingTitleMessage = "请输入题目摘要",
    saveButton = "保存错题",
)

val MistakeAddStringsEn = MistakeAddStrings(
    title = "➕ Add mistake",
    subjectLabel = "Subject",
    titleLabel = "Title",
    contentLabel = "Details",
    wrongReasonLabel = "❌ What went wrong",
    errorTypeHint = "Error type (optional, used for error analysis)",
    correctMethodLabel = "✅ Correct solution",
    takePhotoButton = "Take a photo",
    photoTaken = "📷 Photo taken",
    missingTitleMessage = "Please enter a title",
    saveButton = "Save mistake",
)
```

3b. 创建 `ParentMistakeReviewStrings.kt`：7 个字段（ZH：📊 错题分析 / 错题概览 / 总计 / 未掌握 / 已掌握 / 科目分布 / 最近错题；EN：📊 Mistake Analysis / Overview / Total / Unmastered / Mastered / By subject / Recent mistakes）。`statMastered`/`statUnmastered` 是**裸标签**（与共享表 `"✅ 已掌握"` 两个不同值，不得合并）。

3c. `Strings.kt` 加 `val mistakeAdd: MistakeAddStrings,`、`val parentMistakeReview: ParentMistakeReviewStrings,` + 四个聚合行。

3d. 迁移 `ChildMistakeAddScreen.kt`（`val strings = LocalStrings.current.mistakeAdd`、`val shared = LocalStrings.current.mistake`）：
- :52 `"➕ 添加错题"` → `strings.title`
- :61 `"科目"` → `strings.subjectLabel`
- :65-69 五个 chip 标签 → `shared.subjectMathChip` … `shared.subjectOtherChip`
- :78 `"题目摘要"` → `strings.titleLabel`
- :86 `"题目详细描述"` → `strings.contentLabel`
- :93 `"❌ 错误原因分析"` → `strings.wrongReasonLabel`
- :98 `"错误类型（可选，用于错因分析）"` → `strings.errorTypeHint`
- :112 `"✅ 正确解法"` → `strings.correctMethodLabel`
- :123 `"拍照记录"` → `strings.takePhotoButton`
- :127 `"📷 已拍照"` → `strings.photoTaken`
- :136 `contentDescription = "错题照片"` → `shared.photoContentDescription`
- :148 `"请输入题目摘要"` → `strings.missingTitleMessage`
- :175 `"保存错题"` → `strings.saveButton`

验证：`grep -n "[一-鿿]" …/ChildMistakeAddScreen.kt` → 0 行。

3e. 迁移 `ParentMistakeReviewScreen.kt`（`val strings = LocalStrings.current.parentMistakeReview`、`val shared = LocalStrings.current.mistake`）：
- :37 `"📊 错题分析"` → `strings.title`
- :56 `"错题概览"` → `strings.overviewTitle`
- :59-61 `"总计"/"未掌握"/"已掌握"` → `strings.statTotal/statUnmastered/statMastered`
- :71 `"科目分布"` → `strings.subjectDistribution`
- :82-88 科目 `when` → `shared.subjectMath/subjectChinese/subjectEnglish/subjectScience/subjectOther`
- :111 `"最近错题"` → `strings.recentMistakes`
- :132 `"✅ 已掌握"/"⏳ 未掌握"` → `shared.masteredStatus/unmasteredStatus`

验证：`grep -n "[一-鿿]" …/ParentMistakeReviewScreen.kt` → 0 行。

- [ ] **Step 4: 跑测试确认绿**

Expected: 20 条全绿（MistakeCopyTest 8 + SpacedRepetitionTest 12）。

---

### Task 5: ReviewReminderStrings + 通知本地化（common + androidMain 授权两文件）

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ReviewReminderStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ReviewReminderReceiver.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/ReviewReminderReceiver.kt`（用户 2026-11-10 授权）
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidNotificationScheduler.kt`（同上）
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`（追加 2 条）

**Interfaces:**
- Produces: `data class ReviewReminderStrings(title, dueBody: (Int) -> String, defaultBody, fallbackBody)`、`AppStrings.reviewReminder`；`ReviewReminderReceiver.localizedCopy(): ReviewReminderStrings`（suspend）、`ReviewReminderReceiver.schedule(hour: Int)`（改为 suspend）；删除 `TITLE` 常量与 `reminderBody()`（全仓无调用方）。
- 平台侧：`AndroidNotificationScheduler.showNotification(context, kind, message, notificationId, titleOverride: String? = null)`（缺省行为不变；另两个 receiver 不改）。

- [ ] **Step 1: 追加测试（编译红）**

```kotlin
    @Test
    fun reviewReminderCopyDiffers() {
        val zh = AppStringsZh.reviewReminder
        val en = AppStringsEn.reviewReminder
        assertDiffers(
            zh.title to en.title,
            zh.defaultBody to en.defaultBody,
            zh.fallbackBody to en.fallbackBody,
        )
    }

    @Test
    fun reviewReminderFunctionCopyPinsBothLanguages() {
        val zh = AppStringsZh.reviewReminder
        val en = AppStringsEn.reviewReminder
        assertEquals("📚 今日有 3 道错题待复习，趁热打铁及时复习，记得更牢～", zh.dueBody(3))
        assertEquals("📚 3 mistakes due for review today — review while it's fresh to remember longer", en.dueBody(3))
        assertEquals("📚 1 mistake due for review today — review while it's fresh to remember longer", en.dueBody(1))
    }
```

- [ ] **Step 2: 确认红**（`Unresolved reference 'reviewReminder'`）

- [ ] **Step 3: 实现**

3a. 创建 `ReviewReminderStrings.kt`：

```kotlin
package com.hstudy.app.i18n

/**
 * 错题复习通知（ui/mistake/ReviewReminderReceiver.kt）文案表（阶段 2 W1）。
 *
 * 取词时机 = **发出时**（spec §8）：common 侧 `ReviewReminderReceiver.localizedCopy()` 用
 * `stringsFor(currentDisplayLanguage())` 现取；已排程通知不追更，切换语言最迟下一个每日重建周期生效。
 * 三条正文分别对应迁移前 :55（有到期 N 条）、:60（默认）、:61（查询失败兜底）三处字面量；
 * 标题对应 :25。
 */
data class ReviewReminderStrings(
    val title: String,
    val dueBody: (dueCount: Int) -> String,
    val defaultBody: String,
    val fallbackBody: String,
)

val ReviewReminderStringsZh = ReviewReminderStrings(
    title = "错题复习提醒",
    dueBody = { dueCount -> "📚 今日有 $dueCount 道错题待复习，趁热打铁及时复习，记得更牢～" },
    defaultBody = "今天还有错题等着复习，快去看看吧！",
    fallbackBody = "📚 该复习错题啦！及时复习，记得更牢～",
)

val ReviewReminderStringsEn = ReviewReminderStrings(
    title = "Mistake review reminder",
    dueBody = { dueCount ->
        "📚 $dueCount ${mistakesWord(dueCount)} due for review today — review while it's fresh to remember longer"
    },
    defaultBody = "Mistakes are waiting to be reviewed — go take a look!",
    fallbackBody = "📚 Time to review your mistakes! Reviewing on time makes memories stick",
)
```

3b. `Strings.kt` 加 `val reviewReminder: ReviewReminderStrings,` + 两行聚合。

3c. `commonMain/.../ui/mistake/ReviewReminderReceiver.kt` 改为（保留 CHANNEL_ID/NOTIFICATION_ID/REMINDER_ID 常量与 cancel()；删 TITLE 与 reminderBody()）：

```kotlin
package com.hstudy.app.ui.mistake

import com.hstudy.app.i18n.ReviewReminderStrings
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder

/**
 * 错题复习每日提醒（跨平台版）：注册/取消交给 [Platform.notifications]；
 * 文案在**发出时**按生效语言取（spec §8）——已排程通知不追更，下个每日重建周期生效。
 * 平台侧（androidMain ReviewReminderReceiver / iOS UNUserNotificationCenter）消费同一份本地化文案。
 */
object ReviewReminderReceiver {

    const val CHANNEL_ID = "hstudy_review_reminder"
    const val NOTIFICATION_ID = 2001
    const val REMINDER_ID = 9001

    /** 当前生效语言下的通知文案（发出时调用）。 */
    suspend fun localizedCopy(): ReviewReminderStrings =
        stringsFor(currentDisplayLanguage()).reviewReminder

    /** 安排每日复习提醒（每天 hour 点触发；标题/正文按当前语言取词）。 */
    suspend fun schedule(hour: Int) {
        val copy = localizedCopy()
        Platform.notifications.schedule(
            ScheduledReminder(
                id = REMINDER_ID,
                kind = ReminderKind.REVIEW,
                hour = hour.coerceIn(0, 23),
                minute = 0,
                title = copy.title,
                body = copy.defaultBody,
                repeatDaily = true
            )
        )
    }

    /** 取消每日复习提醒 */
    fun cancel() {
        Platform.notifications.cancel(REMINDER_ID)
    }
}
```

调用方 `ChildMistakeListScreen` 的 `ReviewReminderReceiver.schedule(...)` 已在 `scope.launch` 内（suspend 上下文，无需改动）；若编译报"挂起函数需协程"，检查两处调用确实在 `onCheckedChange` 的 `scope.launch {}` 与小时 chip 的 `scope.launch {}` 内（迁移前已如此）。

3d. `androidMain/.../platform/ReviewReminderReceiver.kt` 的 `onReceive` 正文改为：

```kotlin
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init()
                val copy = ReviewReminderReceiver.localizedCopy()
                val mistakes = AppGraph.mistakeRepository.getAllMistakes().first()
                val dueCount = mistakes.count { isDueForReview(it) }
                if (dueCount > 0) {
                    AndroidNotificationScheduler.showNotification(
                        context, ReminderKind.REVIEW,
                        copy.dueBody(dueCount),
                        AndroidNotificationScheduler.REVIEW_NOTIFICATION_ID,
                        titleOverride = copy.title
                    )
                }
            } catch (e: Exception) {
                // 兜底：查询失败时仍推送通用提醒（文案同样按当前语言取；取词再失败则静默跳过）
                runCatching {
                    val copy = ReviewReminderReceiver.localizedCopy()
                    AndroidNotificationScheduler.showNotification(
                        context, ReminderKind.REVIEW,
                        copy.fallbackBody,
                        AndroidNotificationScheduler.REVIEW_NOTIFICATION_ID,
                        titleOverride = copy.title
                    )
                }
            } finally {
                AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
                pendingResult.finish()
            }
        }
    }
```

顶部补 `import com.hstudy.app.ui.mistake.ReviewReminderReceiver`。**其余（isDueForReview 内联算法等）逐字节不动。**

3e. `AndroidNotificationScheduler.kt` 的 `showNotification` 签名与标题选择改为：

```kotlin
        /** 弹出本地通知（渠道/标题/图标与原实现一致），点击打开 MainActivity。
         *  [titleOverride] 供需要本地化标题的调用方使用；缺省 null 时用与渠道固定的原中文标题
         *  （周报/日程仍走缺省，待各自波次接线）。 */
        fun showNotification(
            context: Context,
            kind: ReminderKind,
            message: String,
            notificationId: Int,
            titleOverride: String? = null,
        ) {
```
并把 `.setContentTitle(title)` 改成 `.setContentTitle(titleOverride ?: title)`。其余逐字节不动。

- [ ] **Step 4: 编译 + 测试确认绿**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL（androidMain 两文件编译通过）。
Run: `… ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.MistakeCopyTest" --tests "com.hstudy.app.ui.mistake.SpacedRepetitionTest"`
Expected: 22 条全绿（MistakeCopyTest 10 + SpacedRepetitionTest 12）。

---

### Task 6: 等价闸门登记 + 人工清单 + 全量四闸 + 唯一 W1 commit

**Files:**
- Modify: `scripts/i18n_template_diff.py`（W1_REV + BRANCH_PAGES + BRANCH_TABLES + DISCLOSED_RESIDUALS）
- Modify: `docs/i18n-stage1-verification.md`（§2 人工清单追加 V-34…V-39）
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt`（追加 1 条跨表钉死）

- [ ] **Step 1: 追加跨表钉死测试**

```kotlin
    @Test
    fun crossTableSharedLabelsPinned() {
        // 同一个中文值只允许一个英文出处：错题列表的「全部」与任务列表的「全部」
        assertEquals(AppStringsZh.taskList.filterAll, AppStringsZh.mistakeList.filterAll)
        assertEquals(AppStringsEn.taskList.filterAll, AppStringsEn.mistakeList.filterAll)
        // 科目「其他」与 W0 注册表 canonical 同值同译
        assertEquals("其他", AppStringsZh.mistake.subjectOther)
        assertEquals("Other", AppStringsEn.mistake.subjectOther)
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))
        // 里程碑名与成就注册表命名一致（克星/大师）
        assertEquals("Mistake Buster", AppStringsEn.mistakeList.milestoneName50)
        assertEquals("Mistake Master", AppStringsEn.mistakeList.milestoneName100)
    }
```

- [ ] **Step 2: 登记 BRANCH_PAGES / BRANCH_TABLES / 披露**

在 `scripts/i18n_template_diff.py` 的 `TASK11_REV` 常量之后加：

```python
W1_REV = "4d28c8e"        # 阶段 2 W1（错题域）迁移前 = v3.1.0 发布提交
```

`BRANCH_PAGES` 列表末尾追加：

```python
    # ---- 阶段 2 W1（错题域，W1_REV）----
    (W1_REV, "com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ChildMistakeAddScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ParentMistakeReviewScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/SpacedRepetition.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ReviewReminderReceiver.kt"),
```

`BRANCH_TABLES` 列表（元组推导）末尾追加 6 个表名：`"MistakeStrings", "MistakeListStrings", "MistakeDetailStrings", "MistakeAddStrings", "ParentMistakeReviewStrings", "ReviewReminderStrings"`。

`DISCLOSED_RESIDUALS` 列表末尾追加 7 条（落库原文，页面单边）：

```python
    # ---- 阶段 2 W1：Ruling 25 落库原文（页面单边；值不进表）----
    {"page": "主动回忆-做对", "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:259 reviewMistake(note) 落库，不本地化"},
    {"page": "错题复习做对: %s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:260 submitApproval description 落库"},
    {"page": "错题复习: %s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:273 submitApproval description 落库"},
    {"page": "举一反三思考: %s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:339 submitApproval description 落库"},
    {"page": "间隔复习", "table": None,
     "reason": "W1 Ruling 25：ChildMistakeListScreen.kt:131 reviewMistake(note) 落库"},
    {"page": "复习错题：%s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeListScreen.kt:132 submitApproval description 落库"},
    {"page": "连续复习%s天里程碑奖励" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeListScreen.kt:274 adjustPoints note 落库"},
```

- [ ] **Step 3: 人工清单追加**

在 `docs/i18n-stage1-verification.md` §2 清单末尾追加（编号接续现有最大项）：

```markdown
### 阶段 2 W1（错题域）追加项
- [ ] V-34 英文态进入错题列表：标题/状态卡/筛选栏/空状态全英文（用户错题标题/错因/解法为自建内容，按原样）
- [ ] V-35 英文态错题详情：主动回忆三阶段按钮、举一反三 15 条引导问题、复习记录格式全英文；落库原文（账本描述）按数据原样
- [ ] V-36 英文态添加错题：科目芯片（含 emoji）、错误类型 canonical chip、保存校验 snackbar 全英文
- [ ] V-37 英文态家长错题分析：概览/科目分布/最近错题全英文
- [ ] V-38 切换语言后关闭再开启「每日复习提醒」：到点弹通知的标题/正文为当前语言（已排程旧通知不追更，属 spec §8 既定语义）
- [ ] V-39 中文态回归：以上四页与通知与 v3.1.0 文案逐字节一致
```

- [ ] **Step 4: 全量四闸（顺序执行，逐条 exit 0）**

```bash
PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py
PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py
PYTHONIOENCODING=utf-8 python scripts/i18n_template_diff.py --branch
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest
```

Expected:
- 边界闸门 exit 0（commonMain 129 文件；页面新增的 `strings.*` 用法不是 sink 实参即可）；
- 自测 exit 0（195 断言不变）；
- 等价闸门 `--branch` exit 0；[A] 无 missing/invented（W1 六表 × W1 六源文件并入整枝组），披露 7 条全部被用到；
- assembleDebug BUILD SUCCESSFUL；
- **146 tests，0 failures**（134 基线 + 12）。
任何一闸红：修到绿再继续，不许带红提交。若 `--branch` 报某条 `missing`：先核对该字面量是否真在表里（逐字节，含 emoji 与全角标点）；若报 `invented`：说明表里有页面不存在的字面量，回到对应任务补齐或更正表值——**不许靠加披露条目掩盖实现错误**（披露只允许 Ruling 25 落库原文）。

- [ ] **Step 5: 落唯一 W1 commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeListStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeDetailStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/MistakeAddStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentMistakeReviewStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ReviewReminderStrings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Plurals.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/SpacedRepetition.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeAddScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ParentMistakeReviewScreen.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ReviewReminderReceiver.kt \
        composeApp/src/androidMain/kotlin/com/hstudy/app/platform/ReviewReminderReceiver.kt \
        composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidNotificationScheduler.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/MistakeCopyTest.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/ui/mistake/SpacedRepetitionTest.kt \
        scripts/i18n_template_diff.py \
        docs/i18n-stage1-verification.md
git commit -m "feat(i18n): 阶段 2 W1——错题域四页双语化（6 张表）、复习通知随语言、等价闸门登记"
git status
```

Expected: commit 成功，工作树干净。**到此为止，不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译（含 androidMain 平台改动）、146 单测、字节等价闸门整枝组、存储边界、中文态逐字节不变。
- 不可自动证明：英文态四页的视觉呈现（人工清单 V-34…V-39）；Android 通知实际弹出语言（需设备/模拟器开关提醒后等待到点，本机不可自动验证）；iOS 通知文案（CI 只验证编译与冒烟）。
- `androidMain` 不在任何 Python 闸门的扫描范围：平台两文件的正确性由编译 + 本计划逐行钉死 + 任务审查承担。
- 不以"CI 绿"表述"双语已验证"。
