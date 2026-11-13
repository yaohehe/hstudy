package com.hstudy.app.i18n

import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.RewardExchangeEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.difficultyName
import com.hstudy.app.data.local.entity.mistakeErrorTypes
import com.hstudy.app.data.repository.RewardRepository
import com.hstudy.app.data.repository.TaskRepository
import com.hstudy.app.ui.common.AchievementManager
import com.hstudy.app.ui.pet.petScenes
import com.hstudy.app.ui.pet.petStages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 注册表交叉校验（spec §10.2/10.3）：key 集合与活体种子 canonical **互为子集**（一条集合相等断言），
 * 外加回退语义与漏译守卫。种子采集全部走真实源码路径：
 * - getPresets()/getSingleTaskSuggestions() 是纯函数，不碰 DAO（UnusedTaskDao 全抛）；
 * - initDefaultRewards() 的 defaults 是函数内局部变量，用 CapturingRewardDao 接住 insertReward；
 * - 难度标签/默认分类直接调 difficultyName() / RewardEntity 默认值读出来，不手抄；
 * - FamilySyncHelper/BackupHelper 的字面量是私有代码路径，无法程序化读取：按 2026-11-10 源码
 *   逐字钉住（行号见断言内注释）。本阶段 data/ 零改动；未来若获准改这些字面量，
 *   必须同步更新此处与注册表——这是该组唯一的漂移守卫，诚实声明其上限。
 * - W5 起的页面内预设目录组由本测试按源码字面量逐字钉住（与 deviceFallbacks/diagnostics 同法）。
 */
class PresetRegistryTest {

    private fun taskSeedCanonicals(): Set<String> {
        val repo = TaskRepository(UnusedTaskDao())
        val out = mutableSetOf<String>()
        for (template in repo.getPresets()) {
            out += template.name
            out += template.description
            out += template.ageRange
            for (task in template.tasks) {
                out += task.title
                out += task.description
            }
        }
        for (task in repo.getSingleTaskSuggestions()) {
            out += task.title
            out += task.description
        }
        return out
    }

    private suspend fun rewardSeedCanonicals(): Pair<Set<String>, Set<String>> {
        val dao = CapturingRewardDao()
        RewardRepository(dao).initDefaultRewards()
        val texts = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        for (reward in dao.inserted) {
            texts += reward.name
            texts += reward.description
            categories += reward.category
        }
        return texts to categories
    }

    @Test
    fun registryKeysEqualSeedCanonicalsExactly() = runTest {
        val (rewardTexts, rewardCategories) = rewardSeedCanonicals()
        val difficultyLabels = (1..3).map { level ->
            TaskEntity(title = "probe", category = TaskCategory.BASIC, points = 0, difficulty = level).difficultyName()
        }.toSet()
        val defaultCategory = RewardEntity(name = "probe", pointsCost = 0).category
        val deviceFallbacks = setOf("家长", "家长设备", "孩子设备") // FamilySyncHelper.kt:72/:82/:107/:190
        val diagnostics = setOf( // FamilySyncHelper.kt:140/:226 + BackupHelper.kt:60/:82/:127/:132/:133
            "不是家长控制文件", "不是孩子进度文件",
            "数据库文件尚未创建，暂时无法备份", "没有找到备份文件", "未选择文件",
            "不是有效的备份文件", "备份内容损坏",
        )
        val w5PageLocalPresets = setOf( // PresetRegistry.kt「W5 页面内预设目录」组 16 条新增（目标 5 + 标签 6 + 日程示例 5）
            // 阅读30分钟 / 英语听说训练 / 整理笔记 与任务标题种子组共享 canonical，不计入本组
            "复习错题", "专注2个番茄钟", "完成作业", "预习新课", "体育锻炼",
            "今天很专注", "完成了难题", "复习了错题", "有点分心", "时间不够用", "明天会更好",
            "起床啦", "吃早餐", "开始写作业", "阅读时间", "准备睡觉",
        )
        val w6WishSamples = setOf( // PresetRegistry.kt「W6 页面内预设目录」组（示例心愿 3 条 × name+description）
            "一个小玩具", "存够积分换一个喜欢的小玩具",
            "一本新书", "去书店选一本喜欢的书",
            "一台新平板", "存很久的大目标，加油！",
        )
        val w8SubjectNames = setOf( // PresetRegistry.kt「W8 学科名」组（引擎 canonical 学科串取词；"其他"已在奖励分类组）
            "数学", "语文", "英语", "科学",
        )
        val w9FreeStudy = setOf( // PresetRegistry.kt「W9 专注目标」组（focus_sessions.taskTitle 的 canonical 存储值）
            "自由学习",
        )
        val expected = taskSeedCanonicals() + rewardTexts + rewardCategories + defaultCategory +
            mistakeErrorTypes.toSet() + difficultyLabels + deviceFallbacks + diagnostics + w5PageLocalPresets +
            w6WishSamples + w8SubjectNames + w9FreeStudy // W5/W6/W8/W9 页面内预设组来自 PresetRegistry.kt 对应分组
        val missing = expected - presetTextRegistry.keys
        val extra = presetTextRegistry.keys - expected
        assertTrue(
            missing.isEmpty() && extra.isEmpty(),
            "注册表与种子 canonical 漂移：缺 $missing，多 $extra",
        )
    }

    @Test
    fun zhValueAlwaysEqualsCanonicalKey() {
        presetTextRegistry.forEach { (canonical, copy) ->
            assertEquals(canonical, copy.zh, "zh 值必须等于 canonical key：$canonical")
        }
    }

    @Test
    fun everyEntryHasDistinctEnglishTranslation() {
        presetTextRegistry.forEach { (canonical, copy) ->
            assertNotEquals(copy.zh, copy.en, "疑似漏译：$canonical")
        }
    }

    @Test
    fun englishValuesContainNoChinese() {
        val leftovers = presetTextRegistry.values.flatMap { it.en.toList() }.filter { it in '一'..'鿿' }
        assertTrue(leftovers.isEmpty(), "英文译文残留汉字：${leftovers.distinct()}")
    }

    @Test
    fun unknownTextFallsThroughUnchanged() {
        val userContent = listOf("家长自定义标题", "完成任务获得积分", "", "My own reward")
        for (raw in userContent) {
            assertEquals(raw, localizedPresetText(raw, DisplayLanguage.EN), "EN 未命中必须原样：$raw")
            assertEquals(raw, localizedPresetText(raw, DisplayLanguage.ZH), "ZH 未命中必须原样：$raw")
        }
    }

    @Test
    fun knownCanonicalResolvesPerLanguage() {
        assertEquals("完成当天作业", localizedPresetText("完成当天作业", DisplayLanguage.ZH))
        assertEquals("Finish today's homework", localizedPresetText("完成当天作业", DisplayLanguage.EN))
        // 跨组共享 key（奖励分类/错题类型同为"其他"）只有一个译文
        assertEquals("Other", localizedPresetText("其他", DisplayLanguage.EN))
        // 标题与描述共享 key（"体育锻炼30分钟"）同一译文两用
        assertEquals("Exercise for 30 minutes", localizedPresetText("体育锻炼30分钟", DisplayLanguage.EN))
    }

    @Test
    fun achievementCopyCoversEveryDefTypeInBothLanguages() {
        val types = AchievementManager.allAchievements.map { it.type }.toSet()
        assertEquals(types, achievementCopy[DisplayLanguage.ZH]!!.keys, "ZH 成就键集与定义漂移")
        assertEquals(types, achievementCopy[DisplayLanguage.EN]!!.keys, "EN 成就键集与定义漂移")
    }

    @Test
    fun achievementZhCopyMatchesDefinitionExactly() {
        for (def in AchievementManager.allAchievements) {
            val copy = achievementCopy[DisplayLanguage.ZH]!![def.type]!!
            assertEquals(def.title, copy.title, "ZH 成就标题漂移：${def.type}")
            assertEquals(def.description, copy.description, "ZH 成就描述漂移：${def.type}")
        }
    }

    @Test
    fun achievementEnglishCopyIsTranslated() {
        val zh = achievementCopy[DisplayLanguage.ZH]!!
        val en = achievementCopy[DisplayLanguage.EN]!!
        for (type in zh.keys) {
            assertNotEquals(zh[type]!!.title, en[type]!!.title, "成就标题疑似漏译：$type")
            assertNotEquals(zh[type]!!.description, en[type]!!.description, "成就描述疑似漏译：$type")
        }
        val enChars = en.values.flatMap { listOf(it.title, it.description) }.flatMap { it.toList() }
        assertTrue(enChars.none { it in '一'..'鿿' }, "英文成就文案残留汉字")
    }

    @Test
    fun achievementEnglishNamesStayConsistentWithStageOnePins() {
        // 阶段 1 taskListInterpolatedCopyPinsBothLanguages 已把这两个名字钉进插值断言
        // （StringsCopyTest.kt:451/498）；注册表必须沿用，否则同一成就两处英文漂移。
        assertEquals("Blast Off", achievementCopy[DisplayLanguage.EN]!!["first_checkin"]!!.title)
        assertEquals("Weekly Champion", achievementCopy[DisplayLanguage.EN]!!["streak_7"]!!.title)
    }

    @Test
    fun petSceneCopyCoversEverySceneIdInBothLanguages() {
        val ids = petScenes.map { it.id }.toSet()
        assertEquals(ids, petSceneCopy[DisplayLanguage.ZH]!!.keys, "ZH 场景键集与定义漂移")
        assertEquals(ids, petSceneCopy[DisplayLanguage.EN]!!.keys, "EN 场景键集与定义漂移")
    }

    @Test
    fun petSceneZhCopyMatchesSceneNameExactly() {
        for (scene in petScenes) {
            assertEquals(scene.name, petSceneCopy[DisplayLanguage.ZH]!![scene.id], "ZH 场景名漂移：${scene.id}")
        }
    }

    @Test
    fun petSceneEnglishCopyIsTranslated() {
        val zh = petSceneCopy[DisplayLanguage.ZH]!!
        val en = petSceneCopy[DisplayLanguage.EN]!!
        for (id in zh.keys) {
            assertNotEquals(zh[id], en[id], "场景名疑似漏译：$id")
        }
        assertTrue(en.values.flatMap { it.toList() }.none { it in '一'..'鿿' }, "英文场景名残留汉字")
        assertEquals("My Little Home", en["home"])
    }

    @Test
    fun petStageCopyCoversAndTranslatesAllStages() {
        assertEquals(petStages.size, petStageCopy[DisplayLanguage.ZH]!!.size)
        assertEquals(petStages.size, petStageCopy[DisplayLanguage.EN]!!.size)
        petStages.forEachIndexed { i, stage ->
            assertEquals(stage.name, petStageCopy[DisplayLanguage.ZH]!![i], "ZH 阶段名漂移：$i")
            assertEquals(stage.name, localizedPetStageName(i, DisplayLanguage.ZH))
            assertNotEquals(stage.name, localizedPetStageName(i, DisplayLanguage.EN), "疑似漏译：$i")
        }
        // 越界回落 canonical 索引 0
        assertEquals(petStageCopy[DisplayLanguage.EN]!![0], localizedPetStageName(99, DisplayLanguage.EN))
        // 漏译死角：assertNotEquals(canonical, EN) 只证明"两语不同"，写错的中英混排/漏改仍能通过；
        // 整表 CJK 扫描（同上方 petSceneEnglishCopyIsTranslated）+ EN 全量列表 pin 一起兜住。
        assertTrue(petStageCopy[DisplayLanguage.EN]!!.flatMap { it.toList() }.none { it in '一'..'鿿' }, "英文阶段名残留汉字")
        val expectedEn = listOf("Pet Egg", "Just Hatched", "Little Chick", "Little Cat", "Little Fox", "Little Lion", "Unicorn", "Little Dragon")
        assertEquals(expectedEn, petStageCopy[DisplayLanguage.EN]!!)
        assertEquals(petStageCopy[DisplayLanguage.EN]!!.size, petStageCopy[DisplayLanguage.EN]!!.toSet().size, "EN 阶段名重复")
    }

    @Test
    fun w5PageLocalPresetsResolvePerLanguage() {
        // W5 页面内预设目录：日常目标 8 + 复盘标签 6 + 日程示例 5（canonical 会被落库/进账本）
        // 其中「阅读30分钟」「英语听说训练」「整理笔记」与既有任务标题共享 canonical：
        // 注册表不重复收录，英文必须沿用任务标题种子组既有译文（去重裁决，防末位覆盖改写存量页面）。
        val sharedWithTaskTitles = mapOf(
            "阅读30分钟" to "Read for 30 minutes",
            "英语听说训练" to "English listening and speaking practice",
            "整理笔记" to "Organize class notes",
        )
        val presets = mapOf(
            "复习错题" to "Review mistakes",
            "专注2个番茄钟" to "Focus for 2 Pomodoros",
            "完成作业" to "Finish homework",
            "预习新课" to "Preview the new lesson",
            "体育锻炼" to "Exercise",
            "今天很专注" to "Focused today",
            "完成了难题" to "Solved a hard problem",
            "复习了错题" to "Reviewed mistakes",
            "有点分心" to "A bit distracted",
            "时间不够用" to "Not enough time",
            "明天会更好" to "Tomorrow will be better",
            "起床啦" to "Time to get up",
            "吃早餐" to "Breakfast",
            "开始写作业" to "Homework time",
            "阅读时间" to "Reading time",
            "准备睡觉" to "Getting ready for bed",
        )
        assertEquals(19, presets.size + sharedWithTaskTitles.size, "W5 页面内预设 canonical 共 19 条（16 新增 + 3 共享）")
        for ((zh, en) in sharedWithTaskTitles + presets) {
            assertEquals(zh, localizedPresetText(zh, DisplayLanguage.ZH), "ZH 必须恒等：$zh")
            assertEquals(en, localizedPresetText(zh, DisplayLanguage.EN), "EN 译文漂移：$zh")
        }
    }

    @Test
    fun w6WishSamplesResolvePerLanguage() {
        // W6：3 个示例心愿的 name+description 是 canonical 落库值（addGoal 原样写入）
        val samples = mapOf(
            "一个小玩具" to "A small toy",
            "存够积分换一个喜欢的小玩具" to "Save up for a small toy you like",
            "一本新书" to "A new book",
            "去书店选一本喜欢的书" to "Pick a favorite book at the bookstore",
            "一台新平板" to "A new tablet",
            "存很久的大目标，加油！" to "A big long-term goal — keep going!",
        )
        for ((zh, en) in samples) {
            assertEquals(zh, localizedPresetText(zh, DisplayLanguage.ZH), "ZH 必须恒等：$zh")
            assertEquals(en, localizedPresetText(zh, DisplayLanguage.EN), "EN 译文漂移：$zh")
        }
    }
}

/** getPresets()/getSingleTaskSuggestions() 是纯函数，不触 DAO；任何调用都是测试写错了。 */
private class UnusedTaskDao : TaskDao {
    override fun getAllActiveTasks(): Flow<List<TaskEntity>> = throw UnsupportedOperationException()
    override suspend fun getActiveTaskCount(): Int = throw UnsupportedOperationException()
    override suspend fun getAllActiveTasksSnapshot(): List<TaskEntity> = throw UnsupportedOperationException()
    override fun getTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>> = throw UnsupportedOperationException()
    override suspend fun getTaskById(id: Long): TaskEntity? = throw UnsupportedOperationException()
    override suspend fun insert(task: TaskEntity): Long = throw UnsupportedOperationException()
    override suspend fun update(task: TaskEntity) = throw UnsupportedOperationException()
    override suspend fun softDelete(id: Long) = throw UnsupportedOperationException()
    override suspend fun delete(task: TaskEntity) = throw UnsupportedOperationException()
}

/** 接住 initDefaultRewards() 的 27 条写入（defaults 是函数内局部变量，只能从 DAO 侧捕获）。 */
private class CapturingRewardDao : RewardDao {
    val inserted = mutableListOf<RewardEntity>()
    override suspend fun insertReward(reward: RewardEntity): Long {
        inserted += reward
        return inserted.size.toLong()
    }
    override suspend fun getActiveRewardCount(): Int = inserted.size
    override fun getAllActiveRewards(): Flow<List<RewardEntity>> = throw UnsupportedOperationException()
    override suspend fun getRewardById(id: Long): RewardEntity? = throw UnsupportedOperationException()
    override suspend fun updateReward(reward: RewardEntity) = throw UnsupportedOperationException()
    override suspend fun softDeleteReward(id: Long) = throw UnsupportedOperationException()
    override suspend fun insertExchange(exchange: RewardExchangeEntity): Long = throw UnsupportedOperationException()
    override fun getAllExchanges(): Flow<List<RewardExchangeEntity>> = throw UnsupportedOperationException()
    override fun getExchangesByReward(rewardId: Long): Flow<List<RewardExchangeEntity>> = throw UnsupportedOperationException()
}