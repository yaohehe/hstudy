package com.hstudy.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.AchievementEntity
import kotlinx.coroutines.flow.first

/**
 * 成就管理器 - 检查并解锁成就
 */
object AchievementManager {

    data class AchievementDef(
        val type: String,
        val title: String,
        val description: String,
        val emoji: String
    )

    val allAchievements = listOf(
        AchievementDef("first_checkin", "启航", "首次完成打卡", "🚀"),
        AchievementDef("streak_3", "三日不辍", "连续打卡3天", "🔥"),
        AchievementDef("streak_7", "周冠军", "连续打卡7天", "👑"),
        AchievementDef("checkin_100", "百日积累", "累计打卡100次", "💯"),
        AchievementDef("perfect_day", "百分学霸", "单日完成全部任务", "⭐"),
        AchievementDef("mistake_50", "错题克星", "掌握50道错题", "📖"),
        AchievementDef("focus_600", "专注达人", "累计专注10小时", "🎯"),
        AchievementDef("habit_complete", "习惯达人", "完成一个21天挑战", "🏆"),
        // 新增：庆祝科学学习行为的成就
        AchievementDef("mistake_100", "错题大师", "掌握100道错题", "🎓"),
        AchievementDef("focus_1800", "专注大师", "累计专注30小时", "⏳"),
        AchievementDef("review_30", "复习达人", "累计复习错题30次", "📚"),
        AchievementDef("variation_5", "举一反三", "完成5次举一反三思考", "💡"),
        AchievementDef("training_10", "训练达人", "完成10次专注力训练", "🧠"),
    )

    /**
     * 检查并尝试解锁成就
     */
    suspend fun checkAndUnlock(
        habitRepo: com.hstudy.app.data.repository.HabitRepository,
        streak: Int = 0,
        totalCheckins: Int = 0,
        masteredMistakes: Int = 0,
        totalFocusMinutes: Int = 0,
        isPerfectDay: Boolean = false,
        habitCompleted: Boolean = false,
        totalReviews: Int = 0,
        variationCount: Int = 0,
        trainingCount: Int = 0
    ): List<AchievementDef> {
        val unlocked = mutableListOf<AchievementDef>()

        suspend fun tryUnlock(def: AchievementDef) {
            val success = habitRepo.tryUnlockAchievement(def.type, def.title, def.description, def.emoji)
            if (success) unlocked.add(def)
        }

        if (totalCheckins > 0) tryUnlock(allAchievements[0]) // 首次打卡
        if (streak >= 3) tryUnlock(allAchievements[1])
        if (streak >= 7) tryUnlock(allAchievements[2])
        if (totalCheckins >= 100) tryUnlock(allAchievements[3])
        if (isPerfectDay) tryUnlock(allAchievements[4])
        if (masteredMistakes >= 50) tryUnlock(allAchievements[5])
        if (totalFocusMinutes >= 600) tryUnlock(allAchievements[6])
        if (habitCompleted) tryUnlock(allAchievements[7])
        // 新增科学学习行为成就
        if (masteredMistakes >= 100) tryUnlock(allAchievements[8])   // 错题大师
        if (totalFocusMinutes >= 1800) tryUnlock(allAchievements[9]) // 专注大师
        if (totalReviews >= 30) tryUnlock(allAchievements[10])       // 复习达人
        if (variationCount >= 5) tryUnlock(allAchievements[11])      // 举一反三
        if (trainingCount >= 10) tryUnlock(allAchievements[12])      // 训练达人

        return unlocked
    }

    /**
     * 集中评估成就：汇总各项统计后调用 checkAndUnlock，返回本次新解锁的成就。
     * 应在打卡、复习错题、专注结束、习惯打卡等关键动作后调用。
     */
    suspend fun evaluate(app: HStudyApp, isPerfectDay: Boolean = false): List<AchievementDef> {
        val streak = app.pointRepository.getCurrentStreak()
        val totalCheckins = app.pointRepository.getTotalCompletionCount()
        val masteredMistakes = app.mistakeRepository.getMasteredCount().first()
        val totalFocusMinutes = app.focusRepository.getTotalFocusMinutes().first()
        val habitCompleted = app.habitRepository.getCompletedChallengeCount().first() > 0
        val totalReviews = app.mistakeRepository.getTotalReviewCount().first()
        val variationCount = app.mistakeRepository.getVariationCount().first()
        val trainingCount = app.userPreferences.focusTrainingCount.first()
        return checkAndUnlock(
            habitRepo = app.habitRepository,
            streak = streak,
            totalCheckins = totalCheckins,
            masteredMistakes = masteredMistakes,
            totalFocusMinutes = totalFocusMinutes,
            isPerfectDay = isPerfectDay,
            habitCompleted = habitCompleted,
            totalReviews = totalReviews,
            variationCount = variationCount,
            trainingCount = trainingCount
        )
    }

    /**
     * 计算每个成就的当前进度（当前值 to 目标值），用于展示距离解锁还有多远。
     */
    suspend fun getProgress(app: HStudyApp): Map<String, Pair<Int, Int>> {
        val streak = app.pointRepository.getCurrentStreak()
        val totalCheckins = app.pointRepository.getTotalCompletionCount()
        val masteredMistakes = app.mistakeRepository.getMasteredCount().first()
        val totalFocusMinutes = app.focusRepository.getTotalFocusMinutes().first()
        val totalReviews = app.mistakeRepository.getTotalReviewCount().first()
        val variationCount = app.mistakeRepository.getVariationCount().first()
        val trainingCount = app.userPreferences.focusTrainingCount.first()
        return mapOf(
            "first_checkin" to (totalCheckins.coerceAtMost(1) to 1),
            "streak_3" to (streak.coerceAtMost(3) to 3),
            "streak_7" to (streak.coerceAtMost(7) to 7),
            "checkin_100" to (totalCheckins.coerceAtMost(100) to 100),
            "perfect_day" to (0 to 1),
            "mistake_50" to (masteredMistakes.coerceAtMost(50) to 50),
            "focus_600" to (totalFocusMinutes.coerceAtMost(600) to 600),
            "habit_complete" to (0 to 1),
            "mistake_100" to (masteredMistakes.coerceAtMost(100) to 100),
            "focus_1800" to (totalFocusMinutes.coerceAtMost(1800) to 1800),
            "review_30" to (totalReviews.coerceAtMost(30) to 30),
            "variation_5" to (variationCount.coerceAtMost(5) to 5),
            "training_10" to (trainingCount.coerceAtMost(10) to 10),
        )
    }
}

@Composable
fun AchievementBadges(
    modifier: Modifier = Modifier
) {
    val app = HStudyApp.instance
    val achievements by app.habitRepository.getAllAchievements().collectAsState(initial = emptyList())
    val allDefs = AchievementManager.allAchievements

    // 收集每个成就的当前进度（距离解锁还有多远）
    var progressMap by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        progressMap = AchievementManager.getProgress(app)
    }

    Column(modifier = modifier) {
        Text("🏅 成就徽章", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (achievements.isEmpty()) {
            Text("还没有解锁任何成就，加油吧！", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(achievements, key = { it.id }) { achievement ->
                    AchievementChip(achievement)
                }
            }
        }

        // 未解锁的显示进度（让孩子看见距离目标还有多远）
        val unlockedTypes = achievements.map { it.type }.toSet()
        val locked = allDefs.filter { it.type !in unlockedTypes }
        if (locked.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("🔒 待解锁（加油达成）", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                locked.forEach { def ->
                    val (cur, target) = progressMap[def.type] ?: (0 to 1)
                    AssistChip(
                        onClick = {},
                        label = { Text("${def.emoji} ${def.title} $cur/$target") },
                        enabled = false
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementChip(achievement: AchievementEntity) {
    AssistChip(
        onClick = {},
        label = { Text("${achievement.emoji} ${achievement.title}") },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    )
}
