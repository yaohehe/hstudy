// 通道 A 的形状句柄分支（Task 9b 审查 Important 3）：旧 COPY_SOURCE 第 3 支按**标识符名**认句柄
// （`\w*Strings`），而仓库自己的约定里有整整一批句柄名字不以 Strings 结尾 ——
// `val common = LocalStrings.current.common` 在真实树就有 4 处（ChildDailyPlanScreen.kt:123/172、
// ChildMemoryTrainingScreen.kt:26、ChildPomodoroScreen.kt:28）。审查探针 R4
// `adjustPoints(1, common.deleteContentDescription)` 当时实测 exit 0，现在 P1 必须报红。
// P4 钉住句柄别名（`val alias = common`）——审查 Important 5 点名的 `val cur = strings` 形态，
// 本轮从"边界"改成了"覆盖"。
// 与 CanonicalCompliant.kt 成对：那份文件里同样绑了 `common`，但没有一个 `common.` 进 sink，
// 必须继续全绿（句柄登记本身不许造成误报）。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun GroupHandleCopyScreen() {
    val app = AppGraph
    val common = LocalStrings.current.common
    val plan = LocalStrings.current.focus
    val alias = common

    // P1 探针原样：形状句柄的字段直接进 sink
    app.pointRepository.adjustPoints(1, common.deleteContentDescription)

    // P2 句柄字段先赋给变量，再经变量进 sink（C 通道 + 形状句柄的复合形态）
    val note = common.saveButtonLabel
    app.pointApprovalRepository.submitApproval("R7", note, 1)

    // P3 另一组的句柄 + 函数型字段
    app.focusRepository.recordFocusSession(25, plan.focusTargetLabel(3), 5)

    // P4 句柄别名沿赋值链继续登记
    app.pointRepository.spendPoints(3, alias.petFeedTitle)

    // P5 实体构造里的具名字段
    app.taskRepository.updateTask(TaskEntity(title = common.confirmButtonLabel))
}
