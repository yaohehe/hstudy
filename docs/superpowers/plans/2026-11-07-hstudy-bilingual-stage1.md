# 双语第一阶段 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 HStudy 的启动/首页/打卡/设置链路支持中英双语，默认跟随系统语言、家长设置可手动覆盖且即时生效。

**Architecture:** 在 `commonMain` 自持一份按页面分组的 `data class` 文案表（zh/en 两份实例），通过 `CompositionLocalProvider(LocalStrings …)` 注入根 Composable；语言偏好存 DataStore，`Flow` 驱动重组。不使用 Compose Multiplatform 的 resources/`LocalConfiguration`（1.7.3 的 commonMain 不存在该 API，已用编译器探针证实）。

**Tech Stack:** Kotlin 2.1.20、Compose Multiplatform 1.7.3、kotlinx-coroutines、DataStore Preferences 1.1.1、kotlin("test")。

**Spec:** `docs/superpowers/specs/2026-11-07-hstudy-bilingual-stage1-design.md`
**字面量清单（脚本生成，勿手改）:** `docs/superpowers/plans/2026-11-07-bilingual-stage1-inventory.md`

## Global Constraints

- `commonMain` 禁止出现 `android.*` / `java.*` 导入（校验：`grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l` 必须为 0）。
- 迁移只允许等价替换：`"中文"` → `strings.xxx`。diff 中出现任何逻辑行变化即越界，回滚该文件重做。
- 不动 `ui/dt/DateTimeCompat.kt` 的格式行为（英文态仍 `yyyy-MM-dd`、周一起始）。
- 不本地化写入数据库的种子内容（`data/repository/TaskRepository.kt` 等 125 处 `title =`/`name =`）。
- 每个任务结束时本地三闸全绿才提交推送：
  `compileCommonMainKotlinMetadata` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`。
- 推送前确认无在飞 CI（工作流 `cancel-in-progress: true` 会取消 macOS 任务）。
- 提交信息用仓库既有风格：中文、`feat(i18n):` / `test(i18n):` / `docs:` 前缀。
- 第一阶段规模：**362 条**（10 个目标文件，按文件去重），其中 **67 条含插值**需函数化；占全仓去重 1406 条的 26%。
- 英文译文必须遵循 spec §11 已确认术语表（Task 0）；表中没有的新词先补进 §11 再写文案。

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
import com.hstudy.app.i18n.resolveWith
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
Expected: 该计数等于 diff 的增删行总数（即所有改动行都是文案/导入相关）；若不等，说明动了逻辑，回滚重做。

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
- Produces: `data class SettingsStrings(val languageTitle: String, val languageSystem: String, val languageZh: String, val languageEn: String, val languageNote: String, val partialTranslationNote: String)`；`AppStrings.settings`

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
    languageTitle = "语言",
    languageSystem = "跟随系统",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "选择界面显示语言，立即生效。",
    partialTranslationNote = "部分页面仍在适配英文，会暂时保持中文显示。",
)

val SettingsStringsEn = SettingsStrings(
    languageTitle = "Language",
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
    val storedLanguage by prefs.appLanguage.catch { emit(null) }.collectAsState(initial = null)
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
    Text(strings.partialTranslationNote, style = MaterialTheme.typography.bodySmall)
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

### Task 6 至 Task 11: 其余页面分组迁移

每个任务都是 Task 4 的同一形态，差别只在页面与条数。**每个任务都必须：先取本页清单 → 建 `<Page>Strings` 并填 zh/en 两份 → 在 `AppStrings` 增字段 → 页面等价替换 → 追加 `assertDiffers` 断言 → 三闸 → 提交。**

| 任务 | 文件 | 去重条数 | 新增类型 |
|---|---|---|---|
| Task 6 | `ui/common/CommonComponents.kt` | 5 | `CommonStrings` |
| Task 7 | `ui/common/PomodoroTimer.kt`、`ui/focus/ChildFocusStatsScreen.kt`、`ui/focus/ChildFocusTrainingScreen.kt` | 31 + 17 + 20 = 68 | `PomodoroStrings`、`FocusStrings` |
| Task 8 | `ui/child/ChildTaskListScreen.kt` | 17 | `TaskListStrings` |
| Task 9 | `ui/child/ChildHomeScreen.kt` | 107 | `ChildHomeStrings` |
| Task 10 | `ui/parent/ParentHomeScreen.kt`、`ui/parent/ParentDashboardScreen.kt` | 59 + 36 = 95 | `ParentHomeStrings`、`ParentDashboardStrings` |
| Task 11 | `ui/parent/ParentSettingsScreen.kt` 剩余项 | 64 −（Task 5 已做的语言区） | 并入 `SettingsStrings` |

对每个任务的统一 Step 1（以 Task 9 为例，替换标题串即可复用）：

```bash
python -c "import io;print(io.open('docs/superpowers/plans/2026-11-07-bilingual-stage1-inventory.md',encoding='utf-8').read().split('## child/ChildHomeScreen.kt')[1].split('\n## ')[0])"
```

统一 Step「追加漏译断言」模板（每个字段一行，必须穷举本页所有字段）：

```kotlin
    @Test
    fun childHomeCopyDiffers() {
        val zh = AppStringsZh.childHome
        val en = AppStringsEn.childHome
        assertDiffers(
            zh.greeting to en.greeting,
            zh.todayTasks to en.todayTasks,
            // …本页其余字段逐行列出，不得省略
        )
    }
```

含插值的字段（10 个目标文件共 67 条，分布见清单）必须写成函数并补双语断言。下面是 `ui/common/PomodoroTimer.kt` 第 1 条插值文案的真实形态（原文：`剩余 ${min}分${sec}秒 · 息屏后仍会继续计时`），函数化后断言写法：

```kotlin
    @Test
    fun pomodoroRemainTimeCopy() {
        assertEquals("剩余 12分30秒 · 息屏后仍会继续计时", AppStringsZh.pomodoro.remainTime(12, 30))
        assertEquals("12m 30s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(12, 30))
    }
```

另有一条需要特别处理：`ui/child/ChildHomeScreen.kt` 存在把**任务标题**插进文案的字符串（原文含 `${hardest.title}`）。这类字段函数参数必须是 `String` 内容参数，key 命名要显式体现（如 `tackleFirstHint(hardestTitle: String, stars: Int, name: String)`），并在迁移时确认只改文案来源、不改取数逻辑。

统一 Step「越界自检」（每个任务收尾必做，期望值 = 该页 diff 行数）：

```bash
git diff -- composeApp/src/commonMain/kotlin/com/hstudy/app/ui/<路径> |
  grep -E "^[-+]" | grep -vE "^[-+]{3}" |
  grep -cE "strings\.|LocalStrings|import |val .*Strings"
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
