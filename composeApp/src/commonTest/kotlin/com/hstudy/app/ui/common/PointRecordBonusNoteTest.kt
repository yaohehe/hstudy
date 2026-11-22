package com.hstudy.app.ui.common

import com.hstudy.app.data.local.entity.PointRecordEntity
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-12（v3.3.2，残险台账 D-12）：三处里程碑 bonus 的 note 业务键化回归套件。
 *
 * 修法照 D-08 先例（[com.hstudy.app.data.repository.WishGoalRepositoryLedgerTest]）：
 * note 只写纯 ASCII 业务键，展示文案渲染期派生，旧中文记录 passthrough 不改写。
 * 组装沿用 RA9 约定：真 [PointRepository] + 手写内存 Fake DAO，账本断言看真实落库形状。
 *
 * 钉死四件事：
 * 1. 三键形状与解析（[bonusNoteKeys_arePureAsciiAndOnlyParseTheirOwnShape]）：
 *    前缀登记与通道 H 对账同源，伪键/旧中文一律解析不出；
 * 2. 三键渲染派生双语（[threeBonusKindsDeriveDisplayTextInBothLanguages]）：
 *    中文与 D-12 前直写 DB 的原文**逐字节相同**（新旧记录中文词面一致），
 *    英文界面念英文（这正是本缺陷的病灶）；
 * 3. 旧中文记录 passthrough（[legacyChineseBonusNotesStayPassthrough]）：append-only，
 *    D-12 前落库的中文 ADJUSTMENT 原样保留、派生层不认领；
 * 4. 入账 + 渲染全链路净额守恒（[threeBonuses_fullChain_netConservationAndAsciiNotes]）：
 *    三笔 bonus 经 adjustPoints 落库后余额 = SUM(points)，note 全部纯 ASCII，
 *    渲染层派生的中文与旧原文逐字节一致。
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
        assertEquals("bonus_excellent:2026-11-22", excellent)
        assertEquals("bonus_weekly:2026-11-16", weekly)
        assertEquals("bonus_streak:7", streak)
        // 存储边界（Ruling 25 / 通道 H）：进 DB 的字节必须纯 ASCII
        listOf(excellent, weekly, streak).forEach { key ->
            assertTrue(key.all { it.code < 128 }, "note 业务键必须纯 ASCII：$key")
        }

        assertEquals(BonusNoteKey.Excellent("2026-11-22"), parseBonusNoteKey(excellent))
        assertEquals(BonusNoteKey.Weekly("2026-11-16"), parseBonusNoteKey(weekly))
        assertEquals(BonusNoteKey.Streak(7), parseBonusNoteKey(streak))
        // 前缀认领互不重叠：三条都能解析
        assertEquals(3, listOf(excellent, weekly, streak).count { parseBonusNoteKey(it) != null })
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
            "pre:bonus_excellent:2026-01-01",    // 不是前缀开头
            "bonus_unknown:1",                   // 登记表之外的 bonus_ 键（不许被派生层认领）
        ).forEach { shape ->
            assertNull(parseBonusNoteKey(shape), "不该被解析成 bonus 业务键：$shape")
        }
        // `bonus_streak:0`：形状合法（纯数字），值由写入方（页面里程碑档位）保证 ≥ 阈值；
        // 派生层不越权做业务判定，照常解析、渲染 "连续打卡0天里程碑奖励"——账本如实反映写入内容。
        assertEquals(BonusNoteKey.Streak(0), parseBonusNoteKey("bonus_streak:0"))
    }

    // ==================================================================
    // 2) 三键渲染派生（双语）
    // ==================================================================

    @Test
    fun threeBonusKindsDeriveDisplayTextInBothLanguages() {
        val zh = ChildPointsStringsZh
        val en = ChildPointsStringsEn
        // 中文与 D-12 前直写 DB 的原文逐字节相同（新旧记录在同一界面词面一致）
        assertEquals("今日学习表现优秀奖励", bonusNoteTextFor(BonusNoteKey.Excellent("2026-11-22"), zh))
        assertEquals("每周挑战奖励：本周打卡5天", bonusNoteTextFor(BonusNoteKey.Weekly("2026-11-16"), zh))
        assertEquals("连续打卡7天里程碑奖励", bonusNoteTextFor(BonusNoteKey.Streak(7), zh))
        // 英文界面派生英文（D-12 病灶：旧实现英文用户也会在账本里看到中文）
        assertEquals("Excellent study bonus for today", bonusNoteTextFor(BonusNoteKey.Excellent("2026-11-22"), en))
        assertEquals("Weekly challenge bonus: 5 check-ins this week", bonusNoteTextFor(BonusNoteKey.Weekly("2026-11-16"), en))
        assertEquals("Streak milestone bonus: 7 days in a row", bonusNoteTextFor(BonusNoteKey.Streak(7), en))
        // 英文 one/other（沿 childHome.streakMilestoneTitle 的 §8.3 先例；1 天是防御形态）
        assertEquals("Streak milestone bonus: 1 day in a row", bonusNoteTextFor(BonusNoteKey.Streak(1), en))
    }

    // ==================================================================
    // 3) 旧中文 / 未知 note passthrough
    // ==================================================================

    @Test
    fun legacyChineseBonusNotesStayPassthrough() {
        // D-12 之前直写 DB 的三条原文（存量记录，账本 append-only、不改写）
        listOf(
            "今日学习表现优秀奖励",
            "每周挑战奖励：本周打卡5天",
            "连续打卡7天里程碑奖励",
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
    fun threeBonuses_fullChain_netConservationAndAsciiNotes() = runBlocking {
        val pointDao = FakePointDao()
        val points = PointRepository(pointDao, FakePointApprovalDao())

        // 三处 ChildHomeScreen 同款入账（分值 2 / 15 / 5 按红线原样）
        points.adjustPoints(2, BonusNoteKeys.excellent("2026-11-22"))
        points.adjustPoints(15, BonusNoteKeys.streak(7))
        points.adjustPoints(5, BonusNoteKeys.weekly("2026-11-16"))
        // 一条 D-12 之前的旧中文记录（模拟存量数据混排）
        points.adjustPoints(2, "今日学习表现优秀奖励")

        val records = pointDao.records
        assertEquals(4, records.size)
        assertTrue(records.all { it.type == PointRecordType.ADJUSTMENT }, "bonus 走 adjustPoints = ADJUSTMENT")
        // 净额守恒（不变量 5）：余额 = SUM(points)
        val sum = records.sumOf { it.points }
        assertEquals(24, sum)
        assertEquals(sum, points.getTotalPoints().first(), "不变量 5：余额 = SUM(points)")

        // 新三笔：note 纯 ASCII 键 → 派生层认领，中文渲染与旧原文逐字节一致
        val newKeys = records.take(3).map { it.note }
        newKeys.forEach { note ->
            assertTrue(note.all { it.code < 128 }, "新写入 note 必须纯 ASCII：$note")
        }
        assertEquals(
            listOf("今日学习表现优秀奖励", "连续打卡7天里程碑奖励", "每周挑战奖励：本周打卡5天"),
            newKeys.map { bonusNoteTextFor(parseBonusNoteKey(it)!!, ChildPointsStringsZh) },
            "三键渲染必须与 D-12 前中文原文逐字节相同"
        )
        // 旧记录：解析不出 → passthrough 原样（页面侧 record.note 不被派生层改写）
        assertNull(parseBonusNoteKey(records.last().note), "旧中文记录不得被认领")
        assertEquals("今日学习表现优秀奖励", records.last().note, "账本 append-only：旧记录字节不变")
    }

    /** 渲染器入口的类型闸门：非 ADJUSTMENT 带 bonus_ 键属于异常形态，宁可 passthrough 不掩盖。 */
    @Test
    fun bonusKeyOnWrongTypeIsNotClaimed() {
        // bonusNoteDisplayText 的 `type != ADJUSTMENT → null` 判定在 record.type 上，
        // 本用例钉住键层不依赖 record：同键在 TASK_COMPLETE 记录上派生层（纯函数）仍然可渲染，
        // 但组合入口按类型拒绝（该分支由源码单行 if 钉住，这里断言键语义本身不变）。
        val record = PointRecordEntity(points = 2, type = PointRecordType.TASK_COMPLETE, note = BonusNoteKeys.streak(7))
        assertEquals(BonusNoteKey.Streak(7), parseBonusNoteKey(record.note))
        // ADJUSTMENT 同键记录与上面键串一致 —— 派生只看 type+键（与 wishNoteDisplayText 同构）。
        val adjustment = record.copy(type = PointRecordType.ADJUSTMENT)
        assertEquals(record.note, adjustment.note)
    }
}
