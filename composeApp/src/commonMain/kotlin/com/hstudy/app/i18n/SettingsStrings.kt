package com.hstudy.app.i18n

/**
 * 家长设置页（ui/parent/ParentSettingsScreen.kt）文案表。
 *
 * **两个出处**（Ruling 4：同一张表分两轮扩展，不重建）：
 * - 语言分区 6 条来自 Task 5，逐字节原样保留（含两种 locale 下同值的自称词
 *   [languageZh] / [languageEn]，Ruling 6）；
 * - 其余 63 个字段 + 1 个规范值常量来自 Task 11，覆盖该页迁移前清单剩下的 64 条
 *   （`python scripts/i18n_inventory.py parent/ParentSettingsScreen.kt` 迁移前给**去重 64 条 /
 *   含插值 10 条**；迁移后该页 CJK 字面量 0 条）。
 *
 * 清单口径与字段数的差额是**一进一出**，逐条可查：
 * - **出去一条**：`:192` / `:431` 的 `"取消"` 复用 [CommonStrings.cancelButton]（brief 明令"已在
 *   CommonStrings 的必须复用"），不在本表重开一个槽；由
 *   `StringsCopyTest.settingsCancelButtonComesFromCommon` 钉住出处，并在
 *   `scripts/i18n_template_diff.py` 的 DISCLOSED_RESIDUALS 里登记为**页面单边**残差。
 *   （同一页的 `:429` `"确定"` **不是**同一条文案：中文词形与 `确认` 差一个字、字节也不同，
 *   照 [CommonStrings.confirmButton] 复用会把 ZH 字节改掉，所以 [passwordOkButton] 留在本表，
 *   两侧词形由同一条测试钉住；见下面"确定 / 确认"那段。）
 * - **进来一条**：`:299` 的 `"家长设备"` 是**落库值**、不进文案表（见下面"存储边界"那一段），
 *   但英文态要有它的显示名，于是本表多出一个纯显示字段 [deviceNameFallbackLabel]。
 *   一进一出，64 - 1 + 1 = 64 个新字段（其中 11 个函数型：10 条来自插值清单 +
 *   [deviceNameDisplayLabel] 这条"渲染期映射"）。
 *
 * **新写而非搬来的五条**（Task 5，`scripts/i18n_template_diff.py` 的表侧单边登记）：
 * [languageTitle] / [languageSystem] / [languageZh] / [languageNote] / [partialTranslationNote]
 * 这五条中文在本页**迁移前的原文里不存在**——`git show 767711d` 的那份 ParentSettingsScreen 还没有
 * 语言分区，Task 5 是为这个新功能新写的文案，因此"表侧整串能在页面原文里找到"这条等价命题
 * 对这五条不成立。它们由 DISCLOSED_RESIDUALS 里五条**表侧单边**登记点名（[languageEn] = "English"
 * 不含汉字，本来就不入集合），逐字节等价的证据只对本页 62 条迁移位点成立。
 *
 * ZH 六条的措辞与该页既有分区一致：标题是名词短语（对照"🔒 家长密码"/"💾 数据备份与恢复"），
 * 说明句用陈述句 + 句号收尾（对照"定期备份孩子的学习数据……时可恢复。"）。
 *
 * 标题的装饰性 emoji 写在**表里**、不写在页面里（languageTitle = "🌐 语言" / "🌐 Language"）：
 * 该页既有四个标题的字面量本来就含 emoji，Task 11 是整串迁移的；页面若在代码里拼
 * "🌐 + 表值"，就等于在页面代码里新增一条 UI 文案，与既有约定分叉。
 * 由 StringsCopyTest.sectionTitleCarriesItsEmojiInsideTheTable 与
 * `emojiStaysInsideTheSettingsTable` 钉住（后者穷举本页 22 条带 emoji 的静态文案与 3 条函数型出场）。
 *
 * [partialTranslationNote] 只在**英文态**渲染（spec §7），中文态下页面不显示这一行。
 * ZH 侧的值仍然保留：两份表必须字段对齐（data class 的构造是编译期成对的），Task 11 之后
 * 若中文态需要另一条提示也仍在同一字段上改，不需要动表形状。
 * **Task 11 完成后本页已经全量双语**，这句话在本页对不上的是"其它未迁页面"（Ruling 27 的
 * 审批列表与效率报表属阶段二），所以这一行仍然成立、不要删。
 *
 * 自称词规则（Ruling 6 + Ruling 15）：[languageZh] 与 [languageEn] 是语言的自称名
 * （endonym），**不随界面语言翻译**——两种 locale 下都显示「中文 / English」。
 * 这是语言选择控件的通用惯例：让用户用自己要找的名字选语言，也避免"中文"被译成
 * "Chinese" 后反而看不懂在选什么。因此这两个字段刻意同值，不进 assertDiffers 漏译守卫，
 * 由 StringsCopyTest.languageOptionLabelsAreEndonymsInBothLocales 用等值断言钉死；
 * SettingsStringsEn 里的"中文"字样同时在 StringsCopyTest.EN_TABLE_ENDONYMS 里登记豁免。
 * 请勿把它"顺手翻译"成 Chinese——豁免表存在的原因就是这里必须是中文。
 *
 * ## 存储边界（Ruling 25 / 31 / 34）：本页是 stage1 里第一个**真有写库调用**的迁移页面
 *
 * 该页八个 sink 的实参来源逐条见任务报告。唯一一条"页面中文进 sink"的既有通道是
 * `:299 → :301 setDeviceName(name)`：`name = deviceName.ifBlank { "家长设备" }`，父级没填设备名时
 * 这条中文兜底值就写进 DataStore 的 DEVICE_NAME，再被 `FamilySyncHelper.exportControlConfig()`
 * （`data/sync/FamilySyncHelper.kt:107` 读同一偏好，自己还带一份同字面量的读侧兜底）打进家长控制包
 * 发给孩子设备，孩子端 `importControlConfig` 把它拼进入库后的提示句（`:172`）。
 * 于是它**是**存储值，不是可随意本地化的显示串：
 * - 处置取 Ruling 34 的选项 (a)「规范值 + 渲染期本地化标签」（与 [CanonicalFocusText] +
 *   [PomodoroStrings.targetLabel]、[CanonicalChildHomeText] 同一族）：中文原字节搬进
 *   [CanonicalParentSettingsText]（该对象不在 Zh/En 表里，存储闸门永久豁免 `Canonical*Text.*`，
 *   见 scripts/i18n_storage_boundary.py 已知边界），页面传给 sink 的只有它和用户输入；
 * - 英文态在**只读渲染位**换标签：`:302` 的保存回执把兜底值经 [deviceNameDisplayLabel] 念成
 *   "Parent device"，认不出的输入原样透传（历史行/用户自取名不受影响）；
 * - `:291` 那个 OutlinedTextField 的 `value` **刻意不本地化**：它是数据编辑器，
 *   把 "Parent device" 显示进可编辑框就等于让父级下一次保存把英文标签写回存储，
 *   正是本节要防的那件事。英文态这一格会显示规范中文兜底值，是**已知上限**，进 Task 12 人工清单
 *   （与 `parentDashboardInterpolatedCopyPinsBothLanguages` 记录的"英文态读出 优秀"同一类）。
 *
 * 另一条**看着像 sink 其实不落库**的：`:232` 传给 `FamilySyncHelper.shareFile(filePath, title)` 的
 * `"发送家长设置到孩子设备"`。实测 `data/sync/FamilySyncHelper.kt:249-256` 根本没用 title 参数
 * （只读文件后转给 `Platform.fileSharer.shareTextFile`），存储字节与它无关，所以它走选项 (b)：
 * 进文案表 [exportChooserTitle] 并在英文态本地化。与 [ChildHomeStrings.shareProgressChooserTitle]
 * 是同一条已披露取舍（ChildHomeStrings 类注释 Ruling 20 例外第 4 条），同理由也写进
 * `StringsCopyTest.settingsInterpolatedCopyPinsBothLanguages` 之外的静态清单。
 *
 * `setDeviceName` 的**用户输入**（父级自己打的业务名）不是文案：不进表、不参与任何断言、
 * 不做"看起来像中文就翻一遍"的处理。`setParentPassword` / `setAppLanguage` /
 * `setWeeklyReportEnabled` / `clearParentPassword` 的实参分别是用户输入、枚举常量、布尔量，
 * 与文案无关；`BackupHelper.backup()` / `restore()`、`exportControlConfig()` 0 个文案实参。
 *
 * ## 同形异义与量词（本页三处，逐处判断）
 *
 * 1. **`分` 三种语义里本页只出现第一种**：`:275` 的 `${cp.totalFocusMinutes}分` 是**分钟**，
 *    单位实参来自 [CommonStrings.minuteLabel]（min），本表**不**开任何 `分` 槽——
 *    由 `StringsCopyTest.settingsDurationUnitComesFromCommon` 用"本表 ZH 静态值里没有一条等于 `分`"
 *    的穷举钉住（第二种评分单位 pts、第三种积分 points 本页没有裸单位出场）。
 * 2. **`次`（`:279`）与 `个`（`:283`）是中文量词**，英文没有对应词：照 §11"道"那条的既有处置
 *    **整词删掉**，于是 [completionsValue] / [todayCheckinsValue] 的英文值是纯数字、没有单位字段。
 *    空串/缺单位不是漏译，见 `settingsEnglishDropsTheMeasureWords`。
 * 3. **`天`（`:273`）**与 ParentHome 同一概念同一词形（Streak 的天数槽），本表各存一份、
 *    由 `settingsStreakDaysMatchParentHomeForm` 断言两侧输出逐字相同（刻意不跨表引用）。
 *
 * ## `确定` / `确认`（Task 11 实测分槽）
 *
 * [passwordOkButton]（`:429` `确定`）与 [CommonStrings.confirmButton]（`确认`）是**两个中文值**，
 * 差一个字、字节也不同：复用会把 ZH 改掉（brief 的字节等价约束优先），各写一份又留下两个
 * "Confirm" 可以互相漂移。处置是 ZH 各自保留原字节、英文**分开词形**（`OK` vs `Confirm`），
 * 由 `settingsOkButtonIsNotTheCommonConfirmButton` 逐字钉住两侧，并补 §11 词条。
 *
 * ## Ruling 20 的四条件非 1:1 位点（按字段名披露，报告里同名单列）
 *
 * - [progressFocusMinutesValue]：`(minutes, unit)` 两个实参位，单位来自另一张表
 *   （[CommonStrings.minuteLabel]），页面写 `strings.progressFocusMinutesValue(n, common.minuteLabel)`；
 *   ZH 输出逐字节等于迁移前的 `${cp.totalFocusMinutes}分`。与 [PomodoroStrings.durationChip]、
 *   [ParentDashboardStrings.focusMinutesValue] 同一条例外。
 * - [completionsValue] / [todayCheckinsValue]：英文侧量词整词删掉（上面第 2 条）。
 * - [streakDaysValue]：英文需要 one/other，[Plurals.kt] 本期不改（任务约束），分支写在函数体内，
 *   ZH 侧不分支；由 `settingsEnglishPluralsCoverOneAndOther` 用"中文侧只是换数字"的等式钉住。
 * - [progressCardTitle]：中文"`${deviceName}的学习进度`"的所属关系在英文里必须换成前置介词短语
 *   "Study progress from …"（ZH 侧逐字节不变）；实参是**数据**（孩子设备名），属内容透传。
 * - [deviceNameSavedMessage]：同上，实参是数据；英文语序 "Device name saved: X"。
 * - [deviceNameDisplayLabel]：中文侧恒等函数（规范值就是显示值），只有英文侧做映射——
 *   这条是"存规范值、渲染本地化"的必然形状，不是文案改动。
 * - [backupRunningLabel] / [restoreSuccessMessage]：中文的 `...`（三个句点）与 `…`（U+2026）
 *   在本页**两种都有**（`:158`/`:180` 三个句点、`:255` 单字符省略号），ZH 侧逐字节保留原样，
 *   EN 侧统一用三个句点，因为英文排版惯例不用 U+2026。间距/标点不参与 ZH 字节等价判定，
 *   但 `i18n_template_diff.py` 的 [B] 层比较中文文案里的标点，所以三条都逐字登记在测试里。
 *
 * 函数型字段（本页 10 个）的返回值不在 data class `toString()` 里（Ruling 20-b 的盲区），
 * 整表 CJK 扫描看不见，因此每个都有逐字打出来的双语期望串，见
 * `StringsCopyTest.settingsInterpolatedCopyPinsBothLanguages`；静态字段全部进
 * `settingsCopyDiffers` 的 ZH≠EN 穷举清单。
 *
 * 术语按设计文档 §11：家长审核 = Approval、设备配对 = Pairing（[remoteControlNote] 里的"配对"）、
 * 周报 = Weekly Report、连续打卡 = Streak、打卡 = Check in / Checked in、错题 = Mistakes、
 * 专注 = Focus、番茄钟 = Pomodoro、积分 = Points、时长单位 = min。本轮为"数据备份与恢复 /
 * 家长远程控制 / 完成证明之外的确定 vs 确认 / 量词次·个 / 兜底设备名"补的词条同步进 §11，
 * 每条带真实测试名。
 */
data class SettingsStrings(
    // ===== 语言分区（Task 5，字段与值原样保留）=====
    val languageTitle: String,
    val languageSystem: String,
    val languageZh: String,
    val languageEn: String,
    val languageNote: String,
    val partialTranslationNote: String,

    // ===== 顶栏与家长密码分区（Task 11）=====
    val pageTitle: String,
    val passwordCardTitle: String,
    val passwordSetNote: String,
    val passwordMissingNote: String,
    val changePasswordButton: String,
    val setPasswordButton: String,
    val clearPasswordButton: String,
    val passwordClearedMessage: String,
    val passwordSetMessage: String,

    // ===== 家长密码对话框（PasswordSetupDialog，同文件私有辅助 Composable）=====
    val passwordDialogEditTitle: String,
    val passwordDialogSetTitle: String,
    val newPasswordLabel: String,
    val confirmPasswordLabel: String,
    val passwordTooShortError: String,
    val passwordMismatchError: String,
    val passwordOkButton: String,

    // ===== 每周学习周报分区 =====
    val weeklyReportTitle: String,
    val weeklyReportNote: String,
    val weeklyReportOnMessage: String,
    val weeklyReportOffMessage: String,

    // ===== 数据备份与恢复分区 =====
    val backupCardTitle: String,
    val backupNote: String,
    val backupButton: String,
    val backupRunningLabel: String,
    val restoreButton: String,
    val backupSuccessMessage: String,
    val backupFailureMessage: (detail: String?) -> String,
    val restoreDialogTitle: String,
    val restoreDialogNote: String,
    val restoreConfirmButton: String,
    val restoreSuccessMessage: String,
    val restartAppNote: String,
    val restoreFailureMessage: (detail: String?) -> String,

    // ===== 家长远程控制分区 =====
    val remoteControlTitle: String,
    val remoteControlNote: String,
    val howToTitle: String,
    val pushStepNote: String,
    val viewStepNote: String,
    val transportNote: String,
    val pushSettingsButton: String,
    val viewProgressButton: String,
    val processingLabel: String,
    val exportChooserTitle: String,
    val settingsExportedMessage: String,
    val exportFailedMessage: (detail: String?) -> String,
    val progressImportedMessage: String,
    val childProgressImportFailedMessage: (detail: String?) -> String,

    // ===== 孩子进度卡片 =====
    val progressCardTitle: (deviceName: String) -> String,
    val progressPointsLabel: String,
    val progressStreakLabel: String,
    val streakDaysValue: (days: Int) -> String,
    val progressFocusLabel: String,
    val progressFocusMinutesValue: (minutes: Int, unit: String) -> String,
    val progressCheckinLabel: String,
    val completionsValue: (count: Int) -> String,
    val progressMistakesLabel: String,
    val progressTodayCheckinLabel: String,
    val todayCheckinsValue: (count: Int) -> String,

    // ===== 设备名称（存储边界见类注释）=====
    val deviceNameLabel: String,
    val deviceNamePlaceholder: String,
    val saveDeviceNameButton: String,
    val deviceNameSavedMessage: (name: String) -> String,
    val deviceNameFallbackLabel: String,
    val deviceNameDisplayLabel: (stored: String) -> String,
)

/**
 * 家长设置页里**落库**的规范中文（Ruling 25，模式与 [CanonicalFocusText]、[CanonicalChildHomeText] 相同）。
 *
 * 为什么这条必须由常量而不是文案表承担：`ParentSettingsScreen.kt:299` 的兜底值经
 * `setDeviceName`（`UserPreferences.kt:138`，DataStore DEVICE_NAME）落盘，之后被
 * `FamilySyncHelper.exportControlConfig()`（`:107` 读同一偏好）打进家长控制包传给设备；
 * 若它跟着界面语言走，父级切一次英文再保存，同一台设备的名字就变成两个值，
 * 孩子端看到的来源标识也随之分裂。值必须与迁移前逐字节相同，
 * 由 `StringsCopyTest.settingsDeviceNameFallbackStaysCanonical` 钉住；
 * 英文态只在只读渲染位换标签（见 [SettingsStrings.deviceNameDisplayLabel]）。
 *
 * 与 [CanonicalFocusText] 一样**刻意不进文案表**：表值随语言实例走，存储闸门见到 `strings.`
 * 进 sink 就该红；规范值放这里闸门才能继续当硬约束用（`Canonical*Text.*` 的豁免见脚本注释）。
 */
object CanonicalParentSettingsText {
    /** `ParentSettingsScreen.kt:299` 迁移前传给 `setDeviceName(…)` 的兜底原值 */
    const val DEVICE_NAME_FALLBACK = "家长设备"
}

val SettingsStringsZh = SettingsStrings(
    // ===== 语言分区（Task 5）=====
    languageTitle = "🌐 语言",
    languageSystem = "跟随系统",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "选择界面显示语言，立即生效。",
    partialTranslationNote = "部分页面仍在适配英文，会暂时保持中文显示。",

    // ===== 顶栏与家长密码分区 =====
    pageTitle = "⚙️ 家长设置",                                            // :74
    passwordCardTitle = "🔒 家长密码",                                     // :87
    passwordSetNote = "已设置密码。孩子切换到家长身份或修改规则时需输入密码。",          // :89
    passwordMissingNote = "未设置密码。建议设置密码，防止孩子随意修改积分和任务规则。",     // :90
    changePasswordButton = "修改密码",                                      // :96
    setPasswordButton = "设置密码",                                         // :96
    clearPasswordButton = "清除密码",                                       // :106
    passwordClearedMessage = "已清除家长密码",                               // :103
    passwordSetMessage = "家长密码已设置",                                   // :383

    // ===== 家长密码对话框 =====
    passwordDialogEditTitle = "修改家长密码",                                // :402
    passwordDialogSetTitle = "设置家长密码",                                 // :402
    newPasswordLabel = "新密码（建议4-6位数字）",                             // :407
    confirmPasswordLabel = "确认密码",                                      // :413
    passwordTooShortError = "密码至少4位",                                  // :425
    passwordMismatchError = "两次输入的密码不一致",                            // :426
    passwordOkButton = "确定",                                             // :429

    // ===== 每周学习周报分区 =====
    weeklyReportTitle = "📊 每周学习周报",                                   // :118
    weeklyReportNote = "每周日晚定时推送孩子本周学习情况（打卡/专注/复习/积分），无需打开 App 即可掌握。",  // :119
    weeklyReportOnMessage = "✅ 已开启每周学习周报（每周日晚推送）",              // :129
    weeklyReportOffMessage = "已关闭每周学习周报",                            // :132

    // ===== 数据备份与恢复分区 =====
    backupCardTitle = "💾 数据备份与恢复",                                   // :143
    backupNote = "定期备份孩子的学习数据（积分/错题/专注/成就等），换设备或误卸载时可恢复。",  // :144
    backupButton = "备份数据",                                             // :158
    backupRunningLabel = "备份中...",                                      // :158
    restoreButton = "恢复数据",                                            // :162
    backupSuccessMessage = "✅ 备份成功！文件已保存到应用目录",                  // :153
    backupFailureMessage = { detail -> "❌ 备份失败：$detail" },             // :154
    restoreDialogTitle = "确认恢复数据",                                    // :170
    restoreDialogNote = "恢复将用备份覆盖当前所有数据，并重启应用。此操作不可撤销，确定继续吗？",  // :171
    restoreConfirmButton = "确认恢复",                                      // :190
    restoreSuccessMessage = "✅ 恢复成功，即将重启应用...",                    // :180
    restartAppNote = "请重新启动 App 以生效",                               // :184
    restoreFailureMessage = { detail -> "❌ 恢复失败：$detail" },            // :187

    // ===== 家长远程控制分区 =====
    remoteControlTitle = "📲 家长远程控制",                                  // :199
    remoteControlNote = "孩子有自己的手机/平板？配对后您可以在自己手机上查看孩子进度、推送任务设置。",  // :201
    howToTitle = "使用方法",                                               // :212
    pushStepNote = "📤 推送设置：把您配置的任务/奖励发送给孩子设备应用",            // :213
    viewStepNote = "📥 查看进度：接收孩子发来的学习进度报告",                    // :215
    transportNote = "💡 传输方式：蓝牙/WiFi直连/微信/邮件等任何方式均可",          // :217
    pushSettingsButton = "📤 推送设置给孩子",                                // :242
    viewProgressButton = "📥 查看孩子进度",                                 // :248
    processingLabel = "正在处理…",                                         // :255
    exportChooserTitle = "发送家长设置到孩子设备",                             // :232
    settingsExportedMessage = "✅ 设置已导出，请发送给孩子",                    // :234
    exportFailedMessage = { detail -> "导出失败：$detail" },                 // :236
    progressImportedMessage = "✅ 已获取孩子最新进度",                        // :55
    childProgressImportFailedMessage = { detail -> "导入失败：$detail" },     // :57（异常文本原样透传）

    // ===== 孩子进度卡片 =====
    progressCardTitle = { deviceName -> "📊 ${deviceName}的学习进度" },       // :267
    progressPointsLabel = "⭐ 总积分",                                     // :270
    progressStreakLabel = "🔥 连续打卡",                                   // :272
    streakDaysValue = { days -> "${days}天" },                            // :273
    progressFocusLabel = "🍅 总专注",                                      // :274
    progressFocusMinutesValue = { minutes, unit -> "$minutes$unit" },      // :275（单位来自 common.minuteLabel）
    progressCheckinLabel = "✏️ 总打卡",                                    // :278
    completionsValue = { count -> "${count}次" },                          // :279
    progressMistakesLabel = "📚 错题掌握",                                 // :280
    progressTodayCheckinLabel = "📅 今日打卡",                              // :282
    todayCheckinsValue = { count -> "${count}个" },                        // :283

    // ===== 设备名称 =====
    deviceNameLabel = "本设备名称",                                        // :293
    deviceNamePlaceholder = "如：妈妈的手机",                                // :294
    saveDeviceNameButton = "保存设备名称",                                  // :304
    deviceNameSavedMessage = { name -> "已保存设备名称：$name" },             // :302
    deviceNameFallbackLabel = "家长设备",                                  // :299 的显示侧；落库值是 CanonicalParentSettingsText
    deviceNameDisplayLabel = { stored -> stored },                         // 中文态规范值即显示值，恒等
)

val SettingsStringsEn = SettingsStrings(
    // ===== 语言分区（Task 5；languageZh 是自称词，两表同值）=====
    languageTitle = "🌐 Language",
    languageSystem = "System default",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "Choose the app language. Changes apply immediately.",
    partialTranslationNote = "Some screens are not translated yet and stay in Chinese.",

    // ===== 顶栏与家长密码分区 =====
    pageTitle = "⚙️ Parent settings",
    passwordCardTitle = "🔒 Parent password",
    passwordSetNote = "A password is set. It is required to switch to the parent role or change the rules.",
    passwordMissingNote = "No password yet. Set one so your child can't change points or task rules on their own.",
    changePasswordButton = "Change password",
    setPasswordButton = "Set password",
    clearPasswordButton = "Remove password",
    passwordClearedMessage = "Parent password removed",
    passwordSetMessage = "Parent password set",

    // ===== 家长密码对话框 =====
    passwordDialogEditTitle = "Change parent password",
    passwordDialogSetTitle = "Set parent password",
    newPasswordLabel = "New password (4-6 digits recommended)",
    confirmPasswordLabel = "Confirm password",
    passwordTooShortError = "Use at least 4 digits",
    passwordMismatchError = "The two passwords don't match",
    passwordOkButton = "OK",

    // ===== 每周学习周报分区 =====
    weeklyReportTitle = "📊 Weekly report",
    weeklyReportNote = "Every Sunday evening we send a summary of your child's week — check-ins, focus, review and points — no need to open the app.",
    weeklyReportOnMessage = "✅ Weekly report is on — it arrives every Sunday evening",
    weeklyReportOffMessage = "Weekly report turned off",

    // ===== 数据备份与恢复分区 =====
    backupCardTitle = "💾 Backup and restore",
    backupNote = "Back up your child's study data — points, mistakes, focus and achievements — to restore it on a new device or after an accidental uninstall.",
    backupButton = "Back up data",
    backupRunningLabel = "Backing up...",
    restoreButton = "Restore data",
    backupSuccessMessage = "✅ Backup complete — the file is saved in the app folder",
    backupFailureMessage = { detail -> "❌ Backup failed: $detail" },
    restoreDialogTitle = "Confirm data restore",
    restoreDialogNote = "Restoring overwrites all current data with the backup and restarts the app. This can't be undone. Continue?",
    restoreConfirmButton = "Restore now",
    restoreSuccessMessage = "✅ Restore complete — the app is restarting...",
    restartAppNote = "Please reopen the app for the changes to take effect",
    restoreFailureMessage = { detail -> "❌ Restore failed: $detail" },

    // ===== 家长远程控制分区 =====
    remoteControlTitle = "📲 Remote control",
    remoteControlNote = "Does your child have their own phone or tablet? Once paired, you can check their progress and send task settings from your own device.",
    howToTitle = "How it works",
    pushStepNote = "📤 Push settings — send the tasks and rewards you set up to your child's device",
    viewStepNote = "📥 View progress — receive the study report your child sends you",
    transportNote = "💡 Transfer method — Bluetooth, Wi-Fi Direct, WeChat, email, any of them works",
    pushSettingsButton = "📤 Send settings to child",
    viewProgressButton = "📥 View child's progress",
    processingLabel = "Working...",
    exportChooserTitle = "Send parent settings to your child's device",
    settingsExportedMessage = "✅ Settings exported — send the file to your child",
    exportFailedMessage = { detail -> "Export failed: $detail" },
    progressImportedMessage = "✅ Latest progress from your child is loaded",
    childProgressImportFailedMessage = { detail -> "Import failed: $detail" },

    // ===== 孩子进度卡片 =====
    progressCardTitle = { deviceName -> "📊 Study progress from $deviceName" },
    progressPointsLabel = "⭐ Total points",
    progressStreakLabel = "🔥 Streak",
    streakDaysValue = { days -> if (days == 1) "$days day" else "$days days" },
    progressFocusLabel = "🍅 Total focus",
    progressFocusMinutesValue = { minutes, unit -> "$minutes $unit" },
    progressCheckinLabel = "✏️ Total check-ins",
    completionsValue = { count -> "$count" },
    progressMistakesLabel = "📚 Mistakes mastered",
    progressTodayCheckinLabel = "📅 Today's Check-in",
    todayCheckinsValue = { count -> "$count" },

    // ===== 设备名称 =====
    deviceNameLabel = "This device's name",
    deviceNamePlaceholder = "e.g. Mom's phone",
    saveDeviceNameButton = "Save device name",
    deviceNameSavedMessage = { name -> "Device name saved: $name" },
    deviceNameFallbackLabel = "Parent device",
    // 存储的是规范中文（见 CanonicalParentSettingsText），这里只在渲染时换英文标签；
    // 认不出来的输入原样返回 —— 父级自己起的设备名，以及本分支测试期已写入的英文行。
    // 字面量与上面 deviceNameFallbackLabel 是同一个词的两处出处，与 PomodoroStrings.targetLabel
    // 那一族同形；两侧必须一模一样，由 StringsCopyTest.settingsDeviceNameFallbackStaysCanonical 钉住。
    deviceNameDisplayLabel = { stored ->
        if (stored == CanonicalParentSettingsText.DEVICE_NAME_FALLBACK) "Parent device" else stored
    },
)
