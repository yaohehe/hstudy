package com.hstudy.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hstudy.app.data.repository.todayString
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.random.Random

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

class UserPreferences {

    private val dataStore: DataStore<Preferences> get() = dataStoreInstance

    companion object {
        val ROLE_KEY = stringPreferencesKey("current_role") // "parent" or "child"
        val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")
        val PARENT_PASSWORD = stringPreferencesKey("parent_password") // 家长密码
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
    }

    val currentRole: Flow<String?> = dataStore.data.map { it[ROLE_KEY] }
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

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[HAS_ONBOARDED] = value }
    }

    suspend fun clearRole() {
        dataStore.edit { it.remove(ROLE_KEY) }
    }

    // ===== 家长密码 =====
    suspend fun setParentPassword(password: String) {
        dataStore.edit { it[PARENT_PASSWORD] = password }
    }

    suspend fun clearParentPassword() {
        dataStore.edit { it.remove(PARENT_PASSWORD) }
    }

    suspend fun verifyParentPassword(input: String): Boolean {
        var stored: String? = null
        dataStore.edit { stored = it[PARENT_PASSWORD] }
        return stored != null && stored == input
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
    suspend fun setLastLuckyDraw(date: String) {
        dataStore.edit { it[LAST_LUCKY_DRAW] = date }
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
    /** 领取一个连续打卡里程碑（记录已领取的天数） */
    suspend fun claimStreakMilestone(days: Int) {
        dataStore.edit { prefs ->
            val current = prefs[STREAK_MILESTONES_CLAIMED]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
            current.add(days)
            prefs[STREAK_MILESTONES_CLAIMED] = current.joinToString(",")
        }
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
    /** 领取每周挑战（记录领取的周起始日，每周只能领一次） */
    suspend fun claimWeeklyChallenge(weekStartStr: String) {
        dataStore.edit { it[WEEKLY_CHALLENGE_CLAIMED_WEEK] = weekStartStr }
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

    /** 记录学习表现优秀奖励的领取日期（每天只领一次） */
    suspend fun setDailyExcellentBonusDate(date: String) {
        dataStore.edit { it[DAILY_EXCELLENT_BONUS_DATE] = date }
    }

    // ===== 舒尔特方格专注训练个人最佳记录（自我竞赛，激发反复练习）=====
    val focusTrainingBestMs: Flow<Long> = dataStore.data.map { it[FOCUS_TRAINING_BEST_MS] ?: 0L }

    /** 更新专注训练个人最佳用时（仅当创新纪录时调用） */
    suspend fun setFocusTrainingBest(ms: Long) {
        dataStore.edit { it[FOCUS_TRAINING_BEST_MS] = ms }
    }

    // ===== 连续复习里程碑奖励（巩固每日复习习惯）=====
    val claimedReviewStreakMilestones: Flow<Set<Int>> = dataStore.data.map {
        it[REVIEW_STREAK_MILESTONES_CLAIMED]?.split(",")?.filter { s -> s.isNotBlank() }?.mapNotNull { s -> s.toIntOrNull() }?.toSet() ?: emptySet()
    }

    /** 记录已领取的连续复习里程碑天数 */
    suspend fun addReviewStreakMilestoneClaimed(days: Int) {
        dataStore.edit {
            val current = it[REVIEW_STREAK_MILESTONES_CLAIMED]?.split(",")?.filter { s -> s.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            current.add(days.toString())
            it[REVIEW_STREAK_MILESTONES_CLAIMED] = current.joinToString(",")
        }
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