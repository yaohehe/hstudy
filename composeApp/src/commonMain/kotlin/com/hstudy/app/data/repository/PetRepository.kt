package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.DirectTransactionRunner
import com.hstudy.app.data.local.dao.PetScenePurchaseDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.PetScenePurchaseEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/** Pet care spending and durable scene ownership share one Room transaction. */
class PetRepository(
    private val pointDao: PointDao,
    private val scenePurchaseDao: PetScenePurchaseDao,
    private val transactionRunner: DbTransactionRunner = DirectTransactionRunner
) {
    fun getPurchasedScenes(): Flow<Set<String>> =
        scenePurchaseDao.getPurchasedSceneKeys().map { it.toSet() }

    /** Spend points for care; false means no ledger row was written. */
    suspend fun feed(points: Int, note: String): Boolean = transactionRunner.runInTransaction {
        spend(points, note)
    }

    /**
     * Purchase a scene exactly once. The unique Room ownership row and negative ledger
     * row commit together, so neither a duplicate purchase nor a failed debit can unlock it.
     */
    suspend fun purchaseScene(sceneKey: String, points: Int, note: String): Boolean =
        transactionRunner.runInTransaction {
            if (sceneKey.isBlank() || points <= 0) return@runInTransaction false
            if (scenePurchaseDao.getSceneKey(sceneKey) != null) return@runInTransaction false
            if (pointDao.getTotalPoints().first() < points) return@runInTransaction false

            val purchaseId = scenePurchaseDao.insert(
                PetScenePurchaseEntity(sceneKey = sceneKey, purchasedAt = Clock.System.now().toEpochMilliseconds())
            )
            if (purchaseId == -1L) return@runInTransaction false
            pointDao.insertPointRecord(
                PointRecordEntity(
                    points = -points,
                    type = PointRecordType.REWARD_EXCHANGE,
                    note = note,
                    weekStart = weekStartTimestamp()
                )
            )
            true
        }

    /** Import pre-v14 DataStore ownership without charging the child again. */
    suspend fun importLegacyScenes(sceneKeys: Set<String>) {
        if (sceneKeys.isEmpty()) return
        transactionRunner.runInTransaction {
            sceneKeys.filter { it.isNotBlank() }.forEach { sceneKey ->
                scenePurchaseDao.insert(PetScenePurchaseEntity(sceneKey = sceneKey))
            }
        }
    }

    private suspend fun spend(points: Int, note: String): Boolean {
        if (points <= 0) return false
        if (pointDao.getTotalPoints().first() < points) return false
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = -points,
                type = PointRecordType.REWARD_EXCHANGE,
                note = note,
                weekStart = weekStartTimestamp()
            )
        )
        return true
    }
}
