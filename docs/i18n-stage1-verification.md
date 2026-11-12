# 双语第一阶段：人工验证清单与已知限制

分支 `feature/i18n-stage1`（阶段一，v3.1.0 的前半段，**未发布**）。
配套设计文档：`docs/superpowers/specs/2026-11-07-hstudy-bilingual-stage1-design.md`（下称 **spec**，§N 指其章节）。
实施计划：`docs/superpowers/plans/2026-11-07-hstudy-bilingual-stage1.md`（下称 **plan**）。

本文件的两块内容：

- **§2 人工验证清单**：所有"流水线证不了、只能上眼睛"的渲染命题，逐条给出做法与不成立时是什么问题。
- **§3 已知限制**：阶段一覆盖什么、不覆盖什么，以及存储边界带来的既成后果。

> 出处约定：本清单里的证据只引用**可持久坐标**（spec/plan 的节号、代码 `file:line`、脚本命令）。
> 这些条目最初来自 SDD 台账（`.superpowers/sdd/…/progress.md`）与各任务报告里标注
> "待人工验证 / deferred-to-manual-list"的行，但 `.superpowers/` 是 gitignore 的临时目录，
> 不构成长期引用（plan Ruling 35 的裁决），故逐条改写成代码坐标落在此处。

## 1. 为什么必须人工验证（spec §9）

阶段一**没有任何自动渲染验证**，原因是环境事实而非偷懒：

| 缺口 | 事实 | 复核命令 |
|---|---|---|
| 无设备 | 本机没有挂载任何 Android 设备，也没有启动中的 emulator（AVD 存在，但 C: 剩余空间不足以安全启动一个） | `"$ANDROID_SDK/platform-tools/adb.exe" devices`，`$ANDROID_SDK` 取 `local.properties` 里的 `sdk.dir`（期望：`List of devices attached` 下面为空。`adb` 本身不在 PATH 上，所以要给绝对路径） |
| 无 UI 测试依赖 | 仓库没有 Compose UI 测试依赖，也没有 `androidTest` / `iosTest` 源集 | `grep -rn -e "compose.test" -e "androidTest" composeApp/build.gradle.kts`（期望：0 命中） |
| Apple 目标不可本机编 | Windows 上只能编 commonMain 元数据与 Android 目标；iOS 链接/打包只在 macOS CI 上发生 | `./gradlew :composeApp:compileCommonMainKotlinMetadata` |
| Android CI 无模拟器 | CI 跑 `assembleDebug` + `testDebugUnitTest`，不跑设备 | `.github/workflows/android.yml` |

**能自动证明的只有**（下列命令全部在当前树可复现，退出码 0）：

```bash
python scripts/i18n_storage_boundary.py                          # 文案不进写库调用
python scripts/tests/i18n_storage_boundary_selftest.py            # 上一条每条检测通道都有必红 fixture
python scripts/i18n_template_diff.py                              # 中文态逐字节等价（默认组：distinct 118/118，其中 [strict] 整串逐字节相同 117 条 + 1 条已披露的格式串拆分位点）
python scripts/i18n_template_diff.py --branch                      # 同一命题的**整枝组**（39 条页面组条目 / 38 个文件 × 全部 34 张表）：distinct 831/795，披露后 782/782，[strict] 781 条整串相同 + 1 条拆分
./gradlew :composeApp:testDebugUnitTest                            # 186 tests / 0 failures（含阶段 2 W1 的 12 条、W2 的 9 条、W3 的 9 条、W4 的 8 条与 W5 的 14 条；i18n 包另见 §1 末段）
./gradlew :composeApp:compileCommonMainKotlinMetadata              # commonMain 元数据 + 平台纯净度
grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l   # 0
```

（编码环境注：**已不需要任何前置环境变量**。终审 D-B 把护栏写进了四个脚本本身——每个脚本在模块执行期
调用 `_force_utf8_streams()`，用 `hasattr` 守卫着把 stdout 与 stderr 一起 `reconfigure("utf-8")`。
在此之前，本机默认 GBK 控制台上直接把 `i18n_template_diff.py` 接进管道会在打印 `⟨$⟩` 残差说明处抛
`UnicodeEncodeError` 而**退出码 1**（症状是闸门红、根因是编码）。修复后实测：`PYTHONIOENCODING` 不设、
输出接管道（此时 `sys.stdout.encoding` 仍是 `gbk`）四条命令全部 **exit 0**。）

单测能证明的是**表值**（漏译、one/other、emoji 位置、规范值漂移），证明不了**页面是否读到表**，
也证明不了排版。`AppLanguageResolveTest`（6 条）覆盖解析纯函数、`LanguagePreferenceTest`（2 条：
`languagePersistsAcrossReads`、`languageRewriteReplacesPreviousChoiceAndLocksWireFormat`）
覆盖偏好读写与线格式；`StringsCopyTest`（**63** 条）覆盖**三十四张表**的文案——三十四张是聚合根
`AppStrings` 的字段数（`role / settings / common / taskList / pomodoro / focus / childHome / parentHome /
parentDashboard` 九张 + 终审 Ruling 41 新加的 `parentGate` + 阶段 2 W1 的 `mistake / mistakeList /
mistakeDetail / mistakeAdd / parentMistakeReview / reviewReminder` 六张 + W2 的 `parentTaskManage /
parentTaskEdit / parentAssignTask / parentTaskTemplate` 四张 + W3 的 `parentPoints / parentApproval /
parentRewardEdit / parentRewardManage` 四张 + W4 的 childRewardStore / childLuckyDraw / childPoints / childAchievements 四张 + W5 的 childDailyTarget / parentSchedule / scheduleAlarm / childHabitChallenge / childGoalTracker / childScheduleTimeline 六张），复核：
`grep -c "^    val " composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/Strings.kt` → 34。
原文写的是"六张表 / 59 条"，那是 Task 10 之前的读数（终审第 7 条点出，本波次复算后按当前树改正；
`StringsCopyTest` 的 4 条新增 = `parentGateCopyDiffers`、`parentGateButtonsComeFromTheirOwnTables`、
`totalFocusLabelIsTheSameTwoWordsInBothTables`、`englishTableUsesOneEllipsisForm`）。
W1 的漏译/跨表守卫另在 `MistakeCopyTest`（11 条，含 `crossTableSharedLabelsPinned`），
W2 的另在 `ParentTaskCopyTest`（9 条，含 `crossTableVocabularyPinned` 与英文 one/other 逐字钉死）。
W3 的漏译/跨表守卫另在 `ParentPointsCopyTest`（9 条，含 `crossTableVocabularyPinned`、`rewardCategoryOptionsAreAllRegistered` 与英文 one/other 逐字钉死）。
W4 的孩子端四页守卫另在 ChildRewardStoreCopyTest（8 条，含 crossTableVocabularyPinned 与英文 one/other 逐字钉死）。
W5 的六源守卫另在 ChildScheduleCopyTest（13 条，含通知正文双语钉死与跨表 pin）。

## 2. 人工验证清单

以下每条都是**未验证**状态：本分支没有任何一条由机器或本机会话亲眼确认过。
建议 Android（Android Studio 模拟器或真机）与 iOS（Xcode 模拟器）各走一遍。

### 2.0 验证前准备

- [ ] **V-00a 清除应用数据再开始**。阶段一**故意不回改**测试期写进库里的英文行
  （见 §3.4），残留的历史行会让"英文态显示中文"这类判断失真。
  Android：`adb shell pm clear <applicationId>` 或"设置 → 应用 → 清除存储"；iOS：删除 App 重装。
  出处：spec §5（存储边界）、plan Global Constraints"存储边界"条。
- [ ] **V-00b 记录安装包版本**。当前 `versionName 3.0.0 / versionCode 30000`
  （`composeApp/build.gradle.kts:89-90`、`iosApp/Info.plist:19-22`）——阶段一**没有**升版本号，
  所以 APK 文件名仍是 `HStudy-v3.0.0-debug.apk`，别把它当成已发布的 v3.0.0 回归包。
  出处：`docs/VERSIONING.md`"发布流程"（版本在 CI 绿的发布提交上才动）。
- [ ] **V-00c 构建**：`./gradlew :composeApp:assembleDebug`（APK 在 `composeApp/build/outputs/apk/debug/`）；
  iOS 用 `open iosApp/iosApp.xcodeproj` 跑模拟器。

### 2.1 语言切换机制

- [ ] **V-01 跟随系统**：系统语言改英文 + App 设置选「跟随系统」→ 首屏（角色选择）为英文。
  解析规则：`SYSTEM` 下 `systemLanguageTag()` 以 `zh` 开头 → 中文，**其余一切**（含 `fr`、空串以外）→ 英文，
  空串/纯空格 → 中文（spec §3，解析与注入在 `App.kt:69-72`；自动侧只有 `AppLanguageResolveTest` 的纯函数用例，
  平台桥接的真实取值没在设备上验证过）。
  **若首屏仍是中文**：先查 `systemLanguageTag()` 在该设备上返回什么（Android 取裸子标签，如 `en`）。
- [ ] **V-02 手动覆盖即时生效**：设置页 → 语言 → English → 返回首页与打卡页，文案变英文，
  **不需要重启 App**（spec §1 目标、§4 数据流：写偏好 → Flow 发出 → 根重组）。
- [ ] **V-03 切换即时性的例外要接受**：语言切换**不**重建导航栈、不打断进行中的番茄钟计时（spec §4）。
  "跑一轮中途切语言"在当前树上不可达（入口只在家长设置页、一次只组合一个目的地），文案何时变英文的
  逐条说明见 V-19/V-20/V-21 与 L-07。
- [ ] **V-04 持久化**：关闭 App（杀进程）重开 → 语言选择保持。
  存储线格式是 `"system" / "zh" / "en"`（`AppLanguage.toStorageValue()`），缺省视为 `system`。
- [ ] **V-05 首帧语言闪现**：构造"存储选择与设备语言相反"的设备（存储 `en` + 系统中文，**以及反向**），
  冷启动看角色选择页入场动画里是否闪一帧错误语言。
  已知机制：首帧 `initial` 用 `AppGraph.bootstrap()` 预读的偏好快照（`App.kt:43-53`，注释在函数体首行），
  这把窗口收窄到"预读尚未完成"，**但没有归零**（plan Ruling 10 明确"只收窄、非零窗口"）。
  闪现落在入场动画里、能被人眼看到是正常结果还是缺陷，需要人工判一次；
  若每次都能看到稳定的一帧错语言，说明预读没跑在 `App()` 组合之前。
- [ ] **V-06 语言分区三枚 chip 的选中态**：切换语言后**立刻**看这三枚 chip（`ParentSettingsScreen.kt:335-357`，
  `Row` + 三个 `FilterChip`，非滚动）选中的是否是高亮的那一枚、文案是否同步变英文。

### 2.2 英文态版式（长度 1.5–3 倍，无 maxLines 保护）

这一组是同一个风险族：**阶段一只改文案出处，没有权限动布局**，所以英文变长之后
是否截断/换行/挤压，代码层面完全未知（plan Ruling 14 把它判给"版式风险、不在等价替换范围内"）。
中文态那半侧有 `scripts/i18n_template_diff.py` 的逐字节等价证明，**英文态这一半侧没有任何自动证据**。

- [ ] **V-07 角色选择页（360 dp 手机 + 平板两分支）**：标题/标语/两张卡片的标题+副标题。
  重点看副标题：`RoleCard`（`RoleSelectScreen.kt:139`）里的 `Text` **没有** `maxLines`/`overflow`，
  而英文副标题（`I'm the parent` + `Assign tasks · Manage points · Set rewards` 一侧）明显长于中文。
  手机与平板走的是两个布局分支但**共用同一批字段**，所以平板单独截断是这页特有的风险。
  出处：spec §1（该页属阶段一）、plan Ruling 14。
- [ ] **V-08 语言分区三枚 chip @360 dp**：英文态 `System default` + `中文` + `English`
  三枚非滚动 `Row` 里的 chip（`ParentSettingsScreen.kt:335`）是否可见且可点。
  按字面宽度估算约 350 dp，减去卡片与页面 padding 后**很可能贴边**，必须实测。
  同处两行说明句（`languageNote`、`partialTranslationNote`，`:333`/`:366`）的换行也一并看。
- [ ] **V-09 英文态"部分页面未适配"提示行**：`ParentSettingsScreen.kt:365` 的条件
  `if (LocalDisplayLanguage.current == DisplayLanguage.EN)`。
  **中文态必须看不到这一行，英文态必须看到**（spec §7）。这条是组合期分支，
  单测只能证明表值存在，证明不了"只在英文态渲染"，所以只能人工看。
- [ ] **V-10 专注统计页三列 StatCard**：`ChildFocusStatsScreen.kt:59-63`（`Arrangement.SpaceEvenly` 里三张卡）
  与 `:72-75`、`:122`。英文标签 `Total focus`（终审 Ruling 44 前写作 `All-time focus`）/ `Sessions` + 值 `12 sessions` 在三等分宽度里是否溢出。
- [ ] **V-11 孩子首页评分维度条标签位**：`ChildHomeScreen.kt:846-848` 的 `ScoreDimensionBar`
  把标签放在**固定 `64.dp`** 宽度里，英文态 `✏️ Check-in` 之类是否截断。
- [ ] **V-12 积分徽章**：`PointsBadge`（`CommonComponents.kt:108`，`Row(spacedBy(4.dp))` + 三段 `Text`）
  在 Compact 宽度顶栏里放 `⭐ 12 points`（英文 6 个拉丁字符 vs 中文 1 个字）是否挤压。
  **注意**：英文 1 分会显示 `⭐ 1 points`，这是**已知限制 L-06**，不是本版新引入的 bug，别当缺陷修。
- [ ] **V-13 时分步进器**：`CommonComponents.kt:140-168`（数字位宽度 `:185` 的 `Modifier.width(52.dp)`），
  标签来自 `CommonStrings.hourLabel`/`minuteLabel`（英文 `hr` / `min`）。两个问题：① 缩写是否放得下；② **读屏**会不会把
  "Decrease hr" 念成"decrease hour"以外的东西（缩写对无障碍朗读的影响无法自动验证）。
- [ ] **V-14 时长 chip**：`ChildPomodoroScreen` 的专注/休息选项 chip，英文是 `15 min`（带空格）、
  中文是 `15分`（无空格）（spec §11"时长 chip"条）。同页 `🎯 Focus training · Schulte grid (warm-up before focusing)`
  这类长按钮文案，以及积分阶梯说明 `pointsLadderNote` 那一长串是否溢出。
- [ ] **V-15 长 snackbar 的时长与截断**：撤销打卡扣回（`↩️ Check-in undone for … · 5 points removed`）
  与成就解锁两条，`SnackbarDuration.Short` 是否够读完。
- [ ] **V-16 系统通知栏文案（仅限番茄钟常驻通知这一条）**：`PomodoroTimer.kt:104-106` 在
  `LaunchedEffect` 协程里用 `currentStrings`（`rememberUpdatedState`，`:49`）现取 title/text，
  词源仍是 `LocalStrings`，表值由 `StringsCopyTest.runningNotificationCopyIsPinnedInBothLanguages` 钉住；
  渲染由系统完成，本机看不到内容。**注意覆盖面**：定时提醒类通知在**组合期之外**触发，W1 的错题复习提醒现状是——
  Android 在**触发时**重新按当前语言取词与正文（ZH 态标题沿用渠道默认 `HStudy 错题复习提醒`，与 v3.1.0 逐字节一致；
  EN 态标题/正文均为英文）；iOS 消费**排程时**冻结的本地化文案，但 `AppGraph.bootstrap()` 每次冷启动都用硬编码中文
  重注册提醒 9001，本地化文案只存活到下次重启前（W9 残留，spec §12）；周报提醒与启动补挂仍是中文，
  日程提醒只在"保存/启停 → 下次启动/开机"之间存活（见 **L-16**），
  本条**不**替你确认那几路。
- [ ] **V-17 中文态零位移回归**：以上每一页在**中文态**下应与迁移前逐字、逐位一致
  （`i18n_template_diff.py` 证明字节相同，但"字节相同"不等于"排版没动"——
  字段拆分可能改变组合期布局输入，需人眼确认徽章行、chip 行没有回流）。
- [ ] **V-18 平板/折叠展开宽度**：V-07…V-16 里凡是走 `ResponsiveContent` 双分支的页面，
  Medium/Expanded 分支单独再看一遍（两分支共用字段，但共用字段也共用长度问题）。

### 2.3 运行中切换语言：现在**无法**在组合期内做到（可达性 + 两条仍然成立的取舍）

> **本波次重写这一节的原因（终审第 7 条）**：原先 V-19/V-20/V-21 描述的是 **Task 7b 之前**的
> 陈旧行为（`git show d282cb3` 的提交信息就写着"并修语言切换后的进行中文案陈旧"）。
> 当前树上 `PomodoroTimer.kt:49` 已经是 `val currentStrings by rememberUpdatedState(strings)`，
> `:142-150` 的半场欢呼候选项每次组合都从 `strings` 重新取词、`remember` 只存**索引**，
> 通知文本读的也是 `currentStrings`——即"进行中的番茄钟保持旧语言"这件事**已经被修掉了**。
> 照原样留着这三条，验证人会把已被修好的行为当成回归去报，所以逐条改写并说明上限在哪里。

- [ ] **V-19 语言切换在组合期是不可达操作（先确认这一点，再谈陈旧）**
  切换语言的唯一入口是家长设置页的三枚 `FilterChip`（`ParentSettingsScreen.kt:336`/`:343`/`:350`，
  `setAppLanguage` 写入点在各自的 `onClick` 里：`:339`/`:346`/`:353`），
  而 `AppNavGraph` 一次只组合**一个目的地**（`AppNavGraph.kt:58` 起的那个 `when {}` 分支）——
  孩子端的番茄钟页面与它不在同一棵组合树里，退出番茄钟页面时 `remember` 状态随之销毁。
  所以"跑一轮专注、中途切语言"这条动作在本版本**做不到**，不是难做，是没有路径。
  能做到的等价验证是：① 切语言 → 回角色页 → 重新进入番茄钟 → 文案是新语言；
  ② 配置变更（旋转）时正在跑的番茄钟：Android 会重建 Activity，`endTimeMillis` 之类的
  `remember` 状态丢失，这属 §2.1 的 V-04 一族，不是语言问题。
- [ ] **V-20 完成回调 snackbar 的语言（仍然存在的上限，但成因换了）**
  `onComplete` 是宿主页面传入的回调，`rememberUpdatedState` 只覆盖 `PomodoroTimer` 组件内部；
  由于 V-19 那条不可达，这一处实际只会在"回调跨越一次配置变更重建"时才可能旧语言。
  预期：看不到旧语言。看到了就是新的回归，值得报。
- [ ] **V-21 专注目标选中值**（这条**不受** Task 7b 影响，取舍仍然成立）
  `focusTarget` 的 `remember` key 刻意不含 `strings`（否则切语言会把用户已选目标冲掉），
  所以选中值本身是旧语言那个串。当前只在"重建组合树"时可见。
  存储侧写的是规范中文（见 §3.4），所以只影响显示、不影响判等。

### 2.4 阶段一**预期**中的中英混排（不是缺陷，但必须先确认"看起来像有意为之"）

英文设备上看到中文是阶段一的既定代价（spec §1 非目标第 1 条）。这一组要做的是确认
混排的位置就是"计划没覆盖的位置"，而不是"迁移页面漏了字"。

- [ ] **V-22 家长审批列表整体中文**：`ui/parent/ParentApprovalScreen.kt` 当前 **0 处 `strings.` 引用**
  （复核：`grep -c "strings\." composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentApprovalScreen.kt` → 0）。
  该行英文态会同时出现：`approval.description` 原样中文（`:182`）、`+N分` 原样中文（`:186`）、
  `通过`/按钮文案原样中文（`:196`）。其中专注类描述的中文是**存储的规范值**（见 §3.4），不是漏译。
  出处：plan Ruling 27（定为阶段二）。
- [ ] **V-23 效率报表整体中文**：`ui/habit/ChildEfficiencyReportScreen.kt` 同样 0 处 `strings.`，
  且 `:335` 按 `taskTitle` 分组——测试期写入的英文行与规范中文行会**裂成两根柱**（见 §3.4）。
  出处：plan Ruling 25③、Ruling 27。
- [ ] **V-24 孩子首页露出的中文等级词/鼓励语**：`ChildHomeScreen.kt:289` 直接把
  `LearningScore.grade` 与 `encouragement` 插进句子（值出自 `ui/common/LearningScore.kt:36-50`
  的中文 `when`，如 `优秀`、`今天学习超棒！…`）；`:143` 的 `LevelSystem.getLevelInfo`、
  `:196` `AnimatedPointsDisplay`、`:200` `StreakBadge`、`:339` `PetPreviewCard`、`:388` `AchievementBadges`
  同理仍是中文。英文态孩子首页因此是"英文骨架 + 中文评语"。出处：spec §1 非目标第 1 条。
- [ ] **V-25 成就名**：`ui/common/AchievementManager.kt:29-42` 的 13 个成就标题是 Kotlin 字面量
  （`启航`、`三日不辍`、`周冠军`…），英文态解锁句会念成 `🏅 Achievement unlocked: 启航!`
  （模板在 `i18n/TaskListStrings.kt:88-89`）。注意它们的**成因**与种子任务不同：
  成就名不是 spec §5 的数据库种子，只是不在阶段一的 13 个文件清单里（plan Ruling 21）。
- [ ] **V-26 种子任务/奖励标题**：数据库里 `title = "完成当天作业"` 一类的种子内容保持中文
  （spec §5 的非目标：种子随界面语言变会让同一 schema 因安装语言写入不同行）。
- [ ] **V-27 未迁移页里的公共控件**：`HStudyTopBar` / `PointsBadge` / `HourMinuteStepper` / `NumberStepper`
  已被阶段一双语化（spec §1 把"公共控件文案"列入范围），但它们被**未迁移页面**复用，
  于是英文设备会出现"中文页面里嵌英文控件"。典型点位：
  `ui/schedule/ParentScheduleManageScreen.kt:226-241`：`AlertDialog` 的标题 `添加日程`/`编辑日程`（`:228`）、
  标签 `时间`（`:233`）、`日程标题（如：起床、写作业）`（`:241`）全中文，中间夹 `07 hr : 00 min`（步进器调用 `:234`）。
  同页 `:45` 的 `contentDescription = "添加日程"` 与 `:141` 的 `"✅ 已添加日程"` 同理。
  （W5 后该对话框已双语化，本条示例已失效，保留作阶段 1 历史）
- [ ] **V-28 家长密码门对话框（Ruling 41 起已双语，验证"确实跟表走"）**：`ui/navigation/AppNavGraph.kt:253-288`
  的对话框原先整块中文（本清单在 Task 12 的新发现），终审 Ruling 41 已迁入
  `ParentGateStrings`（字段声明 `ParentGateStrings.kt:28-30` = 标题 / 输入框标签 / 校验失败提示，
  Zh/EN 两侧逐条在 `:34-36` / `:40-42`），
  两个按钮**复用既有字段**（`确定` ← `strings.settings.passwordOkButton`，`AppNavGraph.kt:284`；
  `取消` ← `strings.common.cancelButton`，`:286`）。
  复核：`python scripts/i18n_inventory.py navigation/AppNavGraph.kt` → `出现 0 次 / 去重 0 条`（本波次实跑 0）。
  人工侧要看的：英文态从**任意**家长入口触发门禁，三句 + 两枚按钮都应是英文；
  出现整块中文即是回归（曾是限制的这条已由 Ruling 41 关闭，见 L-09）。
- [ ] **V-29 Android 分享面板标题**：英文态导出家长设置/学习进度时，系统分享面板标题仍是中文「分享文件」
  （`composeApp/src/androidMain/…/platform/AndroidFileSharer.kt:27` 硬编码，`shareTextFile` 签名里没有标题位；
  `FamilySyncHelper.kt:249-260` 收下 `title` 参数就丢弃）。
  所以 `SettingsStrings.exportChooserTitle` / `ChildHomeStrings.shareProgressChooserTitle` 两份英文值
  **目前在两个平台上都渲染不出来**（iOS 的 `UIActivityViewController` 根本没有标题行，
  `IosFileSharer.kt:34-38`）。属既成事实，不是漏译（spec §11"家长远程控制"行的注记）。
- [ ] **V-30 设备名编辑器**：英文态、家长没填设备名时，可编辑框那一格显示规范中文 `家长设备`
  （`ParentSettingsScreen.kt:292-293` 的 `OutlinedTextField` `value` **刻意不本地化**，否则下一次保存会把英文标签写回存储）；
  而保存回执那一行是英文 `Device name saved: Parent device`（只读渲染位做了本地化）。
  **预期就是"编辑框中文、回执英文"这种不对称**（spec §11"家长设备"条）。
- [ ] **V-31 英文态日期与数字格式不变**：仍 `yyyy-MM-dd`、周一起始、ISO 时间（spec §6）。
  英文用户看到 `2026-11-08`、`08:00` 而不是 `Nov 8, 2026` 是设计决定。
  附带事实：`ui/dt/DateTimeCompat.kt:112` 的中文星期名（`星期一`…`星期日`）在当前树上**不可达**——
  只有 `EEEE` 及以上长度才会取它们，而全仓没有任何 `EEE*` 格式串
  （复核：`grep -rn --include=*.kt -E '"[^"]*EEE[^"]*"' composeApp/src` → 0 命中）。
  若将来有人引入 `EEEE`，中文星期名会在**两种语言**下一起出现（`weekdayName` 不看语言）。

### 2.5 iOS（本机零验证）

- [ ] **V-32 iOS 上把 V-01…V-21 全部重走一遍**。当前分支上 iOS 只被证明到
  "commonMain 元数据可编译"这一步；`linkDebugFrameworkIosSimulatorArm64` + `xcodebuild`
  只在 macOS CI 上跑（spec §9、spec §10），**模拟器里一次都没打开过这个 App**。
  风险最集中的两处：① `SystemLocale.ios.kt` 的 `systemLanguageTag()` 实现走
  `NSLocale.currentLocale.localeIdentifier` 取子标签，真实返回值形状（`en-US-POSIX`、`zh-Hans-CN` 等）
  从未在本机观测过，V-01 因此可能只在 Android 上成立；② iOS 改系统语言通常需要重启 App，
  "跟随系统"在 iOS 上的生效时机与 Android 不同。
- [ ] **V-33 iOS 文件分享与推送文案**：阶段一没动平台分享器；`LocalStrings` 会喂英文串给分享路径与番茄钟常驻通知
  （V-16；后者的词源是组合期的 `currentStrings`）。**W1 起**错题复习提醒的 iOS 定时通知改在**排程时**拿到本地化文案
  （切换语言后关闭再开启「每日复习提醒」即重新排程），但冷启动 `AppGraph.bootstrap()` 会用硬编码中文重注册提醒 9001
  （W9 残留，见 L-16），故英文态只存活到重启前；周报与启动补挂仍是中文；日程提醒 W5 起在排程/重新保存时取词，
  但同样只存活到下次启动/开机（管理页重新保存才刷新，见 L-16）。iOS 侧的实际呈现
  （尤其 V-29 的"无标题行"）需目视一次。

### 阶段 2 W1（错题域）追加项

- [ ] **V-34** 英文态进入错题列表：标题/状态卡/筛选栏/空状态全英文（用户错题标题/错因/解法为自建内容，按原样）
- [ ] **V-35** 英文态错题详情：主动回忆三阶段按钮、举一反三 15 条引导问题、复习记录格式全英文；落库原文（账本描述）按数据原样
- [ ] **V-36** 英文态添加错题：科目芯片（含 emoji）、保存校验 snackbar 全英文；**错误类型 chip 刻意保持 canonical 中文**（值参与落库，W9 才接线映射），不要按"漏译"记缺陷
- [ ] **V-37** 英文态家长错题分析：概览/科目分布/最近错题全英文
- [ ] **V-38** 切换语言后关闭再开启「每日复习提醒」：
  **Android**——到点弹的通知按**触发时**语言渲染（ZH 标题 `HStudy 错题复习提醒`，EN 标题 `Mistake review reminder`；正文同为当前语言），语言切换从下一次触发生效，已排程旧通知不追更（spec §8）；
  **iOS**——本地化只在**排程时**冻结（关闭再开启后重新排程即英文），但冷启动 `AppGraph.bootstrap()` 会用硬编码中文重注册 9001（W9），英文文案只存活到下次重启。
- [ ] **V-39** 中文态回归：以上四页与通知与 v3.1.0 文案逐字节一致

> **W9 残留（本轮已知、不修）**：`AppGraph.bootstrap()` 每次冷启动都用硬编码中文重注册提醒 9001（`AppGraph.kt:126-129`）
> 与周报 9002（`:134-138`）；iOS 端会因此覆盖排程时冻结的本地化文案，故英文态复习提醒"只到下一次重启为止"。
> spec §12 的 W9 行（"AppGraph 通知接线"）把这两处改为按生效语言取词后消解；日程（W5）与周报（W6）的本地化各自归其波次。

### 阶段 2 W2（家长任务管理）追加项

- [ ] V-40 英文态任务管理页：标题/分类筛选/积分行全英文；任务标题与描述为预设时显示英文（注册表），家长自建内容原样（本页**没有空状态文案**：列表为空时直接不渲染行，别去找"英文空态"）
- [ ] V-41 英文态任务编辑页：分类/频率/难度芯片与说明、必填扣分提示、保存/删除全英文；建议芯片标题预设英文、点击后写入仍为 canonical 中文（落库不变）。
  **点过建议芯片后，可编辑的标题/描述输入框里就是 canonical 中文——设计如此，不是漏译**：这两个值原样进落库调用（编辑框喂 DB 写入），本地化只发生在渲染期的列表/芯片（走注册表），编辑框不回填译文
- [ ] V-42 英文态布置任务页：表单/建议芯片/已布置列表/完成状态全英文；已布置列表标题走注册表（`ParentAssignTaskScreen.kt:143` 的 `localizedPresetText(a.title, displayLanguage)`）——预设标题译出、家长手输标题原样，`a.parentNote` 原样。
  另看「只有 1 条已布置任务」：积分徽标为 `+1 point`（不是 `+1 points`）、标题行 `Assigned today (1)`；再放到 2 条对比 `+2 points`（任务计数的英文单复数）
- [ ] V-43 英文态积分模板页：模板名/年龄段/描述/预览任务标题显示英文（注册表），分类计数与确认对话框全英文；应用后落库任务仍为 canonical 中文

### 阶段 2 W3（家长积分/审批/奖励）追加项
- [ ] V-44 英文态积分管理页：每日得分统计/任务排行/记录列表/调整对话框全英文；记录类型标签（任务完成/学习奖励/手动调整/兑换消耗/撤销扣回）英文；排行榜任务标题为预设时显示英文（注册表）、自建原样、缺失显示 "Task #N"；账本备注 record.note 原样（动态数据）。注意排行行 "N times" 与 "Net N" 在 36.dp/50.dp 固定宽度内的排版
- [ ] V-45 英文态积分审核页：待审核/历史选项卡、空态、通过/拒绝按钮、snackbar（含 one/other：1 point awarded）、五种状态标签全英文；审批描述 approval.description 原样（动态数据）
- [ ] V-46 英文态奖励编辑页：表单标签/预设提示/保存/删除全英文；分类下拉选项与选中值显示英文（注册表 Entertainment/Food/Outings/Privileges/Items/Social/Other），保存后落库分类仍为 canonical 中文
- [ ] V-47 英文态奖励商店管理页：空态引导/一键载入/积分成本（one/other：1 point / 300 points）全英文；奖励名/描述/分类为预设时显示英文（注册表），家长自建内容原样；载入预设后落库仍为 canonical 中文

### 阶段 2 W4（孩子商店/积分/抽奖/成就）追加项
- [ ] V-48 英文态孩子奖励商店：激励横幅/分类标题/兑换卡片/确认对话框/兑换成功庆祝与 snackbar 全英文；预设奖励名/描述/分类显示英文（注册表）、家长自建原样；兑换后积分账本 note 与兑换记录行的 note（兑换时冻结的奖励名）仍为中文 canonical（动态数据）。注意兑换卡片 160.dp 固定宽度内英文名/描述的单行截断
- [ ] V-49 英文态每日幸运抽奖：标题/说明/按钮/结果文案/奖池说明全英文；one/other（+1 point / +3 points）；抽奖后账本 note 仍为中文 canonical
- [ ] V-50 英文态积分明细：余额卡/待家长审核提示/记录类型回退标签全英文；账本 note 原样（动态数据）；日期格式保持 MM/dd HH:mm
- [ ] V-51 英文态成就中心：标题/汇总文案全英文；13 个成就标题与描述显示英文（achievementCopy 注册表）；进度 N/M 数字格式不变

### 阶段 2 W5（习惯/目标/日程）追加项
- [ ] V-52 英文态每日学习目标：设定/推荐/复盘三块全英文；8 个目标标题与 6 个复盘标签显示英文（注册表）、历史账本 note 仍为中文 canonical；智能推荐卡片标题/理由仍是中文（GoalRecommendationEngine 属 W8，spec §14 明示残留）
- [ ] V-53 英文态家长日程管理：提示/空态/示例载入/编辑对话框/星期行（Every day / Mon, Tue…）全英文；示例日程标题显示英文（注册表）、家长自建原样；删除 snackbar 的标题已本地化；编辑对话框的标题输入框回填 canonical 中文（它是写库来源，不本地化）
- [ ] V-54 英文态日程闹钟通知（**W5 只做到"发出时取词"，生存期有限，别按"已完成"读**）：管理页保存/启停日程时按当前语言注册正文——预设日程标题译出、自建原样，关联任务的"完成后记得打卡"句随语言并与前句有语言分隔符（ZH 空串、EN 空格）。仍然存在的边界（终审复核）：① Android 通知**标题**恒为 per-kind 渠道默认中文「HStudy 日程提醒」（`platform/ScheduleAlarmReceiver.kt` 调 showNotification 不带 `titleOverride`，`AndroidNotificationScheduler` 回落中文），本地化 `ScheduledReminder.title` 只作额外/正文回退；② 每次启动/开机 `HStudyApplication` → `AndroidReminderBootstrap.rescheduleScheduleAlarms()` 用硬编码 canonical 中文正文重注册，EN 正文**下次启动/开机即回退**；③ `ScheduleAlarmHelper.rescheduleAll()` 当前无生产调用点；④ 切语言后须在管理页**重新保存/启停**该日程才刷新文案（iOS 同样没有启动重注册来治好）。W9 计划：`ScheduleAlarmReceiver` 把 `EXTRA_TITLE` 当 `titleOverride` 传入、bootstrap 改走本地化辅助（均需平台文件授权）。人工验证只要求"保存/启停后下一次通知正文为英文（关联任务句带空格分隔）"
- [ ] V-55 英文态习惯挑战：列表/空态/对话框/打卡按钮与成功文案全英文（+N points one/other）；挑战标题/描述是用户内容原样；账本 note 与成就解锁数据仍为中文 canonical
- [ ] V-56 英文态目标追踪：完成率卡/统计标签/四条建议/四条鼓励语全英文
- [ ] V-57 英文态今日时间线：当前时间提示/空态/时间线卡片/👈 Now 徽标全英文；日程与关联任务标题预设译出、自建原样

## 3. 已知限制

### 3.1 覆盖范围：425 条 / 13 个文件，其余全部是阶段二

阶段一真正双语的 UI 文件（复现：`python scripts/i18n_inventory.py <相对路径>`，迁移后除
`focus/ChildFocusTrainingScreen.kt:23` 外全部 `出现 0 次 / 去重 0 条`；
那一处是 `/** */` 块注释里的中文，`i18n_inventory.py` 只剥 `//` 行注释，**不渲染**，见 §3.5 L-13）：

| 文件（相对 `composeApp/src/commonMain/kotlin/com/hstudy/app/ui/`） | 迁移前去重条数 | 任务 |
|---|---|---|
| `role/RoleSelectScreen.kt` | 6 | Task 4 |
| `parent/ParentHomeScreen.kt` | 59 | Task 10 |
| `parent/ParentDashboardScreen.kt` | 36 | Task 10 |
| `parent/ParentSettingsScreen.kt` | 64 | Task 5（语言分区 6 条新写）+ Task 11（其余 64 条） |
| `child/ChildHomeScreen.kt` | 107 | Task 9 |
| `child/ChildTaskListScreen.kt` | 17 | Task 8 |
| `focus/ChildFocusStatsScreen.kt` | 17 | Task 7 |
| `focus/ChildFocusTrainingScreen.kt` | 20 | Task 7 |
| `common/CommonComponents.kt`（共享控件，非页面） | 5 | Task 6 |
| `common/PomodoroTimer.kt`（共享组件，非页面） | 31 | Task 7 |
| `focus/ChildPomodoroScreen.kt` | 32 | Task 7b（Ruling 23 新增） |
| `focus/ChildMemoryTrainingScreen.kt` | 17 | Task 7b |
| `focus/ChildDailyPlanScreen.kt` | 14 | Task 7b |

- **10 个 plan 原始目标文件 = 362 条去重**（plan Global Constraints"第一阶段规模"条、
  `docs/superpowers/plans/2026-11-07-bilingual-stage1-inventory.md` 的十个分节小计）。
- **Task 7b 追加的 3 页 = 63 条**（复现：对 `git show 761b24e:…` 的三个文件跑同一段正则统计，得 32 / 17 / 14）。
- **合计 425 条 / 13 个文件**（11 个页面 + 2 个共享组件文件）。注意两点：其一，终审 Ruling 41 起
  `ui/navigation/AppNavGraph.kt` 的家长密码门（迁移前 5 条去重）也双语化了——它是**第 14 个文件**，
  那 5 条不计入 425（门禁不属于"阶段一的 13 个文件清单"，见 V-28 与 spec §11"家长密码"行）；其二，
  `ParentSettingsScreen` 的语言分区 6 条里有 5 条是 Task 5 **新写的文案**、不是从页面搬来的（`SettingsStrings.kt:36-41` 披露，
  `i18n_template_diff.py` 里五条表侧单边登记），所以"425 条迁移位点"与"425 条翻译工作量"口径略有出入。
- **未覆盖**：`python scripts/i18n_coverage_audit.py` 当前给出
  **计划未覆盖页面 905 条去重文案**（按文件各自去重求和；944 次出现；这 45 个 `.kt` 文件
  跨文件再去重后是 810 条。终审修复波次按当前树复跑，退出码 0、四个数字一字未变）。
  spec §2 的全仓口径是 1661 处出现 / 1406 条去重（含 `data/` 与非 UI 文件），
  所以阶段二的规模量级是"约九成文案"。
  本清单**修正**了台账 Ruling 23 里"973 条 / 约 35 个文件"这个数字：那是 Task 7b 之前的读数。
  **复跑时还要读到分类口径这一层（终审第 10 条）**：脚本用"文件名是否出现在 plan 文本里"判定覆盖，
  于是 `AppNavGraph.kt` 与 `DateTimeCompat.kt` 早在**没被迁移的时候**就已被算成"已覆盖"——
  前者藏着门禁对话框那几条（Ruling 41 迁移后它们才真正既不在页面也不在 905 里，数字因此没动），
  后者今天仍贡献着 8 条"已覆盖"计数里的 7 条星期名（`星期一`…`星期日`，未被迁移、也未计入 905，
  本波次实跑：被判"已覆盖"且含中文的只剩 `DateTimeCompat.kt` 7 条 + `ChildFocusTrainingScreen.kt`
  1 条块注释假阳性）。所以 905 是"计划口径的阶段二积压"，不是全仓剩余文案的完备清单：
  `DateTimeCompat.kt` 的 7 条与 `data/**` 侧（§3.1 末条、L-17）都要另账处理。
- **不动的东西**：schema 与迁移（v12，未变）、`data/**`、`ui/dt/DateTimeCompat.kt` 的格式行为、
  平台分享器与通知桥接、Compose Multiplatform 版本（spec §1 非目标第 5 条）。

### 3.2 英文质量依赖人工抽查（spec §12 风险 1）

术语表（spec §11）保证"同一概念同一译法"，但**没有**保证英文读起来自然——机器只能断言 ZH≠EN、
one/other 覆盖、emoji 位置。已被实现者点名"如果别扭就改表值 + 几条断言"的有：

- `约 3 hr`（`About` + `3 hr` 略拗口，Task 7 自查）；
- `Sessions` + `12 sessions` 的英文冗余（与中文 `累计训练` + `12次` 对称保留）；
- `Wish Pool` 与中文"心愿存钱罐"的意象距离（spec §11 词条，用户选定）；
- `Study process` / `Complete`（而非 `Done`）等可读性优先的取舍（Task 9 自查）；
- `减少时` / `增加分` 这类**中文本身**就别扭的插值产物，按等价替换原样保留（Task 6+8 自查）——
  要改是文案决定，不是翻译漏了。
- 另有一处英文**大小写**待裁决（§4 第 2 条）；§4 第 1 条已在终审 Ruling 45 裁决关闭。

### 3.3 英文态混排是设计代价，不是缺陷清单

spec §1 非目标第 1 条："不迁移其余页面（保持中文），因此英文态会出现中英混排"。
V-22…V-27 列的是**已知会混排的位置**（V-28 原先也在这一列，Ruling 41 起门禁已双语、只剩验证项；
V-29/V-30 是平台与规范值侧的既成混排）。设置页那一行英文提示
（`Some screens are not translated yet and stay in Chinese.`，`SettingsStrings.kt:434`）
就是给这个代价兜底的，所以它只在英文态出现（V-09）。

### 3.4 存储边界：英文行不回改，英文设备因此仍会读到中文

plan Global Constraints 的硬约束（Ruling 25）：**任何文案表字符串都不得进入写库调用或业务判等参数**。
处置形态是"存规范值、只在渲染时本地化"（先例 `ParentApprovalScreen.kt:172-177` 的 `actionType` 分支）。后果：

1. **测试期已经写进库里的英文行是永久的**。阶段一从 Task 5 起就能切英文，
   如果那时点过打卡/专注，`focus_sessions.taskTitle`、`point_approvals.description`、
   `point_records.note` 里可能已经有 `Focus on Free study for 25 min` 这样的英文行。
   **裁决是不回改**（加列、写迁移都不做），所以：
   - 效率报表按 `taskTitle` 分组会把同一目标的两语言行**裂成两根柱**（`ChildEfficiencyReportScreen.kt:335`）；
   - 渲染期映射（`CanonicalFocusText` / `CanonicalChildHomeText` / `CanonicalParentSettingsText`
     + `targetLabel` / `deviceNameDisplayLabel`）**认不出的输入原样透传**，
     于是英文设备上仍会看到这些历史英文行/中文行的原文，与新写的规范中文值混在一起。
   → 这就是 V-00a 要求"验证前清除应用数据"的原因。
2. **英文设备显示中文是设计**，两处最容易误判为 bug 的位置：
   家长审批列表与效率报表整体中文（V-22、V-23），以及设备名编辑框那一格（V-30）。
3. **幂等比较依赖 description**：`ChildHomeScreen.kt` 传给 `PointRepository` 的 description
   参与幂等判等（Ruling 25④），若本地化会把"切换语言"误判成业务变更 → 已按规范值处理，
   但这条**只能靠真机验证**：切语言后重复领取同一项奖励，确认不重复计分。

### 3.5 已知限制清单（可直接抄进发布说明）

| 编号 | 限制 | 证据 |
|---|---|---|
| L-01 | 阶段一只双语 425 条 / 13 个文件，其余 ~905 条（45 文件）仍是中文 | §3.1 |
| L-02 | 英文态会出现中英混排（设计代价），设置页有一行英文提示兜底 | spec §1 非目标 1、§7；V-09 |
| L-03 | 家长审批列表、效率报表整体中文，其中出现的专注描述是存储的规范中文 | plan Ruling 27；`ParentApprovalScreen.kt:182`、`ChildEfficiencyReportScreen.kt:335` |
| L-04 | 等级评语/鼓励语/成就名/宠物与积分动画组件仍是中文（`LearningScore`、`LevelSystem`、`AchievementManager` 等） | `ChildHomeScreen.kt:289`、`AchievementManager.kt:29-42` |
| L-05 | `TaskEntity.difficultyName()`（`data/local/entity/TaskEntity.kt:50-54`）仍返回中文，但**当前零调用点**，英文态实际看不到它——这是死代码里的中文，属阶段二清理项（本期不动 `data/**`） | `grep -rn "difficultyName" composeApp/src` 只命中声明本身；另一处在不参与构建的 `legacy/android-v2.88/` |
| L-06 | `PointsBadge` 的单位是静态字段，英文 1 分显示 `⭐ 1 points`（需要"带计数的单位"字段，属页面改动，超出等价替换） | plan Ruling 22 相关（Task 6+8 新发现）；`CommonComponents.kt:108-131` |
| L-07 | 番茄钟进行中的文案陈旧**已不是限制**（Task 7b `d282cb3` 起 `rememberUpdatedState` + 索引式 `remember`，见 V-19 注记），且"跑一轮中途切语言"在本版本**不可达**（语言入口只在家长设置页、`AppNavGraph` 一次只组合一个目的地）；仍然成立的取舍只剩 `focusTarget` 选中值不随语言（key 刻意不含 `strings`，否则冲掉用户已选目标）与跨越配置变更的完成回调 | spec §4；V-19/V-20/V-21；`PomodoroTimer.kt:49`、`:142-150` |
| L-08 | 英文态 Android 系统分享面板标题仍是「分享文件」；iOS 分享面板根本没有标题位 | `AndroidFileSharer.kt:27`、`IosFileSharer.kt:34-38`；spec §11"家长远程控制"行注记；V-29 |
| L-09 | （已关闭，编号保留防指针漂移）家长密码门对话框曾是整块中文门禁，**终审 Ruling 41 已迁入 `ParentGateStrings`**、两个按钮复用既有字段，英文态不再是限制；验证项转为 V-28 的"确认真的跟表走" | `AppNavGraph.kt:253-288`、`ParentGateStrings.kt:28-30/34-36/40-42`；V-28 |
| L-10 | 英文态日期/数字格式不本地化（`yyyy-MM-dd`、周一起始、`8:00`） | spec §6；V-31 |
| L-11 | 未迁移页面里的公共控件（顶栏返回说明、`hr`/`min` 步进器）会形成"中文页面嵌英文控件"的反向混排 | `ParentScheduleManageScreen.kt:228-241`（W5 后该对话框已双语化，本条示例已失效，保留作阶段 1 历史）；V-27 |
| L-12 | 测试期写入的英文行不回改；渲染期映射对认不出的输入原样透传 | §3.4；V-00a |
| L-13 | 清单脚本的两个已知口径问题：`i18n_inventory.py` 只剥 `//` 行注释，块注释里的中文会被算成"残留字面量"（`ChildFocusTrainingScreen.kt:23` 就是这种假阳性）；`i18n_coverage_audit.py` 用"文件名是否出现在 plan 文本里"判定覆盖 | `scripts/i18n_inventory.py:30`、`scripts/i18n_coverage_audit.py:35` |
| L-14 | 双语视觉效果无法由 CI 验证；iOS 在本工作机上连链接都没发生过 | spec §9；§1、§2.5 |
| L-15 | 阶段一是 v3.1.0 的**前半段**（Analytics 子项目未实施），版本号仍为 3.0.0/30000 | `docs/VERSIONING.md`；`composeApp/build.gradle.kts:89-90`；V-00b |
| L-16 | **定时提醒类通知的本地化现状（设计边界，不是漏译）**：W1 只接通了错题复习提醒——Android 在**触发时**重新按当前语言取词（ZH 态标题沿用渠道默认 `HStudy 错题复习提醒`，与 v3.1.0 逐字节一致；EN 态标题/正文为英文）；iOS 在**排程时**冻结本地化文案，但 `AppGraph.bootstrap()` 冷启动会用硬编码中文重注册提醒 9001，英文文案只存活到重启前（**W9 残留**，spec §12）。周报提醒与启动补挂的 title/body 仍在**任何组合期之外**构造（`bootstrap()`、Receiver、AlarmHelper 里没有 CompositionLocal，`LocalStrings` 按构造到不了它们），英文态仍是中文，归 W6（周报）/W9（AppGraph 接线）。**日程提醒（W5 已交付部分的残留，不是"已完成"）**：管理页保存/启停时按当前语言注册正文（关联任务句带语言分隔符，ZH 空串 / EN 空格），但 Android 通知**标题**恒为 per-kind 渠道默认中文（`platform/ScheduleAlarmReceiver.kt` 调 showNotification 不带 `titleOverride`），且每次启动/开机 `AndroidReminderBootstrap` 重注册硬编码 canonical 中文正文，EN 副本只存活到下次启动/开机；`ScheduleAlarmHelper.rescheduleAll()` 无生产调用点；切语言须在管理页重新保存/启停日程才刷新（iOS 同样没有启动重注册来治好）。收口归 W9：`ScheduleAlarmReceiver` 传 `EXTRA_TITLE` + bootstrap 走本地化辅助（均需平台文件授权）。番茄钟常驻通知不在此列（计时协程里现取 `currentStrings`，见 V-16）。 | `AppGraph.kt:128`、`:136`（两处 `ScheduledReminder` 的 title/body 裸字面量，均在 `bootstrap()` 里；终审 Minor 3 把 `startupLanguage` 换成 `StateFlow` 后这两个坐标从 `:112`/`:120` 顺移而来，本行按当前树复算）、`ui/parent/WeeklyReportReceiver.kt:28-29`、`ui/schedule/ScheduleAlarmHelper.kt:94-100`（正文拼接；文案规则与存活期见该文件 `:27-38` KDoc）；`ui/mistake/ReviewReminderReceiver.kt:34-35` 已改为取 `localizedCopy()`（不再有裸字面量）。Android 周报触发器仍是自己的裸字面量（`androidMain/…/platform/WeeklyReportReceiver.kt:41-42`/`:53`）。**平台文件口径**：用户 2026-11-10 明确授权的平台改动只有两个 androidMain 通知文件（`platform/ReviewReminderReceiver.kt`、`platform/AndroidNotificationScheduler.kt` 的 `titleOverride` 可选参数），其余平台文件零改动；`AndroidFileSharer.kt:27` 的分享标题仍属 W9（L-08） |
| L-17 | 设备名兜底在 `data/**` 里各存了一份**表外副本**：家长控制导出包的 `家长设备`（`FamilySyncHelper.kt:107`，落进孩子设备存储，不在设置页出现）与进度包的 `孩子设备`（`:190` 与 DTO 默认值 `:82`）。它们不引用 `CanonicalParentSettingsText`/文案表，本阶段禁改 `data/**`，属阶段二与规范值常量对账的项（另见 spec §11"家长设备"行） | `grep -n "家长设备\|孩子设备" composeApp/src/commonMain/kotlin/com/hstudy/app/data/sync/FamilySyncHelper.kt`（3 处命中：`:82`/`:107`/`:190`） |
| L-18 | `App.kt:60` 的 `.catch { emit(null) }` 是"降级不崩根"的例外：DataStore 读一旦抛错，上游以一次 `null` 完成、订阅就此终止，**本次会话语言被钉死在系统语言**，设置页切换不再生效，直到组合树重建（Android 配置变更 / iOS 重启）。代码注释写明了代价（`App.kt:54-59`），本清单此前未列 | `composeApp/src/commonMain/kotlin/com/hstudy/app/App.kt:52-62` |
| L-19 | **等价性登记表的"共享词遮蔽"**（终审 D-A 第 4 条裁决为记录、不修）：`i18n_template_diff.py` 的 `DISCLOSED_RESIDUALS`（现 56 条）里有**六条两侧同形**的通用词登记——`取消` / `确定` / `确认` / `删除` / `分` / `+⟨$⟩分`——而 `apply_disclosures()` 是在三层比对**之前**按**值**（不按字段）把它们从两侧集合里 `pop` 掉的。后果：在**默认组（窄组）**里新写或改出一条与这六个词**逐字节相同**的中文表值，会被无声摘掉，既不参与 `[strict]` 也不参与 `[A]/[B]`，等价性检查看不见它。边界要说清三条，别把它读成"闸门失效"：① 只有整串相同才遮蔽——`累计专注` 改成 `累计专注。` 这种真实漂移照旧报 `invented` 并退 1（本波次在当前树复做正对照实测：默认组 `[A] NO (1 missing, 1 invented)`、exit 1）；② 遮蔽的是**这条值**，不是这个字段，同字段其他值仍被比；③ `--branch` 整枝组里这六条是双边命中，摘掉等于"披露成立"，风险只在没带供值那张表的窄组。同一机制的第二个实例：表里第 14 条（`ParentDashboardStrings.scoreUnit` 的表侧单边 `分`）被第 13 条双边 `分`/`分` 先 pop，因而在整枝组恒报"（未用到）"——这就是 §6 那句"55 命中 / 1 未用到"里那 1 条的来历，不是登记过期。阶段二的收口形状：给登记加**字段名**限定（如 `table_field="CommonStrings.cancelButton"`）而不是继续按值 pop | 脚本头「已知边界」最后一条 + `scripts/i18n_template_diff.py` 的 [DISCLOSED_RESIDUALS] 注释；复跑：`python scripts/i18n_template_diff.py`（0）与"把一个 ZH 值改一个中文字"的 mutation（1） |

## 4. 英文措辞：一处已裁决关闭（Ruling 45），一处仍待裁决

原先这里的两处"刻意没有改"，第 1 条已由终审 Ruling 45 裁决关闭；第 2 条仍越出"等价替换 + 本页范围"，裁决权在用户/控制器。

1. **`Weekly report`（设置页分区）vs `Weekly Report`（spec §11"周报"词条）——已裁决（终审 Ruling 45）**
   裁决方向是**改术语表、不改实现**：§11 的"周报"行（spec `:122`）、逐字引用它的"效率报表"行（`:156`）
   与"每周学习周报"行（`:168`）三行统一为句首大写 `Weekly report`；三条英文落地值
   `weeklyReportTitle` / `weeklyReportOnMessage` / `weeklyReportOffMessage`（当前树
   `SettingsStrings.kt:457`/`:459`/`:460`）与 `ParentHomeStrings.kt:240` 的 `Weekly reports`
   **一个字节都没动**——裁决理由原文"不为凑一行术语表重排 6 个 EN 值"。
   人工侧只剩：英文态设置页那一格读起来可接受。
2. **`Today's Check-in`（标题式大写）vs `Total check-ins`（句首式）**
   同一张进度卡里的两种大小写（`SettingsStrings.progressTodayCheckinLabel` / `progressCheckinLabel`）。
   未改的理由是跨表的：`Today's Check-in` 在 `ChildHomeStrings.todayCheckinTitle`
   与 `TaskListStrings.checkInTitle` 里是同一个词形，只改设置页会新造一处跨表漂移；
   要改就三张表一起改。

## 5. 存储边界的人工审结记录（闸门抓不到的四类形态）

`scripts/i18n_storage_boundary.py`（CI 退出码闸门）覆盖直接引用、跨行调用、`by remember` 委托、
赋值污点、污点链、sink 词根、别名句柄，但**有四类形态结构上抓不到**（plan Ruling 31 因此强制人工自查）。
各任务已逐条审结的结论如下——表 KDoc 里"见任务报告"的悬空指针（Ruling 35）就是改指这一节。

| 盲区形态 | 审结结论 | 复核命令 |
|---|---|---|
| ① 同文件 helper 的返回值 | 已迁移页面中，只有 `ParentSettingsScreen` 与两个家长页做过逐函数审：`ParentSettingsScreen.kt` 没有任何返回 `String` 的本地函数（两个"辅助物"是返回 `Unit` 的 lambda 与 `@Composable`） | `grep -n "fun .*) *: *String" …/ParentSettingsScreen.kt`（0 命中） |
| ② 字段赋值后再 update | `ParentSettingsScreen.kt` 内 `[a-z]+\.[a-z]+ = strings\.` 0 命中，页面不构造 Entity、不调 insert/update | `grep -nE "[a-zA-Z_]+\.[a-zA-Z_]+ = strings\." composeApp/src/commonMain/kotlin/com/hstudy/app/ui/parent/ParentSettingsScreen.kt` |
| ③ 无关键字的重新赋值 | 该页 `error = strings.…`（`:429`/`:430`）正是这一形的活样本：赋值处无 `val/var`、闸门看不见；逐行核对 `error` 的全部出场只有声明、两处文案赋值、两处清空、一次渲染（`Text(error!!)` 在 `:422`），**不流向任何 sink** | `grep -n "error" …/ParentSettingsScreen.kt` |
| ④ 多行 / 三引号字符串里的文案 | `ParentSettingsScreen.kt` 内 `"""` 0 命中；跨行 sink 调用只有 `shareFile(...)`（`:233-235`），其文案实参落在被 callee 丢弃的 title 位 | `grep -n '"""' …/ParentSettingsScreen.kt` |
| 家长两页（Task 10） | 审批入口两页实为**只读页**：`ParentHomeScreen.kt`、`ParentDashboardScreen.kt` 无真实写库 sink，四类盲区逐条审结为不可达 | `python scripts/i18n_storage_boundary.py`（0）+ 页面内 sink 名 grep |
| 设备名兜底（Task 11） | `家长设备` 是**落库值**（`setDeviceName` → DataStore `DEVICE_NAME` → 家长控制包 → 孩子端），故中文原字节进 `CanonicalParentSettingsText`，英文态只换只读渲染位的标签 | `grep -n "DEVICE_NAME_FALLBACK" composeApp/src/commonMain/kotlin/com/hstudy/app/**` |

> 闸门自身的已知边界也写在这里，免得被当成"0 = 绝对安全"：三引号原始串赋值有 60 行上限、
> 辅助函数返回值/字段赋值/无关键字再赋值三形不覆盖（脚本头注释列明，Ruling 32/33）。

## 6. 复现与回归

（编码前提见 §1 末注：`export PYTHONIOENCODING=utf-8` **已经不需要**，四个脚本自带 UTF-8 换装护栏。
以下每条都是终审修复波次在当前树实跑过的，括注实测退出码；终审 D-B 之后本波次又按"不设
`PYTHONIOENCODING` + 输出接管道"的更严条件复跑过一遍，退出码逐条不变。）

- 阶段一各页的中文态等价证明：`python scripts/i18n_template_diff.py`（默认组，实测 **exit 0**；
  distinct 123/120 → 披露后 **118/118**，`[strict]` **整串逐字节相同 117 条** + 1 条已披露的
  格式串拆分位点（`%.1f 秒` ↔ `⟨$⟩ 秒`，就是 `FocusStrings.elapsedValue` 那处），`[A]/[B]` 双向 0 残差。
  注意"118 条"是**披露后的模板集合大小**，"117 条"才是**整串相同**的条数，两个数字不是一回事，
  旧文档把 118/118 写成"条整串"是假陈述，本波次已按工具当日输出改正）。
- **整枝组（终审第 9 条新发布的长期复验命令，`d4d441b` 起脚本自带 `--branch`）**：
  `python scripts/i18n_template_diff.py --branch`（阶段 2 W5 登记后实测 **exit 0**；distinct 831/795 →
  披露后 782/782，`[strict]` 781 条整串相同 + 同一条拆分位点。它把本分支全部已迁页面（39 条页面组条目 /
  38 个文件，含 W1 的六页、W2 的四页、W3 的四页、W4 的四页与 W5 的六页）× 全部 34 张表放进同一命题，`DISCLOSED_RESIDUALS` 登记表（现 56 条）整张受它复验
  （实跑 55 条命中打印、第 19 条与其同形条共用一次命中），
  默认组只碰到其中 6 条（实跑打印 3 条"已披露"双边/单边命中 + 3 条"单边在范围"），其余 50 条默认组用不到。
- **`SettingsStrings` 这张表必须带两个页面 rev 才退 0**（表由 Task 5 与 Task 11 两批页面共同填写，
  其中语言分区那 5 条是 Task 5 为新功能新写、没有迁移前原文）：
  ```bash
  python scripts/i18n_template_diff.py \
    --page 767711d:com/hstudy/app/ui/parent/ParentSettingsScreen.kt \
    --page ad6aa93:com/hstudy/app/ui/parent/ParentSettingsScreen.kt \
    --table com/hstudy/app/i18n/SettingsStrings.kt#SettingsStringsZh
  ```
  `--table` 的形态是 **相对 `composeApp/src/commonMain/kotlin/` 的路径 + `#Zh 变量名`**；
  本清单原先记录的 `composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt`
  （全前缀、无 `#Variable`）**跑不出 0**——本波次实测该原样命令 **exit 1**，
  脚本自 `d4d441b` 起会把这种写法直接拦成带示例的用法错误（更早的版本是裸 `ValueError: not enough values to unpack`），
  上面改正后的那条实测 **exit 0**。不带 `--page 767711d:` 时那 5 条会被报成 `invented` 并退 1。
  登记落点在脚本的 `DISCLOSED_RESIDUALS`。
- 清单残留：`python scripts/i18n_inventory.py parent/ParentSettingsScreen.kt …`（13 个文件应全为 0，
  除 §3.1 说明的那条块注释假阳性；本波次 13 个文件逐一实跑，仅此 1 处非 0）。
- 覆盖规模：`python scripts/i18n_coverage_audit.py`（实测 **exit 0**；905 条 / 944 次 / 45 文件 / 跨文件 810 条，
  与 §3.1 的引用逐字一致；分类口径的坑见 §3.1"复跑时还要读到分类口径这一层"）。
- 存储边界：`python scripts/i18n_storage_boundary.py`（实测 **exit 0**）与其自测
  `python scripts/tests/i18n_storage_boundary_selftest.py`（实测 **exit 0**，195 断言 / 0 失败）。
