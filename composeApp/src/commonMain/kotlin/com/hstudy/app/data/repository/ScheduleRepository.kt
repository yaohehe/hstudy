package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayOfWeek
import kotlinx.datetime.toLocalDateTime

class ScheduleRepository(private val scheduleDao: DailyScheduleDao) {

    fun getAllSchedules(): Flow<List<DailyScheduleEntity>> = scheduleDao.getAllSchedules()

    fun getEnabledSchedules(): Flow<List<DailyScheduleEntity>> = scheduleDao.getEnabledSchedules()

    suspend fun getScheduleById(id: Long): DailyScheduleEntity? = scheduleDao.getScheduleById(id)

    suspend fun addSchedule(schedule: DailyScheduleEntity): Long = scheduleDao.insert(schedule)

    suspend fun updateSchedule(schedule: DailyScheduleEntity) = scheduleDao.update(schedule)

    suspend fun deleteSchedule(id: Long) = scheduleDao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = scheduleDao.setEnabled(id, enabled)

    suspend fun getEnabledSchedulesSync(): List<DailyScheduleEntity> = scheduleDao.getEnabledSchedulesSync()

    /** 判断日程在指定星期几（1=周一...7=周日）是否重复 */
    fun isRepeatOn(schedule: DailyScheduleEntity, dayOfWeek: Int): Boolean {
        return schedule.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(dayOfWeek)
    }

    companion object {
        /** 获取今天是星期几（1=周一...7=周日） */
        fun getTodayDayOfWeek(): Int {
            // ISO：MONDAY=1 ... SUNDAY=7，与原 Calendar 转换后的语义一致
            return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                .date.dayOfWeek.isoDayOfWeek
        }
    }
}