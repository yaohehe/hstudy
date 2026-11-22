# HStudy v3.3.2 验收增补报告 — 裁决修复批（D-12/D-13/事务原子化，2026-11-23）

对照 `docs/superpowers/plans/2026-11-22-v332-adjudicated-fixes.md` 三修复 task 的验收口径与
`docs/acceptance-v331-hardening.md` 残险台账（D-12/D-13/P2-2 续项）逐条举证。
执行范围 = Task A（D-12）/ Task B（D-13 + fix round 1）/ Task C（withTransaction）/ Task D（收尾）。
分支 `feature/v332-hardening`，基线 `4f0594d`（v3.3.1 合并点，302 单测全绿），本波 4 个修复 commit + 收尾：

| Commit | 内容 | 计划 task | 审查状态 |
| :--- | :--- | :--- | :--- |
| `5a051e6` | fix(points): 三处里程碑 bonus note 业务键化 + 渲染期派生双语，旧记录 passthrough（D-12） | Task A | Approved |
| `977642b` | fix(points): 复习里程碑与幸运转盘改 CAS 先置位后入账，双击不双发（D-13） | Task B | Approved（6/6） |
| `20df6e1` | fix(points): 复习里程碑与转盘 note 并入 bonus_* 业务键体系 + type 判定纯函数补测（D-13 fix round 1） | Task B 审查裁决续项 | 同上收口 |
| `987752e` | perf(points): 批量审批/超期清理/撤回三入口事务原子化，消除读判写窗口（P2-2/终审残留） | Task C | Approved（8/8） |
| （本次提交） | release: v3.3.2 版本+回归+验收增补 | Task D | — |

**本报告证据全部产生于 Windows 本机（无 Android/iOS 模拟器或真机）**；凡需设备实测的条款如实标注
"待真机签核"，汇总见文末《真机签核追加清单》。本报告为 v3.3.1 验收报告的**增补**，不推翻其结论；
两报告合读构成 v3.3.2 发布依据。

---

## 1. 逐 task 验收举证

### Task A / D-12 三处里程碑 bonus note 业务键化
| 计划验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 锚点三处 `adjustPoints(..., CanonicalChildHomeText.中文常量)` 中文字面量不再直进 DB（ChildHomeScreen.kt:444/587/633） | `5a051e6`：三处改 `BonusNoteKeys.excellent(today)` / `streak(days)` / `weekly(weekStartStr)`，落库 note 仅纯 ASCII 键 `bonus_excellent:<date>` / `bonus_streak:<days>` / `bonus_weekly:<weekStart>`；分值 2/5/里程碑表值、CAS 置位顺序、snackbar/成就重评原样（红线） | **达成** |
| 渲染期三消费面按 type+键派生文案（D-08 `PointRecordWishNote.kt` 同型） | 新派生器 `ui/common/PointRecordBonusNote.kt`（`BonusNoteKeys` 构造 / `parseBonusNoteKey` 形状钉死 / `bonusNoteTextFor` 纯函数 / `bonusNoteDisplayText` @Composable）；ChildPointsScreen、ParentPointsScreen、ParentDashboard「最近积分变动」渲染链统一为 `wishNoteText ?: bonusNoteText ?: record.note` | **达成** |
| 文案进双语表 | `ChildPointsStrings` 新增 `bonusExcellent`/`bonusWeekly`/`bonusStreak(days)`，中文值与迁移前直写 DB 原文**逐字节相同**（新旧记录中文词面一致，经字节比对测试钉死）；英文新写、不进 EN_STATIC_POINTS_MENTIONS 闭集 | **达成** |
| 旧中文记录原样展示不改写 | 零迁移 passthrough（append-only 不变量 5）：解析不合一律 null→原样展示库内文本；`legacyChineseBonusNotesStayPassthrough` 钉旧中文/家长备注不认领 | **达成**（代码层）；英文界面旧中文行观感**待真机签核**（追加清单 F-3） |
| 测试：三键派生 + 旧记录 passthrough + 净额守恒 | `PointRecordBonusNoteTest` 6 例（键形状/伪键穷举/双语派生/passthrough/入账+渲染全链路净额守恒/type 闸门），随 Task B fix round 1 扩为五键 317→325 基线内全绿；`ChildRewardStoreCopyTest`/`StringsCopyTest`/`BonusClaimGuardTest` 同步收口 | **达成** |
| 存储边界闸门同步 | 通道 H `NOTE_BIZKEY_PREFIXES` 纳入三键（双向对账：纯 ASCII + 扫描根字面量写入点存在）；selftest 6b 节逐键来源表 + 红半点名 + 绿半 fixture；模板等价 diff 闸门 Task 9 组三条页面单边登记改两侧同形（条数不变 262） | **达成**（四闸门 Task D 复跑读数见 §2） |

### Task B / D-13 复习里程碑与幸运转盘 CAS 防重入
| 计划验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| 两入口改 tryClaim CAS + BonusClaimGuard（D-09 同型）：`ChildMistakeListScreen.kt:313-314`、`ChildLuckyDrawScreen.kt:119-121` | `977642b`：`setLastLuckyDraw`/`addReviewStreakMilestoneClaimed` 改 `tryClaimDailyLuckyDraw(date)`/`tryClaimReviewStreakMilestone(days)`（读-判-写同一 `dataStore.edit` 事务）；两页面领取段走 `bonusClaimGuard.claim(...)`（`Mutex.tryLock` 当场拒绝不排队），旧函数全仓无残留调用方；奖池/random/delay/概率/埋点位置原样（红线） | **达成** |
| "转盘若需原子扣奖池走同一 edit 事务"——按 brief 预留分支判定 | 裁决**不需要**：奖池是页面内常量列表（无持久化池，每日重置），当日唯一资格位即 `LAST_LUCKY_DRAW` 一键，CAS 置位即等价"判定+抽取资格"原子化（task-b-report §奖池方案取舍） | **达成（按裁决分支，如实披露）** |
| 测试：双击只一发奖 | `BonusClaimGuardD13Test` 双击在飞第二击不入账、同日重入 CAS 拒绝、换日开窗（5 例）+ `UserPreferencesD13CasTest` 真 DataStore 并发恰一胜（2 例）；fix round 1 增全链路 CAS 入账→明细派生净额守恒例 | **达成**（代码层）；真机双击实测**待真机签核**（追加清单 F-1） |
| fix round 1（审查裁决：两入口 note 并入 bonus_* 键体系） | `20df6e1`：`bonus_review:<days>` / `bonus_lucky:<date>` 两键入库（中文槽逐字节=旧原文）；type+note 认领判定抽纯函数 `claimsBonusNote` 补真断言；通道 H 扩至 7 键、selftest/模板登记同步 | **达成**——D-12 口径对 Task B 残留盲区的加强执行 |

### Task C / P2-2 + 终审残留：withTransaction 三入口事务原子化
| 计划验收标准 | 证据 | 判定 |
| :--- | :--- | :--- |
| `approveAllAndAward` 整批包 Room `withTransaction`（不可用则披露替代锁方案） | `987752e`：Room 2.7.0 KMP `withTransaction` 实证可用（linkdata + Android 编译），**未退化 Mutex**；批量整批一事务，中途异常=整批回滚，杜绝"前半批 APPROVED 后半批 PENDING" | **达成** |
| 超期清理与 `revokeApproval` 读-判-写纳入同一事务消除毫秒窗口 | 三入口全包：`revokeApproval` 复读-判半区-判最新-委托 `undoCheckIn` 整段同事务（判定本体逐字零改）；`expireStalePendingApprovals` 快照-复读-判定-落库循环同事务（嵌套 revoke 合并外层，Room 语义） | **达成** |
| 注入形态 | `DbTransactionRunner` 小接口（生产 `RoomTransactionRunner` 委托 `withTransaction`；`DirectTransactionRunner` 直通默认保 317 基线构造点零改动）；`AppGraph.kt:106` 装配层接入 database 实例 | **达成**；装配行与真 SQLite 事务行为只有编译/Fake 语义钉——**待真机/CI 验证**（追加清单 F-4/F-5，task-c-report 披露 2/3） |
| 测试：批量中途异常整体回滚 | `PointRepositoryTransactionAtomicityTest` 8 例：JournalTxRunner 快照-回滚假事务器钉 Room 语义（整批一外层事务/嵌套不另起/中途抛全回滚/终态幂等零回归/嵌套共享事务/cleaned 不虚报） | **达成**（Fake 语义钉）；真机杀进程路径见 F-4 |
| 语义变化披露 | 批量中途异常由"半批已提交、看起来成功"改为"整体回滚+异常上抛到 UI 协程"（v3.3.1 既有 scope.launch 无 handler 形态，审查员 Minor① 点名）——UI 零改动红线守住，失败态专门文案留待后续 | **达成（有意变化，已裁决；不闪退实测归 F-4）** |

## 2. 交付标准举证（Task D 本机复跑，2026-11-23）

| 条款 | 证据 | 判定 |
| :--- | :--- | :--- |
| 全量单测 ≥325 且 100% 通过 | `:composeApp:testDebugUnitTest` **325 tests / 0 failures / 0 errors**（增长链 302→310(T-A)→316→317(T-B fix r1)→**325**(T-C)，Task D 复跑读数） | **达成** |
| assembleDebug / assembleRelease | `HStudy-v3.3.2-debug.apk`、`HStudy-v3.3.2-release-unsigned.apk` 产出（lintVitalRelease 随 assembleRelease 执行；未设签名环境变量=release 照旧未签名产出，签名链走用户发布闸门） | **达成** |
| 静态闸门 | `analytics_call_sites.py` → `ANALYTICS_GATE_OK: 21 site checks + data-layer zero-call guard` exit 0（本波埋点零改动）；`i18n_storage_boundary.py` exit 0（180 kt，通道 H 现 **7 条键**——wish 两键 + bonus_excellent/weekly/streak/review/lucky 五键，全 ASCII 各一写入点）；`i18n_storage_boundary_selftest.py` **202 断言 0 失败** exit 0；`i18n_template_diff.py` 默认组 exit 0（[A] 0 missing/0 invented，[B] 118/118 零差异），`--branch` exit 0（980/980，登记披露全部成立） | **达成**（四闸门全绿） |
| secret scan（本分支范围） | `git log 4f0594d..HEAD -S 'ghp_'` 唯一命中为 eac4c21 验收文档自引的扫描命令字面量（终审 Minor① 更正举证口径：`--all` 会越出本分支范围且文档正文自指）；`git grep -E 'ghp_[A-Za-z0-9]{20}' 4f0594d..HEAD` **0 命中**（HEAD 树同样 0 命中）——无真实泄露 | **达成** |
| 版本四处升 30302/"3.3.2" | 照 v3.3.1 发布 commit `c0fa442` 落点清单：`composeApp/build.gradle.kts` versionCode **30301→30302** / versionName **3.3.2**；`iosApp/Info.plist` CFBundleShortVersionString/CFBundleVersion；`project.pbxproj` MARKETING_VERSION/CURRENT_PROJECT_VERSION 各 Debug/Release 2 处；`version_history.md` 新增 v3.3.2 条目（加粗一行摘要 + D-12/D-13/事务原子化三条 + 测试/闸门/版本行）。**README 本轮无改动**：c0fa442 的 README 两行是 Room schema v12→v13 表述随 D-02 迁移更新，本波 schema 维持 v13 零迁移，无对应事实变化 | **达成** |
| 五条不变量 + Ruling 25 | 存储边界闸门 exit 0；本波新落库 note 仅 ASCII 业务键；账本 append-only、撤销对称、审批幂等由 Invariant/Revoke/Cleanup/TransactionAtomicity 套件复钉；Room schema 不动 | **达成** |
| 审查链路 | Task A Approved；Task B 6/6 Approved（微瑕 2 条留档不阻塞）；Task C 8/8 Approved（Minor①② 均已列入本报告真机清单 F-4/F-5） | **达成** |

## 3. 红线自查
- Task A–C 各自声明未动版本四处与彼此资产（progress.md 台账逐条核）；Task D 改动**仅**
  `composeApp/build.gradle.kts`（版本号两行）、`iosApp/Info.plist`、`project.pbxproj`、`version_history.md`、
  `docs/acceptance-v332-adjudicated-fixes.md`（本报告）与 `.superpowers/sdd/2026-11-22-v332-adjudicated-fixes/task-d-report.md`——**零业务代码改动**。
- 奖励分值（2/5、3/8/15/30、转盘池 1-5）/概率/埋点数量/`spendPoints`/bizKey/checkIn 判等路径：本波全程未动（各 task 报告红线复查）。
- 未 push、未开 PR、未打 tag；发布链（push/PR/双绿合并/tag v3.3.2/release/资产五重校验）由控制器接管用户闸门。

## 4. 真机签核追加清单（设备实测，本机 Windows 无模拟器/真机）

审查员点名的四项 + 装配验证一项 + 回归复验一项：

1. **F-1 转盘/复习双击实测（D-13）**：错题本连续复习里程碑领取按钮与每日幸运转盘快速连点/双击——
   账本各仅 1 条加分（`bonus_review:<days>`/`bonus_lucky:<date>` 一笔）；转盘被拒第二击如实翻"明天再来"；
   成就重评与 snackbar 正常；换日恢复可领/可抽。
2. **F-2 D-12/D-13 五键展示如实**：中英界面下三消费面（孩子积分明细/家长积分管理/家长仪表盘"最近积分变动"）
   新 bonus 行按业务键派生——中文行词面与升级前逐字节一致，英文行念英文；DB note 列仅 ASCII 键。
3. **F-3 英文界面旧中文存量行 passthrough 观感**：切英文界面查看历史账本——D-08 心愿行、D-12/D-13 前
   落库的中文 ADJUSTMENT 行**原样显示库内中文**（零迁移裁决，不翻译不改写）；签核该混排观感是否可接受，
   不可接受则裁决是否另行回填迁移。
4. **F-4 真 SQLite 批量回滚不闪退（D-P2-2，审查员 Minor①点名）**：
   a) 家长"一键通过"多条待审中途**杀进程**→重开验证余额与审批态**全部回到批前**（绝无半批 APPROVED），
      与 task-c-report 披露 2 的 Fake 语义钉对真；
   b) 批量中途异常路径（如写库失败）异常上抛至 UI 协程——v3.3.1 既有 `scope.launch` 无 handler 形态下
      **核实 App 不闪退**；若闪退，签核是否补 catch 处理或专门失败文案（本波红线未动 UI）。
5. **F-5 AppGraph 装配行验证（审查员 Minor②点名）**：`AppGraph.kt:106` 的
   `RoomTransactionRunner(database)` 生产装配路径仅编译钉住（无 Fake 可替代）——F-4a 杀进程实验即该行的
   实证覆盖：若装配未接对（仓库仍走直通默认值），杀进程后必留半批态。撤回/超期清理两入口同事务生效性
   一并观察。
6. **F-6 本波触点回归复验**：首页三处里程碑 bonus（D-09）双击防刷在 note 键化后无回归；
   超期待审清理（D-11）与撤回审批（D-10）在事务包装后行为与 v3.3.1 验收一致（cleaned 计数不虚报、
   二次操作零变化）。
7. **F-7 iOS 侧首验（终审整分支审查补登）**：本机 Windows 无法编译 Kotlin/Native target，
   Task C 的 Room `withTransaction` 与 Task B 的 DataStore CAS 仅 Android 编译/单测实证——
   iOS 模拟器至少跑：批量审批中途杀进程→回滚不半批、转盘双击不双发；同时覆盖
   `idleTimerDisabled` interop 首编译关注点（见 [[hstudy-ios-kn-interop-limits]]）。
   **CI fix round 补记（2026-11-23，run 45045438877 实证）**：iOS 首验即命中 commonMain 平台纯度问题——
   `androidx.room.withTransaction` 仅 Room 2.7 JVM/Android klib 提供，`compileKotlinIosSimulatorArm64`
   报 Unresolved reference 失败。修复：Room 事务实现移入 androidMain（`DbTransactionRunner.android.kt`），
   commonMain 改 expect 工厂 `roomTransactionRunner(database)`；**iOS actual=DirectTransactionRunner 直通——
   Room KMP 原生事务 API 缺位，iOS 批量原子降级为直通（等同 v3.3.1 现状），待 Room 提供 common 事务 API 后回切**
   （KDoc 披露见 `DbTransactionRunner.ios.kt`）。对 F-7 影响：iOS 侧"批量审批中途杀进程→回滚不半批"一项
   **随降级移出验证范围**（直通无回滚语义可言），改验"不闪退、账本判定与 Android 一致"；
   Android 侧 F-4/F-5 红线（真 withTransaction 零变化）不受本修复影响。

### 终审残余登记（2026-11-23 整分支审查，Minor 级不阻塞发布）
- **转盘作废态展示**：`ChildLuckyDrawScreen` onClaimed 内 `resultPoints=won` 先于 adjustPoints 赋值，
  guard 吞异常作废的**理论不可达路径**下 UI 显示"中奖 X"而账本无记录（账本侧裁决与 D-09
  "作废不双发"一致，红线不破）。F-6 顺带观察，若产品要求展示同源可后续把赋值移到入账后。
- **不变量 6 措辞修订（待下批文档波）**：`PointRepository` KDoc"任何自动业务不得借 adjustPoints 入账"
  与五处 bonus 自动入账（恒 ADJUSTMENT，`claimsBonusNote` 已把 type 钉死）正面冲突——
  后人按措辞"纠正"为 BONUS 类型将致三消费面全盲。正确口径应为"自动业务经 CAS 防重入后可走
  adjustPoints，type 恒 ADJUSTMENT 且 note 必须业务键化"。本波红线不动 KDoc，登记待改。

### 存量清单延续声明
**`docs/acceptance-v331-hardening.md` §4 的 11 项真机签核待办清单继续有效**——本波未触碰其任一验证对象
（版本升级不改变行为语义），无一失效；其中第 5 项（D-09 双击防刷）、第 6 项（D-10 撤回）、第 8 项（D-11 清理）
与本波 F-1/F-4/F-6 同页触点，真机执行时建议连做。§4 清单附注登记的 D-12/D-13/P2-2 三条残险台账，
本波已全部按裁决收口，**自台账销项**；附注其余各条（§6.3 GA4 DebugView、D-04 布局观感、番茄钟墙钟/P3 残险等）
维持原状继续待签核。

## 5. 总判定
- 计划 Task A/B/C 三修复：**代码层全部达成**（325 单测 + 四闸门 + 双 assemble + secret scan 全绿举证如上；
  A/B/C 审查全 Approved）；涉及真 SQLite 事务行为、装配行实证、设备双击与展示观感的条款**待真机签核**
  （追加清单 F-1~F-6 + v3.3.1 既有 11 项）。
- Task D 交付标准：**全部达成**（单测 325 ≥ 325、版本四处升 30302/3.3.2、version_history 增条目、
  未 push/PR/tag）。
- 发布裁决留给用户闸门（控制器接管发布链）。

## 6. 证据索引
- 测试：`composeApp/build/test-results/testDebugUnitTest/`（325/0/0/0，2026-11-23 Task D 复跑）
- 构建产物：`composeApp/build/outputs/apk/debug/HStudy-v3.3.2-debug.apk`、
  `composeApp/build/outputs/apk/release/HStudy-v3.3.2-release-unsigned.apk`
- 闸门：`scripts/analytics_call_sites.py`、`scripts/i18n_storage_boundary.py`、
  `scripts/tests/i18n_storage_boundary_selftest.py`、`scripts/i18n_template_diff.py`（默认 + `--branch`）
- 波次过程记录：`.superpowers/sdd/2026-11-22-v332-adjudicated-fixes/`（task-{a,b,c}-report.md、
  review-*.diff、progress.md 审查台账）
- 计划：`docs/superpowers/plans/2026-11-22-v332-adjudicated-fixes.md`；
  上一波验收基线：`docs/acceptance-v331-hardening.md`（含 11 项真机清单原文）
