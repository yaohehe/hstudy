package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机重启接收器：设备重启后 AlarmManager 中的闹钟会被清空，
 * 需要从数据库/偏好重新注册全部提醒（日程 + 复习 + 周报）。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AndroidReminderBootstrap.rescheduleAll()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}