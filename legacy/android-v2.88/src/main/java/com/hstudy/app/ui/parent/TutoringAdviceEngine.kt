package com.hstudy.app.ui.parent

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject

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

    private fun subjectName(s: MistakeSubject): String = when (s) {
        MistakeSubject.MATH -> "数学"
        MistakeSubject.CHINESE -> "语文"
        MistakeSubject.ENGLISH -> "英语"
        MistakeSubject.SCIENCE -> "科学"
        MistakeSubject.OTHER -> "其他"
    }

    /** 针对薄弱科目的具体辅导方法 */
    private fun subjectTutoringMethod(s: MistakeSubject): String = when (s) {
        MistakeSubject.MATH -> "数学薄弱建议：每天陪孩子做2-3道基础题巩固概念，重视错题重做而非刷难题；让孩子把解题思路讲给你听，'讲题'能暴露理解漏洞。"
        MistakeSubject.CHINESE -> "语文薄弱建议：加强每日阅读积累，陪孩子朗读课文培养语感；字词基础每天听写巩固，作文多鼓励、少批评，先肯定再提建议。"
        MistakeSubject.ENGLISH -> "英语薄弱建议：每天15分钟听力磨耳朵，陪孩子大声朗读培养语感；单词用'少量多次'记忆法，结合图片/场景记忆更牢固。"
        MistakeSubject.SCIENCE -> "科学薄弱建议：多联系生活实际讲解原理，有条件一起做小实验；让孩子画出知识导图，理解比死记硬背更重要。"
        MistakeSubject.OTHER -> "建议和孩子一起分析薄弱环节的具体原因，是基础不牢还是粗心，针对性地制定改进计划。"
    }

    /**
     * 生成家长辅导建议列表
     * @param mistakes 全部错题
     * @param recentFocusMinutes 最近专注分钟数
     * @param streak 当前连续打卡天数
     */
    fun generate(mistakes: List<MistakeEntity>, recentFocusMinutes: Int, streak: Int): List<TutoringAdvice> {
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
                    "📉", "薄弱科目辅导",
                    "${subjectName(weakest.first)}是薄弱科目（掌握率${(weakest.third * 100).toInt()}%）",
                    subjectTutoringMethod(weakest.first)
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
                val errorAdvice = when (topError.key) {
                    "粗心马虎" -> "孩子最常因‘粗心马虎’出错。建议：培养做完检查的习惯，平时练习限时完成模拟考试节奏；粗心往往是熟练度不够，基础题要多练到自动化。"
                    "概念不清" -> "孩子最常因‘概念不清’出错。建议：回归课本把基础概念真正弄懂，让孩子用自己的话讲出概念含义；概念不清就刷题是无效的，先理解再练习。"
                    "方法不会" -> "孩子最常因‘方法不会’出错。建议：帮孩子总结同类题的解题套路，多看例题学习标准方法；建立‘题型-方法’对应，遇到新题先想属于哪类。"
                    "审题错误" -> "孩子最常因‘审题错误’出错。建议：培养孩子读题时圈出关键条件和问题的习惯，看清要求再动笔；审题能力需要刻意练习。"
                    "计算错误" -> "孩子最常因‘计算错误’出错。建议：草稿纸写工整、关键步骤验算；每天少量计算练习提升准确率，计算熟练度需要积累。"
                    else -> "建议和孩子一起分析错题的具体原因，针对性地制定改进计划。"
                }
                advice.add(
                    TutoringAdvice(
                        "🔍", "错因针对辅导",
                        "孩子最常因「${topError.key}」出错（${topError.value}次）",
                        errorAdvice
                    )
                )
            }
        }

        // 2. 错题复习督促
        val unmastered = mistakes.count { !it.isMastered }
        if (unmastered > 0) {
            advice.add(
                TutoringAdvice(
                    "📚", "错题复习督促",
                    "还有${unmastered}道错题未掌握",
                    "建议每天固定15分钟陪孩子复习错题，用'遮住答案先回想'的方式效果最好。错题是提分最快的突破口，坚持清零进步明显。"
                )
            )
        }

        // 3. 专注力培养
        if (recentFocusMinutes < 30) {
            advice.add(
                TutoringAdvice(
                    "🍅", "专注力培养",
                    "孩子最近专注时长偏少",
                    "建议用番茄钟（25分钟专注+5分钟休息）帮助孩子进入专注状态；学习时段收走手机等干扰物，营造安静环境；专注后给予肯定，逐步延长专注时间。"
                )
            )
        } else {
            advice.add(
                TutoringAdvice(
                    "🍅", "专注力保持",
                    "孩子专注表现不错（最近${recentFocusMinutes}分钟）",
                    "继续保持！可以在孩子黄金专注时段安排较难的学习任务，效率更高。专注时尽量不打扰孩子。"
                )
            )
        }

        // 4. 习惯养成激励
        if (streak >= 3) {
            advice.add(
                TutoringAdvice(
                    "🔥", "习惯养成激励",
                    "孩子已连续打卡${streak}天",
                    "好习惯正在养成中！及时肯定孩子的坚持（口头表扬比物质奖励更有效），帮助孩子把'坚持'内化为习惯，避免中断。"
                )
            )
        } else {
            advice.add(
                TutoringAdvice(
                    "🌱", "习惯养成激励",
                    "帮助孩子建立学习规律",
                    "建议和孩子一起固定每天的学习时间和流程，从'小目标'开始（如每天打卡2-3个任务），达成后及时鼓励，慢慢建立学习节奏感。"
                )
            )
        }

        // 5. 通用辅导原则（总是提供）
        advice.add(
            TutoringAdvice(
                "❤️", "辅导心态建议",
                "做孩子的'学习合伙人'而非'监工'",
                "多关注努力过程而非只看分数；孩子遇到困难时先共情再引导；每天留一点时间听孩子讲讲今天学了什么，'输出'是最好的'输入'。"
            )
        )

        return advice
    }
}
