# HStudy v3.3.2 裁决修复批实施计划

**Goal:** 落地用户裁决的三项挂起缺陷（D-12/D-13/withTransaction），发布 v3.3.2。

**Spec:** docs/acceptance-v331-hardening.md 残险台账（D-12/D-13/P2-2 条目）+ 本计划。

## Global Constraints
- 五条积分不变量 + Ruling 25 存储边界；四闸门 exit 0；不动既有商店/审批语义；每 task 一 commit；发布链用户已一次批准（双绿即合→tag→release→资产校验）。

- [x] **Task A / D-12**：三处里程碑 bonus 的 note 业务键化。
  - 锚点：ChildHomeScreen.kt:444/586/630 adjustPoints(..., CanonicalChildHomeText.EXCELLENT_BONUS_NOTE / WEEKLY_CHALLENGE_NOTE / streakMilestoneNote)——中文字面量直进 DB。
  - 修法（D-08 同型）：note 改纯 ASCII 业务键（如 `bonus_excellent:<date>` / `bonus_weekly:<weekStart>` / `bonus_streak:<days>`），渲染期在 ChildPointsScreen/ParentPointsScreen/ParentDashboard 三消费面按 type+键派生文案（扩展 PointRecordWishNote.kt 模式或新建 MilestoneNote 派生器），文案进双语表；旧中文记录原样展示不改写。测试：三键派生 + 旧记录 passthrough + 净额守恒。
- [x] **Task B / D-13**：复习里程碑（ChildMistakeListScreen.kt:313-314）与幸运转盘（ChildLuckyDrawScreen.kt:119-121）改 tryClaim CAS + BonusClaimGuard（D-09 同型）；转盘若需原子扣奖池走同一 edit 事务。测试：双击只一发奖。
- [x] **Task C / withTransaction**：`approveAllAndAward`（P2-2）整批包 Room `withTransaction`（KMP Room 2.7 支持 `RoomDatabase.withTransaction`；不可用则报告披露替代锁方案）；超期清理与 revokeApproval 的读-判-写纳入同一事务消除毫秒窗口。测试：批量中途异常整体回滚（Fake 或 JVM Room 可行则真测，否则纯逻辑层钉）。
- [ ] **Task D**：版本四处 30302/3.3.2 + version_history + 全量回归 + 验收增补 docs/acceptance-v332-adjudicated-fixes.md + push/PR/双绿合并/tag v3.3.2/release/资产五重校验。
