# 双语第一阶段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 HStudy 的启动/首页/打卡/设置链路支持中英双语，默认跟随系统语言、家长设置可手动覆盖且即时生效。

**Architecture:** 在 `commonMain` 自持一份按页面分组的 `data class` 文案表（zh/en 两份实例），通过 `CompositionLocalProvider(LocalStrings …)` 注入根 Composable；语言偏好存 DataStore，`Flow` 驱动重组。不使用 Compose Multiplatform 的 resources/`LocalConfiguration`（1.7.3 的 commonMain 不存在该 API，已用编译器探针证实）。

**Tech Stack:** Kotlin 2.1.20、Compose Multiplatform 1.7.3、kotlinx-coroutines、DataStore Preferences 1.1.1、kotlin("test")。

**Spec:** `docs/superpowers/specs/2026-11-07-hstudy-bilingual-stage1-design.md`
**字面量清单（脚本生成，勿手改）:** `docs/superpowers/plans/2026-11-07-bilingual-stage1-inventory.md`

## Global Constraints

- `commonMain` 禁止出现 `android.*` / `java.*` 导入（校验：`grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l` 必须为 0）。
- 迁移默认只允许等价替换 `"中文"` → `strings.xxx`。例外必须同时满足四条（Ruling 20）：(a) 移动的每个 token 都属文案（分隔符、括号、单位、语序）；(b) 中文态输出逐字节不变，且由字面量钉死的测试证明而非口头论证；(c) 不改控制流、数据源、组件与布局参数；(d) 在字段 KDoc 与任务报告里同时披露。四条缺一即回滚。
- 不动 `ui/dt/DateTimeCompat.kt` 的格式行为（英文态仍 `yyyy-MM-dd`、周一起始）。
- 不本地化写入数据库的种子内容（`data/repository/TaskRepository.kt` 等 125 处 `title =`/`name =`）。
- 存储边界（Ruling 25，硬约束）：任何文案表字符串都不得进入写库调用或参与业务判等参数。每个迁移任务收尾必须跑 `python scripts/i18n_storage_boundary.py` 且退出码为 0；违规的处置方式是"存规范值、只在渲染时本地化"，不是把英文存进去。`bizKey` 与幂等比较涉及的 description 一律不本地化。
- 越界自检只约束**迁移类提交**（字面量 → strings 引用）。修复/重构类提交把值从 `strings.` 移回规范常量时，必然出现不含中文的删除行：改用扩展模式 `strings\.|common\.|Canonical|LocalStrings|import |val strings` 并在报告里声明提交类别（Ruling 26）。
- 页面文件不得新增中文注释行；解释性说明写进 i18n 表文件的 KDoc（Ruling 26 附带）。
- 每个任务结束时本地三闸全绿才提交推送：
  `compileCommonMainKotlinMetadata` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`。
- 推送前确认无在飞 CI（工作流 `cancel-in-progress: true` 会取消 macOS 任务）。
- 提交信息用仓库既有风格：中文、`feat(i18n):` / `test(i18n):` / `docs:` 前缀。
- 第一阶段规模：**362 条**（10 个目标文件，按文件去重），其中 **67 条含插值**需函数化；占全仓去重 1406 条的 26%。
- 变异取证约定（Task 7 审查确立）：为证明断言会失败而临时修改生产代码是**允许**的，但必须当轮 revert、用 `git diff --exit-code` 证明干净、且在任何其他命令之前完成；报告里写清探针与日志名。
- 函数型（lambda）文案不在 `toString()` 的返回值里，整表 CJK 守卫看不见它们：每个 lambda 字段必须有"字面量钉死"的双语断言，且任务审查必须逐字段列出 lambda 与其断言位置（9/9 那种核对），漏一个即视为 spec ❌。这是审查闸门强制项，不是可选加固（Ruling 20-b）。
- 英文译文必须遵循 spec §11 已确认术语表（Task 0）；表中没有的新词先补进 §11 再写文案。
- 页面侧取文案统一写 `val strings = LocalStrings.current.<group>`（Ruling 13）；根节点 `App.kt` 保留 `strings`（它负责 provide）。

## File Structure

**新建**
- `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/AppLanguage.kt` — 语言枚举 + 纯函数解析
- `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SystemLocale.kt` — `expect fun systemLanguageTag(): String`
- `composeApp/src/androidMain/kotlin/com/hstudy/app/i18n/SystemLocale.android.kt` — actual
- `composeApp/src/iosMain/kotlin/com/hstudy/app/i18n/SystemLocale.ios.kt` — actual
- `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt` — `AppStrings` 聚合根 + `LocalStrings`
- `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/RoleStrings.kt` — 角色选择页文案（zh/en）
- `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/{Common,Pomodoro,Focus,TaskList,ChildHome,ParentHome,ParentDashboard,ParentSettings}Strings.kt` — 后续页面各一文件
- `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/AppLanguageResolveTest.kt`
- `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt` — 漏译守卫 + 插值文案断言
- `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/AssertDiffers.kt` — 测试辅助

**修改**
- `composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt:29-33` — 提供 `LocalStrings`
- `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt:29` 附近 — 新增 `LANGUAGE_KEY` + Flow + setter
- 10 个页面文件（见清单）

---

### Task 0: 术语表（已完成，无需执行）

术语表已于 2026-11-07 与用户确认，定稿在 `docs/superpowers/specs/2026-11-07-hstudy-bilingual-stage1-design.md` §11。

**对后续所有任务生效的硬约束：**
- 英文文案必须使用该表中的译法；同一中文概念在 362 条里出现多次时，译法必须一致。
- `Wish Pool` 需按 §11 的配套文案规则处理（英文态首次进入要带一句解释性副标题，且不得与 Reward Store 混用）。
- 品牌行与标语允许按英文语序重组，不做逐字硬译（表内已给出 `Study Points & Rewards`、`Build visible habits`）。
- 表中没有的新术语，先补进 §11 再写文案（一个 PR 内完成，避免边写边造词）。

---

### Task 1: AppLanguage 与解析函数

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/AppLanguage.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/AppLanguageResolveTest.kt`

**Interfaces:**
- Produces: `enum class AppLanguage { SYSTEM, ZH, EN }`（用户设置）、`enum class DisplayLanguage { ZH, EN }`（生效语言）、`fun AppLanguage.resolveWith(systemTag: String): DisplayLanguage`、`fun AppLanguage.toStorageValue(): String`、`fun appLanguageFromStorage(raw: String?): AppLanguage`

拆成两个枚举的原因：`SYSTEM` 是"设置值"，永远不该出现在"生效语言"里。若只用一个枚举，`stringsFor(AppLanguage)` 就必须为 `SYSTEM` 编一个含义（ZH 还是 EN？），那是设计漏洞而不是实现细节。

- [ ] **Step 1: 写失败测试**

`composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/AppLanguageResolveTest.kt`
```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageResolveTest {
    @Test
    fun explicitChoiceWins() {
        assertEquals(DisplayLanguage.ZH, AppLanguage.ZH.resolveWith("en"))
        assertEquals(DisplayLanguage.EN, AppLanguage.EN.resolveWith("zh"))
    }

    @Test
    fun systemChineseSelectsZh() {
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh-CN"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh-Hant-TW"))
    }

    @Test
    fun systemOtherSelectsEn() {
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("en"))
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("fr"))
    }

    @Test
    fun emptySystemTagFallsBackToZh() {
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith(""))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("   "))
    }

    @Test
    fun storageRoundTrip() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage(null))
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage("system"))
        assertEquals(AppLanguage.EN, appLanguageFromStorage(AppLanguage.EN.toStorageValue()))
        assertEquals("zh", AppLanguage.ZH.toStorageValue())
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --tests "*AppLanguageResolveTest*" --console=plain`
Expected: 编译失败，`Unresolved reference 'AppLanguage'`

- [ ] **Step 3: 最小实现**

`composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/AppLanguage.kt`
```kotlin
package com.hstudy.app.i18n

/** 用户可选的语言设置。 */
enum class AppLanguage {
    SYSTEM, ZH, EN;

    /** 解析为实际生效语言：显式选择优先；SYSTEM 下 zh* 走中文，空标签回退中文，其余走英文。 */
    fun resolveWith(systemTag: String): DisplayLanguage {
        val tag = systemTag.trim().lowercase()
        return when (this) {
            ZH -> DisplayLanguage.ZH
            EN -> DisplayLanguage.EN
            SYSTEM -> if (tag.isEmpty() || tag.startsWith("zh")) DisplayLanguage.ZH else DisplayLanguage.EN
        }
    }

    fun toStorageValue(): String = name.lowercase()
}

/** 生效语言，不含 SYSTEM 语义。 */
enum class DisplayLanguage { ZH, EN }

fun appLanguageFromStorage(raw: String?): AppLanguage = when (raw?.lowercase()) {
    "zh" -> AppLanguage.ZH
    "en" -> AppLanguage.EN
    else -> AppLanguage.SYSTEM
}
```

- [ ] **Step 4: 运行确认通过**

Run: 同 Step 2
Expected: `BUILD SUCCESSFUL`，5 个测试通过

- [ ] **Step 5: 提交**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/AppLanguage.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/AppLanguageResolveTest.kt
git commit -m "feat(i18n): 语言枚举与解析规则（未知/空标签回退中文）"
```

---

### Task 2: 系统语言标签（expect/actual）

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SystemLocale.kt`
- Create: `composeApp/src/androidMain/kotlin/com/hstudy/app/i18n/SystemLocale.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/hstudy/app/i18n/SystemLocale.ios.kt`

**Interfaces:**
- Produces: `expect fun systemLanguageTag(): String`（返回如 `zh`、`en`、`fr` 的小写语言子标签；全计划唯一 expect/actual 点）

- [ ] **Step 1: commonMain 声明**

`SystemLocale.kt`
```kotlin
package com.hstudy.app.i18n

/** 当前系统语言标签（小写，如 zh / en / fr）。 */
expect fun systemLanguageTag(): String
```

- [ ] **Step 2: androidMain 实现**

`SystemLocale.android.kt`
```kotlin
package com.hstudy.app.i18n

import java.util.Locale

actual fun systemLanguageTag(): String = Locale.getDefault().language.lowercase()
```

- [ ] **Step 3: iosMain 实现**

`SystemLocale.ios.kt`
```kotlin
package com.hstudy.app.i18n

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.languageCode

actual fun systemLanguageTag(): String =
    NSLocale.currentLocale.objectForKey(NSLocaleLanguageCode) as? String
        ?: NSLocale.languageCode().lowercase()
```

若 `objectForKey` / `languageCode()` 在 iOS 26 SDK 的 cinterop 中不可用，按仓库既有做法回退（不要猜第三种拼写）：

```kotlin
import platform.UIKit.UIScreen  // 仅用于确认 import 未被裁剪
actual fun systemLanguageTag(): String =
    platform.Foundation.NSLocale.currentLocale.localeIdentifier.substringBefore("-").lowercase()
```

- [ ] **Step 4: 三闸验证**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:compileCommonMainKotlinMetadata --console=plain -q
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:assembleDebug --console=plain -q
```
Expected: 两条均 BUILD SUCCESSFUL。iOS 侧由 CI 的 `linkDebugFrameworkIosSimulatorArm64` 验证（本机不可编译 Apple 目标）。

- [ ] **Step 5: 平台纯净度校验**

Run: `grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l`
Expected: `0`（`java.util.Locale` 只允许出现在 androidMain）

- [ ] **Step 6: 提交**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SystemLocale.kt composeApp/src/androidMain/kotlin/com/hstudy/app/i18n composeApp/src/iosMain/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 系统语言标签的 expect/actual 桥接"
```

---

### Task 3: Strings 骨架、LocalStrings、偏好键与根接线

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/RoleStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt`（companion 键表 + Flow 属性 + setter，参照既有 `setRole` 写法）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt:29-33`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/AssertDiffers.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `AppLanguage`、`DisplayLanguage`、`appLanguageFromStorage`、`resolveWith`、`systemLanguageTag`（Task 1/2）
- Produces: `data class AppStrings(val role: RoleStrings)`；`val LocalStrings: ProvidableCompositionLocal<AppStrings>`；`fun stringsFor(language: DisplayLanguage): AppStrings`；`UserPreferences.appLanguage: Flow<String?>`；`suspend fun setAppLanguage(language: AppLanguage)`

- [ ] **Step 1: 写失败测试（漏译守卫 + 选择函数）**

`AssertDiffers.kt`
```kotlin
package com.hstudy.app.i18n

import kotlin.test.assertNotEquals

/** 英文字段若与中文原样相同，视为漏译。 */
fun assertDiffers(vararg pairs: Pair<String, String>) {
    pairs.forEach { (zh, en) -> assertNotEquals(zh, en, "疑似漏译：$zh") }
}
```

`StringsCopyTest.kt`
```kotlin
package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsCopyTest {
    @Test
    fun roleCopyDiffersBetweenLanguages() {
        val zh = AppStringsZh.role
        val en = AppStringsEn.role
        assertDiffers(
            zh.brandTitle to en.brandTitle,
            zh.tagline to en.tagline,
            zh.parentEntry to en.parentEntry,
            zh.parentDesc to en.parentDesc,
            zh.childEntry to en.childEntry,
            zh.childDesc to en.childDesc,
        )
    }

    @Test
    fun stringsForPicksInstance() {
        assertEquals(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertEquals(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --tests "*StringsCopyTest*" --console=plain`
Expected: 编译失败，`Unresolved reference 'AppStringsZh'`

- [ ] **Step 3: 定义 RoleStrings（值取自实测字面量）**

`RoleStrings.kt`。下列 6 条中文是 `ui/role/RoleSelectScreen.kt` 第 75/83/98/99/106/107 行的原文，逐字照抄，不得改写语义；英文按 Task 0 术语表（积分 = Points、打卡 = Check-in）。

```kotlin
package com.hstudy.app.i18n

data class RoleStrings(
    val brandTitle: String,
    val tagline: String,
    val parentEntry: String,
    val parentDesc: String,
    val childEntry: String,
    val childDesc: String,
)

val RoleStringsZh = RoleStrings(
    brandTitle = "学习积分奖励",
    tagline = "好习惯，看得见 · 选择你的身份",
    parentEntry = "我是家长",
    parentDesc = "发布任务 · 管理积分 · 设置奖励",
    childEntry = "我是孩子",
    childDesc = "完成任务 · 获取积分 · 兑换奖励",
)

val RoleStringsEn = RoleStrings(
    brandTitle = "Study Points & Rewards",
    tagline = "Build visible habits · Choose who you are",
    parentEntry = "I'm the parent",
    parentDesc = "Assign tasks · Manage points · Set rewards",
    childEntry = "I'm the learner",
    childDesc = "Finish tasks · Earn points · Redeem rewards",
)
```

本页**没有**含插值的文案（实测 0 条），因此本页不产生函数型字段；函数型字段在 Task 7 首次出现。

- [ ] **Step 4: 聚合根与 LocalStrings**

`Strings.kt`
```kotlin
package com.hstudy.app.i18n

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

data class AppStrings(
    val role: RoleStrings,
)

val AppStringsZh = AppStrings(role = RoleStringsZh)
val AppStringsEn = AppStrings(role = RoleStringsEn)

fun stringsFor(language: DisplayLanguage): AppStrings = when (language) {
    DisplayLanguage.ZH -> AppStringsZh
    DisplayLanguage.EN -> AppStringsEn
}

val LocalStrings: ProvidableCompositionLocal<AppStrings> =
    staticCompositionLocalOf { error("LocalStrings 未提供：请在 App 根注入") }
```

- [ ] **Step 5: 偏好键与读写**

在 `UserPreferences.kt` 的 companion 中，紧跟 `ROLE_KEY` 之后加入：
```kotlin
        val LANGUAGE_KEY = stringPreferencesKey("app_language") // "system" | "zh" | "en"
```
在 Flow 属性区（`currentRole` 之后）加入：
```kotlin
    val appLanguage: Flow<String?> = dataStore.data.map { it[LANGUAGE_KEY] }
```
在 setter 区（`setRole` 之后）加入：
```kotlin
    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[LANGUAGE_KEY] = language.toStorageValue() }
    }
```
并在文件顶部加 `import com.hstudy.app.i18n.AppLanguage`。

- [ ] **Step 6: 根 Composable 注入**

`App.kt` 的 `App()` 改为：
```kotlin
@Composable
fun App() {
    val prefs = remember { UserPreferences() }
    val storedLanguage by prefs.appLanguage
        .catch { emit(null) }
        .collectAsState(initial = null)
    val strings = stringsFor(appLanguageFromStorage(storedLanguage).resolveWith(systemLanguageTag()))

    HStudyTheme {
        ProvideWindowSizeClass {
            val navigator = rememberAppNavigator(Screen.RoleSelect.route)
            Surface(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalStrings provides strings,
                ) {
                    AppNavGraph(navigator)
                }
            }
        }
    }
}
```
新增 import：
```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.catch
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.appLanguageFromStorage
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.i18n.systemLanguageTag
```
若 `UserPreferences` 在本工程是单例/由 `AppGraph` 暴露，则改用既有实例而不是 `remember { UserPreferences() }`（检查 `AppGraph.kt` 中 `userPreferences` 字段并用它）。

- [ ] **Step 7: 运行确认通过 + 三闸**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --console=plain
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:assembleDebug --console=plain -q
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:compileCommonMainKotlinMetadata --console=plain -q
```
Expected: 三条全 BUILD SUCCESSFUL

- [ ] **Step 8: 提交**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 文案表骨架、LocalStrings 注入与语言偏好读写"
```

---

### Task 4: 角色选择页迁移

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/role/RoleSelectScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`（已覆盖，无需新增）

**Interfaces:**
- Consumes: `LocalStrings`、`AppStrings.role`（Task 3）
- Produces: 第一个真实双语页面；后续页面沿用同一姿势

- [ ] **Step 1: 取本页字面量清单**

Run: `python -c "import io;print(io.open('docs/superpowers/plans/2026-11-07-bilingual-stage1-inventory.md',encoding='utf-8').read().split('## role/RoleSelectScreen.kt')[1].split('## ')[0])"`
Expected: 6 行表格（行号 + 中文）

- [ ] **Step 2: 在页面顶部取文案**

```kotlin
    val strings = LocalStrings.current.role
```

- [ ] **Step 3: 逐条等价替换**

只替换字符串字面量为 `strings.<field>`。本页 6 条字面量一一对应 `RoleStrings` 的 6 个字段（实测无插值）；若清单出现第 7 条，为 `RoleStrings` 增加字段并在 zh/en 两份都填上（漏填英文 = 编译错误）。

- [ ] **Step 4: 三闸 + 平台纯净度**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:assembleDebug --console=plain -q
grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l
```
Expected: BUILD SUCCESSFUL 且 `0`

- [ ] **Step 5: 确认 diff 只含文案行**

Run: `git diff --stat && git diff -- composeApp/src/commonMain/kotlin/com/hstudy/app/ui/role/RoleSelectScreen.kt | grep -E "^[-+]" | grep -vE "^[-+]{3}" | grep -cE "stringResource|strings\.|import |val strings"`
Expected: 两个计数都为 0（新增行全部涉及文案/导入，删除行全部含中文字面量）；非 0 即越界，回滚重做。

- [ ] **Step 6: 提交**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/ui/role/RoleSelectScreen.kt
git commit -m "feat(i18n): 角色选择页双语"
```

---

### Task 5: 设置页语言选择控件

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（新增 `settings` 组）
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `UserPreferences.setAppLanguage`、`appLanguage` Flow、`LocalStrings`
- Produces: `LocalDisplayLanguage`（根节点提供生效语言，页面不得重复解析）；`data class SettingsStrings(val languageTitle: String, val languageSystem: String, val languageZh: String, val languageEn: String, val languageNote: String, val partialTranslationNote: String)`；`AppStrings.settings`

- [ ] **Step 1: 写失败测试**

追加到 `StringsCopyTest.kt`：
```kotlin
    @Test
    fun settingsLanguageCopyDiffers() {
        assertDiffers(
            AppStringsZh.settings.languageTitle to AppStringsEn.settings.languageTitle,
            AppStringsZh.settings.languageNote to AppStringsEn.settings.languageNote,
            AppStringsZh.settings.partialTranslationNote to AppStringsEn.settings.partialTranslationNote,
        )
    }

    @Test
    fun languageOptionLabelsAreEndonymsInBothLocales() {
        // 语言选项名不随界面语言翻译：中文/English 在两种语言下都这样显示
        assertEquals("中文", AppStringsZh.settings.languageZh)
        assertEquals("中文", AppStringsEn.settings.languageZh)
        assertEquals("English", AppStringsZh.settings.languageEn)
        assertEquals("English", AppStringsEn.settings.languageEn)
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --tests "*StringsCopyTest*" --console=plain`
Expected: 编译失败，`Unresolved reference 'settings'`

- [ ] **Step 3: 实现 SettingsStrings**

`SettingsStrings.kt`（中文值需与 `ParentSettingsScreen.kt` 现有措辞风格一致；英文按术语表）
```kotlin
package com.hstudy.app.i18n

data class SettingsStrings(
    val languageTitle: String,
    val languageSystem: String,
    val languageZh: String,
    val languageEn: String,
    val languageNote: String,
    val partialTranslationNote: String,
)

val SettingsStringsZh = SettingsStrings(
    languageTitle = "🌐 语言",
    languageSystem = "跟随系统",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "选择界面显示语言，立即生效。",
    partialTranslationNote = "部分页面仍在适配英文，会暂时保持中文显示。",
)

val SettingsStringsEn = SettingsStrings(
    languageTitle = "🌐 Language",
    languageSystem = "System default",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "Choose the app language. Changes apply immediately.",
    partialTranslationNote = "Some screens are not translated yet and stay in Chinese.",
)
```
并在 `AppStrings` 增加 `val settings: SettingsStrings`，两份聚合实例补上 `settings = SettingsStringsZh/En`。

- [ ] **Step 4: 在设置页加控件**

在 `ParentSettingsScreen` 的现有项之后插入（沿用该页既有分区/卡片组件，不新造样式）：
```kotlin
    val strings = LocalStrings.current.settings
    val prefs = remember { UserPreferences() }
    val storedLanguage by AppGraph.userPreferences.appLanguage.collectAsState(initial = AppGraph.startupLanguage)
    val current = appLanguageFromStorage(storedLanguage)

    // 标题 + 三个可选项；点击写入偏好并即时生效
    Text(strings.languageTitle)
    Text(strings.languageNote, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == AppLanguage.SYSTEM,
            onClick = { AppGraph.appScope.launch { prefs.setAppLanguage(AppLanguage.SYSTEM) } },
            label = { Text(strings.languageSystem) },
        )
        FilterChip(
            selected = current == AppLanguage.ZH,
            onClick = { AppGraph.appScope.launch { prefs.setAppLanguage(AppLanguage.ZH) } },
            label = { Text(strings.languageZh) },
        )
        FilterChip(
            selected = current == AppLanguage.EN,
            onClick = { AppGraph.appScope.launch { prefs.setAppLanguage(AppLanguage.EN) } },
            label = { Text(strings.languageEn) },
        )
    }
    // 只在英文态显示"部分页面未适配"说明（spec §7）；生效语言由根节点提供，页面不得自行解析
    if (LocalDisplayLanguage.current == DisplayLanguage.EN) {
        Text(strings.partialTranslationNote, style = MaterialTheme.typography.bodySmall)
    }
```
若该页已有 ViewModel/scope，用既有 scope 而非 `AppGraph.appScope`。`FilterChip` 若未在该页 import，补 `import androidx.compose.material3.FilterChip`。

- [ ] **Step 5: 持久化往返测试（spec §8 第 5 项）**

Create: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/LanguagePreferenceTest.kt`
```kotlin
package com.hstudy.app.i18n

import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguagePreferenceTest {
    @AfterTest
    fun cleanup() {
        // 偏好文件是进程内单例，测试产物留在 build 目录下，避免污染真实数据目录
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
    }

    @Test
    fun languagePersistsAcrossReads() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.EN)
        assertEquals("en", prefs.appLanguage.first())
        assertEquals(AppLanguage.EN, appLanguageFromStorage(prefs.appLanguage.first()))
    }
}
```

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --tests "*LanguagePreferenceTest*" --console=plain`
Expected: 首次运行可能因 `PreferenceDataStoreFactory` 需要协程调度器而在 JVM 测试里超时——若失败，加 `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")` 到 `commonTest.dependencies`（若已存在则跳过），仍失败则把本测试改为 `androidUnitTest` 源集下的同名文件，并在计划提交信息里记录该决定。

- [ ] **Step 6: 三闸 + 提交**

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --console=plain
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:assembleDebug --console=plain -q
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 家长设置页语言切换（跟随系统/中文/English）+ 偏好持久化测试"
```

---

### Task 6: 公共控件文案迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/CommonStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（`AppStrings` 增 `common` 字段，两份聚合实例补值）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/CommonComponents.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`（追加本页断言）

**Interfaces:**
- Consumes: `LocalStrings`、`AppStrings`（Task 3）
- Produces: `data class CommonStrings(...)`；`AppStrings.common`

**实测规模：** `ui/common/CommonComponents.kt` 去重 5 条，其中 2 条含插值。

- [ ] **Step 1: 取本页清单**

Run: `python scripts/i18n_inventory.py common/CommonComponents.kt`

- [ ] **Step 2: 建 `CommonStrings`，zh/en 两份都填**

字段命名用 `<元素><用途>` 小驼峰（如 `confirmButton`、`backButton`）；含插值的 2 条写成函数型字段 `val confirmHint: (Int) -> String`。英文必须遵循 spec §11 术语表。

- [ ] **Step 3: 在 `AppStrings` 增 `val common: CommonStrings`，并给 `AppStringsZh` / `AppStringsEn` 补值**

- [ ] **Step 4: 页面等价替换**

在 Composable 内取 `val strings = LocalStrings.current.common`，只把中文字面量替换为 `strings.<field>`（插值用 `strings.<fn>(arg)`）。禁止改动任何逻辑行。

- [ ] **Step 5: 追加漏译断言（穷举本页全部字段，不得省略）**

```kotlin
    @Test
    fun commonCopyDiffers() {
        val zh = AppStringsZh.common
        val en = AppStringsEn.common
        assertDiffers(
            zh.confirmButton to en.confirmButton,
            // 本页其余字段逐行列出，不得省略
        )
    }
```

- [ ] **Step 6: 三闸 + 越界自检 + 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- <本页文件路径> | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```


```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:testDebugUnitTest --console=plain
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :composeApp:assembleDebug --console=plain -q
# 越界自检（可用版）：新增行必须全部是文案/导入相关，删除行必须全部含中文字面量
git diff -U0 -- composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/CommonComponents.kt |
  grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l   # 必须为 0
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l                                        # 必须为 0
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/CommonComponents.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 公共控件文案双语"
```

两个计数都必须为 0：新增行若不涉及文案/导入即视为越界，删除行若不含中文字面量同样视为越界。

---

### Task 7: 番茄钟与专注页迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PomodoroStrings.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/FocusStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（增 `pomodoro`、`focus`）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/PomodoroTimer.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildFocusStatsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildFocusTrainingScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `LocalStrings`、`AppStrings`（Task 3）
- Produces: `AppStrings.pomodoro`、`AppStrings.focus`

**实测规模：** 三文件去重 68 条（31 + 17 + 20），其中 14 条含插值。本页建立插值函数化的写法。

- [ ] **Step 1: 取三页清单**

Run: `python scripts/i18n_inventory.py common/PomodoroTimer.kt focus/ChildFocusStatsScreen.kt focus/ChildFocusTrainingScreen.kt`

- [ ] **Step 2: 建 `PomodoroStrings` 与 `FocusStrings`**

`PomodoroTimer.kt` 第一条插值原文是 `剩余 <min>分<sec>秒 · 息屏后仍会继续计时`（Kotlin 里写作 `剩余 ${min}分${sec}秒 · …`），函数化后：

```kotlin
val PomodoroStringsZh = PomodoroStrings(
    remainTime = { min, sec -> "剩余 ${min}分${sec}秒 · 息屏后仍会继续计时" },
    phaseFocus = "专注中",
    // 其余字段
)

val PomodoroStringsEn = PomodoroStrings(
    remainTime = { min, sec -> "$min min $sec s left · Keeps counting with the screen off" },
    phaseFocus = "Focusing",
)
```

- [ ] **Step 3: 在 `AppStrings` 增 `pomodoro` / `focus` 并补两份聚合值**

- [ ] **Step 4: 三个页面各自等价替换**

只换字面量。注意 `PomodoroTimer` 含非 Composable 的状态逻辑：不得把 `CompositionLocal` 存进单例字段，文案要么作为参数传入，要么只在 Composable 内取用。

- [ ] **Step 5: 追加断言，含插值函数双语断言**

```kotlin
    @Test
    fun pomodoroRemainTimeCopy() {
        assertEquals("剩余 12分30秒 · 息屏后仍会继续计时", AppStringsZh.pomodoro.remainTime(12, 30))
        assertEquals("12 min 30 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(12, 30))
    }

    @Test
    fun pomodoroAndFocusCopyDiffers() {
        assertDiffers(
            AppStringsZh.pomodoro.phaseFocus to AppStringsEn.pomodoro.phaseFocus,
            AppStringsZh.focus.title to AppStringsEn.focus.title,
            // 其余字段逐行列出，不得省略
        )
    }
```

- [ ] **Step 6: 三闸 + 越界自检（三个文件各自计数）+ 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- <本页文件路径> | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```


```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/PomodoroTimer.kt composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 番茄钟与专注页双语（插值文案函数化首例）"
```

---

### Task 8: 孩子任务列表页迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/TaskListStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（增 `taskList`）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `LocalStrings`、`AppStrings`（Task 3）
- Produces: `AppStrings.taskList`

**实测规模：** 去重 17 条，其中 7 条含插值。

- [ ] **Step 1: 取本页清单**

Run: `python scripts/i18n_inventory.py child/ChildTaskListScreen.kt`

- [ ] **Step 2: 建 `TaskListStrings`**

7 条插值写成函数型字段。英文遵循术语表：积分 = Points、打卡 = Check in、任务 = Task。

- [ ] **Step 3: `AppStrings` 增 `taskList` 并补两份聚合值**

- [ ] **Step 4: 页面等价替换（只换字面量）**

- [ ] **Step 5: 追加 `taskListCopyDiffers`（穷举 17 字段）与 7 条插值函数的双语断言**

- [ ] **Step 6: 三闸 + 越界自检 + 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- <本页文件路径> | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```


```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 孩子任务列表页双语"
```

---

### Task 9: 孩子首页迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ChildHomeStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（增 `childHome`）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildHomeScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `LocalStrings`、`AppStrings`（Task 3）
- Produces: `AppStrings.childHome`

**实测规模：** 去重 107 条（第一阶段最大页），其中 20 条含插值。

**本页特殊风险（实测原文）：**

1. 有把**任务标题**插进文案的字符串：`趁精力充沛，先攻克「<hardest.title>」(<hardestStars><hardestName>)，剩下的会更轻松！`。函数签名必须显式体现内容参数，迁移时只改文案来源、不改 `hardest` 的取数逻辑：

```kotlin
val tackleFirstHint: (taskTitle: String, stars: Int, name: String) -> String
```

2. 有透传异常文本的字符串：`导入失败：<result.exceptionOrNull()?.message>`。保留异常消息透传，不吞掉、不改成固定文案。

3. 有扣分提示：`⚠️ 有每日必须任务未完成，已自动扣减 <penalty> 分`。英文按术语表用 Daily must-do。

- [ ] **Step 1: 取本页清单**

Run: `python scripts/i18n_inventory.py child/ChildHomeScreen.kt`

- [ ] **Step 2: 建 `ChildHomeStrings`（107 条 → 字段，其中 20 条函数型）**

- [ ] **Step 3: `AppStrings` 增 `childHome` 并补两份聚合值**

- [ ] **Step 4: 页面等价替换**

- [ ] **Step 5: 追加 `childHomeCopyDiffers`（穷举 107 字段）与 20 条函数断言**

- [ ] **Step 6: 三闸 + 越界自检 + 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- <本页文件路径> | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```


```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildHomeScreen.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 孩子首页双语（含任务标题插值字段）"
```

---

### Task 10: 家长首页与仪表盘迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentHomeStrings.kt`
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentDashboardStrings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（增 `parentHome`、`parentDashboard`）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentHomeScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentDashboardScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `LocalStrings`、`AppStrings`（Task 3）
- Produces: `AppStrings.parentHome`、`AppStrings.parentDashboard`

**实测规模：** 去重 95 条（59 + 36），其中 14 条含插值。

- [ ] **Step 1: 取两页清单**

Run: `python scripts/i18n_inventory.py parent/ParentHomeScreen.kt parent/ParentDashboardScreen.kt`

- [ ] **Step 2: 建 `ParentHomeStrings` 与 `ParentDashboardStrings`**

- [ ] **Step 3: `AppStrings` 增两字段并补两份聚合值**

- [ ] **Step 4: 两页等价替换**

- [ ] **Step 5: 追加两页的 `assertDiffers` 穷举断言 + 14 条函数断言**

- [ ] **Step 6: 三闸 + 越界自检（两文件各自计数）+ 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- <本页文件路径> | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```


```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentHomeScreen.kt composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentDashboardScreen.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 家长首页与仪表盘双语"
```

---

### Task 11: 家长设置页剩余文案迁移

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt`（**扩展既有 data class，不重建**）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `SettingsStrings`（Task 5 已创建，含语言区字段）
- Produces: 扩展后的 `SettingsStrings`

**实测规模：** 该页去重 64 条（Task 5 已消化语言区若干条，本页处理剩余项），含插值 10 条。

**顺序约束：** 必须在 Task 5 之后执行——同一文件同一类型，并行会互相覆盖。

- [ ] **Step 1: 取本页清单**

Run: `python scripts/i18n_inventory.py parent/ParentSettingsScreen.kt`

- [ ] **Step 2: 在 `SettingsStrings` 追加字段**

保留 Task 5 的语言字段原样，包括两种语言下同值的 `languageZh` / `languageEn`。

- [ ] **Step 3: 页面等价替换剩余字面量**

- [ ] **Step 4: 追加 `settingsCopyDiffers`，穷举本页新增字段**

自称词 `languageZh` / `languageEn` 不进入 `assertDiffers`（它们在两种语言下故意同值，由 Task 5 的等值断言保护）。

- [ ] **Step 5: 三闸 + 越界自检 + 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- <本页文件路径> | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```


```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 家长设置页剩余文案双语"
```

---
### Task 7b: 番茄钟宿主页面与专注训练子页迁移（Ruling 23 新增）

**为什么存在：** spec §1 承诺"专注/打卡"双语，Task 7 迁了 `common/PomodoroTimer.kt` 组件本体，但它的宿主页面 `ui/focus/ChildPomodoroScreen.kt` 不在任何任务清单里，英文态会出现"英文计时圆环嵌在整页中文里"。审计（`scripts/i18n_coverage_audit.py`）确认这是阶段一边界的真实缺陷，而非有意推迟。

同一入口链路上的两个子页一并纳入，避免英文态出现"引导文案是英文、点进去是中文"：`ui/focus/ChildMemoryTrainingScreen.kt`（仅能经 `ChildPomodoroScreen` 进入，见 `AppNavGraph.kt:168-172`）与 `ui/focus/ChildDailyPlanScreen.kt`。

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PomodoroStrings.kt`（追加页面字段，不新建第三个表）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/FocusStrings.kt`（训练子页标题类字段复用 `trainingTitle` / `schulteTitle`，不得复制同义字段）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt`（如需新增分组）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildPomodoroScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildMemoryTrainingScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildDailyPlanScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`

**Interfaces:**
- Consumes: `LocalStrings`、`PomodoroStrings`、`FocusStrings`、`CommonStrings.minuteLabel`、`Plurals.pointsWord`
- Produces: 打卡全链路（首页入口 → 番茄钟页 → 训练子页 → 每日计划页）双语

**实测规模：** `ChildPomodoroScreen.kt` 去重 32 条 / 插值 8 条；`ChildMemoryTrainingScreen.kt` 去重 17 条；`ChildDailyPlanScreen.kt` 去重 14 条。合计 63 条。

**本页必须做对的三件事：**

1. **`分` 的两种含义会出现在同一行。** `ChildPomodoroScreen.kt:246` 原文 `每专注 5 分钟 = +1 分`：`5 分钟` 是分钟单位，`+1 分` 是积分单位。积分单位必须走 `i18n/Plurals.kt` 的 `pointsWord(n)`，分钟单位必须是独立字段；`PomodoroStrings.kt` 里"唯一出现单字分"的旧说法已被 Task 7 修复为逐条陈述，不要重新写成绝对句。
2. **步进器标签复用 `CommonStrings.minuteLabel`，不要新建第三个 `分` 字段**（Task 6+8 已为它加了字面量钉死的同形异义守卫）。
3. **计时进行中的文案陈旧问题在本次一并解决。** Task 7 报告披露：语言切换后 `PomodoroTimer.kt:104-105` 的通知与欢呼仍用切换前语言，当时不能修是因为加 `strings` 进 `remember`/`LaunchedEffect` 的 key 会重启计时循环（违反 Ruling 20(c)）。本次用不改变 key 的方式修：

```kotlin
    val currentStrings by rememberUpdatedState(strings)
```
   在循环体内读 `currentStrings`，key 保持不变。这不属于等价替换，属于本次授权的功能修正，必须同时满足：中文态输出逐字节不变、有字面量钉死断言、不改控制流以外的任何东西、并在 KDoc 与报告披露。

- [ ] **Step 1: 取三页清单**

Run: `PYTHONIOENCODING=utf-8 python scripts/i18n_inventory.py focus/ChildPomodoroScreen.kt focus/ChildMemoryTrainingScreen.kt focus/ChildDailyPlanScreen.kt`

- [ ] **Step 2: 追加字段（页面文案进 `PomodoroStrings`，训练子页复用 `FocusStrings` 现有标题字段）**

- [ ] **Step 3: 三页等价替换**

- [ ] **Step 4: `rememberUpdatedState` 修陈旧语言问题**

- [ ] **Step 5: 追加断言**

每个 lambda 字段都要有中英双语的字面量钉死断言；`每专注 5 分钟 = +1 分` 这类同行双单位必须两条都断言；`assertDiffers` 穷举本页全部非 lambda 字段。

- [ ] **Step 6: 三闸 + 越界自检 + 提交**

Run（越界自检，两个计数都必须为 0）:
```bash
git diff -U0 -- composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildPomodoroScreen.kt | grep -E "^[-+]" | grep -vE "^[-+]{3}" > /tmp/gate.txt
awk '/^\+/ && $0 !~ /strings\.|LocalStrings|import |val strings/' /tmp/gate.txt | wc -l
awk '/^-/ && $0 !~ /[一-鿿]/' /tmp/gate.txt | wc -l
```
另外两个文件同样各跑一次。Step 4 的 `rememberUpdatedState` 一行是已披露的例外，不计入"删除行必须含中文"的违规，但必须在报告里单列。

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus composeApp/src/commonTest/kotlin/com/hstudy/app/i18n
git commit -m "feat(i18n): 番茄钟宿主页与专注训练子页双语，并修语言切换后的进行中文案陈旧"
```

---
### Task 12: 文档与验证说明

**Files:**
- Modify: `README.md`（iOS/Android 已知差异附近新增「语言」小节）
- Modify: `version_history.md`（记 v3.1.0 双语第一阶段范围）

- [ ] **Step 1: README 写明范围与验证上限**

必须包含三句事实：第一阶段覆盖 362 条 / 10 个文件；未迁移页面在英文态仍显中文；**双语视觉效果无法由 CI 验证**（本机无 emulator/adb，Android CI 无模拟器），需人工在 Android Studio 与 iOS 模拟器各走一遍启动→首页→打卡→设置。

- [ ] **Step 2: 人工验证清单（交给用户执行，不由我声称已验证）**

1. Android：`./gradlew :composeApp:assembleDebug` 装机 → 设置切 English → 回首页与打卡页确认文案变化；
2. iOS：Xcode 模拟器运行 → 同上；
3. 系统语言改为英文 + 设置选「跟随系统」→ 首屏为英文；
4. 关闭 App 重开 → 语言选择保持。

- [ ] **Step 3: 提交并推送（推送前确认无在飞 CI）**

```bash
git add README.md version_history.md
git commit -m "docs(i18n): 双语第一阶段范围、术语表与人工验证清单"
git push origin main
```
