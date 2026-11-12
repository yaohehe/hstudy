package com.hstudy.app.ui.target

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.GoalRecommendationCopy
import com.hstudy.app.i18n.localizedPresetText
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

    /**
     * 学科 canonical 串（注册表 key）：只作为 [localizedPresetText] 的入参，不直接渲染。
     * ZH 字面量保持迁移前原样，英文由注册表 W8 学科组取词。
     */
    private fun canonicalSubject(s: MistakeSubject): String = when (s) {
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
     * @param lang 生效语言：标题/学科名走注册表，理由走 [GoalRecommendationCopy]（W8 T2）
     */
    fun generate(
        mistakes: List<MistakeEntity>,
        recentFocusMinutes: Int,
        lang: DisplayLanguage,
    ): List<GoalRecommendation> {
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
                "review_mistakes", "📚", localizedPresetText("复习错题", lang),
                GoalRecommendationCopy.weakSubject(
                    localizedPresetText(canonicalSubject(weakest.first), lang),
                    (weakest.third * 100).toInt(),
                    lang,
                )
            )
        }

        // 2. 到期复习（遗忘曲线）→ 复习错题
        val dueCount = mistakes.count { SpacedRepetition.isDueForReview(it) }
        if (dueCount > 0 && !recs.containsKey("review_mistakes")) {
            recs["review_mistakes"] = GoalRecommendation(
                "review_mistakes", "📚", localizedPresetText("复习错题", lang),
                GoalRecommendationCopy.dueReview(dueCount, lang)
            )
        }

        // 3. 未掌握错题较多 → 攻克错题
        val unmastered = mistakes.count { !it.isMastered }
        if (unmastered > 5 && !recs.containsKey("review_mistakes")) {
            recs["review_mistakes"] = GoalRecommendation(
                "review_mistakes", "📚", localizedPresetText("复习错题", lang),
                GoalRecommendationCopy.manyUnmastered(unmastered, lang)
            )
        }

        // 4. 专注不足 → 专注番茄钟
        if (recentFocusMinutes < 30) {
            recs["focus_pomodoro"] = GoalRecommendation(
                "focus_pomodoro", "🍅", localizedPresetText("专注2个番茄钟", lang),
                if (recentFocusMinutes == 0) GoalRecommendationCopy.noFocus(lang)
                else GoalRecommendationCopy.lowFocus(recentFocusMinutes, lang)
            )
        }

        // 5. 完成作业（基础目标，总是推荐）
        recs["homework"] = GoalRecommendation(
            "homework", "✏️", localizedPresetText("完成作业", lang),
            GoalRecommendationCopy.homework(lang)
        )

        // 6. 均衡发展 → 阅读（若错题掌握较好，推荐拓展）
        val overallMastered = mistakes.count { it.isMastered }
        if (mistakes.isEmpty() || overallMastered.toFloat() / mistakes.size > 0.5f) {
            recs["reading"] = GoalRecommendation(
                "reading", "📕", localizedPresetText("阅读30分钟", lang),
                GoalRecommendationCopy.reading(lang)
            )
        }

        // 7. 体育锻炼（劳逸结合，总是适度推荐）
        recs["exercise"] = GoalRecommendation(
            "exercise", "🏃", localizedPresetText("体育锻炼", lang),
            GoalRecommendationCopy.exercise(lang)
        )

        return recs.values.toList()
    }
}
