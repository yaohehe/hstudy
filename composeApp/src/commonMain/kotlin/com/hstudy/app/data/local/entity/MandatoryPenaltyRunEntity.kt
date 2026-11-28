package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable exactly-once marker for one mandatory-task penalty date.
 *
 * The marker is claimed and committed in the same Room transaction as the PENALTY ledger row.
 * A process crash rolls both back; a concurrent runner loses the primary-key claim and writes nothing.
 */
@Entity(tableName = "mandatory_penalty_runs")
data class MandatoryPenaltyRunEntity(
    @PrimaryKey val periodKey: String,
    val processedAt: Long,
    val deductedPoints: Int,
)
