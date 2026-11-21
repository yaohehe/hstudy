package com.hstudy.app.ui.child

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/**
 * D-09（v3.3.1 指令 §4.3）首页系统奖励防重入守卫。
 *
 * [ChildHomeScreen] 的三处系统奖励入口（连续打卡里程碑 / 每周挑战 / 学习表现优秀奖励）
 * 共用同一实例的 in-flight 互斥，覆盖"同一协程重入与跨帧双击"两种重入路径：
 *
 * - **跨帧双击 / 并发协程**：claimed 标记经 DataStore flow 回流到 UI 有延迟，两次快速点击
 *   可能各自启动一个领取协程且都看到"未领取"。第一把 [Mutex.tryLock] 成功者进入临界区；
 *   后来者 **立即返回 false、不排队等锁** —— 否则锁释放后它会补发第二份分（正是本缺陷根因）。
 * - **同一协程重入 / 陈旧 UI 状态重试**：[tryClaim] 是 UserPreferences 的原子
 *   读取-判定-写入（CAS，DataStore edit 事务串行化），只有真正赢得"首次置位"的那次返回 true，
 *   已置位后的一切再领取都返回 false，与 UI 上按钮是否还可见无关。
 *
 * 时序为"先原子置 claimed、再入账"（[onClaimed] 里才调 adjustPoints）：
 * 入账失败（理论不可达）**不回滚 claimed** —— 与账本 append-only 口径一致，该奖励作废、
 * 绝不双发；[onClaimed] 抛出的异常在此捕获并落日志（补偿 = 释放互斥 + 复位进行中状态，
 * 不让协程异常击穿 UI 作用域），裁决披露见 task-5-report.md。
 *
 * [isClaimingBonus] 与互斥锁同源（要求 3"视觉禁用与防抖同一状态"）：三处按钮一律
 * `enabled = !guard.isClaimingBonus`，锁被持有期间三个入口同时禁用。
 */
class BonusClaimGuard {

    private val claimMutex = Mutex()

    /** 是否有领取流程在飞（持锁期间为 true）。按钮 enabled 直接读它。 */
    var isClaimingBonus by mutableStateOf(false)
        private set

    /**
     * 执行一次"置位 → 入账"的原子领取。
     *
     * @param tryClaim 原子 CAS 置位（UserPreferences 的 tryClaim* 系列）；返回 false 表示
     *                 该奖励已被领过，本次不产生任何资金动作。
     * @param onClaimed 仅在 [tryClaim] 返回 true 后执行的入账序列
     *                  （adjustPoints + 成就重评 + snackbar 提示等 UI 反馈）。
     * @return true = 本次调用完成了置位并入账；false = 在飞互斥拒绝（未排队）、CAS 未赢、
     *         或入账抛异常被吞（状态已复位）。
     */
    suspend fun claim(tryClaim: suspend () -> Boolean, onClaimed: suspend () -> Unit): Boolean {
        if (!claimMutex.tryLock()) return false
        try {
            isClaimingBonus = true
            if (!tryClaim()) return false
            onClaimed()
            return true
        } catch (ce: CancellationException) {
            throw ce // 作用域被取消：照常向上传播，由协程框架收尾，不误报为业务失败
        } catch (e: Exception) {
            // 入账异常（理论不可达）：claimed 保持置位、不回滚（append-only 语义），
            // 捕获吞掉以保证锁与 isClaimingBonus 在 finally 里复位，页面不崩。
            println("[BonusClaimGuard] W: 系统奖励置位后入账异常，该奖励作废不双发：${e::class.simpleName}: ${e.message}")
            return false
        } finally {
            isClaimingBonus = false
            claimMutex.unlock()
        }
    }
}
