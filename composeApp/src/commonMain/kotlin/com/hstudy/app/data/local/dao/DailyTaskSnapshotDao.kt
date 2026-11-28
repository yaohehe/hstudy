package com.hstudy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTaskSnapshotDao {
    @Query("SELECT * FROM daily_task_snapshots WHERE date = :date")
    fun getByDate(date: String): Flow<DailyTaskSnapshotEntity?>

    @Query("SELECT * FROM daily_task_snapshots WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): DailyTaskSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DailyTaskSnapshotEntity)

    /** First-write-wins producer insert; the date primary key makes racing captures harmless. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(snapshot: DailyTaskSnapshotEntity)
}
