package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.MistakeDao
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeReviewEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class MistakeRepository(private val mistakeDao: MistakeDao) {

    fun getAllMistakes(): Flow<List<MistakeEntity>> = mistakeDao.getAllMistakes()

    fun getUnmasteredMistakes(): Flow<List<MistakeEntity>> = mistakeDao.getUnmasteredMistakes()

    fun getMistakesBySubject(subject: MistakeSubject): Flow<List<MistakeEntity>> =
        mistakeDao.getMistakesBySubject(subject)

    suspend fun getMistakeById(id: Long): MistakeEntity? = mistakeDao.getMistakeById(id)

    suspend fun addMistake(mistake: MistakeEntity): Long = mistakeDao.insert(mistake)

    suspend fun updateMistake(mistake: MistakeEntity) = mistakeDao.update(mistake)

    suspend fun deleteMistake(id: Long) = mistakeDao.delete(id)

    suspend fun setMastered(id: Long, mastered: Boolean) {
        val masteredAt = if (mastered) Clock.System.now().toEpochMilliseconds() else null
        mistakeDao.setMastered(id, mastered, masteredAt)
    }

    fun getMasteredCount(): Flow<Int> = mistakeDao.getMasteredCount()

    suspend fun addReview(mistakeId: Long, points: Int = 3, note: String = ""): Long {
        return mistakeDao.insertReview(
            MistakeReviewEntity(
                mistakeId = mistakeId,
                earnedPoints = points,
                note = note
            )
        )
    }

    /**
     * 复习错题（艾宾浩斯间隔复习）：
     * 更新上次复习时间与复习次数，并写入一条复习记录。
     */
    suspend fun reviewMistake(mistakeId: Long, points: Int = 3, note: String = "") {
        val mistake = mistakeDao.getMistakeById(mistakeId) ?: return
        mistakeDao.updateReviewProgress(
            id = mistakeId,
            lastReviewAt = Clock.System.now().toEpochMilliseconds(),
            reviewCount = mistake.reviewCount + 1
        )
        mistakeDao.insertReview(
            MistakeReviewEntity(mistakeId = mistakeId, earnedPoints = points, note = note)
        )
    }

    fun getReviewsForMistake(mistakeId: Long): Flow<List<MistakeReviewEntity>> =
        mistakeDao.getReviewsForMistake(mistakeId)

    /** 保存举一反三笔记并标记已完成举一反三思考 */
    suspend fun saveVariation(mistake: MistakeEntity, note: String) {
        mistakeDao.update(mistake.copy(variationNote = note, hasVariation = true))
    }

    fun getTotalReviewCount(): Flow<Int> = mistakeDao.getTotalReviewCount()

    /** 已完成举一反三思考的错题数 */
    fun getVariationCount(): Flow<Int> = mistakeDao.getVariationCount()

    /** 某时间点之后的复习次数（用于统计今日复习量） */
    fun getReviewCountSince(since: Long): Flow<Int> = mistakeDao.getReviewCountSince(since)

    /** 某时间点之后的复习记录列表（用于按日统计复习趋势） */
    fun getReviewsSince(since: Long): Flow<List<MistakeReviewEntity>> = mistakeDao.getReviewsSince(since)
}
