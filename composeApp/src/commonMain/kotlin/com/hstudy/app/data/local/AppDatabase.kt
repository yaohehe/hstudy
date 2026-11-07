package com.hstudy.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.hstudy.app.data.local.dao.*
import com.hstudy.app.data.local.entity.*

@Database(
    entities = [
        TaskEntity::class,
        PointRecordEntity::class,
        TaskCompletionEntity::class,
        RewardEntity::class,
        RewardExchangeEntity::class,
        MistakeEntity::class,
        MistakeReviewEntity::class,
        FocusSessionEntity::class,
        DailyPlanEntity::class,
        CompletionProofEntity::class,
        HabitChallengeEntity::class,
        AchievementEntity::class,
        DailyAssignmentEntity::class,
        WishGoalEntity::class,
        DailyScheduleEntity::class,
        PointApprovalEntity::class
    ],
    version = 12,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun pointDao(): PointDao
    abstract fun rewardDao(): RewardDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun focusDao(): FocusDao
    abstract fun habitDao(): HabitDao
    abstract fun dailyAssignmentDao(): DailyAssignmentDao
    abstract fun wishGoalDao(): WishGoalDao
    abstract fun dailyScheduleDao(): DailyScheduleDao
    abstract fun pointApprovalDao(): PointApprovalDao
}

// Room 编译器（KSP）会为每个目标平台生成 actual 实现（内部实例化 AppDatabase_Impl），
// 因此无需手写 androidMain/iosMain 的 actual object（手写反而会与生成代码冲突）。
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}