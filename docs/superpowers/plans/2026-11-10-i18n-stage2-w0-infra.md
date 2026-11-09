# i18n 阶段 2 W0（基础设施波）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性建全预设显示注册表（204 条固定串 → 202 个去重 key + 13 组成就 + 7 条场景）、`localizedPresetText` helper、`currentDisplayLanguage` 解析、交叉校验测试与存储边界闸门扩展；不迁任何页面。

**Architecture:** 方案 A 显示注册表：DB 保持 canonical 中文，注册表只在渲染点使用（本波不接线，W1–W9 接）。主表按 canonical 字节映射（家长改过的标题落回原样），成就按 `def.type`、场景按 `scene.id` 键控映射（二者有持久化稳定键）。`AppStrings` 聚合根**无需结构改动**——现有 data class + `stringsFor` 就是扩展点（Task 5 以 KDoc 钉住该决策）。

**Tech Stack:** Kotlin 2.1.20 / Compose Multiplatform 1.7.3（不升级）；commonTest 经 `:composeApp:testDebugUnitTest`（Android 本地 JVM）执行；Python 闸门脚本。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（已批准，commit 4ee0d02）

## Global Constraints（逐字摘自 spec，每个任务隐含包含）

- 不改 schema、不写迁移、不动 `data/**` 的任何文件（含种子、账本描述、同步 DTO、偏好键）。（§1）
- 注册表/i18n 表的任何字符串**不得**到达：DB 写入、bizKey 构造、幂等/去重比较、同步 DTO 字段、偏好键值（Ruling 25）。（§5）
- `localizedPresetText` 及一切 `strings.*` 只允许出现在渲染表达式里。（§5）
- 不升级 Compose Multiplatform / Kotlin 版本；不动 `ui/dt/DateTimeCompat.kt`、`Plurals.kt` 机制。（§1）
- W0 **不迁任何页面**；唯一改动形态是新增 i18n 基础设施与测试。（§12 表 W0 行）
- 平台文件例外仅 `AndroidFileSharer.kt` 分享标题（用户 2026-11-09 授权），属 W9，**本波不碰**。（§1）
- 每波本地四闸：`i18n_storage_boundary.py` + 自测 → `i18n_template_diff.py --branch` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`。（§12）
- **按波提交，一波一个 commit**：本计划 Task 1–4 只验证不提交，Task 5 落唯一 W0 commit。（§12）
- 推送经用户逐次批准；本计划**不含推送步骤**。PAT 只经环境变量/内联认证使用，绝不写入仓库、文档或日志。
- 不以"CI 绿"表述"双语已验证"；W0 无页面接线，无可视觉验证之物（§11 诚实声明）。

## 环境与命令（零上下文执行者必读）

- 工作目录：`D:\game\hstudy-1`（Git Bash）。Python 脚本一律带 `PYTHONIOENCODING=utf-8`。
- Gradle 必须内联两个环境变量（本机 PATH 无 java）：
  `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew …`
- 单测跑法：`:composeApp:testDebugUnitTest`（commonTest 在 Android 本地 JVM 执行；sqlite-bundled 无 desktop JVM variant，不要尝试建 desktop 测试目标）。
- 基线：117 个测试全绿（bd55eea 实测）。W0 新增 17 条（CurrentDisplayLanguageTest 4 + PresetRegistryTest 13），终态 134 条 0 失败。
- 注册表规模（2026-11-10 脚本实测，`D:/tmp_qoder/w0_registry_count.py`）：204 条来源条目 → **202 个去重 key**。两处跨组共享：`其他`（奖励分类 ∩ 错题类型）、`体育锻炼30分钟`（任务标题 ∩ 任务描述）。共享 key 只有一个译文，两用。
- 成就英文名沿用阶段 1 已钉死的两个：`启航`→"Blast Off"、`周冠军`→"Weekly Champion"（StringsCopyTest.kt:451/498）。其余文学性命名按 spec §13 随本清单复核。

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/CurrentDisplayLanguage.kt` | 新增 | 无 CompositionLocal 场景的生效语言解析（通知/分享调用点用） |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt` | 新增 | 主表 202 key + 成就/场景键控表 + `localizedPresetText` |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt` | 修改 | 仅 KDoc：钉住 AppStrings 扩展点决策（无结构改动） |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/CurrentDisplayLanguageTest.kt` | 新增 | 4 条：显式选择优先 + SYSTEM 解析（注入 systemTag，确定性） |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt` | 新增 | 13 条：交叉校验/回退/漏译/键控覆盖；内含 FakeDao |
| `scripts/i18n_storage_boundary.py` | 修改 | COPY_SOURCE 第 5 支：注册表四形态算文案 |
| `scripts/tests/i18n_storage_boundary_selftest.py` | 修改 | 登记 2 个新 fixture + 覆盖清单计数 |
| `scripts/tests/fixtures/storage_boundary/PresetCopyToSink.kt` | 新增 | 必红 fixture：注册表文案进 sink |
| `scripts/tests/fixtures/storage_boundary/PresetCopyRenderNoFp.kt` | 新增 | 必绿 fixture：注册表文案只进渲染 |

**分层决定（为什么注册表不 import ui/data 种子）：** `PresetRegistry.kt` 的 ZH 值全部手打（主表 zh==key 由构造保证），不 import `AchievementManager`/`petScenes`/仓库——i18n 保持自包含；**测试**负责把注册表与活体种子逐字对账（测试可以跨层）。`CurrentDisplayLanguage.kt` 读 `UserPreferences`（只读），默认参数引用 `AppGraph.userPreferences`（调用期求值，bootstrap 前调用 = fail-fast，与仓库风格一致）。

---

### Task 1: currentDisplayLanguage + 确定性测试

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/CurrentDisplayLanguage.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/CurrentDisplayLanguageTest.kt`

**Interfaces:**
- Consumes: `UserPreferences.appLanguage: Flow<String?>`、`UserPreferences.setAppLanguage(AppLanguage)`（data.preferences，只读/测试写）、`appLanguageFromStorage`/`AppLanguage.resolveWith`（i18n 既有）、`systemLanguageTag()`（i18n expect）、`Platform.init(filesDir, cacheDir)`。
- Produces: `suspend fun currentDisplayLanguage(prefs: UserPreferences = AppGraph.userPreferences, systemTag: String = systemLanguageTag()): DisplayLanguage`（package `com.hstudy.app.i18n`）。W9 通知接线与分享标题调用点消费它。

- [ ] **Step 1: 写测试（新 API 尚不存在 = 编译期红）**

创建 `CurrentDisplayLanguageTest.kt`，完整内容：

```kotlin
package com.hstudy.app.i18n

import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * currentDisplayLanguage 的接线证明：读存储 → appLanguageFromStorage → resolveWith(systemTag)。
 *
 * 与 LanguagePreferenceTest 共用 build/test-i18n-prefs 目录是刻意的：DataStore 每文件单实例
 * 是进程内的（UserPreferences.kt 顶层 by lazy），两个测试类无论谁先跑，单例都锚在同一路径；
 * 每条测试先写后读，顺序无关。systemTag 用参数注入而非真机 locale——GitHub runner 是 en-US，
 * 依赖真实 locale 的断言在 zh 开发机上会翻车；解析规则本身已由 AppLanguageResolveTest 逐形状钉死，
 * 这里只证接线（显式选择压过系统标签、SYSTEM 走注入标签）。
 * null（未设置）→ SYSTEM 一环由 AppLanguageResolveTest.storageRoundTrip 钉住，不在此重复。
 */
class CurrentDisplayLanguageTest {
    @AfterTest
    fun cleanup() {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
    }

    @Test
    fun explicitZhWinsOverEnglishSystem() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.ZH)
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = "en"))
    }

    @Test
    fun explicitEnWinsOverChineseSystem() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.EN)
        assertEquals(DisplayLanguage.EN, currentDisplayLanguage(prefs, systemTag = "zh"))
    }

    @Test
    fun systemPreferenceResolvesChineseTags() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.SYSTEM)
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = "zh"))
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = "zh-CN"))
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = ""))
    }

    @Test
    fun systemPreferenceResolvesOtherTags() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.SYSTEM)
        assertEquals(DisplayLanguage.EN, currentDisplayLanguage(prefs, systemTag = "en"))
        assertEquals(DisplayLanguage.EN, currentDisplayLanguage(prefs, systemTag = "fr"))
    }
}
```

- [ ] **Step 2: 跑测试确认红**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.CurrentDisplayLanguageTest"`
Expected: 编译失败，`unresolved reference: currentDisplayLanguage`。

- [ ] **Step 3: 写实现**

创建 `CurrentDisplayLanguage.kt`，完整内容：

```kotlin
package com.hstudy.app.i18n

import com.hstudy.app.AppGraph
import com.hstudy.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * 无 CompositionLocal 场景的生效语言解析：通知发出点（AppGraph bootstrap、各 Receiver、
 * ScheduleAlarmHelper）与分享标题调用点用；界面内仍读 LocalDisplayLanguage，不走这里。
 *
 * 两个参数都可注入只为测试确定性（见 CurrentDisplayLanguageTest）：生产调用一律无参，
 * 默认值在**调用期**求值——bootstrap 之前调用会 UninitializedPropertyAccessException，
 * fail-fast 与仓库其余 lateinit 约定一致。通知文案在发出时冻结（spec §8），不追更。
 */
suspend fun currentDisplayLanguage(
    prefs: UserPreferences = AppGraph.userPreferences,
    systemTag: String = systemLanguageTag(),
): DisplayLanguage = appLanguageFromStorage(prefs.appLanguage.first()).resolveWith(systemTag)
```

- [ ] **Step 4: 跑测试确认绿**

Run: 同 Step 2 命令。
Expected: 4 tests, 0 failures。

---

### Task 2: 主表——机制骨架 + 交叉校验（红）→ 204 条全量内容（绿）

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt`

**Interfaces:**
- Consumes: `DisplayLanguage`（既有）；测试只读引用 `TaskRepository.getPresets()/getSingleTaskSuggestions()`、`RewardRepository.initDefaultRewards()`、`mistakeErrorTypes`、`TaskEntity.difficultyName()`、`RewardEntity.category` 默认值。
- Produces:
  - `data class PresetCopy(val zh: String, val en: String)`
  - `val presetTextRegistry: Map<String, PresetCopy>`（202 key）
  - `fun localizedPresetText(raw: String, lang: DisplayLanguage): String`
  - `data class AchievementCopy(val title: String, val description: String)`
  - `val achievementCopy: Map<DisplayLanguage, Map<String, AchievementCopy>>`（本任务先空，Task 3 填）
  - `val petSceneCopy: Map<DisplayLanguage, Map<String, String>>`（本任务先空，Task 3 填）

- [ ] **Step 1: 写骨架（三个表全空）+ 交叉校验测试**

创建 `PresetRegistry.kt`，完整内容（主表内容在 Step 3 填入）：

```kotlin
package com.hstudy.app.i18n

/** 主表条目：zh 必须等于 canonical key 本身（由 preset() 构造保证，测试再钉一遍）。 */
data class PresetCopy(val zh: String, val en: String)

private fun preset(canonical: String, en: String): Pair<String, PresetCopy> =
    canonical to PresetCopy(canonical, en)

/**
 * 预设内容显示注册表（方案 A，spec §4）：DB 保持 canonical 中文，本表**只**在渲染点使用。
 * 任何值不得进入 DB 写入 / bizKey / 幂等比较（Ruling 25；i18n_storage_boundary.py 第 5 支覆盖）。
 * 未命中 = 原样返回：用户自建内容、家长改过的预设、历史账本描述全走这条路。
 * key 集合与种子 canonical 由 PresetRegistryTest 互为子集钉死——改种子名会打破测试，
 * 而不是静默显示未翻译文本。ZH 值手打（不 import ui/data 种子，i18n 自包含），测试负责跨层对账。
 */
val presetTextRegistry: Map<String, PresetCopy> = mapOf()

/**
 * 渲染点唯一入口：命中按语言取词，未命中原样。ZH 命中返回 zh（==key==输入），
 * 所以 ZH 态对任何输入恒等于原串——家长改过的标题在两种语言下都显示存储值。
 */
fun localizedPresetText(raw: String, lang: DisplayLanguage): String =
    presetTextRegistry[raw]?.let { if (lang == DisplayLanguage.EN) it.en else it.zh } ?: raw

/**
 * 成就文案：按 AchievementDef.type（稳定业务键）映射，不按标题字节——
 * AchievementManager.kt:63 把 title/description 写库，标题改字不得影响映射（spec §4）。
 */
data class AchievementCopy(val title: String, val description: String)

val achievementCopy: Map<DisplayLanguage, Map<String, AchievementCopy>> = mapOf(
    DisplayLanguage.ZH to mapOf(),
    DisplayLanguage.EN to mapOf(),
)

/** 宠物场景名：按 PetScene.id（PET_OWNED_SCENES 持久化的稳定键）映射。 */
val petSceneCopy: Map<DisplayLanguage, Map<String, String>> = mapOf(
    DisplayLanguage.ZH to mapOf(),
    DisplayLanguage.EN to mapOf(),
)
```

创建 `PresetRegistryTest.kt`，完整内容（键控表 7 条测试在 Task 3 追加）：

```kotlin
package com.hstudy.app.i18n

import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.RewardExchangeEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.difficultyName
import com.hstudy.app.data.local.entity.mistakeErrorTypes
import com.hstudy.app.data.repository.RewardRepository
import com.hstudy.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 注册表交叉校验（spec §10.2/10.3）：key 集合与活体种子 canonical **互为子集**（一条集合相等断言），
 * 外加回退语义与漏译守卫。种子采集全部走真实源码路径：
 * - getPresets()/getSingleTaskSuggestions() 是纯函数，不碰 DAO（UnusedTaskDao 全抛）；
 * - initDefaultRewards() 的 defaults 是函数内局部变量，用 CapturingRewardDao 接住 insertReward；
 * - 难度标签/默认分类直接调 difficultyName() / RewardEntity 默认值读出来，不手抄；
 * - FamilySyncHelper/BackupHelper 的字面量是私有代码路径，无法程序化读取：按 2026-11-10 源码
 *   逐字钉住（行号见断言内注释）。本阶段 data/** 零改动；未来若获准改这些字面量，
 *   必须同步更新此处与注册表——这是该组唯一的漂移守卫，诚实声明其上限。
 */
class PresetRegistryTest {

    private fun taskSeedCanonicals(): Set<String> {
        val repo = TaskRepository(UnusedTaskDao())
        val out = mutableSetOf<String>()
        for (template in repo.getPresets()) {
            out += template.name
            out += template.description
            out += template.ageRange
            for (task in template.tasks) {
                out += task.title
                out += task.description
            }
        }
        for (task in repo.getSingleTaskSuggestions()) {
            out += task.title
            out += task.description
        }
        return out
    }

    private suspend fun rewardSeedCanonicals(): Pair<Set<String>, Set<String>> {
        val dao = CapturingRewardDao()
        RewardRepository(dao).initDefaultRewards()
        val texts = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        for (reward in dao.inserted) {
            texts += reward.name
            texts += reward.description
            categories += reward.category
        }
        return texts to categories
    }

    @Test
    fun registryKeysEqualSeedCanonicalsExactly() = runTest {
        val (rewardTexts, rewardCategories) = rewardSeedCanonicals()
        val difficultyLabels = (1..3).map { level ->
            TaskEntity(title = "probe", category = TaskCategory.BASIC, points = 0, difficulty = level).difficultyName()
        }.toSet()
        val defaultCategory = RewardEntity(name = "probe", pointsCost = 0).category
        val deviceFallbacks = setOf("家长", "家长设备", "孩子设备") // FamilySyncHelper.kt:72/:82/:107/:190
        val diagnostics = setOf( // FamilySyncHelper.kt:140/:226 + BackupHelper.kt:60/:82/:127/:132/:133
            "不是家长控制文件", "不是孩子进度文件",
            "数据库文件尚未创建，暂时无法备份", "没有找到备份文件", "未选择文件",
            "不是有效的备份文件", "备份内容损坏",
        )
        val expected = taskSeedCanonicals() + rewardTexts + rewardCategories + defaultCategory +
            mistakeErrorTypes.toSet() + difficultyLabels + deviceFallbacks + diagnostics
        val missing = expected - presetTextRegistry.keys
        val extra = presetTextRegistry.keys - expected
        assertTrue(
            missing.isEmpty() && extra.isEmpty(),
            "注册表与种子 canonical 漂移：缺 $missing，多 $extra",
        )
    }

    @Test
    fun zhValueAlwaysEqualsCanonicalKey() {
        presetTextRegistry.forEach { (canonical, copy) ->
            assertEquals(canonical, copy.zh, "zh 值必须等于 canonical key：$canonical")
        }
    }

    @Test
    fun everyEntryHasDistinctEnglishTranslation() {
        presetTextRegistry.forEach { (canonical, copy) ->
            assertNotEquals(copy.zh, copy.en, "疑似漏译：$canonical")
        }
    }

    @Test
    fun englishValuesContainNoChinese() {
        val leftovers = presetTextRegistry.values.flatMap { it.en.toList() }.filter { it in '一'..'鿿' }
        assertTrue(leftovers.isEmpty(), "英文译文残留汉字：${leftovers.distinct()}")
    }

    @Test
    fun unknownTextFallsThroughUnchanged() {
        val userContent = listOf("家长自定义标题", "完成任务获得积分", "", "My own reward")
        for (raw in userContent) {
            assertEquals(raw, localizedPresetText(raw, DisplayLanguage.EN), "EN 未命中必须原样：$raw")
            assertEquals(raw, localizedPresetText(raw, DisplayLanguage.ZH), "ZH 未命中必须原样：$raw")
        }
    }

    @Test
    fun knownCanonicalResolvesPerLanguage() {
        assertEquals("完成当天作业", localizedPresetText("完成当天作业", DisplayLanguage.ZH))
        assertEquals("Finish today's homework", localizedPresetText("完成当天作业", DisplayLanguage.EN))
        // 跨组共享 key（奖励分类/错题类型同为"其他"）只有一个译文
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))
        // 标题与描述共享 key（"体育锻炼30分钟"）同一译文两用
        assertEquals("Exercise for 30 minutes", localizedPresetText("体育锻炼30分钟", DisplayLanguage.EN))
    }
}

/** getPresets()/getSingleTaskSuggestions() 是纯函数，不触 DAO；任何调用都是测试写错了。 */
private class UnusedTaskDao : TaskDao {
    override fun getAllActiveTasks(): Flow<List<TaskEntity>> = throw UnsupportedOperationException()
    override suspend fun getActiveTaskCount(): Int = throw UnsupportedOperationException()
    override suspend fun getAllActiveTasksSnapshot(): List<TaskEntity> = throw UnsupportedOperationException()
    override fun getTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>> = throw UnsupportedOperationException()
    override suspend fun getTaskById(id: Long): TaskEntity? = throw UnsupportedOperationException()
    override suspend fun insert(task: TaskEntity): Long = throw UnsupportedOperationException()
    override suspend fun update(task: TaskEntity) = throw UnsupportedOperationException()
    override suspend fun softDelete(id: Long) = throw UnsupportedOperationException()
    override suspend fun delete(task: TaskEntity) = throw UnsupportedOperationException()
}

/** 接住 initDefaultRewards() 的 27 条写入（defaults 是函数内局部变量，只能从 DAO 侧捕获）。 */
private class CapturingRewardDao : RewardDao {
    val inserted = mutableListOf<RewardEntity>()
    override suspend fun insertReward(reward: RewardEntity): Long {
        inserted += reward
        return inserted.size.toLong()
    }
    override suspend fun getActiveRewardCount(): Int = inserted.size
    override fun getAllActiveRewards(): Flow<List<RewardEntity>> = throw UnsupportedOperationException()
    override suspend fun getRewardById(id: Long): RewardEntity? = throw UnsupportedOperationException()
    override suspend fun updateReward(reward: RewardEntity) = throw UnsupportedOperationException()
    override suspend fun softDeleteReward(id: Long) = throw UnsupportedOperationException()
    override suspend fun insertExchange(exchange: RewardExchangeEntity): Long = throw UnsupportedOperationException()
    override fun getAllExchanges(): Flow<List<RewardExchangeEntity>> = throw UnsupportedOperationException()
    override suspend fun getExchangesByReward(rewardId: Long): Flow<List<RewardExchangeEntity>> = throw UnsupportedOperationException()
}
```

- [ ] **Step 2: 跑测试确认红**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.PresetRegistryTest"`
Expected: 2 条失败——`registryKeysEqualSeedCanonicalsExactly`（缺全部 202 个 key）、`knownCanonicalResolvesPerLanguage`（空表回退原样，EN 断言不等）。其余 4 条在空表上空过成立，Step 3 后才实质生效。

- [ ] **Step 3: 填入主表全量内容（204 条来源 → 202 key）**

把 `PresetRegistry.kt` 里的 `val presetTextRegistry: Map<String, PresetCopy> = mapOf()` 整行替换为下面的完整声明（译文逐条人工给定；分组顺序与 spec §6 表一致；两处共享 key 只出现一次，注释标明）：

```kotlin
val presetTextRegistry: Map<String, PresetCopy> = mapOf(
    // ===== 预设任务标题（55 条去重；TaskRepository.getPresets() + getSingleTaskSuggestions()）=====
    preset("完成当天作业", "Finish today's homework"),
    preset("整理书包", "Pack the school bag"),
    preset("朗读课文", "Read the text aloud"),
    preset("练字15分钟", "Practice handwriting for 15 minutes"),
    preset("背诵一首古诗", "Recite a classical poem"),
    preset("阅读20分钟", "Read for 20 minutes"),
    preset("口算练习", "Mental math practice"),
    preset("讲一个故事", "Tell a story"),
    preset("认识新字词", "Learn new words"),
    preset("早睡早起", "Early to bed, early to rise"),
    preset("自己穿衣", "Get dressed by myself"),
    preset("帮忙做家务", "Help with chores"),
    preset("礼貌问好", "Greet people politely"),
    preset("收拾玩具", "Tidy up the toys"),
    preset("错题订正工整", "Correct mistakes neatly"),
    preset("课前预习", "Preview before class"),
    preset("复习回顾", "Review and recap"),
    preset("英语单词听写", "English word dictation"),
    preset("完成课外练习", "Finish extra exercises"),
    preset("错题原因分析", "Analyze mistake causes"),
    preset("阅读30分钟", "Read for 30 minutes"),
    preset("写作练习", "Writing practice"),
    preset("科学小实验", "A little science experiment"),
    preset("每日阅读打卡", "Daily reading check-in"),
    preset("帮助同学", "Help classmates"),
    preset("积极参与课堂", "Join in actively in class"),
    preset("整理房间", "Tidy my room"),
    preset("坚持运动", "Keep exercising"),
    preset("课前预习笔记", "Preview and take notes"),
    preset("课后复习总结", "Review and summarize after class"),
    preset("错题深度分析", "Deep mistake analysis"),
    preset("英语晨读", "English morning reading"),
    preset("专题突破练习", "Targeted topic practice"),
    preset("整理知识框架", "Build a knowledge framework"),
    preset("英语听说训练", "English listening and speaking practice"),
    preset("深度阅读45分钟", "Deep reading for 45 minutes"),
    preset("难题挑战", "Hard problem challenge"),
    preset("番茄钟专注2小时", "Two hours of pomodoro focus"),
    preset("自主制定学习计划", "Make my own study plan"),
    preset("体育锻炼30分钟", "Exercise for 30 minutes"), // 与任务描述共享 key（getSingleTaskSuggestions 的"坚持运动"描述同串）
    preset("远离手机2小时", "Stay off the phone for 2 hours"),
    preset("早睡不熬夜", "Sleep early, no staying up late"),
    preset("课后复习", "Review after class"),
    preset("错题订正", "Correct mistakes"),
    preset("背诵默写", "Recite and write from memory"),
    preset("整理笔记", "Organize class notes"),
    preset("课外阅读", "Reading beyond class"),
    preset("专项练习", "Targeted practice"),
    preset("思维导图", "Mind map"),
    preset("英语听说", "English listening and speaking"),
    preset("科学实验", "Science experiment"),
    preset("做家务", "Do chores"),
    preset("礼貌待人", "Be polite to others"),
    preset("专注学习", "Study without distractions"),
    preset("帮助他人", "Help others"),
    // ===== 预设任务描述（60 条去重；"体育锻炼30分钟"已在标题组，此处 59 条）=====
    preset("按时完成老师布置的作业", "Finish the teacher's homework on time"),
    preset("自己整理明天的书包", "Pack tomorrow's school bag by myself"),
    preset("大声朗读一篇课文", "Read one text aloud"),
    preset("认真练字15分钟", "Practice handwriting carefully for 15 minutes"),
    preset("背诵并默写一首古诗", "Recite a classical poem and write it from memory"),
    preset("课外阅读20分钟", "Read beyond class for 20 minutes"),
    preset("完成一页口算练习", "Finish one page of mental math"),
    preset("给家人讲一个完整的故事", "Tell my family a complete story"),
    preset("学习并记住5个新字词", "Learn and remember 5 new words"),
    preset("21:00前上床，7:00前起床", "In bed by 21:00, up by 7:00"),
    preset("自己选择并穿好衣服", "Choose my clothes and get dressed by myself"),
    preset("帮忙擦桌子、扫地等", "Help wipe tables, sweep the floor, and more"),
    preset("主动跟长辈、老师问好", "Greet elders and teachers without being asked"),
    preset("玩完后自己收拾整理玩具", "Tidy up the toys myself after playing"),
    preset("订正错题，书写工整", "Correct mistakes with neat handwriting"),
    preset("预习明天的课程内容", "Preview tomorrow's lessons"),
    preset("复习当天所学内容", "Review what was learned today"),
    preset("听写并订正10个单词", "Take dictation of 10 words and correct them"),
    preset("完成1套课外练习", "Finish one set of extra exercises"),
    preset("写出错误点和正确思路", "Write down the error and the correct approach"),
    preset("课外阅读至少30分钟", "Read beyond class for at least 30 minutes"),
    preset("100字起步写作练习", "Writing practice starting from 100 characters"),
    preset("动手做一个科学小实验", "Do a little science experiment hands-on"),
    preset("坚持每天阅读", "Keep reading every day"),
    preset("主动帮助其他同学", "Help other classmates without being asked"),
    preset("上课积极发言参与", "Speak up and take part actively in class"),
    preset("整理自己的房间保持整洁", "Tidy my room and keep it neat"),
    preset("跳绳、跑步等运动20分钟", "Exercise for 20 minutes: jump rope, running, and more"),
    preset("高质量完成所有作业", "Finish all homework to a high standard"),
    preset("预习并做笔记", "Preview and take notes"),
    preset("总结当天知识点", "Summarize today's key points"),
    preset("分析错因+举一反三", "Analyze the error and try variations"),
    preset("晨读英语20分钟", "Read English aloud for 20 minutes in the morning"),
    preset("针对薄弱科目做专项练习", "Do targeted practice for weak subjects"),
    preset("用思维导图整理一章知识", "Organize one chapter with a mind map"),
    preset("30分钟英语听力或口语练习", "30 minutes of English listening or speaking practice"),
    preset("阅读并写读后感", "Read and write a reading response"),
    preset("独立攻克3道压轴难题", "Solve 3 of the hardest problems on my own"),
    preset("累计完成4个番茄钟", "Complete 4 pomodoros in total"),
    preset("制定并执行周学习计划", "Make and follow a weekly study plan"),
    preset("每天坚持运动", "Keep exercising every day"),
    preset("学习时段不玩手机", "No phone during study time"),
    preset("22:30前睡觉保证睡眠", "Asleep by 22:30 to get enough sleep"),
    preset("复习当天所学知识", "Review the knowledge learned today"),
    preset("订正错题并分析原因", "Correct mistakes and analyze the causes"),
    preset("背诵课文/单词并默写", "Recite texts or words and write them from memory"),
    preset("整理课堂笔记使其清晰", "Organize class notes so they are clear"),
    preset("课外阅读30分钟以上", "Read beyond class for more than 30 minutes"),
    preset("完成一篇写作练习", "Finish one writing exercise"),
    preset("针对薄弱点做专项练习", "Do targeted practice for weak points"),
    preset("用思维导图梳理知识结构", "Map out the knowledge structure with a mind map"),
    preset("英语听力或口语训练", "English listening or speaking practice"),
    preset("独立攻克几道难题", "Solve a few hard problems on my own"),
    preset("规律作息不熬夜", "Keep a regular schedule and don't stay up late"),
    preset("主动分担家务劳动", "Share the housework without being asked"),
    preset("主动问好、文明礼貌", "Greet people proactively and be courteous"),
    preset("保持房间整洁有序", "Keep my room tidy and orderly"),
    preset("学习时段不分心玩手机", "No phone distractions during study time"),
    preset("主动帮助同学或家人", "Help classmates or family without being asked"),
    // ===== 模板名/描述/年龄段（3 套 × 3）=====
    preset("小学低年级", "Lower primary"),
    preset("适合1-3年级，以基础习惯养成为主，积分值较低易于达成", "For grades 1-3: builds basic habits, with low points that are easy to earn"),
    preset("6-9岁", "Ages 6-9"),
    preset("小学高年级", "Upper primary"),
    preset("适合4-6年级，注重自主学习能力和深度思考", "For grades 4-6: focuses on independent learning and deep thinking"),
    preset("9-12岁", "Ages 9-12"),
    preset("初中", "Middle school"),
    preset("适合初中生，注重高效学习、自主管理和深度思考", "For middle schoolers: efficient studying, self-management, and deep thinking"),
    preset("12-15岁", "Ages 12-15"),
    // ===== 预设奖励名（27 条；RewardRepository.initDefaultRewards()）=====
    preset("看一集动画片", "Watch one cartoon episode"),
    preset("打游戏30分钟", "Play games for 30 minutes"),
    preset("打游戏60分钟", "Play games for 60 minutes"),
    preset("玩桌游一次", "Play a board game once"),
    preset("看一场电影", "Watch a movie"),
    preset("买一份零食", "Buy a snack"),
    preset("买一个冰淇淋", "Buy an ice cream"),
    preset("自己点一次外卖", "Order takeout myself once"),
    preset("选一家餐厅吃大餐", "Pick a restaurant for a big meal"),
    preset("骑自行车兜风", "Go for a bike ride"),
    preset("去图书馆/书店", "Visit the library/bookstore"),
    preset("去公园玩半天", "Spend half a day at the park"),
    preset("周末短途旅行", "A weekend short trip"),
    preset("免一次家务", "Skip one chore"),
    preset("选择今晚菜品", "Choose tonight's menu"),
    preset("晚睡30分钟", "Stay up 30 minutes later"),
    preset("晚睡60分钟", "Stay up 60 minutes later"),
    preset("当一天小家长", "Be the little parent for a day"),
    preset("买一套卡片", "Buy a card set"),
    preset("买一本课外书", "Buy a book for fun"),
    preset("买文具/小玩具", "Buy stationery/a small toy"),
    preset("买一件喜欢的衣服", "Buy a piece of clothing I like"),
    preset("和朋友视频30分钟", "Video chat with a friend for 30 minutes"),
    preset("邀请朋友来家玩", "Invite a friend over"),
    preset("兴趣班体验课", "A trial hobby class"),
    preset("实现一个小愿望", "Grant a small wish"),
    preset("神秘大奖", "Mystery grand prize"),
    // ===== 预设奖励描述（27 条）=====
    preset("看一集喜欢的动画片", "Watch one episode of a favorite cartoon"),
    preset("玩30分钟喜欢的游戏", "Play a favorite game for 30 minutes"),
    preset("畅享60分钟游戏时间", "Enjoy 60 minutes of game time"),
    preset("和家人玩一次桌游", "Play a board game with the family once"),
    preset("影院或家庭影院看一场电影", "Watch a movie at the cinema or at home"),
    preset("买一份喜欢的零食小吃", "Buy a favorite snack"),
    preset("买一个冰淇淋或小蛋糕", "Buy an ice cream or a small cake"),
    preset("自己选一家店点外卖", "Pick a restaurant and order takeout myself"),
    preset("选一家喜欢的餐厅吃大餐", "Pick a favorite restaurant for a big meal"),
    preset("出去骑自行车兜风", "Go out for a bike ride"),
    preset("去图书馆或书店逛一逛", "Browse the library or bookstore"),
    preset("去公园或游乐场玩半天", "Spend half a day at the park or playground"),
    preset("和家人一次周末短途旅行", "A short weekend trip with the family"),
    preset("可以免除一次家务", "Get out of one chore"),
    preset("决定今晚吃什么", "Decide what's for dinner tonight"),
    preset("可以晚睡30分钟", "Stay up 30 minutes later"),
    preset("可以晚睡1小时", "Stay up 1 hour later"),
    preset("决定一天的家庭活动安排", "Plan the family's day of activities"),
    preset("集齐一套喜欢的卡片", "Collect a full set of favorite cards"),
    preset("去书店选一本喜欢的课外书", "Pick a favorite book at the bookstore"),
    preset("买一个喜欢的文具或小玩具", "Buy a favorite stationery item or small toy"),
    preset("预算内买一件喜欢的衣服", "Buy a piece of clothing I like within budget"),
    preset("和好朋友视频通话30分钟", "Video call a good friend for 30 minutes"),
    preset("邀请好朋友来家里玩", "Invite a good friend over to play"),
    preset("参加一次兴趣班体验课", "Attend one trial hobby class"),
    preset("实现一个合理的小愿望", "Grant one reasonable small wish"),
    preset("爸爸妈妈准备的神秘大奖", "A mystery grand prize prepared by Mom and Dad"),
    // ===== 奖励分类（7 条；"其他"同时是 RewardEntity 默认值与错题类型，共享一个 key）=====
    preset("娱乐", "Entertainment"),
    preset("美食", "Food"),
    preset("出行", "Outings"),
    preset("特权", "Privileges"),
    preset("实物", "Items"),
    preset("社交", "Social"),
    preset("其他", "Other"), // 与错题错误类型共享 key
    // ===== 错题错误类型（6 条；"其他"已在分类组，此处 5 条；MistakeEntity.kt:19）=====
    preset("粗心马虎", "Careless mistakes"),
    preset("概念不清", "Unclear concepts"),
    preset("方法不会", "Unknown method"),
    preset("审题错误", "Misread the question"),
    preset("计算错误", "Calculation errors"),
    // ===== 难度标签（3 条；TaskEntity.difficultyName()，阶段 1 §11 既定译名）=====
    preset("普通", "Normal"),
    preset("进阶", "Advanced"),
    preset("挑战", "Challenge"),
    // ===== 设备名回退（3 条；FamilySyncHelper.kt:72/:82/:107/:190，渲染侧映射，canonical 写入不动）=====
    preset("家长", "Parent"),
    preset("家长设备", "Parent device"),
    preset("孩子设备", "Child device"),
    // ===== 固定诊断文案（7 条；FamilySyncHelper.kt:140/:226 + BackupHelper.kt:60/:82/:127/:132/:133）=====
    preset("不是家长控制文件", "Not a parent control file"),
    preset("不是孩子进度文件", "Not a child progress file"),
    preset("数据库文件尚未创建，暂时无法备份", "The database file hasn't been created yet, so backup is unavailable"),
    preset("没有找到备份文件", "No backup file found"),
    preset("未选择文件", "No file selected"),
    preset("不是有效的备份文件", "Not a valid backup file"),
    preset("备份内容损坏", "The backup content is corrupted"),
)
```

- [ ] **Step 4: 跑测试确认绿**

Run: 同 Step 2 命令。
Expected: 6 tests, 0 failures。若 `registryKeysEqualSeedCanonicalsExactly` 报"缺/多"，按报错里的集合逐字核对种子与表（不要改断言迁就表）。

---

### Task 3: 键控表——成就 13 组 + 场景 7 条（红 → 绿）

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt`（两个空表填全）
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt`（追加 7 条）

**Interfaces:**
- Consumes: `AchievementManager.allAchievements`（`ui.common`，只读）、`petScenes`（`ui.pet`，只读）——仅测试引用。
- Produces: `achievementCopy` / `petSceneCopy` 全量内容（签名不变）。W4/W7/W9 渲染点消费。

- [ ] **Step 1: 追加键控表测试（对空表 = 红）**

在 `PresetRegistryTest.kt` 的 import 区追加两行：

```kotlin
import com.hstudy.app.ui.common.AchievementManager
import com.hstudy.app.ui.pet.petScenes
```

在 `PresetRegistryTest` 类体内（`knownCanonicalResolvesPerLanguage` 之后）追加：

```kotlin
    @Test
    fun achievementCopyCoversEveryDefTypeInBothLanguages() {
        val types = AchievementManager.allAchievements.map { it.type }.toSet()
        assertEquals(types, achievementCopy[DisplayLanguage.ZH]!!.keys, "ZH 成就键集与定义漂移")
        assertEquals(types, achievementCopy[DisplayLanguage.EN]!!.keys, "EN 成就键集与定义漂移")
    }

    @Test
    fun achievementZhCopyMatchesDefinitionExactly() {
        for (def in AchievementManager.allAchievements) {
            val copy = achievementCopy[DisplayLanguage.ZH]!![def.type]!!
            assertEquals(def.title, copy.title, "ZH 成就标题漂移：${def.type}")
            assertEquals(def.description, copy.description, "ZH 成就描述漂移：${def.type}")
        }
    }

    @Test
    fun achievementEnglishCopyIsTranslated() {
        val zh = achievementCopy[DisplayLanguage.ZH]!!
        val en = achievementCopy[DisplayLanguage.EN]!!
        for (type in zh.keys) {
            assertNotEquals(zh[type]!!.title, en[type]!!.title, "成就标题疑似漏译：$type")
            assertNotEquals(zh[type]!!.description, en[type]!!.description, "成就描述疑似漏译：$type")
        }
        val enChars = en.values.flatMap { listOf(it.title, it.description) }.flatMap { it.toList() }
        assertTrue(enChars.none { it in '一'..'鿿' }, "英文成就文案残留汉字")
    }

    @Test
    fun achievementEnglishNamesStayConsistentWithStageOnePins() {
        // 阶段 1 taskListInterpolatedCopyPinsBothLanguages 已把这两个名字钉进插值断言
        // （StringsCopyTest.kt:451/498）；注册表必须沿用，否则同一成就两处英文漂移。
        assertEquals("Blast Off", achievementCopy[DisplayLanguage.EN]!!["first_checkin"]!!.title)
        assertEquals("Weekly Champion", achievementCopy[DisplayLanguage.EN]!!["streak_7"]!!.title)
    }

    @Test
    fun petSceneCopyCoversEverySceneIdInBothLanguages() {
        val ids = petScenes.map { it.id }.toSet()
        assertEquals(ids, petSceneCopy[DisplayLanguage.ZH]!!.keys, "ZH 场景键集与定义漂移")
        assertEquals(ids, petSceneCopy[DisplayLanguage.EN]!!.keys, "EN 场景键集与定义漂移")
    }

    @Test
    fun petSceneZhCopyMatchesSceneNameExactly() {
        for (scene in petScenes) {
            assertEquals(scene.name, petSceneCopy[DisplayLanguage.ZH]!![scene.id], "ZH 场景名漂移：${scene.id}")
        }
    }

    @Test
    fun petSceneEnglishCopyIsTranslated() {
        val zh = petSceneCopy[DisplayLanguage.ZH]!!
        val en = petSceneCopy[DisplayLanguage.EN]!!
        for (id in zh.keys) {
            assertNotEquals(zh[id], en[id], "场景名疑似漏译：$id")
        }
        assertTrue(en.values.flatMap { it.toList() }.none { it in '一'..'鿿' }, "英文场景名残留汉字")
        assertEquals("My Little Home", en["home"])
    }
```

- [ ] **Step 2: 跑测试确认红**

Run: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.i18n.PresetRegistryTest"`
Expected: 新增 7 条中 6 条红（空键集断言失败；`!!` 取不到值抛 NPE 亦计失败）；`achievementEnglishCopyIsTranslated` 在空表上空过成立，Step 3 填全后才实质生效。Task 2 的 6 条仍绿。

- [ ] **Step 3: 填全两个键控表**

把 `PresetRegistry.kt` 中的空 `achievementCopy` 声明整体替换为：

```kotlin
val achievementCopy: Map<DisplayLanguage, Map<String, AchievementCopy>> = mapOf(
    DisplayLanguage.ZH to mapOf(
        "first_checkin" to AchievementCopy("启航", "首次完成打卡"),
        "streak_3" to AchievementCopy("三日不辍", "连续打卡3天"),
        "streak_7" to AchievementCopy("周冠军", "连续打卡7天"),
        "checkin_100" to AchievementCopy("百日积累", "累计打卡100次"),
        "perfect_day" to AchievementCopy("百分学霸", "单日完成全部任务"),
        "mistake_50" to AchievementCopy("错题克星", "掌握50道错题"),
        "focus_600" to AchievementCopy("专注达人", "累计专注10小时"),
        "habit_complete" to AchievementCopy("习惯达人", "完成一个21天挑战"),
        "mistake_100" to AchievementCopy("错题大师", "掌握100道错题"),
        "focus_1800" to AchievementCopy("专注大师", "累计专注30小时"),
        "review_30" to AchievementCopy("复习达人", "累计复习错题30次"),
        "variation_5" to AchievementCopy("举一反三", "完成5次举一反三思考"),
        "training_10" to AchievementCopy("训练达人", "完成10次专注力训练"),
    ),
    DisplayLanguage.EN to mapOf(
        "first_checkin" to AchievementCopy("Blast Off", "Complete your first check-in"),
        "streak_3" to AchievementCopy("Three-Day Streak", "Check in 3 days in a row"),
        "streak_7" to AchievementCopy("Weekly Champion", "Check in 7 days in a row"),
        "checkin_100" to AchievementCopy("100 Days Strong", "Check in 100 times in total"),
        "perfect_day" to AchievementCopy("Perfect Day", "Finish every task in a single day"),
        "mistake_50" to AchievementCopy("Mistake Buster", "Master 50 mistakes"),
        "focus_600" to AchievementCopy("Focus Pro", "Focus for 10 hours in total"),
        "habit_complete" to AchievementCopy("Habit Hero", "Complete a 21-day challenge"),
        "mistake_100" to AchievementCopy("Mistake Master", "Master 100 mistakes"),
        "focus_1800" to AchievementCopy("Focus Master", "Focus for 30 hours in total"),
        "review_30" to AchievementCopy("Review Pro", "Review mistakes 30 times in total"),
        "variation_5" to AchievementCopy("Variation", "Complete 5 variation reflections"),
        "training_10" to AchievementCopy("Training Pro", "Complete 10 focus training sessions"),
    ),
)
```

把空 `petSceneCopy` 声明整体替换为：

```kotlin
val petSceneCopy: Map<DisplayLanguage, Map<String, String>> = mapOf(
    DisplayLanguage.ZH to mapOf(
        "home" to "我的小窝",
        "forest" to "森林小屋",
        "beach" to "阳光海滩",
        "candy" to "糖果乐园",
        "castle" to "梦幻城堡",
        "space" to "浩瀚星空",
        "rainbow" to "彩虹天堂",
    ),
    DisplayLanguage.EN to mapOf(
        "home" to "My Little Home",
        "forest" to "Forest Cabin",
        "beach" to "Sunny Beach",
        "candy" to "Candy Land",
        "castle" to "Dream Castle",
        "space" to "Starry Space",
        "rainbow" to "Rainbow Paradise",
    ),
)
```

- [ ] **Step 4: 跑测试确认绿**

Run: 同 Step 2 命令。
Expected: 13 tests, 0 failures。

---

### Task 4: 存储边界闸门认识注册表文案（红 → 绿）

**为什么属于 W0：** spec §5/§10.6 声称"注册表字符串不进写库由闸门保证"。现有 `COPY_SOURCE` 只认 `strings.*`/`*Strings.*`/`LocalStrings.current.*` 四支，**不认识** `localizedPresetText(...)`/三个表名——W1 起这些形态会出现在渲染点，若有人把它写进 sink，闸门静默放行。本任务补第 5 支 + 正反 fixture，让该保证从 W0 起为真。只改 `scripts/`，不碰 `data/**`。

**Files:**
- Modify: `scripts/i18n_storage_boundary.py:241-246`（COPY_SOURCE_INNER）
- Modify: `scripts/tests/i18n_storage_boundary_selftest.py`（EXPECTED_CASES + 覆盖清单计数）
- Create: `scripts/tests/fixtures/storage_boundary/PresetCopyToSink.kt`
- Create: `scripts/tests/fixtures/storage_boundary/PresetCopyRenderNoFp.kt`

**Interfaces:**
- Consumes: Task 2/3 的四个名字（`localizedPresetText`/`presetTextRegistry`/`achievementCopy`/`petSceneCopy`）作为正则锚点。
- Produces: 闸门对注册表文案形态的阻塞覆盖（CI 既有步骤自动生效，无需改 workflow）。

- [ ] **Step 1: 写两个 fixture（行号被自测钉死，逐字照抄）**

创建 `scripts/tests/fixtures/storage_boundary/PresetCopyToSink.kt`（必红；sink 调用在第 20/22/24/26 行）：

```kotlin
// 通道 A 扩展（W0 第 5 支）：阶段 2 注册表文案形态进 sink 必须报红。
// 本文件**不参与编译**（scripts/tests/fixtures 不属于任何 Kotlin 源集），
// 覆盖 localizedPresetText / presetTextRegistry / achievementCopy / petSceneCopy 四种新文案来源；
// 见 scripts/i18n_storage_boundary.py 的 COPY_SOURCE_INNER 与自测 EXPECTED_CASES。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.achievementCopy
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.petSceneCopy
import com.hstudy.app.i18n.presetTextRegistry

@Composable
fun PresetCopyToSinkScreen() {
    val app = AppGraph
    val lang = DisplayLanguage.EN
    // A1 渲染 helper 直接进 sink
    app.taskRepository.addTask(TaskEntity(title = localizedPresetText("完成当天作业", lang), category = TaskCategory.BASIC, points = 3))
    // A2 主表直查进 sink
    app.rewardRepository.addReward(RewardEntity(name = presetTextRegistry["看一集动画片"]!!.en, pointsCost = 80, iconEmoji = "📺"))
    // A3 成就键控表进 sink
    app.habitRepository.tryUnlockAchievement("first_checkin", achievementCopy[lang]!!["first_checkin"]!!.title, achievementCopy[lang]!!["first_checkin"]!!.description, "🚀")
    // A4 场景键控表进 sink
    app.userPreferences.setDeviceName(petSceneCopy[lang]!!["home"]!!)
}
```

创建 `scripts/tests/fixtures/storage_boundary/PresetCopyRenderNoFp.kt`（必绿）：

```kotlin
// 反向样本（必须绿）：W0 注册表文案只流向渲染面，合规形态一条都不许判红。
// 本文件**不参与编译**；与 PresetCopyToSink.kt 互为正反证据。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.achievementCopy
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.petSceneCopy

@Composable
fun PresetCopyRenderScreen() {
    val app = AppGraph
    val lang = LocalDisplayLanguage.current
    // 1) helper 只进 Text
    Text(localizedPresetText(task.title, lang))
    // 2) 键控表只进渲染（回退到 canonical 原值）
    Text(achievementCopy[lang]?.get(def.type)?.title ?: def.title)
    Text(petSceneCopy[lang]?.get(scene.id) ?: scene.name)
    // 3) 文案赋值后只进渲染（污点链的合规终点）
    val localized = localizedPresetText(task.title, lang)
    Text(localized)
    // 4) 用户输入进 sink：与注册表无关
    app.taskRepository.addTask(TaskEntity(title = draftTitle.value, category = TaskCategory.BASIC, points = 3))
    // 5) canonical 原值进 sink 仍是合规形态（Ruling 25：存规范值，渲染时本地化）
    app.habitRepository.tryUnlockAchievement(def.type, def.title, def.description, def.emoji)
}
```

- [ ] **Step 2: 登记进 EXPECTED_CASES**

在 `i18n_storage_boundary_selftest.py` 的 `GroupHandleCopy.kt` 条目之后、`# ---- 必须报绿的四条 ----` 注释之前插入：

```python
    # ---- W0 新增：阶段 2 显示注册表文案形态（COPY_SOURCE 第 5 支）----
    ("PresetCopyToSink.kt", 1, 4,
     {"addTask", "addReward", "tryUnlockAchievement", "setDeviceName"},
     [DIRECT],
     [("20", "addTask"), ("22", "addReward"), ("24", "tryUnlockAchievement"), ("26", "setDeviceName")]),
```

把必绿区的注释改为 `# ---- 必须报绿的五条 ----` 并在 `CommentedSinkNoFp.kt` 条目后追加：

```python
    ("PresetCopyRenderNoFp.kt", 0, 0, set(), [], []),
```

同步文件头 docstring 的覆盖清单计数：`必红（11）` → `必红（12）`（行尾补 `/ **注册表文案四形态（W0）**`），`必绿（4）` → `必绿（5）`。

- [ ] **Step 3: 跑自测确认红**

Run: `PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py`
Expected: FAIL——`PresetCopyToSink.kt 退出码 1（通道必须红）  <<< 实际 0`（及命中数/行号连带失败）；`PresetCopyRenderNoFp.kt` 绿。其余既有用例不受影响。

- [ ] **Step 4: 给 COPY_SOURCE_INNER 加第 5 支**

在 `scripts/i18n_storage_boundary.py` 的 `COPY_SOURCE_INNER` 定义处（:241-246），把末支 `r"|\bLocalStrings\.current(?:\.\w+){2,}"` 之后追加一支（并在定义上方补注释）：

```python
# W0 第 5 支：阶段 2 显示注册表的四种文案形态。localizedPresetText( 是渲染 helper，
# 三个表名是直查形态；与 strings.* 一样只许进渲染面，进 sink 即违规（stage2 spec §5）。
# 正反 fixture：PresetCopyToSink.kt（必红）/ PresetCopyRenderNoFp.kt（必绿）。
# Canonical*Text.* 仍天然豁免（名字不匹配任何一支）。
COPY_SOURCE_INNER = (
    r"\bstrings\.[A-Za-z]\w*"
    r"|\bAppStrings(?:Zh|En)\.\w+"
    r"|\b(?!LocalStrings\b)[A-Za-z]\w*Strings(?:Zh|En)?\.\w+"
    r"|\bLocalStrings\.current(?:\.\w+){2,}"
    r"|\b(?:localizedPresetText\s*\(|presetTextRegistry\b|achievementCopy\b|petSceneCopy\b)"
)
```

- [ ] **Step 5: 跑自测 + 真实树闸门确认绿**

Run: `PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py`
Expected: exit 0，全部 OK（含新 fixture 的行号逐条相符、sink 双向对账、真实树 exit 0 且 >100 文件）。
Run: `PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py`
Expected: exit 0——PresetRegistry.kt/CurrentDisplayLanguage.kt 在扫描范围内且无自误报（表声明的 RHS 是 `mapOf(`，不含文案来源；helper 定义不是赋值句）。

---

### Task 5: AppStrings 扩展点 KDoc + 本地四闸 + 唯一 W0 commit

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt:6`（仅 KDoc）

**Interfaces:**
- Consumes: Task 1–4 全部产物。
- Produces: 唯一 W0 commit（spec §12：一波一个 commit）。**不推送**——推送由用户逐次批准。

- [ ] **Step 1: 钉住扩展点决策（无结构改动）**

把 `Strings.kt:6` 的单行 KDoc：

```kotlin
/** 跨页面文案聚合根：每页一个子表，后续任务按页扩展。 */
```

替换为：

```kotlin
/**
 * 跨页面文案聚合根：每页一个子表，后续任务按页扩展。
 *
 * 阶段 2 扩展点（W0 确认，无需结构改动）：新域 = 新建 XxxStrings data class + Zh/En 两份表，
 * 本 data class 加一个字段、AppStringsZh/AppStringsEn 各加一行，stringsFor 的穷举 when 自动覆盖。
 * 预设内容（种子任务/奖励/成就/场景等）不走本聚合根，走 PresetRegistry.kt 的显示注册表（方案 A）。
 */
```

- [ ] **Step 2: 本地四闸全跑（spec §12 顺序）**

Run（逐条，全部 exit 0）：

```bash
PYTHONIOENCODING=utf-8 python scripts/i18n_storage_boundary.py
PYTHONIOENCODING=utf-8 python scripts/tests/i18n_storage_boundary_selftest.py
PYTHONIOENCODING=utf-8 python scripts/i18n_template_diff.py --branch
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:assembleDebug
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest
```

Expected: 边界闸门 exit 0；自测全 OK；等价闸门 393/393 exit 0（W0 未迁页面，BRANCH_PAGES 无需登记）；APK 构建成功；**134 tests, 0 failures**（基线 117 + 新增 17）。任何一闸红：修到绿再继续，不许带红提交。

- [ ] **Step 3: 落唯一 W0 commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/CurrentDisplayLanguage.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt \
        composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/CurrentDisplayLanguageTest.kt \
        composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/PresetRegistryTest.kt \
        scripts/i18n_storage_boundary.py \
        scripts/tests/i18n_storage_boundary_selftest.py \
        scripts/tests/fixtures/storage_boundary/PresetCopyToSink.kt \
        scripts/tests/fixtures/storage_boundary/PresetCopyRenderNoFp.kt
git commit -m "feat(i18n): 阶段 2 W0——显示注册表全量内容（202 key+13 成就+7 场景）、语言解析 helper、交叉校验与边界闸门第 5 支"
git status
```

Expected: commit 成功，工作树干净（build/ 产物被 .gitignore 覆盖）。**到此为止，不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译、134 单测、字节等价、存储边界（含新形态正反 fixture）、注册表与种子互为子集。
- 不可自动证明：英文译文的语言质量（人工复核清单在本计划 Task 2/3 的表里，随审查逐条过）、未来渲染点接线的视觉呈现（W1 起）。
- FamilySyncHelper/BackupHelper 组是手钉字面量（私有路径无法程序化读取），漂移守卫上限已在测试 KDoc 声明。
- W0 不迁页面，设备上无任何可见变化；"双语已验证"之类的表述不适用于本波。