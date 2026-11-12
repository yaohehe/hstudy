package com.hstudy.app.i18n

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.ui.common.LearningScoreCalculator
import com.hstudy.app.ui.common.LearningTree
import com.hstudy.app.ui.common.LevelSystem
import com.hstudy.app.ui.target.GoalRecommendationEngine
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 阶段 2 W8 文案守卫（沿用 StringsCopyTest/PetCopyTest/WishCopyTest 约定）：
 * 静态字段 ZH≠EN；函数型字段逐字双语钉死（Ruling 20-b），英文 one/other 词形一并钉死；
 * 键控注册表按索引覆盖 + 越界回落 + 英文无汉字。
 *
 * 本波 T1 的覆盖面：
 * - [levelTitleCopy] / [treeStageCopy]（等级称号、学习树阶段名）是**独立键控表**，
 *   并行 W7 的 `petStageCopy`：不并入 `presetTextRegistry`、不扩 `PresetRegistryTest` 的
 *   canonical 子集守卫（Ruling 2（W8））。所以覆盖性由本文件钉住；
 * - [CommonStrings] 新增的两个函数型字段（`levelRemaining` / `streakDays`）只在本文件逐字断言
 *   ——data class 的 `toString()` 打印 lambda 对象本身、不含返回值，
 *   `StringsCopyTest.englishTableContainsNoChineseLeftovers` 这类整表 CJK 扫描看不见插值里的中文
 *   （该盲区的说明在 CommonStrings 的 KDoc 与 StringsCopyTest.commonInterpolatedCopyPinsBothLanguages）。
 *
 * T2 追加三个引擎文案守卫：`GoalRecommendationCopy` / `TutoringCopy` / `LearningScoreCopy`
 * 是 lang-keyed 常量（不是 AppStrings 表，Ruling 1（W8）），在聚合根之外，整表扫描永远看不见；
 * 本文件逐字钉住代表性文案，并用 `generate`/`calculate` 的调用验证"生效语言 → 输出语言"链路。
 *
 * 为什么注册表 ZH 必须与 UI 目录逐字相等：两张表都是"按索引取词"的显示目录，索引错位不会编译失败，
 * 只会让第 7 级显示成第 8 级的称号。`LevelSystem.levels` 是 private，无法像 `petStages` 那样整表
 * import 对账，因此 ZH 全量列表**手打钉死**（漂移守卫的上限与 PetCopyTest 同类），
 * 首尾两级另用公开入口 `getLevelInfo` 做活体对账；`LearningTree.getStageName` 是公开函数，
 * 六阶段全部活体对账。
 */
class W8CopyTest {

    /**
     * 等级称号与学习树阶段名的键控表守卫（10 / 6 条）。
     *
     * 手打的 ZH 列表就是 LevelSystem.levels 第 1..10 级的标题（等级 1 起、helper 按 `level - 1` 取词）；
     * 学习树阶段名则直接以 `LearningTree.getStageName` 为源 —— 那个函数**保持原样**（显示目录的
     * canonical 出处），Canvas 改用 [localizedTreeStageName]，两者必须逐字一致，否则中文态就变了。
     */
    @Test
    fun levelAndTreeRegistryCoveragePinned() {
        // ===== 等级称号（10 级）=====
        val levelZh = levelTitleCopy[DisplayLanguage.ZH]!!
        val levelEn = levelTitleCopy[DisplayLanguage.EN]!!
        val expectedLevelTitlesZh = listOf(
            "学习新手", "勤奋小学徒", "知识探索者", "学习达人", "学霸少年",
            "知识王者", "学习之星", "超级学霸", "传奇学者", "学神",
        )
        assertEquals(10, levelZh.size, "等级称号 ZH 数量漂移")
        assertEquals(10, levelEn.size, "等级称号 EN 数量漂移")
        assertEquals(expectedLevelTitlesZh, levelZh, "ZH 等级称号必须逐字等于 LevelSystem.levels 的标题")
        assertTrue(levelEn.flatMap { it.toList() }.none { it in '一'..'鿿' }, "英文等级称号残留汉字")
        assertEquals(levelEn.size, levelEn.toSet().size, "EN 等级称号重复")
        // LevelSystem.levels 是 private，全量列表只能手打；首尾两级用公开入口做活体对账
        assertEquals(levelZh[0], LevelSystem.getLevelInfo(0).title)
        assertEquals(levelZh[9], LevelSystem.getLevelInfo(10000).title)
        // helper 按 level - 1 取词：1 级取首条、10 级取末条
        assertEquals("学习新手", localizedLevelTitle(1, DisplayLanguage.ZH))
        assertEquals("Study Legend", localizedLevelTitle(10, DisplayLanguage.EN))
        // 越界回落索引 0（0 级不存在；99 级超过上限），与 petStageCopy 的回落约定一致
        assertEquals(levelZh[0], localizedLevelTitle(0, DisplayLanguage.ZH), "0 级应回落索引 0")
        assertEquals(levelEn[0], localizedLevelTitle(99, DisplayLanguage.EN), "越界应回落索引 0")

        // ===== 学习树阶段名（6 阶段）=====
        val treeZh = treeStageCopy[DisplayLanguage.ZH]!!
        val treeEn = treeStageCopy[DisplayLanguage.EN]!!
        assertEquals(6, treeZh.size, "树阶段名 ZH 数量漂移")
        assertEquals(6, treeEn.size, "树阶段名 EN 数量漂移")
        for (i in 0..5) {
            assertEquals(LearningTree.getStageName(i), treeZh[i], "ZH 阶段名必须逐字等于 getStageName：$i")
            assertEquals(LearningTree.getStageName(i), localizedTreeStageName(i, DisplayLanguage.ZH))
        }
        assertTrue(treeEn.flatMap { it.toList() }.none { it in '一'..'鿿' }, "英文树阶段名残留汉字")
        assertEquals(treeEn.size, treeEn.toSet().size, "EN 树阶段名重复")
        assertEquals("种子", localizedTreeStageName(0, DisplayLanguage.ZH))
        assertEquals("Fruiting", localizedTreeStageName(5, DisplayLanguage.EN))
        // 越界回落索引 0（stage 6 不存在；负数同样回落）
        assertEquals(treeZh[0], localizedTreeStageName(6, DisplayLanguage.ZH), "越界应回落索引 0")
        assertEquals(treeEn[0], localizedTreeStageName(-1, DisplayLanguage.EN), "负数应回落索引 0")
    }

    /**
     * [CommonStrings] 两个新函数型字段的双语逐字钉死。
     *
     * 期望串全部手打：`levelRemaining` 的英文走 [pointsWord]（1 → "1 point"，其余 "N points"），
     * `streakDays` 的英文 one/other 分支写在 lambda 内（1 → "1 day"），两端都必须钉死，
     * 否则"英文表里塞中文"或"漏了单数分支"这类错误在整表 CJK 扫描下完全隐形。
     */
    @Test
    fun commonStringsLevelAndStreakPinned() {
        val zh = AppStringsZh.common
        val en = AppStringsEn.common
        // 等级徽章副行（LevelSystem.LevelBadge 的 "还需 N分 升级"）
        assertEquals("还需 40分 升级", zh.levelRemaining(40))
        assertEquals("还需 1分 升级", zh.levelRemaining(1))
        // 英文 one/other：单数端与复数端各钉一条
        assertEquals("1 point to level up", en.levelRemaining(1))
        assertEquals("40 points to level up", en.levelRemaining(40))
        // 连续打卡天数（Animations.StreakBadge 的 "🔥 N天"）
        assertEquals("1天", zh.streakDays(1))
        assertEquals("3天", zh.streakDays(3))
        assertEquals("1 day", en.streakDays(1))
        assertEquals("3 days", en.streakDays(3))
    }

    /**
     * [GoalRecommendationCopy] 8 条理由模板两语逐字 + 引擎取词链路（T2）。
     *
     * 上 16 条是 copy 函数的 pin（函数型/引擎文案不在任何 AppStrings 表里，整表 CJK 扫描看不见）；
     * 随后用两语 `localizedPresetText` 钉住引擎依赖的标题/学科名注册表命中——这是"引擎 title/subject
     * 走注册表"这一设计的实际证据，注册表漏一条就会让英文推荐卡回退成中文。
     * 最后用两条 MATH 错题（均已到复习期）驱动 `generate`，钉死 EN/ZH 两种语言下的推荐标题与理由：
     * 薄弱科目分支优先级最高，reason 必须是由 copy 模板拼出的目标语言全串。
     */
    @Test
    fun goalRecommendationCopyPinsBothLanguages() {
        assertEquals("数学掌握率仅40%，是薄弱科目，建议重点复习", GoalRecommendationCopy.weakSubject("数学", 40, DisplayLanguage.ZH))
        assertEquals(
            "Math mastery is only 40% — a weak subject; focus your review there",
            GoalRecommendationCopy.weakSubject("Math", 40, DisplayLanguage.EN),
        )
        assertEquals("有3道错题到了复习时间，按遗忘曲线及时复习记得更牢", GoalRecommendationCopy.dueReview(3, DisplayLanguage.ZH))
        assertEquals(
            "3 mistakes are due for review — reviewing on the forgetting curve makes them stick",
            GoalRecommendationCopy.dueReview(3, DisplayLanguage.EN),
        )
        assertEquals("还有6道错题未掌握，建议今天攻克2-3道", GoalRecommendationCopy.manyUnmastered(6, DisplayLanguage.ZH))
        assertEquals("6 mistakes still unmastered — aim to clear 2–3 today", GoalRecommendationCopy.manyUnmastered(6, DisplayLanguage.EN))
        assertEquals("最近还没专注记录，用番茄钟开始高效学习吧", GoalRecommendationCopy.noFocus(DisplayLanguage.ZH))
        assertEquals(
            "No focus sessions recently — start with a Pomodoro for efficient study",
            GoalRecommendationCopy.noFocus(DisplayLanguage.EN),
        )
        assertEquals("最近专注较少（12分钟），多专注能提升效率", GoalRecommendationCopy.lowFocus(12, DisplayLanguage.ZH))
        assertEquals("Only 12 minutes of focus recently — more focus boosts efficiency", GoalRecommendationCopy.lowFocus(12, DisplayLanguage.EN))
        assertEquals("按时完成作业是巩固课堂知识的基础", GoalRecommendationCopy.homework(DisplayLanguage.ZH))
        assertEquals(
            "Finishing homework on time consolidates what you learn in class",
            GoalRecommendationCopy.homework(DisplayLanguage.EN),
        )
        assertEquals("错题掌握不错，课外阅读能拓展知识面、提升理解力", GoalRecommendationCopy.reading(DisplayLanguage.ZH))
        assertEquals(
            "Mistake mastery looks good — outside reading broadens knowledge and comprehension",
            GoalRecommendationCopy.reading(DisplayLanguage.EN),
        )
        assertEquals("劳逸结合，运动能让大脑更专注、记忆更牢固", GoalRecommendationCopy.exercise(DisplayLanguage.ZH))
        assertEquals("Balance work and rest — exercise sharpens focus and memory", GoalRecommendationCopy.exercise(DisplayLanguage.EN))

        // 引擎的标题与学科名走注册表：5 个 canonical 目标标题 + 5 个学科名都要命中 EN
        assertEquals("复习错题", localizedPresetText("复习错题", DisplayLanguage.ZH))
        assertEquals("Review mistakes", localizedPresetText("复习错题", DisplayLanguage.EN))
        assertEquals("专注2个番茄钟", localizedPresetText("专注2个番茄钟", DisplayLanguage.ZH))
        assertEquals("Focus for 2 Pomodoros", localizedPresetText("专注2个番茄钟", DisplayLanguage.EN))
        assertEquals("完成作业", localizedPresetText("完成作业", DisplayLanguage.ZH))
        assertEquals("Finish homework", localizedPresetText("完成作业", DisplayLanguage.EN))
        assertEquals("阅读30分钟", localizedPresetText("阅读30分钟", DisplayLanguage.ZH))
        assertEquals("Read for 30 minutes", localizedPresetText("阅读30分钟", DisplayLanguage.EN))
        assertEquals("体育锻炼", localizedPresetText("体育锻炼", DisplayLanguage.ZH))
        assertEquals("Exercise", localizedPresetText("体育锻炼", DisplayLanguage.EN))
        assertEquals("数学", localizedPresetText("数学", DisplayLanguage.ZH))
        assertEquals("Math", localizedPresetText("数学", DisplayLanguage.EN))
        assertEquals("语文", localizedPresetText("语文", DisplayLanguage.ZH))
        assertEquals("Chinese", localizedPresetText("语文", DisplayLanguage.EN))
        assertEquals("英语", localizedPresetText("英语", DisplayLanguage.ZH))
        assertEquals("English", localizedPresetText("英语", DisplayLanguage.EN))
        assertEquals("科学", localizedPresetText("科学", DisplayLanguage.ZH))
        assertEquals("Science", localizedPresetText("科学", DisplayLanguage.EN))
        assertEquals("其他", localizedPresetText("其他", DisplayLanguage.ZH))
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))

        // 两条数学错题均未掌握且已到复习期：薄弱科目分支（rate=0）压过到期复习分支
        val threeDaysAgo = Clock.System.now().toEpochMilliseconds() - 3L * 24 * 60 * 60 * 1000
        val mistakes = listOf(
            MistakeEntity(id = 1, subject = MistakeSubject.MATH, title = "错题一", isMastered = false, createdAt = threeDaysAgo),
            MistakeEntity(id = 2, subject = MistakeSubject.MATH, title = "错题二", isMastered = false, createdAt = threeDaysAgo),
        )
        val enReview = GoalRecommendationEngine.generate(mistakes, recentFocusMinutes = 100, lang = DisplayLanguage.EN)
            .first { it.targetId == "review_mistakes" }
        assertEquals("Review mistakes", enReview.title)
        assertEquals("Math mastery is only 0% — a weak subject; focus your review there", enReview.reason)
        val zhReview = GoalRecommendationEngine.generate(mistakes, recentFocusMinutes = 100, lang = DisplayLanguage.ZH)
            .first { it.targetId == "review_mistakes" }
        assertEquals("复习错题", zhReview.title)
        assertEquals("数学掌握率仅0%，是薄弱科目，建议重点复习", zhReview.reason)
    }

    /**
     * [TutoringCopy] 代表性 12 条两语逐字 + 7 个类别名全集（T2）。
     *
     * 12 条覆盖五个学科方法、三个错因分支（含兜底）、两条标题模板、两条详情模板；
     * 类别清单按 ZH/EN 两份精确列表断言，既证明"7 个类别两语齐全"，也钉住每个类别的英文
     * （`assertDiffers` 式的只比不等对交叉错位是瞎的）。38 条文案全部在
     * `TutoringAdviceEngine` 的消费路径里由本对象取词，整表 CJK 扫描看不见它们。
     */
    @Test
    fun tutoringCopyPinsBothLanguages() {
        // 五个学科方法
        assertEquals(
            "数学薄弱建议：每天陪孩子做2-3道基础题巩固概念，重视错题重做而非刷难题；让孩子把解题思路讲给你听，'讲题'能暴露理解漏洞。",
            TutoringCopy.subjectMethod(MistakeSubject.MATH, DisplayLanguage.ZH),
        )
        assertEquals(
            "Math needs work: do 2–3 basic problems a day consolidating concepts with your child, and prioritize redoing mistakes over grinding hard problems; have your child explain the solution steps to you — “teaching it back” exposes understanding gaps.",
            TutoringCopy.subjectMethod(MistakeSubject.MATH, DisplayLanguage.EN),
        )
        assertEquals(
            "语文薄弱建议：加强每日阅读积累，陪孩子朗读课文培养语感；字词基础每天听写巩固，作文多鼓励、少批评，先肯定再提建议。",
            TutoringCopy.subjectMethod(MistakeSubject.CHINESE, DisplayLanguage.ZH),
        )
        assertEquals(
            "Chinese needs work: build daily reading and read the textbook aloud together to develop language sense; practice daily dictation of characters and words; encourage more and criticize less in writing — affirm first, then suggest.",
            TutoringCopy.subjectMethod(MistakeSubject.CHINESE, DisplayLanguage.EN),
        )
        assertEquals(
            "英语薄弱建议：每天15分钟听力磨耳朵，陪孩子大声朗读培养语感；单词用'少量多次'记忆法，结合图片/场景记忆更牢固。",
            TutoringCopy.subjectMethod(MistakeSubject.ENGLISH, DisplayLanguage.ZH),
        )
        assertEquals(
            "English needs work: 15 minutes of listening daily to train the ear and read aloud together to build language sense; memorize words in small, frequent batches, reinforced with pictures or scenes.",
            TutoringCopy.subjectMethod(MistakeSubject.ENGLISH, DisplayLanguage.EN),
        )
        assertEquals(
            "科学薄弱建议：多联系生活实际讲解原理，有条件一起做小实验；让孩子画出知识导图，理解比死记硬背更重要。",
            TutoringCopy.subjectMethod(MistakeSubject.SCIENCE, DisplayLanguage.ZH),
        )
        assertEquals(
            "Science needs work: explain principles through everyday life and do small experiments together when possible; have your child draw knowledge maps — understanding beats rote memorization.",
            TutoringCopy.subjectMethod(MistakeSubject.SCIENCE, DisplayLanguage.EN),
        )
        assertEquals(
            "建议和孩子一起分析薄弱环节的具体原因，是基础不牢还是粗心，针对性地制定改进计划。",
            TutoringCopy.subjectMethod(MistakeSubject.OTHER, DisplayLanguage.ZH),
        )
        assertEquals(
            "Sit down with your child and analyze the specific cause of the weak area — shaky foundations or carelessness — then make a targeted improvement plan.",
            TutoringCopy.subjectMethod(MistakeSubject.OTHER, DisplayLanguage.EN),
        )

        // 三个错因分支：首分支、次分支、兜底
        assertEquals(
            "孩子最常因‘粗心马虎’出错。建议：培养做完检查的习惯，平时练习限时完成模拟考试节奏；粗心往往是熟练度不够，基础题要多练到自动化。",
            TutoringCopy.errorAdvice("粗心马虎", DisplayLanguage.ZH),
        )
        assertEquals(
            "Your child most often errs from “carelessness”. Suggestion: build a checking habit after finishing and do timed practice to simulate exam pace; carelessness often means insufficient fluency — drill basic problems to automaticity.",
            TutoringCopy.errorAdvice("粗心马虎", DisplayLanguage.EN),
        )
        assertEquals(
            "孩子最常因‘概念不清’出错。建议：回归课本把基础概念真正弄懂，让孩子用自己的话讲出概念含义；概念不清就刷题是无效的，先理解再练习。",
            TutoringCopy.errorAdvice("概念不清", DisplayLanguage.ZH),
        )
        assertEquals(
            "Your child most often errs from “unclear concepts”. Suggestion: return to the textbook and truly understand the basics; have your child explain them in their own words; drilling without understanding is ineffective — understand first, then practice.",
            TutoringCopy.errorAdvice("概念不清", DisplayLanguage.EN),
        )
        assertEquals(
            "建议和孩子一起分析错题的具体原因，针对性地制定改进计划。",
            TutoringCopy.errorAdvice("未知类型", DisplayLanguage.ZH),
        )
        assertEquals(
            "Analyze the specific causes of mistakes together with your child and make a targeted improvement plan.",
            TutoringCopy.errorAdvice("未知类型", DisplayLanguage.EN),
        )

        // 两条标题模板（学科名/错因名由调用方先包注册表再传入）
        assertEquals("数学是薄弱科目（掌握率40%）", TutoringCopy.weakSubjectTitle("数学", 40, DisplayLanguage.ZH))
        assertEquals("Math is a weak subject (mastery 40%)", TutoringCopy.weakSubjectTitle("Math", 40, DisplayLanguage.EN))
        assertEquals("孩子最常因「粗心马虎」出错（3次）", TutoringCopy.errorTitle("粗心马虎", 3, DisplayLanguage.ZH))
        assertEquals(
            "Your child most often errs from “Careless mistakes” (3 times)",
            TutoringCopy.errorTitle("Careless mistakes", 3, DisplayLanguage.EN),
        )

        // 两条详情模板
        assertEquals(
            "建议每天固定15分钟陪孩子复习错题，用'遮住答案先回想'的方式效果最好。错题是提分最快的突破口，坚持清零进步明显。",
            TutoringCopy.unmasteredDetail(DisplayLanguage.ZH),
        )
        assertEquals(
            "Set aside 15 minutes daily to review mistakes together — covering the answer and recalling first works best. Mistakes are the fastest lever for improvement; keep clearing them and progress will show.",
            TutoringCopy.unmasteredDetail(DisplayLanguage.EN),
        )
        assertEquals(
            "多关注努力过程而非只看分数；孩子遇到困难时先共情再引导；每天留一点时间听孩子讲讲今天学了什么，'输出'是最好的'输入'。",
            TutoringCopy.partnerDetail(DisplayLanguage.ZH),
        )
        assertEquals(
            "Focus on the effort, not just scores; empathize before guiding when your child struggles; save a little time each day to hear what they learned — “output” is the best “input”.",
            TutoringCopy.partnerDetail(DisplayLanguage.EN),
        )

        // 7 个类别名两语精确列表（同时证明集合大小恒为 7 且两语一一对应）
        val zhCategories = listOf(
            TutoringCopy.weakSubjectCategory(DisplayLanguage.ZH),
            TutoringCopy.errorCategory(DisplayLanguage.ZH),
            TutoringCopy.reviewCategory(DisplayLanguage.ZH),
            TutoringCopy.focusTrainingCategory(DisplayLanguage.ZH),
            TutoringCopy.focusMaintenanceCategory(DisplayLanguage.ZH),
            TutoringCopy.habitCategory(DisplayLanguage.ZH),
            TutoringCopy.mindsetCategory(DisplayLanguage.ZH),
        )
        assertEquals(
            listOf("薄弱科目辅导", "错因针对辅导", "错题复习督促", "专注力培养", "专注力保持", "习惯养成激励", "辅导心态建议"),
            zhCategories,
        )
        assertEquals(7, zhCategories.toSet().size)
        val enCategories = listOf(
            TutoringCopy.weakSubjectCategory(DisplayLanguage.EN),
            TutoringCopy.errorCategory(DisplayLanguage.EN),
            TutoringCopy.reviewCategory(DisplayLanguage.EN),
            TutoringCopy.focusTrainingCategory(DisplayLanguage.EN),
            TutoringCopy.focusMaintenanceCategory(DisplayLanguage.EN),
            TutoringCopy.habitCategory(DisplayLanguage.EN),
            TutoringCopy.mindsetCategory(DisplayLanguage.EN),
        )
        assertEquals(
            listOf(
                "Weak subject coaching", "Error-type coaching", "Mistake review reminder",
                "Focus training", "Focus maintenance", "Habit encouragement", "Coaching mindset",
            ),
            enCategories,
        )
        assertEquals(7, enCategories.toSet().size)
        assertTrue(enCategories.flatMap { it.toList() }.none { it in '一'..'鿿' }, "英文类别名残留汉字")
    }

    /**
     * [LearningScoreCopy] 5 个等级 + 5 条鼓励语两语逐字 + `calculate` 按语言取词（T2）。
     *
     * 阈值（85/60/40/1/0）在各语言的 when 里各写一份，测试用边界值逐一覆盖；
     * `calculate` 的两语期望同时证明评分逻辑（total/维度分）未随语言改变。
     */
    @Test
    fun learningScoreCopyPinsBothLanguages() {
        assertEquals("优秀", LearningScoreCopy.grade(85, DisplayLanguage.ZH))
        assertEquals("Excellent", LearningScoreCopy.grade(85, DisplayLanguage.EN))
        assertEquals("良好", LearningScoreCopy.grade(60, DisplayLanguage.ZH))
        assertEquals("Good", LearningScoreCopy.grade(60, DisplayLanguage.EN))
        assertEquals("不错", LearningScoreCopy.grade(40, DisplayLanguage.ZH))
        assertEquals("Nice", LearningScoreCopy.grade(40, DisplayLanguage.EN))
        assertEquals("起步", LearningScoreCopy.grade(1, DisplayLanguage.ZH))
        assertEquals("Starting", LearningScoreCopy.grade(1, DisplayLanguage.EN))
        assertEquals("待开始", LearningScoreCopy.grade(0, DisplayLanguage.ZH))
        assertEquals("Not started", LearningScoreCopy.grade(0, DisplayLanguage.EN))

        assertEquals("今天学习超棒！全面发展，继续保持！", LearningScoreCopy.encouragement(85, DisplayLanguage.ZH))
        assertEquals("Outstanding learning today! Well-rounded — keep it up!", LearningScoreCopy.encouragement(85, DisplayLanguage.EN))
        assertEquals("今天表现很好！再坚持一下就完美了！", LearningScoreCopy.encouragement(60, DisplayLanguage.ZH))
        assertEquals("Great work today! A little more consistency and it's perfect!", LearningScoreCopy.encouragement(60, DisplayLanguage.EN))
        assertEquals("不错的开始！专注和复习还能再加把劲！", LearningScoreCopy.encouragement(40, DisplayLanguage.ZH))
        assertEquals("A good start! A bit more focus and review will go a long way!", LearningScoreCopy.encouragement(40, DisplayLanguage.EN))
        assertEquals("已经迈出一大步！继续完成更多任务吧！", LearningScoreCopy.encouragement(1, DisplayLanguage.ZH))
        assertEquals("A big step already! Keep completing more tasks!", LearningScoreCopy.encouragement(1, DisplayLanguage.EN))
        assertEquals("新的一天，从完成第一个任务开始吧！", LearningScoreCopy.encouragement(0, DisplayLanguage.ZH))
        assertEquals("A new day — start by finishing your first task!", LearningScoreCopy.encouragement(0, DisplayLanguage.EN))

        val zh = LearningScoreCalculator.calculate(1f, 60, 3, DisplayLanguage.ZH)
        assertEquals(100, zh.total)
        assertEquals("优秀", zh.grade)
        assertEquals("今天学习超棒！全面发展，继续保持！", zh.encouragement)
        val en = LearningScoreCalculator.calculate(1f, 60, 3, DisplayLanguage.EN)
        assertEquals(100, en.total)
        assertEquals("Excellent", en.grade)
        assertEquals("Outstanding learning today! Well-rounded — keep it up!", en.encouragement)
        val enZero = LearningScoreCalculator.calculate(0f, 0, 0, DisplayLanguage.EN)
        assertEquals(0, enZero.total)
        assertEquals(0, enZero.checkinScore)
        assertEquals("Not started", enZero.grade)
        assertEquals("A new day — start by finishing your first task!", enZero.encouragement)
    }

    /**
     * 成就徽章区三条骨架文案两语守卫（T3）。
     *
     * 徽章标题/描述**不在这张表里**：它们按 `AchievementDef.type` 走注册表 [achievementCopy]，
     * 本表只有区标题/空态/待解锁标题三条；三对 ZH≠EN 即可证明标签随语言。
     */
    @Test
    fun achievementSectionCopyDiffers() {
        val zh = AppStringsZh.achievementSection
        val en = AppStringsEn.achievementSection
        assertDiffers(
            zh.sectionTitle to en.sectionTitle,
            zh.emptyLocked to en.emptyLocked,
            zh.lockedTitle to en.lockedTitle,
        )
    }

    /**
     * 效率报表页全部**静态**字段的 ZH≠EN 穷举（T3，55 条）。
     *
     * 函数型字段进不了 `AppStrings.toString()`（Ruling 20-b 的老盲区），由
     * [efficiencyReportInterpolatedPinsBothLanguages] 逐字钉住；这里把每个静态字段
     * 手工列全并断言清单长度，字段改名/新增时本清单必须同步。
     *
     * 末段钉住页面依赖的注册表命中：五个学科名与五种错因名都是 **canonical 存储值**，
     * 页面渲染统一包 [localizedPresetText]（不是逐科建表字段）；英文命中是"英文态科目行/
     * 错因行不出现中文"的实际证据。
     */
    @Test
    fun efficiencyReportCopyDiffers() {
        val zh = AppStringsZh.efficiencyReport
        val en = AppStringsEn.efficiencyReport
        val statics = listOf(
            "title" to (zh.title to en.title),
            "statPointsLabel" to (zh.statPointsLabel to en.statPointsLabel),
            "statFocusLabel" to (zh.statFocusLabel to en.statFocusLabel),
            "statCheckinLabel" to (zh.statCheckinLabel to en.statCheckinLabel),
            "weekTitle" to (zh.weekTitle to en.weekTitle),
            "weekVerdictNew" to (zh.weekVerdictNew to en.weekVerdictNew),
            "weekVerdictUp" to (zh.weekVerdictUp to en.weekVerdictUp),
            "weekVerdictFlat" to (zh.weekVerdictFlat to en.weekVerdictFlat),
            "weekVerdictDown" to (zh.weekVerdictDown to en.weekVerdictDown),
            "focusMetricLabel" to (zh.focusMetricLabel to en.focusMetricLabel),
            "checkinMetricLabel" to (zh.checkinMetricLabel to en.checkinMetricLabel),
            "reviewMetricLabel" to (zh.reviewMetricLabel to en.reviewMetricLabel),
            "avgScoreLabel" to (zh.avgScoreLabel to en.avgScoreLabel),
            "focusUnit" to (zh.focusUnit to en.focusUnit),
            "checkinUnit" to (zh.checkinUnit to en.checkinUnit),
            "reviewUnit" to (zh.reviewUnit to en.reviewUnit),
            "scoreUnit" to (zh.scoreUnit to en.scoreUnit),
            "monthTitle" to (zh.monthTitle to en.monthTitle),
            "monthVerdictNew" to (zh.monthVerdictNew to en.monthVerdictNew),
            "monthVerdictUp" to (zh.monthVerdictUp to en.monthVerdictUp),
            "monthVerdictFlat" to (zh.monthVerdictFlat to en.monthVerdictFlat),
            "monthVerdictDown" to (zh.monthVerdictDown to en.monthVerdictDown),
            "subjectTitle" to (zh.subjectTitle to en.subjectTitle),
            "subjectSubtitle" to (zh.subjectSubtitle to en.subjectSubtitle),
            "errorTitle" to (zh.errorTitle to en.errorTitle),
            "errorSubtitle" to (zh.errorSubtitle to en.errorSubtitle),
            "errorAdviceCareless" to (zh.errorAdviceCareless to en.errorAdviceCareless),
            "errorAdviceConcepts" to (zh.errorAdviceConcepts to en.errorAdviceConcepts),
            "errorAdviceMethod" to (zh.errorAdviceMethod to en.errorAdviceMethod),
            "errorAdviceReading" to (zh.errorAdviceReading to en.errorAdviceReading),
            "errorAdviceCalculation" to (zh.errorAdviceCalculation to en.errorAdviceCalculation),
            "errorAdviceFallback" to (zh.errorAdviceFallback to en.errorAdviceFallback),
            "suggestionsTitle" to (zh.suggestionsTitle to en.suggestionsTitle),
            "scoreTrendTitle" to (zh.scoreTrendTitle to en.scoreTrendTitle),
            "scoreTrendEmpty" to (zh.scoreTrendEmpty to en.scoreTrendEmpty),
            "focusInsightTitle" to (zh.focusInsightTitle to en.focusInsightTitle),
            "bestPeriodLabel" to (zh.bestPeriodLabel to en.bestPeriodLabel),
            "avgSessionLabel" to (zh.avgSessionLabel to en.avgSessionLabel),
            "focusCountLabel" to (zh.focusCountLabel to en.focusCountLabel),
            "freeStudy" to (zh.freeStudy to en.freeStudy),
            "distributionTitle" to (zh.distributionTitle to en.distributionTitle),
            "distributionSubtitle" to (zh.distributionSubtitle to en.distributionSubtitle),
            "focusTrendTitle" to (zh.focusTrendTitle to en.focusTrendTitle),
            "noData" to (zh.noData to en.noData),
            "checkinTrendTitle" to (zh.checkinTrendTitle to en.checkinTrendTitle),
            "deltaFlat" to (zh.deltaFlat to en.deltaFlat),
            "periodEarlyMorning" to (zh.periodEarlyMorning to en.periodEarlyMorning),
            "periodMorning" to (zh.periodMorning to en.periodMorning),
            "periodNoon" to (zh.periodNoon to en.periodNoon),
            "periodAfternoon" to (zh.periodAfternoon to en.periodAfternoon),
            "periodEvening" to (zh.periodEvening to en.periodEvening),
            "periodLateNight" to (zh.periodLateNight to en.periodLateNight),
            "suggestionPomodoro" to (zh.suggestionPomodoro to en.suggestionPomodoro),
            "suggestionMastered" to (zh.suggestionMastered to en.suggestionMastered),
            "suggestionEmpty" to (zh.suggestionEmpty to en.suggestionEmpty),
        )
        statics.forEach { (name, pair) ->
            assertNotEquals(pair.first, pair.second, "疑似漏译：efficiencyReport.$name")
        }
        assertEquals(55, statics.size, "静态字段清单漂移：新增/删除字段时同步本清单")

        // 页面依赖的 canonical 取词：5 个学科名（含"其他"）+ 5 种错因名
        listOf(
            "数学" to "Math", "语文" to "Chinese", "英语" to "English",
            "科学" to "Science", "其他" to "Other",
        ).forEach { (canonical, english) ->
            assertEquals(canonical, localizedPresetText(canonical, DisplayLanguage.ZH))
            assertEquals(english, localizedPresetText(canonical, DisplayLanguage.EN), "学科名注册表漏命中：$canonical")
        }
        listOf(
            "粗心马虎" to "Careless mistakes", "概念不清" to "Unclear concepts",
            "方法不会" to "Unknown method", "审题错误" to "Misread the question",
            "计算错误" to "Calculation errors",
        ).forEach { (canonical, english) ->
            assertEquals(canonical, localizedPresetText(canonical, DisplayLanguage.ZH))
            assertEquals(english, localizedPresetText(canonical, DisplayLanguage.EN), "错因名注册表漏命中：$canonical")
        }
    }

    /**
     * [EfficiencyReportStrings] 全部 13 个函数型字段两语逐字（代表 10 条以上，Ruling 20-b 全覆盖）。
     *
     * 期望串全部手打：中文侧与迁移前页面的内插字面量逐字节一致（空格、量词、全角括号都保留），
     * 英文侧钉住语序与单位（minutes / times / pts）。页面把 `hourLabel` 的结果再喂给
     * `bestPeriodHint` / `suggestionBestPeriod`，这里用同一条 `hourLabel` 输出当实参，
     * 钉住"时段名 → 提示句"的整链，而不是分别自证。
     */
    @Test
    fun efficiencyReportInterpolatedPinsBothLanguages() {
        val zh = AppStringsZh.efficiencyReport
        val en = AppStringsEn.efficiencyReport

        // 四个指标行的值与增减单位
        assertEquals("12分", zh.focusValue(12))
        assertEquals("12 min", en.focusValue(12))
        assertEquals("分", zh.focusUnit)
        assertEquals("min", en.focusUnit)
        assertEquals("12次", zh.checkinValue(12))
        assertEquals("12 times", en.checkinValue(12))
        assertEquals("次", zh.checkinUnit)
        assertEquals("times", en.checkinUnit)
        assertEquals("3道", zh.reviewValue(3))
        assertEquals("3", en.reviewValue(3))
        assertEquals("道", zh.reviewUnit)
        assertEquals("mistakes", en.reviewUnit)
        assertEquals("87分", zh.scoreValue(87))
        assertEquals("87 pts", en.scoreValue(87))
        assertEquals("分", zh.scoreUnit)
        assertEquals("pts", en.scoreUnit)

        // 7 日平均分
        assertEquals("7日平均 87 分", zh.scoreTrendAverage(87))
        assertEquals("7-day average: 87 pts", en.scoreTrendAverage(87))

        // 时段名 + "$period${hour}点" 的整链
        val zhPeriod = zh.hourLabel(zh.periodEarlyMorning, 8)
        val enPeriod = en.hourLabel(en.periodEarlyMorning, 8)
        assertEquals("早上8点", zhPeriod)
        assertEquals("Morning 8:00", enPeriod)
        assertEquals("💡 你在 早上8点 专注力最好，建议把难题和重要学习安排在这个时段。", zh.bestPeriodHint(zhPeriod))
        assertEquals(
            "💡 You focus best during Morning 8:00 — schedule hard problems and important study then.",
            en.bestPeriodHint(enPeriod),
        )

        // 专注投入分布的首要目标提示
        assertEquals("💡 你在「数学」上投入最多（30分钟），注意各科均衡分配哦。", zh.distributionTopHint("数学", 30))
        assertEquals(
            "💡 You invested the most in “Math” (30 min) — keep the balance across subjects.",
            en.distributionTopHint("Math", 30),
        )

        // 学习建议 5 条函数型（薄弱科目/到期复习/未掌握/专注良好/黄金时段）
        assertEquals(
            "数学是你的薄弱科目（掌握率40%），建议多花时间复习数学错题。",
            zh.suggestionWeakSubject("数学", 40),
        )
        assertEquals(
            "Math is a weak subject (mastery 40%) — spend more time reviewing Math mistakes.",
            en.suggestionWeakSubject("Math", 40),
        )
        assertEquals(
            "有 3 道错题到了复习时间，按遗忘曲线及时复习，记忆保持率可提升一倍以上。",
            zh.suggestionDueReview(3),
        )
        assertEquals(
            "3 mistakes are due for review — reviewing on the forgetting curve can more than double retention.",
            en.suggestionDueReview(3),
        )
        assertEquals("还有 6 道错题未掌握，建议每天攻克 2-3 道，逐步清零。", zh.suggestionUnmastered(6))
        assertEquals(
            "6 mistakes are still unmastered — clear 2–3 a day to work down to zero.",
            en.suggestionUnmastered(6),
        )
        assertEquals("累计专注 120 分钟，保持专注是高效学习的关键，继续加油！", zh.suggestionFocusGood(120))
        assertEquals(
            "120 minutes of total focus — staying focused is the key to efficient study. Keep going!",
            en.suggestionFocusGood(120),
        )
        assertEquals(
            "你在 早上8点 专注力最好，把难题和重要科目安排在这个时段，效率更高。",
            zh.suggestionBestPeriod(zhPeriod),
        )
        assertEquals(
            "You focus best during Morning 8:00 — schedule hard problems and key subjects then for higher efficiency.",
            en.suggestionBestPeriod(enPeriod),
        )
    }

    /**
     * [ParentTutoringAdviceStrings] 两字段守卫（T4 补齐 T2 KDoc 声称的"逐字段 ZH≠EN"）。
     *
     * 本表只有顶栏标题与说明卡两条页面骨架，建议条目全部是引擎生成（[TutoringCopy]）。
     * 两条都进 [assertDiffers]（只比不等对"两条英文互换/整条漏译"是瞎的），
     * 英文 title/intro 另按整串钉死。
     */
    @Test
    fun parentTutoringAdviceCopyDiffers() {
        val zh = AppStringsZh.parentTutoringAdvice
        val en = AppStringsEn.parentTutoringAdvice
        assertDiffers(
            zh.title to en.title,
            zh.intro to en.intro,
        )
        assertEquals("💡 Tutoring advice", en.title)
        assertEquals(
            "The advice below is generated from your child's subject mastery, mistakes and focus — to help you coach more effectively.",
            en.intro,
        )
    }

    /**
     * W8 跨表词汇 pin（等级评语 / 学科名两处跨源同值）。
     *
     * 等级评语由 [LearningScoreCopy]（引擎文案，不在任何 AppStrings 表里）生成，
     * 数据看板 `childPerformanceLine` 的内容位与 `LearningScoreCalculator.calculate` 的结果同源；
     * 两语词形在这里钉死，避免"看板换一套评语、计算器换另一套"。
     * 学科名是**注册表 canonical 串**：`TutoringAdviceEngine` / `GoalRecommendationEngine` /
     * 效率报表都先取 canonical 中文再包 [localizedPresetText]，与 [MistakeStrings] 的裸标签
     * 必须逐字一致（两处各存一份词形，漂移只能靠本 pin 抓）。
     */
    @Test
    fun crossTableVocabularyPinned() {
        assertEquals("优秀", LearningScoreCopy.grade(95, DisplayLanguage.ZH))
        assertEquals("Excellent", LearningScoreCopy.grade(95, DisplayLanguage.EN))
        assertEquals(AppStringsZh.mistake.subjectMath, localizedPresetText("数学", DisplayLanguage.ZH))
        assertEquals(AppStringsEn.mistake.subjectMath, localizedPresetText("数学", DisplayLanguage.EN))
        assertEquals(AppStringsEn.mistake.subjectOther, localizedPresetText("其他", DisplayLanguage.EN))
    }
}
