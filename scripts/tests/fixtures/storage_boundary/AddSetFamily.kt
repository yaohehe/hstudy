// 通道 F 的 add/set 分支（Task 9b 审查 Critical 2）：`\b(?:add|set)[A-Z]\w*(` 这批写库路径
// 在真实树上是 8 条已经存在、UI 可达、写进内容最密的表（task/mistake/wish/reward 的 name+title 列）
// 的路径，旧表只逐字列举了 addPlan/addReview/addSavedPoints/setDeviceName/setPairedDevice 五条。
// 同时钉住"不能误报"的另一半：裸 `add(`（buildList 渲染容器，ChildHomeScreen.kt:302-304 的真实
// 形态）与 SINK_CAPSUFFIX_DENY 里的 `setOf(` / `addAll(` 一条都不许判红。
package com.hstudy.app.fixture

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings

@Composable
fun AddSetFamilyScreen() {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    val common = LocalStrings.current.common

    // ---- 审查点名的六条 ----
    app.taskRepository.addTask(TaskEntity(title = strings.defaultTaskTitle))
    app.mistakeRepository.addMistake(MistakeEntity(question = strings.mistakeQuestionFallback))
    app.wishGoalRepository.addGoal(WishGoalEntity(name = common.goalNameLabel))
    app.rewardRepository.addReward(RewardEntity(name = strings.rewardStoreNote))
    app.habitRepository.addChallenge(HabitChallengeEntity(title = strings.habitNameFallback))
    app.scheduleRepository.addSchedule(DailyScheduleEntity(title = strings.scheduleFallback))

    // ---- set 家族同理（DataStore 落盘 / DAO 更新），一次性覆盖而非逐字列举 ----
    app.userPreferences.setDailyTargets(strings.dailyTargetLabel)
    app.mistakeRepository.setMastered(1L, common.masteredToastShort)

    // ---- 经变量传入也要抓（K9 的 addTask 与 K1 同 sink，不同通道）----
    val taskTitle = strings.defaultTaskTitle
    app.taskRepository.addTask(TaskEntity(title = taskTitle))

    // ---- 以下四条**必须不报**：不是写库路径 ----
    val weakTips = buildList {
        add(strings.weakTipCheckin)
        add(strings.weakTipFocus)
    }
    val tagSet = setOf(strings.dailyReflectionTag)
    val more = mutableListOf<String>()
    more.addAll(strings.weakTipReview)
    Text(weakTips.joinToString() + tagSet.joinToString())
}
