package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock

/**
 * 错题科目
 */
enum class MistakeSubject {
    MATH,       // 数学
    CHINESE,    // 语文
    ENGLISH,    // 英语
    SCIENCE,    // 科学
    OTHER       // 其他
}

/** 错题错误类型（用于错因分析，针对性改进学习方法） */
val mistakeErrorTypes = listOf("粗心马虎", "概念不清", "方法不会", "审题错误", "计算错误", "其他")

@Entity(tableName = "mistakes")
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: MistakeSubject,
    val title: String,                    // 题目摘要
    val content: String = "",             // 题目详细描述
    val wrongReason: String = "",         // 错误原因分析
    val correctMethod: String = "",       // 正确解法
    val isMastered: Boolean = false,      // 是否已掌握
    val photoPath: String = "",           // 照片路径（可空）
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val masteredAt: Long? = null,
    val lastReviewAt: Long = 0,           // 上次复习时间（艾宾浩斯间隔复习）
    val reviewCount: Int = 0,             // 累计复习次数（决定下次复习间隔）
    val variationNote: String = "",       // 举一反三笔记（变式题/知识点拓展）
    val hasVariation: Boolean = false,    // 是否已完成举一反三思考
    val errorType: String = ""            // 错误类型（粗心马虎/概念不清/方法不会/审题错误/计算错误/其他）
)

@Entity(tableName = "mistake_reviews")
data class MistakeReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: Long,
    val reviewedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val earnedPoints: Int = 0,
    val note: String = ""
)
