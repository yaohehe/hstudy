package com.hstudy.app.i18n

import com.hstudy.app.data.local.entity.MistakeSubject

/**
 * 家长辅导建议引擎（`ui/parent/TutoringAdviceEngine.kt`）的 lang-keyed 文案（阶段 2 W8 T2，Ruling 1（W8））。
 *
 * 与 [GoalRecommendationCopy] 同族：引擎输出是生成器产物，不进 [AppStrings] 聚合根；
 * canonical 学科名/错因名由引擎先包 [localizedPresetText] 再传给标题模板，本表只收句式与类别名。
 * 错因分支按 canonical 错因串（`MistakeEntity.errorType` 的中文枚举值）选择，与语言无关——
 * 语言只决定"取哪份译文"，不参与任何写库/幂等比较（Ruling 25）。
 *
 * ZH 值逐字节等于迁移前引擎里的字面量（闸门证明字节）；两语代表串由
 * `W8CopyTest.tutoringCopyPinsBothLanguages` 钉死。
 */
object TutoringCopy {

    // ===== 五个学科的具体辅导方法 =====

    fun subjectMethod(subject: MistakeSubject, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> when (subject) {
            MistakeSubject.MATH -> "数学薄弱建议：每天陪孩子做2-3道基础题巩固概念，重视错题重做而非刷难题；让孩子把解题思路讲给你听，'讲题'能暴露理解漏洞。"
            MistakeSubject.CHINESE -> "语文薄弱建议：加强每日阅读积累，陪孩子朗读课文培养语感；字词基础每天听写巩固，作文多鼓励、少批评，先肯定再提建议。"
            MistakeSubject.ENGLISH -> "英语薄弱建议：每天15分钟听力磨耳朵，陪孩子大声朗读培养语感；单词用'少量多次'记忆法，结合图片/场景记忆更牢固。"
            MistakeSubject.SCIENCE -> "科学薄弱建议：多联系生活实际讲解原理，有条件一起做小实验；让孩子画出知识导图，理解比死记硬背更重要。"
            MistakeSubject.OTHER -> "建议和孩子一起分析薄弱环节的具体原因，是基础不牢还是粗心，针对性地制定改进计划。"
        }
        DisplayLanguage.EN -> when (subject) {
            MistakeSubject.MATH -> "Math needs work: do 2–3 basic problems a day consolidating concepts with your child, and prioritize redoing mistakes over grinding hard problems; have your child explain the solution steps to you — “teaching it back” exposes understanding gaps."
            MistakeSubject.CHINESE -> "Chinese needs work: build daily reading and read the textbook aloud together to develop language sense; practice daily dictation of characters and words; encourage more and criticize less in writing — affirm first, then suggest."
            MistakeSubject.ENGLISH -> "English needs work: 15 minutes of listening daily to train the ear and read aloud together to build language sense; memorize words in small, frequent batches, reinforced with pictures or scenes."
            MistakeSubject.SCIENCE -> "Science needs work: explain principles through everyday life and do small experiments together when possible; have your child draw knowledge maps — understanding beats rote memorization."
            MistakeSubject.OTHER -> "Sit down with your child and analyze the specific cause of the weak area — shaky foundations or carelessness — then make a targeted improvement plan."
        }
    }

    // ===== 五个错因分支 + 兜底（入参为 canonical 错因串）=====

    fun errorAdvice(errorTypeCanonical: String, lang: DisplayLanguage): String = when (errorTypeCanonical) {
        "粗心马虎" -> when (lang) {
            DisplayLanguage.ZH -> "孩子最常因‘粗心马虎’出错。建议：培养做完检查的习惯，平时练习限时完成模拟考试节奏；粗心往往是熟练度不够，基础题要多练到自动化。"
            DisplayLanguage.EN -> "Your child most often errs from “carelessness”. Suggestion: build a checking habit after finishing and do timed practice to simulate exam pace; carelessness often means insufficient fluency — drill basic problems to automaticity."
        }
        "概念不清" -> when (lang) {
            DisplayLanguage.ZH -> "孩子最常因‘概念不清’出错。建议：回归课本把基础概念真正弄懂，让孩子用自己的话讲出概念含义；概念不清就刷题是无效的，先理解再练习。"
            DisplayLanguage.EN -> "Your child most often errs from “unclear concepts”. Suggestion: return to the textbook and truly understand the basics; have your child explain them in their own words; drilling without understanding is ineffective — understand first, then practice."
        }
        "方法不会" -> when (lang) {
            DisplayLanguage.ZH -> "孩子最常因‘方法不会’出错。建议：帮孩子总结同类题的解题套路，多看例题学习标准方法；建立‘题型-方法’对应，遇到新题先想属于哪类。"
            DisplayLanguage.EN -> "Your child most often errs from “not knowing the method”. Suggestion: help summarize solution patterns and study worked examples; build a “problem type → method” mapping and classify new problems first."
        }
        "审题错误" -> when (lang) {
            DisplayLanguage.ZH -> "孩子最常因‘审题错误’出错。建议：培养孩子读题时圈出关键条件和问题的习惯，看清要求再动笔；审题能力需要刻意练习。"
            DisplayLanguage.EN -> "Your child most often errs from “misreading the question”. Suggestion: build the habit of circling key conditions and questions before writing; reading comprehension needs deliberate practice."
        }
        "计算错误" -> when (lang) {
            DisplayLanguage.ZH -> "孩子最常因‘计算错误’出错。建议：草稿纸写工整、关键步骤验算；每天少量计算练习提升准确率，计算熟练度需要积累。"
            DisplayLanguage.EN -> "Your child most often errs from “calculation errors”. Suggestion: keep scratch work neat and verify key steps; short daily calculation practice improves accuracy — fluency takes accumulation."
        }
        else -> when (lang) {
            DisplayLanguage.ZH -> "建议和孩子一起分析错题的具体原因，针对性地制定改进计划。"
            DisplayLanguage.EN -> "Analyze the specific causes of mistakes together with your child and make a targeted improvement plan."
        }
    }

    // ===== 七个建议类别名 =====

    fun weakSubjectCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "薄弱科目辅导"
        DisplayLanguage.EN -> "Weak subject coaching"
    }

    fun errorCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "错因针对辅导"
        DisplayLanguage.EN -> "Error-type coaching"
    }

    fun reviewCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "错题复习督促"
        DisplayLanguage.EN -> "Mistake review reminder"
    }

    fun focusTrainingCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "专注力培养"
        DisplayLanguage.EN -> "Focus training"
    }

    fun focusMaintenanceCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "专注力保持"
        DisplayLanguage.EN -> "Focus maintenance"
    }

    fun habitCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "习惯养成激励"
        DisplayLanguage.EN -> "Habit encouragement"
    }

    fun mindsetCategory(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "辅导心态建议"
        DisplayLanguage.EN -> "Coaching mindset"
    }

    // ===== 标题模板（学科名/错因名已由调用方包注册表）=====

    fun weakSubjectTitle(subject: String, rate: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "${subject}是薄弱科目（掌握率${rate}%）"
        DisplayLanguage.EN -> "$subject is a weak subject (mastery $rate%)"
    }

    fun errorTitle(errorTypeLocalized: String, count: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "孩子最常因「${errorTypeLocalized}」出错（${count}次）"
        DisplayLanguage.EN -> "Your child most often errs from “$errorTypeLocalized” ($count times)"
    }

    fun unmasteredTitle(count: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "还有${count}道错题未掌握"
        DisplayLanguage.EN -> "$count mistakes still unmastered"
    }

    fun lowFocusTitle(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "孩子最近专注时长偏少"
        DisplayLanguage.EN -> "Your child's recent focus time is low"
    }

    fun goodFocusTitle(minutes: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "孩子专注表现不错（最近${minutes}分钟）"
        DisplayLanguage.EN -> "Your child is focusing well ($minutes min recently)"
    }

    fun streakTitle(days: Int, lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "孩子已连续打卡${days}天"
        DisplayLanguage.EN -> "Your child has checked in $days days in a row"
    }

    fun habitBuildTitle(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "帮助孩子建立学习规律"
        DisplayLanguage.EN -> "Help your child build a study routine"
    }

    fun partnerTitle(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "做孩子的'学习合伙人'而非'监工'"
        DisplayLanguage.EN -> "Be your child's study partner, not supervisor"
    }

    // ===== 详情模板 =====

    fun unmasteredDetail(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "建议每天固定15分钟陪孩子复习错题，用'遮住答案先回想'的方式效果最好。错题是提分最快的突破口，坚持清零进步明显。"
        DisplayLanguage.EN -> "Set aside 15 minutes daily to review mistakes together — covering the answer and recalling first works best. Mistakes are the fastest lever for improvement; keep clearing them and progress will show."
    }

    fun focusLowDetail(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "建议用番茄钟（25分钟专注+5分钟休息）帮助孩子进入专注状态；学习时段收走手机等干扰物，营造安静环境；专注后给予肯定，逐步延长专注时间。"
        DisplayLanguage.EN -> "Use the Pomodoro (25 min focus + 5 min break) to help your child get into focus; remove phones and distractions during study and keep the environment quiet; praise after focus sessions and gradually extend the time."
    }

    fun focusGoodDetail(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "继续保持！可以在孩子黄金专注时段安排较难的学习任务，效率更高。专注时尽量不打扰孩子。"
        DisplayLanguage.EN -> "Keep it up! Schedule harder tasks during your child's golden focus hours for higher efficiency. Try not to interrupt during focus time."
    }

    fun streakDetail(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "好习惯正在养成中！及时肯定孩子的坚持（口头表扬比物质奖励更有效），帮助孩子把'坚持'内化为习惯，避免中断。"
        DisplayLanguage.EN -> "A good habit is forming! Acknowledge persistence promptly (verbal praise works better than material rewards) and help internalize it as a habit — avoid breaking the streak."
    }

    fun habitBuildDetail(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "建议和孩子一起固定每天的学习时间和流程，从'小目标'开始（如每天打卡2-3个任务），达成后及时鼓励，慢慢建立学习节奏感。"
        DisplayLanguage.EN -> "Set a fixed daily study time and routine together, and start with small goals (e.g. 2–3 check-ins a day), encourage promptly after success, and gradually build a sense of rhythm."
    }

    fun partnerDetail(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "多关注努力过程而非只看分数；孩子遇到困难时先共情再引导；每天留一点时间听孩子讲讲今天学了什么，'输出'是最好的'输入'。"
        DisplayLanguage.EN -> "Focus on the effort, not just scores; empathize before guiding when your child struggles; save a little time each day to hear what they learned — “output” is the best “input”."
    }
}
