package com.hstudy.app.ui.target

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.ui.mistake.SpacedRepetition

/**
 * 智能推荐的学习目标：关联一个每日目标ID + 个性化推荐理由
 */
data class GoalRecommendation(
    val targetId: String,
    val emoji: String,
    val title: String,
    val reason: String
)

/**
 * 学习目标智能推荐引擎：根据孩子的薄弱项、错题复习情况、专注表现，
 * 自动推荐今天最该做的事，让目标设定从"凭感觉"变为"数据驱动"。
 */
object GoalRecommendationEngine {

    private fun subjectName(s: MistakeSubject): String = when (s) {
        MistakeSubject.MATH -> "数学"
        MistakeSubject.CHINESE -> "语文"
        MistakeSubject.ENGLISH -> "英语"
        MistakeSubject.SCIENCE -> "科学"
        MistakeSubject.OTHER -> "其他"
    }

    /**
     * 生成推荐目标列表（按优先级排序，去重）
     * @param mistakes 全部错题
     * @param recentFocusMinutes 最近专注总分钟数
     */
    fun generate(mistakes: List<MistakeEntity>, recentFocusMinutes: Int): List<GoalRecommendation> {
        val recs = LinkedHashMap<String, GoalRecommendation>() // 按 targetId 去重，保留最高优先级

        // 1. 薄弱科目 → 复习错题（最高优先级）
        val subjectStats = MistakeSubject.entries.map { subj ->
            val list = mistakes.filter { it.subject == subj }
            val mastered = list.count { it.isMastered }
            val rate = if (list.isNotEmpty()) mastered.toFloat() / list.size else 1f
            Triple(subj, list.size, rate)
        }.filter { it.second >= 2 }
        val weakest = subjectStats.filter { it.third < 0.6f }.minByOrNull { it.third }
        if (weakest != null) {
            recs["review_mistakes"] = GoalRecommendation(
                "review_mistakes", "📚", "复习错题",
                "${subjectName(weakest.first)}掌握率仅${(weakest.third * 100).toInt()}%，是薄弱科目，建议重点复习"
            )
        }

        // 2. 到期复习（遗忘曲线）→ 复习错题
        val dueCount = mistakes.count { SpacedRepetition.isDueForReview(it) }
        if (dueCount > 0 && !recs.containsKey("review_mistakes")) {
            recs["review_mistakes"] = GoalRecommendation(
                "review_mistakes", "📚", "复习错题",
                "有${dueCount}道错题到了复习时间，按遗忘曲线及时复习记得更牢"
            )
        }

        // 3. 未掌握错题较多 → 攻克错题
        val unmastered = mistakes.count { !it.isMastered }
        if (unmastered > 5 && !recs.containsKey("review_mistakes")) {
            recs["review_mistakes"] = GoalRecommendation(
                "review_mistakes", "📚", "复习错题",
                "还有${unmastered}道错题未掌握，建议今天攻克2-3道"
            )
        }

        // 4. 专注不足 → 专注番茄钟
        if (recentFocusMinutes < 30) {
            recs["focus_pomodoro"] = GoalRecommendation(
                "focus_pomodoro", "🍅", "专注2个番茄钟",
                if (recentFocusMinutes == 0) "最近还没专注记录，用番茄钟开始高效学习吧"
                else "最近专注较少（${recentFocusMinutes}分钟），多专注能提升效率"
            )
        }

        // 5. 完成作业（基础目标，总是推荐）
        recs["homework"] = GoalRecommendation(
            "homework", "✏️", "完成作业",
            "按时完成作业是巩固课堂知识的基础"
        )

        // 6. 均衡发展 → 阅读（若错题掌握较好，推荐拓展）
        val overallMastered = mistakes.count { it.isMastered }
        if (mistakes.isEmpty() || overallMastered.toFloat() / mistakes.size > 0.5f) {
            recs["reading"] = GoalRecommendation(
                "reading", "📕", "阅读30分钟",
                "错题掌握不错，课外阅读能拓展知识面、提升理解力"
            )
        }

        // 7. 体育锻炼（劳逸结合，总是适度推荐）
        recs["exercise"] = GoalRecommendation(
            "exercise", "🏃", "体育锻炼",
            "劳逸结合，运动能让大脑更专注、记忆更牢固"
        )

        return recs.values.toList()
    }
}
