package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * W9 收口守卫（沿用 StringsCopyTest/PresetRegistryTest/W8CopyTest 约定）：
 * 阶段 1 页标题 sweep 的注册表取词、成就解锁 snackbar 的按 type 解析、
 * 同步/备份诊断适配器（spec §9）的双语 pin 与 canonical 格式钉死，
 * 以及平台发出点取词的两条 pin（分享面板标题 [ShareStrings]、日程通知兜底标题
 * [ScheduleAlarmStrings.alarmTitle]）。
 *
 * 前三条测试都**不渲染 UI**：它们钉的是渲染点使用的纯函数/键控表——
 * [localizedPresetText]、[achievementCopy] 的解析式、[SyncDiagnosticAdapter.localize]。
 * 页面上的接入点（`ChildTaskListScreen`、`ChildHomeScreen`、`ParentSettingsScreen` 等）
 * 由代码审阅与 CJK 扫描覆盖，单测无法证明 Compose 实际绘制结果。
 *
 * 后两条（[shareTitlePinned] / [alarmTitlePinned]）钉的是 androidMain 发出点用的取值来源：
 * 平台接线本身不可单测（闸门也不读平台文件），实际弹出门面属 V-73/V-74 人工项。
 */
class W9CopyTest {

    /**
     * 阶段 1 页标题 sweep：既有 canonical 任务/奖励标题经 [localizedPresetText] 两语 pin。
     *
     * 抽 3 个注册表既有标题值（任务标题 ×2、奖励名 ×1），并钉"未命中 = 原样"：
     * 家长自建标题不在注册表里，英文态不得被意外改写（Ruling 25 的存储/显示边界）。
     * W9 T1 修复补 1 条专注目标 canonical「自由学习」（`focus_sessions.taskTitle` 存储值，
     * `ChildEfficiencyReportScreen` 分布行以它为分组键渲染）：注册表此前未收录，
     * 英文态在报表里回落中文；本 pin 与 `PresetRegistryTest.registryKeysEqualSeedCanonicalsExactly`
     * 的 `w9FreeStudy` 组互为保险。
     */
    @Test
    fun taskRewardTitlesWrappedPinned() {
        // 任务标题（TaskRepository.getPresets 既有 canonical）
        assertEquals("Finish today's homework", localizedPresetText("完成当天作业", DisplayLanguage.EN))
        assertEquals("Pack the school bag", localizedPresetText("整理书包", DisplayLanguage.EN))
        // 奖励名（RewardRepository.initDefaultRewards 既有 canonical；产品口径的"看电影"是"看一场电影"）
        assertEquals("Watch a movie", localizedPresetText("看一场电影", DisplayLanguage.EN))
        // 专注目标（CanonicalFocusText.TARGET_FREE_STUDY；英文与 PomodoroStrings/EfficiencyReportStrings 同词）
        assertEquals("Free study", localizedPresetText("自由学习", DisplayLanguage.EN))

        // ZH 恒等：注册表 zh 值 == key == 输入（中文态逐字节不变）
        assertEquals("完成当天作业", localizedPresetText("完成当天作业", DisplayLanguage.ZH))
        assertEquals("整理书包", localizedPresetText("整理书包", DisplayLanguage.ZH))
        assertEquals("看一场电影", localizedPresetText("看一场电影", DisplayLanguage.ZH))
        assertEquals("自由学习", localizedPresetText("自由学习", DisplayLanguage.ZH))

        // 未命中（家长自建/历史标题）原样返回，两语都不改写
        val custom = "家长自建的任务"
        assertEquals(custom, localizedPresetText(custom, DisplayLanguage.EN))
        assertEquals(custom, localizedPresetText(custom, DisplayLanguage.ZH))
    }

    /**
     * 成就解锁 snackbar 的标题解析：`achievementCopy[displayLanguage]?.get(type)?.title ?: def.title`
     * （ChildTaskListScreen 接入式）。
     *
     * 按 `AchievementDef.type`（稳定业务键）映射而不是标题字节：英文值以 [achievementCopy] 为准
     * 逐字 pin 3 条；未知 type 回落到写入时的 canonical 标题（老数据/新成就的安全网）。
     */
    @Test
    fun achievementSnackbarTitlesResolve() {
        val en = achievementCopy[DisplayLanguage.EN]!!
        val zh = achievementCopy[DisplayLanguage.ZH]!!
        // 3 条 EN 逐字 pin（值来自 PresetRegistry.achievementCopy）
        assertEquals("Blast Off", en.getValue("first_checkin").title)
        assertEquals("Three-Day Streak", en.getValue("streak_3").title)
        assertEquals("Perfect Day", en.getValue("perfect_day").title)
        // 同 3 条的 ZH 侧（canonical）
        assertEquals("启航", zh.getValue("first_checkin").title)
        assertEquals("三日不辍", zh.getValue("streak_3").title)
        assertEquals("百分学霸", zh.getValue("perfect_day").title)

        // 与 snackbar 相同的解析式（类型 → 标题，未知 type 回落标题字节）
        fun snackbarTitle(type: String, fallback: String, lang: DisplayLanguage): String =
            achievementCopy[lang]?.get(type)?.title ?: fallback
        assertEquals("Blast Off", snackbarTitle("first_checkin", "启航", DisplayLanguage.EN))
        assertEquals("启航", snackbarTitle("first_checkin", "启航", DisplayLanguage.ZH))
        assertEquals("未知成就", snackbarTitle("unknown_type", "未知成就", DisplayLanguage.EN))

        // 两语键集一致且非空（防单侧漏译导致 snackbar 退回标题字节）
        assertEquals(zh.keys, en.keys)
        assertTrue(en.keys.isNotEmpty())
    }

    /**
     * 同步/备份诊断适配器：ZH 恒等、canonical 插值摘要重组英文、deviceName 回退映射、
     * 固定文案走注册表、未命中原样。
     *
     * 格式钉死：canonical 模板逐字对应 `FamilySyncHelper.kt:172`
     * `"已应用${deviceName}的设置：${dto.tasks.size}个任务、${dto.rewards.size}个奖励"`——
     * 下面构造样例时复刻同一形状（全角冒号/顿号、"个任务"、"个奖励"），一旦 FamilySyncHelper
     * 改格式而适配器正则未同步，本测试仍绿但真机不再命中；因此该格式是双方共用的**钉死契约**，
     * 改任何一侧都必须同时改这里（正则位于 SyncDiagnosticAdapter.APPLIED）。
     */
    @Test
    fun syncAdapterPinsBothLanguages() {
        // 格式钉死：与 FamilySyncHelper.kt:172 的插值模板同形
        val canonical = "已应用${"家长设备"}的设置：${3}个任务、${2}个奖励"
        assertEquals("已应用家长设备的设置：3个任务、2个奖励", canonical)

        // ZH 恒等（即使是可识别的插值摘要）
        assertEquals(canonical, SyncDiagnosticAdapter.localize(canonical, DisplayLanguage.ZH))

        // EN：deviceName 经注册表回退映射（家长设备 → Parent device），数字用 tasksWord / reward(s)
        assertEquals(
            "Applied settings from Parent device: 3 tasks, 2 rewards",
            SyncDiagnosticAdapter.localize(canonical, DisplayLanguage.EN)
        )
        // 孩子设备同样命中注册表；单数词形一并钉住
        assertEquals(
            "Applied settings from Child device: 1 task, 0 rewards",
            SyncDiagnosticAdapter.localize("已应用孩子设备的设置：1个任务、0个奖励", DisplayLanguage.EN)
        )
        // 家长自建设备名原样透传（仅数字词形变化）
        assertEquals(
            "Applied settings from 爸爸的手机: 1 task, 1 reward",
            SyncDiagnosticAdapter.localize("已应用爸爸的手机的设置：1个任务、1个奖励", DisplayLanguage.EN)
        )

        // 固定诊断文案：精确命中走注册表（FamilySyncHelper/BackupHelper 的 canonical 字面量）
        assertEquals("Not a parent control file", SyncDiagnosticAdapter.localize("不是家长控制文件", DisplayLanguage.EN))
        assertEquals("Not a child progress file", SyncDiagnosticAdapter.localize("不是孩子进度文件", DisplayLanguage.EN))
        assertEquals("The backup content is corrupted", SyncDiagnosticAdapter.localize("备份内容损坏", DisplayLanguage.EN))
        assertEquals("不是家长控制文件", SyncDiagnosticAdapter.localize("不是家长控制文件", DisplayLanguage.ZH))

        // 未命中（用户数据/历史消息/序列化异常文本）原样返回
        val unknown = "自定义诊断消息"
        assertEquals(unknown, SyncDiagnosticAdapter.localize(unknown, DisplayLanguage.EN))
        assertEquals(unknown, SyncDiagnosticAdapter.localize(unknown, DisplayLanguage.ZH))

        // 注册表设备名直查（W9 T1 修复：家长设置页进度卡改用 localizedPresetText(cp.deviceName, …)，
        // 此前 deviceNameDisplayLabel 只映射「家长设备」，「孩子设备」在英文态仍显中文）
        assertEquals("Child device", localizedPresetText("孩子设备", DisplayLanguage.EN))
        assertEquals("孩子设备", localizedPresetText("孩子设备", DisplayLanguage.ZH))
    }

    /**
     * 分享面板标题（spec §1/§3 的"分享标题例外"）：[ShareStrings] 两语逐字 pin。
     *
     * 它只有一个调用点（`androidMain/platform/AndroidFileSharer.kt` 的
     * `Intent.createChooser(…, ShareStrings.shareFileTitle(currentDisplayLanguageSnapshot()))`），
     * 闸门不读平台文件，所以这两条值是该例外唯一的自动化守卫；中文值必须与迁移前字面量逐字节
     * 一致（"分享文件"），英文给系统分享面板的标题短语。实际弹出语言属 V-74 人工项。
     */
    @Test
    fun shareTitlePinned() {
        assertEquals("分享文件", ShareStrings.shareFileTitle(DisplayLanguage.ZH))
        assertEquals("Share file", ShareStrings.shareFileTitle(DisplayLanguage.EN))
        assertNotEquals(
            ShareStrings.shareFileTitle(DisplayLanguage.ZH),
            ShareStrings.shareFileTitle(DisplayLanguage.EN)
        )
    }

    /**
     * 日程通知兜底标题：W5 表 [ScheduleAlarmStrings] 在 W9 新增的 `alarmTitle` 两语逐字 pin。
     *
     * 取用点：`androidMain/platform/ScheduleAlarmReceiver.kt`（`EXTRA_TITLE` 缺失/为空时回落），
     * EN 态正常路径用排程时冻结的日程标题（ZH 态标题沿用渠道默认，Ruling 7）。
     * 中文值必须与迁移前的兜底字面量"日程提醒"逐字节一致。
     * 该表原有三条（timeUp / rememberCheckIn / bodySeparator）的 pin 仍在
     * `ChildScheduleCopyTest.scheduleAlarmBodyPinsBothLanguages`，本测试不重复。
     */
    @Test
    fun alarmTitlePinned() {
        assertEquals("日程提醒", AppStringsZh.scheduleAlarm.alarmTitle)
        assertEquals("Schedule reminder", AppStringsEn.scheduleAlarm.alarmTitle)
        assertNotEquals(AppStringsZh.scheduleAlarm.alarmTitle, AppStringsEn.scheduleAlarm.alarmTitle)
    }
}
