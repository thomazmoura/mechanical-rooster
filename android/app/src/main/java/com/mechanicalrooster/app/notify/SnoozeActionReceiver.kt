package com.mechanicalrooster.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mechanicalrooster.app.RoosterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Medium wait" / "Long wait" snooze actions on a reminder notification. */
class SnoozeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(Notifications.EXTRA_TASK_ID) ?: return
        val minutes = intent.getIntExtra(Notifications.EXTRA_SNOOZE_MINUTES, 0)
        if (minutes < 1) return
        val container = (context.applicationContext as RoosterApp).container
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.snoozeTask(taskId, minutes)
            } finally {
                result.finish()
            }
        }
    }
}
