# HStudy 双语（第一阶段）设计

日期：2026-07-11（撰写于 v3.0.0 发布后）
状态：已与用户逐节确认，待写实现计划
范围：仅第一阶段。全量 1370 条文案的迁移不在本 spec 内。

## 1. 目标与非目标

**目标**
- App 界面支持中文 / 英文，默认跟随系统语言，家长设置页可手动覆盖（跟随系统 / 中文 / English）。
- 语言选择持久化，重启后保持；切换即时生效，不需要重启 App，不打断进行中的番茄钟计时。
- 第一阶段真正双语的页面：角色选择（启动）、家长首页、家长仪表盘、孩子首页、孩子任务列表、专注/打卡（含 `common/PomodoroTimer.kt`）、家长设置，以及 `common/CommonComponents.kt` 中的公共控件文案。

**非目标（明确排除）**
- 不迁移其余页面（保持中文），因此英文态会出现中英混排——这是分阶段的既定代价。
- 不本地化写入数据库的种子内容（见 §5）。
- 不引入 locale 相关的日期/数字格式差异（见 §6）。
- 不做通用复数引擎（英文只做 one/other，用函数分支表达）。
- 不升级 Compose Multiplatform 版本。

## 2. 决策依据（实测证据）

| 事实 | 证据 | 对设计的影响 |
|---|---|---|
| CMP 1.7.3 的 commonMain 没有 `androidx.compose.ui.LocalConfiguration` | 本机 `compileCommonMainKotlinMetadata` 探针报 `Unresolved reference 'LocalConfiguration'`（探针文件已删除） | 官方"运行时覆盖 locale"路径不可用；选择自持文案表方案，不为本功能升级 CMP |
| 中文文案 100% 硬编码 | `commonMain` 108 个文件、1370 处中文字面量；`composeApp/src/commonMain/res/` 不存在 | 必须新建文案层；`compose.components.resources` 依赖在本阶段成为无用依赖（保留，不本次清理） |
| 中文字符串未被当作逻辑 key | `== "中文"` / `.equals("中文")` 在 commonMain 零命中 | 切换语言不会破坏 `when`/分支逻辑，双语可安全落地 |
| 存在写库的中文字面量 | 125 处 `title = "…"` / `name = "…"`，如 `data/repository/TaskRepository.kt:46` 的默认任务 | 划入非目标，见 §5 |
| 第一阶段规模 | 目标文件 257 处字面量，去重 244 条，其中 32 条含 `$` 插值 | 244 条为翻译工作量；32 条需函数化 |
| 既有可复用约定 | `App()` 根已用 `CompositionLocalProvider(LocalNavigator …)`；`Platform` 为 Noop 兜底 DI；`UserPreferences` 为 `Flow` 属性 + companion 键表；`AppGraph.appScope` 可跑异步 | 不发明新机制，全部沿用 |

## 3. 架构与组件

新增目录 `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/`：

- **`AppLanguage.kt`**
  `enum class AppLanguage { SYSTEM, ZH, EN }`
  纯函数解析：`fun resolve(setting: AppLanguage, systemTag: String): AppLanguage`
  规则：`ZH`/`EN` 直接返回；`SYSTEM` 时 `systemTag` 以 `zh` 开头 → `ZH`，否则 → `ZH`（未知语言回退中文，保持现状行为）。
  注：回退选中文而非英文，因为既有用户与内容全部是中文语境。

- **`SystemLocale.kt`**
  `expect fun systemLanguageTag(): String`
  androidMain：取 `Locale.getDefault().language`；iosMain：取 `NSLocale.currentLocale` 的 languageCode。这是全套设计中唯一的 expect/actual 点。

- **`Strings.kt` + 按页面分文件（`HomeStrings.kt`、`SettingsStrings.kt`、`FocusStrings.kt`…）**
  按页面分组的 data class：
  ```kotlin
  data class HomeStrings(val title: String, val emptyHint: String, val minutesLabel: (Int) -> String)
  data class AppStrings(val home: HomeStrings, val role: RoleStrings, val pomodoro: PomodoroStrings, val parentSettings: ParentSettingsStrings, …)
  ```
  两种语言各构造一份（`AppStringsZh` / `AppStringsEn`）。
  选择 data class 而非 `Map<String, String>` 的理由：漏一条英文 = 编译错误，而不是运行时静默掉回中文。
  带参数文案存**函数**不存 `{0}` 模板：中英文语序不同（"还剩 3 个任务" vs "3 tasks left"），位置插值在英文里会写出洋泾浜；函数各自表达语序且可直接单测。英文复数在函数内用 one/other 分支。
  按页面分文件的理由：避免最终 1370 条挤成几千行单文件。

- **`LocalStrings.kt`**
  `val LocalStrings = staticCompositionLocalOf<AppStrings> { error("LocalStrings 未提供") }`

- **`LanguagePreference`（挂在既有 `UserPreferences`）**
  `val LANGUAGE_KEY = stringPreferencesKey("app_language")`（存 `"system"`/`"zh"`/`"en"`，缺省视为 `system`）
  `val appLanguage: Flow<String?>` + `suspend fun setAppLanguage(value: String)`

## 4. 数据流

```
UserPreferences.appLanguage (Flow)  +  systemLanguageTag()
        └──────────► resolve() ──► AppLanguage ──► AppStrings（zh 或 en）
                                     │
                        App(): CompositionLocalProvider(LocalStrings provides strings)
                                     │
                          各页面 strings.home.title
```
单向、无重启：写入偏好 → Flow 发出 → 根重组 → 全 App 文案切换。语言切换不重建 `AppNavigator`、不触碰 ViewModel 状态，因此进行中的番茄钟计时不受影响。

## 5. 边界：不本地化写入数据的内容

`TaskRepository` 等处向数据库写入中文默认任务（"完成当天作业"等）。本阶段**不动**。理由：若种子内容随界面语言变化，同一份 schema 会因安装语言不同写入不同行，且用户事后切换语言不会回改历史数据，显示与数据将永久错位；这与"schema v12 + 迁移前自动备份 + 数据连续"的既有不变量冲突。此项列为后续独立决策。

## 6. 边界：日期与数字格式

`ui/dt/DateTimeCompat.kt` 保持现状（ISO、周一起始、`yyyy-MM-dd`）。英文界面日期仍按此显示。理由：该兼容层刻意固定了与原 Android 中文 locale 一致的行为，引入 locale 相关格式会触及业务时间语义，属于另一个变更。

## 7. 设置页新增项

`ParentSettingsScreen` 增加「语言 / Language」三选控件（跟随系统 / 中文 / English），写入偏好即时生效。控件文案本身随语言切换。英文态在该页附一行说明：部分页面仍在适配中（避免让用户误以为功能未完成是 bug）。

## 8. 测试策略（全部在 commonMain，由 CI 的 `testDebugUnitTest` 执行）

1. **漏译检测**：对第一阶段每个字段，断言 EN 值不等于对应 ZH 值（防止整表复制粘贴伪翻译）。
2. **成对性**：由 data class 构造保证，无需运行时校验。
3. **插值/函数文案**：32 条含参文案逐条断言两种语言输出，覆盖英文 one/other（1 task / 3 tasks）。
4. **语言解析**：`resolve()` 对 `ZH`/`EN`/`SYSTEM+zh`/`SYSTEM+en`/`SYSTEM+未知` 的返回值。
5. **持久化**：`setAppLanguage` 写入后 `appLanguage` Flow 反映新值。

## 9. 验证上限（诚实声明）

- 本机无可用的 emulator/adb 设备；Android CI 无模拟器；iOS CI 仅到"编译 + 打包 + 模拟器启动"。
- 因此**双语的视觉呈现无法自动验证**。可自动证明的只有：编译通过、上述单测通过、语言选择逻辑正确。
- 界面是否整体变英文、有无漏字/换行溢出，需用户在 Android Studio 手动运行确认。
- 本 spec 的实现不以"CI 绿"作为"双语已验证"的表述。

## 10. 迁移纪律

- 唯一允许的改动形态：`"文本"` → `strings.xxx` 的等价替换。diff 中出现任何逻辑行变化视为越界，回滚该文件重做。
- 按页面分组提交（约 6 个 commit），每组本地三闸：`compileCommonMainKotlinMetadata` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`；推送后由 CI 复验，Android 必须保持全绿。
- iOS 侧只需编译/链接通过（现有 iOS Build 工作流覆盖）。

## 11. 术语表（先定，后生成英文）

实现前需与用户确认下列核心术语的官方英文，第一阶段所有译文遵循该表：

| 中文 | 待定英文 |
|---|---|
| 积分 | Points / Stars |
| 番茄钟 | Pomodoro / Focus Timer |
| 错题 | Mistakes / Wrong Answers |
| 习惯 | Habits |
| 许愿池 | Wish Pool / Rewards Wishlist |
| 宠物（饱食度/心情） | Pet (Fullness / Mood) |
| 周报 | Weekly Report |
| 打卡 | Check-in |
| 家长审核 | Parent Approval |
| 设备配对 | Device Pairing |

## 12. 风险

- 244 条英文质量依赖用户抽查；术语表不一致会造成同一概念多种译法。
- 英文态混排（未迁移页面仍中文）可能被误解为缺陷，需设置页提示兜底。
- `Strings` 表随后续阶段膨胀，需坚持按页面分文件。
