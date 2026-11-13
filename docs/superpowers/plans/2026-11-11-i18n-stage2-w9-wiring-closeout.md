# i18n 阶段 2 W9——注册表接线收尾 + 分享标题 + 通知接线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 阶段 2 终波：完成阶段 1 页面的 task/reward 标题渲染 sweep、成就解锁 snackbar 接 `achievementCopy`、设备名/同步与备份诊断渲染映射（含插值同步消息正则适配器）、**AppGraph 两处通知接线**、**androidMain 4 文件授权接线**（AndroidFileSharer 分享标题 / AndroidReminderBootstrap 重注册取词 / ScheduleAlarmReceiver 与 AndroidNotificationScheduler 通知标题）、等价闸门字段级披露机制收口、术语与文档终审清理；测试 219→~225，唯一 W9 commit。

**Architecture:** 沿用方案 A。所有 canonical 值（任务/奖励标题、成就名、设备名、诊断消息）只经注册表在渲染点本地化；后台发出点用 `currentDisplayLanguage()`+`stringsFor(...)`；插值同步消息用**钉死正则适配器**（spec §9）。平台文件改动**仅 4 个已授权 androidMain 文件**（用户 2026-11-12 授权，仅文案/取词，不动逻辑）。

**Tech Stack:** KMP + CMP 1.7.3、Kotlin 2.1.20；commonTest；Python 闸门。

**Spec:** `docs/superpowers/specs/2026-11-09-hstudy-bilingual-stage2-design.md`（§3 分享标题例外、§8 通知、§9 诊断映射、§12 W9、§13、§14）

## Global Constraints

- **Ruling 25**：注册表/文案不得进入写库/bizKey；`FamilySyncHelper`/`BackupHelper` 返回的 canonical 字符串不改（data/** 零改动），显示侧映射。
- **平台文件（用户 2026-11-12 授权，仅这 4 个）**：`androidMain/platform/AndroidFileSharer.kt`、`androidMain/platform/AndroidReminderBootstrap.kt`、`androidMain/platform/ScheduleAlarmReceiver.kt`、`androidMain/platform/AndroidNotificationScheduler.kt`。仅文案取词/标题传递，不动统计、调度、id、渠道注册逻辑。此前已授权的 androidMain `ReviewReminderReceiver.kt`/`WeeklyReportReceiver.kt` 可在本波补 `titleOverride`（属既有授权范围的通知本地化）。
- **不动**：`data/**`、schema、`DateTimeCompat.kt`、`Plurals.kt` 机制、iOS 源集（分享标题 iOS 无槽，不改）。
- AppGraph 是 commonMain：`bootstrap()` 非 suspend；接线需 `runBlocking { … }` 或把两段改为 `init()` 外的协程（评估后择一，**不阻塞主线程**；若 runBlocking 会阻塞启动则改用 appScope）。
- 唯一改动形态：`"文本"` → `strings.xxx` / 注册表渲染 / 通知取词；逻辑零变化。
- 闸门收口（L-19）：为 `DISCLOSED_RESIDUALS` 增加可选 `table_field` / `page_path` 限定（向后兼容：无限定=按值 pop，同现状），并把 9 条 both-side 通用词与 5 条通用词 page-only 改为限定匹配；selftest 增加限定语义断言；其余条目保持值级并在 L-19 说明。
- 闭集逐任务加；每波四闸；T4 唯一 commit；不推送。
- 环境：Gradle 内联 JAVA_HOME/GRADLE_USER_HOME；Python `PYTHONIOENCODING=utf-8`。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `.../ui/child/ChildTaskListScreen.kt`（改） | `task.title` 渲染点包注册表；成就解锁 snackbar 标题走 `achievementCopy` |
| `.../ui/parent/ParentSettingsScreen.kt` 等（按 grep，改） | 同步/备份成功消息与诊断消息渲染包 `localizedPresetText`；插值同步消息走适配器 |
| `.../i18n/SyncDiagnosticAdapter.kt`（新建） | 同步摘要正则适配器（识别 canonical 格式重组英文；deviceName 再走回退映射） |
| `.../AppGraph.kt`（改，commonMain） | bootstrap 两处通知取词（9001 用 ReviewReminderStrings、9002 用 WeeklyReportStrings） |
| `androidMain/platform/AndroidReminderBootstrap.kt`（改，授权） | 9001/9002/日程重注册按语言取词 |
| `androidMain/platform/ScheduleAlarmReceiver.kt`（改，授权） | `titleOverride = EXTRA_TITLE` + 兜底标题取词 |
| `androidMain/platform/AndroidNotificationScheduler.kt`（改，授权） | 接受/使用 titleOverride（若已支持则仅确认），按语言兜底标题 |
| `androidMain/platform/AndroidFileSharer.kt`（改，授权） | 分享标题读缓存生效语言 |
| `androidMain/platform/ReviewReminderReceiver.kt`、`WeeklyReportReceiver.kt`（改，既有授权） | showNotification 传 `titleOverride`（本地化标题） |
| `Composite/Docs`（改） | spec §13 Wish Jar→Wish Pool；L-16/V-64/V-25/L-04 更新为 W9 完成；L-19 更新；PetStrings dead 字段删除（含披露/测试清理） |
| `scripts/i18n_template_diff.py`（改） | 字段级披露机制 + W9 登记 + 读数 |
| `docs/i18n-stage1-verification.md`（改） | V-73…V-76 + 全量读数 |

---

### Task 1: 阶段 1 页 sweep + 成就 snackbar + 诊断适配器（commonMain）

- [ ] 全仓 grep 阶段 1 已迁页面（role/settings/childHome/taskList/focus/pomodoro/mistake 等）中**未包注册表**的 `task.title`/`reward.name`/`taskName` 渲染点，逐一包 `localizedPresetText`（写操作参数不动）；已知至少 `ChildTaskListScreen.kt:259`。
- [ ] `ChildTaskListScreen` 成就解锁 snackbar：`newlyUnlocked.map { it.title }` → 按 `it.type` 走 `achievementCopy[displayLanguage]?.get(type)?.title ?: it.title`（snackbar 文案本身已本地化）。
- [ ] `SyncDiagnosticAdapter`（新建 `.../i18n/SyncDiagnosticAdapter.kt`）：
```kotlin
/**
 * 同步/备份诊断消息的渲染侧映射（spec §9）：固定文案精确命中走注册表；
 * 唯一插值消息「已应用${deviceName}的设置：${n}个任务、${m}个奖励」用钉死正则识别
 * canonical 格式并重组英文；deviceName 为「家长设备/孩子设备」时再走回退映射；
 * 不命中（用户数据/历史消息）原样返回。
 */
object SyncDiagnosticAdapter {
    private val APPLIED = Regex("^已应用(.+)的设置：(\\d+)个任务、(\\d+)个奖励$")
    fun localize(raw: String, lang: DisplayLanguage): String
}
```
  - ZH 返回 raw（恒等）；EN 命中正则 → `"Applied settings from $deviceName: $tasks tasks, $rewards rewards"`（deviceName 经 `localizedPresetText`；数字用 `tasksWord` 词形？规范：`$tasks tasks` 用 tasksWord(tasks)）；未命中 → `localizedPresetText(raw, EN)`。
  - `ParentSettingsScreen`（及 grep 到的其它显示点）：`result.getOrNull()` 等消息渲染 → `SyncDiagnosticAdapter.localize(raw, displayLanguage)`。
- [ ] 测试（`W9CopyTest.kt` 新建）：`taskRewardTitlesWrappedPinned`（抽样 3 个既有 canonical 标题经 localizedPresetText 两语）；`achievementSnackbarTitlesResolve`（first_checkin 等 3 条 EN）；`syncAdapterPinsBothLanguages`（canonical 样例「已应用家长设备的设置：3个任务、2个奖励」→ EN 整串；「已应用爸爸的手机的设置：…」原 deviceName 原样；未命中原样；ZH 恒等）+ 格式钉死（构造字符串与 FamilySyncHelper.kt:172 的格式一致，注释引用行号）。预期 **~222/0**。

---

### Task 2: 通知接线（AppGraph + 授权 4 androidMain 文件 + 两个 receiver titleOverride）

- [ ] **AppGraph**（commonMain）：bootstrap 的 9001/9002 两段改为按语言取词——9001：`stringsFor(lang).reviewReminder` 的 title/defaultBody；9002：`stringsFor(lang).weeklyReport` 的 title/body；`lang = currentDisplayLanguage()`（bootstrap 非 suspend：若阻塞风险低用 `runBlocking`，否则注入 `appScope.launch { … }`；选型在实现报告说明，**不得阻塞主线程**）。
- [ ] **AndroidReminderBootstrap**（授权）：`rescheduleAll()` 的 9001/9002 与 `toReminder()` 的日程 title/body 按语言取词（suspend 上下文直接 `currentDisplayLanguage()`；`title = localizedPresetText(schedule.title, lang)`；body 用 `ScheduleAlarmStrings` 同 W5 规则）。**注意** `toReminder` 是同步函数：改为 suspend 或把 lang 参数传入（调用方是 suspend）。
- [ ] **ScheduleAlarmReceiver**（授权）：`showNotification(..., titleOverride = title)`（title 来自 EXTRA_TITLE，排程时已是本地化值）；兜底 `?: "日程提醒"` → 按语言取（`stringsFor(currentDisplayLanguage()).scheduleAlarm` 新增 `fallbackTitle` 字段？——**新增 1 字段** `alarmTitle` "日程提醒"/"Schedule reminder"，并同步 W5 表与测试）。
- [ ] **AndroidNotificationScheduler**（授权）：`showNotification` 已接受 `titleOverride`；确认 `titleOverride ?: perKindDefault` 的用法正确（若 titleOverride 已有但未生效，修正取用位置）。
- [ ] **ReviewReminderReceiver.kt / WeeklyReportReceiver.kt**（androidMain，既有授权）：`showNotification(..., titleOverride = <本地化标题>)`（review：`stringsFor(lang).reviewReminder.title`；weekly：`stringsFor(lang).weeklyReport.title`）。
- [ ] **AndroidFileSharer**（授权）：`Intent.createChooser(sendIntent, "分享文件")` → 读缓存生效语言（`AppGraph` 暴露的 startup/current 语言快照，若只有 startupLanguage 则用它；标题新增 i18n 常量 `ShareStrings.shareFileTitle` ZH "分享文件"/EN "Share file"——放 `.../i18n/` 小对象）。
- [ ] 测试：`shareTitlePinned`（两语两值）；`alarmTitlePinned`（W5 表新字段两语）；平台接线本身不可单测 → 由 `assembleDebug` + V-73/V-74 人工项覆盖。预期 **~224/0**。

---

### Task 3: 闸门字段级披露收口（L-19）

- [ ] `scripts/i18n_template_diff.py`：`DISCLOSED_RESIDUALS` 条目支持可选键 `table_field`（如 `"CommonStrings.cancelButton"`）与 `page_path`（相对路径）；`apply_disclosures` 语义：带限定 → 只从对应侧的**该字段/该文件**值集合中 pop；不带限定 → 保持现行按值 pop（向后兼容）。both-side 9 条通用词改为 `table_field` 限定（字段名以注册表/表定义实际字段为准）；5 条通用词 page-only（优秀/良好/不错/起步/待开始 或 种子/开花/结果——以实跑清单为准）改为 `page_path` 限定。
- [ ] selftest 增加：限定 pop 不影响其它值的正/反例（至少 4 条）。
- [ ] L-19 文档更新：机制已支持字段/页面限定、已迁移条目清单、剩余值级条目的范围与理由。
- [ ] 预期：`--branch` 仍 exit 0，读数与迁移前一致（限定不改变实际命中，只收紧作用域）；selftest 断言数上升（以实跑为准）。预期 **~224/0**（无新测试）。

---

### Task 4: 文档/术语收口 + 四闸 + 唯一 W9 commit

- [ ] spec §13："心愿存钱罐 Wish Jar" → "Wish Pool"（术语校正，与阶段 1 锁定一致）。
- [ ] `PetStrings` dead 字段 `feedLabel`/`playLabel`：删除字段/ZH/EN/测试引用/2 条 table-only 披露，读数同步（W8 终审 park 项，W9 清理）。
- [ ] 文档：L-16/V-64/V-25/L-04 更新为「W9 已完成」（通知标题/重注册/解锁 snackbar 已接线）；新增 V-73 通知标题与重注册随语言（Android 通知标题为本地化标题；冷启动/开机重注册按语言）、V-74 分享标题随语言、V-75 同步/备份诊断消息英文（固定 7 条+插值摘要+设备名回退）、V-76 阶段 1 页面 task/reward 标题 sweep（抽样页确认英文）；§1/§6/L-19 读数按实跑。
- [ ] 四闸全绿；**~224/0**（以实跑为准）；唯一 commit：
  `feat(i18n): 阶段 2 W9——阶段 1 标题 sweep、通知与分享标题随语言、诊断消息映射、闸门字段级披露收口`
  **不推送。**

---

## 验证上限（诚实声明，spec §11）

- 可自动证明：编译（含 4 个授权平台文件）、单测、字节等价闸门整枝组、存储边界、中文态逐字节不变、适配器/标题解析两语 pin、闸门限定语义自测。
- 不可自动证明：Android 通知实际标题/分享面板标题/开机重注册的实际弹出语言（V-73/V-74 人工项）；同步/备份诊断在真实数据下的显示（V-75）。
- 不以"CI 绿"表述"双语已验证"。