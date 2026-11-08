// 反向样本（必须绿）：插值保留**不能**把字符串的另一半变成代码。
// Task 9b 审查 Critical 1 的修法有两半，这一条钉住第二半：
//   (1) 非插值部分仍然照旧空白化 → 串里写的 `save(` / `checkIn(x)` / 赋值示例不得判红；
//   (2) 插值区按代码处理，但插值里**再套一层字面量**时那层字面量的内容也要空白化
//       （N4 是这条的机理探针：空白化失灵时 `demoNote` 会被当成真赋值污染，
//        紧接着那条**用户输入**的同名变量就误报）。
package com.hstudy.app.fixture

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun InterpolationNoFpScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome

    // N1 非插值部分里的 sink 名与英文括号文案：照旧只是文字
    Text("已完成(save) 与 checkIn(x) 的说明 ${count + 1} 项")

    // N2 `$name` 带的确实是代码，但它是用户输入 → 写库合法
    val draftTitle = "标题：$userTitle"
    app.focusRepository.addPlan(DailyPlanEntity(taskTitle = draftTitle, plannedAt = nowMillis))

    // N3 插值里的字面量内容仍被空白化，所以 `save(` / `insert(` 是假头不是调用点
    app.pointRepository.adjustPoints(2, "说明 ${if (ok) "save(it)" else "insert(x)"} 结束")

    // N4 插值里写的**赋值示例**（帮助文案的真实形态）不得进污点集
    Text("写法示例：${"val demoNote = strings.demoTip 会被写进库里"}")
    val demoNote = userInput
    app.pointApprovalRepository.submitApproval("DEMO", demoNote, 1)

    // N5 同一份文案既进渲染又进规范值通道：与本闸门无关
    Text(strings.milestoneBonusNote)
    app.pointRepository.checkIn("BIZ_N5", CanonicalChildHomeText.milestoneReward(days), 2)
}
