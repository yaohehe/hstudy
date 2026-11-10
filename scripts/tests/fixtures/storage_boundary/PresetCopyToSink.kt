// 通道 A 扩展（W0 第 5 支）：阶段 2 注册表文案形态进 sink 必须报红。
// 本文件**不参与编译**（scripts/tests/fixtures 不属于任何 Kotlin 源集），
// 覆盖 localizedPresetText / presetTextRegistry / achievementCopy / petSceneCopy 四种新文案来源；
// 见 scripts/i18n_storage_boundary.py 的 COPY_SOURCE_INNER 与自测 EXPECTED_CASES。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.achievementCopy
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.petSceneCopy
import com.hstudy.app.i18n.presetTextRegistry

@Composable
fun PresetCopyToSinkScreen() {
    val app = AppGraph
    val lang = DisplayLanguage.EN
    // A1 渲染 helper 直接进 sink
    app.taskRepository.addTask(TaskEntity(title = localizedPresetText("完成当天作业", lang), category = TaskCategory.BASIC, points = 3))
    // A2 主表直查进 sink
    app.rewardRepository.addReward(RewardEntity(name = presetTextRegistry["看一集动画片"]!!.en, pointsCost = 80, iconEmoji = "📺"))
    // A3 成就键控表进 sink
    app.habitRepository.tryUnlockAchievement("first_checkin", achievementCopy[lang]!!["first_checkin"]!!.title, achievementCopy[lang]!!["first_checkin"]!!.description, "🚀")
    // A4 场景键控表进 sink
    app.userPreferences.setDeviceName(petSceneCopy[lang]!!["home"]!!)
}
