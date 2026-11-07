package com.hstudy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
