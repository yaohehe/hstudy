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
- 不迁移其余页面（保持中文），因此英文态会出现中英混排——这是分阶段的既定代价。第一阶段覆盖 362 条（占全仓去重 1406 条的 26%）。
- 不本地化写入数据库的种子内容（见 §5）。
- 不引入 locale 相关的日期/数字格式差异（见 §6）。
- 不做通用复数引擎（英文只做 one/other，用函数分支表达）。
- 不升级 Compose Multiplatform 版本。

## 2. 决策依据（实测证据）

| 事实 | 证据 | 对设计的影响 |
|---|---|---|
| CMP 1.7.3 的 commonMain 没有 `androidx.compose.ui.LocalConfiguration` | 本机 `compileCommonMainKotlinMetadata` 探针报 `Unresolved reference 'LocalConfiguration'`（探针文件已删除） | 官方"运行时覆盖 locale"路径不可用；选择自持文案表方案，不为本功能升级 CMP |
| 中文文案 100% 硬编码 | `commonMain` 108 个文件、1661 处中文字面量（去重 1406 条，Python 正则统计）；`composeApp/src/commonMain/res/` 不存在 | 必须新建文案层；`compose.components.resources` 依赖在本阶段成为无用依赖（保留，不本次清理） |
| 中文字符串未被当作逻辑 key | `== "中文"` / `.equals("中文")` 在 commonMain 零命中 | 切换语言不会破坏 `when`/分支逻辑，双语可安全落地 |
| 存在写库的中文字面量 | 125 处 `title = "…"` / `name = "…"`，如 `data/repository/TaskRepository.kt:46` 的默认任务 | 划入非目标，见 §5 |
| 第一阶段规模 | 10 个目标文件共 371 处字面量，按文件去重 362 条，其中 67 条含 `$` 插值 | 362 条为翻译工作量；67 条需函数化。注：先前用 Git Bash grep 统计得 257/244/32，系 `[一-龥]` 字节范围漏匹配，已用 Python 重算更正 |
| 既有可复用约定 | `App()` 根已用 `CompositionLocalProvider(LocalNavigator …)`；`Platform` 为 Noop 兜底 DI；`UserPreferences` 为 `Flow` 属性 + companion 键表；`AppGraph.appScope` 可跑异步 | 不发明新机制，全部沿用 |

## 3. 架构与组件

新增目录 `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/`：

- **`AppLanguage.kt`**
  `enum class AppLanguage { SYSTEM, ZH, EN }`（用户设置值）与 `enum class DisplayLanguage { ZH, EN }`（生效语言）分离。
  纯函数解析：`fun AppLanguage.resolveWith(systemTag: String): DisplayLanguage`
  规则：`ZH`/`EN` 直接映射；`SYSTEM` 时 `systemTag` 去空格后为空 → `ZH`，以 `zh` 开头 → `ZH`，其余 → `EN`。
  分离两个枚举的理由：`SYSTEM` 属于"设置"，不该出现在"生效语言"里；若合一，`stringsFor(SYSTEM)` 必须被赋予含义，那是设计漏洞。
  注：未知语言回退中文，因为既有用户与内容全部是中文语境。

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
  按页面分文件的理由：避免最终 1406 条挤成几千行单文件。

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
3. **插值/函数文案**：67 条含参文案逐条断言两种语言输出，覆盖英文 one/other（1 task / 3 tasks）。
4. **语言解析**：`resolve()` 对 `ZH`/`EN`/`SYSTEM+zh`/`SYSTEM+en`/`SYSTEM+未知` 的返回值。
5. **持久化**：`setAppLanguage` 写入后 `appLanguage` Flow 反映新值。测试需先把 `Platform.dataDir` 指到 `build/` 下的临时目录（DataStore 走 okio 系统文件系统），因此该测试落在能访问真实文件系统的单测源集。

## 9. 验证上限（诚实声明）

- 本机无可用的 emulator/adb 设备；Android CI 无模拟器；iOS CI 仅到"编译 + 打包 + 模拟器启动"。
- 因此**双语的视觉呈现无法自动验证**。可自动证明的只有：编译通过、上述单测通过、语言选择逻辑正确。
- 界面是否整体变英文、有无漏字/换行溢出，需用户在 Android Studio 手动运行确认。
- 本 spec 的实现不以"CI 绿"作为"双语已验证"的表述。

## 10. 迁移纪律

- 唯一允许的改动形态：`"文本"` → `strings.xxx` 的等价替换。diff 中出现任何逻辑行变化视为越界，回滚该文件重做。
- 按页面分组提交（约 6 个 commit），每组本地三闸：`compileCommonMainKotlinMetadata` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`；推送后由 CI 复验，Android 必须保持全绿。
- iOS 侧只需编译/链接通过（现有 iOS Build 工作流覆盖）。

## 11. 术语表（已确认，2026-11-07）

第一阶段所有译文必须引用本表。选择依据优先是"与代码既有命名一致"，避免界面英文与类名各说一套。

| 中文 | 英文 | 依据 |
|---|---|---|
| 积分 | **Points** | 代码 `points` / `adjustPoints` / `ParentPointsScreen` |
| 番茄钟 | **Pomodoro** | 类名 `PomodoroTimer`；英语世界通用该词 |
| 错题 | **Mistakes** | 包名 `ui/mistake` |
| 习惯 | **Habits** | 包名 `ui/habit` |
| 周报 | **Weekly Report** | `WeeklyReportReceiver` / `ReminderKind.WEEKLY_REPORT` |
| 家长审核 | **Approval** | 审批路径为幂等 approve |
| 设备配对 | **Pairing** | `PAIR_CODE` / `paired_device` |
| 宠物饱食度 / 心情 | **Fullness / Mood** | `PET_FULLNESS` / `PET_MOOD` |
| 打卡（动作） | **Check in / Checked in** | 与"连续"概念分开表达 |
| 连续打卡 | **Streak** | `streak_milestones_claimed` |
| 许愿池 | **Wish Pool** | 用户选定，保留品牌感（见下方文案规则） |
| 抽奖 | **Lucky Draw** | `LAST_LUCKY_DRAW` |
| 奖励 / 任务 | **Reward / Task** | 直译，无冲突 |
| 专注 | **Focus** | 包名 `ui/focus` |
| 每日必须任务 | **Daily must-do** | 实测文案"有每日必须任务未完成，已自动扣减 X 分" |
| 困难任务 | **Hard task** | 实测文案"还有 N 个困难任务"；代码 `hardTasksNotDone` |
| 深呼吸 | **Breath** | 实测文案"第 N/M 次深呼吸"；代码 `breathCycle` |
| 呼吸引导 | **Breathing guide** | Task 7 实测需要；`PomodoroTimer.kt` 的 `state == BREATHING` 分支文案 |
| 科学休息 | **Smart break** | Task 7 实测需要；`PomodoroTimer.kt` 休息卡片标题，与 break tips 同组 |
| 专注力训练 | **Focus training** | Task 7 实测需要；包名 `ui/focus` + 代码 `focusTrainingCount` / `focusTrainingBestMs` |
| 舒尔特方格 | **Schulte grid** | Task 7 实测需要；英语世界对该训练的通行名（不是逐字硬译 "Schulte square"） |
| 个人最佳 / 个人纪录 | **Personal best / personal record** | Task 7 实测需要；代码 `focusTrainingBestMs`，两条文案分别对应"最佳成绩"和"破纪录"事件 |
| 时长单位（分/小时/秒） | **min / hr / s** | Task 7 实测需要；与 Task 6 的 `CommonStringsEn.minuteLabel = "min"`、`hourLabel = "hr"` 同源。缩写不随单复数变化，所以时长文案不需要 one/other；`分` 作积分单位时另见 **Points** |
| 时长 chip（`15分`） | **15 min** | Task 7b 实测需要；宿主页 `ChildPomodoroScreen.kt` 的专注/休息选项 chip。中文 `15分` 无空格、英文有空格，且单位仍取 `CommonStrings.minuteLabel`（不为这一处开第三个 `分` 字段） |
| 基础 / 提升（任务分类） | **Basic / Advanced** | Task 6+8 实测需要；`ParentDashboardScreen`、`WeeklyTrackerScreen`、`ParentTaskManageScreen` 会复用 |
| 记忆广度 / 数字记忆 | **memory span / number memory** | Task 7b 实测需要；`ChildMemoryTrainingScreen.kt` 顶栏与页内标题，两种训练之一（另一种是舒尔特方格） |
| 工作记忆 | **working memory** | Task 7b 实测需要；`memoryHowTo`、`memoryEntryButton` 的说法与 §11"专注力训练"行一致（选择性注意 vs 工作记忆互补） |
| 黄金专注时段 | **golden focus window** | Task 7b 实测需要；`ChildPomodoroScreen.kt` 由历史专注数据算出的高效时段（`goldenHour`），逐字硬译 "golden focus period" 会与"时段"混淆 |
| 时段词（早上/上午/中午/下午/晚上/深夜） | **Early morning / Morning / Midday / Afternoon / Evening / Late night** | Task 7b 实测需要；六个值必须互不相同（同一字段的两两 ZH≠EN 断言依赖这一点），且英文按 24 小时制配 `8:00` 而不是 a.m./p.m. |
| 今日计划 / 每日计划 | **today's plan** | Task 7b 实测需要；`ChildDailyPlanScreen.kt` 顶栏与空状态文案；与"任务 = Task"分行使用（计划是时间块，任务是打卡项） |
| 积分规则 | **Points rules** | Task 7b 实测需要；`PomodoroStrings.pointsRulesTitle`，与"积分 = Points"同源 |
| 我是家长 / 我是孩子 | **I'm the parent / I'm the learner** | 用户选定 learner，避免 kid 的低龄感，且与 parent 对称 |
| 学习积分奖励（品牌行） | **Study Points & Rewards** | 实测 `RoleSelectScreen.kt:75` |
| 好习惯，看得见 | **Build visible habits** | 实测 `RoleSelectScreen.kt:83`，允许按英文语序重组，不逐字硬译 |

**Wish Pool 的配套文案规则**（该词在英文里不常见，需靠句子把语义撑起来）
- 首次进入许愿池页面时，副标题必须用一句解释性英文，例如 `Spend your points on wishes you set yourself.`
- 不得在英文态把 Wish Pool 与 Reward Store 混用于同一入口；两者若在同页并列，须各自带一句说明。

## 12. 风险

- 362 条英文质量依赖用户抽查；术语表不一致会造成同一概念多种译法。
- 英文态混排（未迁移页面仍中文）可能被误解为缺陷，需设置页提示兜底。
- `Strings` 表随后续阶段膨胀，需坚持按页面分文件。
