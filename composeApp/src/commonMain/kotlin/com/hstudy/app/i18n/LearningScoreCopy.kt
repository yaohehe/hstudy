package com.hstudy.app.i18n

/**
 * 每日学习表现评分（`ui/common/LearningScore.kt`）的 lang-keyed 等级与鼓励语（阶段 2 W8 T2，Ruling 1（W8））。
 *
 * 评分是纯计算 + 生成文案，等级/鼓励语属于生成器输出，不进 [AppStrings] 聚合根；
 * [com.hstudy.app.ui.common.LearningScoreCalculator.calculate] 把生效语言透传到这里取词。
 * 阈值（85/60/40/1、0）与迁移前计算器逐条相同，语言只选择译文，不改变任何分值/区间（Ruling 25）。
 *
 * ZH 值逐字节等于迁移前计算器里的字面量；两语逐字由
 * `W8CopyTest.learningScoreCopyPinsBothLanguages` 钉死。
 */
object LearningScoreCopy {

    /** 等级评语（total 为 0..100 的综合得分，阈值与迁移前一致）。 */
    fun grade(total: Int, lang: DisplayLanguage): String = when {
        total >= 85 -> when (lang) {
            DisplayLanguage.ZH -> "优秀"
            DisplayLanguage.EN -> "Excellent"
        }
        total >= 60 -> when (lang) {
            DisplayLanguage.ZH -> "良好"
            DisplayLanguage.EN -> "Good"
        }
        total >= 40 -> when (lang) {
            DisplayLanguage.ZH -> "不错"
            DisplayLanguage.EN -> "Nice"
        }
        total > 0 -> when (lang) {
            DisplayLanguage.ZH -> "起步"
            DisplayLanguage.EN -> "Starting"
        }
        else -> when (lang) {
            DisplayLanguage.ZH -> "待开始"
            DisplayLanguage.EN -> "Not started"
        }
    }

    /** 鼓励语（与 [grade] 同一组阈值）。 */
    fun encouragement(total: Int, lang: DisplayLanguage): String = when {
        total >= 85 -> when (lang) {
            DisplayLanguage.ZH -> "今天学习超棒！全面发展，继续保持！"
            DisplayLanguage.EN -> "Outstanding learning today! Well-rounded — keep it up!"
        }
        total >= 60 -> when (lang) {
            DisplayLanguage.ZH -> "今天表现很好！再坚持一下就完美了！"
            DisplayLanguage.EN -> "Great work today! A little more consistency and it's perfect!"
        }
        total >= 40 -> when (lang) {
            DisplayLanguage.ZH -> "不错的开始！专注和复习还能再加把劲！"
            DisplayLanguage.EN -> "A good start! A bit more focus and review will go a long way!"
        }
        total > 0 -> when (lang) {
            DisplayLanguage.ZH -> "已经迈出一大步！继续完成更多任务吧！"
            DisplayLanguage.EN -> "A big step already! Keep completing more tasks!"
        }
        else -> when (lang) {
            DisplayLanguage.ZH -> "新的一天，从完成第一个任务开始吧！"
            DisplayLanguage.EN -> "A new day — start by finishing your first task!"
        }
    }
}
