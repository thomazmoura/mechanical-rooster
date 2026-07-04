package com.mechanicalrooster.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mechanicalrooster.app.RoosterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires for a single task: shows the nag notification and schedules the next
 * repeat. The chain only stops when the task is completed (row removed or
 * flagged pendingDone).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as RoosterApp).container
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = container.taskDao.getById(taskId) ?: return@launch
                if (task.pendingDone) return@launch

                Notifications.showReminder(context, task)

                val next = task.copy(
                    nextFireAtMillis = System.currentTimeMillis() + task.repeatIntervalMinutes * 60_000L,
                )
                container.taskDao.upsert(next)
                container.scheduler.schedule(next)
            } finally {
                result.finish()
            }
        }
    }
}
