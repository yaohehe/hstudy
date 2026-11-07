package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeReviewEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeDao {

    @Insert
    suspend fun insert(mistake: MistakeEntity): Long

    @Update
    suspend fun update(mistake: MistakeEntity)

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM mistakes WHERE isMastered = 0 ORDER BY createdAt DESC")
    fun getUnmasteredMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes ORDER BY createdAt DESC")
    fun getAllMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE subject = :subject ORDER BY createdAt DESC")
    fun getMistakesBySubject(subject: MistakeSubject): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun getMistakeById(id: Long): MistakeEntity?

    @Query("UPDATE mistakes SET isMastered = :mastered, masteredAt = :masteredAt WHERE id = :id")
    suspend fun setMastered(id: Long, mastered: Boolean, masteredAt: Long? = null)

    /** 更新复习进度（艾宾浩斯间隔复习） */
    @Query("UPDATE mistakes SET lastReviewAt = :lastReviewAt, reviewCount = :reviewCount WHERE id = :id")
    suspend fun updateReviewProgress(id: Long, lastReviewAt: Long, reviewCount: Int)

    @Query("SELECT COUNT(*) FROM mistakes WHERE isMastered = 1")
    fun getMasteredCount(): Flow<Int>

    // 复习记录
    @Insert
    suspend fun insertReview(review: MistakeReviewEntity): Long

    @Query("SELECT * FROM mistake_reviews WHERE mistakeId = :mistakeId ORDER BY reviewedAt DESC")
    fun getReviewsForMistake(mistakeId: Long): Flow<List<MistakeReviewEntity>>

    @Query("SELECT COUNT(*) FROM mistake_reviews")
    fun getTotalReviewCount(): Flow<Int>

    /** 已完成举一反三思考的错题数 */
    @Query("SELECT COUNT(*) FROM mistakes WHERE hasVariation = 1")
    fun getVariationCount(): Flow<Int>

    /** 某时间点之后的复习次数（用于统计今日复习量） */
    @Query("SELECT COUNT(*) FROM mistake_reviews WHERE reviewedAt >= :since")
    fun getReviewCountSince(since: Long): Flow<Int>

    /** 某时间点之后的复习记录列表（用于按日统计复习趋势） */
    @Query("SELECT * FROM mistake_reviews WHERE reviewedAt >= :since ORDER BY reviewedAt DESC")
    fun getReviewsSince(since: Long): Flow<List<MistakeReviewEntity>>
}
