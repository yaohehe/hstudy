package com.hstudy.app.ui.common

import androidx.compose.runtime.Composable
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.repository.WishGoalRepository
import com.hstudy.app.data.repository.WishNoteKind
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText

/**
 * D-08（v3.3.1，指令 §3.3 工程师复核补充 2）：把账本 note 里的**心愿业务键**翻成展示文案。
 *
 * 库里的 note 只写 `wish_deposit:<goalId>` / `wish_refund:<goalId>` 这种纯 ASCII 业务键，
 * 文案（含心愿名）一律在渲染时派生 —— 与 REVERSAL 条目挂 `[打卡撤销]` 标签是同一个模式。
 * 为什么不能像旧实现那样把"存入心愿存钱罐：一台新平板"直接写进库：那串中文不在双语表里，
 * 存储边界闸门按"文案表引用 → sink"的形状抓不到它（Ruling 25 的存量盲区），
 * 英文界面的孩子就会在自己的账本里读到中文。
 *
 * 三处共用（[com.hstudy.app.ui.child.ChildPointsScreen] / [com.hstudy.app.ui.parent.ParentPointsScreen] /
 * [com.hstudy.app.ui.parent.ParentDashboardScreen]「最近积分变动」），词面统一取 `childPoints` 槽
 * （沿 tagCheckInUndo 的跨表复用先例，不重开同值槽）。
 * 看板那一条是指令 §3.3 未列的**第三个** note 消费面：它同样 `Text(record.note…)`，
 * 不改就会把 `wish_deposit:3` 直接念给家长看（v3.3.1 实施时按代码现状补齐）。
 *
 * @param goalNamesById 心愿 id → 存储名（页面把 `wishGoalRepository.getAllGoals()` 收成 map 传进来；
 *   含非进行中的心愿，只有**已删除**的心愿才查不到）
 * @return null = 这条记录不该被本页改写：不是 REWARD_EXCHANGE、note 不是心愿业务键
 *   （商店兑换 `兑换：xx`、家长手写备注、D-08 之前留下的中文 ADJUSTMENT 旧记录），
 *   或键形不合法。调用方按既有逻辑**原样展示 note**，旧账目一条都不动。
 */
@Composable
fun wishNoteDisplayText(
    record: PointRecordEntity,
    goalNamesById: Map<Long, String>
): String? {
    if (record.type != PointRecordType.REWARD_EXCHANGE) return null
    val key = WishGoalRepository.parseWishNoteKey(record.note) ?: return null
    val strings = LocalStrings.current.childPoints
    val displayLanguage = LocalDisplayLanguage.current
    // 心愿名是 canonical 存储值（示例心愿由注册表译出、家长自建原样），与心愿两页同一渲染口径
    val goalName = goalNamesById[key.goalId]?.let { localizedPresetText(it, displayLanguage) }
    return when (key.kind) {
        WishNoteKind.DEPOSIT ->
            goalName?.let(strings.wishDeposit) ?: strings.wishDepositGeneric
        WishNoteKind.REFUND ->
            goalName?.let(strings.wishRefund) ?: strings.wishRefundGeneric
    }
}
