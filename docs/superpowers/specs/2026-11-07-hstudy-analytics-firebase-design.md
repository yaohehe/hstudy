# HStudy Analytics（Google Analytics for Firebase）设计

日期：2026-11-07（撰写于 v3.0.0 发布后）
状态：已与用户逐节确认，待写实现计划
依赖：子项目一「双语」先落地（设置页开关文案依赖 `Strings`）

## 1. 目标与非目标

**目标**
- 接入 Google Analytics for Firebase，采集最小匿名的使用统计，用于回答"哪些功能真的被用、哪些页面是死路"。
- 家长设置页提供「分享使用数据」开关，默认开启，可关闭且关闭状态持久化。
- 双端（Android / iOS）行为一致；Firebase 初始化失败绝不影响 App 功能。

**非目标**
- 不采集任何学习内容、任务文本、积分数值、姓名、设备名、配对码。
- 不自建事件队列/批量/重试（Firebase SDK 自带）。
- 不使用 GA4 Measurement Protocol（其 `api_secret` 必须随请求明文出现在客户端，可被从发布包中提取并用于污染数据）。
- 不引入 Crashlytics、Remote Config、FCM 等其他 Firebase 产品。
- 不承诺"数据真的进了 GA4"由自动化证明（见 §7）。

## 2. 为什么是 Firebase SDK 而不是 MP

| 维度 | Firebase SDK（选定） | Measurement Protocol（否决） |
|---|---|---|
| 客户端凭据 | 使用配置文件里受限的 api_key，按设计随包分发；无服务端秘密 | 必须在 URL query 明文携带 `api_secret`，任何人可从 APK/IPA 提取后写入垃圾事件 |
| 自动事件 | session / first_visit / user_engagement / 设备与系统信息 | 全部保留不可发，需自定义事件名模拟 |
| 构建链影响 | Android 加 google-services 插件；iOS 需 SPM/CocoaPods（风险所在） | 零构建链改动 |
| 数据流形态 | App / Firebase 数据流（用户选定） | 可选 Web 或 App |

结论：用户明确接受构建链风险以换取"客户端不携带任何秘密 + 自动事件"。风险缓解见 §8。

## 3. 架构：Firebase 归平台所有，Kotlin 只认接口

关键约束（决定架构，非偏好）：Kotlin/Native 的 `iosMain` 无法直接调用通过 SPM 链接进 App target 的 `FirebaseAnalytics`——没有对应的 cinterop 模块。为其做 module map + cinterop 等于重开刚修绿的 interop 战线。

因此：

- **`commonMain`**：`platform/Analytics.kt`
  ```kotlin
  enum class UserProperty { APP_LANGUAGE, USER_ROLE, APP_CHANNEL }

  interface Analytics {
      fun logEvent(event: AnalyticsEvent)
      fun setUserProperty(property: UserProperty, value: String?)
      fun setEnabled(enabled: Boolean)
  }
  object NoopAnalytics : Analytics { /* 全部空实现 */ }
  ```
  `Platform` 增加 `var analytics: Analytics = NoopAnalytics` 与 `init(... analytics: Analytics = NoopAnalytics)`，与既有 `notifications` / `fileSharer` / `camera` / `deviceDiscovery` 完全同构。

- **事件目录用 sealed class 固定**，不接受自由字符串：
  ```kotlin
  sealed class AnalyticsEvent {
      data class ScreenView(val route: String) : AnalyticsEvent()
      data class FeatureUsed(val feature: Feature) : AnalyticsEvent()
      data class ReminderScheduled(val kind: ReminderKind) : AnalyticsEvent()
      data class LanguageChanged(val to: AppLanguage) : AnalyticsEvent()
      object BackupExported : AnalyticsEvent()
      object PairingCompleted : AnalyticsEvent()

      enum class Feature { POMODORO_START, POMODORO_COMPLETE, CHECKIN, LUCKY_DRAW,
                           PET_CARE, WISH_SUBMIT, APPROVAL_ACTION, BACKUP_EXPORT, PAIRING }
  }
  ```
  不含 `AppOpen`：`app_open` 由 SDK 自动采集，自报会造成重复事件与死分支。
  用户属性同样收口为 `UserProperty` 枚举，避免 `setUserProperty` 成为自由字符串的后门。
  新增任何会外发的信息都必须改这些类型 → 每次埋点扩展都过代码审查，且单测可穷举。

- **Android**：`androidMain/kotlin/.../platform/FirebaseAnalytics.kt` 实现接口，调用 `com.google.firebase.analytics.FirebaseAnalytics`，`AnalyticsEvent` 映射为 SDK 的事件名 + `Bundle` 参数。
- **iOS**：Kotlin 接口经 framework 导出为 ObjC protocol，由 **Swift** 在 `iosApp` 中实现（`final class FirebaseAnalyticsBridge: Analytics`），在 `iOSApp.init()` 里传入 `IosPlatformKt.doInitIosPlatform(analytics:)`。Swift 侧调用 `FirebaseAnalytics.logEvent`。
- Kotlin framework 的构建脚本、cinterop、KSP 配置**一行不改**。

## 4. 事件与字段

**发送**
- `app_open`（SDK 自动；不重复自报）
- `screen_view`，参数 `screen_name = Screen.*.route`（复用既有路由名，不新造）——Compose Multiplatform 下 SDK 无法自动得知路由，需我们主动发
- `feature_used`，参数 `feature`（枚举名，snake_case）
- `reminder_scheduled`，参数 `kind`
- `language_changed`，参数 `to`
- `backup_exported`、`pairing_completed`
- Firebase SDK 自带的设备型号、系统版本、国家、时区、语言

**明确不发送**
任务标题、错题内容、题目文本、积分数值、学习时长数值、孩子姓名、设备名（`DEVICE_NAME` 偏好值）、配对码（`PAIR_CODE` 偏好值）、任何自由文本。
`checkin` 只表示"发生了一次打卡"，不含是哪条任务。

**用户属性**（需在 GA4 后台注册后进报表）
- `app_language`（zh / en）
- `user_role`（parent / child）
- `app_channel`（debug / release）

**必须写进隐私说明的事实**
Firebase 会生成稳定的 `app_instance_id`（伪匿名设备标识）。它不是 PII，但可在同一设备上跨事件关联。"最小匿名"的含义止于此，文档不得表述为"匿名不可关联"。

## 5. 开关与默认值

- `UserPreferences` 新增 `ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")`，缺省 `true`。偏好文件名与路径不变，schema 不变（仅新增一个键，兼容既有 `hstudy_prefs.preferences_pb`）。
- 家长设置页新增「分享使用数据 / Share usage data」开关，位于语言选择项下方；文案走子项目一的 `Strings`。
- 关闭时两层拦截：
  1. 调用点先读标志位，关闭则连 `Platform.analytics` 都不碰；
  2. 同时调 `Analytics.setEnabled(false)` → Android/iOS 各自调 `setAnalyticsCollectionEnabled(false)`，关掉 SDK 自身的自动采集。
- 关闭状态持久化，重启后仍关闭；重新开启不回溯补发关闭期间的数据。
- 设置页附一行说明采集范围（对应 §4 的"发送/不发送"），双语。

## 6. 失败处理

- `FirebaseApp` 初始化与 `FirebaseAnalytics` 获取全部包在 `runCatching` 中；失败降级为 `NoopAnalytics`，控制台输出一行原因，App 功能零影响。理由：CI 产出未签名包，配置解析失败在模拟器/真机上是真实可能路径。
- 网络不可达时不做任何自为处理（SDK 负责缓冲）。
- 事件参数值长度上限 100 字符，超长直接丢弃该参数而非截断（避免把内容裁成可猜的片段）。由于参数已收口为枚举与路由名，这条实际只作用于 `screen_name`，作为兜底守卫保留。

## 7. 测试与验证上限

**可自动化证明（commonMain 单测，CI `testDebugUnitTest` 执行）**
1. `FakeAnalytics` 记录调用：断言各调用点发出预期的事件名与参数。
2. 开关拦截：`analytics_enabled=false` 时 `FakeAnalytics` 收到零次调用。
3. 事件目录穷举：`AnalyticsEvent` 每个分支的映射结果（含 `Feature` 全枚举）。
4. 无内容外泄守卫：断言所有事件参数值均取自枚举/路由常量，不含来自实体字段的字符串（以类型约束实现：参数类型限定为枚举与路由名，不接受任意 `String` 传入内容字段）。

**无法自动化证明**
本机无可用 emulator/adb 设备；Android CI 无模拟器；iOS CI 仅到未签名模拟器包。因此**没有任何自动化能证明事件真的进入 GA4**。真实投递需用户手动验证一次：
1. 安装 debug 包；
2. GA4 后台「调试 / DebugView」（或 24 小时内的实时报告）；
3. 看到 `app_open` 与至少一条 `screen_view`；
4. 关闭开关后再操作，确认无新事件。
iOS 模拟器产生的 `app_instance_id` 与真机不同，验证时不要误判为丢数据。

**用户需在后台完成的动作**
- Firebase 项目启用 Google Analytics 并与现有 GA4 属性关联；注册 Android（包名）与 iOS（bundle id `com.hstudy.app.ios`）应用。
- 下载 `google-services.json` 放 `composeApp/`，`GoogleService-Info.plist` 放 `iosApp/` 并加入所有 target。
- GA4 注册 3 个用户属性；为自定义事件参数建立维度（可选）。
- 在 Google Cloud 控制台给配置文件里的 api_key 加上 iOS bundle ID / Android 包名限制（公开仓库里必须做）。

## 8. 构建链风险与实施闸门

1. 全程在 `feature/analytics-firebase` 分支 + PR 上进行，靠现有 `pull_request` 触发 iOS/Android 工作流；main 与 `v3.0.0` 不受影响。
2. **先做一次性 spike**（可丢弃，不进 main）：仅"SPM 引入 FirebaseAnalytics + 空 logEvent + CI 能产出未签名模拟器包"，回答唯一问题——GitHub macOS runner 上未签名模拟器构建能否解析并链接 Firebase SPM 包。
   - 通过 → 按 §3 正式实现。
   - 不通过 → 依次退路：CocoaPods（CI 命令需从 `-project` 改 `-workspace`，并确认 runner 仍预装 pod）；或退回"接口层 + Noop 实现"，等具备真机环境再接。
3. 闸门顺序，每步独立 PR，任一步红则停在该步、不回滚已绿步骤：spike → Android 接入 → iOS 接入 → 事件目录与调用点 → 开关与设置页 → 文档与版本号。
4. 回滚代价：Kotlin framework 与 cinterop 未改动，最坏情况删除 `iosApp` 的 SPM 引用与 plist、移除 Android 插件与 json，即回到当前绿灯状态。

## 9. 版本与文档影响

- 与子项目一合并计为 MINOR：`v3.1.0` / `versionCode 30100`。
- 同步四处版本字段：`composeApp/build.gradle.kts`、`iosApp/Info.plist`、pbxproj 的 `MARKETING_VERSION` 与 `CURRENT_PROJECT_VERSION`。
- 更新 `version_history.md`、`README`（新增"埋点与隐私"章节；工程结构图补 `composeApp/google-services.json` 与 `iosApp/GoogleService-Info.plist`）、`docs/VERSIONING.md`。
- tag `v3.1.0` 仅在 Android + iOS 双绿提交上打（沿用 v3.0.0 纪律）。

## 10. 风险

- iOS SPM 与未签名 CI 构建的兼容性未验证（由 §8 的 spike 前置消解，不拿 main 赌）。
- 采集范围与 Play 控制台数据安全表单、以及"面向儿童"的政策要求需用户自行确认合规；本设计只保证技术上最小化，不构成合规结论。
- 事件目录一旦扩张（后续阶段）容易顺手带上内容字段；sealed class + 单测守卫是主要防线。
