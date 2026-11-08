// 反向样本（必须绿）：今天真实存在的合规形态，一条都不许判红。
// 这份 fixture 是 Task 9b 缺陷 1 的另一半证据——旧 TAINT_ASSIGN 每文件只产出一个**位置性**
// 名字（ChildHomeScreen.kt → ['app']、ChildTaskListScreen.kt → ['strings']、
// CommonComponents.kt → ['widthModifier']），而 `app` 恰好出现在下面这些 lambda 实参里，
// 于是合规代码被误报。新实现下这三个名字都不该进污点集。
// Canonical*Text.* 是 Ruling 25 处置方案的规范值出口（ChildHomeStrings.kt:193、
// PomodoroStrings.kt:269），永久豁免，不得改成文案。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.CanonicalChildHomeText
import com.hstudy.app.i18n.CanonicalFocusText
import com.hstudy.app.i18n.LocalStrings

@Composable
fun CanonicalCompliantScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    val taskListStrings = LocalStrings.current.taskList
    val widthModifier = Modifier.fillMaxWidth().padding(12.dp)

    // 1) Canonical 规范值进 sink：Ruling 25 规定的处置形态（存规范值、渲染时本地化）
    app.focusRepository.recordFocusSession(minutes, CanonicalFocusText.TARGET_FREE_STUDY, 0)
    app.pointApprovalRepository.submitApproval(
        "TASK_COMPLETE",
        CanonicalFocusText.recordDescription(focusTarget, minutes),
        points,
    )
    app.pointRepository.adjustPoints(5, CanonicalChildHomeText.weeklyChallengeReward)

    // 2) `app` 作为 lambda 参数出现（旧污点集里的假名字，绝不能被判成"经由变量传入"）
    scope.launch { app.pointRepository.checkInForTask(taskId, points, "user note") }
    val onClick = { app2 -> app2.userPreferences.setDeviceName(userInput) }
    tasks.forEach { app.pointRepository.undoCheckIn(app.bizKeyOf(it), it.id, date) }

    // 3) 取组句柄（两级 LocalStrings.current.<组>）不是文案
    val common = LocalStrings.current.common

    // 4) 句柄变量本身进 lambda / 传给非 sink 函数
    val render = { s -> Text(s.dailyReflectionTag) }
    LaunchedEffect(strings) { viewModel.onLanguageChanged(strings) }

    // 5) 文案只流向渲染面（Text / 提示列表），不流向存储：`add(...)` 是 buildList 的渲染列表，
    //    大写后缀规则 `\b(?:add|set)[A-Z]\w*(` 故意不匹配裸 `add(`；`setOf(`/`addAll(` 在
    //    SINK_CAPSUFFIX_DENY 里逐字排除（脚本 SINK 名单第 5 条有取证说明）
    val weakTips = buildList {
        if (score < 20) add(strings.weakTipCheckin)
        add(taskListStrings.pointsEarnedLabel(2))
    }
    weakTips.forEach { tip -> Text(tip) }
    Text(strings.improvementSuggestionsHeading)

    // 6) 用户输入（TextField / ViewModel）进 sink：与本闸门无关
    app.focusRepository.addPlan(DailyPlanEntity(taskTitle = draft.value, plannedAt = nowMillis))
    app.assignmentRepository.assignTask(TitleAndContent(viewModel.customTitle.value, ""))
    app.mistakeRepository.saveVariation(mistake, noteFromUser)

    // 7) 同文件确有文案赋值（第 5 组的 weakTips），但文案只进渲染，绝不进 sink
    val localizedTitle = strings.hardTaskFirstTitle(tasks.size)
    Text(localizedTitle)

    // 8) **旧版必红、新版必须绿**的一条：sink 实参里的 lambda 出现 `app`。
    //    旧 TAINT_ASSIGN 每文件只产出一个位置性名字，ChildHomeScreen.kt 得到的是 ['app']，
    //    于是 `app` 一出现在 lambda 实参里就被判成"经由变量 app 传入"——这是纯误报
    //    （app 是 AppGraph 单例，不是文案）。新实现下 app 不在污点集。
    app.pointApprovalRepository.submitApproval(
        actionType = "TASK_COMPLETE",
        description = { app.formatCanonicalNote(days) }.invoke(),
        points = pts,
    )
    tasks.forEach { item ->
        app.pointRepository.checkIn(
            bizKey = "TASK:${item.id}",
            description = { app.canonicalTaskNote(item) }.invoke(),
            points = item.points,
        )
    }
}
