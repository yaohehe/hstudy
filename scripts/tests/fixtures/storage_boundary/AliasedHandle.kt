// 通道 A 的别名句柄变体：Task 9b 缺陷 3 的正面样本。
// 旧 COPY_SOURCE 把 `(?:Zh|En)` 写成强制后缀，于是下面这些"活的"第二文案句柄
// （ChildHomeScreen.kt:39 的 taskListStrings、PomodoroTimer.kt:49 的 currentStrings）
// 在闸门里完全隐身，跨表复用的字段可以随便写进库。
// 同时钉住：`LocalStrings.current.<组>` 两级是"取句柄"（合规，见 CanonicalCompliant.kt），
// 三级以上 `LocalStrings.current.<组>.<字段>` 才是文案。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun AliasedHandleScreen() {
    val app = AppGraph
    val taskListStrings = LocalStrings.current.taskList
    val homeStrings = LocalStrings.current.childHome
    val currentStrings = homeStrings

    // G1 别名句柄 + 函数型字段（真实形态：ChildHomeScreen.kt:264）
    app.pointRepository.adjustPoints(1, taskListStrings.pointsEarnedLabel(3))
    // G2 别名句柄 + 普通字段
    app.pointApprovalRepository.submitApproval("ALIAS", currentStrings.milestoneBonusNote, 1)
    // G3 全表实例写法
    app.pointRepository.checkInForTask(1L, 1, AppStringsZh.weeklyChallengeDescription)
    // G4 高阶链式取字段（三级以上）
    app.focusRepository.recordFocusSession(5, LocalStrings.current.pomodoro.phaseBreak, 1)
    // G5 别名句柄先赋给变量，再经变量进 sink（缺陷 3 + 缺陷 1 的复合形态）
    val aliasNote = taskListStrings.taskDoneLabel
    app.pointApprovalRepository.submitApproval("ALIAS_VIA_VAR", aliasNote, 1)
}
