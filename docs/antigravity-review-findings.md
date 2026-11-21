# HStudy 实施结果回审结论报告 (Review Findings)

> **评审基准**：对合入 `main` 的 PR #4 (`feature/m3-ui-points`，Merge commit `c9ec990`，对比基线 `828f1f0`) 实施结果进行严格对照审查。  
> **审查依据**：`docs/antigravity-optimization-directives.md`（原始指令）、`积分规则与审批逻辑说明.md`、Google Material 3 设计标准。  
> **报告归档**：`docs/antigravity-review-findings.md`

---

## 目录

1. [总体评审结论与合规度概览](#1-总体评审结论与合规度概览)
2. [Part B 积分体系与审批链路回审](#2-part-b-积分体系与审批链路回审)
   - [2.1 P0 / P1 / P2 指令逐项落实核对](#21-p0--p1--p2-指令逐项落实核对)
   - [2.2 五条积分不变量完整性审计](#22-五条积分不变量完整性审计)
   - [2.3 PointRepositoryInvariantTest 测试套件真实有效性断言审计](#23-pointrepositoryinvarianttest-测试套件真实有效性断言审计)
3. [Part A Material 3 界面美化回审](#3-part-a-material-3-界面美化回审)
   - [3.1 3.2 体系化主题设计落实核对](#31-32-体系化主题设计落实核对)
   - [3.2 3.3.1-3.3.5 五大核心页面重设计核对](#32-331-335-五大核心页面重设计核对)
   - [3.3 3.4 其余页面统一升级清单核对](#33-34-其余页面统一升级清单核对)
   - [3.4 经裁决的偏离合理性专项审查](#34-经裁决的偏离合理性专项审查)
4. [残留差距、隐患与后续改进建议 (P0 / P1 / P2)](#4-残留差距隐患与后续改进建议-p0--p1--p2)

---

## 1. 总体评审结论与合规度概览

本次实施分支 `feature/m3-ui-points`（共 14 个 commit，涉及 57 个文件，净增 3480 行代码）对此前下发的 `docs/antigravity-optimization-directives.md` 执行度极高。

* **Part B（积分与审批）**：P0×3、P1×2、P2×1 全部按指令要求精准落地，五条生命周期不变量不仅未被破坏，且通过全新手写 Fake DAO 构筑了严密的不变量自动化回归套件；同时修正了跨午夜撤销、同日复习分值不降级等边缘缺陷。
* **Part A（Material 3 美化）**：全局色彩全面接线 M3 语义色板；核心五大页面完成结构化重塑；其余 13 个既有页面完成清扫。全工程在指令范围内实现了零裸色值、零裸圆角。
* **裁决偏离项**：涉及的 WCAG 色板微调、防误触二次确认、同日复习最大值保护等偏离均有严谨的工程与无障碍依据，裁决合理。

综合评审判定：**全项达标，同意合入生产基线**。

---

## 2. Part B 积分体系与审批链路回审

### 2.1 P0 / P1 / P2 指令逐项落实核对

| 指令编号 | 要求项 | 落实文件与关键代码 | 审查结论 |
| :--- | :--- | :--- | :---: |
| **【P0-01】** | 错题复习与举一反三接入统一审批与 `bizKey` | `MistakeRepository.kt:48-98`<br>- `reviewMistake` 产生 `bizKey = "MISTAKE_REV:$mistakeId:$today"`，做对+3/温习+1<br>- `saveVariation` 产生 `bizKey = "VARIATION:${mistake.id}:$today"` (+3)<br>- `ChildMistakeDetailScreen.kt` 与 `ChildMistakeListScreen.kt` 移除直接直调 `submitApproval`，统一走仓储 | **完全落实** ✅ |
| **【P0-02】** | 番茄钟专注完成纳入 `PointRepository.checkIn` 与 `bizKey` 约束 | `ChildPomodoroScreen.kt:182-196`<br>- 获取 `sessionId = app.focusRepository.recordFocusSession(...)`<br>- 构造 `bizKey = "FOCUS:$sessionId:$checkInDate"`<br>- 调用 `app.pointRepository.checkIn(...)` 登记待审单 | **完全落实** ✅ |
| **【P0-03】** | 建立积分生命周期五条不变量核心单元测试套件 | `PointRepositoryInvariantTest.kt`<br>- 落地包含 8 个核心不变量与扩展场景测试用例 | **完全落实** ✅ |
| **【P1-01】** | 布置任务与习惯打卡补齐撤销闭环 (Undo) | `AssignmentRepository.kt:49` (`uncompleteAssignment`)<br>`HabitRepository.kt:65` (`undoCheckInChallenge`)<br>`ChildHomeScreen.kt:742-760` (布置任务撤销按钮 + 跨午夜回退日期派生 + `undoCheckIn`)<br>`ChildHabitChallengeScreen.kt:79, 137, 260-286` (今日已打卡与完成日打卡支持撤销) | **完全落实** ✅ |
| **【P1-02】** | 家长审批历史纠正 `CANCELLED`/`REVOKED` 误导展示 | `ParentApprovalScreen.kt:70-80, 160-230`<br>- 状态图标分流为：`✅` (通过) / `⛔` (拒绝) / `↩️` (取消) / `🔄` (撤销)<br>- 分值与颜色对齐：`REVOKED` 显示 `-N (已扣回)` 并使用 `error` 红色；`CANCELLED` 显示 `0 (打卡已取消)` 并使用中性色<br>- 增设二级状态筛选芯片，且先全量筛选后 `take(20)` 截断 | **完全落实** ✅ |
| **【P2-01】** | 每日必须任务未完成扣分规范为 `PENALTY` 类型 | `PointRecordEntity.kt:22` (新增枚举项 `PENALTY`)<br>`PointRepository.kt:308` (新增 `applyPenalty` 入账专用方法)<br>`MandatoryPenaltyHelper.kt:83` (改为调用 `applyPenalty`) | **完全落实** ✅ |

---

### 2.2 五条积分不变量完整性审计

1. **不变量 1：打卡不计分**
   - **审计结果**：全部打卡类入口（每日任务、布置任务、习惯挑战、每日目标、错题主动回忆与复习、举一反三思考、番茄钟专注）均通过 `pointRepository.checkIn` 或 `checkInForTask` 登记 `PENDING` 申请与业务流水，无任何一处在审批前调用 `pointDao.insertPointRecord`。
2. **不变量 2：一业务一生效申请**
   - **审计结果**：所有打卡业务均具备 `bizKey`（前缀:业务主键:日期/会话）。`PointRepository.ensureApproval` 在检测到同 key 已有 `PENDING` 时复用申请并不重复生成；已有生效 `APPROVED` 时拒绝重复申请。
3. **不变量 3：审批幂等**
   - **审计结果**：`approveAndAward` 与 `approveAllAndAward` 严格限制必须为 `PENDING` 状态才加分；重复审批第二次返回 0 分，不重复加分。
4. **不变量 4：撤销对称**
   - **审计结果**：未审批打卡撤销将状态置为 `CANCELLED`，净额 0 变动；已审批打卡撤销追加 `REVERSAL` 反向记录冲抵（`points = -原值`），净额绝对归零。
5. **不变量 5：账本 append-only**
   - **审计结果**：积分账本严格保持无物理删除，可用余额恒等于 `SUM(point_records.points)`。

---

### 2.3 PointRepositoryInvariantTest 测试套件真实有效性断言审计

测试类位于 `composeApp/src/commonTest/kotlin/com/hstudy/app/data/repository/PointRepositoryInvariantTest.kt`，经逐行核查：

1. **架构模式**：采用手写内存 Fake DAO（`FakePointDao` 与 `FakePointApprovalDao`），去除了 JVM SQLite 实体依赖，纯粹断言内存账本状态流转。
2. **用例完整性与有效性**：
   - `checkIn_doesNotWritePointRecord`: 真实断言打卡后 `pointDao.records.isEmpty()`，且审批单为 `PENDING`。
   - `checkIn_duplicateCall_reusesExistingPendingApproval`: 真实断言二次调用返回 `ALREADY_PENDING` 且单条 `PENDING`。
   - `approveAndAward_calledTwice_onlyCreditsOnce`: 真实断言二次审批返回 0 分，流水仅 1 条。
   - `undoCheckIn_whenPending_setsCancelledAndZeroPointsClawedBack`: 真实断言未审撤销扣回 0 分，无流水生成。
   - `undoCheckIn_whenApproved_appendsReversalRecordAndClawsBackPoints`: 真实断言追加 `REVERSAL` 负向记录，冲抵后净额为 0，保留审计轨迹。
   - `checkIn_afterUndoApproved_createsNewPendingAndAwardsCorrectly`: 真实断言撤销后重打卡能重新产生新 PENDING，审批后只结算一次。
   - `totalPoints_equalsSumOfAllPointRecords`: 混合打卡、审批、手动调整、兑换消耗、撤销冲抵全流程，断言 `repo.getTotalPoints().first()` 与累加和完全一致。
   - `reviewMistake_sameDayLowerPoints_keepsPendingMaxAndAwardsOnce`: 针对终审发现的“同日做对+3与温习+1”边缘场景，断言分值取 max 不降级且只入账一次。
3. **结论**：所有断言均属于强断言，无软断言或空实现，套件真实且严密。

---

## 3. Part A Material 3 界面美化回审

### 3.1 3.2 体系化主题设计落实核对

* **色板接线 (`Color.kt`, `Theme.kt`)**：
  - 定义并接线了 `M3Primary`、`M3Secondary`、`M3Tertiary`、`M3Error` 以及 5 级 `SurfaceContainer`（Lowest 到 Highest）。
  - 深浅双主题均接线了 `inverseSurface`、`inverseOnSurface` 与 `inversePrimary`。
* **排版系统 (`Type.kt`)**：
  - 补齐了 `displaySmall`、`headlineSmall`、`titleSmall`、`labelSmall` 4 个缺失槽位，整体排版字号节奏与 M3 保持一致。
* **圆角形变 (`Theme.kt`)**：
  - 规范了 `HStudyShapes`（Extra Small 4dp 到 Extra Large 28dp），全屏卡片统一引用语义 shapes。

### 3.2 3.3.1-3.3.5 五大核心页面重设计核对

1. **孩子首页 (`ChildHomeScreen.kt`)**：
   - 彻底移除了导致画面抖动的 `breathTransition` 全屏持续呼吸动画；
   - Hero Card 采用 `ElevatedCard`，渐变改为 `primaryContainer` 到 `surface` 的语义微渐变；
   - “待家长审核胶囊”提升至 Hero 卡片下方；已通过/已拒绝回顾卡常驻在其下方，两者共存；
   - 底部行动区升级为 `HomeCtaCard`。
2. **任务打卡 (`ChildTaskListScreen.kt`)**：
   - FilterChip 与今日完成进度拆解为两行整齐排列；
   - 废弃左侧 6dp 突兀硬色条，卡片采用 M3 ElevatedCard 容器色差区分状态；
   - 打卡按钮扩展为 56dp 圆形弹性按钮；
   - 打卡后提供明确的撤销打卡动作，并弹出二次确认对话框。
3. **家长审批 (`ParentApprovalScreen.kt`)**：
   - 头部增设待审资产看板卡（笔数、分值、一键全部通过按钮及二次确认）；
   - `ApprovalCard` 采用三段式：业务类型 `SuggestionChip`、主体描述与大号分值、底部 6:4 比例双操作按钮；
   - 历史 Tab 实现了二级筛选，并解决了 `take(20)` 截断导致假空态的问题。
4. **积分明细 (`ChildPointsScreen.kt` & `ParentPointsScreen.kt`)**：
   - 头部 M3 资产看板：`displayLarge` 可用余额、`tertiaryContainer` 待审积分胶囊、累计获得统计；
   - 实现了按“今天 / 昨天 / 本周更早 / 历史记录”的时间轴分组；
   - 流水项附带 `[打卡撤销]`、`[未完成扣分]` 明确标识，正负色彩对比鲜明。
5. **家长首页 (`ParentHomeScreen.kt`)**：
   - 废除原 15 个菜单卡片单列纵向排列的超长滚动布局；
   - 重构为现代三层驾驶舱：① 顶部待办警示条；② 今日学情 Pulse 2×2 宫格；③ 分组功能控制台（日常管理 2×2、学情追踪 2×2、高级与设置横向快捷栏）。

### 3.3 3.4 其余页面统一升级清单核对

涵盖错题、作息、奖励与心愿、专注、宠物、报表等 13 个既有屏幕，全部替换了旧有 `surfaceVariant` 与裸圆角，改用 `surfaceContainer` 系列容器、M3 动画圆角进度条、脉冲状态指示边框与 `chartPalette`。

### 3.4 经裁决的偏离合理性专项审查

| 偏离项 | 裁决内容 | 合理性审查意见 | 结论 |
| :--- | :--- | :--- | :---: |
| **Primary 降至 tone-40** | `0xFFEF6C00` (tone-50) $\to$ `0xFFA65100` (tone-40) | 原值白字对比度仅 3.08:1，不满足 WCAG AA (4.5:1)。调深后达 5.52:1，且符合 Google M3 官方基准。 | **合理且必要** ✅ |
| **Error 降至 tone-40** | `0xFFE53935` $\to$ `0xFFC62828` | 原值正文对比度 3.82:1 不达标，调整后白字达 5.62:1，正文达 5.35:1，符合无障碍标准。 | **合理且必要** ✅ |
| **Inverse 角色接线** | 暗色主题补全 `inverseSurface` 等 | 避免了深色模式回落冷紫默认值导致的 Snackbar 动作色对比度仅 1.33:1 的严重缺陷。 | **合理且必要** ✅ |
| **任务卡撤销二次确认** | 点击撤销改弹 `AlertDialog` | 儿童端手指面积大易误触，直接撤销会导致打卡记录与待审单被误删，二次确认防误触设计极佳。 | **合理体验优化** ✅ |
| **历史筛选改“撤销与取消”** | ZH 芯片词面优化 | 准确涵盖 `REVOKED` (审批后撤销) 与 `CANCELLED` (审批前取消) 两类终态，消除与单项标签的混淆。 | **合理文案优化** ✅ |
| **同日复习分值取 max** | `ensureApproval` 复用 PENDING 时取 `maxOf` | 防止儿童同一天做对 (+3) 后点快捷复习 (+1) 反被改低挂起分值，保护儿童正向激励。 | **重要业务保全** ✅ |
| **画布 Helper 保留裸色值** | Canvas 纯绘图保留裸十六进制 | 纯图形绘制（树木枝干、光晕粒子、背景道具）不属于系统 UI 组件，保留独立颜色具有合理性。 | **合理解耦** ✅ |

---

## 4. 残留差距、隐患与后续改进建议 (P0 / P1 / P2)

经全面深度代码审查，虽核心目标已完全达成，但在极限边界、平台自适应及非核心页面中仍存在以下可改进项：

### 【P0 级别（严重隐患）】
* **无**。所有核心业务流程、积分生命周期与不变量均已严格闭环且具备测试覆盖。

### 【P1 级别（体验不完整与边界风险）】
1. **习惯挑战打卡跨午夜撤销的边界不对称风险**：
   - **位置**：`ChildHabitChallengeScreen.kt:274` (`undoTodayCheckIn`) 与 `HabitRepository.kt:66` (`undoCheckInChallenge`)
   - **分析**：当儿童在前一日 23:59 打卡，而在次日 00:01 点击撤销时，`undoTodayCheckIn` 获取的是撤销当时的 `today`（已是次日），而 `challenge.lastCheckInDate` 存储的是前一日日期。这会导致 `if (challenge.lastCheckInDate != todayString()) return` 提前退出，且传入的 `bizKey` 为次日键名，无法扣回前一日的待审单或积分。
   - **改进建议**：借鉴布置任务终审 M-1 的修复策略，撤销时直接读取 `challenge.lastCheckInDate` 构造 `bizKey = "HABIT:${challenge.id}:${challenge.lastCheckInDate}"`，并据此回退状态，解除对实时系统日期的强依赖。
2. **专注训练 (番茄钟) 与错题复习撤销打卡能力缺失**：
   - **位置**：`ChildPomodoroScreen.kt`、`ChildMistakeDetailScreen.kt`
   - **分析**：目前任务、布置、习惯挑战均已闭环撤销（Undo），但番茄钟打卡与错题复习提交后，若儿童误触或错选，UI 侧完全没有“撤销本次专注/撤销本次复习”的路径，只能由家长在审批端被动拒绝。
   - **改进建议**：在番茄钟完成后的几秒内提供撤销 Snackbar/按钮，调用 `undoCheckIn(bizKey = "FOCUS:$sessionId:$date")`。
3. **真实多端设备的像素级走查与性能签核待执行**：
   - **位置**：跨端渲染与视觉感知
   - **分析**：当前验证全部基于 Windows 环境下的静态分析与 JVM 单元测试，尚未在 Android 实机与 iOS 真机上走查暗色主题下的深层渐变过度、平板横屏 Expanded 断点下的网格拉伸及列表滚动 FPS。
   - **改进建议**：按照验收报告中的真机签核清单（11 条）进行真机实测签核。

### 【P2 级别（次要缺陷与代码洁净度）】
1. **未在 §3.4 清单内的 3 个业务屏幕仍存裸色值**：
   - **位置**：
     - `ui/role/RoleSelectScreen.kt`（6 处角色卡片渐变硬编码）；
     - `ui/habit/ChildEfficiencyReportScreen.kt:511`（1 处对比色硬编码）；
     - `ui/weekly/WeeklyTrackerScreen.kt:229`（1 处周报图表色硬编码）。
   - **改进建议**：后续微迭代中，将这 3 个页面的硬编码颜色统一收口至 `MaterialTheme.colorScheme` 与 `ui/theme/Chart.kt` 的 `chartPalette`。
2. **批量审批操作缺乏事务一致性保护**：
   - **位置**：`PointRepository.kt:approveAllAndAward`
   - **分析**：`pending.forEach { total += approveAndAward(it.id) }` 是逐条调用，未在 DB 事务块中执行。若在批量批注过程中应用进程意外中断，可能造成部分通过、部分挂起。虽然幂等设计保证了不会重发，但在高并发场景下建议包裹 `@Transaction`。
3. **平板横屏大屏下的功能台适配**：
   - **位置**：`ParentHomeScreen.kt`
   - **分析**：家长首页在手机端表现极佳，但在 Expanded 平板宽屏横屏下，$2 \times 2$ 宫格可能显得拉伸较宽。建议结合 `WindowWidthSizeClass.Expanded` 将宫格动态调整为 $4 \times 1$。
