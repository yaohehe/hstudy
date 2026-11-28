package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.TaskFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Task completion cadence, independent of its persisted enum name. */
enum class FrequencyPeriod { DAY, WEEK, MONTH, ONCE }

internal data class FrequencyWindow(
    val startInclusive: LocalDate?,
    val endExclusive: LocalDate?
)

internal fun TaskFrequency.period(): FrequencyPeriod = when (this) {
    TaskFrequency.DAILY -> FrequencyPeriod.DAY
    TaskFrequency.WEEKLY -> FrequencyPeriod.WEEK
    TaskFrequency.MONTHLY -> FrequencyPeriod.MONTH
    TaskFrequency.ONCE -> FrequencyPeriod.ONCE
}

/**
 * Stable idempotency key for the frequency period containing [date].
 *
 * DAILY deliberately retains the historical `yyyy-MM-dd` suffix so existing
 * task approval keys and rejected-day re-check-in behavior remain compatible.
 * WEEK uses the ISO Monday date, MONTH uses its calendar month, and ONCE uses
 * the lifetime key required by the task contract.
 */
fun TaskFrequency.periodKey(date: LocalDate): String = when (period()) {
    FrequencyPeriod.DAY -> date.toString()
    FrequencyPeriod.WEEK -> {
        val monday = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
        "WEEK:$monday"
    }
    FrequencyPeriod.MONTH -> "MONTH:${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
    FrequencyPeriod.ONCE -> "ONCE"
}

/** Half-open local-date window used by Room range queries; ONCE is unbounded. */
internal fun TaskFrequency.periodWindow(date: LocalDate): FrequencyWindow = when (period()) {
    FrequencyPeriod.DAY -> FrequencyWindow(date, date.plus(1, DateTimeUnit.DAY))
    FrequencyPeriod.WEEK -> {
        val monday = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
        FrequencyWindow(monday, monday.plus(1, DateTimeUnit.WEEK))
    }
    FrequencyPeriod.MONTH -> {
        val first = LocalDate(date.year, date.monthNumber, 1)
        FrequencyWindow(first, first.plus(1, DateTimeUnit.MONTH))
    }
    FrequencyPeriod.ONCE -> FrequencyWindow(null, null)
}
