package com.hstudy.app.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hstudy.app.MainActivity
import java.util.Calendar

/**
 * Android 提醒调度器：AlarmManager 精确闹钟 + 广播接收器弹通知。
 * 触发后由接收器重新注册下一次（reschedule-on-fire），保证 Doze 模式下也能到点提醒；
 * 无精确闹钟权限时降级为 setAndAllowWhileIdle。
 * 渠道/通知 id/标题与原 app 保持一致。
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    override fun schedule(reminder: ScheduledReminder) {
        scheduleAlarm(context, reminder)
    }

    override fun cancel(id: Int) {
        ReminderKind.entries.forEach { kind -> cancel(id, kind) }
    }

    override fun cancel(id: Int, kind: ReminderKind) {
        val pi = buildPendingIntent(context, id, kind)
        alarmManager(context)?.cancel(pi)
        pi.cancel()
        untrackId(context, kind, id)
    }

    override fun cancelKind(kind: ReminderKind) {
        trackedIds(context, kind).forEach { id ->
            val pi = buildPendingIntent(context, id, kind)
            alarmManager(context)?.cancel(pi)
            pi.cancel()
        }
        prefs(context).edit().remove(kindKey(kind)).apply()
    }

    override fun rescheduleAll(reminders: List<ScheduledReminder>) {
        ReminderKind.entries.forEach { cancelKind(it) }
        reminders.forEach { schedule(it) }
    }

    companion object {
        const val CHANNEL_SCHEDULE = "hstudy_schedule_reminder"
        const val CHANNEL_REVIEW = "hstudy_review_reminder"
        const val CHANNEL_WEEKLY = "hstudy_weekly_report"

        const val REVIEW_NOTIFICATION_ID = 2001
        const val WEEKLY_NOTIFICATION_ID = 2002

        const val EXTRA_ID = "extra_id"
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_REPEAT_DAILY = "extra_repeat_daily"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_REPEAT_DAYS = "extra_repeat_days"
        const val EXTRA_ROUTE = NotificationRoutes.EXTRA_ROUTE

        private const val PREFS_NAME = "hstudy_reminder_alarms"

        /** 注册（或更新）一个提醒闹钟；只有系统接受注册后才纳入 active 跟踪。 */
        fun scheduleAlarm(context: Context, reminder: ScheduledReminder): Boolean {
            val am = alarmManager(context) ?: return false
            return attemptScheduleAlarm(
                context = context,
                reminder = reminder,
                canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms(),
                setExact = { triggerAt, pi ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                },
                setInexact = { triggerAt, pi ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                },
            )
        }

        /** Injectable registration boundary used by the swallowed-failure regression test. */
        internal fun attemptScheduleAlarm(
            context: Context,
            reminder: ScheduledReminder,
            canScheduleExact: Boolean,
            setExact: (Long, PendingIntent) -> Unit,
            setInexact: (Long, PendingIntent) -> Unit,
        ): Boolean {
            val pi = buildPendingIntent(context, reminder.id, reminder.kind, reminder)
            val triggerAt = nextTriggerTime(reminder)
            return try {
                if (canScheduleExact) {
                    try {
                        setExact(triggerAt, pi)
                    } catch (_: SecurityException) {
                        // 缺少精确闹钟权限时降级为非精确闹钟。
                        setInexact(triggerAt, pi)
                    }
                } else {
                    setInexact(triggerAt, pi)
                }
                trackId(context, reminder.kind, reminder.id)
                true
            } catch (_: Exception) {
                // AlarmManager can reject registration at runtime. Do not retain a phantom active ID.
                pi.cancel()
                false
            }
        }

        /** 接收器触发后重注册下一次（每日/每周重复） */
        fun rescheduleNextFromReceiver(context: Context, intent: Intent) {
            val id = intent.getIntExtra(EXTRA_ID, -1)
            if (id < 0) return
            val kind = runCatching {
                ReminderKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "")
            }.getOrNull() ?: return
            val repeatDaily = intent.getBooleanExtra(EXTRA_REPEAT_DAILY, true)
            if (!repeatDaily && kind != ReminderKind.WEEKLY_REPORT) return
            val reminder = ScheduledReminder(
                id = id,
                kind = kind,
                hour = intent.getIntExtra(EXTRA_HOUR, 0),
                minute = intent.getIntExtra(EXTRA_MINUTE, 0),
                title = intent.getStringExtra(EXTRA_TITLE) ?: "",
                body = intent.getStringExtra(EXTRA_BODY) ?: "",
                repeatDaily = repeatDaily,
                dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1),
                repeatDays = intent.getIntArrayExtra(EXTRA_REPEAT_DAYS)?.toSet() ?: emptySet(),
            )
            // nextTriggerTime 以当前时刻为基准，触发后自然推到明天/下周
            scheduleAlarm(context, reminder)
        }

        /** 弹出本地通知（渠道/标题/图标与原实现一致），点击打开 MainActivity。
         *  [titleOverride] 供需要本地化标题的调用方使用；缺省 null 时用各 kind 与渠道固定的原中文标题。
         *  W9 起的调用方口径：日程、错题复习与周报接收器**都只在 EN 态**覆盖标题；ZH 态一律不传，
         *  保持该 kind 的既有默认标题（与 v3.1.0 逐字节一致——W1 终审 Important 裁决，W9 Ruling 7
         *  把它扩到日程接收器）。 */
        fun showNotification(
            context: Context,
            kind: ReminderKind,
            message: String,
            notificationId: Int,
            titleOverride: String? = null,
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannel(context, nm, kind)

            val launchIntent = buildNotificationIntent(context, kind, notificationId)
            val pendingIntent = PendingIntent.getActivity(
                context, notificationId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val (channelId, title, smallIcon, priority) = when (kind) {
                ReminderKind.SCHEDULE -> Quad(
                    CHANNEL_SCHEDULE, "HStudy 日程提醒",
                    android.R.drawable.ic_lock_idle_alarm, NotificationCompat.PRIORITY_HIGH
                )
                ReminderKind.REVIEW -> Quad(
                    CHANNEL_REVIEW, "HStudy 错题复习提醒",
                    android.R.drawable.ic_lock_idle_alarm, NotificationCompat.PRIORITY_HIGH
                )
                ReminderKind.WEEKLY_REPORT -> Quad(
                    CHANNEL_WEEKLY, "HStudy 每周学习周报",
                    android.R.drawable.ic_dialog_info, NotificationCompat.PRIORITY_DEFAULT
                )
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(titleOverride ?: title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                nm.notify(notificationId, notification)
            } catch (_: SecurityException) {
                // 未授予 POST_NOTIFICATIONS 时静默忽略
            }
        }

        /** Builds the exact click Intent used by every notification PendingIntent. */
        internal fun buildNotificationIntent(
            context: Context,
            kind: ReminderKind,
            notificationId: Int,
        ): Intent {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            return launchIntent.apply {
                putExtra(EXTRA_ROUTE, NotificationRoutes.forKind(kind))
                putExtra(EXTRA_ID, notificationId)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        private data class Quad(val channelId: String, val title: String, val smallIcon: Int, val priority: Int)

        private data class ChannelInfo(val id: String, val name: String, val importance: Int, val description: String)

        private fun ensureChannel(context: Context, nm: NotificationManager, kind: ReminderKind) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val info = when (kind) {
                ReminderKind.SCHEDULE -> ChannelInfo(
                    CHANNEL_SCHEDULE, "每日日程提醒",
                    NotificationManager.IMPORTANCE_HIGH, "到点提醒孩子完成日程安排"
                )
                ReminderKind.REVIEW -> ChannelInfo(
                    CHANNEL_REVIEW, "错题复习提醒",
                    NotificationManager.IMPORTANCE_HIGH, "每天定时提醒孩子复习错题，巩固薄弱知识点"
                )
                ReminderKind.WEEKLY_REPORT -> ChannelInfo(
                    CHANNEL_WEEKLY, "每周学习周报",
                    NotificationManager.IMPORTANCE_DEFAULT, "每周定时推送孩子本周学习情况汇总给家长"
                )
            }
            val channel = NotificationChannel(info.id, info.name, info.importance).apply {
                description = info.description
            }
            nm.createNotificationChannel(channel)
        }

        private fun receiverClass(kind: ReminderKind): Class<*> = when (kind) {
            ReminderKind.SCHEDULE -> ScheduleAlarmReceiver::class.java
            ReminderKind.REVIEW -> ReviewReminderReceiver::class.java
            ReminderKind.WEEKLY_REPORT -> WeeklyReportReceiver::class.java
        }

        /** PendingIntent 以 requestCode=id + 接收器组件区分；extras 不影响其身份，取消时可复用 */
        private fun buildPendingIntent(
            context: Context,
            id: Int,
            kind: ReminderKind,
            reminder: ScheduledReminder? = null
        ): PendingIntent {
            val intent = Intent(context, receiverClass(kind))
            if (reminder != null) {
                intent.putExtra(EXTRA_ID, reminder.id)
                intent.putExtra(EXTRA_KIND, reminder.kind.name)
                intent.putExtra(EXTRA_HOUR, reminder.hour)
                intent.putExtra(EXTRA_MINUTE, reminder.minute)
                intent.putExtra(EXTRA_TITLE, reminder.title)
                intent.putExtra(EXTRA_BODY, reminder.body)
                intent.putExtra(EXTRA_REPEAT_DAILY, reminder.repeatDaily)
                intent.putExtra(EXTRA_DAY_OF_WEEK, reminder.dayOfWeek)
                intent.putExtra(EXTRA_REPEAT_DAYS, reminder.repeatDays.toIntArray())
            }
            return PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 计算下次触发时间：遵守 ISO repeatDays；周报始终推到下一个指定星期。 */
        internal fun nextTriggerTime(
            reminder: ScheduledReminder,
            nowMillis: Long = System.currentTimeMillis(),
        ): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, reminder.hour.coerceIn(0, 23))
                set(Calendar.MINUTE, reminder.minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (reminder.kind == ReminderKind.WEEKLY_REPORT) {
                // dayOfWeek: 1=周一…7=周日（与原 repeatDays 约定一致）；Calendar: 周日=1
                val iso = if (reminder.dayOfWeek in 1..7) reminder.dayOfWeek else 7
                cal.set(Calendar.DAY_OF_WEEK, if (iso == 7) Calendar.SUNDAY else iso + 1)
                if (cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            } else {
                val repeatDays = reminder.repeatDays.filter { it in 1..7 }.toSet()
                if (repeatDays.isNotEmpty()) {
                    if (!repeatDays.contains(isoWeekday(cal)) || cal.timeInMillis <= nowMillis) {
                        do {
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                        } while (!repeatDays.contains(isoWeekday(cal)))
                    }
                } else if (cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return cal.timeInMillis
        }

        private fun isoWeekday(calendar: Calendar): Int = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }

        private fun alarmManager(context: Context): AlarmManager? =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        // ---- 已注册闹钟 id 跟踪（cancelKind 需要枚举某类全部闹钟） ----

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun kindKey(kind: ReminderKind) = "ids_${kind.name}"

        private fun trackedIds(context: Context, kind: ReminderKind): Set<Int> =
            prefs(context).getStringSet(kindKey(kind), emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

        private fun trackId(context: Context, kind: ReminderKind, id: Int) {
            val p = prefs(context)
            val set = p.getStringSet(kindKey(kind), emptySet())!!.toMutableSet()
            set.add(id.toString())
            p.edit().putStringSet(kindKey(kind), set).apply()
        }

        private fun untrackId(context: Context, kind: ReminderKind, id: Int) {
            val p = prefs(context)
            val set = p.getStringSet(kindKey(kind), emptySet())!!.toMutableSet()
            set.remove(id.toString())
            p.edit().putStringSet(kindKey(kind), set).apply()
        }
    }
}