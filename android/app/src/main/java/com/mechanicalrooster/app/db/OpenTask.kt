package com.mechanicalrooster.app.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Local copy of every open (not yet completed) task. This is what the alarm
 * receivers run off, so reminders keep firing offline and across reboots.
 * pendingDone marks tasks completed on-device that still need to reach the API.
 */
@Entity(tableName = "open_tasks")
data class OpenTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtMillis: Long,
    val initialDelayMinutes: Int,
    val repeatIntervalMinutes: Int,
    val firstWarningAtMillis: Long? = null,
    val nextFireAtMillis: Long,
    val pendingDone: Boolean = false,
)

@Dao
interface OpenTaskDao {
    @Query("SELECT * FROM open_tasks WHERE pendingDone = 0 ORDER BY createdAtMillis DESC")
    fun observeActive(): Flow<List<OpenTaskEntity>>

    @Query("SELECT * FROM open_tasks WHERE pendingDone = 0")
    suspend fun getActive(): List<OpenTaskEntity>

    @Query("SELECT * FROM open_tasks")
    suspend fun getAll(): List<OpenTaskEntity>

    @Query("SELECT * FROM open_tasks WHERE id = :id")
    suspend fun getById(id: String): OpenTaskEntity?

    @Query("SELECT * FROM open_tasks WHERE pendingDone = 1")
    suspend fun getPendingDone(): List<OpenTaskEntity>

    @Upsert
    suspend fun upsert(task: OpenTaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<OpenTaskEntity>)

    @Query("UPDATE open_tasks SET pendingDone = 1 WHERE id = :id")
    suspend fun markPendingDone(id: String)

    @Query("DELETE FROM open_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM open_tasks WHERE pendingDone = 0 AND id NOT IN (:ids)")
    suspend fun deleteActiveNotIn(ids: List<String>)

    @Query("DELETE FROM open_tasks")
    suspend fun clear()
}

@Database(entities = [OpenTaskEntity::class], version = 2, exportSchema = false)
abstract class RoosterDb : RoomDatabase() {
    abstract fun openTaskDao(): OpenTaskDao
}
