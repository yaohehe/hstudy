package com.hstudy.app.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.PointsBadge
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.common.wishNoteDisplayText
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

/**
 * 孩子端积分明细页（指令 §3.3.4：透明信用账本）。
 *
 * v3.3 W4 重构：
 * 1. **资产看板**（surfaceContainerHigh）：displayLarge 余额 + "当前可用积分"副标；
 *    待审行沿用 [ChildPointsStrings.pendingNote]（⏳ +M 分、"通过后计入余额"即指令的"审核后计入"注），
 *    底色 tertiaryContainer；🌟 累计获得行来自账本正分合计（只读展示，不参与任何余额判等）。
 * 2. **时间轴分组**：今天 / 昨天 / 本周更早（近 7 天滚动窗）/ 历史记录，分组标题走 Strings。
 * 3. **条目卡**：左类型图标圆角徽章 + 中描述与时间 + 右带符号分值，语义色沿主题 token
 *    （获得=secondary 绿、兑换=primary 橙、撤销/PENALTY=error 警示并附 [打卡撤销]/[未完成扣分] 标签）。
 * 余额不变量（append-only、余额=SUM）不受本页影响：本页纯读，数值全部来自既有 flows。
 *
 * v3.3.1 D-08：账本 note 不再存展示文案，心愿存入/退回两条业务键在此派生标签
 * （见 [wishNoteDisplayText]）；旧记录的 note 原样显示，本页不改写任何账目。
 */
@Composable
fun ChildPointsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.childPoints
    val childHome = LocalStrings.current.childHome
    val app = AppGraph
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    // 待家长审批的积分：尚未计入余额，单独展示，避免与可用余额混淆
    val pendingPoints by app.pointRepository.getPendingApprovalPoints().collectAsState(initial = 0)
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())
    // D-08：心愿存入/退回流水的 note 只有业务键，心愿名回查 wish_goals 后派生（含非进行中心愿）
    val wishGoals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())
    val wishNamesById = remember(wishGoals) { wishGoals.associate { it.id to it.name } }

    // 累计获得 = 账本正分合计（TASK/BONUS/加分调整；含撤销的历史获得仍是"挣到过"，扣回在流水里如实展示）
    val totalEarned = remember(records) { records.filter { it.points > 0 }.sumOf { it.points } }
    // 时间轴分桶：LazyColumn 的 LazyListScope 内容不是 composable 上下文，remember 必须在页面作用域算好
    val grouped = remember(records) { records.groupBy { pointRecordSection(it.date) } }

    Scaffold(
        topBar = {
            HStudyTopBar(title = childHome.entryPointsDetailTitle, onNavigateBack = onNavigateBack, actions = { PointsBadge(points = totalPoints) })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // ===== 资产看板（指令 §3.3.4-1）=====
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(strings.balanceTitle, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(strings.balanceValue(totalPoints), style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(strings.balanceSubtitle, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                // 待审批积分不参与余额，单独列示（tertiaryContainer 醒目标注）
                                if (pendingPoints > 0) {
                                    Text(
                                        strings.pendingNote(pendingPoints),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    strings.totalEarnedValue(totalEarned),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { Text(strings.recordsTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }

                    // ===== 时间轴分组（指令 §3.3.4-2）：今天/昨天/本周更早/历史记录 =====
                    val sectionTitles = listOf(
                        strings.sectionToday, strings.sectionYesterday, strings.sectionWeekEarlier, strings.sectionHistory
                    )
                        .mapIndexed { index, title -> index to title }
                    sectionTitles.forEach { (sectionIndex, title) ->
                        val sectionRecords = grouped[sectionIndex].orEmpty()
                        if (sectionRecords.isNotEmpty()) {
                            item(key = "section-$sectionIndex") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(sectionRecords) { record ->
                                PointHistoryItem(record = record, wishNamesById = wishNamesById)
                            }
                        }
                    }

                    if (records.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(strings.emptyRecords, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * 流水时间轴分组：0=今天 / 1=昨天 / 2=本周更早（近 7 天滚动窗内）/ 3=历史记录。
 * 纯函数，仅按记录时间分桶，不改变记录内容或顺序（列表本身已按时间倒序取数）。
 * internal：家长端积分管理页（ParentPointsScreen）跨包复用同一分组口径（指令 §3.3.4-2 两页一致）。
 */
internal fun pointRecordSection(dateMillis: Long): Int {
    fun midnightAt(offsetDays: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -offsetDays)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    return when {
        dateMillis >= midnightAt(0) -> 0
        dateMillis >= midnightAt(1) -> 1
        dateMillis >= midnightAt(6) -> 2
        else -> 3
    }
}

/**
 * 明细条目（指令 §3.3.4-3）：左圆角类型徽章 / 中描述+时间 / 右带符号分值（M3 语义色）+
 * REVERSAL/PENALTY 小标签。数值语义映射不动账本：符号永远如实来自 record.points。
 *
 * D-08：中间那行多一种来源 —— note 是心愿业务键时按 [wishNoteDisplayText] 派生，
 * 其余记录（商店兑换、家长备注、旧中文 ADJUSTMENT）照旧原样显示 note。
 */
@Composable
private fun PointHistoryItem(
    record: PointRecordEntity,
    wishNamesById: Map<Long, String>
) {
    val strings = LocalStrings.current.childPoints
    val wishNoteText = wishNoteDisplayText(record, wishNamesById)
    val icon = when (record.type) {
        PointRecordType.TASK_COMPLETE -> "✅"
        PointRecordType.BONUS -> "🎁"
        PointRecordType.ADJUSTMENT -> "✏️"
        PointRecordType.REWARD_EXCHANGE -> "🛒"
        PointRecordType.REVERSAL -> "↩️"
        PointRecordType.PENALTY -> "⚠️"
    }
    val badgeContainer = when (record.type) {
        PointRecordType.TASK_COMPLETE, PointRecordType.BONUS -> MaterialTheme.colorScheme.secondaryContainer
        PointRecordType.REWARD_EXCHANGE -> MaterialTheme.colorScheme.primaryContainer
        PointRecordType.REVERSAL, PointRecordType.PENALTY -> MaterialTheme.colorScheme.errorContainer
        PointRecordType.ADJUSTMENT -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    // 指令 §3.3.4-3 数值色彩语义：获得=绿（secondary）、兑换=橙（primary）、撤销/惩罚=警示（error）；
    // ADJUSTMENT 按符号走绿/警示（家长手动加分也是"获得"）。
    // D-08：REWARD_EXCHANGE 第一次可能出现正向记录（删除心愿的原路退还），按符号走绿，
    // 存量消耗记录全是负数，橙色不变。
    val isPositive = record.points >= 0
    val valueColor = when {
        record.type == PointRecordType.REWARD_EXCHANGE ->
            if (isPositive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        record.type == PointRecordType.REVERSAL || record.type == PointRecordType.PENALTY -> MaterialTheme.colorScheme.error
        isPositive -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 左：圆角矩形类型图标徽章
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
            Spacer(modifier = Modifier.width(12.dp))
            // 中：业务描述 + 精确时间
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // D-08：心愿业务键 → 派生文案；否则 note 原样；note 空 → 类型标签
                    wishNoteText ?: record.note.ifEmpty {
                        when (record.type) {
                            PointRecordType.TASK_COMPLETE -> strings.typeTaskComplete
                            PointRecordType.BONUS -> strings.typeBonus
                            PointRecordType.ADJUSTMENT -> strings.typeAdjustment
                            PointRecordType.REWARD_EXCHANGE -> strings.typeRewardExchange
                            PointRecordType.REVERSAL -> strings.typeReversal
                            PointRecordType.PENALTY -> strings.typePenalty
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    // P2-01：系统扣分（PENALTY）用警示色标签，与家长奖惩区分
                    color = if (record.type == PointRecordType.PENALTY)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(dateFormat.format(Date(record.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 右：大号带符号分值 + 撤销/惩罚小标签（W2 的 typePenalty 词面复用，不新建同值槽）
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isPositive) "+" else ""}${record.points}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
                when (record.type) {
                    PointRecordType.REVERSAL -> Text(
                        "[${strings.tagCheckInUndo}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    PointRecordType.PENALTY -> Text(
                        "[${strings.typePenalty}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }
            }
        }
    }
}
