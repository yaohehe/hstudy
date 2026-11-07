package com.hstudy.app.ui.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hstudy.app.HStudyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机重启接收器：设备重启后 AlarmManager 中的闹钟会被清空，需要重新注册所有日程闹钟
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                ScheduleAlarmHelper.rescheduleAll(context)
            }
        }
    }
}
