package com.relentlessbadger.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.relentlessbadger.app.BadgerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Done" action button on a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as BadgerApp).container
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.completeTask(taskId)
                Notifications.cancel(context, taskId)
            } finally {
                result.finish()
            }
        }
    }
}
