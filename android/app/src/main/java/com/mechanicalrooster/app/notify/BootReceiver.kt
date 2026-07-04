package com.mechanicalrooster.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mechanicalrooster.app.RoosterApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms do not survive a reboot (or app update), so re-arm every open task
 * from the local database. Fire times that passed while the device was off
 * are nudged one minute into the future.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val container = (context.applicationContext as RoosterApp).container
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                for (task in container.taskDao.getActive()) {
                    val next = if (task.nextFireAtMillis <= now) {
                        task.copy(nextFireAtMillis = now + 60_000L)
                    } else {
                        task
                    }
                    container.taskDao.upsert(next)
                    container.scheduler.schedule(next)
                }
            } finally {
                result.finish()
            }
        }
    }
}
