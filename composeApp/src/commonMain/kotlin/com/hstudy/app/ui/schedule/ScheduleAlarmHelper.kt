package com.hstudy.app.ui.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.ui.dt.Calendar

/**
 * 日程闹钟调度辅助：使用 AlarmManager 注册/取消每日重复提醒
 */
object ScheduleAlarmHelper {

    /** 为单个日程注册每日重复闹钟 */
    fun scheduleAlarm(context: Context, schedule: DailyScheduleEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, schedule)

        val triggerAt = getNextTriggerTime(schedule.time)

        // 使用 setRepeating 每日重复（间隔 24 小时）
        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // 缺少 SCHEDULE_EXACT_ALARM 权限时降级为非精确闹钟
        }
    }

    /** 取消单个日程的闹钟 */
    fun cancelAlarm(context: Context, schedule: DailyScheduleEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, schedule)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** 重新注册所有已启用的日程闹钟（App 启动/设备重启后调用） */
    suspend fun rescheduleAll(context: Context) {
        val app = AppGraph
        val schedules = app.scheduleRepository.getEnabledSchedulesSync()
        schedules.forEach { scheduleAlarm(context, it) }
    }

    /** 构建闹钟的 PendingIntent（用 schedule.id 作为 requestCode 区分） */
    private fun buildPendingIntent(context: Context, schedule: DailyScheduleEntity): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, schedule.title)
            putExtra(ScheduleAlarmReceiver.EXTRA_EMOJI, schedule.emoji)
            putExtra(ScheduleAlarmReceiver.EXTRA_HAS_TASK, schedule.linkedTaskId != null)
        }
        return PendingIntent.getBroadcast(
            context,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 计算下次触发时间：若今天该时刻已过则推到明天 */
    private fun getNextTriggerTime(time: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 若已过该时刻，则设为明天
        if (cal.timeInMillis <= com.hstudy.app.ui.dt.nowMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
