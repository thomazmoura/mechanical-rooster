package com.relentlessbadger.app.scenario

import com.relentlessbadger.app.data.SettingsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScenarios : ScenarioTest() {

    @Test
    fun `settings saved offline take effect immediately and are pushed when connectivity returns`() = scenario {
        givenOffline()

        whenSettingsSaved(SettingsDto(30, 5, 90, 300))

        // Immediately effective: a new task snapshots the new values.
        val task = whenTaskCreated("water plants")
        assertEquals(30, task.initialDelayMinutes)
        assertEquals(5, task.repeatIntervalMinutes)
        assertTrue("flagged for push", settingsStore.dirty)
        thenNothingPushed()

        givenOnline()
        whenSyncRuns()

        assertEquals(SettingsDto(30, 5, 90, 300), server.receivedSettingsPuts.single())
        assertEquals(SettingsDto(30, 5, 90, 300), server.settings)
        assertFalse("flag cleared once acknowledged", settingsStore.dirty)
    }

    @Test
    fun `unpushed local settings are not overwritten by the server's copy`() = scenario {
        server.settings = SettingsDto(10, 10, 10, 10)
        server.failSettingsPush = true

        whenSettingsSaved(SettingsDto(30, 5, 90, 300))
        whenSyncFailsWith { it is retrofit2.HttpException }

        assertEquals("local edit survives", SettingsDto(30, 5, 90, 300), settingsStore.settings)
        assertTrue("still flagged for push", settingsStore.dirty)
    }

    @Test
    fun `with no local edits, settings changed on the server flow down on sync`() = scenario {
        server.settings = SettingsDto(45, 20, 120, 480)

        whenSyncRuns()

        assertEquals(SettingsDto(45, 20, 120, 480), settingsStore.settings)
    }
}
