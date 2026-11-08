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
python scripts/i18n_template_diff.py                              # 中文态逐字节等价（118/118 条整串）
./gradlew :composeApp:testDebugUnitTest                            # 109 tests / 0 failures（其中 i18n 包 67 条）
./gradlew :composeApp:compileCommonMainKotlinMetadata              # commonMain 元数据 + 平台纯净度
grep -rl --include=*.kt -E "^import (android\.|java\.)" composeApp/src/commonMain | wc -l   # 0
```

单测能证明的是**表值**（漏译、one/other、emoji 位置、规范值漂移），证明不了**页面是否读到表**，
也证明不了排版。`AppLanguageResolveTest`（6 条）覆盖解析纯函数、`LanguagePreferenceTest`（2 条：
`languagePersistsAcrossReads`、`languageRewriteReplacesPreviousChoiceAndLocksWireFormat`）
覆盖偏好读写与线格式；`StringsCopyTest`（59 条）覆盖六张表的文案。

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
  进行中的那一轮文案何时变英文见 V-19、V-20。
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
  与 `:72-75`、`:122`。英文标签 `All-time focus` / `Sessions` + 值 `12 sessions` 在三等分宽度里是否溢出。
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
- [ ] **V-16 系统通知栏文案**：番茄钟进行中的常驻通知（title/text/progress）。
  它在组合期构造、由系统渲染，本机完全看不到内容。
- [ ] **V-17 中文态零位移回归**：以上每一页在**中文态**下应与迁移前逐字、逐位一致
  （`i18n_template_diff.py` 证明字节相同，但"字节相同"不等于"排版没动"——
  字段拆分可能改变组合期布局输入，需人眼确认徽章行、chip 行没有回流）。
- [ ] **V-18 平板/折叠展开宽度**：V-07…V-16 里凡是走 `ResponsiveContent` 双分支的页面，
  Medium/Expanded 分支单独再看一遍（两分支共用字段，但共用字段也共用长度问题）。

### 2.3 运行中切换语言的陈旧文案（设计取舍，需确认"看起来可接受"）

- [ ] **V-19 进行中的番茄钟**：跑一轮专注，中途切语言。
  预期：**当前这一轮的常驻通知与"半场欢呼"文案保持旧语言**，到下一个阶段边界（IDLE→RUNNING→BREAK）
  或下一个番茄钟才换（`PomodoroTimer.kt` 的 `LaunchedEffect(state)` 与 `remember(completedPomodoros)` 索引）。
  这是刻意保留的：把 `strings` 加进 key 会重启计时协程，与 spec §4"不打断进行中的番茄钟"冲突。
- [ ] **V-20 完成回调的 snackbar**：同一条测试里，专注完成后弹出的
  `focusDoneSnackbar` 仍可能用页面在 effect 创建时捕获的语言（`onComplete` 是页面传入的回调，
  `rememberUpdatedState` 只覆盖组件内部）。预期"偶尔一条旧语言"，属已知上限。
- [ ] **V-21 专注目标选中值**：切语言后 `focusTarget` 的选中值仍是旧语言的字符串
  （其 `remember` key 刻意不含 `strings`，否则切语言会把用户已选目标冲掉）。
  预期表现：下拉框里旧语言的选中值和新语言的预设项列表**不匹配**（不崩，但看起来怪）。
  存储侧写的是规范中文（见 §3.4），所以这只影响显示。

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
- [ ] **V-24 孩子首页露出的中文等级词/鼓励语**：`ChildHomeScreen.kt:288` 直接把
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
- [ ] **V-28 家长密码门对话框全中文**：`ui/navigation/AppNavGraph.kt:254-283`
  （`🔒 请输入家长密码` / `家长密码` / `密码错误，请重试` / `确定` / `取消`）**不在阶段一的 13 个文件里**，
  英文设备上从任何入口触发家长验证都会掉进一个纯中文对话框。
  这一条是本任务从 `scripts/i18n_coverage_audit.py` 的新发现（plan/台账均未点名），**建议列为阶段二的第一批**：
  它是阶段一已迁移页面的**必经门禁**，观感上最像 bug。
  顺带事实：这里的 `确定`/`取消` 是页面裸字面量，与已迁移的
  `SettingsStrings.passwordOkButton` / `CommonStrings.cancelButton` 不同源（阶段二若迁这里应复用既有字段）。
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
- [ ] **V-33 iOS 文件分享与推送文案**：阶段一没动平台分享器与通知桥接，
  但 `LocalStrings` 现在会喂英文串给分享/通知路径，iOS 侧的实际呈现（尤其 V-29 的"无标题行"）需目视一次。

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
- **合计 425 条 / 13 个文件**（11 个页面 + 2 个共享组件文件）。注意 `ParentSettingsScreen` 的
  语言分区 6 条里有 5 条是 Task 5 **新写的文案**、不是从页面搬来的（`SettingsStrings.kt:36-41` 披露，
  `i18n_template_diff.py` 里五条表侧单边登记），所以"425 条迁移位点"与"425 条翻译工作量"口径略有出入。
- **未覆盖**：`python scripts/i18n_coverage_audit.py` 当前给出
  **计划未覆盖页面 905 条去重文案**（按文件各自去重求和；944 次出现；这 45 个 `.kt` 文件
  跨文件再去重后是 810 条）。spec §2 的全仓口径是 1661 处出现 / 1406 条去重（含 `data/` 与非 UI 文件），
  所以阶段二的规模量级是"约九成文案"。
  本清单**修正**了台账 Ruling 23 里"973 条 / 约 35 个文件"这个数字：那是 Task 7b 之前的读数，
  且 `i18n_coverage_audit.py` 用"文件名是否出现在 plan 文本里"判定覆盖，Task 7b 之后三个页面
  从两侧同时消失（迁移完就没有中文字面量了）。以脚本当前输出为准。
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
- 另有两处英文**大小写**待裁决，见 §4。

### 3.3 英文态混排是设计代价，不是缺陷清单

spec §1 非目标第 1 条："不迁移其余页面（保持中文），因此英文态会出现中英混排"。
V-22…V-28 列的是**已知会混排的位置**。设置页那一行英文提示
（`Some screens are not translated yet and stay in Chinese.`，`SettingsStrings.kt:412`）
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
| L-04 | 等级评语/鼓励语/成就名/宠物与积分动画组件仍是中文（`LearningScore`、`LevelSystem`、`AchievementManager` 等） | `ChildHomeScreen.kt:288`、`AchievementManager.kt:29-42` |
| L-05 | `TaskEntity.difficultyName()`（`data/local/entity/TaskEntity.kt:50-54`）仍返回中文，但**当前零调用点**，英文态实际看不到它——这是死代码里的中文，属阶段二清理项（本期不动 `data/**`） | `grep -rn "difficultyName" composeApp/src` 只命中声明本身；另一处在不参与构建的 `legacy/android-v2.88/` |
| L-06 | `PointsBadge` 的单位是静态字段，英文 1 分显示 `⭐ 1 points`（需要"带计数的单位"字段，属页面改动，超出等价替换） | plan Ruling 22 相关（Task 6+8 新发现）；`CommonComponents.kt:108-131` |
| L-07 | 进行中的番茄钟不跟随语言切换：通知与半场欢呼到阶段边界才换、完成回调 snackbar 与 `focusTarget` 选中值可能停留在旧语言 | spec §4（不重启协程的代价）；V-19/V-20/V-21 |
| L-08 | 英文态 Android 系统分享面板标题仍是「分享文件」；iOS 分享面板根本没有标题位 | `AndroidFileSharer.kt:27`、`IosFileSharer.kt:34-38`；spec §11"家长远程控制"行注记；V-29 |
| L-09 | 家长密码门对话框（导航层）完全未迁移，英文态任何家长入口都会掉进中文对话框 | `AppNavGraph.kt:254-283`；V-28（本清单的新发现） |
| L-10 | 英文态日期/数字格式不本地化（`yyyy-MM-dd`、周一起始、`8:00`） | spec §6；V-31 |
| L-11 | 未迁移页面里的公共控件（顶栏返回说明、`hr`/`min` 步进器）会形成"中文页面嵌英文控件"的反向混排 | `ParentScheduleManageScreen.kt:228-241`；V-27 |
| L-12 | 测试期写入的英文行不回改；渲染期映射对认不出的输入原样透传 | §3.4；V-00a |
| L-13 | 清单脚本的两个已知口径问题：`i18n_inventory.py` 只剥 `//` 行注释，块注释里的中文会被算成"残留字面量"（`ChildFocusTrainingScreen.kt:23` 就是这种假阳性）；`i18n_coverage_audit.py` 用"文件名是否出现在 plan 文本里"判定覆盖 | `scripts/i18n_inventory.py:30`、`scripts/i18n_coverage_audit.py:35` |
| L-14 | 双语视觉效果无法由 CI 验证；iOS 在本工作机上连链接都没发生过 | spec §9；§1、§2.5 |
| L-15 | 阶段一是 v3.1.0 的**前半段**（Analytics 子项目未实施），版本号仍为 3.0.0/30000 | `docs/VERSIONING.md`；`composeApp/build.gradle.kts:89-90`；V-00b |

## 4. 两处英文措辞待裁决（不是笔误）

这两处**刻意没有改**，因为改动越出"等价替换 + 本页范围"。裁决权在用户/控制器。

1. **`Weekly report`（设置页分区）vs `Weekly Report`（spec §11"周报"词条）**
   设置页三条英文落地值（`weeklyReportTitle` / `weeklyReportOnMessage` / `weeklyReportOffMessage`）
   一律句首大写，而 §11 给的是词组大写。改动牵动 3 条 EN 值 + §11 三行
   （`:122` 周报本身、`:156` 效率报表行**逐字引用**了 `周报 = Weekly Report`、`:168` 每周学习周报行），
   见 spec §11"每周学习周报"行。人工侧只需确认：英文态设置页那一格读起来可接受。
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

- 阶段一各页的中文态等价证明：`python scripts/i18n_template_diff.py`（默认组，退出码 0，
  `[strict]` 整串逐字节相同、`[A]/[B]` 双向 0 残差）。
- **`SettingsStrings` 这张表必须带两个页面 rev 才退 0**（表由 Task 5 与 Task 11 两批页面共同填写，
  其中语言分区那 5 条是 Task 5 为新功能新写、没有迁移前原文）：
  ```bash
  python scripts/i18n_template_diff.py \
    --page 767711d:com/hstudy/app/ui/parent/ParentSettingsScreen.kt \
    --page ad6aa93:com/hstudy/app/ui/parent/ParentSettingsScreen.kt \
    --table composeApp/src/commonMain/kotlin/com/hstudy/app/i18n/SettingsStrings.kt
  ```
  不带 `--page 767711d:` 时那 5 条会被报成 `invented` 并退 1。登记落点在脚本的 `DISCLOSED_RESIDUALS`。
- 清单残留：`python scripts/i18n_inventory.py parent/ParentSettingsScreen.kt …`（13 个文件应全为 0，
  除 §3.1 说明的那条块注释假阳性）。
- 覆盖规模：`python scripts/i18n_coverage_audit.py`。
