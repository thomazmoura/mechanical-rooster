package com.relentlessbadger.app.db

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @Test
    fun `existing v2 data survives the migration to v3`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // The exact schema Room generated for version 2 (pre title_history,
        // pre pendingCreate), with a task an existing install would have.
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { old ->
            old.execSQL(
                "CREATE TABLE IF NOT EXISTS `open_tasks` (`id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                    "`initialDelayMinutes` INTEGER NOT NULL, `repeatIntervalMinutes` INTEGER NOT NULL, " +
                    "`firstWarningAtMillis` INTEGER, `nextFireAtMillis` INTEGER NOT NULL, " +
                    "`pendingDone` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            old.execSQL(
                "INSERT INTO open_tasks VALUES ('task-1', 'water plants', 1000, 60, 15, NULL, 2000, 0)",
            )
            old.version = 2
        }

        val db = Room.databaseBuilder(context, BadgerDb::class.java, "migration-test.db")
            .addMigrations(BadgerDb.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val task = db.openTaskDao().getAll().single()
                assertEquals("task-1", task.id)
                assertEquals("water plants", task.title)
                assertEquals(2000L, task.nextFireAtMillis)
                assertFalse("migrated rows are not pending creates", task.pendingCreate)

                db.titleHistoryDao().recordUse("water plants", 3000L)
                assertEquals(listOf("water plants"), db.titleHistoryDao().getRanked())
            }
        } finally {
            db.close()
        }
    }
}
