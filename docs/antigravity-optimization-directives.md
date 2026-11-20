# HStudy 界面美化与积分审批强化优化执行指令

> **文档性质**：工程重构与优化执行规范说明书（Directives Specification）  
> **面向对象**：执行重构与功能交付的开发工程师  
> **基准架构**：Kotlin Multiplatform + Compose Multiplatform (Android / iOS)  
> **设计基准**：Google Material Design 3 (Material 3) 规范与儿童/家长双角色交互体系  
> **执行约束**：本指令详细指定文件路径、类与函数名、修改逻辑、测试用例与验收标准，请严格依照优先级与步骤实施，不得破坏既有业务生命周期。

---

## 目录

1. [执行概览与架构基准](#1-执行概览与架构基准)
2. [第一部分：积分体系与审批链路深度审计及强化 (Part B)](#2-第一部分积分体系与审批链路深度审计及强化-part-b)
   - [2.1 积分生命周期五条不变量审计与状态对照](#21-积分生命周期五条不变量审计与状态对照)
   - [2.2 规则 1-17 现状落实审计总表](#22-规则-1-17-现状落实审计总表)
   - [2.3 差距项与强化指令清单 (P0 / P1 / P2)](#23-差距项与强化指令清单-p0--p1--p2)
   - [2.4 核心测试套件编写规范](#24-核心测试套件编写规范)
3. [第二部分：界面美化与 Google Material 3 升级规范 (Part A)](#3-第二部分界面美化与-google-material-3-升级规范-part-a)
   - [3.1 UI 现状审计与痛点诊断](#31-ui-现状审计与痛点诊断)
   - [3.2 Material 3 体系化设计基准](#32-material-3-体系化设计基准)
   - [3.3 五大核心页面重设计指令与验收标准](#33-五大核心页面重设计指令与验收标准)
     - [3.3.1 孩子首页 (ChildHomeScreen)](#331-孩子首页-childhomescreen)
     - [3.3.2 任务打卡 (ChildTaskListScreen)](#332-任务打卡-childtasklistscreen)
     - [3.3.3 家长审批 (ParentApprovalScreen)](#333-家长审批-parentapprovalscreen)
     - [3.3.4 积分明细 (ChildPointsScreen & ParentPointsScreen)](#334-积分明细-childpointsscreen--parentpointsscreen)
     - [3.3.5 家长首页 (ParentHomeScreen)](#335-家长首页-parenthomescreen)
   - [3.4 其余页面统一升级清单](#34-其余页面统一升级清单)
4. [执行计划与验收总则](#4-执行计划与验收总则)

---

## 1. 执行概览与架构基准

本工程 `hstudy-1` 是一款基于 Kotlin Multiplatform (KMP) 和 Compose Multiplatform 打造的儿童学习与成长激励 App，支持 Android 与 iOS 平台。

项目包含两大核心逻辑主线：
1. **积分与审批生命周期**：保障儿童正面激励的可信度，遵循《积分规则与审批逻辑说明.md》约定的“打卡不计分、审批才入账、撤销必对称、账本只追加”原则。
2. **界面交互与视觉体验**：构建兼具“儿童端温馨童趣、清晰反馈”与“家长端高效掌控、结构明晰”的现代 Google Material 3 交互体系。

执行工程师须按以下分工顺序开展工作：首先闭环积分与审批漏洞（消除数据风险与测试真空），随后系统性重塑 Theme 与核心页面（提升视觉质感与操作体验）。

---

## 2. 第一部分：积分体系与审批链路深度审计及强化 (Part B)

### 2.1 积分生命周期五条不变量审计与状态对照

| 不变量 | 规范定义 | 现状审计结论 | 差距与隐患分析 |
| :--- | :--- | :--- | :--- |
| **不变量 1：打卡不计分** | 打卡仅写入完成流水 + `PENDING` 申请，绝对禁止写入 `point_records`。 | **部分落实** ⚠️ | 任务打卡、习惯挑战、布置任务已落实；但**错题复习**与**举一反三思考**直接传 `points = 0` 未产生申请；**番茄钟专注**调用了 `PointApprovalRepository.submitApproval` 而非统一走 `PointRepository.checkIn`。 |
| **不变量 2：一业务一生效申请** | 使用 `bizKey`（如 `TASK:12:2026-09-06`）强约束，同一业务仅能有一条生效状态（`PENDING` 或 `APPROVED`）。 | **部分落实** ⚠️ | 任务、习惯、目标均包含 `bizKey`；但**番茄钟专注完成**未注入 `bizKey`，多次专注会产生无业务主键的裸记录，无法幂等去重。 |
| **不变量 3：审批幂等** | 仅 `PENDING` 状态可转 `APPROVED` 并加分；重复审批不得多次入账。 | **已落实** ✅ | `PointRepository.approveAndAward` 严格校验 `approval.status == PENDING` 及 `getActiveAwardedByBizKey`，防止重复审批。 |
| **不变量 4：撤销对称** | 未审批撤销转 `CANCELLED`（0分变动）；已审批撤销追加 `REVERSAL` 负向冲抵记录。 | **部分落实** ⚠️ | 任务打卡和每日目标支持对称撤销；但**布置任务（ASSIGN）**与**习惯打卡（HABIT）**在 UI 层未提供撤销操作入口，缺乏完整撤销链路。 |
| **不变量 5：账本 append-only** | 积分流水只增不改不删，当前可用余额 = $\sum(\text{point\_records.points})$。 | **已落实** ✅ | 底层 DAO 已移除物理删除接口，历史修正统一使用 `REVERSAL` 反向记录冲销。 |

---

### 2.2 规则 1-17 现状落实审计总表

对照《积分规则与审批逻辑说明.md》第一章规则总表，逐条核对代码现状：

| 规则 # | 行为类别 | 约定分值 | 是否需审批 | 当前代码实现路径 | 落实状态 | 缺陷/差距诊断 |
| :---: | :--- | :--- | :---: | :--- | :---: | :--- |
| **1** | 任务打卡 (每日任务) | 基础分 × 难度系数 | ✅ 是 | `ChildTaskListScreen.kt` $\to$ `PointRepository.checkInForTask()` | **合规** | 遵循规范，打卡记 PENDING，审批加分，撤销回退。 |
| **2** | 习惯打卡 (21天挑战) | `challenge.dailyPoints` | ✅ 是 | `ChildHabitChallengeScreen.kt` $\to$ `pointRepository.checkIn(bizKey="HABIT:...")` | **缺陷** | 仅有打卡，缺少误触撤销（undoCheckIn）交互入口。 |
| **3** | 完成家长布置任务 | `assignment.points` | ✅ 是 | `ChildHomeScreen.kt` $\to$ `pointRepository.checkIn(bizKey="ASSIGN:...")` | **缺陷** | 完成后直接固化显示对勾，缺少打卡反悔/撤销机制。 |
| **4** | 完成单个每日目标 | +2 | ✅ 是 | `ChildDailyTargetScreen.kt` $\to$ `pointRepository.checkIn(bizKey="TARGET:...")` | **合规** | 勾选走审批，取消勾选调用 `undoCheckIn`。 |
| **5** | 每日目标全部达成 | +10 (每日限1次) | ✅ 是 | `ChildDailyTargetScreen.kt` $\to$ `pointRepository.checkIn(bizKey="TARGET_ALL:...")` | **合规** | 达成生成申请，未全达成自动调用 `undoCheckIn` 撤回。 |
| **6** | 错题复习 | 做对+3 / 仅复习+1 | ✅ 是 | `ChildMistakeDetailScreen.kt:266`、`ChildMistakeListScreen.kt:135` | **严重不符** | 代码硬编码 `points = 0`，且未调用 `checkIn` 产生审批流。 |
| **7** | 举一反三思考 | +3 | ✅ 是 | `MistakeRepository.kt:saveVariation()` | **严重不符** | 仅更改错题笔记与标志位，完全遗漏积分申请逻辑。 |
| **8** | 专注(番茄钟)完成 | 按时长 (每5分钟+1) | ✅ 是 | `ChildPomodoroScreen.kt:184` $\to$ `pointApprovalRepository.submitApproval()` | **缺陷** | 绕过 `PointRepository.checkIn`，缺少 `bizKey`，存在防刷漏洞。 |
| **9** | 连续打卡里程碑 | 按档位阶梯奖励 | ❌ 否 | `ChildHomeScreen.kt:493` $\to$ `pointRepository.adjustPoints()` | **合规** | 系统自动发放，立即计入流水。 |
| **10** | 每周挑战(打卡5天) | +5 (v2.88 调整) | ❌ 否 | `ChildHomeScreen.kt:529` $\to$ `pointRepository.adjustPoints(5, ...)` | **合规** | 系统自动发放，立即计入流水。 |
| **11** | 连续复习里程碑 | 按档位阶梯奖励 | ❌ 否 | `ChildMistakeListScreen.kt:280` $\to$ `pointRepository.adjustPoints()` | **合规** | 系统自动发放，达成即入账。 |
| **12** | 今日学习表现优秀 | +2 (v2.88 调整) | ❌ 否 | `ChildHomeScreen.kt:332` $\to$ `pointRepository.adjustPoints(2, ...)` | **合规** | 综合得分达标后系统自动发放。 |
| **13** | 每日幸运抽奖 | 随机奖励 | ❌ 否 | `ChildLuckyDrawScreen.kt:119` $\to$ `pointRepository.adjustPoints()` | **合规** | 随机分值直接入账，每天限一次。 |
| **14** | 家长手动调整 | 任意分值(含负数) | ❌ 否 | `ParentPointsScreen.kt` $\to$ `pointRepository.adjustPoints()` | **合规** | 家长特权，操作即审批生效。 |
| **15** | 完成证明审核 +5 | 已删除 (v2.88) | — | 历史记录保留 | **合规** | 证明审核与积分脱钩，仅留存审计证据。 |
| **16** | 每日必须任务未完成 | 扣 `effectivePoints` | ❌ 否 | `MandatoryPenaltyHelper.kt:81` $\to$ `pointRepository.adjustPoints(-deducted)` | **待优化** | 类型被归入 `ADJUSTMENT`，缺少显式 `PENALTY` 类型标识。 |
| **17** | 兑换奖励/宠物消耗 | 负数扣减 | ❌ 否 | `ChildRewardStoreScreen.kt`、`ChildPetScreen.kt` $\to$ `pointRepository.spendPoints()` | **合规** | 实时扣减，写入 `REWARD_EXCHANGE` 记录。 |

---

### 2.3 差距项与强化指令清单 (P0 / P1 / P2)

#### 【P0-01】错题复习与举一反三思考积分审批链路补齐 (规则 6、7)
- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/MistakeRepository.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt`
- **问题现状**：当前错题主动回忆与间隔复习时均传入 `points = 0`，且 `saveVariation` 仅保存文本，导致规则 6（做对 +3，仅复习 +1）与规则 7（举一反三 +3）处于失控与缺失状态。
- **具体修改指令**：
  1. `MistakeRepository` 构造函数注入 `pointRepository: PointRepository`（或通过 `AppGraph` 统一调用）。
  2. 修改 `reviewMistake(mistakeId: Long, isCorrect: Boolean, note: String)`：
     - 计算预扣分值：若 `isCorrect == true` 则 `points = 3`，否则 `points = 1`。
     - 写入错题复习记录 `mistakeDao.insertReview(...)`。
     - 生成审批申请：调用 `pointRepository.checkIn(bizKey = "MISTAKE_REV:$mistakeId:$today", actionType = "MISTAKE_REVIEW", description = "错题复习：$note", points = points, date = today)`。
  3. 修改 `saveVariation(mistake: MistakeEntity, note: String)`：
     - 保存笔记后，生成举一反三审批申请：调用 `pointRepository.checkIn(bizKey = "VARIATION:${mistake.id}:$today", actionType = "VARIATION", description = "举一反三思考：${mistake.title.take(15)}", points = 3, date = today)`。
  4. 更新 `ChildMistakeDetailScreen.kt` 中回忆按钮的点击回调：
     - 点击“做对了”：调用 `reviewMistake(mistakeId, isCorrect = true, note = "主动回忆-做对")`，Snackbar 提示 `+3 分待家长审核`。
     - 点击“没记住”：调用 `reviewMistake(mistakeId, isCorrect = false, note = "主动回忆-温习")`，Snackbar 提示 `+1 分待家长审核`。
  5. 更新 `ChildMistakeListScreen.kt` 中的快捷复习按钮，接入审批流水。

#### 【P0-02】番茄钟专注积分审批纳入统一防刷与 bizKey 约束 (规则 8)
- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/focus/ChildPomodoroScreen.kt`
- **问题现状**：第 184 行直接调用 `app.pointApprovalRepository.submitApproval("FOCUS_COMPLETE", ...)`，绕过了 `PointRepository` 的生命周期管控，缺少 `bizKey`，如果用户重进页面或重放将产生重复待审单。
- **具体修改指令**：
  1. 打开 `ChildPomodoroScreen.kt`，定位至 `onComplete` 回调（约第 177-190 行）。
  2. 获取 `recordFocusSession` 返回的 `sessionId: Long`：
     ```kotlin
     val sessionId = app.focusRepository.recordFocusSession(durationMinutes = minutes, taskTitle = focusTarget, points = 0)
     ```
  3. 将直接调用 `pointApprovalRepository` 替换为权威入口：
     ```kotlin
     if (points > 0) {
         app.pointRepository.checkIn(
             bizKey = "FOCUS:$sessionId:$today",
             actionType = "FOCUS_COMPLETE",
             description = CanonicalFocusText.recordDescription(focusTarget, minutes),
             points = points,
             date = today
         )
     }
     ```

#### 【P0-03】建立积分生命周期五条不变量核心单元测试套件
- **目标文件 (新建)**：`composeApp/src/commonTest/kotlin/com/hstudy/app/data/repository/PointRepositoryInvariantTest.kt`
- **问题现状**：`commonTest` 目录下仅有国际化复制测试和算法工具测试，积分核心生命周期（打卡、幂等、撤销、冲抵）完全处于零自动化测试保护状态。
- **具体修改指令**：
  1. 新建测试文件，使用 Mock 或 In-Memory Fake DAO 组装 `PointRepository`。
  2. 覆盖五条不变量的 7 个核心测试用例（详见下文 [2.4 节](#24-核心测试套件编写规范)）。

#### 【P1-01】补齐布置任务与习惯打卡的撤销打卡 (Undo) 闭环 (规则 2、3)
- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildHomeScreen.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/habit/ChildHabitChallengeScreen.kt`
- **问题现状**：家长布置任务和 21 天习惯打卡，在打卡成功后直接变为“已完成”状态，没有任何取消或撤销按钮。若儿童误触，不仅打卡天数失真，且生成的待审单无法由儿童端撤回。
- **具体修改指令**：
  1. 在 `ChildHomeScreen.kt`（约 592 行）：
     - 当 `a.isCompleted == true` 时，右侧不再单纯展示 `Text("✅")`，增加 `TextButton` 撤销动作：
     ```kotlin
     TextButton(onClick = {
         scope.launch {
             val assignDate = PointRepository.getTodayString()
             app.assignmentRepository.uncompleteAssignment(a.id)
             val clawedBack = app.pointRepository.undoCheckIn(bizKey = "ASSIGN:${a.id}:$assignDate", date = assignDate)
             snackbarHostState.showSnackbar(if (clawedBack > 0) "已撤销完成，扣回 ${clawedBack} 积分" else "已撤销完成申请")
         }
     }) {
         Text("撤销", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
     }
     ```
  2. 在 `ChildHabitChallengeScreen.kt`：
     - 在 `ChallengeCard` 中增加对今日已打卡状态的撤销支持，调用 `app.habitRepository.undoCheckInChallenge(challenge)` 并同步执行 `app.pointRepository.undoCheckIn(bizKey = "HABIT:${challenge.id}:$today", date = today)`。

#### 【P1-02】纠正家长审批历史中 CANCELLED 与 REVOKED 的误导性展示
- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentApprovalScreen.kt`
- **问题现状**：
  - 第 140 行将非 `APPROVED` 的状态一律显示为图标 `❌`；
  - 第 156-157 行将 `CANCELLED`（孩子主动取消）和 `REVOKED`（已撤销扣回）的分值一律用红色标为 `+N`，导致家长误以为是“审批被拒绝”或“积分已增加”，造成严重误解。
- **具体修改指令**：
  1. 修改状态图标逻辑：
     ```kotlin
     val statusIcon = when (approval.status) {
         ApprovalStatus.APPROVED -> "✅"
         ApprovalStatus.REJECTED -> "⛔"
         ApprovalStatus.CANCELLED -> "↩️"
         ApprovalStatus.REVOKED -> "🔄"
         ApprovalStatus.PENDING -> "⏳"
     }
     ```
  2. 修改分值渲染逻辑：
     ```kotlin
     val (scoreText, scoreColor) = when (approval.status) {
         ApprovalStatus.APPROVED -> "+${approval.requestedPoints}" to MaterialTheme.colorScheme.primary
         ApprovalStatus.REVOKED -> "-${approval.requestedPoints} (已扣回)" to MaterialTheme.colorScheme.error
         ApprovalStatus.CANCELLED -> "0 (打卡已取消)" to MaterialTheme.colorScheme.outline
         ApprovalStatus.REJECTED -> "0 (已拒绝)" to MaterialTheme.colorScheme.error
         ApprovalStatus.PENDING -> "+${approval.requestedPoints} (待审)" to MaterialTheme.colorScheme.tertiary
     }
     ```

#### 【P2-01】系统自动惩罚扣分类型规范化 (规则 16)
- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/local/entity/PointRecordEntity.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/PointRepository.kt`
- **目标文件 3**：`composeApp/src/commonMain/kotlin/com/hstudy/app/data/repository/MandatoryPenaltyHelper.kt`
- **问题现状**：未完成必须任务的扣分当前调用 `adjustPoints(-deducted, "每日必须任务未完成自动扣分")`，类型为 `ADJUSTMENT`（父母手动调整），导致数据报表中无法区分“系统扣分”与“家长奖惩”。
- **具体修改指令**：
  1. 在 `PointRecordType` 枚举中增加 `PENALTY` 类型：
     ```kotlin
     enum class PointRecordType {
         TASK_COMPLETE, BONUS, ADJUSTMENT, REWARD_EXCHANGE, REVERSAL, PENALTY
     }
     ```
  2. 在 `PointRepository` 中增加专用方法：
     ```kotlin
     suspend fun applyPenalty(points: Int, note: String) {
         pointDao.insertPointRecord(
             PointRecordEntity(
                 points = -points,
                 type = PointRecordType.PENALTY,
                 note = note,
                 weekStart = getWeekStartTimestamp()
             )
         )
     }
     ```
  3. `MandatoryPenaltyHelper.kt:81` 改为调用 `app.pointRepository.applyPenalty(deducted, "每日必须任务未完成自动扣分")`。

---

### 2.4 核心测试套件编写规范

在 `composeApp/src/commonTest/kotlin/com/hstudy/app/data/repository/PointRepositoryInvariantTest.kt` 中必须实现的 7 个标准测试用例：

```kotlin
class PointRepositoryInvariantTest {

    // 1. 不变量 1：打卡绝不直接写入积分流水
    @Test
    fun checkIn_doesNotWritePointRecord()

    // 2. 不变量 2：重复打卡复用同一申请，绝不生成两条 PENDING
    @Test
    fun checkIn_duplicateCall_reusesExistingPendingApproval()

    // 3. 不变量 3：审批幂等性验证，重复批准只加一次积分
    @Test
    fun approveAndAward_calledTwice_onlyCreditsOnce()

    // 4. 不变量 4：未审批撤销打卡，申请置 CANCELLED 且积分净额为 0
    @Test
    fun undoCheckIn_whenPending_setsCancelledAndZeroPointsClawedBack()

    // 5. 不变量 4：已审批撤销打卡，产生 REVERSAL 记录且积分净额归零
    @Test
    fun undoCheckIn_whenApproved_appendsReversalRecordAndClawsBackPoints()

    // 6. 不变量 2 & 4：撤销后重新打卡，能重新生成一条新 PENDING，审批后依然只入账一次
    @Test
    fun checkIn_afterUndoApproved_createsNewPendingAndAwardsCorrectly()

    // 7. 不变量 5：账本 append-only，总积分始终严格等于所有流水记录的代数和
    @Test
    fun totalPoints_equalsSumOfAllPointRecords()
}
```

---

## 3. 第二部分：界面美化与 Google Material 3 升级规范 (Part A)

### 3.1 UI 现状审计与痛点诊断

对当前 `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/` 的视觉现状审计，发现以下全局问题：
1. **配色混乱与对比度缺失**：
   - `Color.kt` 中定义了 `Orange40`、`WarmBg (0xFFFFF8F0)` 等，但在页面中频繁出现硬编码的渐变（如 `ChildHomeScreen` 的 `listOf(Color(0xFFFFE8CC), Color(0xFFFFD8A8), ...)`、`ParentHomeScreen` 的 `listOf(Color(0xFFDCEBFF), ...)`），脱离了 Material 3 语义色彩系统。
   - 缺少 M3 标准的 `surfaceContainerLow`、`surfaceContainer`、`surfaceContainerHigh` 等容器层级，导致页面层次感扁平或杂乱。
2. **圆角与形变规范分裂**：
   - `Theme.kt` 中配置了 `HStudyShapes`（Medium 14dp, Large 20dp），但页面组件中充斥着随意写死的 `RoundedCornerShape(3.dp)`、`RoundedCornerShape(6.dp)`、`RoundedCornerShape(20.dp)`，缺乏系统性约束。
3. **字号与排版缺乏节奏**：
   - `Type.kt` 存在标准样式，但在多个核心组件内随意覆盖 `fontSize = 24.sp`、`fontSize = 12.sp`，行高与段落间距未与 Material 3 Type Scale 对齐。
4. **动效滥用与视觉疲劳**：
   - 孩子端首页常驻全屏 `breathTransition`（1.0f 到 1.02f 的持续缩放），造成页面文字边缘锯齿抖动，分散儿童注意力，违背 M3 动效的“功能性与克制性”原则。
5. **布局架构重心失衡**：
   - 家长首页单列平铺多达 15 个菜单卡片，手机端滑动超过 3 屏，核心的“审批待办”与“孩子今日进度”无法在一屏内聚焦呈现。

---

### 3.2 Material 3 体系化设计基准

#### 3.2.1 语义色彩系统规范 (Semantic Color Palette)
在 `ui/theme/Color.kt` 与 `Theme.kt` 中严格建立 M3 映射：

- **Primary (活力暖橙 - 孩子端主色)**：
  - Primary: `Color(0xFFEF6C00)`
  - OnPrimary: `Color(0xFFFFFFFF)`
  - PrimaryContainer: `Color(0xFFFFDCC2)`
  - OnPrimaryContainer: `Color(0xFF2E1500)`
- **Secondary (清风竹绿 - 习惯与完成态)**：
  - Secondary: `Color(0xFF2E6C38)`
  - OnSecondary: `Color(0xFFFFFFFF)`
  - SecondaryContainer: `Color(0xFFB1F5B3)`
  - OnSecondaryContainer: `Color(0xFF002107)`
- **Tertiary (星空青蓝 - 专注与探索)**：
  - Tertiary: `Color(0xFF00658F)`
  - TertiaryContainer: `Color(0xFFC7E7FF)`
  - OnTertiaryContainer: `Color(0xFF001E2E)`
- **Neutral Surface Containers (温暖米白底色，消除纯灰冷感)**：
  - Surface: `Color(0xFFFFF8F5)`
  - SurfaceContainerLowest: `Color(0xFFFFFFFF)`
  - SurfaceContainerLow: `Color(0xFFF9F2EF)`
  - SurfaceContainer: `Color(0xFFF3ECE9)`
  - SurfaceContainerHigh: `Color(0xFFEDE6E3)`
  - SurfaceContainerHighest: `Color(0xFFE7E1DD)`
  - OnSurface: `Color(0xFF1D1B1A)`
  - OnSurfaceVariant: `Color(0xFF52443C)`
  - Outline: `Color(0xFF85736B)`
  - OutlineVariant: `Color(0xFFD7C2B9)`

#### 3.2.2 形状与圆角等级规范 (Shape Scale)
严格使用 `MaterialTheme.shapes`，禁止在业务页面内随意使用裸数值 `RoundedCornerShape(X.dp)`：
- **None**: 0dp (全面屏背景铺满)
- **Extra Small**: 4dp (标签/小徽章 Chip)
- **Small**: 8dp (内嵌组件/输入框/进度条轨道)
- **Medium**: 12dp (对话框/下拉菜单/小卡片)
- **Large**: 16dp (标准业务卡片/底栏面板)
- **Extra Large**: 24dp (Hero 焦点卡片/大按钮/模态底板)
- **Full**: CircleShape (头像/圆形打卡触发按钮)

#### 3.2.3 间距与栅格标准 (Spacing & Grid)
- 遵循 4dp 基础网格系统：
  - 组件内微间距：`4.dp` / `8.dp`
  - 列表项垂直间距：`12.dp`
  - 屏幕外边距 (Margin)：Compact 屏 `16.dp`，Expanded 平板屏 `24.dp`
  - 页面大段落间隔：`20.dp` / `24.dp`
- 触控目标保障：儿童端主按钮与打卡按钮触控面积 $\ge 56 \times 56\text{ dp}$，常规交互控件 $\ge 48 \times 48\text{ dp}$。

---

### 3.3 五大核心页面重设计指令与验收标准

#### 3.3.1 孩子首页 (ChildHomeScreen)
- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildHomeScreen.kt`
- **重设计定位**：儿童积极情绪点火台——温暖、聚焦、即时反馈。
- **重构指令**：
  1. **移除全屏呼吸缩放动效**：删除 `breathTransition` 及 `scale(breathScale)`，改为静止但富有光泽感的 Material 3 Hero Card。
  2. **重塑 Hero Card (积分与成长树)**：
     - 使用 `CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)`。
     - 渐变色限定在卡片装饰层，改为自 `PrimaryContainer.copy(alpha = 0.6f)` 至 `Surface` 的微妙渐变，禁止硬编码十六进制色值。
     - 左侧 LearningTree Canvas 增加微妙的等级光环，右侧积分采用 `MaterialTheme.typography.displayMedium` 加粗显示。
  3. **申请状态悬浮胶囊置顶 (提升可见度)**：
     - 将原本位于第 405 行底部的“积分申请状态卡片”重构为精简的“待审进度胶囊（Approval Status Capsule）”，上移至 Hero Card 正下方。
     - 当 `pendingApprovalCount > 0` 时，呈现 `AssistChip` 样式的通知卡片：`⏳ ${pendingApprovalCount} 个打卡正在等家长盖章 (+${pendingApprovalPoints}分)`，点击直接弹出详情 Sheet。
  4. **今日打卡进度聚合**：
     - 使用 M3 `LinearProgressIndicator`，圆角设为 `8.dp`，增加动态弹簧过渡动效 (`spring(dampingRatio = 0.75f)`)。
  5. **快捷任务行动卡片组 (Call-to-Action)**：
     - 简化下方冗长内容，突出“今日必做”与“开始专注”两个大尺寸快捷 Action Card。
- **验收标准**：
  - 页面滚动流畅，无视觉抖动；
  - 积分申请状态一眼可见，儿童清楚感知“已提交，待审批”；
  - 视觉色调温暖明亮，各项元素均使用 `MaterialTheme.colorScheme` 与 `MaterialTheme.shapes`。

#### 3.3.2 任务打卡 (ChildTaskListScreen)
- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildTaskListScreen.kt`
- **重设计定位**：仪式感打卡工作台——轻快、明确、防误触。
- **重构指令**：
  1. **顶部状态栏升级**：
     - 顶栏 FilterChip 与总进度数字分开两行布局：首行展示分类 FilterChip（支持带图标选态），次行展示当前类别的打卡完成比率与进度条。
  2. **任务卡片重构 (`ChildTaskCard`)**：
     - 采用 `ElevatedCard`，未打卡时容器色为 `surface`，描边为 `outlineVariant`；打卡后变为 `secondaryContainer`。
     - 彻底移除卡片左侧生硬的 6dp 色条，改为卡片自身的背景色调与左侧图标颜色联动。
     - 打卡按钮：增大为 $56 \times 56\text{ dp}$ 圆形按钮。未打卡为 `surfaceContainerHigh` 伴随任务类型 Emoji；打卡瞬间触发 M3 弹性动画 (`scale` 快速微弹 + 换为 `Icons.Filled.Check`)。
  3. **分值与状态明确化**：
     - 分值文案区分：“打卡预得 +N 分（需审批）”，避免误导儿童认为立刻加分。
  4. **卡片内撤销操作 (Undo)**：
     - 打卡完成后，卡片右下角显式出现“撤销打卡”微按钮（带回退箭头小图标），点击触发二度确认对话框或弹出 5 秒内 Undo Snackbar。
- **验收标准**：
  - 点击打卡按钮触感明确，弹性动画利落不拖沓；
  - 已完成与未完成卡片具有鲜明的视觉层级区分；
  - 误触能直接在卡片上完成回退，数据与界面完全同步。

#### 3.3.3 家长审批 (ParentApprovalScreen)
- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentApprovalScreen.kt`
- **重设计定位**：家长决策中枢——高效、清晰、闭环可溯源。
- **重构指令**：
  1. **顶部统计与一键批量通过栏**：
     - 在 Tab 切换栏下方，增加待审资产看板卡片：
       - 展示“待审总笔数：N 笔”与“待发放总积分：+M 分”。
       - 提供“全部通过 (+M分)”显著的主题填充按钮 (`Button`)，点击弹出批量确认对话框，调用 `pointRepository.approveAllAndAward()`。
  2. **待审卡片信息结构重构 (`ApprovalCard`)**：
     - 背景色使用 `surfaceContainerLow`，圆角 `16.dp`。
     - 头部：业务类型标签（如：每日任务、专注番茄钟、错题复习、习惯挑战），用不同颜色的 M3 `SuggestionChip` 呈现。
     - 主体：打卡内容、儿童提交时间、申请分值（加粗大号字体）。
     - 底部操作：`FilledTonalButton` (通过) 与 `OutlinedButton` (拒绝)，按 6:4 比例横向排列，操作间距 `12.dp`。
  3. **历史记录分段与状态重塑**：
     - 历史 Tab 增加二级筛选：[全部]、[已通过]、[已拒绝]、[已撤销]。
     - 彻底替换原有 ❌ 混淆逻辑，采用清晰的 M3 徽章：
       - `APPROVED`: 绿色容器 + “已计入 +N 分”；
       - `REVOKED`: 橙色容器 + “已撤销 (扣回 N 分)”；
       - `CANCELLED`: 灰色容器 + “打卡已取消 (未计分)”；
       - `REJECTED`: 红色容器 + “已拒绝 (不计分)”。
- **验收标准**：
  - 待审列表支持单条快速审批与一键批量处理；
  - 历史状态文案与颜色严格符合不变量定义，杜绝状态误读。

#### 3.3.4 积分明细 (ChildPointsScreen & ParentPointsScreen)
- **目标文件 1**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/child/ChildPointsScreen.kt`
- **目标文件 2**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentPointsScreen.kt`
- **重设计定位**：透明信用账本——权威、结构化、分列清晰。
- **重构指令**：
  1. **资产看板重构 (Header Dashboard)**：
     - 顶部卡片采用 `surfaceContainerHigh` 容器。
     - 上半部：展示大字号可用余额（`displayLarge`），副标注明“当前可用积分”。
     - 下半部（分列展示）：
       - 待审批积分：`⏳ 待家长审核 +M 分`（背景使用 `tertiaryContainer`，醒目标注“审核后计入”）。
       - 累计获得总分：`🌟 累计获得 N 分`。
  2. **流水记录时间轴分组 (Grouped Timeline)**：
     - 将原本平铺的 `LazyColumn` 按时间分组：`今天`、`昨天`、`本周更早`、`历史记录`，增加 Sticky Header 或分组标题。
  3. **明细卡片视觉升级 (`PointHistoryItem`)**：
     - 统一左右结构：左侧为圆角矩形类型图标徽章，中间为业务描述与精确时间，右侧为大号变动数值。
     - 数值色彩语义：
       - 获得积分（`TASK_COMPLETE`, `BONUS`）：显示 `+N`，主色绿色 `Color(0xFF2E6C38)`；
       - 兑换消耗（`REWARD_EXCHANGE`）：显示 `-N`，主色橙色 `Color(0xFFEF6C00)`；
       - 撤销扣回（`REVERSAL`）：显示 `-N`，并附带小标签 `[打卡撤销]`，使用警示色；
       - 系统惩罚（`PENALTY`）：显示 `-N`，附带 `[未完成扣分]` 红色标签。
- **验收标准**：
  - 可用余额与待审分值严格分列，无概念混淆；
  - 流水记录分类与收支清晰，具备完整的审计透明度。

#### 3.3.5 家长首页 (ParentHomeScreen)
- **目标文件**：`composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentHomeScreen.kt`
- **重设计定位**：家长驾驶舱——分层管理、重点突出、告别超长列表。
- **重构指令**：
  1. **重构整体信息架构为三层架构**：
     - **第 1 层：紧急待办处理区 (Urgent Action Bar)**：
       - 当有待审批积分或待审核证明时，置顶显示 M3 警示卡片：`待办提醒：${pendingApprovalCount} 条积分申请待批，${pendingProofCount} 张证明待审`，右侧带“立即处理”按钮，一键直达审批页。
     - **第 2 层：今日学情概览仪表盘 (Daily Pulse Card)**：
       - 彻底删除硬编码的浅蓝渐变 `listOf(Color(0xFFDCEBFF), ...)`。
       - 使用 `ElevatedCard` 配合 `surfaceContainer` 容器。
       - 采用 $2 \times 2$ 核心指标宫格：[今日打卡率]、[连续天数]、[专注时长]、[待复习错题]。
     - **第 3 层：结构化功能控制台 (Categorized Control Matrix)**：
       - 严禁将 15 个功能菜单单列纵向排列！按业务场景划分为三大折叠面板或分组网格：
         1. **日常任务与激励**（网格 $2 \times 2$）：任务管理、布置任务、积分管理、奖励商城；
         2. **学情追踪与辅导**（网格 $2 \times 2$）：错题分析、成长报告、辅导建议、作息管理；
         3. **高级与设置**（横向快捷条）：完成证明、任务模板、数据大屏、系统设置。
  2. **卡片视觉标准**：
     - 菜单小卡片使用 `OutlinedCard`，图标尺寸统一为 `28.dp`，采用主色调浅色背景圆形包裹，排版整洁干练。
- **验收标准**：
  - 手机端首屏即可掌握今日孩子学习核心进展与待办事务；
  - 告别漫无目的的超长滚动，高频功能触达步数不超过 1 步。

---

### 3.4 其余页面统一升级清单

其余 15+ 页面按以下统一升级规程执行重构：

| 模块 / 页面 | 文件路径 | 升级重点要求 |
| :--- | :--- | :--- |
| **错题模块** | `ui/mistake/ChildMistakeListScreen.kt`<br>`ui/mistake/ChildMistakeDetailScreen.kt`<br>`ui/mistake/ParentMistakeReviewScreen.kt` | 1. 接入规则 6/7 审批申请反馈提示；<br>2. 掌握度进度条改用 M3 自带动画与圆角；<br>3. 错题卡片统一使用 `surfaceContainer` 质感。 |
| **作息计划模块** | `ui/schedule/ChildScheduleTimelineScreen.kt`<br>`ui/schedule/ParentScheduleManageScreen.kt` | 1. 时间轴节律点使用 M3 Primary 与 Outline 层次；<br>2. 当前进行中任务采用发光脉冲边框替代死板色块。 |
| **奖励与心愿** | `ui/child/ChildRewardStoreScreen.kt`<br>`ui/wish/ChildWishGoalScreen.kt`<br>`ui/wish/ParentWishGoalManageScreen.kt` | 1. 兑换商品卡片采用网格布局，按钮显式提示“消耗 N 积分”；<br>2. 心愿达成进度条采用 M3 宽轨指示器。 |
| **专注训练模块** | `ui/focus/ChildPomodoroScreen.kt`<br>`ui/focus/ChildFocusTrainingScreen.kt` | 1. 计时表盘居中，字体采用 `displayLarge` 等宽数字字体；<br>2. 专注完成弹窗明确标示“+N 积分待家长确认”。 |
| **宠物成长模块** | `ui/pet/ChildPetScreen.kt` | 1. 宠物场景与属性条（饱食度、心情）使用 M3 药丸形状进度胶囊；<br>2. 喂食消耗与积分余额同步绑定。 |
| **报表分析模块** | `ui/report/GrowthReportScreen.kt`<br>`ui/parent/ParentDashboardScreen.kt` | 1. 图表配色接入 M3 Chart Palette；<br>2. 区分已结算积分趋势与待审积分预估。 |

---

## 4. 执行计划与验收总则

### 4.1 阶段规划与依赖关系

```
[阶段 1: 积分与审批漏洞修复 (P0)] 
  ├── 1.1 MistakeRepository 接入规则 6/7 审批流水
  ├── 1.2 Pomodoro 接入统一 checkIn 与 bizKey
  └── 1.3 编写 PointRepositoryInvariantTest 验证 5 条不变量
          ↓
[阶段 2: 撤销机制与状态展示修正 (P1 & P2)]
  ├── 2.1 补齐布置任务与习惯打卡撤销闭环
  ├── 2.2 修正 ParentApprovalScreen 历史状态图标与分值
  └── 2.3 规范系统自动惩罚 PENALTY 类型
          ↓
[阶段 3: Material 3 基础主题与核心 5 页面重塑 (Part A)]
  ├── 3.1 Color / Type / Shape 体系化对齐 M3
  ├── 3.2 孩子首页 (ChildHomeScreen) 重构
  ├── 3.3 任务打卡 (ChildTaskListScreen) 重构
  ├── 3.4 家长审批 (ParentApprovalScreen) 重构
  ├── 3.5 积分明细 (ChildPointsScreen / ParentPointsScreen) 重构
  └── 3.6 家长首页 (ParentHomeScreen) 重构
          ↓
[阶段 4: 其余页面统一升级与全量验证]
  └── 全模块回归测试 + 多端屏幕尺寸（Compact / Expanded）适配验证
```

### 4.2 最终交付验收标准
1. **测试通过标准**：
   - `:composeApp:compileDebugKotlin` 编译无任何错误与警告；
   - 新增的 `PointRepositoryInvariantTest` 所有 7 个不变量测试用例 100% 通过；
   - 历史单元测试套件全部回归通过。
2. **业务逻辑验收标准**：
   - 孩子端任何主动学习操作（任务打卡、习惯、布置任务、目标、错题、专注），在审批前总积分余额保持绝对静止；
   - 家长审批通过后，积分准确入账，且无法二次审批多加积分；
   - 撤销已审批任务时，积分严格扣回，历史记录追加 `REVERSAL` 冲抵流水；
   - 撤销未审批任务时，待审单置为 `CANCELLED`，不残留任何脏数据。
3. **视觉设计验收标准**：
   - 全局无硬编码十六进制色值，完全基于 `MaterialTheme.colorScheme`；
   - 消除不适的屏幕持续缩放动效；
   - 家长首页实现三层结构，首屏展现核心数据与审批待办。
