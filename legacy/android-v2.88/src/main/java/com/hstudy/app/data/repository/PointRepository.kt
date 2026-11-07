package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 打卡结果
 */
enum class CheckInResult {
    CREATED,           // 已生成新的待审申请
    ALREADY_PENDING,   // 已有待审申请，本次复用（不会重复生成）
    ALREADY_CREDITED   // 该业务已审批通过并计分，无需再申请
}

/**
 * 积分仓库 —— 积分生命周期的唯一权威入口。
 *
 * ## 核心规则
 * 1. **打卡不计分**：任何打卡类行为只写入完成记录 + 生成待审申请，绝不写积分记录。
 *    积分只在家长审批通过时写入一次。
 * 2. **一业务一生效申请**：通过 bizKey（如 `TASK:12:2026-09-06`）约束，
 *    同一业务同时最多一条 PENDING、最多一条 APPROVED，从根本上杜绝重复累计。
 * 3. **审批幂等**：只有 PENDING 状态才能转为 APPROVED 并计分，重复审批不会重复加分。
 * 4. **撤销对称**：未审批撤销 → 申请置 CANCELLED（本就没分，无残留）；
 *    已审批撤销 → 追加 REVERSAL 反向记录冲抵，净额归零且保留审计轨迹。
 * 5. **账本 append-only**：积分记录只增不改不删，余额 = SUM(points)。
 */
class PointRepository(
    private val pointDao: PointDao,
    private val approvalDao: PointApprovalDao
) {

    fun getTotalPoints(): Flow<Int> = pointDao.getTotalPoints()

    /** 待审批积分（仅展示，绝不计入可用余额） */
    fun getPendingApprovalPoints(): Flow<Int> = approvalDao.getPendingPoints()

    /** 累计获得的总积分（只算正数）——用于学习宠物成长等长期激励 */
    fun getLifetimeEarnedPoints(): Flow<Int> = pointDao.getLifetimeEarnedPoints()

    /** 最近 7 天日均获得积分（只算正数）——用于预估心愿达成天数 */
    suspend fun getRecentDailyAvgEarned(): Double {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val earned = pointDao.getPositivePointsBetween(sevenDaysAgo, now)
        return earned / 7.0
    }

    /** 本周打卡天数（周一起算）与本周起始日期字符串——用于每周挑战 */
    suspend fun getThisWeekCheckinInfo(): Pair<Int, String> {
        val weekStart = getWeekStartTimestamp()
        val days = pointDao.getDistinctCompletionDates(weekStart).size
        val weekStartStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(weekStart))
        return days to weekStartStr
    }

    /** 累计打卡总次数——用于成就评估 */
    suspend fun getTotalCompletionCount(): Int = pointDao.getTotalCompletionCount()

    fun getAllRecords(): Flow<List<PointRecordEntity>> = pointDao.getAllPointRecords()

    fun getRecordsBetween(start: Long, end: Long): Flow<List<PointRecordEntity>> =
        pointDao.getPointRecordsBetween(start, end)

    fun getWeeklyPoints(weekStart: Long): Flow<Int> = pointDao.getWeeklyPoints(weekStart)

    // ==================================================================
    // 打卡 / 撤销 / 审批 —— 积分生命周期三大动作
    // ==================================================================

    /**
     * 任务打卡：记录完成 + 生成（或复用）待审申请。**不写入任何积分记录。**
     *
     * @return 打卡结果，供 UI 提示；无论哪种结果，本次都不会产生重复申请
     */
    suspend fun checkInForTask(taskId: Long, points: Int, note: String = ""): CheckInResult {
        val today = todayString()
        // 完成记录幂等：同一天同一任务只保留一条（用于 streak / 今日进度统计）
        if (pointDao.getCompletionForTaskOnDate(taskId, today) == null) {
            pointDao.insertCompletion(
                TaskCompletionEntity(
                    taskId = taskId,
                    pointsEarned = points,
                    note = note,
                    date = today
                )
            )
        }
        return ensureApproval(
            bizKey = bizKey("TASK", taskId, today),
            actionType = "TASK_COMPLETE",
            description = "完成任务：$note",
            points = points,
            taskId = taskId,
            date = today
        )
    }

    /**
     * 通用打卡（习惯 / 布置任务 / 每日目标等）：只生成（或复用）待审申请，**不计分**。
     */
    suspend fun checkIn(
        bizKey: String,
        actionType: String,
        description: String,
        points: Int,
        taskId: Long? = null,
        date: String = todayString()
    ): CheckInResult = ensureApproval(
        bizKey = bizKey,
        actionType = actionType,
        description = description,
        points = points,
        taskId = taskId,
        date = date
    )

    /**
     * 撤销任务打卡：
     * - 若尚未审批 → 申请置 CANCELLED，本就没有积分，无残留
     * - 若已审批通过 → 追加 REVERSAL 反向记录扣回已发积分，净额归零
     *
     * @return 实际扣回的积分数（正数；0 表示原本尚未计分）
     */
    suspend fun undoTaskCheckIn(taskId: Long): Int {
        val today = todayString()
        return undoCheckIn(bizKey = bizKey("TASK", taskId, today), taskId = taskId, date = today)
    }

    /**
     * 通用撤销打卡（习惯 / 布置任务 / 每日目标等），语义同 [undoTaskCheckIn]。
     *
     * @return 实际扣回的积分数（正数；0 表示原本尚未计分）
     */
    suspend fun undoCheckIn(bizKey: String, taskId: Long? = null, date: String = todayString()): Int {
        // 先移除完成记录（任务类打卡才有）
        if (taskId != null) {
            pointDao.deleteCompletionForTaskOnDate(taskId, date)
        }
        val approval = approvalDao.getLatestByBizKey(bizKey) ?: return 0
        return when (approval.status) {
            ApprovalStatus.PENDING -> {
                // 未审批：直接取消申请，积分从未计入，无需扣回
                approvalDao.updateReview(approval.id, ApprovalStatus.CANCELLED, null)
                0
            }
            ApprovalStatus.APPROVED -> {
                // 已审批：写反向记录冲抵（不删原记录，保留审计轨迹）
                pointDao.insertPointRecord(
                    PointRecordEntity(
                        taskId = approval.taskId,
                        points = -approval.requestedPoints,
                        type = PointRecordType.REVERSAL,
                        note = "撤销打卡扣回：${approval.description}",
                        weekStart = getWeekStartTimestamp(approval.requestedAt),
                        approvalId = approval.id
                    )
                )
                approvalDao.updateReview(approval.id, ApprovalStatus.REVOKED, approval.awardedRecordId)
                approval.requestedPoints
            }
            else -> 0 // REJECTED / CANCELLED / REVOKED 均不计分，无需处理
        }
    }

    /**
     * 审批通过并一次性发放积分（幂等）。
     *
     * 防护：
     * - 非 PENDING 状态直接忽略 → 重复点击 / 重复回调不会重复加分
     * - 同一 bizKey 已存在生效的 APPROVED 申请 → 本次作废，杜绝"一业务多次计分"
     *
     * @return 实际发放的积分（0 表示未发放）
     */
    suspend fun approveAndAward(approvalId: Long): Int {
        val approval = approvalDao.getById(approvalId) ?: return 0
        if (approval.status != ApprovalStatus.PENDING) return 0

        // 重复防护（bizKey 为空的历史数据不具备去重能力，跳过该检查）
        if (approval.bizKey.isNotEmpty() &&
            approvalDao.getActiveAwardedByBizKey(approval.bizKey) != null
        ) {
            approvalDao.updateReview(approvalId, ApprovalStatus.CANCELLED, null)
            return 0
        }

        val recordId = pointDao.insertPointRecord(
            PointRecordEntity(
                taskId = approval.taskId,
                points = approval.requestedPoints,
                type = if (approval.actionType == "TASK_COMPLETE")
                    PointRecordType.TASK_COMPLETE else PointRecordType.BONUS,
                note = approval.description,
                weekStart = getWeekStartTimestamp(approval.requestedAt),
                approvalId = approval.id
            )
        )
        approvalDao.updateReview(approvalId, ApprovalStatus.APPROVED, recordId)
        return approval.requestedPoints
    }

    /** 家长拒绝单条申请（不计分） */
    suspend fun rejectApproval(approvalId: Long) {
        val approval = approvalDao.getById(approvalId) ?: return
        if (approval.status != ApprovalStatus.PENDING) return
        approvalDao.updateReview(approvalId, ApprovalStatus.REJECTED, null)
    }

    /** 批量通过：逐条走幂等发放，返回实际发放总积分 */
    suspend fun approveAllAndAward(): Int {
        val pending = approvalDao.getPendingList()
        var total = 0
        pending.forEach { total += approveAndAward(it.id) }
        return total
    }

    /**
     * 确保同一业务只存在一条有效的待审申请。
     */
    private suspend fun ensureApproval(
        bizKey: String,
        actionType: String,
        description: String,
        points: Int,
        taskId: Long?,
        date: String
    ): CheckInResult {
        if (bizKey.isEmpty()) {
            // 无业务键的兜底路径（历史数据兼容）：直接新建申请
            approvalDao.insert(
                PointApprovalEntity(
                    actionType = actionType,
                    description = description,
                    requestedPoints = points,
                    taskId = taskId,
                    date = date
                )
            )
            return CheckInResult.CREATED
        }

        // 1) 已有待审申请 → 复用，仅同步分数与描述，绝不新建（防止取消后重打卡产生第二条）
        val pending = approvalDao.getPendingByBizKey(bizKey)
        if (pending != null) {
            if (pending.requestedPoints != points || pending.description != description) {
                approvalDao.update(pending.copy(requestedPoints = points, description = description))
            }
            return CheckInResult.ALREADY_PENDING
        }

        // 2) 已审批且仍生效 → 已计分，无需再申请
        if (approvalDao.getActiveAwardedByBizKey(bizKey) != null) {
            return CheckInResult.ALREADY_CREDITED
        }

        // 3) 新建待审申请（此前为 REJECTED / CANCELLED / REVOKED，或从未申请过）
        approvalDao.insert(
            PointApprovalEntity(
                actionType = actionType,
                description = description,
                requestedPoints = points,
                taskId = taskId,
                bizKey = bizKey,
                date = date
            )
        )
        return CheckInResult.CREATED
    }

    /** 业务唯一键：类型 + 主体 id + 日期 */
    private fun bizKey(prefix: String, subjectId: Long, date: String): String =
        "$prefix:$subjectId:$date"

    /**
     * 父母手动调整积分（家长操作本身即为审批，直接生效）
     */
    suspend fun adjustPoints(points: Int, note: String) {
        val weekStart = getWeekStartTimestamp()
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = points,
                type = PointRecordType.ADJUSTMENT,
                note = note,
                weekStart = weekStart
            )
        )
    }

    /**
     * 兑换奖励消耗积分
     */
    suspend fun spendPoints(points: Int, note: String) {
        val weekStart = getWeekStartTimestamp()
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = -points,
                type = PointRecordType.REWARD_EXCHANGE,
                note = note,
                weekStart = weekStart
            )
        )
    }

    /**
     * 检查某任务今天是否已打卡
     */
    suspend fun isTaskCompletedToday(taskId: Long): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return pointDao.getCompletionForTaskOnDate(taskId, today) != null
    }

    fun getCompletedTaskIdsForDate(date: String): Flow<List<Long>> =
        pointDao.getCompletedTaskIdsForDate(date)

    fun getCompletionsByDate(date: String): Flow<List<TaskCompletionEntity>> =
        pointDao.getCompletionsByDate(date)

    fun getCompletionsBetween(start: Long, end: Long): Flow<List<TaskCompletionEntity>> =
        pointDao.getCompletionsBetween(start, end)

    /**
     * 计算当前连续打卡天数（streak）
     * 从昨天或今天开始往回数，连续的打卡天数
     */
    suspend fun getCurrentStreak(): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        // 查看最近60天的打卡日期
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -60) }
        val dates = pointDao.getDistinctCompletionDates(cal.timeInMillis).toSet()

        var streak = 0
        val cursor = Calendar.getInstance()
        // 如果今天还没打卡，从今天开始数；否则从今天开始
        val today = dateFormat.format(Date())
        if (today !in dates) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        while (true) {
            val dateStr = dateFormat.format(cursor.time)
            if (dateStr in dates) {
                streak++
                cursor.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    /** 获取指定时刻所在周的周一 00:00:00 时间戳 */
    private fun getWeekStartTimestamp(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    companion object {
        fun getTodayString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
