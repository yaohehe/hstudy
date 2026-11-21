# HStudy v3.3.1 缺陷修复、全流程业务审计与稳定性强化执行指令

> **文档性质**：v3.3.1 研发执行规范说明书（Directives Specification for v3.3.1）  
> **面向对象**：执行实施与代码落地的开发工程师  
> **基准基线**：`main` 分支 `6573b52`（v3.3.0 发布版，252 个单元测试全绿）  
> **触发来源**：  
> 1. Redmi Android 17 Compact 竖屏真机签核（深色+浅色模式）实测新暴露的 7 项核心缺陷（D-01 至 D-07）与 3 项待办验证；  
> 2. 全流程业务贯穿审计（角色切换 $\to$ 任务打卡 $\to$ 习惯 $\to$ 目标 $\to$ 错题/举一反三 $\to$ 专注 $\to$ 商城/心愿 $\to$ 宠物 $\to$ PENALTY $\to$ 审批/撤销 $\to$ 明细 $\to$ 报表）排查出的新增深层漏洞（D-08 至 D-11）。  
> **执行约束**：不改动现有任何代码，本指令直接写入 `docs/antigravity-v331-directives.md`。每条指令均标明具体文件路径、函数名、根因剖析、修复指令、测试用例与验收标准，保持代码与既有规范的高契合度，可直接落地。

---

## 目录

1. [版本背景与全流程审计基准](#1-版本背景与全流程审计基准)
2. [缺陷与隐患总览矩阵 (D-01 至 D-11，含 P0 / P1 / P2 分级)](#2-缺陷与隐患总览矩阵-d-01-至-d-11含-p0--p1--p2-分级)
3. [P0 级：严重交互阻断与资产/数据一致性修复](#3-p0-级严重交互阻断与资产数据一致性修复)
   - [3.1 【P0-01 / D-01】错题本窄屏布局压瘪导致错题不可触达](#31-p0-01--d-01错题本窄屏布局压瘪导致错题不可触达)
   - [3.2 【P0-02 / D-02】任务表历史重复行治理与冷启动播种幂等化](#32-p0-02--d-02任务表历史重复行治理与冷启动播种幂等化)
   - [3.3 【P0-03 / D-08】心愿存钱罐积分账本类型污染与删除目标资产蒸发修复](#33-p0-03--d-08心愿存钱罐积分账本类型污染与删除目标资产蒸发修复)
4. [P1 级：业务状态真实性、系统防刷与全生命周期可靠性](#4-p1-级业务状态真实性系统防刷与全生命周期可靠性)
   - [4.1 【P1-01 / D-03】任务打卡卡片动态审批状态真实映射与反悔重打卡](#41-p1-01--d-03任务打卡卡片动态审批状态真实映射与反悔重打卡)
   - [4.2 【P1-02 / D-05+D-06+D-07】番茄钟全流程时间戳持久化与屏幕常亮综合架构](#42-p1-02--d-05d-06d-07番茄钟全流程时间戳持久化与屏幕常亮综合架构)
   - [4.3 【P1-03 / D-09】系统自动奖励点击防连击与原子标记防刷防护](#43-p1-03--d-09系统自动奖励点击防连击与原子标记防刷防护)
   - [4.4 【P1-04 / D-10】家长端已审批记录撤回审批 (Revoke) 闭环补齐](#44-p1-04--d-10家长端已审批记录撤回审批-revoke-闭环补齐)
5. [P2 级：界面交互与多端自适应优化](#5-p2-级界面交互与多端自适应优化)
   - [5.1 【P2-01 / D-04】家长首页功能控制台自适应响应式重塑](#51-p2-01--d-04家长首页功能控制台自适应响应式重塑)
   - [5.2 【P2-02 / D-11】超期滞留待审申请清理机制与冻结分释放](#52-p2-02--d-11超期滞留待审申请清理机制与冻结分释放)
6. [待办验收项执行规程 (Verification Checklist)](#6-待办验收项执行规程-verification-checklist)
   - [6.1 PENALTY 跨日自动扣分真机验证](#61-penalty-跨日自动扣分真机验证)
   - [6.2 iOS 真机/模拟器视觉与交互签核](#62-ios-真机模拟器视觉与交互签核)
   - [6.3 GA4 DebugView 埋点事件全流核对](#63-ga4-debugview-埋点事件全流核对)
7. [交付计划与验收总则](#7-交付计划与验收总则)

---

## 1. 版本背景与全流程审计基准

HStudy v3.3.0 完成了 Material 3 体系化改版与积分审批闭环强化，经 Redmi（Android 17 / HyperOS，Compact 竖屏断点）环境真机签核，此前确定的习惯撤销修复及第 1、2、5、6、8、9、10 项视觉与生命周期标准均已通过。

为了确保 v3.3.1 的绝对稳定性与一致性，本期对 HStudy 的整条业务流程进行了无死角端到端复审：
- **登录角色切换**：家长门禁与角色偏好隔离；
- **打卡与申请链路**：每日任务打卡 $\to$ 家长布置任务 $\to$ 21天习惯挑战 $\to$ 每日学习目标 $\to$ 错题艾宾浩斯复习/举一反三 $\to$ 番茄钟专注；
- **积分消费与激励链路**：奖励商城兑换 $\to$ 心愿存钱罐存入与达成 $\to$ 宠物喂养/玩耍/场景购买 $\to$ 每日必须任务未完成 PENALTY 自动惩罚扣分；
- **审批与撤销闭环**：家长单条通过/拒绝/批量通过 $\to$ 撤销打卡（CANCELLED/REVERSAL） $\to$ 积分明细时间轴呈现 $\to$ 学情报表与学习树。

全流程逐一核对《积分规则与审批逻辑说明.md》**五条不变量**（打卡不计分、一业务一生效申请、审批幂等、撤销对称、账本 append-only）与 **Ruling 25**（存储边界：copy-table 字符串不进 DB 写入与 bizKey 比较，落库字面量保持 canonical 中文原样）。

---

## 2. 缺陷与隐患总览矩阵 (D-01 至 D-11，含 P0 / P1 / P2 分级)

| 编号 | 定级 | 归属模块 | 缺陷与风险简述 | 修复策略概述 |
| :---: | :---: | :--- | :--- | :--- |
| **D-01** | **P0** | 错题本 (`ui/mistake`) | 窄屏多层嵌套导致错题列表压瘪为 0dp，复习操作不可触达 | 窄屏改用单一顶层 `LazyColumn`，面板与筛选栏作前置 Item |
| **D-02** | **P0** | 任务库 (`data/local`, `AppGraph`) | 覆盖升级冷启动盲插导致预设任务成对重复，可刷双倍积分 | Room `MIGRATION_12_13` 去重 + `AppGraph` 首启表空才播种 |
| **D-08** | **P0** | 心愿目标 (`data/repository`, `ui/wish`) | 存入积分错用 `ADJUSTMENT` 且删除心愿时已存积分蒸发 | 改用 `REWARD_EXCHANGE`，删除目标时退回积分，保护儿童资产 |
| **D-03** | **P1** | 任务打卡 (`ui/child`, `data/local`) | 任务卡分值标签为静态“需审批”，审批拒绝后仍显示对勾 | 联动当日审批流，动态呈现 ⏳待审/✅入账/⛔拒绝/↩️撤销 |
| **D-05~07**| **P1** | 番茄钟 (`ui/focus`, `platform`) | 计时状态仅内存态，退出/息屏/被杀丢失且不结算，无常亮 | 起始时间戳持久化 + 跨进程自动结算 + 平台 KeepScreenOn 注入 |
| **D-09** | **P1** | 首页激励 (`ui/child`) | 里程碑/挑战/优秀表现奖励先发分后置标志位且无防连击 | 引入 `isClaiming` 防重标志位，调整写入原子顺序 |
| **D-10** | **P1** | 家长审批 (`ui/parent`, `PointRepo`) | 家长端审批历史缺乏撤回操作，误通过后无法闭环冲抵 | 历史 `APPROVED` 项增设“撤回审批”入口，调用 `undoCheckIn` |
| **D-04** | **P2** | 家长首页 (`ui/parent`) | 底部横向快捷条在手机上长滚体验差，平板大屏利用低 | 手机端改纵向两列网格，平板 Expanded 调整为 4 列规整矩阵 |
| **D-11** | **P2** | 审批系统 (`data/local`, `PointRepo`) | 长期未审的申请永久挂起，冻结待审积分虚高且无法清除 | 增设超期（30天）待审申请批量清理与提示机制 |

---

## 3. P0 级：严重交互阻断与资产/数据一致性修复

### 3.1 【P0-01 / D-01】错题本窄屏布局压瘪导致错题不可触达

- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt`
- **目标函数**：`ChildMistakeListScreen` (约第 180~240 行) 及 `MistakeListContent`
- **根因剖析 (Root Cause)**：
  在 `windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact`（手机竖屏/窄屏分支）中，根布局采用了普通的 `Column(modifier = Modifier.padding(padding))`：
  1. 第一个子项为 `StatusCardsPanel(...)`（包含 streak、28天日历、未来负荷预测、薄弱科目、复习提醒开关等 7 大卡片，自然高度超出 600dp）；
  2. 第二个子项为 `FilterBar(...)`（横向搜索与科目 FilterChip）；
  3. 第三个子项为 `MistakeListContent(...)`（内部包含独立的 `LazyColumn`）。
  
  由于外层 `Column` 不具备滚动能力，上方内容直接撑满全屏，内部 `LazyColumn` 分配到的可用测量高度≈0dp，导致错题列表完全不可见，用户向下滑动发生手势阻断，儿童无法触达错题卡片，无法完成“主动回忆（做对了+3/没记住+1）”复习打卡。

- **具体实施指令**：
  1. 重构竖屏/窄屏（Compact）分支的布局结构，**废弃顶层无滚动的 Column 嵌套内部 LazyColumn 结构**，统一改为**单一顶层 `LazyColumn`**：
     ```kotlin
     LazyColumn(
         modifier = Modifier.fillMaxSize().padding(padding),
         contentPadding = PaddingValues(bottom = 16.dp),
         verticalArrangement = Arrangement.spacedBy(8.dp)
     ) {
         // Item 1: 状态面板（所有统计卡片作为列表第一个组装项）
         item(key = "status_panel") {
             StatusCardsPanel(
                 app = app, scope = scope, snackbarHostState = snackbarHostState,
                 allMistakes = allMistakes, masteredCount = masteredCount,
                 reviewStreak = reviewStreak, claimableReviewMilestone = claimableReviewMilestone,
                 reviewCalendar = reviewCalendar, reviewForecast = reviewForecast,
                 weakestSubject = weakestSubject, dueForReview = dueForReview,
                 reviewReminderEnabled = reviewReminderEnabled, reviewReminderHour = reviewReminderHour,
                 onWeakSubjectClick = onWeakSubjectClick, onDueClick = onDueClick
             )
         }
         
         // Item 2: 过滤搜索栏
         item(key = "filter_bar") {
             FilterBar(
                 searchQuery = searchQuery, onSearchChange = { searchQuery = it },
                 selectedSubject = selectedSubject, onSelectSubject = { selectedSubject = it },
                 showOnlyUnmastered = showOnlyUnmastered, onToggleUnmastered = { showOnlyUnmastered = !showOnlyUnmastered },
                 showOnlyDue = showOnlyDue, onToggleDue = { showOnlyDue = !showOnlyDue },
                 dueCount = dueForReview.size
             )
         }
         
         // Items 3..N: 错题列表项
         if (filtered.isEmpty()) {
             item(key = "empty_state") {
                 Box(
                     modifier = Modifier.fillMaxWidth().padding(40.dp),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(strings.emptyFilter, color = MaterialTheme.colorScheme.onSurfaceVariant)
                 }
             }
         } else {
             items(filtered, key = { it.id }) { mistake ->
                 MistakeCard(
                     mistake = mistake,
                     onViewDetail = { onViewDetail(mistake.id) },
                     onToggleMastered = { onToggleMastered(mistake) },
                     onReview = { onReview(mistake) }
                 )
             }
         }
     }
     ```
  2. 平板分栏（Expanded）保持现有并排分栏（左侧可滚动面板，右侧错题列表），仅改造 Compact 分支。
  3. 保留所有参数与交互逻辑不变，移除 `MistakeListContent` 中针对单栏多余的嵌套包裹。

- **验收标准**：
  - 在 Redmi Compact 竖屏设备（无论系统字体放大与否）打开错题本，页面可平滑顺畅向下滑动；
  - 滑动越过状态卡片后，筛选栏与所有错题卡片完全可见；
  - 点击卡片上的“复习”按钮或点击卡片进入详情，能正常触发“做对了 (+3)”或“没记住 (+1)”审批申请。

---

### 3.2 【P0-02 / D-02】任务表历史重复行治理与冷启动播种幂等化

- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/AppDatabase.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/Migrations.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/TaskDao.kt`
- **目标文件 4**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/TaskRepository.kt`
- **目标文件 5**：`composeApp/src/commonMain/kotlin/com/hstudy/app/AppGraph.kt`
- **真机根因确证（Redmi 310592a0 签核实测，证据 `.verify-v330/shot-25-dup-tasks.png`）**：
  1. **完整触发链**：v2 老用户覆盖升级至 v3.3.0 $\to$ `hasOnboarded` 为 v3 新增 DataStore 键，老数据缺省即为 `false` $\to$ 升级后首次冷启动走 `AppGraph.kt:151` 的首次启动分支 $\to$ 调用 `taskRepository.initDefaultTasks()` 且**不检查表内既有任务直接盲插** 6 条预设 $\to$ 与 v2 既有同名任务并存，导致 `getAllActiveTasks()` 成对呈现。
  2. **危害评估**：重复任务 ID 不同、生成的 `bizKey` 不同（如 `TASK:1:2026-11-22` 与 `TASK:7:2026-11-22`），儿童可分别打卡领取双倍审批积分，属于严重防刷漏洞。

- **具体实施指令**：
  1. **数据库版本升级与清理迁移 (Room Migration)**：
     - `AppDatabase.kt` 数据库版本自 `12` 升至 `13`；
     - 在 `Migrations.kt` 中编写 `MIGRATION_12_13`，并在 `Migrations.ALL` 数组中追加：
     ```kotlin
     val MIGRATION_12_13 = object : Migration(12, 13) {
         override fun migrate(connection: SQLiteConnection) {
             // 历史脏数据去重：对相同 title 的活跃任务，仅保留 ID 最小的一条，其余标记为 isActive = 0
             connection.execSQL("""
                 UPDATE tasks 
                 SET isActive = 0 
                 WHERE id NOT IN (
                     SELECT MIN(id) 
                     FROM tasks 
                     WHERE isActive = 1 
                     GROUP BY title
                 ) AND isActive = 1
             """.trimIndent())
         }
     }
     ```
  2. **DAO 层增加幂等查询接口 (`TaskDao.kt`)**：
     ```kotlin
     @Query("SELECT * FROM tasks WHERE title = :title AND isActive = 1 LIMIT 1")
     suspend fun getActiveTaskByTitle(title: String): TaskEntity?
     ```
  3. **仓库层播种防护 (`TaskRepository.kt`)**：
     - 修改 `initDefaultTasks()`：插入前校验 `if (taskDao.getActiveTaskByTitle(it.title) == null) taskDao.insert(it)`，彻底实现插入幂等。
  4. **AppGraph 冷启动播种策略改为“表空才播种” (`AppGraph.kt:150-158`)**：
     ```kotlin
     appScope.launch {
         if (!userPreferences.hasOnboarded.first()) {
             // 源头治本：表内已有活跃任务/奖励则视为已有数据，不盲插播种，仅标记已引导
             taskRepository.ensureDefaultTasks()
             rewardRepository.ensureDefaultRewards()
             userPreferences.setOnboarded(true)
         } else {
             taskRepository.ensureDefaultTasks()
             rewardRepository.ensureDefaultRewards()
         }
     ```
  5. **编写迁移回归测试**：
     - 在 `composeApp/src/commonTest/kotlin/.../data/local/MigrationVerifyTest.kt` 新增用例，验证包含重复 title 的任务表在经历 `MIGRATION_12_13` 后同名活跃记录仅剩 1 条。

- **验收标准**：
  - 覆盖升级老版本 APK 后，任务列表完全无重复卡片；
  - 任务打卡只产生一条对应业务的申请单，杜绝双倍冒领。

---

### 3.3 【P0-03 / D-08】心愿存钱罐积分账本类型污染与删除目标资产蒸发修复

- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/WishGoalRepository.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/wish/ParentWishGoalManageScreen.kt`
- **根因剖析 (Root Cause)**：
  1. **账本类型错误与统计污染**：
     在 `WishGoalRepository.kt:40-48`（`depositPoints`）中，扣除积分直接调用了 `pointDao.insertPointRecord`，写入类型为 `PointRecordType.ADJUSTMENT`（父母手动调整）！存入心愿是孩子自主的消费冻结行为，却被记入家长调整账目，导致家长端积分明细与报表中的父母调整统计完全失真，且绕过了权威入口 `pointRepository.spendPoints`。
  2. **严重漏洞：删除心愿导致已存积分蒸发**：
     在 `ParentWishGoalManageScreen.kt` 中，家长点击删除心愿调用 `wishGoalRepository.deleteGoal(goal.id)`，DAO 直接执行物理 `DELETE FROM wish_goals`。若该心愿孩子此前已存入积分（如 `savedPoints = 200`），**删除时系统未产生任何积分退还流水**，导致孩子努力攒下的 200 分凭空蒸发！严重违背“资产可信与生命周期闭环”原则。

- **具体实施指令**：
  1. **存入积分入口重构 (`WishGoalRepository.kt`)**：
     - 注入 `PointRepository`（或通过 `AppGraph.pointRepository`），修改 `depositPoints`：
     - 废除直接调用 `pointDao.insertPointRecord` 与 `ADJUSTMENT` 类型；
     - 改为调用权威入口：
       ```kotlin
       pointRepository.spendPoints(
           points = amount, 
           note = "存入心愿存钱罐：${goal.name}"
       )
       ```
       记入 `PointRecordType.REWARD_EXCHANGE`，账本类型准确且可审计。
  2. **心愿目标删除增加资产原路退还闭环 (`WishGoalRepository.kt` & `ParentWishGoalManageScreen.kt`)**：
     - 修改 `deleteGoal(id: Long)` 逻辑：
       ```kotlin
       suspend fun deleteGoal(id: Long) {
           val goal = wishGoalDao.getGoalById(id) ?: return
           // 若心愿仍在进行中且已有已存积分，必须原路退还给孩子可用余额！
           if (goal.isActive && goal.savedPoints > 0) {
               pointRepository.adjustPoints(
                   points = goal.savedPoints,
                   note = "心愿删除退回已存积分：${goal.name}"
               )
           }
           wishGoalDao.delete(id)
       }
       ```
  3. **家长端删除确认弹窗提示已存积分退还**：
     - 在 `ParentWishGoalManageScreen.kt` 删除弹窗中：若 `goal.savedPoints > 0`，显式提示“该心愿已存入 ${goal.savedPoints} 积分，删除后将自动退还至孩子积分余额”。
  4. **编写单元测试**：
     - 新增测试 `depositPoints_usesRewardExchangeType` 与 `deleteGoal_withSavedPoints_refundsToTotalPoints`，断言总积分在存入扣除与删除退回后净额代数和守恒（不变量 5）。

- **验收标准**：
  - 存入心愿的积分在明细中准确标识为“兑换消耗”；
  - 删除存有积分的心愿后，总积分立即自动加回已存数值，明细出现“心愿删除退回已存积分”流水，零资金损耗。

- **工程师复核补充（2026-11-22，对码核验通过，随本节一并实施）**：
  1. 根因两条均属实：`WishGoalRepository.kt:47` 存入确写 `ADJUSTMENT`（对照商店消耗 `PointRepository.kt:328` 的 `REWARD_EXCHANGE`，同"消耗"两种口径）；`WishGoalRepository.kt:26` `deleteGoal` 确为裸删零退款；
  2. **存储边界红线（Ruling 25）**：本节示例代码中 `note = "存入心愿存钱罐：${goal.name}"` 与 `"心愿删除退回已存积分：${goal.name}"` 为**硬编码中文字面量直进 DB 写入**——英文用户明细将见中文，且该串不在文案表内、现有 `i18n_storage_boundary.py` 闸门抓不到。实施时 **note 不得落文案**：改为仅存业务标识（如 `wishGoalId`/纯 ASCII 键），展示文案由 UI 层按 `type + goal.name` 派生（与 `ChildPointsScreen` 现有 `[未完成扣分]` 标签派生模式同型）；
  3. 新增回归断言：`depositPoints`/`deleteGoal` 写入的 `note` 字段必须通过存储边界闸门脚本的 DB 字面量检查（可将该检查扩为对 `point_records.note` 列的通用断言）。

---

## 4. P1 级：业务状态真实性、系统防刷与全生命周期可靠性

### 4.1 【P1-01 / D-03】任务打卡卡片动态审批状态真实映射与反悔重打卡

- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointApprovalDao.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt`
- **目标文件 4**：`composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/TaskListStrings.kt`
- **根因剖析 (Root Cause)**：
  当前 `ChildTaskListScreen.kt` 仅通过 `completedIds`（来自 `task_completions` 表）判断任务是否完成（`isCompleted = task.id in completedIds`）。
  打卡后卡片固定展示静态文案 `打卡预得 +N 分（需审批）`。
  当家长在后台审核通过（`APPROVED`）、拒绝（`REJECTED`），或者该打卡被撤销（`CANCELLED`/`REVOKED`）时：
  - `task_completions` 表中的记录不会因拒绝而删除；
  - 任务卡片依然呈现绿色打卡状态，并继续显示“需审批”；
  - 儿童端完全感知不到“家长已同意”、“家长已拒绝”或“积分已到账”，给儿童造成严重的误解与心理落差。

- **具体实施指令**：
  1. **数据层提供当天任务审批状态映射流**：
     - 在 `PointApprovalDao.kt` 增加：
       ```kotlin
       @Query("SELECT * FROM point_approvals WHERE date = :date AND taskId IS NOT NULL")
       fun getTaskApprovalsForDate(date: String): Flow<List<PointApprovalEntity>>
       ```
     - 在 `PointRepository.kt` 暴露 `getTaskApprovalsForDate(date: String): Flow<Map<Long, PointApprovalEntity>>`，以 `taskId` 建立映射字典。
  2. **扩展 `TaskListStrings.kt` 动态状态文案**：
     - 增加多状态文案定义：
       - `approvalPendingBadge`: "⏳ 待家长审核 (+{0}分)"
       - `approvalApprovedBadge`: "✅ 积分已到账 (+{0}分)"
       - `approvalRejectedBadge`: "⛔ 家长已拒绝 (未计分)"
       - `approvalCancelledBadge`: "↩️ 打卡已取消"
       - `recheckInButton`: "重新打卡"
  3. **UI 侧 `ChildTaskCard` 状态绑定与视觉重塑**：
     - 根据任务在当天的 `PointApprovalEntity.status` 渲染：
       - **未打卡**：原有未打卡样式，分值显示 `预得 +N 分`；
       - **`PENDING`**：卡片呈 `secondaryContainer`，分值显示 `⏳ 待家长审核 (+N分)`；
       - **`APPROVED`**：卡片呈 `secondaryContainer`，分值高亮显示 `✅ 积分已到账 (+N分)`；
       - **`REJECTED`**：卡片切为 `errorContainer`，分值显示 `⛔ 家长已拒绝`；右侧圆圈打卡按钮重置为可点击，允许点击重新打卡并覆盖说明；
       - **`CANCELLED`/`REVOKED`**：卡片恢复为未完成样式。

- **验收标准**：
  - 任务打卡后卡片立即显示 `⏳ 待家长审核`；
  - 家长审批通过后实时刷新为 `✅ 积分已到账`；
  - 家长拒绝后卡片明确变为红色告警态并标明 `⛔ 家长已拒绝`，绝不继续显示“需审批”假象。

---

### 4.2 【P1-02 / D-05+D-06+D-07】番茄钟全流程时间戳持久化与屏幕常亮综合架构

- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildPomodoroScreen.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/common/PomodoroTimer.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/preferences/UserPreferences.kt`
- **目标文件 4**：`composeApp/src/commonMain/kotlin/com/hstudy/app/platform/PlatformKeepScreenOn.kt` (新建 expect/actual)
- **根因剖析 (Root Cause)**：
  1. `PomodoroTimer.kt` 中的 `endTimeMillis`、`remainingSeconds` 与 `state` 纯粹是 Compose `remember` 内存态，离开页面或按返回键全部销毁归零；
  2. 操作系统后台杀进程（Process Death）后，已跑时长全部丢失且不产生任何结算；
  3. 缺乏屏幕常亮控制，设备在 30 秒至 1 分钟后自动息屏，强行打断儿童专注。

- **具体实施指令 (三点合并设计)**：
  1. **首选项持久化锚点 (`UserPreferences.kt`)**：
     - 新增持久化 Keys：
       ```kotlin
       val POMODORO_ACTIVE_START_TIME = longPreferencesKey("pomodoro_active_start_time") // 绝对起始时间戳 (Epoch ms)
       val POMODORO_ACTIVE_DURATION_MIN = intPreferencesKey("pomodoro_active_duration_min") // 设定总时长(分钟)
       val POMODORO_ACTIVE_TARGET = stringPreferencesKey("pomodoro_active_target") // 专注任务名
       val POMODORO_ACTIVE_STATE = stringPreferencesKey("pomodoro_active_state") // IDLE, RUNNING, PAUSED
       ```
  2. **跨导航与跨进程恢复/结算机制 (`ChildPomodoroScreen.kt` & `PomodoroTimer.kt`)**：
     - **进入页面恢复检查**：
       - 若 `POMODORO_ACTIVE_STATE == "RUNNING"`：
         - 计算真实流逝时间：$\Delta t = (\text{Clock.System.now().toEpochMilliseconds()} - \text{startTime}) / 1000$；
         - 若 $\Delta t < \text{totalSeconds}$：恢复计时器，剩余秒数设为 $\text{totalSeconds} - \Delta t$，继续倒计时；
         - 若 $\Delta t \ge \text{totalSeconds}$（即后台期间时长已自然走完）：判定为完成！自动触发 `onComplete(targetDuration)` 结算，并清理持久化状态。
     - **异常被杀后保底结算**：
       - 若进入页面发现计时被打断且已流逝时间 $\ge 5 \text{ 分钟}$，弹出提示“检测到上次专注了 X 分钟，已为你登记专注记录”，保证儿童努力不浪费。
  3. **跨平台屏幕常亮 (`PlatformKeepScreenOn.kt`)**：
     - 定义跨平台 Composable：
       ```kotlin
       @Composable
       expect fun KeepScreenOn(enabled: Boolean)
       ```
     - **Android 实现** (`androidMain`)：通过 `DisposableEffect(enabled)` 控制 `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` 与 `clearFlags`。
     - **iOS 实现** (`iosMain`)：通过 `DisposableEffect(enabled)` 控制 `UIApplication.sharedApplication.idleTimerDisabled = enabled`。
     - 在 `PomodoroTimer.kt` 中接线：`KeepScreenOn(enabled = state == PomodoroState.RUNNING)`。

- **验收标准**：
  - 番茄钟开始计时后手机保持常亮；暂停/退出后自动恢复息屏策略；
  - 计时中按返回键退出页面再进，倒计时无缝继续；
  - 计时期间杀进程，25 分钟后打开 App 自动结算该次专注并生成待审积分。

---

### 4.3 【P1-03 / D-09】系统自动奖励点击防连击与原子标记防刷防护

- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildHomeScreen.kt`
- **目标函数**：`ChildHomeScreen` 中优秀表现奖励（行 433）、连续打卡里程碑（行 569）、每周挑战（行 605）的 `Button.onClick`
- **根因剖析 (Root Cause)**：
  在三处系统奖励领取的实现中：
  ```kotlin
  // 缺陷代码模式：先加分，后记录已领状态，且按钮无防重状态控制
  app.pointRepository.adjustPoints(bonus, note)
  app.userPreferences.claimStreakMilestone(days)
  ```
  儿童在真机上连续快速连击（如 1 秒内点击 3 次）或者在弱网/线程挂起间隙，会导致进入多个协程并发调用 `adjustPoints`，造成同一里程碑被重复发放多次系统积分，破坏防刷边界。

- **具体实施指令**：
  1. 在 `ChildHomeScreen` 引入局部协程防重状态：
     ```kotlin
     var isClaimingBonus by remember { mutableStateOf(false) }
     ```
  2. 调整三处领奖逻辑顺序，**先标记已领状态，再执行入账加分**，并用 `isClaimingBonus` 保护：
     ```kotlin
     Button(
         onClick = {
             if (isClaimingBonus) return@Button
             isClaimingBonus = true
             scope.launch {
                 try {
                     // 1. 先置位，阻断并发与后续点击
                     app.userPreferences.setDailyExcellentBonusDate(today)
                     // 2. 写入账本
                     app.pointRepository.adjustPoints(2, CanonicalChildHomeText.EXCELLENT_BONUS_NOTE)
                     com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                     snackbarHostState.showSnackbar(strings.excellentBonusMessage)
                 } finally {
                     isClaimingBonus = false
                 }
             }
         },
         enabled = !isClaimingBonus
     )
     ```
  3. 对连续打卡里程碑和每周挑战执行相同防重与先标记后发分改造。

- **验收标准**：
  - 对三个领奖按钮无论如何疯狂狂点连击，积分账本严格仅产生 1 条加分记录。

---

### 4.4 【P1-04 / D-10】家长端已审批记录撤回审批 (Revoke) 闭环补齐

- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentApprovalScreen.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/ParentApprovalStrings.kt`
- **根因剖析 (Root Cause)**：
  目前家长在审批通过后，若发现儿童打卡内容有假（如上传了无关照片），家长端历史列表中只有只读展示，**没有任何“撤回审批”入口**。家长唯一的补救手段是前往积分调整页面盲扣积分，导致审批单与扣分流水割裂。

- **具体实施指令**：
  1. 在 `ParentApprovalStrings.kt` 增加撤回文案：
     - `revokeApprovalButton`: "撤回通过"
     - `revokeConfirmTitle`: "确认撤回审批？"
     - `revokeConfirmMessage`: "撤回后将扣回已发放的 {0} 积分，并将申请状态置为已撤销。"
  2. 在 `ParentApprovalScreen.kt` 的历史卡片中：
     - 当 `approval.status == ApprovalStatus.APPROVED` 时，右侧操作区提供 `TextButton`：“撤回通过”；
     - 点击弹出二次确认对话框，确认后调用已有的：
       ```kotlin
       val clawedBack = app.pointRepository.undoCheckIn(
           bizKey = approval.bizKey, 
           taskId = approval.taskId, 
           date = approval.date
       )
       ```
     - 自动追加 `REVERSAL` 冲抵负分记录，并将状态转为 `REVOKED`，实现家长端的主动纠错闭环。

- **验收标准**：
  - 家长在历史列表中点击撤回某条已通过任务，系统严格扣回对应积分，列表徽章变为 `🔄 已撤销 (已扣回)`。

---

## 5. P2 级：界面交互与多端自适应优化

### 5.1 【P2-01 / D-04】家长首页功能控制台自适应响应式重塑

- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentHomeScreen.kt`
- **目标函数**：`ParentHomeScreen` (约第 303~325 行 `stripItems` 渲染块)
- **根因剖析 (Root Cause)**：
  当前底部“高级与设置”7 个功能卡片置于单行横滚 `Row` 中（总宽逾 1300dp）。在手机竖屏（宽 360-400dp）上必须经历 3~4 次大幅横划才能触达末尾项；而在平板 Expanded 宽屏下留白失衡。

- **具体实施指令**：
  1. 彻底移除 `stripItems` 的 `horizontalScroll` 单行长滑结构。
  2. 根据 `windowSizeClass.widthSizeClass` 进行响应式分支：
     - **Compact 窄屏 (手机端)**：改为与上方一致的**纵向双列卡片**（`stripItems.chunked(2)`，随主页面垂直滚动，无手势冲突）；
     - **Expanded 宽屏 (平板端)**：改为 **4 列规整网格**（`stripItems.chunked(4)`），与上方 Pulse 宫格协同。

- **验收标准**：
  - 手机上底部高级设置直观展示为整洁的双列卡片，随页面整体自然纵向滚动；
  - 平板横屏呈现美观的 4 列栅格排布。

---

### 5.2 【P2-02 / D-11】超期滞留待审申请清理机制与冻结分释放

- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/dao/PointApprovalDao.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentApprovalScreen.kt`
- **根因剖析 (Root Cause)**：
  若家长长期未处理打卡申请（如超过 30 天未打开审批页），这些申请将永久处于 `PENDING` 状态，导致孩子端首页与明细页中的“⏳ 待家长审核 +M 分”虚高滞留，无法消除。

- **具体实施指令**：
  1. 在 `PointApprovalDao.kt` 增加清理接口：
     ```kotlin
     @Query("UPDATE point_approvals SET status = 'CANCELLED', reviewedAt = :now WHERE status = 'PENDING' AND requestedAt < :threshold")
     suspend fun expireOldPendingApprovals(threshold: Long, now: Long): Int
     ```
  2. 在 `ParentApprovalScreen.kt` 待审看板卡片中，若存在超过 30 天的待审项，提示“存在超期未审批申请”，并提供“一键清理过期申请”按钮，将超期单置为 `CANCELLED`，释放虚高挂起积分。

- **验收标准**：
  - 点击清理后，超期 30 天的待审申请转为已取消，孩子端待审分值准确扣减复位。

---

## 6. 待办验收项执行规程 (Verification Checklist)

### 6.1 PENALTY 跨日自动扣分真机验证
- **验证目的**：验证规则 16（每日必须任务未完成扣分）在真实跨午夜场景下的正确性。
- **操作规程**：
  1. 家长端配置一个“每日必须”任务（`isMandatory = true`）；
  2. 孩子端当天**不打卡**该任务；
  3. 将测试设备系统时间手动拨快 1 天（如 11-22 拨至 11-23），重启 HStudy App 打开孩子首页；
  4. 检查点：
     - 可用积分余额准确扣减该任务分值；
     - 积分明细出现最新负分记录，标有红色 `[未完成扣分]` 标签，记录类型确认为 `PENALTY`；
     - 再次重启 App，确认不会重复扣分（`lastMandatoryPenaltyDate` 幂等）。

### 6.2 iOS 真机/模拟器视觉与交互签核
- **验证目的**：确保 KMP 共享 UI 在 iOS 平台无渲染畸变与平台手势冲突。
- **操作规程**：
  1. 运行 iOS 模拟器（iPhone 16 / iOS 18）及 Xcode 真机构建；
  2. 检查点：
     - 顶部与底部 SafeArea 避让正常；
     - 错题本单一 `LazyColumn` 在 iOS 弹性滚动阻尼下顺畅无跳变；
     - 番茄钟常亮控制：计时状态下 `UIApplication.sharedApplication.idleTimerDisabled == true`，退出后恢复；
     - 深色模式切换无对比度反冲。

### 6.3 GA4 DebugView 埋点事件全流核对
- **验证目的**：确保 M3 重构后新增与迁移的操作埋点在数据分析看板上 100% 准确上报。
- **操作规程**：
  1. 连接 adb 执行 `adb shell setprop debug.firebase.analytics.app com.hstudy.app`；
  2. 打开 Firebase / GA4 控制台的 **DebugView** 实时监视面板；
  3. 依次执行打卡、撤销、番茄钟完成、家长单条/批量审批，确认所有对应事件在 DebugView 实时瀑布流中可见。

---

## 7. 交付计划与验收总则

### 7.1 阶段实施路线图

```
[阶段 1: P0 阻塞消除与资产/数据治理]
  ├── 1.1 ChildMistakeListScreen 窄屏重构为单一 LazyColumn (D-01)
  ├── 1.2 Room MIGRATION_12_13 任务去重迁移与 AppGraph 表空才播种 (D-02)
  └── 1.3 WishGoalRepository 存入改 spendPoints 与删除目标积分退还 (D-08)
          ↓
[阶段 2: P1 业务真实性、防刷与可靠性]
  ├── 2.1 任务打卡卡片状态联动 PointApprovalEntity 审批态 (D-03)
  ├── 2.2 番茄钟时间戳锚定持久化与跨平台 KeepScreenOn (D-05/06/07)
  ├── 2.3 首页系统自动奖励防连击与先置位防刷 (D-09)
  └── 2.4 家长审批历史增加“撤回审批”操作闭环 (D-10)
          ↓
[阶段 3: P2 响应式重塑、超期清理与真机核对]
  ├── 3.1 家长首页底部快捷条改为手机双列/平板 4 列 (D-04)
  ├── 3.2 待审申请 30 天超期清理机制 (D-11)
  └── 3.3 执行跨日自动扣分真机验证、iOS 签核与 GA4 校验
```

### 7.2 最终发布交付标准
1. **测试套件标准**：
   - `:composeApp:compileDebugKotlin` 编译无任何新增错误与警告；
   - 新增针对任务去重、心愿退款、防连击等单元测试，总单测数升至 $\ge 258$ 且 100% 通过；
   - 现有的 252 个历史单元测试全量回归通过。
2. **真机业务验收标准**：
   - 错题本窄屏无论如何滑动均可自由到达错题卡片并打卡；
   - 任务列表无任何重复卡片，杜绝冒领；
   - 心愿存钱罐删除时已存积分 100% 原路退回可用余额；
   - 任务卡清晰反映“待审/到账/拒绝”真实审批态；
   - 番茄钟退出/息屏/被杀不丢进度，屏幕常亮可靠；
   - 首页系统奖励狂点不刷分，家长端支持撤回审批。
