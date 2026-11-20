package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.MistakeDao
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeReviewEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class MistakeRepository(
    private val mistakeDao: MistakeDao,
    private val pointRepository: PointRepository
) {

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
     *
     * 规则 6（积分规则与审批逻辑说明.md §一）：错题复习**需家长审批**——做对 +3 / 仅复习 +1。
     * P0-01（Antigravity 指令）：points > 0 时在写入复习流水后调用统一权威入口
     * [PointRepository.checkIn]（bizKey = `MISTAKE_REV:$mistakeId:$today`），
     * 同一道错题同一天重复复习复用同一条待审申请（不变量 1/2：当场不计分、一业务一生效申请）。
     * description 沿用 [checkInForTask] 的「完成任务：$note」同一模式（RA5：与既有审批记录词面一致）；
     * 该值是落库 canonical（Ruling 25），不随界面语言变化，所以不进文案表。
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
        if (points > 0) {
            val today = todayString()
            pointRepository.checkIn(
                bizKey = "MISTAKE_REV:$mistakeId:$today",
                actionType = "MISTAKE_REVIEW",
                description = "错题复习：$note",
                points = points,
                date = today
            )
        }
    }

    fun getReviewsForMistake(mistakeId: Long): Flow<List<MistakeReviewEntity>> =
        mistakeDao.getReviewsForMistake(mistakeId)

    /**
     * 保存举一反三笔记并标记已完成举一反三思考。
     *
     * 规则 7：举一反三思考 +3、需家长审批。P0-01（Antigravity 指令）：保存后调用统一权威入口
     * [PointRepository.checkIn]（bizKey = `VARIATION:${mistake.id}:$today`，同一道错题每天最多一条生效申请）。
     * description 与该业务既有审批记录词面一致（RA5：迁移前 UI 侧 submitApproval 原文
     * `"举一反三思考: ${title}"`，落库 canonical、不进文案表，Ruling 25）。
     */
    suspend fun saveVariation(mistake: MistakeEntity, note: String) {
        mistakeDao.update(mistake.copy(variationNote = note, hasVariation = true))
        val today = todayString()
        pointRepository.checkIn(
            bizKey = "VARIATION:${mistake.id}:$today",
            actionType = "VARIATION",
            description = "举一反三思考: ${mistake.title}",
            points = 3,
            date = today
        )
    }

    fun getTotalReviewCount(): Flow<Int> = mistakeDao.getTotalReviewCount()

    /** 已完成举一反三思考的错题数 */
    fun getVariationCount(): Flow<Int> = mistakeDao.getVariationCount()

    /** 某时间点之后的复习次数（用于统计今日复习量） */
    fun getReviewCountSince(since: Long): Flow<Int> = mistakeDao.getReviewCountSince(since)

    /** 某时间点之后的复习记录列表（用于按日统计复习趋势） */
    fun getReviewsSince(since: Long): Flow<List<MistakeReviewEntity>> = mistakeDao.getReviewsSince(since)
}
