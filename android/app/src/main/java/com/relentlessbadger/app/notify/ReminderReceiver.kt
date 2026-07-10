package com.relentlessbadger.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.relentlessbadger.app.BadgerApp
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
        val container = (context.applicationContext as BadgerApp).container
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.onReminderFired(taskId)
            } finally {
                result.finish()
            }
        }
    }
}
