package com.hstudy.app.ui.common

import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.repository.FakePointApprovalDao
import com.hstudy.app.data.repository.FakePointDao
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.i18n.ChildPointsStringsEn
import com.hstudy.app.i18n.ChildPointsStringsZh
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-12（v3.3.2，残险台账 D-12）：里程碑 bonus 的 note 业务键化回归套件；
 * D-13 fix round 1 扩 `bonus_review:<days>` / `bonus_lucky:<yyyy-MM-dd>` 两键同口径回归。
 *
 * 修法照 D-08 先例（[com.hstudy.app.data.repository.WishGoalRepositoryLedgerTest]）：
 * note 只写纯 ASCII 业务键，展示文案渲染期派生，旧中文记录 passthrough 不改写。
 * 组装沿用 RA9 约定：真 [PointRepository] + 手写内存 Fake DAO，账本断言看真实落库形状。
 *
 * 钉死五件事：
 * 1. 五键形状与解析（[bonusNoteKeys_arePureAsciiAndOnlyParseTheirOwnShape]）：
 *    前缀登记与通道 H 对账同源，伪键/旧中文一律解析不出；
 * 2. 五键渲染派生双语（[fiveBonusKindsDeriveDisplayTextInBothLanguages]）：
 *    中文与业务键化前直写 DB 的原文**逐字节相同**（新旧记录中文词面一致），
 *    英文界面念英文（这正是本缺陷的病灶）；
 * 3. 旧中文记录 passthrough（[legacyChineseBonusNotesStayPassthrough]）：append-only，
 *    D-12/D-13 前落库的中文 ADJUSTMENT 原样保留、派生层不认领；
 * 4. 入账 + 渲染全链路净额守恒（[fiveBonuses_fullChain_netConservationAndAsciiNotes]）：
 *    五笔 bonus 经 adjustPoints 落库后余额 = SUM(points)，note 全部纯 ASCII，
 *    渲染层派生的中文与旧原文逐字节一致；
 * 5. type 闸门（[bonusKeyOnWrongTypeIsNotClaimed]）：认领判定抽成纯函数
 *    [claimsBonusNote]（D-13 fix round 1，审查员点名旧用例名实不符），这里做真断言。
 */
class PointRecordBonusNoteTest {

    // ==================================================================
    // 1) 键形状与解析（页面写入点与派生层的唯一契约）
    // ==================================================================

    @Test
    fun bonusNoteKeys_arePureAsciiAndOnlyParseTheirOwnShape() {
        val excellent = BonusNoteKeys.excellent("2026-11-22")
        val weekly = BonusNoteKeys.weekly("2026-11-16")
        val streak = BonusNoteKeys.streak(7)
        val review = BonusNoteKeys.review(7)
        val lucky = BonusNoteKeys.lucky("2026-11-22")
        assertEquals("bonus_excellent:2026-11-22", excellent)
        assertEquals("bonus_weekly:2026-11-16", weekly)
        assertEquals("bonus_streak:7", streak)
        assertEquals("bonus_review:7", review)
        assertEquals("bonus_lucky:2026-11-22", lucky)
        // 存储边界（Ruling 25 / 通道 H）：进 DB 的字节必须纯 ASCII
        listOf(excellent, weekly, streak, review, lucky).forEach { key ->
            assertTrue(key.all { it.code < 128 }, "note 业务键必须纯 ASCII：$key")
        }

        assertEquals(BonusNoteKey.Excellent("2026-11-22"), parseBonusNoteKey(excellent))
        assertEquals(BonusNoteKey.Weekly("2026-11-16"), parseBonusNoteKey(weekly))
        assertEquals(BonusNoteKey.Streak(7), parseBonusNoteKey(streak))
        assertEquals(BonusNoteKey.Review(7), parseBonusNoteKey(review))
        assertEquals(BonusNoteKey.Lucky("2026-11-22"), parseBonusNoteKey(lucky))
        // 前缀认领互不重叠：五条 bonus 键 + 两键参数形状与 streak/excellent 各自独立
        assertEquals(5, listOf(excellent, weekly, streak, review, lucky).count { parseBonusNoteKey(it) != null })
    }

    @Test
    fun malformedBonusKeysDoNotParse() {
        listOf(
            "",                                  // 空 note（页面回退类型标签）
            "bonus_excellent:",                  // 缺日期
            "bonus_excellent:2026-11",           // 日期不完整
            "bonus_excellent:abc",               // 日期非日期形
            "bonus_weekly:2099-W1",              // 周参数不是 yyyy-MM-dd
            "bonus_streak:",                     // 缺天数
            "bonus_streak:abc",                  // 天数非数字
            "bonus_streak:-1",                   // 天数非法（负号不是键）
            "bonus_streak:1x",                   // 数字带尾巴
            "bonus_review:",                     // 复习键缺天数
            "bonus_review:abc",                  // 复习键天数非数字
            "bonus_review:7x",                   // 复习键数字带尾巴
            "bonus_lucky:",                      // 转盘键缺日期
            "bonus_lucky:2026-11",               // 转盘键日期不完整
            "bonus_lucky:2026-11-22x",           // 转盘键日期带尾巴
            "pre:bonus_excellent:2026-01-01",    // 不是前缀开头
            "bonus_unknown:1",                   // 登记表之外的 bonus_ 键（不许被派生层认领）
        ).forEach { shape ->
            assertNull(parseBonusNoteKey(shape), "不该被解析成 bonus 业务键：$shape")
        }
        // `bonus_streak:0` / `bonus_review:0`：形状合法（纯数字），值由写入方（页面里程碑档位）保证 ≥ 阈值；
        // 派生层不越权做业务判定，照常解析、渲染 "连续打卡0天里程碑奖励"——账本如实反映写入内容。
        assertEquals(BonusNoteKey.Streak(0), parseBonusNoteKey("bonus_streak:0"))
        assertEquals(BonusNoteKey.Review(0), parseBonusNoteKey("bonus_review:0"))
    }

    // ==================================================================
    // 2) 五键渲染派生（双语）
    // ==================================================================

    @Test
    fun fiveBonusKindsDeriveDisplayTextInBothLanguages() {
        val zh = ChildPointsStringsZh
        val en = ChildPointsStringsEn
        // 中文与业务键化前直写 DB 的原文逐字节相同（新旧记录在同一界面词面一致）
        assertEquals("今日学习表现优秀奖励", bonusNoteTextFor(BonusNoteKey.Excellent("2026-11-22"), zh))
        assertEquals("每周挑战奖励：本周打卡5天", bonusNoteTextFor(BonusNoteKey.Weekly("2026-11-16"), zh))
        assertEquals("连续打卡7天里程碑奖励", bonusNoteTextFor(BonusNoteKey.Streak(7), zh))
        // D-13 fix round 1 两键：中文词面 = Task B 直写 DB 旧原文（ChildMistakeListScreen / ChildLuckyDrawScreen）
        assertEquals("连续复习7天里程碑奖励", bonusNoteTextFor(BonusNoteKey.Review(7), zh))
        assertEquals("每日幸运抽奖", bonusNoteTextFor(BonusNoteKey.Lucky("2026-11-22"), zh))
        // 英文界面派生英文（D-12 病灶：旧实现英文用户也会在账本里看到中文）
        assertEquals("Excellent study bonus for today", bonusNoteTextFor(BonusNoteKey.Excellent("2026-11-22"), en))
        assertEquals("Weekly challenge bonus: 5 check-ins this week", bonusNoteTextFor(BonusNoteKey.Weekly("2026-11-16"), en))
        assertEquals("Streak milestone bonus: 7 days in a row", bonusNoteTextFor(BonusNoteKey.Streak(7), en))
        assertEquals("Review milestone bonus: 7 days of review in a row", bonusNoteTextFor(BonusNoteKey.Review(7), en))
        assertEquals("Daily lucky draw bonus", bonusNoteTextFor(BonusNoteKey.Lucky("2026-11-22"), en))
        // 英文 one/other（沿 childHome.streakMilestoneTitle 的 §8.3 先例；1 天是防御形态）
        assertEquals("Streak milestone bonus: 1 day in a row", bonusNoteTextFor(BonusNoteKey.Streak(1), en))
        assertEquals("Review milestone bonus: 1 day of review in a row", bonusNoteTextFor(BonusNoteKey.Review(1), en))
    }

    // ==================================================================
    // 3) 旧中文 / 未知 note passthrough
    // ==================================================================

    @Test
    fun legacyChineseBonusNotesStayPassthrough() {
        // 业务键化之前直写 DB 的五条原文（D-12 三条 + D-13 Task B 两条；存量记录，账本 append-only、不改写）
        listOf(
            "今日学习表现优秀奖励",
            "每周挑战奖励：本周打卡5天",
            "连续打卡7天里程碑奖励",
            "连续复习7天里程碑奖励",   // D-13 Task B 旧形态（fix round 1 起新记录写 bonus_review:7）
            "每日幸运抽奖",            // D-13 Task B 旧形态（fix round 1 起新记录写 bonus_lucky:<date>）
            "家长加分",                 // 家长手写备注
            "完成布置任务: 数学作业",    // checkIn description 形态（不参与 note 派生）
        ).forEach { legacy ->
            assertNull(parseBonusNoteKey(legacy), "旧记录不该被派生层认领：$legacy")
        }
    }

    // ==================================================================
    // 4) 入账 + 渲染全链路：净额守恒 + note 纯 ASCII + 中文词面与旧原文一致
    // ==================================================================

    @Test
    fun fiveBonuses_fullChain_netConservationAndAsciiNotes() = runBlocking {
        val pointDao = FakePointDao()
        val points = PointRepository(pointDao, FakePointApprovalDao())

        // 五处写入点同款入账（分值 2 / 15 / 5 / 8 / 3 按红线原样；转盘样例取 3 分档）
        points.adjustPoints(2, BonusNoteKeys.excellent("2026-11-22"))
        points.adjustPoints(15, BonusNoteKeys.streak(7))
        points.adjustPoints(5, BonusNoteKeys.weekly("2026-11-16"))
        points.adjustPoints(8, BonusNoteKeys.review(7))
        points.adjustPoints(3, BonusNoteKeys.lucky("2026-11-22"))
        // 一条业务键化之前的旧中文记录（模拟存量数据混排）
        points.adjustPoints(2, "今日学习表现优秀奖励")

        val records = pointDao.records
        assertEquals(6, records.size)
        assertTrue(records.all { it.type == PointRecordType.ADJUSTMENT }, "bonus 走 adjustPoints = ADJUSTMENT")
        // 净额守恒（不变量 5）：余额 = SUM(points)
        val sum = records.sumOf { it.points }
        assertEquals(35, sum)
        assertEquals(sum, points.getTotalPoints().first(), "不变量 5：余额 = SUM(points)")

        // 新五笔：note 纯 ASCII 键 → 派生层认领，中文渲染与旧原文逐字节一致
        val newKeys = records.take(5).map { it.note }
        newKeys.forEach { note ->
            assertTrue(note.all { it.code < 128 }, "新写入 note 必须纯 ASCII：$note")
        }
        assertEquals(
            listOf("今日学习表现优秀奖励", "连续打卡7天里程碑奖励", "每周挑战奖励：本周打卡5天",
                "连续复习7天里程碑奖励", "每日幸运抽奖"),
            newKeys.map { bonusNoteTextFor(parseBonusNoteKey(it)!!, ChildPointsStringsZh) },
            "五键渲染必须与业务键化前中文原文逐字节相同"
        )
        // 旧记录：解析不出 → passthrough 原样（页面侧 record.note 不被派生层改写）
        assertNull(parseBonusNoteKey(records.last().note), "旧中文记录不得被认领")
        assertEquals("今日学习表现优秀奖励", records.last().note, "账本 append-only：旧记录字节不变")
    }

    /**
     * 渲染器入口的类型闸门：非 ADJUSTMENT 带 bonus_ 键属于异常形态，宁可 passthrough 不掩盖。
     * D-13 fix round 1（审查员点名本用例旧版名实不符——只断了键语义、没真断 type 闸门）：
     * 判定抽成纯函数 [claimsBonusNote] 后在这里做真断言（type×note 四象限）。
     */
    @Test
    fun bonusKeyOnWrongTypeIsNotClaimed() {
        val keyNote = BonusNoteKeys.streak(7)
        // 真断言 type 闸门：同一条 bonus_ 键，ADJUSTMENT 认领、其它类型一律拒绝
        assertTrue(claimsBonusNote(PointRecordType.ADJUSTMENT, keyNote))
        listOf(
            PointRecordType.TASK_COMPLETE,
            PointRecordType.BONUS,
            PointRecordType.REWARD_EXCHANGE,
            PointRecordType.REVERSAL,
            PointRecordType.PENALTY,
        ).forEach { type ->
            assertFalse(claimsBonusNote(type, keyNote), "非 ADJUSTMENT 带 bonus_ 键不得被认领：$type")
        }
        // note 维度同样真断言：ADJUSTMENT 但键解析不出（旧中文 / wish 键 / 伪键）不认领
        listOf("今日学习表现优秀奖励", "连续复习7天里程碑奖励", "wish_deposit:42", "bonus_streak:abc")
            .forEach { note -> assertFalse(claimsBonusNote(PointRecordType.ADJUSTMENT, note), "不该被认领：$note") }
        // D-13 两新键同闸门：ADJUSTMENT 认领、错类型拒绝
        assertTrue(claimsBonusNote(PointRecordType.ADJUSTMENT, BonusNoteKeys.review(7)))
        assertTrue(claimsBonusNote(PointRecordType.ADJUSTMENT, BonusNoteKeys.lucky("2026-11-22")))
        assertFalse(claimsBonusNote(PointRecordType.TASK_COMPLETE, BonusNoteKeys.review(7)))
        assertFalse(claimsBonusNote(PointRecordType.REWARD_EXCHANGE, BonusNoteKeys.lucky("2026-11-22")))
    }
}
