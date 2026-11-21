# HStudy v3.3.1 验收报告 — 真机签核缺陷修复波（D-01~D-11，2026-11-22）

对照 `docs/antigravity-v331-directives.md` 各节验收标准与 §7 交付总则逐条举证。
执行范围 = 波次 Task 1–9（波 1 P0：D-01/D-02/D-08；波 2 P1：D-03/D-05~07/D-09/D-10；波 3 P2：D-04/D-11；收尾 Task 9）。
分支 `feature/v331-hardening`，基线 `6573b52`（v3.3.0，252 单测全绿），本波 9 个修复 commit：

| Commit | 内容 | 指令书节号 |
| :--- | :--- | :--- |
| `9903f2d` | fix(ui): 错题本窄屏改单一 LazyColumn（D-01） | §3.1 P0-01 |
| `45be645` | fix(data): 任务表去重迁移 12→13 + 播种幂等 + 表空才播种（D-02） | §3.2 P0-02 |
| `500e456` | fix(points): 心愿存入 REWARD_EXCHANGE 权威入口 + 删除原路退还 + note 业务键化（D-08） | §3.3 P0-03 |
| `bb9c4cf` | feat(ui): 任务卡按当日 bizKey 渲染真实审批态，拒绝可重打卡（D-03） | §4.1 P1-01 |
| `09a4f21` | fix(points): 首页三处系统奖励先置位后入账 + in-flight 互斥（D-09） | §4.3 P1-03 |
| `0ea99fd` | feat(parent): 审批历史 APPROVED 卡撤回审批闭环（D-10） | §4.4 P1-04 |
| `a3edb21` | feat(focus): 番茄钟时间戳锚定持久化 + 跨进程补结算 + keepScreenOn（D-05/06/07） | §4.2 P1-02 |
| `e19fc8f` | feat(parent): 家长首页断点自适应网格 + 超期待审一键清理（D-04/D-11） | §5.1 P2-01 / §5.2 P2-02 |
| `c7aa445` | fix(points): 超期清理按 id 复读、仅真置 CANCELLED 才计数（D-11 review fix） | §5.2 审查收口 |

**本报告证据全部产生于 Windows 本机（无 Android/iOS 模拟器或真机）**；凡需设备实测的条款如实标注
"待真机签核"，汇总见文末《真机签核待办清单》。

---

## 1. 逐节验收举证

### §3.1 D-01 错题本窄屏单一 LazyColumn（P0）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| Compact 竖屏页面平滑下滑、筛选栏与错题卡完全可见 | 代码层落地（`9903f2d`）：Compact 分支废弃无滚动 Column 嵌套，改单一顶层 `LazyColumn`（status_panel / filter_bar 前置 item，错题卡 `items(key=id)`，底部 88dp FAB 留白）；宽屏双栏分支零改动（走查确认 LazyColumn 内无嵌套可滚动容器）。**像素级滑动观感需设备** | 代码层**达成**；视觉**待真机签核**（清单第 1 项） |
| 点击复习触发"做对了 +3 / 没记住 +1"审批申请 | 交互逻辑与业务调用链未改动（本 task 仅布局层）；复习审批链路本身由 v3.3.0 既有回归测试覆盖，252→302 全绿 | **达成**（代码层）；真机复验归入清单第 1 项 |

### §3.2 D-02 任务表去重迁移 + 播种幂等（P0）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| Room version 12→13、MIGRATION_12_13 入 ALL、DAO 幂等查询、AppGraph 表空才播种 | `45be645` 逐条落地（见 task-2-report）：`AppDatabase.kt version = 13`；`MIGRATION_12_13` 同 title 活跃任务保留 MIN(id) 其余软删 `isActive=0`（沿用已上线的 MIGRATION_11_12 同款 GROUP BY/MIN 惯用法）；`getActiveTaskByTitle` + `initDefaultTasks` 按 title 幂等；首启分支改 `ensureDefaultTasks/ensureDefaultRewards`（表空才播种） | **达成** |
| 迁移回归测试 | `TaskRepositorySeedIdempotencyTest` 3 例（双调不重复 / 有存量不插入 / 空表播种）。**如实披露**：迁移 SQL 本身因 JVM 测试环境无 sqlite-bundled 原生库不可直接执行，正确性由"同款已上线惯用法 + 运行期幂等防线单测"间接保证（task-2-report §迁移可测性说明） | **达成（有已披露限制）**；真机覆盖升级去重复验归清单第 2 项 |
| 覆盖升级后任务列表无重复、杜绝双倍冒领 | 运行期防线（幂等播种 + 表空才播种）已单测钉死；老库存量重复行由迁移软删 | 代码层**达成**；**待真机签核**（清单第 2 项） |

### §3.3 D-08 心愿账本类型与删除退还（P0）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 存入积分明细准确标识"兑换消耗" | `500e456`：`depositPoints` 废除裸 `insertPointRecord(ADJUSTMENT)`，改走权威入口 `pointRepository.spendPoints` → `REWARD_EXCHANGE`；`WishGoalRepositoryLedgerTest` 断言类型与余额 | **达成** |
| 删除存有积分的心愿 → 已存积分原路退回、明细出现退回流水 | `deleteGoal`：`isActive && savedPoints>0` 先 `refundSpentPoints` 正向入账再删；测试断言净额代数和守恒（不变量 5） | **达成**（仓库层单测）；家长端删除弹窗退还提示的真机复验归清单第 3 项 |
| 工程师复核补充：note 不落文案（Ruling 25） | note 仅存 ASCII 业务键 `wish_deposit:<id>` / `wish_refund:<id>`；展示文案由 UI 层 `ui/common/PointRecordWishNote.kt` 派生（三消费面统一）；存储边界闸门新增**通道 H**（point_records.note 业务键登记表，2 条键纯 ASCII 且各有一个写入点）入 selftest 断言 | **达成**——本条为对指令 §3.3 补充口径的加强执行 |

### §4.1 D-03 任务卡动态审批态（P1）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 打卡后立即显示 ⏳ 待家长审核 | `bb9c4cf`：`PointApprovalDao.getTaskApprovalsForDate`（`bizKey LIKE 'TASK:%' AND date=:date` 单查询，Room 失效驱动流）+ `PointRepository.getTaskApprovalStates` 按 bizKey 折叠最新态；`ChildTaskCard` 按 PENDING/APPROVED/REJECTED/CANCELLED/REVOKED 五态渲染小标，6 个新槽全进 `TaskListStrings` 双语表 | **达成**（代码 + 3 例仓库层单测：最新态跟随 / 跨日不串 / 非 TASK 前缀不混入） |
| 审批通过实时刷新 ✅、拒绝转 ⛔ 红色告警且不再显示"需审批"假象 | 失效驱动流即时刷卡；REJECTED 卡面 `errorContainer` + 双入口重新打卡（拒绝可重打卡走 `ensureApproval` 新建 PENDING，打卡语义未动） | 代码层**达成**；三态标签真机复核归清单第 4 项 |
| 如实披露（task-4-report 顾虑） | CANCELLED/REVOKED 小标在当前撤销闭环下不可达（防御分支）；首帧竞态一帧兜底"需审批"下帧即刷——均已在报告披露 | 已知限制，不构成本条违规 |

### §4.2 D-05/06/07 番茄钟时间戳持久化 + 跨进程补结算 + 常亮（P1）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 计时中屏幕保持常亮；暂停/退出恢复息屏策略 | `a3edb21`：`PlatformKeepScreenOn.kt` expect/actual——Android `FLAG_KEEP_SCREEN_ON`（minSdk 26 不用 API 33 属性）、iOS `UIApplication.idleTimerDisabled`（**非 no-op**）；接线 `KeepScreenOn(RUNNING || BREAK)`，DisposableEffect 离开组合自动释放 | 代码层**达成**；**设备实测归清单第 7/9 项**（iOS 侧本机 Windows 无法编译 Kotlin/Native target，仅源码落地） |
| 返回键退出再进，倒计时无缝继续 | 会话态持久化于 DataStore（`PomodoroSessionState` 纯函数状态机，deadline 公式含暂停冻结/顺延），`PomodoroTimer` 改会话驱动 `deriveRemainingMs(now)` 续倒数；`PomodoroSessionStateTest` 8 例逐值钉死 | **达成**（纯函数层全测）；真机复验归清单第 7 项 |
| 杀进程后按时自动结算该次专注并生成待审积分 | `AppGraph.bootstrap` 启动 `recoverStaleSession`：FOCUS 未结算且过 deadline → 与 UI 完成回调**同一条** `settleFocusSession` 补结算（`FOCUS:$sid:$date` bizKey 幂等 + DataStore CAS，双触发恰好一笔入账一笔统计）；17 例新测试含顺序/并发/陈旧快照三路幂等与两个崩溃续跑窗口 | **达成**（代码层，指令"进页弹提示保底"按 brief 改为启动静默补结算，差异已在 task-7-report 披露）；真机杀进程复验归清单第 7 项 |

### §4.3 D-09 系统奖励防连击（P1）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 三处领奖按钮狂点连击仅产生 1 条加分记录 | `09a4f21`：`tryClaim*` 读-判-写同一 `dataStore.edit` 事务（原子 CAS，先置位后入账）+ `BonusClaimGuard` `Mutex.tryLock` 当场拒绝不排队 + 三入口共用状态源禁用；`BonusClaimGuardTest` 4 例（双击在飞第二击不入账 / 顺序重领 CAS 拒绝 / 入账异常作废不双发 / 三入口同禁）+ `UserPreferencesBonusClaimCasTest` 真 DataStore 并发恰一 true（3 例，Task 8 已修其持久态 flake 并连跑两遍实证） | **达成**；真机双击复验归清单第 5 项。Task 5 范围外同构风险（错题本复习里程碑、每日幸运抽奖）如实登记，见清单附注 |

### §4.4 D-10 家长撤回审批闭环（P1）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 历史 APPROVED 卡有"撤回通过"入口 + 二次确认 | `0ea99fd`：`ParentApprovalScreen` 历史卡 `status==APPROVED && bizKey.isNotEmpty()` 渲染 TextButton → AlertDialog（复用 CommonStrings 确认/取消钮）→ `revokeApproval(id)`；snackbar 按实际返回值分支（0=终态无变化如实报"未扣回"） | **达成**（UI 无自动化测试基建，靠编译 + 埋点闸门字符串通道 + 双语文案 pin 钉住；真机操作复验归清单第 6 项） |
| 撤回严格扣回对应积分、状态置已撤销、REVERSAL 对称冲抵 | 复用 `undoCheckIn(bizKey, taskId, date)` 同一闭环零新扣回逻辑；`PointRepositoryParentRevokeTest` 5 例：撤回返回 8 / 账本 [+8,-8] / REVERSAL 关联 approvalId / 状态 REVOKED / SUM=0；二次撤回逐字段零变动 | **达成**；徽章"🔄 已撤销"真机复核归清单第 6 项 |
| 孩子端撤销路径不变（红线） | `undoCheckIn`/`undoTaskCheckIn` 逐字节未动，孩子端四处调用点零 diff；`PointRepositoryInvariantTest` 全绿 | **达成** |
| 埋点：撤回动作可观测 | 登记表 20→21 项（APPROVAL_ACTION 计数 3→4 + revokeApproval 接线钉条各 1），闸门输出 `21 site checks` exit 0 | **达成** |

### §5.1 D-04 家长首页断点自适应（P2）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 手机竖屏双列卡片随页面纵向滚动、平板 4 列栅格 | `e19fc8f`：stripItems 删除 `horizontalScroll`，`chunked(stripColumns)` Compact/中等两列、Expanded 四列，末行透明占位保列宽；Daily Pulse Expanded 4×1（延伸已披露）；8 入口 1:1、徽章语义零改动 | 代码层**达成**（布局类改动无单测面，像素观感**待真机签核**，归清单附注） |

### §5.2 D-11 超期待审清理（P2）
| 指令验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 超 30 天待审提示 + 一键清理入口 | `e19fc8f`：看板下 errorContainer 提示条（`getStalePendingCount` 流读数）→「清理超期申请」→ 二次确认 → snackbar 按实际笔数如实反馈；30 天口径唯一出处 `STALE_PENDING_MILLIS` | **达成** |
| 清理后孩子端待审分值准确复位 | 逐条 `revokeApproval`→`undoCheckIn` 对称闭环置 CANCELLED（**覆盖指令裸批量 UPDATE 方案**，brief 红线，差异已披露）；`PointRepositoryStalePendingCleanupTest` 6 例钉死：30 天边界严格、PENDING 归零、余额分毫不动、二次清理 0 变化、TASK 单连带撤销完成记录、空 bizKey 兜底不波及他行 | **达成**（仓库层）；真机清理复验归清单第 8 项 |
| review fix（`c7aa445`） | 清理循环先按 id 复读：仅仍 PENDING 且为 bizKey 最新单才执行、仅真置 CANCELLED 才计数——防同 bizKey 更新 APPROVED 单被误落 REVERSAL 半区、防 snackbar 虚报；第 6 例专测陈旧单被新单遮蔽时 cleaned=0 账本零写入 | **达成** |

---

## 2. §7.2 最终发布交付标准举证

| 条款 | 证据 | 判定 |
| :--- | :--- | :--- |
| `:composeApp:compileDebugKotlin` 无新增错误与警告 | Task 9 全量回归 `testDebugUnitTest + assembleDebug + assembleRelease` **BUILD SUCCESSFUL**（exit 0，2026-11-22 本机复跑）；Kotlin 编译为其前置 task。既有 M3/Gradle deprecation 警告自 v3.0 存量未新增类别（如实说明，同 v3.3.0 验收口径） | **达成** |
| 总单测 ≥258 且 100% 通过 | `composeApp/build/test-results/testDebugUnitTest/` XML 汇总：**302 tests / 0 failures / 0 errors / 0 skipped**。增长链 252→255(T2)→264(T3)→267(T4)→274(T5)→279(T6)→296(T7)→301(T8)→**302**(review fix) | **达成**（302 ≥ 258） |
| 历史单元测试全量回归通过 | 上述 302 含全部历史套件（积分不变量、i18n copy pin、审批闭环、迁移、番茄钟等）；本波 Task 8 另以 `--rerun-tasks` 连跑两遍全绿收口一处测试基建持久态 flake | **达成** |
| 静态闸门 | `analytics_call_sites.py` → `ANALYTICS_GATE_OK: 21 site checks + data-layer zero-call guard` exit 0；`i18n_storage_boundary.py` exit 0（178 kt，通道 H 业务键登记 wish_deposit:/wish_refund: 2 条全 ASCII 各一写入点）；`scripts/tests/i18n_storage_boundary_selftest.py` **201 断言 0 失败** exit 0；`i18n_template_diff.py` 默认组 exit 0（[A] 0 missing/0 invented，[B] 中文文案 118/118 零差异，登记表 261 条未过期）；`--branch` 变体 exit 0（980/980，登记披露全部成立、0 条未用到） | **达成**（四闸门全绿，Task 9 本机复跑读数） |
| assembleDebug / assembleRelease / lintVitalRelease | 双 APK 产出：`HStudy-v3.3.1-debug.apk`、`HStudy-v3.3.1-release-unsigned.apk`；`lintVitalAnalyzeRelease/lintVitalReportRelease/lintVitalRelease` 随 assembleRelease 执行通过——**v3.2.0 fragment 坑未复发**（既有 pin `androidx.fragment:fragment:1.8.5`，composeApp/build.gradle.kts:71，未改动） | **达成** |
| 版本三处（实际四处惯例）升 3.3.1 | 照 v3.3.0 发布 commit `9f33bd7` 惯例：`composeApp/build.gradle.kts` versionCode **30300→30301** / versionName **3.3.1**；`iosApp/Info.plist` CFBundleShortVersionString/CFBundleVersion 同步；`project.pbxproj` MARKETING_VERSION/CURRENT_PROJECT_VERSION 各 2 处；`version_history.md` 新增 v3.3.1 修复清单（D-01~D-11 逐条一行 + review fix）；**README 双语版本行**：Room schema v12→**v13** 表述（英 74 / 中 141 行，随 D-02 迁移事实更新，v3.3.0 未涉及此项故本轮补充） | **达成** |
| 五条不变量 + Ruling 25 | 全程存储边界闸门 exit 0；心愿 note 业务键化后落库字面量仅 ASCII 键；账本 append-only、撤销对称、审批幂等由 Invariant/Revoke/Cleanup/Settlement 套件复钉 | **达成** |

## 3. 红线自查
- Task 1–8 均声明未动版本三处与彼此资产；Task 9 改动**仅** `composeApp/build.gradle.kts`（版本号两行）、`iosApp/Info.plist`、`project.pbxproj`、`version_history.md`、`README.md`（双语 schema 行）、`docs/acceptance-v331-hardening.md`（本报告）与 `.superpowers/sdd/.../task-9-report.md`——**零业务代码改动**。
- 未 push、未开 PR、未打 tag；发布走用户闸门。

## 4. 真机签核待办清单（设备实测，本机 Windows 无模拟器/真机）

1. **D-01 错题本窄屏复验**（=原签核待办第 3 项）：Redmi Compact 竖屏滑动至错题卡完全可见，点复习触发 +3/+1 审批申请；深浅色各一遍。
2. **D-02 覆盖升级去重**：v2/v3.3.0 老数据 APK 覆盖升级到 v3.3.1，任务列表无重复卡、重复行已软删；打卡仅一条申请单。
3. **D-08 心愿删除退款**：存入→明细显示"兑换消耗"→家长删除有分心愿→余额即时加回、退回流水展示（中英文双语下文案由 goal 名派生、note 键不直显）。
4. **D-03 三态标签**：打卡后 ⏳ → 家长通过 ✅ → 拒绝 ⛔（红色告警 + 可重新打卡）全链路真机标签如实翻转。
5. **D-09 双击防刷**：首页优秀表现/连续里程碑/每周挑战快速连点，账本各仅 1 条加分。
6. **D-10 撤回审批**：历史 APPROVED 卡撤回→余额扣回、徽章翻"🔄 已撤销"、二次撤回无变化；顺带复核窄屏撤回钮触达（task-6 顾虑 4）。
7. **番茄钟跨进程恢复**：计时中返回键再进无缝续时；杀进程 25 分钟后重开自动补结算恰好一笔待审；计时中常亮、暂停/退出息屏（D-05/06/07）。
8. **D-11 超期清理**：造 >30 天 PENDING → 提示条出现 → 一键清理 → 孩子端待审分复位、余额不动、二次清理报 0。
9. **iOS 首验**（含 `idleTimerDisabled` interop 关注点）：iPhone 16/iOS 18 模拟器 + 真机构建——SafeArea、错题本弹性滚动、常亮实际生效（Task 7 在 Windows 无法编译 Kotlin/Native iOS target，iOS actual 仅源码落地未实证）、深色对比度。
10. **PENALTY 跨日自动扣分**：按指令 §6.1 规程——必须任务不打卡、拨系统时间跨午夜、余额扣减 + 红色 `[未完成扣分]` PENALTY 流水 + 再重启不重复扣（`lastMandatoryPenaltyDate` 幂等）。
11. **Task 7 P3 残余风险登记确认**：极窄崩溃窗口（recordFocusSession 与 markPomodoroFocusRecordId 之间被杀 → 续跑可能重插一条 focus_session 产生第二张待审单）——真机压力路径复核该窗口实际可触达性并签核是否接受。

### 清单附注（指令书有、brief 清单未点名，一并登记不隐瞒）
- **§6.3 GA4 DebugView 全流核对**：本波未新增埋点调用点（唯一变动为 D-10 的 APPROVAL_ACTION 计数 3→4，静态闸门钉住），事件流真机 DebugView 瀑布核对仍需设备执行。
- **D-04 断点布局观感**：手机双列/平板四列与 Daily Pulse 4×1 的像素级排布走查（代码层无测试面）。
- **Task 5 登记的 D-09 同构风险两处**（错题本复习里程碑、每日幸运抽奖先入账后置位）：本波按红线未顺手修，建议签核裁决是否另立条目。
- **D-03 CANCELLED/REVOKED 小标不可达路径**：当前撤销闭环下不出现，真机按 ⏳/✅/⛔ 三态验证即可。
- **Task 7 P3 另两条残余风险（终审整分支审查后补登，同清单第 11 项口径）**：① FOCUS 段**到期后暂停**——deadline 判定用冻结剩余量，进程内暂停期间不会触发补结算（不双入账，只是结算延后到恢复/下次进入）；② 结算基准依赖**墙钟**（`nowMillis`），孩子改系统时间可提前到期、提前结算赚分。两者均属设备侧时间可信边界，本波红线未动，签核裁决是否加单调时钟/防篡改约束。
- **withTransaction 批量原子建议**（与存量 P2-2 `@Transaction` 同批裁决）：`expireStalePendingApprovals` 的"按 id 复读→撤销"与终审 Minor-1 后 `revokeApproval` 的"复读→最新性校验→委托闭环"仍是**多次 DAO 读写的毫秒级非原子窗口**（c7aa445 review fix 与终审核查均记录此残留）；真原子需 Room `withTransaction` 包裹整段读-判-写。因与存量 P2-2（审批发放侧 @Transaction 缺失）同根，建议下一波同批裁决，避免本波单点改动引 regress。
- **D-12（存量盲区，终审编号入台账）**：三处里程碑 bonus 的 `note` 仍以**中文字面量**直写 DB——`ChildHomeStrings.kt:207/210/213` 的 `EXCELLENT_BONUS_NOTE`/`WEEKLY_CHALLENGE_NOTE`/`streakMilestoneNote`，经 `ChildHomeScreen.kt:444/586/630` 的 `adjustPoints` 落库，属 D-08 心愿 note 业务键化之前的存量口径，四闸门均未覆盖（迁移前即如此、本波按红线未扩面）；如裁决扩面，按 D-08"note 只存 ASCII 业务键、文案渲染期派生"同形处理。
- **D-13（同构重入候选，终审编号入台账）**：错题复习里程碑（`ChildMistakeListScreen.kt:313-314`）与每日幸运转盘（`ChildLuckyDrawScreen.kt:119-121`）存在与 D-09 同构的"先入账后置位"重入窗口——本波红线只修签核清单 D-01~D-11，两处按红线未顺手修，签核裁决是否另立条目走 tryClaim CAS 同款收口。

## 5. 总判定
- §3/§4/§5 九项缺陷修复：**代码层全部达成**（302 单测 + 四闸门 + 双 assemble + lintVitalRelease 全绿举证如上）；涉及视觉/设备行为/覆盖升级的条款**待真机签核**（清单 11 项 + 附注 8 项，终审修复批补登 4 项残险台账）。
- §7.2 交付标准：**全部达成**（单测 302 ≥ 258、历史回归 100%、闸门全绿、版本四处升 3.3.1、未 push/PR/tag）。
- 发布裁决留给用户闸门。

## 6. 证据索引
- 测试：`composeApp/build/test-results/testDebugUnitTest/`（302/0/0/0，2026-11-22 Task 9 复跑）
- 构建产物：`composeApp/build/outputs/apk/debug/HStudy-v3.3.1-debug.apk`、`composeApp/build/outputs/apk/release/HStudy-v3.3.1-release-unsigned.apk`
- 闸门：`scripts/analytics_call_sites.py`、`scripts/i18n_storage_boundary.py`、`scripts/tests/i18n_storage_boundary_selftest.py`、`scripts/i18n_template_diff.py`（默认 + `--branch`）
- 波次过程记录：`.superpowers/sdd/2026-11-22-v331-hardening/task-{1..9}-report.md` 与 `review-*.diff`
- 指令书：`docs/antigravity-v331-directives.md`；上一波验收风格基线：`docs/acceptance-m3-ui-points-hardening.md`
