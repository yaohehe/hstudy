package com.hstudy.app.i18n

/** 主表条目：zh 必须等于 canonical key 本身（由 preset() 构造保证，测试再钉一遍）。 */
data class PresetCopy(val zh: String, val en: String)

private fun preset(canonical: String, en: String): Pair<String, PresetCopy> =
    canonical to PresetCopy(canonical, en)

/**
 * 预设内容显示注册表（方案 A，spec §4）：DB 保持 canonical 中文，本表**只**在渲染点使用。
 * 任何值不得进入 DB 写入 / bizKey / 幂等比较（Ruling 25；i18n_storage_boundary.py 第 5 支覆盖）。
 * 未命中 = 原样返回：用户自建内容、家长改过的预设、历史账本描述全走这条路。
 * key 集合与**可程序化读取的**种子 canonical 由 PresetRegistryTest 互为子集钉死——改这类种子名
 * 会打破测试，而不是静默显示未翻译文本；10 条手钉在 FamilySyncHelper/BackupHelper 里的
 * 设备/诊断字面量无法活体交叉校验（漂移守卫的上限见 PresetRegistryTest 的 KDoc）。ZH 值手打
 * （不 import ui/data 种子，i18n 自包含），测试负责跨层对账。
 * W5 起另有 16 条页面内预设手工收录（见「W5 页面内预设目录」组，守卫见
 * PresetRegistryTest.w5PageLocalPresetsResolvePerLanguage）。
 * W6 另有 6 条示例心愿 canonical（见「W6 页面内预设目录」组，守卫见
 * PresetRegistryTest.w6WishSamplesResolvePerLanguage）。
 * W8 另有 4 条学科名（见「W8 学科名」组）：W8 三个文案引擎把 canonical 学科串包 [localizedPresetText]
 * 取词，注册表必须命中才能让英文推荐/辅导标题显示 Math/Chinese/… 而不是回退中文。
 */
val presetTextRegistry: Map<String, PresetCopy> = mapOf(
    // ===== 预设任务标题（55 条去重；TaskRepository.getPresets() + getSingleTaskSuggestions()）=====
    preset("完成当天作业", "Finish today's homework"),
    preset("整理书包", "Pack the school bag"),
    preset("朗读课文", "Read the text aloud"),
    preset("练字15分钟", "Practice handwriting for 15 minutes"),
    preset("背诵一首古诗", "Recite a classical poem"),
    preset("阅读20分钟", "Read for 20 minutes"),
    preset("口算练习", "Mental math practice"),
    preset("讲一个故事", "Tell a story"),
    preset("认识新字词", "Learn new words"),
    preset("早睡早起", "Early to bed, early to rise"),
    preset("自己穿衣", "Get dressed by myself"),
    preset("帮忙做家务", "Help with chores"),
    preset("礼貌问好", "Greet people politely"),
    preset("收拾玩具", "Tidy up the toys"),
    preset("错题订正工整", "Correct mistakes neatly"),
    preset("课前预习", "Preview before class"),
    preset("复习回顾", "Review and recap"),
    preset("英语单词听写", "English word dictation"),
    preset("完成课外练习", "Finish extra exercises"),
    preset("错题原因分析", "Analyze mistake causes"),
    preset("阅读30分钟", "Read for 30 minutes"),
    preset("写作练习", "Writing practice"),
    preset("科学小实验", "A little science experiment"),
    preset("每日阅读打卡", "Daily reading check-in"),
    preset("帮助同学", "Help classmates"),
    preset("积极参与课堂", "Join in actively in class"),
    preset("整理房间", "Tidy my room"),
    preset("坚持运动", "Keep exercising"),
    preset("课前预习笔记", "Preview and take notes"),
    preset("课后复习总结", "Review and summarize after class"),
    preset("错题深度分析", "Deep mistake analysis"),
    preset("英语晨读", "English morning reading"),
    preset("专题突破练习", "Targeted topic practice"),
    preset("整理知识框架", "Build a knowledge framework"),
    preset("英语听说训练", "English listening and speaking practice"),
    preset("深度阅读45分钟", "Deep reading for 45 minutes"),
    preset("难题挑战", "Hard problem challenge"),
    preset("番茄钟专注2小时", "Two hours of pomodoro focus"),
    preset("自主制定学习计划", "Make my own study plan"),
    preset("体育锻炼30分钟", "Exercise for 30 minutes"), // 与任务描述共享 key（getSingleTaskSuggestions 的"坚持运动"描述同串）
    preset("远离手机2小时", "Stay off the phone for 2 hours"),
    preset("早睡不熬夜", "Sleep early, no staying up late"),
    preset("课后复习", "Review after class"),
    preset("错题订正", "Correct mistakes"),
    preset("背诵默写", "Recite and write from memory"),
    preset("整理笔记", "Organize class notes"),
    preset("课外阅读", "Reading beyond class"),
    preset("专项练习", "Targeted practice"),
    preset("思维导图", "Mind map"),
    preset("英语听说", "English listening and speaking"),
    preset("科学实验", "Science experiment"),
    preset("做家务", "Do chores"),
    preset("礼貌待人", "Be polite to others"),
    preset("专注学习", "Study without distractions"),
    preset("帮助他人", "Help others"),
    // ===== 预设任务描述（60 条去重；"体育锻炼30分钟"已在标题组，此处 59 条）=====
    preset("按时完成老师布置的作业", "Finish the teacher's homework on time"),
    preset("自己整理明天的书包", "Pack tomorrow's school bag by myself"),
    preset("大声朗读一篇课文", "Read one text aloud"),
    preset("认真练字15分钟", "Practice handwriting carefully for 15 minutes"),
    preset("背诵并默写一首古诗", "Recite a classical poem and write it from memory"),
    preset("课外阅读20分钟", "Read beyond class for 20 minutes"),
    preset("完成一页口算练习", "Finish one page of mental math"),
    preset("给家人讲一个完整的故事", "Tell my family a complete story"),
    preset("学习并记住5个新字词", "Learn and remember 5 new words"),
    preset("21:00前上床，7:00前起床", "In bed by 21:00, up by 7:00"),
    preset("自己选择并穿好衣服", "Choose my clothes and get dressed by myself"),
    preset("帮忙擦桌子、扫地等", "Help wipe tables, sweep the floor, and more"),
    preset("主动跟长辈、老师问好", "Greet elders and teachers without being asked"),
    preset("玩完后自己收拾整理玩具", "Tidy up the toys myself after playing"),
    preset("订正错题，书写工整", "Correct mistakes with neat handwriting"),
    preset("预习明天的课程内容", "Preview tomorrow's lessons"),
    preset("复习当天所学内容", "Review what was learned today"),
    preset("听写并订正10个单词", "Take dictation of 10 words and correct them"),
    preset("完成1套课外练习", "Finish one set of extra exercises"),
    preset("写出错误点和正确思路", "Write down the error and the correct approach"),
    preset("课外阅读至少30分钟", "Read beyond class for at least 30 minutes"),
    preset("100字起步写作练习", "Writing practice starting from 100 characters"),
    preset("动手做一个科学小实验", "Do a little science experiment hands-on"),
    preset("坚持每天阅读", "Keep reading every day"),
    preset("主动帮助其他同学", "Help other classmates without being asked"),
    preset("上课积极发言参与", "Speak up and take part actively in class"),
    preset("整理自己的房间保持整洁", "Tidy my room and keep it neat"),
    preset("跳绳、跑步等运动20分钟", "Exercise for 20 minutes: jump rope, running, and more"),
    preset("高质量完成所有作业", "Finish all homework to a high standard"),
    preset("预习并做笔记", "Preview and take notes"),
    preset("总结当天知识点", "Summarize today's key points"),
    preset("分析错因+举一反三", "Analyze the error and try variations"),
    preset("晨读英语20分钟", "Read English aloud for 20 minutes in the morning"),
    preset("针对薄弱科目做专项练习", "Do targeted practice for weak subjects"),
    preset("用思维导图整理一章知识", "Organize one chapter with a mind map"),
    preset("30分钟英语听力或口语练习", "30 minutes of English listening or speaking practice"),
    preset("阅读并写读后感", "Read and write a reading response"),
    preset("独立攻克3道压轴难题", "Solve 3 of the hardest problems on my own"),
    preset("累计完成4个番茄钟", "Complete 4 pomodoros in total"),
    preset("制定并执行周学习计划", "Make and follow a weekly study plan"),
    preset("每天坚持运动", "Keep exercising every day"),
    preset("学习时段不玩手机", "No phone during study time"),
    preset("22:30前睡觉保证睡眠", "Asleep by 22:30 to get enough sleep"),
    preset("复习当天所学知识", "Review the knowledge learned today"),
    preset("订正错题并分析原因", "Correct mistakes and analyze the causes"),
    preset("背诵课文/单词并默写", "Recite texts or words and write them from memory"),
    preset("整理课堂笔记使其清晰", "Organize class notes so they are clear"),
    preset("课外阅读30分钟以上", "Read beyond class for more than 30 minutes"),
    preset("完成一篇写作练习", "Finish one writing exercise"),
    preset("针对薄弱点做专项练习", "Do targeted practice for weak points"),
    preset("用思维导图梳理知识结构", "Map out the knowledge structure with a mind map"),
    preset("英语听力或口语训练", "English listening or speaking practice"),
    preset("独立攻克几道难题", "Solve a few hard problems on my own"),
    preset("规律作息不熬夜", "Keep a regular schedule and don't stay up late"),
    preset("主动分担家务劳动", "Share the housework without being asked"),
    preset("主动问好、文明礼貌", "Greet people proactively and be courteous"),
    preset("保持房间整洁有序", "Keep my room tidy and orderly"),
    preset("学习时段不分心玩手机", "No phone distractions during study time"),
    preset("主动帮助同学或家人", "Help classmates or family without being asked"),
    // ===== 模板名/描述/年龄段（3 套 × 3）=====
    preset("小学低年级", "Lower primary"),
    preset("适合1-3年级，以基础习惯养成为主，积分值较低易于达成", "For grades 1-3: builds basic habits, with low points that are easy to earn"),
    preset("6-9岁", "Ages 6-9"),
    preset("小学高年级", "Upper primary"),
    preset("适合4-6年级，注重自主学习能力和深度思考", "For grades 4-6: focuses on independent learning and deep thinking"),
    preset("9-12岁", "Ages 9-12"),
    preset("初中", "Middle school"),
    preset("适合初中生，注重高效学习、自主管理和深度思考", "For middle schoolers: efficient studying, self-management, and deep thinking"),
    preset("12-15岁", "Ages 12-15"),
    // ===== 预设奖励名（27 条；RewardRepository.initDefaultRewards()）=====
    preset("看一集动画片", "Watch one cartoon episode"),
    preset("打游戏30分钟", "Play games for 30 minutes"),
    preset("打游戏60分钟", "Play games for 60 minutes"),
    preset("玩桌游一次", "Play a board game once"),
    preset("看一场电影", "Watch a movie"),
    preset("买一份零食", "Buy a snack"),
    preset("买一个冰淇淋", "Buy an ice cream"),
    preset("自己点一次外卖", "Order takeout myself once"),
    preset("选一家餐厅吃大餐", "Pick a restaurant for a big meal"),
    preset("骑自行车兜风", "Go for a bike ride"),
    preset("去图书馆/书店", "Visit the library/bookstore"),
    preset("去公园玩半天", "Spend half a day at the park"),
    preset("周末短途旅行", "A weekend short trip"),
    preset("免一次家务", "Skip one chore"),
    preset("选择今晚菜品", "Choose tonight's menu"),
    preset("晚睡30分钟", "Stay up 30 minutes later"),
    preset("晚睡60分钟", "Stay up 60 minutes later"),
    preset("当一天小家长", "Be the little parent for a day"),
    preset("买一套卡片", "Buy a card set"),
    preset("买一本课外书", "Buy a book for fun"),
    preset("买文具/小玩具", "Buy stationery/a small toy"),
    preset("买一件喜欢的衣服", "Buy a piece of clothing I like"),
    preset("和朋友视频30分钟", "Video chat with a friend for 30 minutes"),
    preset("邀请朋友来家玩", "Invite a friend over"),
    preset("兴趣班体验课", "A trial hobby class"),
    preset("实现一个小愿望", "Grant a small wish"),
    preset("神秘大奖", "Mystery grand prize"),
    // ===== 预设奖励描述（27 条）=====
    preset("看一集喜欢的动画片", "Watch one episode of a favorite cartoon"),
    preset("玩30分钟喜欢的游戏", "Play a favorite game for 30 minutes"),
    preset("畅享60分钟游戏时间", "Enjoy 60 minutes of game time"),
    preset("和家人玩一次桌游", "Play a board game with the family once"),
    preset("影院或家庭影院看一场电影", "Watch a movie at the cinema or at home"),
    preset("买一份喜欢的零食小吃", "Buy a favorite snack"),
    preset("买一个冰淇淋或小蛋糕", "Buy an ice cream or a small cake"),
    preset("自己选一家店点外卖", "Pick a restaurant and order takeout myself"),
    preset("选一家喜欢的餐厅吃大餐", "Pick a favorite restaurant for a big meal"),
    preset("出去骑自行车兜风", "Go out for a bike ride"),
    preset("去图书馆或书店逛一逛", "Browse the library or bookstore"),
    preset("去公园或游乐场玩半天", "Spend half a day at the park or playground"),
    preset("和家人一次周末短途旅行", "A short weekend trip with the family"),
    preset("可以免除一次家务", "Get out of one chore"),
    preset("决定今晚吃什么", "Decide what's for dinner tonight"),
    preset("可以晚睡30分钟", "Stay up 30 minutes later"),
    preset("可以晚睡1小时", "Stay up 1 hour later"),
    preset("决定一天的家庭活动安排", "Plan the family's day of activities"),
    preset("集齐一套喜欢的卡片", "Collect a full set of favorite cards"),
    preset("去书店选一本喜欢的课外书", "Pick a favorite book at the bookstore"),
    preset("买一个喜欢的文具或小玩具", "Buy a favorite stationery item or small toy"),
    preset("预算内买一件喜欢的衣服", "Buy a piece of clothing I like within budget"),
    preset("和好朋友视频通话30分钟", "Video call a good friend for 30 minutes"),
    preset("邀请好朋友来家里玩", "Invite a good friend over to play"),
    preset("参加一次兴趣班体验课", "Attend one trial hobby class"),
    preset("实现一个合理的小愿望", "Grant one reasonable small wish"),
    preset("爸爸妈妈准备的神秘大奖", "A mystery grand prize prepared by Mom and Dad"),
    // ===== 奖励分类（7 条；"其他"同时是 RewardEntity 默认值与错题类型，共享一个 key）=====
    preset("娱乐", "Entertainment"),
    preset("美食", "Food"),
    preset("出行", "Outings"),
    preset("特权", "Privileges"),
    preset("实物", "Items"),
    preset("社交", "Social"),
    preset("其他", "Other"), // 与错题错误类型共享 key
    // ===== 错题错误类型（6 条；"其他"已在分类组，此处 5 条；MistakeEntity.kt:19）=====
    preset("粗心马虎", "Careless mistakes"),
    preset("概念不清", "Unclear concepts"),
    preset("方法不会", "Unknown method"),
    preset("审题错误", "Misread the question"),
    preset("计算错误", "Calculation errors"),
    // ===== 难度标签（3 条；TaskEntity.difficultyName()，阶段 1 §11 既定译名）=====
    preset("普通", "Normal"),
    preset("进阶", "Advanced"),
    preset("挑战", "Challenge"),
    // ===== 设备名回退（3 条；FamilySyncHelper.kt:72/:82/:107/:190，渲染侧映射，canonical 写入不动）=====
    preset("家长", "Parent"),
    preset("家长设备", "Parent device"),
    preset("孩子设备", "Child device"),
    // ===== 固定诊断文案（7 条；FamilySyncHelper.kt:140/:226 + BackupHelper.kt:60/:82/:127/:132/:133）=====
    preset("不是家长控制文件", "Not a parent control file"),
    preset("不是孩子进度文件", "Not a child progress file"),
    preset("数据库文件尚未创建，暂时无法备份", "The database file hasn't been created yet, so backup is unavailable"),
    preset("没有找到备份文件", "No backup file found"),
    preset("未选择文件", "No file selected"),
    preset("不是有效的备份文件", "Not a valid backup file"),
    preset("备份内容损坏", "The backup content is corrupted"),
    // ===== W5 页面内预设目录（16 条新增：日常目标 5 + 复盘标签 6 + 日程示例 5）=====
    // 阅读30分钟 / 英语听说训练 / 整理笔记 与既有任务标题共享 canonical，不重复收录
    // （同 key 二次 preset 在 mapOf 末位覆盖，会静默改写既有页面英文——去重裁决）。
    // 这些 canonical 会被落库（日程示例写入 daily_schedules；复盘标签写入偏好）
    // 或进入积分账本 description（每日目标），渲染/发通知时才本地化（Ruling 25）。
    preset("复习错题", "Review mistakes"),
    preset("专注2个番茄钟", "Focus for 2 Pomodoros"),
    preset("完成作业", "Finish homework"),
    preset("预习新课", "Preview the new lesson"),
    preset("体育锻炼", "Exercise"),
    preset("今天很专注", "Focused today"),
    preset("完成了难题", "Solved a hard problem"),
    preset("复习了错题", "Reviewed mistakes"),
    preset("有点分心", "A bit distracted"),
    preset("时间不够用", "Not enough time"),
    preset("明天会更好", "Tomorrow will be better"),
    preset("起床啦", "Time to get up"),
    preset("吃早餐", "Breakfast"),
    preset("开始写作业", "Homework time"),
    preset("阅读时间", "Reading time"),
    preset("准备睡觉", "Getting ready for bed"),
    // ===== W6 页面内预设目录（示例心愿 3 条：name + description 共 6 值）=====
    // 这些 canonical 会被 addGoal 原样落库（ParentWishGoalManageScreen 的一键示例），
    // 渲染时包 localizedPresetText；家长自建未命中原样（Ruling 25）。
    preset("一个小玩具", "A small toy"),
    preset("存够积分换一个喜欢的小玩具", "Save up for a small toy you like"),
    preset("一本新书", "A new book"),
    preset("去书店选一本喜欢的书", "Pick a favorite book at the bookstore"),
    preset("一台新平板", "A new tablet"),
    preset("存很久的大目标，加油！", "A big long-term goal — keep going!"),
    // ===== W8 学科名（4 条；引擎 canonical 学科串的渲染取词）=====
    // 数学/语文/英语/科学 此前只在 MistakeStrings 里作为页面直读字段存在；
    // W8 三个引擎把 canonical 学科串包 localizedPresetText 取词，需要注册表命中（"其他"复用奖励分类组）。
    preset("数学", "Math"),
    preset("语文", "Chinese"),
    preset("英语", "English"),
    preset("科学", "Science"),
)

/**
 * 渲染点唯一入口：命中按语言取词，未命中原样。ZH 命中返回 zh（==key==输入），
 * 所以 ZH 态对任何输入恒等于原串——家长改过的标题在两种语言下都显示存储值。
 */
fun localizedPresetText(raw: String, lang: DisplayLanguage): String =
    presetTextRegistry[raw]?.let { if (lang == DisplayLanguage.EN) it.en else it.zh } ?: raw

/**
 * 成就文案：按 AchievementDef.type（稳定业务键）映射，不按标题字节——
 * AchievementManager.kt:63 把 title/description 写库，标题改字不得影响映射（spec §4）。
 */
data class AchievementCopy(val title: String, val description: String)

val achievementCopy: Map<DisplayLanguage, Map<String, AchievementCopy>> = mapOf(
    DisplayLanguage.ZH to mapOf(
        "first_checkin" to AchievementCopy("启航", "首次完成打卡"),
        "streak_3" to AchievementCopy("三日不辍", "连续打卡3天"),
        "streak_7" to AchievementCopy("周冠军", "连续打卡7天"),
        "checkin_100" to AchievementCopy("百日积累", "累计打卡100次"),
        "perfect_day" to AchievementCopy("百分学霸", "单日完成全部任务"),
        "mistake_50" to AchievementCopy("错题克星", "掌握50道错题"),
        "focus_600" to AchievementCopy("专注达人", "累计专注10小时"),
        "habit_complete" to AchievementCopy("习惯达人", "完成一个21天挑战"),
        "mistake_100" to AchievementCopy("错题大师", "掌握100道错题"),
        "focus_1800" to AchievementCopy("专注大师", "累计专注30小时"),
        "review_30" to AchievementCopy("复习达人", "累计复习错题30次"),
        "variation_5" to AchievementCopy("举一反三", "完成5次举一反三思考"),
        "training_10" to AchievementCopy("训练达人", "完成10次专注力训练"),
    ),
    DisplayLanguage.EN to mapOf(
        "first_checkin" to AchievementCopy("Blast Off", "Complete your first check-in"),
        "streak_3" to AchievementCopy("Three-Day Streak", "Check in 3 days in a row"),
        "streak_7" to AchievementCopy("Weekly Champion", "Check in 7 days in a row"),
        "checkin_100" to AchievementCopy("100 Days Strong", "Check in 100 times in total"),
        "perfect_day" to AchievementCopy("Perfect Day", "Finish every task in a single day"),
        "mistake_50" to AchievementCopy("Mistake Buster", "Master 50 mistakes"),
        "focus_600" to AchievementCopy("Focus Pro", "Focus for 10 hours in total"),
        "habit_complete" to AchievementCopy("Habit Hero", "Complete a 21-day challenge"),
        "mistake_100" to AchievementCopy("Mistake Master", "Master 100 mistakes"),
        "focus_1800" to AchievementCopy("Focus Master", "Focus for 30 hours in total"),
        "review_30" to AchievementCopy("Review Pro", "Review mistakes 30 times in total"),
        "variation_5" to AchievementCopy("Variation", "Complete 5 variation reflections"),
        "training_10" to AchievementCopy("Training Pro", "Complete 10 focus training sessions"),
    ),
)

/** 宠物场景名：按 PetScene.id（PET_OWNED_SCENES 持久化的稳定键）映射。 */
val petSceneCopy: Map<DisplayLanguage, Map<String, String>> = mapOf(
    DisplayLanguage.ZH to mapOf(
        "home" to "我的小窝",
        "forest" to "森林小屋",
        "beach" to "阳光海滩",
        "candy" to "糖果乐园",
        "castle" to "梦幻城堡",
        "space" to "浩瀚星空",
        "rainbow" to "彩虹天堂",
    ),
    DisplayLanguage.EN to mapOf(
        "home" to "My Little Home",
        "forest" to "Forest Cabin",
        "beach" to "Sunny Beach",
        "candy" to "Candy Land",
        "castle" to "Dream Castle",
        "space" to "Starry Space",
        "rainbow" to "Rainbow Paradise",
    ),
)

/**
 * 宠物成长阶段名（键控表，索引对齐 ui.pet.petStages）：petStages 保持 canonical 目录不动
 * （显示目录、不入库）；渲染点用 [localizedPetStageName]。覆盖性由 PresetRegistryTest 钉住。
 */
val petStageCopy: Map<DisplayLanguage, List<String>> = mapOf(
    DisplayLanguage.ZH to listOf("宠物蛋", "刚破壳", "小黄鸡", "小猫咪", "小狐狸", "小狮子", "独角兽", "小神龙"),
    DisplayLanguage.EN to listOf("Pet Egg", "Just Hatched", "Little Chick", "Little Cat", "Little Fox", "Little Lion", "Unicorn", "Little Dragon"),
)

/** 阶段名按索引取词；越界回落索引 0（与 petStages 的默认阶段一致） */
fun localizedPetStageName(index: Int, lang: DisplayLanguage): String =
    petStageCopy[lang]?.getOrNull(index) ?: petStageCopy[lang]?.firstOrNull() ?: ""

/**
 * 等级称号（键控表，索引对齐 LevelSystem 的 1..10 级）。
 *
 * 与 [petStageCopy] 同一模式：`LevelSystem.levels` 是带标题的 canonical 目录（private），
 * 本表只在渲染点（LevelBadge）取词，不参与任何存储/业务比较。
 * ZH 必须逐字等于 `LevelSystem.levels` 的标题——索引错位不会编译失败，只会安静地显示错级称号，
 * 因此由 `W8CopyTest.levelAndTreeRegistryCoveragePinned` 全量钉住（LevelSystem 目录 private，
 * 无法 import 对账，故 ZH 手打、首尾两级用 getLevelInfo 活体对账）。
 */
val levelTitleCopy: Map<DisplayLanguage, List<String>> = mapOf(
    DisplayLanguage.ZH to listOf("学习新手", "勤奋小学徒", "知识探索者", "学习达人", "学霸少年", "知识王者", "学习之星", "超级学霸", "传奇学者", "学神"),
    DisplayLanguage.EN to listOf("Learning Novice", "Diligent Apprentice", "Knowledge Explorer", "Study Expert", "Star Student", "Knowledge King", "Study Star", "Super Scholar", "Legendary Scholar", "Study Legend"),
)

/** 等级称号按等级取词（1 级起）；越界回落索引 0（1 级称号） */
fun localizedLevelTitle(level: Int, lang: DisplayLanguage): String =
    levelTitleCopy[lang]?.getOrNull(level - 1) ?: levelTitleCopy[lang]?.firstOrNull() ?: ""

/**
 * 学习树成长阶段名（键控表，6 阶段）。
 *
 * `LearningTree.getStageName` 保持原样（canvas 语义与其它调用者的 canonical 出处），
 * Canvas 的显示文本改用 [localizedTreeStageName]；ZH 与 getStageName 逐字相等由
 * `W8CopyTest.levelAndTreeRegistryCoveragePinned` 六阶段活体对账。
 */
val treeStageCopy: Map<DisplayLanguage, List<String>> = mapOf(
    DisplayLanguage.ZH to listOf("种子", "发芽", "幼苗", "大树", "开花", "结果"),
    DisplayLanguage.EN to listOf("Seed", "Sprout", "Sapling", "Big Tree", "Blooming", "Fruiting"),
)

/** 树阶段名按阶段取词（0 起）；越界回落索引 0（种子，与 getStageName 的 else 分支一致） */
fun localizedTreeStageName(stage: Int, lang: DisplayLanguage): String =
    treeStageCopy[lang]?.getOrNull(stage) ?: treeStageCopy[lang]?.firstOrNull() ?: ""