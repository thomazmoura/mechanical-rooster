package com.relentlessbadger.app.scenario

import com.relentlessbadger.app.scenario.BadgerScenario.Companion.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeAndReminderScenarios : ScenarioTest() {

    @Test
    fun `a medium snooze pushes the next nag out, dismisses the notification and never touches the server`() = scenario {
        givenLocalSettings(60, 15, mediumWaitMinutes = 90, longWaitMinutes = 300)
        givenOffline()
        val task = whenTaskCreated("water plants")

        whenSnoozed(task.id, 90)

        assertEquals(clock.now() + 90 * MINUTE, localTask(task.id).nextFireAtMillis)
        thenAlarmScheduledAt(task.id, clock.now() + 90 * MINUTE)
        assertTrue(alarms.dismissed.contains(task.id))
        thenNothingPushed()
    }

    @Test
    fun `a long snooze does the same with the long wait`() = scenario {
        givenLocalSettings(60, 15, mediumWaitMinutes = 90, longWaitMinutes = 300)
        givenOffline()
        val task = whenTaskCreated("water plants")

        whenSnoozed(task.id, 300)

        assertEquals(clock.now() + 300 * MINUTE, localTask(task.id).nextFireAtMillis)
        thenAlarmScheduledAt(task.id, clock.now() + 300 * MINUTE)
        thenNothingPushed()
    }

    @Test
    fun `a snooze survives a sync - the local nag time is preserved over the server's view`() = scenario {
        val task = givenSyncedTask("water plants")

        whenSnoozed(task.id, 30)
        whenSyncRuns()

        assertEquals(clock.now() + 30 * MINUTE, localTask(task.id).nextFireAtMillis)
        thenAlarmScheduledAt(task.id, clock.now() + 30 * MINUTE)
    }

    @Test
    fun `a task pulled long after its first warning lands on the next repeat slot in the future`() = scenario {
        // Created 100 min ago, first fire at +60, repeating every 15: slots at
        // +60/+75/+90/+105 — the next one in the future is 5 min from now.
        server.seedOpenTask(
            "old task",
            createdAtMillis = clock.now() - 100 * MINUTE,
            initialDelayMinutes = 60,
            repeatIntervalMinutes = 15,
        )

        whenSyncRuns()

        val task = taskDao.getActive().single()
        assertEquals(clock.now() + 5 * MINUTE, task.nextFireAtMillis)
    }

    @Test
    fun `when a reminder fires it nags with the configured waits and schedules the next repeat`() = scenario {
        givenLocalSettings(60, 15, mediumWaitMinutes = 45, longWaitMinutes = 120)
        givenOffline()
        val task = whenTaskCreated("water plants")
        whenTimeAdvancesMinutes(60)

        whenReminderFires(task.id)

        val shown = alarms.shownReminders.single()
        assertEquals(task.id, shown.task.id)
        assertEquals(45, shown.mediumWaitMinutes)
        assertEquals(120, shown.longWaitMinutes)
        assertEquals(clock.now() + 15 * MINUTE, localTask(task.id).nextFireAtMillis)
        thenAlarmScheduledAt(task.id, clock.now() + 15 * MINUTE)
    }

    @Test
    fun `a stale alarm for an already-completed task shows nothing and stops the chain`() = scenario {
        givenOffline()
        val task = whenTaskCreated("water plants")
        whenTaskCompleted(task.id)

        whenReminderFires(task.id)

        assertTrue("no nag for a completed task", alarms.shownReminders.isEmpty())
        thenNoAlarmArmed(task.id)
    }
}
