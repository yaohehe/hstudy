# HStudy 双语（第二阶段）设计

日期：2026-11-10（设计于 2026-11-09 经用户签字）
状态：设计形状已签字（三桶 / 方案 A 显示注册表 / 仅分享标题平台例外 / W0–W9），本 spec 待用户复核
范围：阶段 1 之外的剩余文案铺量。Analytics 不在本 spec 内。

## 1. 目标与非目标

**目标**
- 把阶段 1 未覆盖的页面全部迁入双语文案表，英文态不再出现"整页中文"。
- 预设内容（默认任务 / 默认奖励 / 成就 / 错题类型 / 难度标签 / 宠物场景等）在英文界面显示英文，**数据库一个字节都不改**（方案 A，见 §4）。
- 通知文案（复习提醒 / 每周周报 / 日程闹钟）在发出时按当前生效语言取文案。
- 安卓分享标题（`AndroidFileSharer.kt` 的"分享文件"）随生效语言；这是唯一获准的平台文件改动（用户 2026-11-09 明确授权）。

**非目标（明确排除）**
- 不改 schema、不写迁移、不动 `data/**` 的任何文件（含种子、账本描述、同步 DTO、偏好键）。
- 不本地化**动态业务数据**：积分账本描述（`PointRepository.kt:103/:165`、`WishGoalRepository.kt:48`、`MandatoryPenaltyHelper.kt:81`）、用户自建的 task/reward/wish 文本、用户填写的错题内容。这些是写入时语言冻结的数据，不是界面文案（沿用阶段 1 §5 的不变量）。
- 不动 `ui/dt/DateTimeCompat.kt`（星期名数组 :112 保持中文；英文态日期/星期格式沿用阶段 1 §6 的既定决策）。
- 不动 `Plurals.kt` 机制（英文复数仍 one/other，新增文案沿用 `pointsWord` 模式）。
- 不升级 Compose Multiplatform / Kotlin 版本。
- 不做通知的"发出后改语言追更"——通知文案在发出时冻结，下次发出按新语言（日程/提醒每日重建，可接受）。

## 2. 决策依据（实测证据）

| 事实 | 证据 | 对设计的影响 |
|---|---|---|
| 剩余中文字面量 923 行 / 60 文件 | `cjk_count.py`（排除 i18n 目录与注释行，2026-11-09 复算） | 铺量规模；逐文件分类见 §7 |
| 预设任务标题 55 条去重 + 预设奖励名 27 条 = 82 条 canonical | `TaskRepository.getPresets()/getSingleTaskSuggestions()`、`RewardRepository.initDefaultRewards()` 逐条点数 | 注册表主表规模可数、可交叉校验 |
| 种子标题在多个渲染点原样显示 | `ChildHomeScreen.kt:569` `Text(a.title,…)`；`:264` 把 `hardest.title` 原样插进 `strings.tackleFirstHint(...)` | 渲染点必须包 `localizedPresetText(...)`，否则英文态仍中文 |
| 成就 title/description 会写库 | `AchievementManager.kt:63` `habitRepo.tryUnlockAchievement(def.type, def.title, def.description, def.emoji)` | 成就注册表按 **`def.type`**（稳定业务键）映射，不按标题字节 |
| 宠物场景以 ID 持久化 | `UserPreferences.PET_OWNED_SCENES` 存逗号分隔 ID；`PetScenes.kt:17-20` ID 为 `home/forest/beach/candy…` | 场景注册表按 ID 映射 |
| 错题类型/难度标签/奖励分类是 canonical 枚举字符串 | `MistakeEntity.kt:19`、`TaskEntity.kt:51-53`、`RewardEntity.kt:15-16`（默认值"其他"进 schema） | 列表/默认值原地不动，渲染侧映射 |
| 阶段 1 机制可直接复用 | `AppStrings` 聚合 10 表、`stringsFor`、`LocalStrings`/`LocalDisplayLanguage`、函数型插值字段 | 不发明新机制；注册表是唯一新增件 |
| 等价闸门已扩整枝组 | `c2b6f80` 起 CI 跑 `i18n_template_diff.py --branch`（393/393 绿） | 阶段 2 每波必须把新迁页面的**迁移前 rev** 登记进脚本（§12） |

## 3. 三个工作桶（签字形状）

1. **纯渲染 UI 文案**（约 50 个 `ui/**` 文件）：沿用阶段 1 模式，按域建 strings 表、扩展 `AppStrings`，`"文本"` → `strings.xxx` 等价替换。
2. **预设显示注册表（方案 A）**：DB 保持 canonical 中文；新增 `i18n` 注册表 `canonical → {zh,en}`，`localizedPresetText(raw, lang)` **只**出现在渲染点（Text/snackbar/contentDescription），绝不出现在 DB 写入、bizKey、幂等比较（Ruling 25）。家长改过的标题不再命中注册表 → 原样显示存储值。
3. **分享标题例外**：`AndroidFileSharer.kt` 的硬编码"分享文件"读当前生效语言；iOS 无标题槽，不改。

## 4. 架构与组件

新增 `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/PresetRegistry.kt`：

```kotlin
// 主表：canonical 字节 → 双语条目。zh 值必须等于 key 本身（canonical 即中文）。
data class PresetCopy(val zh: String, val en: String)
val presetTextRegistry: Map<String, PresetCopy>   // 任务标题/描述、奖励名/描述、分类、错题类型、难度、设备名回退、固定诊断文案

fun localizedPresetText(raw: String, lang: DisplayLanguage): String =
    presetTextRegistry[raw]?.let { if (lang == DisplayLanguage.EN) it.en else it.zh } ?: raw

// 键控表：持久化实体带稳定业务键的，按键不按文案字节
data class AchievementCopy(val title: String, val description: String)
val achievementCopy: Map<DisplayLanguage, Map<String, AchievementCopy>>  // key = AchievementDef.type
val petSceneCopy: Map<DisplayLanguage, Map<String, String>>              // key = PetScene.id
```

- **为什么主表按字节、成就/场景按键**：任务/奖励的 canonical 标题本身就是种子写入的稳定值，且家长编辑后必须落回原样显示；成就/场景有显式 ID/type 键，按键更稳（标题改字不影响映射）。
- **回退语义**：未命中 = 原样返回。用户自建内容、家长改过的预设、历史账本描述全部走这条路——注册表永远不需要"认识"用户数据。
- **插值**：种子内容无插值（逐条核对），主表全部固定字符串。界面侧插值仍走阶段 1 的函数型字段。
- **通知语言解析**：新增 `suspend fun currentDisplayLanguage(): DisplayLanguage`（读 `UserPreferences.appLanguage.first()` + `systemLanguageTag()` → `resolveWith`），供无 CompositionLocal 的后台发出点（AppGraph bootstrap、各 Receiver、ScheduleAlarmHelper）使用。界面内仍用 `LocalDisplayLanguage`。
- **分享标题**：`AndroidFileSharer.kt` 由调用方传入已解析的标题字符串（平台文件不读偏好、不依赖 i18n 内部），调用点用 `stringsFor(currentDisplayLanguage())` 取词。

## 5. 存储边界（Ruling 25，本阶段最高不变量）

- 注册表/i18n 表的任何字符串**不得**到达：DB 写入、bizKey 构造、幂等/去重比较、同步 DTO 字段、偏好键值。
- `localizedPresetText` 及一切 `strings.*` 只允许出现在渲染表达式里。现有 `i18n_storage_boundary.py` 闸门（CI 阻塞）继续覆盖；每波本地必跑。
- `data/**` 零改动：种子、账本描述、同步/备份助手、实体、迁移全部原地保留。诊断消息的本地化在**渲染侧**做映射（§9），不改助手返回的 canonical 字符串。
- 交叉校验测试（§10）保证注册表 key 与种子 canonical 字节互为子集——未来改种子名会打破测试，而不是静默显示未翻译文本。

## 6. 注册表内容清单（W0 一次建全）

| 组 | 来源（只读引用，不改原文件） | 条目数 | 键 |
|---|---|---|---|
| 预设任务标题 | `TaskRepository.getPresets()` 44 + `getSingleTaskSuggestions()` 20，去重 | 55 | canonical 字节 |
| 预设任务描述 | 同上 | 60（64 处去重，2026-11-10 实测） | canonical 字节 |
| 模板名/描述/年龄段 | `getPresets()` 3 套 × 3 | 9 | canonical 字节 |
| 预设奖励名/描述 | `RewardRepository.initDefaultRewards()` | 27 + 27 | canonical 字节 |
| 奖励分类 | 娱乐/美食/出行/特权/实物/社交/其他 | 7 | canonical 字节 |
| 成就标题+描述 | `AchievementManager.allAchievements` | 13 组 | `def.type` |
| 错题错误类型 | `MistakeEntity.mistakeErrorTypes` | 6 | canonical 字节 |
| 难度标签 | `TaskEntity` 普通/进阶/挑战 | 3 | canonical 字节 |
| 宠物场景名 | `PetScenes` | 7（home/forest/beach/candy/castle/space/rainbow） | `scene.id` |
| 设备名回退 | 家长/家长设备/孩子设备（`FamilySyncHelper`） | 3 | canonical 字节 |
| 固定诊断文案 | `FamilySyncHelper` 2 条（:140/:226）+ `BackupHelper` 5 条 | 7 | canonical 字节 |

主表合计 204 条固定字符串 + 13 组成就 + 7 条场景。W0 用脚本从上述来源**生成精确清单**并人工填英文；清单落 `docs/superpowers/plans/` 供逐条核对。

## 7. 数据层与特殊文件分类（60 文件全量处置）

**原地不动（canonical 数据 / schema / 注释 / 既定排除），共 112 行：**
- `TaskRepository.kt`(73)、`RewardRepository.kt`(27)：种子，注册表覆盖其显示。
- `Migrations.kt`(2)、`RewardEntity.kt`(2 默认值"其他")：schema 组成部分。
- `PointRepository.kt`(2)、`WishGoalRepository.kt`(1)、`MandatoryPenaltyHelper.kt`(1)：动态账本描述，非目标。
- `UserPreferences.kt`(1)、`WishGoalEntity.kt`(1)、`PointApprovalEntity.kt`(1)：注释，非字面量。
- `DateTimeCompat.kt`(1)：既定排除（阶段 1 §6）。

**canonical 不动、渲染侧映射：**
- `TaskEntity.kt`(3 难度标签)、`MistakeEntity.kt:19`(错误类型列表)、`FamilySyncHelper.kt`(7)、`BackupHelper.kt`(5)：见 §6/§9。

**迁入 strings 表 / 注册表接线（约 50 个 ui 文件 + AppGraph）：** 分波见 §12。

**对账（2026-11-10 实测）**：112（不动）+ 16（data 层 canonical 不动、渲染映射：TaskEntity 3 + MistakeEntity 1 + FamilySyncHelper 7 + BackupHelper 5）+ 793（W1–W8 页面）+ 2（AppGraph 通知，W9）= **923 行 / 60 文件**，与 `cjk_count.py` 全量一致。

## 8. 通知文案

- 发出点：`AppGraph.kt:128/:136`（bootstrap 排程）、`ReviewReminderReceiver.kt`(:25/:55/:60/:61)、`WeeklyReportReceiver.kt`(:28/:29)、`ScheduleAlarmHelper.kt`(:64/:65)。
- 机制：发出时 `currentDisplayLanguage()` → `stringsFor(...)` 取词；日程闹钟里 `${schedule.title}` 属预设/用户内容，包 `localizedPresetText`。
- 冻结语义：已排程通知不追更；复习提醒每日重建、周报每周重建、日程闹钟按日重排，切换语言后最迟下一个周期生效。spec 明示此上限。
- 分波归属：复习提醒→W1，日程闹钟→W5，周报→W6，AppGraph 两处接线→W9。

## 9. 同步/备份诊断消息与设备名回退

- 助手返回的 canonical 字符串不改（`data/**` 零改动）。显示这些消息的渲染点（`ParentSettingsScreen`、`ChildHomeScreen` 的 snackbar/结果行）包 `localizedPresetText`：7 条固定文案精确命中即翻译，未命中（含用户数据）原样。
- 唯一插值消息 `FamilySyncHelper.kt:172`（"已应用${deviceName}的设置：${n}个任务、${m}个奖励"）：渲染侧适配器用**钉死的正则**识别 canonical 格式并重组英文，deviceName 部分再走一次回退映射；正则与 canonical 格式由单测互为钉死（格式漂移 = 测试红）。不命中则原样。
- 设备名回退（"家长设备"/"孩子设备"）按 Ruling 38/42 既定语义：canonical 写入不动，渲染侧映射；用户自设设备名原样（L-17 闭环）。

## 10. 测试策略（commonTest，CI `testDebugUnitTest` 执行）

1. **漏译检测**：每波新增字段断言 EN ≠ ZH（沿用 `StringsCopyTest`/`AssertDiffers`）。
2. **注册表交叉校验**：测试用 FakeDao 捕获 `initDefaultRewards` 写入、直接调 `getPresets()/getSingleTaskSuggestions()`、读 `allAchievements`/`mistakeErrorTypes`/`PetScenes`，断言注册表 key 集合与种子 canonical 集合**互为子集**；成就/场景按键覆盖同理。
3. **回退语义**：`localizedPresetText("家长自定义标题", EN)` 原样返回；`ZH` 恒等于输入。
4. **插值钉死**：新增函数型字段逐条断言两语输出（含英文 one/other）；同步摘要正则适配器两语各钉一条。
5. **通知解析**：`currentDisplayLanguage()` 对 system/zh/en 偏好的返回值（复用阶段 1 解析测试模式）。
6. **存储边界**：`i18n_storage_boundary.py` + 自测每波必跑；注册表字符串不进写库由闸门保证。

## 11. 验证上限（诚实声明）

- 本机无 emulator/adb；Android CI 无模拟器；iOS CI 到"编译+打包+模拟器冒烟（95s 存活闸门）"。
- 可自动证明：编译、单测、字节等价、存储边界、iOS 启动存活。**不可**自动证明：英文界面视觉呈现、换行溢出、通知实际弹出语言。后者进人工验证清单（阶段 1 清单追加阶段 2 页面）。
- 不以"CI 绿"表述"双语已验证"。

## 12. 迁移纪律与分波

- 唯一改动形态：`"文本"` → `strings.xxx` / `localizedPresetText(...)` 等价替换；diff 出现逻辑行变化 = 越界，回滚重做。
- **每波必须**把本波页面的迁移前 rev 登记进 `scripts/i18n_template_diff.py`（BRANCH_PAGES 追加），否则整枝闸门覆盖不到新迁页面；登记后本地跑 `--branch` 必须绿。
- 每波本地四闸：`i18n_storage_boundary.py` + 自测 → `i18n_template_diff.py --branch` → `:composeApp:assembleDebug` → `:composeApp:testDebugUnitTest`；推送经用户逐次批准，CI 复验（含 iOS 链接+冒烟）。
- 按波提交，一波一个 commit；W0+W1 是首个合理落地。

| 波 | 内容 | 文件（行数） |
|---|---|---|
| W0 | 基础设施：注册表全量内容+helper+交叉校验、`currentDisplayLanguage`、AppStrings 扩展点；不迁任何页面 | 新增 i18n 文件 + 测试 |
| W1 | 错题域 | ChildMistakeDetailScreen(55)、ChildMistakeListScreen(52)、ChildMistakeAddScreen(17)、ParentMistakeReviewScreen(13)、SpacedRepetition(7)、ReviewReminderReceiver(4) |
| W2 | 家长任务管理 | ParentTaskEditScreen(26)、ParentAssignTaskScreen(17)、ParentTaskTemplateScreen(16)、ParentTaskManageScreen(12) |
| W3 | 家长积分/审批/奖励 | ParentPointsScreen(19)、ParentApprovalScreen(19)、ParentRewardEditScreen(12)、ParentRewardManageScreen(10) |
| W4 | 孩子商店/积分/抽奖/成就 | ChildRewardStoreScreen(23)、ChildLuckyDrawScreen(12)、ChildPointsScreen(11)、ChildAchievementsScreen(4) |
| W5 | 习惯/目标/日程 | ChildDailyTargetScreen(35)、ParentScheduleManageScreen(30)、ChildHabitChallengeScreen(23)、ChildGoalTrackerScreen(15)、ChildScheduleTimelineScreen(7)、ScheduleAlarmHelper(2) |
| W6 | 心愿/证明/报告/周报 | ChildWishGoalScreen(27)、ParentWishGoalManageScreen(23)、GrowthReportScreen(20)、ParentProofReviewScreen(14)、ChildProofCaptureScreen(11)、WeeklyTrackerScreen(10)、PhotoViewerScreen(3)、WeeklyReportReceiver(2) |
| W7 | 宠物 | ChildPetScreen(38)、PetState(18)、PetScenes(7) |
| W8 | 生成器/引擎文案 | ChildEfficiencyReportScreen(74)、TutoringAdviceEngine(38)、GoalRecommendation(20)、AchievementManager(16 渲染点)、LevelSystem(11)、LearningScore(10)、LearningTree(7)、ParentTutoringAdviceScreen(2)、Animations(1) |
| W9 | 注册表接线收尾+分享标题 | 阶段 1 已迁页面的 task/reward 标题渲染点 sweep、成就/场景/错误类型/难度/分类映射接线、设备名回退、同步/备份诊断映射、AppGraph 通知接线、AndroidFileSharer 分享标题 |

## 13. 术语表增补（沿用阶段 1 §11 规则：优先与代码命名一致）

| 中文 | 英文 | 依据 |
|---|---|---|
| 错题本 | Mistakes | 包名 `ui/mistake`（阶段 1 已定 Mistakes） |
| 举一反三 | Variation | `variationNote`/`variation_5`/`VARIATION` |
| 心愿存钱罐 | Wish Jar | `ui/wish`、`WishGoalEntity` |
| 习惯挑战 | Habit Challenge | `ui/habit`、`habit_challenges` |
| 每日目标 | Daily Target | `DAILY_TARGETS`、`ChildDailyTargetScreen` |
| 专注力训练 | Focus Training | `FOCUS_TRAINING_COUNT`、`ChildFocusTrainingScreen` |
| 舒尔特方格 | Schulte Grid | 通用英文名 |
| 学习宠物 | Study Pet | `ui/pet`、`PET_FULLNESS` |
| 饱食度 / 心情 | Fullness / Mood | `PET_FULLNESS`/`PET_MOOD` |
| 证明 | Proof | `completion_proofs`、`ui/proof` |
| 周报 | Weekly Report | `WEEKLY_REPORT_ENABLED` |
| 辅导建议 | Tutoring Advice | `TutoringAdviceEngine` |
| 效率报表 | Efficiency Report | `ChildEfficiencyReportScreen` |
| 普通/进阶/挑战 | Normal / Advanced / Challenge | 阶段 1 §11 既定 |

各波新增 domain 术语先补本表再填译文；成就名/宠物阶段名等文学性命名在 W0 清单逐条给出并随 spec 复核。

## 14. 已知残留（英文态仍中文的部分，明示不遮）

- 动态账本描述、用户自建内容、历史同步摘要里未命中适配器的文本（§1 非目标）。
- 日期/星期格式（阶段 1 §6 既定）。
- 已发出通知不追更（§8）。
- 阶段 1 残留混排说明行（设置页 partialTranslationNote）在 W9 完成后移除——届时全量页面已双语。