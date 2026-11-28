package com.hstudy.app.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.MainActivity
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSchedulerTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun weeklyIntentWithRepeatDailyFalseRegistersNextAlarm() {
        val scheduler = AndroidNotificationScheduler(context)
        val id = 9201
        scheduler.cancel(id)
        val intent = Intent(context, WeeklyReportReceiver::class.java).apply {
            putExtra(AndroidNotificationScheduler.EXTRA_ID, id)
            putExtra(AndroidNotificationScheduler.EXTRA_KIND, ReminderKind.WEEKLY_REPORT.name)
            putExtra(AndroidNotificationScheduler.EXTRA_HOUR, 20)
            putExtra(AndroidNotificationScheduler.EXTRA_MINUTE, 0)
            putExtra(AndroidNotificationScheduler.EXTRA_TITLE, "Weekly")
            putExtra(AndroidNotificationScheduler.EXTRA_BODY, "Weekly body")
            putExtra(AndroidNotificationScheduler.EXTRA_REPEAT_DAILY, false)
            putExtra(AndroidNotificationScheduler.EXTRA_DAY_OF_WEEK, 7)
        }
        try {
            AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)

            assertTrue(trackedIds(ReminderKind.WEEKLY_REPORT).contains(id.toString()))
        } finally {
            scheduler.cancel(id)
        }
    }

    @Test
    fun oneShotNonWeeklyIntentDoesNotRegisterAnotherAlarm() {
        val scheduler = AndroidNotificationScheduler(context)
        val id = 9202
        scheduler.cancel(id)
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(AndroidNotificationScheduler.EXTRA_ID, id)
            putExtra(AndroidNotificationScheduler.EXTRA_KIND, ReminderKind.SCHEDULE.name)
            putExtra(AndroidNotificationScheduler.EXTRA_HOUR, 8)
            putExtra(AndroidNotificationScheduler.EXTRA_MINUTE, 0)
            putExtra(AndroidNotificationScheduler.EXTRA_REPEAT_DAILY, false)
        }
        try {
            AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)

            assertFalse(trackedIds(ReminderKind.SCHEDULE).contains(id.toString()))
        } finally {
            scheduler.cancel(id)
        }
    }

    @Test
    fun weekendOnlyScheduleFromMondaySelectsSaturday() {
        val mondayNineAm = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 23, 9, 0, 0)
        }.timeInMillis
        val reminder = ScheduledReminder(
            id = 9203,
            kind = ReminderKind.SCHEDULE,
            hour = 8,
            minute = 0,
            title = "Weekend",
            body = "Weekend only",
            repeatDays = setOf(6, 7),
        )

        val trigger = AndroidNotificationScheduler.nextTriggerTime(reminder, mondayNineAm)
        val expected = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 28, 8, 0, 0)
        }

        assertEquals(expected.timeInMillis, trigger)
        assertEquals(Calendar.SATURDAY, Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun notificationClickIntentCarriesStableRouteForEveryKind() {
        val cases = listOf(
            ReminderKind.SCHEDULE to "child_schedule_timeline",
            ReminderKind.REVIEW to "child_mistake_list",
            ReminderKind.WEEKLY_REPORT to "parent_weekly_report",
        )

        cases.forEach { (kind, expectedRoute) ->
            val intent = AndroidNotificationScheduler.buildNotificationIntent(context, kind, 9300)

            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(expectedRoute, intent.getStringExtra(AndroidNotificationScheduler.EXTRA_ROUTE))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        }
    }

    @Test
    fun cancelScheduleDoesNotCancelSameIdWeeklyReminder() {
        val scheduler = AndroidNotificationScheduler(context)
        val id = 9301
        scheduler.cancel(id)
        try {
            scheduler.schedule(reminder(id, ReminderKind.SCHEDULE))
            scheduler.schedule(reminder(id, ReminderKind.WEEKLY_REPORT))

            scheduler.cancel(id, ReminderKind.SCHEDULE)

            assertFalse(trackedIds(ReminderKind.SCHEDULE).contains(id.toString()))
            assertTrue(trackedIds(ReminderKind.WEEKLY_REPORT).contains(id.toString()))
        } finally {
            scheduler.cancel(id)
        }
    }

    @Test
    fun weeklyNextTriggerIsSundayEvenWhenRepeatDailyIsFalse() {
        val mondayNineAm = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 23, 9, 0, 0)
        }.timeInMillis
        val reminder = ScheduledReminder(
            id = 9204,
            kind = ReminderKind.WEEKLY_REPORT,
            hour = 20,
            minute = 0,
            title = "Weekly",
            body = "Weekly body",
            repeatDaily = false,
            dayOfWeek = 7,
        )

        val trigger = AndroidNotificationScheduler.nextTriggerTime(reminder, mondayNineAm)
        val actual = Calendar.getInstance().apply { timeInMillis = trigger }
        val expected = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 29, 20, 0, 0)
        }

        assertEquals(expected.timeInMillis, trigger)
        assertEquals(Calendar.SUNDAY, actual.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun failedExactAndFallbackRegistrationIsNotTrackedAsActive() {
        val scheduler = AndroidNotificationScheduler(context)
        val id = 9302
        scheduler.cancel(id)
        var attempts = 0
        try {
            val scheduled = AndroidNotificationScheduler.attemptScheduleAlarm(
                context = context,
                reminder = reminder(id, ReminderKind.SCHEDULE),
                canScheduleExact = true,
                setExact = { _, _ ->
                    attempts++
                    throw SecurityException("injected exact alarm failure")
                },
                setInexact = { _, _ ->
                    attempts++
                    throw IllegalStateException("injected fallback alarm failure")
                },
            )

            assertFalse(scheduled)
            assertEquals(2, attempts)
            assertFalse(trackedIds(ReminderKind.SCHEDULE).contains(id.toString()))
        } finally {
            scheduler.cancel(id)
        }
    }

    private fun reminder(id: Int, kind: ReminderKind) = ScheduledReminder(
        id = id,
        kind = kind,
        hour = 20,
        minute = 0,
        title = "Reminder",
        body = "Body",
        repeatDaily = kind != ReminderKind.WEEKLY_REPORT,
        dayOfWeek = if (kind == ReminderKind.WEEKLY_REPORT) 7 else -1,
    )

    private fun trackedIds(kind: ReminderKind): Set<String> =
        context.getSharedPreferences("hstudy_reminder_alarms", Context.MODE_PRIVATE)
            .getStringSet("ids_${kind.name}", emptySet()).orEmpty()

    private fun Calendar.clear() {
        set(Calendar.MILLISECOND, 0)
        set(Calendar.SECOND, 0)
    }
}
