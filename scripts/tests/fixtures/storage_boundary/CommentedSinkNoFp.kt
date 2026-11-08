// 反向样本（必须绿）：这一条**专门为 strip_comments 而存在**（Task 9b 审查 Minor：
// 原来这个保护只是"某个 fixture 恰好带了一行注释掉的 sink"，没有任何东西钉住它）。
// 每一段注释里都塞一条"落到代码上必然判红"的形态，代码部分则全部合规；
// strip_comments 一旦被删掉或改成只处理 `//`，本文件的命中数立刻从 0 变成 ≥4。
package com.hstudy.app.fixture

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun CommentedSinkNoFpScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    val common = LocalStrings.current.common

    // 1) 行注释：直接引用 + 大写后缀写库路径 + 形状句柄，三条都是真违规形态
    // app.pointRepository.adjustPoints(5, strings.milestoneBonusNote)
    // app.taskRepository.addTask(TaskEntity(title = common.taskTitleFallback))

    /* 2) 块注释：连同污点链的源头一起注释掉了，两个名字都不许进污点集
       val commentedNote = strings.checkInDescription
       val commentedHop = commentedNote
       app.pointApprovalRepository.submitApproval("COMMENTED", commentedHop, 2)
    */

    // 3) 行尾注释挂在合规调用后面
    app.pointRepository.undoCheckIn(bizKey, task.id, date) // 老写法：adjustPoints(1, strings.x)

    // 4) 代码部分全部合规：用户输入进 sink、文案只进渲染面
    val userInput = textFieldValue
    app.focusRepository.addPlan(DailyPlanEntity(taskTitle = userInput, plannedAt = nowMillis))
    Text(strings.milestoneBonusNote)
    Text(common.saveButtonLabel)
}
