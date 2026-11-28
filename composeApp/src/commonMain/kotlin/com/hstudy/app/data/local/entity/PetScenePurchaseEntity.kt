package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock

/** Room-backed ownership for one pet scene purchase. */
@Entity(tableName = "pet_scene_purchases")
data class PetScenePurchaseEntity(
    @PrimaryKey val sceneKey: String,
    val purchasedAt: Long = Clock.System.now().toEpochMilliseconds()
)
