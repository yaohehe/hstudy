package com.hstudy.app.i18n

/**
 * 家长密码门禁（ui/navigation/AppNavGraph.kt 的 `RoleSelectDestination`）文案表（终审 Ruling 41）。
 *
 * **为什么单独一张表而不并进 [SettingsStrings]**：那张表的出处是"家长设置页"（文件头第一行就是
 * `ui/parent/ParentSettingsScreen.kt`），等价命令的页面组也只带那一页；本表这三条住在
 * `AppNavGraph.kt`，并进设置表会让"表 ↔ 页面"的对应关系出现第二个页面，
 * 而 `scripts/i18n_template_diff.py` 的等价命题正是靠这层对应成立的。
 * 与 [CommonStrings] 同理：**出处按组件而不是按页面**，一处文案一个出处。
 *
 * **两个按钮词不在本表**（Ruling 41 的"复用现有字段"半句）：
 * - `确定`（原 `AppNavGraph.kt:281`）复用 [SettingsStrings.passwordOkButton]，中文值逐字节相同；
 * - `取消`（原 `AppNavGraph.kt:283`）复用 [CommonStrings.cancelButton]。
 * 在本表重开一个同值槽等于再造一个可以互相漂移的出处，与 [CommonStrings] 类注释里
 * "通用按钮词现在就上收"那条决定相反，所以这里只留一条引用注释、不留字段。
 * 两条复用由 `StringsCopyTest.parentGateButtonsComeFromTheirOwnTables` 钉住出处，
 * 登记在 `scripts/i18n_template_diff.py` 的 DISCLOSED_RESIDUALS 里（两条页面单边残差）。
 *
 * ZH 三条逐字来自迁移前的 `AppNavGraph.kt:254`（对话框标题）、`:260`（输入框标签）、
 * `:278`（密码校验失败提示）；标题的装饰性 🔒 写在**表里**，与 [SettingsStrings.passwordCardTitle]
 * 那条既有约定一致（`"🔒 家长密码"` / `"🔒 Parent password"`），页面代码里不拼 emoji。
 *
 * EN 按设计文档 §11：家长密码 = Parent password、专注/密码类错误提示用祈使句。
 * `密码错误，请重试` 的英文补出主语（英文那句没有主语读不通），中文侧原样。
 */
data class ParentGateStrings(
    val passwordGateTitle: String,
    val passwordGateInputLabel: String,
    val passwordGateWrongPasswordError: String,
)

val ParentGateStringsZh = ParentGateStrings(
    passwordGateTitle = "🔒 请输入家长密码",                                 // 原 AppNavGraph.kt:254
    passwordGateInputLabel = "家长密码",                                     // 原 :260
    passwordGateWrongPasswordError = "密码错误，请重试",                       // 原 :278
)

val ParentGateStringsEn = ParentGateStrings(
    passwordGateTitle = "🔒 Enter parent password",
    passwordGateInputLabel = "Parent password",
    passwordGateWrongPasswordError = "Wrong password, please try again",
)
