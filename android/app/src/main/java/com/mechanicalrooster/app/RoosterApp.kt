package com.mechanicalrooster.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mechanicalrooster.app.data.ApiClient
import com.mechanicalrooster.app.data.SessionStore
import com.mechanicalrooster.app.data.TaskRepository
import com.mechanicalrooster.app.db.RoosterDb
import com.mechanicalrooster.app.notify.Notifications
import com.mechanicalrooster.app.notify.ReminderScheduler
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    val session = SessionStore(context)
    val apiClient = ApiClient(session)
    private val db = Room.databaseBuilder(context, RoosterDb::class.java, "rooster.db")
        // open_tasks is a disposable cache rebuilt by sync(); safe to wipe on upgrade.
        .fallbackToDestructiveMigration()
        .build()
    val taskDao = db.openTaskDao()
    val scheduler = ReminderScheduler(context)
    val repository = TaskRepository(apiClient, taskDao, scheduler)
}

class RoosterApp : Application() {
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
