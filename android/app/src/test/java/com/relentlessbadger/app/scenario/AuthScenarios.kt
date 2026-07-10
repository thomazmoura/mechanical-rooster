package com.relentlessbadger.app.scenario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthScenarios : ScenarioTest() {

    @Test
    fun `a rejected session loses nothing - the queue survives and pushes after re-login`() = scenario {
        givenOffline()
        val created = whenTaskCreated("made offline")
        givenOnline()
        server.unauthorized = true

        whenSyncFailsWith { it is retrofit2.HttpException && it.code() == 401 }

        thenTaskVisible("made offline")
        assertTrue("create still queued", localTask(created.id).pendingCreate)

        server.unauthorized = false // user signed in again
        whenSyncRuns()

        thenServerHasOpenTask("made offline")
    }

    @Test
    fun `signing out flushes the pending queue before clearing local data`() = scenario {
        givenOffline()
        val task = whenTaskCreated("made offline")
        whenTaskCompleted(task.id)
        givenOnline()

        repository.signOut()

        assertEquals("create pushed before wiping", task.id, server.receivedCreates.single().id)
        assertEquals(listOf(task.id), server.receivedCompletions)
        assertTrue("local tasks wiped", taskDao.getAll().isEmpty())
        assertTrue("title history wiped", repository.titles().isEmpty())
    }
}
