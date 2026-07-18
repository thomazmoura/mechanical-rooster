package com.relentlessbadger.app.scenario

import com.relentlessbadger.app.data.Recurrence
import com.relentlessbadger.app.scenario.BadgerScenario.Companion.MINUTE
import com.relentlessbadger.app.data.RecurUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar's completion history: completing a task caches it locally the
 * moment it happens, and sync fills in completions from other devices — so the
 * calendar works offline and never loses the truthful local completion time.
 */
class CalendarScenarios : ScenarioTest() {

    @Test
    fun `completing a task offline caches it with the local clock's timestamp`() = scenario {
        val task = givenSyncedTask("water plants")
        givenOffline()
        whenTimeAdvancesMinutes(30)

        whenTaskCompleted(task.id)

        thenCompletionCached("water plants", atMillis = clock.now())
    }

    @Test
    fun `the cached completion survives the sync that removes the open row`() = scenario {
        val task = givenSyncedTask("water plants")

        whenTaskCompleted(task.id)
        whenSyncRuns()

        assertNull("open row flushed", taskDao.getById(task.id))
        thenCompletionCached("water plants")
    }

    @Test
    fun `sync pulls completions made on other devices into the cache`() = scenario {
        givenServerHasCompletedTask("walk dog", completedAtMillis = clock.now() - 10 * MINUTE)

        whenSyncRuns()

        thenCompletionCached("walk dog", atMillis = clock.now() - 10 * MINUTE)
    }

    @Test
    fun `the server's later push-time stamp never overwrites the local completion time`() = scenario {
        val task = givenSyncedTask("water plants")
        givenOffline()
        whenTaskCompleted(task.id)
        val completedLocallyAt = clock.now()

        whenTimeAdvancesMinutes(120)
        givenOnline()
        whenSyncRuns()

        assertEquals(
            "server stamped its own (later) completion time",
            true,
            server.tasks[task.id]?.completedAt != null,
        )
        thenCompletionCached("water plants", atMillis = completedLocallyAt)
    }

    @Test
    fun `signing out clears the completion cache`() = scenario {
        val task = givenSyncedTask("water plants")
        whenTaskCompleted(task.id)

        repository.signOut()

        assertTrue("cache cleared on sign-out", completedCache().isEmpty())
    }

    @Test
    fun `completing a recurring occurrence caches it tagged with its series`() = scenario {
        val task = whenTaskCreated(
            "meds",
            firstWarningAtMillis = clock.now() + 60 * MINUTE,
            recurrence = Recurrence(1, RecurUnit.DAYS),
        )

        whenTaskCompleted(task.id)

        assertEquals(task.seriesId, completedCache().single().seriesId)
    }
}
