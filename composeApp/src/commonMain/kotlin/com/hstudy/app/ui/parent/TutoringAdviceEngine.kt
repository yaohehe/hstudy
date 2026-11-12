package com.hstudy.app.ui.parent

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.TutoringCopy
import com.hstudy.app.i18n.localizedPresetText

/**
 * 家长辅导建议条目
 */
data class TutoringAdvice(
    val emoji: String,
    val category: String,
    val title: String,
    val detail: String
)

/**
 * 家长辅导建议引擎：基于孩子的学习数据（学科掌握度、错题分布、专注表现），
 * 为家长生成具体、可执行的辅导方法建议，帮助家长科学地辅导孩子。
 */
object TutoringAdviceEngine {

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
     * 生成家长辅导建议列表
     * @param mistakes 全部错题
     * @param recentFocusMinutes 最近专注分钟数
     * @param streak 当前连续打卡天数
     * @param lang 生效语言：学科名/错因名走注册表，其余文案走 [TutoringCopy]（W8 T2）
     */
    fun generate(
        mistakes: List<MistakeEntity>,
        recentFocusMinutes: Int,
        streak: Int,
        lang: DisplayLanguage,
    ): List<TutoringAdvice> {
        val advice = mutableListOf<TutoringAdvice>()

        // 1. 薄弱科目辅导方法（最重要）
        val subjectStats = MistakeSubject.entries.map { subj ->
            val list = mistakes.filter { it.subject == subj }
            val mastered = list.count { it.isMastered }
            val rate = if (list.isNotEmpty()) mastered.toFloat() / list.size else 1f
            Triple(subj, list.size, rate)
        }.filter { it.second >= 2 }
        val weakest = subjectStats.filter { it.third < 0.6f }.minByOrNull { it.third }
        if (weakest != null) {
            advice.add(
                TutoringAdvice(
                    "📉", TutoringCopy.weakSubjectCategory(lang),
                    TutoringCopy.weakSubjectTitle(
                        localizedPresetText(canonicalSubject(weakest.first), lang),
                        (weakest.third * 100).toInt(),
                        lang,
                    ),
                    TutoringCopy.subjectMethod(weakest.first, lang)
                )
            )
        }

        // 1.5 错因针对辅导（针对最主要的错误类型给出具体辅导方法）
        val typedMistakes = mistakes.filter { it.errorType.isNotEmpty() }
        if (typedMistakes.size >= 2) {
            val topError = typedMistakes.groupBy { it.errorType }
                .mapValues { it.value.size }
                .maxByOrNull { it.value }
            if (topError != null) {
                advice.add(
                    TutoringAdvice(
                        "🔍", TutoringCopy.errorCategory(lang),
                        TutoringCopy.errorTitle(
                            localizedPresetText(topError.key, lang),
                            topError.value,
                            lang,
                        ),
                        TutoringCopy.errorAdvice(topError.key, lang)
                    )
                )
            }
        }

        // 2. 错题复习督促
        val unmastered = mistakes.count { !it.isMastered }
        if (unmastered > 0) {
            advice.add(
                TutoringAdvice(
                    "📚", TutoringCopy.reviewCategory(lang),
                    TutoringCopy.unmasteredTitle(unmastered, lang),
                    TutoringCopy.unmasteredDetail(lang)
                )
            )
        }

        // 3. 专注力培养
        if (recentFocusMinutes < 30) {
            advice.add(
                TutoringAdvice(
                    "🍅", TutoringCopy.focusTrainingCategory(lang),
                    TutoringCopy.lowFocusTitle(lang),
                    TutoringCopy.focusLowDetail(lang)
                )
            )
        } else {
            advice.add(
                TutoringAdvice(
                    "🍅", TutoringCopy.focusMaintenanceCategory(lang),
                    TutoringCopy.goodFocusTitle(recentFocusMinutes, lang),
                    TutoringCopy.focusGoodDetail(lang)
                )
            )
        }

        // 4. 习惯养成激励
        if (streak >= 3) {
            advice.add(
                TutoringAdvice(
                    "🔥", TutoringCopy.habitCategory(lang),
                    TutoringCopy.streakTitle(streak, lang),
                    TutoringCopy.streakDetail(lang)
                )
            )
        } else {
            advice.add(
                TutoringAdvice(
                    "🌱", TutoringCopy.habitCategory(lang),
                    TutoringCopy.habitBuildTitle(lang),
                    TutoringCopy.habitBuildDetail(lang)
                )
            )
        }

        // 5. 通用辅导原则（总是提供）
        advice.add(
            TutoringAdvice(
                "❤️", TutoringCopy.mindsetCategory(lang),
                TutoringCopy.partnerTitle(lang),
                TutoringCopy.partnerDetail(lang)
            )
        )

        return advice
    }
}
