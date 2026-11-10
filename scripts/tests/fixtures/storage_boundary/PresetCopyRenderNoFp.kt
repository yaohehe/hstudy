// 反向样本（必须绿）：W0 注册表文案只流向渲染面，合规形态一条都不许判红。
// 本文件**不参与编译**；与 PresetCopyToSink.kt 互为正反证据。
package com.hstudy.app.fixture

import androidx.compose.runtime.Composable
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.achievementCopy
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.petSceneCopy

@Composable
fun PresetCopyRenderScreen() {
    val app = AppGraph
    val lang = LocalDisplayLanguage.current
    // 1) helper 只进 Text
    Text(localizedPresetText(task.title, lang))
    // 2) 键控表只进渲染（回退到 canonical 原值）
    Text(achievementCopy[lang]?.get(def.type)?.title ?: def.title)
    Text(petSceneCopy[lang]?.get(scene.id) ?: scene.name)
    // 3) 文案赋值后只进渲染（污点链的合规终点）
    val localized = localizedPresetText(task.title, lang)
    Text(localized)
    // 4) 用户输入进 sink：与注册表无关
    app.taskRepository.addTask(TaskEntity(title = draftTitle.value, category = TaskCategory.BASIC, points = 3))
    // 5) canonical 原值进 sink 仍是合规形态（Ruling 25：存规范值，渲染时本地化）
    app.habitRepository.tryUnlockAchievement(def.type, def.title, def.description, def.emoji)
}
