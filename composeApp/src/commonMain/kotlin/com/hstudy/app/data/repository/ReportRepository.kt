package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.DailyTaskSnapshotDao
import com.hstudy.app.data.local.dao.ReportDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.time.AppClock
import com.hstudy.app.time.SystemAppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

/** Immutable awarded-point/count snapshot for one task in a report range. */
data class HistoricalTaskStats(val points: Int, val awards: Int)

enum class CompletionRateSource {
    DAILY_SNAPSHOT,
    LEGACY_ACTIVE_TASK_FALLBACK
}

/**
 * A completion rate and the exact denominator source used to produce it. The source is
 * carried with the number so legacy estimates can be labelled instead of presented as
 * historical fact.
 */
data class CompletionRateReport(
    val rate: Int,
    val completed: Int,
    val total: Int,
    val source: CompletionRateSource
)

/** Report aggregates backed by Room ledger rows and immutable daily task snapshots. */
class ReportRepository(
    private val reportDao: ReportDao,
    private val snapshotDao: DailyTaskSnapshotDao,
    private val taskDao: TaskDao,
    private val clock: AppClock = SystemAppClock
) {
    /** Shared with TaskRepository so observed/initialized task lists produce report denominators. */
    val snapshotWriter = DailyTaskSnapshotWriter(taskDao, snapshotDao, clock)

    fun historicalTaskStats(range: LongRange): Flow<Map<Long, HistoricalTaskStats>> =
        reportDao.getHistoricalTaskStats(range.first, range.last + 1).map { rows ->
            rows.associate { row -> row.taskId to HistoricalTaskStats(row.points, row.awards) }
        }

    fun historicalTaskPoints(range: LongRange): Flow<Map<Long, Int>> =
        historicalTaskStats(range).map { stats -> stats.mapValues { it.value.points } }

    /** Inclusive local-date range; the ledger query itself uses a half-open millisecond range. */
    fun dailyAwardedPoints(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Int>> =
        reportDao.getDailyAwardedPoints(startMillis(start), endMillisExclusive(end)).map { rows ->
            rows.mapNotNull { row -> parseDate(row.date)?.let { it to row.points } }.toMap()
        }

    fun weeklyPoints(weekStart: LocalDate): Flow<Map<LocalDate, Int>> =
        dailyAwardedPoints(weekStart, weekStart.plus(6, DateTimeUnit.DAY))

    fun completionRate(date: LocalDate): Flow<Int> =
        completionRateReport(date).map { it.rate }

    fun completionRateReport(date: LocalDate): Flow<CompletionRateReport> = flow {
        // Only today can be truthfully backfilled from the current active set. Historical dates
        // without a snapshot stay explicitly labelled legacy estimates.
        if (date.toString() == clock.today()) snapshotWriter.captureActive()
        emitAll(completionRateReportFromSnapshot(date))
    }

    private fun completionRateReportFromSnapshot(date: LocalDate): Flow<CompletionRateReport> = combine(
        snapshotDao.getByDate(date.toString()),
        reportDao.getApprovedTaskCompletions(date.toString(), date.toString()),
        taskDao.getAllActiveTasks()
    ) { snapshot, completions, activeTasks ->
        val approved = completions.mapTo(mutableSetOf()) { it.taskId }
        if (snapshot != null) {
            val taskIds = parseTaskIds(snapshot.taskIdsJson)
            val completed = if (taskIds != null) approved.count { it in taskIds }
            else approved.size.coerceAtMost(snapshot.totalCount)
            rateReport(completed, snapshot.totalCount.coerceAtLeast(0), CompletionRateSource.DAILY_SNAPSHOT)
        } else {
            val activeTaskIds = activeTasks.mapTo(mutableSetOf()) { it.id }
            val completed = approved.count { it in activeTaskIds }
            rateReport(completed, activeTaskIds.size, CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK)
        }
    }

    /**
     * One Room-backed flow for every date in [start, end]. Snapshot dates use their stored
     * task set and approved completions only. Dates without a snapshot are explicitly marked
     * as a legacy active-task fallback, and every result is clamped to 0..100.
     */
    fun completionRateReports(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, CompletionRateReport>> {
        if (start > end) return kotlinx.coroutines.flow.flowOf(emptyMap())
        val dates = generateSequence(start) { cursor ->
            cursor.takeIf { it < end }?.plus(1, DateTimeUnit.DAY)
        }.toList()
        if (dates.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyMap())
        return flow {
            val today = clock.today()
            if (dates.any { it.toString() == today }) snapshotWriter.captureActive()
            emitAll(completionRateReportsFromSnapshots(dates))
        }
    }

    private fun completionRateReportsFromSnapshots(
        dates: List<LocalDate>
    ): Flow<Map<LocalDate, CompletionRateReport>> = combine(
        reportDao.getDailySnapshots(dates.first().toString(), dates.last().toString()),
        reportDao.getApprovedTaskCompletions(dates.first().toString(), dates.last().toString()),
        taskDao.getAllActiveTasks()
    ) { snapshots, completions, activeTasks ->
        val snapshotsByDate = snapshots.associateBy { it.date }
        val completedByDate = completions.groupBy({ it.date }, { it.taskId }).mapValues { it.value.toSet() }
        dates.associateWith { date ->
            val key = date.toString()
            val snapshot = snapshotsByDate[key]
            val approved = completedByDate[key].orEmpty()
            if (snapshot != null) {
                val taskIds = parseTaskIds(snapshot.taskIdsJson)
                val completed = if (taskIds != null) approved.count { it in taskIds }
                else approved.size.coerceAtMost(snapshot.totalCount)
                rateReport(completed, snapshot.totalCount.coerceAtLeast(0), CompletionRateSource.DAILY_SNAPSHOT)
            } else {
                val activeTaskIds = activeTasks.mapTo(mutableSetOf()) { it.id }
                val completed = approved.count { it in activeTaskIds }
                rateReport(completed, activeTaskIds.size, CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK)
            }
        }
    }

    /** Approved awarded completions grouped by their stored local completion date. */
    fun approvedCompletionsBetween(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Set<Long>>> =
        reportDao.getApprovedTaskCompletions(start.toString(), end.toString()).map { rows ->
            rows.groupBy({ parseDate(it.date) }, { it.taskId })
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.toSet() }
        }

    private fun rateReport(completed: Int, total: Int, source: CompletionRateSource): CompletionRateReport {
        val safeTotal = total.coerceAtLeast(0)
        val safeCompleted = completed.coerceIn(0, safeTotal)
        val rate = if (safeTotal == 0) 0 else (safeCompleted.toLong() * 100L / safeTotal).toInt()
        return CompletionRateReport(rate = rate, completed = safeCompleted, total = safeTotal, source = source)
    }

    private fun parseTaskIds(json: String): Set<Long>? = runCatching {
        Json.decodeFromString<List<Long>>(json).toSet()
    }.getOrNull()

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun startMillis(date: LocalDate): Long =
        date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun endMillisExclusive(date: LocalDate): Long =
        date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
