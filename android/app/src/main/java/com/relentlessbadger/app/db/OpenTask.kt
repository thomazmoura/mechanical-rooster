package com.relentlessbadger.app.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Local copy of every open (not yet completed) task. This is the source of
 * truth the app runs off: tasks are created here first and the alarm
 * receivers read from here, so everything works offline and across reboots.
 * pendingCreate marks tasks created on-device that still need to reach the API;
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
    val pendingCreate: Boolean = false,
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

    @Query("SELECT * FROM open_tasks WHERE pendingCreate = 1")
    suspend fun getPendingCreate(): List<OpenTaskEntity>

    @Upsert
    suspend fun upsert(task: OpenTaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<OpenTaskEntity>)

    @Query("UPDATE open_tasks SET pendingDone = 1 WHERE id = :id")
    suspend fun markPendingDone(id: String)

    @Query("UPDATE open_tasks SET pendingCreate = 0 WHERE id = :id")
    suspend fun clearPendingCreate(id: String)

    @Query("DELETE FROM open_tasks WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Prunes tasks the server no longer lists as open. Rows with pending local
     * changes are kept: an unpushed create or completion must never be lost to
     * a pull that ran before the push could reach the server.
     */
    @Query("DELETE FROM open_tasks WHERE pendingDone = 0 AND pendingCreate = 0 AND id NOT IN (:ids)")
    suspend fun deleteSyncedNotIn(ids: List<String>)

    @Query("DELETE FROM open_tasks")
    suspend fun clear()
}

@Database(
    entities = [OpenTaskEntity::class, TitleHistoryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class BadgerDb : RoomDatabase() {
    abstract fun openTaskDao(): OpenTaskDao
    abstract fun titleHistoryDao(): TitleHistoryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE open_tasks ADD COLUMN pendingCreate INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `title_history` (" +
                        "`title` TEXT NOT NULL, " +
                        "`useCount` INTEGER NOT NULL, " +
                        "`lastUsedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`title`))",
                )
            }
        }
    }
}
