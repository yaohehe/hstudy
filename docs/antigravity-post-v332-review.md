# HStudy v3.3.2 独立代码复审与证据报告（主会话终审版）

> **分支**：`feature/v332-hardening`（审查提交 `42f757d`）
> **报告来源**：Antigravity Desktop 2.34.0 在指定工作区实际生成初稿并完成两轮修订；Windows 锁屏阻止第三轮 UI 输入后，主会话依据源码、JUnit XML 与闸门输出完成独立终审更正。下文结论是双方材料的合并裁决，不把主会话更正冒称为 Antigravity 自述。
> **审查基准**：基于源码实际阅读（`composeApp/commonMain/androidMain/iosMain`、DAO、实体、迁移、仓库、UI调用链）与真实测试运行记录（`TEST-*.xml`、静态闸门脚本）
> **审查约束**：未修改任何产品/测试/配置/其他文档代码，不 commit/push；仅维护本报告。
> **核心声明**：**本报告列出的所有新发现缺陷与风险，均为源码静态确认、未动态复现。测试通过只证明相应测试覆盖范围，不等于所有业务流已经真机或并发验证。**

---

## 一、执行摘要

### 1.1 静态闸门执行真实原始读数

4 条生产闸门与 2 条支撑自测均实际执行完毕，退出码均为 `0`：

**生产闸门**

1. `python scripts/analytics_call_sites.py`
   - 退出码：`0`
   - 读数：`ANALYTICS_GATE_OK: 21 site checks + data-layer zero-call guard`
2. `python scripts/i18n_storage_boundary.py`
   - 退出码：`0`
   - 读数：扫描 180 个 `.kt` 文件，7 条 ASCII note 业务键，边界检查通过。
3. `python scripts/i18n_template_diff.py`
   - 退出码：`0`
   - 读数：118/118 模板；`[A] YES`；`[B] YES`，118/118 中文文案。
4. `python scripts/i18n_template_diff.py --branch`
   - 退出码：`0`
   - 读数：984/984 检查项；`[A] YES`，0 missing/invented；`[B] YES`，980/980 中文文案。

**支撑自测（不计作生产闸门）**

5. `python scripts/tests/i18n_storage_boundary_selftest.py`
   - 退出码：`0`
   - 读数：202 条断言，失败 0 条。
6. `python scripts/tests/i18n_template_diff_selftest.py`
   - 退出码：`0`
   - 读数：37 条断言，失败 0 条。

两种模板检查均只保留已登记的格式串拆分残余 `'%.1f 秒'` 与 `'⟨$⟩ 秒'`，不是新增缺陷。

### 1.2 Gradle 单元测试真实执行命令与读数

- **执行命令**：
  `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" GRADLE_USER_HOME="D:/gradle-home" ./gradlew :composeApp:testDebugUnitTest --rerun-tasks --console=plain`
- **执行结果**：`BUILD SUCCESSFUL in 47s`
- **机器 XML 真实统计**（解析自 `composeApp/build/test-results/testDebugUnitTest/TEST-*.xml`）：
  - **测试 XML 文件总数**：37 个类
  - **测试用例总数**：325 条
  - **失败 (Failures)**：0
  - **错误 (Errors)**：0
  - **跳过 (Skipped)**：0

### 1.3 核心发现摘要

- **P0（阻断级缺陷）**：**未发现新缺陷**。核心账本不变量、防重入互斥锁以及 D-01～D-13 涉及的关键链路未发现阻断级漏洞。
- **P1（高风险/并发数据破坏/功能断裂）**：共 8 项（均为源码静态确认）。涵盖：单条审批与打卡读判写非事务及乐观锁缺失、任务模板应用非原子清空、心愿超额存入与存取无事务、Android 周报首周后不再排期（iOS repeats=true 平台差异）、备份与恢复破坏性操作无回滚且导入未校验 SQLite 完整性、家庭同步空配置覆盖全量软删除且无事务保护、任务频率定义被打卡机制完全忽略、宠物照料与场景购买陈旧余额读写。
- **P2（中风险/状态不一致/数据失真/防御薄弱）**：共 29 项（均为源码静态确认、未动态复现）。涵盖：任务 bonusPoints 永久无发分路径、证明审核终态竞态（无 PENDING 守卫）、每日目标重置不清旧审批与 bonus、番茄钟结算后旧会话写回状态覆盖、成就进度硬编码 (0 to 1) 且页面不自动刷新、宠物成长与报表把退款当收入、Android 系统 Back 键未接线应用栈直接退出、全表 Flow<List> 无分页扩展性缺口、PointApprovalRepository 存在废弃重复 API、日程 repeatDays 被写死为 daily、报表历史积分用当前分值逆乘与活跃数作分母、专注训练首破纪录提示失效、布置任务先完成再申请失败早退、习惯挑战整实体覆盖与成就无唯一约束、错题复习三表分裂写、商城兑换记录与扣费分裂写、未完成扣分返回未封顶值、通知点击缺深链路由、任务软删残留日程外键、跨午夜时间快照不自动推进、目标追踪完成率超 100%、家长密码明文存储、数据库迁移测试仅使用 FakeConnection 缺乏真实 SQLite 物理迁移测试、奖励预设可重复插入、家庭进度导出多查询非一致快照、家庭进度导入缺少数值一致性校验、分享失败仍提示成功、周追踪历史积分与日期口径失真、实时设备发现能力未接入产品流程。
- **已确认修复项**：D-01～D-13 修复逻辑经源码比对与回归测试确认生效，不作为新缺陷列出。
- **已知残余与设计边界说明**：
  1. 宠物与商城硬编码中文 note（Ruling 25 已披露残余，不作为新 P1）；
  2. iOS `DirectTransactionRunner` 直通降级（F-7 披露）；
  3. `spendPoints` 准确语义：默认 `requireSufficientBalance = false`，对正数总会写入负分流水；只有显式传入 `requireSufficientBalance = true` 时才做余额校验并在余额不足时返回 false。

---

## 二、逐流程矩阵

### 2.1 路由完整性核对（43 个 Screen 目的地）

本节以 `Screen.kt:10-90` 的封闭路由表为分母，以 `AppNavGraph.kt:58-219` 的实际分发为入口，逐项映射到下方流程证据。**同一仓库上的多个页面合并成一条业务流程，不代表页面行为已被独立测试**；合并项的覆盖缺口在下表“覆盖边界”中明示。此核对不把未接线的平台能力（实时设备发现）伪装成已上线页面。

| Screen 路由（可合并） | 实际页面 / AppNavGraph 分发行 | 对应下方流程 | 覆盖边界 |
|---|---|---|---|
| `RoleSelect` | `RoleSelectDestination`（60-70、222-289） | 启动、角色选择与默认数据；家长密码门禁 | 角色选择本身无行为测试，密码门禁无行为测试 |
| `ParentHome` | `ParentHomeScreen`（73-80） | 家长首页与仪表盘 | 聚合/边界行为未测 |
| `ParentTaskManage` / `ParentTaskEdit` | `ParentTaskManageScreen` / `ParentTaskEditScreen`（81-89） | 任务增删改与模板；任务频率与额外奖励 | 仅默认任务播种有测试，CRUD 无行为测试 |
| `ParentTaskTemplate` | `ParentTaskTemplateScreen`（118-119） | 任务增删改与模板 | `applyTemplate` 未包裹事务；模板页无行为测试 |
| `ParentPoints` / `ChildPoints` | `ParentPointsScreen` / `ChildPointsScreen`（90-91、145-146） | 积分账本、余额消费与明细展示 | 账本不变量有测试；两页分组/待审展示无行为测试 |
| `ParentRewardManage` / `ParentRewardEdit` | `ParentRewardManageScreen` / `ParentRewardEditScreen`（92-100） | 奖励预设加载、创建编辑与管理 | 预设/文案有测试；表单保存与幂等行为未测 |
| `ParentDashboard` | `ParentDashboardScreen`（101-102） | 家长首页与仪表盘；成长报告与效率报告 | 聚合口径未测 |
| `ParentWeeklyReport` / `ChildWeeklyView` | `WeeklyTrackerScreen(isParentView = true/false)`（103-104、149-150） | 周追踪与每日积分表 | 同一页面两种角色参数均无业务行为测试 |
| `ParentGrowthReport` | `GrowthReportScreen`（122-123） | 成长报告与效率报告 | 报表聚合未测 |
| `ParentProofReview` / `ChildProofCapture` / `PhotoViewer` | 证明审核、拍照、照片查看（105-109、182-192） | 证明拍照、预览与审核 | 审核/相机/文件均无行为测试 |
| `ParentMistakeReview` / `ChildMistakeList` / `ChildMistakeAdd` / `ChildMistakeDetail` | 错题列表、新增、详情、家长分析（110-111、154-166） | 错题增删、复习、举一反三与家长分析 | Repository/helper 部分测试，页面与分析聚合未测 |
| `ParentSettings` | `ParentSettingsScreen`（112-113） | 设置、语言与 Analytics；家庭同步；数据库备份恢复 | 仅语言/Analytics 子集有测试 |
| `ParentApproval` | `ParentApprovalScreen`（114-115） | 单条审批通过/拒绝；批量审批与撤回 | 仓库不变量/事务 Fake 有测试，页面未测 |
| `ParentAssignTask` | `ParentAssignTaskScreen`（116-117） | 每日任务布置 | 无行为测试 |
| `ParentWishGoalManage` | `ParentWishGoalManageScreen`（120-121） | 心愿目标管理；心愿存入、完成与删除退款 | 心愿存取/退款 Repository 有测试，管理页新建/编辑/示例/确认弹窗未测 |
| `ParentScheduleManage` | 日程管理（124-125） | 每日计划与日程时间线；日程管理、提醒与通知点击 | 仅排程文案有测试，平台排程需真机 |
| `ParentTutoringAdvice` | `ParentTutoringAdviceScreen`（126-127） | 家长辅导建议与目标推荐 | 仅文案有测试 |
| `ChildHome` | `ChildHomeScreen`（130-137） | 孩子首页与系统奖励入口 | 仅三处 CAS/guard 场景有测试 |
| `ChildTaskList` | `ChildTaskListScreen`（138-144） | 孩子打卡与幂等防重；打卡撤销与闭环 | 仓库路径部分静态确认，页面未测 |
| `ChildRewardStore` | `ChildRewardStoreScreen`（147-148） | 奖励商城兑换 | 仅文案有测试 |
| `ChildLuckyDraw` | `ChildLuckyDrawScreen`（151-152） | 幸运抽奖与防双领 | CAS/guard 有动态测试 |
| `ChildPomodoro` | `ChildPomodoroScreen`（169-174） | 番茄钟暂停恢复与结算；番茄钟恢复与补结算 | 纯函数/状态路径有测试，UI 陈旧快照缺口见 P2-04 |
| `ChildFocusTraining` / `ChildMemoryTraining` | 舒尔特方格 / 记忆训练（175-176） | 专注训练；记忆训练 | 均无行为测试 |
| `ChildDailyPlan` | `ChildDailyPlanScreen`（177-178） | 每日计划与日程时间线 | 无行为测试 |
| `ChildFocusStats` | `ChildFocusStatsScreen`（179-180） | 专注统计（近 7 天） | 仅文案有测试 |
| `ChildHabitChallenge` | `ChildHabitChallengeScreen`（195-196） | 习惯挑战打卡与撤销 | 仅 undo 有测试 |
| `ChildGoalTracker` | `ChildGoalTrackerScreen`（197-198） | 目标追踪 (Goal Tracker) | 无直接完成率行为测试 |
| `ChildEfficiencyReport` | `ChildEfficiencyReportScreen`（199-200） | 成长报告与效率报告 | 仅文案有测试 |
| `ChildWishGoal` | `ChildWishGoalScreen`（203-204） | 心愿存入、完成与删除退款 | 存入/删除 Repository 有测试，UI 未测 |
| `ChildScheduleTimeline` | `ChildScheduleTimelineScreen`（205-206） | 每日计划与日程时间线 | 无行为测试 |
| `ChildPet` | `ChildPetScreen`（209-210） | 宠物照料、成长与场景购买 | 仅文案有测试 |
| `ChildDailyTarget` | `ChildDailyTargetScreen`（213-214） | 每日目标与重置 | 无直接行为/CAS 测试 |
| `ChildAchievements` | `ChildAchievementsScreen`（217-218） | 成就中心与进度呈现 | 无行为测试 |

> **符号说明**：
> - **覆盖判定**：✅ 通过（有针对性单测且通过）｜ ⚠️ 部分（核心路径有测但边界/并发缺失）｜ ❌ 未覆盖（无对应单测）
> - **验证状态**：动态验证（有真实运行的针对性单测）｜ 静态确认（通过源码控制流与数据流确认）｜ 未验证（需外部环境或真机运行时）

| 业务流程 | 入口文件:行号 | 关键 DAO / 仓库路径 | 对应实际测试类与方法名（XML真实记录） | 覆盖判定 | 验证状态 | 风险与最小修复建议 |
|---|---|---|---|---|---|---|
| **启动、角色选择与默认数据** | `App.kt:79-97`<br>`AppGraph.kt:157-206`<br>`TaskRepository.kt:158-170` | `AppGraph.bootstrap()` → `TaskRepository.ensureDefaultTasks()` / `RewardRepository.ensureDefaultRewards()` | `TaskRepositorySeedIdempotencyTest.ensureDefaultTasks_withEmptyTable_seedsDefaults`；`ensureDefaultTasks_withExistingTasks_doesNotInsert`；`initDefaultTasks_calledTwice_doesNotCreateDuplicateTasks` | ⚠️ 部分 | 动态验证（仅任务播种） | 任务空表播种和重复调用有测试；奖励播种及两个 onboarding 分支无等价幂等测试，不宣称启动幂等完备 |
| **路由分发与内存导航** | `ui/navigation/Screen.kt:10-130`<br>`ui/navigation/AppNavGraph.kt:58-219`<br>`AppNavigator.kt:14-39` | `AppNavigator.push/popBackStack` → 各 Screen | 无导航行为测试 | ❌ 未覆盖 | 静态确认 | 路由参数会百分号编码，排除 `/` 截断猜测；Android 系统 Back 未接入内存栈 |
| **家长密码门禁** | `ui/navigation/AppNavGraph.kt:222-289` | `UserPreferences.parentPassword` | 无 | ❌ 未覆盖 | 静态确认 | 门禁存在但无行为测试；密码明文存 DataStore，见 P2-22 |
| **家长首页与仪表盘** | `ParentHomeScreen.kt`<br>`ParentDashboardScreen.kt:91-105,151-185` | `PointRepository` / `TaskRepository` / `AppGraph` | `ParentPointsCopyTest.parentPointsCopyDiffers` 等仅文案 | ⚠️ 部分 | 静态确认 + 文案动态验证 | 有文案测试，无仪表盘聚合/边界行为测试；报表口径风险见 P2-06/P2-11 |
| **孩子首页与系统奖励入口** | `ChildHomeScreen.kt` | `BonusClaimGuard` / `UserPreferences.tryClaim*` | `BonusClaimGuardTest.doubleClickWhileFirstInFlight_secondClaimDoesNotDeposit`；`inFlightDisablesAllThreeEntries`；CAS 测试 3+2 条 | ✅ 通过（限已有 CAS/guard 场景） | 动态验证 | D-09/D-13 已覆盖优秀奖励、连胜、周挑战、抽奖、复习里程碑；不等于全首页行为已测 |
| **任务增删改与模板** | `ParentTaskManageScreen.kt:27`<br>`ParentTaskEditScreen.kt:25`<br>`ParentTaskTemplateScreen.kt:25-34,101-107`<br>`TaskRepository.kt:144-170` | `TaskDao.softDelete` / `TaskDao.insert` / `TaskDao.getActiveTaskCount` | `TaskRepositorySeedIdempotencyTest.ensureDefaultTasks_withExistingTasks_doesNotInsert`（仅播种） | ⚠️ 部分 | 静态确认 + 播种动态验证 | `applyTemplate` 先软删所有活跃任务再插入模板任务，未包裹事务，进程被杀可导致任务全空；CRUD/模板无直接行为测试 |
| **任务频率与额外奖励** | `ParentTaskEditScreen.kt:171-188, 248-252` | `TaskEntity.bonusPoints` / `TaskFrequency` | `无` | ❌ 未覆盖 | 静态确认 | 任务打卡仅发 `effectivePoints()`，`bonusPoints` 永久无法发放；`frequency` 被打卡逻辑忽略 |
| **每日任务布置** | `ParentAssignTaskScreen.kt:24`<br>`AssignmentRepository.kt:42-55` | `DailyAssignmentDao.markCompleted` → `onPoints` | `无` | ❌ 未覆盖 | 静态确认 | `completeAssignment` 先落库完成再回调申请积分，积分失败后重试早退 |
| **孩子打卡与幂等防重** | `ChildTaskListScreen.kt:40`<br>`PointRepository.kt:117-138, 374-425` | `PointRepository.ensureApproval` → `PointApprovalDao` | `PointRepositoryInvariantTest.checkIn_duplicateCall_reusesExistingPendingApproval` | ⚠️ 部分 | 静态确认 | `ensureApproval` 读判写非事务，`PointApprovalEntity` 的 `bizKey` 无唯一约束，高并发打卡存在双发风险 |
| **打卡撤销与闭环** | `PointRepository.kt:176-205` | `PointDao.deleteCompletionForTaskOnDate` → `PointDao.insertPointRecord` | `PointRepositoryInvariantTest.undoCheckIn_whenApproved_appendsReversalRecordAndClawsBackPoints` | ⚠️ 部分 | 静态确认 | `undoCheckIn` 未包裹事务，删完成记录与写冲抵流水非原子；`updateReview` 无 PENDING 状态判断 |
| **单条审批通过/拒绝** | `ParentApprovalScreen.kt:56`<br>`PointRepository.kt:216-248` | `pointDao.insertPointRecord` → `approvalDao.updateReview` | `PointRepositoryInvariantTest.approveAndAward_calledTwice_onlyCreditsOnce` | ⚠️ 部分 | 静态确认 | 单条 `approveAndAward` / `rejectApproval` 未包裹事务，`updateReview` SQL 无 `WHERE status = 'PENDING'` 守卫 |
| **批量审批与撤回** | `PointRepository.kt:251-340` | `transactionRunner.runInTransaction` | `PointRepositoryTransactionAtomicityTest.approveAllAndAward_throwMidBatch_rollsBackEntireBatch` 等 8 条 | ⚠️ 部分 | 动态验证（Fake 事务） | 仅证明仓库入口包装与快照/回滚 Fake 行为；真实 SQLite 并发、崩溃恢复未验证；iOS DirectTransactionRunner 为 F-7 已披露降级 |
| **超期未审自动清理** | `PointRepository.kt:346-369` | `approvalDao.getStalePendingList` → 逐条复读 | `PointRepositoryStalePendingCleanupTest.expireStalePendingApprovals_cancelsAllStaleAndPendingCountDropsToZero` | ✅ 通过 | 动态验证 | 复读最新状态与 bizKey 锚定行校验防误撤销通过 |
| **积分账本、余额消费与明细展示（家长/孩子）** | `ParentPointsScreen.kt:44-53`<br>`ChildPointsScreen.kt:44-62`<br>`PointRepository.kt:482-494` | `pointDao.insertPointRecord`（负分流水）/ `getAllRecords` | `PointRepositoryInvariantTest.totalPoints_equalsSumOfAllPointRecords`；`PointRecordBonusNoteTest`；`ParentPointsCopyTest.parentPointsCopyDiffers` | ✅ 通过（限账本不变量/note） | 动态验证 + 页面静态确认 | 余额不变量有测试；两页的待审、时间分组与 note 展示无行为测试，全表 Flow 扩展性见 P2-08；`spendPoints` 默认不校验余额直接记负分流水，显式传 `requireSufficientBalance=true` 时余额不足返回 false |
| **奖励商城兑换** | `ChildRewardStoreScreen.kt:216-225` | `RewardRepository.exchangeReward` → `PointRepository.spendPoints` | `ChildRewardStoreCopyTest.childRewardStoreCopyDiffers` 等仅文案；无兑换行为测试 | ⚠️ 部分 | 静态确认 + 文案动态验证 | 兑换记录插入与扣分分裂写，且未开启余额强校验，异常时留下无扣款的兑换记录 |
| **奖励预设加载、创建编辑与管理** | `ParentHomeScreen.kt:215-219`<br>`ParentRewardManageScreen.kt:26,57-79`<br>`ParentRewardEditScreen.kt:22-50,62-75`<br>`RewardRepository.kt:14-19,44-81` | `RewardDao.insertReward` / `updateReward`（id=0 autogenerate） | `PresetRegistryTest.knownCanonicalResolvesPerLanguage`；`ParentPointsCopyTest.parentRewardEditCopyDiffers` 等仅注册表/文案 | ⚠️ 部分 | 静态确认 + 文案动态验证 | 两处 UI 直接调用 `initDefaultRewards()`；无唯一 presetKey、无 in-flight guard，可重复插入整套预设，见 P2-24；编辑表单保存无行为测试 |
| **幸运抽奖与防双领** | `ChildLuckyDrawScreen.kt:120-145` | `UserPreferences.tryClaimDailyLuckyDraw` | `UserPreferencesD13CasTest.tryClaimDailyLuckyDraw_concurrentSameDay_exactlyOneWins_nextDayOpensAgain`；`BonusClaimGuardD13Test.luckyDraw_doubleClickWhileFirstInFlight_secondDrawDoesNotDeposit` | ✅ 通过（CAS/guard 场景） | 动态验证 | DataStore 原子 CAS 与 in-flight guard 有直接测试 |
| **每日目标与重置** | `ChildDailyTargetScreen.kt:152-161, 213-255` | `UserPreferences.setDailyTargets` / `PointRepository.checkIn` | 无直接行为/CAS 测试 | ❌ 未覆盖 | 静态确认 | 重新配置每日目标未清理旧 TARGET 审批；Checkbox 先写 DataStore 且 `doneTargets.size+1` 存在并发快照偏差 |
| **番茄钟暂停恢复与结算** | `PomodoroTimer.kt:405-435`<br>`PomodoroFocusSettlement.kt:53-83` | `PomodoroSessionState` 纯函数 / `UserPreferences` | `PomodoroSessionStateTest.resumeShiftsDeadlineByAccumulatedPausedMillis` | ⚠️ 部分 | 静态确认 | 时间戳锚定纯函数正确；但已结算后 UI 端的陈旧 pause/resume 操作可能把旧会话写回 DataStore |
| **番茄钟恢复与补结算** | `PomodoroFocusSettlement.kt:30-110` | `PointRepository.checkIn` / DataStore settlement CAS | `PomodoroFocusSettlementTest.doubleTrigger_concurrentRecover_exactlyOneWins`；`crashAfterRecordBeforeCheckIn_resumeReusesSameSessionIdAndBizKey` | ⚠️ 部分 | 动态验证（fake/状态路径） | 覆盖恢复与 CAS 防重路径；未真实杀进程或跨进程验证，且 P2-04 的 settled 后旧快照写回仍存在 |
| **专注训练 (舒尔特方格)** | `ChildFocusTrainingScreen.kt:38-40, 70-79` | `UserPreferences.setFocusTrainingBest` | 无直接行为测试 | ❌ 未覆盖 | 静态确认 | 首次完成训练时 `bestMs == 0L` 导致 `isNewRecord` 为 false，首创纪录不报破纪录；无并发写保护 |
| **记忆训练** | `ChildMemoryTrainingScreen.kt` | 纯内存 `remember` 状态管理 | 无直接行为测试 | ❌ 未覆盖 | 静态确认 | 纯内存状态，切屏/进程死亡后重置；这是设计/覆盖边界，不单独升级为高危缺陷 |
| **每日计划与日程时间线** | `ChildDailyPlanScreen.kt:33-34`<br>`ChildScheduleTimelineScreen.kt:47-54` | `FocusRepository.getPlansByDate` / 日程 Flow | 无直接行为测试 | ❌ 未覆盖 | 静态确认 | 日期/时间初次快照不随午夜或系统时间变化刷新，见 P2-20 |
| **专注统计（近 7 天）** | `ChildFocusStatsScreen.kt:23-43` | `FocusRepository.getDailyFocusMinutesSince` / `getTotalFocusMinutes*` | `StringsCopyTest.focusCopyDiffers` 等仅文案 | ❌ 未覆盖（业务行为） | 静态确认 + 文案动态验证 | 7 天起点只取一次，页面跨日不滚动窗口；全量 Flow 无分页；需补日期窗口/跨日行为测试 |
| **错题增删、复习、举一反三与家长分析** | `MistakeRepository.kt:58-100`<br>`ChildMistakeListScreen.kt` / `ChildMistakeDetailScreen.kt` / `ParentMistakeReviewScreen.kt` | `MistakeDao.updateReviewProgress` / `insertReview` / `PointRepository.checkIn` | `MistakeRepositoryTest.saveVariation_updates_note_and_hasVariation_flag`；`SpacedRepetitionTest.nextReviewAt_uses_createdAt_when_never_reviewed` 等 | ⚠️ 部分 | 动态验证（Repository/helper） | 进度、历史表、审批表三步分裂写非事务；UI/分析聚合无直接行为测试；物理删除错题的照片/流水清理需另行核验 |
| **证明拍照、预览与审核** | `ChildProofCaptureScreen.kt` / `PhotoViewerScreen.kt`<br>`ParentProofReviewScreen.kt:101-113` | `HabitDao.reviewProof` / 平台相机与照片文件 | 无证明行为测试；`WishCopyTest.parentProofReviewCopyDiffers` 仅文案 | ❌ 未覆盖（业务行为） | 静态确认 + 文案动态验证 | `approveProof` 不发积分；`reviewProof` 缺少 `WHERE status = 'PENDING'` 守卫，存在终态覆写竞态 |
| **习惯挑战打卡与撤销** | `HabitRepository.kt:39-54, 57-82` | `HabitDao.updateChallenge` / `completeChallenge` | `HabitRepositoryUndoTest.undo_rollbacksCheckInMadeYesterday` 等 4 条 | ⚠️ 部分 | 动态验证（仅 undo） | 24h 撤销窗口和状态回滚有测试；挑战正常打卡、陈旧实体覆盖、并发无直接覆盖，见 P2-14 |
| **成就中心与进度呈现** | `AchievementManager.kt:118-140`<br>`ChildAchievementsScreen.kt:38-41` | `AchievementManager.getProgress` | `无` | ❌ 未覆盖 | 静态确认 | `perfect_day` 与 `habit_complete` 进度硬编码为 `(0 to 1)`；页面采用 `LaunchedEffect(Unit)` 不会自动刷新 |
| **目标追踪 (Goal Tracker)** | `ChildGoalTrackerScreen.kt:28-38` | `TaskRepository` / `PointRepository` | 无直接完成率行为测试；`LearningScoreCalculatorTest` 测的是另一学习评分 helper | ❌ 未覆盖 | 静态确认 | 任务调分或软删后，完成数可能大于活跃任务数，完成率未设上限可超 100% |
| **心愿目标管理与存入、完成、删除退款** | `ParentWishGoalManageScreen.kt:38-58,93-103,123-169`<br>`WishGoalRepository.kt:56-87`<br>`ChildWishGoalScreen.kt:315-341` | `PointRepository.spendPoints` / `refundSpentPoints` / `WishGoalDao` | `WishGoalRepositoryLedgerTest.depositPoints_reachingTarget_marksGoalCompleted`；`depositPoints_insufficientBalance_writesNothingAndKeepsSavedPoints`；3 条 deleteGoal 测试 | ⚠️ 部分 | 动态验证（顺序 Fake） | 管理页新建/编辑/示例加载/删除确认无行为测试；存入与删除退款无事务；前端和仓库均未按 remaining 限額，允许超额存入，见 P1-03 |
| **周追踪与每日积分表（家长/孩子双入口）** | `AppNavGraph.kt:103-104,149-150`（`isParentView=true/false`）<br>`WeeklyTrackerScreen.kt:31-67,249-307` | `TaskRepository.getAllActiveTasks` / `PointRepository.getCompletedTaskIdsForDate` / `getAllRecords` | `WishCopyTest.weeklyTrackerCopyDiffers` 仅文案 | ❌ 未覆盖（业务行为） | 静态确认 + 文案动态验证 | 当前任务分值重算历史、pending/rejected 计入、bonus/penalty 遗漏、跨周日期快照错位，见 P2-28 |
| **家长辅导建议与目标推荐** | `ParentTutoringAdviceScreen.kt:23-40`<br>`GoalRecommendation.kt:24-80` | `MistakeRepository.getAllMistakes` / `FocusRepository.getAllSessions` / `getCurrentStreak` | `W8CopyTest.tutoringCopyPinsBothLanguages` / `goalRecommendationCopyPinsBothLanguages` 仅文案 | ❌ 未覆盖（业务行为） | 静态确认 + 文案动态验证 | streak 仅 LaunchedEffect(Unit) 取一次；全量错题/专注列表；建议引擎缺专门业务规则测试 |
| **日程管理、提醒与通知点击** | `ParentScheduleManageScreen.kt:299-305`<br>`AndroidNotificationScheduler.kt:66-84,123-127`<br>`ScheduleAlarmHelper.kt:47-60,74-89` | `AlarmManager` / `UNUserNotificationCenter` / `DailyScheduleEntity` | `ChildScheduleCopyTest.scheduleAlarmBodyPinsBothLanguages` 仅文案 | ⚠️ 部分 | 静态确认 + 真机未验证 | 排程忽略 `repeatDays` 写死 daily（影响 legacy/导入自定义星期）；通知点击缺深链路由，见 P2-10/P2-18 |
| **周报推送与周期调度** | `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/WeeklyReportReceiver.kt:48-62`<br>`composeApp/src/androidMain/kotlin/com/hstudy/app/platform/WeeklyReportReceiver.kt:68-71`<br>`AndroidNotificationScheduler.kt:93` | 广播接收器重排下一周闹钟 | `无` | ❌ 未覆盖 | 静态确认 | Android 端 `repeatDaily=false` 导致首周触发后不再排下周；iOS 采用 `repeats=true` 存在平台差异 |
| **开机广播自启动恢复** | `BootCompletedReceiver.kt:14-28` | `AndroidReminderBootstrap.rescheduleAll` | `无` | ❌ 未覆盖 | 未验证 | 设备重启后闹钟恢复需 Android 真机实测 |
| **宠物照料、成长与场景购买** | `ChildPetScreen.kt:213-244,373-382`<br>`PointDao.kt:24-26` | `PointRepository.spendPoints` / `PointDao.getLifetimeEarnedPoints` / DataStore 场景状态 | `PetCopyTest.petStringsCopyDiffers` 仅文案 | ⚠️ 部分 | 静态确认 + 文案动态验证 | 陈旧余额、非事务扣款/授权，见 P1-08；心愿退款正分被计入终身收入，见 P2-06 |
| **成长报告与效率报告** | `ui/report/GrowthReportScreen.kt:36-100`<br>`ui/habit/ChildEfficiencyReportScreen.kt:31-119,436-443` | `PointDao` / `TaskCompletionDao` / 当前活跃任务列表 | `WishCopyTest.growthReportCopyDiffers`；`W8CopyTest.efficiencyReportCopyDiffers` 仅文案 | ⚠️ 部分 | 静态确认 + 文案动态验证 | 当前分值逆乘历史、当前活跃任务数作历史分母，见 P2-11；无报表聚合行为测试 |
| **设置、语言与 Analytics** | `ParentSettingsScreen.kt`<br>`AnalyticsReporter.kt` | `UserPreferences` / `Platform.analytics` | `AppLanguageResolveTest.systemChineseSelectsZh` 等 6 条；`LanguagePreferenceTest.languagePersistsAcrossReads`；`AnalyticsReporterTest.enabledReporterForwardsEvents`；`AnalyticsPreferenceTest.defaultsToEnabledAndPersistsToggle` | ✅ 通过（限语言/Analytics 已有测试） | 动态验证 + 静态闸门 | 语言解析/持久化、Analytics 偏好和 reporter 有直接测试；不等于设置页全部业务已测 |
| **家庭控制配置导入** | `FamilySyncHelper.kt:134-176` | 解码 JSON → 软删除现有 → 插入新条目 | 无导入行为测试；`W9CopyTest.syncAdapterPinsBothLanguages` 仅文案 | ❌ 未覆盖 | 静态确认 | `{"type":"parent_control"}` 可清空本地任务/奖励；无确认、无事务，见 P1-06 |
| **家庭进度导出/导入与分享** | `FamilySyncHelper.kt:180-259`<br>`ParentSettingsScreen.kt:251-270` | 多次独立 Room/DataStore 查询 → JSON → `Platform.fileSharer` | 无行为测试；仅有同步文案 pin | ❌ 未覆盖 | 静态确认 | 导出非一致快照、进度 DTO 未校验数值关系、分享异常被吞但 UI 仍提示成功，见 P2-25～P2-27 |
| **实时设备发现（未接线能力）** | `Platform.kt:75-100`<br>`AndroidDeviceDiscovery.kt:18-37`<br>`IosDeviceDiscovery.kt:21-28` | `DeviceDiscovery.startDiscovery/stopDiscovery` | 无 | ❌ 未覆盖 | 静态确认 | 实现存在但生产 UI/仓库无调用；实际同步是手动 JSON 文件流程，不能宣称实时设备同步已实现，见 P2-29 |
| **数据库一致性备份与恢复** | `BackupHelper.kt:52-98` | `BundledSQLiteDriver` → `VACUUM INTO` / 文件覆盖 | `无` | ❌ 未覆盖 | 静态确认 | 备份先删旧备份，恢复先物理删 live 库，无回滚保护；导入备份仅验 Base64 未验 SQLite 完整性 |
| **数据库升级迁移校验** | `Migrations.kt` (v1 -> v13) | `MigrationSafetyHelper` + Room 迁移链 | `MigrationVerifyTest.populatedTableWithV12ColumnsReportsNothingMissing` | ⚠️ 部分 | 动态验证 | 数据库实体为 16 张表；单测仅为 `FakeConnection` 模拟 PRAGMA 检查，缺乏真实物理数据库迁移校验 |

---

## 三、新发现缺陷与风险（P0 / P1 / P2）

> **特别声明**：本节所列全部问题均为基于源码控制流、数据流分析及数据库约束审查得出的**静态分析结论**，并未在生产或动态测试环境中进行入侵式复现。

### 3.1 P0 级缺陷
**未发现新缺陷**。核心账本不变量、防重入互斥锁以及 D-01～D-13 涉及的关键链路未发现阻断级漏洞。

---

### 3.2 P1 级缺陷（严重影响数据一致性、并发安全或核心功能）

#### 【P1-01】PointRepository 单条审批/撤销/打卡读判写非事务，DAO updateReview 无 PENDING 条件且 bizKey 无唯一索引
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt:117-138, 176-205, 216-248, 374-425`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointApprovalDao.kt:54-60`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/entity/PointApprovalEntity.kt:18-35`
- **复现步骤（推演）**：
  1. 家长快速连续点击同一条待审申请（ID=10）的“通过”按钮；
  2. 协程 A 和协程 B 同时进入 `approveAndAward(10)`；
  3. 协程 A、B 分别调用 `getById(10)`，均读取到 `status == PENDING` 并通过检查；
  4. 协程 A 插入流水记录 `recordA`，并调用 `updateReview(10, APPROVED, recordA)`；
  5. 协程 B 插入流水记录 `recordB`，并调用 `updateReview(10, APPROVED, recordB)`；
  6. 结果：账本生成了两笔相同的加分流水（重复发分），而审批表单被 `recordB` 覆盖。
- **根因分析**：
  1. Task C 仅对批量审批、超期清理、审批撤回增加了事务保护，但**单条审批**（`approveAndAward`）、**单条拒绝**（`rejectApproval`）、**打卡与建单**（`checkIn` / `ensureApproval`）、**孩子撤销打卡**（`undoCheckIn`）均运行在裸协程中，无事务包裹；
  2. `PointApprovalDao.updateReview` 的 SQL 缺少 `AND status = 'PENDING'` 的 CAS/乐观锁条件；
  3. `PointApprovalEntity` 的 `bizKey` 字段在 SQLite 表中没有唯一索引约束（UNIQUE INDEX），导致 `ensureApproval` 在读判写间隔期可被并发插入相同 `bizKey` 的重复单据。
- **潜在影响**：并发操作或网络/UI重复点击时，积分重复发放或账本流水与审批单状态出现终态矛盾。
- **建议修复**：
  1. 将 `approveAndAward`、`undoCheckIn` 等读判写链路包裹进 `transactionRunner.runInTransaction`，并在事务内复读；
  2. `updateReview` 增加乐观锁约束：`WHERE id = :id AND status = 'PENDING'`，并依据受影响行数判定是否发放成功；
  3. **不得直接增加无条件 `UNIQUE(bizKey)` 索引**：既有 undo→recheck 场景已确认会产生历史重复 `bizKey` 行。升级前必须审计/归一化历史数据，再按“当前有效审批”建模唯一约束（例如规范化活跃表或经过验证的条件部分唯一索引），同时保留历史审计行。
- **建议测试**：除同一审批单并发只发 1 分外，必须构造历史重复 `bizKey` 数据执行迁移，断言历史不被误删、新有效单仍受约束。

#### 【P1-02】TaskRepository.applyTemplate 先软删后插无事务，中断可致任务清空
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/TaskRepository.kt:141-150`
- **复现步骤（推演）**：
  1. 家长选择应用预设任务模板；
  2. 代码执行 `val existingTasks = taskDao.getAllActiveTasksSnapshot(); existingTasks.forEach { taskDao.softDelete(it.id) }`；
  3. 在执行第 149 行 `template.tasks.forEach { task -> taskDao.insert(task) }` 之前，App 进程被杀或系统发生 OOM 崩溃；
  4. 重新打开 App，原有的任务已被软删除置为非活跃，而模板任务尚未插入。
- **根因分析**：清空旧任务和插入新模板任务之间缺乏数据库事务包裹，属于多步分裂写入。
- **潜在影响**：用户现有任务完全丢失，任务列表呈现空白。
- **建议修复**：通过数据库事务执行 `softDelete` 与 `insert`，失败时全量回滚。
- **建议测试**：在插入环节注入异常，断言原有活跃任务数量保持不变。

#### 【P1-03】WishGoalRepository 删除退款/存入无事务，且前端允许存入超出目标上限
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/WishGoalRepository.kt:56-86`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/wish/ChildWishGoalScreen.kt:315-341`
- **复现步骤（推演）**：
  1. 孩子创建一个心愿：目标 50 分，已存 0 分（`remaining = 50`），孩子当前总积分余额为 100 分；
  2. 点击心愿存钱罐弹出存款弹窗，手动输入存入金额为 `100`；
  3. 前端第 317 行：`val valid = amount in 1..currentPoints`，计算结果为 `true`（未判断 `amount <= remaining`）；
  4. 点击确定，`depositPoints(goalId, 100)` 扣除 100 积分成功，心愿 `savedPoints` 累加至 100，达成目标；
  5. 结果：孩子超额存入 50 积分，被锁死在心愿存钱罐中，无法在商店消费；
  6. 同时，在 `depositPoints` 底层（`spendPoints` 成功之后、`addSavedPoints` 写入之前）如果进程被杀，余额已被扣除但存入进度未累加。
- **根因分析**：
  1. `ChildWishGoalScreen.kt:317` 的表单校验仅限制了 `currentPoints`，漏掉了 `remaining` 上限校验；
  2. `WishGoalRepository.depositPoints` 与 `deleteGoal` 内部的积分增减与心愿进度增减属于分裂读写，无原子事务。
- **潜在影响**：积分超存锁定；异常中断时积分被扣但存钱罐进度丢失。
- **建议修复**：
  1. 前端弹窗增加限制：`val valid = amount in 1..minOf(currentPoints, remaining)`；
  2. `WishGoalRepository` 内校验 `amount <= remaining`，并将扣减积分与更新心愿放进事务中。
- **建议测试**：编写单测验证输入大于 `remaining` 时 `depositPoints` 返回 `false` 或被前端校验拦截。

#### 【P1-04】Android 每周周报 repeatDaily=false 导致首周后不再排期，iOS 存在平台差异
- **文件与行号**：
  - `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/WeeklyReportReceiver.kt:68-71`
  - `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidNotificationScheduler.kt:87-106`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/WeeklyReportReceiver.kt:59`
  - `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosNotificationScheduler.kt:47-53`
- **复现步骤（推演）**：
  1. 用户在 Android 设置中开启“每周学习周报”；
  2. 注册闹钟时，`ScheduledReminder` 的 `repeatDaily` 被明确赋值为 `false`（每周一次）；
  3. 首个周日 20:00 闹钟触发，`WeeklyReportReceiver.onReceive` 弹出周报通知；
  4. 在 `finally` 块中调用 `AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)`；
  5. 进入该方法第 93 行：`if (!intent.getBooleanExtra(EXTRA_REPEAT_DAILY, true)) return`；
  6. 由于注册时存入的 `EXTRA_REPEAT_DAILY` 为 `false`，此处条件成立，直接 `return` 退出，未执行后续 `scheduleAlarm`；
  7. 结果：Android 端首周推送一次后，后续周日闹钟彻底丢失。而在 iOS 端，`IosNotificationScheduler.kt:50` 对周报显式设置了 `repeats = true`，系统日历通知能持续每周触发，造成平台行为严重割裂。
- **根因分析**：Android 调度器假定周期重复闹钟必须满足 `repeatDaily == true`，未对周频次的 `WEEKLY_REPORT` 作周期豁免。
- **潜在影响**：Android 家长在开启周报后，第二周起永久失去推送。
- **建议修复**：Android 调度器增加周报类型放行逻辑：
  `val isWeekly = intent.getStringExtra(EXTRA_KIND) == ReminderKind.WEEKLY_REPORT.name`
  `if (!isWeekly && !intent.getBooleanExtra(EXTRA_REPEAT_DAILY, true)) return`
- **建议测试**：对 `rescheduleNextFromReceiver` 传入周报 Intent，断言其未被提前 return 拦截，并重新注册了闹钟。

#### 【P1-05】BackupHelper 破坏性操作缺少完整性校验与失败回滚
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/backup/BackupHelper.kt:52-98, 120-140`
- **复现步骤（推演）**：
  1. 备份场景：设备存储不足，调用 `backup()`，第 62 行 `if (fs.exists(backupFile)) fs.delete(backupFile)` 先物理删除了旧备份；随后执行 `VACUUM INTO` 因磁盘满抛出异常。结果：旧备份被删，新备份未成，用户备份全失。
  2. 恢复场景：用户调用 `restore()`，第 85-91 行主动关闭数据库并物理删除了 live 的 `hstudy_db`、`wal`、`shm` 文件；第 93 行 `fs.copy(backupFile, dbFile)` 因磁盘或权限问题抛出 IO 异常。结果：live 数据库已被删除，备份复制失败，数据全部丢失。
  3. 导入场景：`importBackup` 仅校验了信封格式 `type == "hstudy_db_backup"` 与 Base64 解码，未对解码出的二进制文件做 SQLite 头部校验（`SQLite format 3`）、schema 或 PRAGMA 完整性检查，写入损坏的二进制文件后如果执行恢复，将直接摧毁应用数据。
- **根因分析**：删除先于安全检查执行，无临时文件过渡，无异常回滚保护，导入端缺少 SQLite 文件特征检验。
- **潜在影响**：在极端环境（磁盘空间紧张/IO异常/导入损坏文件）下可能使 live 数据库不可用；若独立备份文件也丢失或不可用，则无法恢复。不能把“应用内数据丢失”绝对表述为任何情况下都永久丢失。
- **建议修复**：
  1. 备份时先写入临时文件，成功后再原子重命名；
  2. 恢复时先将 live 数据库重命名为 `.bak`，成功后再删除备份副本；
  3. 导入时校验 SQLite magic header 与 `PRAGMA integrity_check`。
- **建议测试**：Mock 文件系统注入复制失败，断言原 live 数据库文件完好保留。

#### 【P1-06】FamilySyncHelper 控制包导入全量覆盖无事务，空数据可致任务清空
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/sync/FamilySyncHelper.kt:134-176`
- **复现步骤（推演）**：
  1. 孩子设备已有 10 个有效任务；
  2. 孩子导入控制包，传入 JSON 内容为 `{"type":"parent_control"}`（tasks/rewards 省略，使用 DTO 默认的 `emptyList()`）；
  3. JSON 顺利通过解码，第 139 行 `dto.type == "parent_control"` 校验通过；
  4. 第 148 行执行 `existingTasks.forEach { app.taskRepository.deleteTask(it.id) }`，无条件将本地现有活跃任务全部软删除；
  5. 随后由于 `dto.tasks` 为空列表，未插入任何新任务；全过程无二次确认弹窗，亦无事务回滚。
- **根因分析**：空配置输入合法，覆盖导入缺少数据有效性防御门禁（如空内容校验）、缺少用户感知预览确认，且非原子执行。
- **潜在影响**：误操作直接导致孩子端活跃任务和奖励配置全部清空。
- **建议修复**：
  1. 导入前增加数据合理性校验：当 `dto.tasks.isEmpty()` 时禁止清空现有任务或弹出强制确认；
  2. 将任务与奖励的替换逻辑包裹在事务中。
- **建议测试**：传入 `{"type":"parent_control"}` 的 JSON，断言返回失败且现有任务未被删除。

#### 【P1-07】TaskFrequency 定义被实际打卡机制完全忽略
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentTaskEditScreen.kt:171-188`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/TaskDao.kt:11-12`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt:157-180`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt:117-137`
- **复现步骤（推演）**：
  1. 家长在任务编辑页将某项任务（如“整理大扫除”）的频次设置为 `TaskFrequency.WEEKLY`（每周一次）或 `ONCE`（单次）；
  2. 孩子在周一完成该任务并打卡，完成记录写入数据库；
  3. 到了周二，孩子打开任务列表，`ChildTaskListScreen` 仅检查 `completedIdsForDate(today)`（今天是否完成）；
  4. 结果：由于今天尚未打卡，该周频次或单次任务再次以可打卡状态展现，孩子仍可继续打卡并获得积分。
- **根因分析**：UI 提供了 `DAILY / WEEKLY / MONTHLY / ONCE` 选项，实体中保存了 `frequency` 字段，但打卡逻辑（`ChildTaskListScreen` 与 `PointRepository.checkIn`）完全沿用“每日（`todayString`）”作为判定唯一依据，忽略了任务本身的频率语义。
- **潜在影响**：家长设定的周/月/单次限制形同虚设，孩子可每日刷分。
- **建议修复**：
  1. 在 `checkIn` 与列表渲染时，依据 `frequency` 校验对应周期（本周、本月、历史总计）内是否已完成；
  2. 或在暂不支持复杂频次时于编辑页弱化/提示该选项。
- **建议测试**：创建 WEEKLY 任务并在同一周不同日期执行两次打卡，断言第二次打卡被拦截。

#### 【P1-08】ChildPetScreen 照料与场景购买依赖陈旧余额快照与分裂写入
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/pet/ChildPetScreen.kt:213-244, 373-382`
- **复现步骤（推演）**：
  1. 孩子当前积分余额刚好为 10 分，进入宠物页面；
  2. 快速双击“喂养宠物”（消耗 10 分）或同时点击“喂养”和“玩耍”；
  3. 两个按钮的点击回调均通过协程读取当前 Compose 状态的 `totalPoints` 快照（均为 10 分）；
  4. 两个协程分别触发 `app.pointRepository.spendPoints(10, ...)` 和 `app.pointRepository.spendPoints(5, ...)`；
  5. 由于 `spendPoints` 默认参数 `requireSufficientBalance = false`，未进行严格余额校验，两笔扣减流水均成功插入；
  6. 结果：总积分变为 -5 分（透支负分）；
  7. 在购买场景场景下，先 `spendPoints` 扣分，再更新 `UserPreferences`；若扣分后写入 DataStore 失败，积分已扣但场景未解锁；若未校验已拥有状态，重复点击可扣多次场景款。
- **根因分析**：
  1. UI 层的判断依赖于 `collectAsState` 的异步快照，缺乏并发互斥锁（如 `BonusClaimGuard`）；
  2. 调用 `spendPoints` 时未显式传入 `requireSufficientBalance = true`；
  3. 扣款与授予权限为分裂异步调用。
- **潜在影响**：积分透支为负数、场景购买重复扣款或扣款后未解锁。
- **建议修复**：
  1. 购买和照料操作引入局部防双击互斥锁；
  2. `spendPoints` 调用显式指定 `requireSufficientBalance = true` 并根据返回值执行后续操作。
- **建议测试**：在余额为 10 分时并发触发两次喂养，断言第二次扣费失败且总积分不为负。

---

### 3.3 P2 级缺陷（状态微弱不一致、数据失真、缺少边缘防御）

#### 【P2-01】任务 bonusPoints 永久无发分路径
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/entity/TaskEntity.kt:33`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentTaskEditScreen.kt:248-252`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt:171-175, 418-420`
- **复现推演**：家长在编辑任务时配置了基础积分 5 分、额外奖励 `bonusPoints = 2` 分。孩子端任务卡片第 418 行显示了“🎁 额外+2”。但孩子点击完成打卡时，第 171 行仅执行 `val earned = task.effectivePoints()`，传入 `checkInForTask` 的只是基础分根据难度折算后的分值，`bonusPoints` 字段在全流程中从未参与计算，孩子永远拿不到额外奖励分。
- **根因**：数据模型增加了 `bonusPoints` 且 UI 做了展示，但 `TaskEntity.effectivePoints()` 及打卡入账链路未将 `bonusPoints` 纳入结算。
- **影响**：家长误以为设置了激励分，孩子实际打卡却少发积分，造成激励信任受损。
- **建议修复**：在打卡入账或难度结算中合并 `bonusPoints`，或在当前版本移除该未实装字段。
- **建议测试**：配置包含 `bonusPoints` 的任务，打卡后断言待审申请分值包含该额外奖励。

#### 【P2-02】ParentProofReviewScreen 证明审核终态竞态
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/proof/ParentProofReviewScreen.kt:101-113`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/HabitDao.kt:73-74`
- **复现推演**：家长在多端或因快速点击，对同一条待审完成证明同时触发“通过”和“拒绝”。第 105 行调用 `approveProof(proof.id, 0)`（审核证明不发积分，积分走打卡审批），底层执行 `reviewProof`。由于 SQL 语句 `UPDATE completion_proofs SET status = :status, reviewedAt = :reviewedAt, pointsAwarded = :points WHERE id = :id` 缺少 `WHERE status = 'PENDING'` 守卫，后执行的协程会无条件覆写先执行协程的终态。
- **根因**：证明审核 DAO 更新操作缺少单向状态机约束（PENDING 守卫）。
- **影响**：多操作或重入时证明终态被非预期覆盖；虽无重复发分风险，但破坏了审核审计痕迹。
- **建议修复**：在 `HabitDao.reviewProof` 的 SQL 中增加 `AND status = 'PENDING'` 条件。
- **建议测试**：并发调用 `reviewProof`，断言仅第一次状态变更生效。

#### 【P2-03】每日目标重置不清旧审批与 bonus、复选框并发判定偏差
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/target/ChildDailyTargetScreen.kt:152-161, 213-255`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt:254-275`
- **复现推演**：孩子在今日完成了一组每日目标，生成了 `TARGET:<id>:$today` 审批并领取了 `TARGET_ALL:$today` 额外奖励。随后孩子在编辑模式点击“重新设定目标”，`setDailyTargets` 仅更新了目标列表，没有撤销或清理旧的审批单和 bonus 标记。孩子在新的一组目标上可以再次打卡。此外，勾选时第 228 行 `val newDoneCount = doneTargets.size + 1` 依赖于异步状态快照，并发快速点击两个复选框可能导致全部达成的判定出现时序偏差。
- **根因**：目标重置未与积分审批联动；全部完成判定依赖前端快照计数而非原子判定。
- **影响**：利用重新设定目标可能重复发起目标积分申请；并发勾选时状态提示错乱。
- **建议修复**：重置目标时联动检查当日是否已有审批；全部达成判定下沉到仓库层统一计算。
- **建议测试**：已完成目标后重新设定目标，验证旧审批单状态与打卡防刷限制。

#### 【P2-04】番茄钟已认领后陈旧会话写回可覆盖已结算状态
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/PomodoroTimer.kt:405-435`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt:423-456`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/focus/PomodoroFocusSettlement.kt:53-83`
- **复现推演**：一个专注段到期，后台/跨进程补结算机制成功赢下认领，调用 `settleFocusSession` 记录了统计并标记 `settled = true`。但前台界面的 `PomodoroTimer` 仍持有旧会话快照（`settled = false`）。此时用户在前台点击了“暂停”或“恢复”，第 410 行 `app.userPreferences.savePomodoroSession(s.paused(...))`（未带 `expectedStartEpochMs` CAS）将旧状态写回 DataStore，覆盖了已结算标志。下次检查时可能再次触发补结算，虽被 `FOCUS:$sessionId:$checkInDate` 的 bizKey 拦截防住了重复发积分，但统计流水可能被重复记录。
- **根因**：UI 层的 pause/resume 未带版本/时间戳 CAS 守卫，会盲目覆盖已在后台结算的会话。
- **影响**：专注统计流水可能重复记录；虽无重复发分，但破坏了专注历史统计的准确性。
- **建议修复**：仅传 `expectedStartEpochMs` 不足，因为结算不会修改 session start。`savePomodoroSession` 必须同时拒绝当前已 `settled` 的会话，或增加版本/期望 settled 状态/transition-specific CAS；pause/resume 只允许明确的运行态转换。
- **建议测试**：后台已结算后，用同一 session start 的旧快照执行前台 pause/resume，断言写回被拒绝且统计不重复。

#### 【P2-05】成就进度硬编码 (0 to 1) 且页面不自动刷新
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/AchievementManager.kt:118-140`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildAchievementsScreen.kt:38-41`
- **复现推演**：孩子打开“成就中心”页面查看进度。在 `AchievementManager.kt:131, 134` 中，`perfect_day`（完美一天）和 `habit_complete`（习惯达人）的进度被直接硬编码为 `(0 to 1)`，即使孩子完成了所有任务或坚持了习惯，进度条永远显示 0/1。此外，`ChildAchievementsScreen` 采用 `LaunchedEffect(Unit)` 一次性获取进度快照，停留在此页面打卡时，进度条不会响应式刷新。
- **根因**：部分成就进度逻辑未实际计算而以静态伪代码占位；UI 收集进度非流式响应。
- **影响**：部分成就进度永远显示为 0；完成任务后成就页不刷新，体验不佳。
- **建议修复**：补齐 `perfect_day` 与 `habit_complete` 的真实数据库查询统计，并将进度作为 Flow 暴露。
- **建议测试**：满足完美一天条件时调用 `getProgress`，断言对应进度为 `1 to 1`。

#### 【P2-06】宠物成长与长期报告把心愿退款当成正向收入刷分
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointDao.kt:24-26`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/report/GrowthReportScreen.kt:45, 62, 90-100`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentPointsScreen.kt:73-74`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentDashboardScreen.kt:91-105, 182-185`
- **复现推演**：孩子当前有 100 积分。孩子创建心愿存入 100 积分（扣除 100 分），随后删除心愿退回 100 积分。`WishGoalRepository.deleteGoal` 调用 `refundSpentPoints` 生成了一条 `points = 100` 的正分 `REWARD_EXCHANGE` 退款流水。在 `PointDao.kt:25` 中，`SELECT COALESCE(SUM(points), 0) FROM point_records WHERE points > 0` 仅以 `points > 0` 判定收入。结果：心愿退款被当作新获得的收入计入 `getLifetimeEarnedPoints()`。孩子通过反复“存心愿→删心愿”，可以无限刷高累计获得积分，导致学习宠物快速升级进化，同时成长报告和家长看板的总收入数据严重虚高。
- **根因**：DAO 统计“累计获得”时仅粗暴判断 `points > 0`，未排除 `type == REWARD_EXCHANGE` 的退还记录。
- **影响**：孩子可作弊刷高宠物等级与历史收入统计，破坏长期激励的公平性。
- **建议修复**：修改 SQL 查询为：`WHERE points > 0 AND type != 'REWARD_EXCHANGE'` 或显式指定收入类型白名单。
- **建议测试**：执行存入心愿再退款，断言 `getLifetimeEarnedPoints` 前后读数保持不变。

#### 【P2-07】Android 系统 Back 键未接线应用栈直接退出
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/navigation/AppNavigator.kt:14-39`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt:79-97`
  - `composeApp/src/androidMain/kotlin/com/hstudy/app/MainActivity.kt:17-30`
- **复现推演**：在 Android 手机上，孩子从主页进入“错题本”，再进入“错题详情”，此时 `AppNavigator` 内存堆栈深度为 3。孩子按下 Android 手机底部的系统返回键或执行边缘侧滑返回手势。由于全仓没有在 Compose 中引入 `BackHandler`，也没有在 `MainActivity` 中对接 `onBackPressedDispatcher`，系统将该事件判定为未消费，直接将 `MainActivity` finish 退出并退回手机桌面，而非返回“错题本”列表。
- **根因**：轻量级跨平台导航器 `AppNavigator` 仅管理内存列表，未接入 Android 平台的返回事件分发体系。
- **影响**：Android 端所有深层页面无法使用系统手势返回，极大破坏用户体验。
- **建议修复**：在 `App.kt` 的 Android 平台实现中接入 `BackHandler(enabled = navigator.backStack.size > 1) { navigator.popBackStack() }`。
- **建议测试**：编写 UI 测试模拟 Android 返回按键分发，断言堆栈弹出上一层而非退出。

#### 【P2-08】全表 Flow<List> 无分页机制的可扩展性测试缺口
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointDao.kt:15-19`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/FocusDao.kt:15-16, 27-31`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/MistakeDao.kt:24-25, 62-63`
- **复现推演**：用户使用应用半年以上，积分流水表 `point_records` 累积达 5000 条，错题记录达 1000 条。`ChildPointsScreen` 与 `MistakeListScreen` 通过 `getAllRecords()` 和 `getAllMistakes()` 全量流式拉取数据。未采用 `PagingSource` 时，一条写入会触发整表对象重新发射并带来列表重组、内存与耗时风险；本项没有实测崩溃，也不能据此断言 Room 查询必然发生在主线程。
- **根因**：数据访问层全部采用无分页的裸 `Flow<List<Entity>>`。
- **影响**：当前小数据量无故障，但长期使用数据量膨胀后存在明显的内存占用偏高与卡顿风险（注：此项为架构扩展性缺口，不得声称已在当前小数据集下测得崩溃）。
- **建议修复**：为流水表、错题表、专注表引入分页加载机制（如 LIMIT/OFFSET 或 Paging 3）。
- **建议测试**：在基准测试中注入 5000 条记录，监控单次插入时全表查询的反序列化耗时。

#### 【P2-09】PointApprovalRepository 存在废弃且无发分/冲销的重复 API
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointApprovalRepository.kt:46-59`
- **复现推演**：若后续开发者在新增家长端审批界面时，误调用了 `PointApprovalRepository.approve(id)` 或 `approveAll()`，该方法仅执行 `dao.updateStatus(id, ApprovalStatus.APPROVED)`，没有创建积分流水（`insertPointRecord`），没有记录 `awardedRecordId`，也没有校验状态是否为 PENDING。结果：审批单变成了已通过，但孩子实际未得到任何积分。
- **根因**：项目中存在两套审批仓库——正统走带有积分发放与冲销闭环的 `PointRepository`，而 `PointApprovalRepository` 保留了无账本联动的陈旧 API。
- **影响**：当前虽无生产调用者，但作为公共仓库类存在严重的代码误用与维护陷阱。
- **建议修复**：废弃（@Deprecated）或删除 `PointApprovalRepository` 中未联动账本的危险方法。
- **建议测试**：静态检查全仓无 `PointApprovalRepository.approve` 调用点，并在重构后将其移除。

#### 【P2-10】日程排程忽略 repeatDays 写死为 daily（影响 legacy/导入日程）
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ScheduleAlarmHelper.kt:47-60, 74-89`
  - `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidReminderBootstrap.kt:87-101`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/entity/DailyScheduleEntity.kt:9-18`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ParentScheduleManageScreen.kt:299-305`
- **复现推演**：在新版 UI 中默认创建的日程是全周（`repeatDays = "1,2,3,4,5,6,7"`）。但实体定义中允许自定义重复星期，且通过家庭同步或历史版本可能导入仅在周末重复（`repeatDays = "6,7"`）的日程。在 `ScheduleAlarmHelper.kt:59, 88` 中，注册系统闹钟时直接硬编码了 `repeatDaily = true`。结果：该日程在周一至周五依然会触发系统闹钟提醒孩子。
- **根因**：排程工具类抹平了实体的 `repeatDays` 属性，强制按每日重复向系统注册。
- **影响**：仅影响包含自定义星期的 legacy 或导入日程，导致非预期的闹钟打扰（注：新 UI 默认创建为全周，不属于默认缺陷）。
- **建议修复**：在排程计算触发时间时根据当前星期与 `repeatDays` 计算下一次匹配时间。
- **建议测试**：传入仅周末生效的日程，验证周一不会注册当天触发的闹钟。

#### 【P2-11】报表历史积分用当前分值逆乘与活跃数作分母
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentPointsScreen.kt:160-171`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildEfficiencyReportScreen.kt:98-119, 436-443`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/report/GrowthReportScreen.kt:54-56, 102-103`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentDashboardScreen.kt:151-165`
- **复现推演**：
  1. 在“近7天任务积分排行”中，代码通过 `taskMap[it].effectivePoints()` 乘以历史完成次数来计算得分。若任务分值昨天被家长从 2 分调整为 5 分，历史前天完成的得分被错误地按 5 分呈现；若任务被删除，历史完成得分变成 0 分；
  2. 在成长与效率看板中，计算历史某一天的完成率时，分母采用的是当前的活跃任务数 `tasks.size`，而非历史当天的任务数快照；且分子统计时可能计入了未经过家长 APPROVED 的打卡。
- **根因**：报表展示未从只读的不可变流水表（`point_records`）或快照数据出具，而是依赖可变实体逆向估算。
- **影响**：历史数据口径失真，家长看到的历史统计与实际账本流水不一致。
- **建议修复**：报表积分必须按 `point_records` 流水聚合；完成率统计记录每日任务基数快照。
- **建议测试**：修改任务分值后，断言历史看板中该任务此前的得分读数不变。

#### 【P2-12】专注训练首次纪录 isNewRecord=false、陈旧并发可覆盖更佳成绩；记忆训练无进程恢复
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildFocusTrainingScreen.kt:38-40, 70-79`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildMemoryTrainingScreen.kt`
- **复现推演**：
  1. 孩子首次完成舒尔特方格训练，用时 25 秒。第 74 行判断 `val isRecord = bestMs != 0L && elapsedMs < bestMs`，由于首次 `bestMs == 0L`，计算出 `isRecord = false`，孩子首次创造纪录却未收到“破纪录”的表扬；且 `bestMs` 通过 `collectAsState` 读取，在写回时可能覆盖更佳成绩；
  2. 记忆训练模块完全依赖 Composable 内的 `remember` 变量，在切屏或进程死亡后直接重置，缺乏状态恢复或完整的行为单测。
- **根因**：首创边界条件判断失误；辅助训练模块未做持久化契约。
- **影响**：破纪录激励体验瑕疵；记忆训练进程中断后需重新开始。
- **建议修复**：修正判断：`val isRecord = bestMs == 0L || elapsedMs < bestMs`；记忆训练增加持久化支持。
- **建议测试**：在 `bestMs = 0` 时完成训练，断言触发了破纪录鼓励。

#### 【P2-13】AssignmentRepository 先完成再申请，失败后重试早退
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/AssignmentRepository.kt:42-55`
- **复现推演**：调用 `completeAssignment` 时先将 `isCompleted` 写为 `true`，随后 `onPoints` 抛异常；再次调用同一布置任务时，第 44 行的 `if (assignment.isCompleted) return` 直接返回，积分申请不再执行。
- **根因**：完成状态与积分申请分裂写，且重试仅以完成状态作为早退条件，没有独立的发分状态或幂等键。
- **影响**：孩子永久漏领该布置作业积分，家长重试也无法修复。
- **建议修复**：将状态变更与发分放入同一事务，或引入 `pointsClaimed/approvalBizKey` 状态，使发分失败后可安全重试且不会重复发分。
- **建议测试**：首次 `onPoints` 抛异常，第二次重试成功；断言最终只产生一个有效审批和一次完成状态。

#### 【P2-14】HabitRepository 整实体覆盖与成就 type 唯一约束缺失
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/HabitRepository.kt:39-54, 103-112`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/entity/HabitChallengeEntity.kt:25-33`（`AchievementEntity`）
- **复现推演**：协程 A 读取旧挑战实体后暂停，协程 B 完成打卡并更新进度；A 恢复后用旧实体 `copy(...)` 整行更新，覆盖 B 的新进度。另两个协程同时 `tryUnlockAchievement` 同一 `type` 时，查后插没有唯一约束兜底，可写入重复成就。
- **根因**：仓库采用整体实体覆盖而非字段级 CAS；成就表缺少 `type` 唯一约束，解锁仅依赖先查后插。
- **影响**：挑战进度回退或丢失，成就重复解锁，统计与徽章展示失真。
- **建议修复**：改用字段级条件更新并校验受影响行数；为 `AchievementEntity.type` 增加经历史数据清理的唯一约束和 upsert 语义。
- **建议测试**：构造交错旧快照写入，断言不覆盖最新进度；并发解锁同一类型成就，断言表中只有一条。

#### 【P2-15】MistakeRepository 进度/历史/审批三表分裂写
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/MistakeRepository.kt:58-100`
- **复现推演**：`reviewMistake` 已更新错题复习进度并插入复习历史，随后 `pointRepository.checkIn` 抛异常或进程中断；再次进入页面时复习次数已增加，但对应积分审批不存在。
- **根因**：错题进度、复习历史和积分审批三表写入没有统一事务边界，部分成功无法回滚。
- **影响**：复习统计与积分审批不一致，重试可能再次增加复习次数。
- **建议修复**：将同一次复习业务操作纳入数据库事务，并以固定 `bizKey` 保证审批幂等；失败时整体回滚。
- **建议测试**：在第三步注入异常，断言前两步全部回滚；重试后复习进度、历史和审批各只增加一次。

#### 【P2-16】RewardRepository 与 ChildRewardStoreScreen 兑换和扣积分分裂写
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/RewardRepository.kt:29-36`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildRewardStoreScreen.kt:216-225`
- **复现推演**：`exchangeReward` 已插入兑换记录，随后 `spendPoints` 抛异常或进程在两步之间终止；默认参数下正数虽会写负分流水，但异常中断时仍只留下兑换记录而没有扣款。
- **根因**：兑换记录和积分流水是两个独立写入，且没有统一事务或余额强校验。
- **影响**：出现“已兑换但未扣款”的免费兑换，账本与兑换历史不一致。
- **建议修复**：在事务内先强校验余额，再插入兑换记录和负分流水；任一步失败整体回滚。
- **建议测试**：在扣款步骤注入异常，断言兑换记录不存在；余额不足时断言两个表都不写入。

#### 【P2-17】MandatoryPenaltyHelper 返回未封顶扣分而账本封顶
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/MandatoryPenaltyHelper.kt:76-88`
- **复现推演**：当前余额 10 分、累计罚分 25 分时，账本实际只写入 `deducted = 10`，函数却返回 `totalPenalty = 25`。
- **根因**：返回值仍使用封顶前的总罚分，而不是实际写入账本的扣分值。
- **影响**：调用方提示或统计的扣分大于真实扣款，家长与孩子看到不一致的处罚结果。
- **建议修复**：返回 `deducted`，并让 UI 文案明确“已扣 10 分、剩余 15 分未执行”。
- **建议测试**：余额 10、罚分 25 时断言返回值、账本负分和 UI 数据均为 10。

#### 【P2-18】通知点击缺少深链路由（Android 无 extras，iOS 缺 response 处理）
- **文件与行号**：
  - `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidNotificationScheduler.kt:123-127`
  - `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosNotificationScheduler.kt`
- **复现推演**：用户点击日程、复习或周报通知；Android `PendingIntent` 使用默认启动 Intent 且无路由 extras，iOS 也未实现点击 response 委托，应用只打开首页。
- **根因**：排程只完成“发通知”，没有把提醒类型与目标路由传递到应用导航层。
- **影响**：用户必须手工寻找对应页面，提醒与业务入口脱节。
- **建议修复**：在两端通知点击回调中携带稳定的路由标识，由应用启动/恢复路径统一分发。
- **建议测试**：Android 断言 Intent extras 与目标路由；iOS 断言 delegate 收到点击后进入对应页面，冷启动与热启动均覆盖。

#### 【P2-19】任务软删除未清理 DailyScheduleEntity 的 linkedTaskId 提醒
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/TaskRepository.kt:33-40`
- **复现推演**：创建一个带任务关联的日程后软删除该任务，`daily_schedules.linkedTaskId` 仍保留原 ID；后续日程读取或提醒无法找到有效任务。
- **根因**：任务软删除只更新任务表，没有同步解绑关联日程或取消对应提醒。
- **影响**：残留失效关联与提醒， timeline 可能显示空任务或继续打扰。
- **建议修复**：软删除时在事务内解绑 `linkedTaskId`，并取消已注册的相关日程提醒。
- **建议测试**：删除关联任务后，断言日程不再指向该任务且对应提醒被取消；无关联任务删除行为不变。

#### 【P2-20】ChildDailyPlanScreen 与 ChildScheduleTimelineScreen 跨午夜时间快照不自动刷新
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildDailyPlanScreen.kt:33-34`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/schedule/ChildScheduleTimelineScreen.kt:47-54`
- **复现推演**：页面在 23:59 打开并持续停留，跨过 00:00 后 `today` 和 `nowMinutes` 仍保留进入页面时的值，日期和当前日程高亮不推进。
- **根因**：时间在初次组合时计算为普通局部常量，没有日期/分钟变化的状态源。
- **影响**：跨午夜后显示昨日计划和错误高亮，用户可能误以为新一天没有更新。
- **建议修复**：使用可订阅的当前日期/时间状态，在日期变化时刷新查询，在分钟变化时刷新高亮。
- **建议测试**：可控时钟从 23:59 推进到 00:01，断言日期、查询日期与高亮均切换到新一天。

#### 【P2-21】ChildGoalTrackerScreen 完成率未设上限可超 100%
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildGoalTrackerScreen.kt:28-38`
- **复现推演**：今日完成 3 个任务后，家长软删除其中 1 个；当前活跃任务变为 2 个，而 `completedIds` 仍有 3 个，完成率按 3/2 计算超过 100%。
- **根因**：分子使用历史完成集合，分母使用当前活跃任务集合，二者时间口径不同且没有上限保护。
- **影响**：目标追踪显示不可能超过 100% 的完成率，统计失真。
- **建议修复**：完成率以同一时点的任务快照为分子分母，并设置 `0..100` 边界。
- **建议测试**：完成后软删任务，断言完成率不超过 100%，并验证无完成记录时为 0。

#### 【P2-22】UserPreferences 家长控制密码明文存储在 DataStore
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt:137-148`
- **复现推演**：设置家长密码后读取 DataStore，`PARENT_PASSWORD` 的值就是用户输入的明文；备份或设备数据被提取时可直接还原密码。
- **根因**：凭据校验只需要摘要，但实现直接持久化原始字符串，没有加盐哈希或密钥派生。
- **影响**：本地备份或设备提取会泄露家长控制凭据。
- **建议修复**：迁移为带随机盐的慢哈希摘要，校验时只比较计算结果；提供旧值透明迁移并清除明文。
- **建议测试**：设置密码后存储值不等于明文且每次盐不同；旧明文迁移后校验通过、原键被清除。

#### 【P2-23】MigrationVerifyTest 仅使用 FakeConnection 模拟列名检查，缺乏真实物理迁移测试
- **文件与行号**：`composeApp/src/commonTest/kotlin/com/hstudy/app/data/local/MigrationVerifyTest.kt:1-93`
- **复现推演**：现有测试用 `FakeConnection` 返回预置 PRAGMA 列名，只验证比对函数；它不会执行真实 SQL、索引、外键或数据转换，因此无法发现真实 v1→v13 升级中的数据丢失。
- **根因**：测试替身替代了 SQLite 驱动与迁移执行，不是数据库集成测试。
- **影响**：真实升级可能出现列缺失、约束冲突或数据损坏而现有 325 条测试仍全绿。
- **建议修复**：使用真实 SQLite 文件建立各历史版本样本数据，逐步执行迁移并校验 schema 与数据保留。
- **建议测试**：覆盖 v1→v13 全链路及关键跨版本升级，逐表校验行数、主外键、索引和代表性字段值。

#### 【P2-24】奖励预设入口可重复插入整套奖励
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/RewardRepository.kt:14-19,44-81`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentHomeScreen.kt:215-219`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentRewardManageScreen.kt:57-79`
- **复现推演**：UI 直接调用 `initDefaultRewards()`；该方法每次无条件插入 27 个 `id=0` 的 `RewardEntity`。主键 autogenerate 使 `REPLACE` 变成追加而非按名称替换，表也没有 name/presetKey 唯一约束。快速重复点击且 Room Flow 尚未刷新时，可连续追加多套预设。
- **根因分析**：`ensureDefaultRewards()` 有 active-count 检查，但两个 UI 入口绕过它直接调用无守卫的初始化方法；DAO 无预设唯一键，调用方也无 in-flight 互斥。
- **影响**：奖励列表出现重复项，预设语义和后续管理失真。
- **建议修复**：增加稳定 `presetKey` 唯一约束，并在事务内执行 ensure/upsert；UI 统一调用幂等入口并防重复点击。
- **建议测试**：连续两次调用只保留 27 项；并发调用不产生重复；已有用户自定义奖励不被覆盖。
- **验证边界**：`PresetRegistryTest` 只验证 canonical/翻译注册表，不验证插入幂等或并发。

#### 【P2-25】家庭进度导出由多次独立查询拼成非一致快照
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/sync/FamilySyncHelper.kt:180-210`
- **复现推演**：导出依次读取余额、连胜、完成数、累计专注、错题、掌握数、今日完成和今日专注；这些查询/Flow 不在同一事务或快照中。并发打卡、审批或错题更新可落在不同读取之间。
- **根因分析**：摘要 DTO 没有快照时间点语义，跨 Room/DataStore 的读取无法保证同一时刻。
- **影响**：家长收到的摘要可能内部矛盾，例如错题总数与掌握数不匹配，或今日数据与总额度不对应。
- **建议修复**：尽量用单次数据库快照/事务聚合 Room 数据；对 DataStore 值记录采样时间并明确“近似摘要”，或提供一致性版本。
- **建议测试**：在两次查询之间注入写入，断言导出拒绝不一致快照或返回明确的时间/版本说明。

#### 【P2-26】家庭进度导入缺少数值关系校验
- **文件与行号**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/sync/FamilySyncHelper.kt:220-245`
- **复现推演**：导入只检查 `type == "child_progress"`。负数积分/专注、`masteredMistakes > totalMistakes`、`unmasteredMistakes` 与 `total-mastered` 不一致等输入都会形成摘要并显示。
- **根因分析**：外部 JSON 边界只有类型校验，没有字段范围与交叉字段约束。
- **影响**：损坏或恶意文件会产生无意义统计，误导家长。
- **建议修复**：校验非负、上限/合理范围、掌握数不超过总数、未掌握数等于总数减掌握数，并校验版本与导出时间。
- **建议测试**：负数、掌握数超总数、未掌握数不一致、错误 type/version 均返回失败。

#### 【P2-27】家庭配置分享失败仍显示导出成功
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/data/sync/FamilySyncHelper.kt:248-259`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt:251-270`
- **复现推演**：`exportControlConfig()` 成功写文件后调用 `shareFile()`；后者读取或系统分享异常被静默吞掉。调用方无法区分“已导出”和“已分享”，仍显示 `settingsExportedMessage`。
- **根因分析**：`FileSharer.shareTextFile` 是 `Unit` 且无结果回调，helper 又把异常转为成功路径。
- **影响**：用户误以为设置已发送给孩子，实际分享未发生。
- **建议修复**：将导出与分享结果分离，分享接口返回可观察结果；至少区分“文件已生成，分享失败”的提示。
- **建议测试**：注入分享失败，断言 UI 显示失败而非成功。

#### 【P2-28】周追踪历史积分与日期口径可失真
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/weekly/WeeklyTrackerScreen.kt:31-67,249-307`
- **复现推演**：每日积分用当前 `taskMap[it].points` 重算历史完成，任务调分或删除会改写历史显示；pending/rejected 完成也计入，bonus/penalty/adjustment 不计。`weekDates` 只在初次组合固定，周起止每次重组重算，页面跨周后表头与汇总窗口可能错位。
- **根因分析**：展示层用可变当前任务实体逆推历史，而非只读账本/审批快照；日期状态没有统一锚点。
- **影响**：孩子/家长看到的历史每日积分与真实账本不一致，跨周后表格错位。
- **建议修复**：历史任务得分从 `point_records` 或审批快照聚合；明确完成状态过滤；将周锚点作为单一状态源。
- **建议测试**：改分值/删任务后历史读数不变；pending/rejected 不计入；跨周重组后表头与汇总窗口一致。

#### 【P2-29】实时设备发现能力未接入产品流程
- **文件与行号**：
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/platform/Platform.kt:75-100`
  - `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/AndroidDeviceDiscovery.kt:18-37`
  - `composeApp/src/iosMain/kotlin/com/hstudy/app/platform/IosDeviceDiscovery.kt:21-28`
  - `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt:394-397`
- **复现推演**：平台定义并初始化了 `DeviceDiscovery`，Android/iOS 也有实现，但生产代码没有 `Platform.deviceDiscovery.startDiscovery/stopDiscovery` 调用。设置页仍显示“配对后”查看进度/推送任务，而实际可用路径只有 JSON 文件导出/导入。
- **根因分析**：发现接口停留在平台能力层，没有 UI、会话连接和业务仓库接线。
- **影响**：设置页所述远程/设备流程不能提供实时发现与传输；测试也不能把接口存在当作功能已实现。
- **建议修复**：明确产品范围：要么接线发现、连接、权限与错误状态，要么在 UI/文档中明确当前仅支持手动文件同步。
- **建议测试**：若保留该能力，补发现启动/停止、权限拒绝、设备消失和连接失败测试；若删除，移除误导性能力与文案。

---

## 四、已确认修复核对（D-01～D-13）

以下为此前修复批次的验证情况，确认未发生逻辑回退，**不计入新缺陷**：

| 编号 | 核心内容 | 验证依据与源码定位 | 状态判定 |
|---|---|---|---|
| **D-01** | 窄屏错题列表嵌套滑动修复 | `ChildMistakeListScreen.kt:1-15`，采用单一顶层 LazyColumn；无专门 UI 滚动测试 | ⚠️ 静态回归，UI 未动态验证 |
| **D-02** | 任务表历史重复行治理与幂等播种 | 空表检查在 `TaskRepository.kt:167-170`；`AppGraph.kt:157-206` 调 ensure；`TaskRepositorySeedIdempotencyTest` (3/3 passed) | ⚠️ 任务播种回归通过；奖励播种未等价覆盖 |
| **D-03/04** | 任务与奖励软删除机制 | `TaskDao.kt` 与 `RewardDao.kt` 均使用 `isActive = 0`，不破坏历史流水外键引用 | ⚠️ 静态回归通过 |
| **D-05/07** | 番茄钟时间戳锚定与补结算 | `PomodoroSessionStateTest` (8/8) 与 `PomodoroFocusSettlementTest` (10/10) 通过 | ⚠️ 纯函数/CAS/fake 恢复回归通过；真实进程死亡、跨进程与真机未验证 |
| **D-08** | 心愿存入/退款走权威入口与账本不留展示文案 | `WishGoalRepositoryLedgerTest` (8/8 passed)，`wish_deposit:<id>` / `wish_refund:<id>` 派生显示通过 | ✅ 账本与文案回归通过；事务/超额边界仍见 P1-03 |
| **D-09** | 系统奖励防重入 BonusClaimGuard + DataStore CAS | `BonusClaimGuardTest` (4/4 passed)，`UserPreferencesBonusClaimCasTest` (3/3 passed) | ✅ 对应 CAS/guard 场景回归通过 |
| **D-10** | 家长撤回审批闭环与打卡状态对称 | `PointRepositoryParentRevokeTest` (6/6 passed)，撤销状态机与历史 bizKey 边界通过 | ✅ 对应回归通过 |
| **D-11** | 超期待审清理逐条复读防并发 | `PointRepositoryStalePendingCleanupTest` (6/6 passed)，过期清理安全阈值与防误删通过 | ✅ 对应回归通过；真实 SQLite 并发未验证 |
| **D-12** | 规范化 `bonus_*` 业务键及呈现层派生 | `PointRecordBonusNoteTest` (6/6 passed)，`i18n_template_diff.py --branch` 984/984、[B] 980/980 | ✅ 对应回归与闸门通过 |
| **D-13** | 连续复习里程碑/抽奖原子 CAS 与防重入 | `BonusClaimGuardD13Test` (5/5 passed)，`UserPreferencesD13CasTest` (2/2 passed) | ✅ 对应 CAS/guard 场景回归通过 |
| **Task C** | 批量审批/撤销/超期清理事务包装 | `PointRepositoryTransactionAtomicityTest` (8/8 passed)，使用 Fake DAO + `JournalTxRunner` | ⚠️ 仅证明入口包装和 Fake 回滚；不能替代真实 SQLite/Android 并发验证；iOS 降级已披露 |

---

## 五、现有测试真实结果（37 个测试类，325 tests 全集）

依据 `composeApp/build/test-results/testDebugUnitTest/TEST-*.xml` 逐个文件解析统计。下表每个测试类只列 1 个 XML 中确实存在的代表方法，**不是方法全集**；类名、用例数及代表方法均已机器核对。

| 序号 | 完整测试类名 | 运行数 | 失败 | 错误 | 跳过 | 代表性真实测试方法名（每类 1 个，来自 XML） |
|---|---|---|---|---|---|---|
| 1 | `com.hstudy.app.data.focus.PomodoroFocusSettlementTest` | 10 | 0 | 0 | 0 | `doubleTrigger_concurrentRecover_exactlyOneWins` |
| 2 | `com.hstudy.app.data.focus.PomodoroSessionStateTest` | 8 | 0 | 0 | 0 | `resumeShiftsDeadlineByAccumulatedPausedMillis` |
| 3 | `com.hstudy.app.data.local.MigrationVerifyTest` | 4 | 0 | 0 | 0 | `populatedTableWithV12ColumnsReportsNothingMissing` |
| 4 | `com.hstudy.app.data.repository.HabitRepositoryUndoTest` | 4 | 0 | 0 | 0 | `undo_rollbacksCheckInMadeYesterday` |
| 5 | `com.hstudy.app.data.repository.MistakeRepositoryTest` | 10 | 0 | 0 | 0 | `saveVariation_updates_note_and_hasVariation_flag` |
| 6 | `com.hstudy.app.data.repository.PointRepositoryInvariantTest` | 11 | 0 | 0 | 0 | `approveAndAward_calledTwice_onlyCreditsOnce` |
| 7 | `com.hstudy.app.data.repository.PointRepositoryParentRevokeTest` | 6 | 0 | 0 | 0 | `revokeApproval_whenApproved_clawsBackBalanceAppendsReversalAndRevokes` |
| 8 | `com.hstudy.app.data.repository.PointRepositoryStalePendingCleanupTest` | 6 | 0 | 0 | 0 | `expireStalePendingApprovals_cancelsAllStaleAndPendingCountDropsToZero` |
| 9 | `com.hstudy.app.data.repository.PointRepositoryTaskApprovalStateTest` | 3 | 0 | 0 | 0 | `sameBizKeyMultipleRows_latestByIdWins` |
| 10 | `com.hstudy.app.data.repository.PointRepositoryTransactionAtomicityTest` | 8 | 0 | 0 | 0 | `approveAllAndAward_throwMidBatch_rollsBackEntireBatch` |
| 11 | `com.hstudy.app.data.repository.TaskRepositorySeedIdempotencyTest` | 3 | 0 | 0 | 0 | `ensureDefaultTasks_withExistingTasks_doesNotInsert` |
| 12 | `com.hstudy.app.data.repository.WishGoalRepositoryLedgerTest` | 8 | 0 | 0 | 0 | `depositPoints_reachingTarget_marksGoalCompleted` |
| 13 | `com.hstudy.app.i18n.AppLanguageResolveTest` | 6 | 0 | 0 | 0 | `systemChineseSelectsZh` |
| 14 | `com.hstudy.app.i18n.ChildRewardStoreCopyTest` | 8 | 0 | 0 | 0 | `childRewardStoreCopyDiffers` |
| 15 | `com.hstudy.app.i18n.ChildScheduleCopyTest` | 13 | 0 | 0 | 0 | `scheduleAlarmBodyPinsBothLanguages` |
| 16 | `com.hstudy.app.i18n.CurrentDisplayLanguageTest` | 4 | 0 | 0 | 0 | `explicitZhWinsOverEnglishSystem` |
| 17 | `com.hstudy.app.i18n.LanguagePreferenceTest` | 2 | 0 | 0 | 0 | `languagePersistsAcrossReads` |
| 18 | `com.hstudy.app.i18n.MistakeCopyTest` | 11 | 0 | 0 | 0 | `mistakeSharedStaticCopyDiffers` |
| 19 | `com.hstudy.app.i18n.ParentPointsCopyTest` | 9 | 0 | 0 | 0 | `parentPointsCopyDiffers` |
| 20 | `com.hstudy.app.i18n.ParentTaskCopyTest` | 9 | 0 | 0 | 0 | `parentTaskEditCopyDiffers` |
| 21 | `com.hstudy.app.i18n.PetCopyTest` | 4 | 0 | 0 | 0 | `petStringsCopyDiffers` |
| 22 | `com.hstudy.app.i18n.PresetRegistryTest` | 16 | 0 | 0 | 0 | `knownCanonicalResolvesPerLanguage` |
| 23 | `com.hstudy.app.i18n.StringsCopyTest` | 64 | 0 | 0 | 0 | `englishTableContainsNoChineseLeftovers` |
| 24 | `com.hstudy.app.i18n.W8CopyTest` | 10 | 0 | 0 | 0 | `tutoringCopyPinsBothLanguages` |
| 25 | `com.hstudy.app.i18n.W9CopyTest` | 5 | 0 | 0 | 0 | `syncAdapterPinsBothLanguages` |
| 26 | `com.hstudy.app.i18n.WishCopyTest` | 18 | 0 | 0 | 0 | `childWishGoalCopyDiffers` |
| 27 | `com.hstudy.app.platform.AnalyticsMappingTest` | 7 | 0 | 0 | 0 | `everyFeatureMapsToSnakeCase` |
| 28 | `com.hstudy.app.platform.AnalyticsPreferenceTest` | 1 | 0 | 0 | 0 | `defaultsToEnabledAndPersistsToggle` |
| 29 | `com.hstudy.app.platform.AnalyticsReporterTest` | 4 | 0 | 0 | 0 | `enabledReporterForwardsEvents` |
| 30 | `com.hstudy.app.ui.child.BonusClaimGuardD13Test` | 5 | 0 | 0 | 0 | `luckyDraw_doubleClickWhileFirstInFlight_secondDrawDoesNotDeposit` |
| 31 | `com.hstudy.app.ui.child.BonusClaimGuardTest` | 4 | 0 | 0 | 0 | `doubleClickWhileFirstInFlight_secondClaimDoesNotDeposit` |
| 32 | `com.hstudy.app.ui.child.UserPreferencesBonusClaimCasTest` | 3 | 0 | 0 | 0 | `tryClaimDailyExcellentBonus_concurrentSameDay_exactlyOneWins_nextDayOpensAgain` |
| 33 | `com.hstudy.app.ui.child.UserPreferencesD13CasTest` | 2 | 0 | 0 | 0 | `tryClaimDailyLuckyDraw_concurrentSameDay_exactlyOneWins_nextDayOpensAgain` |
| 34 | `com.hstudy.app.ui.common.LearningScoreCalculatorTest` | 12 | 0 | 0 | 0 | `full_checkin_gives_40_points` |
| 35 | `com.hstudy.app.ui.common.LevelSystemTest` | 8 | 0 | 0 | 0 | `zero_points_yields_level_1` |
| 36 | `com.hstudy.app.ui.common.PointRecordBonusNoteTest` | 6 | 0 | 0 | 0 | `bonusNoteKeys_arePureAsciiAndOnlyParseTheirOwnShape` |
| 37 | `com.hstudy.app.ui.mistake.SpacedRepetitionTest` | 13 | 0 | 0 | 0 | `nextReviewAt_uses_createdAt_when_never_reviewed` |
| **合计** | **37 个测试类** | **325** | **0** | **0** | **0** | **全绿通过** |

> **特别测试说明**：现有测试主要集中在单协程状态校验、纯 helper 工具函数与 Fake 顺序执行上；**纯 helper / 顺序 Fake 测试不等于真实的 SQLite 并发事务、进程崩溃恢复与真机跨进程验证**。

---

## 六、无法验证项（环境限制与硬件依赖）

1. **iOS 目标构建与真实执行**：当前环境为 Windows 11，缺少 macOS 与 Xcode 工具链，无法验证 iOS 的 Kotlin/Native 编译、`IosNotificationScheduler` 以及 `DirectTransactionRunner` 在 iOS 上的运行时表现；
2. **Android 闹钟后台触发（Doze 模式）与开机重启广播**：`AlarmManager` 在低电耗模式下的唤醒稳定性及 `BootCompletedReceiver` 必须在物理设备上经过系统重启实测；
3. **通知权限拒绝与静默降级**：需在 Android 13+ 真机上手动关闭 `POST_NOTIFICATIONS` 权限验证静默忽略链路；
4. **真实数据库升级历史迁移（v1 -> v13）**：缺乏包含存量数据的真实历史 SQLite 文件测试升级期间的数据保留完整性；
5. **系统时区变更与夏令时跳变**：需动态修改操作系统时区观察跨午夜 `todayString()` 的对齐行为。

---

## 七、后续最小测试清单（推荐补齐）

为覆盖上述新确认的 P1/P2 盲区，建议按优先级补充以下单元与集成测试：

1. **PointRepositoryConcurrencyTest (针对 P1-01)**：
   - 模拟 2 个协程同时针对同一个处于 `PENDING` 状态的 `approvalId` 调用 `approveAndAward`，断言只有 1 个返回大于 0，账本仅新增 1 条正分记录。
2. **TaskRepositoryApplyTemplateRollbackTest (针对 P1-02)**：
   - 在模板任务插入环节注入异常，断言原有活跃任务数量保持完好。
3. **WishGoalDepositLimitTest (针对 P1-03)**：
   - 给定心愿 `targetPoints = 50, savedPoints = 0`，当前总积分 100，测试传入金额 100 时应被拒绝或截断。
4. **WeeklyReportRescheduleTest (针对 P1-04)**：
   - 构造携带 `EXTRA_KIND = WEEKLY_REPORT`、`EXTRA_REPEAT_DAILY = false` 的 Intent 传入 `rescheduleNextFromReceiver`，断言其未被提前 return 拦截，且重新排程。
5. **BackupHelperRollbackTest (针对 P1-05)**：
   - 模拟复制目标路径不可写抛出异常，断言原 live 数据库保持完好；传入损坏二进制文件时断言校验失败。
6. **FamilySyncEmptyTasksGuardTest (针对 P1-06)**：
   - 导入 `{"type":"parent_control"}`（tasks 为空），断言本地数据库现有任务不被删除，导入返回失败。
7. **TaskFrequencyEnforcementTest (针对 P1-07)**：
   - 创建 `WEEKLY` 任务并在同一周连续打卡两次，断言第二次打卡被判定为重复并被拦截。
8. **PetCareBalanceGuardTest (针对 P1-08)**：
   - 余额不足 10 分时调用喂养，断言积分不被扣为负数。
9. **TaskBonusPointsCreditTest (针对 P2-01)**：
   - 配置包含 `bonusPoints` 的任务，打卡后断言待审申请分值包含该额外奖励。
10. **ProofReviewConcurrencyTest (针对 P2-02)**：
    - 并发调用 `reviewProof`，断言仅第一次状态变更生效，第二次操作被拦截。
11. **PomodoroSettledSessionOverwriteTest (针对 P2-04)**：
    - 在会话已被标记 `settled` 后模拟前台 pause 写回，断言其写入被拒绝。
12. **LifetimePointsExcludeRefundTest (针对 P2-06)**：
    - 执行存入心愿再退款，断言 `getLifetimeEarnedPoints` 前后读数保持不变。
13. **AppNavigatorBackHandlerTest (针对 P2-07)**：
    - 模拟 Android 系统 Back 事件，断言栈深度大于 1 时仅弹出栈顶，不退出应用。
14. **RewardPresetIdempotencyTest (针对 P2-24)**：
    - 连续两次调用 `initDefaultRewards()`，断言只存在 27 个默认奖励；并发调用也只能插入一套。
15. **FamilySyncSnapshotConsistencyTest (针对 P2-25)**：
    - 在导出各查询之间改变积分或专注数据，断言导出失败或提供单一一致快照，不混合不同时刻。
16. **FamilyProgressDtoValidationTest (针对 P2-26)**：
    - 导入负数积分、完成数大于总任务数等非法进度 DTO，断言拒绝导入且不改变本地数据。
17. **FamilyShareFailureTest (针对 P2-27)**：
    - 注入文件读取或系统分享失败，断言 UI 显示失败/取消而非继续提示导出成功。
18. **WeeklyTrackerHistoryTest (针对 P2-28)**：
    - 修改任务分值、软删任务并加入 bonus/penalty 后，断言历史每日积分仍与权威流水一致；页面跨周时表格与汇总使用同一周。
19. **DeviceDiscoveryWiringTest (针对 P2-29)**：
    - 若产品保留实时发现能力，断言设置页能启动/停止发现并处理权限拒绝、设备消失和连接失败；若删除能力，则静态门禁禁止继续暴露未接线入口。
20. **NavigationRouteCoverageTest (针对 2.1 路由完整性)**：
    - 以 `Screen` 的 43 个 data object 为封闭集合，断言每个 route 在 `AppNavGraph` 均有对应分发；带参路由额外断言空参、非法数字、`/` 与 `%` 参数不会误命中其他页面。此测试只保证接线完整，不替代页面行为测试。
21. **ParentTaskTemplateUiTest (针对 P1-02 / 模板页覆盖缺口)**：
    - 断言取消确认不调用 `applyTemplate`，确认后只调用一次；插入失败时页面显示失败且不宣称已应用；模板标题/分类/积分按当前语言展示。
22. **ParentWishGoalManageUiTest (针对 P1-03 / 管理页覆盖缺口)**：
    - 覆盖新建、编辑、一键示例重复点击、空名称拒绝；已存积分心愿删除必须二次确认，取消不删不退款，确认后与仓库退款结果一致。
23. **PointsDetailPresentationTest (针对家长/孩子积分明细覆盖缺口)**：
    - 固定 PENDING 与 APPROVED 流水，断言待审分不进入可用余额；核验今天/昨天/本周/历史分组、正负分与旧 note 透传，并验证同一条 bonus/wish 业务键只显示一个标签。
24. **ParentRewardEditFormTest (针对奖励编辑覆盖缺口)**：
    - 断言 `rewardId=0` 走新增、正 id 走更新；空名称不保存；非法积分输入遵循表单约定；重复点击保存不会产生两条新增记录。

---

## 八、已知残余与设计边界说明

以下项目在此前裁决或文档中已明确披露，不作为新发现缺陷对待，仅供后续迭代参考：

1. **宠物与商城流水中的硬编码中文字面量**：
   `ChildPetScreen.kt` 与 `ChildRewardStoreScreen.kt` 中写入 `point_records.note` 的字符串（如 `"喂养宠物"`、`"兑换：${reward.name}"`）属于 Ruling 25 已明确披露的存量设计残余。当前 i18n 存储边界闸门的设计目标是拦截“动态文案表引用注入数据库”，对硬编码字面量属于已知豁免，后续可通过定义类似 `pet_feed` 的纯 ASCII 业务键并由 UI 呈现层派生文案进行清理；
2. **iOS 批量事务降级直通（F-7 披露）**：
   由于 Room 在 KMP iOS 架构上 direct driver 事务支持的限制，`DbTransactionRunner.ios.kt` 采用了 `DirectTransactionRunner` 直通执行，该局限已在提交 `42f757d` 中完整披露；
3. **`spendPoints` 准确语义约定**：
   `PointRepository.kt:482` 中 `spendPoints(points: Int, note: String, requireSufficientBalance: Boolean = false)` 的默认参数为 `false`。在默认模式下，对于正数积分直接执行负分流水插入，不进行余额校验；只有显式指定 `requireSufficientBalance = true` 时，才会在 `points <= 0 || getTotalPoints().first() < points` 条件下返回 `false` 且不写入流水。调用方应根据业务场景明确是否启用余额守卫。

---

## 九、“用户要求 → 报告证据”完成核对清单

| 用户原始要求 | 报告证据与核验方式 | 完成判定 |
|---|---|---|
| **使用 Antigravity 分析代码** | 报告来源注明 Antigravity Desktop 2.34.0 实际生成初稿及两轮修订；锁屏阻断第三轮输入后，由主会话基于源码/XML/脚本输出独立终审，不冒充 Antigravity 结论 | ✅ 已实际使用，来源可追溯 |
| **对相关功能逐一分析** | 第 2.1 节以 `Screen.kt` 的 43 个 data object 为封闭集合，逐个映射 `AppNavGraph` 实际页面与流程证据；第二节按启动、导航、门禁、任务、积分、奖励、目标、专注、错题、证明、习惯、成就、心愿、日程、宠物、报表、同步、备份、迁移等流程给出入口、行号、DAO/仓库路径 | ✅ 已形成逐流程矩阵与路由完整性核对 |
| **对每一个业务流程进行测试** | 第一节与第五节给出 Gradle 命令及 37 个 XML、325 条用例、0 失败；第 2.1 节和第二节逐项区分“已有针对性测试 / 仅文案 / 无业务测试 / 真机未验证” | ⚠️ 已测试现有覆盖范围；未覆盖项没有被伪装成通过，见第六、七节 |
| **判断是否还有改进空间** | 第三节列出 0 个新 P0、8 个 P1、29 个 P2；第七节给出 24 组最小测试与修复方向 | ✅ 已给出可执行改进项 |
| **不把 D-01～D-13 再报为新缺陷** | 第四节单列既有批次回归核对，明确不计入新缺陷 | ✅ 已去重 |
| **每个新发现都有证据，不虚构动态复现** | 第三节每项包含文件/行号、推演路径、根因、影响、修复与建议测试；核心声明及第三节声明均明确“源码静态确认、未动态复现” | ✅ 证据口径一致 |
| **测试名称必须真实存在** | 第五节 37 个代表方法逐个来自本轮 JUnit XML；明确每类仅列 1 个代表方法，不是方法全集 | ✅ 已机器核对 |
| **给出 Gradle 与静态闸门真实输出** | 第一节记录完整 Gradle 命令、`BUILD SUCCESSFUL in 47s`、37 files/325 tests，以及 4 条生产闸门和 2 条支撑自测的退出码与读数 | ✅ 已记录 |
| **披露无法验证项与后续最小测试** | 第六节列出 iOS、Doze、权限、真实迁移、时区等外部依赖；第七节 P1-01～P1-08、P2-01/02/04/06/07/24～29 均配最小测试，并新增路由、任务模板、心愿管理、积分明细、奖励编辑共 5 组页面/接线测试 | ✅ 已披露 |
| **不修改产品代码、测试、构建配置或其他文档，不 commit/push** | 本任务仅维护 `docs/antigravity-post-v332-review.md`；终审检查分支与 HEAD 保持不变，工作区无跟踪文件修改 | ✅ 范围合规 |

---

*报告重新生成时间：2026-11-25*
*核验方式：源码严格对照 + Gradle 真实运行（325 tests passed）+ 4 条生产静态闸门与 2 条支撑自测*
