// 通道 A（直接引用）：sink 实参里直接出现文案表引用。
// 本文件**不参与编译**（在 scripts/tests/fixtures 下，不属于任何 Kotlin 源集），
// 它是闸门自证用的"必须报红"样本；见 scripts/tests/i18n_storage_boundary_selftest.py。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun DirectReferenceScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    // A1 位置实参
    app.pointRepository.adjustPoints(5, strings.milestoneBonusNote)
    // A2 具名实参
    app.pointApprovalRepository.submitApproval(
        actionType = "TASK_COMPLETE",
        description = strings.weeklyChallengeDescription,
        points = 3,
    )
    // A3 文案经函数型字段（lambda）求值后传入，仍是同一行的文案引用
    app.pointRepository.checkInForTask(taskId = 1L, points = 2, note = strings.pointsEarnedLabel(2))
}
