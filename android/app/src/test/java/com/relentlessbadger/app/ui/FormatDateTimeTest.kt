package com.relentlessbadger.app.ui

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FormatDateTimeTest {

    // formatDateTime renders in the system zone, so build the input from a
    // local wall-clock time to keep the expected strings zone-independent.
    private val afternoon = LocalDateTime.of(2026, 7, 17, 15, 5)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun pinLocale() {
        // The formatters resolve month and AM/PM names from the default
        // locale when first created.
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `12-hour format uses am-pm clock`() {
        assertEquals("Jul 17, 3:05 PM", formatDateTime(afternoon, use24Hour = false))
    }

    @Test
    fun `24-hour format uses zero-padded 24h clock`() {
        assertEquals("Jul 17, 15:05", formatDateTime(afternoon, use24Hour = true))
    }
}
