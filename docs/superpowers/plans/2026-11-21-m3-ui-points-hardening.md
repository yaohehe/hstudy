# HStudy v3.3 计划 — M3 界面美化 + 积分审批薄弱环节强化（执行 Antigravity 指令）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 指令源：docs/antigravity-optimization-directives.md（Antigravity 产出，用户指定为指挥）。本计划是其**适配后的可执行版本**；冲突处以本计划+代码证据为准。

**Goal:** 落实指令 Part B（积分审批 P0/P1/P2 缺口）与 Part A（Material 3 主题体系 + 5 核心页重设计 + 其余页统一升级），全部经既有 i18n/analytics/存储边界闸门与 CI。

**Architecture:** 业务逻辑不变量优先；所有新 UI 文案进 Strings 注册表（ZH+EN 双构造器）；埋点调用点按 analytics_call_sites.py 计数保留；DB 写入字符串沿用既有 canonical 模式（Ruling 25）。

**Spec/依据:** 积分规则与审批逻辑说明.md、docs/antigravity-optimization-directives.md、docs/superpowers/specs/2026-11-07-hstudy-analytics-firebase-design.md（埋点约束）。

## Global Constraints（逐字生效）

1. **i18n**：任何新增用户可见文案 = Strings 注册表新字段（data class + ZH/EN 两个构造器）+ `scripts/i18n_template_diff.py` DISCLOSED_RESIDUALS/计数登记 + StringsCopyTest 闭集登记（SETTINGS_STATIC_DIFFERS_FIELDS 等）；禁止 UI 内联中文字面量。默认组消耗数按实测更新。
2. **analytics 闸门**：`python scripts/analytics_call_sites.py` 的 20 项计数必须保持；重写含埋点的块（如 ChildPomodoroScreen onComplete :178、ChildHomeScreen checkIn）时埋点行**原样保留**。
3. **业务不变量**：五条不变量（打卡不计分 / bizKey 一业务一生效 / 审批幂等 / 撤销对称 CANCELLED|REVERSAL / append-only 余额=SUM）不得破坏；`approveAndAward`/`undoCheckIn` 语义保持。
4. **Ruling 25**：copy-table 字符串不进 DB 写入与 bizKey 比较；审批 description/积分 note 沿用现有调用点模式（如 CanonicalFocusText.recordDescription）。
5. **环境**：JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"；GRADLE_USER_HOME="D:/gradle-home"；PYTHONIOENCODING=utf-8；本地 `python`，CI `python3`。
6. **测试基线**：当前 237/237；每波结束全绿 + `assembleDebug` + 四闸门（storage_boundary、两个 selftest、template --branch、analytics gate）exit 0。
7. **提交纪律**：分支 `feature/m3-ui-points`（自 main 828f1f0）；每波本地 commit；push/PR/CI/merge 由控制器执行，merge+tag 前需用户确认。
8. **禁止**为过编译删功能、加桩。

## 已验证事实（控制器 2026-11-21 grep 证据）

- `MistakeRepository.reviewMistake(mistakeId, points=3, note)`（:48）只写 review 流水，**无积分/审批**；UI 两处传 `points = 0`（ChildMistakeDetailScreen:266/:279）。
- `saveVariation`（:64）无任何积分逻辑。
- `ChildPomodoroScreen`:182-184 `recordFocusSession(minutes, focusTarget, 0)` 后 `pointApprovalRepository.submitApproval("FOCUS_COMPLETE", CanonicalFocusText.recordDescription(...), points)`——绕过 checkIn、无 bizKey；:178 有埋点行。
- `PointRepository.checkIn(bizKey, actionType, description, points, taskId=null, date=todayString()): CheckInResult`（:113）；`undoCheckIn(bizKey, taskId?, date): Int`（:146，返回扣回分值）。
- `PointRecordType` 无 PENALTY；`MandatoryPenaltyHelper:81` 用 `adjustPoints(-deducted, "每日必须任务未完成自动扣分")`。
- `ParentApprovalScreen:140` 非 APPROVED 一律 "❌"；:153 一律 "+N"。
- `ChildHomeScreen:147-183` breathTransition 全屏 scale。
- `AssignmentRepository.uncompleteAssignment` 存在；习惯仓库文件名待实现者定位（ui/habit 对应 repository，含 checkIn 调用点）。

## 裁决（对 Antigravity 指令的适配，执行者不得偏离）

- RA1：指令中的内联中文（撤销按钮、Snackbar、状态标签、待办横幅等）一律改为 Strings 新字段，ZH/EN 双份；指令文案作为 ZH 词面参考。
- RA2：P0-01 不改 `reviewMistake` 签名：保持 `(mistakeId, points, note)`，在方法体内新增 `pointRepository.checkIn(bizKey="MISTAKE_REV:$mistakeId:${todayString()}", actionType="MISTAKE_REVIEW", description=<canonical，见 RA5>, points, date)`；UI 两处把 `points = 0` 改为 3（做对）/1（没记住）。MistakeRepository 构造注入 PointRepository（AppGraph 装配处同步）。
- RA3：P0-02 保持 `recordFocusSession` 返回值现状——先查其签名；若无返回 id，则 bizKey 用 `"FOCUS:${todayString()}:$minutes:$focusTarget.take(24)"` 等价防刷键；埋点行不动。
- RA4：P1-01 撤销入口文案走 Strings；撤销成功/失败 Snackbar 用既有 undo 文案模式（ChildTaskListScreen 已有撤销反馈，复用其取词路径）。
- RA5：新审批 description 采用与同业务既有审批记录一致的词面（TASK/HABIT/ASSIGN 模式），保证家长端历史可读且 i18n 闸门可控（表侧登记）。
- RA6：P2-01 PENALTY：枚举加值（TypeConverter 按 name 存取，无 schema 变更、不升 Room 版本——若发现按 ordinal 存储则改为升 v13 迁移，先验证 converter）；`applyPenalty(points: Int, note: String)` 写 `-points`；helper 调用点替换，note 字面量保持原样（历史数据一致）。积分明细页 PENALTY 展示标签走 Strings。
- RA7：Part A 视觉重构**不得删改任何业务回调/审批流/埋点**；只动视觉层（颜色/形状/排版/布局结构/动效）；删除 breathTransition 与硬编码渐变改语义色；触控尺寸按指令。
- RA8：ParentApprovalScreen 历史状态徽章文案（"已计入 +N 分"等）走 Strings；emoji/图标不受 i18n 约束。
- RA9：测试套件用**手写 Fake DAO**（Room DAO 是接口）组装 PointRepository，覆盖 2.4 节 7 用例；不引入 JVM sqlite 依赖。
- RA10：版本/发布不在本计划内（完成后由用户决定是否发 v3.3.0）。

## 波次（每波一个 dispatch brief，用 task-brief 脚本按 Task 号提取）

### Task 1: P0 积分链路（指令 P0-01+P0-02+P0-03）
Files: MistakeRepository.kt（注入 PointRepository + reviewMistake/saveVariation 接 checkIn）、ChildMistakeDetailScreen.kt（points 0→3/1 + Snackbar 取词）、ChildMistakeListScreen.kt 快捷复习、ChildPomodoroScreen.kt（submitApproval→checkIn，保埋点）、AppGraph.kt 装配、新 Strings 字段（mistakeReviewPendingNote 等）、commonTest PointRepositoryInvariantTest.kt（Fake DAO 7 用例）、analytics_call_sites.py SITES 若新增/变动调用点同步。
Gates: 237+7 测试全绿；四闸门；assembleDebug。

### Task 2: P1/P2（指令 P1-01+P1-02+P2-01）
Files: ChildHomeScreen.kt（ASSIGN 撤销按钮）、habit 仓库+ChildHabitChallengeScreen.kt（HABIT 撤销）、ParentApprovalScreen.kt（状态图标/分值/徽章重构 + 历史二级筛选）、PointRecordEntity.kt/PointRepository.kt/MandatoryPenaltyHelper.kt（PENALTY）、ChildPointsScreen/ParentPointsScreen（PENALTY 展示）、Strings 新字段全套 + 闸门登记。
Gates: 全测试 + 四闸门 + analytics 计数复核。

### Task 3: M3 主题体系 + 孩子首页 + 任务打卡页
Files: ui/theme/Color.kt（指令 3.2.1 色板 + surfaceContainer 层级）、Theme.kt（shape scale 3.2.2、间距约定 3.2.3、暗色基础适配可后置）、Type.kt、ChildHomeScreen.kt（去 breath、Hero Card、审批胶囊上移、进度聚合、CTA 组）、ChildTaskListScreen.kt（卡片 ElevatedCard 状态色、56dp 打卡钮、弹性动效、卡内撤销入口与 Task 2 对齐）。
Gates: 全测试 + 四闸门（视觉改动不应动文案；若动则登记）。

### Task 4: 家长审批页 + 积分明细两页 + 家长首页三层重构
Files: ParentApprovalScreen.kt（看板卡+批量钮+卡片结构）、ChildPointsScreen.kt/ParentPointsScreen.kt（资产看板+时间分组+条目徽章）、ParentHomeScreen.kt（三层信息架构，15 菜单→分组网格）。
Gates: 同上 + analytics SITES 复核（ParentApprovalScreen 有 APPROVAL_ACTION 埋点 3 处）。

### Task 5: 其余页面统一升级 + 全量回归
Files: 指令 3.4 表 6 模块 15+ 页面（错题/作息/奖励心愿/专注/宠物/报表）按清单执行；全量四闸门 + testDebugUnitTest + assembleDebug；生成对照验收报告（指令 4.2 三条标准逐条举证）。
Gates: 全部。

## Self-Review 记录
- 覆盖：Part B P0×3/P1×2/P2×1 → Task 1-2；Part A 3.2/3.3/3.4 → Task 3-5；指令 4.2 验收 → Task 5 报告。
- 类型一致：checkIn/undoCheckIn/applyPenalty 签名与代码核对；Fake DAO 测试不依赖真库。
- 风险：habit 仓库文件名、recordFocusSession 返回类型、TypeConverter 存储方式——Task 1/2 首步先验证再改。
