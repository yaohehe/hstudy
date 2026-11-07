package com.hstudy.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.datetime.Clock

/**
 * 数据库迁移定义。
 *
 * 版本演进：
 * - v1: 初始 5 张表（tasks / point_records / task_completions / rewards / reward_exchanges）
 * - v2: 新增 7 张表（mistakes / mistake_reviews / focus_sessions / daily_plans /
 *       completion_proofs / habit_challenges / achievements）
 * - v3: rewards 增加 category 列；habit_challenges 增加 lastCheckInDate 列
 * - v4: 新增 daily_assignments 表
 * - v5: 为 rewards.category / habit_challenges.lastCheckInDate 补齐 DEFAULT 约束，
 *       使迁移产生的 schema 与实体（@ColumnInfo defaultValue）完全一致（重建表，保留数据）
 *
 * 所有迁移均保留已有数据，不使用破坏性迁移。
 */
object Migrations {

    /** v1 -> v2：创建 7 张新表 */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mistakes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`subject` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`wrongReason` TEXT NOT NULL, " +
                    "`correctMethod` TEXT NOT NULL, " +
                    "`isMastered` INTEGER NOT NULL, " +
                    "`photoPath` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`masteredAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mistake_reviews` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mistakeId` INTEGER NOT NULL, " +
                    "`reviewedAt` INTEGER NOT NULL, " +
                    "`earnedPoints` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`durationMinutes` INTEGER NOT NULL, " +
                    "`taskTitle` TEXT NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, " +
                    "`earnedPoints` INTEGER NOT NULL, " +
                    "`date` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_plans` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` TEXT NOT NULL, " +
                    "`startHour` INTEGER NOT NULL, " +
                    "`startMinute` INTEGER NOT NULL, " +
                    "`endHour` INTEGER NOT NULL, " +
                    "`endMinute` INTEGER NOT NULL, " +
                    "`taskTitle` TEXT NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `completion_proofs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`taskId` INTEGER NOT NULL, " +
                    "`photoPath` TEXT NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`submittedAt` INTEGER NOT NULL, " +
                    "`reviewedAt` INTEGER, " +
                    "`pointsAwarded` INTEGER NOT NULL, " +
                    "`date` TEXT NOT NULL)"
            )
            // v2 阶段的 habit_challenges 尚无 lastCheckInDate 列（v3 才添加）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habit_challenges` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`startDate` TEXT NOT NULL, " +
                    "`totalDays` INTEGER NOT NULL, " +
                    "`completedDays` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "`emoji` TEXT NOT NULL, " +
                    "`dailyPoints` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `achievements` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`emoji` TEXT NOT NULL, " +
                    "`unlockedAt` INTEGER NOT NULL)"
            )
        }
    }

    /** v2 -> v3：rewards 增加 category；habit_challenges 增加 lastCheckInDate */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `rewards` ADD COLUMN `category` TEXT NOT NULL DEFAULT '其他'")
            db.execSQL("ALTER TABLE `habit_challenges` ADD COLUMN `lastCheckInDate` TEXT NOT NULL DEFAULT ''")
        }
    }

    /** v3 -> v4：新增 daily_assignments 表 */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_assignments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`points` INTEGER NOT NULL, " +
                    "`assignedDate` TEXT NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER, " +
                    "`parentNote` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    /**
     * v4 -> v5：重建 rewards / habit_challenges 表以补齐 DEFAULT 约束，
     * 使由 Room 直接创建（无默认值）的旧 v4 表与实体 @ColumnInfo(defaultValue) 期望一致。
     * 采用「建新表-拷数据-删旧表-重命名」标准模式，完整保留数据。
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ---- rewards ----
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `rewards_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`pointsCost` INTEGER NOT NULL, " +
                    "`iconEmoji` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL DEFAULT '其他', " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `rewards_new` " +
                    "(`id`,`name`,`description`,`pointsCost`,`iconEmoji`,`category`,`isActive`,`sortOrder`,`createdAt`) " +
                    "SELECT `id`,`name`,`description`,`pointsCost`,`iconEmoji`,`category`,`isActive`,`sortOrder`,`createdAt` FROM `rewards`"
            )
            db.execSQL("DROP TABLE `rewards`")
            db.execSQL("ALTER TABLE `rewards_new` RENAME TO `rewards`")

            // ---- habit_challenges ----
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habit_challenges_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`startDate` TEXT NOT NULL, " +
                    "`totalDays` INTEGER NOT NULL, " +
                    "`completedDays` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, " +
                    "`emoji` TEXT NOT NULL, " +
                    "`dailyPoints` INTEGER NOT NULL, " +
                    "`lastCheckInDate` TEXT NOT NULL DEFAULT '', " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `habit_challenges_new` " +
                    "(`id`,`title`,`description`,`startDate`,`totalDays`,`completedDays`,`isActive`,`isCompleted`,`emoji`,`dailyPoints`,`lastCheckInDate`,`createdAt`) " +
                    "SELECT `id`,`title`,`description`,`startDate`,`totalDays`,`completedDays`,`isActive`,`isCompleted`,`emoji`,`dailyPoints`,`lastCheckInDate`,`createdAt` FROM `habit_challenges`"
            )
            db.execSQL("DROP TABLE `habit_challenges`")
            db.execSQL("ALTER TABLE `habit_challenges_new` RENAME TO `habit_challenges`")
        }
    }

    /**
     * v5 -> v6：V2.11 三大功能
     * - tasks 增加 difficulty 列（难度分级）
     * - 新增 wish_goals 表（心愿存钱罐）
     * - 新增 daily_schedules 表（每日日程提醒）
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 任务难度分级
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `difficulty` INTEGER NOT NULL DEFAULT 1")

            // 心愿存钱罐
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wish_goals` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`targetPoints` INTEGER NOT NULL, " +
                    "`savedPoints` INTEGER NOT NULL, " +
                    "`iconEmoji` TEXT NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER)"
            )

            // 每日日程
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_schedules` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`time` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`emoji` TEXT NOT NULL, " +
                    "`linkedTaskId` INTEGER, " +
                    "`isEnabled` INTEGER NOT NULL, " +
                    "`repeatDays` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL)"
            )
        }
    }

    /**
     * v6 -> v7：错题本艾宾浩斯间隔复习
     * - mistakes 增加 lastReviewAt（上次复习时间）、reviewCount（复习次数）
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `mistakes` ADD COLUMN `lastReviewAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `mistakes` ADD COLUMN `reviewCount` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v7 -> v8：错题举一反三练习
     * - mistakes 增加 variationNote（举一反三笔记）、hasVariation（是否已举一反三）
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `mistakes` ADD COLUMN `variationNote` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `mistakes` ADD COLUMN `hasVariation` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v8 -> v9：错题错误类型标签（错因分析）
     * - mistakes 增加 errorType（错误类型：粗心马虎/概念不清/方法不会/审题错误/计算错误/其他）
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `mistakes` ADD COLUMN `errorType` TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v9 -> v10：积分审核制（家长审核孩子积分申请）
     * - 新增 point_approvals 表
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `point_approvals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `actionType` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `requestedPoints` INTEGER NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `requestedAt` INTEGER NOT NULL,
                `reviewedAt` INTEGER NOT NULL DEFAULT 0
            )""".trimIndent())
        }
    }

    /**
     * v10 -> v11：任务每日必须标记（未完成自动扣分）
     * - tasks 增加 isMandatory（0/1，默认0）
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `isMandatory` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v11 -> v12：积分生命周期一致性改造
     * - point_approvals 增加 taskId / bizKey / awardedRecordId / date：
     *   bizKey 保证同一业务（任务+日期）只有一条生效申请，杜绝重复累计；
     *   awardedRecordId 记录审批发放的积分记录，撤销时可精确冲抵，杜绝积分残留。
     * - point_records 增加 approvalId：积分记录可溯源到审批申请。
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `point_approvals` ADD COLUMN `taskId` INTEGER")
            db.execSQL("ALTER TABLE `point_approvals` ADD COLUMN `bizKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `point_approvals` ADD COLUMN `awardedRecordId` INTEGER")
            db.execSQL("ALTER TABLE `point_approvals` ADD COLUMN `date` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `point_records` ADD COLUMN `approvalId` INTEGER")

            // 数据修复：旧逻辑"取消打卡不撤销申请 + 重新打卡又建申请"会留下多条 PENDING。
            // 家长一旦全部通过就会重复计分，此处按 (描述, 申请积分) 分组只保留最早一条，
            // 其余置为 CANCELLED（本就未计分，不影响余额，且从源头消除重复计入）。
            db.execSQL(
                """UPDATE point_approvals SET status = 'CANCELLED', reviewedAt = ${Clock.System.now().toEpochMilliseconds()}
                   WHERE status = 'PENDING' AND id NOT IN (
                       SELECT MIN(id) FROM point_approvals WHERE status = 'PENDING'
                       GROUP BY description, requestedPoints
                   )""".trimIndent()
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12
    )
}
