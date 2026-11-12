package com.hstudy.app.i18n

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 跨页面文案聚合根：每页一个子表，后续任务按页扩展。
 *
 * 阶段 2 扩展点（W0 确认，无需结构改动）：新域 = 新建 XxxStrings data class + Zh/En 两份表，
 * 本 data class 加一个字段、AppStringsZh/AppStringsEn 各加一行，stringsFor 的穷举 when 自动覆盖。
 * 预设内容（种子任务/奖励/成就/场景等）不走本聚合根，走 PresetRegistry.kt 的显示注册表（方案 A）。
 */
data class AppStrings(
    val role: RoleStrings,
    val settings: SettingsStrings,
    val common: CommonStrings,
    val mistake: MistakeStrings,
    val mistakeList: MistakeListStrings,
    val mistakeDetail: MistakeDetailStrings,
    val mistakeAdd: MistakeAddStrings,
    val parentMistakeReview: ParentMistakeReviewStrings,
    val reviewReminder: ReviewReminderStrings,
    val taskList: TaskListStrings,
    val pomodoro: PomodoroStrings,
    val focus: FocusStrings,
    val childHome: ChildHomeStrings,
    val parentHome: ParentHomeStrings,
    val parentDashboard: ParentDashboardStrings,
    val parentGate: ParentGateStrings,
    val parentTaskManage: ParentTaskManageStrings,
    val parentTaskEdit: ParentTaskEditStrings,
    val parentAssignTask: ParentAssignTaskStrings,
    val parentTaskTemplate: ParentTaskTemplateStrings,
    val parentPoints: ParentPointsStrings,
    val parentApproval: ParentApprovalStrings,
    val parentRewardEdit: ParentRewardEditStrings,
    val parentRewardManage: ParentRewardManageStrings,
    val childRewardStore: ChildRewardStoreStrings,
    val childLuckyDraw: ChildLuckyDrawStrings,
    val childPoints: ChildPointsStrings,
    val childAchievements: ChildAchievementsStrings,
    val childDailyTarget: ChildDailyTargetStrings,
    val parentSchedule: ParentScheduleStrings,
    val scheduleAlarm: ScheduleAlarmStrings,
    val childHabitChallenge: ChildHabitChallengeStrings,
    val childGoalTracker: ChildGoalTrackerStrings,
    val childScheduleTimeline: ChildScheduleTimelineStrings,
    val childWishGoal: ChildWishGoalStrings,
    val parentWishGoal: ParentWishGoalStrings,
    val weeklyTracker: WeeklyTrackerStrings,
    val growthReport: GrowthReportStrings,
    val parentProofReview: ParentProofReviewStrings,
    val childProofCapture: ChildProofCaptureStrings,
    val photoViewer: PhotoViewerStrings,
    val weeklyReport: WeeklyReportStrings,
)

val AppStringsZh = AppStrings(
    role = RoleStringsZh,
    settings = SettingsStringsZh,
    common = CommonStringsZh,
    mistake = MistakeStringsZh,
    mistakeList = MistakeListStringsZh,
    mistakeDetail = MistakeDetailStringsZh,
    mistakeAdd = MistakeAddStringsZh,
    parentMistakeReview = ParentMistakeReviewStringsZh,
    reviewReminder = ReviewReminderStringsZh,
    taskList = TaskListStringsZh,
    pomodoro = PomodoroStringsZh,
    focus = FocusStringsZh,
    childHome = ChildHomeStringsZh,
    parentHome = ParentHomeStringsZh,
    parentDashboard = ParentDashboardStringsZh,
    parentGate = ParentGateStringsZh,
    parentTaskManage = ParentTaskManageStringsZh,
    parentTaskEdit = ParentTaskEditStringsZh,
    parentAssignTask = ParentAssignTaskStringsZh,
    parentTaskTemplate = ParentTaskTemplateStringsZh,
    parentPoints = ParentPointsStringsZh,
    parentApproval = ParentApprovalStringsZh,
    parentRewardEdit = ParentRewardEditStringsZh,
    parentRewardManage = ParentRewardManageStringsZh,
    childRewardStore = ChildRewardStoreStringsZh,
    childLuckyDraw = ChildLuckyDrawStringsZh,
    childPoints = ChildPointsStringsZh,
    childAchievements = ChildAchievementsStringsZh,
    childDailyTarget = ChildDailyTargetStringsZh,
    parentSchedule = ParentScheduleStringsZh,
    scheduleAlarm = ScheduleAlarmStringsZh,
    childHabitChallenge = ChildHabitChallengeStringsZh,
    childGoalTracker = ChildGoalTrackerStringsZh,
    childScheduleTimeline = ChildScheduleTimelineStringsZh,
    childWishGoal = ChildWishGoalStringsZh,
    parentWishGoal = ParentWishGoalStringsZh,
    weeklyTracker = WeeklyTrackerStringsZh,
    growthReport = GrowthReportStringsZh,
    parentProofReview = ParentProofReviewStringsZh,
    childProofCapture = ChildProofCaptureStringsZh,
    photoViewer = PhotoViewerStringsZh,
    weeklyReport = WeeklyReportStringsZh,
)
val AppStringsEn = AppStrings(
    role = RoleStringsEn,
    settings = SettingsStringsEn,
    common = CommonStringsEn,
    mistake = MistakeStringsEn,
    mistakeList = MistakeListStringsEn,
    mistakeDetail = MistakeDetailStringsEn,
    mistakeAdd = MistakeAddStringsEn,
    parentMistakeReview = ParentMistakeReviewStringsEn,
    reviewReminder = ReviewReminderStringsEn,
    taskList = TaskListStringsEn,
    pomodoro = PomodoroStringsEn,
    focus = FocusStringsEn,
    childHome = ChildHomeStringsEn,
    parentHome = ParentHomeStringsEn,
    parentDashboard = ParentDashboardStringsEn,
    parentGate = ParentGateStringsEn,
    parentTaskManage = ParentTaskManageStringsEn,
    parentTaskEdit = ParentTaskEditStringsEn,
    parentAssignTask = ParentAssignTaskStringsEn,
    parentTaskTemplate = ParentTaskTemplateStringsEn,
    parentPoints = ParentPointsStringsEn,
    parentApproval = ParentApprovalStringsEn,
    parentRewardEdit = ParentRewardEditStringsEn,
    parentRewardManage = ParentRewardManageStringsEn,
    childRewardStore = ChildRewardStoreStringsEn,
    childLuckyDraw = ChildLuckyDrawStringsEn,
    childPoints = ChildPointsStringsEn,
    childAchievements = ChildAchievementsStringsEn,
    childDailyTarget = ChildDailyTargetStringsEn,
    parentSchedule = ParentScheduleStringsEn,
    scheduleAlarm = ScheduleAlarmStringsEn,
    childHabitChallenge = ChildHabitChallengeStringsEn,
    childGoalTracker = ChildGoalTrackerStringsEn,
    childScheduleTimeline = ChildScheduleTimelineStringsEn,
    childWishGoal = ChildWishGoalStringsEn,
    parentWishGoal = ParentWishGoalStringsEn,
    weeklyTracker = WeeklyTrackerStringsEn,
    growthReport = GrowthReportStringsEn,
    parentProofReview = ParentProofReviewStringsEn,
    childProofCapture = ChildProofCaptureStringsEn,
    photoViewer = PhotoViewerStringsEn,
    weeklyReport = WeeklyReportStringsEn,
)

fun stringsFor(language: DisplayLanguage): AppStrings = when (language) {
    DisplayLanguage.ZH -> AppStringsZh
    DisplayLanguage.EN -> AppStringsEn
}

val LocalStrings: ProvidableCompositionLocal<AppStrings> =
    staticCompositionLocalOf { error("LocalStrings 未提供：请在 App 根注入") }

/**
 * 生效语言（不含 SYSTEM），供页面做"仅某种语言态才出现"的文案分支。
 *
 * 为什么页面需要它：解析规则（appLanguageFromStorage(...).resolveWith(systemLanguageTag())）
 * 只在 App 根算一次。页面若自己再算一遍，就是第二份解析实现，将来改动解析时两边会漂移，
 * 而且每次重组都要重走一次平台桥接。所以根把算好的结果一并 provide 出来，页面只读不算。
 *
 * 这里刻意不给默认值：与 [LocalStrings] 一致地 fail-fast（漏注入 = 立刻报错，而不是静默按中文渲染）。
 * 全工程只有 App 根 provide 这两个 Local（另一处 CompositionLocalProvider 在
 * ui/theme/WindowSize.kt，与文案无关），所以这条错误只会在漏接线时出现。
 * 首个使用者是家长设置页的 partialTranslationNote（spec §7，仅英文态显示）。
 */
val LocalDisplayLanguage: ProvidableCompositionLocal<DisplayLanguage> =
    staticCompositionLocalOf { error("LocalDisplayLanguage 未提供：请在 App 根注入") }
