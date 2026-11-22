package com.hstudy.app.ui.common

import androidx.compose.runtime.Composable
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.i18n.ChildPointsStrings
import com.hstudy.app.i18n.LocalStrings

/**
 * D-12（v3.3.2，残险台账 D-12）：把账本 note 里的**里程碑 bonus 业务键**翻成展示文案。
 *
 * 修法与 [PointRecordWishNote]（D-08）完全同型：库里只写 `bonus_excellent:<yyyy-MM-dd>` /
 * `bonus_weekly:<weekStart>` / `bonus_streak:<days>` 这种纯 ASCII 业务键，
 * 文案（含连续天数插值）一律在渲染时派生。为什么不能再像旧实现那样把
 * "今日学习表现优秀奖励" 这类中文字面量直接写进 DB：那串中文不在双语表里（它曾住在
 * `CanonicalChildHomeText`，D-12 前是 Ruling 25 承认的存量盲区），英文界面的家长/孩子
 * 会在三本账里读到中文，而键里的日期/周起始日/天数本来就是数据位，不该烧进文案。
 *
 * D-13 fix round 1（本批裁决）把另外两处同盲区入口并入本键体系：
 * `bonus_review:<days>`（ChildMistakeListScreen 连续复习里程碑）与
 * `bonus_lucky:<yyyy-MM-dd>`（ChildLuckyDrawScreen 每日幸运转盘发奖，日期取法与
 * bonus_excellent 同源 `todayString()`）。中文原文逐字节搬进 [ChildPointsStrings] 新槽，
 * D-13 Task B 直写 DB 的旧中文记录（"连续复习7天里程碑奖励" / "每日幸运抽奖"）
 * 解析不出键 → 原样 passthrough，一条都不改写。
 *
 * 五处写入点（ChildHomeScreen 的优秀表现 / 连续打卡里程碑 / 每周挑战 bonus +
 * ChildMistakeListScreen 复习里程碑 + ChildLuckyDrawScreen 抽奖发奖）都走
 * `PointRepository.adjustPoints`，落库类型恒为 ADJUSTMENT；渲染器据此把 type 也钉死
 * （见 [claimsBonusNote]），别的类型带 bonus_ 前缀属于异常形态，宁可原样展示也不派生
 * （不掩盖账本被写错的事实）。
 *
 * 三处消费面（[com.hstudy.app.ui.child.ChildPointsScreen] / [com.hstudy.app.ui.parent.ParentPointsScreen] /
 * [com.hstudy.app.ui.parent.ParentDashboardScreen]「最近积分变动」）与 D-08 心愿派生共用
 * `childPoints` 槽位模式；旧中文记录**解析不出键 → 原样展示**（append-only，一条都不改写）。
 */

/** bonus 业务键的五种形态（键里的参数位各自决定渲染插值）。 */
sealed class BonusNoteKey {
    /** `bonus_excellent:<yyyy-MM-dd>`——当日优秀表现 bonus 的领取日（幂等由 tryClaim CAS 负责，日期只是数据位）。 */
    data class Excellent(val date: String) : BonusNoteKey()

    /** `bonus_weekly:<weekStart>`——每周挑战的周起始日（yyyy-MM-dd，周一）。 */
    data class Weekly(val weekStart: String) : BonusNoteKey()

    /** `bonus_streak:<days>`——连续打卡里程碑天数（渲染要插值，解析不出正整数就不是合法键）。 */
    data class Streak(val days: Int) : BonusNoteKey()

    /** `bonus_review:<days>`——连续复习里程碑天数（D-13 fix round 1；形状与 [Streak] 同、档位表 3/7/14/30）。 */
    data class Review(val days: Int) : BonusNoteKey()

    /** `bonus_lucky:<yyyy-MM-dd>`——幸运转盘发奖日（幂等由 tryClaimDailyLuckyDraw CAS 负责，日期只是数据位）。 */
    data class Lucky(val date: String) : BonusNoteKey()
}

/**
 * 五枚 bonus 业务键前缀：登记在 scripts/i18n_storage_boundary.py 的 NOTE_BIZKEY_PREFIXES（通道 H 双向对账），
 * 删掉这里的字面量或改前缀都会把闸门打红——改名要三处同步：这里、写入点、登记表。
 */
object BonusNoteKeys {
    const val EXCELLENT_PREFIX = "bonus_excellent:"
    const val WEEKLY_PREFIX = "bonus_weekly:"
    const val STREAK_PREFIX = "bonus_streak:"
    const val REVIEW_PREFIX = "bonus_review:"
    const val LUCKY_PREFIX = "bonus_lucky:"

    /** 优秀表现 bonus 的 note 业务键（date 传 ChildHomeScreen 的 today，yyyy-MM-dd）。 */
    fun excellent(date: String): String = EXCELLENT_PREFIX + date

    /** 每周挑战 bonus 的 note 业务键（weekStart 传页面已加载好的 weekStartStr，yyyy-MM-dd）。 */
    fun weekly(weekStart: String): String = WEEKLY_PREFIX + weekStart

    /** 连续打卡里程碑 bonus 的 note 业务键（days 是里程碑档位，渲染期插回文案）。 */
    fun streak(days: Int): String = STREAK_PREFIX + days

    /** 连续复习里程碑 bonus 的 note 业务键（D-13 fix round 1；days 是复习档位 3/7/14/30，渲染期插回文案）。 */
    fun review(days: Int): String = REVIEW_PREFIX + days

    /** 幸运转盘发奖 bonus 的 note 业务键（D-13 fix round 1；date 传页面 today，与 excellent 同源 todayString()）。 */
    fun lucky(date: String): String = LUCKY_PREFIX + date
}

/** `yyyy-MM-dd` 日期位（优秀/周挑战/转盘键的参数形状；周起始日与今天都出自 yyyy-MM-dd 格式化）。 */
private val BonusDatePattern = Regex("""\d{4}-\d{2}-\d{2}""")

/** 正整数天数（打卡/复习里程碑键的参数形状；`-7`、`7x`、空串都不是键）。 */
private val BonusDaysPattern = Regex("""\d+""")

/**
 * 解析 note 里的 bonus 业务键。
 *
 * 只认"前缀 + 各自钉死的参数形状"五种形态：商店兑换 `兑换：xx`、家长手写备注、
 * D-12 之前留下的三条中文 ADJUSTMENT 旧记录（"今日学习表现优秀奖励" 等）、
 * D-13 fix round 1 之前留下的两条中文落库（"连续复习7天里程碑奖励" / "每日幸运抽奖"）、
 * 以及形状不完整的伪键（`bonus_streak:abc`、`bonus_weekly:2099-W1`）一律返回 null——
 * 调用方据此**原样展示 note**，旧账目一条都不动。
 */
fun parseBonusNoteKey(note: String): BonusNoteKey? = when {
    // startsWith 顺序无关：五个前缀互不为前缀（bonus_excellent:/bonus_weekly:/bonus_streak:/bonus_review:/bonus_lucky:）。
    note.startsWith(BonusNoteKeys.EXCELLENT_PREFIX) ->
        note.substring(BonusNoteKeys.EXCELLENT_PREFIX.length)
            .let { if (BonusDatePattern.matches(it)) BonusNoteKey.Excellent(it) else null }
    note.startsWith(BonusNoteKeys.WEEKLY_PREFIX) ->
        note.substring(BonusNoteKeys.WEEKLY_PREFIX.length)
            .let { if (BonusDatePattern.matches(it)) BonusNoteKey.Weekly(it) else null }
    note.startsWith(BonusNoteKeys.STREAK_PREFIX) ->
        note.substring(BonusNoteKeys.STREAK_PREFIX.length)
            .let { arg ->
                // 形状对但数值溢出（超 Int 的天数）同样不是合法键 → passthrough 暴露原样。
                if (BonusDaysPattern.matches(arg)) arg.toIntOrNull()?.let { BonusNoteKey.Streak(it) } else null
            }
    note.startsWith(BonusNoteKeys.REVIEW_PREFIX) ->
        note.substring(BonusNoteKeys.REVIEW_PREFIX.length)
            .let { arg ->
                if (BonusDaysPattern.matches(arg)) arg.toIntOrNull()?.let { BonusNoteKey.Review(it) } else null
            }
    note.startsWith(BonusNoteKeys.LUCKY_PREFIX) ->
        note.substring(BonusNoteKeys.LUCKY_PREFIX.length)
            .let { if (BonusDatePattern.matches(it)) BonusNoteKey.Lucky(it) else null }
    else -> null
}

/**
 * 纯函数渲染层：键 → 展示文案（双语槽在 [ChildPointsStrings]）。
 * 与 @Composable 壳拆开是 commonTest 没有 Compose UI 测试依赖的既有约定——
 * 派生逻辑直接喂表实例断言，页面侧由编译钉住接线。
 */
fun bonusNoteTextFor(key: BonusNoteKey, strings: ChildPointsStrings): String = when (key) {
    is BonusNoteKey.Excellent -> strings.bonusExcellent
    is BonusNoteKey.Weekly -> strings.bonusWeekly
    is BonusNoteKey.Streak -> strings.bonusStreak(key.days)
    is BonusNoteKey.Review -> strings.bonusReview(key.days)
    is BonusNoteKey.Lucky -> strings.bonusLucky
}

/**
 * type+note 认领判定的**纯函数**（D-13 fix round 1：审查员点名旧测试名实不符，
 * type 闸门从 @Composable 壳里抽出来直测）：只有 ADJUSTMENT 且 note 解析得出 bonus 业务键
 * 才该派生。五处写入点全部走 `adjustPoints`（恒 ADJUSTMENT），其它类型带 bonus_ 前缀
 * 属于账本被写错的异常形态——不认领、原样展示。
 */
fun claimsBonusNote(type: PointRecordType, note: String): Boolean =
    type == PointRecordType.ADJUSTMENT && parseBonusNoteKey(note) != null

/**
 * 账本条目 → bonus 派生文案；null = 本页不该改写这条记录
 * （不是 ADJUSTMENT、note 不是 bonus 业务键、或旧中文/家长手写/pending 审批描述等 passthrough 形态）。
 * 认领判定在 [claimsBonusNote] 纯函数上（commonTest 直测），这里只是 Composable 取表壳。
 * 与 [wishNoteDisplayText] 互斥不重叠：那边只认 REWARD_EXCHANGE + wish_* 键，这边只认 ADJUSTMENT + bonus_* 键。
 */
@Composable
fun bonusNoteDisplayText(record: PointRecordEntity): String? {
    if (!claimsBonusNote(record.type, record.note)) return null
    // claimsBonusNote 已保证解析非 null；null 分支不可达，仍走 ?: return null 而非 !! 保持风格一致。
    val key = parseBonusNoteKey(record.note) ?: return null
    return bonusNoteTextFor(key, LocalStrings.current.childPoints)
}
