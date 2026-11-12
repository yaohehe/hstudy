# i18n 阶段 2 W6——心愿/证明/报告/周报 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** W6 八源双语化：心愿存钱罐/心愿管理/周报总览/成长报告/证明审核/证明提交/照片查看七页 + 周报通知（commonMain 排程取词 + **androidMain 接收器摘要取词，用户已授权**）；注册表扩 3 条示例心愿（6 值），8 张新表进 `AppStrings`（34→42），测试 186→203，唯一 W6 commit。

**Architecture:** 沿用方案 A：UI 文案进 `data class …Strings`；示例心愿名/描述是 canonical 落库值 → 注册表 + 渲染包 `localizedPresetText`；任务标题（周报表格/证明页/证明提交页参数）渲染包注册表；宠物阶段名（8 个）归 W7，本波原样 + 披露；周报通知按 W1/W5 同型在**发出点**取词，Android 摘要拼接从 androidMain 迁到 commonMain 表函数（平台文件仅改为调用 helper）。

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform 1.7.3、Kotlin 2.1.20；commonTest；Python 等价/边界闸门。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§12 W6 行、§8 通知文案、§14 已知残留、§10、§11）

## Global Constraints

- **Ruling 25 存储边界**：文案表/注册表字符串不得进入写库/业务键参数。canonical 落库字面量保持原样并披露：3 个示例心愿（name+description 共 6 值）。
- **平台文件例外（用户 2026-11-12 明确授权，仅 1 个文件）**：`composeApp/src/androidMain/kotlin/com/hstudy/app/platform/WeeklyReportReceiver.kt` 只允许做「摘要拼接改调 commonMain helper」这一处改动；不动其它平台文件、不动 AndroidNotificationScheduler。
- **不动**：`data/**`、schema/迁移、`DateTimeCompat.kt`、`Plurals.kt`（复用 `pointsWord`/`timesWord`，不新增 helper）、`AppGraph`、`AchievementManager`、`PresetRegistry` 除 W6 组外内容。
- 唯一改动形态：`"文本"` → `strings.xxx` / `localizedPresetText(...)` 等价替换；逻辑零变化。**授权形变**：①`WeeklyReportReceiver.schedule()`（commonMain）suspend 化（调用点在 `ParentSettingsScreen` 的 `scope.launch` 内，无需改调用点）；②androidMain 接收器改为调用新 common helper（授权）。
- 中文态字节等价；复用值逐字节同值：`CommonStrings.cancelButton`/`deleteContentDescription`、`ParentTaskEditStrings.saveButton`。
- 注册表守卫双向相等：+6 值必须同步 `PresetRegistryTest`（新增来源组）。
- 闭集逐任务加（W4/W5 先例）。
- 每波四闸；Task 6 唯一 commit；不推送。
- 环境：Gradle 内联 JAVA_HOME/GRADLE_USER_HOME；Python `PYTHONIOENCODING=utf-8`。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `.../i18n/PresetRegistry.kt`（改） | +6 条示例心愿（3 名 + 3 描述） |
| `.../i18n/ChildWishGoalStrings.kt`（新建） | 心愿存钱罐页 27 字段 |
| `.../i18n/ParentWishGoalStrings.kt`（新建） | 心愿管理页 19 字段 |
| `.../i18n/WeeklyTrackerStrings.kt`（新建） | 周报总览页 11 字段 |
| `.../i18n/GrowthReportStrings.kt`（新建） | 成长报告页 25 字段 |
| `.../i18n/ParentProofReviewStrings.kt`（新建） | 证明审核页 15 字段 |
| `.../i18n/ChildProofCaptureStrings.kt`（新建） | 证明提交页 11 字段 |
| `.../i18n/PhotoViewerStrings.kt`（新建） | 照片查看页 3 字段 |
| `.../i18n/WeeklyReportStrings.kt`（新建） | 周报通知 6 字段 |
| `.../i18n/Strings.kt`（改） | AppStrings +8 表 |
| `.../ui/wish/ChildWishGoalScreen.kt`、`.../ui/wish/ParentWishGoalManageScreen.kt` | 替换 + 心愿名/描述包注册表 |
| `.../ui/weekly/WeeklyTrackerScreen.kt`、`.../ui/report/GrowthReportScreen.kt` | 替换 + 任务标题包注册表 |
| `.../ui/proof/ParentProofReviewScreen.kt`、`.../ui/proof/ChildProofCaptureScreen.kt`、`.../ui/proof/PhotoViewerScreen.kt` | 替换 + 任务名包注册表 |
| `.../ui/parent/WeeklyReportReceiver.kt`（commonMain） | suspend + 排程取词 |
| `composeApp/src/androidMain/kotlin/com/hstudy/app/platform/WeeklyReportReceiver.kt`（改，**已授权**） | 摘要拼接改调 common helper |
| `composeApp/src/commonTest/kotlin/com/hstudy/app/i18n/WishCopyTest.kt`（新建） | W6 八表守卫 + 跨表 pin |
| `.../i18n/PresetRegistryTest.kt`、`.../i18n/StringsCopyTest.kt`（改） | 注册表守卫 + 闭集 |
| `scripts/i18n_template_diff.py`、`docs/i18n-stage1-verification.md`（改） | 闸门登记 + V-58…V-64 + 读数 |

---

### Task 1: 注册表扩 6 条示例心愿 + 守卫测试

**Files:** Modify `PresetRegistry.kt`、`PresetRegistryTest.kt`

- [ ] **Step 1: 追加测试（红）** 在 `PresetRegistryTest` 追加：

```kotlin
    @Test
    fun w6WishSamplesResolvePerLanguage() {
        // W6：3 个示例心愿的 name+description 是 canonical 落库值（addGoal 原样写入）
        val samples = mapOf(
            "一个小玩具" to "A small toy",
            "存够积分换一个喜欢的小玩具" to "Save up for a small toy you like",
            "一本新书" to "A new book",
            "去书店选一本喜欢的书" to "Pick a favorite book at the bookstore",
            "一台新平板" to "A new tablet",
            "存很久的大目标，加油！" to "A big long-term goal — keep going!",
        )
        for ((zh, en) in samples) {
            assertEquals(zh, localizedPresetText(zh, DisplayLanguage.ZH), "ZH 必须恒等：$zh")
            assertEquals(en, localizedPresetText(zh, DisplayLanguage.EN), "EN 译文漂移：$zh")
        }
    }
```

- [ ] **Step 2: 确认红**（EN 未命中断言失败）。
- [ ] **Step 3: 实现** 3a. `presetTextRegistry` 末尾追加分组（沿用 `preset(...)` 形式）：

```kotlin
    // ===== W6 页面内预设目录（示例心愿 3 条：name + description 共 6 值）=====
    // 这些 canonical 会被 addGoal 原样落库（ParentWishGoalManageScreen 的一键示例），
    // 渲染时包 localizedPresetText；家长自建未命中原样（Ruling 25）。
    preset("一个小玩具", "A small toy"),
    preset("存够积分换一个喜欢的小玩具", "Save up for a small toy you like"),
    preset("一本新书", "A new book"),
    preset("去书店选一本喜欢的书", "Pick a favorite book at the bookstore"),
    preset("一台新平板", "A new tablet"),
    preset("存很久的大目标，加油！", "A big long-term goal — keep going!"),
```
  3b. `PresetRegistryTest.registryKeysEqualSeedCanonicalsExactly` 的 `expected` 追加：

```kotlin
        val w6WishSamples = setOf( // PresetRegistry.kt「W6 页面内预设目录」组（示例心愿 3 条 × name+description）
            "一个小玩具", "存够积分换一个喜欢的小玩具",
            "一本新书", "去书店选一本喜欢的书",
            "一台新平板", "存很久的大目标，加油！",
        )
```
  并把 `expected` 加法行包含 `+ w6WishSamples`；在 W5 分组注释旁补一句 W6 来源。
- [ ] **Step 4: 确认绿** focused 16/16；全量 **187/0**。
- [ ] **Step 5: 暂存** 2 文件。

---

### Task 2: 心愿两页（ChildWishGoalStrings 27 + ParentWishGoalStrings 19）

**Files:** Create 2 表；Modify `Strings.kt`、两页、`WishCopyTest.kt`（新建，2×2 测试）

**表值（ZH/EN）**：
`ChildWishGoalStrings`：title「🐷 心愿存钱罐」/「🐷 Wish Jar」；introTitle「💡 什么是心愿存钱罐？」/「💡 What is the Wish Jar?」；introBody「设定一个大目标，把积分一点点存进去，攒够就能兑换啦！学会耐心等待，收获更大的惊喜～」/「Set a big goal and save your points into it bit by bit — once you've saved enough, you can redeem it! Learn to wait patiently and enjoy a bigger surprise.」；emptyTitle「还没有心愿目标」/「No wishes yet」；emptyHint「请爸爸妈妈帮你设定一个心愿吧！」/「Ask your parents to set up a wish for you!」；completedSection「🎉 已达成的心愿」/「🎉 Achieved wishes」；primaryBadge「👑主目标」/「👑 Main goal」；progressValue(saved,target)「$saved / $target 分」/「$saved / $target ${pointsWord(target)}」；remaining(n)「还差 $n 分达成心愿」/「$n ${pointsWord(n)} to go」；milestone75/50/25/start（原文照录：🔥 已完成75%，胜利在望，冲刺！ / 🌟 已经过半啦！继续保持，心愿越来越近！ / 💪 已完成25%，好的开始是成功的一半！ / 🌱 已迈出第一步，加油攒积分！ → EN 见测试）；estDays(days)「📅 照最近的速度，大约再攒 $days 天就能实现啦！」/「📅 At your recent pace, about more $days ${if (days == 1) "day" else "days"} to go!」；estPending「📅 开始赚积分后，这里会告诉你大概几天能实现～」/「📅 Once you start earning points, we'll show roughly how many days it will take」；depositButton(available)「💰 存入积分（当前可用 $available 分）」/「💰 Save points ($available available)」；celebrate(name)「🎉 心愿达成！\n$name」/「🎉 Wish achieved!\n$name」；depositSuccess(amount,name)「✅ 已存入 $amount 积分到「$name」」/「✅ Saved $amount ${pointsWord(amount)} into “$name”」；insufficient「积分不足，无法存入」/「Not enough points to save」；completedLine(saved)「已达成 · 共存入 $saved 分」/「Achieved · $saved ${pointsWord(saved)} saved」；depositDialogTitle(name)「💰 存入积分到「$name」」/「💰 Save points into “$name”」；depositDialogInfo(available,remaining)「当前可用积分：$available 分\n还差 $remaining 分达成心愿」/「Available: $available ${pointsWord(available)}\n$remaining ${pointsWord(remaining)} to go」；amountLabel「存入积分数量」/「Amount to save」；allRemaining「全部差额」/「All remaining」；invalidAmount「积分不足或数量无效」/「Not enough points or invalid amount」；confirmDeposit「存入」/「Save」；复用 `common.cancelButton`。

`ParentWishGoalStrings`：title「🐷 心愿目标管理」/「🐷 Wish goal management」；hint（原文照录）/「💡 Set a big goal for your child (e.g. a new tablet, a trip). Your child saves points into it step by step and redeems it once complete — building delayed gratification.」；emptyTitle「还没有心愿目标」/「No wishes yet」；emptyHint（原文）/「Tap the button below to add sample wishes (a small toy / a new tablet / a Disneyland trip), then adjust as needed.」；samplesLoaded「✅ 已设置示例心愿」/「✅ Sample wishes added」；loadSamplesButton「✨ 一键设置示例心愿」/「✨ Add sample wishes」；addOwnHint「或点击右下角 + 自行创建」/「Or tap + at the bottom right to create your own」；newWishContentDescription「新建心愿」/「New wish」；deletedMessage(name)「已删除「$name」」/「Deleted “$name”」；savedMessage「✅ 已保存」/「✅ Saved」；activeLine(saved,target)「进行中 · $saved/$target分」/「In progress · $saved/$target ${pointsWord(target)}」；achievedBadge「🎊 已达成」/「🎊 Achieved」；dialogTitleNew「新建心愿目标」/「New wish goal」；dialogTitleEdit「编辑心愿目标」/「Edit wish goal」；nameLabel「心愿名称（如：一台新平板）」/「Wish name (e.g. a new tablet)」；descLabel「描述（可选）」/「Description (optional)」；targetPointsLabel「目标积分」/「Target points」；iconPickerLabel「选择图标」/「Choose an icon」；复用 `taskEdit.saveButton`/`common.deleteContentDescription`/`common.cancelButton`。

**替换要点**：ChildWishGoalScreen 主 composable + WishGoalCard + CompletedGoalCard + DepositDialog 各读所需 vals（strings / displayLanguage）；`goal.name`/`goal.description` 渲染包 `localizedPresetText`（示例译出、自建原样）；`celebrationMsg` 用 `strings.celebrate(localizedPresetText(goal.name, displayLanguage))`；`depositPoints(goal.id, amount)`/`getGoalById` 等写库路径不动；`"$percent%"`/数字不动。ParentWishGoal 页：FAB cd、提示、空态、示例按钮/提示、删除 snackbar（`removedMessage` 式命名规避 `delete` sink 词根 → 本表字段名用 `removedMessage`）、保存 snackbar、GoalManageCard 状态行、删除 cd、对话框标题/标签/保存/取消；3 个示例 `WishGoalEntity(...)` 字面量**不动**（Ruling 25）；卡片 `goal.name` 包注册表（示例译出）。

**测试**（`WishCopyTest.kt` 新建，类 KDoc 按 W5 风格）：`childWishGoalCopyDiffers`（静态全对）、`childWishGoalInterpolatedCopyPinsBothLanguages`（含 celebrate 换行/「」与“”引号、one/other）、`parentWishGoalCopyDiffers`、`parentWishGoalInterpolatedCopyPinsBothLanguages`。预期 **191/0**。

---

### Task 3: 周报总览 + 成长报告（WeeklyTrackerStrings 11 + GrowthReportStrings 25）

**表值**：WeeklyTracker……（titleParent/titleChild/weeklyPointsLabel/totalBalanceLabel/basicsTitle/enhanceTitle/habitTitle/dailySummaryTitle/pointsRowLabel/noTasks + `weekdayNames: List<String>` 7 条 ZH 周一…周日 / EN Mon…Sun，如计划前文所列）。GrowthReport：title/heading/subtitle/shareButton/shareHint/六标签/六个数值子模板 + 两个鼓励子句 + `reportText`（7 参函数，ZH 8 行分享文案照录；EN 见测试）+ `petStage` 组合（emoji + 原样阶段名，阶段名归 W7）。

**替换要点**：WeeklyTracker `task.title` 渲染包注册表；`dayNames` 常量删除、改用 `strings.weekdayNames`（两处表头）；数字模板不动。GrowthReport：`ReportStat(...)` 六个 label/value/subtitle 用表函数；分享按钮文案/提示；`reportText` 用 `strings.reportText(lifetimePoints, streak, petStage, completedIds.size, tasks.size, exchanges.size, achievedWishes)`；`petStageName()` 与其 8 个阶段名**不动**（W7 归属）。

**测试**：`weeklyTrackerCopyDiffers` / `weeklyTrackerInterpolatedCopyPinsBothLanguages`（含 weekdayNames 逐条两语）/ `growthReportCopyDiffers` / `growthReportInterpolatedCopyPinsBothLanguages`（reportText 整串两语钉死，含宠物阶段原样中文出现的预期）。预期 **195/0**。

---

### Task 4: 证明审核 + 证明提交 + 照片查看（15 + 11 + 3）

**表值**：按计划前文所列（status 三标签、filter/empty、`taskFallback(id)`、approved/rejected snackbar、`noteLine(note)`、拒绝/通过按钮（「通过 (+5分)」照录 /「Approve (+5 points)」）、`photoContentDescription`；capture 页 11 条；photo viewer 3 条）。

**替换要点**：ParentProofReview `taskNames[...] ?: strings.taskFallback(id)` → 再包 `localizedPresetText`（`localizedPresetText(taskName, displayLanguage)` 只对映射值；fallback 已是本地化文案）；`proof.note` 原样（用户内容）；`approveProof/rejectProof` 不动。ChildProofCapture `taskTitle` 渲染包注册表（参数是 canonical 任务标题）；`Platform.camera.takePhoto`/`addProof` 不动。PhotoViewer 三处替换。

**测试**：每表 differs+pins 共 6 条。预期 **201/0**。

---

### Task 5: 周报通知（WeeklyReportStrings 6 + commonMain suspend + **androidMain 授权改动**）

**表值**：title「每周学习周报」/「Weekly learning report」；body「本周学习周报已生成，来看看孩子的学习情况吧！」/「Your child's weekly learning report is ready — take a look!」；summary(checkins,focusMin,reviewCount,earned) ZH「📊 孩子本周学习周报：打卡${checkins}次 · 专注${focusMin}分钟 · 复习${reviewCount}道错题 · 获得${earned}积分」/ EN「📊 Your child's week: ${checkins} check-ins · ${focusMin} min focused · ${reviewCount} mistakes reviewed · ${earned} ${pointsWord(earned)} earned」；encourageLow「。本周学习较少，多鼓励孩子加油！」/「. A lighter week — give your child some encouragement!」；encourageGood「。继续保持，稳步进步！」/「. Keep it up — steady progress!」；fallback「📊 孩子本周学习周报已生成，打开 App 查看详细报告。」/「📊 Your child's weekly report is ready — open the app for details.」

**实现**：
1. commonMain `WeeklyReportReceiver`：`schedule()` → `suspend fun schedule(hour: Int = 20)`，开头 `val lang = currentDisplayLanguage(); val strings = stringsFor(lang).weeklyReport`，`title = strings.title`、`body = strings.body`；删除 `TITLE`/`BODY` 常量（改为软删除？外部引用检查：grep 无外部引用后删除）；KDoc 更新（iOS 排程冻结、Android 触发时取词、已知残留：中文渠道默认标题 + AndroidReminderBootstrap 重注册，与 W5 钟类同）。
2. androidMain `platform/WeeklyReportReceiver.kt`（**授权**）：在协程内 `val lang = currentDisplayLanguage(); val strings = stringsFor(lang).weeklyReport`，`message = strings.summary(checkins, focusMin, reviewCount, earned) + if (empty) strings.encourageLow else strings.encourageGood`；catch 分支用 `strings.fallback`；其余统计/调度逻辑不动。
3. `Strings.kt` 加 `weeklyReport`。
4. 测试：`weeklyReportCopyDiffers`（title/body/encourageLow/encourageGood/fallback 五个静态对）+ `weeklyReportSummaryPinsBothLanguages`（ZH 整串含「·」分隔；EN 含 one/other 与 `·`；空周与非空周拼接各一）。预期 **203/0**。

**注意**：平台文件仅此一处改动；`AndroidNotificationScheduler` 不动（标题残留已在 W5 文档记录）。

---

### Task 6: 闭集 + 跨表 + 闸门 + 文档 + 四闸 + 唯一 W6 commit

- [ ] 闭集：按各表 EN 静态值含 `point(s)` 者逐任务已加（T2…T5 各自注册）；T6 只核验总数（预期：childWishGoal 若干、parentWishGoal 0、weeklyTracker 2、growthReport 3、proofReview 1、capture 0、photoViewer 0、weeklyReport 0——以实跑闭集守卫为准）。
- [ ] 跨表 pin（`WishCopyTest` 追加）：`common.cancelButton`/`deleteContentDescription`、`taskEdit.saveButton` 三对。
- [ ] 闸门：`W6_REV = "1e1e382"`；`BRANCH_PAGES` +7（七页；androidMain 不登记，沿用 W1 先例）；`BRANCH_TABLES` +8；`DISCLOSED_RESIDUALS` +11（示例心愿 6 + 宠物阶段名 8？→ 6+8=**14**，以实跑为准）；读数注释与文件头（四十二张表）、最早 rev 口径、`--tables-rev` help。
- [ ] 文档：V-58…V-64（心愿存钱罐 / 心愿管理 / 周报总览 / 成长报告（含宠物阶段名 W7 残留）/ 证明审核 / 证明提交+照片查看 / 周报通知（Android 标题与重注册残留见 L-16））；§1/§6/L-19 读数（预期 registry 56→70、branch 命中 69、unused 64；页面 39→46 / 文件 38→45 / 表 34→42——全部以实跑为准）。
- [ ] 四闸顺序全绿；**203/0/0**。
- [ ] 唯一 commit（文件清单 = 8 表 + Strings + 7 页 + common 接收器 + androidMain 接收器 + 2 测试文件（WishCopyTest 新建 + PresetRegistryTest + StringsCopyTest）+ 脚本 + 文档；以 `git diff --cached --stat` 实数为准），消息：`feat(i18n): 阶段 2 W6——心愿/证明/报告/周报七页双语化（8 张表）、注册表扩示例心愿、周报通知发出时取词、等价闸门登记`。**不推送**。

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译（含 androidMain 改动）、203 单测、字节等价闸门整枝组、存储边界、中文态逐字节不变、注册表 6 值两语解析、周报摘要两语钉死。
- 不可自动证明：英文态七页视觉呈现与通知实际弹出语言（人工 V-58…V-64）；宠物阶段名英文态仍为中文（W7 归属，V-61 明示）；Android 通知标题为中文渠道默认（L-16 已记录，W9）。
- 不以"CI 绿"表述"双语已验证"。
