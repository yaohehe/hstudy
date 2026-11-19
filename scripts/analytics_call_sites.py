# -*- coding: utf-8 -*-
"""埋点 wiring 静态闸门（v3.2.0）：钉死每个调用点存在，且数据层零埋点。

单测证明不了 Compose 调用点（仓库无 Compose UI 测试依赖），此脚本是 wiring 的
持久守卫：删埋点/改枚举名会红。与 i18n 闸门同教义：恒红的闸门不会被读，故阻塞。
"""
import io
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BASE = os.path.join(ROOT, "composeApp", "src", "commonMain", "kotlin", "com", "hstudy", "app")

# (相对 BASE 路径, 必须出现的子串, 最少次数)
SITES = [
    ("ui/common/PomodoroTimer.kt", "Feature.POMODORO_START", 3),
    ("ui/focus/ChildPomodoroScreen.kt", "Feature.POMODORO_COMPLETE", 1),
    ("ui/child/ChildTaskListScreen.kt", "Feature.CHECKIN", 1),
    ("ui/child/ChildHomeScreen.kt", "Feature.CHECKIN", 1),
    ("ui/child/ChildHomeScreen.kt", "AnalyticsEvent.PairingCompleted", 1),
    ("ui/target/ChildDailyTargetScreen.kt", "Feature.CHECKIN", 2),
    ("ui/habit/ChildHabitChallengeScreen.kt", "Feature.CHECKIN", 2),
    ("ui/child/ChildLuckyDrawScreen.kt", "Feature.LUCKY_DRAW", 1),
    ("ui/pet/ChildPetScreen.kt", "Feature.PET_CARE", 2),
    ("ui/wish/ChildWishGoalScreen.kt", "Feature.WISH_SUBMIT", 1),
    ("ui/parent/ParentApprovalScreen.kt", "Feature.APPROVAL_ACTION", 3),
    ("ui/parent/ParentSettingsScreen.kt", "AnalyticsEvent.BackupExported", 1),
    ("ui/mistake/ReviewReminderReceiver.kt", "AnalyticsEvent.ReminderScheduled", 1),
    ("ui/parent/WeeklyReportReceiver.kt", "AnalyticsEvent.ReminderScheduled", 1),
    ("ui/schedule/ScheduleAlarmHelper.kt", "AnalyticsEvent.ReminderScheduled", 1),
    ("App.kt", "AnalyticsEvent.ScreenView", 1),
    ("AppGraph.kt", "AnalyticsEvent.LanguageChanged", 1),
    ("AppGraph.kt", "AnalyticsReporter.setEnabled", 1),
    ("ui/parent/ParentSettingsScreen.kt", "setAnalyticsEnabled", 1),
    ("ui/parent/ParentSettingsScreen.kt", "strings.analyticsTitle", 1),
]

failures = []
for rel, needle, minimum in SITES:
    path = os.path.join(BASE, rel)
    with open(path, encoding="utf-8") as fh:
        count = fh.read().count(needle)
    if count < minimum:
        failures.append("%s: expected >=%d occurrence(s) of %r, found %d" % (rel, minimum, needle, count))

data_dir = os.path.join(BASE, "data")
for dirpath, _, filenames in os.walk(data_dir):
    for name in filenames:
        if not name.endswith(".kt"):
            continue
        with open(os.path.join(dirpath, name), encoding="utf-8") as fh:
            content = fh.read()
        for needle in ("AnalyticsReporter.", "Platform.analytics"):
            if needle in content:
                failures.append("data layer must not call analytics (%s): %s" % (needle, os.path.join(dirpath, name)))

if failures:
    for line in failures:
        print("ANALYTICS_GATE_FAIL: " + line)
    sys.exit(1)
print("ANALYTICS_GATE_OK: %d site checks + data-layer zero-call guard" % len(SITES))
