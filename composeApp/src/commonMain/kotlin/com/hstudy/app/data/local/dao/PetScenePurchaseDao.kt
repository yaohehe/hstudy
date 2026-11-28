package com.hstudy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hstudy.app.data.local.entity.PetScenePurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PetScenePurchaseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(purchase: PetScenePurchaseEntity): Long

    @Query("SELECT sceneKey FROM pet_scene_purchases ORDER BY sceneKey")
    fun getPurchasedSceneKeys(): Flow<List<String>>

    @Query("SELECT sceneKey FROM pet_scene_purchases WHERE sceneKey = :sceneKey LIMIT 1")
    suspend fun getSceneKey(sceneKey: String): String?
}
