package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.DirectTransactionRunner
import com.hstudy.app.data.local.dao.WishGoalDao
import com.hstudy.app.data.local.entity.WishGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

/**
 * 心愿业务键的种类：存入（消耗孩子余额）/ 退回（删除心愿时原路退还）。
 * 明细页据此派生展示文案，见 [WishGoalRepository.parseWishNoteKey]。
 */
enum class WishNoteKind { DEPOSIT, REFUND }

/** [WishGoalRepository.parseWishNoteKey] 的解析结果：note 业务键 → 心愿 id + 种类。 */
data class WishNoteKey(val goalId: Long, val kind: WishNoteKind)

/**
 * 心愿存钱罐（家长建目标、孩子往里存积分）。
 *
 * ## D-08（v3.3.1，指令 §3.3）改的两件事
 * 1. **存入不再自己写流水**：以前这里直接 `pointDao.insertPointRecord(type = ADJUSTMENT, note = "存入心愿存钱罐：…")`，
 *    孩子的自主消耗被记进"家长调整"口径，家长报表与周报因此失真。现在走
 *    [PointRepository.spendPoints] 这个 REWARD_EXCHANGE 权威入口，余额校验也在同一处完成。
 * 2. **删除不再吞掉已存积分**：以前 [deleteGoal] 是裸删 DAO，孩子攒进心愿的 savedPoints 直接蒸发、
 *    账本上零流水。现在删除前先用 [PointRepository.refundSpentPoints] 原路退还一笔正向记录再删。
 *
 * ## note 的存储边界（指令 §3.3 工程师复核补充 2 / Ruling 25）
 * 两条流水的 note **只写纯 ASCII 业务键**（[wishDepositNote] / [wishRefundNote]），不写"存入心愿存钱罐：xx"
 * 这类展示文案——文案不在双语表里、闸门抓不到，切到英文的孩子会在自己的账本里看到中文。
 * 展示文案由积分明细页按 `type + [parseWishNoteKey]` 派生（与 REVERSAL 的 [打卡撤销] 标签同型）。
 */
class WishGoalRepository(
    private val wishGoalDao: WishGoalDao,
    private val pointRepository: PointRepository,
    private val transactionRunner: DbTransactionRunner = DirectTransactionRunner
) {

    fun getAllGoals(): Flow<List<WishGoalEntity>> = wishGoalDao.getAllGoals()

    fun getActiveGoals(): Flow<List<WishGoalEntity>> = wishGoalDao.getActiveGoals()

    suspend fun getGoalById(id: Long): WishGoalEntity? = wishGoalDao.getGoalById(id)

    suspend fun addGoal(goal: WishGoalEntity): Long = wishGoalDao.insert(goal)

    suspend fun updateGoal(goal: WishGoalEntity) = wishGoalDao.update(goal)

    /**
     * 删除心愿目标：进行中的心愿若已有已存积分，**先原路退还再删**。
     *
     * 退款只在 `isActive && savedPoints > 0` 时发生，与家长端删除确认弹窗
     * （ui/wish/ParentWishGoalManageScreen.kt）的提示条件同源：已达成的心愿是"已经花掉"的目标，
     * 删它不该退钱；给家长看一条不会发生的退还承诺就是撒谎。
     * 删除后明细里的 `wish_refund:<id>` 再也回查不到心愿名，页面按通用文案展示（这是 append-only
     * 账本留痕的代价，也是它不该把文案写进库的另一个理由）。
     */
    suspend fun deleteGoal(id: Long) {
        transactionRunner.runInTransaction {
            val goal = wishGoalDao.getGoalById(id) ?: return@runInTransaction
            if (goal.isActive && goal.savedPoints > 0) {
                pointRepository.refundSpentPoints(goal.savedPoints, wishRefundNote(id))
            }
            wishGoalDao.delete(id)
        }
    }

    /**
     * 存入积分到心愿存钱罐：从总积分中扣除（REWARD_EXCHANGE 消耗）、累加到 savedPoints。
     *
     * @return 是否存入成功（心愿不存在 / 金额非正 / 余额不足 → false，且**不写任何流水**）
     */
    suspend fun depositPoints(goalId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        return transactionRunner.runInTransaction {
            val goal = wishGoalDao.getGoalById(goalId) ?: return@runInTransaction false
            val remaining = (goal.targetPoints - goal.savedPoints).coerceAtLeast(0)
            if (amount > remaining || amount > pointRepository.getTotalPoints().first()) {
                return@runInTransaction false
            }

            // 扣减与进度更新在同一事务；spendPoints 的余额复读也加入最外层事务。
            if (!pointRepository.spendPoints(amount, wishDepositNote(goalId), requireSufficientBalance = true)) {
                return@runInTransaction false
            }
            wishGoalDao.addSavedPoints(goalId, amount)

            val updated = wishGoalDao.getGoalById(goalId)
            if (updated != null && updated.savedPoints >= updated.targetPoints && updated.isActive) {
                wishGoalDao.markCompleted(goalId, Clock.System.now().toEpochMilliseconds())
            }
            true
        }
    }

    companion object {
        /**
         * 存入流水的 note 业务键前缀（纯 ASCII，Ruling 25）。
         * 完整形态 `wish_deposit:<goalId>`；登记在 scripts/i18n_storage_boundary.py 的
         * NOTE_BIZKEY_PREFIXES 里，删掉这条写入会让闸门红（登记表与代码互为对账）。
         */
        const val WISH_DEPOSIT_NOTE_PREFIX = "wish_deposit:"

        /** 退款流水的 note 业务键前缀，完整形态 `wish_refund:<goalId>`。同上登记。 */
        const val WISH_REFUND_NOTE_PREFIX = "wish_refund:"

        /** 存入心愿的账本业务键（只含 id，不含心愿名——名字在展示层回查）。 */
        fun wishDepositNote(goalId: Long): String = WISH_DEPOSIT_NOTE_PREFIX + goalId

        /** 删除心愿退还的账本业务键。 */
        fun wishRefundNote(goalId: Long): String = WISH_REFUND_NOTE_PREFIX + goalId

        /**
         * 解析明细 note 里的心愿业务键。
         *
         * 只认"前缀 + 纯数字 id"这一种形状：商店的 `兑换：xx`、家长手写的备注、D-08 之前留下的
         * 中文 ADJUSTMENT 记录一律返回 null，页面据此**原样展示旧数据**（旧记录不改写）。
         */
        fun parseWishNoteKey(note: String): WishNoteKey? {
            val parsed = when {
                note.startsWith(WISH_DEPOSIT_NOTE_PREFIX) ->
                    note.substring(WISH_DEPOSIT_NOTE_PREFIX.length) to WishNoteKind.DEPOSIT
                note.startsWith(WISH_REFUND_NOTE_PREFIX) ->
                    note.substring(WISH_REFUND_NOTE_PREFIX.length) to WishNoteKind.REFUND
                else -> return null
            }
            val (rawId, kind) = parsed
            if (rawId.isEmpty() || !rawId.all { it.isDigit() }) return null
            val goalId = rawId.toLongOrNull() ?: return null
            if (goalId <= 0L) return null
            return WishNoteKey(goalId, kind)
        }
    }
}
