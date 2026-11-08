// 通道 C（赋值污点）：Task 9b 缺陷 1 的正面样本。
// 旧正则（贪婪 .* + re.DOTALL）在这个文件里只会产出**一个位置性的名字**，
// 下面四个真被文案赋值的变量一个都进不了污点集；新正则要求值限定在同一行内、
// 并允许类型注解（`: () -> String`），于是四个全部命中。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun AssignTaintScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    val unrelatedWidth = 12.dp
    val plainCount = tasks.size + 3

    // C1 朴素赋值
    val storedDesc = strings.checkInDescription
    app.pointRepository.checkIn(bizKey = "TASK_1", description = storedDesc, points = 1)

    // C2/C3 同文件里两个赋值都要进污点集（旧实现只报得到最左边那个 var）
    val firstNote = strings.rewardStoreNote
    val secondNote = strings.petSpendNote
    app.pointRepository.spendPoints(10, firstNote)
    app.pointRepository.spendPoints(20, secondNote)

    // C4 函数型（lambda）文案字段：带类型注解，旧模式被 `: ` 挡掉
    val typedNote: () -> String = { strings.milestoneBonusNote }
    app.pointApprovalRepository.submitApproval("MILESTONE", typedNote(), 6)

    // C5 文案出现在具名实参的内联 copy(...) 里，也要被判污点
    val copied = task.copy(note = strings.weeklyChallengeDescription)
    app.taskRepository.updateTask(copied)
}
