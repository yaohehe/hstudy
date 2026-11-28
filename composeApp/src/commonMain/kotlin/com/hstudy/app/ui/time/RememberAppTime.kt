package com.hstudy.app.ui.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hstudy.app.time.AppClock
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

private const val MINUTE_MILLIS = 60_000L

/** Date, minute-of-day and ISO weekday derived from one clock observation. */
internal data class AppTimeSnapshot(
    val date: String,
    val minute: Int,
    val dayOfWeek: Int,
)

/**
 * One minute ticker is the state source for every time-derived value in a screen.
 * Keeping the values in one snapshot prevents the date, query key and highlight from
 * observing different minutes while a composition is crossing midnight.
 */
@Composable
internal fun rememberAppTime(clock: AppClock): AppTimeSnapshot {
    var snapshot by remember(clock) { mutableStateOf(snapshotAt(clock.nowMillis())) }

    LaunchedEffect(clock) {
        while (true) {
            val now = clock.nowMillis()
            val next = snapshotAt(now)
            if (snapshot != next) snapshot = next
            delay(MINUTE_MILLIS - (now % MINUTE_MILLIS))
        }
    }

    return snapshot
}

@Composable
fun rememberAppDate(clock: AppClock): String = rememberAppTime(clock).date

@Composable
fun rememberAppMinute(clock: AppClock): Int = rememberAppTime(clock).minute

private fun snapshotAt(now: Long): AppTimeSnapshot {
    val local = Instant.fromEpochMilliseconds(now)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return AppTimeSnapshot(
        date = local.date.toString(),
        minute = local.hour * 60 + local.minute,
        dayOfWeek = local.date.dayOfWeek.isoDayNumber,
    )
}
