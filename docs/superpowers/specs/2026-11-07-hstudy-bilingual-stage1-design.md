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

**行号引用约定（Ruling 37，Task 11 修复轮确立）**："依据"列里的 `:NNN` 是**迁移前**坐标——那条中文还写在页面里时的那一行；本轮碰到（以及审查点出）的行同时给迁移后坐标，形态是 `迁移前 :NNN（迁移后 :MMM）`。**只有带双坐标的行是"按当前树逐行数过"的**（本轮复算：**57** 条数据行里 10 条带双坐标，逐行数是 Task 11 及其修复轮做的；终审修复波次又补三行——累计专注、家长密码（门禁对话框）、省略号——三行都带 `.kt` 坐标但不带双坐标）；更早各任务留下的单坐标行没有逐行回填（churn 大于收益），读时一律按迁移前坐标理解，需要当前行号请自己 `grep`，不要按字面去那一行找。第三类是**根本没有行号的行**（57 条里 25 条不含任何 `.kt` 文件引用，如"积分 = Points"那行的依据只有代码 `points` / `adjustPoints` / 类名 `ParentPointsScreen`）：它们的依据是代码命名而不是坐标，因此不存在坐标漂移，也不需要回填。**双坐标行按"当前树"负责**：改动过一次页面就要重新数一遍，终审修复波次为此复算过本波次牵连的 4 行（家长设备、每周学习周报分区名、周报、效率报表），并复算出"周报分区那三条 EN 落地值"在写下时就少数 8 行——那句现在写在周报那一行的依据格里，不再是"留给控制器"的待裁决项。

| 中文 | 英文 | 依据 |
|---|---|---|
| 积分 | **Points** | 代码 `points` / `adjustPoints` / `ParentPointsScreen` |
| 番茄钟 | **Pomodoro** | 类名 `PomodoroTimer`；英语世界通用该词 |
| 错题 | **Mistakes** | 包名 `ui/mistake` |
| 习惯 | **Habits** | 包名 `ui/habit` |
| 周报 | **Weekly report** | `WeeklyReportReceiver` / `ReminderKind.WEEKLY_REPORT`。**终审 Ruling 45 把本行从 `Weekly Report` 改成句首大写**：类名与枚举名按 JVM 命名约定继续写 PascalCase，但**界面词形以已实现的英文落地值为准**（`SettingsStrings.kt:457`/`:459`/`:460` 三条 + `ParentHomeStrings.kt:240` 的 `Weekly reports`），不为凑一行术语表重排 6 个 EN 值。牵连的两处逐字引用同批改掉：本表"效率报表"那一行的依据格、以及上面 Task 11 那行分区名。ZH 侧一个字节没动，因此三条 ZH≠EN 仍由 `settingsCopyDiffers` 穷举 |
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
| 任务难度三档（普通 / 进阶 / 挑战） | **Normal / Advanced / Challenge** | Task 9 实测需要；`ChildHomeScreen.kt:249` 与 `data/local/entity/TaskEntity.kt:50-54 difficultyName()`。"进阶"与任务分类"提升"（**Basic / Advanced** 那一行）在英文里同名 Advanced：两者都是"更高的那一档"，但分属**难度轴**与**分类轴**，字段名不同（`difficultyAdvancedLevel` vs `filterEnhancement`），不要合并成一个字段 |
| 先难后易（吃青蛙原则） | **Hard ones first** | Task 9 实测需要；`ChildHomeScreen.kt:258`，代码变量 `hardTasksNotDone`。逐字硬译 "hard first, easy later" 英文里没有这种说法，注释里的"吃青蛙"也不该进界面 |
| 学习表现评分的单位「分」 | **pts** | Task 9 实测需要；`ChildHomeScreen.kt:290` 紧跟 `${learningScore.total}` 的 0–100 评分单位。**不是**积分：与"积分 = Points"必须分开译，否则英文态把一次评分念成 87 积分（同形异义，见 `ChildHomeStrings` 类注释与 `childHomeScoreUnitIsNotPoints`）。Task 10 在同一列上出现第二处出场：`ParentDashboardScreen.kt:142` 的 `ParentDashboardStrings.scoreUnit`，两张表各存一份、由 `parentDashboardScoreUnitIsNotPoints` 断言两侧词形相同，**不**跨表引用（家长页反向依赖孩子端表会把两张表的演化绑在一起）；同一屏里第三种 `分` 是**分钟**，走 `CommonStrings.minuteLabel`（min），三槽互不相同由同一条测试钉死 |
| 心愿存钱罐 | **Wish Pool** | Task 9 实测需要；`ChildHomeScreen.kt:646` 的入口名与"许愿池"是同一个功能（同一路由 `Screen.ChildWishGoal`），沿用本表既有词条，不开第三个名字。与 Reward Store 并列出现时各自带一句说明（本表下方规则） |
| 效率报表 | **Efficiency report** | Task 9 实测需要；`ChildHomeScreen.kt:643`，代码 `ChildEfficiencyReportScreen`。与"周报 = Weekly report"分属两个入口，不可互换（这里的引用形随终审 Ruling 45 一起从 `Weekly Report` 改成句首大写） |
| 家长管理台 | **Parent console** | Task 10 实测需要；`ParentHomeScreen.kt:65`（迁移后 `:67`）顶栏。刻意**不**译成 "Parent dashboard"：本表已有"数据看板 = Data dashboard"（同页 `:212`（迁移后 `:214`）的菜单项与 `ParentDashboardScreen.kt:95`（迁移后 `:98`）顶栏），两个入口在英文里必须能分清 |
| 数据看板 | **Data dashboard** | Task 10 实测需要；`ParentDashboardScreen.kt:95`（迁移后 `:98`）与 `ParentHomeScreen.kt:212`（迁移后 `:214`）两处必须同词形（同一功能的菜单入口名和页面顶栏名）。**Task 11 补上真正的跨表守卫**（Ruling 34 携带项 1）：`StringsCopyTest.parentDashboardCopyDiffers` 末尾两行断言 `parentHome.menuDashboardTitle == parentDashboard.pageTitle.substringAfter(' ')`（中英各一行）。方向以代码为准：带 emoji 的是**页面顶栏**（"📊 Data dashboard"），菜单入口名没有 emoji（"Data dashboard"），所以 `substringAfter(' ')` 只能取在 pageTitle 那一边——计划简报里写的是反方向，那样取到的是 "dashboard"，恒不成立 |
| 快速配置助手 | **Quick setup assistant** | Task 10 实测需要；`ParentHomeScreen.kt:299`，代码 `SetupAssistantCard`。四个补齐按钮（去配置 / 一键载入 / 去设定 / 去设置）英文必须四个值各不相同，见 `parentHomeCopyDiffers` 尾部那条断言 |
| 完成证明 | **Completion proofs** | Task 10 实测需要；`ParentHomeScreen.kt:208`，代码 `habitRepository.getPendingProofCount` / 路由 `ParentProofReview`。与"家长审核 = Approval"是**两个入口**（证明审核 vs 积分申请审核），不可互换 |
| 积分模板 | **Points templates** | Task 10 实测需要；`ParentHomeScreen.kt:203`，路由 `ParentTaskTemplate`；`ParentDashboardScreen.kt:249` 的"请使用积分模板"复用同一个词形 |
| 积分审核 / 积分申请 | **Approve point requests / point requests** | Task 10 实测需要；`ParentHomeScreen.kt:209`（迁移后 `:211`）——**标题与副标题都在这一行**（`MenuAction(strings.menuApprovalTitle, strings.menuApprovalSubtitle, …)` 是一条语句），迁移前的 `:210`（迁移后 `:212`）是错题分析。原引的 `:209-210` 按 Ruling 34(b) 改成 `:209` 是**半对**：那个数字自始至终是迁移前坐标，本轮按双坐标约定把两个坐标同时写下（校正责任在控制器，见 Ruling 37）。与 Task 9 的"我的申请 = My point requests"（`ChildHomeStrings.myApprovalRequestsTitle`）同源，家长侧是动作、孩子侧是列表名 |
| 薄弱项提示 | **Areas to work on** | Task 10 实测需要；`ParentDashboardScreen.kt:258`，代码局部变量 `weakAreas`。逐字硬译 "Weak items" 在英文里是指责孩子而不是提示家长，本行的另一头是"学习状态良好 = Everything looks good" |
| 中文量词「道」（题） | **不译，整词删掉** | Task 10 实测需要；`ParentDashboardScreen.kt:172` 的 `${n}道` 与增减位单位 `道`。英文没有与"道/个/项"对应的量词，值位退化成纯数字、单位位是**空串**（ChildHome 的"今日有 3 道错题待复习 → 3 mistakes to review today"是同一条处置）。空串不是漏译，见 `parentDashboardEnglishDropsTheMeasureWord` |
| 中文量词「次」「个」（家长设置页进度卡） | **不译，值位退化成纯数字** | Task 11 实测需要；`ParentSettingsScreen.kt:279/:283`（迁移后 `:281/:285`）的 `${cp.totalCompletions}次` 与 `${cp.todayCompletedTasks}个`。与上面"道"那条同一条处置，差别是本页连单位槽都不需要开（英文侧没有单位位），见 `settingsEnglishDropsTheMeasureWords` |
| 确定（表单提交按钮） / 确认（通用对话框按钮） | **OK / Confirm** | Task 11 实测需要；`ParentSettingsScreen.kt:429`（迁移后 `:433`）的 `确定`（密码对话框提交，`SettingsStrings.passwordOkButton`）与 `CommonStrings.confirmButton` 的 `确认` **是两个中文值**，差一个字、字节也不同：按 `确认` 复用会改掉中文态字节，各写一份又留下两个英文词可互换，所以英文**分词形**并逐边钉死，见 `settingsOkButtonIsNotTheCommonConfirmButton`。**本波次按当前树复算时更正了 round 1 的一处错配**：原括注"`确认`（本页 `:192`/`:431` 两处按钮已改为复用，迁移后 `:194`/`:435`）"里的四个坐标其实属于**取消位**——本页迁移前 `:192`/`:431` 的裸 `取消` 改的是复用 `CommonStrings.cancelButton`（迁移后 `:194`/`:435`）；恢复对话框的确认位走 `restoreConfirmButton`（原文 `:190` 的裸 `确认恢复`，当前树 `:192`）；`confirmButton` 本页并不使用，它在全仓唯一的使用点是 `ChildMemoryTrainingScreen.kt:105` |
| 家长远程控制 | **Remote control** | Task 11 实测需要；`ParentSettingsScreen.kt:199`（迁移后 `:201`）分区标题，代码 `FamilySyncHelper` 的配对导出/导入。与"设备配对 = Pairing"（本表已有行）分属两侧：配对是动作、远程控制是这一分区的名字；字段在 `settingsCopyDiffers` 的穷举清单里。**同一分区还有一条可见性上限（审查 round 1 记，属既成事实）**：`exportChooserTitle`（迁移前 `:232`，迁移后 `:233`-`:235`）**两个平台都渲染不出来**——`data/sync/FamilySyncHelper.kt:249-260` 收下 title 参数就丢弃，Android 分享面板的标题写死在 `androidMain/…/AndroidFileSharer.kt:27` 的 `"分享文件"`（平台文件，本阶段禁改），iOS 的 `UIActivityViewController` 没有标题位。所以**英文态用户在 Android 上仍看到中文分享标题**，已落在 `docs/i18n-stage1-verification.md` 的已知限制 L-08 与人工验证项 V-29（Task 12 落库，可点开核对）；字段注释在同一处（`SettingsStrings.exportChooserTitle`） |
| 每周学习周报（家长设置页分区） | **Weekly report** | Task 11 实测需要；`ParentSettingsScreen.kt:118`（迁移后 `:120`），代码 `WeeklyReportReceiver`。与 ParentHome 的"周报总览 = Weekly reports"（`ParentHomeStrings.menuWeeklyReportTitle`）同一个词根，不开第三种写法。**大小写曾经是待裁决项，终审 Ruling 45 已经裁决并关闭**：保留已实现的句首大写 `Weekly report`，改的是本表两行（上面"周报"那一行的 `Weekly Report` 词组大写、"效率报表"那一行对它的逐字引用），**页面 EN 值一个都没动**——裁决理由原文是"不为凑一行术语表重排 6 个 EN 值"。三条英文落地值 `weeklyReportTitle` / `weeklyReportOnMessage` / `weeklyReportOffMessage` 在**当前树**的 `SettingsStrings.kt:457`/`:459`/`:460`（审查 round 1 记的 `:435`/`:437`/`:438` 在写下时就已经少数 8 行，本波次按当前树复算改掉，见 verification.md §6 的坐标复算记录）；另加 ParentHome 的 `Weekly reports`（`ParentHomeStrings.kt:240`）同词根复数，三处现在互为引用而不是互相漂移。ZH≠EN 由 `settingsCopyDiffers` 穷举、行首那枚 📊 两侧同枚由 `emojiStaysInsideTheSettingsTable` 钉住 |
| 数据备份与恢复（家长设置页分区） | **Backup and restore** | Task 11 实测需要；`ParentSettingsScreen.kt:143`（迁移后 `:145`）分区标题，代码 `BackupHelper.backup()` / `restore()`（迁移后 `:152` / `:178`）。分区名用名词 **Backup**、按钮 `backupButton` 用动词 **Back up data**，是英文正字法而不是两种译法；`restoreButton` / `restoreDialogTitle` 同根 Restore。行首 💾 两侧同枚由 `emojiStaysInsideTheSettingsTable` 钉住，ZH≠EN 由 `settingsCopyDiffers` 穷举。**这一行是修复轮补的**：表 KDoc 原先就声称 §11 有它、实际没有（假陈述，Ruling 36），现在补齐的是"文档跟代码对齐"，不是新决策 |
| 连续打卡的天数值槽（`N天`） | **N days / 1 day** | Task 11 实测需要；`ParentSettingsScreen.kt:273`（迁移后 `:275`）与 `ParentHomeScreen.kt:114`（迁移后 `:116`）两张表各存一份（刻意不跨表引用，理由同"评分单位 pts"那行），英文 one/other 分支写在函数体内（`Plurals.kt` 本期不改），两表同词形由 `settingsStreakDaysMatchParentHomeForm` 钉住 |
| 家长设备（未填设备名时的兜底值） | **Parent device（存储侧仍是规范中文）** | Task 11 实测需要；`ParentSettingsScreen.kt:299`（迁移后 `:301`）传给 `setDeviceName`（迁移后 `:303`；`UserPreferences.kt:138` DataStore）后又被 `FamilySyncHelper.kt:107` 打进家长控制包传给孩子设备，所以它是**落库值**：中文原字节留在 `CanonicalParentSettingsText.DEVICE_NAME_FALLBACK`，英文态只在只读渲染位换标签（`SettingsStrings.deviceNameDisplayLabel`，调用点迁移后 `ParentSettingsScreen.kt:304`），见 `settingsDeviceNameFallbackStaysCanonical`；可编辑的 OutlinedTextField 那格（迁移前 `ParentSettingsScreen.kt:291`，迁移后 `:293`）**刻意不本地化**（否则英文标签会被下一次保存写回存储）。**被否决的另一种形态（Ruling 38）**：连回执也原样透传（`deviceNameSavedMessage(name)` 不套 `deviceNameDisplayLabel`）——否决理由是那样英文态会念出 `Device name saved: 家长设备`，而回执是**当时唯一**把规范值当**标签**展示的地方，且它只喂 snackbar、没有任何写回路径；能安全本地化的恰好就是没有风险的那一位。**终审 Ruling 42 补上第二处渲染位**：孩子进度卡片标题（当前树 `ParentSettingsScreen.kt:269`）原先把 `cp.deviceName` **裸插**进英文句子，所以"整页唯一"这句在当时是假的——本波次给它套上同一个 `deviceNameDisplayLabel`（中文侧恒等、字节不变，见 `settingsInterpolatedCopyPinsBothLanguages` 的 progressCardTitle 段，规范值那条中英各钉一条字面量），现在两处渲染位都本地化、两处都只喂 Text/snackbar 而不进 sink，所以"没有写回路径"这个前提在两处同样成立。**并更正审查报告认错的值**：`FamilySyncHelper.kt:107` 那个 `家长设备` 兜底走的是**家长导出控制包**的路径（它落到孩子设备的存储里，不出现在本页）；进度包的设备名兜底在 `FamilySyncHelper.kt:190` 与 DTO 默认值 `:82`，值是 `孩子设备`。`deviceNameDisplayLabel` 只认 `CanonicalParentSettingsText.DEVICE_NAME_FALLBACK`，所以"孩子端从没起过名字"那一格在英文态仍会念出 `📊 Study progress from 孩子设备`——把它纳入映射要先给 `孩子设备` 立规范常量，那是数据层副本（本阶段不动数据层），已记为阶段二项（见 verification.md 的 L-17，控制包与进度包两处兜底在同一条里） |
| 累计专注 | **Total focus** | 终审 Ruling 44 实测需要：同一个中文值 `累计专注` 原本在两张表念成两个词形——`FocusStrings.kt:296`（孩子专注统计页，**当前树**坐标，本波次按 head 复算）写 `All-time focus`、`ParentDashboardStrings.kt:189`（家长数据看板）写 `Total focus`。统一取 `Total focus`，理由是同卡片相邻两格已是 Total points / Total check-ins，`All-time` 在这一排里是唯一个异类；改动落在 FocusStrings 一侧（1 个 EN 值，ZH 两侧本来就同值未动）。跨表同形由 `StringsCopyTest.totalFocusLabelIsTheSameTwoWordsInBothTables` 逐字钉住（`assertDiffers` 对"两表两个英文词形"是瞎的，两边都不等于中文） |
| 家长密码（门禁对话框） | **Parent password** | 终审 Ruling 41 实测需要：所有已迁移页面的入口 `AppNavGraph.kt:254`（迁移后该页 CJK 残留 0），新表 `ParentGateStrings.kt:34-36` 三条 = 标题 / 输入框标签 / 校验失败提示；两个按钮词**复用不重造**（`确定` ← `SettingsStrings.passwordOkButton`、`取消` ← `CommonStrings.cancelButton`，见该表类注释），字面量与 ZH≠EN 由 `parentGateCopyDiffers` + `parentGateButtonsComeFromTheirOwnTables` 钉住 |
| 省略号（英文侧书写形式） | **…（U+2026），不写三个句点** | 终审 Ruling 44：`Working...` 与 `Working…` 这种同词两形分裂原先有 3 条 EN 值（`SettingsStrings.kt:466`/`:473`/`:486` 的 Backing up… / restarting… / Working…）与 3 条已用 U+2026 的值并存。统一取多数形状 U+2026，**中文侧两种都有且原字节保留**（`正在处理…` 与 `备份中...` 来自迁移前的不同行，标点参与 `i18n_template_diff.py` 的 [B] 层集合相等判定）。守卫是 `StringsCopyTest.englishTableUsesOneEllipsisForm`（扫整棵英文表 `toString()`），上限也写在这里：函数型字段的返回值不在 `toString()` 里，那部分仍靠各表的逐字双语期望串 |

**Wish Pool 的配套文案规则**（该词在英文里不常见，需靠句子把语义撑起来）
- 首次进入许愿池页面时，副标题必须用一句解释性英文，例如 `Spend your points on wishes you set yourself.`
- 不得在英文态把 Wish Pool 与 Reward Store 混用于同一入口；两者若在同页并列，须各自带一句说明。

## 12. 风险

- 362 条英文质量依赖用户抽查；术语表不一致会造成同一概念多种译法。
- 英文态混排（未迁移页面仍中文）可能被误解为缺陷，需设置页提示兜底。
- `Strings` 表随后续阶段膨胀，需坚持按页面分文件。
