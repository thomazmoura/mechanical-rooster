package com.relentlessbadger.app.scenario

import com.relentlessbadger.app.scenario.BadgerScenario.Companion.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Test

class BootAndRecoveryScenarios : ScenarioTest() {

    @Test
    fun `after a reboot every open task's alarm is re-armed at its stored time`() = scenario {
        givenOffline()
        val a = whenTaskCreated("water plants")
        val b = whenTaskCreated("walk dog", firstWarningAtMillis = clock.now() + 300 * MINUTE)
        alarms.scheduled.clear() // reboot: all alarms lost

        whenBootReArmRuns()

        thenAlarmScheduledAt(a.id, a.nextFireAtMillis)
        thenAlarmScheduledAt(b.id, b.nextFireAtMillis)
    }

    @Test
    fun `a fire time missed while the device was off is nudged a minute into the future`() = scenario {
        givenOffline()
        val task = whenTaskCreated("water plants") // fires at +60 min
        alarms.scheduled.clear()
        whenTimeAdvancesMinutes(90) // device was off past the fire time

        whenBootReArmRuns()

        assertEquals(clock.now() + MINUTE, localTask(task.id).nextFireAtMillis)
        thenAlarmScheduledAt(task.id, clock.now() + MINUTE)
    }
}
