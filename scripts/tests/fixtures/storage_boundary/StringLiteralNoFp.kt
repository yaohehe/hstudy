// 反向样本（必须绿）：Task 9b 缺陷 6 —— 字符串字面量里的"代码样子"不算代码。
// 三类都要挡住：
//   (1) 文案串里出现的 `save(` / `checkIn(y)` 假 sink 头（旧实现会把它当成调用点，
//       只是 extract_args 自己也跳字符串、引号奇偶会让配对失败，所以极少真报红——
//       但每个假头都是一次无意义的括号扫描，属于脆性）；
//   (2) **真正会误报的一类**：文案串里写着赋值示例（帮助文案/错误提示常这么写），
//       旧实现把串里的 `val demoNote = strings.demoTip` 当成代码收进污点集，
//       于是紧接着那个同名的**用户输入**变量被判成"经由变量 demoNote 传入"→ 合规代码判红；
//   (3) 9b 审查 Important 3 新加的形状句柄也吃同一份空白化输入：串里写的
//       `val demoHandle = LocalStrings.current.common` 若漏进去就会被登记成句柄，
//       下面 `demoHandle.noteText` 那条用户输入就会被判成"直接引用文案表"。
// 修法：扫描前把字符串的非插值内容空白化（引号与换行保留，行号不受影响）。
// 与 ParenInStringStillCaught.kt 成对：那条钉住"空白化**不带插值的**字面量不会漏抓"；
// 插值那一半的边界见 InterpolationCopy.kt / InterpolationNoFp.kt。
package com.hstudy.app.fixture

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun StringLiteralNoFpScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    Text("已完成(点击撤销) update(一下) save(这条) checkIn(y)")
    Text("详见 https://hstudy.example.com/docs/checkIn(points) 与 insert(x) 的说明")
    Text("多行文案里也有括号 (a(b)c) 和引号转义 \" save( ")
    // 上面第 (2) 类：串里的赋值示例不得进污点集，下面这条同名变量的真实赋值是用户输入
    Text("写法示例：val demoNote = strings.demoTip 会被写进库里")
    val demoNote = userInput
    app.pointRepository.adjustPoints(1, demoNote)
    // 第 (3) 类（9b 审查 Important 3 的副作用检查）：串里写的"取组句柄示例"如果漏进代码，
    // 会被 HANDLE_BIND 登记成形状句柄，于是下面这个**来自用户输入**的同名变量在
    // `demoHandle.noteText` 那一行被判成文案 → 合规代码判红。空白化必须先把这类示例抹掉。
    Text("写法示例：val demoHandle = LocalStrings.current.common 就是取组句柄")
    val demoHandle = readDraftFromUser()
    app.pointRepository.adjustPoints(2, demoHandle.noteText)
    // 用户输入（TextField）走写库是允许的：这不是文案表
    app.focusRepository.addPlan(DailyPlanEntity(taskTitle = userInput, plannedAt = nowMillis))
    app.userPreferences.setDeviceName(viewModel.deviceName.value)
    // 规范值走 sink 是 Ruling 25 的处置形态
    app.pointApprovalRepository.submitApproval(
        "TASK_COMPLETE",
        CanonicalChildHomeText.milestoneReward(days),
        points,
    )
}
