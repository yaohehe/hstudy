package com.hstudy.app.data.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayOfWeek
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * 跨平台时间工具：替代原 java.text.SimpleDateFormat("yyyy-MM-dd") / java.util.Calendar 用法。
 * 日期字符串格式与原实现完全一致（ISO yyyy-MM-dd，设备本地时区）。
 */

/** 今天日期字符串（yyyy-MM-dd，本地时区），等价于原 SimpleDateFormat("yyyy-MM-dd").format(Date()) */
internal fun todayString(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

/** 将时间戳格式化为 yyyy-MM-dd（本地时区），等价于原 SimpleDateFormat("yyyy-MM-dd").format(Date(millis)) */
internal fun dateStringOf(timeMillis: Long): String =
    Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

/**
 * 获取指定时刻所在周的周一 00:00:00 时间戳（本地时区）。
 * 与原 Calendar.set(DAY_OF_WEEK, MONDAY) 在周一为一周首日（中文 locale/ISO）的设备上行为一致。
 */
internal fun weekStartTimestamp(timeMillis: Long = Clock.System.now().toEpochMilliseconds()): Long {
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(tz).date
    val monday = date.minus(date.dayOfWeek.isoDayOfWeek - 1, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(tz).toEpochMilliseconds()
}

/** yyyyMMdd_HHmmss 格式时间戳（本地时区），用于迁移备份文件名 */
internal fun backupStamp(): String {
    val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    fun Int.p2() = toString().padStart(2, '0')
    return "${dt.year}${dt.monthNumber.p2()}${dt.dayOfMonth.p2()}_${dt.hour.p2()}${dt.minute.p2()}${dt.second.p2()}"
}

/** 解析 yyyy-MM-dd；非法格式返回 null（对应原 SimpleDateFormat.parse 抛异常的场景） */
internal fun parseDateOrNull(value: String): LocalDate? = try {
    LocalDate.parse(value)
} catch (_: Exception) {
    null
}