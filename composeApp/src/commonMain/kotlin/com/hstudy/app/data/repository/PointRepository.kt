package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.DirectTransactionRunner
import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.local.entity.effectivePoints
import com.hstudy.app.time.AppClock
import com.hstudy.app.time.SystemAppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
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
 *    同一业务同时最多一条 PENDING、最多一条 APPROVED；任务类再用 frequencyPeriod 把
 *    legacy/canonical bizKey 归入同一频次周期，从根本上杜绝重复累计。
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
 *
 * ## 事务原子化（v3.3.2 Task C + v3.3.3 Task 4）
 * [approveAllAndAward] / [expireStalePendingApprovals] / [revokeApproval] 三个批量/撤回入口经
 * [transactionRunner] 整体包 DB 事务：批量中途任意一条抛异常 → **整批回滚**。
 * v3.3.3 Task 4 再将单条 [checkIn] / [checkInForTask] / [undoCheckIn] / [approveAndAward] /
 * [rejectApproval] / [spendPoints] / [refundSpentPoints] 全部包进同一注入事务；审核/撤销使用状态 CAS，
 * 余额在事务内复读，账本只在 CAS 成功后追加。批量入口复用事务内私有本体，
 * 避免无意义嵌套，同时保持最外层事务边界与既有回归口径。
 * append-only 余额=SUM（不变量 5）在提交与回滚两态下都成立。
 */
class PointRepository(
    private val pointDao: PointDao,
    private val approvalDao: PointApprovalDao,
    /**
     * Task C 事务执行器（见 [DbTransactionRunner] 的选型说明）。
     * 生产装配（AppGraph）注入 [com.hstudy.app.data.local.roomTransactionRunner] 平台工厂产物
     * （Android=Room `withTransaction` 真事务；iOS=Room `useWriterConnection` /
     * `immediateTransaction` 真事务，见 DbTransactionRunner.ios.kt）；
     * 默认 [DirectTransactionRunner]=直通不包事务，
     * 既有两参构造的仓库测试（RA9 手写 Fake，无 JVM sqlite）行为零变化。
     */
    private val transactionRunner: DbTransactionRunner = DirectTransactionRunner
) {

    fun getTotalPoints(): Flow<Int> = pointDao.getTotalPoints()

    /** 待审批积分（仅展示，绝不计入可用余额） */
    fun getPendingApprovalPoints(): Flow<Int> = approvalDao.getPendingPoints()

    /** 审批全量历史流；审批状态变更仍必须经由本仓库的账本联动入口。 */
    fun getAllApprovals(): Flow<List<PointApprovalEntity>> = approvalDao.getAllApprovals()

    /** 待审批笔数流；与审批页/首页共用同一 DAO 口径。 */
    fun getPendingApprovalCount(): Flow<Int> = approvalDao.getPendingCount()

    /** 累计获得收入（任务/奖励/家长奖惩正分；心愿退款等非收入正调整除外）。 */
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

    fun observeTotalCompletionCount(): Flow<Int> = pointDao.observeTotalCompletionCount()

    fun observeDistinctCompletionDates(since: Long): Flow<List<String>> =
        pointDao.observeDistinctCompletionDates(since)

    fun isPerfectDay(date: String): Flow<Boolean> = pointDao.isPerfectDay(date)

    fun getAllRecords(): Flow<List<PointRecordEntity>> = pointDao.getAllPointRecords()

    fun getRecordsBetween(start: Long, end: Long): Flow<List<PointRecordEntity>> =
        pointDao.getPointRecordsBetween(start, end)

    fun getWeeklyPoints(weekStart: Long): Flow<Int> = pointDao.getWeeklyPoints(weekStart)

    // ==================================================================
    // 打卡 / 撤销 / 审批 —— 积分生命周期三大动作
    // ==================================================================

    /**
     * Frequency-aware task check-in: records one completion per DAILY/WEEKLY/
     * MONTHLY/ONCE period and creates or reuses that period's approval request.
     * The configured bonus is included once in both snapshots. No points are
     * credited until approval.
     *
     * The legacy task-id overload below remains for existing callers/tests and
     * delegates here as a DAILY task with zero bonus.
     */
    suspend fun checkInForTask(
        task: TaskEntity,
        date: String,
        clock: AppClock
    ): CheckInResult = transactionRunner.runInTransaction {
        val localDate = LocalDate.parse(date)
        val window = task.frequency.periodWindow(localDate)
        val periodKey = task.frequency.periodKey(localDate)
        val requestedPoints = task.effectivePoints() + task.bonusPoints
        val requestedAt = clock.nowMillis()

        val activeApprovals = approvalDao.getActiveTaskApprovalsForPeriod(task.id, periodKey)
        if (activeApprovals.isNotEmpty()) {
            return@runInTransaction if (activeApprovals.any { it.status == ApprovalStatus.APPROVED }) {
                CheckInResult.ALREADY_CREDITED
            } else {
                CheckInResult.ALREADY_PENDING
            }
        }

        val existingCompletion = findCompletionForPeriod(task.id, periodKey, window)
        if (existingCompletion == null) {
            pointDao.insertCompletion(
                TaskCompletionEntity(
                    taskId = task.id,
                    completedAt = requestedAt,
                    pointsEarned = requestedPoints,
                    note = task.title,
                    date = date,
                    frequencyPeriod = periodKey
                )
            )
        }
        ensureApproval(
            bizKey = bizKey("TASK", task.id, periodKey),
            actionType = "TASK_COMPLETE",
            description = "完成任务：${task.title}",
            points = existingCompletion?.pointsEarned ?: requestedPoints,
            taskId = task.id,
            date = existingCompletion?.date ?: date,
            requestedAt = requestedAt,
            frequencyPeriod = periodKey,
            syncPendingSnapshot = false
        )
    }

    /** Legacy DAILY check-in entry retained for source and stored-key compatibility. */
    suspend fun checkInForTask(taskId: Long, points: Int, note: String = ""): CheckInResult =
        checkInForTask(
            task = TaskEntity(
                id = taskId,
                title = note,
                category = TaskCategory.BASIC,
                points = points
            ),
            date = todayString(),
            clock = SystemAppClock
        )

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
    ): CheckInResult = transactionRunner.runInTransaction {
        ensureApproval(
            bizKey = bizKey,
            actionType = actionType,
            description = description,
            points = points,
            taskId = taskId,
            date = date
        )
    }

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

    /** Frequency-aware undo, including a later-day undo of an earlier period completion. */
    suspend fun undoTaskCheckIn(task: TaskEntity, date: String): Int =
        transactionRunner.runInTransaction {
            val localDate = LocalDate.parse(date)
            val window = task.frequency.periodWindow(localDate)
            val periodKey = task.frequency.periodKey(localDate)
            val active = approvalDao.getActiveTaskApprovalsForPeriod(task.id, periodKey)
            val approval = active.firstOrNull { it.status == ApprovalStatus.APPROVED }
                ?: active.firstOrNull { it.status == ApprovalStatus.PENDING }
                ?: approvalDao.getLatestByBizKey(bizKey("TASK", task.id, periodKey))
            val completion = findCompletionForPeriod(
                taskId = task.id,
                periodKey = periodKey,
                window = window,
                preferredDate = approval?.date
            )
            undoSelectedInTx(approval, completion)
        }

    /**
     * 通用撤销打卡（习惯 / 布置任务 / 每日目标等），语义同 [undoTaskCheckIn]。
     *
     * @return 实际扣回的积分数（正数；0 表示原本尚未计分）
     */
    suspend fun undoCheckIn(bizKey: String, taskId: Long? = null, date: String = todayString()): Int =
        transactionRunner.runInTransaction { undoCheckInInTx(bizKey, taskId, date) }

    /** [undoCheckIn] 的事务内本体；批量/撤回入口已在事务中时直接复用，避免嵌套计数漂移。 */
    private suspend fun undoCheckInInTx(bizKey: String, taskId: Long?, date: String): Int {
        val approval = approvalDao.getLatestByBizKey(bizKey)
        val taskKeyPrefix = taskId?.let { "TASK:$it:" }
        val periodKey = when {
            taskKeyPrefix == null || !bizKey.startsWith(taskKeyPrefix) -> date
            approval?.frequencyPeriod != null -> approval.frequencyPeriod
            else -> bizKey.removePrefix(taskKeyPrefix)
        }
        val completion = taskId?.let {
            findCompletionForPeriod(
                taskId = it,
                periodKey = periodKey,
                window = frequencyWindowForKey(periodKey, date),
                preferredDate = approval?.date
            )
        }
        return undoSelectedInTx(approval, completion)
    }

    /** Apply the selected approval and delete only its exact completion row. */
    private suspend fun undoSelectedInTx(
        approval: PointApprovalEntity?,
        completion: TaskCompletionEntity?
    ): Int {
        completion?.let { pointDao.deleteCompletionById(it.id) }
        if (approval == null) return 0
        return when (approval.status) {
            ApprovalStatus.PENDING -> {
                // 未审批：先 CAS 抢占取消权；只有成功者返回，并发审批/拒绝不能覆盖终态。
                if (approvalDao.cancelPending(approval.id) != 1) return 0
                0
            }
            ApprovalStatus.APPROVED -> {
                // 已审批：先 CAS 转为 REVOKED，再追加反向记录；真事务保证失败时状态与账本同时回滚。
                if (approvalDao.revokeApproved(approval.id) != 1) {
                    return 0
                }
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
                approval.requestedPoints
            }
            else -> 0 // REJECTED / CANCELLED / REVOKED 均不计分，无需处理
        }
    }

    /** Canonical snapshot first; only NULL legacy snapshots may use the date-range fallback. */
    private suspend fun findCompletionForPeriod(
        taskId: Long,
        periodKey: String,
        window: FrequencyWindow,
        preferredDate: String? = null
    ): TaskCompletionEntity? = pointDao.getCompletionForTaskInFrequencyPeriod(
        taskId = taskId,
        frequencyPeriod = periodKey,
        preferredDate = preferredDate
    ) ?: pointDao.getLegacyCompletionForTaskInPeriod(
        taskId = taskId,
        startDate = window.startInclusive?.toString(),
        endDate = window.endExclusive?.toString(),
        preferredDate = preferredDate
    )

    /** Reconstruct a safe fallback window from a stored canonical key or legacy daily suffix. */
    private fun frequencyWindowForKey(periodKey: String, fallbackDate: String): FrequencyWindow {
        if (periodKey == "ONCE") return FrequencyWindow(null, null)
        val noMatch = FrequencyWindow(LocalDate(1, 1, 1), LocalDate(1, 1, 1))
        val fallback = parseDateOrNull(fallbackDate) ?: return noMatch
        val date = when {
            periodKey.startsWith("WEEK:") ->
                parseDateOrNull(periodKey.removePrefix("WEEK:")) ?: return noMatch
            periodKey.startsWith("MONTH:") ->
                parseDateOrNull("${periodKey.removePrefix("MONTH:")}-01") ?: return noMatch
            else -> parseDateOrNull(periodKey) ?: fallback
        }
        return when {
            periodKey.startsWith("WEEK:") -> TaskFrequency.WEEKLY.periodWindow(date)
            periodKey.startsWith("MONTH:") -> TaskFrequency.MONTHLY.periodWindow(date)
            else -> TaskFrequency.DAILY.periodWindow(date)
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
    suspend fun approveAndAward(approvalId: Long): Int =
        transactionRunner.runInTransaction { approveAndAwardInTx(approvalId) }

    /** [approveAndAward] 的事务内本体；批量审批共享外层事务时直接复用。 */
    private suspend fun approveAndAwardInTx(approvalId: Long): Int {
        val approval = approvalDao.getById(approvalId) ?: return 0
        if (approval.status != ApprovalStatus.PENDING) return 0

        // 重复防护（bizKey 为空的历史数据不具备去重能力，跳过该检查）
        if (approval.bizKey.isNotEmpty() &&
            approvalDao.getActiveAwardedByBizKey(approval.bizKey) != null
        ) {
            approvalDao.cancelPending(approvalId)
            return 0
        }
        if (approval.actionType == "TASK_COMPLETE" && approval.taskId != null && approval.frequencyPeriod != null &&
            approvalDao.getActiveAwardedTaskApprovalForPeriod(
                taskId = approval.taskId,
                frequencyPeriod = approval.frequencyPeriod,
                excludeId = approval.id
            ) != null
        ) {
            approvalDao.cancelPending(approvalId)
            return 0
        }

        // 先以 PENDING CAS 抢占审核权，再写账本；失败时由外层真事务回滚这次抢占。
        val reviewedAt = Clock.System.now().toEpochMilliseconds()
        if (approvalDao.updateReview(approvalId, ApprovalStatus.APPROVED, null, reviewedAt) != 1) return 0
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
        // CAS 已释放 activeBizKey；补落账本 id，保留历史审批行与审计关联。
        approvalDao.update(
            approval.copy(
                status = ApprovalStatus.APPROVED,
                reviewedAt = reviewedAt,
                awardedRecordId = recordId,
                activeBizKey = null
            )
        )
        return approval.requestedPoints
    }

    /** 家长拒绝单条申请（不计分） */
    suspend fun rejectApproval(approvalId: Long) {
        transactionRunner.runInTransaction { rejectApprovalInTx(approvalId) }
    }

    private suspend fun rejectApprovalInTx(approvalId: Long) {
        val approval = approvalDao.getById(approvalId) ?: return
        if (approval.status != ApprovalStatus.PENDING) return
        approvalDao.updateReview(approvalId, ApprovalStatus.REJECTED, null)
    }

    /**
     * 家长撤回已审批申请（v3.3.1 D-10）：**复用 [undoCheckIn] 的事务内本体，与孩子端撤销走同一闭环**，
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
     * **终审 Minor-1（D-10 同构竞态，防护同款于 D-11 review fix `c7aa445`）**：[undoCheckIn] 按
     * bizKey 锚定"最新一条"判半区，确认弹窗停留期间孩子撤销+重打卡会留下**新的同 bizKey PENDING**，
     * 裸委托会误伤它。因此入口先按 id 复读本次撤回目标：① 仍在可撤销半区（PENDING/APPROVED，
     * 终态 REVOKED/CANCELLED/REJECTED 幂等拒绝）且 ② 仍是其 bizKey 的最新一条才委托闭环；
     * 否则返回 0 零写入——UI 既有 `revokeTerminalMessage`"已终态"分支如实反馈。
     * （①取 PENDING/APPROVED 两态而非仅 APPROVED：[expireStalePendingApprovals] 对 bizKey 锚定
     * PENDING 行的复用路径依赖半区可达，且家长端 PENDING 撤回与 D-10 终态语义同形。）
     *
     * **Task C（终审残留窗口收口）**：以上"按 id 复读 → 判半区 → 判最新 → 委托 [undoCheckIn]"
     * 整段包进同一 DB 事务——弹窗停留期间孩子撤销+重打卡与撤回写库的毫秒级交错，由事务的
     * 读-判-写原子性兜住；判定逻辑本身一字未动（红线：只原子化、不改幂等/终态语义）。
     * 被 [expireStalePendingApprovals] 嵌套调用时，Room 重入机制合并到外层同一事务，不另起事务。
     *
     * @return 实际扣回的积分数（正数；0 表示未扣回：终态、查无此单、空 bizKey 或已被并发撤销/重打卡顶替）
     */
    suspend fun revokeApproval(approvalId: Long): Int =
        transactionRunner.runInTransaction { revokeApprovalInTx(approvalId) }

    /** [revokeApproval] 的判定本体——在事务内执行；判定口径保持不变，写入复用撤销事务内本体。 */
    private suspend fun revokeApprovalInTx(approvalId: Long): Int {
        val approval = approvalDao.getById(approvalId) ?: return 0
        if (approval.bizKey.isEmpty()) return 0
        // 终审 Minor-1：按 id 复读撤回目标——弹窗停留期间孩子撤销+重打卡后，这一条要么已进终态，
        // 要么已不是 bizKey 的最新一条（新 PENDING 顶替），两种都必须整单跳过，防误伤新单。
        if (approval.status != ApprovalStatus.PENDING && approval.status != ApprovalStatus.APPROVED) return 0
        if (approvalDao.getLatestByBizKey(approval.bizKey)?.id != approvalId) return 0
        val taskKeyPrefix = approval.taskId?.let { "TASK:$it:" }
        val periodKey = approval.frequencyPeriod ?: if (
            taskKeyPrefix != null && approval.bizKey.startsWith(taskKeyPrefix)
        ) {
            approval.bizKey.removePrefix(taskKeyPrefix)
        } else {
            approval.date
        }
        val completion = approval.taskId?.let {
            findCompletionForPeriod(
                taskId = it,
                periodKey = periodKey,
                window = frequencyWindowForKey(periodKey, approval.date),
                preferredDate = approval.date
            )
        }
        return undoSelectedInTx(approval, completion)
    }

    /**
     * 批量通过：整批包 DB 事务（Task C / P2-2），逐条走 [approveAndAward] 幂等发放。
     *
     * v3.3.1 前形态：`pending.forEach { approveAndAward(it.id) }` 逐条各自落库——批量中途
     * 任何一条抛异常（DAO 写失败、外部取消后的部分提交），已处理的前半批会**留在 APPROVED +
     * 已入账状态**、后半批仍 PENDING，UI 重试虽幂等但用户看到的"一次批量"实际是半批账。
     * 现在整批一个事务：中途异常 → 整批回滚（前半批的积分流水与 APPROVED 状态一并撤销，
     * 回到"一条都没批"的干净起点）；成功路径的发放顺序、bizKey 防重、返回总额均不变。
     *
     * @return 实际发放的总积分（0 = 无待审单 / 全部被幂等跳过）
     */
    suspend fun approveAllAndAward(): Int = transactionRunner.runInTransaction {
        val pending = approvalDao.getPendingList()
        var total = 0
        pending.forEach { total += approveAndAwardInTx(it.id) }
        total
    }

    // ==================================================================
    // v3.3.1 D-11（指令 §5.2）—— 超期滞留待审申请清理
    // ==================================================================

    /** D-11 提示条读数流：PENDING 且 requestedAt **严格早于** threshold 的笔数（边界值不算超期） */
    fun getStalePendingApprovalCount(threshold: Long): Flow<Int> =
        approvalDao.getStalePendingCount(threshold)

    /**
     * 超期待审一键清理（指令 §5.2）：把在 `now - [STALE_PENDING_MILLIS]` 之前提交、仍挂 PENDING 的
     * 申请逐条取消，释放虚高滞留的待审分。
     *
     * **禁止裸批量 UPDATE**：有 bizKey 的行逐条走 [revokeApproval]——即 [undoCheckIn] 同一条闭环——
     * PENDING 半区语义恰好是"未审批撤销 → 置 CANCELLED、零扣回"（不变量 4 对称 / 不变量 5
     * append-only：不写任何积分记录，余额分毫不动）。任务类 PENDING 行同时清掉当日完成记录，
     * 与孩子端撤销未审批打卡完全同形（复用既有闭环，不新写清理逻辑）。
     *
     * 空 bizKey 的 v2 遗留行无法被统一撤销锚定（[revokeApproval] 会保守拒绝返回 0），而清理
     * 必须能收敛：这类行已在查询侧精确筛出（PENDING + 超期、按 id 定位），直接调用
     * [PointApprovalDao.cancelPending] 的 PENDING → CANCELLED CAS 兜底——不涉扣回，
     * 与闭环 PENDING 半区终态一致（报告已披露此兜底）。
     *
     * **D-11 review fix（防 bizKey 新单误伤）**：快照列表逐条处理前先按 id 复读该 approval，
     * 仅当 ① 仍 PENDING（防清理瞬间被家长并发通过/拒绝后落错半区）且 ② 仍是其 bizKey 的
     * 最新一条（[PointApprovalDao.getLatestByBizKey]，防 v2 遗留同 bizKey 重复行让闭环命中
     * 更新的 APPROVED 行而追加 REVERSAL 扣回，违背弹窗"余额不变"承诺）时才执行撤销；
     * 否则整行跳过、**不计入 cleaned**，snackbar 笔数与实际置 CANCELLED 的行严格一致。
     *
     * **Task C（终审残留窗口收口）**：整个"快照 → 逐条复读 → 判定 → 落库"循环包进同一 DB 事务。
     * v3.3.1 形态里每条"按 id 复读后判半区/判最新"与后续写库之间各有毫秒窗口（清理瞬间被家长
     * 并发通过/拒绝、或被同 bizKey 新单顶替后仍按旧读判定）；现在读-判-写同事务，
     * 中途任何一条抛异常 = **整批回滚**，不存在"清了一半、cleaned 与实际落库笔数不符"的半态。
     * 循环内的 [revokeApproval] 嵌套进同一事务（Room `withTransaction` 重入合并，不另起事务）。
     * 判定逻辑（复读、半区、最新性、空 bizKey 兜底、cleaned 只计真正 CANCELLED 行）一字未动。
     *
     * @param now 判定基准时刻（测试注入固定时钟以钉 30 天边界）
     * @return 实际清理笔数（只计真正置 CANCELLED 的行；0 = 无超期单 / 二次清理 / 全部被跳过，UI snackbar 如实反馈）
     */
    suspend fun expireStalePendingApprovals(now: Long = Clock.System.now().toEpochMilliseconds()): Int =
        transactionRunner.runInTransaction {
            val stale = approvalDao.getStalePendingList(now - STALE_PENDING_MILLIS)
            var cleaned = 0
            for (approval in stale) {
                // 按 id 复读快照行：并发审批/拒绝会让这一条早已不在 PENDING 半区
                val fresh = approvalDao.getById(approval.id) ?: continue
                if (fresh.status != ApprovalStatus.PENDING) continue
                if (fresh.bizKey.isNotEmpty()) {
                    // bizKey 锚定行必须仍是该业务最新一条才可撤销：undoCheckIn 按 bizKey 取
                    // "最新一条"判半区，若同 bizKey 存在更新记录（v2 遗留重复或并发新单），
                    // 清理会命中更新的 APPROVED 行触发 REVERSAL 扣回——跳过并留待看板按真实态处理。
                    if (approvalDao.getLatestByBizKey(fresh.bizKey)?.id != fresh.id) continue
                    revokeApproval(fresh.id)
                } else {
                    // 空 bizKey 兜底：cancelPending 只允许 PENDING → CANCELLED，并释放 activeBizKey；
                    // 不涉扣回，与闭环 PENDING 半区终态一致。
                    approvalDao.cancelPending(fresh.id)
                }
                // 仅对真正落库 CANCELLED 的行计数（防御性复读，cleaned 与账本/状态变更严格对齐）
                if (approvalDao.getById(fresh.id)?.status == ApprovalStatus.CANCELLED) cleaned++
            }
            cleaned
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
        date: String,
        requestedAt: Long = Clock.System.now().toEpochMilliseconds(),
        frequencyPeriod: String? = null,
        syncPendingSnapshot: Boolean = true
    ): CheckInResult {
        if (bizKey.isEmpty()) {
            // 无业务键的兜底路径（历史数据兼容）：直接新建申请
            approvalDao.insert(
                PointApprovalEntity(
                    actionType = actionType,
                    description = description,
                    requestedPoints = points,
                    taskId = taskId,
                    date = date,
                    requestedAt = requestedAt,
                    frequencyPeriod = frequencyPeriod
                )
            )
            return CheckInResult.CREATED
        }

        // 1) 已有待审申请 → 复用，绝不新建（防止取消后重打卡产生第二条）。
        // 通用业务保留终审 M-2 的分数/描述同步；任务周期审批使用不可变快照，后续打卡不得
        //  retroactively 改写已挂起的分值或描述。
        val pending = approvalDao.getPendingByBizKey(bizKey)
        if (pending != null) {
            if (syncPendingSnapshot) {
                val syncedPoints = maxOf(pending.requestedPoints, points)
                if (pending.requestedPoints != syncedPoints || pending.description != description) {
                    approvalDao.update(pending.copy(requestedPoints = syncedPoints, description = description))
                }
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
                activeBizKey = bizKey,
                date = date,
                requestedAt = requestedAt,
                frequencyPeriod = frequencyPeriod
            )
        )
        return CheckInResult.CREATED
    }

    /** 业务唯一键：类型 + 主体 id + 日期/周期 */
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
     * v3.3.3 Task 4：余额读取与流水写入在同一注入事务内完成，默认拒绝余额不足。
     * 调用方审计：商店、宠物喂养/玩耍/场景、心愿存入均有余额前置校验，均要求不得透支；
     * 没有生产调用方需要负余额，因此无需传 `false`。确需历史式透支的调用必须显式传
     * `requireSufficientBalance = false`，不能让默认值静默放过。
     *
     * @return true = 已写入消耗流水；false = 校验未通过（余额不足或金额非正），**未写任何记录**
     */
    suspend fun spendPoints(
        points: Int,
        note: String,
        requireSufficientBalance: Boolean = true
    ): Boolean = transactionRunner.runInTransaction {
        if (points <= 0) return@runInTransaction false
        if (requireSufficientBalance && getTotalPoints().first() < points) return@runInTransaction false
        val weekStart = getWeekStartTimestamp()
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = -points,
                type = PointRecordType.REWARD_EXCHANGE,
                note = note,
                weekStart = weekStart
            )
        )
        true
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
        transactionRunner.runInTransaction {
            if (points <= 0) return@runInTransaction
            pointDao.insertPointRecord(
                PointRecordEntity(
                    points = points,
                    type = PointRecordType.REWARD_EXCHANGE,
                    note = note,
                    weekStart = getWeekStartTimestamp()
                )
            )
        }
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
     * Completed task ids for each task's actual frequency period on [date].
     * Uses one Room query for all active task ids and filters snapshots in Kotlin.
     */
    fun getCompletedTaskIdsForPeriod(
        tasks: List<TaskEntity>,
        date: LocalDate
    ): Flow<Set<Long>> {
        if (tasks.isEmpty()) return flowOf(emptySet())
        val tasksById = tasks.associateBy { it.id }
        return pointDao.getCompletionsForTasks(tasksById.keys.toList()).map { completions ->
            completions.mapNotNullTo(mutableSetOf()) { completion ->
                val task = tasksById[completion.taskId] ?: return@mapNotNullTo null
                if (completionBelongsToPeriod(completion, task, date)) completion.taskId else null
            }
        }
    }

    private fun completionBelongsToPeriod(
        completion: TaskCompletionEntity,
        task: TaskEntity,
        date: LocalDate
    ): Boolean {
        val periodKey = task.frequency.periodKey(date)
        if (completion.frequencyPeriod != null) return completion.frequencyPeriod == periodKey
        val completionDate = parseDateOrNull(completion.date) ?: return false
        return dateInWindow(completionDate, task.frequency.periodWindow(date))
    }

    private fun dateInWindow(date: LocalDate, window: FrequencyWindow): Boolean =
        (window.startInclusive == null || date >= window.startInclusive) &&
            (window.endExclusive == null || date < window.endExclusive)

    /** Daily-target approval rows for reset safety checks; includes TARGET_ALL bonus requests. */
    suspend fun getDailyTargetApprovals(date: String): List<PointApprovalEntity> =
        approvalDao.getDailyTargetApprovals(date)

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

    /**
     * Period-aware D-03 state lookup used by task cards. One approval query reads
     * all active task ids; Kotlin filters canonical snapshots (or NULL legacy daily
     * keys) by period. The latest row per bizKey is selected first; across keys,
     * APPROVED dominates, then PENDING, then the latest terminal row. This matches
     * check-in's active-award precedence instead of letting a newer legacy rejection
     * hide an approval that still blocks another award.
     */
    fun getTaskApprovalStates(
        tasks: List<TaskEntity>,
        date: LocalDate
    ): Flow<Map<Long, ApprovalStatus>> {
        if (tasks.isEmpty()) return flowOf(emptyMap())
        val tasksById = tasks.associateBy { it.id }
        return approvalDao.getTaskApprovalsForTaskIds(tasksById.keys.toList()).map { rows ->
            val latestByKey = rows.groupBy { it.bizKey }
                .mapNotNull { (key, group) -> key to group.maxByOrNull { it.id }!! }
                .toMap()
            buildMap {
                tasks.forEach { task ->
                    val approval = latestByKey.values
                        .filter { it.taskId == task.id && approvalBelongsToPeriod(it, task, date) }
                        .minWithOrNull(
                            compareBy<PointApprovalEntity> {
                                when (it.status) {
                                    ApprovalStatus.APPROVED -> 0
                                    ApprovalStatus.PENDING -> 1
                                    else -> 2
                                }
                            }.thenByDescending { it.id }
                        )
                    if (approval != null) put(task.id, approval.status)
                }
            }
        }
    }

    private fun approvalBelongsToPeriod(
        approval: PointApprovalEntity,
        task: TaskEntity,
        date: LocalDate
    ): Boolean {
        val periodKey = task.frequency.periodKey(date)
        if (approval.frequencyPeriod != null) return approval.frequencyPeriod == periodKey
        val taskKeyPrefix = "TASK:${task.id}:"
        if (!approval.bizKey.startsWith(taskKeyPrefix)) return false
        val suffix = approval.bizKey.removePrefix(taskKeyPrefix)
        if (suffix == periodKey) return true
        val approvalDate = parseDateOrNull(suffix.ifBlank { approval.date }) ?: return false
        return dateInWindow(approvalDate, task.frequency.periodWindow(date))
    }

    fun getCompletionsByDate(date: String): Flow<List<TaskCompletionEntity>> =
        pointDao.getCompletionsByDate(date)

    fun getCompletionsBetween(start: Long, end: Long): Flow<List<TaskCompletionEntity>> =
        pointDao.getCompletionsBetween(start, end)

    /**
     * 计算当前连续打卡天数（streak）
     * 从昨天或今天开始往回数，连续的打卡天数
     */
    suspend fun getCurrentStreak(): Int = observeCurrentStreak().first()

    /** 连续打卡流的唯一计算口径；数据库变化时成就进度随之刷新。 */
    fun observeCurrentStreak(): Flow<Int> {
        val since = Clock.System.now().toEpochMilliseconds() - 60L * 24 * 60 * 60 * 1000
        return pointDao.observeDistinctCompletionDates(since).map { dates ->
            val dateSet = dates.toSet()
            var streak = 0
            val tz = TimeZone.currentSystemDefault()
            var cursor = Clock.System.now().toLocalDateTime(tz).date
            if (todayString() !in dateSet) cursor = cursor.minus(1, DateTimeUnit.DAY)
            while (cursor.toString() in dateSet) {
                streak++
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            }
            streak
        }
    }

    /** 获取指定时刻所在周的周一 00:00:00 时间戳 */
    private fun getWeekStartTimestamp(timeMillis: Long = Clock.System.now().toEpochMilliseconds()): Long =
        weekStartTimestamp(timeMillis)

    companion object {
        fun getTodayString(): String = todayString()

        /**
         * D-11（指令 §5.2）超期待审判定窗口：申请提交后超过 30 天仍未审核即视为陈旧。
         * 30 天口径唯一出处——清理入口与看板提示条（threshold = now - 本常量）共用，禁止各写各的。
         */
        const val STALE_PENDING_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

        /**
         * 终审 M-1：把完成时间戳换算成打卡日期串（yyyy-MM-dd，本地时区，与 [todayString] 同格式）。
         * 撤销侧必须用**完成当时**的日期构造 bizKey，而不是点击撤销时的今天——跨午夜后
         * 用今天构造 `ASSIGN:id:今天` 会查不到昨天的申请，撤销对称（不变量 4）在该窗口漏。
         * 公开薄封装（内部复用 internal dateStringOf，UI 跨包不可见 internal），不改任何写库/比较语义。
         */
        fun completionDateStringOf(timeMillis: Long): String = dateStringOf(timeMillis)
    }
}