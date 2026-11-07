package com.hstudy.app.ui.dt

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.TimeSource

/**
 * 跨平台日期时间兼容层：替代 app 端原有的 java.util.Date / java.util.Calendar /
 * java.text.SimpleDateFormat / java.util.Locale / String.format 用法。
 *
 * 设计目标：
 * - 保持原有调用写法（Calendar.getInstance()、SimpleDateFormat("yyyy-MM-dd").format(Date())）不变，
 *   UI 代码只需替换 import，行为与原 Android 版一致；
 * - 底层全部基于 kotlinx-datetime + 设备本地时区（TimeZone.currentSystemDefault()）；
 * - 格式化输出与 java 版逐位一致（yyyy-MM-dd / MM/dd HH:mm / MM-dd HH:mm / MM/dd /
 *   yyyyMMdd_HHmmss / H 等本 App 实际用到的 pattern）。
 *
 * 说明：原代码 Calendar.set(DAY_OF_WEEK, MONDAY) 依赖 locale 的 firstDayOfWeek，
 * 本实现统一按 ISO（周一为一周首日），与 App 面向中文 locale（周一首日）的原行为一致。
 */

/** 当前时刻（epoch millis），替代 System.currentTimeMillis() */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** 单调毫秒时钟（替代 android.os.SystemClock.elapsedRealtime()），用于计时器不受时区/NTP 影响 */
private val bootMark = TimeSource.Monotonic.markNow()
fun monotonicMillis(): Long = bootMark.elapsedNow().inWholeMilliseconds

/** 文件存在性（替代 java.io.File(path).exists()） */
fun fileExists(path: String): Boolean = try {
    okio.FileSystem.SYSTEM.exists(okio.Path.Companion.toPath(path))
} catch (_: Exception) {
    false
}

private fun localOf(millis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())

private fun millisOf(dt: LocalDateTime): Long = dt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

private fun pad2(v: Int): String = if (v in 0..9) "0$v" else v.toString()

private fun timeOfDayMillis(dt: LocalDateTime): Long =
    dt.hour * 3_600_000L + dt.minute * 60_000L + dt.second * 1_000L + dt.nanosecond / 1_000_000L

/** ISO：周一=1..周日=7（kotlinx-datetime 的 DayOfWeek 枚举本身按 ISO 顺序声明） */
private val LocalDate.isoDay: Int get() = dayOfWeek.ordinal + 1

/** 替代 java.util.Date：仅承载 epoch millis，time 语义与 Date.time 一致 */
class Date(val time: Long = nowMillis())

/** 替代 java.util.Locale：本 App 只用到 Locale.getDefault()，无 locale 分支逻辑 */
class Locale private constructor(val name: String) {
    companion object {
        private val Default = Locale("default")
        fun getDefault(): Locale = Default
    }
}

/** 替代 java.text.SimpleDateFormat：仅实现本 App 用到的 pattern 字母 */
class SimpleDateFormat(private val pattern: String, private val locale: Locale = Locale.getDefault()) {

    fun format(date: Date): String = format(date.time)

    fun format(calendar: Calendar): String = format(calendar.timeInMillis)

    fun format(timeMillis: Long): String {
        val dt = localOf(timeMillis)
        return buildString {
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                if (!c.isLetter()) {
                    append(c); i++; continue
                }
                var n = i
                while (n < pattern.length && pattern[n] == c) n++
                val count = n - i
                when (c) {
                    'y' -> append(if (count >= 4) dt.year.toString().padStart(4, '0') else dt.year.toString())
                    'M' -> append(if (count >= 2) pad2(dt.monthNumber) else dt.monthNumber.toString())
                    'd' -> append(if (count >= 2) pad2(dt.dayOfMonth) else dt.dayOfMonth.toString())
                    'H' -> append(if (count >= 2) pad2(dt.hour) else dt.hour.toString())
                    'h' -> append(if (count >= 2) pad2(hour12(dt.hour)) else hour12(dt.hour).toString())
                    'm' -> append(if (count >= 2) pad2(dt.minute) else dt.minute.toString())
                    's' -> append(if (count >= 2) pad2(dt.second) else dt.second.toString())
                    'E' -> append(weekdayName(dt.date.isoDay, count))
                    else -> append(c.toString().repeat(count))
                }
                i = n
            }
        }
    }

    /** 解析 yyyy-MM-dd，其它 pattern 不支持时返回 null（对应原 parse 抛异常场景） */
    fun parse(text: String): Date? = parseDateMillisOrNull(text)?.let { Date(it) }

    private fun hour12(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

    private fun weekdayName(iso: Int, count: Int): String {
        val full = arrayOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val short = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return if (count >= 4) full[iso - 1] else short[iso - 1]
    }
}

/**
 * 替代 java.util.Calendar 的最小子集：支持本 App 用到的字段常量与 set/add/get/before/after。
 * 字段取值沿用 java.util.Calendar 约定（DAY_OF_WEEK：Sunday=1..Saturday=7，MONTH 从 0 起）。
 */
class Calendar private constructor(private var stamp: Long) {

    val timeInMillis: Long get() = stamp
    val time: Date get() = Date(stamp)

    fun set(field: Int, value: Int): Calendar {
        val cur = localOf(stamp)
        when (field) {
            YEAR, MONTH, DAY_OF_MONTH -> {
                val y = if (field == YEAR) value else cur.year
                val m = if (field == MONTH) (value + 1).coerceIn(1, 12) else cur.monthNumber
                val d = if (field == DAY_OF_MONTH) value else cur.dayOfMonth
                stamp = startOfDay(clampDate(y, m, d)) + timeOfDayMillis(cur)
            }
            DAY_OF_WEEK -> {
                // java DAY_OF_WEEK(周日=1) -> ISO(周一=1)
                val target = if (value == SUNDAY) 7 else value - 1
                stamp = startOfDay(cur.date.plus(target - cur.date.isoDay, DateTimeUnit.DAY)) + timeOfDayMillis(cur)
            }
            else -> {
                stamp = millisOf(
                    LocalDateTime(
                        cur.year, cur.monthNumber, cur.dayOfMonth,
                        if (field == HOUR_OF_DAY) value else cur.hour,
                        if (field == MINUTE) value else cur.minute,
                        if (field == SECOND) value else cur.second,
                        if (field == MILLISECOND) value * 1_000_000 else cur.nanosecond
                    )
                )
            }
        }
        return this
    }

    fun add(field: Int, amount: Int): Calendar {
        when (field) {
            MILLISECOND -> stamp += amount
            SECOND -> stamp += amount * 1_000L
            MINUTE -> stamp += amount * 60_000L
            HOUR_OF_DAY -> stamp += amount * 3_600_000L
            else -> {
                val cur = localOf(stamp)
                val unit = when (field) {
                    DAY_OF_YEAR, DATE -> DateTimeUnit.DAY
                    WEEK_OF_YEAR, WEEK_OF_MONTH -> DateTimeUnit.WEEK
                    MONTH -> DateTimeUnit.MONTH
                    YEAR -> DateTimeUnit.YEAR
                    else -> return this
                }
                val newDate = if (amount >= 0) cur.date.plus(amount, unit) else cur.date.minus(-amount, unit)
                stamp = startOfDay(newDate) + timeOfDayMillis(cur)
            }
        }
        return this
    }

    fun get(field: Int): Int {
        val dt = localOf(stamp)
        return when (field) {
            ERA -> 1
            YEAR -> dt.year
            MONTH -> dt.monthNumber - 1
            DAY_OF_MONTH -> dt.dayOfMonth
            DAY_OF_YEAR -> dt.date.dayOfYear
            DAY_OF_WEEK -> if (dt.date.isoDay == 7) SUNDAY else dt.date.isoDay + 1
            HOUR_OF_DAY -> dt.hour
            MINUTE -> dt.minute
            SECOND -> dt.second
            MILLISECOND -> dt.nanosecond / 1_000_000
            WEEK_OF_YEAR -> dt.date.dayOfYear / 7 + 1
            else -> 0
        }
    }

    fun before(other: Calendar): Boolean = stamp < other.stamp
    fun after(other: Calendar): Boolean = stamp > other.stamp

    private fun startOfDay(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun clampDate(y: Int, m: Int, d: Int): LocalDate = try {
        LocalDate(y, m, d)
    } catch (_: Exception) {
        LocalDate(y, m, 1)
    }

    companion object {
        const val ERA = 0
        const val YEAR = 1
        const val MONTH = 2
        const val WEEK_OF_YEAR = 3
        const val WEEK_OF_MONTH = 4
        const val DATE = 5
        const val DAY_OF_MONTH = 5
        const val DAY_OF_YEAR = 6
        const val DAY_OF_WEEK = 7
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14

        const val SUNDAY = 1
        const val MONDAY = 2
        const val TUESDAY = 3
        const val WEDNESDAY = 4
        const val THURSDAY = 5
        const val FRIDAY = 6
        const val SATURDAY = 7

        fun getInstance(): Calendar = Calendar(nowMillis())
    }
}

/** 解析 yyyy-MM-dd（本地时区 00:00）为 epoch millis；非法返回 null */
fun parseDateMillisOrNull(text: String): Long? = try {
    LocalDate.parse(text).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
} catch (_: Exception) {
    null
}

/**
 * 替代 java 的 String.format(...) / "% .Nf".format(...)：
 * 支持 %d、%s、%0Nd（补零整数）、%.Nf（定点小数，四舍五入）、%x（十六进制）、%%。
 */
fun strFormat(pattern: String, vararg args: Any?): String {
    val sb = StringBuilder()
    var i = 0
    var argIndex = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%' || i == pattern.lastIndex) { sb.append(c); i++; continue }
        if (pattern[i + 1] == '%') { sb.append('%'); i += 2; continue }
        var j = i + 1
        var zeroPad = false
        var width = 0
        while (j < pattern.length && pattern[j] == '0') { zeroPad = true; j++ }
        while (j < pattern.length && pattern[j].isDigit()) { width = width * 10 + (pattern[j] - '0'); j++ }
        var precision = -1
        if (j < pattern.length && pattern[j] == '.') {
            j++
            precision = 0
            while (j < pattern.length && pattern[j].isDigit()) { precision = precision * 10 + (pattern[j] - '0'); j++ }
        }
        val conv = if (j < pattern.length) pattern[j] else 's'
        val arg = if (argIndex < args.size) args[argIndex] else ""
        argIndex++
        val text = when (conv) {
            'd', 'i' -> (arg as? Number)?.toLong()?.toString() ?: arg.toString()
            'f' -> fixed((arg as? Number)?.toDouble() ?: 0.0, if (precision < 0) 2 else precision)
            'x' -> (arg as? Number)?.toLong()?.toString(16) ?: arg.toString()
            else -> arg.toString()
        }
        sb.append(if (zeroPad && width > text.length) text.padStart(width, '0') else text)
        i = j + 1
    }
    return sb.toString()
}

private fun fixed(value: Double, decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val rounded = round(value * factor) / factor
    val intPart = rounded.toLong()
    if (decimals == 0) return intPart.toString()
    val frac = abs(round(round((rounded - intPart) * factor))).toLong()
    return "$intPart.${frac.toString().padStart(decimals, '0')}"
}
