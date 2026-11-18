# HStudy v3.2.0 — Analytics Firebase 集成 + 双语 README + Sponsors 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已批准 spec 集成 Google Analytics for Firebase（Android + iOS），发布 v3.2.0，README 重写为中英双语并加入 GitHub Sponsors 捐赠入口。

**Architecture:** Firebase SDK 归平台所有：commonMain 只认 `Analytics` 接口 + sealed 事件目录 + 纯函数 wire 映射；Android 用 google-services 插件 + firebase-analytics 依赖实现接口；iOS 由 Swift bridge 实现 Kotlin framework 导出的 ObjC `Analytics` protocol（SPM 链接 FirebaseAnalytics，spike 先验证）。`AnalyticsReporter`（commonMain object）持开关标志做第一层拦截，bootstrap 订阅偏好做第二层与用户属性下发；埋点全部走枚举/路由常量，静态闸门钉死调用点。

**Tech Stack:** Kotlin 2.1.20 / Compose Multiplatform 1.7.3 / google-services 4.4.2 / firebase-analytics 22.1.0（Android）；firebase-ios-sdk 11.x via SPM（iOS）；DataStore preferences；python3 静态闸门；GitHub Actions 双 CI + tag 触发 release。

**Spec:** `docs/superpowers/specs/2026-11-07-hstudy-analytics-firebase-design.md`

**批准偏离（用户 2026-11-19 批准）：**
- spec §8.3 的"每步独立 PR"改为 **单 draft PR + 顺序闸门提交**（分支 `feature/analytics-firebase`）。
- spec §9 写的 v3.1.0/30100 已被 v3.1.1 发布超越；本次按用户要求计 **v3.2.0 / versionCode 30200**。
- 推送纪律两段式：设计批准已授权 feature 分支推送 + draft PR；**merge 到 main + push tag v3.2.0 必须在双 CI 绿之后再向用户做一次证据确认**。

## Global Constraints

以下逐条抄自 spec（括号内为章节），每个任务的要求隐含包含本节：

- 家长设置页提供「分享使用数据」开关，默认开启，可关闭且关闭状态持久化。（§1）
- 不使用 GA4 Measurement Protocol（其 `api_secret` 必须随请求明文出现在客户端，可被从发布包中提取并用于污染数据）。（§1）
- `Platform` 增加 `var analytics: Analytics = NoopAnalytics` 与 `init(... analytics: Analytics = NoopAnalytics)`，与既有 `notifications` / `fileSharer` / `camera` / `deviceDiscovery` 完全同构。（§3）
- 事件目录用 sealed class 固定，不接受自由字符串；用户属性同样收口为 `UserProperty` 枚举；新增任何会外发的信息都必须改这些类型。（§3）
- 不含 `AppOpen`：`app_open` 由 SDK 自动采集，自报会造成重复事件与死分支。（§3）
- Kotlin framework 的构建脚本、cinterop、KSP 配置**一行不改**。（§3）
- `screen_view` 参数 `screen_name = Screen.*.route`（复用既有路由名，不新造）；`feature_used` 参数 `feature`（枚举名，snake_case）。（§4）
- 明确不发送：任务标题、错题内容、题目文本、积分数值、学习时长数值、孩子姓名、设备名（`DEVICE_NAME` 偏好值）、配对码（`PAIR_CODE` 偏好值）、任何自由文本。`checkin` 只表示"发生了一次打卡"，不含是哪条任务。（§4）
- Firebase 会生成稳定的 `app_instance_id`（伪匿名设备标识）。它不是 PII，但可在同一设备上跨事件关联。"最小匿名"的含义止于此，文档不得表述为"匿名不可关联"。（§4）
- `ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")`，缺省 `true`；偏好文件名与路径不变，schema 不变。（§5）
- 关闭时两层拦截：① 调用点先读标志位，关闭则连 `Platform.analytics` 都不碰；② 同时调 `Analytics.setEnabled(false)` → 各平台 `setAnalyticsCollectionEnabled(false)`。关闭状态持久化；重新开启不回溯补发关闭期间的数据。（§5）
- `FirebaseApp` 初始化与 `FirebaseAnalytics` 获取全部包在 `runCatching` 中；失败降级为 `NoopAnalytics`，控制台输出一行原因，App 功能零影响。（§6）
- 网络不可达时不做任何自为处理（SDK 负责缓冲）。（§6）
- 事件参数值长度上限 100 字符，超长直接丢弃该参数而非截断。（§6）
- 在 Google Cloud 控制台给配置文件里的 api_key 加上 iOS bundle ID / Android 包名限制（公开仓库里必须做）——**已于 2026-11-18 完成并 reload 验证**，本计划不再操作控制台。（§7）
- iOS 闸门：SPM spike 不通过 → 依次退路 CocoaPods（CI 命令需从 `-project` 改 `-workspace`）；或退回"接口层 + Noop 实现"。（§8）
- 配置文件 `composeApp/google-services.json` 与 `iosApp/GoogleService-Info.plist` 已在工作区（untracked），**字节保持官方原样**（plist 的 `IS_ANALYTICS_ENABLED` 是 SDK 不读字段，不改）；本计划负责把它们 commit 进仓库。

会话级安全约束（继续有效）：

- PAT 只经 env/inline API auth 使用，**绝不**进入仓库、代码、文档、日志或 git config；发布前对新提交做秘密扫描。
- 后台 agent 写工作树期间不得 `git checkout`/switch 分支。
- 推送前检查 in-flight CI：`cancel-in-progress: true` 会取消运行中的 build job；正在等某次 run 的裁决时不推新提交（md-only 提交因 `paths-ignore` 安全）。
- 不得以 stub/删功能换构建绿；业务不变量（append-only 账本、bizKey 去重、审批才计分、幂等 approve、CANCELLED/REVERSAL 对称、schema v12 + v1→v12 迁移、Android 数据路径连续）不动。
- Ruling 25 存储边界：任何文案表字符串不得进入落库调用或业务键/幂等比较；`language_changed` 埋点**不得**写进 `UserPreferences.setAppLanguage`（数据层），走 bootstrap 订阅。
- 每次 push 到共享分支 / merge / tag / publish 都要用户明确决定；本计划仅 feature 分支推送 + draft PR 已获授权。

## 文件结构

| 文件 | 责任 | 任务 |
|---|---|---|
| `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Analytics.kt`（新建） | 接口 + Noop + UserProperty + sealed AnalyticsEvent(含 Feature) + AnalyticsMapping 纯函数 | T2 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Platform.kt`（改） | 增 `analytics` / `appChannel` 属性与 init 参数 | T2 |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsMappingTest.kt`（新建） | 映射穷举 + 100 字符守卫 + 属性名 | T2 |
| `gradle/libs.versions.toml`、`composeApp/build.gradle.kts`（改） | google-services 插件 + firebase-analytics 依赖 | T3 |
| `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidFirebaseAnalytics.kt`（新建） | Android 接口实现 | T3 |
| `composeApp/src/androidMain/kotlin/com/hstudy/app/HStudyApplication.kt`（改） | runCatching 装配 + appChannel | T3 |
| `composeApp/google-services.json`（commit 既有文件） | Android Firebase 配置 | T3 |
| `iosApp/iosApp.xcodeproj/project.pbxproj`（改） | SPM package reference + product dependency + plist resource | T1(spike)/T4 |
| `iosApp/AnalyticsSpike.swift`（新建后删除） | spike 探针 | T1 |
| `iosApp/FirebaseAnalyticsBridge.swift`（新建） | Swift 实现导出的 Analytics protocol | T4 |
| `iosApp/iosApp.swift`、`composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosPlatform.kt`（改） | configure + bridge 传入 + appChannel | T4 |
| `iosApp/GoogleService-Info.plist`（commit 既有文件） | iOS Firebase 配置 | T4 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/AnalyticsReporter.kt`（新建） | 标志位第一层拦截 + 属性缓存/重发 | T5 |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsReporterTest.kt`（新建） | 开关零调用 + 重发 | T5 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/AppGraph.kt`（改） | 三条订阅：enabled/role/language(+language_changed) | T5 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt`（改） | snapshotFlow screen_view | T5 |
| 14 个埋点页面/Helper（改，清单见 T5） | feature_used / reminder_scheduled / backup / pairing | T5 |
| `scripts/analytics_call_sites.py`（新建）+ `.github/workflows/android.yml`（改） | 埋点静态闸门 + 数据层零埋点守卫 | T5/T6 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt`（改） | ANALYTICS_ENABLED 键/Flow/setter | T6 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt`（改） | analyticsTitle/analyticsNote 双语 | T6 |
| `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt`（改） | 开关 Card | T6 |
| `scripts/i18n_template_diff.py`（改） | DISCLOSED_RESIDUALS +2、计数 182→184 | T6 |
| `composeApp/src/commonTest/.../i18n/StringsCopyTest.kt`、`.../platform/AnalyticsPreferenceTest.kt`（改/新建） | 文案 ZH≠EN pin + 偏好往返 | T6 |
| `README.md`（重写）、`.github/FUNDING.yml`（新建） | 双语介绍 + Sponsors | T7 |
| `composeApp/build.gradle.kts`、`iosApp/Info.plist`、`iosApp/iosApp.xcodeproj/project.pbxproj`、`version_history.md`（改） | 30200/"3.2.0" 四处 + 历史 | T7 |

本地命令环境（每个跑 Gradle/python 的步骤都先执行）：

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export GRADLE_USER_HOME="D:/gradle-home"
export PYTHONIOENCODING=utf-8
```

本地闸门复跑（T5/T6 完成后各跑一次全套）：

```bash
python scripts/i18n_storage_boundary.py
python scripts/tests/i18n_storage_boundary_selftest.py
python scripts/tests/i18n_template_diff_selftest.py
python scripts/i18n_template_diff.py --branch
python scripts/analytics_call_sites.py
./gradlew :composeApp:testDebugUnitTest
```

---

### Task 1: iOS SPM spike（throwaway）+ 分支与 draft PR

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`
- Create: `iosApp/AnalyticsSpike.swift`
- Modify: `iosApp/iosApp.swift`（init 内加一行探针调用）
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`（Resources 加 GoogleService-Info.plist）

**Interfaces:**
- Consumes: 工作区既有 `iosApp/GoogleService-Info.plist`（untracked）。
- Produces: 裁决记录（SPM 可行 / CocoaPods 退路 / Noop 退路），T4 据此选分支；spike 提交在 T4 第一步被 revert。

- [ ] **Step 1: 建分支（controller 执行，不派 subagent）**

```bash
git status --short
git switch -c feature/analytics-firebase
```

若工作区有未提交改动（两个配置文件除外），先报告用户再动。

- [ ] **Step 2: pbxproj 加 SPM 与 plist resource（精确编辑）**

在 `/* Begin PBXBuildFile section */` 内追加两行：

```
		C10000000000000000000002 /* FirebaseAnalytics in Frameworks */ = {isa = PBXBuildFile; productRef = C10000000000000000000001 /* FirebaseAnalytics */; };
		C10000000000000000000005 /* GoogleService-Info.plist in Resources */ = {isa = PBXBuildFile; fileRef = C10000000000000000000004 /* GoogleService-Info.plist */; };
```

在 `/* Begin PBXFileReference section */` 内追加：

```
		C10000000000000000000004 /* GoogleService-Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = "GoogleService-Info.plist"; sourceTree = "<group>"; };
```

`B1000000000000000000000B /* Frameworks */` 的 `files = (` 内追加：

```
				C10000000000000000000002 /* FirebaseAnalytics in Frameworks */,
```

主 group `B10000000000000000000002` 的 `children = (` 内追加：

```
				C10000000000000000000004 /* GoogleService-Info.plist */,
```

`B1000000000000000000000C /* Resources */` 的 `files = (` 内追加：

```
				C10000000000000000000005 /* GoogleService-Info.plist in Resources */,
```

native target `B10000000000000000000009` 内 `dependencies = (` 之后追加：

```
			packageProductDependencies = (
				C10000000000000000000001 /* FirebaseAnalytics */,
			);
```

`PBXProject` 对象内 `mainGroup = ...` 之前追加：

```
			packageReferences = (
				C10000000000000000000003 /* XCRemoteSwiftPackageReference "firebase-ios-sdk" */,
			);
```

在 `/* End XCConfigurationList section */` 之后、`};`（objects 闭括号）之前追加两个新 section：

```
/* Begin XCRemoteSwiftPackageReference section */
		C10000000000000000000003 /* XCRemoteSwiftPackageReference "firebase-ios-sdk" */ = {
			isa = XCRemoteSwiftPackageReference;
			repositoryURL = "https://github.com/firebase/firebase-ios-sdk.git";
			requirement = {
				kind = upToNextMajorVersion;
				minimumVersion = 11.0.0;
			};
		};
/* End XCRemoteSwiftPackageReference section */

/* Begin XCSwiftPackageProductDependency section */
		C10000000000000000000001 /* FirebaseAnalytics */ = {
			isa = XCSwiftPackageProductDependency;
			package = C10000000000000000000003 /* XCRemoteSwiftPackageReference "firebase-ios-sdk" */;
			productName = FirebaseAnalytics;
		};
/* End XCSwiftPackageProductDependency section */
```

- [ ] **Step 3: 探针 Swift 文件与调用**

Create `iosApp/AnalyticsSpike.swift`：

```swift
import Foundation
import FirebaseAnalytics

enum AnalyticsSpike {
    static func fire() {
        guard FirebaseApp.app() != nil else {
            print("SPIKE: FirebaseApp not configured")
            return
        }
        Analytics.logEvent("spike_probe", parameters: ["probe": "v320"])
        print("SPIKE: logEvent issued")
    }
}
```

`iosApp/iosApp.swift` 的 `init()` 内、`IosPlatformKt.doInitIosPlatform()` 之前加：

```swift
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        AnalyticsSpike.fire()
```

并在该文件顶部加 `import FirebaseAnalytics`。
pbxproj 的 Sources phase（`B1000000000000000000000A`）与 PBXBuildFile/PBXFileReference/主 group 需为 `AnalyticsSpike.swift` 各加一条（UUID 用 `C10000000000000000000006` fileRef、`C10000000000000000000007` buildFile，形状照抄 iosApp.swift 的三条）。

- [ ] **Step 4: commit + push + draft PR（已授权）**

```bash
git add iosApp/iosApp.xcodeproj/project.pbxproj iosApp/AnalyticsSpike.swift iosApp/iosApp.swift iosApp/GoogleService-Info.plist
git commit -m "spike(ios): SPM FirebaseAnalytics probe (throwaway, verdict gate for v3.2.0)"
git push -u origin feature/analytics-firebase
gh pr create --draft --title "feat: Analytics Firebase integration + v3.2.0 (bilingual README, Sponsors)" --body "顺序闸门提交：spike → Android → iOS → events → toggle → docs/version。spec: docs/superpowers/specs/2026-11-07-hstudy-analytics-firebase-design.md"
```

- [ ] **Step 5: 等 iOS CI 裁决（controller）**

```bash
gh run list --branch feature/analytics-firebase --limit 5
gh run watch <ios-run-id> --exit-status
```

裁决三分支（写进 ledger）：
- **绿** → SPM 路线成立，T4 走主分支步骤。
- **红且原因是 SPM 解析/编译**（package resolution、Swift 编译 FirebaseAnalytics 失败）→ T4 走 CocoaPods 退路（T4 附步骤）。
- **红且原因是 runner 基础设施**（无模拟器等 SKIP 语义不算红；xcodebuild 本身基础设施错误）→ 重跑一次再裁决。
- spike 的 smoke 段若因 Firebase 初始化崩溃而红：记录崩溃栈，仍按"SPM 链接成功但运行时失败"处理 → T4 主分支 + bridge guard 已覆盖；若 configure 本身抛异常导致 SIGABRT，则退 Noop 分支。

- [ ] **Step 6: 记录裁决，不 revert（revert 是 T4 第一步）**

ledger 写 `Ruling: spike verdict = <SPM|CocoaPods|Noop> — <run url> — <若错代价>`。

---

### Task 2: commonMain 契约 + wire 映射 + Platform 接线

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Analytics.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Platform.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsMappingTest.kt`

**Interfaces:**
- Consumes: `com.hstudy.app.platform.ReminderKind`（Platform.kt:50）、`com.hstudy.app.i18n.AppLanguage`（含 `toStorageValue()`）。
- Produces: `Analytics` / `NoopAnalytics` / `UserProperty` / `AnalyticsEvent`（嵌套 `Feature`）/ `AnalyticsMapping.wire(event): Pair<String, Map<String, String>>` / `AnalyticsMapping.propertyName(property): String` / `Platform.analytics` / `Platform.appChannel` / `Platform.init(..., analytics: Analytics = NoopAnalytics)`。T3/T4/T5/T6 全部依赖这些名字。

- [ ] **Step 1: 写失败测试**

Create `AnalyticsMappingTest.kt`：

```kotlin
package com.hstudy.app.platform

import com.hstudy.app.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsMappingTest {
    @Test
    fun screenViewUsesScreenNameParam() {
        assertEquals(
            "screen_view" to mapOf("screen_name" to "child_home"),
            AnalyticsMapping.wire(AnalyticsEvent.ScreenView("child_home")),
        )
    }

    @Test
    fun everyFeatureMapsToSnakeCase() {
        AnalyticsEvent.Feature.entries.forEach { feature ->
            val (name, params) = AnalyticsMapping.wire(AnalyticsEvent.FeatureUsed(feature))
            assertEquals("feature_used", name)
            assertEquals(feature.name.lowercase(), params["feature"])
            assertEquals(1, params.size)
        }
    }

    @Test
    fun everyReminderKindMaps() {
        ReminderKind.entries.forEach { kind ->
            val (name, params) = AnalyticsMapping.wire(AnalyticsEvent.ReminderScheduled(kind))
            assertEquals("reminder_scheduled", name)
            assertEquals(kind.name.lowercase(), params["kind"])
        }
    }

    @Test
    fun languageChangedUsesStorageWireFormat() {
        AppLanguage.entries.forEach { language ->
            val (name, params) = AnalyticsMapping.wire(AnalyticsEvent.LanguageChanged(language))
            assertEquals("language_changed", name)
            assertEquals(language.toStorageValue(), params["to"])
        }
    }

    @Test
    fun zeroParamEvents() {
        assertEquals("backup_exported" to emptyMap(), AnalyticsMapping.wire(AnalyticsEvent.BackupExported))
        assertEquals("pairing_completed" to emptyMap(), AnalyticsMapping.wire(AnalyticsEvent.PairingCompleted))
    }

    @Test
    fun overlongParamDiscardedNotTruncated() {
        val (_, atLimit) = AnalyticsMapping.wire(AnalyticsEvent.ScreenView("x".repeat(100)))
        assertEquals("x".repeat(100), atLimit["screen_name"])
        val (_, over) = AnalyticsMapping.wire(AnalyticsEvent.ScreenView("x".repeat(101)))
        assertTrue(over.isEmpty())
    }

    @Test
    fun userPropertyNamesAreSnakeCase() {
        assertEquals(
            listOf("app_language", "user_role", "app_channel"),
            UserProperty.entries.map { AnalyticsMapping.propertyName(it) },
        )
    }
}
```

- [ ] **Step 2: 跑测试确认红**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.platform.AnalyticsMappingTest"`
Expected: 编译失败（AnalyticsEvent 等未定义）。

- [ ] **Step 3: 实现 Analytics.kt**

```kotlin
package com.hstudy.app.platform

import com.hstudy.app.i18n.AppLanguage

enum class UserProperty { APP_LANGUAGE, USER_ROLE, APP_CHANNEL }

interface Analytics {
    fun logEvent(event: AnalyticsEvent)
    fun setUserProperty(property: UserProperty, value: String?)
    fun setEnabled(enabled: Boolean)
}

object NoopAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) {}
    override fun setUserProperty(property: UserProperty, value: String?) {}
    override fun setEnabled(enabled: Boolean) {}
}

sealed class AnalyticsEvent {
    data class ScreenView(val route: String) : AnalyticsEvent()
    data class FeatureUsed(val feature: Feature) : AnalyticsEvent()
    data class ReminderScheduled(val kind: ReminderKind) : AnalyticsEvent()
    data class LanguageChanged(val to: AppLanguage) : AnalyticsEvent()
    object BackupExported : AnalyticsEvent()
    object PairingCompleted : AnalyticsEvent()

    enum class Feature {
        POMODORO_START, POMODORO_COMPLETE, CHECKIN, LUCKY_DRAW,
        PET_CARE, WISH_SUBMIT, APPROVAL_ACTION, BACKUP_EXPORT, PAIRING
    }
}

/** 唯一的事件→SDK wire 映射点：Android 与 Swift bridge 都消费它，避免两份映射漂移。 */
object AnalyticsMapping {
    fun wire(event: AnalyticsEvent): Pair<String, Map<String, String>> {
        val (name, params) = when (event) {
            is AnalyticsEvent.ScreenView -> "screen_view" to mapOf("screen_name" to event.route)
            is AnalyticsEvent.FeatureUsed -> "feature_used" to mapOf("feature" to event.feature.name.lowercase())
            is AnalyticsEvent.ReminderScheduled -> "reminder_scheduled" to mapOf("kind" to event.kind.name.lowercase())
            is AnalyticsEvent.LanguageChanged -> "language_changed" to mapOf("to" to event.to.toStorageValue())
            AnalyticsEvent.BackupExported -> "backup_exported" to emptyMap()
            AnalyticsEvent.PairingCompleted -> "pairing_completed" to emptyMap()
        }
        // spec §6：超长丢弃而非截断，避免把内容裁成可猜的片段
        return name to params.filterValues { it.length <= 100 }
    }

    fun propertyName(property: UserProperty): String = property.name.lowercase()
}
```

- [ ] **Step 4: Platform.kt 接线**

在 `object Platform` 内既有 bridge 属性旁加：

```kotlin
    var analytics: Analytics = NoopAnalytics
    var appChannel: String = "release"
```

`init(...)` 签名末尾加参数 `analytics: Analytics = NoopAnalytics`，函数体内照既有 bridge 赋值形状加 `this.analytics = analytics`。`appChannel` 不经 init（平台侧直接赋值）。

- [ ] **Step 5: 跑测试确认绿 + 全套闸门**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: 全绿（含既有 224 测试）。
Run: `python scripts/i18n_storage_boundary.py && python scripts/i18n_template_diff.py --branch`
Expected: exit 0。

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Analytics.kt composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Platform.kt composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsMappingTest.kt
git commit -m "feat(analytics): commonMain 契约——sealed 事件目录、wire 映射、Platform.analytics 接线（spec §3/§4/§6）"
```

---

### Task 3: Android Firebase 实现

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidFirebaseAnalytics.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/hstudy/app/HStudyApplication.kt`
- Commit 既有: `composeApp/google-services.json`

**Interfaces:**
- Consumes: T2 的 `Analytics` / `AnalyticsMapping` / `NoopAnalytics` / `Platform.analytics` / `Platform.appChannel`。
- Produces: `AndroidFirebaseAnalytics(context: android.content.Context) : Analytics`；装配后 `Platform.analytics` 在 Android 为真实现；`Platform.appChannel` = "debug"/"release"。

- [ ] **Step 1: 版本目录与构建脚本**

`gradle/libs.versions.toml` `[versions]` 加：

```toml
google-services = "4.4.2"
firebase-analytics = "22.1.0"
```

`[libraries]` 加：

```toml
firebase-analytics = { module = "com.google.firebase:firebase-analytics", version.ref = "firebase-analytics" }
```

`[plugins]` 加：

```toml
googleServices = { id = "com.google.gms.google-services", version.ref = "google-services" }
```

`composeApp/build.gradle.kts` plugins 块加 `alias(libs.plugins.googleServices)`；`androidMain` dependencies 块加 `implementation(libs.firebase.analytics)`。**其余一行不改**（framework/cinterop/KSP 不动）。

- [ ] **Step 2: 实现类**

```kotlin
package com.hstudy.app.platform

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AndroidFirebaseAnalytics(context: Context) : Analytics {
    private val firebase: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun logEvent(event: AnalyticsEvent) {
        val (name, params) = AnalyticsMapping.wire(event)
        firebase.logEvent(name, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }

    override fun setUserProperty(property: UserProperty, value: String?) {
        firebase.setUserProperty(AnalyticsMapping.propertyName(property), value)
    }

    override fun setEnabled(enabled: Boolean) {
        firebase.setAnalyticsCollectionEnabled(enabled)
    }
}
```

- [ ] **Step 3: HStudyApplication 装配**

`onCreate` 内、`Platform.init(...)` 调用之前加：

```kotlin
        Platform.appChannel =
            if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "debug" else "release"
        val analytics: Analytics = runCatching { AndroidFirebaseAnalytics(this) as Analytics }
            .onFailure { android.util.Log.w("HStudy", "analytics init failed; falling back to noop", it) }
            .getOrDefault(NoopAnalytics)
```

`Platform.init(...)` 调用加实参 `analytics = analytics`。import 补 `com.hstudy.app.platform.Analytics` / `AndroidFirebaseAnalytics` / `NoopAnalytics`（若该文件已 import platform 包成员则照既有形状）。

- [ ] **Step 4: 本地构建 + 测试**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL；google-services 插件处理 `composeApp/google-services.json` 无 error。
若插件报缺 json：确认文件在 `composeApp/` 根（已在工作区）。

- [ ] **Step 5: Commit（含配置文件）**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidFirebaseAnalytics.kt composeApp/src/androidMain/kotlin/com/hstudy/app/HStudyApplication.kt composeApp/google-services.json
git commit -m "feat(analytics): Android FirebaseAnalytics 实现 + google-services 插件与官方配置（spec §3/§6）"
```

秘密扫描：`git show --stat HEAD` 确认无 PAT 形状字符串；json 内 api_key 为公开客户端标识（已加包名+SHA-1 限制），允许进仓库。

---

### Task 4: iOS Swift bridge（按 T1 裁决选分支）

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`（revert spike 后重加 SPM/plist；Sources 换 bridge 文件）
- Create: `iosApp/FirebaseAnalyticsBridge.swift`
- Modify: `iosApp/iosApp.swift`
- Modify: `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosPlatform.kt`
- Delete: `iosApp/AnalyticsSpike.swift`（经 revert）

**Interfaces:**
- Consumes: T2 导出到 ObjC 的 `Analytics` protocol、`AnalyticsEvent`、`UserProperty`、`AnalyticsMapping.shared`（`wire(event:)` 返回 `KotlinPair`，`propertyName(property:)` 返回 String）。
- Produces: `initIosPlatform(analytics: Analytics, appChannel: String)`（ObjC selector `doInitIosPlatformAnalytics:appChannel:`）；iOS 真实现或 Noop（裁决分支）。

- [ ] **Step 1: revert spike**

```bash
git revert --no-edit <T1-spike-commit-sha>
```

revert 会移除 SPM/plist/探针；下一步重加正式部分。若 revert 冲突（iosApp.swift 被 T1 后无人改过，不应冲突），按"保留非 spike 内容"解。

- [ ] **Step 2: 主分支（裁决=SPM）——pbxproj 重加 T1 Step 2 的全部 SPM/plist 编辑（逐字同 T1），但 Sources 不加 AnalyticsSpike，改为 FirebaseAnalyticsBridge.swift 三条（fileRef `C10000000000000000000008`、buildFile `C10000000000000000000009`，形状照抄 iosApp.swift）**

**另加 FirebaseCore product**（spike 裁决证据：`FirebaseApp` 在 FirebaseCore module，只 import FirebaseAnalytics 会 cannot-find-in-scope）：
PBXBuildFile 加 `C1000000000000000000000B /* FirebaseCore in Frameworks */ = {isa = PBXBuildFile; productRef = C1000000000000000000000A /* FirebaseCore */; };`；
Frameworks phase files 加 `C1000000000000000000000B /* FirebaseCore in Frameworks */,`；
target packageProductDependencies 加 `C1000000000000000000000A /* FirebaseCore */,`；
XCSwiftPackageProductDependency section 加：

```
		C1000000000000000000000A /* FirebaseCore */ = {
			isa = XCSwiftPackageProductDependency;
			package = C10000000000000000000003 /* XCRemoteSwiftPackageReference "firebase-ios-sdk" */;
			productName = FirebaseCore;
		};
```

- [ ] **Step 3: Swift bridge**

Create `iosApp/FirebaseAnalyticsBridge.swift`：

```swift
import Foundation
import ComposeApp
import FirebaseCore
import FirebaseAnalytics

final class FirebaseAnalyticsBridge: NSObject, ComposeApp.Analytics {
    func logEvent(event: AnalyticsEvent) {
        guard FirebaseApp.app() != nil else { return }
        let wire = AnalyticsMapping.shared.wire(event: event)
        let name = wire.first as? String ?? ""
        let params = wire.second as? [String: String] ?? [:]
        FirebaseAnalytics.Analytics.logEvent(name, parameters: params)
    }

    func setUserProperty(property: UserProperty, value: String?) {
        guard FirebaseApp.app() != nil else { return }
        FirebaseAnalytics.Analytics.setUserProperty(value, forName: AnalyticsMapping.shared.propertyName(property: property))
    }

    func setEnabled(enabled: Bool) {
        guard FirebaseApp.app() != nil else { return }
        FirebaseAnalytics.Analytics.setAnalyticsCollectionEnabled(enabled)
    }
}
```

（**名字冲突护栏**：ComposeApp 与 FirebaseAnalytics 两个 module 都导出 `Analytics` 这个名字——protocol 构象必须写 `ComposeApp.Analytics`，Firebase 调用必须写 `FirebaseAnalytics.Analytics.*`，裸 `Analytics` 会 ambiguous。ObjC selector 对应：`logEventEvent:` / `setUserPropertyProperty:value:` / `setEnabledEnabled:`；Swift 侧签名即上述写法。若 CI 报 protocol 构象不匹配，按编译器给出的 required selector 修正 Swift 方法标签，**不改 Kotlin 接口**。）

- [ ] **Step 4: iosApp.swift init**

```swift
    init() {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        if FirebaseApp.app() == nil { print("analytics: FirebaseApp configure failed; noop") }
        let channel: String
        #if DEBUG
        channel = "debug"
        #else
        channel = "release"
        #endif
        IosPlatformKt.doInitIosPlatform(analytics: FirebaseAnalyticsBridge(), appChannel: channel)
    }
```

顶部 import 加 `import FirebaseCore` 与 `import FirebaseAnalytics`（spike revert 已删旧 import）。

- [ ] **Step 5: IosPlatform.kt 签名**

`fun initIosPlatform()` 改为 `fun initIosPlatform(analytics: Analytics, appChannel: String)`；函数体 `Platform.init(...)` 加实参 `analytics = analytics`，并在其前加 `Platform.appChannel = appChannel`。其余（installDeviceFeedback / AppGraph.bootstrap / MainViewController）不动。import 补 `com.hstudy.app.platform.Analytics`。

- [ ] **Step 6: 本地可验证部分 + commit + push + CI 裁决**

Windows 无 Xcode：本地只跑 `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` 不可行（无 mac 工具链）→ 本地跑 `./gradlew :composeApp:testDebugUnitTest` 确认 commonMain/iosMain 编译面（iosMain 编译需 KN 工具链，本机 ~/.konan 存在则跑 `:composeApp:compileKotlinIosSimulatorArm64`；失败因工具链则记录并以 CI 为准）。

```bash
git add iosApp/iosApp.xcodeproj/project.pbxproj iosApp/FirebaseAnalyticsBridge.swift iosApp/iosApp.swift composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosPlatform.kt
git commit -m "feat(analytics): iOS Swift bridge 实现导出 Analytics protocol（SPM FirebaseAnalytics，spec §3/§8）"
git push
gh run list --branch feature/analytics-firebase --limit 3
```

等 ios.yml 绿（smoke PASS 或 infra SKIP 均算链接成功；smoke FAIL 需读 crashreport_content.txt 裁决）。

- [ ] **Step 7（仅裁决=CocoaPods 时执行）: 退路步骤**

Create `iosApp/Podfile`：

```ruby
platform :ios, '16.0'
target 'iosApp' do
  use_frameworks!
  pod 'FirebaseAnalytics', '~> 11.0'
end
```

pbxproj 不加 SPM section（保留 plist resource 编辑）；`.github/workflows/ios.yml` 与 `.github/workflows/release.yml` 的 ios job 在 xcodebuild 前加 step：

```yaml
      - name: Install CocoaPods deps
        run: pod install --project-directory=iosApp
```

且两处 xcodebuild 命令 `-project iosApp/iosApp.xcodeproj` 改 `-workspace iosApp/iosApp.xcworkspace`。bridge Swift 同 Step 3。commit message 注明退路原因与 run url。

- [ ] **Step 8（仅裁决=Noop 时执行）: 退路步骤**

iOS 不接 SDK：`initIosPlatform(analytics: Analytics = NoopAnalytics, appChannel: String = "release")` 保留签名但 iosApp.swift 传 `NoopAnalytics.shared`（ObjC 导出 object 为 `.shared`）；pbxproj 仅加 plist resource 或不加（Noop 不需要 plist，则不加）；README 隐私节注明 iOS 统计待真机环境接入（T7 文本按裁决微调）。ledger 记 Ruling。

---

### Task 5: AnalyticsReporter + 订阅 + screen_view + 全部埋点 + 静态闸门

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/AnalyticsReporter.kt`
- Test: `composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsReporterTest.kt`
- Modify: `AppGraph.kt`、`App.kt`
- Modify: 埋点清单 14 文件（见 Step 4）
- Create: `scripts/analytics_call_sites.py`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Consumes: T2 全部；`UserPreferences.analyticsEnabled/currentRole/appLanguage`（analyticsEnabled 由 T6 新增——**本任务 bootstrap 订阅先写 `userPreferences.analyticsEnabled`，T6 补键；故 T5 与 T6 之间本地编译会红，T5 的测试步骤只跑 reporter/mapping 测试与闸门，全套编译绿在 T6 末验证**。若控制器偏好每任务可编译，则把 T6 的 UserPreferences 三行提前到 T5 Step 3 一并提交——**本计划采用后者：T5 包含 prefs 键/Flow/setter 三行，T6 只做 UI/文案/闸门登记**）。
- Produces: `AnalyticsReporter.log(event)` / `.setEnabled(Boolean)` / `.setChannel(String)` / `.setLanguage(String)` / `.setRole(String)`；静态闸门脚本（T6 扩展登记）。

- [ ] **Step 1: 写失败测试**

Create `AnalyticsReporterTest.kt`：

```kotlin
package com.hstudy.app.platform

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val properties = mutableListOf<Pair<UserProperty, String?>>()
    val enabledCalls = mutableListOf<Boolean>()
    override fun logEvent(event: AnalyticsEvent) { events.add(event) }
    override fun setUserProperty(property: UserProperty, value: String?) { properties.add(property to value) }
    override fun setEnabled(enabled: Boolean) { enabledCalls.add(enabled) }
    fun clear() { events.clear(); properties.clear(); enabledCalls.clear() }
}

class AnalyticsReporterTest {
    private val fake = FakeAnalytics()

    @BeforeTest
    fun setup() {
        Platform.analytics = fake
        AnalyticsReporter.setChannel("release")
        AnalyticsReporter.setLanguage("zh")
        AnalyticsReporter.setRole("parent")
        AnalyticsReporter.setEnabled(true)
        fake.clear()
    }

    @AfterTest
    fun teardown() {
        Platform.analytics = NoopAnalytics
    }

    @Test
    fun disabledReporterNeverTouchesPlatformAnalytics() {
        AnalyticsReporter.setEnabled(false)
        val offCalls = fake.enabledCalls.toList()
        fake.clear()
        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
        AnalyticsReporter.setLanguage("en")
        AnalyticsReporter.setRole("child")
        assertTrue(fake.events.isEmpty())
        assertTrue(fake.properties.isEmpty())
        assertTrue(fake.enabledCalls.isEmpty())
        assertEquals(listOf(false), offCalls)
    }

    @Test
    fun enabledReporterForwardsEvents() {
        AnalyticsReporter.log(AnalyticsEvent.ScreenView("child_home"))
        assertEquals(listOf(AnalyticsEvent.ScreenView("child_home")), fake.events)
    }

    @Test
    fun reenableResendsCurrentPropertiesButNoRetroactiveEvents() {
        AnalyticsReporter.setEnabled(false)
        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
        AnalyticsReporter.setLanguage("en")
        fake.clear()
        AnalyticsReporter.setEnabled(true)
        assertTrue(fake.events.isEmpty())
        assertEquals(
            listOf(UserProperty.APP_CHANNEL to "release", UserProperty.APP_LANGUAGE to "en", UserProperty.USER_ROLE to "parent"),
            fake.properties,
        )
        assertEquals(listOf(true), fake.enabledCalls)
    }

    @Test
    fun languageAndRoleUpdatesForwardWhileEnabled() {
        AnalyticsReporter.setLanguage("en")
        AnalyticsReporter.setRole("child")
        assertEquals(
            listOf(UserProperty.APP_LANGUAGE to "en", UserProperty.USER_ROLE to "child"),
            fake.properties,
        )
    }
}
```

- [ ] **Step 2: 跑测试确认红**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.platform.AnalyticsReporterTest"`
Expected: 编译失败（AnalyticsReporter 未定义）。

- [ ] **Step 3: 实现 Reporter + prefs 键**

Create `AnalyticsReporter.kt`：

```kotlin
package com.hstudy.app.platform

/**
 * 第一层拦截（spec §5）：关闭时连 Platform.analytics 都不碰。
 * 属性是"当前状态"不是事件：重新开启时重发缓存值，不构成回溯补发。
 * 标志位与缓存的读写来自 bootstrap 协程与 UI 主线程，竞态最坏后果是切换瞬间
 * 多/少一条属性，不影响事件拦截语义，故不加锁（commonMain 无 @Synchronized）。
 */
object AnalyticsReporter {
    private var enabled = true
    private var channel = "release"
    private var language: String? = null
    private var role: String? = null

    fun setChannel(value: String) { channel = value }

    fun setLanguage(value: String) {
        language = value
        if (enabled) Platform.analytics.setUserProperty(UserProperty.APP_LANGUAGE, value)
    }

    fun setRole(value: String) {
        role = value
        if (enabled) Platform.analytics.setUserProperty(UserProperty.USER_ROLE, value)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        Platform.analytics.setEnabled(on)
        if (on) {
            Platform.analytics.setUserProperty(UserProperty.APP_CHANNEL, channel)
            language?.let { Platform.analytics.setUserProperty(UserProperty.APP_LANGUAGE, it) }
            role?.let { Platform.analytics.setUserProperty(UserProperty.USER_ROLE, it) }
        }
    }

    fun log(event: AnalyticsEvent) {
        if (enabled) Platform.analytics.logEvent(event)
    }
}
```

`UserPreferences.kt` companion 内（LAST_MANDATORY_PENALTY_DATE 之后）加：

```kotlin
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled") // 分享使用数据开关，缺省 true
```

Flow 区（appLanguage 之后）加：

```kotlin
    val analyticsEnabled: Flow<Boolean> = dataStore.data.map { it[ANALYTICS_ENABLED] ?: true }
```

setter 区（setAppLanguage 之后）加：

```kotlin
    suspend fun setAnalyticsEnabled(value: Boolean) {
        dataStore.edit { it[ANALYTICS_ENABLED] = value }
    }
```

- [ ] **Step 4: 跑测试确认绿**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.platform.AnalyticsReporterTest"`
Expected: 4 tests PASS。

- [ ] **Step 5: bootstrap 订阅（AppGraph.kt）**

`bootstrap()` 内既有 `appScope.launch { userPreferences.appLanguage.collect { _startupLanguage.value = it } }`（:125）**替换**为：

```kotlin
        AnalyticsReporter.setChannel(Platform.appChannel)
        appScope.launch { userPreferences.analyticsEnabled.collect { AnalyticsReporter.setEnabled(it) } }
        appScope.launch { userPreferences.currentRole.collect { it?.let { role -> AnalyticsReporter.setRole(role) } } }
        appScope.launch {
            var lastStored: String? = null
            var seen = false
            userPreferences.appLanguage.collect {
                _startupLanguage.value = it
                val display = appLanguageFromStorage(it).resolveWith(systemLanguageTag())
                AnalyticsReporter.setLanguage(display.name.lowercase())
                // language_changed 只在运行中切换时发（首帧 emission 不算切换）；
                // 埋点不进数据层 setAppLanguage（Ruling 25 存储边界）
                if (seen && lastStored != it) {
                    AnalyticsReporter.log(AnalyticsEvent.LanguageChanged(appLanguageFromStorage(it)))
                }
                lastStored = it
                seen = true
            }
        }
```

import 补 `com.hstudy.app.i18n.appLanguageFromStorage`、`com.hstudy.app.i18n.systemLanguageTag`、`com.hstudy.app.platform.AnalyticsEvent`、`com.hstudy.app.platform.AnalyticsReporter`。既有注释（Ruling 46 那段）保留并追加一句说明订阅扩了三条。

- [ ] **Step 6: screen_view（App.kt）**

`val navigator = rememberAppNavigator(Screen.RoleSelect.route)`（:76）之后加：

```kotlin
            LaunchedEffect(navigator) {
                snapshotFlow { navigator.currentRoute }
                    .distinctUntilChanged()
                    .collect { route ->
                        // 参数化路由含实体 ID：只发首段路由名（spec §4 不发送任何 ID/自由文本）
                        AnalyticsReporter.log(AnalyticsEvent.ScreenView(route.substringBefore('/')))
                    }
            }
```

import 补 `androidx.compose.runtime.LaunchedEffect`、`androidx.compose.runtime.snapshotFlow`、`kotlinx.coroutines.flow.distinctUntilChanged`、`com.hstudy.app.platform.AnalyticsEvent`、`com.hstudy.app.platform.AnalyticsReporter`。

- [ ] **Step 7: 埋点清单（每处一行插入，import 补 AnalyticsEvent/AnalyticsReporter）**

| 文件 | 锚点 | 插入行 |
|---|---|---|
| `ui/common/PomodoroTimer.kt` | :87 呼吸完成 `state = PomodoroState.RUNNING` 之后 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.POMODORO_START))` |
| 同上 | :315 跳过呼吸开始分支同语句之后 | 同上 |
| 同上 | :332 跳过呼吸按钮同语句之后 | 同上 |
| `ui/focus/ChildPomodoroScreen.kt` | :175-185 `onComplete = {` lambda 首行 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.POMODORO_COMPLETE))` |
| `ui/child/ChildTaskListScreen.kt` | :133 `checkInForTask(...)` 之后 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))` |
| `ui/child/ChildHomeScreen.kt` | :597 `checkIn(` 调用（completeAssignment lambda 内，:603 闭括号后） | 同上 CHECKIN |
| `ui/target/ChildDailyTargetScreen.kt` | :217 与 :227 两处 `checkIn(` 调用之后 | 同上 CHECKIN（两行） |
| `ui/habit/ChildHabitChallengeScreen.kt` | :86 与 :101 两处成功分支 `checkIn(` 调用之后（ALREADY_CHECKED_IN 分支不发） | 同上 CHECKIN（两行） |
| `ui/child/ChildLuckyDrawScreen.kt` | :117 `adjustPoints(won, ...)` 之后 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.LUCKY_DRAW))` |
| `ui/pet/ChildPetScreen.kt` | :216 `spendPoints(10, ...)` 之后 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.PET_CARE))` |
| 同上 | :231 `spendPoints(5, ...)` 之后 | 同上 PET_CARE |
| `ui/wish/ChildWishGoalScreen.kt` | :133 `if (success) {` 内首行 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.WISH_SUBMIT))` |
| `ui/parent/ParentApprovalScreen.kt` | :57 `approveAllAndAward()` 之后 | `AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.APPROVAL_ACTION))` |
| 同上 | :99 `approveAndAward(approval.id)` 之后 | 同上 |
| 同上 | :108 `rejectApproval(approval.id)` 之后 | 同上 |
| `ui/parent/ParentSettingsScreen.kt` | :162 `onSuccess = {` 内、showSnackbar 之前 | `AnalyticsReporter.log(AnalyticsEvent.BackupExported)` |
| `ui/child/ChildHomeScreen.kt` | :55 `if (result.isSuccess) {` 内首行 | `AnalyticsReporter.log(AnalyticsEvent.PairingCompleted)` |
| `ui/mistake/ReviewReminderReceiver.kt` | :35-45 `Platform.notifications.schedule(...)` 调用之后 | `AnalyticsReporter.log(AnalyticsEvent.ReminderScheduled(ReminderKind.REVIEW))` |
| `ui/parent/WeeklyReportReceiver.kt` | :49-60 同调用之后 | `AnalyticsReporter.log(AnalyticsEvent.ReminderScheduled(ReminderKind.WEEKLY_REPORT))` |
| `ui/schedule/ScheduleAlarmHelper.kt` | :49-59 同调用之后 | `AnalyticsReporter.log(AnalyticsEvent.ReminderScheduled(ReminderKind.SCHEDULE))` |

Ruling（写进 ledger）：POMODORO_START 只在新会话进入 RUNNING 的三处发（:87/:315/:332），暂停恢复 :357 不算新开始；bootstrap 的提醒重注册（AppGraph :142/:150）不发 reminder_scheduled（恢复不是用户动作，避免每次冷启动刷事件）；WISH_SUBMIT 取孩子存入成功路径（家长创建是配置不是使用）。

- [ ] **Step 8: 静态闸门脚本**

Create `scripts/analytics_call_sites.py`：

```python
# -*- coding: utf-8 -*-
"""埋点 wiring 静态闸门（v3.2.0）：钉死每个调用点存在，且数据层零埋点。

单测证明不了 Compose 调用点（仓库无 Compose UI 测试依赖），此脚本是 wiring 的
持久守卫：删埋点/改枚举名会红。与 i18n 闸门同教义：恒红的闸门不会被读，故阻塞。
"""
import io
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BASE = os.path.join(ROOT, "composeApp", "src", "commonMain", "kotlin", "com", "hstudy", "app")

# (相对 BASE 路径, 必须出现的子串, 最少次数)
SITES = [
    ("ui/common/PomodoroTimer.kt", "Feature.POMODORO_START", 3),
    ("ui/focus/ChildPomodoroScreen.kt", "Feature.POMODORO_COMPLETE", 1),
    ("ui/child/ChildTaskListScreen.kt", "Feature.CHECKIN", 1),
    ("ui/child/ChildHomeScreen.kt", "Feature.CHECKIN", 1),
    ("ui/child/ChildHomeScreen.kt", "AnalyticsEvent.PairingCompleted", 1),
    ("ui/target/ChildDailyTargetScreen.kt", "Feature.CHECKIN", 2),
    ("ui/habit/ChildHabitChallengeScreen.kt", "Feature.CHECKIN", 2),
    ("ui/child/ChildLuckyDrawScreen.kt", "Feature.LUCKY_DRAW", 1),
    ("ui/pet/ChildPetScreen.kt", "Feature.PET_CARE", 2),
    ("ui/wish/ChildWishGoalScreen.kt", "Feature.WISH_SUBMIT", 1),
    ("ui/parent/ParentApprovalScreen.kt", "Feature.APPROVAL_ACTION", 3),
    ("ui/parent/ParentSettingsScreen.kt", "AnalyticsEvent.BackupExported", 1),
    ("ui/mistake/ReviewReminderReceiver.kt", "AnalyticsEvent.ReminderScheduled", 1),
    ("ui/parent/WeeklyReportReceiver.kt", "AnalyticsEvent.ReminderScheduled", 1),
    ("ui/schedule/ScheduleAlarmHelper.kt", "AnalyticsEvent.ReminderScheduled", 1),
    ("App.kt", "AnalyticsEvent.ScreenView", 1),
    ("AppGraph.kt", "AnalyticsEvent.LanguageChanged", 1),
    ("AppGraph.kt", "AnalyticsReporter.setEnabled", 1),
]

failures = []
for rel, needle, minimum in SITES:
    path = os.path.join(BASE, rel)
    with open(path, encoding="utf-8") as fh:
        count = fh.read().count(needle)
    if count < minimum:
        failures.append("%s: expected >=%d occurrence(s) of %r, found %d" % (rel, minimum, needle, count))

data_dir = os.path.join(BASE, "data")
for dirpath, _, filenames in os.walk(data_dir):
    for name in filenames:
        if not name.endswith(".kt"):
            continue
        with open(os.path.join(dirpath, name), encoding="utf-8") as fh:
            if "AnalyticsReporter." in fh.read():
                failures.append("data layer must not call analytics: %s" % os.path.join(dirpath, name))

if failures:
    for line in failures:
        print("ANALYTICS_GATE_FAIL: " + line)
    sys.exit(1)
print("ANALYTICS_GATE_OK: %d site checks + data-layer zero-call guard" % len(SITES))
```

`.github/workflows/android.yml` 在 template byte-equivalence step 之后加：

```yaml
      - name: analytics call-site wiring (v3.2.0, blocking)
        run: python3 scripts/analytics_call_sites.py
```

- [ ] **Step 9: 全套本地验证**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Run: `python scripts/analytics_call_sites.py && python scripts/i18n_storage_boundary.py && python scripts/i18n_template_diff.py --branch`
Expected: 全绿（settings 开关 UI 尚未加，闸门 SITES 不含 settings 断言，T6 补）。

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/platform/AnalyticsReporter.kt composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsReporterTest.kt composeApp/src/commonMain/kotlin/com/hstudy/app/AppGraph.kt composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt composeApp/src/commonMain/kotlin/com/hstudy/app/ui scripts/analytics_call_sites.py .github/workflows/android.yml
git commit -m "feat(analytics): Reporter 两层拦截 + bootstrap 订阅 + screen_view + 全埋点 + wiring 静态闸门（spec §4/§5/§7）"
```

---

### Task 6: 设置页开关 + 双语文案 + i18n 闸门登记

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt`（data class :227 后、ZH :346 后、EN :434 后）
- Modify: `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt`（:399 语言 Card 闭括号后）
- Modify: `scripts/i18n_template_diff.py`（DISCLOSED_RESIDUALS :481 后 + 计数注释 :190-194）
- Modify: `scripts/analytics_call_sites.py`（SITES 加 settings 两条）
- Modify: `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt`
- Create: `composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsPreferenceTest.kt`

**Interfaces:**
- Consumes: T5 的 `UserPreferences.analyticsEnabled/setAnalyticsEnabled`、`AnalyticsReporter`（bootstrap 已订阅）。
- Produces: `SettingsStrings.analyticsTitle/analyticsNote`（两表）；设置页开关；闸门登记 184 条。

- [ ] **Step 1: 写失败测试**

Create `AnalyticsPreferenceTest.kt`（setup 形状照 LanguagePreferenceTest.kt:26-39）：

```kotlin
package com.hstudy.app.platform

import com.hstudy.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsPreferenceTest {
    // 目录与 LanguagePreferenceTest 相同：DataStore 是进程内单例（顶层 by lazy），
    // 换目录名不会换实例，反而让"产物落在哪"的注释失真；共用同一测试目录。
    @AfterTest
    fun cleanup() {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
    }

    @Test
    fun defaultsToEnabledAndPersistsToggle() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        assertTrue(prefs.analyticsEnabled.first())
        prefs.setAnalyticsEnabled(false)
        assertEquals(false, prefs.analyticsEnabled.first())
        prefs.setAnalyticsEnabled(true)
        assertEquals(true, prefs.analyticsEnabled.first())
    }
}
```

`StringsCopyTest.kt` 在 `settingsLanguageCopyDiffers`（:346-352）之后加：

```kotlin
    @Test
    fun analyticsCopyDiffers() {
        assertDiffers(
            AppStringsZh.settings.analyticsTitle to AppStringsEn.settings.analyticsTitle,
            AppStringsZh.settings.analyticsNote to AppStringsEn.settings.analyticsNote,
        )
    }
```

- [ ] **Step 2: 跑测试确认红**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.hstudy.app.platform.AnalyticsPreferenceTest" --tests "com.hstudy.app.i18n.StringsCopyTest"`
Expected: StringsCopyTest 编译失败（analyticsTitle 未定义）；AnalyticsPreferenceTest 绿（T5 已加键）。

- [ ] **Step 3: SettingsStrings 三处加字段**

data class（`partialTranslationNote: String,` 之后）：

```kotlin
    val analyticsTitle: String,
    val analyticsNote: String,
```

ZH 表（`partialTranslationNote = ...` 之后）：

```kotlin
    analyticsTitle = "📊 分享使用数据",
    analyticsNote = "仅统计功能使用次数、界面语言等匿名信息，不含姓名、任务内容或积分明细。关闭后立即停止上报。",
```

EN 表（同位置）：

```kotlin
    analyticsTitle = "📊 Share usage data",
    analyticsNote = "Only anonymous usage counts and app language are collected. No names, task content or point details. Turning this off stops reporting immediately.",
```

- [ ] **Step 4: 设置页开关 Card**

`ParentSettingsScreen.kt` 语言 Card 的闭括号（:399 `}`）之后、Column 闭括号（:400）之前插入：

```kotlin
                    // ===== 分享使用数据（v3.2.0，spec §5：语言项下方）=====
                    val analyticsEnabled by app.userPreferences.analyticsEnabled.collectAsState(initial = true)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(strings.analyticsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Switch(
                                    checked = analyticsEnabled,
                                    onCheckedChange = { on ->
                                        scope.launch { app.userPreferences.setAnalyticsEnabled(on) }
                                    },
                                )
                            }
                            Text(strings.analyticsNote,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
```

import 补 `androidx.compose.material3.Switch`（若未 import）。`collectAsState(initial = true)` 与缺省 true 一致：首帧不闪"关"。

- [ ] **Step 5: i18n 闸门登记**

`scripts/i18n_template_diff.py` DISCLOSED_RESIDUALS 内 Task 11 那组五条之后（:481 后）加：

```python
    # ---- v3.2.0 Analytics 开关（只有表侧：新写文案，迁移前该页没有此分区）----
    {"page": None, "table": "📊 分享使用数据",
     "reason": "v3.2.0 新写文案（spec §5 开关标题）：SettingsStringsZh.analyticsTitle"},
    {"page": None, "table": "仅统计功能使用次数、界面语言等匿名信息，不含姓名、任务内容或积分明细。关闭后立即停止上报。",
     "reason": "v3.2.0 新写文案（spec §5 采集范围说明）：SettingsStringsZh.analyticsNote"},
```

计数注释（:190-194）首段替换为：

```python
# 登记表的当前实况读数（**v3.2.0 复算**，改登记时必须同时改这里）：表里现在 **184 条**
# （9 条双侧成对 + 155 条只看页面 + 20 条只看表侧）。默认组碰到 **6** 条（3 条双侧命中 + 3 条
# "单边在范围"），另外 **178** 条默认组用不到；`--branch` 组碰到 **184 条**、**0** 条"（未用到）"。
# v3.2.0 在 W9 Task 4 的 182 条上新增 Analytics 开关 2 条表侧单边登记 → 182 + 2 = 184。
```

（历史追溯段落 :193 起保留不动。）**验证步骤**：跑默认组与 `--branch`，若脚本打印的默认组消耗数与"6/178"不符，按实测改这两个数字（只改数字，不改结构）。

- [ ] **Step 6: 闸门 SITES 扩展**

`scripts/analytics_call_sites.py` SITES 列表加：

```python
    ("ui/parent/ParentSettingsScreen.kt", "setAnalyticsEnabled", 1),
    ("ui/parent/ParentSettingsScreen.kt", "strings.analyticsTitle", 1),
```

- [ ] **Step 7: 全套本地验证**

Run: `./gradlew :composeApp:testDebugUnitTest`
Run: `python scripts/i18n_storage_boundary.py && python scripts/tests/i18n_storage_boundary_selftest.py && python scripts/tests/i18n_template_diff_selftest.py && python scripts/i18n_template_diff.py --branch && python scripts/analytics_call_sites.py`
Expected: 全绿；template diff 打印 987+2 模板级命中、0 missing、0 invented。

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt scripts/i18n_template_diff.py scripts/analytics_call_sites.py composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/StringsCopyTest.kt composeApp/src/commonTest/kotlin/com/hstudy/app/platform/AnalyticsPreferenceTest.kt
git commit -m "feat(analytics): 家长设置「分享使用数据」开关 + 双语文案 + 闸门登记 184（spec §5）"
```

---

### Task 7: v3.2.0 版本戳 + version_history + 双语 README + FUNDING.yml

**Files:**
- Modify: `composeApp/build.gradle.kts`（:89-90）
- Modify: `iosApp/Info.plist`（CFBundleShortVersionString / CFBundleVersion）
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`（MARKETING_VERSION ×2、CURRENT_PROJECT_VERSION ×2）
- Modify: `version_history.md`（顶部新条目）
- Rewrite: `README.md`
- Create: `.github/FUNDING.yml`

**Interfaces:**
- Consumes: T1 裁决（README 隐私节 iOS 表述按裁决微调：Noop 分支需注明 iOS 统计待接入）。
- Produces: 四处版本戳 30200/"3.2.0"；release commit 的内容基础。

- [ ] **Step 1: 版本戳四处**

`composeApp/build.gradle.kts`：`versionCode = 30200`、`versionName = "3.2.0"`。
`iosApp/Info.plist`：`CFBundleShortVersionString` → `3.2.0`；`CFBundleVersion` → `30200`。
pbxproj：两处 `MARKETING_VERSION = 3.1.1;` → `3.2.0;`；两处 `CURRENT_PROJECT_VERSION = 30101;` → `30200;`。
核对公式：3*10000 + 2*100 + 0 = 30200（docs/VERSIONING.md:12，无需改该文件）。

- [ ] **Step 2: version_history.md 顶部新条目**

```markdown
## v3.2.0 (2026-11-19)

- Analytics：集成 Google Analytics for Firebase（GA4），Android + iOS 双平台匿名使用统计
  （screen_view / feature_used / reminder_scheduled / language_changed / backup_exported /
  pairing_completed；app_open 由 SDK 自动采集），用户属性 app_language / user_role / app_channel。
- 家长设置新增「分享使用数据 / Share usage data」开关（默认开启）：两层拦截，关闭立即停止
  上报且持久化，重新开启不回溯补发；初始化失败降级 Noop，主流程零影响。
- iOS 经 SPM 引入 FirebaseAnalytics（spike 闸门验证），Swift bridge 实现 Kotlin 导出的
  Analytics protocol；Kotlin framework/cinterop/KSP 配置未改。
- 新增埋点 wiring 静态闸门 scripts/analytics_call_sites.py（CI 阻塞）。
- README 重写为中英双语；新增 GitHub Sponsors 捐赠入口（.github/FUNDING.yml）。
- versionCode 30200 / versionName 3.2.0（build.gradle.kts、Info.plist、pbxproj 四处同步）。
```

- [ ] **Step 3: README.md 全文重写**

````markdown
# HStudy

[![Android Build](https://github.com/yaohehe/hstudy/actions/workflows/android.yml/badge.svg)](https://github.com/yaohehe/hstudy/actions/workflows/android.yml)
[![iOS Build](https://github.com/yaohehe/hstudy/actions/workflows/ios.yml/badge.svg)](https://github.com/yaohehe/hstudy/actions/workflows/ios.yml)
[![Release](https://img.shields.io/github/v/release/yaohehe/hstudy)](https://github.com/yaohehe/hstudy/releases/latest)
[![Sponsor](https://img.shields.io/badge/Sponsor-GitHub%20Sponsors-ea4aaa)](https://github.com/sponsors/yaohehe)

**A family learning companion for parents and kids — tasks, focus, review and rewards in one bilingual app.**
**家长与孩子共用的学习伙伴：任务、专注、复习与奖励，一个双语应用搞定。**

[English](#english) | [中文](#中文)

---

## English

### What is HStudy

HStudy pairs a **parent** role and a **child** role on one device (or two paired devices):

- **Tasks & check-ins** — kids check in completed tasks; points are awarded **only after parent approval** (append-only ledger, idempotent approvals).
- **Focus** — pomodoro timer with breathing guide, break cycles and a Schulte-grid focus trainer.
- **Mistake review** — spaced review with daily reminders and active-recall flows.
- **Rewards** — lucky draw, pet care, wish goals with point deposits, weekly learning report.
- **Family sync** — parent configuration export/import and device pairing; full backup & restore.
- **Bilingual UI** — 中文 / English, switchable at runtime (parent settings), system-default aware.

### Platforms & downloads

One Kotlin Multiplatform + Compose Multiplatform codebase ships both platforms:

- **Android** — signed release APK on the [Releases page](https://github.com/yaohehe/hstudy/releases/latest). Sideload it (allow "unknown sources"); uninstall first if a debug/other-signature build is installed.
- **iOS** — simulator `.app` bundle (unsigned) on the same page. Installing on a real device requires an Apple Developer Program membership and signing certificates.

### Build from source

Requirements: JDK 17, Android SDK (for Android), macOS + Xcode (for iOS).

```bash
./gradlew :composeApp:assembleDebug        # Android debug APK
./gradlew :composeApp:testDebugUnitTest    # shared-unit tests (runs on Android JVM)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework (macOS)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build            # iOS simulator app (macOS)
```

Release APKs are signed in CI from repository secrets; the keystore lives outside the repo.

### Analytics & privacy

HStudy uses **Google Analytics for Firebase** to understand which features are used:

- Sent: screen names (route names only), feature-usage counts, reminder scheduling, language
  changes, backup export and pairing completion, plus SDK-provided device model / OS / country /
  timezone. User properties: `app_language`, `user_role`, `app_channel` (debug/release).
- **Never sent:** names, task titles, mistake content, point values, study durations, device
  names, pairing codes, or any free text.
- Firebase assigns a stable pseudonymous `app_instance_id`. It is not personal information, but
  it **can link events from the same device across sessions** — "anonymous" here means
  pseudonymous, not unlinkable.
- **Opt out anytime:** Parent settings → "Share usage data". Off stops reporting immediately
  (both our call sites and SDK auto-collection), persists across restarts, and nothing collected
  while off is sent later.

### Support / donate

HStudy is free. If it helps your family, you can support development via
[GitHub Sponsors](https://github.com/sponsors/yaohehe) — entirely optional, no paywalls.

### Versioning & data compatibility

`versionCode = MAJOR*10000 + MINOR*100 + PATCH` (see [docs/VERSIONING.md](docs/VERSIONING.md));
history in [version_history.md](version_history.md). Room schema v12 with v1→v12 migrations;
upgrades preserve all existing data.

---

## 中文

### HStudy 是什么

HStudy 在同一台设备（或两台配对设备）上提供**家长**与**孩子**两种身份：

- **任务与打卡**——孩子完成打卡，积分**只在家长审批通过后**写入一次（append-only 账本、幂等审批）。
- **专注**——番茄钟（呼吸引导、休息循环）与舒尔特方格专注训练。
- **错题复习**——间隔复习、每日提醒、主动回忆流程。
- **奖励**——每日抽奖、宠物照料、心愿目标存分、每周学习周报。
- **家庭同步**——家长配置导出/导入、设备配对；完整备份与恢复。
- **双语界面**——中文 / English 运行中切换（家长设置），默认跟随系统。

### 平台与下载

一套 Kotlin Multiplatform + Compose Multiplatform 代码同时出两个平台：

- **Android**——[Releases 页](https://github.com/yaohehe/hstudy/releases/latest)提供已签名 release APK，
  可直接侧载（允许"未知来源"）；装过调试版或其他签名版本需先卸载。
- **iOS**——同页提供模拟器 `.app` 包（未签名）。真机安装需 Apple Developer Program 会员与签名证书。

### 从源码构建

环境：JDK 17、Android SDK（Android）；macOS + Xcode（iOS）。

```bash
./gradlew :composeApp:assembleDebug        # Android debug APK
./gradlew :composeApp:testDebugUnitTest    # 共享单测（Android JVM 执行）
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework（macOS）
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build            # iOS 模拟器包（macOS）
```

release APK 由 CI 用仓库 secrets 签名；keystore 不在仓库内。

### 统计与隐私

HStudy 使用 **Google Analytics for Firebase** 了解功能使用情况：

- 发送：界面路由名、功能使用次数、提醒排程、语言切换、备份导出与配对完成，以及 SDK 自带的
  设备型号/系统版本/国家/时区。用户属性：`app_language`、`user_role`、`app_channel`（debug/release）。
- **绝不发送**：姓名、任务标题、错题内容、积分数值、学习时长、设备名、配对码或任何自由文本。
- Firebase 会生成稳定的伪匿名 `app_instance_id`：它不是个人信息，但**可在同一设备上跨事件
  关联**——这里的"匿名"止于伪匿名，不是不可关联。
- **随时关闭**：家长设置 →「分享使用数据」。关闭立即停止上报（调用点与 SDK 自动采集两层），
  重启后仍关闭，关闭期间的数据不会补发。

### 支持 / 捐赠

HStudy 免费。如果它对您的家庭有帮助，欢迎通过
[GitHub Sponsors](https://github.com/sponsors/yaohehe) 支持开发——完全自愿，无任何付费墙。

### 版本与数据兼容

`versionCode = MAJOR*10000 + MINOR*100 + PATCH`（见 [docs/VERSIONING.md](docs/VERSIONING.md)）；
历史见 [version_history.md](version_history.md)。Room schema v12，含 v1→v12 迁移，升级保留全部数据。
````

（若 T1 裁决=Noop：两语隐私节各加一句——EN: "iOS analytics is not wired yet in this release; the toggle still governs Android."；ZH: "本版本 iOS 尚未接入统计；开关仍对 Android 生效。"）

- [ ] **Step 4: FUNDING.yml**

Create `.github/FUNDING.yml`：

```yaml
github: [yaohehe]
```

- [ ] **Step 5: 验证 + commit**

Run: `./gradlew :composeApp:assembleDebug`，然后核对 manifest 版本戳：
`/c/Users/xuany/AppData/Local/Android/Sdk/build-tools/35.0.0/aapt2 dump badging composeApp/build/outputs/apk/debug/composeApp-debug.apk | head -3`
应见 versionCode='30200' versionName='3.2.0'（aapt2 不在该路径时以构建脚本值 + T8 release 校验为准）。
Run: `python scripts/i18n_template_diff.py --branch`（README 不含界面文案，应仍绿）。

```bash
git add composeApp/build.gradle.kts iosApp/Info.plist iosApp/iosApp.xcodeproj/project.pbxproj version_history.md README.md .github/FUNDING.yml
git commit -m "release: v3.2.0 — Analytics Firebase 集成、双语 README、GitHub Sponsors 入口"
```

---

### Task 8: 终审 + 双 CI + 用户确认 + merge/tag/release + 资产校验（controller）

**Files:** 无新代码；操作 git/gh/release 资产。

**Interfaces:**
- Consumes: T1–T7 全部提交；双 CI 绿证据。
- Produces: main 上的 merge commit、tag v3.2.0、GitHub Release 资产、给用户的 DebugView 手动验证步骤。

- [ ] **Step 1: 全分支终审（派最强模型 reviewer）**

review package 用 `scripts/review-package PLAN_FILE <T1-base> HEAD`；重点：spec §3–§7 逐条、两层拦截、无内容外泄、闸门登记计数、版本戳四处、README 隐私表述（app_instance_id 可关联）。ONE fix dispatch + scoped re-review，残差裁决记 ledger。

- [ ] **Step 2: 秘密扫描 + PR ready**

```bash
git diff baec27a..HEAD | grep -nE "ghp_[A-Za-z0-9]{36}" || echo "SECRET_SCAN_CLEAN"
gh pr ready
```

- [ ] **Step 3: 等双 CI 绿（PR head）**

```bash
gh run list --branch feature/analytics-firebase --limit 6
gh run watch <android-run-id> --exit-status
gh run watch <ios-run-id> --exit-status
```

android.yml 四道 i18n 闸门 + analytics 闸门 + assembleDebug + testDebugUnitTest 全绿；ios.yml link + xcodebuild + smoke（PASS 或 infra SKIP）绿。红则 systematic-debugging 修后再跑，**不 push tag**。

- [ ] **Step 4: 向用户做一次证据确认（唯一停止点）**

呈报：双 CI run url + 测试数 + 闸门读数 + 终审结论 + 拟执行动作（merge PR 到 main、push tag v3.2.0、release workflow 自动出资产）。等用户明确同意。

- [ ] **Step 5: merge + tag（获确认后）**

```bash
gh pr merge --merge --delete-branch=false
git switch main && git pull --ff-only
git tag v3.2.0
git push origin v3.2.0
```

（merge 方式 --merge 保留闸门提交历史；不 squash。）

- [ ] **Step 6: release workflow + 资产校验**

```bash
gh run list --workflow release.yml --limit 3
gh run watch <release-run-id> --exit-status
gh release download v3.2.0 --dir dist-v320
```

校验（照 v3.1.1 流程）：
- Android：`aapt2 dump badging dist-v320/HStudy-v3.2.0-signed.apk` → versionCode='30200' versionName='3.2.0'；`java -jar "$LOCALAPPDATA/Android/Sdk/build-tools/35.0.0/lib/apksigner.jar" verify --print-certs dist-v320/HStudy-v3.2.0-signed.apk` → 证书 SHA-256 与 v3.1.1 连续（703e2991…2f00）。
- iOS：解压 zip，`plutil -p`（mac）或 python plistlib 读 Info.plist → CFBundleShortVersionString 3.2.0 / CFBundleVersion 30200；GoogleService-Info.plist 在 bundle 内。
- Release 页资产名与 body 双语说明在位。

- [ ] **Step 7: 给用户 DebugView 手动验证步骤（spec §7 上限）**

1. 侧载 v3.2.0 signed APK（或 debug 包）；
2. GA4 后台 → 管理 → DebugView（设备需开 debug 模式：`adb shell setprop debug.firebase.analytics.app com.hstudy.app`）；
3. 看到 `app_open` 与至少一条 `screen_view`（screen_name 为路由名）；操作打卡/番茄钟看 `feature_used`；
4. 家长设置关闭「分享使用数据」后再操作 → DebugView 无新事件；重新开启 → 恢复；
5. iOS 模拟器包的 app_instance_id 与真机不同，不要误判丢数据；
6. 24h 内实时报告可见事件即投递成立。

- [ ] **Step 8: 更新记忆与 ledger 收尾**

project memory：v3.2.0 released+verified、Analytics 已集成（不再 deferred）、剩余 manual checklist；ledger 全任务 complete。

---

## Self-Review 记录

1. **Spec coverage：** §1 开关/默认→T6；§1 不用 MP→全计划无 MP；§3 接口/Noop/Platform 同构→T2；sealed 目录/无 AppOpen→T2；framework/cinterop/KSP 不改→T3/T4 明示；§4 事件与参数名（screen_name）→T2 映射+T5 埋点；不发送清单→T5 路由首段+类型收口+闸门数据层守卫；app_instance_id 表述→T7 README；§5 键/缺省/两层/持久化/不回溯→T5 Reporter+T6 UI；§6 runCatching/Noop/一行日志→T3/T4；100 字符丢弃→T2+测试；§7 四类单测→T2/T5/T6（调用点 wiring 以静态闸门补，上限诚实披露）；§7 控制台动作→已完成（Global Constraints）；§8 spike+退路→T1/T4；§9 版本/文档→T7（3.2.0 偏离已批准）。
2. **Placeholder scan：** 无 TBD/TODO；T4 退路分支含完整 Podfile/CI 编辑；T7 README 全文在计划内；T6 计数注释含"按实测改数字"的验证步骤（非 placeholder）。
3. **Type consistency：** `AnalyticsMapping.wire`/`propertyName`、`AnalyticsEvent.Feature.*`、`AnalyticsReporter.log/setEnabled/setChannel/setLanguage/setRole`、`UserPreferences.analyticsEnabled/setAnalyticsEnabled`、`initIosPlatform(analytics:appChannel:)` 在 T2–T7 一致；T5 闸门 SITES 与 T6 扩展不冲突（settings 断言 T6 才加）；T5 含 prefs 三行保证每任务可编译（Interfaces 块已注明该决定）。