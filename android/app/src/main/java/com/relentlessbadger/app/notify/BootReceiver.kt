package com.relentlessbadger.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.relentlessbadger.app.BadgerApp
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

        val container = (context.applicationContext as BadgerApp).container
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.reArmAlarms()
                container.syncScheduler.requestSync()
            } finally {
                result.finish()
            }
        }
    }
}
