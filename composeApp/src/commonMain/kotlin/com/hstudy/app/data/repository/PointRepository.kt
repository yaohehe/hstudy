package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

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
 * 6. **消耗只有一个入口**（D-08）：孩子自主消耗（商店兑换、喂养宠物、存入心愿存钱罐）一律走
 *    [spendPoints] → REWARD_EXCHANGE；退款走 [refundSpentPoints] 追加正向记录。
 *    [adjustPoints] 只表达"家长手动奖惩"，任何自动业务都不得借它入账。
 *
 * **note 列的边界（Ruling 25）**：note 是账本数据，不得写界面文案。仓库里现存两种合规形态——
 * ① 用户自己输入的自由备注（家长调整备注、任务名等 canonical 存储值）；
 * ② 纯 ASCII 业务键（心愿存入/退回用 `wish_deposit:<goalId>` / `wish_refund:<goalId>`，
 *    见 [WishGoalRepository]），展示文案由明细页按 `type + note` 派生。
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
        val now = Clock.System.now().toEpochMilliseconds()
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val earned = pointDao.getPositivePointsBetween(sevenDaysAgo, now)
        return earned / 7.0
    }

    /** 本周打卡天数（周一起算）与本周起始日期字符串——用于每周挑战 */
    suspend fun getThisWeekCheckinInfo(): Pair<Int, String> {
        val weekStart = getWeekStartTimestamp()
        val days = pointDao.getDistinctCompletionDates(weekStart).size
        val weekStartStr = dateStringOf(weekStart)
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

    /**
     * 家长撤回已审批申请（v3.3.1 D-10）：**委托 [undoCheckIn]，与孩子端撤销走同一条函数**，
     * 保证不变量 4（撤销对称）在家长侧同样成立——APPROVED → 追加 REVERSAL 冲抵并置 REVOKED；
     * PENDING → CANCELLED；REJECTED / CANCELLED / REVOKED 等终态幂等拒绝（返回 0、零写入）。
     *
     * 本方法只做入口适配（approvalId → 该单自己的 bizKey/taskId/date），**不含任何扣回逻辑**；
     * 扣回口径、审计轨迹与返回值语义完全以 [undoCheckIn] 为准，孩子端调用零行为变化。
     *
     * 保守拒绝：`bizKey` 为空的历史数据（v2 迁移遗留，[approveAndAward] 的防重同样跳过它们）
     * 无法被 bizKey 锚定的统一撤销定位，直接返回 0 不做任何写入——绝不凭 `getLatestByBizKey("")`
     * 的"最后一条空 bizKey"误伤别的记录。
     *
     * @return 实际扣回的积分数（正数；0 表示未扣回：终态、查无此单或空 bizKey）
     */
    suspend fun revokeApproval(approvalId: Long): Int {
        val approval = approvalDao.getById(approvalId) ?: return 0
        if (approval.bizKey.isEmpty()) return 0
        return undoCheckIn(bizKey = approval.bizKey, taskId = approval.taskId, date = approval.date)
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
        // 终审 M-2（积分不降级）：复用 PENDING 时分值取 max(已挂起, 本次请求)——
        // 同一 bizKey 先 +3（做对）后 +1（快捷复习）不得把已挂起的高分值改低，
        // 孩子已挣到的待审分值只升不降；审批仍只按最终 requestedPoints 入账一次（不变量 3）。
        val pending = approvalDao.getPendingByBizKey(bizKey)
        if (pending != null) {
            val syncedPoints = maxOf(pending.requestedPoints, points)
            if (pending.requestedPoints != syncedPoints || pending.description != description) {
                approvalDao.update(pending.copy(requestedPoints = syncedPoints, description = description))
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
     * 系统自动惩罚扣分（Antigravity 指令 P2-01 / 规则 16）：
     * 每日必须任务未完成的自动扣分专用入口，写 `-points` 且类型为 [PointRecordType.PENALTY]，
     * 与家长奖惩的 ADJUSTMENT 在账本上可区分。note 沿用落库字面量模式（Ruling 25，不进文案表）。
     */
    suspend fun applyPenalty(points: Int, note: String) {
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = -points,
                type = PointRecordType.PENALTY,
                note = note,
                weekStart = getWeekStartTimestamp()
            )
        )
    }

    /**
     * 兑换奖励消耗积分 —— **REWARD_EXCHANGE 的唯一权威写入口**。
     *
     * D-08（v3.3.1 / 指令 §3.3）：心愿存钱罐的"存入"过去直接 `pointDao.insertPointRecord(ADJUSTMENT)`，
     * 同一种"孩子自主消耗"在账本里记成了家长调整。现在它走本函数，与商店消耗同一口径。
     *
     * 新增的 [requireSufficientBalance] 是给这类**内部自主扣减**的调用方用的：余额校验写在扣减点内部，
     * 于是"校验 + 落账"只有一处、一个口径，调用方不必自己先 `getTotalPoints().first()` 再决定要不要扣
     * （那会在仓库外形成第二处校验点，两边对"余额"的读法一旦分叉就会出现扣成负数的窗口）。
     * **默认 false —— 商店/宠物四处调用点（ChildRewardStoreScreen.kt:220、
     * ChildPetScreen.kt:219/235/379）的既有语义与字节行为完全不变**（D-08 红线）。
     *
     * 诚实说明：本仓库没有把这两步包进 Room 事务，所以"读余额"与"写流水"之间仍有极窄的并发窗口
     * （单用户本地应用，且 UI 侧同一时刻只有一个扣减入口）；包事务需要给仓库注入事务执行器，
     * 超出 D-08 范围，记此备忘。
     *
     * @return true = 已写入消耗流水；false = 校验未通过（余额不足或金额非正），**未写任何记录**
     */
    suspend fun spendPoints(points: Int, note: String, requireSufficientBalance: Boolean = false): Boolean {
        if (requireSufficientBalance && (points <= 0 || getTotalPoints().first() < points)) return false
        val weekStart = getWeekStartTimestamp()
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = -points,
                type = PointRecordType.REWARD_EXCHANGE,
                note = note,
                weekStart = weekStart
            )
        )
        return true
    }

    /**
     * 原路退还一笔已经消耗掉的积分（D-08 删除心愿闭环）：
     * **新增**一条正向 REWARD_EXCHANGE 记录冲抵，绝不 UPDATE/DELETE 当初那条消耗记录。
     *
     * 为什么不用 [adjustPoints]：退款是孩子自己资金的往返，不是家长的奖惩动作。
     * 记成 ADJUSTMENT 会把这笔钱混进"家长调整"报表口径（D-08 根因 1 的镜像错误）；
     * 记成 REWARD_EXCHANGE 的正向记录，则与当初那条负向消耗同进同出，
     * 净额守恒仍由不变量 5（append-only、余额 = SUM(points)）如实托住。
     *
     * 与 [undoCheckIn] 的 REVERSAL 区别开：REVERSAL 的语义是"撤销一次已审批的**获得**"，
     * 且必须挂 approvalId 溯源；本函数没有任何审批单，撤销打卡的账目不能凭空多出一条 REVERSAL。
     *
     * @param note 只许写纯 ASCII 业务键（如 `wish_refund:12`），不得写展示文案（Ruling 25 存储边界）
     */
    suspend fun refundSpentPoints(points: Int, note: String) {
        if (points <= 0) return
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = points,
                type = PointRecordType.REWARD_EXCHANGE,
                note = note,
                weekStart = getWeekStartTimestamp()
            )
        )
    }

    /**
     * 检查某任务今天是否已打卡
     */
    suspend fun isTaskCompletedToday(taskId: Long): Boolean {
        val today = todayString()
        return pointDao.getCompletionForTaskOnDate(taskId, today) != null
    }

    fun getCompletedTaskIdsForDate(date: String): Flow<List<Long>> =
        pointDao.getCompletedTaskIdsForDate(date)

    /**
     * D-03（指令 §4.1 P1-01）：某一天任务打卡的**真实审批态**：taskId → 该任务当日 bizKey
     * （`TASK:<taskId>:<date>`，见 [checkInForTask]）对应的最新一条申请的 [ApprovalStatus]。
     *
     * 契约（由 PointRepositoryTaskApprovalStateTest 钉死）：
     * - **单查询**取当日全部 task 类审批（[PointApprovalDao.getTaskApprovalsForDate]），不逐卡 N+1；
     * - 同一 bizKey 多条（拒绝后重打卡会追加新行）取 **id 最大**一条的状态；
     * - 跨日不串：只认 date 列（与 bizKey 日期尾巴同源同值）等于 [date] 的行；
     * - 非 `TASK:` 前缀（布置任务/习惯/错题复习等）不混入。
     *
     * 只读展示流：不写库、不参与任何 bizKey 判等/幂等逻辑（展示需求不改比较语义，D-03 红线）。
     */
    fun getTaskApprovalStates(date: String): Flow<Map<Long, ApprovalStatus>> =
        approvalDao.getTaskApprovalsForDate(date).map { rows ->
            rows.groupBy { it.bizKey }
                .values
                .mapNotNull { group -> group.maxByOrNull { it.id } }
                .mapNotNull { approval -> approval.taskId?.let { taskId -> taskId to approval.status } }
                .toMap()
        }

    fun getCompletionsByDate(date: String): Flow<List<TaskCompletionEntity>> =
        pointDao.getCompletionsByDate(date)

    fun getCompletionsBetween(start: Long, end: Long): Flow<List<TaskCompletionEntity>> =
        pointDao.getCompletionsBetween(start, end)

    /**
     * 计算当前连续打卡天数（streak）
     * 从昨天或今天开始往回数，连续的打卡天数
     */
    suspend fun getCurrentStreak(): Int {
        // 查看最近60天的打卡日期
        val since = Clock.System.now().toEpochMilliseconds() - 60L * 24 * 60 * 60 * 1000
        val dates = pointDao.getDistinctCompletionDates(since).toSet()

        var streak = 0
        val tz = TimeZone.currentSystemDefault()
        var cursor = Clock.System.now().toLocalDateTime(tz).date
        // 如果今天还没打卡，从昨天开始数；否则从今天开始
        val today = todayString()
        if (today !in dates) {
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        while (true) {
            val dateStr = cursor.toString()
            if (dateStr in dates) {
                streak++
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            } else {
                break
            }
        }
        return streak
    }

    /** 获取指定时刻所在周的周一 00:00:00 时间戳 */
    private fun getWeekStartTimestamp(timeMillis: Long = Clock.System.now().toEpochMilliseconds()): Long =
        weekStartTimestamp(timeMillis)

    companion object {
        fun getTodayString(): String = todayString()

        /**
         * 终审 M-1：把完成时间戳换算成打卡日期串（yyyy-MM-dd，本地时区，与 [todayString] 同格式）。
         * 撤销侧必须用**完成当时**的日期构造 bizKey，而不是点击撤销时的今天——跨午夜后
         * 用今天构造 `ASSIGN:id:今天` 会查不到昨天的申请，撤销对称（不变量 4）在该窗口漏。
         * 公开薄封装（内部复用 internal dateStringOf，UI 跨包不可见 internal），不改任何写库/比较语义。
         */
        fun completionDateStringOf(timeMillis: Long): String = dateStringOf(timeMillis)
    }
}