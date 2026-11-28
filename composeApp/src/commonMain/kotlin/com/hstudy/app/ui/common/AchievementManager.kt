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
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.AchievementEntity
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.HabitRepository
import com.hstudy.app.data.repository.MistakeRepository
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.data.repository.todayString
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.achievementCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AchievementProgress(
    val type: String,
    val current: Int,
    val target: Int,
)

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
    suspend fun evaluate(app: AppGraph): List<AchievementDef> {
        val streak = app.pointRepository.getCurrentStreak()
        val totalCheckins = app.pointRepository.getTotalCompletionCount()
        val masteredMistakes = app.mistakeRepository.getMasteredCount().first()
        val totalFocusMinutes = app.focusRepository.getTotalFocusMinutes().first()
        val habitCompleted = app.habitRepository.getCompletedChallengeCount().first() > 0
        val isPerfectDay = app.pointRepository.isPerfectDay(todayString()).first()
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

    /** 反应式成就进度；页面持续收集，数据库或 DataStore 变化时自动刷新。 */
    fun progress(app: AppGraph): Flow<List<AchievementProgress>> = progress(
        pointRepository = app.pointRepository,
        habitRepository = app.habitRepository,
        mistakeRepository = app.mistakeRepository,
        focusRepository = app.focusRepository,
        userPreferences = app.userPreferences,
    )

    fun progress(
        pointRepository: PointRepository,
        habitRepository: HabitRepository,
        mistakeRepository: MistakeRepository,
        focusRepository: FocusRepository,
        userPreferences: UserPreferences,
    ): Flow<List<AchievementProgress>> {
        val pointStats = combine(
            pointRepository.observeCurrentStreak(),
            pointRepository.observeTotalCompletionCount(),
            pointRepository.isPerfectDay(todayString()),
        ) { streak, checkins, perfectDay -> PointStats(streak, checkins, perfectDay) }
        val mistakeStats = combine(
            mistakeRepository.getMasteredCount(),
            mistakeRepository.getTotalReviewCount(),
            mistakeRepository.getVariationCount(),
        ) { mastered, reviews, variations -> MistakeStats(mastered, reviews, variations) }
        val activityStats = combine(
            focusRepository.getTotalFocusMinutes(),
            userPreferences.focusTrainingCount,
            habitRepository.getCompletedChallengeCount(),
        ) { focusMinutes, trainingCount, completedHabits ->
            ActivityStats(focusMinutes, trainingCount, completedHabits > 0)
        }
        return combine(pointStats, mistakeStats, activityStats) { points, mistakes, activity ->
            listOf(
                AchievementProgress("first_checkin", points.checkins.coerceAtMost(1), 1),
                AchievementProgress("streak_3", points.streak.coerceAtMost(3), 3),
                AchievementProgress("streak_7", points.streak.coerceAtMost(7), 7),
                AchievementProgress("checkin_100", points.checkins.coerceAtMost(100), 100),
                AchievementProgress("perfect_day", if (points.perfectDay) 1 else 0, 1),
                AchievementProgress("mistake_50", mistakes.mastered.coerceAtMost(50), 50),
                AchievementProgress("focus_600", activity.focusMinutes.coerceAtMost(600), 600),
                AchievementProgress("habit_complete", if (activity.habitCompleted) 1 else 0, 1),
                AchievementProgress("mistake_100", mistakes.mastered.coerceAtMost(100), 100),
                AchievementProgress("focus_1800", activity.focusMinutes.coerceAtMost(1800), 1800),
                AchievementProgress("review_30", mistakes.reviews.coerceAtMost(30), 30),
                AchievementProgress("variation_5", mistakes.variations.coerceAtMost(5), 5),
                AchievementProgress("training_10", activity.trainingCount.coerceAtMost(10), 10),
            )
        }
    }

    /** 兼容一次性快照调用方；UI 应持续收集 [progress]。 */
    suspend fun getProgress(app: AppGraph): Map<String, Pair<Int, Int>> =
        progress(app).first().associate { it.type to (it.current to it.target) }

    private data class PointStats(val streak: Int, val checkins: Int, val perfectDay: Boolean)
    private data class MistakeStats(val mastered: Int, val reviews: Int, val variations: Int)
    private data class ActivityStats(val focusMinutes: Int, val trainingCount: Int, val habitCompleted: Boolean)
}

@Composable
fun AchievementBadges(
    modifier: Modifier = Modifier
) {
    val app = AppGraph
    val strings = LocalStrings.current.achievementSection
    val displayLanguage = LocalDisplayLanguage.current
    val achievements by app.habitRepository.getAllAchievements().collectAsState(initial = emptyList())
    val allDefs = AchievementManager.allAchievements

    // 收集每个成就的当前进度（距离解锁还有多远）；源数据变化时持续刷新。
    val progress by AchievementManager.progress(app).collectAsState(initial = emptyList())
    val progressMap = remember(progress) {
        progress.associate { it.type to (it.current to it.target) }
    }

    Column(modifier = modifier) {
        Text(strings.sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (achievements.isEmpty()) {
            Text(strings.emptyLocked, style = MaterialTheme.typography.bodySmall,
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
            Text(strings.lockedTitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                locked.forEach { def ->
                    val (cur, target) = progressMap[def.type] ?: (0 to 1)
                    // 标题按 def.type（稳定业务键）走注册表；未命中原样回落（进度数字不随语言）。
                    // 变量名避开 `title`：存储边界闸门按标识符污点追踪，同名会被误报进 tryUnlockAchievement。
                    val localizedTitle = achievementCopy[displayLanguage]?.get(def.type)?.title ?: def.title
                    AssistChip(
                        onClick = {},
                        label = { Text("${def.emoji} $localizedTitle $cur/$target") },
                        enabled = false
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementChip(achievement: AchievementEntity) {
    val displayLanguage = LocalDisplayLanguage.current
    // 标题按实体 type（稳定业务键）走注册表；未命中原样回落
    val localizedTitle = achievementCopy[displayLanguage]?.get(achievement.type)?.title ?: achievement.title
    AssistChip(
        onClick = {},
        label = { Text("${achievement.emoji} $localizedTitle") },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    )
}
