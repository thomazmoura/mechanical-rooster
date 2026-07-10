package com.relentlessbadger.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.relentlessbadger.app.data.ApiClient
import com.relentlessbadger.app.data.SessionStore
import com.relentlessbadger.app.data.TaskRepository
import com.relentlessbadger.app.db.BadgerDb
import com.relentlessbadger.app.notify.Notifications
import com.relentlessbadger.app.notify.ReminderScheduler
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    val session = SessionStore(context)
    val apiClient = ApiClient(session)
    private val db = Room.databaseBuilder(context, BadgerDb::class.java, "badger.db")
        // open_tasks is a disposable cache rebuilt by sync(); safe to wipe on upgrade.
        .fallbackToDestructiveMigration()
        .build()
    val taskDao = db.openTaskDao()
    val scheduler = ReminderScheduler(context)
    val repository = TaskRepository(apiClient, taskDao, scheduler)
}

class BadgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        // Warm the token/baseUrl mirrors before any receiver touches the API.
        runBlocking { container.session.current() }
    }
}
