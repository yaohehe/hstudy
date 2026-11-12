package com.hstudy.app.ui.pet

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.PetStrings
import com.hstudy.app.i18n.localizedPetStageName

/**
 * 学习宠物成长阶段：随累计获得积分逐步进化，给孩子长期的情感寄托与进化动力
 */
data class PetStage(val emoji: String, val name: String, val requiredPoints: Int)

val petStages = listOf(
    PetStage("🥚", "宠物蛋", 0),
    PetStage("🐣", "刚破壳", 100),
    PetStage("🐤", "小黄鸡", 300),
    PetStage("🐱", "小猫咪", 600),
    PetStage("🦊", "小狐狸", 1000),
    PetStage("🦁", "小狮子", 2000),
    PetStage("🦄", "独角兽", 4000),
    PetStage("🐉", "小神龙", 8000)
)

/** 根据累计积分获取当前宠物阶段索引 */
fun getStageIndex(lifetimePoints: Int): Int {
    var idx = 0
    petStages.forEachIndexed { i, stage ->
        if (lifetimePoints >= stage.requiredPoints) idx = i
    }
    return idx
}

/**
 * 计算考虑每日衰减后的宠物饱食度与心情。
 * 隔了一天没照顾，饱食度/心情每天衰减 15，可降到 0（宠物会饥饿/昏迷，需花积分照料才能恢复）。
 */
fun computePetState(storedFullness: Int, storedMood: Int, lastCare: String?, today: String): Pair<Int, Int> {
    if (lastCare != null && lastCare != today) {
        val daysMissed = try {
            val fmt = com.hstudy.app.ui.dt.SimpleDateFormat("yyyy-MM-dd", com.hstudy.app.ui.dt.Locale.getDefault())
            val last = fmt.parse(lastCare)?.time ?: com.hstudy.app.ui.dt.nowMillis()
            val now = fmt.parse(today)?.time ?: com.hstudy.app.ui.dt.nowMillis()
            ((now - last) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
        } catch (_: Exception) { 1 }
        val decay = daysMissed * 15
        return (storedFullness - decay).coerceAtLeast(0) to (storedMood - decay).coerceAtLeast(0)
    }
    return storedFullness to storedMood
}

/** 宠物健康状态：根据饱食度/心情判断，让忽视宠物有真实后果 */
enum class PetCondition { HEALTHY, HUNGRY, CRITICAL }

fun petCondition(fullness: Int, mood: Int): PetCondition = when {
    fullness <= 0 || mood <= 0 -> PetCondition.CRITICAL
    fullness < 30 || mood < 30 -> PetCondition.HUNGRY
    else -> PetCondition.HEALTHY
}

/**
 * 根据宠物状态与今日任务完成度生成宠物想说的话（首页预览卡）。
 * 文案随语言（W7 起由调用方传入 [strings]，签名为 5 参；唯一调用点是 [PetPreviewCard]）。
 */
fun petStatusMessage(
    fullness: Int,
    mood: Int,
    todayCompleted: Int,
    todayTotal: Int,
    strings: PetStrings,
): String = when {
    fullness <= 0 || mood <= 0 -> strings.statusCritical
    fullness < 30 -> strings.statusHungry
    mood < 30 -> strings.statusBored
    todayTotal > 0 && todayCompleted == todayTotal -> strings.statusAllDone
    todayCompleted > 0 -> strings.statusProgress(todayCompleted)
    else -> strings.statusNewDay
}

/**
 * 首页学习宠物状态预览卡片：让孩子在首页就能看到宠物的状态，
 * 增强情感连接、提醒照料宠物，提升每日活跃度。点击进入宠物页。
 */
@Composable
fun PetPreviewCard(
    onNavigateToPet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = AppGraph
    val strings = LocalStrings.current.pet
    val displayLanguage = LocalDisplayLanguage.current
    val lifetimePoints by app.pointRepository.getLifetimeEarnedPoints().collectAsState(initial = 0)
    val storedFullness by app.userPreferences.petFullness.collectAsState(initial = 80)
    val storedMood by app.userPreferences.petMood.collectAsState(initial = 80)
    val lastCare by app.userPreferences.petLastCareDate.collectAsState(initial = null)
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())

    val (fullness, mood) = remember(storedFullness, storedMood, lastCare, today) {
        computePetState(storedFullness, storedMood, lastCare, today)
    }
    val stageIndex = getStageIndex(lifetimePoints)
    val stage = petStages[stageIndex]
    val message = petStatusMessage(fullness, mood, completedIds.size, tasks.size, strings)

    Card(
        onClick = onNavigateToPet,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stage.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.previewTitle(localizedPetStageName(stageIndex, displayLanguage)), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.fullnessShort(fullness), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(strings.moodShort(mood), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text(strings.previewTip, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
