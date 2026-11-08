// 通道 D（委托污点）：`var x by remember { mutableStateOf(<文案>) }` → 之后 x 进 sink 即违规。
// 真实形态来自 Task 7b 审查的 ChildPomodoroScreen.kt:47（当时只有赋值那条，这一形漏抓）。
// 第二、三段另给出 rememberSaveable 与带 key 的 remember 两个变体。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun DelegateTaintScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.pomodoro

    var storedNote by remember { mutableStateOf(strings.breathingGuide) }
    app.pointRepository.adjustPoints(2, storedNote)

    var savedTitle by rememberSaveable { mutableStateOf(strings.phaseFocus) }
    app.focusRepository.recordFocusSession(25, savedTitle, 5)

    var keyedNote by remember(strings) { mutableStateOf(strings.finishEncouragement) }
    app.pointApprovalRepository.submitApproval("FOCUS_COMPLETE", keyedNote, 4)
}
