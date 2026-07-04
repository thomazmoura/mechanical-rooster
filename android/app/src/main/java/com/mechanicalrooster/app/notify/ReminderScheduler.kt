package com.mechanicalrooster.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mechanicalrooster.app.db.OpenTaskEntity

class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(task: OpenTaskEntity) {
        val pendingIntent = reminderIntent(task.id)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, task.nextFireAtMillis, pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, task.nextFireAtMillis, pendingIntent,
            )
        }
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(reminderIntent(taskId))
        Notifications.cancel(context, taskId)
    }

    private fun reminderIntent(taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            Intent(context, ReminderReceiver::class.java).putExtra(Notifications.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
