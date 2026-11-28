package com.hstudy.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hstudy.app.data.focus.PomodoroSessionPhase
import com.hstudy.app.data.focus.PomodoroSessionState
import com.hstudy.app.data.repository.todayString
import com.hstudy.app.i18n.AppLanguage
import com.hstudy.app.i18n.CanonicalFocusText
import com.hstudy.app.platform.Platform
import com.hstudy.app.security.ParentPasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import kotlin.random.Random

/** 调用方读取到的 Pomodoro 会话快照；三个字段必须全部匹配才允许转换。 */
data class PomodoroWriteExpectation(
    val startEpochMs: Long,
    val settled: Boolean,
    val version: Long,
)

const val MEMORY_TRAINING_SCHEMA_VERSION = 1

/** 数字记忆训练的持久化状态；schema 不匹配时读取方丢弃，避免旧格式误解释。 */
data class MemoryTrainingState(
    val schemaVersion: Int = MEMORY_TRAINING_SCHEMA_VERSION,
    val phase: Phase = Phase.IDLE,
    val level: Int = 3,
    val sequence: String = "",
    val bestLevel: Int = 0,
    val lastCorrect: Boolean = false,
    val showUntilEpochMs: Long = 0L,
) {
    enum class Phase { IDLE, SHOWING, INPUT, RESULT }

    /** 只结束触发定时器的那一轮 SHOWING；陈旧定时器不能改写新序列。 */
    fun afterShow(expectedSequence: String, expectedShowUntilEpochMs: Long): MemoryTrainingState =
        if (phase == Phase.SHOWING && sequence == expectedSequence && showUntilEpochMs == expectedShowUntilEpochMs) {
            copy(phase = Phase.INPUT)
        } else {
            this
        }

    /** 起新轮前校验调用方所见的 phase/sequence，拒绝重复点击或陈旧页面快照。 */
    fun startRound(
        sequence: String,
        level: Int,
        showUntilEpochMs: Long,
        expectedPhase: Phase,
        expectedSequence: String,
    ): MemoryTrainingState =
        if (phase == expectedPhase && this.sequence == expectedSequence) {
            copy(
                phase = Phase.SHOWING,
                level = level,
                sequence = sequence,
                lastCorrect = false,
                showUntilEpochMs = showUntilEpochMs,
            )
        } else {
            this
        }

    /** 仅接受仍在 INPUT 且序列未变的答案；同一轮重复提交不会再次更新结果。 */
    fun answer(input: String, expectedSequence: String): MemoryTrainingState =
        if (phase == Phase.INPUT && sequence == expectedSequence) {
            val correct = input == sequence
            copy(
                phase = Phase.RESULT,
                bestLevel = if (correct) maxOf(bestLevel, level) else bestLevel,
                lastCorrect = correct,
            )
        } else {
            this
        }
}

/**
 * DataStore 必须每文件单实例：原 Android 版通过 Context.preferencesDataStore(name = "hstudy_prefs")
 * 委托保证进程内单例，文件位于 <dataDir>/datastore/hstudy_prefs.preferences_pb（dataStoreFile 基于 dataDir）。
 * 这里保持完全相同的文件名与相对路径，确保老 Android 用户的偏好数据无缝存活。
 */
private val dataStoreInstance: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { Platform.dataDir.toPath() / "datastore" / "hstudy_prefs.preferences_pb" }
    )
}

class UserPreferences(private val dataStore: DataStore<Preferences> = dataStoreInstance) {

    /** Test-only raw credential snapshot; null fields mean the corresponding key is absent. */
    internal data class ParentPasswordRawSnapshot(
        val value: String?,
        val format: String?,
    )

    companion object {
        val ROLE_KEY = stringPreferencesKey("current_role") // "parent" or "child"
        val LANGUAGE_KEY = stringPreferencesKey("app_language") // "system" | "zh" | "en"
        val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")
        val PARENT_PASSWORD = stringPreferencesKey("parent_password") // 仅存版本化 PBKDF2 编码值；旧明文首次验证成功后原子替换
        val PARENT_PASSWORD_FORMAT = stringPreferencesKey("parent_password_format") // ENCODED_V1 | LEGACY；缺省按 LEGACY
        internal const val PARENT_PASSWORD_FORMAT_ENCODED_V1 = "ENCODED_V1"
        internal const val PARENT_PASSWORD_FORMAT_LEGACY = "LEGACY"
        val PAIR_CODE = stringPreferencesKey("pair_code")            // 设备配对码
        val DEVICE_NAME = stringPreferencesKey("device_name")        // 本设备名称/角色
        val PAIRED_DEVICE = stringPreferencesKey("paired_device")    // 已配对设备名称
        val LAST_LUCKY_DRAW = stringPreferencesKey("last_lucky_draw") // 上次抽奖日期
        val PET_FULLNESS = intPreferencesKey("pet_fullness")           // 宠物饱食度 0-100
        val PET_MOOD = intPreferencesKey("pet_mood")                   // 宠物心情 0-100
        val PET_LAST_CARE_DATE = stringPreferencesKey("pet_last_care_date") // 上次照料日期
        val STREAK_MILESTONES_CLAIMED = stringPreferencesKey("streak_milestones_claimed") // 已领取的连续打卡里程碑（逗号分隔天数）
        val PET_OWNED_SCENES = stringPreferencesKey("pet_owned_scenes")   // 已购买的宠物场景（逗号分隔ID）
        val PET_EQUIPPED_SCENE = stringPreferencesKey("pet_equipped_scene") // 当前装扮的宠物场景ID
        val WEEKLY_CHALLENGE_CLAIMED_WEEK = stringPreferencesKey("weekly_challenge_claimed_week") // 已领取每周挑战的周起始日
        val DAILY_TARGET_DATE = stringPreferencesKey("daily_target_date")   // 每日学习目标设定日期
        val DAILY_TARGETS = stringPreferencesKey("daily_targets")           // 当日选定的学习目标（逗号分隔ID）
        val DAILY_TARGETS_DONE = stringPreferencesKey("daily_targets_done") // 当日已完成的学习目标（逗号分隔ID）
        val DAILY_TARGET_BONUS_DATE = stringPreferencesKey("daily_target_bonus_date") // 每日目标全部达成奖励的领取日期
        val DAILY_REFLECTION_DATE = stringPreferencesKey("daily_reflection_date")   // 每日复盘日期
        val DAILY_REFLECTION_STARS = intPreferencesKey("daily_reflection_stars")     // 每日复盘自评星级(1-5)
        val DAILY_REFLECTION_TAGS = stringPreferencesKey("daily_reflection_tags")    // 每日复盘标签（逗号分隔）
        val REVIEW_REMINDER_ENABLED = booleanPreferencesKey("review_reminder_enabled") // 错题复习每日提醒开关
        val REVIEW_REMINDER_HOUR = intPreferencesKey("review_reminder_hour")           // 错题复习提醒小时（0-23，默认19）
        val DAILY_EXCELLENT_BONUS_DATE = stringPreferencesKey("daily_excellent_bonus_date") // 学习表现优秀奖励领取日期
        val FOCUS_TRAINING_BEST_MS = longPreferencesKey("focus_training_best_ms") // 舒尔特方格专注训练个人最佳用时（毫秒，0=无记录）
        val REVIEW_STREAK_MILESTONES_CLAIMED = stringPreferencesKey("review_streak_milestones_claimed") // 已领取的连续复习里程碑（逗号分隔天数）
        val WEEKLY_REPORT_ENABLED = booleanPreferencesKey("weekly_report_enabled") // 家长每周学习周报推送开关
        val FOCUS_TRAINING_COUNT = intPreferencesKey("focus_training_count") // 舒尔特方格专注训练累计完成次数
        val POMODORO_TODAY_COUNT = stringPreferencesKey("pomodoro_today_count") // 今日番茄钟完成数（格式："日期:数量"）
        val POMODORO_SKIP_BREATHING = booleanPreferencesKey("pomodoro_skip_breathing") // 是否跳过专注前呼吸引导
        val LAST_SYNC_DATE = stringPreferencesKey("last_sync_date") // 上次设备间数据同步日期（yyyy-MM-dd）
        val SYNC_REMINDER_ENABLED = booleanPreferencesKey("sync_reminder_enabled") // 定期同步提醒开关
        val LAST_MANDATORY_PENALTY_DATE = stringPreferencesKey("last_mandatory_penalty_date") // 已处理每日必须扣分的最后日期
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled") // 分享使用数据开关，缺省 true
        // ===== 番茄钟时间戳锚定会话（v3.3.1 D-05/07，指令 §4.2；键名沿用指令的 pomodoro_active_* 前缀） =====
        // 唯一权威状态源 = 下面这组键（跨导航/跨进程都从这里恢复，绝不存"剩余秒数"）。
        val POMODORO_SESSION_START = longPreferencesKey("pomodoro_active_start_time") // 本段起点（epoch ms）
        val POMODORO_SESSION_PLANNED_MIN = intPreferencesKey("pomodoro_active_duration_min") // 本段计划分钟数
        val POMODORO_SESSION_TARGET = stringPreferencesKey("pomodoro_active_target") // 专注目标（规范值）
        val POMODORO_SESSION_PHASE = stringPreferencesKey("pomodoro_active_phase") // FOCUS | BREAK
        val POMODORO_SESSION_PAUSED_ACCUM_MS = longPreferencesKey("pomodoro_active_paused_accum_ms") // 累计暂停毫秒
        val POMODORO_SESSION_PAUSE_STARTED_AT = longPreferencesKey("pomodoro_active_pause_start_time") // 0=未暂停
        val POMODORO_SESSION_SETTLED = booleanPreferencesKey("pomodoro_active_settled") // 结算认领已赢（先置位后入账）
        val POMODORO_SESSION_FOCUS_RECORD_ID = longPreferencesKey("pomodoro_active_focus_record_id") // 已落 focus_sessions 行 id
        val POMODORO_SESSION_SETTLE_DATE = stringPreferencesKey("pomodoro_active_settle_date") // 结算日（bizKey 日期段）
        val POMODORO_SESSION_VERSION = longPreferencesKey("pomodoro_active_version") // 单调递增会话版本
        val MEMORY_TRAINING_SCHEMA = intPreferencesKey("memory_training_schema_version")
        val MEMORY_TRAINING_PHASE = stringPreferencesKey("memory_training_phase")
        val MEMORY_TRAINING_LEVEL = intPreferencesKey("memory_training_level")
        val MEMORY_TRAINING_SEQUENCE = stringPreferencesKey("memory_training_sequence")
        val MEMORY_TRAINING_BEST_LEVEL = intPreferencesKey("memory_training_best_level")
        val MEMORY_TRAINING_LAST_CORRECT = booleanPreferencesKey("memory_training_last_correct")
        val MEMORY_TRAINING_SHOW_UNTIL = longPreferencesKey("memory_training_show_until_epoch_ms")
    }

    val currentRole: Flow<String?> = dataStore.data.map { it[ROLE_KEY] }
    val appLanguage: Flow<String?> = dataStore.data.map { it[LANGUAGE_KEY] }
    val analyticsEnabled: Flow<Boolean> = dataStore.data.map { it[ANALYTICS_ENABLED] ?: true }
    val hasOnboarded: Flow<Boolean> = dataStore.data.map { it[HAS_ONBOARDED] ?: false }
    val parentPassword: Flow<String?> = dataStore.data.map { it[PARENT_PASSWORD] }
    val hasParentPassword: Flow<Boolean> = dataStore.data.map { !it[PARENT_PASSWORD].isNullOrEmpty() }
    val pairCode: Flow<String?> = dataStore.data.map { it[PAIR_CODE] }
    val deviceName: Flow<String?> = dataStore.data.map { it[DEVICE_NAME] }
    val pairedDevice: Flow<String?> = dataStore.data.map { it[PAIRED_DEVICE] }
    val lastLuckyDraw: Flow<String?> = dataStore.data.map { it[LAST_LUCKY_DRAW] }
    val petFullness: Flow<Int> = dataStore.data.map { it[PET_FULLNESS] ?: 80 }
    val petMood: Flow<Int> = dataStore.data.map { it[PET_MOOD] ?: 80 }
    val petLastCareDate: Flow<String?> = dataStore.data.map { it[PET_LAST_CARE_DATE] }
    val claimedStreakMilestones: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[STREAK_MILESTONES_CLAIMED]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
    }
    val ownedPetScenes: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PET_OWNED_SCENES]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    val equippedPetScene: Flow<String> = dataStore.data.map { it[PET_EQUIPPED_SCENE] ?: "home" }
    val weeklyChallengeClaimedWeek: Flow<String?> = dataStore.data.map { it[WEEKLY_CHALLENGE_CLAIMED_WEEK] }
    val dailyTargetDate: Flow<String?> = dataStore.data.map { it[DAILY_TARGET_DATE] }
    val dailyTargets: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[DAILY_TARGETS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    val dailyTargetsDone: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[DAILY_TARGETS_DONE]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    val dailyTargetBonusDate: Flow<String?> = dataStore.data.map { it[DAILY_TARGET_BONUS_DATE] }
    val dailyReflectionDate: Flow<String?> = dataStore.data.map { it[DAILY_REFLECTION_DATE] }
    val dailyReflectionStars: Flow<Int> = dataStore.data.map { it[DAILY_REFLECTION_STARS] ?: 0 }
    val dailyReflectionTags: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[DAILY_REFLECTION_TAGS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    suspend fun setRole(role: String) {
        dataStore.edit { it[ROLE_KEY] = role }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[LANGUAGE_KEY] = language.toStorageValue() }
    }

    suspend fun setAnalyticsEnabled(value: Boolean) {
        dataStore.edit { it[ANALYTICS_ENABLED] = value }
    }

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[HAS_ONBOARDED] = value }
    }

    suspend fun clearRole() {
        dataStore.edit { it.remove(ROLE_KEY) }
    }

    // ===== 家长密码 =====
    suspend fun setParentPassword(password: String) {
        val encoded = withContext(Dispatchers.Default) { ParentPasswordHasher.hash(password) }
        dataStore.edit {
            it[PARENT_PASSWORD] = encoded
            it[PARENT_PASSWORD_FORMAT] = PARENT_PASSWORD_FORMAT_ENCODED_V1
        }
    }

    suspend fun clearParentPassword() {
        dataStore.edit {
            it.remove(PARENT_PASSWORD)
            it.remove(PARENT_PASSWORD_FORMAT)
        }
    }

    /**
     * 校验与旧明文迁移共用一次 DataStore.edit：读值、校验和替换明文都在同一事务内完成。
     * parent_password_format 是唯一格式判据；缺省按 LEGACY，未知标记 fail-closed。
     * PBKDF2 在 Default 线程执行，避免阻塞调用方 Main 线程或 DataStore actor。
     * 错误密码不写任何值；成功后编码值与 ENCODED_V1 标记一起原子提交。
     */
    suspend fun verifyParentPassword(input: String): Boolean {
        var verified = false
        dataStore.edit { prefs ->
            val stored = prefs[PARENT_PASSWORD]
            if (stored.isNullOrEmpty()) return@edit

            when (prefs[PARENT_PASSWORD_FORMAT]) {
                PARENT_PASSWORD_FORMAT_ENCODED_V1 -> {
                    verified = withContext(Dispatchers.Default) {
                        ParentPasswordHasher.verify(input, stored)
                    }
                }
                PARENT_PASSWORD_FORMAT_LEGACY, null -> {
                    if (stored == input) {
                        val encoded = withContext(Dispatchers.Default) {
                            ParentPasswordHasher.hash(input)
                        }
                        prefs[PARENT_PASSWORD] = encoded
                        prefs[PARENT_PASSWORD_FORMAT] = PARENT_PASSWORD_FORMAT_ENCODED_V1
                        verified = true
                    }
                }
                else -> Unit
            }
        }
        return verified
    }

    /** 测试夹具专用：原样快照值与格式标记，绝不经 setParentPassword 二次哈希。 */
    internal suspend fun snapshotParentPasswordRawForTest(): ParentPasswordRawSnapshot {
        val prefs = dataStore.data.first()
        return ParentPasswordRawSnapshot(
            value = prefs[PARENT_PASSWORD],
            format = prefs[PARENT_PASSWORD_FORMAT],
        )
    }

    /** 测试夹具专用：在一次 edit 中原样恢复值与格式标记（包括两者均缺失）。 */
    internal suspend fun restoreParentPasswordRawForTest(snapshot: ParentPasswordRawSnapshot) {
        dataStore.edit { prefs ->
            if (snapshot.value == null) {
                prefs.remove(PARENT_PASSWORD)
            } else {
                prefs[PARENT_PASSWORD] = snapshot.value
            }
            if (snapshot.format == null) {
                prefs.remove(PARENT_PASSWORD_FORMAT)
            } else {
                prefs[PARENT_PASSWORD_FORMAT] = snapshot.format
            }
        }
    }

    // ===== 设备配对 =====
    suspend fun generatePairCode(): String {
        val code = Random.nextInt(1_000_000).toString().padStart(6, '0')
        dataStore.edit { it[PAIR_CODE] = code }
        return code
    }

    suspend fun setDeviceName(name: String) {
        dataStore.edit { it[DEVICE_NAME] = name }
    }

    suspend fun setPairedDevice(name: String) {
        dataStore.edit { it[PAIRED_DEVICE] = name }
    }

    suspend fun clearPairedDevice() {
        dataStore.edit { it.remove(PAIRED_DEVICE) }
    }

    // ===== 每日幸运抽奖 =====
    /**
     * D-13（v3.3.2）：原子领取当日幸运抽奖。语义与 [tryClaimStreakMilestone]（D-09）相同：
     * 读取-判定-写入在同一 edit 事务内完成，同一 [date] 并发/重复抽奖只有第一次返回 true
     * （换日即新一轮抽奖窗口）。原 `setLastLuckyDraw`（无条件写入、无判定结果）已由本函数取代。
     */
    suspend fun tryClaimDailyLuckyDraw(date: String): Boolean {
        var claimedNow = false
        dataStore.edit { prefs ->
            claimedNow = prefs[LAST_LUCKY_DRAW] != date
            if (claimedNow) prefs[LAST_LUCKY_DRAW] = date
        }
        return claimedNow
    }

    // ===== 学习宠物照料 =====
    /** 更新宠物照料状态（饱食度/心情自动限制在 0-100） */
    suspend fun setPetCare(fullness: Int, mood: Int, date: String) {
        dataStore.edit {
            it[PET_FULLNESS] = fullness.coerceIn(0, 100)
            it[PET_MOOD] = mood.coerceIn(0, 100)
            it[PET_LAST_CARE_DATE] = date
        }
    }

    /**
     * 学习行为提升宠物心情（复习错题/完成专注时调用）。
     * 在孩子的情感寄托（宠物）与学习行为之间建立正向连接：学习让宠物开心。
     */
    suspend fun boostPetMood(amount: Int) {
        dataStore.edit {
            val current = it[PET_MOOD] ?: 80
            it[PET_MOOD] = (current + amount).coerceIn(0, 100)
        }
    }

    // ===== 连续打卡里程碑 =====
    /**
     * D-09（v3.3.1 指令 §4.3）：原子领取一个连续打卡里程碑。
     *
     * 读取-判定-写入在同一个 `dataStore.edit` 事务内完成（DataStore 串行化 edit 块），
     * 并发/重复领取同一 [days] 时**恰好第一次**返回 true。调用方仅在返回 true 后才允许
     * 入账（先置位、后入账）；入账失败不回滚本置位——与账本 append-only 口径一致。
     * 原 `claimStreakMilestone`（无条件写入、无判定结果）已由本函数取代。
     */
    suspend fun tryClaimStreakMilestone(days: Int): Boolean {
        var claimedNow = false
        dataStore.edit { prefs ->
            val current = prefs[STREAK_MILESTONES_CLAIMED]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
            claimedNow = current.add(days)
            if (claimedNow) prefs[STREAK_MILESTONES_CLAIMED] = current.joinToString(",")
        }
        return claimedNow
    }

    // ===== 宠物场景装饰 =====
    /** 购买一个宠物场景 */
    suspend fun addOwnedScene(sceneId: String) {
        dataStore.edit { prefs ->
            val current = prefs[PET_OWNED_SCENES]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            current.add(sceneId)
            prefs[PET_OWNED_SCENES] = current.joinToString(",")
        }
    }

    /** 装扮（切换）当前宠物场景 */
    suspend fun setEquippedScene(sceneId: String) {
        dataStore.edit { it[PET_EQUIPPED_SCENE] = sceneId }
    }

    // ===== 每周挑战 =====
    /**
     * D-09（v3.3.1 指令 §4.3）：原子领取每周挑战。语义与 [tryClaimStreakMilestone] 相同：
     * 读取-判定-写入在同一 edit 事务内完成，同一 [weekStartStr] 并发/重复领取只有第一次返回 true。
     */
    suspend fun tryClaimWeeklyChallenge(weekStartStr: String): Boolean {
        var claimedNow = false
        dataStore.edit { prefs ->
            claimedNow = prefs[WEEKLY_CHALLENGE_CLAIMED_WEEK] != weekStartStr
            if (claimedNow) prefs[WEEKLY_CHALLENGE_CLAIMED_WEEK] = weekStartStr
        }
        return claimedNow
    }

    // ===== 每日学习目标（自我调节学习）=====
    /** 设定当日学习目标（同时重置完成状态） */
    suspend fun setDailyTargets(date: String, targetIds: Set<String>) {
        dataStore.edit {
            it[DAILY_TARGET_DATE] = date
            it[DAILY_TARGETS] = targetIds.joinToString(",")
            it[DAILY_TARGETS_DONE] = ""
        }
    }

    /** 切换某个学习目标的完成状态 */
    suspend fun setDailyTargetDone(targetId: String, done: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[DAILY_TARGETS_DONE]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (done) current.add(targetId) else current.remove(targetId)
            prefs[DAILY_TARGETS_DONE] = current.joinToString(",")
        }
    }

    /** 记录每日目标全部达成奖励的领取日期（每天只领一次） */
    suspend fun setDailyTargetBonusDate(date: String) {
        dataStore.edit { it[DAILY_TARGET_BONUS_DATE] = date }
    }

    // ===== 每日学习复盘（自我调节学习的反思环节）=====
    /** 保存每日复盘（日期+自评星级+反思标签） */
    suspend fun saveDailyReflection(date: String, stars: Int, tags: Set<String>) {
        dataStore.edit {
            it[DAILY_REFLECTION_DATE] = date
            it[DAILY_REFLECTION_STARS] = stars
            it[DAILY_REFLECTION_TAGS] = tags.joinToString(",")
        }
    }

    // ===== 错题复习每日提醒（让间隔复习真正落实到日常）=====
    val reviewReminderEnabled: Flow<Boolean> = dataStore.data.map { it[REVIEW_REMINDER_ENABLED] ?: false }
    val reviewReminderHour: Flow<Int> = dataStore.data.map { it[REVIEW_REMINDER_HOUR] ?: 19 }

    /** 设置错题复习提醒开关与提醒小时 */
    suspend fun setReviewReminder(enabled: Boolean, hour: Int) {
        dataStore.edit {
            it[REVIEW_REMINDER_ENABLED] = enabled
            it[REVIEW_REMINDER_HOUR] = hour.coerceIn(0, 23)
        }
    }

    // ===== 每日学习表现优秀奖励（激励打卡+专注+复习全面卓越）=====
    val dailyExcellentBonusDate: Flow<String> = dataStore.data.map { it[DAILY_EXCELLENT_BONUS_DATE] ?: "" }

    /**
     * D-09（v3.3.1 指令 §4.3）：原子领取当日学习表现优秀奖励。语义与
     * [tryClaimStreakMilestone] 相同：读取-判定-写入在同一 edit 事务内完成，
     * 同一 [date] 并发/重复领取只有第一次返回 true（换日即新一轮领取窗口）。
     */
    suspend fun tryClaimDailyExcellentBonus(date: String): Boolean {
        var claimedNow = false
        dataStore.edit { prefs ->
            claimedNow = prefs[DAILY_EXCELLENT_BONUS_DATE] != date
            if (claimedNow) prefs[DAILY_EXCELLENT_BONUS_DATE] = date
        }
        return claimedNow
    }

    // ===== 舒尔特方格专注训练个人最佳记录（自我竞赛，激发反复练习）=====
    val focusTrainingBestMs: Flow<Long> = dataStore.data.map { it[FOCUS_TRAINING_BEST_MS] ?: 0L }

    /** 原子更新专注训练个人最佳用时；返回 true 表示本次结果是新纪录。 */
    suspend fun tryUpdateFocusTrainingBest(candidateMs: Long): Boolean {
        if (candidateMs <= 0L) return false
        var updated = false
        dataStore.edit { prefs ->
            val current = prefs[FOCUS_TRAINING_BEST_MS] ?: 0L
            if (current == 0L || candidateMs < current) {
                prefs[FOCUS_TRAINING_BEST_MS] = candidateMs
                updated = true
            }
        }
        return updated
    }

    // ===== 数字记忆训练状态（跨导航/跨进程恢复）=====
    val memoryTrainingState: Flow<MemoryTrainingState?> = dataStore.data.map { readMemoryTrainingState(it) }

    private fun readMemoryTrainingState(prefs: Preferences): MemoryTrainingState? {
        if ((prefs[MEMORY_TRAINING_SCHEMA] ?: 0) != MEMORY_TRAINING_SCHEMA_VERSION) return null
        val phase = runCatching {
            MemoryTrainingState.Phase.valueOf(prefs[MEMORY_TRAINING_PHASE] ?: MemoryTrainingState.Phase.IDLE.name)
        }.getOrDefault(MemoryTrainingState.Phase.IDLE)
        return MemoryTrainingState(
            schemaVersion = MEMORY_TRAINING_SCHEMA_VERSION,
            phase = phase,
            level = (prefs[MEMORY_TRAINING_LEVEL] ?: 3).coerceAtLeast(3),
            sequence = prefs[MEMORY_TRAINING_SEQUENCE] ?: "",
            bestLevel = (prefs[MEMORY_TRAINING_BEST_LEVEL] ?: 0).coerceAtLeast(0),
            lastCorrect = prefs[MEMORY_TRAINING_LAST_CORRECT] ?: false,
            showUntilEpochMs = prefs[MEMORY_TRAINING_SHOW_UNTIL] ?: 0L,
        )
    }

    suspend fun saveMemoryTrainingState(state: MemoryTrainingState) {
        require(state.schemaVersion == MEMORY_TRAINING_SCHEMA_VERSION) {
            "Unsupported memory training schema: ${state.schemaVersion}"
        }
        require(state.level >= 3) { "Memory training level must be at least 3" }
        require(state.bestLevel >= 0) { "Memory training best level cannot be negative" }
        dataStore.edit { prefs ->
            prefs.writeMemoryTrainingState(state)
        }
    }

    /**
     * 串行更新数字记忆状态：读取、转换、校验和写入都在同一个 DataStore.edit 内完成。
     * 返回 true 表示状态确实变化；屏幕转换器用 phase/sequence 守卫拒绝陈旧快照。
     */
    suspend fun updateMemoryTrainingState(
        transform: (MemoryTrainingState) -> MemoryTrainingState,
    ): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val current = readMemoryTrainingState(prefs) ?: MemoryTrainingState()
            val next = transform(current)
            require(next.schemaVersion == MEMORY_TRAINING_SCHEMA_VERSION) {
                "Unsupported memory training schema: ${next.schemaVersion}"
            }
            require(next.level >= 3) { "Memory training level must be at least 3" }
            require(next.bestLevel >= 0) { "Memory training best level cannot be negative" }
            if (next != current) {
                prefs.writeMemoryTrainingState(next)
                changed = true
            }
        }
        return changed
    }

    private fun MutablePreferences.writeMemoryTrainingState(state: MemoryTrainingState) {
        this[MEMORY_TRAINING_SCHEMA] = state.schemaVersion
        this[MEMORY_TRAINING_PHASE] = state.phase.name
        this[MEMORY_TRAINING_LEVEL] = state.level
        this[MEMORY_TRAINING_SEQUENCE] = state.sequence
        this[MEMORY_TRAINING_BEST_LEVEL] = state.bestLevel
        this[MEMORY_TRAINING_LAST_CORRECT] = state.lastCorrect
        this[MEMORY_TRAINING_SHOW_UNTIL] = state.showUntilEpochMs
    }

    // ===== 连续复习里程碑奖励（巩固每日复习习惯）=====
    val claimedReviewStreakMilestones: Flow<Set<Int>> = dataStore.data.map {
        it[REVIEW_STREAK_MILESTONES_CLAIMED]?.split(",")?.filter { s -> s.isNotBlank() }?.mapNotNull { s -> s.toIntOrNull() }?.toSet() ?: emptySet()
    }

    /**
     * D-13（v3.3.2）：原子领取一个连续复习里程碑。语义与 [tryClaimStreakMilestone]（D-09）相同：
     * 读取-判定-写入在同一 `dataStore.edit` 事务内完成（edit 块串行化），并发/重复领取同一
     * [days] 时**恰好第一次**返回 true。调用方仅在返回 true 后才允许入账（先置位、后入账）；
     * 入账失败不回滚本置位（append-only 口径）。原 `addReviewStreakMilestoneClaimed`
     * （无条件写入、无判定结果）已由本函数取代。
     */
    suspend fun tryClaimReviewStreakMilestone(days: Int): Boolean {
        var claimedNow = false
        dataStore.edit { prefs ->
            val current = prefs[REVIEW_STREAK_MILESTONES_CLAIMED]?.split(",")?.filter { s -> s.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            claimedNow = current.add(days.toString())
            if (claimedNow) prefs[REVIEW_STREAK_MILESTONES_CLAIMED] = current.joinToString(",")
        }
        return claimedNow
    }

    // ===== 家长每周学习周报推送 =====
    val weeklyReportEnabled: Flow<Boolean> = dataStore.data.map { it[WEEKLY_REPORT_ENABLED] ?: false }

    /** 设置家长每周学习周报推送开关 */
    suspend fun setWeeklyReportEnabled(enabled: Boolean) {
        dataStore.edit { it[WEEKLY_REPORT_ENABLED] = enabled }
    }

    // ===== 舒尔特方格专注训练次数统计 =====
    val focusTrainingCount: Flow<Int> = dataStore.data.map { it[FOCUS_TRAINING_COUNT] ?: 0 }

    /** 累加一次专注训练完成次数 */
    suspend fun incrementFocusTrainingCount() {
        dataStore.edit { it[FOCUS_TRAINING_COUNT] = (it[FOCUS_TRAINING_COUNT] ?: 0) + 1 }
    }

    // ===== 番茄钟今日完成数持久化（离开页面不丢失） =====
    val pomodoroTodayCount: Flow<Int> = dataStore.data.map {
        val raw = it[POMODORO_TODAY_COUNT] ?: ""
        val today = todayString()
        if (raw.startsWith("$today:")) raw.substringAfter(":").toIntOrNull() ?: 0 else 0
    }

    /** 累加今日番茄钟完成数 */
    suspend fun incrementPomodoroTodayCount() {
        dataStore.edit {
            val today = todayString()
            val raw = it[POMODORO_TODAY_COUNT] ?: ""
            val current = if (raw.startsWith("$today:")) raw.substringAfter(":").toIntOrNull() ?: 0 else 0
            it[POMODORO_TODAY_COUNT] = "$today:${current + 1}"
        }
    }

    // ===== 番茄钟跳过呼吸引导偏好（记住用户选择） =====
    val pomodoroSkipBreathing: Flow<Boolean> = dataStore.data.map { it[POMODORO_SKIP_BREATHING] ?: false }

    /** 设置是否跳过专注前呼吸引导 */
    suspend fun setPomodoroSkipBreathing(skip: Boolean) {
        dataStore.edit { it[POMODORO_SKIP_BREATHING] = skip }
    }

    // ===== 番茄钟时间戳锚定会话持久化（v3.3.1 D-05/07：跨导航恢复 + 跨进程补结算的唯一状态源） =====

    /**
     * 当前持久化会话；null = 无进行中会话（键组整体缺失或起点<=0）。
     * 页面与 bootstrap 恢复都读这条流；"剩余多少"永远由 [PomodoroSessionState.deriveRemainingMs] 派生。
     */
    val pomodoroSession: Flow<PomodoroSessionState?> = dataStore.data.map { readPomodoroSession(it) }

    private fun readPomodoroSession(prefs: Preferences): PomodoroSessionState? {
        val start = prefs[POMODORO_SESSION_START] ?: return null
        if (start <= 0L) return null
        val planned = prefs[POMODORO_SESSION_PLANNED_MIN] ?: return null
        return PomodoroSessionState(
            sessionStartEpochMs = start,
            plannedMinutes = planned,
            phase = if (prefs[POMODORO_SESSION_PHASE] == PomodoroSessionPhase.BREAK.name)
                PomodoroSessionPhase.BREAK else PomodoroSessionPhase.FOCUS,
            focusTarget = prefs[POMODORO_SESSION_TARGET] ?: CanonicalFocusText.TARGET_FREE_STUDY,
            pausedAccumMs = prefs[POMODORO_SESSION_PAUSED_ACCUM_MS] ?: 0L,
            pauseStartedAtEpochMs = (prefs[POMODORO_SESSION_PAUSE_STARTED_AT] ?: 0L).takeIf { it > 0L },
            settled = prefs[POMODORO_SESSION_SETTLED] ?: false,
            recordedSessionId = prefs[POMODORO_SESSION_FOCUS_RECORD_ID] ?: 0L,
            settleDate = prefs[POMODORO_SESSION_SETTLE_DATE] ?: "",
            version = prefs[POMODORO_SESSION_VERSION] ?: 0L,
        )
    }

    /**
     * 整组写入/整组清除（null=清会话；结算完成、停止、过期 BREAK 静默清都走这里）。
     *
     * [expectation] 非 null 时在同一个 DataStore.edit 事务内核对起锚点、settled 与 version；
     * 暂停/恢复/停止等 UI 转换必须传它，陈旧快照返回 false。已结算会话只接受显式结算续跑
     * （清会话、转 BREAK 或保留 settled 的 FOCUS），拒绝一切暂停/恢复类回写。
     *
     * 缺省 null 仅用于**新起锚**：当前必须无会话，或写入一个不同 start 的未结算 FOCUS 段。
     * 无锚清除、同锚覆盖、BREAK 起锚与已结算会话回写一律拒绝；显式转换必须传 [expectation]。
     */
    suspend fun savePomodoroSession(
        state: PomodoroSessionState?,
        expectation: PomodoroWriteExpectation? = null,
    ): Boolean {
        var wrote = false
        dataStore.edit { prefs ->
            val current = readPomodoroSession(prefs)
            if (expectation == null) {
                val isNewAnchor = state != null &&
                    (current == null || state.sessionStartEpochMs != current.sessionStartEpochMs) &&
                    state.phase == PomodoroSessionPhase.FOCUS &&
                    !state.settled
                if (!isNewAnchor) {
                    println("[Pomodoro][CAS] savePomodoroSession 放弃：无锚写入只能启动新的 FOCUS 段")
                    return@edit
                }
            } else {
                if (current == null ||
                    current.sessionStartEpochMs != expectation.startEpochMs ||
                    current.settled != expectation.settled ||
                    current.version != expectation.version
                ) {
                    println(
                        "[Pomodoro][CAS] savePomodoroSession 放弃：expected=$expectation " +
                            "但现存会话=$current"
                    )
                    return@edit
                }
                if (current.settled) {
                    val explicitSettlement = expectation.settled && state != current &&
                        (state == null || state.phase == PomodoroSessionPhase.BREAK ||
                            (state.phase == PomodoroSessionPhase.FOCUS && state.settled))
                    if (!explicitSettlement) {
                        println("[Pomodoro][CAS] savePomodoroSession 放弃：已结算会话拒绝非结算转换")
                        return@edit
                    }
                }
            }

            if (state == null) {
                prefs.remove(POMODORO_SESSION_START)
                prefs.remove(POMODORO_SESSION_PLANNED_MIN)
                prefs.remove(POMODORO_SESSION_TARGET)
                prefs.remove(POMODORO_SESSION_PHASE)
                prefs.remove(POMODORO_SESSION_PAUSED_ACCUM_MS)
                prefs.remove(POMODORO_SESSION_PAUSE_STARTED_AT)
                prefs.remove(POMODORO_SESSION_SETTLED)
                prefs.remove(POMODORO_SESSION_FOCUS_RECORD_ID)
                prefs.remove(POMODORO_SESSION_SETTLE_DATE)
                prefs.remove(POMODORO_SESSION_VERSION)
            } else {
                prefs[POMODORO_SESSION_START] = state.sessionStartEpochMs
                prefs[POMODORO_SESSION_PLANNED_MIN] = state.plannedMinutes
                prefs[POMODORO_SESSION_TARGET] = state.focusTarget
                prefs[POMODORO_SESSION_PHASE] = state.phase.name
                prefs[POMODORO_SESSION_PAUSED_ACCUM_MS] = state.pausedAccumMs
                prefs[POMODORO_SESSION_PAUSE_STARTED_AT] = state.pauseStartedAtEpochMs ?: 0L
                prefs[POMODORO_SESSION_SETTLED] = state.settled
                prefs[POMODORO_SESSION_FOCUS_RECORD_ID] = state.recordedSessionId
                prefs[POMODORO_SESSION_SETTLE_DATE] = state.settleDate
                prefs[POMODORO_SESSION_VERSION] =
                    if (expectation != null) expectation.version + 1L else state.version
            }
            wrote = true
        }
        return wrote
    }

    /**
     * 结算认领 CAS（要求 4，先置位后入账，形态同 [tryClaimStreakMilestone]）：
     * 读取-判定-写入在同一个 `dataStore.edit` 事务内完成（DataStore 串行化 edit 块），
     * 双触发（页面内到点 + 启动补结算）恰好第一次赢。
     *
     * 判定条件：会话存在、FOCUS 段、未结算、起锚点与调用方读取到的会话一致（防止把
     * **新**会话误认成旧会话结算）、deadline（含暂停顺延）已过。赢下认领的同一段临界区里
     * 一并落"结算日 + 今日番茄数 +1 + 宠物心情 +3"——这三件事随认领恰好发生一次，
     * 崩溃重放不会再重复加分之外的统计（入账本身由 sessionId/bizKey 幂等兜底）。
     */
    suspend fun tryClaimPomodoroFocusSettle(expectedStartEpochMs: Long, nowMs: Long): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            val session = readPomodoroSession(prefs)
            if (session != null &&
                session.phase == PomodoroSessionPhase.FOCUS &&
                !session.settled &&
                session.sessionStartEpochMs == expectedStartEpochMs &&
                session.deriveRemainingMs(nowMs) <= 0L
            ) {
                val today = todayString()
                prefs[POMODORO_SESSION_SETTLED] = true
                prefs[POMODORO_SESSION_SETTLE_DATE] = today
                prefs[POMODORO_SESSION_VERSION] = session.version + 1L
                val raw = prefs[POMODORO_TODAY_COUNT] ?: ""
                val current = if (raw.startsWith("$today:")) raw.substringAfter(":").toIntOrNull() ?: 0 else 0
                prefs[POMODORO_TODAY_COUNT] = "$today:${current + 1}"
                prefs[PET_MOOD] = ((prefs[PET_MOOD] ?: 80) + 3).coerceIn(0, 100)
                claimed = true
            }
        }
        return claimed
    }

    /**
     * 落盘已写入的 focus_sessions 行 id（仅当会话起锚点仍是同一段时）。
     * 崩溃续跑据此用**同一条** `FOCUS:$sessionId:$date` bizKey 补 checkIn，不产生第二笔待审单。
     */
    suspend fun markPomodoroFocusRecordId(expectedStartEpochMs: Long, recordId: Long) {
        dataStore.edit { prefs ->
            if (prefs[POMODORO_SESSION_START] == expectedStartEpochMs &&
                (prefs[POMODORO_SESSION_FOCUS_RECORD_ID] ?: 0L) != recordId
            ) {
                prefs[POMODORO_SESSION_FOCUS_RECORD_ID] = recordId
                prefs[POMODORO_SESSION_VERSION] = (prefs[POMODORO_SESSION_VERSION] ?: 0L) + 1L
            }
        }
    }

    // ===== 设备间数据同步追踪（配对后持续同步） =====
    val lastSyncDate: Flow<String> = dataStore.data.map { it[LAST_SYNC_DATE] ?: "" }
    val syncReminderEnabled: Flow<Boolean> = dataStore.data.map { it[SYNC_REMINDER_ENABLED] ?: true }

    /** 记录一次成功同步（备份发送或导入成功时调用） */
    suspend fun recordSync() {
        val today = todayString()
        dataStore.edit { it[LAST_SYNC_DATE] = today }
    }

    /** 设置定期同步提醒开关 */
    suspend fun setSyncReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[SYNC_REMINDER_ENABLED] = enabled }
    }

    // ===== 每日必须任务扣分追踪 =====
    val lastMandatoryPenaltyDate: Flow<String?> = dataStore.data.map { it[LAST_MANDATORY_PENALTY_DATE] }


    /** 记录已处理每日必须扣分的最后日期 */
    suspend fun setLastMandatoryPenaltyDate(date: String) {
        dataStore.edit { it[LAST_MANDATORY_PENALTY_DATE] = date }
    }
}
