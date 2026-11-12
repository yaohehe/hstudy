package com.hstudy.app.i18n

/**
 * 智能推荐引擎（`ui/target/GoalRecommendation.kt`）的 lang-keyed 理由文案（阶段 2 W8 T2，Ruling 1（W8））。
 *
 * 引擎输出是**生成器产物**而不是页面固定文案，因此不进 [AppStrings] 聚合根（也不进 `presetTextRegistry`）：
 * 调用方在生成前把生效语言传进来，本对象按语言取模板。canonical 学科名与目标标题仍由引擎传给
 * [localizedPresetText] 取词，本表只收"理由"这一层句式。
 *
 * ZH 值逐字节等于迁移前引擎里的字面量（闸门证明字节）；两语逐字由
 * `W8CopyTest.goalRecommendationCopyPinsBothLanguages` 钉死。本对象只读，不参与任何存储/业务比较（Ruling 25）。
 */
object GoalRecommendationCopy {

    /** 薄弱科目：`$subject` 是渲染前已过注册表的学科名，`rate` 为 0..100 的整数掌握率。 */
    fun weakSubject(subject: String, rate: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "${subject}掌握率仅${rate}%，是薄弱科目，建议重点复习"
        DisplayLanguage.EN -> "$subject mastery is only $rate% — a weak subject; focus your review there"
    }

    fun dueReview(count: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "有${count}道错题到了复习时间，按遗忘曲线及时复习记得更牢"
        DisplayLanguage.EN -> "$count mistakes are due for review — reviewing on the forgetting curve makes them stick"
    }

    fun manyUnmastered(count: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "还有${count}道错题未掌握，建议今天攻克2-3道"
        DisplayLanguage.EN -> "$count mistakes still unmastered — aim to clear 2–3 today"
    }

    fun noFocus(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "最近还没专注记录，用番茄钟开始高效学习吧"
        DisplayLanguage.EN -> "No focus sessions recently — start with a Pomodoro for efficient study"
    }

    fun lowFocus(minutes: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "最近专注较少（${minutes}分钟），多专注能提升效率"
        DisplayLanguage.EN -> "Only $minutes minutes of focus recently — more focus boosts efficiency"
    }

    fun homework(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "按时完成作业是巩固课堂知识的基础"
        DisplayLanguage.EN -> "Finishing homework on time consolidates what you learn in class"
    }

    fun reading(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "错题掌握不错，课外阅读能拓展知识面、提升理解力"
        DisplayLanguage.EN -> "Mistake mastery looks good — outside reading broadens knowledge and comprehension"
    }

    fun exercise(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "劳逸结合，运动能让大脑更专注、记忆更牢固"
        DisplayLanguage.EN -> "Balance work and rest — exercise sharpens focus and memory"
    }
}
