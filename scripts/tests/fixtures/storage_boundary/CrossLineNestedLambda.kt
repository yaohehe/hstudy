// 通道 B（跨行调用 + 嵌套 lambda）：Ruling 28 的行级盲区样本。
// 真实形态是 ChildHomeScreen.kt:583-589 的 checkIn(bizKey = …, description = …)（跨 7 行）
// 与 ChildDailyPlanScreen.kt:100 的 addPlan(DailyPlanEntity(…))（跨 8 行）。
// 按行匹配的旧实现永远报不出来；现在用括号配对取实参。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun CrossLineNestedLambdaScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    app.pointRepository.checkIn(
        bizKey = buildString {
            append("MILESTONE_")
            append(days)
        },
        description = strings.milestoneRewardDescription(
            days,
        ),
        points = if (days >= 7) 10 else 5,
    )
    app.focusRepository.addPlan(
        DailyPlanEntity(
            taskTitle = { strings.planTitleFallback }.invoke(),
            plannedAt = nowMillis,
        )
    )
}
