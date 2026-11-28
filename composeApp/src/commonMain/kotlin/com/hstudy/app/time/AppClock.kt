package com.hstudy.app.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface AppClock {
    fun nowMillis(): Long
    fun today(): String = nowMillis().let { millis ->
        Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toString()
    }
}

object SystemAppClock : AppClock {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

class FixedAppClock(var millis: Long) : AppClock {
    override fun nowMillis(): Long = millis
}
