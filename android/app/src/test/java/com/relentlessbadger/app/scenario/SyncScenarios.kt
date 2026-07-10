package com.relentlessbadger.app.scenario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScenarios : ScenarioTest() {

    @Test
    fun `a task the server stopped listing is pruned locally and its alarm cancelled`() = scenario {
        val task = givenSyncedTask("done elsewhere")
        server.tasks.remove(task.id)

        whenSyncRuns()

        thenTaskGone("done elsewhere")
        assertTrue(alarms.cancelled.contains(task.id))
        thenNoAlarmArmed(task.id)
    }

    @Test
    fun `an offline-created task whose push failed survives the pull instead of being pruned`() = scenario {
        server.failCreatesWithServerError = true
        val task = whenTaskCreated("must not vanish")

        whenSyncRuns() // push fails with 500 (flag kept), pull still happens

        thenTaskVisible("must not vanish")
        assertTrue("still queued for the next sync", localTask(task.id).pendingCreate)

        server.failCreatesWithServerError = false
        whenSyncRuns()
        thenServerHasOpenTask("must not vanish")
    }

    @Test
    fun `a task created on another device appears locally with an armed reminder after sync`() = scenario {
        val dto = givenServerHasOpenTask("from the other phone")

        whenSyncRuns()

        thenTaskVisible("from the other phone")
        assertNotNull("alarm armed for the new task", alarms.scheduled[dto.id])
    }

    @Test
    fun `a sync that dies mid-pull loses no local data and no pending flags`() = scenario {
        val synced = givenSyncedTask("already here")
        givenOffline()
        val created = whenTaskCreated("made offline")
        givenOnline()
        server.failTaskPull = true

        whenSyncFailsWith { it is java.net.ConnectException }

        thenTaskVisible("already here")
        thenTaskVisible("made offline")
        assertNotNull(taskDao.getById(synced.id))
        // The create was pushed before the pull died, so its flag is rightly
        // cleared; the row itself must still be intact.
        assertEquals("made offline", localTask(created.id).title)

        server.failTaskPull = false
        whenSyncRuns()
        thenServerHasOpenTask("made offline")
    }
}
